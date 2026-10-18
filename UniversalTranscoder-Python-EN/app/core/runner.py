# -*- coding: utf-8 -*-
# 子进程执行器（本项目算法核心之二）。
# 职责：执行 ffmpeg 命令，并把“机器可读的进度流”解析成结构化进度事件，回调给界面。
# 关键技术点：
#   1) 使用 `-progress pipe:1`：ffmpeg 会把形如 `key=value` 的进度块按固定节奏写到标准输出，
#      比解析人类可读的 stderr（形如 frame=... time=...）稳定得多，也不受语言环境影响。
#   2) 进度块的终止标志是 `progress=continue` 或 `progress=end`，据此切分记录。
#   3) 取消机制：先调用 terminate()（Windows 上等价于终止进程），等待短暂时间后仍未退出则 kill()，
#      确保“停止”按钮点击后能迅速回收资源，而不是留下僵尸进程继续占满 CPU。
#   4) 所有回调都在工作线程中触发，界面层负责把回调切换到主线程（Qt 的信号机制天然支持跨线程）。

import os  # 路径处理（判定输出文件是否存在、大小）
import subprocess  # 启动与管理子进程
import threading  # 取消事件
import time  # 计时（计算速度与剩余时间）
import logging  # 记录执行细节
from dataclasses import dataclass, field  # 结果结构

from app.core import ffmpeg_locator  # 复用“不弹窗”标志

logger = logging.getLogger(__name__)


@dataclass
class Progress:
    """一次进度更新。"""
    percent: float = 0.0  # 0~100
    out_time: float = 0.0  # 已编码的媒体时间（秒）
    fps: float = 0.0  # 当前编码帧率
    frame: int = 0  # 已编码帧数
    speed: float = 0.0  # 相对实时的倍速（1.0 表示 1 倍速）
    bitrate: str = ""  # 当前码率文本
    total_size: int = 0  # 当前输出已写入字节数
    eta: float = -1.0  # 预计剩余秒数（-1 表示未知）
    phase: str = ""  # 阶段名（中文，来自 strings.Phase）
    done: bool = False  # 是否已结束


@dataclass
class RunResult:
    """一次执行的结果。"""
    returncode: int = -1  # 进程返回码（0 表示成功）
    stderr: str = ""  # 收集到的错误输出（用于诊断）
    stdout: str = ""  # 收集到的进度输出（一般不需要展示）
    elapsed: float = 0.0  # 总耗时（秒）
    out_path: str = ""  # 输出文件路径
    out_size: int = 0  # 输出文件大小（字节）
    canceled: bool = False  # 是否被用户取消

    @property
    def ok(self) -> bool:
        """是否成功：返回码为 0、未被取消、且输出文件存在且非空。"""
        return (self.returncode == 0) and (not self.canceled) and (self.out_size > 0)


def _extract_out_path(args) -> str:
    """从参数列表中提取输出文件路径（即最后一个非选项参数）。

    做法：从末尾往前找，跳过以 "-" 开头的项（排除最后一个参数本身是选项的极端情况），
    同时把该位置之后的参数视为输出相关参数（ffmpeg 允许输出 URL 位于末尾）。
    """
    # 参数可能为空（防御）。
    if not args:
        return ""
    # 取最后一个参数作为候选；ffmpeg 的用法里输出文件恒为最后一个位置参数。
    for a in reversed(args):
        s = str(a)
        # 跳过选项项（以 - 开头）。
        if s.startswith("-"):
            continue
        # 跳过可能存在的选项“值”（这里无法完全区分，但结合我们的构建器可知输出必在最后）。
        return s
    return ""


