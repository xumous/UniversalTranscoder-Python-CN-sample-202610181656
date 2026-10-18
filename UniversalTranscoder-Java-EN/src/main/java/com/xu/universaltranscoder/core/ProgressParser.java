package com.xu.universaltranscoder.core;

// 引入 Locale，用于百分比与时间格式化时固定小数点符号

import java.util.Locale;

// 关键字 class：ProgressParser 解析 ffmpeg 的 -progress pipe:1 输出块
// 之所以要自己解析：进度块是若干 key=value 行的集合，只有读到 progress 行才算一块完整数据，直接按行处理会得到残缺状态
public final class ProgressParser {

    // 当前进度块中已累积的已输出时间（秒）
    private double currentOutTime;
    // 当前进度块中已累积的帧率
    private double currentFps;
    // 当前进度块中已累积的帧数
    private long currentFrame;
    // 当前进度块中已累积的速度倍率
    private double currentSpeed;
    // 当前进度块中已累积的已写入字节数
    private long currentTotalSize;
    // 当前进度块中已累积的运行时码率
    private double currentBitrate;

    // 解析整数值，遇到 N/A 等非数字文本时返回 0
    private static long parseLongValue(String text) {
        // 尝试按浮点解析再取整，兼容 12345.0 这类写法
        try {
            // 去掉可能的非数字后缀
            return (long) Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            // 解析失败时返回 0，界面会把它当作未知值
            return 0L;
        }
    }

    // 解析双精度数值，遇到 N/A 等非数字文本时返回 0
    private static double parseDoubleValue(String text) {
        // 捕获解析异常，保证脏数据不会中断整个进度流程
        try {
            // 直接解析双精度浮点
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            // 解析失败时返回 0
            return 0d;
        }
    }

    // 解析 speed 文本，形如 2.5x 或 N/A
    private static double parseSpeedText(String text) {
        // 非数值文本原样返回 0
        if (text == null || text.isEmpty()) {
            // 返回 0 表示速度未知
            return 0d;
        }
        // 去掉末尾的 x 后缀再解析
        String cleaned = text.endsWith("x") || text.endsWith("X")
                // 截掉最后一个字符
                ? text.substring(0, text.length() - 1)
                // 无需清理时原样使用
                : text;
        // 复用双精度解析方法
        return parseDoubleValue(cleaned);
    }

    // 解析 bitrate 文本，形如 1234.5kbits/s 或 N/A
    private static double parseBitrateText(String text) {
        // 非数值文本返回 0
        if (text == null || text.isEmpty()) {
            // 返回 0 表示码率未知
            return 0d;
        }
        // 去掉 kbits/s 后缀，只保留数字部分
        int index = text.toLowerCase(Locale.ROOT).indexOf("kbit");
        // 截取数字部分
        String numeric = index > 0 ? text.substring(0, index) : text;
        // 复用双精度解析方法
        return parseDoubleValue(numeric.trim());
    }

    // 解析 HH:MM:SS.ffffff 形式的时间文本为秒数
    private static double parseTimeText(String text) {
        // 文本为空时返回 0
        if (text == null || text.isEmpty()) {
            // 返回 0 表示时间未知
            return 0d;
        }
        // 按冒号切分时分秒
        String[] parts = text.split(":");
        // 段数不是三时无法解析，返回 0
        if (parts.length != 3) {
            // 返回 0 表示时间未知
            return 0d;
        }
        // 捕获解析异常
        try {
            // 解析小时
            double hours = Double.parseDouble(parts[0]);
            // 解析分钟
            double minutes = Double.parseDouble(parts[1]);
            // 解析带小数的秒
            double seconds = Double.parseDouble(parts[2]);
            // 换算成总秒数
            return hours * 3600d + minutes * 60d + seconds;
        } catch (NumberFormatException ex) {
            // 解析失败时返回 0
            return 0d;
        }
    }

