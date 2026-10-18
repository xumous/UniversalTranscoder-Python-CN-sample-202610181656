package com.xu.universaltranscoder.util;

/**
 * MediaOp 集中定义“媒体大类”与“具体转换操作”两套枚举。
 * 把它们放在一起，是为了让界面、任务队列、转换引擎共享同一份语义，避免字符串魔法值。
 */
public final class MediaOp {

    // 私有构造：该类型只作为枚举容器，不允许实例化。
    private MediaOp() {
        // 空实现，禁止外部 new。
    }

    /**
     * 媒体大类：决定界面使用哪个标签页与哪一组目标格式。
     */
    public enum Category {
        // 视频：包含视频轨（通常也含音频轨）。
        VIDEO,
        // 音频：只有音频轨。
        AUDIO,
        // 图片：位图文件（png/jpg/webp 等）。
        IMAGE,
        // 文档：图片集合、PDF 或纯文本。
        DOCUMENT
    }

    /**
     * 具体操作：决定转换引擎调用哪一条流水线。
     */
    public enum Operation {
        // 视频重封装：只换容器，不重新编码，速度最快。
        VIDEO_REMUX,
        // 视频重编码：解码后再编码，支持改分辨率、码率、帧率与编码格式。
        VIDEO_TRANSCODE,
        // 视频抽帧：在指定时间点取关键帧并存为 PNG。
        EXTRACT_FRAMES,
        // 音频重封装：只换容器，保持原始音频编码。
        AUDIO_REMUX,
        // 音频重编码：解码后编码为 AAC，支持改采样率、声道与码率。
        AUDIO_TRANSCODE,
        // 图片转换：解码后按目标格式与质量重新压缩，可选缩放。
        IMAGE_CONVERT,
        // 图片合成 PDF：多张图片按页写入一个 PDF。
        IMAGES_TO_PDF,
        // PDF 渲染为图片：逐页渲染为 PNG。
        PDF_TO_IMAGES,
        // 文本转 PDF：把纯文本按页排版绘制到 PDF。
        TEXT_TO_PDF
    }
}
