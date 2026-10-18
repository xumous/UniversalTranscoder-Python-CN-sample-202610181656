// *****************************************************************************
// presets.cpp —— 内置预设定义表。
// 说明：以下参数均为“经验上兼容性最好”的选择（而非画质最优选择），
//       目标是“随便挑一个都能在手机/电视/电脑上正常播放”。
//       name 与 note 会显示给用户，因此一律使用英文。
// *****************************************************************************
#include "presets.h"

// 视频预设（14 套）。
static QVector<Preset> videoPresets()
{
    // 结果容器。
    QVector<Preset> list;
    // ---- 1) MP4 + H.264 + 高质量 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp4_h264_high");
        // 显示名。
        p.name = QStringLiteral("MP4 - H.264 - High quality (universal)");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mp4");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("high");
        // CRF 值。
        p.crf = QStringLiteral("20");
        // 音频码率。
        p.aBitrate = QStringLiteral("192k");
        // 启用快启动。
        p.faststart = true;
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("Best compatibility, suitable for phones, TVs and web playback.");
        // 加入列表。
        list.append(p);
    }
    // ---- 2) MP4 + H.264 + 标准（推荐）----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp4_h264_std");
        // 显示名。
        p.name = QStringLiteral("MP4 - H.264 - Standard (recommended)");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mp4");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("23");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 启用快启动。
        p.faststart = true;
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("The everyday sweet spot between size and quality.");
        // 加入列表。
        list.append(p);
    }
    // ---- 3) MP4 + H.264 + 小体积 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp4_h264_small");
        // 显示名。
        p.name = QStringLiteral("MP4 - H.264 - Small size");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mp4");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("low");
        // CRF 值。
        p.crf = QStringLiteral("28");
        // 音频码率。
        p.aBitrate = QStringLiteral("96k");
        // 启用快启动。
        p.faststart = true;
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("For sharing and uploading, noticeably reduces the file size.");
        // 加入列表。
        list.append(p);
    }
    // ---- 4) MP4 + H.265/HEVC ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp4_hevc");
        // 显示名。
        p.name = QStringLiteral("MP4 - H.265/HEVC - High compression");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mp4");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx265");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("28");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 启用快启动。
        p.faststart = true;
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("Smaller files at the same quality, but older devices may not play it.");
        // 加入列表。
        list.append(p);
    }
    // ---- 5) MKV + H.264 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mkv_h264");
        // 显示名。
        p.name = QStringLiteral("MKV - H.264 - Highly compatible container");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mkv");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("23");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("MKV can carry multiple audio tracks and subtitles.");
        // 加入列表。
        list.append(p);
    }
    // ---- 6) WebM + VP9 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("webm_vp9");
        // 显示名。
        p.name = QStringLiteral("WebM - VP9 - Web friendly");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("webm");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libvpx-vp9");
        // 音频编码器。
        p.audioCodec = QStringLiteral("libopus");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("32");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("Royalty free and open source, great for embedding in web pages.");
        // 加入列表。
        list.append(p);
    }
    // ---- 7) WebM + VP8 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("webm_vp8");
        // 显示名。
        p.name = QStringLiteral("WebM - VP8 - Older devices");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("webm");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libvpx");
        // 音频编码器。
        p.audioCodec = QStringLiteral("libvorbis");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("10");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("Compatible with older browsers and devices.");
        // 加入列表。
        list.append(p);
    }
    // ---- 8) AVI + Xvid ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("avi_xvid");
        // 显示名。
        p.name = QStringLiteral("AVI - Xvid - Legacy players");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("avi");
        // 视频编码器。
        p.videoCodec = QStringLiteral("mpeg4");
        // 音频编码器。
        p.audioCodec = QStringLiteral("libmp3lame");
        // 质量档位。
        p.quality = QStringLiteral("high");
        // 视频码率。
        p.vBitrate = QStringLiteral("2500k");
        // 音频码率。
        p.aBitrate = QStringLiteral("192k");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("For old players and DVD units that cannot handle MP4.");
        // 加入列表。
        list.append(p);
    }
    // ---- 9) MOV + ProRes ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mov_prores");
        // 显示名。
        p.name = QStringLiteral("MOV - ProRes - Editing intermediate");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mov");
        // 视频编码器。
        p.videoCodec = QStringLiteral("prores_ks");
        // 音频编码器。
        p.audioCodec = QStringLiteral("pcm_s16le");
        // 质量档位。
        p.quality = QStringLiteral("high");
        // 像素格式（ProRes 需要 10bit 4:2:2）。
        p.pixelFormat = QStringLiteral("yuv422p10le");
        // 说明文本。
        p.note = QStringLiteral("Large files but very smooth scrubbing in editing software.");
        // 加入列表。
        list.append(p);
    }
    // ---- 10) MOV + DNxHD ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mov_dnxhd");
        // 显示名。
        p.name = QStringLiteral("MOV - DNxHD - Broadcast intermediate");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mov");
        // 视频编码器。
        p.videoCodec = QStringLiteral("dnxhd");
        // 音频编码器。
        p.audioCodec = QStringLiteral("pcm_s16le");
        // 质量档位。
        p.quality = QStringLiteral("high");
        // 视频码率（DNxHD 需要固定码率，36M 对应 1080p 中等档）。
        p.vBitrate = QStringLiteral("36M");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv422p");
        // 说明文本。
        p.note = QStringLiteral("Common intermediate codec in broadcast and post production.");
        // 加入列表。
        list.append(p);
    }
    // ---- 11) FLV + H.264 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("flv_h264");
        // 显示名。
        p.name = QStringLiteral("FLV - H.264 - Streaming / web");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("flv");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("26");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("Suits live streaming and legacy web players.");
        // 加入列表。
        list.append(p);
    }
    // ---- 12) TS + H.264 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("ts_h264");
        // 显示名。
        p.name = QStringLiteral("TS - H.264 - Transport stream");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("ts");
        // 视频编码器。
        p.videoCodec = QStringLiteral("libx264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("24");
        // 音频码率。
        p.aBitrate = QStringLiteral("192k");
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("For TV and broadcast transport stream scenarios.");
        // 加入列表。
        list.append(p);
    }
    // ---- 13) GIF 动图 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("gif_anim");
        // 显示名。
        p.name = QStringLiteral("GIF - Animated");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("gif");
        // 视频编码器。
        p.videoCodec = QStringLiteral("gif");
        // 音频编码器留空（GIF 不能承载音频）。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 附加视频滤镜：限制帧率与宽度，避免动图体积爆炸。
        p.extraVf = QStringLiteral("fps=12,scale=480:-2:flags=lanczos");
        // 说明文本。
        p.note = QStringLiteral("Creates an animated image from video with a sane frame rate and width.");
        // 加入列表。
        list.append(p);
    }
    // ---- 14) MP4 + 硬件加速 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp4_hw_nvenc");
        // 显示名。
        p.name = QStringLiteral("MP4 - H.264 - GPU accelerated (auto)");
        // 类别。
        p.category = QStringLiteral("video");
        // 容器。
        p.container = QStringLiteral("mp4");
        // 视频编码器使用特殊标记：由命令构建器解析为“自动挑选可用的硬件 H.264 编码器”。
        p.videoCodec = QStringLiteral("auto_hw_h264");
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // CRF 值。
        p.crf = QStringLiteral("23");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 启用快启动。
        p.faststart = true;
        // 像素格式。
        p.pixelFormat = QStringLiteral("yuv420p");
        // 说明文本。
        p.note = QStringLiteral("Prefers NVIDIA/Intel/AMD hardware encoders and falls back to software automatically.");
        // 加入列表。
        list.append(p);
    }
    // 返回视频预设列表。
    return list;
}

