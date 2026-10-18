package com.xu.universaltranscoder.core;

// 引入 List 接口，作为错误行集合的类型

import java.util.List;
// 引入 Locale，用于大小写无关比较时的区域设置
import java.util.Locale;
// 引入正则 Pattern，用于匹配各类错误的特征串
import java.util.regex.Pattern;

// 关键字 class：ErrorClassifier 把 ffmpeg 的原始错误文本归类成可读原因与建议
// 之所以用正则匹配而不是精确字符串比较：不同版本与不同平台的内核错误措辞并不一致，正则能覆盖措辞变体
public final class ErrorClassifier {

    // 输入文件缺失的特征串
    private static final Pattern INPUT_MISSING = Pattern.compile("no such file or directory|could not open input|error opening input", Pattern.CASE_INSENSITIVE);
    // 权限不足或被占用的特征串
    private static final Pattern PERMISSION = Pattern.compile("permission denied|access is denied|being used by another process", Pattern.CASE_INSENSITIVE);
    // 输入数据非法的特征串
    private static final Pattern INVALID_DATA = Pattern.compile("invalid data found when processing input|moov atom not found|end of file|could not find codec parameters", Pattern.CASE_INSENSITIVE);
    // 编码器缺失的特征串
    private static final Pattern UNKNOWN_ENCODER = Pattern.compile("unknown encoder|could not find encoder|encoder .* not found", Pattern.CASE_INSENSITIVE);
    // 封装缺失的特征串
    private static final Pattern UNKNOWN_MUXER = Pattern.compile("unable to find a suitable output format|unknown output format|requested output format .* is not a suitable output format", Pattern.CASE_INSENSITIVE);
    // 奇数分辨率的特征串，覆盖多处不同措辞
    private static final Pattern ODD_RESOLUTION = Pattern.compile("not divisible by 2|width not divisible|height not divisible|invalid width|invalid height", Pattern.CASE_INSENSITIVE);
    // 硬件加速失败的特征串，覆盖 NVIDIA、Intel、AMD 与通用初始化错误
    private static final Pattern HARDWARE_FAILED = Pattern.compile("cannot load nvcuda|no capable devices found|error initializing output stream.*(nvenc|qsv|amf|vaapi|videotoolbox)|mfx session|failed to initialize|device creation failed|hwaccel", Pattern.CASE_INSENSITIVE);
    // 编码器与封装不兼容的特征串
    private static final Pattern CODEC_CONTAINER = Pattern.compile("could not find tag for codec|codec not currently supported in container|not supported in container", Pattern.CASE_INSENSITIVE);
    // 字幕处理失败的特征串
    private static final Pattern SUBTITLE_FAILED = Pattern.compile("unable to open .*\\.(srt|ass|ssa)|error opening subtitle|could not find subtitle", Pattern.CASE_INSENSITIVE);
    // 滤镜失败的特征串
    private static final Pattern FILTER_FAILED = Pattern.compile("no such filter|error initializing filter|error reinitializing filters|invalid filter", Pattern.CASE_INSENSITIVE);
    // 磁盘写入失败的特征串
    private static final Pattern DISK_FAILED = Pattern.compile("no space left on device|error writing trailer|error closing file|input/output error", Pattern.CASE_INSENSITIVE);
    // 声道布局失败的特征串
    private static final Pattern AUDIO_LAYOUT = Pattern.compile("specified channel layout|rematrix|channel layout not supported|unsupported number of channels", Pattern.CASE_INSENSITIVE);
    // 参数非法的特征串
    private static final Pattern INVALID_ARGS = Pattern.compile("invalid argument|error parsing options|option .* not found|unrecognized option", Pattern.CASE_INSENSITIVE);

