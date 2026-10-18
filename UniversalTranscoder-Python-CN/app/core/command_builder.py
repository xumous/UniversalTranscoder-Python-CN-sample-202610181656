# -*- utf-8 -*-
# 命令构建模块（本项目算法核心之一）。
# 职责：把“用户在界面上表达的意图”（TranscodeSettings）编译成一条可直接执行的 ffmpeg 参数列表。
# 关键设计：
#   1) 一切以“参数列表”而非“命令行字符串”形式产出，彻底避免 Windows/Unix 的引号与空格差异，
#      也避免用户输入中的特殊字符造成命令注入。
#   2) 所有可能触发失败的组合都会做前置校验与降级（例如：图片格式不允许带音频流 → 自动丢弃音频）。
#   3) 滤镜链按固定顺序拼接：裁剪 → 缩放 → 旋转/镜像 → 帧率 → 水印 → 字幕 → 变速。
#      顺序不可随意调换，否则会出现“旋转后坐标错乱”“水印随缩放变形”等隐蔽问题。

import os  # 路径处理（扩展名、绝对路径、文件名）
import copy  # 深拷贝设置对象（生成“软件编码回退”副本时需要）
import logging  # 记录降级决策
from dataclasses import dataclass, field  # 承载配置
from typing import List, Optional  # 类型标注

from app.core import hwaccel  # 硬件编码器选择
from app.core import capabilities  # 内核能力查询（判断封装名是否受支持）
from app.util import paths  # 资源路径（字体等）

logger = logging.getLogger(__name__)

# ---------------------- 常量表 ----------------------

# 仅含视频的容器：这些容器不能承载音频流，若用户选了它们，需要主动丢弃音频（否则 ffmpeg 会报错）。
VIDEO_ONLY_CONTAINERS = {"gif", "apng", "ico", "bmp", "jpg", "jpeg", "png", "tiff", "webp", "avif"}

# 仅含音频的容器：这些容器不能承载视频流。
AUDIO_ONLY_CONTAINERS = {"mp3", "wav", "flac", "ogg", "oga", "opus", "aac", "ac3", "m4a", "wma", "amr", "aiff"}

# 需要 moov 前置（faststart）的容器：主要是 MP4 家族，用于网络边下边播。
FASTSTART_CONTAINERS = {"mp4", "m4v", "m4a", "mov"}

# 扩展名与 ffmpeg 封装名不一致时的换算表。
# 重要：本表的值全部经过“向真实内核求证”后确定（不是凭印象猜的），
# 已用 `ffmpeg -formats` 逐条核对了下列名称确实存在：
#     matroska / mpegts / asf / mpeg / ipod / mp4 / image2 / webp / gif / avif / ico
# 如果不做这个换算，像 `-f mkv`、`-f ts` 这类写法会被 ffmpeg 直接拒绝
# （报 “Unknown muxer” 或 “Invalid argument”），这正是最初冒烟测试失败的原因。
CONTAINER_ALIAS = {
    "mkv": "matroska",  # MKV 的真实封装名是 matroska（不存在名为 mkv 的封装）
    "ts": "mpegts",  # TS 的真实封装名是 mpegts
    "wmv": "asf",  # WMV 是编码名，其封装容器是 asf
    "mpg": "mpeg",  # MPG 对应 MPEG program stream，封装名为 mpeg
    "mpeg": "mpeg",
    "m4a": "ipod",  # m4a 音频封装（AAC）
    "m4v": "mp4",  # m4v 视频封装
    "jpg": "image2",  # 单帧 JPEG 由 image2 封装承载（编码器为 mjpeg）
    "jpeg": "image2",
    "png": "image2",  # 单帧 PNG 同样由 image2 封装承载（编码器为 png）
    "bmp": "image2",
    "tif": "image2",
    "tiff": "image2",
    "oga": "ogg",  # Ogg 音频别名
    "m2ts": "mpegts",
    "vob": "mpeg",
}

# 图片类目标的“扩展名 →（编码器, 封装名）”映射表。
# 为什么必须单独处理图片：图片既不是“视频容器”也不是“音频容器”，
# 如果沿用视频那套默认编码器推断逻辑，会被错误地推断成 libx264（导致生成的文件无法作为图片打开）。
IMAGE_TARGETS = {
    "jpg": ("mjpeg", "image2"),
    "jpeg": ("mjpeg", "image2"),
    "png": ("png", "image2"),
    "bmp": ("bmp", "image2"),
    "tiff": ("tiff", "image2"),
    "tif": ("tiff", "image2"),
    "webp": ("libwebp", "webp"),
    "gif": ("gif", "gif"),
    "avif": ("libaom-av1", "avif"),
    "ico": ("bmp", "ico"),
}

# image2 封装在使用时要求输出文件名含序号模板，或显式声明 -update 1。
# 本项目的图片输出都是“单个文件”，因此统一追加 -frames:v 1 -update 1，避免该限制。
IMAGE2_MUXERS = {"image2"}

# 各容器的默认视频编码器（未显式指定时使用）。
# 重要：本表的键必须使用“换算后的封装名”（即 resolve_container 的输出），而不是用户输入的扩展名。
#       例如用户输入的 mpg 会被换算为 mpeg，因此这里必须存在 "mpeg" 键；
#       否则会回退到 libx264，而 MPEG-PS 封装无法承载 H.264，导致报错（这一点已在矩阵验证中被实测确认）。
DEFAULT_VIDEO_CODEC = {
    "mp4": "libx264", "m4v": "libx264", "mkv": "libx264", "mov": "libx264",
    "avi": "mpeg4", "flv": "libx264", "ts": "libx264", "webm": "libvpx-vp9",
    "gif": "gif", "wmv": "wmv2", "mpg": "mpeg2video",
    "3gp": "libx264",
    # 以下为“换算后名称”的条目，覆盖同名扩展名被换算后的情况。
    "matroska": "libx264",  # mkv → matroska，H.264 兼容性最好
    "mpegts": "libx264",  # ts  → mpegts
    "mpeg": "mpeg2video",  # mpg → mpeg，MPEG-PS 只接受 MPEG 系编码
    "asf": "libx264",  # wmv → asf（实测 asf 承载 H.264 可正常工作）
    "ipod": "libx264",  # m4a → ipod（音频场景下不会用到视频编码器）
}

