# -*- coding: utf-8 -*-
# 任务模型与任务队列（本项目算法核心之三）。
# 职责：
#   1) 用 Task 描述“一个待转码文件 + 它的转码设置 + 运行状态 + 结果”。
#   2) 用 TaskQueue 负责调度：按并发上限取任务、驱动 Runner、把进度/状态变化回调给界面。
#   3) 维护状态机：等待 → 运行 → （完成 | 失败 | 取消 | 跳过），并保证状态迁移是单向且幂等的。
# 设计要点：
#   - 队列本身不依赖任何 GUI 框架，只通过“回调”与界面交互，因此 Web 端可原样复用。
#   - 所有回调都在工作线程触发，界面层用信号/事件队列切回主线程，避免跨线程直接操作控件。
#   - 支持暂停/继续/停止三种控制，语义清晰：暂停只影响“取新任务”，不影响已开始的任务。

import os  # 路径与文件操作
import copy  # 深拷贝设置对象
import time  # 计时
import queue  # 线程安全队列（存放待处理任务）
import threading  # 工作线程与锁
import logging  # 记录执行过程
import enum  # 定义状态枚举
from dataclasses import dataclass, field  # 承载任务数据
from typing import List, Optional, Callable, Dict  # 类型标注

from app.core import media_info, command_builder, runner as runner_mod, errors
from app.core.runner import Runner, RunResult, Progress

logger = logging.getLogger(__name__)


class TaskStatus(enum.Enum):
    """任务状态枚举。"""
    WAITING = "waiting"  # 等待执行
    RUNNING = "running"  # 正在执行
    DONE = "done"  # 成功完成
    FAILED = "failed"  # 失败
    CANCELED = "canceled"  # 被取消
    SKIPPED = "skipped"  # 被跳过（如去重、无权限）


# 状态到中文短名的映射（界面列表使用）。
STATUS_TEXT = {
    TaskStatus.WAITING: "等待中",
    TaskStatus.RUNNING: "转换中",
    TaskStatus.DONE: "完成",
    TaskStatus.FAILED: "失败",
    TaskStatus.CANCELED: "已取消",
    TaskStatus.SKIPPED: "已跳过",
}


@dataclass
class Task:
    """单个转码任务。"""
    # ---- 标识 ----
    tid: str = ""  # 任务唯一 ID（用于界面引用与 Web 端标识）
    # ---- 输入 ----
    settings: command_builder.TranscodeSettings = field(default_factory=command_builder.TranscodeSettings)
    info: Optional[media_info.MediaInfo] = None  # 源文件探测结果（懒加载）
    # ---- 运行态 ----
    status: TaskStatus = TaskStatus.WAITING  # 当前状态
    progress: float = 0.0  # 进度百分比 0~100
    speed: float = 0.0  # 当前速度倍率
    eta: float = -1.0  # 预计剩余秒数
    out_path: str = ""  # 计算出的输出路径
    # ---- 结果 ----
    err_code: str = ""  # 错误码（见 errors.CODE_SHORT）
    err_title: str = ""  # 错误短标题
    err_detail: str = ""  # 错误详情（原始 stderr 尾部）
    err_advice: str = ""  # 处理建议
    elapsed: float = 0.0  # 耗时（秒）
    in_size: int = 0  # 源文件大小
    out_size: int = 0  # 输出文件大小
    # ---- 内部 ----
    _runner: Optional[Runner] = None  # 正在执行时的执行器句柄（用于取消）

    # ---------- 便捷属性 ----------
    @property
    def source(self) -> str:
        """源文件路径。"""
        return self.settings.source

    @property
    def display_name(self) -> str:
        """列表显示的源文件名。"""
        return os.path.basename(self.settings.source) if self.settings.source else "(未知)"

    @property
    def status_text(self) -> str:
        """状态的短中文名。"""
        return STATUS_TEXT.get(self.status, "未知")

    @property
    def is_finished(self) -> bool:
        """是否已进入终态。"""
        return self.status in (TaskStatus.DONE, TaskStatus.FAILED,
                               TaskStatus.CANCELED, TaskStatus.SKIPPED)

    def to_dict(self) -> Dict:
        """转成可跨进程传输的字典（Web API 使用）。"""
        return {
            "tid": self.tid,
            "source": self.source,
            "name": self.display_name,
            "target": self.settings.target_format,
            "status": self.status.value,
            "status_text": self.status_text,
            "progress": round(self.progress, 2),
            "speed": round(self.speed, 3),
            "eta": self.eta,
            "out_path": self.out_path,
            "err_code": self.err_code,
            "err_title": self.err_title,
            "err_detail": self.err_detail,
            "err_advice": self.err_advice,
            "elapsed": round(self.elapsed, 2),
            "in_size": self.in_size,
            "out_size": self.out_size,
        }


