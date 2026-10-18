# -*- coding: utf-8 -*-
# 桌面端界面冒烟测试脚本（开发者自检用）。
# 目的：在无显示器的环境下（offscreen 平台插件）真实构建主窗口并跑通一次完整转码，
#       以此验证“界面构建 → 参数收集 → 任务入队 → 工作线程执行 → 跨线程信号回传 → 表格刷新”
#       这一整条链路没有异常。这是桌面端最关键、最容易出问题（跨线程操作控件）的部分。
# 运行：.venv\Scripts\python.exe tools\smoke_gui.py

import os
import sys

# 必须在导入 PyQt 之前设置平台插件，否则会尝试连接真实显示器而失败。
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

# 项目根目录入路径。
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROJECT_ROOT)

# 样例目录。
SAMPLES = os.path.abspath(os.path.join(PROJECT_ROOT, "..", "Samples"))

from PyQt6.QtWidgets import QApplication, QMessageBox
from PyQt6.QtCore import QTimer

from app.util import logger as app_logger
from app.gui.style import apply_theme
from app.gui.main_window import MainWindow
from app.core.tasks import TaskStatus


def _silence_modals() -> None:
    """把 QMessageBox 的四个静态方法替换为“立即返回”的空实现。

    为什么必须这样做：
        测试运行在 offscreen 平台插件下，没有任何窗口可供点击。
        此时任何模态对话框（QMessageBox）都会进入自己的事件循环并永久等待用户点击，
        导致测试进程挂死。替换为空实现后，对话框不再阻塞，测试可以连续跑完。
        注意：这只影响测试进程，不影响正式程序的行为。
    """
    # 返回值统一给一个“确定/是”，保证调用方的分支判断不会异常。
    ok = QMessageBox.StandardButton.Ok
    yes = QMessageBox.StandardButton.Yes
    # 逐个替换（用 staticmethod 包装，避免被当成实例方法而多传 self）。
    QMessageBox.information = staticmethod(lambda *a, **k: ok)
    QMessageBox.warning = staticmethod(lambda *a, **k: ok)
    QMessageBox.critical = staticmethod(lambda *a, **k: ok)
    QMessageBox.question = staticmethod(lambda *a, **k: yes)


def main() -> int:
    """构建窗口 → 入队 → 等待完成 → 校验产物。"""
    # 关闭一切模态弹窗（否则在无显示器环境下会永久阻塞）。
    _silence_modals()
    # 初始化日志。
    app_logger.configure("INFO")

    # 创建 Qt 应用（offscreen）。
    app = QApplication(sys.argv)
    apply_theme(app, "dark")

    # 构建主窗口（这一步会真实创建全部控件，任何语法/接口错误都会在此暴露）。
    win = MainWindow()
    win.show()
    print("[1] 主窗口构建成功，标签页数量 =", win.tabs.count())

    # 断言：三个媒体面板与文档/高级/设置/关于页都存在。
    assert win.tabs.count() == 7, "标签页数量不为 7"

    # 检查内核指示。
    print("[2] 内核状态：", "就绪" if win.kernel_label.text().startswith("内核就绪") else win.kernel_label.text())

    # 添加一个真实样例文件。
    src = os.path.join(SAMPLES, "test_video.mp4")
    if not os.path.isfile(src):
        print("[失败] 缺少样例文件：", src)
        return 1
    win.add_paths([src])
    print("[3] 待转文件数量 =", len(win.selected_files()))
    assert len(win.selected_files()) == 1, "文件未加入列表"

    # 去重逻辑验证：再次添加同一文件应被跳过。
    win.add_paths([src])
    print("[4] 重复添加后数量 =", len(win.selected_files()))
    assert len(win.selected_files()) == 1, "重复文件未被去重"

    # 配置视频面板：转成 MKV（H.264 + AAC），缩小到 320 宽，低质量以加快测试。
    win.tabs.setCurrentIndex(0)  # 切到“视频”页
    p = win.panel_video
    # 目标格式切到 mkv。
    idx = p.cb_format.findText("mkv")
    if idx >= 0:
        p.cb_format.setCurrentIndex(idx)
    # 缩小分辨率。
    p.sp_w.setValue(320)
    p.sp_h.setValue(0)
    # 低质量档。
    qi = p.cb_quality.findData("low")
    if qi >= 0:
        p.cb_quality.setCurrentIndex(qi)
    # 输出目录。
    out_dir = os.path.join(SAMPLES, "gui_out")
    p.ed_outdir.setText(out_dir)
    print("[5] 面板参数采集：", win.current_settings().target_format,
          "宽=", win.current_settings().width)

    # 开始队列。
    win.start_queue()
    print("[6] 队列已启动，表格行数 =", win.table.rowCount())
    assert win.table.rowCount() == 1, "队列表格未插入行"

    # 用事件循环等待完成（最多 120 秒），期间真实处理跨线程信号。
    result = {"ok": False, "text": ""}

    def _poll():
        """轮询任务状态。"""
        tasks = win.queue.tasks()
        if not tasks:
            result["text"] = "无任务"
            app.quit()
            return
        t = tasks[0]
        # 打印一次进度（验证信号确实在刷新表格）。
        bar = win.table.cellWidget(0, 4)
        status_cell = win.table.item(0, 3).text() if win.table.item(0, 3) else ""
        print(f"    状态={t.status_text:<6} 任务进度={t.progress:6.1f}% "
              f"表格进度条={bar.value() if bar else -1:3d} 表格状态列={status_cell}")
        if t.is_finished:
            result["ok"] = (t.status == TaskStatus.DONE and t.out_size > 0
                            and os.path.isfile(t.out_path))
            # 失败时把错误标题与原始 stderr 尾部一并打印，便于定位真实原因。
            result["text"] = (f"最终状态={t.status_text} 输出={t.out_path} 体积={t.out_size}\n"
                              f"错误标题={t.err_title}\n错误建议={t.err_advice}\n"
                              f"原始错误=\n{t.err_detail}")
            app.quit()

    # 每 400 毫秒轮询一次。
    timer = QTimer()
    timer.timeout.connect(_poll)
    timer.start(400)
    # 超时保护：120 秒后强制退出。
    QTimer.singleShot(120000, app.quit)

    # 进入事件循环（阻塞直到 app.quit）。
    app.exec()

    print("[7] " + result["text"])
    if not result["ok"]:
        print("[失败] 桌面端转码未成功。")
        return 1

    # 校验总进度条已到 100。
    print("[8] 总进度条 =", win.prog_total.value())
    # 校验汇总标签。
    print("[9] 汇总 =", win.lbl_summary.text())

    print("\n桌面端冒烟测试：全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