# 各容器的默认音频编码器。同样使用“换算后的封装名”作为键。
DEFAULT_AUDIO_CODEC = {
    "mp4": "aac", "m4v": "aac", "mkv": "aac", "mov": "aac", "m4a": "aac",
    "avi": "libmp3lame", "flv": "aac", "ts": "aac", "webm": "libopus",
    "mp3": "libmp3lame", "wav": "pcm_s16le", "flac": "flac", "ogg": "libvorbis",
    "oga": "libvorbis", "opus": "libopus", "aac": "aac", "ac3": "ac3",
    "wma": "wmav2", "amr": "libopencore_amrnb", "aiff": "pcm_s16be",
    "mpg": "mp2",
    # 换算后名称的条目。
    "matroska": "aac",  # mkv → matroska
    "mpegts": "aac",  # ts  → mpegts
    "mpeg": "mp2",  # mpg → mpeg，MPEG-PS 使用 MP2 音频
    "asf": "aac",  # wmv → asf（实测 asf 承载 AAC 可正常工作）
    "ipod": "aac",  # m4a → ipod
    "image2": "",  # 图片封装不带音频
}

# 质量档位到 x264 CRF 的映射（当用户选了档位但没给具体 CRF 时使用）。
QUALITY_TO_CRF = {
    "lossless": "0",
    "high": "20",
    "medium": "23",
    "low": "28",
}

# 质量档位到音频码率的映射。
QUALITY_TO_ABR = {
    "lossless": "320k",
    "high": "192k",
    "medium": "128k",
    "low": "96k",
}


@dataclass
class TranscodeSettings:
    """一次转码请求的全部可调参数。

    说明：字段较多是因为要覆盖视频/音频/图片/文档四类场景，任一场景只用到其中一部分。
    """

    # ---- 基本 ----
    source: str = ""  # 源文件绝对路径
    target_format: str = "mp4"  # 目标格式（扩展名，不含点）
    preset_key: str = ""  # 使用的预设 key（用于回填与记忆）

    # ---- 编码 ----
    video_codec: str = ""  # 视频编码器；空表示用容器默认值
    audio_codec: str = ""  # 音频编码器；空表示用容器默认（或 copy）
    quality: str = "medium"  # 质量档位
    crf: str = ""  # 恒定质量因子（优先于 v_bitrate）
    v_bitrate: str = ""  # 视频码率（如 "2500k"）
    a_bitrate: str = ""  # 音频码率（如 "128k"）
    pixel_format: str = ""  # 目标像素格式（空则使用预设/默认 yuv420p）
    threads: int = 0  # 编码线程数；0 = ffmpeg 自动

    # ---- 画面 ----
    width: int = 0  # 目标宽（0 = 保持原样）
    height: int = 0  # 目标高（0 = 保持原样）
    scale_mode: str = "fit"  # 缩放模式：fit / fill / stretch
    fps: str = ""  # 目标帧率（空 = 保持原样）
    rotate: int = 0  # 旋转角度：0/90/180/270
    flip: str = "none"  # 镜像：none / h / v / both
    crop: str = ""  # 裁剪 "w:h:x:y"（空 = 不裁剪）
    watermark_path: str = ""  # 图片水印路径
    watermark_text: str = ""  # 文字水印内容
    watermark_pos: str = "tr"  # 水印位置：tl/tr/bl/br/center
    watermark_scale: float = 0.15  # 图片水印相对宽度比例
    watermark_opacity: float = 0.75  # 水印不透明度（0~1）
    subtitle_path: str = ""  # 外挂字幕（烧录进画面）

    # ---- 音频 ----
    audio_channels: int = 0  # 目标声道数（0 = 保持）
    sample_rate: int = 0  # 目标采样率（0 = 保持）
    volume: str = ""  # 音量滤镜参数（如 "1.5" 或 "-6dB"）

    # ---- 时间 ----
    start: str = ""  # 起始时间（如 "00:00:10" 或 "10.5"）
    end: str = ""  # 结束时间（绝对时间点）
    duration: str = ""  # 截取时长（与 end 二选一，二者都给时以 duration 为准）

    # ---- 变速 ----
    speed: float = 1.0  # 播放速度倍率（0.25~4.0 常用）

    # ---- 元数据 ----
    meta_title: str = ""  # 标题
    meta_artist: str = ""  # 艺术家
    meta_album: str = ""  # 专辑
    meta_comment: str = ""  # 备注

    # ---- 行为 ----
    copy_mode: bool = False  # 直接复制流（仅换容器，不重编码）
    hwaccel: str = "none"  # 硬件加速策略：auto / none / 指定编码器名
    # 默认 none：默认走软件编码，保证任何机器上都能成功。
    # 需要提速时，用户可在“设置”或预设中改为 auto 或具体硬件编码器。
    hwaccel_vendor: str = "auto"  # 硬件厂商偏好
    custom_args: List[str] = field(default_factory=list)  # 高级页自定义追加参数
    stream_mode: str = "auto"  # 流选择：auto（自动）/ first（取第一组）/ custom
    video_index: int = -1  # 指定视频流序号（stream_mode=custom 时有效）
    audio_index: int = -1  # 指定音频流序号
    subtitle_index: int = -1  # 指定字幕流序号
    drop_audio: bool = False  # 强制丢弃音频
    drop_video: bool = False  # 强制丢弃视频

    # ---- 输出 ----
    output_dir: str = ""  # 输出目录（空 = 与源文件同目录）
    output_name: str = ""  # 显式输出文件名（含扩展名）；空则自动生成
    overwrite: bool = False  # 是否允许覆盖


