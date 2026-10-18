# -*- coding: utf-8 -*-
# FFmpeg 可执行文件定位与校验。
# 背景：本项目自身不含任何转码算法，全部转码能力来自 FFmpeg。
#       因此“能否找到并正确运行 ffmpeg.exe / ffprobe.exe”是整个软件的第一生命线。
# 容错设计：按“优先用户显式指定 → 随包副本 → 程序同级目录 → 系统 PATH”的顺序查找，
#       对所有候选逐个执行 `-version` 做真实校验（而不是只看文件是否存在）。

import os  # 用于路径拼接与存在性判断
import shutil  # 用于在 PATH 中查找可执行文件
import subprocess  # 用于执行 -version 做真实验证
import threading  # 用于缓存加锁
import logging  # 记录查找过程，便于排障

from app.util import paths  # 复用资源目录与用户配置目录解析
from app.util.config import config  # 读取用户自定义的 ffmpeg 路径

# 模块级日志器。
logger = logging.getLogger(__name__)

# 缓存：避免每次转码都重新查找与验证（查找涉及多次进程调用，代价不小）。
_CACHE = {}
# 缓存读写锁。
_CACHE_LOCK = threading.RLock()

# Windows 下的可执行文件扩展名。
_EXE_SUFFIX = ".exe" if os.name == "nt" else ""


def _candidate_paths(tool: str) -> list:
    """生成某个工具（ffmpeg/ffprobe/ffplay）的全部候选路径。

    参数:
        tool: 工具基名，不带扩展名，例如 "ffmpeg"。
    返回:
        去重后的候选绝对路径列表，按优先级从高到低排列。
    """
    # 结果容器。
    cands = []

    def _add(p):
        # 统一的追加逻辑：仅接受非空路径并做去重，避免重复做 -version 验证。
        if p and p not in cands:
            cands.append(p)

    # ---- 候选 1：用户在“设置”里显式指定的自定义路径 ----
    # 支持两种写法：直接指向 exe，或指向包含 exe 的目录。
    custom = config.get("ffmpeg_custom_path", "") or ""
    if custom:
        if os.path.isdir(custom):
            # 自定义的是目录：拼上工具名。
            _add(os.path.join(custom, tool + _EXE_SUFFIX))
        else:
            # 自定义的是文件：仅当其属于“同名工具”时才采纳。
            base = os.path.basename(custom).lower()
            if tool.lower() in base:
                _add(custom)

    # ---- 候选 2：随程序分发的副本（打包后位于 _MEIPASS/resources/ffmpeg） ----
    _add(os.path.join(paths.bundled_ffmpeg_dir(), tool + _EXE_SUFFIX))

    # ---- 候选 3：可执行文件同级目录（方便用户自行放置新版 ffmpeg） ----
    exe_dir = os.path.dirname(os.path.abspath(os.sys.executable))
    _add(os.path.join(exe_dir, tool + _EXE_SUFFIX))
    # 同级目录下的 resources/ffmpeg（源码运行时常见布局）。
    _add(os.path.join(exe_dir, "resources", "ffmpeg", tool + _EXE_SUFFIX))

    # ---- 候选 4：系统 PATH 中的同名工具 ----
    found = shutil.which(tool)
    _add(found)

    # 返回整理后的候选列表。
    return cands


