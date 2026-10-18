# -*- coding: utf-8 -*-
# 视频快照（缩略图）抽取模块。
# 用途：从视频中抽取若干帧保存为图片，实现“视频 → 图片序列/封面/截图”的常见需求。
# 实现要点：
#   1) 使用 `-ss <时间> -i <输入> -frames:v 1` 的“快速定位”写法：把 -ss 放在 -i 之前，
#      让 ffmpeg 先按关键帧快速跳转，再解码到精确帧，速度快且足够精确。
#   2) 时间点策略：均匀分布 + 首尾保护（避开 0 秒黑场与结尾滚动字幕）。
#   3) 每张图独立一次子进程调用，避免复杂滤镜图带来的兼容性问题。

import os  # 路径拼接、判断输出
import subprocess  # 执行 ffmpeg
import logging  # 记录失败细节

from app.core import ffmpeg_locator  # 复用定位器

logger = logging.getLogger(__name__)

# 单张快照抽取超时（秒）。
SNAP_TIMEOUT = 60.0


def _even(value: int) -> int:
    """把宽度/高度规整为偶数，规避多数编码器对奇数尺寸的限制。"""
    # 至少保证为 2，且向下取偶。
    v = max(2, int(value))
    return v if v % 2 == 0 else v - 1


def pick_timestamps(duration: float, count: int = 5) -> list:
    """按总时长计算均匀分布的快照时间点。

    参数:
        duration: 视频总时长（秒）；<=0 时返回从 0 开始的默认点。
        count: 需要的张数。
    返回:
        去重且升序的时间点列表（秒）。
    说明:
        为避免抽到片头黑场与片尾字幕，把有效区间收缩到 [5%, 95%]。
    """
    # 张数至少为 1，避免调用方传 0 导致空列表。
    n = max(1, int(count))
    # 时长非法时给出一组常见默认点，保证功能仍可用。
    if duration is None or duration <= 0:
        return [0.0, 1.0, 2.0, 3.0, 4.0][:n]
    # 有效区间：片头片尾各留 5%。
    start = duration * 0.05
    end = duration * 0.95
    # 极端短视频（不足 0.2 秒）直接取中点。
    if end <= start:
        return [max(0.0, duration / 2.0)]
    # 只有一张时取中点。
    if n == 1:
        return [(start + end) / 2.0]
    # 均匀切分 n 个点：第 i 个点位于 start + i*(end-start)/(n-1)。
    step = (end - start) / (n - 1)
    points = [round(start + i * step, 3) for i in range(n)]
    # 去重并升序（四舍五入后可能产生重复）。
    return sorted(set(points))


def _default_ext(target_ext: str) -> str:
    """规整扩展名并给出兜底（默认 jpg）。"""
    # 去点、转小写、去空白。
    ext = str(target_ext or "jpg").lower().strip().lstrip(".")
    # 仅接受常见图片扩展名，其它一律回退 jpg（避免生成无法打开的怪文件）。
    if ext in ("jpg", "jpeg", "png", "bmp", "webp", "tiff", "tif"):
        return "jpg" if ext == "jpeg" else ext
    return "jpg"


