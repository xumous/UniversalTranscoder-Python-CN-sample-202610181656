package com.xu.universaltranscoder.core;

// 引入 ArrayList，构造命令行参数

import java.util.ArrayList;
// 引入 Files，判断输出产物是否存在并读取体积
import java.nio.file.Files;
// 引入 IOException，处理文件属性读取异常
import java.io.IOException;
// 引入 Path，表示输入视频与输出目录
import java.nio.file.Path;
// 引入 List 接口，作为参数列表类型
import java.util.List;
// 引入 AtomicBoolean，跨帧传递取消信号
import java.util.concurrent.atomic.AtomicBoolean;

// 关键字 class：FrameExtractor 调用 ffmpeg 在若干指定时间点抽取视频帧为 PNG 图片
// 之所以逐时间点单独调用 ffmpeg：-ss 放在 -i 之前可以做到关键帧精确定位且只解码必要片段，速度远快于整条流解码
public final class FrameExtractor {

    // 私有构造方法，禁止实例化这个纯静态抽取器
    private FrameExtractor() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 按给定时间点抽帧，返回成功写出的图片数量
    public static int extractFrames(Path input, Path outputDirectory, double[] timestamps, String baseName,
                                    AtomicBoolean cancelSignal, FfmpegRunner.Listener listener) throws IOException {
        // 时间点数组为空时无法抽帧
        if (timestamps == null || timestamps.length == 0) {
            // 返回 0 表示没有产物
            return 0;
        }
        // 确保输出目录存在
        Files.createDirectories(outputDirectory);
        // 取得内核对象，需要 ffmpeg 路径
        Toolchain toolchain = Toolchain.get();
        // 内核不可用时无法抽帧
        if (toolchain.ffmpeg() == null) {
            // 抛出异常由任务层给出可读原因
            throw new IOException("FFmpeg core is not available.");
        }
        // 成功计数
        int written = 0;
        // 逐个时间点抽帧
        for (int i = 0; i < timestamps.length; i++) {
            // 用户取消时立即停止，避免继续创建无意义的进程
            if (cancelSignal != null && cancelSignal.get()) {
                // 跳出循环
                break;
            }
            // 取当前时间点，负值归零
            double timestamp = Math.max(timestamps[i], 0d);
            // 构造该帧的输出文件名，使用帧序号而不是时间点，避免小数点进入文件名
            String frameName = baseName + "-frame" + (i + 1) + ".png";
            // 构造输出路径
            Path outputFile = outputDirectory.resolve(frameName);
            // 构造 ffmpeg 参数列表
            List<String> args = new ArrayList<>();
            // 首个参数是 ffmpeg 绝对路径
            args.add(toolchain.ffmpeg().toString());
            // 关闭版本横幅
            args.add("-hide_banner");
            // 关闭标准输入读取
            args.add("-nostdin");
            // 允许覆盖已有文件，因为抽帧产物是可重复生成的
            args.add("-y");
            // 精确到指定时间点，该参数必须放在 -i 之前才能触发快速定位
            args.add("-ss");
            // 追加秒级时间文本，保留三位小数足以覆盖常见的定位精度需求
            args.add(String.format(java.util.Locale.ROOT, "%.3f", timestamp));
            // 指定输入文件
            args.add("-i");
            // 追加输入绝对路径
            args.add(input.toAbsolutePath().toString());
            // 只输出一帧画面
            args.add("-frames:v");
            // 帧数为 1
            args.add("1");
            // 启用单文件更新模式，保证多帧场景下写到同一路径
            args.add("-update");
            // 打开单文件更新
            args.add("1");
            // 关闭统计行
            args.add("-nostats");
            // 只输出错误日志
            args.add("-loglevel");
            // 指定级别
            args.add("error");
            // 显式指定 image2 封装，保证写出单张 PNG
            args.add("-f");
            // 追加封装名
            args.add("image2");
            // 追加输出文件绝对路径
            args.add(outputFile.toAbsolutePath().toString());
            // 构造命令对象，工作目录留空使用默认值
            CommandBuilder.BuiltCommand command = new CommandBuilder.BuiltCommand(args, outputFile, null, "png", null, "image2");
            // 输出命令日志便于排查
            if (listener != null) {
                // 记录即将执行的抽帧命令
                listener.onLog("Extract frame " + (i + 1) + ": " + command.display());
            }
            // 执行抽帧命令，进度意义不大因此传入 0 作为总时长
            FfmpegRunner.RunResult result = FfmpegRunner.run(command, 0d, cancelSignal, listener);
            // 判断产物是否存在且非空，以此决定是否计入成功
            if (result.success() && sizeOf(outputFile) > 0L) {
                // 成功计数加一
                written++;
            } else if (listener != null) {
                // 记录失败原因，便于用户定位是哪个时间点抽帧失败
                listener.onLog("Frame " + (i + 1) + " failed: " + result.describe());
            }
        }
        // 返回成功写出的图片数量
        return written;
    }

    // 生成若干均匀分布的时间点，用于用户只给出数量时自动挑选抽帧位置
    public static double[] evenlySpacedTimestamps(double durationSeconds, int count) {
        // 抽帧数量至少为 1
        int safeCount = Math.max(count, 1);
        // 时长为 0 或不合法时全部时间点归零，抽取视频开头的帧
        if (durationSeconds <= 0d || !Double.isFinite(durationSeconds)) {
            // 构造全零时间点数组
            double[] zeros = new double[safeCount];
            // 返回该数组
            return zeros;
        }
        // 构造结果数组
        double[] result = new double[safeCount];
        // 把时间轴等分成 safeCount 段，取每段的中点作为抽帧位置，避免取到首尾黑帧
        for (int i = 0; i < safeCount; i++) {
            // 计算区间中点时刻
            result[i] = durationSeconds * (i + 0.5d) / safeCount;
        }
        // 返回时间点数组
        return result;
    }

    // 读取文件体积，失败时返回 0
    private static long sizeOf(Path path) {
        // 捕获属性读取异常
        try {
            // 文件存在时返回字节数
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ex) {
            // 读取失败返回 0
            return 0L;
        }
    }
}
