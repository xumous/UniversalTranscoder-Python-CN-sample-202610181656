# -*- coding: utf-8 -*-
# 硬件加速探测模块。
# 目的：充分利用显卡的专用编码单元（NVENC/QSV/AMF/VideoToolbox 等）大幅提速。
# 但硬件编码器“存在不等于可用”（驱动版本、显存、分辨率限制都可能导致失败），
# 因此本模块只做“是否被编入 ffmpeg”的静态探测，把可用性判断交给运行时容错（见 errors 模块的 HW_ACCEL 分类）。

import logging  # 记录探测结果
import threading  # 缓存加锁

from app.core import capabilities  # 复用能力矩阵中的编码器列表

logger = logging.getLogger(__name__)

# 缓存与锁。
_CACHE = {}
_LOCK = threading.RLock()

# 硬件编码器到“厂商 / 说明”的映射表。
# 键为 ffmpeg 中的编码器名，值为 (厂商标识, 面向用户的说明)。
HW_ENCODERS = {
    "h264_nvenc": ("nvidia", "NVIDIA NVENC（H.264）"),
    "hevc_nvenc": ("nvidia", "NVIDIA NVENC（H.265/HEVC）"),
    "av1_nvenc": ("nvidia", "NVIDIA NVENC（AV1）"),
    "h264_qsv": ("intel", "Intel Quick Sync Video（H.264）"),
    "hevc_qsv": ("intel", "Intel Quick Sync Video（H.265/HEVC）"),
    "av1_qsv": ("intel", "Intel Quick Sync Video（AV1）"),
    "h264_amf": ("amd", "AMD AMF（H.264）"),
    "hevc_amf": ("amd", "AMD AMF（H.265/HEVC）"),
    "av1_amf": ("amd", "AMD AMF（AV1）"),
    "h264_vaapi": ("intel", "VAAPI（H.264，Linux/部分驱动）"),
    "hevc_vaapi": ("intel", "VAAPI（H.265，Linux/部分驱动）"),
    "h264_videotoolbox": ("apple", "VideoToolbox（H.264，macOS）"),
    "hevc_videotoolbox": ("apple", "VideoToolbox（H.265，macOS）"),
    "h264_d3d12va": ("any", "Direct3D 12 视频加速（H.264）"),
    "hevc_d3d12va": ("any", "Direct3D 12 视频加速（H.265）"),
}

# 软件编码器 -> 各厂商硬件编码器的候选映射（用于“自动”策略时优先选硬件）。
SOFT_TO_HW_H264 = {
    "nvidia": "h264_nvenc",
    "intel": "h264_qsv",
    "amd": "h264_amf",
    "apple": "h264_videotoolbox",
    "any": None,
}
SOFT_TO_HW_HEVC = {
    "nvidia": "hevc_nvenc",
    "intel": "hevc_qsv",
    "amd": "hevc_amf",
    "apple": "hevc_videotoolbox",
    "any": None,
}


def available() -> list:
    """返回当前内核中可用的硬件编码器名列表。"""
    # 命中缓存。
    with _LOCK:
        if "available" in _CACHE:
            return _CACHE["available"]
    # 取全部视频编码器名集合。
    enc_names = set(capabilities.all_encoders_of("V"))
    # 与已知硬件编码器表求交集。
    result = [name for name in HW_ENCODERS.keys() if name in enc_names]
    with _LOCK:
        _CACHE["available"] = result
    # 记录日志，便于排障时确认硬件能力。
    logger.info("检测到硬件编码器：%s", result or "无")
    return result


def available_detail() -> list:
    """返回可用的硬件编码器详情列表：[{name, vendor, label}]。"""
    out = []
    for name in available():
        vendor, label = HW_ENCODERS.get(name, ("any", name))
        out.append({"name": name, "vendor": vendor, "label": label})
    return out


def pick_h264(vendor: str = "auto") -> str:
    """为 H.264 编码选择硬件编码器；无可用时返回空串（调用方回退软件编码）。"""
    # 取可用集合。
    av = set(available())
    # 自动策略：按 NVIDIA → Intel → AMD → Apple 的偏好顺序挑选（NVENC 通用性最好）。
    if vendor in ("auto", "", None):
        for v in ("nvidia", "intel", "amd", "apple"):
            cand = SOFT_TO_HW_H264.get(v)
            if cand in av:
                return cand
        return ""
    # 指定厂商：直接取其对应编码器，若不可用则返回空串。
    cand = SOFT_TO_HW_H264.get(vendor)
    return cand if cand in av else ""


def pick_hevc(vendor: str = "auto") -> str:
    """为 H.265 编码选择硬件编码器；无可用时返回空串。"""
    av = set(available())
    if vendor in ("auto", "", None):
        for v in ("nvidia", "intel", "amd", "apple"):
            cand = SOFT_TO_HW_HEVC.get(v)
            if cand in av:
                return cand
        return ""
    cand = SOFT_TO_HW_HEVC.get(vendor)
    return cand if cand in av else ""


def has_any() -> bool:
    """是否存在任何可用硬件编码器。"""
    return len(available()) > 0


def clear_cache() -> None:
    """清空缓存。"""
    with _LOCK:
        _CACHE.clear()
