package com.xu.universaltranscoder.core;

// 引入 ArrayList，用于构建预设清单

import java.util.ArrayList;
// 引入 Collections，把清单包装为不可变列表
import java.util.Collections;
// 引入 List 接口，作为预设清单的类型

import java.util.List;

// 关键字 class：PresetLibrary 提供 35 套内置预设，按视频 14 套、音频 11 套、图片 10 套分组
// 之所以内置预设：多数用户无法独立推导出合适的码率与分辨率组合，预设能让常见场景一键可用
public final class PresetLibrary {

    // 全部预设清单，静态构建后作为只读数据长期驻留内存
    private static final List<Preset> PRESETS = build();

    // 私有构造方法，禁止实例化这个纯静态预设库
    private PresetLibrary() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 获取全部预设的只读清单
    public static List<Preset> all() {
        // 返回不可变清单
        return PRESETS;
    }

    // 按类别筛选预设，供界面在对应标签页展示
    public static List<Preset> byCategory(ConversionRequest.Category category) {
        // 准备结果列表
        List<Preset> result = new ArrayList<>();
        // 遍历全部预设
        for (Preset preset : PRESETS) {
            // 类别匹配时加入结果
            if (preset.category() == category) {
                // 加入结果列表
                result.add(preset);
            }
        }
        // 返回筛选结果
        return Collections.unmodifiableList(result);
    }

    // 按名称查找预设，名称匹配忽略大小写
    public static Preset byName(String name) {
        // 名称为空时返回 null
        if (name == null || name.isEmpty()) {
            // 返回 null 表示未找到
            return null;
        }
        // 遍历全部预设
        for (Preset preset : PRESETS) {
            // 名称忽略大小写相等即命中
            if (preset.name().equalsIgnoreCase(name)) {
                // 返回命中的预设
                return preset;
            }
        }
        // 未命中时返回 null
        return null;
    }

