# -*- coding: utf-8 -*-
# 主窗口实现（PyQt6 桌面端）。
# 结构：
#   ┌───────────────────────────────────────────────────────────┐
#   │ 工具栏：添加文件 / 添加文件夹 / 移除 / 清空 ‖ 开始 / 暂停 / 停止 ‖ 启动 Web 端 │
#   ├──────────────────┬────────────────────────────────────────┤
#   │ 待转换文件列表    │ 参数标签页（视频/音频/图片/文档/高级/设置/关于） │
#   ├──────────────────┴────────────────────────────────────────┤
#   │ 任务队列表格 + 总进度条 + 队列操作按钮                        │
#   └───────────────────────────────────────────────────────────┘
# 线程安全说明（重点）：
#   内核 TaskQueue 的回调在“工作线程”中触发，Qt 控件只能在 GUI 主线程操作。
#   因此本文件把所有回调都通过 pyqtSignal 转发：信号跨线程发射时，Qt 会自动以
#   “队列连接（QueuedConnection）”把槽函数排到主线程执行，从而彻底避免跨线程操作控件导致的崩溃。

import os  # 路径处理
import sys  # 用于在 Windows 上设置任务栏图标等
import logging  # 日志
from typing import List, Optional, Dict  # 类型标注

from PyQt6.QtCore import Qt, pyqtSignal, QTimer, QSize  # Qt 核心（信号、定时器、尺寸）
from PyQt6.QtGui import QAction, QIcon, QFont, QDesktopServices  # 图标、字体、打开目录
from PyQt6.QtCore import QUrl  # 用于 QDesktopServices
from PyQt6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
    QGroupBox, QLabel, QPushButton, QListWidget, QListWidgetItem, QTabWidget,
    QComboBox, QLineEdit, QSpinBox, QDoubleSpinBox, QCheckBox, QRadioButton,
    QPlainTextEdit, QTableWidget, QTableWidgetItem, QProgressBar, QSplitter,
    QFileDialog, QMessageBox, QScrollArea, QFrame, QHeaderView, QAbstractItemView,
    QFormLayout, QButtonGroup, QTextBrowser, QDialog, QDialogButtonBox,
)

from app import __app_name__, __version__, __author__
# 注意：strings 模块里定义的是“类 S”，所有界面文案都是 S 的类属性/嵌套类属性。
# 因此这里要导入类本身（from ... import S），而不是导入模块，否则 S.Win 这类写法会失败。
from app.util.strings import S
from app.util.config import config
from app.util import paths, fmt
from app.core import (ffmpeg_locator, capabilities, hwaccel, presets,
                      media_info, command_builder, doc_convert, thumbnail)
from app.core.tasks import TaskQueue, Task, TaskStatus, STATUS_TEXT

logger = logging.getLogger(__name__)

# 各类型可选的目标格式（界面下拉框）。
FORMATS_VIDEO = ["mp4", "mkv", "mov", "avi", "flv", "webm", "ts", "mpg", "wmv", "3gp", "m4v", "gif"]
FORMATS_AUDIO = ["mp3", "aac", "m4a", "wav", "flac", "ogg", "opus", "wma", "ac3", "amr", "aiff"]
FORMATS_IMAGE = ["jpg", "png", "webp", "bmp", "tiff", "gif", "avif", "ico"]

# 质量档位显示名与内部值。
QUALITY_ITEMS = [
    ("无损（体积最大）", "lossless"),
    ("高质量", "high"),
    ("标准（推荐）", "medium"),
    ("小体积", "low"),
    ("自定义", "custom"),
]

# 缩放模式显示名与内部值。
SCALE_ITEMS = [
    ("等比缩放（完整画面）", "fit"),
    ("等比填充（裁剪）", "fill"),
    ("拉伸（可能变形）", "stretch"),
]

# 镜像选项。
FLIP_ITEMS = [("不镜像", "none"), ("水平镜像", "h"), ("垂直镜像", "v"), ("水平+垂直", "both")]

# 旋转选项。
ROTATE_ITEMS = [("不旋转", "0"), ("顺时针 90°", "90"), ("旋转 180°", "180"), ("顺时针 270°", "270")]

# 水印位置（overlay 表达式关键字）。
WM_POS_ITEMS = [("左上", "tl"), ("右上", "tr"), ("左下", "bl"), ("右下", "br"), ("居中", "center")]

# 输出命名方式。
NAME_MODE_ITEMS = [("替换扩展名", "replace"), ("原名+后缀", "suffix"), ("自定义模板", "custom")]

# 流选择模式。
STREAM_MODE_ITEMS = [("自动选择", "auto"), ("只取第一组流", "first"), ("手动指定流序号", "custom")]


class DropListWidget(QListWidget):
    """支持拖入文件/文件夹的列表控件。

    为什么单独封装：Qt 的拖放需要重写 dragEnterEvent / dragMoveEvent / dropEvent 三个事件，
    且需要显式开启 acceptDrops，封装成类后主窗口代码更干净。
    """

    # 自定义信号：拖入文件后发出（携带路径列表），由主窗口负责添加与去重。
    files_dropped = pyqtSignal(list)

    def __init__(self, parent=None):
        """初始化并开启拖放能力。"""
        super().__init__(parent)
        # 允许接收拖放。
        self.setAcceptDrops(True)
        # 支持多选（便于批量移除）。
        self.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        # 允许拖动内部条目（本项目未使用内部排序，但开启后体验更自然）。
        self.setDragDropMode(QAbstractItemView.DragDropMode.DropOnly)
        # 交替行底色，便于横向阅读。
        self.setAlternatingRowColors(True)

    def dragEnterEvent(self, event):
        """拖入时判断是否接受：只要带文件 URL 就接受。"""
        # 检查 mime 数据里是否有 URL。
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
            # 给用户一个视觉提示（虚线边框），通过动态属性 + 样式表实现。
            self.setProperty("dragActive", "true")
            self.style().unpolish(self)
            self.style().polish(self)
        else:
            event.ignore()

    def dragMoveEvent(self, event):
        """拖动过程中持续接受，保证放下动作可用。"""
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dragLeaveEvent(self, event):
        """离开时清除视觉提示。"""
        self.setProperty("dragActive", "false")
        self.style().unpolish(self)
        self.style().polish(self)

    def dropEvent(self, event):
        """放下时收集本地文件路径并发出信号。"""
        # 清除视觉提示。
        self.setProperty("dragActive", "false")
        self.style().unpolish(self)
        self.style().polish(self)
        # 收集路径（toLocalFile 会把 URL 转成本地路径）。
        paths_in = [u.toLocalFile() for u in event.mimeData().urls() if u.isLocalFile()]
        # 过滤空值。
        paths_in = [p for p in paths_in if p]
        if paths_in:
            # 发出信号，交给主窗口处理。
            self.files_dropped.emit(paths_in)
            event.acceptProposedAction()


def _make_row(layout: QGridLayout, row: int, label: str, widget: QWidget,
              hint: str = "") -> QLabel:
    """在网格布局中插入一行“标签 + 控件”，返回标签控件以便后续显隐。

    参数:
        layout: 目标网格布局。
        row: 行号。
        label: 左侧标签文本。
        widget: 右侧控件。
        hint: 可选的灰色提示（放在控件下方或右侧）。
    返回:
        标签 QLabel（调用方可保存以便整行显隐）。
    """
    # 创建标签。
    lbl = QLabel(label)
    # 右对齐，形成整齐的两列观感。
    lbl.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
    # 固定标签列宽，避免不同行参差不齐。
    lbl.setMinimumWidth(78)
    # 放入网格。
    layout.addWidget(lbl, row, 0)
    layout.addWidget(widget, row, 1)
    # 若给了提示，作为第三列的灰色小字。
    if hint:
        h = QLabel(hint)
        h.setProperty("role", "hint")
        layout.addWidget(h, row, 2)
    return lbl