// 音频预设（11 套）。
static QVector<Preset> audioPresets()
{
    // 结果容器。
    QVector<Preset> list;
    // ---- 1) MP3 320k ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp3_320");
        // 显示名。
        p.name = QStringLiteral("MP3 - 320 kbps - High quality");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("mp3");
        // 无视频编码器。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("libmp3lame");
        // 质量档位。
        p.quality = QStringLiteral("high");
        // 音频码率。
        p.aBitrate = QStringLiteral("320k");
        // 说明文本。
        p.note = QStringLiteral("Highest common MP3 bitrate, plays everywhere.");
        // 加入列表。
        list.append(p);
    }
    // ---- 2) MP3 192k ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp3_192");
        // 显示名。
        p.name = QStringLiteral("MP3 - 192 kbps - Standard");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("mp3");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("libmp3lame");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 音频码率。
        p.aBitrate = QStringLiteral("192k");
        // 说明文本。
        p.note = QStringLiteral("Balanced size and quality, the general recommendation.");
        // 加入列表。
        list.append(p);
    }
    // ---- 3) MP3 128k ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("mp3_128");
        // 显示名。
        p.name = QStringLiteral("MP3 - 128 kbps - Small size");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("mp3");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("libmp3lame");
        // 质量档位。
        p.quality = QStringLiteral("low");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 说明文本。
        p.note = QStringLiteral("Good for speech and podcasts.");
        // 加入列表。
        list.append(p);
    }
    // ---- 4) AAC / M4A ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("aac_m4a");
        // 显示名。
        p.name = QStringLiteral("AAC - M4A - Apple ecosystem");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("m4a");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("aac");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 音频码率。
        p.aBitrate = QStringLiteral("192k");
        // 说明文本。
        p.note = QStringLiteral("Natively supported by Apple devices.");
        // 加入列表。
        list.append(p);
    }
    // ---- 5) FLAC 无损 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("flac_lossless");
        // 显示名。
        p.name = QStringLiteral("FLAC - Lossless");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("flac");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("flac");
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("Lossless at roughly half the size of WAV.");
        // 加入列表。
        list.append(p);
    }
    // ---- 6) WAV / PCM ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("wav_pcm");
        // 显示名。
        p.name = QStringLiteral("WAV - PCM - Uncompressed");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("wav");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("pcm_s16le");
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("Standard uncompressed audio for editing and mastering.");
        // 加入列表。
        list.append(p);
    }
    // ---- 7) OGG / Vorbis ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("ogg_vorbis");
        // 显示名。
        p.name = QStringLiteral("OGG - Vorbis");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("ogg");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("libvorbis");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 音频码率。
        p.aBitrate = QStringLiteral("160k");
        // 说明文本。
        p.note = QStringLiteral("Open source format commonly used on the web.");
        // 加入列表。
        list.append(p);
    }
    // ---- 8) OPUS ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("opus_webm");
        // 显示名。
        p.name = QStringLiteral("OPUS - High compression");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("opus");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("libopus");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 说明文本。
        p.note = QStringLiteral("Better quality than MP3 at the same bitrate, great for voice.");
        // 加入列表。
        list.append(p);
    }
    // ---- 9) WMA ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("wma_asf");
        // 显示名。
        p.name = QStringLiteral("WMA - Windows Media");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器（WMA 的真实封装是 asf）。
        p.container = QStringLiteral("asf");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("wmav2");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 音频码率。
        p.aBitrate = QStringLiteral("128k");
        // 说明文本。
        p.note = QStringLiteral("Compatible with legacy Windows players.");
        // 加入列表。
        list.append(p);
    }
    // ---- 10) AC3 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("ac3_dolby");
        // 显示名。
        p.name = QStringLiteral("AC3 - Dolby Digital");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("ac3");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器。
        p.audioCodec = QStringLiteral("ac3");
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 音频码率。
        p.aBitrate = QStringLiteral("384k");
        // 说明文本。
        p.note = QStringLiteral("Common on home theatre systems and set-top boxes.");
        // 加入列表。
        list.append(p);
    }
    // ---- 11) M4A 直接封装 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("m4a_copy");
        // 显示名。
        p.name = QStringLiteral("M4A - Remux only (no re-encode)");
        // 类别。
        p.category = QStringLiteral("audio");
        // 容器。
        p.container = QStringLiteral("m4a");
        // 无视频。
        p.videoCodec = QString();
        // 音频编码器使用 "copy" 表示不重编码、只换容器。
        p.audioCodec = QStringLiteral("copy");
        // 质量档位（无损）。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("Only rewraps the container: extremely fast and lossless, source must be AAC.");
        // 加入列表。
        list.append(p);
    }
    // 返回音频预设列表。
    return list;
}