# 回调类型别名，提升可读性。
ProgressCallback = Callable[[Task, Progress], None]
StateCallback = Callable[[Task], None]


class TaskQueue:
    """任务队列与调度器。

    线程模型：
        - 一个调度线程负责“从待办队列取任务、交给执行器、等待完成”。
        - 并发上限通过信号量控制（max_parallel=1 时退化为严格串行，机械硬盘上最稳）。
    控制接口：
        start() / pause() / resume() / stop_current() / cancel_all()
    """

    def __init__(self, max_parallel: int = 1):
        # 全部任务（含已完成），供界面展示与统计。
        self._tasks: List[Task] = []
        # 待执行任务的 ID 队列（线程安全）。
        self._pending = queue.Queue()
        # 并发上限。
        self._max_parallel = max(1, int(max_parallel or 1))
        # 调度线程。
        self._scheduler: Optional[threading.Thread] = None
        # 运行标志。
        self._running = False
        # 暂停标志。
        self._paused = False
        # 停止标志（取消全部）。
        self._stopped = False
        # 保护任务列表与状态的锁。
        self._lock = threading.RLock()
        # 当前正在执行的任务 ID 集合。
        self._active: set = set()
        # 回调注册表。
        self._on_progress: List[ProgressCallback] = []
        self._on_state: List[StateCallback] = []
        self._on_finished: List[Callable[[], None]] = []

    # ---------------- 回调注册 ----------------
    def on_progress(self, fn: ProgressCallback) -> None:
        """注册进度回调。"""
        self._on_progress.append(fn)

    def on_state(self, fn: StateCallback) -> None:
        """注册状态变化回调。"""
        self._on_state.append(fn)

    def on_all_finished(self, fn: Callable[[], None]) -> None:
        """注册“全部结束”回调。"""
        self._on_finished.append(fn)

    def _emit_progress(self, task: Task, prog: Progress) -> None:
        """安全触发进度回调。"""
        for fn in list(self._on_progress):
            try:
                fn(task, prog)
            except Exception as exc:
                logger.debug("进度回调异常：%s", exc)

    def _emit_state(self, task: Task) -> None:
        """安全触发状态回调。"""
        for fn in list(self._on_state):
            try:
                fn(task)
            except Exception as exc:
                logger.debug("状态回调异常：%s", exc)

    def _emit_finished(self) -> None:
        """安全触发“全部结束”回调。"""
        for fn in list(self._on_finished):
            try:
                fn()
            except Exception as exc:
                logger.debug("结束回调异常：%s", exc)

    # ---------------- 任务管理 ----------------
    def add(self, settings: command_builder.TranscodeSettings, dedup: bool = True) -> Optional[Task]:
        """添加一个任务。

        参数:
            settings: 转码设置（会被深拷贝一份，避免外部修改影响队列内任务）。
            dedup: 是否对“同一源文件 + 同一目标格式”去重。
        返回:
            新建的 Task；若因去重被跳过则返回 None。
        """
        with self._lock:
            # 去重判断：源路径与目标格式都相同视为重复。
            if dedup:
                src_abs = os.path.abspath(settings.source) if settings.source else ""
                for t in self._tasks:
                    if (os.path.abspath(t.settings.source) == src_abs
                            and t.settings.target_format == settings.target_format
                            and not t.is_finished):
                        logger.info("跳过重复任务：%s → %s", settings.source, settings.target_format)
                        return None
            # 生成任务 ID：时间戳 + 序号，保证单调且可读。
            tid = f"t{int(time.time() * 1000)}_{len(self._tasks) + 1}"
            # 深拷贝设置，隔离外部对象。
            st_copy = copy.deepcopy(settings)
            # 创建任务。
            task = Task(tid=tid, settings=st_copy)
            # 记录源文件大小（若可读）。
            try:
                if st_copy.source and os.path.isfile(st_copy.source):
                    task.in_size = os.path.getsize(st_copy.source)
            except OSError:
                task.in_size = 0
            # 加入列表与待办队列。
            self._tasks.append(task)
            self._pending.put(tid)
            # 通知界面新增。
            self._emit_state(task)
            return task

    def tasks(self) -> List[Task]:
        """返回当前全部任务的浅拷贝列表。"""
        with self._lock:
            return list(self._tasks)

    def get(self, tid: str) -> Optional[Task]:
        """按 ID 查找任务。"""
        with self._lock:
            for t in self._tasks:
                if t.tid == tid:
                    return t
            return None

    def remove(self, tid: str) -> bool:
        """移除一个尚未执行的任务（正在运行的不可移除，需先停止）。"""
        with self._lock:
            task = self.get(tid)
            if not task:
                return False
            # 正在运行的不能移除。
            if task.status == TaskStatus.RUNNING:
                return False
            self._tasks = [t for t in self._tasks if t.tid != tid]
            return True

    def clear(self, include_running: bool = False) -> int:
        """清空任务列表。

        参数:
            include_running: 是否连正在运行的任务一起清理（调用方应先 stop）。
        返回:
            被清理的任务数。
        """
        with self._lock:
            if include_running:
                # 先取消全部活动任务。
                self.cancel_all()
                count = len(self._tasks)
                self._tasks = []
            else:
                # 保留等待/运行中的任务，清掉其余。
                keep = [t for t in self._tasks if t.status in (TaskStatus.WAITING, TaskStatus.RUNNING)]
                count = len(self._tasks) - len(keep)
                self._tasks = keep
            # 重建待办队列（剔除已移除项）。
            self._rebuild_pending()
            return count

    def _rebuild_pending(self) -> None:
        """根据当前任务列表重建待办队列，保证队列里只剩“仍存在且等待中”的 ID。"""
        # 新建空队列。
        new_q = queue.Queue()
        # 按任务列表顺序压入仍处于等待状态的任务。
        for t in self._tasks:
            if t.status == TaskStatus.WAITING:
                new_q.put(t.tid)
        # 原子替换。
        self._pending = new_q

    def pending_count(self) -> int:
        """返回待执行（未进入终态且未运行）的任务数。"""
        with self._lock:
            return len([t for t in self._tasks if t.status == TaskStatus.WAITING])

    def finished_count(self) -> int:
        """返回已进入终态的任务数。"""
        with self._lock:
            return len([t for t in self._tasks if t.is_finished])

    def stats(self) -> Dict:
        """统计各状态数量，供界面汇总展示。"""
        with self._lock:
            total = len(self._tasks)
            ok = len([t for t in self._tasks if t.status == TaskStatus.DONE])
            fail = len([t for t in self._tasks if t.status == TaskStatus.FAILED])
            skip = len([t for t in self._tasks if t.status == TaskStatus.SKIPPED])
            canceled = len([t for t in self._tasks if t.status == TaskStatus.CANCELED])
            waiting = len([t for t in self._tasks if t.status == TaskStatus.WAITING])
        return {"total": total, "ok": ok, "fail": fail, "skip": skip,
                "canceled": canceled, "waiting": waiting}

    # ---------------- 调度控制 ----------------
    def set_max_parallel(self, n: int) -> None:
        """设置并发上限（下次启动调度时生效）。"""
        with self._lock:
            self._max_parallel = max(1, int(n or 1))

    def start(self) -> None:
        """启动调度线程（若已在运行则忽略）。"""
        with self._lock:
            # 已运行则不重复启动。
            if self._running and self._scheduler and self._scheduler.is_alive():
                return
            # 重置停止/暂停标志。
            self._stopped = False
            self._paused = False
            self._running = True
            # 创建并启动调度线程（守护线程，随主程序退出）。
            self._scheduler = threading.Thread(target=self._scheduler_loop, name="task-scheduler", daemon=True)
            self._scheduler.start()

    def pause(self) -> None:
        """暂停（只阻止取新任务，不影响正在运行的任务）。"""
        with self._lock:
            self._paused = True

    def resume(self) -> None:
        """恢复调度。"""
        with self._lock:
            self._paused = False

    def stop_current(self) -> None:
        """停止当前正在运行的任务（会把其状态置为 CANCELED）。"""
        with self._lock:
            active = [t for t in self._tasks if t.status == TaskStatus.RUNNING]
        for t in active:
            if t._runner:
                # 触发执行器取消。
                t._runner.cancel()

    def stop_all(self) -> None:
        """停止调度并取消所有活动任务。"""
        with self._lock:
            self._stopped = True
        self.stop_current()

    def cancel_all(self) -> None:
        """取消所有任务（等待中的标记为已取消，运行中的触发取消）。"""
        with self._lock:
            for t in self._tasks:
                if t.status == TaskStatus.WAITING:
                    t.status = TaskStatus.CANCELED
                    self._emit_state(t)
                elif t.status == TaskStatus.RUNNING and t._runner:
                    t._runner.cancel()

    def is_running(self) -> bool:
        """队列是否处于调度状态。"""
        with self._lock:
            return self._running and not self._stopped

    # ---------------- 调度主循环 ----------------
    def _scheduler_loop(self) -> None:
        """调度线程主体：按并发上限取任务并执行。"""
        logger.info("任务调度线程启动，并发上限 %d", self._max_parallel)
        # 工作线程列表，用于等待全部结束。
        workers: List[threading.Thread] = []
        # 循环直到被要求停止且没有活动任务。
        while True:
            # 检查停止条件。
            with self._lock:
                stopped = self._stopped
                paused = self._paused
            # 若已停止且无活动任务，退出循环。
            if stopped and not self._active:
                break
            # 暂停状态下不取新任务，短暂休眠。
            if paused:
                time.sleep(0.1)
                continue
            # 若活动数已达上限，等待空闲槽位。
            with self._lock:
                active_count = len(self._active)
            if active_count >= self._max_parallel:
                time.sleep(0.05)
                continue
            # 尝试取一个新任务。
            try:
                tid = self._pending.get_nowait()
            except queue.Empty:
                # 队列空：若还有活动任务则等待它们完成；否则退出。
                if not self._active:
                    break
                time.sleep(0.05)
                continue
            # 取到任务：检查其是否仍为等待状态（可能已被取消/移除）。
            task = self.get(tid)
            if not task or task.status != TaskStatus.WAITING:
                continue
            # 标记为运行并登记活动集合。
            with self._lock:
                task.status = TaskStatus.RUNNING
                self._active.add(tid)
            # 通知状态变化（进入 RUNNING）。
            self._emit_state(task)
            # 启动工作线程执行该任务。
            th = threading.Thread(target=self._run_one, args=(task,), name=f"worker-{tid}", daemon=True)
            th.start()
            workers.append(th)
            # 清理已结束的线程引用，避免列表无限增长。
            workers = [w for w in workers if w.is_alive()]

        # 循环退出前等待所有工作线程结束。
        for w in workers:
            w.join(timeout=5.0)
        # 标记不再运行。
        with self._lock:
            self._running = False
        logger.info("任务调度线程退出")
        # 触发“全部结束”回调。
        self._emit_finished()

    def _run_one(self, task: Task) -> None:
        """执行单个任务（在工作线程中运行）。"""
        # 记录开始时间。
        start_ts = time.time()
        try:
            # ---- 1. 探测源文件（懒加载）----
            if task.info is None:
                task.info = media_info.probe(task.settings.source)
            info = task.info
            # 源文件不存在：直接判定为失败，且不用调用 ffmpeg。
            if not info.exists:
                self._fail(task, "FILE_MISSING", "文件不存在或无法读取",
                           "请确认文件仍然存在且可读。", "")
                return
            # ---- 2. 计算输出路径 ----
            out_path = command_builder.compute_output_path(
                task.settings,
                overwrite=bool(task.settings.overwrite),
            )
            # 确保输出目录存在（用户可能指定了一个尚未创建的目录）。
            out_dir = os.path.dirname(out_path)
            if out_dir:
                try:
                    os.makedirs(out_dir, exist_ok=True)
                except OSError as exc:
                    self._fail(task, "PERMISSION", "无法创建输出目录",
                               f"请检查目录权限或更换输出位置：{exc}", "")
                    return
            task.out_path = out_path
            # ---- 3. 构建命令 ----
            args = command_builder.build_command(task.settings, info, out_path=out_path)
            logger.debug("任务 %s 命令：%s", task.tid,
                         command_builder.describe_command(args))
            # ---- 4. 执行 ----
            rn = Runner()
            task._runner = rn

            # 进度回调闭包：把 Runner.Progress 转成任务进度并上报。
            def _on_prog(p: Progress):
                # 更新任务上的进度字段。
                task.progress = 100.0 if p.done else p.percent
                task.speed = p.speed
                task.eta = p.eta
                # 向界面广播。
                self._emit_progress(task, p)

            # 源时长（用于百分比换算）。
            duration = float(info.duration or 0.0)
            # 执行（可能耗时很久，期间可被 cancel）。
            result: RunResult = rn.run(args, duration=duration, on_progress=_on_prog)
            task._runner = None
            # ---- 5. 处理结果 ----
            task.elapsed = time.time() - start_ts
            if result.canceled:
                # 用户取消：不影响其它任务，标记为已取消。
                task.status = TaskStatus.CANCELED
                task.err_title = "用户取消"
                self._emit_state(task)
                return
            if result.ok:
                # 成功：记录体积并置为完成。
                task.status = TaskStatus.DONE
                task.progress = 100.0
                task.out_size = result.out_size
                self._emit_state(task)
                return
            # 失败：需要区分“ffmpeg 报错”与“返回码 0 但文件为空”。
            if result.returncode == 0 and result.out_size == 0:
                # 返回码正常但没有产出（例如源文件无有效流）。
                self._fail(task, "NO_STREAM", "输出为空",
                           "源文件可能不含可转换的音视频流，或目标格式与该内容不兼容。", result.stderr)
                return
            # 走错误分类。
            diag = errors.classify(result.stderr, result.returncode)
            # ---- 6. 自动补救：对可恢复错误带补救参数重试一次 ----
            if errors.is_recoverable(diag.code) and diag.retry_args:
                logger.info("任务 %s 命中可恢复错误（%s），尝试自动补救重试", task.tid, diag.code)
                # 两类可恢复错误的补救方式不同，必须分别处理：
                if diag.code == "HW_ACCEL":
                    # 硬件编码失败：不是“追加参数”，而是必须“整套换成软件编码器”重新构建命令。
                    # 原因见 command_builder.software_fallback 的说明。
                    fallback_st = command_builder.software_fallback(task.settings)
                    retry_args = command_builder.build_command(fallback_st, info, out_path=out_path)
                else:
                    # 其它可恢复错误（如奇数分辨率）：把补救参数插入到输出路径之前即可。
                    retry_args = list(args[:-1]) + list(diag.retry_args) + [args[-1]]
                rn2 = Runner()
                task._runner = rn2
                result2 = rn2.run(retry_args, duration=duration, on_progress=_on_prog)
                task._runner = None
                task.elapsed = time.time() - start_ts
                if result2.ok:
                    # 补救成功。
                    task.status = TaskStatus.DONE
                    task.progress = 100.0
                    task.out_size = result2.out_size
                    self._emit_state(task)
                    return
                # 补救仍失败：用第二次的错误信息覆盖诊断。
                diag = errors.classify(result2.stderr, result2.returncode)
                result = result2
            # 记录失败详情。
            self._fail(task, diag.code, diag.title, diag.advice, diag.detail)
        except Exception as exc:
            # 任何未预期异常都不能让工作线程崩溃（否则队列会停滞）。
            logger.exception("任务 %s 执行时发生未预期异常", task.tid)
            self._fail(task, "UNKNOWN", "内部异常", f"请查看日志定位问题：{exc}", "")
        finally:
            # 无论如何都要把任务从活动集合移除并检查是否全部结束。
            with self._lock:
                self._active.discard(task.tid)
                task._runner = None

    def _fail(self, task: Task, code: str, title: str, advice: str, detail: str) -> None:
        """统一设置失败状态并广播。"""
        task.status = TaskStatus.FAILED
        task.err_code = code
        task.err_title = title or errors.CODE_SHORT.get(code, "未知错误")
        task.err_advice = advice
        task.err_detail = detail
        # 失败时把进度冻结在当前值，便于用户判断失败发生在哪个阶段。
        logger.warning("任务 %s 失败：%s（%s）", task.tid, task.err_title, code)
        self._emit_state(task)
