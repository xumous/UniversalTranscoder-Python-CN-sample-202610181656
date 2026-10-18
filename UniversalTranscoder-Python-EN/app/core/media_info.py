# -*- coding: utf-8 -*-
# 媒体信息探测模块。
# 职责：调用 ffprobe 以 JSON 形式读取任意文件的容器/流信息，并解析成结构化对象。
# 容错要点：
#   1) 很多文件（尤其是损坏的、或纯图片）会让 ffprobe 报错；此时不抛异常，而是返回“低置信度”结果。
#   2) 时长/码率字段经常缺失或为字符串 "N/A"，解析时必须逐字段兜底。
#   3) 解析使用“宽松策略”：任何单字段解析失败都不影响其它字段。

import json  # 解析 ffprobe 的 JSON 输出
import os  # 取文件名、判断文件是否存在
import subprocess  # 执行 ffprobe
import logging  # 记录异常与降级
from dataclasses import dataclass, field  # 用数据类承载结构化结果
from typing import List, Optional  # 类型标注

from app.core import ffmpeg_locator  # 复用定位器（ffprobe 路径与不弹窗标志）

# 模块日志器。
logger = logging.getLogger(__name__)

# ffprobe 探测超时（秒）。损坏文件可能让 ffprobe 卡住，必须有上限。
PROBE_TIMEOUT = 30.0


@dataclass
class StreamInfo:
    """单个流（视频/音频/字幕）的信息。"""
    index: int = -1  # 流序号（ffprobe 的 index，供 -map 使用）
    codec_type: str = ""  # 类型：video/audio/subtitle/data
    codec_name: str = ""  # 编码名：h264/hevc/aac/mp3…
    codec_long_name: str = ""  # 编码全称，用于展示
    width: int = 0  # 视频宽度（像素）
    height: int = 0  # 视频高度（像素）
    fps: float = 0.0  # 视频帧率（已把分数解析为浮点）
    bit_rate: int = 0  # 该流码率（bps），常缺失
    sample_rate: int = 0  # 音频采样率（Hz）
    channels: int = 0  # 音频声道数
    channel_layout: str = ""  # 声道布局：stereo/mono/5.1…
    duration: float = 0.0  # 该流时长（秒）
    pix_fmt: str = ""  # 像素格式：yuv420p/rgba…
    language: str = ""  # 流语言标记（字幕常用）
    title: str = ""  # 流标题（多音轨常见）

    @property
    def is_video(self) -> bool:
        """是否为视频流。"""
        return self.codec_type == "video"

    @property
    def is_audio(self) -> bool:
        """是否为音频流。"""
        return self.codec_type == "audio"

    @property
    def is_subtitle(self) -> bool:
        """是否为字幕流。"""
        return self.codec_type == "subtitle"

    @property
    def resolution(self) -> str:
        """返回 "宽x高" 形式的字符串；未知时返回空串。"""
        # 宽高都大于 0 才有意义。
        if self.width > 0 and self.height > 0:
            return f"{self.width}x{self.height}"
        return ""