    // 私有构造方法，禁止实例化这个纯静态分类器
    private ErrorClassifier() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 对一次失败执行做诊断，返回可读原因与建议
    public static Diagnosis classify(List<String> errorLines, int exitCode, boolean hardwareAccelerationUsed) {
        // 把全部错误行拼成一个大文本，避免逐行匹配时漏掉跨行特征
        StringBuilder combined = new StringBuilder();
        // 错误行集合非空时逐行拼接
        if (errorLines != null) {
            // 遍历每一行
            for (String line : errorLines) {
                // 行非空时追加
                if (line != null && !line.isEmpty()) {
                    // 追加当前行并补一个换行
                    combined.append(line).append('\n');
                }
            }
        }
        // 得到用于匹配的完整文本
        String text = combined.toString();
        // 文本空白时无法判断具体原因，只能按退出码给出通用结论
        if (text.trim().isEmpty()) {
            // 用户主动取消或进程被强制终止时给出取消类结论
            if (exitCode == 0) {
                // 退出码为 0 但文本为空属于异常情况，按未知处理
                return new Diagnosis(Category.UNKNOWN, "FFmpeg 未返回错误文本。", "请查看日志区获取详细信息。", false, false);
            }
            // 退出码非零且无文本时按未知处理
            return new Diagnosis(Category.UNKNOWN, "FFmpeg 以退出码 " + exitCode + " 失败。", "请检查日志区与输入文件。", false, false);
        }
        // 输入文件缺失优先判断，因为这是最常见也最容易修复的问题
        if (INPUT_MISSING.matcher(text).find()) {
            // 返回输入缺失结论
            return new Diagnosis(Category.INPUT_NOT_FOUND, "无法打开输入文件。",
                    // 建议检查路径是否存在以及文件名是否含特殊字符
                    "请确认文件路径存在，且文件名不含异常字符。", false, false);
        }
        // 权限问题次之判断
        if (PERMISSION.matcher(text).find()) {
            // 返回权限结论
            return new Diagnosis(Category.PERMISSION_DENIED, "文件或输出目录不可写。",
                    // 建议关闭占用程序或换个输出目录
                    "请关闭占用该文件的程序，或换一个输出目录。", false, false);
        }
        // 输入数据非法
        if (INVALID_DATA.matcher(text).find()) {
            // 返回数据非法结论
            return new Diagnosis(Category.INVALID_INPUT_DATA, "输入内容不像是有效的媒体文件。",
                    // 建议换源文件或先修复容器
                    "请换一个源文件，或先修复容器文件。", false, false);
        }
        // 硬件加速失败需要在软件回退之前判断，否则会被编码器错误掩盖
        if (HARDWARE_FAILED.matcher(text).find()) {
            // 返回硬件失败结论，并允许软件回退补救
            return new Diagnosis(Category.HARDWARE_ACCELERATION_FAILED, "无法初始化硬件加速。",
                    // 建议关闭硬件加速或更新驱动
                    "请在设置页改用软件编码，或更新显卡驱动。", false, true);
        }
        // 编码器缺失
        if (UNKNOWN_ENCODER.matcher(text).find()) {
            // 返回编码器缺失结论
            return new Diagnosis(Category.UNKNOWN_ENCODER, "所选编码器在当前 FFmpeg 内核中不可用。",
                    // 建议换用内核支持的编码器
                    "请改用其它编码器，或把编码器恢复为自动。", false, false);
        }
        // 封装缺失
        if (UNKNOWN_MUXER.matcher(text).find()) {
            // 返回封装缺失结论
            return new Diagnosis(Category.UNKNOWN_MUXER, "当前 FFmpeg 内核不支持该目标封装格式。",
                    // 建议换一个目标格式
                    "请改用其它目标格式。", false, false);
        }
        // 编码器与封装不兼容
        if (CODEC_CONTAINER.matcher(text).find()) {
            // 返回不兼容结论
            return new Diagnosis(Category.CODEC_INCOMPATIBLE_WITH_CONTAINER, "所选编码无法写入所选封装格式。",
                    // 建议换容器或换编码器
                    "请改用兼容的封装格式，或将编码器保持为自动。", false, false);
        }
        // 奇数分辨率，允许补齐偶数补救
        if (ODD_RESOLUTION.matcher(text).find()) {
            // 返回奇数分辨率结论
            return new Diagnosis(Category.ODD_RESOLUTION, "画面尺寸不是偶数，部分编码器会拒绝。",
                    // 建议自动补齐为偶数
                    "任务将自动补边为偶数后重试。", true, false);
        }
        // 字幕处理失败
        if (SUBTITLE_FAILED.matcher(text).find()) {
            // 返回字幕失败结论
            return new Diagnosis(Category.SUBTITLE_FAILED, "无法读取字幕文件。",
                    // 建议检查字幕路径与编码
                    "请检查字幕路径，并确认字幕文件使用受支持的文本编码。", false, false);
        }
        // 滤镜失败
        if (FILTER_FAILED.matcher(text).find()) {
            // 返回滤镜失败结论
            return new Diagnosis(Category.FILTER_FAILED, "无法初始化某个视频滤镜。",
                    // 建议减少滤镜或检查参数
                    "请简化滤镜设置，或清空水印与字幕输入。", false, false);
        }
        // 磁盘写入失败
        if (DISK_FAILED.matcher(text).find()) {
            // 返回磁盘失败结论
            return new Diagnosis(Category.DISK_WRITE_FAILED, "无法把产物写入磁盘。",
                    // 建议清理空间或换目录
                    "请清理磁盘空间，或换一个输出目录。", false, false);
        }
        // 声道布局失败
        if (AUDIO_LAYOUT.matcher(text).find()) {
            // 返回声道布局结论
            return new Diagnosis(Category.AUDIO_LAYOUT_FAILED, "目标音频编码器不支持该声道布局。",
                    // 建议换音频编码器
                    "请改用 AAC 或 MP3 等其它音频编码器。", false, false);
        }
        // 参数非法
        if (INVALID_ARGS.matcher(text).find()) {
            // 返回参数非法结论
            return new Diagnosis(Category.INVALID_ARGUMENTS, "FFmpeg 拒绝了某个命令行参数。",
                    // 建议重置参数
                    "请重置进阶参数后重试。", false, false);
        }
        // 使用了硬件加速却以非零码退出，即使文本未命中特征串也按硬件失败处理
        if (hardwareAccelerationUsed && exitCode != 0) {
            // 返回硬件失败结论并允许软件回退
            return new Diagnosis(Category.HARDWARE_ACCELERATION_FAILED, "硬件编码以退出码 " + exitCode + " 失败。",
                    // 建议回退到软件编码
                    "任务将改用软件编码重试。", false, true);
        }
        // 全部特征串都未命中时按未知归类，并截取最后一行作为原因提示
        String lastLine = lastNonEmptyLine(errorLines);
        // 返回未知结论
        return new Diagnosis(Category.UNKNOWN, "FFmpeg 以退出码 " + exitCode + " 失败：" + lastLine,
                // 建议查看日志面板
                "请打开日志区查看完整的 FFmpeg 输出。", false, false);
    }

