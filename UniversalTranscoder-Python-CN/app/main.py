# -*- coding: utf-8 -*-
# 程序总入口。
# 提供两个启动函数：run_gui()（桌面端）与 run_web()（Web 端）。
# 之所以把入口集中在一个模块：打包时只需一个入口脚本，避免多入口导致 PyInstaller 分析混乱。

import os  # 路径处理
import sys  # 命令行参数与退出码
import argparse  # 命令行解析
import logging  # 日志

# 保证以“脚本方式”运行时也能正确导入 app 包（例如直接执行 run_gui.py）。
if __package__ in (None, ""):
    # 把当前文件所在目录的上一级（项目根）加入模块搜索路径。
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import __app_name__, __version__, __author__  # 应用元信息
from app.util import logger as app_logger  # 日志初始化
from app.util import paths  # 资源路径
from app.util.config import config  # 配置


def _ensure_std_streams() -> None:
    """保证 sys.stdout / sys.stderr 不为 None。

    为什么必须做这件事（打包后才会暴露的经典故障）：
        PyInstaller 以 console=False（无控制台）方式打包出的程序，运行时
        sys.stdout 与 sys.stderr 都会被置为 None。由此会引发两类崩溃：
          1) 任何 print() 调用都会抛 AttributeError；
          2) uvicorn 初始化日志配置时会执行 sys.stdout.isatty()，直接崩在
             "AttributeError: 'NoneType' object has no attribute 'isatty'"。
        解决办法：把缺失的标准流替换为一个指向系统空设备（nul）的可写流。
        该流具备 write / flush，且 isatty() 返回 False，足以满足上述两种调用。
    注意：本函数必须在任何日志初始化与任何 print 之前调用。
    """
    # 只有在确实缺失时才处理（源码运行时正常存在，不应被替换）。
    if sys.stdout is not None and sys.stderr is not None:
        return
    # 打开系统空设备作为替代流（Windows 下为 nul，其它平台为 /dev/null）。
    try:
        null_stream = open(os.devnull, "w", encoding="utf-8")
    except OSError:
        # 极端情况下连空设备都打不开：退化为一个自定义的“黑洞”对象。
        class _Null:
            """极简黑洞流：吞掉所有写入，isatty 恒为假。"""

            def write(self, _data):  # 吞掉写入内容
                return len(_data) if _data else 0

            def flush(self):  # 空实现
                pass

            def isatty(self):  # 明确告知“不是终端”，避免依赖终端的彩色输出逻辑
                return False

            def close(self):  # 空实现
                pass

        null_stream = _Null()
    # 逐个补上缺失的流。
    if sys.stdout is None:
        sys.stdout = null_stream
    if sys.stderr is None:
        sys.stderr = null_stream


def _prepare() -> None:
    """所有启动路径共用的准备工作。"""
    # 第一步必须是修复标准流：否则后面的日志初始化与 print 都可能直接崩溃。
    _ensure_std_streams()
    # 载入配置。
    config.load()
    # 按配置初始化日志级别。
    app_logger.configure(str(config.get("log_level", "INFO")))
    # 安装未捕获异常钩子，保证崩溃信息落盘。
    app_logger.install_excepthook()
    # 记录一条启动日志。
    logging.getLogger(__name__).info("%s v%s 启动（作者：%s）", __app_name__, __version__, __author__)


def _app_icon():
    """尝试加载应用图标；找不到时返回 None。

    图标在源码运行时位于 assets/icon.ico，打包后位于 _MEIPASS/assets/icon.ico。
    找不到图标不是致命问题，程序照常运行。
    """
    # 可能的图标位置。
    candidates = [
        paths.resource_path("assets", "icon.ico"),
        paths.resource_path("resources", "icon.ico"),
    ]
    for c in candidates:
        if os.path.isfile(c):
            try:
                # 延迟导入，避免 Web 端也强制依赖 PyQt。
                from PyQt6.QtGui import QIcon
                return QIcon(c)
            except Exception:
                return None
    return None


def run_gui() -> int:
    """启动桌面端（PyQt6）。

    返回:
        进程退出码。
    """
    _prepare()
    # 延迟导入 PyQt，保证 Web 端启动时不加载 Qt（节省内存与启动时间）。
    from PyQt6.QtWidgets import QApplication
    from app.gui.style import apply_theme
    from app.gui.main_window import MainWindow

    # 创建应用对象。
    app = QApplication(sys.argv)
    # 应用名称与组织名（影响部分系统集成行为）。
    app.setApplicationName(__app_name__)
    app.setApplicationVersion(__version__)
    app.setOrganizationName(__author__)
    # 应用图标（窗口与任务栏）。
    icon = _app_icon()
    if icon is not None:
        app.setWindowIcon(icon)
    # 应用主题。
    apply_theme(app, str(config.get("theme", "dark")))
    # 创建并显示主窗口。
    win = MainWindow()
    if icon is not None:
        win.setWindowIcon(icon)
    win.show()
    # 进入事件循环。
    return app.exec()


def run_web(host: str = "", port: int = 0, open_browser: bool = True) -> int:
    """启动 Web 端（FastAPI + uvicorn）。

    参数:
        host: 监听地址；空则读取配置。
        port: 监听端口；0 则读取配置。
        open_browser: 是否自动打开浏览器。
    返回:
        进程退出码。
    """
    _prepare()
    # 导入 Web 服务模块。
    from app.web import server as web_server
    # 启动（内部会阻塞直到服务停止）。
    web_server.run(host=host, port=port, open_browser=open_browser)
    return 0


def main(argv=None) -> int:
    """命令行入口：解析参数并分派到 GUI 或 Web。

    参数:
        argv: 参数列表；默认取 sys.argv[1:]。
    返回:
        退出码。
    """
    parser = argparse.ArgumentParser(prog="万能转码器", description="离线万能格式转换工具")
    # 模式选择：默认桌面端。
    parser.add_argument("--web", action="store_true", help="启动 Web 端而不是桌面端")
    # Web 监听地址与端口（可覆盖配置）。
    parser.add_argument("--host", default="", help="Web 端监听地址，如 127.0.0.1")
    parser.add_argument("--port", type=int, default=0, help="Web 端监听端口，如 8765")
    # 是否自动打开浏览器。
    parser.add_argument("--no-browser", action="store_true", help="Web 端启动后不自动打开浏览器")
    # 直接指定要转换的文件（可选，作为桌面端的初始列表内容）。
    parser.add_argument("files", nargs="*", help="可选的初始待转文件列表")
    args = parser.parse_args(argv)

    # 分派。
    if args.web:
        return run_web(host=args.host, port=args.port, open_browser=not args.no_browser)
    # 桌面端：暂不处理位置参数（保持简单可靠）。
    return run_gui()


if __name__ == "__main__":
    # 以模块方式运行时（python -m app.main）走标准入口。
    sys.exit(main())