class SettingsPanel(QWidget):
    """参数设置面板（视频/音频/图片三种模式共用同一套控件，按模式显隐）。

    设计权衡：三种媒体类型有大量重合参数（格式、质量、码率、裁剪…），
    如果各写一套面板会造成大量重复代码与不一致；因此这里用“一套控件 + 按模式显隐”的方式实现。
    """

    def __init__(self, mode: str = "video", parent=None):
        """初始化面板。

        参数:
            mode: "video" / "audio" / "image"，决定默认显示哪些行。
        """
        super().__init__(parent)
        # 记录当前模式。
        self.mode = mode
        # 保存“行号 -> 行内控件列表”，用于按模式显隐整行。
        self._rows: Dict[int, List[QWidget]] = {}
        # 构建界面。
        self._build()

    def _wrap(self, title: str) -> QGroupBox:
        """创建一个带标题的分组框，并在其中放置一个网格布局。"""
        box = QGroupBox(title)
        grid = QGridLayout(box)
        # 设置列拉伸：第 1 列（控件列）占据主要空间。
        grid.setColumnStretch(1, 1)
        # 行间距紧凑但可读。
        grid.setVerticalSpacing(7)
        grid.setHorizontalSpacing(9)
        # 把布局挂在 box 上并记录，供 _build 使用。
        box._grid = grid  # 动态挂属性，简化后续访问
        return box

    def _add_row(self, box: QGroupBox, label: str, widget: QWidget, hint: str = "") -> int:
        """向分组框追加一行，返回行号。"""
        grid = box._grid
        # 该行当前已有多少行（用行计数属性维护）。
        idx = getattr(box, "_rowcount", 0)
        # 插入控件。
        lbl = _make_row(grid, idx, label, widget, hint)
        # 行号自增。
        box._rowcount = idx + 1
        # 记录该行的全部控件（用于显隐）。
        widgets = [lbl, widget]
        # 若存在提示列，也一并记录。
        if hint:
            item = grid.itemAtPosition(idx, 2)
            if item and item.widget():
                widgets.append(item.widget())
        self._rows[idx] = widgets
        # 记录行号与 box 的对应关系，供模式切换时使用。
        setattr(widget, "_row_index", idx)
        setattr(widget, "_row_box", box)
        return idx

    def _build(self) -> None:
        """构建全部控件。"""
        # 外层纵向布局；内容放在滚动区域里，因为行数较多。
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        inner = QWidget()
        v = QVBoxLayout(inner)
        v.setContentsMargins(10, 6, 10, 10)
        v.setSpacing(10)

        # ========== 分组一：输出与基本设置 ==========
        g1 = self._wrap("输出设置")
        # 目标格式下拉。
        self.cb_format = QComboBox()
        # 按模式填充格式列表。
        self.cb_format.addItems({"video": FORMATS_VIDEO, "audio": FORMATS_AUDIO,
                                 "image": FORMATS_IMAGE}.get(self.mode, FORMATS_VIDEO))
        self.r_format = self._add_row(g1, "目标格式", self.cb_format)

        # 预设下拉（选预设自动回填其它参数）。
        self.cb_preset = QComboBox()
        # 第一项为“不使用预设”。
        self.cb_preset.addItem("（不使用预设）", "")
        # 按类别填充预设。
        for p in presets.by_category(self.mode):
            self.cb_preset.addItem(p.name, p.key)
        self.r_preset = self._add_row(g1, "预设方案", self.cb_preset)

        # 输出目录 + 浏览按钮（用容器承载两个控件）。
        out_wrap = QWidget()
        oh = QHBoxLayout(out_wrap)
        oh.setContentsMargins(0, 0, 0, 0)
        self.ed_outdir = QLineEdit()
        # 提示文字说明留空的含义。
        self.ed_outdir.setPlaceholderText("留空 = 与源文件同目录")
        self.btn_outdir = QPushButton("浏览…")
        oh.addWidget(self.ed_outdir, 1)
        oh.addWidget(self.btn_outdir, 0)
        self.r_outdir = self._add_row(g1, "输出目录", out_wrap)

        # 命名方式。
        self.cb_name = QComboBox()
        for text, val in NAME_MODE_ITEMS:
            self.cb_name.addItem(text, val)
        self.r_name = self._add_row(g1, "命名方式", self.cb_name)

        # 覆盖开关。
        self.chk_overwrite = QCheckBox("覆盖已存在的输出文件（关闭时自动追加序号）")
        self.r_overwrite = self._add_row(g1, "覆盖", self.chk_overwrite)

        v.addWidget(g1)

        # ========== 分组二：视频参数 ==========
        g2 = self._wrap("视频参数")
        # 质量档。
        self.cb_quality = QComboBox()
        for text, val in QUALITY_ITEMS:
            self.cb_quality.addItem(text, val)
        self.r_quality = self._add_row(g2, "质量", self.cb_quality)

        # CRF（恒定质量）。
        self.sp_crf = QSpinBox()
        self.sp_crf.setRange(0, 51)
        self.sp_crf.setValue(23)
        self.r_crf = self._add_row(g2, "CRF", self.sp_crf, "0 为无损，越大画质越低体积越小")

        # 分辨率：宽 + 高 + 缩放模式。
        res_wrap = QWidget()
        rh = QHBoxLayout(res_wrap)
        rh.setContentsMargins(0, 0, 0, 0)
        self.sp_w = QSpinBox()
        self.sp_w.setRange(0, 16384)
        # 特殊值 0 表示“保持原样”。
        self.sp_w.setSpecialValueText("原样")
        self.sp_h = QSpinBox()
        self.sp_h.setRange(0, 16384)
        self.sp_h.setSpecialValueText("原样")
        self.cb_scale = QComboBox()
        for text, val in SCALE_ITEMS:
            self.cb_scale.addItem(text, val)
        rh.addWidget(QLabel("宽"))
        rh.addWidget(self.sp_w)
        rh.addWidget(QLabel("高"))
        rh.addWidget(self.sp_h)
        rh.addWidget(self.cb_scale, 1)
        self.r_res = self._add_row(g2, "分辨率", res_wrap)

        # 帧率（可编辑下拉，允许手填 "30000/1001"）。
        self.cb_fps = QComboBox()
        self.cb_fps.setEditable(True)
        self.cb_fps.addItem("保持原样", "")
        for f in ("23.976", "24", "25", "29.97", "30", "50", "60"):
            self.cb_fps.addItem(f, f)
        self.r_fps = self._add_row(g2, "帧率", self.cb_fps)

        # 视频编码器（可编辑下拉）。
        self.cb_vcodec = QComboBox()
        self.cb_vcodec.setEditable(True)
        self.cb_vcodec.addItem("自动（按容器决定）", "")
        for c in ("libx264", "libx265", "libvpx-vp9", "libvpx", "mpeg4", "prores_ks",
                  "h264_nvenc", "hevc_nvenc", "h264_qsv", "h264_amf"):
            self.cb_vcodec.addItem(c, c)
        self.r_vcodec = self._add_row(g2, "视频编码", self.cb_vcodec)

        # 视频码率。
        self.ed_vbr = QLineEdit()
        self.ed_vbr.setPlaceholderText("例如 2500k；留空则按 CRF 恒定质量")
        self.r_vbr = self._add_row(g2, "视频码率", self.ed_vbr)

        # 像素格式。
        self.cb_pixfmt = QComboBox()
        for pf in ("（自动）", "yuv420p", "yuv422p", "yuv444p", "yuv420p10le", "nv12", "rgb24"):
            self.cb_pixfmt.addItem(pf, "" if pf.startswith("（") else pf)
        self.r_pixfmt = self._add_row(g2, "像素格式", self.cb_pixfmt)

        # 旋转与镜像。
        trans_wrap = QWidget()
        th = QHBoxLayout(trans_wrap)
        th.setContentsMargins(0, 0, 0, 0)
        self.cb_rotate = QComboBox()
        for text, val in ROTATE_ITEMS:
            self.cb_rotate.addItem(text, val)
        self.cb_flip = QComboBox()
        for text, val in FLIP_ITEMS:
            self.cb_flip.addItem(text, val)
        th.addWidget(self.cb_rotate, 1)
        th.addWidget(self.cb_flip, 1)
        self.r_transform = self._add_row(g2, "旋转/镜像", trans_wrap)

        # 裁剪区域。
        self.ed_crop = QLineEdit()
        self.ed_crop.setPlaceholderText("w:h:x:y，例如 640:480:0:0；留空不裁剪")
        self.r_crop = self._add_row(g2, "裁剪区域", self.ed_crop)

        v.addWidget(g2)

        # ========== 分组三：音频参数 ==========
        g3 = self._wrap("音频参数")
        # 音频编码器。
        self.cb_acodec = QComboBox()
        self.cb_acodec.setEditable(True)
        self.cb_acodec.addItem("自动（按容器决定）", "")
        for c in ("aac", "libmp3lame", "libopus", "libvorbis", "flac", "pcm_s16le",
                  "ac3", "wmav2", "copy"):
            self.cb_acodec.addItem(c, c)
        self.r_acodec = self._add_row(g3, "音频编码", self.cb_acodec)

        # 音频码率。
        self.ed_abr = QLineEdit()
        self.ed_abr.setPlaceholderText("例如 192k；留空按质量档推断")
        self.r_abr = self._add_row(g3, "音频码率", self.ed_abr)

        # 声道。
        self.cb_channels = QComboBox()
        self.cb_channels.addItem("保持原样", 0)
        for n in (1, 2, 6, 8):
            self.cb_channels.addItem(f"{n} 声道", n)
        self.r_channels = self._add_row(g3, "声道", self.cb_channels)

        # 采样率。
        self.cb_samplerate = QComboBox()
        self.cb_samplerate.addItem("保持原样", 0)
        for sr in (8000, 16000, 22050, 32000, 44100, 48000, 96000):
            self.cb_samplerate.addItem(f"{sr} Hz", sr)
        self.r_samplerate = self._add_row(g3, "采样率", self.cb_samplerate)

        # 音量。
        self.ed_volume = QLineEdit()
        self.ed_volume.setPlaceholderText("倍数（1.5）或分贝（-6dB）；留空不调整")
        self.r_volume = self._add_row(g3, "音量", self.ed_volume)

        v.addWidget(g3)

        # ========== 分组四：时间与效果 ==========
        g4 = self._wrap("时间与效果")
        # 起始时间。
        self.ed_start = QLineEdit()
        self.ed_start.setPlaceholderText("00:00:10 或 10.5；留空从头开始")
        self.r_start = self._add_row(g4, "起始时间", self.ed_start)

        # 截取时长。
        self.ed_duration = QLineEdit()
        self.ed_duration.setPlaceholderText("截取时长，例如 00:01:00；留空到结尾")
        self.r_duration = self._add_row(g4, "截取时长", self.ed_duration)

        # 结束时间。
        self.ed_end = QLineEdit()
        self.ed_end.setPlaceholderText("结束时间点；与“截取时长”二选一，时长优先")
        self.r_end = self._add_row(g4, "结束时间", self.ed_end)

        # 变速。
        self.sp_speed = QDoubleSpinBox()
        self.sp_speed.setRange(0.1, 20.0)
        self.sp_speed.setSingleStep(0.1)
        self.sp_speed.setValue(1.0)
        self.sp_speed.setSuffix(" 倍")
        self.r_speed = self._add_row(g4, "变速", self.sp_speed)

        v.addWidget(g4)

        # ========== 分组五：水印与字幕 ==========
        g5 = self._wrap("水印与字幕")
        # 图片水印路径 + 浏览。
        wm_wrap = QWidget()
        wh = QHBoxLayout(wm_wrap)
        wh.setContentsMargins(0, 0, 0, 0)
        self.ed_wm_path = QLineEdit()
        self.ed_wm_path.setPlaceholderText("图片水印路径；留空不加水印")
        self.btn_wm_path = QPushButton("浏览…")
        wh.addWidget(self.ed_wm_path, 1)
        wh.addWidget(self.btn_wm_path, 0)
        self.r_wm_path = self._add_row(g5, "图片水印", wm_wrap)

        # 文字水印。
        self.ed_wm_text = QLineEdit()
        self.ed_wm_text.setPlaceholderText("文字水印内容；与图片水印二选一")
        self.r_wm_text = self._add_row(g5, "文字水印", self.ed_wm_text)

        # 水印位置。
        self.cb_wm_pos = QComboBox()
        for text, val in WM_POS_ITEMS:
            self.cb_wm_pos.addItem(text, val)
        self.r_wm_pos = self._add_row(g5, "水印位置", self.cb_wm_pos)

        # 水印不透明度。
        self.sp_wm_opacity = QDoubleSpinBox()
        self.sp_wm_opacity.setRange(0.05, 1.0)
        self.sp_wm_opacity.setSingleStep(0.05)
        self.sp_wm_opacity.setValue(0.75)
        self.r_wm_opacity = self._add_row(g5, "水印不透明度", self.sp_wm_opacity)

        # 字幕路径 + 浏览。
        sub_wrap = QWidget()
        sh = QHBoxLayout(sub_wrap)
        sh.setContentsMargins(0, 0, 0, 0)
        self.ed_sub_path = QLineEdit()
        self.ed_sub_path.setPlaceholderText("外挂字幕（srt/ass）；留空不烧录")
        self.btn_sub_path = QPushButton("浏览…")
        sh.addWidget(self.ed_sub_path, 1)
        sh.addWidget(self.btn_sub_path, 0)
        self.r_sub_path = self._add_row(g5, "字幕烧录", sub_wrap)

        v.addWidget(g5)

        # ========== 分组六：元数据与高级 ==========
        g6 = self._wrap("元数据与高级")
        # 元数据四项（用户/艺术家/专辑/备注）。
        self.ed_meta_title = QLineEdit()
        self.r_meta_title = self._add_row(g6, "标题", self.ed_meta_title)
        self.ed_meta_artist = QLineEdit()
        self.r_meta_artist = self._add_row(g6, "艺术家", self.ed_meta_artist)
        self.ed_meta_album = QLineEdit()
        self.r_meta_album = self._add_row(g6, "专辑", self.ed_meta_album)
        self.ed_meta_comment = QLineEdit()
        self.r_meta_comment = self._add_row(g6, "备注", self.ed_meta_comment)

        # 流模式。
        self.cb_stream = QComboBox()
        for text, val in STREAM_MODE_ITEMS:
            self.cb_stream.addItem(text, val)
        self.r_stream = self._add_row(g6, "流选择", self.cb_stream)

        # 手动流序号（三个）。
        idx_wrap = QWidget()
        ih = QHBoxLayout(idx_wrap)
        ih.setContentsMargins(0, 0, 0, 0)
        self.sp_vindex = QSpinBox()
        self.sp_vindex.setRange(-1, 99)
        self.sp_vindex.setValue(-1)
        self.sp_aindex = QSpinBox()
        self.sp_aindex.setRange(-1, 99)
        self.sp_aindex.setValue(-1)
        self.sp_sindex = QSpinBox()
        self.sp_sindex.setRange(-1, 99)
        self.sp_sindex.setValue(-1)
        ih.addWidget(QLabel("视频"))
        ih.addWidget(self.sp_vindex)
        ih.addWidget(QLabel("音频"))
        ih.addWidget(self.sp_aindex)
        ih.addWidget(QLabel("字幕"))
        ih.addWidget(self.sp_sindex)
        self.r_index = self._add_row(g6, "流序号", idx_wrap, "-1 表示不选该类流")

        # 丢弃音/视频。
        drop_wrap = QWidget()
        dh = QHBoxLayout(drop_wrap)
        dh.setContentsMargins(0, 0, 0, 0)
        self.chk_drop_audio = QCheckBox("丢弃音频")
        self.chk_drop_video = QCheckBox("丢弃视频")
        dh.addWidget(self.chk_drop_audio)
        dh.addWidget(self.chk_drop_video)
        dh.addStretch(1)
        self.r_drop = self._add_row(g6, "丢弃流", drop_wrap)

        # 仅换容器（流复制）。
        self.chk_copy = QCheckBox("仅更换容器（流复制，不重新编码；与滤镜/水印互斥）")
        self.r_copy = self._add_row(g6, "快速模式", self.chk_copy)

        v.addWidget(g6)
        # 底部留白，保证滚动到底部时内容不被贴着边缘。
        v.addStretch(1)
        # 把内部控件塞进滚动区。
        scroll.setWidget(inner)
        outer.addWidget(scroll)

        # 绑定事件（预设联动、命名方式联动等）。
        self._wire_events()
        # 按模式刷新显隐。
        self.apply_mode(self.mode)

    def _wire_events(self) -> None:
        """绑定控件事件。"""
        # 预设切换：回填参数。
        self.cb_preset.currentIndexChanged.connect(self._on_preset_changed)
        # 质量档位联动 CRF：避免“界面上显示 23、实际却按低质量 28 执行”这类不一致。
        self.cb_quality.currentIndexChanged.connect(self._on_quality_changed)
        # 输出目录浏览。
        self.btn_outdir.clicked.connect(self._pick_outdir)
        # 水印图片浏览。
        self.btn_wm_path.clicked.connect(lambda: self._pick_file(self.ed_wm_path, "选择水印图片",
                                                                 "图片文件 (*.png *.jpg *.jpeg *.bmp *.webp *.gif)"))
        # 字幕浏览。
        self.btn_sub_path.clicked.connect(lambda: self._pick_file(self.ed_sub_path, "选择字幕文件",
                                                                  "字幕文件 (*.srt *.ass *.ssa *.vtt);;全部文件 (*.*)"))
        # 命名方式为自定义模板时给出提示。
        self.cb_name.currentIndexChanged.connect(self._on_name_mode_changed)

    # 质量档位 → CRF 的映射（与内核 command_builder.QUALITY_TO_CRF 保持一致）。
    QUALITY_CRF_MAP = {"lossless": 0, "high": 20, "medium": 23, "low": 28}

    def _on_quality_changed(self) -> None:
        """质量档位变化时同步 CRF 数值。

        说明：选择“自定义”时不修改 CRF，把控制权交给用户手动填写。
        """
        q = str(self.cb_quality.currentData() or "medium")
        if q in self.QUALITY_CRF_MAP:
            self.sp_crf.setValue(self.QUALITY_CRF_MAP[q])

    def _pick_outdir(self) -> None:
        """选择输出目录。"""
        # 打开目录选择对话框，默认目录取当前输入值或上次记忆。
        start = self.ed_outdir.text().strip() or str(config.get("last_output_dir", "") or "")
        chosen = QFileDialog.getExistingDirectory(self, "选择输出目录", start)
        if chosen:
            # 回填并记忆。
            self.ed_outdir.setText(chosen)
            config.set("last_output_dir", chosen)
            config.save()

    def _pick_file(self, target: QLineEdit, title: str, filt: str) -> None:
        """通用文件选择并回填到指定输入框。"""
        chosen, _ = QFileDialog.getOpenFileName(self, title, "", filt)
        if chosen:
            target.setText(chosen)

    def _on_name_mode_changed(self) -> None:
        """命名方式变化时给出说明。"""
        val = self.cb_name.currentData()
        # 自定义模板时把输入框切换为显示模板（复用输出目录输入框会混乱，这里用状态栏提示足够）。
        if val == "custom":
            self.ed_outdir.setToolTip("自定义模板示例：{name}_{index}（{name}=原名，{ext}=目标扩展名，{date}=日期）")
        else:
            self.ed_outdir.setToolTip("")

    def _on_preset_changed(self) -> None:
        """选中预设后，把预设参数回填到面板控件。"""
        key = self.cb_preset.currentData()
        # 空 key 表示不使用预设。
        if not key:
            return
        p = presets.by_key(key)
        if not p:
            return
        # 回填目标格式。
        idx = self.cb_format.findData(p.container) if self.cb_format.findData(
            p.container) >= 0 else self.cb_format.findText(p.container)
        if idx >= 0:
            self.cb_format.setCurrentIndex(idx)
        # 回填质量档。
        qi = self.cb_quality.findData(p.quality)
        if qi >= 0:
            self.cb_quality.setCurrentIndex(qi)
        # 回填 CRF。
        if p.crf:
            try:
                self.sp_crf.setValue(int(float(p.crf)))
            except ValueError:
                pass
        # 回填视频码率与编码器。
        self.ed_vbr.setText(p.v_bitrate or "")
        if p.video_codec:
            vi = self.cb_vcodec.findData(p.video_codec)
            if vi >= 0:
                self.cb_vcodec.setCurrentIndex(vi)
            else:
                self.cb_vcodec.setCurrentText(p.video_codec)
        # 回填音频。
        self.ed_abr.setText(p.a_bitrate or "")
        if p.audio_codec:
            ai = self.cb_acodec.findData(p.audio_codec)
            if ai >= 0:
                self.cb_acodec.setCurrentIndex(ai)
            else:
                self.cb_acodec.setCurrentText(p.audio_codec)
        # 回填像素格式。
        if p.pixel_format:
            pi = self.cb_pixfmt.findData(p.pixel_format)
            if pi >= 0:
                self.cb_pixfmt.setCurrentIndex(pi)

    def apply_mode(self, mode: str) -> None:
        """按模式显隐各行。

        显隐策略（哪一行属于哪种媒体）：
            视频：几乎全部行；
            音频：隐藏视频专属行（分辨率/帧率/视频编码/视频码率/像素格式/旋转镜像/裁剪/字幕/流选择）；
            图片：隐藏音频专属行与视频编码相关行，保留分辨率/旋转/裁剪/水印。
        """
        # 记录模式。
        self.mode = mode

        # 定义各模式需要隐藏的行号集合（行号来自 _build 中的添加顺序）。
        # 说明：这里用“按属性名字定位”的方式更稳妥——直接收集每个控件所在行的索引。
        def hide(widget, hidden: bool):
            """按控件找到其所在行并整体显隐。"""
            idx = getattr(widget, "_row_index", None)
            box = getattr(widget, "_row_box", None)
            if idx is None or box is None:
                return
            for w in self._rows.get(idx, []):
                # 记录该行属于哪个 box，避免不同分组框行号相同而互相影响。
                if getattr(w, "_row_box", box) is box:
                    w.setVisible(not hidden)

        # 视频专属控件列表。
        video_only = [self.cb_fps, self.cb_vcodec, self.ed_vbr, self.cb_pixfmt,
                      self.cb_rotate, self.cb_flip, self.ed_crop, self.ed_sub_path,
                      self.cb_stream, self.sp_vindex, self.sp_aindex, self.sp_sindex,
                      self.ed_wm_path, self.ed_wm_text, self.cb_wm_pos, self.sp_wm_opacity]
        # 音频专属控件列表。
        audio_only = [self.cb_acodec, self.ed_abr, self.cb_channels,
                      self.cb_samplerate, self.ed_volume]
        # 时间/变速对音视频都有意义，但对图片无意义。
        time_only = [self.ed_start, self.ed_duration, self.ed_end, self.sp_speed]

        if mode == "video":
            # 视频：显示全部。
            for w in video_only + audio_only + time_only:
                hide(w, False)
        elif mode == "audio":
            # 音频：隐藏视频专属。
            for w in video_only:
                hide(w, True)
            for w in audio_only + time_only:
                hide(w, False)
            # 分辨率/质量/CRF 对音频无意义，一并隐藏。
            for w in (self.sp_w, self.sp_h, self.cb_scale, self.sp_crf, self.cb_quality):
                hide(w, True)
        elif mode == "image":
            # 图片：隐藏音频专属与时间/变速与字幕/流选择。
            for w in audio_only + time_only:
                hide(w, True)
            for w in (self.cb_fps, self.ed_vbr, self.cb_vcodec, self.cb_stream,
                      self.sp_vindex, self.sp_aindex, self.sp_sindex, self.ed_sub_path):
                hide(w, True)
            # 图片仍需要：格式/质量/CRF/分辨率/旋转镜像/裁剪/水印/编码器（部分）。
            for w in (self.ed_wm_path, self.ed_wm_text, self.cb_wm_pos, self.sp_wm_opacity,
                      self.sp_crf, self.cb_quality, self.sp_w, self.sp_h, self.cb_scale,
                      self.cb_rotate, self.cb_flip, self.ed_crop):
                hide(w, False)

    def _cv(self, combo: QComboBox) -> str:
        """安全读取下拉框的“值”。

        为什么不能写成 `combo.currentData() or combo.currentText()`：
            Qt 的 currentData() 在两种情况下都可能为空——
              (a) 该项压根没有设置 itemData（返回 None）——此时应当回退到显示文本；
              (b) 该项设置了 itemData 且恰好是空字符串（返回 ""）——此时**不能**回退到显示文本。
            形如「自动（按容器决定）」的选项正是情况 (b)：它的 data 是 ""，表示“让内核自动决定”。
            如果用 `or` 直接把空串当成假值，就会把中文显示文本当成编码器名传给 ffmpeg，
            从而出现 `Unknown encoder '自动（按容器决定）'` 这类错误。
        因此这里必须显式区分 None 与 ""。
        """
        data = combo.currentData()
        # 仅在“未设置 itemData”时才使用显示文本（支持可编辑下拉框的手工输入）。
        if data is None:
            return str(combo.currentText()).strip()
        return str(data).strip()

    def collect(self) -> command_builder.TranscodeSettings:
        """把界面上的值收集为一个 TranscodeSettings。"""
        # 逐项取值；对文本字段做 strip 处理，避免首尾空格带来的隐性错误。
        st = command_builder.TranscodeSettings()
        # 模式决定目标格式的来源（三种模式共用同一控件）。
        st.target_format = self._cv(self.cb_format)
        st.preset_key = self._cv(self.cb_preset)
        st.quality = self._cv(self.cb_quality) or "medium"
        st.crf = str(self.sp_crf.value())
        st.v_bitrate = self.ed_vbr.text().strip()
        st.a_bitrate = self.ed_abr.text().strip()
        st.video_codec = self._cv(self.cb_vcodec)
        st.audio_codec = self._cv(self.cb_acodec)
        st.pixel_format = self._cv(self.cb_pixfmt)
        # 分辨率与缩放。
        st.width = int(self.sp_w.value())
        st.height = int(self.sp_h.value())
        st.scale_mode = self._cv(self.cb_scale) or "fit"
        # 帧率：可编辑下拉，取文本更直观（用户可能手填分数）。
        fps_text = self.cb_fps.currentText().strip()
        st.fps = "" if fps_text in ("保持原样", "") else fps_text
        # 旋转与镜像。
        st.rotate = int(self.cb_rotate.currentData() or 0)
        st.flip = self._cv(self.cb_flip) or "none"
        st.crop = self.ed_crop.text().strip()
        # 音频。
        st.audio_channels = int(self.cb_channels.currentData() or 0)
        st.sample_rate = int(self.cb_samplerate.currentData() or 0)
        st.volume = self.ed_volume.text().strip()
        # 时间。
        st.start = self.ed_start.text().strip()
        st.duration = self.ed_duration.text().strip()
        st.end = self.ed_end.text().strip()
        st.speed = float(self.sp_speed.value())
        # 水印与字幕。
        st.watermark_path = self.ed_wm_path.text().strip()
        st.watermark_text = self.ed_wm_text.text().strip()
        st.watermark_pos = self._cv(self.cb_wm_pos) or "tr"
        st.watermark_opacity = float(self.sp_wm_opacity.value())
        st.subtitle_path = self.ed_sub_path.text().strip()
        # 元数据。
        st.meta_title = self.ed_meta_title.text().strip()
        st.meta_artist = self.ed_meta_artist.text().strip()
        st.meta_album = self.ed_meta_album.text().strip()
        st.meta_comment = self.ed_meta_comment.text().strip()
        # 流选择。
        st.stream_mode = self._cv(self.cb_stream) or "auto"
        st.video_index = int(self.sp_vindex.value())
        st.audio_index = int(self.sp_aindex.value())
        st.subtitle_index = int(self.sp_sindex.value())
        st.drop_audio = bool(self.chk_drop_audio.isChecked())
        st.drop_video = bool(self.chk_drop_video.isChecked())
        st.copy_mode = bool(self.chk_copy.isChecked())
        # 输出。
        st.output_dir = self.ed_outdir.text().strip()
        st.overwrite = bool(self.chk_overwrite.isChecked())
        # 全局设置回填。
        st.threads = int(config.get("threads", 0) or 0)
        st.hwaccel = str(config.get("hwaccel", "none") or "none")
        st.hwaccel_vendor = str(config.get("hwaccel_vendor", "auto") or "auto")
        return st

    def name_mode(self) -> str:
        """返回当前选择的命名方式。"""
        return self._cv(self.cb_name) or "replace"


