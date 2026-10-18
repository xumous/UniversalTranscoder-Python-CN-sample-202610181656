package com.xu.universaltranscoder.model;

// 引入 MediaOp：为预设声明大类。

import com.xu.universaltranscoder.util.MediaOp;

// 引入 ArrayList：构建预设列表。
import java.util.ArrayList;
// 引入 Collections：对外返回只读列表。
import java.util.Collections;
// 引入 List：列表类型。
import java.util.List;

/**
 * PresetRepository 保存内置预设表。
 * 之所以内置而不是让用户手写：多数用户只想“把视频弄小一点”“把图片变成 Web 友好的 JPEG”，
 * 预设把这类常见诉求固化成可靠的参数组合，同时界面仍保留手动微调能力。
 */
public final class PresetRepository {

    // 全量预设缓存。
    private static ArrayList<Preset> all;

    // 私有构造：注册表不需要实例。
    private PresetRepository() {
        // 空实现，禁止外部 new。
    }

    /**
     * 惰性构建全部预设，首次访问时初始化。
     */
    private static synchronized void ensureBuilt() {
        // 已构建则直接返回。
        if (all != null) {
            // 无需重复构建。
            return;
        }
        // 新建列表。
        ArrayList<Preset> list = new ArrayList<>();

        // ---------- 视频预设 ----------
        // 高质量重封装：不重编码，保留原始画质，仅换/重建容器。
        ConvertOptions remuxOptions = new ConvertOptions();
        // 记录预设名称，便于日志。
        remuxOptions.presetName = "Full quality MP4 (remux)";
        // 加入预设列表。
        list.add(new Preset("Full quality MP4 (remux)", MediaOp.Category.VIDEO, "video.mp4.remux", remuxOptions));

        // 1080p H.264 网页友好版。
        ConvertOptions h264_1080 = new ConvertOptions();
        // 设置码率 8 Mbps，兼顾画质与体积。
        h264_1080.videoBitrateKbps = 8000;
        // 设置帧率 30。
        h264_1080.videoFrameRate = 30;
        // 设置目标分辨率 1920x1080。
        h264_1080.targetWidth = 1920;
        // 设置目标高度。
        h264_1080.targetHeight = 1080;
        // 音频码率 192kbps。
        h264_1080.audioBitrateKbps = 192;
        // 记录预设名称。
        h264_1080.presetName = "Web MP4 H.264 1080p";
        // 加入预设。
        list.add(new Preset("Web MP4 H.264 1080p", MediaOp.Category.VIDEO, "video.mp4.avc", h264_1080));

        // 720p H.264 平衡版。
        ConvertOptions h264_720 = new ConvertOptions();
        // 码率 4 Mbps。
        h264_720.videoBitrateKbps = 4000;
        // 帧率 30。
        h264_720.videoFrameRate = 30;
        // 分辨率 1280x720。
        h264_720.targetWidth = 1280;
        // 高度 720。
        h264_720.targetHeight = 720;
        // 音频码率 128kbps。
        h264_720.audioBitrateKbps = 128;
        // 记录预设名称。
        h264_720.presetName = "Balanced MP4 H.264 720p";
        // 加入预设。
        list.add(new Preset("Balanced MP4 H.264 720p", MediaOp.Category.VIDEO, "video.mp4.avc", h264_720));

        // 480p 小体积版，适合聊天分享。
        ConvertOptions h264_480 = new ConvertOptions();
        // 码率 1200 kbps。
        h264_480.videoBitrateKbps = 1200;
        // 帧率 24，进一步压缩体积。
        h264_480.videoFrameRate = 24;
        // 分辨率 854x480。
        h264_480.targetWidth = 854;
        // 高度 480。
        h264_480.targetHeight = 480;
        // 音频码率 96kbps。
        h264_480.audioBitrateKbps = 96;
        // 记录预设名称。
        h264_480.presetName = "Small file MP4 H.264 480p";
        // 加入预设。
        list.add(new Preset("Small file MP4 H.264 480p", MediaOp.Category.VIDEO, "video.mp4.avc", h264_480));

        // HEVC 1080p 省空间版。
        ConvertOptions hevc_1080 = new ConvertOptions();
        // HEVC 同画质下码率可更低，这里用 5000kbps。
        hevc_1080.videoBitrateKbps = 5000;
        // 帧率 30。
        hevc_1080.videoFrameRate = 30;
        // 分辨率 1920x1080。
        hevc_1080.targetWidth = 1920;
        // 高度 1080。
        hevc_1080.targetHeight = 1080;
        // 音频 128kbps。
        hevc_1080.audioBitrateKbps = 128;
        // 记录预设名称。
        hevc_1080.presetName = "HEVC 1080p (smaller file)";
        // 加入预设。
        list.add(new Preset("HEVC 1080p (smaller file)", MediaOp.Category.VIDEO, "video.mp4.hevc", hevc_1080));

        // 抽帧预设：均匀取 5 帧。
        ConvertOptions frames = new ConvertOptions();
        // 设置抽帧数量。
        frames.frameCount = 5;
        // 记录预设名称。
        frames.presetName = "Thumbnails (5 PNG frames)";
        // 加入预设。
        list.add(new Preset("Thumbnails (5 PNG frames)", MediaOp.Category.VIDEO, "video.frames.png", frames));

        // ---------- 音频预设 ----------
        // 标准 AAC 立体声。
        ConvertOptions aac128 = new ConvertOptions();
        // 码率 128kbps。
        aac128.audioBitrateKbps = 128;
        // 采样率 44100Hz。
        aac128.sampleRate = 44100;
        // 双声道。
        aac128.channels = 2;
        // 记录预设名称。
        aac128.presetName = "AAC 128 kbps stereo";
        // 加入预设。
        list.add(new Preset("AAC 128 kbps stereo", MediaOp.Category.AUDIO, "audio.m4a.aac", aac128));

        // 高品质 AAC。
        ConvertOptions aac192 = new ConvertOptions();
        // 码率 192kbps。
        aac192.audioBitrateKbps = 192;
        // 采样率 48000Hz。
        aac192.sampleRate = 48000;
        // 双声道。
        aac192.channels = 2;
        // 记录预设名称。
        aac192.presetName = "AAC 192 kbps 48 kHz";
        // 加入预设。
        list.add(new Preset("AAC 192 kbps 48 kHz", MediaOp.Category.AUDIO, "audio.m4a.aac", aac192));

        // 人声/语音用单声道低码率。
        ConvertOptions voice = new ConvertOptions();
        // 码率 64kbps。
        voice.audioBitrateKbps = 64;
        // 采样率 22050Hz。
        voice.sampleRate = 22050;
        // 单声道。
        voice.channels = 1;
        // 记录预设名称。
        voice.presetName = "Voice memo AAC 64 kbps mono";
        // 加入预设。
        list.add(new Preset("Voice memo AAC 64 kbps mono", MediaOp.Category.AUDIO, "audio.m4a.aac", voice));

        // 仅换容器不重编码。
        ConvertOptions audioRemux = new ConvertOptions();
        // 记录预设名称。
        audioRemux.presetName = "Keep original codec (remux to M4A)";
        // 加入预设。
        list.add(new Preset("Keep original codec (remux to M4A)", MediaOp.Category.AUDIO, "audio.m4a.remux", audioRemux));

        // ---------- 图片预设 ----------
        // Web 友好 JPEG。
        ConvertOptions jpg90 = new ConvertOptions();
        // 质量 90。
        jpg90.imageQuality = 90;
        // 记录预设名称。
        jpg90.presetName = "Web JPEG 90%";
        // 加入预设。
        list.add(new Preset("Web JPEG 90%", MediaOp.Category.IMAGE, "image.jpg", jpg90));

        // 体积优先 JPEG + 限宽 1920。
        ConvertOptions jpg75 = new ConvertOptions();
        // 质量 75。
        jpg75.imageQuality = 75;
        // 目标宽度 1920，高度 0 表示按比例自动计算。
        jpg75.imageTargetWidth = 1920;
        // 记录预设名称。
        jpg75.presetName = "JPEG 75% max width 1920";
        // 加入预设。
        list.add(new Preset("JPEG 75% max width 1920", MediaOp.Category.IMAGE, "image.jpg", jpg75));

        // 无损 PNG。
        ConvertOptions png = new ConvertOptions();
        // PNG 忽略质量参数，但为保持界面一致仍给出默认值。
        png.imageQuality = 100;
        // 记录预设名称。
        png.presetName = "Lossless PNG";
        // 加入预设。
        list.add(new Preset("Lossless PNG", MediaOp.Category.IMAGE, "image.png", png));

        // WEBP + 限宽 1280。
        ConvertOptions webp = new ConvertOptions();
        // 质量 80。
        webp.imageQuality = 80;
        // 目标宽度 1280。
        webp.imageTargetWidth = 1280;
        // 记录预设名称。
        webp.presetName = "WEBP 80% max width 1280";
        // 加入预设。
        list.add(new Preset("WEBP 80% max width 1280", MediaOp.Category.IMAGE, "image.webp", webp));

        // ---------- 文档预设 ----------
        // 图片合成 A4 PDF。
        ConvertOptions pdfA4 = new ConvertOptions();
        // 统一使用 A4 页面。
        pdfA4.pdfAutoPageSize = false;
        // 记录预设名称。
        pdfA4.presetName = "Images to A4 PDF";
        // 加入预设。
        list.add(new Preset("Images to A4 PDF", MediaOp.Category.DOCUMENT, "doc.images.pdf", pdfA4));

        // 图片自适应页面尺寸的 PDF。
        ConvertOptions pdfFit = new ConvertOptions();
        // 页面尺寸跟随图片比例。
        pdfFit.pdfAutoPageSize = true;
        // 记录预设名称。
        pdfFit.presetName = "Images to PDF (fit to image)";
        // 加入预设。
        list.add(new Preset("Images to PDF (fit to image)", MediaOp.Category.DOCUMENT, "doc.images.pdf", pdfFit));

        // PDF 逐页导出 PNG（全部页）。
        ConvertOptions pdfPages = new ConvertOptions();
        // 全部页。
        pdfPages.pageRange = "";
        // 记录预设名称。
        pdfPages.presetName = "PDF pages to PNG (all)";
        // 加入预设。
        list.add(new Preset("PDF pages to PNG (all)", MediaOp.Category.DOCUMENT, "doc.pdf.png", pdfPages));

        // 文本转 PDF。
        ConvertOptions textPdf = new ConvertOptions();
        // 字号 12pt。
        textPdf.pdfTextSize = 12;
        // 记录预设名称。
        textPdf.presetName = "Plain text to PDF 12pt";
        // 加入预设。
        list.add(new Preset("Plain text to PDF 12pt", MediaOp.Category.DOCUMENT, "doc.text.pdf", textPdf));

        // 写入缓存。
        all = list;
    }

    /**
     * 返回指定大类的预设列表。
     */
    public static List<Preset> forCategory(MediaOp.Category category) {
        // 确保已构建。
        ensureBuilt();
        // 新建结果列表。
        ArrayList<Preset> result = new ArrayList<>();
        // 遍历全部预设做过滤。
        for (Preset preset : all) {
            // 大类匹配则加入结果。
            if (preset.category == category) {
                // 收集匹配项。
                result.add(preset);
            }
        }
        // 返回只读结果。
        return Collections.unmodifiableList(result);
    }

    /**
     * 按名称查找预设，找不到返回 null。
     */
    public static Preset byName(String name) {
        // 确保已构建。
        ensureBuilt();
        // 遍历查找。
        for (Preset preset : all) {
            // 名称相等即命中。
            if (preset.name.equals(name)) {
                // 返回命中项。
                return preset;
            }
        }
        // 未找到。
        return null;
    }
}