def _verify(exe_path: str, timeout: float = 8.0) -> bool:
    """通过执行 `-version` 验证候选可执行文件确实可用。

    参数:
        exe_path: 待验证的路径。
        timeout: 超时秒数（防止个别损坏文件导致长时间挂起）。
    返回:
        True 表示可执行且返回了包含 "ffmpeg"/"ffprobe" 字样的版本信息。
    任何异常都视为验证失败，绝不向外抛出，保证查找流程能继续尝试下一个候选。
    """
    # 先做存在性与可执行性检查（is_file 会排除目录）。
    if not exe_path or not os.path.isfile(exe_path):
        return False
    try:
        # 执行 -version；捕获标准输出与错误输出。
        proc = subprocess.run(
            [exe_path, "-version"],  # 参数列表形式，避免 shell 注入与空格问题
            stdout=subprocess.PIPE,  # 收 stdout
            stderr=subprocess.STDOUT,  # stderr 合并进 stdout，简化处理
            timeout=timeout,  # 超时保护
            check=False,  # 不因非零返回码抛异常，由我们自行判断
            creationflags=_no_window_flag(),  # Windows 下不弹黑窗
        )
        # 解码输出（ffmpeg 版本信息是纯 ASCII，errors="ignore" 兜底）。
        text = (proc.stdout or b"").decode("utf-8", errors="ignore")
        # 判定条件一：进程正常退出（返回码为 0）。
        exited_normally = (proc.returncode == 0)
        # 判定条件二：输出里出现版本横幅，说明它确实是我们期望的 FFmpeg 家族程序。
        has_banner = ("ffmpeg version" in text) or ("ffprobe version" in text)
        # 两个条件同时满足才算验证通过，避免把同名但无关的程序误判为可用内核。
        return exited_normally and has_banner
    except Exception as exc:
        # 记录一次失败尝试（DEBUG 级别，避免正常缺失时噪声过大）。
        logger.debug("验证候选可执行文件失败 %s：%s", exe_path, exc)
        return False


def _no_window_flag() -> int:
    """返回适合当前平台的“不弹窗”创建标志。

    Windows 下若不加 CREATE_NO_WINDOW，打包后的 GUI 程序每调用一次 ffmpeg
    就会闪出一个黑色控制台窗口，体验极差。
    """
    # 仅 Windows 有意义；其它平台返回 0。
    if os.name == "nt":
        return 0x08000000  # subprocess.CREATE_NO_WINDOW 的数值形式
    return 0


def find_tool(tool: str = "ffmpeg") -> str:
    """查找并返回可用的工具绝对路径；找不到时返回空字符串。

    参数:
        tool: "ffmpeg" 或 "ffprobe"。
    返回:
        已验证可用的绝对路径；失败返回 ""。
    """
    # 加锁访问缓存，保证多线程首次查找不会重复执行。
    with _CACHE_LOCK:
        # 命中缓存直接返回（空字符串也缓存，避免每次都做一轮失败查找）。
        if tool in _CACHE:
            return _CACHE[tool]
        # 逐个候选验证，返回第一个通过的。
        for cand in _candidate_paths(tool):
            if _verify(cand):
                # 记录成功日志，便于用户确认到底用了哪一份内核。
                logger.info("已定位 %s：%s", tool, cand)
                _CACHE[tool] = cand
                return cand
        # 全部失败：记录警告并缓存空值。
        logger.warning("未能找到可用的 %s", tool)
        _CACHE[tool] = ""
        return ""


def ffmpeg_path() -> str:
    """便捷函数：返回 ffmpeg 绝对路径（可能为空字符串）。"""
    return find_tool("ffmpeg")


def ffprobe_path() -> str:
    """便捷函数：返回 ffprobe 绝对路径（可能为空字符串）。"""
    return find_tool("ffprobe")


def is_ready() -> bool:
    """判断转码内核是否就绪（ffmpeg 与 ffprobe 都可用）。"""
    # 两个都找到才算就绪：探测能力与转码能力缺一不可。
    return bool(ffmpeg_path()) and bool(ffprobe_path())


def clear_cache() -> None:
    """清空查找缓存。用户在设置里改了自定义路径后必须调用，否则仍用旧结果。"""
    with _CACHE_LOCK:
        _CACHE.clear()


def version_text() -> str:
    """获取 ffmpeg 版本信息的第一行，用于“关于/设置”页面展示。"""
    # 未找到内核时给出友好占位。
    exe = ffmpeg_path()
    if not exe:
        return "FFmpeg not found"
    try:
        # 执行 -version 取第一行。
        proc = subprocess.run(
            [exe, "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=8.0,
            check=False,
            creationflags=_no_window_flag(),
        )
        # 拆分首行。
        first = (proc.stdout or b"").decode("utf-8", errors="ignore").splitlines()
        return first[0] if first else "Unknown version"
    except Exception:
        # 任何异常都返回占位，避免界面因内核异常而崩溃。
        return "Failed to read version"