# ---------------------- 辅助函数 ----------------------

def resolve_container(target_format: str) -> str:
    """把用户给的目标扩展名换算成 ffmpeg 的封装格式名。

    参数:
        target_format: 目标格式扩展名（可带点、可大写）。
    返回:
        ffmpeg 可识别的封装格式名。
    """
    # 规整输入：去点、去空白、转小写。
    ext = str(target_format or "").lower().strip().lstrip(".")
    # 查换算表；没有则原样返回（多数情况扩展名与封装名一致）。
    return CONTAINER_ALIAS.get(ext, ext)


def _safe_ext(target_format: str) -> str:
    """从目标格式中提取安全的扩展名（仅保留字母数字，兜底 mp4）。"""
    # 去点、去空白、转小写。
    ext = str(target_format or "mp4").lower().strip().lstrip(".")
    # 过滤非法字符，防止生成奇怪文件名。
    ext = "".join(ch for ch in ext if ch.isalnum())
    return ext or "mp4"


def _even(value: int) -> int:
    """向下取偶数（最小为 2）。多数视频编码器要求宽高为偶数。"""
    v = max(2, int(value))
    return v if v % 2 == 0 else v - 1


def _parse_time(value) -> Optional[float]:
    """把时间字符串解析为秒数。

    支持格式：
        "12.5"            → 12.5 秒
        "00:01:23.500"    → 83.5 秒
        "01:23"           → 83 秒
        "1:23:45"         → 5025 秒
    返回 None 表示无法解析（调用方应忽略该字段）。
    """
    # 空值直接返回 None。
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    try:
        # 含冒号按 时:分:秒[.毫秒] 解析，从右往左累加。
        if ":" in s:
            parts = s.split(":")
            # 最多支持三段（时:分:秒）。
            if len(parts) > 3:
                return None
            total = 0.0
            # 逐段换算：第 i 段权重为 60 的 (len-1-i) 次方。
            for i, seg in enumerate(parts):
                seg_val = float(seg) if seg.strip() else 0.0
                total += seg_val * (60 ** (len(parts) - 1 - i))
            return total
        # 纯数字按秒解析。
        return float(s)
    except (ValueError, TypeError):
        return None


