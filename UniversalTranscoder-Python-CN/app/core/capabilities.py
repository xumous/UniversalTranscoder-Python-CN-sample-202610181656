# -*- coding: utf-8 -*-
# 内核能力探测模块（能力矩阵）。
# 目的：不同版本的 FFmpeg 编译选项不同，支持的编码器/容器差异很大（例如有的不含 libx265）。
#       如果界面无条件列出所有格式，用户选中一个内核不支持的格式就会失败。
#       因此本模块在启动时向 ffmpeg 询问真实能力，并据此过滤界面选项。
# 实现方式：
#   - `ffmpeg -encoders` 输出所有编码器（每行形如 " V..... libx264 ..."）。
#   - `ffmpeg -formats`  输出所有封装/解封装格式。
#   - `ffmpeg -hwaccels` 输出可用的硬件加速后端。
#   以上三条命令各执行一次，结果缓存，避免每次转码都重新探测。

import subprocess  # 执行 ffmpeg 能力查询命令
import threading  # 缓存加锁
import logging  # 记录探测结果

from app.core import ffmpeg_locator  # 复用定位器

logger = logging.getLogger(__name__)

# 缓存与锁。
_CACHE = {}
_CACHE_LOCK = threading.RLock()

# ffmpeg 能力查询超时（秒）。
CAP_TIMEOUT = 20.0


def _run_capture(args) -> str:
    """执行一条 ffmpeg 查询命令并返回其标准输出文本（失败返回空串）。"""
    try:
        # 统一走 PIPE 捕获输出。
        proc = subprocess.run(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,  # 编码器/格式列表其实打印在 stderr，这里合并捕获
            timeout=CAP_TIMEOUT,
            check=False,
            creationflags=ffmpeg_locator._no_window_flag(),
        )
        # 解码；能力列表含大量非 ASCII 描述，errors="ignore" 兜底。
        return (proc.stdout or b"").decode("utf-8", errors="ignore")
    except Exception as exc:
        # 查询失败（内核缺失/超时）时记录并返回空串，调用方据此走“宽松/内置默认”分支。
        logger.warning("能力探测命令失败：%s", exc)
        return ""


def encoders() -> dict:
    """返回可用编码器字典：{编码器名: 'V'/'A'/'S'}（视频/音频/字幕）。"""
    # 命中缓存直接返回。
    with _CACHE_LOCK:
        if "encoders" in _CACHE:
            return _CACHE["encoders"]
    # 取得 ffmpeg 路径。
    exe = ffmpeg_locator.ffmpeg_path()
    result = {}
    if exe:
        # 执行查询。
        text = _run_capture([exe, "-hide_banner", "-encoders"])
        # 逐行解析：有效行形如 " V....D libx264  H.264 ..."。
        for line in text.splitlines():
            # 去掉首尾空白；只处理非空行。
            s = line.strip()
            if not s:
                continue
            # 首字符必须是能力标志位（V/A/S/D 等）。
            if s[0] not in "VASDST":
                continue
            # 取第二段（编码器名）。
            parts = s.split()
            if len(parts) < 2:
                continue
            flags = parts[0]  # 能力标志串
            name = parts[1]  # 编码器名
            # 提取视频/音频/字幕能力位。
            kind = ""
            if "V" in flags:
                kind = "V"
            elif "A" in flags:
                kind = "A"
            elif "S" in flags:
                kind = "S"
            # 仅登记我们关心的三类。
            if kind and name not in result:
                result[name] = kind
    # 写入缓存并返回。
    with _CACHE_LOCK:
        _CACHE["encoders"] = result
    return result


def formats() -> set:
    """返回可用封装/解封装格式名集合（小写）。"""
    # 命中缓存。
    with _CACHE_LOCK:
        if "formats" in _CACHE:
            return _CACHE["formats"]
    exe = ffmpeg_locator.ffmpeg_path()
    names = set()
    if exe:
        text = _run_capture([exe, "-hide_banner", "-formats"])
        for line in text.splitlines():
            s = line.strip()
            # 有效行以能力标志开头（D 解封装 / E 封装）。
            if not s or s[0] not in "DE ":
                continue
            parts = s.split()
            # 形如 " DE mov,mp4,m4a,3gp,...  QuickTime / MOV"。
            if len(parts) < 2:
                continue
            # 第二段是逗号分隔的多个别名，全部登记。
            for one in parts[1].split(","):
                if one:
                    names.add(one.strip().lower())
    with _CACHE_LOCK:
        _CACHE["formats"] = names
    return names


def hwaccels() -> list:
    """返回可用的硬件加速后端名列表（如 d3d11va/qsv/nvenc 相关）。"""
    with _CACHE_LOCK:
        if "hwaccels" in _CACHE:
            return _CACHE["hwaccels"]
    exe = ffmpeg_locator.ffmpeg_path()
    result = []
    if exe:
        text = _run_capture([exe, "-hide_banner", "-hwaccels"])
        for line in text.splitlines():
            s = line.strip()
            # 跳过头两行说明与空行。
            if not s or s.lower().startswith("hardware acceleration"):
                continue
            if s.startswith("Hardware acceleration methods"):
                continue
            # 其余每行即一个后端名。
            result.append(s)
    with _CACHE_LOCK:
        _CACHE["hwaccels"] = result
    return result


def all_encoders_of(kind: str) -> list:
    """返回某一类（V/A/S）的全部编码器名列表。"""
    # 过滤字典。
    return sorted([name for name, k in encoders().items() if k == kind])


def supports_encoder(name: str) -> bool:
    """判断内核是否包含某编码器。"""
    return name in encoders()


def supports_format(name: str) -> bool:
    """判断内核是否支持某容器格式（宽松匹配：别名任一命中即算支持）。"""
    target = str(name or "").lower().strip().lstrip(".")
    if not target:
        return False
    fmts = formats()
    # 能力列表为空（探测失败）时采取宽松策略，一律返回 True，
    # 以免因探测失败而把用户可选格式全部禁用。
    if not fmts:
        return True
    # 精确命中。
    if target in fmts:
        return True
    # 部分别名：如用户写 "mp4" 而列表含 "mp4" 已在上面命中；
    # "mkv" 常写作 "matroska"，这里做一次常见别名的等价映射。
    aliases = {
        "mkv": "matroska",
        "m4v": "mp4",
        "m4a": "mp4",
        "aac": "adts",
        "jpg": "image2",
        "jpeg": "image2",
        "png": "image2",
        "webp": "webp",
        "flac": "flac",
        "webm": "webm",
    }
    alt = aliases.get(target)
    if alt and alt in fmts:
        return True
    return False


def summary() -> dict:
    """返回能力探测摘要，供“关于/设置”页展示，也让用户对内核能力心中有数。"""
    enc = encoders()
    # 统计各类编码器数量。
    return {
        "ffmpeg": ffmpeg_locator.ffmpeg_path(),
        "ffprobe": ffmpeg_locator.ffprobe_path(),
        "encoder_total": len(enc),
        "video_encoders": len([k for k in enc.values() if k == "V"]),
        "audio_encoders": len([k for k in enc.values() if k == "A"]),
        "subtitle_encoders": len([k for k in enc.values() if k == "S"]),
        "format_total": len(formats()),
        "hwaccels": hwaccels(),
    }


def clear_cache() -> None:
    """清空能力缓存（更换内核或修改自定义路径后调用）。"""
    with _CACHE_LOCK:
        _CACHE.clear()
