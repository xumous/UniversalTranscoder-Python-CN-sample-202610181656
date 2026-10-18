# -*- coding: utf-8 -*-
# FFmpeg 错误分类与用户可读建议。
# 背景：ffmpeg 的报错文本对普通用户极不友好（例如 "Invalid argument"、"Conversion failed!"）。
#       本模块把常见失败模式归纳为若干类，并给出“人话”建议与可选的自动补救建议。
# 设计原则：分类是“尽力而为”的启发式匹配，匹配不到时归入 UNKNOWN，绝不因此抛异常。

import re  # 用正则做模式匹配
from dataclasses import dataclass  # 承载分类结果


@dataclass
class Diagnosis:
    """一次失败的诊断结果。"""
    code: str = "UNKNOWN"  # 机器可读的错误码
    title: str = "Unknown error"  # 简短标题（中文）
    detail: str = ""  # 原始错误摘要（截取最后若干行）
    advice: str = ""  # 给用户的处理建议
    retry_args: list = None  # 建议的自动重试附加参数（None 表示无）

    def __post_init__(self):
        # 默认给 retry_args 一个空列表，避免调用方判空。
        if self.retry_args is None:
            self.retry_args = []


# 错误模式表：每项为 (正则, 错误码, 标题, 建议)。
# 顺序有意义：越具体的模式越靠前，避免被通用模式提前命中。
_PATTERNS = [
    (
        r"moov atom not found|Invalid data found when processing input",
        "BAD_INPUT",
        "Input file is corrupted or the format is unrecognized",
        "The source may be corrupted, or its extension does not match its content. Try repairing it with another tool, or use a different file.",
    ),
    (
        r"No such file or directory|could not find codec parameters",
        "FILE_MISSING",
        "File does not exist or cannot be read",
        "Make sure the file still exists, has not been moved, and that this program can read it.",
    ),
    (
        r"Permission denied|Access is denied",
        "PERMISSION",
        "No write/read permission",
        "The output folder may be locked or read-only. Choose another output folder, or close the program that is using the file.",
    ),
    (
        r"Invalid argument|Error number -22|Invalid encoder|Unknown encoder",
        "BAD_ARGS",
        "Parameter or encoder not supported by this core",
        "Try a different encoder/container combination, or switch to another FFmpeg core version in Settings.",
    ),
    (
        r"Conversion failed|Error while opening encoder|Error initializing output stream",
        "ENCODER_INIT",
        "Encoder initialization failed",
        "Common causes: the resolution/pixel format is unsupported by this encoder. Try switching to yuv420p, or choose a general encoder such as libx264.",
    ),
    (
        r"No space left on device|disk full",
        "NO_SPACE",
        "Not enough disk space",
        "Free up space on the target disk, or write to a folder with more free space.",
    ),
    (
        r"width not divisible by 2|height not divisible by 2",
        "ODD_DIMENSION",
        "Odd resolution (width or height not divisible by 2)",
        "H.264/H.265 require an even width and height. A retry with automatic padding/cropping to even dimensions has been suggested.",
    ),
    (
        r"does not contain any stream|Output file does not contain any stream",
        "NO_STREAM",
        "Output file contains no audio/video stream",
        "The chosen container does not support the source's stream type (for example, placing video into an audio-only container). Please choose another target format.",
    ),
    (
        r"already exists. Overwrite|File .* already exists",
        "OUTPUT_EXISTS",
        "Output file already exists",
        "You can enable \"overwrite output\", or let the program append a sequence number automatically.",
    ),
    (
        r"Unknown decoder|Decoder .* not found",
        "NO_DECODER",
        "Missing decoder",
        "The source uses a decoder not included in this core. Please switch the core, or convert to a general format first.",
    ),
    (
        r"Hardware|nvenc|qsv|vaapi|amf|d3d11va",
        "HW_ACCEL",
        "Hardware acceleration failed",
        "The GPU driver or hardware encoder is unavailable. Disabling hardware acceleration in Settings is the safest option, or switch to a software encoder.",
    ),
    (
        r"Timed out|timeout|Operation timed out",
        "TIMEOUT",
        "Operation timed out",
        "The file may be too large or the disk is slow to respond. Please retry, or use a faster preset / lower bitrate.",
    ),
    (
        r"Invalid .* bitrate|bitrate .* out of range",
        "BAD_BITRATE",
        "Invalid bitrate value",
        "Please set the video/audio bitrate to a sensible range (video usually 0.2-50 Mb/s, audio 32-512 kb/s).",
    ),
    (
        r"Stream map .* matches no streams|Invalid stream specifier",
        "BAD_MAP",
        "Stream selection (-map) does not match the actual streams",
        "The source may not have the audio/subtitle track you specified. Please switch to \"auto-select streams\".",
    ),
    (
        r"Subtitle|subtitle|Subtitles",
        "SUBTITLE",
        "Subtitle processing failed",
        "The subtitle file's encoding or format is unsupported. Save it as a UTF-8 SRT first, or turn off subtitle burning.",
    ),
]


