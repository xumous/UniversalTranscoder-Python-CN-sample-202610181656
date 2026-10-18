package com.xu.universaltranscoder.core;

// 引入 Path，表示输入与输出文件路径

import java.nio.file.Path;

// 关键字 class：ConversionRequest 是“用户意图”的完整数据载体，界面把控件值写入该对象，命令构建器再读取它
// 之所以把意图与命令构建分离：同一份意图可以派生出多条命令（例如自动补救时重建命令），也能被队列重复执行
public final class ConversionRequest {

    // 输入文件路径，命令构建的 -i 参数
    private Path input;
    // 转换类别
    private Category category = Category.VIDEO;
    // 目标格式扩展名，例如 mp4 / mkv / mp3 / png
    private String targetExtension = "mp4";
    // 输出目录，为空时表示与输入文件同目录
    private Path outputDirectory;
    // 输出命名模式，默认替换扩展名，符合绝大多数用户直觉
    private NamingMode namingMode = NamingMode.REPLACE_EXTENSION;
    // 自定义命名模板，仅在命名模式为模板时生效
    private String nameTemplate = "{name}_{target}";
    // 质量档位，默认中档
    private Quality quality = Quality.MEDIUM;
    // 用户显式指定的视频编码器，为空表示按目标格式自动挑选
    private String videoEncoder;
    // 用户显式指定的音频编码器，为空表示按目标格式自动挑选
    private String audioEncoder;
    // 用户显式指定的视频码率（千比特每秒），0 表示自动
    private int videoBitRateKbps;
    // 用户显式指定的音频码率（千比特每秒），0 表示自动
    private int audioBitRateKbps;
    // 目标宽度，0 表示保持原始宽度
    private int width;
    // 目标高度，0 表示保持原始高度
    private int height;
    // 目标帧率，0 表示保持原始帧率
    private double frameRate;
    // 裁剪起点横坐标
    private int cropX;
    // 裁剪起点纵坐标
    private int cropY;
    // 裁剪宽度，0 表示不裁剪
    private int cropWidth;
    // 裁剪高度，0 表示不裁剪
    private int cropHeight;
    // 旋转角度，仅允许 0 / 90 / 180 / 270
    private int rotateDegrees;
    // 是否水平镜像
    private boolean mirrorHorizontal;
    // 是否垂直镜像
    private boolean mirrorVertical;
    // 水印文本，为空表示不加水印
    private String watermarkText;
    // 水印位置，取值 left-top / right-top / left-bottom / right-bottom / center
    private String watermarkPosition = "right-bottom";
    // 水印字号（像素）
    private int watermarkFontSize = 24;
    // 水印不透明度，取值 0.05 至 1.0
    private double watermarkOpacity = 0.6d;
    // 外挂字幕文件路径，为空表示不烧录字幕
    private Path subtitleFile;
    // 是否烧录字幕，为真时使用 subtitles 滤镜把字幕烙进画面
    private boolean burnSubtitles;
    // 播放速度倍率，1.0 表示不变速
    private double speedFactor = 1.0d;
    // 元数据处理模式，默认保留原元数据
    private String metadataMode = "keep";
    // 是否启用硬件加速编码
    private boolean hardwareAcceleration;
    // 是否强制写入 yuv420p 像素格式，提升播放器兼容性
    private boolean forceYuv420p = true;
    // 是否为本次命令追加补救标记，避免补救逻辑无限递归
    private boolean remedyApplied;
    // 是否在滤镜链末尾追加 pad，把奇数分辨率补齐为偶数，用于“奇数分辨率”失败的自动补救
    private boolean padToEven;

    // 获取输入文件路径
    public Path input() {
        // 返回输入路径
        return input;
    }

    // 设置输入文件路径，返回自身以支持链式调用
    public ConversionRequest input(Path value) {
        // 写入输入路径
        this.input = value;
        // 返回自身，便于连续设置参数
        return this;
    }

    // 获取转换类别
    public Category category() {
        // 返回类别
        return category;
    }