class DocumentPanel(QWidget):
    """文档转换面板：图片→PDF / PDF→图片 / 文本→PDF。"""

    def __init__(self, parent=None):
        """初始化界面。"""
        super().__init__(parent)
        v = QVBoxLayout(self)
        v.setContentsMargins(10, 10, 10, 10)
        v.setSpacing(10)

        # ---- 操作选择 ----
        g1 = QGroupBox("转换类型")
        gl = QVBoxLayout(g1)
        # 三个单选按钮构成互斥组。
        self.rb_img2pdf = QRadioButton("图片 → PDF（把多张图片合并为一个 PDF）")
        self.rb_pdf2img = QRadioButton("PDF → 图片（逐页导出为 PNG/JPG）")
        self.rb_txt2pdf = QRadioButton("文本 → PDF（把纯文本排版为 PDF）")
        # 默认选中第一个。
        self.rb_img2pdf.setChecked(True)
        # 互斥组。
        self.group = QButtonGroup(self)
        self.group.addButton(self.rb_img2pdf, 0)
        self.group.addButton(self.rb_pdf2img, 1)
        self.group.addButton(self.rb_txt2pdf, 2)
        for rb in (self.rb_img2pdf, self.rb_pdf2img, self.rb_txt2pdf):
            gl.addWidget(rb)
        v.addWidget(g1)

        # ---- 输入选择 ----
        g2 = QGroupBox("输入与输出")
        grid = QGridLayout(g2)
        grid.setColumnStretch(1, 1)
        # 图片列表（图片→PDF 用，支持多选与排序）。
        self.lst_images = QListWidget()
        self.lst_images.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.lst_images.setMaximumHeight(120)
        self.lst_images.setAlternatingRowColors(True)
        # 图片操作按钮。
        img_btns = QWidget()
        ib = QHBoxLayout(img_btns)
        ib.setContentsMargins(0, 0, 0, 0)
        self.btn_add_images = QPushButton("添加图片")
        self.btn_del_image = QPushButton("移除选中")
        self.btn_up_image = QPushButton("上移")
        self.btn_down_image = QPushButton("下移")
        for b in (self.btn_add_images, self.btn_del_image, self.btn_up_image, self.btn_down_image):
            ib.addWidget(b)
        ib.addStretch(1)
        # 单文件输入（PDF→图片 / 文本→PDF 用）。
        self.ed_input = QLineEdit()
        self.ed_input.setPlaceholderText("选择输入文件…")
        self.btn_input = QPushButton("浏览…")
        inp_wrap = QWidget()
        iw = QHBoxLayout(inp_wrap)
        iw.setContentsMargins(0, 0, 0, 0)
        iw.addWidget(self.ed_input, 1)
        iw.addWidget(self.btn_input, 0)
        # 输出。
        self.ed_output = QLineEdit()
        self.ed_output.setPlaceholderText("输出文件或目录…")
        self.btn_output = QPushButton("浏览…")
        outp_wrap = QWidget()
        ow = QHBoxLayout(outp_wrap)
        ow.setContentsMargins(0, 0, 0, 0)
        ow.addWidget(self.ed_output, 1)
        ow.addWidget(self.btn_output, 0)

        grid.addWidget(QLabel("图片列表"), 0, 0)
        grid.addWidget(self.lst_images, 0, 1)
        grid.addWidget(img_btns, 1, 1)
        grid.addWidget(QLabel("输入文件"), 2, 0)
        grid.addWidget(inp_wrap, 2, 1)
        grid.addWidget(QLabel("输出位置"), 3, 0)
        grid.addWidget(outp_wrap, 3, 1)
        v.addWidget(g2)

        # ---- 参数 ----
        g3 = QGroupBox("参数")
        pg = QGridLayout(g3)
        pg.setColumnStretch(1, 1)
        # DPI。
        self.sp_dpi = QSpinBox()
        self.sp_dpi.setRange(30, 600)
        self.sp_dpi.setValue(150)
        pg.addWidget(QLabel("DPI"), 0, 0)
        pg.addWidget(self.sp_dpi, 0, 1)
        # 图片格式（PDF→图片 用）。
        self.cb_img_fmt = QComboBox()
        self.cb_img_fmt.addItem("PNG", "png")
        self.cb_img_fmt.addItem("JPG", "jpg")
        pg.addWidget(QLabel("导出图片格式"), 1, 0)
        pg.addWidget(self.cb_img_fmt, 1, 1)
        # 页码范围。
        self.ed_pages = QLineEdit()
        self.ed_pages.setPlaceholderText("如 1-3,5；留空表示全部页")
        pg.addWidget(QLabel("页码范围"), 2, 0)
        pg.addWidget(self.ed_pages, 2, 1)
        # 文本→PDF 的字号与标题。
        self.sp_font = QSpinBox()
        self.sp_font.setRange(6, 48)
        self.sp_font.setValue(11)
        pg.addWidget(QLabel("正文字号"), 3, 0)
        pg.addWidget(self.sp_font, 3, 1)
        self.ed_title = QLineEdit()
        pg.addWidget(QLabel("文档标题"), 4, 0)
        pg.addWidget(self.ed_title, 4, 1)
        v.addWidget(g3)

        # ---- 执行 ----
        run_wrap = QWidget()
        rw = QHBoxLayout(run_wrap)
        rw.setContentsMargins(0, 0, 0, 0)
        self.btn_run_doc = QPushButton("开始转换")
        self.btn_run_doc.setProperty("role", "primary")
        rw.addWidget(self.btn_run_doc)
        rw.addStretch(1)
        v.addWidget(run_wrap)
        v.addStretch(1)

        # 事件绑定。
        self.btn_add_images.clicked.connect(self._add_images)
        self.btn_del_image.clicked.connect(self._remove_images)
        self.btn_up_image.clicked.connect(lambda: self._move_image(-1))
        self.btn_down_image.clicked.connect(lambda: self._move_image(1))
        self.btn_input.clicked.connect(self._pick_input)
        self.btn_output.clicked.connect(self._pick_output)
        self.btn_run_doc.clicked.connect(self.run_convert)

    def _add_images(self) -> None:
        """批量添加图片到列表。"""
        files, _ = QFileDialog.getOpenFileNames(self, "选择图片", "",
                                                "图片文件 (*.png *.jpg *.jpeg *.bmp *.webp *.tif *.tiff *.gif)")
        # 逐个去重后追加。
        existing = {self.lst_images.item(i).text() for i in range(self.lst_images.count())}
        for f in files:
            if f not in existing:
                self.lst_images.addItem(f)

    def _remove_images(self) -> None:
        """移除选中的图片项（从后往前删，避免索引错位）。"""
        rows = sorted({self.lst_images.row(i) for i in self.lst_images.selectedItems()}, reverse=True)
        for r in rows:
            self.lst_images.takeItem(r)

    def _move_image(self, delta: int) -> None:
        """上移/下移选中项，用于调整 PDF 页序。"""
        row = self.lst_images.currentRow()
        # 边界检查。
        if row < 0:
            return
        new_row = row + delta
        if new_row < 0 or new_row >= self.lst_images.count():
            return
        # 取出并重新插入。
        item = self.lst_images.takeItem(row)
        self.lst_images.insertItem(new_row, item)
        # 保持选中。
        self.lst_images.setCurrentRow(new_row)

    def _pick_input(self) -> None:
        """根据当前操作选择输入文件。"""
        op = self.group.checkedId()
        if op == 1:
            filt = "PDF 文件 (*.pdf)"
        elif op == 2:
            filt = "文本文件 (*.txt *.log *.md *.csv);;全部文件 (*.*)"
        else:
            filt = "图片文件 (*.png *.jpg *.jpeg *.bmp *.webp)"
        f, _ = QFileDialog.getOpenFileName(self, "选择输入文件", "", filt)
        if f:
            self.ed_input.setText(f)

    def _pick_output(self) -> None:
        """选择输出文件或目录。"""
        op = self.group.checkedId()
        if op == 1:
            # PDF→图片：输出是目录。
            d = QFileDialog.getExistingDirectory(self, "选择输出目录", "")
            if d:
                self.ed_output.setText(d)
        else:
            # 其余：输出是文件。
            f, _ = QFileDialog.getSaveFileName(self, "选择输出文件", "", "PDF 文件 (*.pdf)")
            if f:
                self.ed_output.setText(f)

    def run_convert(self) -> None:
        """执行所选文档转换，并把结果以弹窗反馈。"""
        op = self.group.checkedId()
        # 参数校验与执行。
        if op == 0:
            # 图片 → PDF。
            imgs = [self.lst_images.item(i).text() for i in range(self.lst_images.count())]
            out = self.ed_output.text().strip()
            if not imgs:
                QMessageBox.warning(self, S.Msg.DETAIL_TITLE, "请先添加至少一张图片。")
                return
            if not out:
                QMessageBox.warning(self, S.Msg.DETAIL_TITLE, "请选择输出 PDF 文件。")
                return
            res = doc_convert.images_to_pdf(imgs, out, dpi=int(self.sp_dpi.value()))
        elif op == 1:
            # PDF → 图片。
            inp = self.ed_input.text().strip()
            out_dir = self.ed_output.text().strip()
            if not inp or not out_dir:
                QMessageBox.warning(self, S.Msg.DETAIL_TITLE, "请选择输入 PDF 与输出目录。")
                return
            res = doc_convert.pdf_to_images(inp, out_dir, dpi=int(self.sp_dpi.value()),
                                            fmt=str(self.cb_img_fmt.currentData()),
                                            page_range=self.ed_pages.text().strip())
        else:
            # 文本 → PDF。
            inp = self.ed_input.text().strip()
            out = self.ed_output.text().strip()
            if not inp or not out:
                QMessageBox.warning(self, S.Msg.DETAIL_TITLE, "请选择输入文本与输出 PDF。")
                return
            res = doc_convert.text_to_pdf(inp, out, font_size=int(self.sp_font.value()),
                                          title=self.ed_title.text().strip())
        # 反馈结果。
        if res.ok:
            QMessageBox.information(self, "转换完成", res.message + "\n\n" + "\n".join(res.outputs[:10]))
        else:
            QMessageBox.critical(self, "转换失败", res.error)


