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
    _p(key="mp4_h264_high", name="MP4 · H.264 · 高质量（通用）", category="video",
       container="mp4", video_codec="libx264", audio_codec="aac", quality="high",
       crf="20", a_bitrate="192k", faststart=True, pixel_format="yuv420p",
       note="兼容性最好的组合，适合手机/电视/网页播放。"),
    _p(key="mp4_h264_std", name="MP4 · H.264 · 标准（推荐）", category="video",
       container="mp4", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="23", a_bitrate="128k", faststart=True, pixel_format="yuv420p",
       note="体积与画质的平衡点，日常转换首选。"),
    _p(key="mp4_h264_small", name="MP4 · H.264 · 小体积", category="video",
       container="mp4", video_codec="libx264", audio_codec="aac", quality="low",
       crf="28", a_bitrate="96k", faststart=True, pixel_format="yuv420p",
       note="用于分享/上传，明显压缩体积。"),
    _p(key="mp4_hevc", name="MP4 · H.265/HEVC · 高压缩", category="video",
       container="mp4", video_codec="libx265", audio_codec="aac", quality="medium",
       crf="28", a_bitrate="128k", faststart=True, pixel_format="yuv420p",
       note="同画质下体积更小，但老设备兼容性略差。"),
    _p(key="mkv_h264", name="MKV · H.264 · 高兼容封装", category="video",
       container="mkv", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="23", a_bitrate="128k", pixel_format="yuv420p",
       note="MKV 可容纳多音轨/多字幕，适合收藏。"),
    _p(key="webm_vp9", name="WebM · VP9 · 网页友好", category="video",
       container="webm", video_codec="libvpx-vp9", audio_codec="libopus", quality="medium",
       crf="32", a_bitrate="128k", pixel_format="yuv420p",
       note="开源免专利，适合嵌入网页。"),
    _p(key="webm_vp8", name="WebM · VP8 · 更老设备", category="video",
       container="webm", video_codec="libvpx", audio_codec="libvorbis", quality="medium",
       crf="10", a_bitrate="128k", pixel_format="yuv420p",
       note="兼容较老的浏览器与设备。"),
    _p(key="avi_xvid", name="AVI · Xvid · 老式播放器", category="video",
       container="avi", video_codec="mpeg4", audio_codec="libmp3lame", quality="high",
       v_bitrate="2500k", a_bitrate="192k", pixel_format="yuv420p",
       note="为不支持 MP4 的老播放器/DVD 机准备。"),
    _p(key="mov_prores", name="MOV · ProRes · 剪辑中间格式", category="video",
       container="mov", video_codec="prores_ks", audio_codec="pcm_s16le", quality="high",
       pixel_format="yuv422p10le",
       note="用于专业剪辑流程，体积大但剪辑流畅。"),
    _p(key="mov_dnxhd", name="MOV · DNxHD · 广播级中间格式", category="video",
       container="mov", video_codec="dnxhd", audio_codec="pcm_s16le", quality="high",
       v_bitrate="36M", pixel_format="yuv422p",
       note="广播/影视后期常用的中间编码。"),
    _p(key="flv_h264", name="FLV · H.264 · 直播/网页流", category="video",
       container="flv", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="26", a_bitrate="128k", pixel_format="yuv420p",
       note="适合推流与旧式网页播放器。"),
    _p(key="ts_h264", name="TS · H.264 · 传输流", category="video",
       container="ts", video_codec="libx264", audio_codec="aac", quality="medium",
       crf="24", a_bitrate="192k", pixel_format="yuv420p",
       note="用于电视/广播传输流场景。"),
    _p(key="gif_anim", name="GIF · 动图", category="video",
       container="gif", video_codec="gif", audio_codec="", quality="medium",
       extra_vf="fps=12,scale=480:-2:flags=lanczos",
       note="从视频生成动图，帧率与宽度已做合理限制。"),
    _p(key="mp4_hw_nvenc", name="MP4 · H.264 · 显卡加速（自动挑硬件）", category="video",
       container="mp4", video_codec="auto_hw_h264", audio_codec="aac", quality="medium",
       crf="23", a_bitrate="128k", faststart=True, pixel_format="yuv420p",
       note="优先使用 NVIDIA/Intel/AMD 硬件编码器，失败会自动回退软件编码。"),
]