    // 处理一行 key=value 输出，读到 progress 行时向回调派发一个完整进度块
    public void feed(String line, Sink sink) {
        // 空行直接忽略，避免解析出无意义的数据
        if (line == null || line.trim().isEmpty()) {
            // 直接返回等待下一行
            return;
        }
        // 去掉首尾空白，ffmpeg 输出中可能夹带空白字符
        String trimmed = line.trim();
        // 查找等号位置，没有等号说明不是 key=value 行
        int equals = trimmed.indexOf('=');
        // 无等号时忽略该行
        if (equals <= 0) {
            // 直接返回等待下一行
            return;
        }
        // 截取键名并转小写，兼容大小写差异
        String key = trimmed.substring(0, equals).trim().toLowerCase(Locale.ROOT);
        // 截取值文本
        String value = trimmed.substring(equals + 1).trim();
        // 按键名分派到对应的字段，未知键忽略即可保证前向兼容
        switch (key) {
            // out_time_us 以微秒给出已输出媒体时间，是最精确的时间来源
            case "out_time_us" -> currentOutTime = parseLongValue(value) / 1_000_000d;
            // out_time_ms 在 ffmpeg 中同样以微秒为单位，作为 out_time_us 缺失时的补充
            case "out_time_ms" -> {
                // 仅在 out_time_us 尚未提供有效值时使用它，避免单位误解造成时间翻倍
                if (currentOutTime <= 0d) {
                    // 按微秒换算成秒
                    currentOutTime = parseLongValue(value) / 1_000_000d;
                }
            }
            // out_time 以 HH:MM:SS.ffffff 文本给出时间，作为最后兜底
            case "out_time" -> {
                // 仅在前两个键都没给出有效值时使用文本时间
                if (currentOutTime <= 0d) {
                    // 解析时间文本为秒数
                    currentOutTime = parseTimeText(value);
                }
            }
            // fps 给出当前编码帧率
            case "fps" -> currentFps = parseDoubleValue(value);
            // frame 给出已编码帧数
            case "frame" -> currentFrame = (long) parseDoubleValue(value);
            // speed 给出处理速度倍数，文本形式为 2.5x 或 N/A
            case "speed" -> currentSpeed = parseSpeedText(value);
            // total_size 给出已写入字节数
            case "total_size" -> currentTotalSize = parseLongValue(value);
            // bitrate 给出运行时码率，文本形式为 1234.5kbits/s 或 N/A
            case "bitrate" -> currentBitrate = parseBitrateText(value);
            // progress 是块结束标记，取值 continue 或 end
            case "progress" -> {
                // 判断本块是否表示流程结束
                boolean ended = "end".equalsIgnoreCase(value);
                // 构造完整进度对象并派发给回调
                sink.onUpdate(new ProgressUpdate(currentOutTime, currentFps, currentFrame, currentSpeed,
                        // 传入已写入字节数
                        currentTotalSize,
                        // 传入运行时码率
                        currentBitrate,
                        // 传入结束标记
                        ended));
                // 块派发完成后重置累加字段，避免上一块数据污染下一块
                reset();
            }
            // 未知键直接忽略，保证对不同 ffmpeg 版本的输出差异具备兼容性
            default -> {
                // 空实现，无需处理
            }
        }
    }

    // 重置累加字段，准备接收下一个进度块
    private void reset() {
        // 已输出时间清零
        currentOutTime = 0d;
        // 帧率清零
        currentFps = 0d;
        // 帧数清零
        currentFrame = 0L;
        // 速度清零
        currentSpeed = 0d;
        // 已写入字节数清零
        currentTotalSize = 0L;
        // 运行时码率清零
        currentBitrate = 0d;
    }

    // 进度上报的回调接口，执行器在解析出完整进度块后调用它
    public interface Sink {
        // 处理一次完整进度更新
        void onUpdate(ProgressUpdate update);
    }

    // 静态嵌套类 ProgressUpdate：承载一个进度块解析出的全部指标
    public static final class ProgressUpdate {

