# -*- coding: utf-8 -*-
# 人类可读格式化工具。
# 用途：把比特/字节、时长秒数、码率、帧率等原始数值，转成界面上友好的字符串。
# 之所以单独成模块：GUI、Web 后端、日志三处都要用同一套规则，避免出现“同一数字两种显示”的不一致。

import math  # 用于对数运算与向下取整


def human_size(num_bytes) -> str:
    """把字节数格式化为 B / KB / MB / GB / TB。

    参数:
        num_bytes: 字节数（可为 int/float/字符串数字；非法输入回退为 "0 B"）。
    返回:
        形如 "1.45 GB" 的字符串。
    """
    try:
        # 先尝试转为浮点，兼容来自 JSON 的字符串数字。
        size = float(num_bytes)
    except (TypeError, ValueError):
        # 转换失败：返回占位字符串，保证界面不出现空白或异常。
        return "0 B"
    # 负值在业务上无意义（可能是估算误差），统一按 0 处理。
    if size <= 0:
        return "0 B"
    # 单位阶梯表；索引 0 为字节。
    units = ["B", "KB", "MB", "GB", "TB", "PB"]
    # 以 1024 为基计算所处的单位级别：log(size)/log(1024) 向下取整。
    idx = min(int(math.log(size, 1024)), len(units) - 1)
    # 按级别换算数值。
    value = size / (1024 ** idx)
    # 字节级别显示为整数更直观；其余保留两位小数。
    if idx == 0:
        return f"{int(value)} {units[idx]}"
    return f"{value:.2f} {units[idx]}"


def human_duration(seconds, with_ms: bool = False) -> str:
    """把秒数格式化为 HH:MM:SS 或 HH:MM:SS.mmm。

    参数:
        seconds: 秒数（可为字符串）。
        with_ms: 是否保留毫秒（用于快照时间点展示）。
    返回:
        形如 "01:23:45" 或 "01:23:45.678" 的字符串；非法输入返回 "--:--:--"。
    """
    try:
        # 转为浮点；负值视为非法（时长不可能为负）。
        total = float(seconds)
        if total < 0:
            raise ValueError("negative")
    except (TypeError, ValueError):
        # 非法输入给出统一占位，避免界面空着让人困惑。
        return "--:--:--" + (".---" if with_ms else "")
    # 取整秒与毫秒部分。
    whole = int(total)  # 整秒
    millis = int(round((total - whole) * 1000))  # 毫秒（四舍五入）
    # 防御毫秒进位到 1000 的边界情况（例如 3.9996 秒）。
    if millis >= 1000:
        millis -= 1000
        whole += 1
    # 拆分时分秒。
    hours = whole // 3600
    minutes = (whole % 3600) // 60
    secs = whole % 60
    # 基础字符串固定两位补零。
    base = f"{hours:02d}:{minutes:02d}:{secs:02d}"
    # 按需追加毫秒三位补零。
    return f"{base}.{millis:03d}" if with_ms else base


def human_bitrate(bits_per_sec) -> str:
    """把比特率格式化为 kb/s 或 Mb/s。

    参数:
        bits_per_sec: 每秒比特数（ffprobe 返回的 bit_rate 通常是该量纲）。
    返回:
        形如 "5.20 Mb/s" 的字符串；非法输入返回 "未知"。
    """
    try:
        value = float(bits_per_sec)
    except (TypeError, ValueError):
        # 很多容器/编码不写码率字段，此时显示“未知”更诚实。
        return "Unknown"
    # 非常小的值（如 0）没有意义。
    if value <= 0:
        return "Unknown"
    # 以 1000 为基（比特率惯用十进制单位）。
    if value >= 1_000_000:
        return f"{value / 1_000_000:.2f} Mb/s"
    if value >= 1_000:
        return f"{value / 1_000:.2f} kb/s"
    return f"{int(value)} b/s"


def human_fps(fps) -> str:
    """把帧率格式化为 "25" 或 "29.97" 这样的简短字符串。

    参数:
        fps: 帧率（可能是 "30000/1001" 分数形式）。
    返回:
        形如 "29.97" 的字符串；非法输入返回 "未知"。
    """
    try:
        # ffprobe 常返回分数字符串，需要先解析。
        if isinstance(fps, str) and "/" in fps:
            num, den = fps.split("/", 1)  # 拆分子分母
            den_f = float(den)  # 分母转浮点
            if den_f == 0:  # 分母为 0 是非法分数
                return "Unknown"
            value = float(num) / den_f  # 计算真实帧率
        else:
            value = float(fps)  # 普通数值直接转换
    except (TypeError, ValueError):
        return "Unknown"
    # 帧率为 0 或负同样视为未知。
    if value <= 0:
        return "Unknown"
    # 整数帧率不带小数位，更干净（如 25 而非 25.00）。
    if abs(value - round(value)) < 1e-6:
        return str(int(round(value)))
    return f"{value:.2f}"


def percent(done: float, total: float) -> float:
    """计算百分比并做除零保护，返回值范围被裁剪到 [0, 100]。

    参数:
        done: 已完成量（如已编码时长/已处理字节）。
        total: 总量；为 0 时返回 0，避免除零异常。
    返回:
        0~100 的浮点数。
    """
    try:
        # 总量非法或为零时无法计算比例。
        if not total or float(total) <= 0:
            return 0.0
        # 计算比例并转百分比。
        p = float(done) / float(total) * 100.0
    except (TypeError, ValueError, ZeroDivisionError):
        # 任何异常都回退为 0，保证进度条不报错。
        return 0.0
    # 裁剪：进度可能因 ffmpeg 估算波动略微超过 100。
    return max(0.0, min(100.0, p))


def shorten(text: str, limit: int = 48) -> str:
    """把过长文本截断并在末尾加省略号，用于列表项展示。

    参数:
        text: 原始文本。
        limit: 最大字符数。
    返回:
        截断后的字符串。
    """
    # 空值直接返回空串，避免对 None 调用 len。
    if not text:
        return ""
    # 转为字符串并判断长度。
    s = str(text)
    if len(s) <= limit:
        return s
    # 保留 limit-1 个字符再加一个省略号，总体不超过 limit。
    return s[: max(1, limit - 1)] + "…"
