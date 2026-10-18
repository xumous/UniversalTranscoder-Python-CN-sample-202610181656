package com.xu.universaltranscoder.core;

/**
 * ErrorReason 把转换过程中可能出现的失败归为有限几类，并为每一类给出：
 * 1) reason：面向用户的英文原因描述；
 * 2) suggestion：可执行的英文修复建议；
 * 3) code：稳定的机器可读编码，便于日志检索与后续统计。
 * <p>
 * 之所以要“分类”而不是直接抛原始异常字符串：MediaCodec/MediaMuxer 抛出的异常文本往往极难理解
 * （例如 “Error -38,0”），分类后用户至少知道“是权限问题还是编码器问题，以及该怎么办”。
 */
public enum ErrorReason {

    // 源文件不存在或已被移动/删除。
    FILE_NOT_FOUND("E001",
            "The source file could not be found or opened.",
            "Pick the file again with the system picker and confirm it still exists."),
    // 缺少读/写授权（SAF 授权可能已失效）。
    PERMISSION_DENIED("E002",
            "The app has no permission to read the source or write to the chosen folder.",
            "Re-select the file and the output folder so Android grants fresh access."),
    // 源文件里没有目标操作所需的轨道。
    NO_TRACK("E003",
            "The source contains no usable media track for this operation.",
            "Choose a file that really contains the audio or video stream you need."),
    // 视频轨缺失。
    NO_VIDEO_TRACK("E004",
            "The source contains no video track.",
            "Pick a video file instead of an audio or image file."),
    // 音频轨缺失。
    NO_AUDIO_TRACK("E005",
            "The source contains no audio track.",
            "Pick a file with an audio stream, or choose a video-only target."),
    // 目标容器或编码在当前设备上不被支持。
    FORMAT_UNSUPPORTED("E006",
            "The requested target format is not supported on this device.",
            "Pick a target that is marked as supported in the format list."),
    // 源轨道编码与目标容器不兼容（例如把 AAC 放进 WebM）。
    CODEC_INCOMPATIBLE("E007",
            "The source codec cannot be stored in the requested container.",
            "Use a container that accepts the source codec, or re-encode instead of remuxing."),
    // 编解码器初始化失败。
    CODEC_INIT_FAILED("E008",
            "The system could not initialise the required codec.",
            "Try a different resolution or codec, or turn off hardware codecs in Settings."),
    // 编解码过程中途报错。
    CODEC_ERROR("E009",
            "The codec reported an error while processing the media.",
            "Retry with software codecs enabled or with a lower resolution and bitrate."),
    // 封装器报错（写轨道/写样本失败）。
    MUXER_ERROR("E010",
            "The muxer rejected the stream or could not write the output file.",
            "Choose a different container, or write to the app's own output folder."),
    // 存储空间不足。
    STORAGE_FULL("E011",
            "There is not enough free space to write the output file.",
            "Free some storage space and run the job again."),
    // 参数非法（例如页码范围写错、缩放比例无法解析）。
    INVALID_PARAMETER("E012",
            "One of the supplied parameters is not valid.",
            "Check the number fields (bitrate, resolution, page range) and try again."),
    // 输入类型与所选操作不匹配（例如对 PDF 使用“图片合成 PDF”）。
    INPUT_MISMATCH("E013",
            "The selected input type does not match the chosen operation.",
            "Select the operation that matches your files, for example 'Render PDF pages to images' for a PDF."),
    // 解码失败（媒体文件损坏或使用了设备不支持的编码）。
    DECODE_FAILED("E014",
            "The media could not be decoded.",
            "The file may be damaged or use a codec this device does not support."),
    // 用户主动取消。
    CANCELED("E015",
            "The job was canceled by the user.",
            "Run the job again whenever you are ready."),
    // 通用 IO 错误。
    IO_ERROR("E016",
            "An input/output error occurred while processing the file.",
            "Make sure the storage is mounted and writable, then retry."),
    // 兜底：未归类的错误。
    UNKNOWN("E999",
            "The job failed for an unexpected reason.",
            "Open the execution log for the raw error text and try again.");

    // 稳定的错误编码。
    public final String code;
    // 面向用户的原因描述（英文）。
    public final String reason;
    // 面向用户的修复建议（英文）。
    public final String suggestion;

    // 枚举构造：把三要素一次性绑定。
    ErrorReason(String code, String reason, String suggestion) {
        // 保存错误编码。
        this.code = code;
        // 保存原因描述。
        this.reason = reason;
        // 保存修复建议。
        this.suggestion = suggestion;
    }

    /**
     * 拼出可直接放进日志/状态栏的一行文本。
     */
    public String oneLine() {
        // 形如 “[E006] reason (suggestion)”。
        return "[" + code + "] " + reason + " " + suggestion;
    }
}
