# -*- coding: utf-8 -*-
# 内置预设方案模块。
# 预设 = “一套开箱即用的完整转码参数”，让不了解编码细节的用户一键得到合理结果。
# 每个预设描述：目标容器、目标编码器、质量/码率策略、必要的附加滤镜或参数。
# 设计原则：
#   1) 预设只是普通字典，界面可以列出、复制、微调，Web 端也能复用同一份定义。
#   2) 优先选用“通用性最好”的编码器（H.264 用 libx264，音频用 aac/mp3），保证跨设备可播。

from dataclasses import dataclass, field  # 承载预设结构
from typing import List, Dict, Optional  # 类型标注


@dataclass
class Preset:
    """一个转码预设。"""
    key: str = ""  # 唯一标识（英文短名，作为配置记忆键）
    name: str = ""  # 显示名（中文）
    category: str = "video"  # 所属类别：video / audio / image / document
    container: str = ""  # 目标容器/扩展名（不含点，如 "mp4"）
    video_codec: str = ""  # 视频编码器（可为空，表示无视频）
    audio_codec: str = ""  # 音频编码器（可为空）
    quality: str = "medium"  # 质量档位：lossless/high/medium/low
    v_bitrate: str = ""  # 视频码率（如 "2500k"；空表示用 CRF 策略）
    a_bitrate: str = "128k"  # 音频码率（如 "128k"）
    crf: str = ""  # 恒定质量因子（x264 系列有效）
    extra_vf: str = ""  # 附加视频滤镜（如 "scale=-2:720"）
    extra_af: str = ""  # 附加音频滤镜
    faststart: bool = False  # 是否启用 MP4 网络快启动（moov 前置）
    pixel_format: str = "yuv420p"  # 目标像素格式（最大兼容性）
    note: str = ""  # 面向用户的一句话说明
    extra: List[str] = field(default_factory=list)  # 其它原样追加的 ffmpeg 参数


def _p(**kw) -> Preset:
    """构造预设的语法糖：让下面的定义表更紧凑、更易读。"""
    return Preset(**kw)


# ---------------------- 视频预设 ----------------------
# 说明：以下参数均为“经验上兼容性最好”的选择，而非最优画质选择。
VIDEO_PRESETS: List[Preset] = [
    _p(key="mp4_h264_high", name="MP4 · H.264 · High quality (universal)", category="video",
       container="mp4", video_codec="libx264", audio_codec="aac", quality="high",
       crf="20", a_bitrate="192k", faststart=True, pixel_format="yuv420p",
       note="Best compatibility; ideal for phone/TV/web playback."),
    _p(key="mp4_h264_std", name="MP4 · H.264 · Standard (recommended)", category="video",
       container="mp4", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="23", a_bitrate="128k", faststart=True, pixel_format="yuv420p",
       note="A balance of size and quality; the everyday first choice."),
    _p(key="mp4_h264_small", name="MP4 · H.264 · Small size", category="video",
       container="mp4", video_codec="libx264", audio_codec="aac", quality="low",
       crf="28", a_bitrate="96k", faststart=True, pixel_format="yuv420p",
       note="For sharing/uploading; noticeably smaller files."),
    _p(key="mp4_hevc", name="MP4 · H.265/HEVC · High compression", category="video",
       container="mp4", video_codec="libx265", audio_codec="aac", quality="medium",
       crf="28", a_bitrate="128k", faststart=True, pixel_format="yuv420p",
       note="Smaller files at the same quality, but slightly worse on older devices."),
    _p(key="mkv_h264", name="MKV · H.264 · High-compatibility container", category="video",
       container="mkv", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="23", a_bitrate="128k", pixel_format="yuv420p",
       note="MKV can hold multiple audio tracks/subtitles; good for archiving."),
    _p(key="webm_vp9", name="WebM · VP9 · Web friendly", category="video",
       container="webm", video_codec="libvpx-vp9", audio_codec="libopus", quality="medium",
       crf="32", a_bitrate="128k", pixel_format="yuv420p",
       note="Open and patent-free; good for embedding in web pages."),
    _p(key="webm_vp8", name="WebM · VP8 · Older devices", category="video",
       container="webm", video_codec="libvpx", audio_codec="libvorbis", quality="medium",
       crf="10", a_bitrate="128k", pixel_format="yuv420p",
       note="Compatible with older browsers and devices."),
    _p(key="avi_xvid", name="AVI · Xvid · Legacy players", category="video",
       container="avi", video_codec="mpeg4", audio_codec="libmp3lame", quality="high",
       v_bitrate="2500k", a_bitrate="192k", pixel_format="yuv420p",
       note="For old players/DVD players that do not support MP4."),
    _p(key="mov_prores", name="MOV · ProRes · Editing intermediate", category="video",
       container="mov", video_codec="prores_ks", audio_codec="pcm_s16le", quality="high",
       pixel_format="yuv422p10le",
       note="For professional editing workflows; large but smooth to edit."),
    _p(key="mov_dnxhd", name="MOV · DNxHD · Broadcast intermediate", category="video",
       container="mov", video_codec="dnxhd", audio_codec="pcm_s16le", quality="high",
       v_bitrate="36M", pixel_format="yuv422p",
       note="A common intermediate codec in broadcast/post-production."),
    _p(key="flv_h264", name="FLV · H.264 · Streaming/web", category="video",
       container="flv", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="26", a_bitrate="128k", pixel_format="yuv420p",
       note="Suitable for streaming and legacy web players."),
    _p(key="ts_h264", name="TS · H.264 · Transport stream", category="video",
       container="ts", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="24", a_bitrate="192k", pixel_format="yuv420p",
       note="For TV/broadcast transport stream scenarios."),
    _p(key="gif_anim", name="GIF · Animated image", category="video",
       container="gif", video_codec="gif", audio_codec="", quality="medium",
       extra_vf="fps=12,scale=480:-2:flags=lanczos",
       note="Create an animated image from a video; frame rate and width are sensibly limited."),
    _p(key="mp4_hw_nvenc", name="MP4 · H.264 · GPU accelerated (auto-pick hardware)", category="video",
       container="mp4", video_codec="auto_hw_h264", audio_codec="aac", quality="medium",
       crf="23", a_bitrate="128k", faststart=True, pixel_format="yuv420p",
       note="Prefers NVIDIA/Intel/AMD hardware encoders, with automatic fallback to software encoding."),
]

