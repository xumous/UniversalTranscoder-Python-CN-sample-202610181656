package com.xu.universaltranscoder.core;

// 引入 Files，用于检查样例文件与产物是否存在

import java.nio.file.Files;
// 引入 Path，表示样例与输出路径
import java.nio.file.Path;
// 引入 IOException，处理临时目录创建异常
import java.io.IOException;
// 引入 List 接口，用于读取任务快照
import java.util.List;
// 引入 AtomicBoolean，为自检任务提供取消信号
import java.util.concurrent.atomic.AtomicBoolean;

// 关键字 class：SelfTest 提供无人值守的自检流程，包含内核自检与一次真实转码
// 之所以提供自检：打包后的可执行文件无法靠人工点界面验证，自检可以给出可复现的成功或失败结论
public final class SelfTest {

    // 私有构造方法，禁止实例化这个纯静态自检类
    private SelfTest() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 运行自检，返回进程退出码：0 表示全部通过，1 表示存在失败项
    public static int run() {
        // 打印自检标题，便于在日志中定位
        System.out.println("=== Universal Transcoder self test ===");
        // 声明总体通过标记，任何一步失败都会把它置为 false
        boolean passed = true;
        // 第一步：内核定位与能力探测自检
        passed &= stepCoreCheck();
        // 第二步：真实转码自检
        passed &= stepRealTranscode();
        // 第三步：图片转换自检，覆盖“图片目标独立映射 + 单帧写出”这条分支
        passed &= stepImageConversion();
        // 打印总体结论
        System.out.println("=== Result: " + (passed ? "PASS" : "FAIL") + " ===");
        // 返回退出码，调用方据此判断自检是否通过
        return passed ? 0 : 1;
    }

    // 内核自检：校验内核路径、版本横幅与能力清单是否可用
    private static boolean stepCoreCheck() {
        // 打印步骤标题
        System.out.println("[1/3] Core check");
        // 执行一次内核定位与能力探测
        Toolchain toolchain = Toolchain.bootstrap(null);
        // 内核整体可用性
        boolean available = toolchain.isAvailable();
        // 打印内核概述，包含路径与能力数量
        System.out.println("  " + toolchain.describe());
        // 内核不可用时直接判定失败
        if (!available) {
            // 打印失败原因
            System.out.println("  FAIL: FFmpeg core is not available.");
            // 返回失败
            return false;
        }
        // 打印 ffmpeg 版本横幅
        System.out.println("  ffmpeg : " + toolchain.ffmpegVersion());
        // 打印 ffprobe 版本横幅
        System.out.println("  ffprobe: " + toolchain.ffprobeVersion());
        // 打印硬件加速能力摘要
        System.out.println("  hardware: " + HardwareAccel.describe());
        // 校验几个关键编码器是否存在，缺失说明能力探测或内核不完整
        String[] requiredEncoders = {"libx264", "aac", "mjpeg", "png", "libmp3lame"};
        // 本步骤的通过标记，任一编码器缺失都会置为失败
        boolean encodersPresent = true;
        // 逐个校验
        for (String encoder : requiredEncoders) {
            // 取是否存在标记
            boolean exists = toolchain.hasEncoder(encoder);
            // 打印校验结果
            System.out.println("  encoder " + encoder + ": " + (exists ? "OK" : "MISSING"));
            // 缺失时把本步骤结果置为失败
            if (!exists) {
                // 标记失败
                encodersPresent = false;
            }
        }
        // 校验 ffprobe 是否定位成功，它是媒体探测的前提
        if (toolchain.ffprobe() == null) {
            // 打印失败原因
            System.out.println("  FAIL: ffprobe was not located.");
            // 返回失败
            return false;
        }
        // 校验封装清单是否包含常用封装
        System.out.println("  muxer mp4: " + (toolchain.hasFormat("mp4") ? "OK" : "MISSING"));
        // 返回本步骤结果，编码器缺失或封装缺失都会返回失败
        return encodersPresent && toolchain.hasFormat("mp4");
    }

