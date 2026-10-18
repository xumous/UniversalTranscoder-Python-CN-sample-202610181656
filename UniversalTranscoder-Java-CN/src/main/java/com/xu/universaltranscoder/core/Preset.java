package com.xu.universaltranscoder.core;

// 关键字 class：Preset 表示一套可一键套用的转换参数，不包含输入输出路径
// 之所以把预设建模成“可作用于请求的参数集合”：预设只需要填充编码相关字段，路径由用户在界面上另行选择
public final class Preset {

    // 预设名称，会显示给用户因此使用英文
    private final String name;
    // 预设所属类别，决定它出现在哪个标签页
    private final ConversionRequest.Category category;
    // 目标格式扩展名
    private final String targetExtension;
    // 质量档位
    private final Quality quality;
    // 目标宽度，0 表示保持原始宽度
    private final int width;
    // 目标高度，0 表示保持原始高度
    private final int height;
    // 目标帧率，0 表示保持原始帧率
    private final double frameRate;
    // 显式视频码率，0 表示按质量档位自动
    private final int videoBitRateKbps;
    // 显式音频码率，0 表示按质量档位自动
    private final int audioBitRateKbps;
    // 显式视频编码器，空串表示按目标格式自动
    private final String videoEncoder;
    // 显式音频编码器，空串表示按目标格式自动
    private final String audioEncoder;
    // 变速倍率，1.0 表示不变速
    private final double speedFactor;
    // 一行英文说明，展示给用户解释该预设的用途
    private final String description;

    // 构造方法：一次性接收全部预设字段
    public Preset(String name, ConversionRequest.Category category, String targetExtension, Quality quality,
                  int width, int height, double frameRate, int videoBitRateKbps, int audioBitRateKbps,
                  String videoEncoder, String audioEncoder, double speedFactor, String description) {
        // 保存名称
        this.name = name;
        // 保存类别
        this.category = category;
        // 保存目标格式
        this.targetExtension = targetExtension;
        // 保存质量档位
        this.quality = quality;
        // 保存宽度
        this.width = width;
        // 保存高度
        this.height = height;
        // 保存帧率
        this.frameRate = frameRate;
        // 保存视频码率
        this.videoBitRateKbps = videoBitRateKbps;
        // 保存音频码率
        this.audioBitRateKbps = audioBitRateKbps;
        // 保存视频编码器
        this.videoEncoder = videoEncoder;
        // 保存音频编码器
        this.audioEncoder = audioEncoder;
        // 保存变速倍率
        this.speedFactor = speedFactor;
        // 保存说明文本
        this.description = description;
    }

    // 获取预设名称
    public String name() {
        // 返回名称
        return name;
    }

    // 获取预设类别
    public ConversionRequest.Category category() {
        // 返回类别
        return category;
    }

    // 获取目标格式扩展名
    public String targetExtension() {
        // 返回目标格式
        return targetExtension;
    }

    // 获取质量档位
    public Quality quality() {
        // 返回质量档位
        return quality;
    }

    // 获取目标宽度
    public int width() {
        // 返回宽度
        return width;
    }

    // 获取目标高度
    public int height() {
        // 返回高度
        return height;
    }

    // 获取目标帧率
    public double frameRate() {
        // 返回帧率
        return frameRate;
    }

    // 获取显式视频码率
    public int videoBitRateKbps() {
        // 返回视频码率
        return videoBitRateKbps;
    }

    // 获取显式音频码率
    public int audioBitRateKbps() {
        // 返回音频码率
        return audioBitRateKbps;
    }

    // 获取显式视频编码器
    public String videoEncoder() {
        // 返回视频编码器名，可能为空表示自动
        return videoEncoder;
    }

    // 获取显式音频编码器
    public String audioEncoder() {
        // 返回音频编码器名，可能为空表示自动
        return audioEncoder;
    }

    // 获取变速倍率
    public double speedFactor() {
        // 返回变速倍率
        return speedFactor;
    }

    // 获取说明文本
    public String description() {
        // 返回说明文本
        return description;
    }

    // 把预设参数套用到目标请求上，只覆盖编码相关字段，不触碰输入输出路径
    public void applyTo(ConversionRequest request) {
        // 写入类别，保证后续命令构建走正确的分支
        request.category(category);
        // 写入目标格式扩展名
        request.targetExtension(targetExtension);
        // 写入质量档位
        request.quality(quality);
        // 写入目标宽度
        request.width(width);
        // 写入目标高度
        request.height(height);
        // 写入目标帧率
        request.frameRate(frameRate);
        // 写入显式视频码率
        request.videoBitRateKbps(videoBitRateKbps);
        // 写入显式音频码率
        request.audioBitRateKbps(audioBitRateKbps);
        // 写入显式视频编码器，空串会被归一化为自动
        request.videoEncoder(videoEncoder);
        // 写入显式音频编码器，空串会被归一化为自动
        request.audioEncoder(audioEncoder);
        // 写入变速倍率
        request.speedFactor(speedFactor);
    }
}