# ---------------------- 音频预设 ----------------------
AUDIO_PRESETS: List[Preset] = [
    _p(key="mp3_320", name="MP3 · 320kbps · High quality", category="audio",
       container="mp3", video_codec="", audio_codec="libmp3lame", quality="high",
       a_bitrate="320k", note="The highest commonly used MP3 bitrate; plays on all players."),
    _p(key="mp3_192", name="MP3 · 192kbps · Standard", category="audio",
       container="mp3", video_codec="", audio_codec="libmp3lame", quality="medium",
       a_bitrate="192k", note="Balanced size and quality; a general recommendation."),
    _p(key="mp3_128", name="MP3 · 128kbps · Small size", category="audio",
       container="mp3", video_codec="", audio_codec="libmp3lame", quality="low",
       a_bitrate="128k", note="Suitable for speech and podcasts."),
    _p(key="aac_m4a", name="AAC · M4A · Apple ecosystem", category="audio",
       container="m4a", video_codec="", audio_codec="aac", quality="medium",
       a_bitrate="192k", note="Natively supported on Apple devices."),
    _p(key="flac_lossless", name="FLAC · Lossless compression", category="audio",
       container="flac", video_codec="", audio_codec="flac", quality="lossless",
       note="Lossless but about half the size of WAV; good for archiving."),
    _p(key="wav_pcm", name="WAV · PCM · Lossless uncompressed", category="audio",
       container="wav", video_codec="", audio_codec="pcm_s16le", quality="lossless",
       note="Standard uncompressed audio; for editing and mastering."),
    _p(key="ogg_vorbis", name="OGG · Vorbis", category="audio",
       container="ogg", video_codec="", audio_codec="libvorbis", quality="medium",
       a_bitrate="160k", note="Open format, commonly used on the web."),
    _p(key="opus_webm", name="OPUS · High compression", category="audio",
       container="opus", video_codec="", audio_codec="libopus", quality="medium",
       a_bitrate="128k", note="Better quality than MP3 at the same bitrate; good for network voice."),
    _p(key="wma_asf", name="WMA · Windows Media", category="audio",
       container="asf", video_codec="", audio_codec="wmav2", quality="medium",
       a_bitrate="128k", note="Compatible with legacy Windows players."),
    _p(key="ac3_dolby", name="AC3 · Dolby Digital", category="audio",
       container="ac3", video_codec="", audio_codec="ac3", quality="medium",
       a_bitrate="384k", note="Commonly used with home theater / set-top boxes."),
    _p(key="m4a_copy", name="M4A · Remux only (no re-encode)", category="audio",
       container="m4a", video_codec="", audio_codec="copy", quality="lossless",
       note="Only changes the container without re-encoding: extremely fast and lossless (source must be AAC)."),
]