    // 真实转码自检：把样例视频转成 mp4 并校验产物存在且非空
    private static boolean stepRealTranscode() {
        // 打印步骤标题
        System.out.println("[2/3] Real transcode (video to MP4)");
        // 定位样例目录
        Path samples = findSamplesDirectory();
        // 样例目录缺失时判定失败，因为无法完成真实转码验证
        if (samples == null) {
            // 打印失败原因
            System.out.println("  FAIL: samples directory not found.");
            // 返回失败
            return false;
        }
        // 定位样例视频文件
        Path source = samples.resolve("test_video.mp4");
        // 样例文件不存在时判定失败
        if (!Files.isRegularFile(source)) {
            // 打印失败原因
            System.out.println("  FAIL: " + source + " not found.");
            // 返回失败
            return false;
        }
        // 创建本次自检的输出目录，使用临时目录避免污染仓库
        Path outputDir;
        // 捕获临时目录创建异常
        try {
            // 创建带前缀的临时目录，便于人工查找产物
            outputDir = Files.createTempDirectory("universal-transcoder-selftest");
        } catch (IOException ex) {
            // 无法创建输出目录时判定失败
            System.out.println("  FAIL: cannot create output directory: " + ex.getMessage());
            // 返回失败
            return false;
        }
        // 打印输出目录，便于人工核对产物位置
        System.out.println("  output dir: " + outputDir);
        // 先用 ffprobe 探测源文件，验证媒体探测链路
        MediaInfo info = MediaProbe.probe(source);
        // 探测无效时判定失败
        if (!info.valid()) {
            // 打印失败原因
            System.out.println("  FAIL: media probe returned no data.");
            // 返回失败
            return false;
        }
        // 打印探测摘要，包含时长与分辨率
        System.out.println("  source: " + info.describe());
        // 构造转换参数：视频类别转 mp4，默认中档质量，保持原始分辨率
        ConversionRequest request = new ConversionRequest()
                // 设置输入文件
                .input(source)
                // 类别为视频
                .category(ConversionRequest.Category.VIDEO)
                // 目标格式为 mp4
                .targetExtension("mp4")
                // 输出目录为临时目录
                .outputDirectory(outputDir)
                // 命名模式为替换扩展名
                .namingMode(ConversionRequest.NamingMode.REPLACE_EXTENSION)
                // 质量档位为中档
                .quality(Quality.MEDIUM)
                // 分辨率保持原始
                .width(0)
                // 高度保持原始
                .height(0)
                // 帧率保持原始
                .frameRate(0d)
                // 元数据保留
                .metadataMode("keep")
                // 关闭硬件加速，保证自检在任意机器上都可复现
                .hardwareAcceleration(false);
        // 构建命令
        CommandBuilder.BuiltCommand command = CommandBuilder.build(request, info);
        // 打印命令文本，便于人工复核参数
        System.out.println("  command: " + command.display());
        // 创建取消信号，自检场景不会触发取消
        AtomicBoolean cancelSignal = new AtomicBoolean(false);
        // 构造日志监听器，把 ffmpeg 的错误输出实时打印出来
        FfmpegRunner.Listener listener = new FfmpegRunner.Listener() {
            // 进度块只打印结尾信息，避免刷屏
            @Override
            public void onProgress(ProgressParser.ProgressUpdate update) {
                // 仅在流程结束时打印一行汇总
                if (update.ended()) {
                    // 打印最终进度摘要
                    System.out.println("  progress: " + update.describe(info.durationSeconds()));
                }
            }

            // 错误输出逐行打印，便于定位失败根因
            @Override
            public void onLog(String line) {
                // 打印内核输出行
                System.out.println("  [ffmpeg] " + line);
            }
        };
        // 执行转码
        FfmpegRunner.RunResult result = FfmpegRunner.run(command, info.durationSeconds(), cancelSignal, listener);
        // 打印执行结果摘要
        System.out.println("  result: " + result.describe());
        // 校验产物存在且体积大于 0
        boolean ok = result.success() && result.outputSize() > 0L;
        // 打印产物路径与体积
        System.out.println("  output: " + result.outputPath() + " | size=" + result.outputSize() + " bytes");
        // 产物异常时对错误做分类，输出可读原因便于排查
        if (!ok) {
            // 执行错误分类
            ErrorClassifier.Diagnosis diagnosis = ErrorClassifier.classify(result.errorLines(), result.exitCode(), false);
            // 打印诊断结论
            System.out.println("  diagnosis: " + diagnosis.describe());
        }
        // 返回本步骤结果
        return ok;
    }

