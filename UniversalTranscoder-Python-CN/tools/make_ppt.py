# -*- coding: utf-8 -*-
# 演示文稿生成脚本：为“Universal Transcoder”生成科技风、卡片式、图文并茂的 PPT（pptx）。
# 设计风格：深色渐变底 + 卡片式布局 + 扁平化图形 + 数据可视化图表 + 短句要点。
# 版式原则：每页正文控制在 100 字以内，全部为短句/要点，不出现大段文字。
# 用法：
#   python tools/make_ppt.py --project <工程目录> --lang cn|en
# 依赖：python-pptx（离线可用）。

import os
import sys
import argparse

try:
    from pptx import Presentation
    from pptx.util import Inches, Pt, Emu
    from pptx.dml.color import RGBColor
    from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
    from pptx.enum.shapes import MSO_SHAPE
    from pptx.chart.data import CategoryChartData
    from pptx.enum.chart import XL_CHART_TYPE, XL_LEGEND_POSITION
except ImportError:
    print("缺少 python-pptx 依赖，请先执行：pip install python-pptx")
    sys.exit(1)

# ---------------------- 配色（科技风深色主题） ----------------------
BG_TOP = RGBColor(0x0B, 0x12, 0x2A)  # 背景深蓝
BG_BOT = RGBColor(0x12, 0x1F, 0x3D)  # 背景稍亮
CARD = RGBColor(0x18, 0x26, 0x48)  # 卡片底色
CARD_ALT = RGBColor(0x1F, 0x30, 0x58)  # 卡片交替色
ACCENT = RGBColor(0x3E, 0xA6, 0xFF)  # 主强调色（天蓝）
ACCENT2 = RGBColor(0x5B, 0xE0, 0xC0)  # 次强调色（青绿）
ACCENT3 = RGBColor(0xFF, 0xB4, 0x54)  # 第三强调色（暖橙）
TEXT = RGBColor(0xEA, 0xF2, 0xFF)  # 主文字
TEXT_DIM = RGBColor(0xA9, 0xBC, 0xD8)  # 次要文字
WHITE = RGBColor(0xFF, 0xFF, 0xFF)

FONT = "Microsoft YaHei"


def _fill(shape, color):
    """给形状填充纯色。"""
    shape.fill.solid()
    shape.fill.fore_color.rgb = color


def _no_line(shape):
    """去掉形状描边。"""
    shape.line.fill.background()


def _gradient_bg(slide, prs):
    """给幻灯片铺一个纵向渐变的深色背景。"""
    # 用一个大矩形承担渐变（比直接改背景更可控）。
    rect = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    _no_line(rect)
    rect.fill.gradient()
    rect.fill.gradient_angle = 90.0
    stops = rect.fill.gradient_stops
    stops[0].color.rgb = BG_TOP
    stops[0].position = 0.0
    stops[1].color.rgb = BG_BOT
    stops[1].position = 1.0
    return rect


def _textbox(slide, left, top, width, height, text, size=14, color=TEXT,
             bold=False, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, spacing=1.1):
    """加一个文本框并写入单段文字。"""
    box = slide.shapes.add_textbox(left, top, width, height)
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    para = tf.paragraphs[0]
    para.alignment = align
    para.line_spacing = spacing
    run = para.add_run()
    run.text = text
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = FONT
    return box


def _card(slide, left, top, width, height, color=CARD, radius=0.06):
    """加一个圆角卡片。"""
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
    _fill(shape, color)
    _no_line(shape)
    # 调整圆角大小。
    try:
        shape.adjustments[0] = radius
    except Exception:
        pass
    return shape


def _bullets(tf, items, size=13, color=TEXT, gap=6):
    """往文本框里写入若干要点（短句）。"""
    first = True
    for item in items:
        para = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        para.line_spacing = 1.15
        para.space_after = Pt(gap)
        run = para.add_run()
        run.text = "• " + item
        run.font.size = Pt(size)
        run.font.color.rgb = color
        run.font.name = FONT


def _text_frame(slide, left, top, width, height):
    """创建一个可写多段的文本框，返回其 text_frame。"""
    box = slide.shapes.add_textbox(left, top, width, height)
    tf = box.text_frame
    tf.word_wrap = True
    return tf


def _header(slide, prs, title, subtitle, index):
    """统一的页头：左侧强调条 + 主标题 + 副标题 + 右上页码。"""
    # 顶部强调条。
    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.55), Inches(0.42),
                                 Inches(0.12), Inches(0.62))
    _fill(bar, ACCENT)
    _no_line(bar)
    # 主标题。
    _textbox(slide, Inches(0.85), Inches(0.34), Inches(9.0), Inches(0.55),
             title, size=26, color=WHITE, bold=True)
    # 副标题。
    _textbox(slide, Inches(0.87), Inches(0.92), Inches(9.4), Inches(0.4),
             subtitle, size=12.5, color=TEXT_DIM)
    # 页码。
    _textbox(slide, prs.slide_width - Inches(1.3), Inches(0.42), Inches(0.8), Inches(0.4),
             f"{index:02d}", size=16, color=ACCENT2, bold=True, align=PP_ALIGN.RIGHT)