def _insert_progress_options(args):
    """在输出路径之前插入进度相关选项，返回新的参数列表。

    为什么插在输出路径之前：`-progress` 与 `-nostats` 属于输出侧选项，
    必须出现在对应输出文件之前；若放在最后（输出文件之后）会被 ffmpeg 判为无效。
    """
    # 拷贝一份，避免修改调用方传入的列表（调用方可能还要展示原始命令）。
    new_args = list(args)
    # 找到最后一个“位置参数”（即输出路径）的索引。
    out_idx = None
    for i in range(len(new_args) - 1, -1, -1):
        if not str(new_args[i]).startswith("-"):
            out_idx = i
            break
    # 未找到输出位置（异常情况）则在末尾追加。
    marker = ["-progress", "pipe:1", "-nostats", "-loglevel", "error"]
    if out_idx is None:
        return new_args + marker
    # 把标记插入到输出路径之前。
    return new_args[:out_idx] + marker + new_args[out_idx:]


class Runner:
    """转码执行器。

    生命周期：
        runner = Runner()
        ok = runner.run(args, duration=..., on_progress=..., on_started=...)
    """

    def __init__(self):
        # 当前子进程句柄；未运行时为 None。
        self._proc = None
        # 取消事件：外部调用 cancel() 触发。
        self._cancel = threading.Event()
        # 保护子进程句柄的锁。
        self._lock = threading.RLock()
        # 是否已被取消（供结果标记使用）。
        self._canceled = False

    def cancel(self) -> None:
        """请求取消当前任务。"""
        # 置位取消标志。
        self._cancel.set()
        # 记录取消状态。
        self._canceled = True
        # 尝试终止子进程。
        with self._lock:
            proc = self._proc
        if proc and proc.poll() is None:
            try:
                # 先温和终止。
                proc.terminate()
            except Exception:
                # 终止失败（进程可能瞬间已退出）不必处理。
                pass
            # 给 2 秒时间优雅退出。
            try:
                proc.wait(timeout=2.0)
            except Exception:
                # 仍未退出则强杀，避免残留进程。
                try:
                    proc.kill()
                except Exception:
                    pass

    def is_running(self) -> bool:
        """当前是否有任务在运行。"""
        with self._lock:
            return self._proc is not None and self._proc.poll() is None

    def run(self, args, duration: float = 0.0, on_progress=None,
            on_started=None, extra_env=None) -> RunResult:
        """执行一条 ffmpeg 命令。

        参数:
            args: 完整参数列表（含 ffmpeg 路径与输出路径）。
            duration: 源媒体总时长（秒），用于把 out_time 换算成百分比；0 表示未知。
            on_progress: 进度回调，签名 f(Progress) -> None。
            on_started: 进程启动后回调，签名 f(pid: int) -> None。
            extra_env: 追加的环境变量（字典），用于特殊场景（如设置编码线程亲和性）。
        返回:
            RunResult。
        """
        # 重置取消状态，保证 Runner 可被复用。
        self._cancel.clear()
        self._canceled = False
        # 结果对象，先记录输出路径。
        result = RunResult(out_path=_extract_out_path(args))
        # 防御式创建输出目录：若目录缺失，ffmpeg 会直接报 “No such file or directory”。
        # 主流程（TaskQueue）本身也会创建目录，这里是双保险，确保 Runner 被独立调用时也能正确工作。
        try:
            if result.out_path:
                out_dir = os.path.dirname(os.path.abspath(result.out_path))
                if out_dir:
                    os.makedirs(out_dir, exist_ok=True)
        except OSError:
            # 目录创建失败（如权限不足）交由 ffmpeg 报错并在后续被错误分类模块识别。
            pass
        # 在输出前插入进度选项。
        run_args = _insert_progress_options(args)
        # 组装环境变量：继承当前环境。
        env = os.environ.copy()
        if extra_env:
            env.update({str(k): str(v) for k, v in extra_env.items()})
        # 防止 ffmpeg 在无标准输入时阻塞（例如需要确认覆盖）——虽然已用 -y，仍加保险。
        env.setdefault("AV_LOG_FORCE_NOCOLOR", "1")
        # 记录开始时间。
        start_ts = time.time()
        # 进度解析用的临时状态：当前正在累积的记录块。
        cur = {}
        # 已编码时长（秒）与上次上报时间，用于计算瞬时速度。
        last_out_time = 0.0
        last_report_ts = start_ts
        # 收集标准错误（用于诊断），u 分块读取后拼接。
        stderr_chunks = []
        # 收集标准输出（进度原文），仅在 DEBUG 时保留，避免内存无谓增长。
        stdout_chunks = []
        try:
            # 启动子进程：
            #   - stdin=DEVNULL：避免等待输入
            #   - stdout=PIPE：读取进度流
            #   - stderr=PIPE：读取错误信息
            proc = subprocess.Popen(
                run_args,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=env,
                creationflags=ffmpeg_locator._no_window_flag(),
                # 说明：此处刻意不设置 bufsize。二进制管道不支持行缓冲（bufsize=1 会触发 RuntimeWarning），
                # 而 ffmpeg 每写完一个进度块都会主动 flush，因此默认缓冲已能保证进度“及时到达”。
            )
        except FileNotFoundError:
            # ffmpeg 路径失效：直接返回失败，并给出明确文本。
            result.returncode = -1
            result.stderr = "Cannot start FFmpeg: the executable does not exist or the path is invalid."
            result.elapsed = time.time() - start_ts
            return result
        except Exception as exc:
            # 其它启动异常同样兜底。
            result.returncode = -1
            result.stderr = f"Cannot start FFmpeg: {exc}"
            result.elapsed = time.time() - start_ts
            return result

        # 保存句柄，供 cancel() 使用。
        with self._lock:
            self._proc = proc
        # 通知调用方进程已启动。
        if on_started:
            try:
                on_started(proc.pid)
            except Exception:
                # 回调异常不影响主流程。
                pass

        # ---- 读取标准错误（独立线程，避免 stderr 缓冲区写满导致子进程阻塞）----
        def _drain_stderr():
            # 逐行读取，直到进程结束。
            try:
                for raw in iter(proc.stderr.readline, b""):
                    stderr_chunks.append(raw)
            except Exception:
                # 读取中断（进程被杀）属正常现象。
                pass

        # 启动错误读取线程。
        err_thread = threading.Thread(target=_drain_stderr, name="ffmpeg-stderr", daemon=True)
        err_thread.start()

        # ---- 主线程读取标准输出（进度流）----
        try:
            while True:
                # 读取一行进度文本；若进程结束返回空字节串。
                raw_line = proc.stdout.readline()
                if not raw_line:
                    # 输出结束：检查进程是否已退出。
                    if proc.poll() is not None:
                        break
                    # 进程仍在运行但暂时无输出：稍作等待避免忙等。
                    time.sleep(0.02)
                    continue
                # 解码为字符串并去空白。
                line = raw_line.decode("utf-8", errors="ignore").strip()
                # DEBUG 级别下保留原文，便于排查进度解析问题。
                if logger.isEnabledFor(logging.DEBUG):
                    stdout_chunks.append(line)
                # 空行忽略。
                if not line:
                    continue
                # 形如 key=value 才算有效进度字段。
                if "=" not in line:
                    continue
                key, _, value = line.partition("=")
                key = key.strip()
                value = value.strip()
                # 记录到当前累积块。
                cur[key] = value
                # 收到 progress 行说明一个进度块结束，可以上报。
                if key == "progress":
                    # 解析出结构化进度并回调。
                    prog = _parse_progress_block(cur, duration, start_ts)
                    # 用瞬时速度修正（ffmpeg 的 speed 字段有时为 N/A）。
                    now = time.time()
                    delta_time = prog.out_time - last_out_time
                    delta_wall = max(1e-6, now - last_report_ts)
                    if delta_time > 0:
                        prog.speed = delta_time / delta_wall
                        # 用更精确的瞬时速度重算 ETA。
                        if duration > 0 and prog.speed > 0:
                            prog.eta = max(0.0, (duration - prog.out_time) / prog.speed)
                    last_out_time = prog.out_time
                    last_report_ts = now
                    # 标记是否结束。
                    prog.done = (value == "end")
                    # 触发回调（异常不影响执行）。
                    if on_progress:
                        try:
                            on_progress(prog)
                        except Exception as exc:
                            logger.debug("进度回调异常：%s", exc)
                    # 清空当前块，准备接收下一块。
                    cur = {}
        except Exception as exc:
            # 读取异常（如管道断裂）记录后继续走收尾逻辑。
            logger.debug("读取 ffmpeg 进度时异常：%s", exc)
        finally:
            # 等待子进程结束。
            try:
                proc.wait(timeout=30.0)
            except Exception:
                # 未在 30 秒内退出：强制结束，避免卡住整个队列。
                try:
                    proc.kill()
                except Exception:
                    pass
            # 等待错误读取线程收尾（给 3 秒，防止极端情况死等）。
            err_thread.join(timeout=3.0)

        # 汇总结果。
        result.returncode = proc.returncode if proc.returncode is not None else -1
        result.stderr = b"".join(stderr_chunks).decode("utf-8", errors="ignore")
        result.stdout = "\n".join(stdout_chunks)
        result.elapsed = time.time() - start_ts
        result.canceled = self._canceled
        # 统计输出文件大小。
        try:
            if result.out_path and os.path.isfile(result.out_path):
                result.out_size = os.path.getsize(result.out_path)
        except OSError:
            result.out_size = 0
        # 清理句柄。
        with self._lock:
            self._proc = None
        # 返回结果。
        return result