    // 图片转换自检：把样例图片转成 JPG，覆盖图片目标的编码器映射与单帧写出分支
    private static boolean stepImageConversion() {
        // 打印步骤标题
        System.out.println("[3/3] Real transcode (image to JPG)");
        // 定位样例目录
        Path samples = findSamplesDirectory();
        // 样例目录缺失时无法验证
        if (samples == null) {
            // 打印失败原因
            System.out.println("  FAIL: samples directory not found.");
            // 返回失败
            return false;
        }
        // 定位样例图片
        Path source = samples.resolve("test_image.png");
        // 样例文件不存在时无法验证
        if (!Files.isRegularFile(source)) {
            // 打印提示并返回失败
            System.out.println("  FAIL: " + source + " not found.");
            // 返回失败
            return false;
        }
        // 创建输出目录
        Path outputDir;
        // 捕获临时目录创建异常
        try {
            // 创建临时目录
            outputDir = Files.createTempDirectory("universal-transcoder-selftest-image");
        } catch (IOException ex) {
            // 无法创建时判定失败
            System.out.println("  FAIL: cannot create output directory: " + ex.getMessage());
            // 返回失败
            return false;
        }
        // 探测图片信息，虽然图片信息有限但仍可验证探测链路
        MediaInfo info = MediaProbe.probe(source);
        // 构造图片转换参数
        ConversionRequest request = new ConversionRequest()
                // 设置输入图片
                .input(source)
                // 类别为图片
                .category(ConversionRequest.Category.IMAGE)
                // 目标格式为 jpg
                .targetExtension("jpg")
                // 输出目录为临时目录
                .outputDirectory(outputDir)
                // 命名模式为替换扩展名
                .namingMode(ConversionRequest.NamingMode.REPLACE_EXTENSION)
                // 质量档位为高档
                .quality(Quality.HIGH)
                // 关闭硬件加速
                .hardwareAcceleration(false);
        // 构建命令
        CommandBuilder.BuiltCommand command = CommandBuilder.build(request, info);
        // 打印命令文本
        System.out.println("  command: " + command.display());
        // 校验命令是否包含图片专用参数，缺失说明图片分支没有被正确编译
        boolean hasFrameLimit = command.arguments().contains("-frames:v");
        // 校验是否包含单文件更新参数
        boolean hasUpdate = command.arguments().contains("-update");
        // 打印参数校验结果
        System.out.println("  image args: -frames:v=" + hasFrameLimit + ", -update=" + hasUpdate);
        // 执行转换
        FfmpegRunner.RunResult result = FfmpegRunner.run(command, 0d, new AtomicBoolean(false), null);
        // 打印执行结果与产物信息
        System.out.println("  output: " + result.outputPath() + " | size=" + result.outputSize() + " bytes | " + result.describe());
        // 三者同时满足才算通过：命令含图片参数、转码成功、产物非空
        boolean ok = hasFrameLimit && hasUpdate && result.success() && result.outputSize() > 0L;
        // 未通过时输出诊断结论
        if (!ok) {
            // 执行错误分类
            ErrorClassifier.Diagnosis diagnosis = ErrorClassifier.classify(result.errorLines(), result.exitCode(), false);
            // 打印诊断结论
            System.out.println("  diagnosis: " + diagnosis.describe());
        }
        // 返回本步骤结果
        return ok;
    }

    // 定位样例目录：按多个候选路径依次尝试，覆盖开发态与打包后的不同工作目录
    public static Path findSamplesDirectory() {
        // 优先读取系统属性覆盖值，便于自动化脚本指定样例位置
        String override = System.getProperty("ut.samples");
        // 覆盖值有效且是目录时直接采用
        if (override != null && !override.isEmpty()) {
            // 转成路径
            Path candidate = Path.of(override);
            // 是目录时返回
            if (Files.isDirectory(candidate)) {
                // 返回覆盖路径
                return candidate;
            }
        }
        // 取当前工作目录
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        // 构造候选路径列表：同级与上级目录中的 Samples
        Path[] candidates = {
                // 工程目录同级的 Samples
                workingDir.resolve("..").resolve("Samples").normalize(),
                // 工作目录下的 Samples
                workingDir.resolve("Samples"),
                // 上两级目录下的 Samples，覆盖在更深目录中启动的情况
                workingDir.resolve("..").resolve("..").resolve("Samples").normalize(),
                // 三级目录下的 Samples，兜底覆盖
                workingDir.resolve("..").resolve("..").resolve("..").resolve("Samples").normalize()
        };
        // 逐个候选检查
        for (Path candidate : candidates) {
            // 命中目录即返回
            if (Files.isDirectory(candidate)) {
                // 返回样例目录
                return candidate;
            }
        }
        // 全部候选都不存在时返回 null
        return null;
    }

    // 在执行结果中查找指定状态的任务数量，供自检之外的自动化脚本统计使用
    public static int countStatus(List<Job> jobs, Job.Status status) {
        // 计数器从 0 开始
        int count = 0;
        // 遍历任务列表
        for (Job job : jobs) {
            // 状态匹配时累加
            if (job.status() == status) {
                // 累加计数
                count++;
            }
        }
        // 返回统计结果
        return count;
    }
}
