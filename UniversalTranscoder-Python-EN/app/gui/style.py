# -*- coding: utf-8 -*-
# 界面样式（QSS）。
# 设计原则：
#   1) 深色、扁平、克制的科技风：用色阶建立层次，而不是靠渐变、发光、阴影堆砌。
#   2) 所有颜色集中为常量，方便统一调整，也方便将来做浅色主题时整体替换。
#   3) 不使用任何图片资源做皮肤（避免打包时丢资源），全部用纯色与圆角实现。
#   4) 控件尺寸与留白以“长时间使用不累眼”为准：正文 13px，行高宽松，选中态对比明显但不过亮。

# ---------- 调色板（深色主题） ----------
C_BG = "#0f1624"  # 窗口底色（深蓝灰）
C_PANEL = "#16202f"  # 面板底色
C_PANEL_2 = "#1c2838"  # 次级面板/输入框底色
C_BORDER = "#26344a"  # 边框色
C_TEXT = "#dbe4f0"  # 主文字
C_TEXT_DIM = "#8fa3bd"  # 次要文字
C_ACCENT = "#3aa0ff"  # 强调色（蓝）
C_ACCENT_DIM = "#2a7fd0"  # 强调色悬停
C_OK = "#38c47a"  # 成功（绿）
C_WARN = "#e2b53c"  # 警告（黄）
C_ERR = "#e05a5a"  # 错误（红）
C_DISABLED_BG = "#141c28"  # 禁用控件底色
C_DISABLED_TEXT = "#55627a"  # 禁用控件文字


