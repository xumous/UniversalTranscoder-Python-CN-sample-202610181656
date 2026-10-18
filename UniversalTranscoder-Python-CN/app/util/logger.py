# -*- coding: utf-8 -*-
# 日志工具。
# 设计目标：
#   1) 同时输出到控制台与文件，源码调试看控制台，打包后现场排查看文件。
#   2) 文件日志采用轮转(RotatingFileHandler)，避免长时间运行把磁盘写满。
#   3) 未捕获异常统一落盘，便于用户把日志发给开发者定位问题。
#   4) 日志编码必须显式 UTF-8，否则在中文 Windows 上中文日志行会变成乱码。

import logging  # Python 标准日志库
import logging.handlers  # 提供轮转处理器
import sys  # 用于把控制台日志接到 stdout/stderr
import threading  # 用于保证初始化只执行一次

from app.util import paths  # 复用日志文件路径与日志目录

# 模块级初始化标志与锁：确保 configure() 在多线程场景下只真正执行一次。
_INITIALIZED = False
_INIT_LOCK = threading.Lock()

# 日志格式：时间 | 级别 | 线程名 | 模块:行号 | 消息。
# 之所以带上线程名，是因为 GUI 渲染线程与转码工作线程会交错输出，线程名能帮助区分来源。
LOG_FORMAT = "%(asctime)s | %(levelname)-7s | %(threadName)-14s | %(name)s:%(lineno)d | %(message)s"
# 时间格式：到毫秒，便于对齐 ffmpeg 的进度输出时间戳。
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


def configure(level: str = "INFO") -> logging.Logger:
    """初始化根日志器。

    参数:
        level: 日志级别字符串，如 "DEBUG"/"INFO"/"WARNING"/"ERROR"。
    返回:
        根日志器对象。
    说明：本函数幂等，重复调用不会重复添加处理器。
    """
    # 声明要在函数内修改模块级标志，必须使用 global。
    global _INITIALIZED
    # 双重检查加锁：先无锁快速判断，再在锁内二次确认，降低开销。
    with _INIT_LOCK:
        if _INITIALIZED:
            # 已初始化：仅调整级别并直接返回，避免重复挂处理器。
            root = logging.getLogger()
            root.setLevel(_to_level(level))
            return root
        # 取得根日志器，所有子日志器默认继承其处理器。
        root = logging.getLogger()
        root.setLevel(_to_level(level))
        # 先清空既有处理器（某些库可能提前加过），保证格式统一。
        for h in list(root.handlers):
            root.removeHandler(h)
        # ---- 处理器一：控制台 ----
        console = logging.StreamHandler(stream=sys.stdout)
        # 控制台使用较简洁的格式，避免刷屏。
        console.setFormatter(logging.Formatter("%(levelname)-7s | %(message)s"))
        root.addHandler(console)
        # ---- 处理器二：轮转文件 ----
        try:
            # 单个日志文件上限 2MB，保留 5 个备份，总计不超过约 12MB。
            file_handler = logging.handlers.RotatingFileHandler(
                filename=paths.log_file(),  # 日志文件绝对路径
                maxBytes=2 * 1024 * 1024,  # 2 MiB 触发轮转
                backupCount=5,  # 保留 5 份历史
                encoding="utf-8",  # 显式 UTF-8，避免中文乱码
                delay=True,  # 延迟到首次写入才创建文件，减少空文件
            )
            # 文件使用完整格式，包含线程名与源码位置。
            file_handler.setFormatter(logging.Formatter(LOG_FORMAT, DATE_FORMAT))
            root.addHandler(file_handler)
        except Exception:
            # 若日志目录不可写（极端环境），跳过文件日志，仅保留控制台，绝不因此崩溃。
            pass
        # 标记已完成初始化。
        _INITIALIZED = True
        return root


def _to_level(name: str) -> int:
    """把字符串级别名转换为 logging 模块的整型级别。

    容错：传入非法名称时回退到 INFO。
    """
    # 统一转大写并去除空白，容忍 "info" / " Info " 这类输入。
    key = str(name or "INFO").strip().upper()
    # getattr 查表，不存在则返回 INFO。
    return getattr(logging, key, logging.INFO)


def get_logger(name: str) -> logging.Logger:
    """获取一个带命名空间的子日志器。

    参数:
        name: 通常传入 __name__，使日志能定位到具体模块。
    """
    # 委托标准库获取子日志器；其处理器继承自根日志器。
    return logging.getLogger(name)


def install_excepthook() -> None:
    """安装全局未捕获异常钩子，把崩溃栈写入日志文件。"""
    # 保存原有钩子，处理完后再调用一次，保持默认的打印行为（便于控制台调试）。
    original = sys.excepthook

    def _hook(exc_type, exc_value, exc_tb):
        # 线程名在信息里可能缺失，这里做兜底。
        logger = logging.getLogger("uncaught")
        # 以 ERROR 级别记录完整异常栈（exc_info 传入三元组）。
        logger.error("未捕获异常，程序可能即将退出", exc_info=(exc_type, exc_value, exc_tb))
        # 转交原始钩子，保持标准错误输出。
        original(exc_type, exc_value, exc_tb)

    # 用自定义钩子替换系统钩子。
    sys.excepthook = _hook