    // 取错误行中最后一条非空行，作为兜底原因提示
    private static String lastNonEmptyLine(List<String> lines) {
        // 行集合为空时返回空串
        if (lines == null || lines.isEmpty()) {
            // 返回空串表示没有可用信息
            return "";
        }
        // 从末尾向前查找非空行
        for (int i = lines.size() - 1; i >= 0; i--) {
            // 取出当前行
            String line = lines.get(i);
            // 非空行直接返回，并做长度截断避免界面被超长文本撑破
            if (line != null && !line.trim().isEmpty()) {
                // 截断到 300 字符以内
                String trimmed = line.trim();
                // 返回截断后的文本
                return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
            }
        }
        // 全部为空行时返回空串
        return "";
    }

    // 判断错误文本是否包含大小写无关的关键词，供界面做二次提示
    public static boolean containsKeyword(List<String> lines, String keyword) {
        // 关键词为空时直接返回 false
        if (lines == null || keyword == null || keyword.isEmpty()) {
            // 返回 false 表示不包含
            return false;
        }
        // 统一转小写后逐行查找
        String needle = keyword.toLowerCase(Locale.ROOT);
        // 遍历所有行
        for (String line : lines) {
            // 行非空且包含关键词时返回真
            if (line != null && line.toLowerCase(Locale.ROOT).contains(needle)) {
                // 返回真表示包含
                return true;
            }
        }
        // 未命中时返回假
        return false;
    }

    // 定义错误分类枚举，覆盖转码链路上最常见的失败类别
    public enum Category {
        // 输入文件不存在或路径无法解析
        INPUT_NOT_FOUND,
        // 文件被占用或缺少读写权限
        PERMISSION_DENIED,
        // 输入文件损坏或不是媒体文件
        INVALID_INPUT_DATA,
        // 目标编码器在当前内核中不存在
        UNKNOWN_ENCODER,
        // 无法推断或找不到目标输出封装
        UNKNOWN_MUXER,
        // 分辨率不是偶数，部分编码器无法处理
        ODD_RESOLUTION,
        // 硬件加速初始化或执行失败
        HARDWARE_ACCELERATION_FAILED,
        // 编码器与目标封装不兼容
        CODEC_INCOMPATIBLE_WITH_CONTAINER,
        // 字幕文件无法打开或解码
        SUBTITLE_FAILED,
        // 滤镜表达式或滤镜初始化失败
        FILTER_FAILED,
        // 磁盘空间不足或写入失败
        DISK_WRITE_FAILED,
        // 音频声道布局不受支持
        AUDIO_LAYOUT_FAILED,
        // 进程被用户取消或超时终止
        CANCELLED_OR_KILLED,
        // 参数组合非法，ffmpeg 拒绝命令行
        INVALID_ARGUMENTS,
        // 其它无法归类的失败
        UNKNOWN;