def build_qss(theme: str = "dark") -> str:
    """生成全局样式表。

    参数:
        theme: "dark" 或 "light"；本文件以深色为主，浅色仅做基础反色（保证不至于不可用）。
    返回:
        QSS 字符串。
    """
    # 浅色主题的调色板（保持同一套结构，便于替换）。
    if theme == "light":
        bg, panel, panel2 = "#f2f5f9", "#ffffff", "#eef2f7"
        border, text, dim = "#d2dae5", "#1d2733", "#5a6b80"
        disabled_bg, disabled_text = "#f0f2f5", "#a8b2c0"
    else:
        bg, panel, panel2 = C_BG, C_PANEL, C_PANEL_2
        border, text, dim = C_BORDER, C_TEXT, C_TEXT_DIM
        disabled_bg, disabled_text = C_DISABLED_BG, C_DISABLED_TEXT

    # 使用 f-string 把颜色注入模板，避免手写大量重复色值。
    return f"""
/* ---------- 全局 ---------- */
QWidget {{
    background-color: {bg};
    color: {text};
    font-family: "Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", sans-serif;
    font-size: 13px;
}}
QMainWindow, QDialog {{ background-color: {bg}; }}

/* ---------- 分组框 ---------- */
QGroupBox {{
    background-color: {panel};
    border: 1px solid {border};
    border-radius: 6px;
    margin-top: 14px;
    padding: 10px 10px 10px 10px;
}}
QGroupBox::title {{
    subcontrol-origin: margin;
    subcontrol-position: top left;
    left: 10px;
    padding: 0 6px;
    color: {dim};
    font-size: 12px;
}}

/* ---------- 标签 ---------- */
QLabel {{ background: transparent; }}
QLabel[role="dim"] {{ color: {dim}; font-size: 12px; }}
QLabel[role="hint"] {{ color: {dim}; font-size: 11px; }}
QLabel[role="title"] {{ color: {text}; font-size: 15px; font-weight: bold; }}

/* ---------- 按钮 ---------- */
QPushButton {{
    background-color: {panel2};
    border: 1px solid {border};
    border-radius: 5px;
    padding: 6px 14px;
    color: {text};
    min-height: 18px;
}}
QPushButton:hover {{ background-color: #223147; border-color: {C_ACCENT_DIM}; }}
QPushButton:pressed {{ background-color: #1a2637; }}
QPushButton:disabled {{ background-color: {disabled_bg}; color: {disabled_text}; border-color: {border}; }}
/* 主按钮：唯一的实心蓝，靠颜色区分主次，而不是靠尺寸或阴影 */
QPushButton[role="primary"] {{
    background-color: {C_ACCENT};
    border: 1px solid {C_ACCENT};
    color: #06101c;
    font-weight: bold;
}}
QPushButton[role="primary"]:hover {{ background-color: #57b0ff; }}
QPushButton[role="primary"]:disabled {{ background-color: {disabled_bg}; color: {disabled_text}; border-color: {border}; }}
/* 危险按钮：仅用于停止/清空这类破坏性操作 */
QPushButton[role="danger"] {{ border-color: #5a3038; color: #ff9d9d; }}
QPushButton[role="danger"]:hover {{ background-color: #3a2028; }}

/* ---------- 输入类控件 ---------- */
QLineEdit, QSpinBox, QDoubleSpinBox, QComboBox, QPlainTextEdit, QTextEdit {{
    background-color: {panel2};
    border: 1px solid {border};
    border-radius: 4px;
    padding: 4px 8px;
    color: {text};
    selection-background-color: {C_ACCENT_DIM};
    selection-color: #ffffff;
}}
QLineEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus, QComboBox:focus,
QPlainTextEdit:focus, QTextEdit:focus {{ border-color: {C_ACCENT}; }}
QLineEdit:disabled, QComboBox:disabled, QSpinBox:disabled, QDoubleSpinBox:disabled {{
    background-color: {disabled_bg}; color: {disabled_text};
}}
QComboBox::drop-down {{ border: none; width: 20px; }}
QComboBox::down-arrow {{
    /* 用 CSS 边框拼出一个下三角，避免依赖图片资源 */
    width: 0; height: 0;
    border-left: 4px solid transparent;
    border-right: 4px solid transparent;
    border-top: 5px solid {dim};
    margin-right: 6px;
}}
QComboBox QAbstractItemView {{
    background-color: {panel};
    border: 1px solid {border};
    selection-background-color: {C_ACCENT_DIM};
    selection-color: #ffffff;
    outline: none;
}}

/* ---------- 勾选框与单选框 ---------- */
QCheckBox, QRadioButton {{ spacing: 6px; background: transparent; }}
QCheckBox::indicator, QRadioButton::indicator {{
    width: 14px; height: 14px;
    border: 1px solid {border};
    background-color: {panel2};
}}
QCheckBox::indicator {{ border-radius: 3px; }}
QRadioButton::indicator {{ border-radius: 7px; }}
QCheckBox::indicator:checked {{ background-color: {C_ACCENT}; border-color: {C_ACCENT}; }}
QRadioButton::indicator:checked {{ background-color: {C_ACCENT}; border-color: {C_ACCENT}; }}

/* ---------- 标签页 ---------- */
QTabWidget::pane {{ border: 1px solid {border}; background-color: {panel}; border-radius: 6px; top: -1px; }}
QTabBar::tab {{
    background-color: {bg};
    border: 1px solid {border};
    border-bottom: none;
    border-top-left-radius: 5px;
    border-top-right-radius: 5px;
    padding: 7px 16px;
    margin-right: 2px;
    color: {dim};
}}
QTabBar::tab:selected {{ background-color: {panel}; color: {text}; border-bottom: 2px solid {C_ACCENT}; }}
QTabBar::tab:hover {{ color: {text}; }}

/* ---------- 列表与表格 ---------- */
QListWidget, QTableWidget, QTreeWidget {{
    background-color: {panel};
    border: 1px solid {border};
    border-radius: 5px;
    alternate-background-color: #182231;
    gridline-color: {border};
    outline: none;
}}
QListWidget::item {{ padding: 5px 6px; border-radius: 3px; }}
QListWidget::item:selected {{ background-color: {C_ACCENT_DIM}; color: #ffffff; }}
QListWidget::item:hover {{ background-color: #20304a; }}
QTableWidget::item {{ padding: 4px 6px; }}
QTableWidget::item:selected {{ background-color: {C_ACCENT_DIM}; color: #ffffff; }}
QHeaderView::section {{
    background-color: {panel2};
    color: {dim};
    border: none;
    border-right: 1px solid {border};
    border-bottom: 1px solid {border};
    padding: 5px 6px;
    font-size: 12px;
}}
QTableCornerButton::section {{ background-color: {panel2}; border: none; }}

/* ---------- 进度条 ---------- */
QProgressBar {{
    background-color: {panel2};
    border: 1px solid {border};
    border-radius: 5px;
    text-align: center;
    color: {text};
    height: 18px;
}}
QProgressBar::chunk {{ background-color: {C_ACCENT}; border-radius: 4px; }}
QProgressBar[role="total"]::chunk {{ background-color: {C_OK}; }}

/* ---------- 滚动条 ---------- */
QScrollBar:vertical {{ background: {bg}; width: 11px; margin: 0; }}
QScrollBar::handle:vertical {{ background: #2c3c54; border-radius: 5px; min-height: 28px; }}
QScrollBar::handle:vertical:hover {{ background: #3a4d69; }}
QScrollBar:horizontal {{ background: {bg}; height: 11px; margin: 0; }}
QScrollBar::handle:horizontal {{ background: #2c3c54; border-radius: 5px; min-width: 28px; }}
QScrollBar::handle:horizontal:hover {{ background: #3a4d69; }}
QScrollBar::add-line, QScrollBar::sub-line {{ height: 0; width: 0; }}
QScrollBar::add-page, QScrollBar::sub-page {{ background: none; }}

/* ---------- 分隔器 ---------- */
QSplitter::handle {{ background-color: {border}; }}
QSplitter::handle:horizontal {{ width: 3px; }}
QSplitter::handle:vertical {{ height: 3px; }}

/* ---------- 工具栏 ---------- */
QToolBar {{ background-color: {panel}; border: none; spacing: 6px; padding: 6px; }}
QToolBar QToolButton {{
    background-color: {panel2}; border: 1px solid {border}; border-radius: 5px; padding: 5px 12px; color: {text};
}}
QToolBar QToolButton:hover {{ border-color: {C_ACCENT_DIM}; }}

/* ---------- 状态栏 ---------- */
QStatusBar {{ background-color: {panel}; color: {dim}; border-top: 1px solid {border}; }}
QStatusBar::item {{ border: none; }}

/* ---------- 文本浏览（关于页/错误详情） ---------- */
QTextBrowser {{
    background-color: {panel2}; border: 1px solid {border}; border-radius: 5px; padding: 8px;
}}

/* ---------- 拖放视觉反馈 ---------- */
QListWidget[dragActive="true"] {{ border: 1px dashed {C_ACCENT}; }}
"""


def apply_theme(app, theme: str = "dark") -> None:
    """把样式应用到整个应用。

    参数:
        app: QApplication 实例。
        theme: 主题名。
    """
    # 直接设置全局样式表；Qt 会自动向下继承到所有子控件。
    app.setStyleSheet(build_qss(theme))