@dataclass
class MediaInfo:
    """一个媒体文件的整体信息。"""
    path: str = ""  # 文件绝对路径
    exists: bool = False  # 文件是否存在
    size: int = 0  # 文件字节数
    format_name: str = ""  # 容器格式名（逗号分隔，如 "mov,mp4,m4a"）
    format_long_name: str = ""  # 容器全称
    duration: float = 0.0  # 总时长（秒）
    bit_rate: int = 0  # 总码率（bps）
    streams: List[StreamInfo] = field(default_factory=list)  # 全部流
    probe_ok: bool = False  # 探测是否成功（置信度标志）
    error: str = ""  # 探测失败时的错误文本
    raw: dict = field(default_factory=dict)  # 原始 JSON（便于高级功能读取冷门字段）

    # ---- 便捷派生属性：界面与命令构建器高频使用 ----
    @property
    def video_streams(self) -> List[StreamInfo]:
        """返回全部视频流。"""
        return [s for s in self.streams if s.is_video]

    @property
    def audio_streams(self) -> List[StreamInfo]:
        """返回全部音频流。"""
        return [s for s in self.streams if s.is_audio]

    @property
    def subtitle_streams(self) -> List[StreamInfo]:
        """返回全部字幕流。"""
        return [s for s in self.streams if s.is_subtitle]

    @property
    def has_video(self) -> bool:
        """是否存在视频流。"""
        return len(self.video_streams) > 0

    @property
    def has_audio(self) -> bool:
        """是否存在音频流。"""
        return len(self.audio_streams) > 0

    @property
    def primary_video(self) -> Optional[StreamInfo]:
        """返回主视频流（第一条）；没有则返回 None。"""
        vids = self.video_streams
        return vids[0] if vids else None

    @property
    def primary_audio(self) -> Optional[StreamInfo]:
        """返回主音频流（第一条）；没有则返回 None。"""
        auds = self.audio_streams
        return auds[0] if auds else None

    @property
    def kind(self) -> str:
        """粗粒度归类：video / audio / image / unknown。

        归类依据（按优先级）：
            - 有视频流且时长大于 1 秒视为视频；
            - 有视频流但时长为 0（单帧）视为图片；
            - 只有音频流视为音频。
        """
        # 先看是否有视频流。
        if self.has_video:
            # 图片类编码（mjpeg/png/webp…）且几乎无时长，判定为图片。
            vid = self.primary_video
            if vid and vid.codec_name in ("mjpeg", "png", "webp", "bmp", "gif", "jpeg", "heif", "avif"):
                return "image"
            # 时长为 0 或极小，按图片处理（例如单帧 mp4 封面）。
            if self.duration <= 0.05:
                return "image"
            return "video"
        # 仅有音频流。
        if self.has_audio:
            return "audio"
        # 都没有（例如纯数据文件）：未知。
        return "unknown"

    @property
    def display_name(self) -> str:
        """用于列表展示的短名称。"""
        return os.path.basename(self.path) if self.path else "(unknown file)"

    def summary_line(self) -> str:
        """生成一行人类可读摘要，用于详情面板。"""
        # 逐项拼装；缺失项自动跳过，避免出现空白括号。
        parts = []
        # 容器格式。
        if self.format_name:
            parts.append(self.format_name)
        # 视频信息：分辨率 + 编码 + 帧率。
        vid = self.primary_video
        if vid:
            vp = []
            if vid.resolution:
                vp.append(vid.resolution)
            if vid.codec_name:
                vp.append(vid.codec_name)
            if vid.fps:
                vp.append(f"{vid.fps:g}fps")
            if vp:
                parts.append("video:" + "/".join(vp))
        # 音频信息：编码 + 采样率 + 声道。
        aud = self.primary_audio
        if aud:
            ap = []
            if aud.codec_name:
                ap.append(aud.codec_name)
            if aud.sample_rate:
                ap.append(f"{aud.sample_rate}Hz")
            if aud.channels:
                ap.append(f"{aud.channels}ch")
            if ap:
                parts.append("audio:" + "/".join(ap))
        # 用中点连接，读起来更紧凑。
        return " · ".join(parts) if parts else "No information available"


def _safe_float(value, default: float = 0.0) -> float:
    """把任意输入安全地转为浮点；"N/A"、None、空串等一律回退为默认值。"""
    try:
        # 字符串先去除空白；ffprobe 常用 "N/A" 表示缺失。
        if isinstance(value, str):
            v = value.strip()
            # 显式拦截常见缺失写法。
            if v in ("", "N/A", "NA", "unknown", "None"):
                return default
            return float(v)
        # 数值直接转换。
        return float(value)
    except (TypeError, ValueError):
        # 任何异常都回退默认值。
        return default


def _safe_int(value, default: int = 0) -> int:
    """把任意输入安全地转为整数；失败回退默认值。"""
    # 先走浮点解析，再取整，兼容 "48000.000000" 这类带小数点的整数字符串。
    f = _safe_float(value, default=float(default))
    try:
        return int(round(f))
    except (TypeError, ValueError):
        return default


def _parse_fps(value) -> float:
    """解析 ffprobe 的帧率字段，支持 "30000/1001" 分数形式。

    特别处理：VFR（可变帧率）文件常用 "0/0"，这种无意义帧率返回 0。
    """
    # 直接转换（可能是 "25" 这类纯数字字符串）。
    direct = _safe_float(value, default=0.0)
    # 若输入本身就是分数字符串，上面的转换会失败并返回 0，这里再尝试分数解析。
    if isinstance(value, str) and "/" in value:
        try:
            num, den = value.split("/", 1)
            den_v = float(den)
            # 分母为 0 表示 VFR/未知。
            if den_v == 0:
                return 0.0
            return float(num) / den_v
        except (ValueError, ZeroDivisionError):
            return 0.0
    return direct