        // 返回该错误类别的中文标题，供界面诊断参考展示
        public String displayName() {
            // 使用 switch 表达式把类别映射为中文标题
            return switch (this) {
                // 输入文件不存在或路径无法解析
                case INPUT_NOT_FOUND -> "输入文件不存在";
                // 文件被占用或缺少读写权限
                case PERMISSION_DENIED -> "权限不足或文件被占用";
                // 输入文件损坏或不是媒体文件
                case INVALID_INPUT_DATA -> "输入数据非法";
                // 目标编码器在当前内核中不存在
                case UNKNOWN_ENCODER -> "编码器不存在";
                // 无法推断或找不到目标输出封装
                case UNKNOWN_MUXER -> "目标封装不受支持";
                // 分辨率不是偶数，部分编码器无法处理
                case ODD_RESOLUTION -> "分辨率为奇数";
                // 硬件加速初始化或执行失败
                case HARDWARE_ACCELERATION_FAILED -> "硬件加速失败";
                // 编码器与目标封装不兼容
                case CODEC_INCOMPATIBLE_WITH_CONTAINER -> "编码与封装不兼容";
                // 字幕文件无法打开或解码
                case SUBTITLE_FAILED -> "字幕处理失败";
                // 滤镜表达式或滤镜初始化失败
                case FILTER_FAILED -> "滤镜初始化失败";
                // 磁盘空间不足或写入失败
                case DISK_WRITE_FAILED -> "磁盘写入失败";
                // 音频声道布局不受支持
                case AUDIO_LAYOUT_FAILED -> "音频声道布局不受支持";
                // 进程被用户取消或超时终止
                case CANCELLED_OR_KILLED -> "已取消或被终止";
                // 参数组合非法，ffmpeg 拒绝命令行
                case INVALID_ARGUMENTS -> "命令行参数非法";
                // 其它无法归类的失败
                case UNKNOWN -> "未知错误";
            };
        }
    }

    // 静态嵌套类 Diagnosis：承载一次错误诊断的结论
    public static final class Diagnosis {

        // 归类结果
        private final Category category;
        // 面向用户的可读原因（英文，会显示在界面上）
        private final String reason;
        // 面向用户的可读建议（英文，会显示在界面上）
        private final String suggestion;
        // 是否可以通过补齐偶数分辨率来补救
        private final boolean oddResolutionRemedy;
        // 是否可以通过切换软件编码器来补救
        private final boolean softwareFallbackRemedy;

        // 构造方法：一次性接收诊断结论
        Diagnosis(Category category, String reason, String suggestion, boolean oddResolutionRemedy, boolean softwareFallbackRemedy) {
            // 保存归类
            this.category = category;
            // 保存可读原因
            this.reason = reason;
            // 保存可读建议
            this.suggestion = suggestion;
            // 保存奇数分辨率补救标记
            this.oddResolutionRemedy = oddResolutionRemedy;
            // 保存软件回退补救标记
            this.softwareFallbackRemedy = softwareFallbackRemedy;
        }

        // 获取归类
        public Category category() {
            // 返回归类
            return category;
        }

        // 获取可读原因
        public String reason() {
            // 返回原因文本
            return reason;
        }

        // 获取可读建议
        public String suggestion() {
            // 返回建议文本
            return suggestion;
        }

        // 判断是否需要奇数分辨率补救
        public boolean needsOddResolutionRemedy() {
            // 返回补救标记
            return oddResolutionRemedy;
        }

        // 判断是否需要软件编码器回退
        public boolean needsSoftwareFallback() {
            // 返回补救标记
            return softwareFallbackRemedy;
        }

        // 生成一行诊断摘要，方便写入日志与状态标签
        public String describe() {
            // 拼出分类中文标题、原因与建议
            return category.displayName() + "：" + reason + " | " + suggestion;
        }
    }
}
