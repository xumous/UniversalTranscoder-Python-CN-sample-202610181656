package com.xu.universaltranscoder.model;

// 引入 Context：用于执行设备能力探测（构造 MediaMuxer 探测容器）。

import android.content.Context;
// 引入 Bitmap：定义图片目标的压缩格式。
import android.graphics.Bitmap;
// 引入 MediaMuxer：使用其 OutputFormat 常量。
import android.media.MediaMuxer;

// 引入 Capabilities：查询设备真实支持的容器与编解码器。
import com.xu.universaltranscoder.core.Capabilities;
// 引入 MediaOp：声明各目标的大类与操作。
import com.xu.universaltranscoder.util.MediaOp;

// 引入 ArrayList：构建目标列表。
import java.util.ArrayList;
// 引入 List：对外返回只读列表。
import java.util.List;
// 引入 Collections：包装为不可修改列表。
import java.util.Collections;
// 引入 Locale：统一小写转换时使用的语言环境。
import java.util.Locale;

/**
 * TargetRegistry 负责生成“当前设备上真实可用的目标格式清单”。
 * <p>
 * 为什么集中放在这里：界面、任务队列、转换引擎都需要同一份目标清单，且清单必须在启动时按设备能力
 * 计算一次（探测 MediaMuxer 容器 + 枚举 MediaCodec 编码器），不能每次点按钮都重新探测。
 */
public final class TargetRegistry {

    // 视频编码 MIME：H.264/AVC。
    public static final String MIME_VIDEO_AVC = "video/avc";
    // 视频编码 MIME：H.265/HEVC。
    public static final String MIME_VIDEO_HEVC = "video/hevc";
    // 视频编码 MIME：VP8（WebM 常用）。
    public static final String MIME_VIDEO_VP8 = "video/x-vnd.on2.vp8";
    // 视频编码 MIME：VP9（WebM 常用）。
    public static final String MIME_VIDEO_VP9 = "video/x-vnd.on2.vp9";
    // 音频编码 MIME：AAC（LC）。
    public static final String MIME_AUDIO_AAC = "audio/mp4a-latm";
    // 音频编码 MIME：Opus。
    public static final String MIME_AUDIO_OPUS = "audio/opus";

    // 静态缓存：整个进程只构建一次目标清单。
    private static ArrayList<TargetSpec> videoTargets;
    // 静态缓存：音频目标清单。
    private static ArrayList<TargetSpec> audioTargets;
    // 静态缓存：图片目标清单。
    private static ArrayList<TargetSpec> imageTargets;
    // 静态缓存：文档目标清单。
    private static ArrayList<TargetSpec> documentTargets;

    // 私有构造：注册表不需要实例。
    private TargetRegistry() {
        // 空实现，禁止外部 new。
    }

