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
    title: str = "未知错误"  # 简短标题（中文）
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
        "输入文件损坏或格式不被识别",
        "源文件可能已损坏或扩展名与实际内容不符。请尝试用其它工具修复，或换一个文件。",
    ),
    (
        r"No such file or directory|could not find codec parameters",
        "FILE_MISSING",
        "文件不存在或无法读取",
        "请确认文件仍然存在、未被移动，且当前程序有权读取它。",
    ),
    (
        r"Permission denied|Access is denied",
        "PERMISSION",
        "没有写入/读取权限",
        "输出目录可能被占用或只读。请更换输出目录，或关闭正在占用该文件的程序。",
    ),
    (
        r"Invalid argument|Error number -22|Invalid encoder|Unknown encoder",
        "BAD_ARGS",
        "参数或编码器不受当前内核支持",
        "请更换编码器/容器组合，或在“设置”中改用其它内核版本的 FFmpeg。",
    ),
    (
        r"Conversion failed|Error while opening encoder|Error initializing output stream",
        "ENCODER_INIT",
        "编码器初始化失败",
        "常见原因：分辨率/像素格式不被该编码器支持。可尝试改为 yuv420p、或选择 libx264 等通用编码器。",
    ),
    (
        r"No space left on device|disk full",
        "NO_SPACE",
        "磁盘空间不足",
        "请清理目标磁盘空间，或改写到空间充足的目录。",
    ),
    (
        r"width not divisible by 2|height not divisible by 2",
        "ODD_DIMENSION",
        "分辨率出现奇数（宽或高不能被 2 整除）",
        "H.264/H.265 要求宽高为偶数。已建议自动补边/裁剪到偶数尺寸后重试。",
    ),
    (
        r"does not contain any stream|Output file does not contain any stream",
        "NO_STREAM",
        "输出文件不含任何音视频流",
        "所选容器不支持源文件的流类型（例如把视频放进仅支持音频的容器）。请更换目标格式。",
    ),
    (
        r"already exists. Overwrite|File .* already exists",
        "OUTPUT_EXISTS",
        "输出文件已存在",
        "可开启“覆盖输出”，或让程序自动追加序号。",
    ),
    (
        r"Unknown decoder|Decoder .* not found",
        "NO_DECODER",
        "缺少解码器",
        "源文件使用了当前内核未包含的解码器。请更换内核，或先转换为通用格式。",
    ),
    (
        r"Hardware|nvenc|qsv|vaapi|amf|d3d11va",
        "HW_ACCEL",
        "硬件加速失败",
        "显卡驱动或硬件编码器不可用。已在“设置”中关闭硬件加速会最稳妥，或改选软件编码器。",
    ),
    (
        r"Timed out|timeout|Operation timed out",
        "TIMEOUT",
        "操作超时",
        "文件可能过大或磁盘响应慢。请重试，或改用更快的预设/更低的码率。",
    ),
    (
        r"Invalid .* bitrate|bitrate .* out of range",
        "BAD_BITRATE",
        "码率取值不合法",
        "请把视频/音频码率调整到合理范围（视频通常 0.2~50 Mb/s，音频 32~512 kb/s）。",
    ),
    (
        r"Stream map .* matches no streams|Invalid stream specifier",
        "BAD_MAP",
        "流选择（-map）与实际流不匹配",
        "源文件可能没有你指定的音轨/字幕轨。请改选“自动选择流”。",
    ),
    (
        r"Subtitle|subtitle|Subtitles",
        "SUBTITLE",
        "字幕处理失败",
        "字幕文件编码或格式不被支持，可先另存为 UTF-8 的 SRT，或关闭字幕烧录。",
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
        "未能自动识别具体原因。请展开下方原始错误信息，或在“设置”中打开 DEBUG 日志后重试以获取更多线索。"
    )
    # 返回码为 0 却进入诊断流程属于异常情况，单独提示。
    if return_code == 0:
        return Diagnosis(code="UNKNOWN", title="输出异常但返回码为 0", detail=detail, advice=generic_advice)
    return Diagnosis(code="UNKNOWN", title="转换失败（原因未识别）", detail=detail, advice=generic_advice)


def is_recoverable(code: str) -> bool:
    """判断某类错误是否值得自动重试（带补救参数）。"""
    # 目前仅这两类提供了明确的自动补救方案。
    return code in ("ODD_DIMENSION", "HW_ACCEL")


# 错误码到中文短名的映射，供列表状态列展示。
CODE_SHORT = {
    "BAD_INPUT": "源文件损坏",
    "FILE_MISSING": "文件缺失",
    "PERMISSION": "权限不足",
    "BAD_ARGS": "参数不支持",
    "ENCODER_INIT": "编码器初始化失败",
    "NO_SPACE": "磁盘已满",
    "ODD_DIMENSION": "奇数分辨率",
    "NO_STREAM": "无有效流",
    "OUTPUT_EXISTS": "输出已存在",
    "NO_DECODER": "缺少解码器",
    "HW_ACCEL": "硬件加速失败",
    "TIMEOUT": "超时",
    "BAD_BITRATE": "码率非法",
    "BAD_MAP": "流选择不匹配",
    "SUBTITLE": "字幕失败",
    "UNKNOWN": "未知错误",
}
