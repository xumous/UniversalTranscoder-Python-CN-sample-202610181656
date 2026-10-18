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
        p.name = QStringLiteral("MP4 - H.264 - 高质量（通用）");
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
        p.note = QStringLiteral("兼容性最佳，适合手机、电视与网页播放。");
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
        p.name = QStringLiteral("MP4 - H.264 - 标准（推荐）");
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
        p.note = QStringLiteral("体积与画质的日常平衡之选。");
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
        p.name = QStringLiteral("MP4 - H.264 - 小体积");
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
        p.note = QStringLiteral("适合分享与上传，可明显减小文件体积。");
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
        p.name = QStringLiteral("MP4 - H.265/HEVC - 高压缩");
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
        p.note = QStringLiteral("同画质下体积更小，但老旧设备可能无法播放。");
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
        p.name = QStringLiteral("MKV - H.264 - 高兼容容器");
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
        p.note = QStringLiteral("MKV 可承载多条音轨与字幕。");
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
        p.name = QStringLiteral("WebM - VP9 - 适合网页");
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
        p.note = QStringLiteral("免版税的开源格式，适合嵌入网页。");
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
        p.name = QStringLiteral("WebM - VP8 - 兼容老旧设备");
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
        p.note = QStringLiteral("兼容较旧的浏览器与设备。");
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
        p.name = QStringLiteral("AVI - Xvid - 老式播放器");
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
        p.note = QStringLiteral("适合无法播放 MP4 的老式播放器与 DVD 机。");
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
        p.name = QStringLiteral("MOV - ProRes - 剪辑中间格式");
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
        p.note = QStringLiteral("文件较大，但在剪辑软件中拖动预览非常流畅。");
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
        p.name = QStringLiteral("MOV - DNxHD - 广播中间格式");
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
        p.note = QStringLiteral("广播与后期制作中常见的中间编码。");
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
        p.name = QStringLiteral("FLV - H.264 - 流媒体 / 网页");
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
        p.note = QStringLiteral("适合直播推流与老式网页播放器。");
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
        p.name = QStringLiteral("TS - H.264 - 传输流");
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
        p.note = QStringLiteral("适合电视与广播传输流场景。");
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
        p.name = QStringLiteral("GIF - 动图");
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
        p.note = QStringLiteral("以合理的帧率与宽度把视频做成动图。");
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
        p.name = QStringLiteral("MP4 - H.264 - GPU 加速（自动）");
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
        p.note = QStringLiteral("优先使用 NVIDIA/Intel/AMD 硬件编码器，失败时自动回退软件编码。");
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
        p.name = QStringLiteral("MP3 - 320 kbps - 高质量");
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
        p.note = QStringLiteral("常见的最高 MP3 码率，各处都能播放。");
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
        p.name = QStringLiteral("MP3 - 192 kbps - 标准");
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
        p.note = QStringLiteral("体积与音质均衡，通用推荐。");
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
        p.name = QStringLiteral("MP3 - 128 kbps - 小体积");
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
        p.note = QStringLiteral("适合语音与播客。");
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
        p.name = QStringLiteral("AAC - M4A - 苹果生态");
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
        p.note = QStringLiteral("苹果设备原生支持。");
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
        p.name = QStringLiteral("FLAC - 无损");
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
        p.note = QStringLiteral("无损格式，体积约为 WAV 的一半。");
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
        p.name = QStringLiteral("WAV - PCM - 未压缩");
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
        p.note = QStringLiteral("用于剪辑与母带处理的标准未压缩音频。");
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
        p.note = QStringLiteral("网页上常见的开源格式。");
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
        p.name = QStringLiteral("OPUS - 高压缩");
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
        p.note = QStringLiteral("同码率下音质优于 MP3，尤其适合语音。");
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
        p.note = QStringLiteral("兼容老式 Windows 播放器。");
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
        p.name = QStringLiteral("AC3 - 杜比数字");
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
        p.note = QStringLiteral("家庭影院与机顶盒上常见。");
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
        p.name = QStringLiteral("M4A - 仅换容器（不重新编码）");
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
        p.note = QStringLiteral("只重新封装容器：极快且无损，要求源为 AAC。");
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
        p.name = QStringLiteral("JPG - 高质量（95）");
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
        p.note = QStringLiteral("照片分享的常用选择。");
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
        p.name = QStringLiteral("JPG - 标准（85）");
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
        p.note = QStringLiteral("体积与画质均衡。");
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
        p.name = QStringLiteral("PNG - 无损（支持透明通道）");
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
        p.note = QStringLiteral("需要透明或无损保真时使用。");
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
        p.name = QStringLiteral("WebP - 有损");
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
        p.note = QStringLiteral("网页小体积图片，现代浏览器均支持。");
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
        p.name = QStringLiteral("WebP - 无损");
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
        p.note = QStringLiteral("无损，体积仍小于 PNG。");
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
        p.name = QStringLiteral("BMP - 未压缩位图");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("bmp");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("与老式软件兼容性最好。");
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
        p.name = QStringLiteral("TIFF - 印刷 / 存档");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("tiff");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("lossless");
        // 说明文本。
        p.note = QStringLiteral("常用于印刷与长期存档。");
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
        p.name = QStringLiteral("GIF - 静态图");
        // 类别。
        p.category = QStringLiteral("image");
        // 容器。
        p.container = QStringLiteral("gif");
        // 无音频。
        p.audioCodec = QString();
        // 质量档位。
        p.quality = QStringLiteral("medium");
        // 说明文本。
        p.note = QStringLiteral("兼容性极好，但最多 256 色。");
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
        p.name = QStringLiteral("AVIF - 现代高压缩");
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
        p.note = QStringLiteral("体积最小，需要较新的软件才能查看。");
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
        p.name = QStringLiteral("ICO - 应用程序图标");
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
        p.note = QStringLiteral("生成 Windows 图标文件。");
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