    /**
     * 初始化目标清单。必须在界面列出目标格式之前调用一次（通常在 MainActivity.onCreate 中）。
     * 重复调用是安全的：内部会检查缓存，避免重复探测。
     */
    public static synchronized void init(Context context) {
        // 已初始化过就直接返回。
        if (videoTargets != null) {
            // 说明四张清单均已就绪。
            return;
        }
        // 获取应用上下文，防止持有 Activity 引用造成泄漏。
        Context appContext = context.getApplicationContext();
        // 探测设备对 MP4 容器的支持（几乎所有设备都支持，但仍按真实探测结果处理）。
        boolean mp4Ok = Capabilities.isMuxerFormatSupported(appContext, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        // 探测设备对 WebM 容器的支持（不同设备差异较大）。
        boolean webmOk = Capabilities.isMuxerFormatSupported(appContext, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);
        // 探测设备对 3GP 容器的支持。
        boolean threeGpOk = Capabilities.isMuxerFormatSupported(appContext, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP);
        // 探测是否存在 AVC 编码器（视频重编码到 H.264 的前提）。
        boolean avcEncoder = Capabilities.hasCodec(MIME_VIDEO_AVC, true);
        // 探测是否存在 HEVC 编码器（视频重编码到 H.265 的前提）。
        boolean hevcEncoder = Capabilities.hasCodec(MIME_VIDEO_HEVC, true);
        // 探测是否存在 VP9 编码器（WebM 重编码的前提）。
        boolean vp9Encoder = Capabilities.hasCodec(MIME_VIDEO_VP9, true);
        // 探测是否存在 AAC 编码器（音频重编码与视频音轨转码的前提）。
        boolean aacEncoder = Capabilities.hasCodec(MIME_AUDIO_AAC, true);
        // 探测是否存在 Opus 编码器（WebM 音频重编码的前提）。
        boolean opusEncoder = Capabilities.hasCodec(MIME_AUDIO_OPUS, true);

        // ---------- 视频目标 ----------
        // 新建视频目标列表。
        ArrayList<TargetSpec> video = new ArrayList<>();
        // MP4 重封装：容器支持即可（轨道编码兼容性在运行时会再校验一次）。
        video.add(new TargetSpec("video.mp4.remux", "MP4 · Remux (no re-encode)", "mp4", "video/mp4",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, MediaOp.Operation.VIDEO_REMUX, MediaOp.Category.VIDEO,
                null, null, null, mp4Ok, mp4Ok ? null : "MediaMuxer rejected the MPEG-4 container"));
        // MKV 重封装：Android 的 MediaMuxer 从未提供 Matroska 输出，因此诚实标注为不支持。
        video.add(new TargetSpec("video.mkv.remux", "MKV · Remux (no re-encode)", "mkv", "video/x-matroska",
                -1, MediaOp.Operation.VIDEO_REMUX, MediaOp.Category.VIDEO,
                null, null, null, false, "Android MediaMuxer has no Matroska (MKV) output at any API level"));
        // WebM 重封装：需要设备容器支持；同时要求源轨道是 VP8/VP9/Opus/Vorbis（运行时校验）。
        video.add(new TargetSpec("video.webm.remux", "WEBM · Remux (no re-encode)", "webm", "video/webm",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, MediaOp.Operation.VIDEO_REMUX, MediaOp.Category.VIDEO,
                null, null, null, webmOk, webmOk ? null : "MediaMuxer rejected the WebM container"));
        // 3GP 重封装：容器支持即可。
        video.add(new TargetSpec("video.3gp.remux", "3GP · Remux (no re-encode)", "3gp", "video/3gpp",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP, MediaOp.Operation.VIDEO_REMUX, MediaOp.Category.VIDEO,
                null, null, null, threeGpOk, threeGpOk ? null : "MediaMuxer rejected the 3GPP container"));
        // MP4 + H.264 重编码：容器可用且存在 AVC 编码器。
        video.add(new TargetSpec("video.mp4.avc", "MP4 · Re-encode to H.264/AVC", "mp4", "video/mp4",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, MediaOp.Operation.VIDEO_TRANSCODE, MediaOp.Category.VIDEO,
                MIME_VIDEO_AVC, MIME_AUDIO_AAC, null, mp4Ok && avcEncoder && aacEncoder,
                mp4Ok ? (avcEncoder ? (aacEncoder ? null : "No AAC audio encoder on this device") : "No H.264 encoder on this device")
                        : "MediaMuxer rejected the MPEG-4 container"));
        // MP4 + H.265 重编码：容器可用且存在 HEVC 编码器。
        video.add(new TargetSpec("video.mp4.hevc", "MP4 · Re-encode to H.265/HEVC", "mp4", "video/mp4",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, MediaOp.Operation.VIDEO_TRANSCODE, MediaOp.Category.VIDEO,
                MIME_VIDEO_HEVC, MIME_AUDIO_AAC, null, mp4Ok && hevcEncoder && aacEncoder,
                mp4Ok ? (hevcEncoder ? (aacEncoder ? null : "No AAC audio encoder on this device") : "No H.265 encoder on this device")
                        : "MediaMuxer rejected the MPEG-4 container"));
        // MKV + H.264 重编码：同样受限于 MediaMuxer 无 Matroska 输出。
        video.add(new TargetSpec("video.mkv.avc", "MKV · Re-encode to H.264/AVC", "mkv", "video/x-matroska",
                -1, MediaOp.Operation.VIDEO_TRANSCODE, MediaOp.Category.VIDEO,
                MIME_VIDEO_AVC, MIME_AUDIO_AAC, null, false,
                "Android MediaMuxer has no Matroska (MKV) output at any API level"));
        // WebM + VP9 重编码：需要 WebM 容器与 VP9 编码器同时可用。
        video.add(new TargetSpec("video.webm.vp9", "WEBM · Re-encode to VP9 (video only)", "webm", "video/webm",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, MediaOp.Operation.VIDEO_TRANSCODE, MediaOp.Category.VIDEO,
                MIME_VIDEO_VP9, null, null, webmOk && vp9Encoder && aacEncoder,
                webmOk ? (vp9Encoder ? (aacEncoder ? null : "No AAC audio encoder on this device") : "No VP9 encoder on this device")
                        : "MediaMuxer rejected the WebM container"));
        // 3GP + H.264 重编码。
        video.add(new TargetSpec("video.3gp.avc", "3GP · Re-encode to H.264/AVC", "3gp", "video/3gpp",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP, MediaOp.Operation.VIDEO_TRANSCODE, MediaOp.Category.VIDEO,
                MIME_VIDEO_AVC, MIME_AUDIO_AAC, null, threeGpOk && avcEncoder && aacEncoder,
                threeGpOk ? (avcEncoder ? (aacEncoder ? null : "No AAC audio encoder on this device") : "No H.264 encoder on this device")
                        : "MediaMuxer rejected the 3GPP container"));
        // 视频抽帧：只需要解码器，属于纯图片输出，任何设备都能提供解码能力。
        video.add(new TargetSpec("video.frames.png", "PNG · Extract frames", "png", "image/png",
                -1, MediaOp.Operation.EXTRACT_FRAMES, MediaOp.Category.VIDEO,
                null, null, Bitmap.CompressFormat.PNG,
                Capabilities.hasCodec(MIME_VIDEO_AVC, false) || Capabilities.hasCodec(MIME_VIDEO_HEVC, false),
                "No video decoder available on this device"));

        // ---------- 音频目标 ----------
        // 新建音频目标列表。
        ArrayList<TargetSpec> audio = new ArrayList<>();
        // M4A 重封装：把源音频轨原样放入 MP4 容器（AAC 源最终即为 .m4a）。
        audio.add(new TargetSpec("audio.m4a.remux", "M4A · Remux AAC (no re-encode)", "m4a", "audio/mp4",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, MediaOp.Operation.AUDIO_REMUX, MediaOp.Category.AUDIO,
                null, null, null, mp4Ok, mp4Ok ? null : "MediaMuxer rejected the MPEG-4 container"));
        // 3GP 音频重封装。
        audio.add(new TargetSpec("audio.3gp.remux", "3GP Audio · Remux (no re-encode)", "3gp", "audio/3gpp",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP, MediaOp.Operation.AUDIO_REMUX, MediaOp.Category.AUDIO,
                null, null, null, threeGpOk, threeGpOk ? null : "MediaMuxer rejected the 3GPP container"));
        // WebM 音频重封装：源必须是 Opus/Vorbis，运行时校验。
        audio.add(new TargetSpec("audio.webm.remux", "WEBM Audio · Remux Opus/Vorbis", "webm", "audio/webm",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, MediaOp.Operation.AUDIO_REMUX, MediaOp.Category.AUDIO,
                null, null, null, webmOk, webmOk ? null : "MediaMuxer rejected the WebM container"));
        // M4A 重编码：解码源音频并编码为 AAC（可改采样率、声道、码率）。
        audio.add(new TargetSpec("audio.m4a.aac", "M4A · Re-encode to AAC (tunable)", "m4a", "audio/mp4",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, MediaOp.Operation.AUDIO_TRANSCODE, MediaOp.Category.AUDIO,
                null, MIME_AUDIO_AAC, null, mp4Ok && aacEncoder,
                mp4Ok ? (aacEncoder ? null : "No AAC encoder on this device") : "MediaMuxer rejected the MPEG-4 container"));
        // WebM 音频重编码为 Opus。
        audio.add(new TargetSpec("audio.webm.opus", "WEBM Audio · Re-encode to Opus", "webm", "audio/webm",
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, MediaOp.Operation.AUDIO_TRANSCODE, MediaOp.Category.AUDIO,
                null, MIME_AUDIO_OPUS, null, webmOk && opusEncoder,
                webmOk ? (opusEncoder ? null : "No Opus encoder on this device") : "MediaMuxer rejected the WebM container"));

        // ---------- 图片目标 ----------
        // 新建图片目标列表。
        ArrayList<TargetSpec> image = new ArrayList<>();
        // PNG：Bitmap.compress 在所有 Android 版本都支持 PNG。
        image.add(new TargetSpec("image.png", "PNG · Lossless", "png", "image/png",
                -1, MediaOp.Operation.IMAGE_CONVERT, MediaOp.Category.IMAGE,
                null, null, Bitmap.CompressFormat.PNG, true, null));
        // JPEG：有损压缩，质量可调。
        image.add(new TargetSpec("image.jpg", "JPEG · Lossy, quality adjustable", "jpg", "image/jpeg",
                -1, MediaOp.Operation.IMAGE_CONVERT, MediaOp.Category.IMAGE,
                null, null, Bitmap.CompressFormat.JPEG, true, null));
        // WEBP：系统位图压缩器同样支持有损 WEBP。
        image.add(new TargetSpec("image.webp", "WEBP · Lossy, quality adjustable", "webp", "image/webp",
                -1, MediaOp.Operation.IMAGE_CONVERT, MediaOp.Category.IMAGE,
                null, null, Bitmap.CompressFormat.WEBP, true, null));

        // ---------- 文档目标 ----------
        // 新建文档目标列表。
        ArrayList<TargetSpec> document = new ArrayList<>();
        // 图片合成 PDF：使用 android.graphics.pdf.PdfDocument，系统自带，始终可用。
        document.add(new TargetSpec("doc.images.pdf", "PDF · Combine images into one PDF", "pdf", "application/pdf",
                -1, MediaOp.Operation.IMAGES_TO_PDF, MediaOp.Category.DOCUMENT,
                null, null, null, true, null));
        // PDF 渲染为图片：使用 android.graphics.pdf.PdfRenderer（API 21+），始终可用。
        document.add(new TargetSpec("doc.pdf.png", "PNG · Render PDF pages to images", "png", "image/png",
                -1, MediaOp.Operation.PDF_TO_IMAGES, MediaOp.Category.DOCUMENT,
                null, null, Bitmap.CompressFormat.PNG, true, null));
        // 文本转 PDF：读文本后用 PdfDocument 绘制页面。
        document.add(new TargetSpec("doc.text.pdf", "PDF · Convert plain text to PDF", "pdf", "application/pdf",
                -1, MediaOp.Operation.TEXT_TO_PDF, MediaOp.Category.DOCUMENT,
                null, null, null, true, null));

        // 把四张清单写入静态缓存（外层方法已加锁，此处直接赋值即可）。
        videoTargets = video;
        // 写入音频清单。
        audioTargets = audio;
        // 写入图片清单。
        imageTargets = image;
        // 写入文档清单。
        documentTargets = document;
    }

    /**
     * 返回视频页可选目标（含抽帧）。
     */
    public static List<TargetSpec> videoTargets() {
        // 未初始化时返回空列表，避免空指针；调用方应先调用 init。
        if (videoTargets == null) {
            // 返回空列表。
            return Collections.emptyList();
        }
        // 返回只读视图，防止外部修改内部清单。
        return Collections.unmodifiableList(videoTargets);
    }

    /**
     * 返回音频页可选目标。
     */
    public static List<TargetSpec> audioTargets() {
        // 未初始化时返回空列表。
        if (audioTargets == null) {
            // 返回空列表。
            return Collections.emptyList();
        }
        // 返回只读视图。
        return Collections.unmodifiableList(audioTargets);
    }

    /**
     * 返回图片页可选目标。
     */
    public static List<TargetSpec> imageTargets() {
        // 未初始化时返回空列表。
        if (imageTargets == null) {
            // 返回空列表。
            return Collections.emptyList();
        }
        // 返回只读视图。
        return Collections.unmodifiableList(imageTargets);
    }

    /**
     * 返回文档页可选目标。
     */
    public static List<TargetSpec> documentTargets() {
        // 未初始化时返回空列表。
        if (documentTargets == null) {
            // 返回空列表。
            return Collections.emptyList();
        }
        // 返回只读视图。
        return Collections.unmodifiableList(documentTargets);
    }

    /**
     * 静态规则：判断某种编码能否被放入指定容器。
     * 这是“容器—编码”层面的硬约束，与设备无关（例如 WebM 只接受 VP8/VP9/AV1 与 Vorbis/Opus）。
     */
    public static boolean isCodecAllowedInContainer(int muxerFormat, String mime) {
        // MIME 为空时直接判定不允许，避免空指针。
        if (mime == null) {
            // 返回不允许。
            return false;
        }
        // 统一小写，规避厂商大小写差异。
        String lower = mime.toLowerCase(Locale.US);
        // MPEG-4 容器：接受 AVC/HEVC/MPEG-4 视频与 AAC/AMR 音频。
        if (muxerFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) {
            // 逐个比较允许的编码。
            return lower.equals(MIME_VIDEO_AVC)
                    || lower.equals(MIME_VIDEO_HEVC)
                    || lower.equals("video/mp4v-es")
                    || lower.equals(MIME_AUDIO_AAC)
                    || lower.equals("audio/3gpp")
                    || lower.equals("audio/amr-wb");
        }
        // 3GPP 容器：接受 H.263/AVC/MPEG-4 视频与 AAC/AMR 音频。
        if (muxerFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            // 逐个比较允许的编码。
            return lower.equals("video/3gpp")
                    || lower.equals(MIME_VIDEO_AVC)
                    || lower.equals("video/mp4v-es")
                    || lower.equals(MIME_AUDIO_AAC)
                    || lower.equals("audio/3gpp")
                    || lower.equals("audio/amr-wb");
        }
        // WebM 容器：接受 VP8/VP9/AV1 视频与 Vorbis/Opus 音频。
        if (muxerFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM) {
            // 逐个比较允许的编码。
            return lower.equals(MIME_VIDEO_VP8)
                    || lower.equals(MIME_VIDEO_VP9)
                    || lower.equals("video/av01")
                    || lower.equals("audio/vorbis")
                    || lower.equals(MIME_AUDIO_OPUS);
        }
        // 其它容器（含 MKV）在本应用中没有对应实现，统一判定不允许。
        return false;
    }
}