    // 设置转换类别
    public ConversionRequest category(Category value) {
        // 写入类别
        this.category = value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取目标扩展名
    public String targetExtension() {
        // 返回目标扩展名
        return targetExtension;
    }

    // 设置目标扩展名
    public ConversionRequest targetExtension(String value) {
        // 写入目标扩展名
        this.targetExtension = value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取输出目录
    public Path outputDirectory() {
        // 返回输出目录
        return outputDirectory;
    }

    // 设置输出目录
    public ConversionRequest outputDirectory(Path value) {
        // 写入输出目录
        this.outputDirectory = value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取命名模式
    public NamingMode namingMode() {
        // 返回命名模式
        return namingMode;
    }

    // 设置命名模式
    public ConversionRequest namingMode(NamingMode value) {
        // 写入命名模式
        this.namingMode = value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取自定义命名模板
    public String nameTemplate() {
        // 返回模板文本
        return nameTemplate;
    }

    // 设置自定义命名模板
    public ConversionRequest nameTemplate(String value) {
        // 模板为空时回退到默认模板，避免生成空文件名
        this.nameTemplate = (value == null || value.trim().isEmpty()) ? "{name}_{target}" : value.trim();
        // 返回自身支持链式调用
        return this;
    }

    // 获取质量档位
    public Quality quality() {
        // 返回质量档位
        return quality;
    }

    // 设置质量档位
    public ConversionRequest quality(Quality value) {
        // 档位为空时回退到中档
        this.quality = value == null ? Quality.MEDIUM : value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取显式视频编码器
    public String videoEncoder() {
        // 返回视频编码器，可能为空表示自动
        return videoEncoder;
    }

    // 设置显式视频编码器，空串归一化为空表示自动
    public ConversionRequest videoEncoder(String value) {
        // 空串与 null 都视为自动
        this.videoEncoder = (value == null || value.trim().isEmpty() || "auto".equalsIgnoreCase(value.trim()))
                // 自动时置为空
                ? null
                // 否则保存用户选择
                : value.trim();
        // 返回自身支持链式调用
        return this;
    }

    // 获取显式音频编码器
    public String audioEncoder() {
        // 返回音频编码器，可能为空表示自动
        return audioEncoder;
    }

    // 设置显式音频编码器
    public ConversionRequest audioEncoder(String value) {
        // 空串与 null 都视为自动
        this.audioEncoder = (value == null || value.trim().isEmpty() || "auto".equalsIgnoreCase(value.trim()))
                // 自动时置为空
                ? null
                // 否则保存用户选择
                : value.trim();
        // 返回自身支持链式调用
        return this;
    }

    // 获取显式视频码率
    public int videoBitRateKbps() {
        // 返回视频码率
        return videoBitRateKbps;
    }

    // 设置显式视频码率
    public ConversionRequest videoBitRateKbps(int value) {
        // 负数归一化为 0 表示自动
        this.videoBitRateKbps = Math.max(value, 0);
        // 返回自身支持链式调用
        return this;
    }

    // 获取显式音频码率
    public int audioBitRateKbps() {
        // 返回音频码率
        return audioBitRateKbps;
    }

    // 设置显式音频码率
    public ConversionRequest audioBitRateKbps(int value) {
        // 负数归一化为 0 表示自动
        this.audioBitRateKbps = Math.max(value, 0);
        // 返回自身支持链式调用
        return this;
    }

    // 获取目标宽度
    public int width() {
        // 返回宽度
        return width;
    }

    // 设置目标宽度
    public ConversionRequest width(int value) {
        // 负数归一化为 0 表示保持原始
        this.width = Math.max(value, 0);
        // 返回自身支持链式调用
        return this;
    }

    // 获取目标高度
    public int height() {
        // 返回高度
        return height;
    }

    // 设置目标高度
    public ConversionRequest height(int value) {
        // 负数归一化为 0 表示保持原始
        this.height = Math.max(value, 0);
        // 返回自身支持链式调用
        return this;
    }

    // 获取目标帧率
    public double frameRate() {
        // 返回帧率
        return frameRate;
    }

    // 设置目标帧率
    public ConversionRequest frameRate(double value) {
        // 负数归一化为 0 表示保持原始
        this.frameRate = value < 0d ? 0d : value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取裁剪起点横坐标
    public int cropX() {
        // 返回裁剪横坐标
        return cropX;
    }

    // 获取裁剪起点纵坐标
    public int cropY() {
        // 返回裁剪纵坐标
        return cropY;
    }

    // 获取裁剪宽度
    public int cropWidth() {
        // 返回裁剪宽度
        return cropWidth;
    }

    // 获取裁剪高度
    public int cropHeight() {
        // 返回裁剪高度
        return cropHeight;
    }

    // 一次性设置裁剪区域，任一维度为 0 即表示不裁剪
    public ConversionRequest crop(int x, int y, int w, int h) {
        // 裁剪横坐标不允许为负
        this.cropX = Math.max(x, 0);
        // 裁剪纵坐标不允许为负
        this.cropY = Math.max(y, 0);
        // 裁剪宽度不允许为负
        this.cropWidth = Math.max(w, 0);
        // 裁剪高度不允许为负
        this.cropHeight = Math.max(h, 0);
        // 返回自身支持链式调用
        return this;
    }

    // 获取旋转角度
    public int rotateDegrees() {
        // 返回旋转角度
        return rotateDegrees;
    }

    // 设置旋转角度，只接受 0/90/180/270 四个值
    public ConversionRequest rotateDegrees(int value) {
        // 对 360 取模后判断是否为受支持角度
        int normalized = ((value % 360) + 360) % 360;
        // 仅支持 90 的整数倍，其余角度归一化为 0 避免滤镜语法错误
        this.rotateDegrees = (normalized == 90 || normalized == 180 || normalized == 270) ? normalized : 0;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否水平镜像
    public boolean mirrorHorizontal() {
        // 返回水平镜像标记
        return mirrorHorizontal;
    }

    // 设置水平镜像
    public ConversionRequest mirrorHorizontal(boolean value) {
        // 写入水平镜像标记
        this.mirrorHorizontal = value;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否垂直镜像
    public boolean mirrorVertical() {
        // 返回垂直镜像标记
        return mirrorVertical;
    }

    // 设置垂直镜像
    public ConversionRequest mirrorVertical(boolean value) {
        // 写入垂直镜像标记
        this.mirrorVertical = value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取水印文本
    public String watermarkText() {
        // 返回水印文本
        return watermarkText;
    }

    // 设置水印文本
    public ConversionRequest watermarkText(String value) {
        // 空白文本视为不加水印
        this.watermarkText = (value == null || value.trim().isEmpty()) ? null : value.trim();
        // 返回自身支持链式调用
        return this;
    }

    // 获取水印位置
    public String watermarkPosition() {
        // 返回水印位置
        return watermarkPosition;
    }

    // 设置水印位置
    public ConversionRequest watermarkPosition(String value) {
        // 位置为空时使用右下角默认值
        this.watermarkPosition = (value == null || value.trim().isEmpty()) ? "right-bottom" : value.trim();
        // 返回自身支持链式调用
        return this;
    }

    // 获取水印字号
    public int watermarkFontSize() {
        // 返回字号
        return watermarkFontSize;
    }

    // 设置水印字号
    public ConversionRequest watermarkFontSize(int value) {
        // 字号做上下限钳制，避免过大或过小导致滤镜异常
        this.watermarkFontSize = Math.min(Math.max(value, 8), 240);
        // 返回自身支持链式调用
        return this;
    }

    // 获取水印不透明度
    public double watermarkOpacity() {
        // 返回不透明度
        return watermarkOpacity;
    }

    // 设置水印不透明度
    public ConversionRequest watermarkOpacity(double value) {
        // 不透明度钳制在 0.05 至 1.0 之间，保证水印可见又不完全遮挡画面
        this.watermarkOpacity = Math.min(Math.max(value, 0.05d), 1.0d);
        // 返回自身支持链式调用
        return this;
    }

    // 获取外挂字幕文件
    public Path subtitleFile() {
        // 返回字幕路径
        return subtitleFile;
    }

    // 设置外挂字幕文件
    public ConversionRequest subtitleFile(Path value) {
        // 写入字幕路径
        this.subtitleFile = value;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否烧录字幕
    public boolean burnSubtitles() {
        // 返回烧录标记
        return burnSubtitles;
    }

    // 设置是否烧录字幕
    public ConversionRequest burnSubtitles(boolean value) {
        // 写入烧录标记
        this.burnSubtitles = value;
        // 返回自身支持链式调用
        return this;
    }

    // 获取播放速度倍率
    public double speedFactor() {
        // 返回速度倍率
        return speedFactor;
    }

    // 设置播放速度倍率
    public ConversionRequest speedFactor(double value) {
        // 倍率钳制在 0.25 至 4.0 之间，超出范围时 atempo 需要复杂串联且听感怪异
        this.speedFactor = Math.min(Math.max(value, 0.25d), 4.0d);
        // 返回自身支持链式调用
        return this;
    }

    // 获取元数据处理模式
    public String metadataMode() {
        // 返回元数据模式
        return metadataMode;
    }

    // 设置元数据处理模式
    public ConversionRequest metadataMode(String value) {
        // 空值归一化为保留
        this.metadataMode = (value == null || value.trim().isEmpty()) ? "keep" : value.trim().toLowerCase();
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否启用硬件加速
    public boolean hardwareAcceleration() {
        // 返回硬件加速标记
        return hardwareAcceleration;
    }

    // 设置是否启用硬件加速
    public ConversionRequest hardwareAcceleration(boolean value) {
        // 写入硬件加速标记
        this.hardwareAcceleration = value;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否强制 yuv420p 像素格式
    public boolean forceYuv420p() {
        // 返回像素格式标记
        return forceYuv420p;
    }

    // 设置是否强制 yuv420p
    public ConversionRequest forceYuv420p(boolean value) {
        // 写入像素格式标记
        this.forceYuv420p = value;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否已经应用过自动补救，用于阻止补救逻辑递归
    public boolean remedyApplied() {
        // 返回补救标记
        return remedyApplied;
    }

    // 标记已经应用过自动补救
    public ConversionRequest remedyApplied(boolean value) {
        // 写入补救标记
        this.remedyApplied = value;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否需要把奇数分辨率补齐为偶数
    public boolean padToEven() {
        // 返回补齐标记
        return padToEven;
    }

    // 设置是否把奇数分辨率补齐为偶数
    public ConversionRequest padToEven(boolean value) {
        // 写入补齐标记
        this.padToEven = value;
        // 返回自身支持链式调用
        return this;
    }

    // 判断是否为纯音频输出，供执行器决定进度换算与提示文案
    public boolean isAudioOnly() {
        // 音频类别即纯音频输出
        return category == Category.AUDIO;
    }

    // 判断是否为图片输出，供执行器决定是否需要百分比进度
    public boolean isImageOutput() {
        // 图片类别即图片输出
        return category == Category.IMAGE;
    }

    // 定义转换类别枚举，决定走视频、音频还是图片的参数分支
    public enum Category {
        // 视频类别，输出带画面（可能同时带声音）
        VIDEO,
        // 音频类别，输出只有声音
        AUDIO,
        // 图片类别，输出静态图片，只取一帧
        IMAGE
    }

    // 定义输出命名模式枚举，决定输出文件名如何从输入文件名派生
    public enum NamingMode {
        // 保留原名并追加目标后缀，例如 movie.mp4 → movie.mp4.mkv
        KEEP_NAME_ADD_SUFFIX,
        // 替换扩展名，例如 movie.mp4 → movie.mkv
        REPLACE_EXTENSION,
        // 使用自定义模板，模板中可用替换标记生成文件名
        CUSTOM_TEMPLATE
    }
}