def _new_slide(prs):
    """新建一张空白版式的幻灯片并铺好渐变背景。"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    _gradient_bg(slide, prs)
    return slide


def _transition(slide):
    """给幻灯片注入淡入切换效果，使放映更流畅（python-pptx 未直接暴露该属性，这里写 XML）。"""
    from pptx.oxml.ns import qn
    xml = slide._element
    # 若已存在则跳过。
    for child in xml:
        if child.tag.endswith("transition"):
            return
    trans = xml.makeelement(qn("p:transition"), {"spd": "med"})
    fade = xml.makeelement(qn("p:fade"), {})
    trans.append(fade)
    xml.append(trans)


class Deck:
    """封装一次 PPT 的构建过程。"""

    def __init__(self, lang: str):
        self.lang = lang
        self.prs = Presentation()
        # 16:9 宽屏。
        self.prs.slide_width = Inches(13.333)
        self.prs.slide_height = Inches(7.5)
        self.index = 0

    def slide(self, title, subtitle):
        """新建一页并写好页头。"""
        self.index += 1
        slide = _new_slide(self.prs)
        _header(slide, self.prs, title, subtitle, self.index)
        _transition(slide)
        return slide

    def save(self, path):
        """保存文件。"""
        self.prs.save(path)


# =============================================================================
# 文案（中英各一套；结构一一对应，便于两个工程各自完整）
# =============================================================================

def build(lang: str) -> Deck:
    """按语言构建整套幻灯片。"""
    d = Deck(lang)
    cn = (lang == "cn")

    def T(z, e):
        """按语言取文案的小工具。"""
        return z if cn else e

    # ---------------- 1. 封面 ----------------
    d.index += 1
    s = _new_slide(d.prs)
    _transition(s)
    # 装饰性扁平图形（同心圆 + 方块）。
    for i, r in enumerate([3.3, 2.5, 1.7]):
        c = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(9.4 - r / 2 * 0.0), Inches(1.6),
                               Inches(r * 2.4), Inches(r * 2.4))
        _no_line(c)
        c.fill.solid()
        c.fill.fore_color.rgb = RGBColor(0x1A, 0x2C, 0x54)
        c.fill.transparency = 0.0
        c.left = Inches(9.2)
        c.top = Inches(1.5)
        c.width = Inches(3.4)
        c.height = Inches(3.4)
        break
    ring = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(9.7), Inches(2.0), Inches(2.4), Inches(2.4))
    _no_line(ring)
    _fill(ring, ACCENT)
    ring.fill.transparency = 0.15
    ring2 = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(10.2), Inches(2.5), Inches(1.4), Inches(1.4))
    _no_line(ring2)
    _fill(ring2, ACCENT2)

    _textbox(s, Inches(0.9), Inches(2.1), Inches(8.6), Inches(0.5),
             T("万能格式转换 · 完全离线", "UNIVERSAL FORMAT CONVERSION - FULLY OFFLINE"),
             size=15, color=ACCENT2, bold=True)
    _textbox(s, Inches(0.9), Inches(2.6), Inches(8.6), Inches(1.4),
             T("万能转码器", "Universal Transcoder"), size=54, color=WHITE, bold=True)
    _textbox(s, Inches(0.9), Inches(3.9), Inches(8.6), Inches(0.7),
             T("视频 · 音频 · 图片 · 文档 全格式互转",
               "Video, audio, image and document, all interchangeable"),
             size=19, color=TEXT_DIM)
    _textbox(s, Inches(0.9), Inches(5.6), Inches(8.6), Inches(0.5),
             T("作者：徐某   |   2026 年 10 月", "Author: Xu   |   October 2026"),
             size=14, color=TEXT)

    # ---------------- 2. 一页看懂 ----------------
    s = d.slide(T("一页看懂：四个数字", "At a glance: four numbers"),
                T("规模、覆盖面与实测结论", "Scale, coverage and measured outcome"))
    metrics = [
        (T("4", "4"), T("类媒体互转\n视频/音频/图片/文档", "media families\nvideo/audio/image/document")),
        (T("35", "35"), T("套内置预设\n一键出结果", "built-in presets\none-click results")),
        (T("30", "30"), T("项格式矩阵\n全部通过", "format matrix cases\nall passed")),
        (T("2", "2"), T("种界面形态\n桌面 + 网页", "UI forms\ndesktop + web")),
    ]
    left = Inches(0.7)
    for i, (num, desc) in enumerate(metrics):
        cx = left + Inches(i * 3.05)
        _card(s, cx, Inches(1.75), Inches(2.75), Inches(2.5), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx, Inches(2.0), Inches(2.75), Inches(1.0), num,
                 size=46, color=ACCENT if i % 2 == 0 else ACCENT2, bold=True, align=PP_ALIGN.CENTER)
        _textbox(s, cx + Inches(0.18), Inches(3.05), Inches(2.4), Inches(1.1), desc,
                 size=12.5, color=TEXT_DIM, align=PP_ALIGN.CENTER)
    _textbox(s, Inches(0.7), Inches(4.85), Inches(11.9), Inches(1.4),
             T("内核基于 FFmpeg：不重复造轮子，把“用户意图”精确编译为可执行参数。\n"
               "两种界面共享同一内核与同一套预设，行为完全一致。",
               "Core built on FFmpeg: we do not reinvent codecs; we compile intent into exact arguments.\n"
               "Both front ends share one core and one preset system, so behaviour is identical."),
             size=14, color=TEXT)

    # ---------------- 3. 痛点与目标 ----------------
    s = d.slide(T("起点：要解决什么", "Starting point: what must be solved"),
                T("四个必须回答的问题", "Four questions that had to be answered"))
    cards = [
        (T("格式太多", "Too many formats"),
         T("扩展名 ≠ 封装名\n选错就失败", "Extension is not the muxer name\nChoosing wrong fails")),
        (T("内核差异", "Core differs"), T("不同构建支持\n的编码器不同", "Different builds ship\ndifferent encoders")),
        (T("报错难懂", "Cryptic errors"), T("“参数无效”\n对用户毫无帮助", "\"Invalid argument\"\nhelps nobody")),
        (T("必须离线", "Must be offline"), T("断网机器\n也要能直接跑", "Must run on a machine\nwith no network")),
    ]
    for i, (title, desc) in enumerate(cards):
        cx = Inches(0.7) + Inches(i * 3.05)
        _card(s, cx, Inches(1.8), Inches(2.75), Inches(3.6), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx + Inches(0.2), Inches(2.05), Inches(2.35), Inches(0.5), title,
                 size=17, color=ACCENT2 if i % 2 == 0 else ACCENT3, bold=True)
        _textbox(s, cx + Inches(0.2), Inches(2.7), Inches(2.35), Inches(1.2), desc,
                 size=12.5, color=TEXT_DIM)
    _textbox(s, Inches(0.7), Inches(5.7), Inches(11.9), Inches(0.8),
             T("目标：把复杂留给自己，把简单交给用户。",
               "Goal: keep the complexity inside, hand simplicity to the user."),
             size=15, color=TEXT, bold=True)

    # ---------------- 4. 总体架构 ----------------
    s = d.slide(T("总体架构：四层分工", "Architecture: four clear layers"),
                T("自上而下，职责单一", "Top-down with single responsibilities"))
    layers = [
        (T("界面层", "UI layer"), T("PyQt 窗口 / Vue 页面", "PyQt window / Vue page")),
        (T("接口层", "Interface layer"), T("REST + WebSocket", "REST + WebSocket")),
        (T("内核逻辑层", "Core logic"), T("探测·构建·执行·队列·错误", "Probe, build, run, queue, triage")),
        (T("内核层", "Core"), T("FFmpeg / FFprobe", "FFmpeg / FFprobe")),
    ]
    colors = [ACCENT, ACCENT2, ACCENT3, RGBColor(0x9A, 0x7B, 0xFF)]
    for i, (name, desc) in enumerate(layers):
        cy = Inches(1.75) + Inches(i * 1.28)
        _card(s, Inches(0.8), cy, Inches(11.7), Inches(1.05), CARD if i % 2 == 0 else CARD_ALT)
        dot = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(1.0), cy + Inches(0.28), Inches(0.5), Inches(0.5))
        _fill(dot, colors[i])
        _no_line(dot)
        _textbox(s, Inches(1.75), cy + Inches(0.16), Inches(2.6), Inches(0.7), name,
                 size=16, color=WHITE, bold=True, anchor=MSO_ANCHOR.MIDDLE)
        _textbox(s, Inches(4.5), cy + Inches(0.16), Inches(7.6), Inches(0.7), desc,
                 size=13, color=TEXT_DIM, anchor=MSO_ANCHOR.MIDDLE)

    # ---------------- 5. 技术栈 ----------------
    s = d.slide(T("技术选型：为什么是它们", "Technology choices: why these"),
                T("每一项都有明确的理由", "Every choice has a reason"))
    stack = [
        (T("FFmpeg", "FFmpeg"), T("覆盖面最广\n单一可执行文件", "Widest coverage\nSingle executable")),
        (T("Python 3", "Python 3"), T("标准库即可胜任\n无额外依赖", "Standard library is enough\nNo extra deps")),
        (T("PyQt6", "PyQt6"), T("原生窗口\n拖拽与表格", "Native window\nDrag, drop, tables")),
        (T("FastAPI", "FastAPI"), T("REST + WebSocket\n异步高效", "REST + WebSocket\nAsync and lean")),
        (T("Vue 3 + JSX", "Vue 3 + JSX"), T("组件化界面\n构建为静态资源", "Component UI\nBuilds to static assets")),
        (T("PyInstaller", "PyInstaller"), T("脱离 Python\n绿色分发", "No Python needed\nPortable output")),
    ]
    for i, (name, desc) in enumerate(stack):
        col = i % 3
        row = i // 3
        cx = Inches(0.7) + Inches(col * 4.05)
        cy = Inches(1.8) + Inches(row * 2.0)
        _card(s, cx, cy, Inches(3.75), Inches(1.7), CARD if (i % 2 == 0) else CARD_ALT)
        _textbox(s, cx + Inches(0.22), cy + Inches(0.18), Inches(3.3), Inches(0.5), name,
                 size=17, color=ACCENT if col == 0 else (ACCENT2 if col == 1 else ACCENT3), bold=True)
        _textbox(s, cx + Inches(0.22), cy + Inches(0.75), Inches(3.3), Inches(0.85), desc,
                 size=12, color=TEXT_DIM)

    # ---------------- 6. 内核定位 ----------------
    s = d.slide(T("第一步：找到并验证内核", "Step one: find and verify the core"),
                T("只看文件存在是不够的", "Checking that a file exists is not enough"))
    steps = [
        T("① 用户自定义路径（目录或文件）", "1. User-specified path (folder or file)"),
        T("② 随程序分发的副本", "2. Copy shipped with the program"),
        T("③ 可执行文件同级目录", "3. Folder next to the executable"),
        T("④ 系统 PATH", "4. System PATH"),
    ]
    tf = _text_frame(s, Inches(0.8), Inches(1.9), Inches(6.6), Inches(3.2))
    _bullets(tf, steps, size=15)
    _card(s, Inches(7.7), Inches(1.9), Inches(4.8), Inches(3.2), CARD_ALT)
    _textbox(s, Inches(8.0), Inches(2.1), Inches(4.2), Inches(0.5),
             T("候选逐个真实校验", "Every candidate is really executed"),
             size=15, color=ACCENT2, bold=True)
    tf2 = _text_frame(s, Inches(8.0), Inches(2.7), Inches(4.2), Inches(2.2))
    _bullets(tf2, [
        T("执行版本命令，检查返回码", "Run the version command, check the exit code"),
        T("检查输出含版本横幅", "Require a version banner in the output"),
        T("任一失败即尝试下一个候选", "Try the next candidate on any failure"),
        T("全部失败给出可读提示", "Report a readable message if all fail"),
    ], size=12.5, color=TEXT_DIM)
    _textbox(s, Inches(0.8), Inches(5.4), Inches(11.6), Inches(0.8),
             T("好处：不会把“同名但无关的程序”误当作内核，避免莫名其妙的失败。",
               "Benefit: an unrelated program with a similar name is never mistaken for the core."),
             size=13.5, color=TEXT)

    # ---------------- 7. 能力探测 ----------------
    s = d.slide(T("第二步：问清内核会做什么", "Step two: ask the core what it can do"),
                T("三条查询，缓存复用", "Three queries, cached and reused"))
    q = [
        (T("编码器清单", "Encoder list"), T("哪些音视频编码可用", "Which encoders exist")),
        (T("封装格式清单", "Muxer list"), T("哪些容器可以写出", "Which containers can be written")),
        (T("硬件加速后端", "Hardware backends"), T("哪些硬件加速可用", "Which accelerators exist")),
    ]
    for i, (name, desc) in enumerate(q):
        cx = Inches(0.7) + Inches(i * 4.05)
        _card(s, cx, Inches(1.85), Inches(3.75), Inches(2.1), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx + Inches(0.22), Inches(2.1), Inches(3.3), Inches(0.5), name,
                 size=17, color=ACCENT, bold=True)
        _textbox(s, cx + Inches(0.22), Inches(2.75), Inches(3.3), Inches(0.9), desc,
                 size=13, color=TEXT_DIM)
    _textbox(s, Inches(0.7), Inches(4.3), Inches(11.9), Inches(1.9),
             T("把结果缓存下来，启动时只探测一次，避免每次转换都重复查询。\n"
               "界面按探测结果过滤格式选项：内核不支持的格式不会出现在下拉框里。\n"
               "若探测失败，则采取宽松方式，不武断禁用全部格式。",
               "Results are cached, so probing happens once at start-up.\n"
               "The UI filters format choices by the probe result: unsupported formats never appear.\n"
               "If probing fails, a permissive policy is used instead of disabling everything."),
             size=13.5, color=TEXT)

    # ---------------- 8. 媒体探测 ----------------
    s = d.slide(T("第三步：读懂源文件", "Step three: understand the source file"),
                T("以结构化方式读取容器与流", "Read container and streams as structured data"))
    tf = _text_frame(s, Inches(0.8), Inches(1.85), Inches(6.3), Inches(3.4))
    _bullets(tf, [
        T("容器：格式名、全称、时长、总码率", "Container: format, long name, duration, bit rate"),
        T("视频流：编码、分辨率、帧率、像素格式", "Video stream: codec, resolution, frame rate, pixel format"),
        T("音频流：编码、采样率、声道、布局", "Audio stream: codec, sample rate, channels, layout"),
        T("字幕流：语言与标题标记", "Subtitle stream: language and title tags"),
    ], size=13.5)
    _card(s, Inches(7.4), Inches(1.85), Inches(5.1), Inches(3.4), CARD_ALT)
    _textbox(s, Inches(7.65), Inches(2.05), Inches(4.6), Inches(0.5),
             T("容错三原则", "Three fault-tolerance rules"), size=15, color=ACCENT2, bold=True)
    tf3 = _text_frame(s, Inches(7.65), Inches(2.6), Inches(4.6), Inches(2.4))
    _bullets(tf3, [
        T("缺失字段逐字段兜底", "Fall back field by field when data is missing"),
        T("损坏文件不抛异常", "Damaged files never raise"),
        T("超时即返回低置信度结果", "Timeouts return a low-confidence result"),
    ], size=12.5, color=TEXT_DIM)

    # ---------------- 9. 命令构建 ----------------
    s = d.slide(T("第四步：把意图编译为参数", "Step four: compile intent into arguments"),
                T("产出参数列表，而不是命令字符串", "Produce an argument list, not a command string"))
    cards = [
        (T("① 容器换算", "1. Container aliasing"),
         T("mkv → matroska\nts → mpegts\nmpg → mpeg", "mkv to matroska\nts to mpegts\nmpg to mpeg")),
        (T("② 编码器推断", "2. Codec inference"),
         T("按封装名查默认值\n不合规则降级", "Look up defaults by muxer\nDowngrade when invalid")),
        (T("③ 滤镜链拼接", "3. Filter assembly"), T("顺序固定\n不可随意调换", "Fixed order\nNever swapped")),
    ]
    for i, (name, desc) in enumerate(cards):
        cx = Inches(0.7) + Inches(i * 4.05)
        _card(s, cx, Inches(1.85), Inches(3.75), Inches(2.5), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx + Inches(0.22), Inches(2.05), Inches(3.3), Inches(0.5), name,
                 size=16, color=ACCENT3 if i == 2 else ACCENT, bold=True)
        _textbox(s, cx + Inches(0.22), Inches(2.7), Inches(3.3), Inches(1.4), desc,
                 size=13, color=TEXT_DIM)
    _textbox(s, Inches(0.7), Inches(4.6), Inches(11.9), Inches(1.6),
             T("用参数列表而非字符串：没有引号与空格差异，也从根本上杜绝命令注入。\n"
               "三层处理让“用户随便选”也能得到一条内核能接受的命令。",
               "An argument list avoids quoting and spacing issues and structurally prevents command injection.\n"
               "The three stages turn any user choice into a command the core will accept."),
             size=13.5, color=TEXT)

    # ---------------- 10. 容器换算数据可视化 ----------------
    s = d.slide(T("关键细节：扩展名 ≠ 封装名", "Key detail: extension is not the muxer name"),
                T("每一个换算都经内核清单核对", "Every alias verified against the core list"))
    chart_data = CategoryChartData()
    chart_data.categories = ["mkv", "ts", "mpg", "wmv", "m4a", "jpg"]
    chart_data.add_series(T("换算后封装名（长度示意）", "Muxer name length"), (8, 6, 4, 3, 4, 6))
    gf = s.shapes.add_chart(XL_CHART_TYPE.COLUMN_CLUSTERED, Inches(0.7), Inches(1.8),
                            Inches(7.0), Inches(4.4), chart_data)
    chart = gf.chart
    chart.has_legend = False
    chart.has_title = False
    plot = chart.plots[0]
    plot.has_data_labels = True
    # 柱子配色。
    for i, point in enumerate(plot.series[0].points):
        point.format.fill.solid()
        point.format.fill.fore_color.rgb = [ACCENT, ACCENT2, ACCENT3,
                                            RGBColor(0x9A, 0x7B, 0xFF), ACCENT, ACCENT2][i]
    _card(s, Inches(7.9), Inches(1.8), Inches(4.6), Inches(4.4), CARD_ALT)
    _textbox(s, Inches(8.15), Inches(2.0), Inches(4.1), Inches(0.5),
             T("换算对照", "Alias examples"), size=15, color=ACCENT2, bold=True)
    tf = _text_frame(s, Inches(8.15), Inches(2.55), Inches(4.1), Inches(3.4))
    _bullets(tf, [
        T("mkv → matroska", "mkv to matroska"),
        T("ts → mpegts", "ts to mpegts"),
        T("mpg → mpeg", "mpg to mpeg"),
        T("wmv → asf", "wmv to asf"),
        T("m4a → ipod", "m4a to ipod"),
        T("jpg/png → image2", "jpg/png to image2"),
    ], size=12.5, color=TEXT_DIM)

    # ---------------- 11. 滤镜链 ----------------
    s = d.slide(T("滤镜链：顺序决定成败", "Filter chain: order decides the outcome"),
                T("固定顺序，避免隐蔽错位", "A fixed order avoids hidden misalignment"))
    order = [
        T("裁剪", "Crop"), T("缩放", "Scale"), T("旋转", "Rotate"), T("镜像", "Mirror"),
        T("帧率", "FPS"), T("水印", "Watermark"), T("字幕", "Subtitle"), T("变速", "Speed"),
    ]
    for i, name in enumerate(order):
        cx = Inches(0.62) + Inches(i * 1.53)
        _card(s, cx, Inches(2.3), Inches(1.36), Inches(1.0), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx, Inches(2.5), Inches(1.36), Inches(0.6), name,
                 size=13, color=ACCENT if i % 2 == 0 else ACCENT2, bold=True, align=PP_ALIGN.CENTER)
        if i < len(order) - 1:
            ar = s.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, cx + Inches(1.38), Inches(2.66),
                                    Inches(0.16), Inches(0.26))
            _fill(ar, TEXT_DIM)
            _no_line(ar)
    _textbox(s, Inches(0.7), Inches(3.75), Inches(11.9), Inches(2.5),
             T("调换顺序会出现的问题：\n"
               "• 先旋转再裁剪 → 坐标错乱，裁不到想要的区域\n"
               "• 先缩放再加水印 → 水印随画面变形\n"
               "• 字幕最后烧录 → 保证叠加在最终画面上\n\n"
               "所有尺寸向下取偶数：主流编码器要求宽高可被 2 整除。",
               "What breaks if the order changes:\n"
               "- rotate then crop: misaligned coordinates and the wrong region\n"
               "- scale then watermark: the watermark deforms with the picture\n"
               "- subtitle last: guaranteed to sit on the final picture\n\n"
               "Every dimension is rounded down to an even number, as mainstream encoders require."),
             size=13.5, color=TEXT)

    # ---------------- 12. 质量档位 ----------------
    s = d.slide(T("质量档位映射", "Quality tiers mapped to parameters"),
                T("用户选档位，内核收参数", "Users pick a tier, the core gets parameters"))
    tiers = [
        (T("无损", "Lossless"), "CRF 0", T("体积最大", "Largest file")),
        (T("高质量", "High"), "CRF 20 / 192k", T("清晰优先", "Quality first")),
        (T("标准", "Standard"), "CRF 23 / 128k", T("均衡推荐", "Balanced")),
        (T("小体积", "Small"), "CRF 28 / 96k", T("分享优先", "Sharing first")),
    ]
    for i, (name, param, note) in enumerate(tiers):
        cx = Inches(0.7) + Inches(i * 3.05)
        _card(s, cx, Inches(1.9), Inches(2.75), Inches(2.7), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx, Inches(2.15), Inches(2.75), Inches(0.5), name,
                 size=17, color=[ACCENT, ACCENT2, ACCENT3, RGBColor(0x9A, 0x7B, 0xFF)][i],
                 bold=True, align=PP_ALIGN.CENTER)
        _textbox(s, cx, Inches(2.85), Inches(2.75), Inches(0.7), param,
                 size=20, color=WHITE, bold=True, align=PP_ALIGN.CENTER)
        _textbox(s, cx, Inches(3.7), Inches(2.75), Inches(0.6), note,
                 size=12.5, color=TEXT_DIM, align=PP_ALIGN.CENTER)
    _textbox(s, Inches(0.7), Inches(5.0), Inches(11.9), Inches(1.2),
             T("用户显式填写的码率优先；预设自带参数优先级最高，保证一键结果稳定可预期。",
               "An explicit user bitrate wins; preset values rank highest so one-click results stay predictable."),
             size=13.5, color=TEXT)

    # ---------------- 13. 执行与进度 ----------------
    s = d.slide(T("执行与进度：把黑盒变透明", "Execution and progress: turning a black box transparent"),
                T("结构化进度流，而非解析人类文本", "A structured progress stream, not parsed prose"))
    tf = _text_frame(s, Inches(0.8), Inches(1.85), Inches(6.2), Inches(3.6))
    _bullets(tf, [
        T("读取键值对形式的进度块", "Read key/value progress blocks"),
        T("已编码时长 ÷ 源总时长 = 百分比", "Encoded time over duration gives the percentage"),
        T("相邻采样差估算瞬时速度", "Instantaneous speed from consecutive samples"),
        T("由速度推算剩余时间", "Remaining time derived from speed"),
        T("取消：先温和终止，超时再强制结束", "Cancel: graceful stop first, force-kill on timeout"),
    ], size=13.5)
    _card(s, Inches(7.3), Inches(1.85), Inches(5.2), Inches(3.6), CARD_ALT)
    _textbox(s, Inches(7.55), Inches(2.05), Inches(4.7), Inches(0.5),
             T("为什么不用人类可读文本", "Why not parse human-readable text"), size=15, color=ACCENT2, bold=True)
    tf2 = _text_frame(s, Inches(7.55), Inches(2.6), Inches(4.7), Inches(2.6))
    _bullets(tf2, [
        T("人类文本受语言环境影响", "Human text depends on the locale"),
        T("字段顺序可能变化", "Field order can change"),
        T("键值对形式稳定且精确", "Key/value blocks are stable and precise"),
        T("瞬时速度比自报速度更贴近真实", "Instantaneous speed tracks reality better"),
    ], size=12.5, color=TEXT_DIM)

    # ---------------- 14. 任务队列状态机 ----------------
    s = d.slide(T("任务队列：单向状态机", "Job queue: a one-way state machine"),
                T("等待 → 运行 → 终态", "Waiting to running to a terminal state"))
    states = [
        (T("等待", "Waiting"), ACCENT),
        (T("运行", "Running"), ACCENT2),
        (T("完成", "Done"), RGBColor(0x5B, 0xE0, 0xA0)),
        (T("失败", "Failed"), RGBColor(0xFF, 0x7A, 0x7A)),
        (T("取消", "Cancelled"), ACCENT3),
        (T("跳过", "Skipped"), RGBColor(0x9A, 0x7B, 0xFF)),
    ]
    for i, (name, color) in enumerate(states):
        cx = Inches(0.7) + Inches((i % 3) * 4.05)
        cy = Inches(1.9) + Inches((i // 3) * 1.5)
        _card(s, cx, cy, Inches(3.75), Inches(1.25), CARD if i % 2 == 0 else CARD_ALT)
        dot = s.shapes.add_shape(MSO_SHAPE.OVAL, cx + Inches(0.3), cy + Inches(0.38),
                                 Inches(0.5), Inches(0.5))
        _fill(dot, color)
        _no_line(dot)
        _textbox(s, cx + Inches(1.0), cy + Inches(0.3), Inches(2.5), Inches(0.6), name,
                 size=16, color=WHITE, bold=True, anchor=MSO_ANCHOR.MIDDLE)
    _textbox(s, Inches(0.7), Inches(5.1), Inches(11.9), Inches(1.5),
             T("队列不依赖任何界面框架，只通过回调上报 —— 因此桌面端与网页端复用同一套调度逻辑。\n"
               "支持并发上限、暂停（只停“取新任务”）、继续、停止与自动去重。",
               "The queue depends on no UI framework and reports only through callbacks, so both front ends reuse it.\n"
               "It supports a concurrency limit, pause (which only stops taking new jobs), resume, stop and de-duplication."),
             size=13.5, color=TEXT)

    # ---------------- 15. 错误分类 ----------------
    s = d.slide(T("错误分类：把报错翻译成人话", "Error triage: translating errors into advice"),
                T("十几类失败模式，各有对策", "More than a dozen failure classes with advice"))
    tf = _text_frame(s, Inches(0.8), Inches(1.85), Inches(11.6), Inches(4.2))
    _bullets(tf, [
        T("源文件损坏 / 格式不符：建议换文件或修复", "Damaged source or mismatched format: replace or repair"),
        T("文件缺失 / 无权限：检查路径与占用", "Missing file or no permission: check path and locks"),
        T("参数或编码器不支持：更换组合或内核", "Unsupported argument or codec: change the combination"),
        T("编码器初始化失败：改用通用编码器", "Encoder init failure: switch to a universal codec"),
        T("磁盘空间不足：清理或改目录", "No space left: clean up or change the target"),
        T("奇数分辨率：自动补边到偶数", "Odd dimensions: pad to even automatically"),
        T("无有效流：更换目标格式", "No usable stream: choose another target format"),
        T("缺少解码器：更换内核或先转通用格式", "Missing decoder: change the core or convert first"),
        T("硬件加速失败：自动回退软件编码", "Hardware failure: fall back to software automatically"),
    ], size=13.5)
    _textbox(s, Inches(0.8), Inches(6.1), Inches(11.6), Inches(0.8),
             T("分类是“尽力而为”的启发式匹配，匹配不到时归为未知，但绝不因此抛异常。",
               "Triage is best-effort heuristic matching; unmatched cases become unknown and never raise."),
             size=13, color=TEXT_DIM)

    # ---------------- 16. 自动补救 ----------------
    s = d.slide(T("自动补救：两条确定的路径", "Automatic repair: two deterministic paths"),
                T("只对“原因明确”的错误动手", "Only act when the cause is certain"))
    for i, (name, desc, color) in enumerate([
        (T("奇数分辨率", "Odd dimensions"),
         T("原因：宽或高不能被 2 整除\n动作：补边到偶数后重试一次",
           "Cause: width or height is odd\nAction: pad to even and retry once"), ACCENT),
        (T("硬件编码失败", "Hardware encoding failure"),
         T("原因：驱动 / 显存 / 分辨率限制\n动作：整套换软件编码器重建命令",
           "Cause: driver, memory or resolution limits\nAction: rebuild the command with software encoding"), ACCENT2),
    ]):
        cx = Inches(0.7) + Inches(i * 6.15)
        _card(s, cx, Inches(1.9), Inches(5.85), Inches(3.3), CARD if i == 0 else CARD_ALT)
        _textbox(s, cx + Inches(0.3), Inches(2.15), Inches(5.2), Inches(0.6), name,
                 size=18, color=color, bold=True)
        _textbox(s, cx + Inches(0.3), Inches(2.95), Inches(5.2), Inches(1.8), desc,
                 size=13.5, color=TEXT_DIM)
    _textbox(s, Inches(0.7), Inches(5.5), Inches(11.9), Inches(1.0),
             T("关键：硬件补救不是“追加参数”，而是整套替换编码器后重建命令 —— 这与最初的做法不同，也是实测后修正的结果。",
               "Crucially, the hardware repair rebuilds the whole command instead of appending arguments - a fix that came from real measurement."),
             size=13.5, color=TEXT)

    # ---------------- 17. 预设体系（数据可视化） ----------------
    s = d.slide(T("预设体系：35 套开箱即用", "Preset system: 35 out-of-the-box recipes"),
                T("视频 14 · 音频 11 · 图片 10", "14 video, 11 audio, 10 image"))
    cd = CategoryChartData()
    cd.categories = [T("视频", "Video"), T("音频", "Audio"), T("图片", "Image")]
    cd.add_series(T("预设数量", "Preset count"), (14, 11, 10))
    gf = s.shapes.add_chart(XL_CHART_TYPE.DOUGHNUT, Inches(0.7), Inches(1.8),
                            Inches(5.6), Inches(4.4), cd)
    ch = gf.chart
    ch.has_legend = True
    ch.legend.position = XL_LEGEND_POSITION.BOTTOM
    ch.legend.include_in_layout = False
    ch.plots[0].has_data_labels = True
    for i, point in enumerate(ch.plots[0].series[0].points):
        point.format.fill.solid()
        point.format.fill.fore_color.rgb = [ACCENT, ACCENT2, ACCENT3][i]
    _card(s, Inches(6.7), Inches(1.8), Inches(5.8), Inches(4.4), CARD_ALT)
    _textbox(s, Inches(6.95), Inches(2.0), Inches(5.3), Inches(0.5),
             T("预设覆盖的场景", "Scenarios covered"), size=15, color=ACCENT2, bold=True)
    tf = _text_frame(s, Inches(6.95), Inches(2.6), Inches(5.3), Inches(3.4))
    _bullets(tf, [
        T("手机通用 MP4 / H.264 三档质量", "Phone-friendly MP4 with three quality tiers"),
        T("H.265 高压缩、MKV 多轨收藏", "H.265 high compression, multi-track MKV"),
        T("WebM 网页、FLV 推流、TS 传输流", "WebM for web, FLV for streaming, TS transport"),
        T("剪辑中间格式 ProRes / DNxHD", "Editing intermediates ProRes and DNxHD"),
        T("无损 FLAC、未压缩 WAV、杜比 AC3", "Lossless FLAC, uncompressed WAV, Dolby AC3"),
        T("网页动图 GIF、现代图片 AVIF / WebP", "Web GIF animation, modern AVIF and WebP"),
    ], size=12.5, color=TEXT_DIM)

    # ---------------- 18. 硬件加速 ----------------
    s = d.slide(T("硬件加速：能快就快", "Hardware acceleration: fast when possible"),
                T("默认软件编码，硬件作为可选项", "Software by default, hardware as an option"))
    vendors = [
        (T("NVIDIA", "NVIDIA"), "NVENC"),
        (T("Intel", "Intel"), "QSV"),
        (T("AMD", "AMD"), "AMF"),
        (T("Apple", "Apple"), "VideoToolbox"),
    ]
    for i, (name, tech) in enumerate(vendors):
        cx = Inches(0.7) + Inches(i * 3.05)
        _card(s, cx, Inches(1.9), Inches(2.75), Inches(2.0), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx, Inches(2.15), Inches(2.75), Inches(0.6), name,
                 size=17, color=ACCENT, bold=True, align=PP_ALIGN.CENTER)
        _textbox(s, cx, Inches(2.85), Inches(2.75), Inches(0.6), tech,
                 size=15, color=ACCENT2, bold=True, align=PP_ALIGN.CENTER)
    _textbox(s, Inches(0.7), Inches(4.2), Inches(11.9), Inches(2.2),
             T("“存在不等于可用”：硬件编码器可能因驱动、显存或分辨率限制而失败。\n"
               "因此默认走软件编码，保证任何机器都能成功；需要提速时再开启硬件路径。\n"
               "自动方式按“通用性优先”的顺序挑选硬件编码器。",
               "Presence does not imply usability: hardware encoders can fail on drivers, memory or resolution.\n"
               "Software encoding is therefore the default so any machine succeeds; hardware is opt-in for speed.\n"
               "Automatic mode picks hardware encoders in a generality-first order."),
             size=13.5, color=TEXT)

    # ---------------- 19. 文档转换 ----------------
    s = d.slide(T("文档转换：不依赖办公软件", "Document conversion: no office suite needed"),
                T("三条路径，全部离线", "Three paths, all offline"))
    docs = [
        (T("多图 → PDF", "Images to PDF"),
         T("透明图合成白底\n一次写出多页", "Composite transparency onto white\nWrite many pages at once")),
        (T("PDF → 图片", "PDF to images"),
         T("按页渲染\n支持页码与分辨率", "Render page by page\nPage range and resolution")),
        (T("文本 → PDF", "Text to PDF"), T("按页宽折行\n手工构造合法 PDF", "Wrap to page width\nHand-built valid PDF")),
    ]
    for i, (name, desc) in enumerate(docs):
        cx = Inches(0.7) + Inches(i * 4.05)
        _card(s, cx, Inches(1.9), Inches(3.75), Inches(2.8), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx + Inches(0.22), Inches(2.15), Inches(3.3), Inches(0.6), name,
                 size=16, color=[ACCENT, ACCENT2, ACCENT3][i], bold=True)
        _textbox(s, cx + Inches(0.22), Inches(2.9), Inches(3.3), Inches(1.6), desc,
                 size=13, color=TEXT_DIM)
    _textbox(s, Inches(0.7), Inches(5.0), Inches(11.9), Inches(1.2),
             T("单张图片或单页失败会被跳过并记录，不影响其余页；全程不依赖任何外部办公软件。",
               "A failing image or page is skipped and logged without affecting the rest, and no external office software is required."),
             size=13.5, color=TEXT)

    # ---------------- 20. 桌面端界面 ----------------
    s = d.slide(T("桌面端：九个标签页", "Desktop: nine tabs"),
                T("拖拽即添加，表格看进度", "Drop to add, watch progress in a table"))
    tabs = [T("视频", "Video"), T("音频", "Audio"), T("图片", "Image"), T("文档", "Document"),
            T("设备", "Device"), T("高级", "Advanced"), T("队列", "Queue"), T("设置", "Settings"),
            T("关于", "About")]
    # 模仿“手机 UI 截图感”的卡片：顶部导航条 + 内容卡片。
    _card(s, Inches(0.7), Inches(1.8), Inches(6.0), Inches(4.4), CARD)
    nav = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.9), Inches(2.0),
                             Inches(5.6), Inches(0.55))
    _fill(nav, CARD_ALT)
    _no_line(nav)
    for i, name in enumerate(tabs[:5]):
        _textbox(s, Inches(1.0) + Inches(i * 1.1), Inches(2.06), Inches(1.0), Inches(0.42), name,
                 size=10.5, color=ACCENT2, bold=True, align=PP_ALIGN.CENTER)
    for i in range(3):
        _card(s, Inches(0.95), Inches(2.75) + Inches(i * 1.1), Inches(5.5), Inches(0.9), CARD_ALT, radius=0.15)
        _textbox(s, Inches(1.15), Inches(2.9) + Inches(i * 1.1), Inches(3.0), Inches(0.5),
                 tabs[i + 5] if i + 5 < len(tabs) else "", size=12, color=TEXT, bold=True)
        _textbox(s, Inches(4.2), Inches(2.92) + Inches(i * 1.1), Inches(2.0), Inches(0.5),
                 T("就绪", "Ready"), size=11, color=TEXT_DIM, align=PP_ALIGN.RIGHT)
    _card(s, Inches(7.1), Inches(1.8), Inches(5.4), Inches(4.4), CARD_ALT)
    _textbox(s, Inches(7.35), Inches(2.0), Inches(4.9), Inches(0.5),
             T("交互要点", "Interaction highlights"), size=15, color=ACCENT2, bold=True)
    tf = _text_frame(s, Inches(7.35), Inches(2.6), Inches(4.9), Inches(3.4))
    _bullets(tf, [
        T("拖拽文件 / 递归添加文件夹", "Drop files, import folders recursively"),
        T("自动去重，重复项直接跳过", "Automatic de-duplication"),
        T("表格实时显示进度、速度、剩余时间", "Live progress, speed and time left"),
        T("双击查看失败详情与建议", "Double-click for failure details and advice"),
        T("预览 / 复制将要执行的命令", "Preview or copy the exact command"),
    ], size=12.5, color=TEXT_DIM)

    # ---------------- 21. 网页端 ----------------
    s = d.slide(T("网页端：同一内核，另一种形态", "Web app: the same core, another form"),
                T("20 个接口 + 1 条进度通道", "20 endpoints plus one progress channel"))
    _card(s, Inches(0.7), Inches(1.8), Inches(6.0), Inches(4.4), CARD)
    _textbox(s, Inches(0.95), Inches(2.0), Inches(5.5), Inches(0.5),
             T("后端接口分组", "Backend endpoint groups"), size=15, color=ACCENT, bold=True)
    tf = _text_frame(s, Inches(0.95), Inches(2.6), Inches(5.5), Inches(3.4))
    _bullets(tf, [
        T("系统与能力：system / presets / formats", "System and capability: system, presets, formats"),
        T("文件与探测：browse / probe / open", "Files and probing: browse, probe, open"),
        T("设置：settings 读取与保存", "Settings: read and save"),
        T("任务：tasks 增删清空", "Jobs: add, remove, clear"),
        T("队列：start / pause / resume / stop", "Queue: start, pause, resume, stop"),
        T("扩展：snapshot 抽帧、doc 文档转换", "Extras: snapshot and document conversion"),
    ], size=12.5, color=TEXT)
    _card(s, Inches(7.1), Inches(1.8), Inches(5.4), Inches(4.4), CARD_ALT)
    _textbox(s, Inches(7.35), Inches(2.0), Inches(4.9), Inches(0.5),
             T("前端 16 个组件", "16 front-end components"), size=15, color=ACCENT2, bold=True)
    tf2 = _text_frame(s, Inches(7.35), Inches(2.6), Inches(4.9), Inches(3.4))
    _bullets(tf2, [
        T("导航与外壳：NavTabs / Dock / ui", "Shell: NavTabs, Dock, ui"),
        T("四类面板：视频 / 音频 / 图片 / 文档", "Four panels: video, audio, image, document"),
        T("文件与输出：FilePanel / FileBrowser / OutputSection", "Files: FilePanel, FileBrowser, OutputSection"),
        T("任务与设置：TaskTable / QueuePanel / SettingsPanel", "Jobs: TaskTable, QueuePanel, SettingsPanel"),
        T("预设与关于：PresetBar / AboutPanel / AdvancedPanel", "Presets: PresetBar, AboutPanel, AdvancedPanel"),
    ], size=12.5, color=TEXT_DIM)

    # ---------------- 22. 30 项矩阵（数据可视化） ----------------
    s = d.slide(T("验证：30 项矩阵全部通过", "Verification: 30 of 30 passed"),
                T("穷举而非抽查", "Exhaustive, not sampled"))
    cd2 = CategoryChartData()
    cd2.categories = [T("视频", "Video"), T("音频", "Audio"), T("图片", "Image"), T("抽帧", "Snapshots")]
    cd2.add_series(T("通过项", "Passed"), (12, 10, 7, 1))
    gf2 = s.shapes.add_chart(XL_CHART_TYPE.BAR_CLUSTERED, Inches(0.7), Inches(1.8),
                             Inches(7.2), Inches(4.4), cd2)
    ch2 = gf2.chart
    ch2.has_legend = False
    ch2.plots[0].has_data_labels = True
    for i, point in enumerate(ch2.plots[0].series[0].points):
        point.format.fill.solid()
        point.format.fill.fore_color.rgb = [ACCENT, ACCENT2, ACCENT3, RGBColor(0x9A, 0x7B, 0xFF)][i]
    _card(s, Inches(8.1), Inches(1.8), Inches(4.4), Inches(4.4), CARD_ALT)
    _textbox(s, Inches(8.35), Inches(2.0), Inches(3.9), Inches(0.5),
             T("关键做法", "The key method"), size=15, color=ACCENT2, bold=True)
    tf = _text_frame(s, Inches(8.35), Inches(2.6), Inches(3.9), Inches(3.4))
    _bullets(tf, [
        T("不看命令返回码", "Do not trust the exit code alone"),
        T("产物二次探测确认类型", "Probe the output again to confirm its type"),
        T("排除“成功但产物是垃圾”", "Rule out plausible but useless outputs"),
        T("内核实测版本横幅校验", "The core itself was verified by its version banner"),
    ], size=12, color=TEXT_DIM)

    # ---------------- 23. 修复的缺陷 ----------------
    s = d.slide(T("过程中修复的九处真实缺陷", "Nine real defects fixed along the way"),
                T("每一处都来自真实运行", "Each one came from a real run"))
    defects = [
        T("封装名换算（mkv/ts/mpg）", "Muxer aliasing for mkv, ts and mpg"),
        T("默认编码器表键名改用封装名", "Default codec table keyed by muxer name"),
        T("图片目标独立映射", "Dedicated image target map"),
        T("下拉取值区分“空”与“未选”", "Combo lookup distinguishes empty from unselected"),
        T("硬件补救整套替换编码器", "Hardware repair rebuilds the whole command"),
        T("去掉二进制管道的行缓冲", "Removed line buffering on a binary pipe"),
        T("执行前兜底创建输出目录", "Ensure the output folder before running"),
        T("入口统一走主函数解析参数", "All entry points go through the main function"),
        T("打包后补齐标准流", "Provide fallback streams in a windowed build"),
    ]
    for i, item in enumerate(defects):
        col = i % 3
        row = i // 3
        cx = Inches(0.7) + Inches(col * 4.05)
        cy = Inches(1.85) + Inches(row * 1.35)
        _card(s, cx, cy, Inches(3.75), Inches(1.1), CARD if i % 2 == 0 else CARD_ALT)
        num = s.shapes.add_shape(MSO_SHAPE.OVAL, cx + Inches(0.22), cy + Inches(0.28),
                                 Inches(0.52), Inches(0.52))
        _fill(num, ACCENT if i % 2 == 0 else ACCENT3)
        _no_line(num)
        _textbox(s, cx + Inches(0.22), cy + Inches(0.33), Inches(0.52), Inches(0.42),
                 str(i + 1), size=12, color=BG_TOP, bold=True, align=PP_ALIGN.CENTER)
        _textbox(s, cx + Inches(0.9), cy + Inches(0.2), Inches(2.7), Inches(0.8), item,
                 size=11.5, color=TEXT, anchor=MSO_ANCHOR.MIDDLE)

    # ---------------- 24. 打包与离线 ----------------
    s = d.slide(T("打包：一个目录，拷贝即用", "Packaging: one folder, copy and run"),
                T("六步构建，任一步失败即停", "Six build steps, stop on the first failure"))
    steps = [
        T("建虚拟环境", "Create the environment"),
        T("安装依赖", "Install dependencies"),
        T("生成图标", "Generate the icon"),
        T("构建前端", "Build the front end"),
        T("内核自检", "Core self-check"),
        T("打包 exe", "Package the executable"),
    ]
    for i, name in enumerate(steps):
        cx = Inches(0.62) + Inches(i * 2.12)
        _card(s, cx, Inches(2.0), Inches(1.85), Inches(1.5), CARD if i % 2 == 0 else CARD_ALT)
        _textbox(s, cx, Inches(2.5), Inches(1.85), Inches(0.6), name,
                 size=12.5, color=ACCENT if i % 2 == 0 else ACCENT2, bold=True, align=PP_ALIGN.CENTER)
    _textbox(s, Inches(0.7), Inches(3.9), Inches(11.9), Inches(2.4),
             T("产物采用 one-folder 绿色版：内核、图标与网页静态资源全部收进同一目录。\n"
               "程序对运行环境无额外要求，无需安装 Python，也无需联网。\n"
               "整个目录拷贝到离线 Windows 机器即可直接运行 —— 这就是“离线可用”的交付形态。",
               "The output uses the one-folder portable layout: core, icon and web assets all in one directory.\n"
               "It needs no extra runtime, no Python installation and no network.\n"
               "Copy the folder to an offline Windows machine and it runs - that is the offline-ready deliverable."),
             size=13.5, color=TEXT)

    # ---------------- 25. 结语 ----------------
    s = d.slide(T("结语：把万能落到实处", "Closing: making universality real"),
                T("可验证 · 可复现 · 可交付", "Verifiable, reproducible, deliverable"))
    _textbox(s, Inches(0.9), Inches(1.9), Inches(11.6), Inches(0.9),
             T("万能 = 4 类媒体 + 35 套预设 + 30 项矩阵全通过",
               "Universal = 4 media families, 35 presets, 30 of 30 matrix cases passed"),
             size=20, color=WHITE, bold=True)
    _textbox(s, Inches(0.9), Inches(3.0), Inches(11.6), Inches(0.9),
             T("容错 = 前置过滤 + 过程监控 + 失败分类与自动补救",
               "Tolerant = capability filtering, live monitoring, error triage and automatic repair"),
             size=20, color=ACCENT2, bold=True)
    _textbox(s, Inches(0.9), Inches(4.1), Inches(11.6), Inches(0.9),
             T("离现交付 = 一个目录，内含内核、图标与网页资源",
               "Offline deliverable = one folder holding the core, the icon and the web assets"),
             size=20, color=ACCENT3, bold=True)
    _textbox(s, Inches(0.9), Inches(5.7), Inches(11.6), Inches(0.6),
             T("Universal Transcoder · 徐某 · 2026",
               "Universal Transcoder - Xu - 2026"),
             size=16, color=TEXT_DIM)

    return d


def main():
    """命令行入口。"""
    parser = argparse.ArgumentParser(description="生成 Universal Transcoder 的演示文稿")
    parser.add_argument("--project", required=True, help="工程根目录（PPT 将写入其 docs 子目录）")
    parser.add_argument("--lang", default="cn", choices=["cn", "en"], help="PPT 语言")
    args = parser.parse_args()

    project = os.path.abspath(args.project)
    docs_dir = os.path.join(project, "docs")
    os.makedirs(docs_dir, exist_ok=True)

    deck = build(args.lang)
    name = "万能转码器_介绍PPT.pptx" if args.lang == "cn" else "Universal-Transcoder-Overview.pptx"
    path = os.path.join(docs_dir, name)
    deck.save(path)
    print("已生成：" + path + f"（共 {len(deck.prs.slides)} 页）")


if __name__ == "__main__":
    main()