class AdvancedPanel(QWidget):
    """高级面板：直接编辑自定义 ffmpeg 参数，并可预览最终命令。"""

    def __init__(self, parent=None):
        """初始化界面。"""
        super().__init__(parent)
        v = QVBoxLayout(self)
        v.setContentsMargins(10, 10, 10, 10)
        v.setSpacing(8)

        # 说明文字。
        tip = QLabel("每行一个参数，将追加到自动生成的命令末尾（放在输出文件之前），可覆盖前面的默认值。\n"
                     "示例：\n"
                     "-vf eq=contrast=1.1\n"
                     "-metadata comment=processed\n"
                     "-max_muxing_queue_size 1024")
        tip.setProperty("role", "dim")
        tip.setWordWrap(True)
        v.addWidget(tip)

        # 参数编辑区。
        self.ed_args = QPlainTextEdit()
        self.ed_args.setPlaceholderText("-vf scale=1280:-2\n-crf 22")
        v.addWidget(self.ed_args, 1)

        # 按钮行。
        btns = QWidget()
        bh = QHBoxLayout(btns)
        bh.setContentsMargins(0, 0, 0, 0)
        self.btn_preview = QPushButton("预览命令")
        self.btn_copy = QPushButton("复制命令")
        bh.addWidget(self.btn_preview)
        bh.addWidget(self.btn_copy)
        bh.addStretch(1)
        v.addWidget(btns)

        # 预览区。
        self.txt_preview = QTextBrowser()
        self.txt_preview.setMaximumHeight(160)
        v.addWidget(self.txt_preview)

        # 事件。
        self.btn_preview.clicked.connect(self._preview)
        self.btn_copy.clicked.connect(self._copy)

    def custom_args(self) -> List[str]:
        """把编辑区文本拆成参数列表（按行拆分，忽略空行）。"""
        text = self.ed_args.toPlainText()
        return [ln.strip() for ln in text.splitlines() if ln.strip()]

    def _preview(self) -> None:
        """预览当前参数与首个待转文件组合出的命令。"""
        # 需要主窗口提供“当前选中设置”，这里通过向上查找主窗口获取。
        win = self.window()
        if not isinstance(win, MainWindow):
            return
        st = win.current_settings()
        # 若没有待转文件，给出一段占位说明。
        if not st.source:
            self.txt_preview.setPlainText("请先在左侧添加至少一个文件，再预览命令。")
            return
        # 追加自定义参数后再构建命令。
        st.custom_args = self.custom_args()
        info = media_info.probe(st.source)
        out_path = command_builder.compute_output_path(st, mode=win.name_mode(),
                                                       overwrite=st.overwrite)
        args = command_builder.build_command(st, info, out_path=out_path)
        self.txt_preview.setPlainText(command_builder.describe_command(args))

    def _copy(self) -> None:
        """复制预览文本到剪贴板。"""
        QApplication.clipboard().setText(self.txt_preview.toPlainText() or "")


