package com.xu.universaltranscoder.core;

// 关键字 class：MediaJobTask 把“命令构建 + 执行 + 自动补救”串成队列可调度的任务
// 之所以单独成类：队列只认通用任务接口，媒体转码的补救策略属于领域逻辑，放在这里可以让队列保持通用
public final class MediaJobTask implements Job.Task {

    // 媒体探测结果，命令构建需要它来决定是否需要缩放、如何估算码率
    private final MediaInfo info;

    // 构造方法：接收媒体信息
    public MediaJobTask(MediaInfo info) {
        // 保存媒体信息
        this.info = info;
    }

    // 执行任务：构建命令、运行、失败时按诊断结论做一次自动补救
    @Override
    public void execute(Job job, Job.Context context) throws Exception {
        // 取出该任务的转换参数
        ConversionRequest request = job.request();
        // 构建首次尝试的命令
        CommandBuilder.BuiltCommand command = CommandBuilder.build(request, info);
        // 记录即将执行的命令，便于用户复制排查
        context.log("命令：" + command.display());
        // 执行首次尝试并取得结果
        FfmpegRunner.RunResult result = runOnce(command, job, context);
        // 被取消时直接返回，队列会依据取消信号标记状态
        if (result.cancelled()) {
            // 记录取消日志
            context.log("转换已取消。");
            // 直接返回，不做补救
            return;
        }
        // 首次成功时记录产物信息并返回
        if (result.success()) {
            // 记录输出路径与体积
            applySuccess(job, context, result);
            // 直接返回，无需补救
            return;
        }
        // 首次失败时对错误做分类诊断，得到可读原因与建议
        ErrorClassifier.Diagnosis diagnosis = ErrorClassifier.classify(result.errorLines(), result.exitCode(),
                // 传入是否使用了硬件加速，便于把无文本的硬件失败也归类出去
                request.hardwareAcceleration());
        // 记录诊断结论，便于用户在日志中看到根因
        context.log("诊断：" + diagnosis.describe());
        // 把可读原因写入任务描述，界面会把它作为失败原因展示
        job.message(diagnosis.reason());
        // 尚未应用过补救时才允许补救，避免补救逻辑无限递归
        if (!request.remedyApplied()) {
            // 硬件加速失败时整套替换为软件编码器并重建命令后重试一次
            if (diagnosis.needsSoftwareFallback()) {
                // 记录补救动作
                context.log("补救：改用软件编码重建命令。");
                // 重试次数加一，便于界面展示尝试情况
                job.incrementAttempts();
                // 重建为纯软件命令并再次执行
                FfmpegRunner.RunResult retry = runOnce(CommandBuilder.buildSoftwareFallback(request, info), job, context);
                // 补救成功时记录产物信息并返回
                if (retry.success()) {
                    // 记录产物信息
                    applySuccess(job, context, retry);
                    // 输出补救成功日志
                    context.log("补救成功，已改用软件编码。");
                    // 直接返回
                    return;
                }
                // 补救被取消时直接返回
                if (retry.cancelled()) {
                    // 输出取消日志
                    context.log("补救过程中转换被取消。");
                    // 直接返回
                    return;
                }
                // 补救仍然失败时再次诊断，得到更贴近真实根因的结论
                ErrorClassifier.Diagnosis second = ErrorClassifier.classify(retry.errorLines(), retry.exitCode(), false);
                // 记录二次诊断结论
                context.log("补救后诊断：" + second.describe());
                // 写入任务描述
                job.message(second.reason());
                // 抛出异常由队列标记失败
                throw new IllegalStateException(second.reason());
            }
            // 奇数分辨率失败时在滤镜链末尾追加 pad 后重试一次
            if (diagnosis.needsOddResolutionRemedy()) {
                // 记录补救动作
                context.log("补救：追加补齐偶数滤镜后重试。");
                // 打开补齐偶数标记
                request.padToEven(true);
                // 标记已应用补救，阻止再次补救
                request.remedyApplied(true);
                // 重试次数加一
                job.incrementAttempts();
                // 用带 pad 的新命令再次执行
                FfmpegRunner.RunResult retry = runOnce(CommandBuilder.build(request, info), job, context);
                // 补救成功时记录产物信息并返回
                if (retry.success()) {
                    // 记录产物信息
                    applySuccess(job, context, retry);
                    // 输出补救成功日志
                    context.log("补救成功，已应用补齐偶数滤镜。");
                    // 直接返回
                    return;
                }
                // 补救被取消时直接返回
                if (retry.cancelled()) {
                    // 输出取消日志
                    context.log("补救过程中转换被取消。");
                    // 直接返回
                    return;
                }
                // 补救仍失败时再次诊断
                ErrorClassifier.Diagnosis second = ErrorClassifier.classify(retry.errorLines(), retry.exitCode(), false);
                // 记录二次诊断结论
                context.log("补救后诊断：" + second.describe());
                // 写入任务描述
                job.message(second.reason());
                // 抛出异常由队列标记失败
                throw new IllegalStateException(second.reason());
            }
        }
        // 无可补救或补救失败时抛出异常，把可读原因交给队列作为失败说明
        throw new IllegalStateException(diagnosis.reason());
    }

    // 执行一次命令并接驳进度回调，返回执行结果
    private FfmpegRunner.RunResult runOnce(CommandBuilder.BuiltCommand command, Job job, Job.Context context) {
        // 构造进度监听器，把 ffmpeg 的进度块换算成任务进度
        FfmpegRunner.Listener listener = new FfmpegRunner.Listener() {
            // 处理进度更新
            @Override
            public void onProgress(ProgressParser.ProgressUpdate update) {
                // 按总时长换算百分比，总时长未知时返回负值
                double percent = update.percentOf(job.durationSeconds());
                // 图片输出与时长未知场景没有百分比概念，直接用帧数上限兜底显示 100 之前的近似值
                if (percent < 0d) {
                    // 未结束时按 50 展示，结束后直接展示 100，避免进度条长期停在 0
                    percent = update.ended() ? 100d : 50d;
                }
                // 按总时长估算剩余秒数
                double remaining = update.remainingSeconds(job.durationSeconds());
                // 通过上下文上报进度、速度与剩余时间
                context.progress(percent, update.speed(), remaining);
            }

            // 处理日志行
            @Override
            public void onLog(String line) {
                // 转交上下文输出日志
                context.log(line);
            }
        };
        // 调用执行器运行命令，传入总时长、取消信号与监听器
        return FfmpegRunner.run(command, job.durationSeconds(), job.cancelSignal(), listener);
    }

    // 记录成功产物的路径与体积，并把进度置为 100
    private void applySuccess(Job job, Job.Context context, FfmpegRunner.RunResult result) {
        // 记录输出路径，供界面打开输出目录使用
        job.outputPath(result.outputPath());
        // 记录输出体积，供界面展示产物体积
        job.outputSize(result.outputSize());
        // 进度置为 100，表示任务已彻底完成
        job.percent(100d);
        // 速度在任务结束后不再有意义，清零避免界面显示误导数值
        job.speed(0d);
        // 剩余时间置为 0，表示不再需要等待
        job.remainingSeconds(0d);
        // 通过上下文输出成功日志
        context.log("产物：" + result.outputPath() + "（" + result.describe() + "）");
    }
}
