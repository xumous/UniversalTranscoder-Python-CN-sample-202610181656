// *****************************************************************************
// theme.cpp —— 深色主题样式表的实现。
// 设计取向：简洁、低饱和、以中性灰为主，强调色只用于关键动作（如主按钮）。
// 目的：长时间盯着转码界面不刺眼，同时保证文字与背景的对比度满足可读性要求。
// *****************************************************************************
#include "theme.h"

// 主强调色。
QString Theme::accentColor()
{
    // 使用低饱和的青色，作为唯一强调色。
    return QStringLiteral("#2f9e9e");
}

// 返回样式表。
QString Theme::darkStyleSheet()
{
    // 用原始字符串拼接，便于阅读与修改。
    return QStringLiteral(R"QSS(
/* 全局：基础背景与文字颜色（深灰底 + 浅灰字，保证对比度）。 */
QWidget {
    background-color: #202427;
    color: #dfe3e6;
    font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
    font-size: 12px;
}

/* 主窗口：略微区分于子控件，形成层次。 */
QMainWindow {
    background-color: #1b1f22;
}

/* 标签页容器：去掉默认边框，避免视觉噪声。 */
QTabWidget::pane {
    border: 1px solid #33383d;
    background-color: #202427;
    top: -1px;
}

/* 标签按钮：横向排布，未选中时低调，选中时显示强调色下划线。 */
QTabBar::tab {
    background-color: #262b2f;
    color: #a9b1b8;
    padding: 7px 16px;
    border: 1px solid #33383d;
    border-bottom: none;
    min-width: 72px;
}

/* 标签按钮选中态：更亮的底色 + 强调色文字。 */
QTabBar::tab:selected {
    background-color: #2c3338;
    color: #66d1d1;
    font-weight: bold;
}

/* 标签按钮悬停态：轻微提亮，提供可点击的反馈。 */
QTabBar::tab:hover:!selected {
    background-color: #2a3034;
    color: #cfd6dc;
}

/* 分组框：用于把“基础/画面/音频/高级”等控件分组。 */
QGroupBox {
    border: 1px solid #33383d;
    border-radius: 4px;
    margin-top: 14px;
    padding: 10px 8px 8px 8px;
}

/* 分组框标题：放在边框上，形成“标题嵌入边框”的紧凑外观。 */
QGroupBox::title {
    subcontrol-origin: margin;
    subcontrol-position: top left;
    left: 10px;
    padding: 0 5px;
    color: #9aa4ad;
    background-color: #202427;
}

/* 输入框、下拉框、数字框：统一底色与边框。 */
QLineEdit, QComboBox, QSpinBox, QDoubleSpinBox, QPlainTextEdit, QTextEdit {
    background-color: #191d20;
    border: 1px solid #363c42;
    border-radius: 3px;
    padding: 4px 6px;
    selection-background-color: #2f9e9e;
    selection-color: #10161a;
}

/* 输入控件获得焦点：用强调色边框提示当前编辑对象。 */
QLineEdit:focus, QComboBox:focus, QSpinBox:focus, QDoubleSpinBox:focus,
QPlainTextEdit:focus, QTextEdit:focus {
    border: 1px solid #2f9e9e;
}

/* 输入控件禁用态：降低对比度以避免误以为可编辑。 */
QLineEdit:disabled, QComboBox:disabled, QSpinBox:disabled, QDoubleSpinBox:disabled {
    background-color: #242a2e;
    color: #6d757c;
}

/* 下拉框弹出列表：深色背景 + 悬停高亮。 */
QComboBox QAbstractItemView {
    background-color: #1d2226;
    border: 1px solid #363c42;
    selection-background-color: #2f6f6f;
    selection-color: #ffffff;
    outline: none;
}

/* 下拉框箭头区域：保持与输入框一致的配色。 */
QComboBox::drop-down {
    border: none;
    width: 20px;
}

/* 普通按钮：浅灰底，悬停提亮，按下下沉。 */
QPushButton {
    background-color: #2d3439;
    border: 1px solid #3b434a;
    border-radius: 3px;
    padding: 5px 14px;
    color: #dfe3e6;
}

/* 按钮悬停。 */
QPushButton:hover {
    background-color: #353d43;
    border: 1px solid #4a545c;
}

/* 按钮按下。 */
QPushButton:pressed {
    background-color: #242a2e;
}

/* 按钮禁用：明显变暗。 */
QPushButton:disabled {
    background-color: #252b2f;
    color: #5f676e;
    border: 1px solid #30363b;
}

/* 主按钮（开始转码）：唯一使用强调色的控件，引导用户点击。 */
QPushButton[primary="true"] {
    background-color: #2f9e9e;
    border: 1px solid #3fb6b6;
    color: #0e1a1a;
    font-weight: bold;
}

/* 主按钮悬停。 */
QPushButton[primary="true"]:hover {
    background-color: #3ab0b0;
}

/* 主按钮禁用。 */
QPushButton[primary="true"]:disabled {
    background-color: #2a4a4a;
    color: #7f9a9a;
    border: 1px solid #345a5a;
}

/* 进度条：细长、深底、强调色填充。 */
QProgressBar {
    background-color: #191d20;
    border: 1px solid #363c42;
    border-radius: 3px;
    text-align: center;
    color: #cfd6dc;
    height: 18px;
}

/* 进度条已完成部分。 */
QProgressBar::chunk {
    background-color: #2f9e9e;
    border-radius: 2px;
}

/* 表格：用于任务队列。 */
QTableWidget, QTableView {
    background-color: #191d20;
    alternate-background-color: #1e2327;
    gridline-color: #2c3237;
    border: 1px solid #33383d;
    selection-background-color: #2a4a4a;
    selection-color: #ffffff;
}

/* 表头。 */
QHeaderView::section {
    background-color: #262b2f;
    color: #a9b1b8;
    padding: 5px 6px;
    border: none;
    border-right: 1px solid #2c3237;
    border-bottom: 1px solid #33383d;
}

/* 日志框：等宽字体便于对齐命令文本。 */
QPlainTextEdit {
    font-family: "Consolas", "Cascadia Mono", monospace;
    font-size: 11px;
}

/* 列表控件（文件列表）。 */
QListWidget {
    background-color: #191d20;
    border: 1px solid #33383d;
    outline: none;
}

/* 列表项选中态。 */
QListWidget::item:selected {
    background-color: #2a4a4a;
    color: #ffffff;
}

/* 复选框指示器：统一大小与配色。 */
QCheckBox::indicator, QRadioButton::indicator {
    width: 14px;
    height: 14px;
}

/* 滚动条：细长深色，减少视觉占用。 */
QScrollBar:vertical {
    background: #1b1f22;
    width: 10px;
    margin: 0;
}

/* 滚动条滑块。 */
QScrollBar::handle:vertical {
    background: #3a4249;
    min-height: 24px;
    border-radius: 5px;
}

/* 滚动条滑块悬停。 */
QScrollBar::handle:vertical:hover {
    background: #4a545c;
}

/* 横向滚动条。 */
QScrollBar:horizontal {
    background: #1b1f22;
    height: 10px;
    margin: 0;
}

/* 横向滑块。 */
QScrollBar::handle:horizontal {
    background: #3a4249;
    min-width: 24px;
    border-radius: 5px;
}

/* 滚动条两端按钮：隐藏以保持简洁。 */
QScrollBar::add-line, QScrollBar::sub-line {
    height: 0;
    width: 0;
}

/* 状态栏。 */
QStatusBar {
    background-color: #1b1f22;
    color: #9aa4ad;
}

/* 工具提示。 */
QToolTip {
    background-color: #262b2f;
    color: #dfe3e6;
    border: 1px solid #4a545c;
    padding: 4px;
}
)QSS");
}