// 图片预设（10 套）。
static QVector<Preset> imagePresets()
{
    // 结果容器。
    QVector<Preset> list;
    // ---- 1) JPG 高质量 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("jpg_high");
        // 显示名。
        p.name = QStringLiteral("JPG - High quality (95)");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("jpg");
        // 图片预设不含音频与视频编码器（编码器由命令构建器按目标格式自动匹配）。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("high");
        // 附加参数：-q:v 2 约等于质量 95。
        p.extra = QStringList{QStringLiteral("-q:v"), QStringLiteral("2")};
        // 说明文本。
        p.note = QStringLiteral("The common choice for photo sharing.");
        // 加入列表。
        list.append(p);
    }
    // ---- 2) JPG 标准 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("jpg_std");
        // 显示名。
        p.name = QStringLiteral("JPG - Standard (85)");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("jpg");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 附加参数。
        p.extra = QStringList{QStringLiteral("-q:v"), QStringLiteral("5")};
        // 说明文本。
        p.note = QStringLiteral("Balanced size and quality.");
        // 加入列表。
        list.append(p);
    }
    // ---- 3) PNG 无损 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("png_lossless");
        // 显示名。
        p.name = QStringLiteral("PNG - Lossless (alpha supported)");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("png");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 附加参数：压缩级别 6（速度与体积的平衡点）。
        p.extra = QStringList{QStringLiteral("-compression_level"), QStringLiteral("6")};
        // 说明文本。
        p.note = QStringLiteral("Use when you need transparency or perfect fidelity.");
        // 加入列表。
        list.append(p);
    }
    // ---- 4) WebP 有损 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("webp_q");
        // 显示名。
        p.name = QStringLiteral("WebP - Lossy");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("webp");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 附加参数：质量 80。
        p.extra = QStringList{QStringLiteral("-q:v"), QStringLiteral("80")};
        // 说明文本。
        p.note = QStringLiteral("Small files for web pages, supported by modern browsers.");
        // 加入列表。
        list.append(p);
    }
    // ---- 5) WebP 无损 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("webp_lossless");
        // 显示名。
        p.name = QStringLiteral("WebP - Lossless");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("webp");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 附加参数：开启无损模式。
        p.extra = QStringList{QStringLiteral("-lossless"), QStringLiteral("1")};
        // 说明文本。
        p.note = QStringLiteral("Lossless yet still smaller than PNG.");
        // 加入列表。
        list.append(p);
    }
    // ---- 6) BMP ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("bmp_raw");
        // 显示名。
        p.name = QStringLiteral("BMP - Uncompressed bitmap");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("bmp");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("Widest compatibility with legacy software.");
        // 加入列表。
        list.append(p);
    }
    // ---- 7) TIFF ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("tiff_print");
        // 显示名。
        p.name = QStringLiteral("TIFF - Print / archive");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("tiff");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("Common for printing and long term archiving.");
        // 加入列表。
        list.append(p);
    }
    // ---- 8) GIF 静态图 ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("gif_image");
        // 显示名。
        p.name = QStringLiteral("GIF - Still image");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("gif");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 说明文本。
        p.note = QStringLiteral("Excellent compatibility, but limited to 256 colours.");
        // 加入列表。
        list.append(p);
    }
    // ---- 9) AVIF ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("avif_modern");
        // 显示名。
        p.name = QStringLiteral("AVIF - Modern high compression");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("avif");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 附加参数：质量 30（AVIF 的量化步长范围与 JPEG 不同）。
        p.extra = QStringList{QStringLiteral("-q:v"), QStringLiteral("30")};
        // 说明文本。
        p.note = QStringLiteral("Smallest files, requires recent software to view.");
        // 加入列表。
        list.append(p);
    }
    // ---- 10) ICO ----
    {
        // 构造预设。
        Preset p;
        // 唯一键。
        p.key = QStringLiteral("ico_icon");
        // 显示名。
        p.name = QStringLiteral("ICO - Application icon");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("ico");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 附加参数：图标需要固定尺寸，这里统一缩放到 256x256。
        p.extra = QStringList{QStringLiteral("-vf"), QStringLiteral("scale=256:256")};
        // 说明文本。
        p.note = QStringLiteral("Produces a Windows icon file.");
        // 加入列表。
        list.append(p);
    }
    // 返回图片预设列表。
    return list;
}

// 返回全部预设。
QVector<Preset> allPresets()
{
    // 结果容器。
    QVector<Preset> all;
    // 追加视频预设。
    all += videoPresets();
    // 追加音频预设。
    all += audioPresets();
    // 追加图片预设。
    all += imagePresets();
    // 返回合并结果（共 14 + 11 + 10 = 35 套）。
    return all;
}

// 按类别筛选。
QVector<Preset> presetsByCategory(const QString& category)
{
    // 结果容器。
    QVector<Preset> out;
    // 遍历全部预设。
    for (const Preset& p : allPresets())
    {
        // 类别一致则收集。
        if (p.category == category)
        {
            // 追加到结果。
            out.append(p);
        }
    }
    // 返回结果。
    return out;
}

// 按 key 查找。
Preset presetByKey(const QString& key)
{
    // 遍历全部预设。
    for (const Preset& p : allPresets())
    {
        // 命中 key 则返回。
        if (p.key == key)
        {
            // 返回该预设的副本。
            return p;
        }
    }
    // 未找到：返回默认构造的空预设（key 为空字符串，调用方据此判断“没找到”）。
    return Preset();
}