    // 构建全部 35 套预设
    private static List<Preset> build() {
        // 使用有序列表保存预设，顺序即界面展示顺序
        List<Preset> presets = new ArrayList<>();
        // 视频类别简写，减少重复书写
        ConversionRequest.Category video = ConversionRequest.Category.VIDEO;
        // 音频类别简写
        ConversionRequest.Category audio = ConversionRequest.Category.AUDIO;
        // 图片类别简写
        ConversionRequest.Category image = ConversionRequest.Category.IMAGE;

        // === 视频预设：共 14 套 ===
        // 通用 1080p H.264 高质量输出，适合网盘归档
        presets.add(new Preset("MP4 H.264 1080p 高画质", video, "mp4", Quality.HIGH, 1920, 1080, 30d, 6000, 256, "", "", 1.0d,
                // 说明文本展示给用户
                "兼容性极广的 1080p H.264 输出。"));
        // 720p 中等质量，体积与画质平衡，适合分享
        presets.add(new Preset("MP4 H.264 720p 均衡", video, "mp4", Quality.MEDIUM, 1280, 720, 30d, 3000, 160, "", "", 1.0d,
                // 说明文本展示给用户
                "体积与画质均衡的 720p 方案，适合日常分享。"));
        // 480p 小体积，适合聊天工具与邮件附件
        presets.add(new Preset("MP4 小体积 480p", video, "mp4", Quality.LOW, 854, 480, 25d, 900, 96, "", "", 1.0d,
                // 说明文本展示给用户
                "体积很小，适合聊天工具与邮件附件。"));
        // H.265 1080p，压缩效率更高，适合本地长期保存
        presets.add(new Preset("MP4 H.265 1080p 高画质", video, "mp4", Quality.HIGH, 1920, 1080, 30d, 0, 256, "libx265", "", 1.0d,
                // 说明文本展示给用户
                "压缩效率更高的 H.265 1080p 输出。"));
        // 4K 极高质量归档，保留最多画面细节
        presets.add(new Preset("MP4 H.264 4K 归档", video, "mp4", Quality.VERY_HIGH, 3840, 2160, 30d, 16000, 320, "", "", 1.0d,
                // 说明文本展示给用户
                "高码率的 4K 归档方案。"));
        // MKV 归档，允许封装几乎任何编码
        presets.add(new Preset("MKV H.264 归档", video, "mkv", Quality.VERY_HIGH, 0, 0, 0d, 0, 256, "", "", 1.0d,
                // 说明文本展示给用户
                "Matroska 归档封装，保持原始尺寸。"));
        // MOV 1080p，面向剪辑软件的中转素材
        presets.add(new Preset("MOV H.264 1080p", video, "mov", Quality.HIGH, 1920, 1080, 30d, 6000, 256, "", "", 1.0d,
                // 说明文本展示给用户
                "适合剪辑软件使用的 QuickTime 素材。"));
        // WebM VP9，适合网页播放且无授权限制
        presets.add(new Preset("WebM VP9 1080p", video, "webm", Quality.HIGH, 1920, 1080, 30d, 0, 0, "libvpx-vp9", "libopus", 1.0d,
                // 说明文本展示给用户
                "面向网页播放的 VP9 输出，音频为 Opus。"));
        // AVI 老式封装，兼容老旧播放器
        presets.add(new Preset("AVI MPEG-4 720p", video, "avi", Quality.MEDIUM, 1280, 720, 25d, 2500, 160, "mpeg4", "libmp3lame", 1.0d,
                // 说明文本展示给用户
                "面向老旧播放器的传统 AVI 方案。"));
        // FLV 低分辨率，面向网页直播与旧平台
        presets.add(new Preset("FLV H.264 480p", video, "flv", Quality.LOW, 854, 480, 25d, 800, 96, "libx264", "aac", 1.0d,
                // 说明文本展示给用户
                "低码率的 Flash 视频方案。"));
        // WMV/ASF 720p，面向 Windows Media 生态
        presets.add(new Preset("WMV 720p", video, "wmv", Quality.MEDIUM, 1280, 720, 25d, 2500, 160, "wmv2", "wmav2", 1.0d,
                // 说明文本展示给用户
                "面向旧式工具的 Windows Media 方案。"));
        // MPEG-2 标准码流，面向机顶盒与老式影碟流程
        presets.add(new Preset("MPG MPEG-2 720p", video, "mpg", Quality.HIGH, 1280, 720, 25d, 8000, 224, "mpeg2video", "mp2", 1.0d,
                // 说明文本展示给用户
                "适合 MPEG-2 传输流的方案。"));
        // MPEG-TS，适合直接投给广播链路或机顶盒
        presets.add(new Preset("TS H.264 1080p", video, "ts", Quality.HIGH, 1920, 1080, 30d, 7000, 192, "", "aac", 1.0d,
                // 说明文本展示给用户
                "面向广播播放的传输流方案。"));
        // GIF 动图，低分辨率加低帧率以控制体积
        presets.add(new Preset("GIF 动图 480p", video, "gif", Quality.LOW, 480, 0, 12d, 0, 0, "gif", "", 1.0d,
                // 说明文本展示给用户
                "降低帧率的 GIF 动图。"));

        // === 音频预设：共 11 套 ===
        // MP3 320k，主流有损编码的最高档位
        presets.add(new Preset("MP3 320kbps", audio, "mp3", Quality.VERY_HIGH, 0, 0, 0d, 0, 320, "", "libmp3lame", 1.0d,
                // 说明文本展示给用户
                "有损 MP3 中的最高音质。"));
        // MP3 128k，兼容性优先的小体积档位
        presets.add(new Preset("MP3 128kbps", audio, "mp3", Quality.MEDIUM, 0, 0, 0d, 0, 128, "", "libmp3lame", 1.0d,
                // 说明文本展示给用户
                "体积小巧，适合便携播放器。"));
        // M4A AAC 256k，移动端与苹果设备常用
        presets.add(new Preset("M4A AAC 256kbps", audio, "m4a", Quality.HIGH, 0, 0, 0d, 0, 256, "", "aac", 1.0d,
                // 说明文本展示给用户
                "M4A 封装 AAC，适合移动设备。"));
        // 裸 AAC 流 192k，适合二次加工
        presets.add(new Preset("AAC ADTS 192kbps", audio, "aac", Quality.HIGH, 0, 0, 0d, 0, 192, "", "aac", 1.0d,
                // 说明文本展示给用户
                "裸 ADTS AAC 音频流。"));
        // WAV 无损 PCM，适合音频后期处理
        presets.add(new Preset("WAV PCM 16 位", audio, "wav", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "pcm_s16le", 1.0d,
                // 说明文本展示给用户
                "未压缩 WAV，适合音频后期处理。"));
        // FLAC 无损压缩，兼顾体积与无损
        presets.add(new Preset("FLAC 无损", audio, "flac", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "flac", 1.0d,
                // 说明文本展示给用户
                "无损 FLAC 压缩。"));
        // OGG Vorbis 192k，开源生态常用
        presets.add(new Preset("OGG Vorbis 192kbps", audio, "ogg", Quality.HIGH, 0, 0, 0d, 0, 192, "", "libvorbis", 1.0d,
                // 说明文本展示给用户
                "OGG 封装 Vorbis 音频。"));
        // Opus 128k，语音与音乐都可胜任
        presets.add(new Preset("Opus 128kbps", audio, "opus", Quality.HIGH, 0, 0, 0d, 0, 128, "", "libopus", 1.0d,
                // 说明文本展示给用户
                "现代 Opus 音频，低码率下依然高效。"));
        // WMA 192k，面向 Windows 生态
        presets.add(new Preset("WMA 192kbps", audio, "wma", Quality.HIGH, 0, 0, 0d, 0, 192, "", "wmav2", 1.0d,
                // 说明文本展示给用户
                "Windows Media Audio 方案。"));
        // AC3 448k，面向家庭影院多声道场景
        presets.add(new Preset("AC3 448kbps", audio, "ac3", Quality.VERY_HIGH, 0, 0, 0d, 0, 448, "", "ac3", 1.0d,
                // 说明文本展示给用户
                "面向家庭影院的杜比 AC-3 方案。"));
        // MKA 封装 AAC，允许承载多条音轨
        presets.add(new Preset("MKA AAC 256kbps", audio, "mka", Quality.HIGH, 0, 0, 0d, 0, 256, "", "aac", 1.0d,
                // 说明文本展示给用户
                "Matroska 音频封装，编码为 AAC。"));

        // === 图片预设：共 10 套 ===
        // JPG 高质量，兼顾体积与画质
        presets.add(new Preset("JPG 高画质", image, "jpg", Quality.HIGH, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "采用高画质设置的 JPEG。"));
        // JPG 限制到 1280 宽度，适合网页与邮件
        presets.add(new Preset("JPG 网页 1280px", image, "jpg", Quality.MEDIUM, 1280, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "缩放后的 JPEG，适合网页与邮件。"));
        // PNG 无损，保留透明通道
        presets.add(new Preset("PNG 无损", image, "png", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "无损 PNG，保留透明通道。"));
        // WebP 高质量，体积通常远小于 PNG
        presets.add(new Preset("WebP 高画质", image, "webp", Quality.HIGH, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "压缩效果显著的 WebP 图片。"));
        // BMP 无压缩位图，面向老旧软件
        presets.add(new Preset("BMP 无压缩", image, "bmp", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "无压缩位图，面向旧式软件。"));
        // TIFF 面向印刷与图像处理流程
        presets.add(new Preset("TIFF 印刷就绪", image, "tiff", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "适合印刷流程的 TIFF 输出。"));
        // TGA 面向贴图与游戏素材
        presets.add(new Preset("TGA 贴图", image, "tga", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "面向贴图流程的 TGA 图像。"));
        // PPM 面向简单像素数据交换
        presets.add(new Preset("PPM 原始", image, "ppm", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "可移植像素图原始数据。"));
        // PGM 面向灰度数据交换
        presets.add(new Preset("PGM 灰度", image, "pgm", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "可移植灰度图。"));
        // EXR 面向高动态范围合成
        presets.add(new Preset("EXR 高动态范围", image, "exr", Quality.LOSSLESS, 0, 0, 0d, 0, 0, "", "", 1.0d,
                // 说明文本展示给用户
                "高动态范围 EXR 图像。"));

        // 返回不可变清单，防止外部修改内置预设
        return Collections.unmodifiableList(presets);
    }
}