def _parse_progress_block(block: dict, duration: float, start_ts: float) -> Progress:
    """把一个进度块（key=value 字典）解析为 Progress 对象。"""
    # 新建对象。
    p = Progress()
    # ---- 已编码时间：优先 out_time_us（微秒，精度最高）----
    out_time = 0.0
    # out_time_us 是微秒。
    if "out_time_us" in block:
        out_time = _to_float(block.get("out_time_us"), 0.0) / 1_000_000.0
    elif "out_time_ms" in block:
        # 注意：部分版本该字段名虽为 ms 实为微秒，这里按微秒处理更稳。
        out_time = _to_float(block.get("out_time_ms"), 0.0) / 1_000_000.0
    elif "out_time" in block:
        # 形如 "00:00:05.000000"。
        out_time = _hms_to_seconds(block.get("out_time"))
    # 负值归零。
    p.out_time = max(0.0, out_time)
    # ---- 百分比 ----
    if duration and duration > 0:
        p.percent = max(0.0, min(99.0, p.out_time / duration * 100.0))
        # 结束时会由 done 标志推到 100%。
    # ---- 帧率与帧数 ----
    p.fps = _to_float(block.get("fps"), 0.0)
    p.frame = int(_to_float(block.get("frame"), 0.0))
    # ---- 速度（可能为 "1.5x" 或 "N/A"）----
    speed_raw = str(block.get("speed", "") or "")
    if speed_raw.endswith("x"):
        p.speed = _to_float(speed_raw[:-1], 0.0)
    # ---- 码率文本 ----
    p.bitrate = str(block.get("bitrate", "") or "")
    # ---- 输出体积 ----
    p.total_size = int(_to_float(block.get("total_size"), 0.0))
    # ---- ETA（先用 ffmpeg 的 speed 粗算，主循环会再用瞬时速度精修）----
    if duration and duration > 0 and p.speed > 0:
        p.eta = max(0.0, (duration - p.out_time) / p.speed)
    return p


def _to_float(value, default: float = 0.0) -> float:
    """安全转浮点。"""
    try:
        s = str(value).strip()
        # ffmpeg 常用 "N/A" 表示未就绪。
        if s in ("", "N/A", "NA"):
            return default
        return float(s)
    except (TypeError, ValueError):
        return default


def _hms_to_seconds(text) -> float:
    """把 "HH:MM:SS.ffffff" 解析为秒。"""
    try:
        parts = str(text).split(":")
        if len(parts) != 3:
            return 0.0
        h, m, s = parts
        return float(h) * 3600 + float(m) * 60 + float(s)
    except (ValueError, TypeError):
        return 0.0