        // 已输出的媒体时间（秒），这是进度百分比计算的核心依据
        private final double outTimeSeconds;
        // 当前编码帧率，用于展示编码速度是否正常
        private final double fps;
        // 已编码帧数，用于图片与短视频场景展示具体进度
        private final long frame;
        // 处理速度相对于播放时长的倍率，例如 2.5 表示 2.5 倍速
        private final double speed;
        // 已写入的总字节数，用于展示产物体积
        private final long totalSize;
        // 运行时的总码率（千比特每秒），用于判断码率是否落在预期区间
        private final double bitrateKbps;
        // 是否已经收到结束标记，为真时表示转码流程即将收尾
        private final boolean ended;

        // 构造方法：接收一个进度块解析出的全部指标
        ProgressUpdate(double outTimeSeconds, double fps, long frame, double speed, long totalSize,
                       double bitrateKbps, boolean ended) {
            // 保存已输出媒体时间
            this.outTimeSeconds = outTimeSeconds;
            // 保存帧率
            this.fps = fps;
            // 保存帧数
            this.frame = frame;
            // 保存速度倍率
            this.speed = speed;
            // 保存已写入字节数
            this.totalSize = totalSize;
            // 保存运行时码率
            this.bitrateKbps = bitrateKbps;
            // 保存结束标记
            this.ended = ended;
        }

        // 获取已输出的媒体时间
        public double outTimeSeconds() {
            // 返回已输出时间
            return outTimeSeconds;
        }

        // 获取当前编码帧率
        public double fps() {
            // 返回帧率
            return fps;
        }

        // 获取已编码帧数
        public long frame() {
            // 返回帧数
            return frame;
        }

        // 获取处理速度倍率
        public double speed() {
            // 返回速度倍率
            return speed;
        }

        // 获取已写入字节数
        public long totalSize() {
            // 返回字节数
            return totalSize;
        }

        // 获取运行时码率
        public double bitrateKbps() {
            // 返回码率
            return bitrateKbps;
        }

        // 判断是否已经结束
        public boolean ended() {
            // 返回结束标记
            return ended;
        }

        // 按总时长换算百分比，总时长不可用时返回 -1 表示无法给出百分比
        public double percentOf(double totalDurationSeconds) {
            // 总时长非法时无法换算百分比
            if (totalDurationSeconds <= 0d) {
                // 返回负值表示百分比不可用
                return -1d;
            }
            // 按已输出时间与总时长的比值换算百分比
            double percent = outTimeSeconds / totalDurationSeconds * 100d;
            // 百分比钳制在 0 至 100 之间，避免首尾帧抖动导致界面显示越界数值
            return Math.min(Math.max(percent, 0d), 100d);
        }

        // 按总时长估算剩余秒数，无法估算时返回 -1
        public double remainingSeconds(double totalDurationSeconds) {
            // 总时长非法或速度不可用时无法估算
            if (totalDurationSeconds <= 0d || speed <= 0d || !Double.isFinite(speed)) {
                // 返回负值表示剩余时间不可用
                return -1d;
            }
            // 用剩余媒体时长除以处理速度得到剩余真实秒数
            double remainingMedia = Math.max(totalDurationSeconds - outTimeSeconds, 0d);
            // 返回按速度折算后的剩余秒数
            return remainingMedia / speed;
        }

        // 生成一行可读的进度摘要，供日志与状态标签展示
        public String describe(double totalDurationSeconds) {
            // 计算百分比
            double percent = percentOf(totalDurationSeconds);
            // 使用 StringBuilder 组织文本
            StringBuilder builder = new StringBuilder();
            // 百分比可用时追加百分比文本
            builder.append(percent >= 0d ? String.format(Locale.ROOT, "%.1f%%", percent) : "n/a");
            // 追加帧率与帧数
            builder.append(" | fps=").append(String.format(Locale.ROOT, "%.2f", fps));
            // 追加已编码帧数
            builder.append(" | frame=").append(frame);
            // 追加速度倍率
            builder.append(" | speed=").append(String.format(Locale.ROOT, "%.2f", speed)).append('x');
            // 追加已写入体积，按兆字节展示便于阅读
            builder.append(" | size=").append(String.format(Locale.ROOT, "%.2f", totalSize / 1048576d)).append("MB");
            // 返回拼好的摘要文本
            return builder.toString();
        }
    }
}