class SettingsTab(QWidget):
    """设置页：全局配置项 + 内核检测。"""

    def __init__(self, parent=None):
        """初始化界面。"""
        super().__init__(parent)
        v = QVBoxLayout(self)
        v.setContentsMargins(10, 10, 10, 10)
        v.setSpacing(10)

        # ---- 内核 ----
        g1 = QGroupBox("转码内核（FFmpeg）")
        gl = QGridLayout(g1)
        gl.setColumnStretch(1, 1)
        # 内核路径显示（只读）。
        self.ed_ffmpeg = QLineEdit()
        self.ed_ffmpeg.setReadOnly(True)
        # 自定义内核路径。
        self.ed_custom_ffmpeg = QLineEdit()
        self.ed_custom_ffmpeg.setPlaceholderText("留空则自动查找随包副本；可填写自定义 ffmpeg.exe 路径")
        self.btn_custom_ffmpeg = QPushButton("浏览…")
        cf_wrap = QWidget()
        ch = QHBoxLayout(cf_wrap)
        ch.setContentsMargins(0, 0, 0, 0)
        ch.addWidget(self.ed_custom_ffmpeg, 1)
        ch.addWidget(self.btn_custom_ffmpeg, 0)
        # 检测按钮。
        self.btn_detect = QPushButton("重新检测内核")
        # 版本显示。
        self.lbl_version = QLabel("")
        self.lbl_version.setProperty("role", "dim")
        self.lbl_version.setWordWrap(True)
        # 能力统计显示。
        self.lbl_caps = QLabel("")
        self.lbl_caps.setProperty("role", "dim")
        self.lbl_caps.setWordWrap(True)
        gl.addWidget(QLabel("当前内核"), 0, 0)
        gl.addWidget(self.ed_ffmpeg, 0, 1)
        gl.addWidget(QLabel("自定义内核"), 1, 0)
        gl.addWidget(cf_wrap, 1, 1)
        gl.addWidget(QLabel("版本信息"), 2, 0)
        gl.addWidget(self.lbl_version, 2, 1)
        gl.addWidget(QLabel("能力统计"), 3, 0)
        gl.addWidget(self.lbl_caps, 3, 1)
        gl.addWidget(self.btn_detect, 4, 1)
        v.addWidget(g1)

        # ---- 运行参数 ----
        g2 = QGroupBox("运行参数")
        f = QFormLayout(g2)
        # 主题。
        self.cb_theme = QComboBox()
        self.cb_theme.addItem("深色", "dark")
        self.cb_theme.addItem("浅色", "light")
        f.addRow("界面主题", self.cb_theme)
        # 并发数。
        self.sp_parallel = QSpinBox()
        self.sp_parallel.setRange(1, 16)
        f.addRow("并发任务数", self.sp_parallel)
        # 线程数。
        self.sp_threads = QSpinBox()
        self.sp_threads.setRange(0, 64)
        self.sp_threads.setSpecialValueText("自动")
        f.addRow("编码线程数", self.sp_threads)
        # 硬件加速策略。
        self.cb_hwaccel = QComboBox()
        self.cb_hwaccel.addItem("关闭（默认，最稳）", "none")
        self.cb_hwaccel.addItem("自动选择硬件编码器", "auto")
        f.addRow("硬件加速", self.cb_hwaccel)
        # 厂商偏好。
        self.cb_vendor = QComboBox()
        for text, val in [("自动", "auto"), ("NVIDIA", "nvidia"), ("Intel", "intel"), ("AMD", "amd")]:
            self.cb_vendor.addItem(text, val)
        f.addRow("硬件厂商偏好", self.cb_vendor)
        # 快照张数。
        self.sp_snap = QSpinBox()
        self.sp_snap.setRange(1, 50)
        f.addRow("快照默认张数", self.sp_snap)
        # 日志级别。
        self.cb_loglevel = QComboBox()
        for lv in ("DEBUG", "INFO", "WARNING", "ERROR"):
            self.cb_loglevel.addItem(lv, lv)
        f.addRow("日志级别", self.cb_loglevel)
        # 完成后打开输出目录。
        self.chk_autoopen = QCheckBox("全部完成后自动打开输出目录")
        f.addRow("完成后行为", self.chk_autoopen)
        v.addWidget(g2)

        # ---- Web 端 ----
        g3 = QGroupBox("Web 端")
        f3 = QFormLayout(g3)
        self.ed_host = QLineEdit()
        f3.addRow("监听地址", self.ed_host)
        self.sp_port = QSpinBox()
        self.sp_port.setRange(1024, 65535)
        f3.addRow("监听端口", self.sp_port)
        v.addWidget(g3)

        # ---- 保存 ----
        save_wrap = QWidget()
        sh = QHBoxLayout(save_wrap)
        sh.setContentsMargins(0, 0, 0, 0)
        self.btn_save = QPushButton("保存设置")
        self.btn_save.setProperty("role", "primary")
        self.btn_reset = QPushButton("恢复默认")
        sh.addWidget(self.btn_save)
        sh.addWidget(self.btn_reset)
        sh.addStretch(1)
        v.addWidget(save_wrap)
        v.addStretch(1)

        # 事件。
        self.btn_custom_ffmpeg.clicked.connect(self._pick_custom_ffmpeg)
        self.btn_detect.clicked.connect(self.refresh_kernel)
        self.btn_save.clicked.connect(self.save_settings)
        self.btn_reset.clicked.connect(self.reset_settings)

        # 载入当前配置。
        self.load_settings()
        # 刷新内核信息。
        self.refresh_kernel()

    def _pick_custom_ffmpeg(self) -> None:
        """选择自定义 ffmpeg 可执行文件。"""
        f, _ = QFileDialog.getOpenFileName(self, "选择 ffmpeg 可执行文件", "",
                                           "可执行文件 (*.exe);;全部文件 (*.*)")
        if f:
            self.ed_custom_ffmpeg.setText(f)

    def refresh_kernel(self) -> None:
        """重新检测内核并刷新显示。"""
        # 若用户填写了自定义路径，先应用它并清空缓存，再重新定位。
        custom = self.ed_custom_ffmpeg.text().strip()
        if custom != str(config.get("ffmpeg_custom_path", "") or ""):
            config.set("ffmpeg_custom_path", custom)
            config.save()
            ffmpeg_locator.clear_cache()
            capabilities.clear_cache()
            hwaccel.clear_cache()
        # 展示定位结果。
        self.ed_ffmpeg.setText(ffmpeg_locator.ffmpeg_path() or "未找到")
        self.lbl_version.setText(ffmpeg_locator.version_text())
        # 展示能力统计。
        cap = capabilities.summary()
        hw = [x["name"] for x in hwaccel.available_detail()]
        self.lbl_caps.setText(
            f"视频编码器 {cap['video_encoders']} 个 · 音频编码器 {cap['audio_encoders']} 个 · "
            f"封装格式 {cap['format_total']} 个 · 硬件编码器 {len(hw)} 个\n"
            f"可用硬件编码器：{', '.join(hw) if hw else '无'}"
        )

    def load_settings(self) -> None:
        """从配置载入到表单控件。"""

        # 设置下拉与输入框的当前值。
        def set_combo_data(combo, value):
            """按数据值选中下拉项；找不到则不改变。"""
            idx = combo.findData(value)
            if idx >= 0:
                combo.setCurrentIndex(idx)

        set_combo_data(self.cb_theme, config.get("theme", "dark"))
        self.sp_parallel.setValue(int(config.get("max_parallel", 1) or 1))
        self.sp_threads.setValue(int(config.get("threads", 0) or 0))
        set_combo_data(self.cb_hwaccel, config.get("hwaccel", "none"))
        set_combo_data(self.cb_vendor, config.get("hwaccel_vendor", "auto"))
        self.sp_snap.setValue(int(config.get("snapshot_count", 5) or 5))
        set_combo_data(self.cb_loglevel, config.get("log_level", "INFO"))
        self.chk_autoopen.setChecked(bool(config.get("auto_open_output", False)))
        self.ed_host.setText(str(config.get("web_host", "127.0.0.1")))
        self.sp_port.setValue(int(config.get("web_port", 8765) or 8765))
        self.ed_custom_ffmpeg.setText(str(config.get("ffmpeg_custom_path", "") or ""))

    def save_settings(self) -> None:
        """把表单值写回配置并落盘。"""
        config.update({
            "theme": self.cb_theme.currentData(),
            "max_parallel": int(self.sp_parallel.value()),
            "threads": int(self.sp_threads.value()),
            "hwaccel": self.cb_hwaccel.currentData(),
            "hwaccel_vendor": self.cb_vendor.currentData(),
            "snapshot_count": int(self.sp_snap.value()),
            "log_level": self.cb_loglevel.currentData(),
            "auto_open_output": bool(self.chk_autoopen.isChecked()),
            "web_host": self.ed_host.text().strip() or "127.0.0.1",
            "web_port": int(self.sp_port.value()),
            "ffmpeg_custom_path": self.ed_custom_ffmpeg.text().strip(),
        })
        config.save()
        # 提示保存成功。
        QMessageBox.information(self, "设置", "配置已保存。部分设置（如并发数、主题）将在重启后完全生效。")

    def reset_settings(self) -> None:
        """恢复默认配置。"""
        # 二次确认，避免误触清空用户配置。
        if QMessageBox.question(self, "恢复默认", "确定要把所有设置恢复为默认值吗？") != QMessageBox.StandardButton.Yes:
            return
        config.reset()
        config.save()
        self.load_settings()
        QMessageBox.information(self, "恢复默认", "已恢复默认设置。")