def probe(path: str, timeout: float = PROBE_TIMEOUT) -> MediaInfo:
    """探测一个媒体文件，返回 MediaInfo。

    参数:
        path: 文件路径（可为相对路径，内部会转绝对路径）。
        timeout: 超时秒数。
    返回:
        MediaInfo 对象。失败时 probe_ok=False 且 error 字段有说明，但对象本身始终可用。
    """
    # 统一转绝对路径，避免子进程在不同工作目录下解析相对路径出错。
    abspath = os.path.abspath(path)
    # 构造基础结果对象，先填入文件系统层面可确定的信息。
    info = MediaInfo(path=abspath)
    # 判断文件是否存在（不存在时后续字段保持默认，界面会给出“文件不存在”提示）。
    info.exists = os.path.isfile(abspath)
    if info.exists:
        try:
            # 记录文件大小（字节）。
            info.size = os.path.getsize(abspath)
        except OSError:
            # 大小读不到（权限/文件被占用）不影响探测流程。
            info.size = 0

    # 取得 ffprobe 路径；没有 ffprobe 就无法探测。
    probe_exe = ffmpeg_locator.ffprobe_path()
    if not probe_exe:
        info.error = "ffprobe not found"
        return info
    # 文件不存在时直接返回，省去一次注定失败的子进程调用。
    if not info.exists:
        info.error = "File does not exist"
        return info

    # 组装 ffprobe 参数：
    #   -v quiet        : 只保留我们需要的信息，屏蔽噪声
    #   -print_format json : 输出 JSON 便于解析
    #   -show_format    : 输出容器层面信息
    #   -show_streams   : 输出各流信息
    #   -show_chapters  : 输出章节（部分容器存在章节，扩展功能会用到）
    args = [
        probe_exe,
        "-v", "quiet",
        "-print_format", "json",
        "-show_format",
        "-show_streams",
        "-show_chapters",
        abspath,
    ]
    try:
        # 执行 ffprobe。
        proc = subprocess.run(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            check=False,
            creationflags=ffmpeg_locator._no_window_flag(),
        )
        # 解码标准输出。
        out = (proc.stdout or b"").decode("utf-8", errors="ignore")
        # 输出为空说明探测失败（例如文件根本不是媒体文件）。
        if not out.strip():
            info.error = (proc.stderr or b"").decode("utf-8", errors="ignore").strip() or "ffprobe produced no output"
            return info
        # 解析 JSON。
        data = json.loads(out)
    except subprocess.TimeoutExpired:
        # 超时：记录并返回低置信度结果。
        info.error = "Probe timed out"
        return info
    except json.JSONDecodeError as exc:
        # JSON 解析失败：说明输出被截断或被污染。
        info.error = f"Failed to parse probe result: {exc}"
        return info
    except Exception as exc:
        # 其它异常统一兜底。
        info.error = f"Probe error: {exc}"
        return info

    # 保留原始 JSON，供高级功能（如读取章节、附件）使用。
    info.raw = data if isinstance(data, dict) else {}
    # 解析容器信息。
    fmt = info.raw.get("format", {}) or {}
    info.format_name = str(fmt.get("format_name", "") or "")
    info.format_long_name = str(fmt.get("format_long_name", "") or "")
    info.duration = _safe_float(fmt.get("duration", 0.0), 0.0)
    info.bit_rate = _safe_int(fmt.get("bit_rate", 0), 0)
    # 若容器未给大小，用文件系统大小补上。
    if not info.size:
        info.size = _safe_int(fmt.get("size", 0), 0)

    # 逐个解析流。
    for raw_stream in (info.raw.get("streams") or []):
        # 防御：非字典项直接跳过（异常文件可能出现）。
        if not isinstance(raw_stream, dict):
            continue
        # 构造流对象并逐字段填充（全部走安全解析）。
        st = StreamInfo()
        st.index = _safe_int(raw_stream.get("index", -1), -1)
        st.codec_type = str(raw_stream.get("codec_type", "") or "")
        st.codec_name = str(raw_stream.get("codec_name", "") or "")
        st.codec_long_name = str(raw_stream.get("codec_long_name", "") or "")
        st.width = _safe_int(raw_stream.get("width", 0), 0)
        st.height = _safe_int(raw_stream.get("height", 0), 0)
        st.fps = _parse_fps(raw_stream.get("avg_frame_rate") or raw_stream.get("r_frame_rate"))
        st.bit_rate = _safe_int(raw_stream.get("bit_rate", 0), 0)
        st.sample_rate = _safe_int(raw_stream.get("sample_rate", 0), 0)
        st.channels = _safe_int(raw_stream.get("channels", 0), 0)
        st.channel_layout = str(raw_stream.get("channel_layout", "") or "")
        st.duration = _safe_float(raw_stream.get("duration", 0.0), 0.0)
        st.pix_fmt = str(raw_stream.get("pix_fmt", "") or "")
        # 标签信息（多语言音轨/字幕依赖 tags）。
        tags = raw_stream.get("tags", {}) or {}
        if isinstance(tags, dict):
            st.language = str(tags.get("language", "") or "")
            st.title = str(tags.get("title", "") or "")
        # 追加到结果列表。
        info.streams.append(st)

    # 若容器时长缺失，尝试用最长的流时长补上（常见于 TS/裸流）。
    if info.duration <= 0:
        stream_durations = [s.duration for s in info.streams if s.duration > 0]
        if stream_durations:
            info.duration = max(stream_durations)

    # 探测成功标记。
    info.probe_ok = True
    return info


def quick_duration(path: str) -> float:
    """只取时长的轻量探测（用于任务列表预估进度）。"""
    # 直接复用完整探测，代码更简单；代价是稍慢，但列表规模通常有限。
    return probe(path).duration
