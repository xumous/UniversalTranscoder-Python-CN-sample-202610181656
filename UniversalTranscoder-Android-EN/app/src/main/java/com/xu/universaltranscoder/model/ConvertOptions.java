package com.xu.universaltranscoder.model;

/**
 * ConvertOptions 汇总一次转换的全部可调参数。
 * 把所有参数集中到一个对象里，好处是：界面套用预设时只需替换一个对象；任务去重时只需比较一个对象；
 * 转换引擎读取参数时也不需要一长串方法参数。
 */
public final class ConvertOptions {

    // 选项的默认值常量：这些数值是经过权衡的“安全默认”，避免默认参数就触发高端设备的编码器拒绝。
    public static final int DEFAULT_VIDEO_BITRATE_KBPS = 4000;
    // 默认视频帧率：30fps 是绝大多数设备都接受的取值。
    public static final int DEFAULT_VIDEO_FPS = 30;
    // 默认图片质量：JPEG/WEBP 常用 90，肉眼几乎无损。
    public static final int DEFAULT_IMAGE_QUALITY = 90;
    // 默认音频码率：128kbps 是 AAC 的常见平衡点。
    public static final int DEFAULT_AUDIO_BITRATE_KBPS = 128;
    // 默认音频采样率：44100Hz 兼容性最好。
    public static final int DEFAULT_SAMPLE_RATE = 44100;
    // 默认音频声道数：2（立体声）。
    public static final int DEFAULT_CHANNELS = 2;
    // 默认抽帧数量：均匀取 5 帧。
    public static final int DEFAULT_FRAME_COUNT = 5;

    // 视频码率（kbps）。
    public int videoBitrateKbps = DEFAULT_VIDEO_BITRATE_KBPS;
    // 视频帧率（fps）。
    public int videoFrameRate = DEFAULT_VIDEO_FPS;
    // 目标视频宽度；0 表示不缩放，沿用源分辨率。
    public int targetWidth = 0;
    // 目标视频高度；0 表示不缩放，沿用源分辨率。
    public int targetHeight = 0;
    // 图片质量（1..100）。
    public int imageQuality = DEFAULT_IMAGE_QUALITY;
    // 图片目标宽度；0 表示不缩放。
    public int imageTargetWidth = 0;
    // 图片目标高度；0 表示不缩放。
    public int imageTargetHeight = 0;
    // 相对缩放百分比（1..1000）；大于 0 时优先于显式宽高，用于支持 "50%" 这类缩放表达式。
    public int scalePercent = 0;
    // 音频码率（kbps）。
    public int audioBitrateKbps = DEFAULT_AUDIO_BITRATE_KBPS;
    // 音频采样率（Hz）。
    public int sampleRate = DEFAULT_SAMPLE_RATE;
    // 音频声道数。
    public int channels = DEFAULT_CHANNELS;
    // 是否保留源视频轨（重封装时可选择只取音频）。
    public boolean keepVideo = true;
    // 是否保留源音频轨。
    public boolean keepAudio = true;
    // PDF 渲染页码表达式，例如 "1-3,5"；空字符串表示全部页。
    public String pageRange = "";
    // 抽帧数量（均匀抽帧模式）。
    public int frameCount = DEFAULT_FRAME_COUNT;
    // 抽帧指定时间点（秒），非空时优先于 frameCount。
    public String frameTimesSeconds = "";
    // PDF 页面尺寸策略：true 表示按图片比例自适应，false 表示统一用 A4。
    public boolean pdfAutoPageSize = true;
    // 文本转 PDF 使用的字号（pt）。
    public int pdfTextSize = 12;
    // 套用的预设名称，仅用于界面展示与日志。
    public String presetName = "";

    /**
     * 构造一份默认参数。
     */
    public ConvertOptions() {
        // 所有字段已由声明处的初值完成初始化，这里无需额外动作。
    }

    /**
     * 深拷贝当前参数，供预设套用时生成独立副本，避免后续修改互相影响。
     */
    public ConvertOptions copy() {
        // 新建目标对象。
        ConvertOptions clone = new ConvertOptions();
        // 复制视频码率。
        clone.videoBitrateKbps = videoBitrateKbps;
        // 复制视频帧率。
        clone.videoFrameRate = videoFrameRate;
        // 复制目标宽度。
        clone.targetWidth = targetWidth;
        // 复制目标高度。
        clone.targetHeight = targetHeight;
        // 复制图片质量。
        clone.imageQuality = imageQuality;
        // 复制图片目标宽度。
        clone.imageTargetWidth = imageTargetWidth;
        // 复制图片目标高度。
        clone.imageTargetHeight = imageTargetHeight;
        // 复制相对缩放百分比。
        clone.scalePercent = scalePercent;
        // 复制音频码率。
        clone.audioBitrateKbps = audioBitrateKbps;
        // 复制采样率。
        clone.sampleRate = sampleRate;
        // 复制声道数。
        clone.channels = channels;
        // 复制保留视频轨标记。
        clone.keepVideo = keepVideo;
        // 复制保留音频轨标记。
        clone.keepAudio = keepAudio;
        // 复制页码范围。
        clone.pageRange = pageRange;
        // 复制抽帧数量。
        clone.frameCount = frameCount;
        // 复制指定时间点。
        clone.frameTimesSeconds = frameTimesSeconds;
        // 复制 PDF 页面尺寸策略。
        clone.pdfAutoPageSize = pdfAutoPageSize;
        // 复制文本字号。
        clone.pdfTextSize = pdfTextSize;
        // 复制预设名称。
        clone.presetName = presetName;
        // 返回副本。
        return clone;
    }

    /**
     * 生成参数摘要文本，写入日志与队列条目，便于复现问题。
     */
    public String describe() {
        // 使用 StringBuilder 拼接。
        StringBuilder builder = new StringBuilder();
        // 若指定了预设则先输出预设名。
        if (!presetName.isEmpty()) {
            // 追加预设名。
            builder.append("preset=").append(presetName).append(' ');
        }
        // 输出视频码率与帧率。
        builder.append("vbitrate=").append(videoBitrateKbps).append("kbps ");
        // 输出帧率。
        builder.append("fps=").append(videoFrameRate).append(' ');
        // 输出目标分辨率（0x0 表示沿用源）。
        builder.append("size=").append(targetWidth).append('x').append(targetHeight).append(' ');
        // 输出图片质量。
        builder.append("quality=").append(imageQuality).append(' ');
        // 输出图片目标尺寸。
        builder.append("imgSize=").append(imageTargetWidth).append('x').append(imageTargetHeight).append(' ');
        // 输出相对缩放百分比（0 表示未启用）。
        builder.append("scale%=").append(scalePercent).append(' ');
        // 输出音频参数。
        builder.append("abitrate=").append(audioBitrateKbps).append("kbps ");
        // 输出采样率。
        builder.append("rate=").append(sampleRate).append("Hz ");
        // 输出声道数。
        builder.append("ch=").append(channels).append(' ');
        // 输出页码范围。
        if (!pageRange.isEmpty()) {
            // 追加页码范围。
            builder.append("pages=").append(pageRange).append(' ');
        }
        // 输出抽帧参数。
        if (!frameTimesSeconds.isEmpty()) {
            // 指定时间点模式。
            builder.append("framesAt=").append(frameTimesSeconds).append(' ');
        } else {
            // 均匀抽帧模式。
            builder.append("frames=").append(frameCount).append(' ');
        }
        // 返回摘要文本。
        return builder.toString();
    }
}
