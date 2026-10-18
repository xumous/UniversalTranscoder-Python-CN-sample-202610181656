package com.xu.universaltranscoder.model;

// 引入 Bitmap：图片目标的压缩格式就定义在 Bitmap.CompressFormat 上。

import android.graphics.Bitmap;
// 引入 MediaMuxer：容器常量来自 MediaMuxer.OutputFormat。
import android.media.MediaMuxer;
// 引入注解：允许部分字段为空。
import androidx.annotation.Nullable;

// 引入 MediaOp：目标规格需要声明自己属于哪个大类、对应哪种操作。
import com.xu.universaltranscoder.util.MediaOp;

/**
 * TargetSpec 描述“一个可选的目标格式/操作”。
 * <p>
 * 之所以把“目标格式”建模成一个字段齐全的对象而不是简单字符串，是因为界面需要根据它决定：
 * 输出扩展名、MIME、使用哪个 MediaMuxer 容器、是否走重编码、需要哪个编码器，
 * 以及最关键的——在当前设备上是否真的可用（supported + unsupportedReason）。
 */
public final class TargetSpec {

    // 唯一键：用于任务去重与持久化设置，例如 "video.mp4.remux"。
    public final String key;
    // 界面展示名（英文），例如 "MP4 · Remux (no re-encode)"。
    public final String label;
    // 输出文件扩展名，不含点号。
    public final String extension;
    // 输出文件 MIME 类型，SAF 新建文档时必须提供。
    public final String mimeType;
    // MediaMuxer 容器常量；-1 表示该目标不经过 MediaMuxer（图片、PDF、抽帧等）。
    public final int muxerFormat;
    // 该目标对应的转换操作。
    public final MediaOp.Operation operation;
    // 该目标所属的媒体大类。
    public final MediaOp.Category category;
    // 重编码时使用的视频编码 MIME；为 null 表示该目标不涉及视频编码。
    public final String videoMime;
    // 目标音频编码 MIME；为 null 表示该目标不指定音频编码（沿用源轨道）。
    public final String audioMime;
    // 图片目标使用的压缩格式；为 null 表示不是图片目标。
    public final Bitmap.CompressFormat compressFormat;
    // 在当前设备上是否受支持：由容器探测、编码器探测以及静态容器规则共同决定。
    public final boolean supported;
    // 不受支持时的英文原因（会直接展示给用户，因此必须具体、可读）。
    public final String unsupportedReason;

    // 全字段构造：由 TargetRegistry 内部使用，避免在外部随意构造出不一致的目标。
    public TargetSpec(String key, String label, String extension, String mimeType, int muxerFormat,
                      MediaOp.Operation operation, MediaOp.Category category,
                      @Nullable String videoMime, @Nullable String audioMime,
                      @Nullable Bitmap.CompressFormat compressFormat,
                      boolean supported, @Nullable String unsupportedReason) {
        // 保存唯一键。
        this.key = key;
        // 保存展示名。
        this.label = label;
        // 保存扩展名。
        this.extension = extension;
        // 保存 MIME。
        this.mimeType = mimeType;
        // 保存容器常量。
        this.muxerFormat = muxerFormat;
        // 保存操作类型。
        this.operation = operation;
        // 保存媒体大类。
        this.category = category;
        // 保存视频编码 MIME。
        this.videoMime = videoMime;
        // 保存音频编码 MIME。
        this.audioMime = audioMime;
        // 保存图片压缩格式。
        this.compressFormat = compressFormat;
        // 保存支持状态。
        this.supported = supported;
        // 保存不受支持的原因；受支持时统一为 null，便于判断。
        this.unsupportedReason = unsupportedReason;
    }

    /**
     * 返回界面下拉框使用的文本：受支持时只显示标签，不受支持时附带明确原因。
     */
    public String displayText() {
        // 受支持目标直接使用标签。
        if (supported) {
            // 返回标签。
            return label;
        }
        // 不受支持目标在标签后附加原因，让用户一眼看到“为什么不支持”。
        return label + " — unsupported on this device (" + unsupportedReason + ")";
    }

    /**
     * 判断该目标是否经由 MediaMuxer 封装。
     */
    public boolean usesMuxer() {
        // 容器常量有效即表示走封装流程。
        return muxerFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                || muxerFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                || muxerFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                || muxerFormat >= 0;
    }

    /**
     * 判断该目标是否需要视频重编码。
     */
    public boolean isVideoTranscode() {
        // 操作类型为视频重编码时才需要编码器。
        return operation == MediaOp.Operation.VIDEO_TRANSCODE;
    }

    /**
     * 判断该目标是否需要音频重编码。
     */
    public boolean isAudioTranscode() {
        // 操作类型为音频重编码时才需要 AAC 编码器。
        return operation == MediaOp.Operation.AUDIO_TRANSCODE;
    }

    /**
     * 判断该目标是否是图片转换。
     */
    public boolean isImageConvert() {
        // 操作类型为图片转换。
        return operation == MediaOp.Operation.IMAGE_CONVERT;
    }
}