def extract_snapshots(
        source: str,
        out_dir: str,
        base_name: str,
        duration: float = 0.0,
        count: int = 5,
        target_ext: str = "jpg",
        width: int = 0,
        quality: int = 3,
        prefix: str = "snap",
) -> list:
    """抽取多张快照。

    参数:
        source: 源视频路径。
        out_dir: 输出目录（不存在会自动创建）。
        base_name: 源文件主名（不含扩展名），用于生成输出名。
        duration: 总时长（秒），用于计算时间点。
        count: 张数。
        target_ext: 目标图片扩展名。
        width: 输出宽度；0 表示保持原始宽度。
        quality: 图片质量参数（-q:v 的数值，越小越好，jpg 常用 2~6）。
        prefix: 文件名前缀。
    返回:
        成功写出的图片绝对路径列表（失败项会被跳过，列表可能短于 count）。
    """
    # 取得 ffmpeg 路径；不可用则直接返回空列表。
    exe = ffmpeg_locator.ffmpeg_path()
    if not exe:
        logger.warning("抽取快照失败：未找到 ffmpeg")
        return []
    # 确保输出目录存在。
    os.makedirs(out_dir, exist_ok=True)
    # 规整扩展名。
    ext = _default_ext(target_ext)
    # 计算时间点。
    points = pick_timestamps(duration, count)
    # 结果列表。
    outputs = []
    # 逐时间点抽取。
    for idx, ts in enumerate(points):
        # 生成输出文件名：前缀_主名_序号.扩展名，序号补零便于排序。
        out_name = f"{prefix}_{base_name}_{idx + 1:02d}.{ext}"
        out_path = os.path.join(out_dir, out_name)
        # 组装参数：
        #   -ss 放在 -i 前做快速定位；
        #   -frames:v 1 只取一帧；
        #   -q:v 控制质量（jpg/mjpeg 有效）；
        #   -y 覆盖同名文件。
        args = [exe]
        # 为避免“输入文件名以 - 开头”被误判为选项，统一加 -i 前缀（-i 后即为文件路径）。
        args += ["-hide_banner", "-loglevel", "error"]
        args += ["-ss", f"{ts}"]  # 快速定位到目标时间
        args += ["-i", os.path.abspath(source)]  # 输入文件
        args += ["-frames:v", "1"]  # 只输出一帧
        # 若指定宽度，等比缩放并保证偶数宽度。
        if width and width > 0:
            # scale=W:-2 表示宽度固定为 W，高度按比例自动计算并对齐到偶数。
            args += ["-vf", f"scale={_even(width)}:-2"]
        # 输出质量参数（PNG 忽略该参数也无害）。
        args += ["-q:v", str(max(1, min(31, int(quality))))]
        args += ["-y", out_path]
        try:
            # 执行抽取。
            proc = subprocess.run(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=SNAP_TIMEOUT,
                check=False,
                creationflags=ffmpeg_locator._no_window_flag(),
            )
            # 判定成功：返回码为 0 且文件确实生成且非空。
            if proc.returncode == 0 and os.path.isfile(out_path) and os.path.getsize(out_path) > 0:
                outputs.append(out_path)
            else:
                # 记录失败原因（截取尾部信息），但不中断其它时间点的抽取。
                err = (proc.stdout or b"").decode("utf-8", errors="ignore")[-400:]
                logger.warning("快照抽取失败（第 %d 张，时间 %.3fs）：%s", idx + 1, ts, err)
        except subprocess.TimeoutExpired:
            logger.warning("快照抽取超时（第 %d 张，时间 %.3fs）", idx + 1, ts)
        except Exception as exc:
            logger.warning("快照抽取异常（第 %d 张）：%s", idx + 1, exc)
    return outputs


def extract_single(source: str, out_path: str, timestamp: float = 0.0, width: int = 0, quality: int = 3) -> bool:
    """抽取单张快照到指定路径。

    参数:
        source: 源视频。
        out_path: 目标图片的完整路径。
        timestamp: 时间点（秒）。
        width: 目标宽度；0 表示原始宽度。
        quality: 质量参数。
    返回:
        True 表示成功。
    """
    exe = ffmpeg_locator.ffmpeg_path()
    if not exe:
        return False
    # 确保输出目录存在。
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    args = [exe, "-hide_banner", "-loglevel", "error", "-ss", f"{max(0.0, float(timestamp))}",
            "-i", os.path.abspath(source), "-frames:v", "1"]
    # 可选缩放。
    if width and width > 0:
        args += ["-vf", f"scale={_even(width)}:-2"]
    # 质量与覆盖。
    args += ["-q:v", str(max(1, min(31, int(quality)))), "-y", out_path]
    try:
        proc = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              timeout=SNAP_TIMEOUT, check=False,
                              creationflags=ffmpeg_locator._no_window_flag())
        return proc.returncode == 0 and os.path.isfile(out_path) and os.path.getsize(out_path) > 0
    except Exception:
        return False