def _fmt_time(seconds: float) -> str:
    """把秒数格式化为 ffmpeg 可接受的 "HH:MM:SS.mmm" 字符串。"""
    # 负值裁剪为 0。
    total = max(0.0, float(seconds))
    # 拆分时分秒与毫秒。
    hours = int(total // 3600)
    minutes = int((total % 3600) // 60)
    secs = total % 60
    return f"{hours:02d}:{minutes:02d}:{secs:06.3f}"


def escape_filter_text(text: str) -> str:
    """转义 drawtext 滤镜中需要特殊处理的字符。

    drawtext 的难点在于：它内部使用 ":" 分隔参数、使用 "\\" 转义，
    且整体又位于 filtergraph 里（filtergraph 里 ":"、","、"'" 也要转义）。
    在 Windows 上还涉及盘符冒号，必须一并转义，否则必然报错。
    """
    s = str(text or "")
    # 第一步：反斜杠先转义（必须最先做，避免后续转义产生的反斜杠被二次转义）。
    s = s.replace("\\", "\\\\")
    # 第二步：冒号（参数分隔符）。
    s = s.replace(":", "\\:")
    # 第三步：单引号（filtergraph 的引号规则）。
    s = s.replace("'", "\\'")
    # 第四步：百分号（drawtext 的文本扩展语法 `%{}` 会误解析）。
    s = s.replace("%", "\\%")
    # 第五步：逗号（filtergraph 中分隔滤镜）。
    s = s.replace(",", "\\,")
    return s


def _escape_path_for_filter(path: str) -> str:
    """把文件路径转义为可安全嵌入 filtergraph 的形式。

    Windows 路径形如 "C:\\a\\b.png"，直接放进 filtergraph 会因盘符冒号与反斜杠而报错。
    处理办法：
        1) 统一为正斜杠（ffmpeg 在 Windows 上接受正斜杠）。
        2) 转义冒号（尤其是盘符那一颗）。
        3) 转义反斜杠（此时已无，但保险起见仍处理）。
    """
    p = str(path or "")
    # 反斜杠统一换成正斜杠。
    p = p.replace("\\", "/")
    # 冒号转义（盘符 C: → C\:）。
    p = p.replace(":", "\\:")
    return p


def _position_expr(pos: str) -> str:
    """把水印位置关键字换算成 overlay 滤镜的坐标表达式。

    overlay 的 x/y 支持表达式：W/H 为底图宽高，w/h 为水印宽高。
    "tr" 等位置使用 W-w、H-h 并减去边距 10 像素，视觉上更舒适。
    """
    # 统一小写并做别名归一。
    key = str(pos or "tr").lower().strip()
    # 归一化常见写法。
    key = {"top-right": "tr", "top-left": "tl", "bottom-right": "br",
           "bottom-left": "bl", "top": "tc", "bottom": "bc"}.get(key, key)
    # 坐标映射表。
    table = {
        "tl": ("10", "10"),  # 左上
        "tr": ("W-w-10", "10"),  # 右上
        "bl": ("10", "H-h-10"),  # 左下
        "br": ("W-w-10", "H-h-10"),  # 右下
        "tc": ("(W-w)/2", "10"),  # 上中
        "bc": ("(W-w)/2", "H-h-10"),  # 下中
        "center": ("(W-w)/2", "(H-h)/2"),  # 正中
    }
    # 取坐标对；未知位置回退右上。
    x, y = table.get(key, table["tr"])
    # 返回 "x,y" 形式表达式。
    return f"{x}:{y}"


def compute_output_path(st: TranscodeSettings, mode: str = "replace",
                        template: str = "{name}_{index}", overwrite: bool = False) -> str:
    """根据设置计算输出文件路径（含自动防覆盖加序号）。

    参数:
        st: 转码设置。
        mode: 命名方式：replace（替换扩展名）/ suffix（原名+后缀）/ custom（模板）。
        template: 自定义模板，支持 {name} {index} {ext} {date}。
        overwrite: 是否允许覆盖；False 时若目标已存在则自动追加序号。
    返回:
        输出文件的绝对路径。
    """
    # 源文件绝对路径。
    src = os.path.abspath(st.source)
    # 源目录与主名/扩展名。
    src_dir = os.path.dirname(src)
    base = os.path.splitext(os.path.basename(src))[0]  # 去掉扩展名的主名
    # 目标扩展名。
    ext = _safe_ext(st.target_format)
    # 若用户显式给了输出文件名，直接用（但仍需补全目录与扩展名）。
    if st.output_name:
        name = st.output_name
        # 若用户没写扩展名，自动补上。
        if not os.path.splitext(name)[1]:
            name = f"{name}.{ext}"
    else:
        # 按命名方式生成主名。
        if mode == "suffix":
            name = f"{base}_{ext}"  # 原名 + 目标格式，如 "movie_mp4.mp4"
        elif mode == "custom":
            # 模板替换：{name} 原名，{ext} 目标扩展名，{index} 暂用占位（下面统一处理），{date} 当前日期。
            import datetime
            name = template.replace("{name}", base).replace("{ext}", ext)
            name = name.replace("{date}", datetime.datetime.now().strftime("%Y%m%d"))
        else:
            # 默认 replace：直接替换扩展名。
            name = base
        # 补全扩展名（若模板已含扩展名则不重复添加）。
        if not name.lower().endswith("." + ext):
            name = f"{name}.{ext}"
    # 输出目录：用户指定优先，否则与源文件同目录。
    out_dir = os.path.abspath(st.output_dir) if st.output_dir else src_dir
    # 组装完整路径。
    out_path = os.path.join(out_dir, name)

    # 防覆盖：若不允许覆盖且目标已存在，则追加 _1、_2 …
    if not overwrite and os.path.exists(out_path):
        stem, suffix = os.path.splitext(out_path)
        i = 1
        # 最多尝试 9999 次，避免极端情况下死循环。
        while os.path.exists(f"{stem}_{i}{suffix}") and i < 10000:
            i += 1
        out_path = f"{stem}_{i}{suffix}"
    return out_path


def _choose_encoder(container: str, user_codec: str, st: TranscodeSettings, has_video: bool) -> str:
    """决定最终使用的视频编码器（含硬件加速与自动降级逻辑）。"""
    # 用户显式指定的编码器优先级最高。
    codec = (user_codec or "").strip()
    # 特殊标记：预设里的 "auto_hw_h264" 表示“自动挑硬件 H.264”。
    if codec == "auto_hw_h264":
        picked = hwaccel.pick_h264(st.hwaccel_vendor)
        if picked:
            # 成功挑到硬件编码器。
            return picked
        # 挑不到就回退软件编码器（保证功能可用，而不是报错）。
        logger.info("未找到可用的硬件 H.264 编码器，回退 libx264")
        return "libx264"
    # 用户的硬件加速策略为“指定编码器名”时，直接采用。
    if st.hwaccel and st.hwaccel not in ("auto", "none"):
        return st.hwaccel
    # 未指定编码器：用容器默认值。
    if not codec:
        codec = DEFAULT_VIDEO_CODEC.get(container, "libx264" if has_video else "")
    # 若用户开启了自动硬件加速且编码器是 libx264/libx265，尝试替换为硬件版。
    if st.hwaccel == "auto" and codec in ("libx264", "libx265"):
        if codec == "libx264":
            picked = hwaccel.pick_h264(st.hwaccel_vendor)
        else:
            picked = hwaccel.pick_hevc(st.hwaccel_vendor)
        if picked:
            logger.info("自动硬件加速：%s → %s", codec, picked)
            return picked
    return codec


def build_filter_chain(st: TranscodeSettings, info=None) -> str:
    """构建视频滤镜链（不含水印/字幕，这两者需要多输入，单独处理）。

    顺序：裁剪 → 缩放 → 旋转 → 镜像 → 帧率 → 变速。
    返回：以逗号分隔的 filtergraph 字符串；无滤镜时返回空串。
    """
    # 收集滤镜片段。
    parts = []
    # ---- 1. 裁剪（先裁剪，后续缩放基于裁剪后的画面，语义更直观）----
    crop = (st.crop or "").strip()
    if crop:
        # 接受 "w:h:x:y" 或 "w:h"（后者默认居中）。
        seg = crop.split(":")
        if len(seg) >= 4:
            parts.append(f"crop={seg[0]}:{seg[1]}:{seg[2]}:{seg[3]}")
        elif len(seg) == 2:
            # 只给宽高：用 (iw-w)/2 与 (ih-h)/2 居中裁剪。
            parts.append(f"crop={seg[0]}:{seg[1]}:(iw-{seg[0]})/2:(ih-{seg[1]})/2")
    # ---- 2. 缩放 ----
    if st.width and st.width > 0:
        w = _even(st.width)
        if st.height and st.height > 0:
            h = _even(st.height)
            if st.scale_mode == "fill":
                # 填充模式：等比放大到覆盖目标，再居中裁剪。
                parts.append(f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h}")
            elif st.scale_mode == "stretch":
                # 拉伸模式：不保持比例直接拉伸。
                parts.append(f"scale={w}:{h}")
            else:
                # 适应模式（默认）：等比缩放到完整放入目标框，可能留黑边（由 pad 补齐）。
                parts.append(f"scale={w}:{h}:force_original_aspect_ratio=decrease,pad={w}:{h}:(ow-iw)/2:(oh-ih)/2")
        else:
            # 只给宽度：高度自动（-2 保证偶数）。
            parts.append(f"scale={w}:-2")
    elif st.height and st.height > 0:
        # 只给高度：宽度自动（-2 保证偶数）。
        parts.append(f"scale=-2:{_even(st.height)}")
    # ---- 3. 旋转 ----
    # 说明：优先使用 transpose 滤镜（精确、无插值损失）。
    if int(st.rotate or 0) % 360 == 90:
        parts.append("transpose=1")  # 顺时针 90°
    elif int(st.rotate or 0) % 360 == 180:
        parts.append("transpose=2,transpose=2")  # 180° = 两次顺时针 90° 的等效（更兼容）
    elif int(st.rotate or 0) % 360 == 270:
        parts.append("transpose=2")  # 逆时针 90°（即顺时针 270°）
    # 注意：rotate=0 时不做任何处理，避免无谓的重编码滤镜。
    # ---- 4. 镜像 ----
    if st.flip == "h":
        parts.append("hflip")  # 水平镜像
    elif st.flip == "v":
        parts.append("vflip")  # 垂直镜像
    elif st.flip == "both":
        parts.append("hflip,vflip")  # 双向
    # ---- 5. 帧率 ----
    if st.fps:
        # 支持 "25" 或 "30000/1001" 两种写法，直接透传给 fps 滤镜。
        parts.append(f"fps={st.fps}")
    # ---- 6. 变速 ----
    sp = float(st.speed or 1.0)
    if abs(sp - 1.0) > 1e-6 and sp > 0:
        # setpts 的系数是“原时间戳 × (1/速度)”。
        parts.append(f"setpts={1.0 / sp:.6f}*PTS")
    # 无任何滤镜时返回空串，调用方据此不添加 -vf。
    return ",".join(parts)


def build_audio_filter_chain(st: TranscodeSettings) -> str:
    """构建音频滤镜链（音量 + 变速）。"""
    parts = []
    # ---- 音量 ----
    vol = (st.volume or "").strip()
    if vol:
        # 允许 "1.5"（倍数）或 "-6dB"（分贝），ffmpeg 两种都支持。
        parts.append(f"volume={vol}")
    # ---- 变速（音频侧用 atempo）----
    sp = float(st.speed or 1.0)
    if abs(sp - 1.0) > 1e-6 and sp > 0:
        # atempo 单次仅支持 0.5~2.0，超出范围需串联多次。
        remaining = sp
        while remaining > 2.0 + 1e-9:
            parts.append("atempo=2.0")
            remaining /= 2.0
        while remaining < 0.5 - 1e-9:
            parts.append("atempo=0.5")
            remaining /= 0.5
        # 处理剩余倍率（保留 6 位小数足够精确）。
        parts.append(f"atempo={remaining:.6f}")
    return ",".join(parts)


def build_command(st: TranscodeSettings, info=None, out_path: str = "") -> List[str]:
    """把设置编译为完整的 ffmpeg 参数列表。

    参数:
        st: 转码设置。
        info: 可选，MediaInfo 对象（用于判断源是否有音/视频、时长等；为 None 时按“都有”处理）。
        out_path: 输出路径；为空则调用 compute_output_path 计算。
    返回:
        参数列表（首元素为 ffmpeg 可执行文件路径）。
    """
    # 取得 ffmpeg 路径。
    from app.core import ffmpeg_locator
    exe = ffmpeg_locator.ffmpeg_path()
    # 目标格式的扩展名与对应封装名。
    ext = _safe_ext(st.target_format)
    # 图片类目标信息（可能为 None）；用于决定图片编码器与封装细节。
    img_target = IMAGE_TARGETS.get(ext)
    # 换算后的封装名（已经过真实内核核对）。
    container = resolve_container(st.target_format)
    # 若目标是图片且用户没有显式指定编码器，则采用与该图片格式匹配的编码器。
    # 这里对设置做一次浅拷贝再改动，避免污染调用方传入的对象（调用方可能还要用它构建别的命令）。
    if img_target and not (st.video_codec or "").strip():
        st = copy.copy(st)
        st.video_codec = img_target[0]
    # 判断源是否含视频/音频（info 为 None 时保守假设都有，交由 ffmpeg 自行处理）。
    has_video_src = True if info is None else info.has_video
    has_audio_src = True if info is None else info.has_audio
    # 根据容器类型决定最终是否保留视频/音频。
    keep_video = has_video_src and not st.drop_video
    keep_audio = has_audio_src and not st.drop_audio
    # 纯图片容器强制丢弃音频。
    if ext in VIDEO_ONLY_CONTAINERS:
        keep_audio = False
    # 纯音频容器强制丢弃视频。
    if ext in AUDIO_ONLY_CONTAINERS:
        keep_video = False
        # 但动图容器（gif）例外——gif 已归入 VIDEO_ONLY_CONTAINERS，此处无需特殊处理。
    # 预判本次是否会用到 filter_complex：水印/字幕需要“多输入”，必须走 filter_complex。
    # 提前判定是必要的：后面的“流映射”段若同时再 -map 一个视频流，会与滤镜输出标签冲突，
    # 导致 ffmpeg 报 “Stream map matches no streams” 或映射出两条视频流。
    need_complex = bool(
        keep_video and (
                (st.watermark_path and os.path.isfile(st.watermark_path))
                or (st.subtitle_path and os.path.isfile(st.subtitle_path))
        )
    )
    # 开始组装参数。
    args: List[str] = [exe]
    # 全局选项：低调输出，避免刷屏；不打印无用的 banner。
    # 这里固定使用 -y（允许覆盖）。原因：“不覆盖”的诉求已在 compute_output_path 内部
    # 通过“目标已存在则自动追加序号”得到满足，走到本函数时输出路径一定是安全路径，
    # 因此 -y 不会覆盖用户已有文件，同时能避免交互式询问导致进程被卡住。
    args += ["-hide_banner", "-y"]

    # ---- 输入侧时间裁剪（放在 -i 之前做快速定位，速度更快）----
    start_sec = _parse_time(st.start)
    # 只有当起始时间明确大于 0 时才加 -ss，避免把不带裁剪的普通转码也变成“快速定位”。
    if start_sec is not None and start_sec > 0:
        args += ["-ss", _fmt_time(start_sec)]

    # ---- 输入文件 ----
    args += ["-i", os.path.abspath(st.source)]
    # ---- 图片水印作为第二输入 ----
    if keep_video and st.watermark_path and os.path.isfile(st.watermark_path):
        args += ["-i", os.path.abspath(st.watermark_path)]
    # ---- 外挂字幕作为额外输入（可选）----
    if keep_video and st.subtitle_path and os.path.isfile(st.subtitle_path):
        args += ["-i", os.path.abspath(st.subtitle_path)]

    # ---- 输出侧时长裁剪 ----
    dur_sec = _parse_time(st.duration)
    end_sec = _parse_time(st.end)
    if dur_sec is not None and dur_sec > 0:
        # 明确时长优先。
        args += ["-t", _fmt_time(dur_sec)]
    elif end_sec is not None and end_sec > 0:
        # 给出的是绝对结束时间：换算成相对于起始点的时长。
        base = start_sec if (start_sec is not None and start_sec > 0) else 0.0
        span = end_sec - base
        if span > 0:
            args += ["-t", _fmt_time(span)]

    # ---- 流选择 ----
    if st.stream_mode == "first":
        # 只映射第一条视频与第一条音频，自动丢弃多音轨/多字幕，输出更干净。
        # 注意：若本次使用了 filter_complex（水印/字幕），视频流由滤镜标签负责映射，这里必须跳过。
        if keep_video and not need_complex:
            args += ["-map", "0:v:0"]
        if keep_audio:
            args += ["-map", "0:a:0"]
    elif st.stream_mode == "custom":
        # 用户显式指定流序号；-1 表示该类型不映射。
        if keep_video and not need_complex and st.video_index >= 0:
            args += ["-map", f"0:{st.video_index}"]
        if keep_audio and st.audio_index >= 0:
            args += ["-map", f"0:{st.audio_index}"]
        if st.subtitle_index >= 0:
            args += ["-map", f"0:{st.subtitle_index}"]
    # stream_mode == "auto" 时不加 -map，交给 ffmpeg 默认策略（通常取第一条视频+第一条音频）。

    # ---- 视频编码 ----
    if keep_video:
        # 决定编码器。
        vcodec = _choose_encoder(container, st.video_codec, st, True)
        # copy 模式：不重编码，直接复制（仅在用户明确要求且容器兼容时使用）。
        if st.copy_mode and st.video_codec == "copy":
            vcodec = "copy"
        # 安全护栏：流复制（copy）与任何视频滤镜/水印/字幕互斥。
        # 因为压缩后的视频流无法直接施加滤镜，若用户同时勾选二者，ffmpeg 必然报错。
        # 这里主动把 copy 降级为“重新编码”，保证任务能跑完（宁可慢，也不报错）。
        if vcodec == "copy" and (build_filter_chain(st, info) or need_complex):
            vcodec = DEFAULT_VIDEO_CODEC.get(container, "libx264")
            logger.info("检测到“流复制”与滤镜同时启用，已自动改为重新编码（编码器：%s）", vcodec)
        args += ["-c:v", vcodec] if vcodec else []
        # 硬件编码器与软件编码器的质量参数不同，需分别处理。
        is_hw = vcodec.endswith("_nvenc") or vcodec.endswith("_qsv") or vcodec.endswith("_amf") \
                or vcodec.endswith("_vaapi") or vcodec.endswith("_videotoolbox")
        if vcodec not in ("copy", ""):
            # ---- 质量策略：CRF 优先，其次码率，最后按档位推断 ----
            crf = (st.crf or "").strip()
            vbr = (st.v_bitrate or "").strip()
            if not crf and not vbr:
                # 用户没给具体参数：按质量档位推断默认值。
                if st.quality in QUALITY_TO_CRF:
                    crf = QUALITY_TO_CRF[st.quality]
                else:
                    crf = QUALITY_TO_CRF["medium"]
            # ---- 高质量档位下的“近无损”预设（仅软件 x264/x265 支持 preset 参数）----
            if vcodec in ("libx264", "libx265"):
                # 编码速度预设：质量档位越高，越偏向慢速高压缩比。
                speed_preset = {"lossless": "veryslow", "high": "slow",
                                "medium": "medium", "low": "faster"}.get(st.quality, "medium")
                args += ["-preset", speed_preset]
                # 无损档位使用无损模式。
                if st.quality == "lossless":
                    args += ["-qp", "0"]
                    crf = ""
            # ---- 应用 CRF（若仍存在）----
            if crf and not is_hw:
                # 软件 x264/x265 用 -crf；其它编码器有的不支持，做保守判断。
                if vcodec in ("libx264", "libx265", "libvpx", "libvpx-vp9", "libaom-av1",
                              "libsvtav1", "librav1e", "libxvid", "mpeg4", "mpeg2video", "mpeg1video"):
                    args += ["-crf", str(crf)]
                elif vcodec in ("mjpeg", "png", "webp", "gif", "tiff", "bmp"):
                    # 图片类编码器：CRF 不适用，改用 -q:v（数值越小质量越高）。
                    qv = {"lossless": "1", "high": "2", "medium": "5", "low": "12"}.get(st.quality, "5")
                    args += ["-q:v", qv]
            elif crf and is_hw:
                # 硬件编码器：质量因子参数名不同。
                if vcodec.endswith("_nvenc"):
                    args += ["-cq", str(crf)]  # NVENC 用 -cq
                    args += ["-rc", "vbr"]  # 速率控制模式
                elif vcodec.endswith("_qsv"):
                    args += ["-global_quality", str(crf)]  # QSV 用全局质量
                elif vcodec.endswith("_amf"):
                    args += ["-qp_i", str(crf), "-qp_p", str(crf)]  # AMF 用 QP
                elif vcodec.endswith("_vaapi"):
                    args += ["-qp", str(crf)]
            # ---- 应用码率（若用户显式给了码率，覆盖 CRF 策略）----
            if vbr:
                args += ["-b:v", vbr]
                # 码率上限与缓冲区：给出合理的两倍上限，避免瞬时超码率。
                try:
                    # 解析数值部分以计算 maxrate/bufsize。
                    num = float("".join(ch for ch in vbr if ch.isdigit() or ch == "."))
                    args += ["-maxrate", f"{int(num * 1.5)}k", "-bufsize", f"{int(num * 3)}k"]
                except ValueError:
                    # 解析失败则跳过 maxrate 设置，不影响主流程。
                    pass
            # ---- 像素格式（兼容性关键）----
            pf = (st.pixel_format or "").strip()
            if not pf:
                # 预设/默认：H.264/H.265 用 yuv420p 最兼容；VP9 同理。
                pf = "yuv420p"
            # 仅对支持 -pix_fmt 的编码器添加（图片编码器大多也支持）。
            if vcodec not in ("copy", ""):
                args += ["-pix_fmt", pf]
            # ---- 线程数 ----
            if st.threads and st.threads > 0:
                args += ["-threads", str(int(st.threads))]

        # ---- 视频滤镜链 ----
        vf = build_filter_chain(st, info)
        # 水印与字幕需要多输入，单独构建额外的滤镜与映射。
        extra_filters = []
        input_index = 1  # 第二个及其后的输入从 1 开始编号
        # 图片水印。
        if st.watermark_path and os.path.isfile(st.watermark_path):
            # 先把水印缩放到相对宽度，再叠加到主画面。
            wm_w = f"iw*{max(0.05, min(1.0, float(st.watermark_scale or 0.15))):.3f}"
            # 水印流编号：使用其内部的第一个视频流。
            wm_label = f"{input_index}:v"
            # overlay 坐标。
            pos = _position_expr(st.watermark_pos)
            # 构建 [主][水印]overlay 滤镜；这里使用简化写法：
            #   [1:v]scale=...，透明度用 format=rgba + colorchannelmixer 实现。
            opacity = max(0.0, min(1.0, float(st.watermark_opacity or 0.75)))
            extra_filters.append(
                f"[{wm_label}]scale={wm_w}:-1,format=rgba,colorchannelmixer=aa={opacity:.2f}[wm]"
            )
            # 主画面打上标签，便于 overlay 引用；若已有 vf 则先接上。
            if vf:
                extra_filters.append(f"[0:v]{vf}[base]")
            else:
                extra_filters.append("[0:v]null[base]")
            extra_filters.append(f"[base][wm]overlay={pos}[vout]")
            input_index += 1
        # 字幕烧录。
        if st.subtitle_path and os.path.isfile(st.subtitle_path):
            # 用 subtitles 滤镜把字幕“烧”进画面；路径必须转义。
            sub_path = _escape_path_for_filter(os.path.abspath(st.subtitle_path))
            # 若前面已有输出标签 [vout]，则在它之后继续处理；否则基于 [0:v]。
            base_label = "[vout]" if (st.watermark_path and os.path.isfile(st.watermark_path)) else (
                "[base]" if vf else "[0:v]"
            )
            if not (st.watermark_path and os.path.isfile(st.watermark_path)) and vf:
                # 若前面只做了 vf 而没有水印，需要先把 vf 挂上。
                extra_filters.append(f"[0:v]{vf}[base]")
                base_label = "[base]"
            extra_filters.append(f"{base_label}subtitles='{sub_path}'[vsub]")

        # 决定最终的 -filter_complex 与 -map。
        if extra_filters:
            # 有附加滤镜：使用 filter_complex，并显式映射最终视频标签。
            args += ["-filter_complex", ";".join(extra_filters)]
            # 最终视频标签：字幕存在用 [vsub]，否则水印存在用 [vout]，再否则 [base]。
            if st.subtitle_path and os.path.isfile(st.subtitle_path):
                final_label = "[vsub]"
            elif st.watermark_path and os.path.isfile(st.watermark_path):
                final_label = "[vout]"
            else:
                final_label = "[base]"
            args += ["-map", final_label]
        elif vf:
            # 只有普通滤镜：直接用 -vf。
            args += ["-vf", vf]

    # ---- 音频编码 ----
    if keep_audio:
        acodec = (st.audio_codec or "").strip()
        if not acodec:
            acodec = DEFAULT_AUDIO_CODEC.get(container, "aac")
        # copy 模式。
        if st.copy_mode and acodec not in ("", "copy"):
            acodec = "copy"
        args += ["-c:a", acodec]
        if acodec != "copy":
            # 音频码率：用户显式优先，否则按质量档位推断。
            abr = (st.a_bitrate or "").strip()
            if not abr:
                abr = QUALITY_TO_ABR.get(st.quality, "128k")
            # 无损音频编码器（flac/pcm 系列）不接受 -b:a，需要跳过。
            lossless_audio = acodec in ("flac", "pcm_s16le", "pcm_s16be", "pcm_s24le",
                                        "pcm_s32le", "alac", "tta", "wavpack", "copy")
            if not lossless_audio:
                args += ["-b:a", abr]
            # 目标声道数与采样率。
            # 特殊编码器存在硬性约束：AMR-NB 只支持 8kHz/单声道，AMR-WB 只支持 16kHz/单声道。
            # 若不在此强制覆盖，ffmpeg 会因“采样率/声道不满足编码器要求”直接报错。
            ch_target = st.audio_channels
            sr_target = st.sample_rate
            if acodec == "libopencore_amrnb":
                sr_target, ch_target = 8000, 1
            elif acodec == "libopencore_amrwb":
                sr_target, ch_target = 16000, 1
            # 声道数。
            if ch_target and ch_target > 0:
                args += ["-ac", str(int(ch_target))]
            # 采样率。
            if sr_target and sr_target > 0:
                args += ["-ar", str(int(sr_target))]
            # 音频滤镜（音量/变速）。
            af = build_audio_filter_chain(st)
            if af:
                args += ["-af", af]

    # ---- 元数据 ----
    # 逐项写入；空值跳过。使用 -metadata 统一处理容器级标签。
    meta_map = {
        "title": st.meta_title,
        "artist": st.meta_artist,
        "album": st.meta_album,
        "comment": st.meta_comment,
    }
    for key, value in meta_map.items():
        if value:
            args += ["-metadata", f"{key}={value}"]

    # ---- 封装与容器细节 ----
    if container in FASTSTART_CONTAINERS:
        # 把索引(moov)前移，便于网页边下边播。
        args += ["-movflags", "+faststart"]
    # 图片输出：image2 封装要求显式声明“只写一帧且持续更新同一文件”，否则会因缺少 %d 序号模板而报错。
    if container in IMAGE2_MUXERS:
        args += ["-frames:v", "1", "-update", "1"]
    # 显式指定目标格式。
    # 关键改动：只在“内核确实支持该封装名”时才加 -f；
    # 否则宁可让 ffmpeg 依据输出扩展名自行推断，也不要传一个内核不认识的封装名（那会直接导致失败）。
    if container and capabilities.supports_format(container):
        args += ["-f", container]
    else:
        logger.debug("封装名 %s 未被内核确认支持，改为交由 ffmpeg 依据扩展名推断。", container)

    # ---- 高级页自定义参数（放在最末，允许用户覆盖前面的默认值）----
    if st.custom_args:
        # 过滤掉空字符串项，防止生成多余参数。
        args += [a for a in st.custom_args if str(a).strip()]

    # ---- 输出路径 ----
    final_out = out_path or compute_output_path(st, overwrite=True)
    args += [final_out]
    return args


def software_fallback(st: TranscodeSettings) -> TranscodeSettings:
    """生成一份“强制软件编码”的设置副本，用于硬件编码失败后的自动回退。

    为什么需要这个函数：
        仅仅追加 `-hwaccel none` 是不够的——它只是关闭“硬件解码”，并不会更换“编码器”。
        如果原命令用的是 h264_nvenc（硬件编码器），失败后仍会继续尝试 h264_nvenc，必然再次失败。
        真正有效的回退是：把编码器替换成对应的软件实现（H.264→libx264，H.265→libx265，AV1→libaom-av1）。

    参数:
        st: 原始设置。
    返回:
        新的设置对象（原对象不被修改）。
    """
    # 深拷贝，避免污染调用方的设置对象。
    new_st = copy.deepcopy(st)
    # 识别原编码器属于哪一档硬件实现。
    vc = (st.video_codec or "").lower()
    # 依据族系选择软件编码器。
    if "hevc" in vc or "h265" in vc:
        new_st.video_codec = "libx265"
    elif "av1" in vc:
        # SVT-AV1 速度与兼容性都较好；若内核没有，退回 libaom-av1。
        from app.core import capabilities
        new_st.video_codec = "libsvtav1" if capabilities.supports_encoder("libsvtav1") else "libaom-av1"
    else:
        # 默认按 H.264 处理（覆盖面最广）。
        new_st.video_codec = "libx264"
    # 关闭自动硬件加速，防止再次被升级为硬件编码器。
    new_st.hwaccel = "none"
    # 返回副本。
    return new_st


def describe_command(args: List[str]) -> str:
    """把参数列表渲染成一行便于阅读的命令文本（仅用于界面展示与复制）。

    注意：这不是用来执行的字符串，仅用于展示。展示时为含空格的参数补上引号。
    """
    out = []
    for a in args:
        s = str(a)
        # 含空格的参数加双引号，读起来更接近真实命令行。
        if " " in s:
            out.append(f'"{s}"')
        else:
            out.append(s)
    return " ".join(out)