def classify(stderr_text: str, return_code: int = 1) -> Diagnosis:
    """根据 ffmpeg 的错误输出生成诊断结果。

    参数:
        stderr_text: 子进程收集到的 stderr 文本（可能很长）。
        return_code: 子进程返回码。
    返回:
        Diagnosis 对象（永不抛异常）。
    """
    # 统一为字符串并做空值保护。
    text = str(stderr_text or "")
    # 提取末尾若干行作为“原始错误摘要”：ffmpeg 真正的原因通常在最后几行。
    tail_lines = [ln for ln in text.splitlines() if ln.strip()][-8:]
    detail = "\n".join(tail_lines)[-1500:]  # 限制长度，避免界面被超长文本撑爆
    # 逐一匹配模式表。
    for pattern, code, title, advice in _PATTERNS:
        try:
            # 使用忽略大小写的搜索，命中即返回。
            if re.search(pattern, text, re.IGNORECASE):
                # 针对可自动补救的两类错误，附带重试参数建议。
                retry = []
                # 奇数宽高：补边到偶数（pad 滤镜会自动把画面垫到偶数尺寸）。
                if code == "ODD_DIMENSION":
                    retry = ["-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2"]
                # 硬件加速失败：加 -hwaccel none 强制走软件路径。
                if code == "HW_ACCEL":
                    retry = ["-hwaccel", "none"]
                return Diagnosis(code=code, title=title, detail=detail, advice=advice, retry_args=retry)
        except re.error:
            # 正则本身写错也应跳过该条，而不是让整个诊断崩掉。
            continue
    # 未命中任何模式：给出通用诊断，并把原始错误尾部保留下来供用户查看。
    generic_advice = (
        "Could not automatically identify the specific cause. Expand the raw error below, or enable DEBUG logging in Settings and retry to get more clues."
    )
    # 返回码为 0 却进入诊断流程属于异常情况，单独提示。
    if return_code == 0:
        return Diagnosis(code="UNKNOWN", title="Output abnormal but return code is 0", detail=detail,
                         advice=generic_advice)
    return Diagnosis(code="UNKNOWN", title="Conversion failed (cause not identified)", detail=detail,
                     advice=generic_advice)


def is_recoverable(code: str) -> bool:
    """判断某类错误是否值得自动重试（带补救参数）。"""
    # 目前仅这两类提供了明确的自动补救方案。
    return code in ("ODD_DIMENSION", "HW_ACCEL")


# 错误码到中文短名的映射，供列表状态列展示。
CODE_SHORT = {
    "BAD_INPUT": "Bad input",
    "FILE_MISSING": "File missing",
    "PERMISSION": "Permission denied",
    "BAD_ARGS": "Unsupported args",
    "ENCODER_INIT": "Encoder init failed",
    "NO_SPACE": "Disk full",
    "ODD_DIMENSION": "Odd dimensions",
    "NO_STREAM": "No valid stream",
    "OUTPUT_EXISTS": "Output exists",
    "NO_DECODER": "Decoder missing",
    "HW_ACCEL": "HW accel failed",
    "TIMEOUT": "Timeout",
    "BAD_BITRATE": "Invalid bitrate",
    "BAD_MAP": "Bad stream map",
    "SUBTITLE": "Subtitle failed",
    "UNKNOWN": "Unknown",
}