# ---------------------- 图片预设 ----------------------
IMAGE_PRESETS: List[Preset] = [
    _p(key="jpg_high", name="JPG · High quality (quality 95)", category="image",
       container="jpg", video_codec="", audio_codec="", quality="high",
       extra=["-q:v", "2"], note="A common choice for sharing photos."),
    _p(key="jpg_std", name="JPG · Standard (quality 85)", category="image",
       container="jpg", video_codec="", audio_codec="", quality="medium",
       extra=["-q:v", "5"], note="Balanced size and quality."),
    _p(key="png_lossless", name="PNG · Lossless (supports transparency)", category="image",
       container="png", video_codec="", audio_codec="", quality="lossless",
       extra=["-compression_level", "6"], note="Use when you need an alpha channel or lossless output."),
    _p(key="webp_q", name="WebP · Lossy compression", category="image",
       container="webp", video_codec="", audio_codec="", quality="medium",
       extra=["-q:v", "80"], note="Web images: small files, compatible with modern browsers."),
    _p(key="webp_lossless", name="WebP · Lossless", category="image",
       container="webp", video_codec="", audio_codec="", quality="lossless",
       extra=["-lossless", "1"], note="Lossless and still smaller than PNG."),
    _p(key="bmp_raw", name="BMP · Uncompressed bitmap", category="image",
       container="bmp", video_codec="", audio_codec="", quality="lossless",
       note="Compatible with legacy software."),
    _p(key="tiff_print", name="TIFF · Print/archival", category="image",
       container="tiff", video_codec="", audio_codec="", quality="lossless",
       note="Commonly used for printing and long-term archiving."),
    _p(key="gif_image", name="GIF · Still image", category="image",
       container="gif", video_codec="", audio_codec="", quality="medium",
       note="Excellent compatibility, but a limited number of colors."),
    _p(key="avif_modern", name="AVIF · Next-gen high compression", category="image",
       container="avif", video_codec="", audio_codec="", quality="medium",
       extra=["-q:v", "30"], note="Smallest files, but requires newer software support."),
    _p(key="ico_icon", name="ICO · App icon", category="image",
       container="ico", video_codec="", audio_codec="", quality="lossless",
       extra=["-vf", "scale=256:256"], note="Generates a Windows icon file."),
]


def all_presets() -> List[Preset]:
    """返回全部预设（视频+音频+图片）。"""
    # 直接拼接；文档类转换不走 ffmpeg 预设，因此不在此列表。
    return VIDEO_PRESETS + AUDIO_PRESETS + IMAGE_PRESETS


def by_category(category: str) -> List[Preset]:
    """按类别筛选预设。"""
    return [p for p in all_presets() if p.category == category]


def by_key(key: str) -> Optional[Preset]:
    """按 key 查找预设；找不到返回 None。"""
    for p in all_presets():
        if p.key == key:
            return p
    return None


def as_dicts() -> List[Dict]:
    """把预设列表转成纯字典列表，便于跨进程（Web API）传输。"""
    out = []
    for p in all_presets():
        # 逐字段拷贝；retry/extra 为列表需要浅拷贝，避免调用方改坏原对象。
        d = {
            "key": p.key, "name": p.name, "category": p.category,
            "container": p.container, "video_codec": p.video_codec,
            "audio_codec": p.audio_codec, "quality": p.quality,
            "v_bitrate": p.v_bitrate, "a_bitrate": p.a_bitrate, "crf": p.crf,
            "extra_vf": p.extra_vf, "extra_af": p.extra_af,
            "faststart": p.faststart, "pixel_format": p.pixel_format,
            "note": p.note, "extra": list(p.extra),
        }
        out.append(d)
    return out