class MainWindow(QMainWindow):
    """主窗口。"""

    # ---- 跨线程信号（内核回调 → GUI 主线程）----
    sig_progress = pyqtSignal(object)  # 参数：Task
    sig_state = pyqtSignal(object)  # 参数：Task
    sig_finished = pyqtSignal()  # 无参数

    def __init__(self):
        """初始化主窗口。"""
        super().__init__()
        # 设置窗口标题与最小尺寸。
        self.setWindowTitle(S.Win.MAIN_TITLE)
        self.setMinimumSize(QSize(1080, 720))
        # 任务队列：并发数取配置值。
        self.queue = TaskQueue(max_parallel=int(config.get("max_parallel", 1) or 1))
        # tid -> (表格行号, 进度条控件) 的映射，便于更新进度。
        self._row_map: Dict[str, tuple] = {}
        # 构建界面。
        self._build_ui()
        # 连接内核回调到 Qt 信号（跨线程安全）。
        self._wire_queue()
        # 首次启动刷新一次状态栏。
        self._update_status("就绪")

    # ---------------- 界面构建 ----------------
    def _build_ui(self) -> None:
        """构建整个界面。"""
        # 中央控件与主纵向布局。
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(9, 9, 9, 9)
        root.setSpacing(9)

        # ===== 工具栏 =====
        bar = QWidget()
        bh = QHBoxLayout(bar)
        bh.setContentsMargins(0, 0, 0, 0)
        bh.setSpacing(7)
        # 文件操作按钮。
        self.btn_add_files = QPushButton("添加文件")
        self.btn_add_dir = QPushButton("添加文件夹")
        self.btn_remove = QPushButton("移除选中")
        self.btn_clear = QPushButton("清空列表")
        self.btn_clear.setProperty("role", "danger")
        # 队列控制按钮。
        self.btn_start = QPushButton("开始")
        self.btn_start.setProperty("role", "primary")
        self.btn_pause = QPushButton("暂停")
        self.btn_stop = QPushButton("停止")
        self.btn_stop.setProperty("role", "danger")
        # Web 端按钮。
        self.btn_web = QPushButton("启动 Web 端")
        # Web 自动打开浏览器的开关。
        self.chk_web_open = QCheckBox("自动打开浏览器")
        self.chk_web_open.setChecked(bool(config.get("web_auto_open", True)))
        # 依次加入工具栏。
        for w in (self.btn_add_files, self.btn_add_dir, self.btn_remove, self.btn_clear):
            bh.addWidget(w)
        # 分隔线。
        bh.addWidget(self._vline())
        for w in (self.btn_start, self.btn_pause, self.btn_stop):
            bh.addWidget(w)
        bh.addWidget(self._vline())
        bh.addWidget(self.btn_web)
        bh.addWidget(self.chk_web_open)
        bh.addStretch(1)
        root.addWidget(bar)

        # ===== 主体：左右分栏 =====
        splitter = QSplitter(Qt.Orientation.Horizontal)
        # 左：待转文件列表。
        left = QGroupBox("待转换文件")
        lv = QVBoxLayout(left)
        self.list_files = DropListWidget()
        lv.addWidget(self.list_files)
        # 左侧小提示。
        tip = QLabel("支持直接把文件或文件夹拖进来")
        tip.setProperty("role", "hint")
        lv.addWidget(tip)
        splitter.addWidget(left)
        # 右：参数标签页。
        self.tabs = QTabWidget()
        # 三个媒体面板（各自独立实例，切换标签时读取对应面板）。
        self.panel_video = SettingsPanel("video")
        self.panel_audio = SettingsPanel("audio")
        self.panel_image = SettingsPanel("image")
        self.panel_doc = DocumentPanel()
        self.panel_adv = AdvancedPanel()
        self.tab_settings = SettingsTab()
        # 关于页。
        self.tab_about = QTextBrowser()
        self.tab_about.setOpenExternalLinks(True)
        self.tab_about.setHtml(self._about_html())
        # 依次加入标签页。
        self.tabs.addTab(self.panel_video, S.Win.TAB_VIDEO)
        self.tabs.addTab(self.panel_audio, S.Win.TAB_AUDIO)
        self.tabs.addTab(self.panel_image, S.Win.TAB_IMAGE)
        self.tabs.addTab(self.panel_doc, S.Win.TAB_DOC)
        self.tabs.addTab(self.panel_adv, S.Win.TAB_ADVANCED)
        self.tabs.addTab(self.tab_settings, S.Win.TAB_SETTINGS)
        self.tabs.addTab(self.tab_about, S.Win.TAB_ABOUT)
        splitter.addWidget(self.tabs)
        # 初始分栏比例：左窄右宽。
        splitter.setSizes([330, 750])
        root.addWidget(splitter, 1)

        # ===== 任务队列 =====
        qbox = QGroupBox("任务队列")
        qv = QVBoxLayout(qbox)
        # 表格。
        self.table = QTableWidget(0, 9)
        self.table.setHorizontalHeaderLabels([
            "#", "文件", "目标格式", "状态", "进度", "速度", "剩余时间", "体积变化", "错误",
        ])
        # 表格行为设置。
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setAlternatingRowColors(True)
        self.table.verticalHeader().setVisible(False)
        # 列宽策略：文件名与错误列自适应，其余按内容。
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        header.setSectionResizeMode(8, QHeaderView.ResizeMode.Stretch)
        # 双击行查看详情。
        self.table.itemDoubleClicked.connect(self._show_task_detail)
        qv.addWidget(self.table, 1)
        # 总进度条与汇总标签。
        bottom = QWidget()
        bl = QHBoxLayout(bottom)
        bl.setContentsMargins(0, 0, 0, 0)
        self.prog_total = QProgressBar()
        self.prog_total.setProperty("role", "total")
        self.lbl_summary = QLabel("")
        self.lbl_summary.setProperty("role", "dim")
        bl.addWidget(self.prog_total, 1)
        bl.addWidget(self.lbl_summary, 0)
        qv.addWidget(bottom)
        root.addWidget(qbox, 0)

        # ===== 状态栏 =====
        self.status_label = QLabel("")
        self.statusBar().addWidget(self.status_label, 1)
        # 内核状态指示（右侧）。
        self.kernel_label = QLabel("")
        self.statusBar().addPermanentWidget(self.kernel_label)
        # 刷新内核指示。
        self._refresh_kernel_indicator()

        # ===== 事件绑定 =====
        self.btn_add_files.clicked.connect(self.add_files_dialog)
        self.btn_add_dir.clicked.connect(self.add_dir_dialog)
        self.btn_remove.clicked.connect(self.remove_selected)
        self.btn_clear.clicked.connect(self.clear_files)
        self.btn_start.clicked.connect(self.start_queue)
        self.btn_pause.clicked.connect(self.toggle_pause)
        self.btn_stop.clicked.connect(self.stop_queue)
        self.btn_web.clicked.connect(self.start_web)
        self.chk_web_open.stateChanged.connect(
            lambda: (config.set("web_auto_open", bool(self.chk_web_open.isChecked())), config.save()))
        # 拖入文件。
        self.list_files.files_dropped.connect(self.add_paths)
        # 标签页切换时刷新右侧面板模式（视频/音频/图片各自独立，无需切换；此处仅用于状态栏提示）。
        self.tabs.currentChanged.connect(self._on_tab_changed)

    def _vline(self) -> QFrame:
        """创建一条竖直分隔线，用于工具栏分组。"""
        line = QFrame()
        line.setFrameShape(QFrame.Shape.VLine)
        # 用较暗的颜色，避免视觉噪声。
        line.setStyleSheet("color: #26344a;")
        return line

    def _about_html(self) -> str:
        """生成关于页的 HTML 内容。"""
        # 收集能力信息用于展示。
        cap = capabilities.summary()
        hw = hwaccel.available_detail()
        # 硬件列表转成可读文本。
        hw_text = "、".join(x["label"] for x in hw) if hw else "未检测到可用硬件编码器"
        return f"""
        <div style="line-height:1.85">
        <h2 style="color:#dbe4f0;margin-bottom:4px">{S.APP_NAME}
            <span style="font-size:13px;color:#8fa3bd">v{S.APP_VERSION}</span></h2>
        <p style="color:#3aa0ff;margin-top:0">{S.APP_SLOGAN}</p>
        <p><b>作者：</b>{S.APP_AUTHOR}</p>
        <p style="color:#8fa3bd">{S.APP_COPYRIGHT}</p>
        <hr style="border:none;border-top:1px solid #26344a">
        <p style="color:#8fa3bd">
        本项目是一款<b>完全离线</b>的通用格式转换工具，覆盖视频、音频、图片、文档四大类转换能力。<br>
        转码内核为 FFmpeg，界面提供 <b>PyQt 桌面端</b> 与 <b>浏览器 Web 端</b> 两种形态；<br>
        两者共用同一套内核逻辑，因此功能与行为完全一致。
        </p>
        <hr style="border:none;border-top:1px solid #26344a">
        <p><b>内核：</b>{ffmpeg_locator.version_text()}</p>
        <p><b>能力：</b>视频编码器 {cap['video_encoders']} 个 ·
           音频编码器 {cap['audio_encoders']} 个 ·
           封装格式 {cap['format_total']} 个</p>
        <p><b>硬件加速：</b>{hw_text}</p>
        <hr style="border:none;border-top:1px solid #26344a">
        <p style="color:#8fa3bd;font-size:12px">
        说明：本软件不修改源文件，所有输出均另存为新文件；<br>
        转换结果受源文件质量与所选参数影响，请在使用前保留原始素材。
        </p>
        </div>
        """

    # ---------------- 内核回调接线 ----------------
    def _wire_queue(self) -> None:
        """把内核队列回调连接到 Qt 信号。

        为什么这样做：内核回调运行在工作线程，直接操作控件会崩溃；
        通过 pyqtSignal.emit 发射后，Qt 会自动把槽函数调度到 GUI 主线程执行。
        """
        # 进度回调：只发任务对象（进度信息已写回任务上）。
        self.queue.on_progress(lambda task, prog: self.sig_progress.emit(task))
        # 状态回调。
        self.queue.on_state(lambda task: self.sig_state.emit(task))
        # 全部结束回调。
        self.queue.on_all_finished(lambda: self.sig_finished.emit())
        # 连接到实际槽函数（默认队列连接即跨线程安全）。
        self.sig_progress.connect(self._on_task_progress)
        self.sig_state.connect(self._on_task_state)
        self.sig_finished.connect(self._on_all_finished)

    # ---------------- 文件列表操作 ----------------
    def add_files_dialog(self) -> None:
        """弹出文件选择对话框并添加文件。"""
        files, _ = QFileDialog.getOpenFileNames(
            self, "添加待转换文件", str(config.get("last_input_dir", "") or ""),
            "媒体文件 (*.mp4 *.mkv *.avi *.mov *.flv *.wmv *.webm *.ts *.mpg *.3gp *.m4v *.gif "
            "*.mp3 *.wav *.flac *.aac *.m4a *.ogg *.opus *.wma *.ac3 *.amr "
            "*.jpg *.jpeg *.png *.bmp *.webp *.tif *.tiff *.avif *.ico *.pdf *.txt);;全部文件 (*.*)")
        if files:
            # 记忆目录。
            config.set("last_input_dir", os.path.dirname(files[0]))
            config.save()
            self.add_paths(files)

    def add_dir_dialog(self) -> None:
        """选择文件夹并递归添加其中的媒体文件。"""
        d = QFileDialog.getExistingDirectory(self, "添加文件夹", str(config.get("last_input_dir", "") or ""))
        if d:
            config.set("last_input_dir", d)
            config.save()
            self.add_paths([d])

    def add_paths(self, paths_in: List[str]) -> None:
        """把路径（文件或目录）展开为文件列表并加入左侧列表。

        规则：
            - 目录会递归展开，只收集扩展名在白名单内的文件；
            - 已在列表中的路径自动跳过，避免重复。
        """
        # 允许的扩展名集合。
        exts = {
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".webm", ".ts", ".mpg", ".3gp", ".m4v", ".gif",
            ".mp3", ".wav", ".flac", ".aac", ".m4a", ".ogg", ".opus", ".wma", ".ac3", ".amr", ".aiff",
            ".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff", ".avif", ".ico",
            ".pdf", ".txt",
        }
        # 收集结果。
        collected = []
        for p in paths_in:
            if os.path.isdir(p):
                # 递归遍历目录。
                for root, _dirs, files in os.walk(p):
                    for name in files:
                        if os.path.splitext(name)[1].lower() in exts:
                            collected.append(os.path.join(root, name))
            elif os.path.isfile(p):
                # 单个文件不做扩展名过滤（用户显式选择的文件应当尊重）。
                collected.append(p)
        # 去重并追加到列表。
        existing = {self.list_files.item(i).text() for i in range(self.list_files.count())}
        added = 0
        for f in collected:
            if f not in existing:
                item = QListWidgetItem(f)
                # 悬停显示完整路径。
                item.setToolTip(f)
                self.list_files.addItem(item)
                existing.add(f)
                added += 1
        # 反馈。
        self._update_status(
            f"已添加 {added} 个文件" + (f"（跳过 {len(collected) - added} 个重复）" if len(collected) > added else ""))
        if added == 0:
            QMessageBox.information(self, "提示", "没有找到可转换的文件，或所选文件已在列表中。")

    def remove_selected(self) -> None:
        """移除左侧列表中选中的文件（从后往前删）。"""
        rows = sorted({self.list_files.row(i) for i in self.list_files.selectedItems()}, reverse=True)
        for r in rows:
            self.list_files.takeItem(r)

    def clear_files(self) -> None:
        """清空左侧文件列表。"""
        # 少量条目无需确认，直接清空更顺手。
        self.list_files.clear()
        self._update_status("已清空待转文件列表")

    def selected_files(self) -> List[str]:
        """返回左侧列表中全部文件的路径。"""
        return [self.list_files.item(i).text() for i in range(self.list_files.count())]

    # ---------------- 参数收集 ----------------
    def _active_panel(self) -> Optional[SettingsPanel]:
        """返回当前标签页对应的参数面板（非视频/音频/图片页返回 None）。"""
        idx = self.tabs.currentIndex()
        if idx == 0:
            return self.panel_video
        if idx == 1:
            return self.panel_audio
        if idx == 2:
            return self.panel_image
        return None

    def current_settings(self, for_source: str = "") -> command_builder.TranscodeSettings:
        """收集当前界面上的设置。

        参数:
            for_source: 指定源文件路径；空则取列表首个文件。
        """
        # 取当前面板；若在文档/高级/设置页，则默认用视频面板，保证“开始”始终有合理的参数。
        panel = self._active_panel() or self.panel_video
        st = panel.collect()
        # 源文件。
        files = self.selected_files()
        st.source = for_source or (files[0] if files else "")
        # 合并高级页的自定义参数。
        st.custom_args = self.panel_adv.custom_args()
        return st

    def name_mode(self) -> str:
        """当前命名方式（来自当前面板）。"""
        panel = self._active_panel() or self.panel_video
        return panel.name_mode()

    # ---------------- 队列操作 ----------------
    def start_queue(self) -> None:
        """把左侧列表的文件按当前设置加入队列并开始执行。"""
        # 内核检查。
        if not ffmpeg_locator.is_ready():
            QMessageBox.critical(self, "内核不可用", S.Msg.FFMPEG_MISSING)
            return
        files = self.selected_files()
        if not files:
            QMessageBox.warning(self, "提示", S.Msg.NO_INPUT)
            return
        # 取一份公共设置作为模板。
        base = self.current_settings(for_source=files[0])
        if not base.target_format:
            QMessageBox.warning(self, "提示", S.Msg.NO_FORMAT)
            return
        # 校验输出目录（若用户填了）。
        if base.output_dir and not os.path.isdir(base.output_dir):
            # 尝试创建；失败则提示。
            try:
                os.makedirs(base.output_dir, exist_ok=True)
            except OSError as exc:
                QMessageBox.warning(self, "输出目录不可用",
                                    S.Msg.DIR_NOT_EXIST.format(path=base.output_dir) + f"\n{exc}")
                return
        # 逐文件创建任务。
        added = 0
        skipped = 0
        for f in files:
            st = self.current_settings(for_source=f)
            # 同步并发数（用户可能在设置页改过）。
            t = self.queue.add(st)
            if t:
                # 为该任务插入表格行。
                self._append_row(t)
                added += 1
            else:
                skipped += 1
        # 反馈。
        self._update_status(f"已加入 {added} 个任务" + (f"，跳过 {skipped} 个重复" if skipped else ""))
        # 启动调度。
        self.queue.set_max_parallel(int(config.get("max_parallel", 1) or 1))
        self.queue.start()
        # 按钮状态更新。
        self._set_running_ui(True)

    def toggle_pause(self) -> None:
        """暂停/继续切换。"""
        # 当前是否在调度中。
        if self.queue.is_running():
            self.queue.pause()
            self.btn_pause.setText("继续")
            self._update_status("已暂停：当前正在执行的任务会继续完成，新任务不再开始")
        else:
            self.queue.resume()
            self.btn_pause.setText("暂停")
            self._update_status("已继续")

    def stop_queue(self) -> None:
        """停止当前任务并终止调度（需确认）。"""
        # 二次确认，避免误触中断长任务。
        if QMessageBox.question(self, "停止", S.Msg.CONFIRM_STOP) != QMessageBox.StandardButton.Yes:
            return
        self.queue.stop_all()
        self._set_running_ui(False)
        self._update_status("已停止")

    def _append_row(self, task: Task) -> None:
        """在队列表格中插入一行。

        参数:
            task: 任务对象。
        """
        # 行号取当前行数。
        row = self.table.rowCount()
        self.table.insertRow(row)
        # 序号列。
        self.table.setItem(row, 0, QTableWidgetItem(str(row + 1)))
        # 文件名列（悬停显示完整路径）。
        name_item = QTableWidgetItem(task.display_name)
        name_item.setToolTip(task.source)
        self.table.setItem(row, 1, name_item)
        # 目标格式。
        self.table.setItem(row, 2, QTableWidgetItem(task.settings.target_format))
        # 状态。
        self.table.setItem(row, 3, QTableWidgetItem(task.status_text))
        # 进度：使用单元格内的进度条控件。
        bar = QProgressBar()
        bar.setRange(0, 100)
        bar.setValue(0)
        # 显示百分比文本。
        bar.setFormat("%p%")
        self.table.setCellWidget(row, 4, bar)
        # 速度、剩余、体积、错误（先置空）。
        for col in (5, 6, 7, 8):
            self.table.setItem(row, col, QTableWidgetItem(""))
        # 记录映射。
        self._row_map[task.tid] = (row, bar)
        # 滚动到最新行。
        self.table.scrollToBottom()

    def _find_row(self, tid: str) -> Optional[int]:
        """按任务 ID 找表格行号。"""
        pair = self._row_map.get(tid)
        return pair[0] if pair else None

    # ---------------- 内核回调的槽函数（GUI 主线程执行） ----------------
    def _on_task_progress(self, task: Task) -> None:
        """进度更新：刷新对应行的进度条与速度/剩余时间。"""
        row = self._find_row(task.tid)
        if row is None:
            return
        pair = self._row_map.get(task.tid)
        # 更新进度条。
        if pair and pair[1]:
            pair[1].setValue(int(task.progress))
        # 速度列。
        self.table.setItem(row, 5, QTableWidgetItem(f"{task.speed:.2f}x" if task.speed else ""))
        # 剩余时间列。
        eta_text = fmt.human_duration(task.eta) if task.eta and task.eta > 0 else S.Queue.REMAIN_UNKNOWN
        self.table.setItem(row, 6, QTableWidgetItem(eta_text))
        # 汇总进度。
        self._update_total_progress()

    def _on_task_state(self, task: Task) -> None:
        """状态更新：刷新状态列、体积列、错误列。"""
        row = self._find_row(task.tid)
        if row is None:
            return
        # 状态文本。
        self.table.setItem(row, 3, QTableWidgetItem(task.status_text))
        # 体积变化列。
        vol_text = ""
        if task.out_size or task.in_size:
            vol_text = f"{fmt.human_size(task.in_size)} → {fmt.human_size(task.out_size)}"
        self.table.setItem(row, 7, QTableWidgetItem(vol_text))
        # 错误列（仅失败时显示短标题，悬停显示建议）。
        err_item = QTableWidgetItem(task.err_title if task.status == TaskStatus.FAILED else "")
        if task.err_advice:
            err_item.setToolTip(task.err_advice)
        self.table.setItem(row, 8, err_item)
        # 完成时把进度条置满。
        if task.status == TaskStatus.DONE:
            pair = self._row_map.get(task.tid)
            if pair and pair[1]:
                pair[1].setValue(100)
        # 汇总。
        self._update_total_progress()
        self._update_summary()

    def _on_all_finished(self) -> None:
        """全部任务结束：恢复按钮状态、按配置打开输出目录。"""
        self._set_running_ui(False)
        stats = self.queue.stats()
        self._update_status(S.Queue.SUMMARY.format(total=stats["total"], ok=stats["ok"],
                                                   fail=stats["fail"], skip=stats["skip"]))
        # 若配置要求，则在有输出时自动打开输出目录。
        if config.get("auto_open_output", False):
            # 取第一个已完成任务所在目录。
            for t in self.queue.tasks():
                if t.status == TaskStatus.DONE and t.out_path:
                    self._open_dir(os.path.dirname(t.out_path))
                    break

    def _show_task_detail(self, item: QTableWidgetItem) -> None:
        """双击某行查看该任务的详细信息（含错误详情与命令）。"""
        row = item.row()
        # 找到该行对应的任务。
        target_tid = None
        for tid, (r, _bar) in self._row_map.items():
            if r == row:
                target_tid = tid
                break
        if not target_tid:
            return
        task = self.queue.get(target_tid)
        if not task:
            return
        # 生成详情文本。
        lines = [
            f"文件：{task.source}",
            f"目标格式：{task.settings.target_format}",
            f"状态：{task.status_text}",
            f"进度：{task.progress:.1f}%",
            f"耗时：{task.elapsed:.2f} 秒",
            f"输出：{task.out_path or '（尚未生成）'}",
            f"体积：{fmt.human_size(task.in_size)} → {fmt.human_size(task.out_size)}",
        ]
        # 若失败，附上错误信息与建议。
        if task.status == TaskStatus.FAILED:
            lines += [
                "",
                f"错误代码：{task.err_code}",
                f"错误标题：{task.err_title}",
                f"处理建议：{task.err_advice}",
                "",
                "原始错误信息：",
                task.err_detail or "（无）",
            ]
        # 弹窗展示（可复制）。
        dlg = QDialog(self)
        dlg.setWindowTitle(S.Msg.DETAIL_TITLE)
        dlg.resize(720, 460)
        dv = QVBoxLayout(dlg)
        browser = QTextBrowser()
        browser.setPlainText("\n".join(lines))
        dv.addWidget(browser)
        bb = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        bb.rejected.connect(dlg.reject)
        dv.addWidget(bb)
        dlg.exec()

    def _open_dir(self, path: str) -> None:
        """用系统文件管理器打开目录。"""
        try:
            QDesktopServices.openUrl(QUrl.fromLocalFile(path))
        except Exception as exc:
            QMessageBox.warning(self, "打开目录失败", str(exc))

    def _update_total_progress(self) -> None:
        """刷新总进度条（按任务数加权平均）。"""
        tasks = self.queue.tasks()
        if not tasks:
            self.prog_total.setValue(0)
            return
        # 已完成任务按 100 计入，其余按自身进度。
        total = 0.0
        for t in tasks:
            total += 100.0 if t.is_finished else float(t.progress)
        self.prog_total.setValue(int(total / len(tasks)))

    def _update_summary(self) -> None:
        """刷新队列汇总标签。"""
        s = self.queue.stats()
        self.lbl_summary.setText(
            f"共 {s['total']} 个任务 · 成功 {s['ok']} · 失败 {s['fail']} · 跳过 {s['skip']} · 取消 {s['canceled']}"
        )

    def _set_running_ui(self, running: bool) -> None:
        """根据是否在运行，切换按钮可用状态。"""
        self.btn_start.setEnabled(not running)
        self.btn_stop.setEnabled(running)
        self.btn_pause.setEnabled(running)
        self.btn_pause.setText("暂停")
        self.btn_add_files.setEnabled(not running)
        self.btn_add_dir.setEnabled(not running)
        self.btn_clear.setEnabled(not running)

    def _update_status(self, text: str) -> None:
        """更新状态栏文本。"""
        self.status_label.setText(text)

    def _refresh_kernel_indicator(self) -> None:
        """刷新状态栏右侧的内核指示。"""
        if ffmpeg_locator.is_ready():
            self.kernel_label.setText(f"内核就绪 · v{__version__}")
            # 绿色表示内核可用。
            self.kernel_label.setStyleSheet("color: #38c47a;")
        else:
            self.kernel_label.setText("内核不可用")
            # 红色表示内核缺失，引导用户去设置页处理。
            self.kernel_label.setStyleSheet("color: #e05a5a;")

    def _on_tab_changed(self, idx: int) -> None:
        """标签页切换时的处理。"""
        # 切到设置页时刷新内核信息。
        if isinstance(self.tabs.widget(idx), SettingsTab):
            self.tabs.widget(idx).refresh_kernel()
        # 更新状态栏提示当前类别。
        names = {0: "视频", 1: "音频", 2: "图片", 3: "文档", 4: "高级", 5: "设置", 6: "关于"}
        self._update_status(f"当前类别：{names.get(idx, '')}")

    # ---------------- Web 端 ----------------
    def start_web(self) -> None:
        """在后台线程启动 Web 服务。"""
        # 避免重复启动。
        if getattr(self, "_web_thread", None) and self._web_thread.is_alive():
            QMessageBox.information(self, "Web 端", "Web 端已在运行。")
            return
        # 使用线程启动，避免阻塞界面。
        import threading

        def _serve():
            # 在子线程里调用 uvicorn（uvicorn.run 会阻塞，因此必须放子线程）。
            try:
                from app.web import server as web_server
                web_server.run(open_browser=bool(self.chk_web_open.isChecked()))
            except Exception as exc:
                logger.exception("Web 端启动失败")
                # 通过信号回传错误（此处直接在主线程弹窗不安全，改用状态栏提示）。
                self._update_status(f"Web 端启动失败：{exc}")

        self._web_thread = threading.Thread(target=_serve, name="web-server", daemon=True)
        self._web_thread.start()
        host = str(config.get("web_host", "127.0.0.1"))
        port = int(config.get("web_port", 8765) or 8765)
        self._update_status(S.Msg.WEB_STARTED.format(url=f"http://{host}:{port}/"))

    # ---------------- 关闭处理 ----------------
    def closeEvent(self, event) -> None:
        """关闭窗口时询问是否停止正在运行的任务。"""
        # 若有任务在运行，先确认。
        running = [t for t in self.queue.tasks() if t.status == TaskStatus.RUNNING]
        if running:
            if QMessageBox.question(self, "退出", "仍有任务正在执行，确定要退出吗？") != QMessageBox.StandardButton.Yes:
                event.ignore()
                return
            # 停止队列，避免留下 ffmpeg 子进程。
            self.queue.stop_all()
        # 保存配置。
        config.save()
        event.accept()