# ---------------------- 音频预设 ----------------------
AUDIO_PRESETS: List[Preset] = [
    _p(key="mp3_320", name="MP3 · 320kbps · 高音质", category="audio",
       container="mp3", video_codec="", audio_codec="libmp3lame", quality="high",
       a_bitrate="320k", note="MP3 最高常用码率，兼容所有播放器。"),
    _p(key="mp3_192", name="MP3 · 192kbps · 标准", category="audio",
       container="mp3", video_codec="", audio_codec="libmp3lame", quality="medium",
       a_bitrate="192k", note="体积与音质平衡，通用推荐。"),
    _p(key="mp3_128", name="MP3 · 128kbps · 小体积", category="audio",
       container="mp3", video_codec="", audio_codec="libmp3lame", quality="low",
       a_bitrate="128k", note="适合语音与播客。"),
    _p(key="aac_m4a", name="AAC · M4A · 苹果生态", category="audio",
       container="m4a", video_codec="", audio_codec="aac", quality="medium",
       a_bitrate="192k", note="Apple 设备原生支持。"),
    _p(key="flac_lossless", name="FLAC · 无损压缩", category="audio",
       container="flac", video_codec="", audio_codec="flac", quality="lossless",
       note="无损但体积约为 WAV 的一半，适合收藏。"),
    _p(key="wav_pcm", name="WAV · PCM · 无损未压缩", category="audio",
       container="wav", video_codec="", audio_codec="pcm_s16le", quality="lossless",
       note="标准未压缩音频，用于编辑与母带。"),
    _p(key="ogg_vorbis", name="OGG · Vorbis", category="audio",
       container="ogg", video_codec="", audio_codec="libvorbis", quality="medium",
       a_bitrate="160k", note="开源格式，网页常用。"),
    _p(key="opus_webm", name="OPUS · 高压缩", category="audio",
       container="opus", video_codec="", audio_codec="libopus", quality="medium",
       a_bitrate="128k", note="同码率下音质优于 MP3，适合网络语音。"),
    _p(key="wma_asf", name="WMA · Windows Media", category="audio",
       container="asf", video_codec="", audio_codec="wmav2", quality="medium",
       a_bitrate="128k", note="老式 Windows 播放器兼容。"),
    _p(key="ac3_dolby", name="AC3 · 杜比数字", category="audio",
       container="ac3", video_codec="", audio_codec="ac3", quality="medium",
       a_bitrate="384k", note="家庭影院/机顶盒常用。"),
    _p(key="m4a_copy", name="M4A · 直接封装（不重编码）", category="audio",
       container="m4a", video_codec="", audio_codec="copy", quality="lossless",
       note="仅更换容器、不改编码，速度极快且无损失（源需为 AAC）。"),
]

# ---------------------- 图片预设 ----------------------
IMAGE_PRESETS: List[Preset] = [
    _p(key="jpg_high", name="JPG · 高质量（质量 95）", category="image",
       container="jpg", video_codec="", audio_codec="", quality="high",
       extra=["-q:v", "2"], note="照片分享的常用选择。"),
    _p(key="jpg_std", name="JPG · 标准（质量 85）", category="image",
       container="jpg", video_codec="", audio_codec="", quality="medium",
       extra=["-q:v", "5"], note="体积与画质平衡。"),
    _p(key="png_lossless", name="PNG · 无损（支持透明）", category="image",
       container="png", video_codec="", audio_codec="", quality="lossless",
       extra=["-compression_level", "6"], note="需要透明通道或无损时使用。"),
    _p(key="webp_q", name="WebP · 有损压缩", category="image",
       container="webp", video_codec="", audio_codec="", quality="medium",
       extra=["-q:v", "80"], note="网页图片，体积小且兼容现代浏览器。"),
    _p(key="webp_lossless", name="WebP · 无损", category="image",
       container="webp", video_codec="", audio_codec="", quality="lossless",
       extra=["-lossless", "1"], note="无损且仍比 PNG 小。"),
    _p(key="bmp_raw", name="BMP · 未压缩位图", category="image",
       container="bmp", video_codec="", audio_codec="", quality="lossless",
       note="老式软件兼容。"),
    _p(key="tiff_print", name="TIFF · 印刷/归档", category="image",
       container="tiff", video_codec="", audio_codec="", quality="lossless",
       note="印刷与长期归档常用。"),
    _p(key="gif_image", name="GIF · 静态图", category="image",
       container="gif", video_codec="", audio_codec="", quality="medium",
       note="兼容性极好，但颜色数受限。"),
    _p(key="avif_modern", name="AVIF · 新一代高压缩", category="image",
       container="avif", video_codec="", audio_codec="", quality="medium",
       extra=["-q:v", "30"], note="体积最小，需要较新软件支持。"),
    _p(key="ico_icon", name="ICO · 应用图标", category="image",
       container="ico", video_codec="", audio_codec="", quality="lossless",
       extra=["-vf", "scale=256:256"], note="生成 Windows 图标文件。"),
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
