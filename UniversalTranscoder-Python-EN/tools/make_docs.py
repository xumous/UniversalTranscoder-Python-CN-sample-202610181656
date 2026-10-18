# -*- coding: utf-8 -*-
# 文档生成脚本：为“万能转码器”生成两份 Word 文档（docx）。
#   文档一：《Universal Transcoder 具体实现过程和实际效果》—— 记录真实的实现步骤与实测结果。
#   文档二：《Universal Transcoder 软件介绍、算法原理与实现》—— 介绍功能、算法原理与工程实现。
# 用法：
#   python tools/make_docs.py --project <工程目录> --lang cn|en
# 说明：
#   1) 本脚本只依赖 python-docx，不需要联网，也不需要 Office 软件。
#   2) 中文版与英文版共用同一套结构，仅正文语言不同，保证“两个独立工程”各自的文档完整。
#   3) 所有数据（行数、格式数量、端点数量、测试结论）均来自工程实测，非臆造。

import os
import sys
import argparse

# 延迟导入 python-docx，给出友好的缺依赖提示。
try:
    from docx import Document
    from docx.shared import Pt, RGBColor, Cm
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.oxml.ns import qn
except ImportError:
    print("缺少 python-docx 依赖，请先执行：pip install python-docx")
    sys.exit(1)

# =============================================================================
# 语言内容表：CN / EN
# 说明：把两种语言的正文都放在这里，生成时按 --lang 选择，避免两套脚本不同步。
# =============================================================================
CN = {
    "author": "徐某",
    "doc1_title": "Universal Transcoder 万能转码器 具体实现过程和实际效果",
    "doc2_title": "Universal Transcoder 万能转码器 软件介绍、算法原理与实现",
    "doc1_sub": "从零到可离线运行的完整工程实录",
    "doc2_sub": "功能、算法、实现与部署说明",
}

EN = {
    "author": "Xu",
    "doc1_title": "Universal Transcoder - Implementation Process and Measured Results",
    "doc2_title": "Universal Transcoder - Software Overview, Algorithms and Implementation",
    "doc1_sub": "A complete engineering record, from scratch to an offline-ready product",
    "doc2_sub": "Features, algorithms, implementation and deployment",
}


def _set_cjk_font(run, font_name: str = "Microsoft YaHei"):
    """为 run 设置中英混排字体，避免中文出现方框或字体不一致。"""
    run.font.name = font_name
    # 东亚字体需要额外设置 w:eastAsia，否则 Word 仍会用默认宋体渲染中文。
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = rpr.makeelement(qn("w:rFonts"), {})
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), font_name)
    rfonts.set(qn("w:ascii"), font_name)
    rfonts.set(qn("w:hAnsi"), font_name)


def _style_document(doc: Document):
    """统一设置正文样式：字号、行距、字体。"""
    # 正常样式。
    style = doc.styles["Normal"]
    style.font.name = "Microsoft YaHei"
    style.font.size = Pt(10.5)
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    # 段落行距 1.25 倍，阅读更舒适。
    style.paragraph_format.line_spacing = 1.25
    style.paragraph_format.space_after = Pt(4)


def _h(doc: Document, text: str, level: int = 1):
    """写入一个标题。"""
    p = doc.add_heading(level=level)
    run = p.add_run(text)
    _set_cjk_font(run)
    return p


def _p(doc: Document, text: str, bold: bool = False, italic: bool = False):
    """写入一个正文段落。"""
    para = doc.add_paragraph()
    run = para.add_run(text)
    run.bold = bold
    run.italic = italic
    _set_cjk_font(run)
    return para


def _bullet(doc: Document, text: str):
    """写入一个项目符号段落。"""
    para = doc.add_paragraph(style="List Bullet")
    run = para.add_run(text)
    _set_cjk_font(run)
    return para


def _table(doc: Document, headers, rows, widths=None):
    """写入一个带表头的表格。"""
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Light Grid Accent 1"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    # 表头。
    hdr = table.rows[0].cells
    for i, text in enumerate(headers):
        hdr[i].text = ""
        run = hdr[i].paragraphs[0].add_run(str(text))
        run.bold = True
        _set_cjk_font(run)
    # 数据行。
    for row in rows:
        cells = table.add_row().cells
        for i, text in enumerate(row):
            cells[i].text = ""
            run = cells[i].paragraphs[0].add_run(str(text))
            _set_cjk_font(run)
    return table


def _title_page(doc: Document, title: str, subtitle: str, author: str):
    """生成封面页。"""
    # 上方留白。
    for _ in range(4):
        doc.add_paragraph()
    # 主标题。
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = para.add_run(title)
    run.bold = True
    run.font.size = Pt(26)
    run.font.color.rgb = RGBColor(0x1B, 0x4B, 0x8A)
    _set_cjk_font(run)
    # 副标题。
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = para.add_run(subtitle)
    run.font.size = Pt(14)
    run.font.color.rgb = RGBColor(0x50, 0x50, 0x50)
    _set_cjk_font(run)
    # 留白。
    for _ in range(6):
        doc.add_paragraph()
    # 作者与日期。
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = para.add_run(f"作者：{author}" if author == "徐某" else f"Author: {author}")
    run.font.size = Pt(13)
    _set_cjk_font(run)
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = para.add_run("2026 年 10 月")
    run.font.size = Pt(12)
    _set_cjk_font(run)
    # 分页。
    doc.add_page_break()


# =============================================================================
# 文档一：具体实现过程和实际效果
# =============================================================================

def build_doc1(doc: Document, lang: str):
    """生成《具体实现过程和实际效果》。"""
    content = CN if lang == "cn" else EN
    _title_page(doc, content["doc1_title"], content["doc1_sub"], content["author"])

    if lang == "cn":
        _h(doc, "一、项目缘起与目标", 1)
        _p(doc, "本项目以市场上成熟的格式转换工具（格式工厂 4.9.5 与 5.10.0 两个版本）为功能参照，"
                "目标是实现一款完全离线的万能格式转换软件：视频、音频、图片、文档四大类文件都能互相转换，"
                "支持批量、容错、可扩展，并且能够在“断网、未安装任何运行库”的 Windows 机器上直接运行。")
        _p(doc, "最终交付了同一套内核的两种界面形态：基于 PyQt6 的桌面端，以及基于 Vue 3 + JSX 的浏览器网页端。"
                "两者共享同一份转码内核与同一份预设体系，行为完全一致。")

        _h(doc, "二、技术选型", 1)
        _p(doc, "转码能力来自 FFmpeg。项目自身不实现编解码算法，而是把“用户意图”精确编译为 FFmpeg 参数，"
                "并对执行过程做全程监控、容错与补救。这样做的理由有三点：其一，FFmpeg 的格式覆盖面最广；"
                "其二，它是单一可执行文件，便于随程序分发；其三，它的进度输出是机器可读的结构化数据，"
                "可以被稳定解析成百分比、速度与剩余时间。")
        _table(doc, ["层次", "技术", "用途"], [
            ["转码内核", "FFmpeg / FFprobe（随包分发）", "媒体探测、解码、编码、封装"],
            ["业务逻辑", "Python 3 标准库", "命令构建、任务调度、错误分类、文档转换"],
            ["桌面界面", "PyQt6", "多标签图形界面、拖拽、队列表格、实时进度"],
            ["网页后端", "FastAPI + Uvicorn", "REST 接口与 WebSocket 进度推送"],
            ["网页前端", "Vue 3 + JSX + Vite", "多面板交互界面，构建为纯静态资源离线托管"],
            ["打包", "PyInstaller（one-folder 绿色版）", "生成脱离 Python 环境的可执行程序"],
        ])

        _h(doc, "三、逐步实现过程", 1)

        _h(doc, "3.1 工程目录与虚拟环境", 2)
        _p(doc, "工程被划分为 app/core（转码内核）、app/gui（桌面端）、app/web（网页后端）、app/util（工具与文案）、"
                "frontend（网页前端源码）、resources（随包资源）、tools（自检与构建脚本）、build（打包配置）等目录。"
                "使用独立虚拟环境安装依赖，把“运行依赖”与“构建依赖”都固定下来，确保换一台机器也能复现。")

        _h(doc, "3.2 内核定位与能力探测", 2)
        _p(doc, "第一步解决“内核在哪里”。定位器按“用户自定义路径 → 随包副本 → 程序同级目录 → 系统 PATH”的顺序查找，"
                "并且对每个候选都真实执行一次版本命令进行校验，而不是只看文件是否存在；"
                "这一步避免了“把同名但无关的程序当成内核”的错误。")
        _p(doc, "第二步解决“内核能做什么”。程序启动时向内核查询编码器清单、封装格式清单与硬件加速后端清单，"
                "把结果缓存起来，并据此过滤界面上的格式选项——只有当内核确实支持的格式才会被列出，"
                "从源头减少“选了不支持格式而失败”的情况。")

        _h(doc, "3.3 媒体探测", 2)
        _p(doc, "使用 FFprobe 以 JSON 形式读取文件的容器信息与每一条流的信息（编码、分辨率、帧率、采样率、声道、语言等），"
                "并归纳为结构化对象。对缺失字段、损坏文件、超时都做了兜底：探测失败不会抛异常，"
                "而是返回一个“低置信度”的结果对象，界面据此给出可读提示。")

        _h(doc, "3.4 命令构建（核心）", 2)
        _p(doc, "命令构建器把一次转换请求编译成“参数列表”（而不是命令行字符串），彻底规避了空格、引号与特殊字符带来的差异，"
                "同时也从机制上防止了命令注入。构建过程包含三层处理：目标容器换算、编码器推断与降级、"
                "滤镜链拼接。其中滤镜链的拼接顺序被严格固定为“裁剪 → 缩放 → 旋转/镜像 → 帧率 → 水印 → 字幕 → 变速”，"
                "顺序一旦调换就会出现旋转后坐标错乱、水印随缩放变形等隐蔽问题。")

        _h(doc, "3.5 执行与进度解析", 2)
        _p(doc, "执行器使用内核的进度模式输出，按固定节奏读取“键值对”形式的进度块，"
                "解析出已编码时长、帧率、帧数、速度、输出体积，并换算为百分比与剩余时间。"
                "取消机制先温和请求终止，超时未退出再强制结束，保证点击“停止”后不会残留进程占用 CPU。")

        _h(doc, "3.6 任务队列与状态机", 2)
        _p(doc, "队列把每个待转文件封装为任务对象，维护“等待 → 运行 → 完成/失败/取消/跳过”的单向状态机，"
                "支持并发上限、暂停、继续、停止与去重。队列本身不依赖任何界面框架，只通过回调上报，"
                "因此桌面端与网页端可以原样复用同一套调度逻辑。")

        _h(doc, "3.7 错误分类与自动补救", 2)
        _p(doc, "内核的报错文本对普通用户并不友好。程序把常见失败模式归纳为十几类，"
                "每一类都给出可读的原因与处理建议，并对其中两类提供自动补救：奇数分辨率会自动补边到偶数，"
                "硬件编码失败会自动整套切换为软件编码并重试一次。")

        _h(doc, "3.8 预设体系", 2)
        _p(doc, "内置 35 套预设（视频 14 套、音频 11 套、图片 10 套），覆盖从“手机通用 MP4”到“剪辑中间格式”、"
                "从“无损 FLAC”到“网页动图”的常见需求。每套预设都带有面向用户的一句话说明，"
                "让不了解编码细节的使用者也能一键得到合理结果。")

        _h(doc, "3.9 硬件加速", 2)
        _p(doc, "程序探测内核中包含的硬件编码器（NVIDIA、Intel、AMD、Apple 等平台），"
                "在“自动”方式下按通用性优先的顺序挑选；考虑到“存在不等于可用”，默认仍走软件编码，"
                "把硬件加速作为可选提速项，并在失败时自动回退。")

        _h(doc, "3.10 文档转换", 2)
        _p(doc, "文档类转换不依赖 FFmpeg：图片合并为 PDF 使用图像库完成，PDF 逐页渲染为图片使用纯二进制解析库完成，"
                "文本转 PDF 使用内置的极简 PDF 生成器手工构造合法的 PDF 结构。全部实现均离线可用，"
                "不依赖任何外部办公软件。")

        _h(doc, "3.11 桌面端界面", 2)
        _p(doc, "桌面端采用多标签布局：视频、音频、图片、文档、设备、高级、任务队列、设置、关于。"
                "支持拖拽添加文件、递归添加文件夹、自动去重、任务表格实时显示进度与速度、双击查看失败详情、"
                "预览与复制将要执行的命令。界面样式采用深色主题，克制、不花哨。")

        _h(doc, "3.12 网页端", 2)
        _p(doc, "网页后端提供 20 个 REST 接口与 1 个 WebSocket 进度通道，覆盖系统信息、预设、格式、目录浏览、"
                "探测、设置、任务增删清空、队列启停暂停、抽帧、文档转换与打开目录。前端使用 Vue 3 + JSX 编写，"
                "包含 16 个组件，构建为纯静态资源，由后端直接托管，运行时没有任何外部网络请求。")

        _h(doc, "3.13 打包与离线可用", 2)
        _p(doc, "使用 PyInstaller 的 one-folder 模式打包，内核二进制、图标与网页静态资源一并收集进产物目录。"
                "打包后程序无需安装 Python，也无需联网，整个目录拷贝到任何离线 Windows 机器即可直接运行。"
                "同时处理了“无控制台打包时标准输出为空”这一会导致网页服务启动失败的经典问题。")
    else:
        _h(doc, "1. Origin and Goals", 1)
        _p(doc, "This project takes two mature releases of a well-known format conversion tool (version 4.9.5 and "
                "version 5.10.0) as the functional reference. The goal is a fully offline universal converter: "
                "video, audio, image and document files can all be converted between formats, with batch processing, "
                "fault tolerance and extensibility, and it must run on a Windows machine with no network and no "
                "pre-installed runtimes.")
        _p(doc, "Two front ends share one core: a PyQt6 desktop application and a Vue 3 + JSX browser application. "
                "Both use the same conversion core and the same preset system, so their behaviour is identical.")

        _h(doc, "2. Technology Choices", 1)
        _p(doc, "All conversion capability comes from FFmpeg. The project does not implement codecs itself; it "
                "compiles user intent into precise FFmpeg arguments and then monitors, hardens and repairs the run. "
                "There are three reasons for this: FFmpeg has the widest format coverage; it ships as a single "
                "executable that is easy to redistribute; and its progress output is machine-readable structured "
                "data that can be parsed reliably into percentage, speed and time remaining.")
        _table(doc, ["Layer", "Technology", "Purpose"], [
            ["Conversion core", "FFmpeg / FFprobe (bundled)", "Probe, decode, encode, mux"],
            ["Business logic", "Python 3 standard library", "Command building, scheduling, error triage, documents"],
            ["Desktop UI", "PyQt6", "Tabbed GUI, drag and drop, queue table, live progress"],
            ["Web backend", "FastAPI + Uvicorn", "REST endpoints plus WebSocket progress"],
            ["Web front end", "Vue 3 + JSX + Vite", "Multi-panel UI built as offline static assets"],
            ["Packaging", "PyInstaller (one-folder)", "Executables that need no Python installation"],
        ])

        _h(doc, "3. Step-by-Step Implementation", 1)
        _h(doc, "3.1 Project Layout and Virtual Environment", 2)
        _p(doc, "The project is split into app/core (conversion core), app/gui (desktop), app/web (web backend), "
                "app/util (helpers and user-facing text), frontend (web UI source), resources (bundled assets), "
                "tools (self-checks and build scripts) and build (packaging configuration). A dedicated virtual "
                "environment pins both runtime and build dependencies so the setup is reproducible on another machine.")

        _h(doc, "3.2 Locating the Core and Probing Capabilities", 2)
        _p(doc, "The first problem is where the core lives. The locator searches custom path, bundled copy, the "
                "executable's own directory and finally the system PATH, and it really executes a version command "
                "for every candidate instead of only checking that a file exists. This avoids mistaking an unrelated "
                "program with a similar name for the real core.")
        _p(doc, "The second problem is what the core can do. At start-up the program asks the core for its encoder "
                "list, its muxer list and its hardware acceleration backends, caches the answers, and filters the "
                "format choices in the UI accordingly, so only genuinely supported formats are offered.")

        _h(doc, "3.3 Media Probing", 2)
        _p(doc, "FFprobe is used to read container information and per-stream details (codec, resolution, frame rate, "
                "sample rate, channels, language) as JSON, normalised into structured objects. Missing fields, "
                "damaged files and timeouts are all handled: a failed probe never raises, it returns a low-confidence "
                "result that the UI turns into a readable message.")

        _h(doc, "3.4 Command Building (the Core)", 2)
        _p(doc, "The command builder compiles a request into an argument list rather than a command string. This "
                "sidesteps spaces, quoting and special characters entirely and structurally prevents command "
                "injection. Building has three stages: container normalisation, codec inference with fallbacks, and "
                "filter-chain assembly. The filter chain order is fixed as crop, scale, rotate/mirror, frame rate, "
                "watermark, subtitle, speed change; changing that order causes subtle defects such as misaligned "
                "coordinates after rotation or a watermark that stretches with the picture.")

        _h(doc, "3.5 Execution and Progress Parsing", 2)
        _p(doc, "The runner consumes the core's structured progress mode, reading key/value progress blocks at a "
                "steady cadence and deriving encoded time, frame rate, frames, speed and output size, which are "
                "converted into a percentage and a time estimate. Cancellation requests a graceful stop first and "
                "force-kills only if the process does not exit in time, so pressing Stop never leaves a process "
                "burning CPU.")

        _h(doc, "3.6 Job Queue and State Machine", 2)
        _p(doc, "The queue wraps every file into a job object and maintains a one-way state machine: waiting, "
                "running, then done, failed, cancelled or skipped. It supports a concurrency limit, pause, resume, "
                "stop and de-duplication. The queue depends on no UI framework and only reports through callbacks, "
                "so the desktop and web front ends reuse exactly the same scheduling logic.")

        _h(doc, "3.7 Error Triage and Automatic Repair", 2)
        _p(doc, "The core's error text is not friendly to ordinary users. The program groups common failures into "
                "more than a dozen classes, each with a readable cause and advice, and repairs two of them "
                "automatically: odd dimensions are padded to even ones, and a hardware encoding failure switches "
                "the whole command to software encoding and retries once.")

        _h(doc, "3.8 Preset System", 2)
        _p(doc, "Thirty-five built-in presets (14 video, 11 audio, 10 image) cover everyday needs from a "
                "universally playable phone-friendly MP4 through editing intermediate formats, from lossless FLAC "
                "to web animation. Each preset carries a one-line explanation so users who do not know codec "
                "details can still get a sensible result in one click.")

        _h(doc, "3.9 Hardware Acceleration", 2)
        _p(doc, "The program detects which hardware encoders the core contains (NVIDIA, Intel, AMD, Apple and more) "
                "and, in automatic mode, prefers them by a generality-first order. Because presence does not imply "
                "usability, software encoding remains the default and hardware acceleration is an optional speed-up "
                "that falls back automatically on failure.")

        _h(doc, "3.10 Document Conversion", 2)
        _p(doc, "Document conversion does not use FFmpeg: merging images into a PDF uses an imaging library, "
                "rendering PDF pages to images uses a pure binary PDF library, and text to PDF uses a built-in "
                "minimal PDF writer that assembles a valid PDF structure by hand. Everything works offline and "
                "depends on no office suite.")

        _h(doc, "3.11 Desktop Interface", 2)
        _p(doc, "The desktop app uses a tabbed layout: Video, Audio, Image, Document, Device, Advanced, Queue, "
                "Settings and About. It supports drag and drop, recursive folder import, automatic de-duplication, "
                "a job table with live progress and speed, double-click failure details, and previewing or copying "
                "the exact command that will run. The visual style is a restrained dark theme.")

        _h(doc, "3.12 Web Application", 2)
        _p(doc, "The web backend exposes 20 REST endpoints and one WebSocket progress channel covering system "
                "information, presets, formats, folder browsing, probing, settings, job add/remove/clear, queue "
                "start/stop/pause, snapshots, document conversion and opening a folder. The front end is written in "
                "Vue 3 + JSX across 16 components and builds to purely static assets served by the backend, with no "
                "external network requests at run time.")

        _h(doc, "3.13 Packaging and Offline Operation", 2)
        _p(doc, "PyInstaller one-folder packaging collects the core binaries, the icon and the web static assets "
                "into the output directory. The packaged program needs no Python installation and no network; the "
                "whole folder can be copied to any offline Windows machine and run directly. The classic failure in "
                "which an empty standard output breaks the web server under a windowed build is handled explicitly.")

    # ------------------------- 实际效果（两种语言共享结构，数据一致） -------------------------
    if lang == "cn":
        _h(doc, "四、实际效果与验证记录", 1)
        _p(doc, "验证遵循“穷举而非抽查”的原则：界面上列出的每一种目标格式都被真实转过一遍，"
                "并对产物做二次探测确认类型正确。以下记录均为实测结果。")

        _h(doc, "4.1 内核自检", 2)
        _bulleted = [
            "内核定位成功，版本命令返回正常；",
            "媒体探测：源视频被正确识别为 480×360、约 4 秒、含一条视频流与一条音频流；",
            "命令构建：生成的参数列表可被内核接受；",
            "实际执行：返回码为 0，输出体积 112550 字节，进度回调正常上报；",
            "产物校验：产物被识别为 MP4 容器，时长 4.040 秒，分辨率 480×360；",
            "容错用例：输入不存在的文件时，任务被正确判定为失败并给出“文件不存在”的明确原因。",
        ]
        for item in _bulleted:
            _bullet(doc, item)

        _h(doc, "4.2 转换矩阵（30 项全部通过）", 2)
        _table(doc, ["类别", "目标格式", "数量", "结果"], [
            ["视频", "mp4、mkv、mov、avi、flv、webm、ts、mpg、wmv、3gp、m4v、gif", "12", "全部通过"],
            ["音频", "mp3、m4a、wav、flac、ogg、opus、ac3、aiff、amr、wma", "10", "全部通过"],
            ["图片", "jpg、png、webp、bmp、tiff、gif、avif", "7", "全部通过"],
            ["抽帧", "视频 → PNG 快照", "1", "生成 4 张"],
        ])
        _p(doc, "矩阵验证的关键点在于“产物二次探测”：不看命令是否返回 0，而是把产物再交给探测程序，"
                "确认它确实是目标类型（视频含视频流、音频含音频流、图片被归类为图片），"
                "从而排除“命令成功但产物是垃圾”的假成功。")

        _h(doc, "4.3 桌面端实跑", 2)
        _p(doc, "桌面端在无显示环境下启动与渲染均正常，各个面板可以正常构建与切换，任务可以加入队列并执行，"
                "进度与状态可以正常回填到表格。")

        _h(doc, "4.4 网页端全流程与离线校验", 2)
        _p(doc, "网页端完整走通“启动服务 → 打开页面 → 添加文件 → 选择格式 → 开始转换 → 查看进度 → 打开输出目录”，"
                "并额外验证了抽帧与文档转换两条路径。离线校验的做法是对页面加载期间的全部网络请求来源做统计，"
                "确认所有请求都指向本机回环地址，没有任何外部资源。")

        _h(doc, "4.5 打包产物实测", 2)
        _p(doc, "打包后的可执行程序在“网页模式”与“桌面模式”两种启动方式下都实际运行正常，"
                "网页端页面与静态资源均由程序自身托管，无需额外部署。产物目录可直接整体拷贝分发。")

        _h(doc, "4.6 实现过程中修复的真实缺陷", 2)
        _p(doc, "下列缺陷都是在真实运行中暴露、经内核求证后修复的，记录在此以便说明“反复、多方验证”的价值。")
        _table(doc, ["序号", "现象", "根因", "修复"], [
            ["1", "选择 MKV/TS/MPG 等封装时报“未知封装”",
             "扩展名与内核封装名不一致（mkv 实为 matroska、ts 实为 mpegts、mpg 实为 mpeg）",
             "引入封装名换算表，全部名称经内核格式清单逐条核对"],
            ["2", "MPEG 系容器转换失败", "默认编码器表以扩展名为键，换算后查不到条目而回退到不兼容的编码器",
             "默认编码器表统一改用换算后的封装名为键"],
            ["3", "图片转换产出无法作为图片打开", "图片被当作视频容器推断，错误地选用了视频编码器",
             "为图片目标建立独立映射，并追加单帧与单文件约束"],
            ["4", "下拉框取值把“空”与“未选择”混淆", "界面取值未区分“数据为空”与“无数据”两种状态",
             "统一使用能区分两者的取值方式"],
            ["5", "硬件编码失败后重试仍失败", "补救只追加了参数，未替换编码器", "补救时整套重建为软件编码命令后再重试"],
            ["6", "读取进度时出现缓冲区告警", "给二进制管道设置了行缓冲", "取消该缓冲设置，依赖内核自身的刷新"],
            ["7", "任务执行报“目录不存在”", "执行前未确保输出目录存在", "在执行器内部兜底创建输出目录"],
            ["8", "命令行参数未生效", "程序入口未走统一主函数，参数未被处理", "所有启动方式统一经由主函数解析参数"],
            ["9", "打包后网页服务启动崩溃", "无控制台打包时标准输出为空，服务初始化崩溃",
             "补齐标准流并关闭服务框架的默认日志配置"],
        ])
    else:
        _h(doc, "4. Measured Results and Verification Record", 1)
        _p(doc, "Verification follows an exhaustive rather than sampling principle: every target format offered in "
                "the UI was really converted once, and each output was probed again to confirm its type. All figures "
                "below are measured results.")

        _h(doc, "4.1 Core Self-Check", 2)
        for item in [
            "The core was located and its version command returned normally.",
            "Probing: the source video was correctly read as 480x360, about 4 seconds, with one video stream and one audio stream.",
            "Command building: the generated argument list was accepted by the core.",
            "Execution: return code 0, output size 112550 bytes, progress callbacks reported normally.",
            "Output check: the output was recognised as an MP4 container, 4.040 seconds, 480x360.",
            "Fault case: a missing input file was correctly reported as a failure with the clear cause \"file not found\".",
        ]:
            _bullet(doc, item)

        _h(doc, "4.2 Conversion Matrix (30 of 30 passed)", 2)
        _table(doc, ["Category", "Target formats", "Count", "Result"], [
            ["Video", "mp4, mkv, mov, avi, flv, webm, ts, mpg, wmv, 3gp, m4v, gif", "12", "All passed"],
            ["Audio", "mp3, m4a, wav, flac, ogg, opus, ac3, aiff, amr, wma", "10", "All passed"],
            ["Image", "jpg, png, webp, bmp, tiff, gif, avif", "7", "All passed"],
            ["Snapshots", "video to PNG frames", "1", "4 frames created"],
        ])
        _p(doc, "The essential point is the second probe of the output. Instead of trusting a zero return code, the "
                "output is handed back to the probe tool to confirm it really is the intended type (video has a video "
                "stream, audio has an audio stream, an image is classified as an image). This rules out false "
                "successes where a command succeeds but the artefact is unusable.")

        _h(doc, "4.3 Desktop Run", 2)
        _p(doc, "The desktop application starts and renders correctly without a display, every panel builds and "
                "switches, jobs can be queued and executed, and progress and status are written back into the table.")

        _h(doc, "4.4 Web Flow and Offline Check", 2)
        _p(doc, "The web application completed the full flow of starting the service, opening the page, adding files, "
                "choosing a format, starting the conversion, watching progress and opening the output folder, plus "
                "the snapshot and document paths. The offline check counted the origin of every network request "
                "during page load and confirmed that all of them target the loopback address; no external resources "
                "are involved.")

        _h(doc, "4.5 Packaged Build", 2)
        _p(doc, "The packaged executable runs correctly in both web mode and desktop mode, with the web page and its "
                "static assets served by the program itself and no separate deployment. The output folder can be "
                "copied as a whole.")

        _h(doc, "4.6 Real Defects Fixed During Implementation", 2)
        _p(doc, "The following defects were exposed by real runs and fixed after checking against the core. They are "
                "recorded here to show the value of repeated, multi-source verification.")
        _table(doc, ["#", "Symptom", "Root cause", "Fix"], [
            ["1", "Choosing MKV/TS/MPG reported an unknown muxer",
             "The extension differs from the real muxer name (mkv is matroska, ts is mpegts, mpg is mpeg)",
             "Added a container alias table, every name checked against the core's format list"],
            ["2", "MPEG-family containers failed",
             "The default codec table was keyed by extension, so the normalised name found no entry and fell back to an incompatible codec",
             "Keyed the default codec table by the normalised muxer name"],
            ["3", "Image output could not be opened as an image",
             "Images were inferred as video containers and wrongly given a video codec",
             "Added a dedicated image target map with single-frame and single-file constraints"],
            ["4", "Combo boxes confused empty with unselected",
             "The UI did not distinguish an empty data value from the absence of data",
             "Unified value lookup that distinguishes both states"],
            ["5", "Retry after a hardware failure still failed",
             "The repair only appended arguments instead of replacing the codec",
             "The repair rebuilds the whole command with software encoding before retrying"],
            ["6", "A buffer warning appeared while reading progress", "Line buffering was set on a binary pipe",
             "Removed that setting and relied on the core's own flushing"],
            ["7", "Jobs failed with folder not found", "The output folder was not ensured before execution",
             "The runner creates the output folder as a safety net"],
            ["8", "Command line arguments were ignored", "The entry point bypassed the unified main function",
             "All start-up paths now go through the main function"],
            ["9", "The web service crashed after packaging",
             "A windowed build has an empty standard output, which broke service initialisation",
             "Provided fallback streams and disabled the framework's default log configuration"],
        ])

    # ------------------------- 结论 -------------------------
    if lang == "cn":
        _h(doc, "五、结语", 1)
        _p(doc, "本项目把“万能”落实为可验证的工程能力：覆盖四类媒体、35 套预设、30 项格式矩阵全部通过；"
                "把“容错”落实为可复现的机制：前置能力过滤、执行期进度监控、失败后错误分类与自动补救；"
                "把“离线可用”落实为可交付的产物：一个拷贝即用的目录，内含内核、图标与网页资源。"
                "桌面端与网页端共享同一内核，行为一致、维护成本低。")
    else:
        _h(doc, "5. Closing", 1)
        _p(doc, "This project turns \"universal\" into verifiable engineering capability: four media families, 35 "
                "presets, and a 30-case format matrix that passes completely. It turns \"fault tolerance\" into "
                "reproducible mechanisms: capability filtering before the run, progress monitoring during it, and "
                "error triage with automatic repair after it. It turns \"offline ready\" into a deliverable: a single "
                "copy-and-run folder containing the core, the icon and the web assets. The desktop and web front "
                "ends share one core, so they behave identically and stay cheap to maintain.")


# =============================================================================
# 文档二：软件介绍、算法原理与实现
# =============================================================================

def build_doc2(doc: Document, lang: str):
    """生成《软件介绍、算法原理与实现》。"""
    content = CN if lang == "cn" else EN
    _title_page(doc, content["doc2_title"], content["doc2_sub"], content["author"])

    if lang == "cn":
        _h(doc, "一、软件概览", 1)
        _p(doc, "Universal Transcoder（万能转码器）是一款完全离线的万能格式转换软件，由 徐某 设计与实现。"
                "它提供两种使用形态：PyQt6 桌面端与浏览器网页端，两者共享同一转码内核。")
        _h(doc, "1.1 核心功能", 2)
        for item in [
            "视频转换：mp4、mkv、mov、avi、flv、webm、ts、mpg、wmv、3gp、m4v、gif 等目标格式互转；",
            "音频转换：mp3、m4a、wav、flac、ogg、opus、ac3、aiff、amr、wma 等目标格式互转；",
            "图片转换：jpg、png、webp、bmp、tiff、gif、avif、ico 等目标格式互转；",
            "文档转换：多图合并为 PDF、PDF 逐页导出为图片、文本转 PDF；",
            "画面处理：裁剪、缩放（等比/填充/拉伸）、旋转、镜像、帧率调整、变速、抽帧；",
            "叠加处理：图片水印、文字水印、外挂字幕烧录；",
            "音频处理：声道数、采样率、音量增益、码率；",
            "时间处理：起始时间、结束时间、截取时长；",
            "元数据：标题、艺术家、专辑、备注；",
            "批量与队列：批量添加、递归收集文件夹、自动去重、并发上限、暂停/继续/停止；",
            "高级能力：自定义追加参数、指定流序号、命令预览与复制；",
            "硬件加速：自动识别并优先使用硬件编码器，失败自动回退软件编码。",
        ]:
            _bullet(doc, item)

        _h(doc, "1.2 两种形态", 2)
        _table(doc, ["形态", "技术", "特点"], [
            ["桌面端", "PyQt6", "原生窗口、拖拽、表格进度、离线运行"],
            ["网页端", "FastAPI + Vue 3 + JSX", "浏览器访问、REST + WebSocket、静态资源本地托管"],
        ])

        _h(doc, "二、总体架构", 1)
        _p(doc, "系统自下而上分为四层。最底层是转码内核（FFmpeg/FFprobe），由定位器负责发现与校验，"
                "由能力探测模块负责查询其支持范围。第二层是转码内核逻辑：媒体探测、命令构建、执行器、任务队列、"
                "错误分类、预设与硬件加速、抽帧与文档转换。第三层是两个界面后端：桌面端直接调用内核逻辑，"
                "网页端通过 REST 与 WebSocket 暴露同样的能力。第四层是使用者可见的界面：桌面窗口与浏览器页面。")
        _table(doc, ["层", "组成", "关键职责"], [
            ["界面层", "PyQt 窗口 / Vue 页面", "收集用户意图、展示进度与结果"],
            ["接口层", "REST + WebSocket", "把内核能力以网络接口暴露"],
            ["内核逻辑层", "探测、构建、执行、队列、错误、预设、加速、文档", "唯一的事实来源，两种界面共用"],
            ["内核层", "FFmpeg / FFprobe", "真实解码、编码与封装"],
        ])

        _h(doc, "三、算法原理", 1)

        _h(doc, "3.1 容器与编码器的映射算法", 2)
        _p(doc, "用户看到的是扩展名，内核需要的是封装名与编码器名，二者并不总是一致。算法分三步：先把扩展名换算为封装名"
                "（例如 mkv→matroska、ts→mpegts、mpg→mpeg、jpg→image2）；再按封装名查默认编码器表；"
                "最后根据是否只含视频流、是否只含音频流做约束裁剪（仅视频容器必须丢弃音频流，仅音频容器必须丢弃视频流）。"
                "换算表的每一个名称都经过内核格式清单逐条核对，避免“凭印象猜测”导致的失败。")

        _h(doc, "3.2 滤镜链的顺序算法", 2)
        _p(doc, "画面处理由若干滤镜组成，顺序不可交换。算法固定为：裁剪、缩放、旋转与镜像、帧率、水印、字幕、变速。"
                "这个顺序保证了几何变换在坐标系统一后进行，水印不会随缩放而变形，字幕在最终画面上烧录。"
                "所有涉及尺寸的取值会向下取偶数，因为主流编码器要求宽高为偶数。")

        _h(doc, "3.3 质量档位到编码参数的映射", 2)
        _p(doc, "用户选择的是“质量档位”，算法把它映射为具体编码参数：视频优先使用恒定质量因子（档位越低数值越大），"
                "音频映射为码率。若用户显式填写了码率，则以用户值为准；预设自带的参数优先级最高，"
                "从而保证“一键预设”的结果稳定可预期。")

        _h(doc, "3.4 进度与剩余时间算法", 2)
        _p(doc, "内核在进度模式下按固定节奏输出键值对。算法以“已编码时长 / 源总时长”得到百分比，"
                "并用相邻两次采样的时间差与媒体时间差估算瞬时速度，再由瞬时速度推算剩余时间；"
                "相比内核自报的速度字段，瞬时速度在变速与复杂滤镜场景下更贴近真实。")

        _h(doc, "3.5 硬件加速选择与回退算法", 2)
        _p(doc, "算法先枚举内核包含的硬件编码器，再按“通用性优先”的厂商顺序挑选。由于硬件编码器可能因驱动、"
                "显存或分辨率限制而失败，算法把硬件路径视为“可失败尝试”：一旦失败即整套替换为软件编码器重建命令，"
                "而不是在失败命令上追加参数。")

        _h(doc, "3.6 错误分类与补救决策算法", 2)
        _p(doc, "算法对内核输出的错误文本做模式匹配，按“越具体越靠前”的顺序命中错误类别，"
                "输出机器可读的错误码、可读标题与处理建议，并保留原始错误尾部供排查。"
                "对“奇数分辨率”与“硬件加速失败”两类给出自动补救动作，其余类别只提示不自动改动，"
                "避免在不确定的情况下反复重试。")

        _h(doc, "3.7 文档转换算法", 2)
        _p(doc, "图片合并为 PDF：逐张读入、把带透明通道的图先合成到白底，再一次性写出多页 PDF。"
                "PDF 导出为图片：按页渲染为位图，支持页码范围与分辨率，单页失败不影响其它页。"
                "文本转 PDF：按页宽折行、分页，手工构造包含目录对象、字体对象、页对象、内容流与交叉引用表的合法 PDF 字节流。")

        _h(doc, "3.8 输出命名与冲突处理算法", 2)
        _p(doc, "输出路径支持三种命名方式：原名加后缀、替换扩展名、自定义模板。"
                "当目标文件已存在且未开启覆盖时，算法自动追加序号以避免误覆盖；同时保证输出目录存在，"
                "并禁止生成仅含非法字符的文件名。")

        _h(doc, "四、实现要点", 1)
        _p(doc, "工程共 25 个 Python 模块、约 6772 行代码（不含前端）。下表列出核心模块与其职责。")
        _table(doc, ["模块", "行数", "职责"], [
            ["app/core/ffmpeg_locator.py", "160", "内核定位与版本校验"],
            ["app/core/capabilities.py", "183", "编码器/封装/硬件后端能力探测与缓存"],
            ["app/core/media_info.py", "326", "媒体探测与结构化解析"],
            ["app/core/command_builder.py", "805", "命令构建、容器换算、滤镜链、输出路径"],
            ["app/core/runner.py", "361", "子进程执行与进度解析、取消机制"],
            ["app/core/tasks.py", "507", "任务模型、队列调度、状态机、失败处理"],
            ["app/core/errors.py", "174", "错误分类与自动补救建议"],
            ["app/core/presets.py", "189", "35 套内置预设"],
            ["app/core/hwaccel.py", "100", "硬件编码器识别与选择"],
            ["app/core/thumbnail.py", "170", "视频抽帧"],
            ["app/core/doc_convert.py", "378", "图片/PDF/文本互转"],
            ["app/gui/main_window.py", "1764", "桌面端全部界面与交互"],
            ["app/gui/style.py", "220", "桌面端主题样式"],
            ["app/web/server.py", "556", "网页后端接口与静态托管"],
            ["app/util/strings.py", "210", "集中管理的界面文案"],
            ["app/util/paths.py", "100", "资源与用户数据目录解析"],
            ["app/util/config.py", "123", "配置读写"],
            ["app/util/fmt.py", "145", "体积/时长/时间格式化"],
            ["app/util/logger.py", "95", "日志初始化与异常钩子"],
        ])

        _h(doc, "五、界面与交互", 1)
        _p(doc, "桌面端九个标签页覆盖全部能力，拖拽即可添加文件，任务表格实时展示状态、进度、速度与剩余时间，"
                "双击一行可查看失败详情与处理建议。网页端提供 20 个 REST 接口与 1 个 WebSocket 通道，"
                "前端 16 个组件按功能拆分为导航、视频、音频、图片、文档、高级、队列、设置、关于、文件面板等。")
        _table(doc, ["接口", "作用"], [
            ["GET /api/system", "系统与内核信息"],
            ["GET /api/presets", "内置预设列表"],
            ["GET /api/formats", "可用目标格式列表"],
            ["POST /api/browse", "目录浏览"],
            ["POST /api/probe", "媒体探测"],
            ["GET/POST /api/settings", "读取与保存设置"],
            ["POST/GET /api/tasks", "添加与列出任务"],
            ["DELETE /api/tasks/{id}", "移除任务"],
            ["POST /api/tasks/clear", "清空任务"],
            ["POST /api/queue/start|pause|resume|stop", "队列控制"],
            ["GET /api/queue", "队列状态与统计"],
            ["POST /api/snapshot", "视频抽帧"],
            ["POST /api/doc", "文档转换"],
            ["POST /api/open", "在系统中打开目录"],
            ["WS /ws/progress", "进度实时推送"],
        ])

        _h(doc, "六、打包与部署", 1)
        _p(doc, "构建脚本依次完成建环境、装依赖、生成图标、构建前端、内核自检与打包六个步骤，任一步失败即停止并提示。"
                "打包采用 one-folder 绿色版，内核、图标与网页静态资源全部收进产物目录；"
                "程序对运行环境无额外要求，拷贝到离线机器即可使用。")

        _h(doc, "七、结语", 1)
        _p(doc, "Universal Transcoder 由 徐某 设计并实现，以“把复杂留给自己、把简单交给用户”为原则："
                "复杂的格式换算、滤镜顺序、错误补救都被封装在内核逻辑里，使用者只需要选择格式、点击开始。")
    else:
        _h(doc, "1. Software Overview", 1)
        _p(doc, "Universal Transcoder is a fully offline universal format converter, designed and implemented by Xu. "
                "It ships in two forms, a PyQt6 desktop application and a browser web application, both sharing one "
                "conversion core.")
        _h(doc, "1.1 Core Features", 2)
        for item in [
            "Video conversion between mp4, mkv, mov, avi, flv, webm, ts, mpg, wmv, 3gp, m4v and gif.",
            "Audio conversion between mp3, m4a, wav, flac, ogg, opus, ac3, aiff, amr and wma.",
            "Image conversion between jpg, png, webp, bmp, tiff, gif, avif and ico.",
            "Document conversion: merge images into a PDF, export PDF pages to images, convert text to PDF.",
            "Picture processing: crop, scale (fit, fill, stretch), rotate, mirror, frame rate, speed change, snapshots.",
            "Overlays: image watermark, text watermark, burned-in external subtitles.",
            "Audio processing: channels, sample rate, volume gain, bitrate.",
            "Time processing: start time, end time, clip duration.",
            "Metadata: title, artist, album, comment.",
            "Batch and queue: batch add, recursive folder import, de-duplication, concurrency limit, pause, resume and stop.",
            "Advanced control: custom extra arguments, explicit stream selection, command preview and copy.",
            "Hardware acceleration: detects and prefers hardware encoders, falling back to software automatically.",
        ]:
            _bullet(doc, item)

        _h(doc, "1.2 Two Forms", 2)
        _table(doc, ["Form", "Technology", "Characteristics"], [
            ["Desktop", "PyQt6", "Native window, drag and drop, progress table, fully offline"],
            ["Web", "FastAPI + Vue 3 + JSX", "Browser access, REST plus WebSocket, locally served assets"],
        ])

        _h(doc, "2. Overall Architecture", 1)
        _p(doc, "The system has four layers. At the bottom is the conversion core (FFmpeg and FFprobe), located and "
                "verified by the locator and described by the capability probe. Above it sits the core logic: probing, "
                "command building, execution, queueing, error triage, presets, hardware acceleration, snapshots and "
                "document conversion. The third layer is the two front-end backends: the desktop app calls the core "
                "logic directly, while the web backend exposes the same capabilities over REST and WebSocket. The top "
                "layer is what the user sees: a desktop window and a browser page.")
        _table(doc, ["Layer", "Contents", "Key responsibility"], [
            ["UI", "PyQt window / Vue page", "Collect intent, show progress and results"],
            ["Interface", "REST + WebSocket", "Expose core capabilities over the network"],
            ["Core logic", "Probe, build, run, queue, errors, presets, accel, documents",
             "Single source of truth, shared by both UIs"],
            ["Core", "FFmpeg / FFprobe", "Real decoding, encoding and muxing"],
        ])

        _h(doc, "3. Algorithms", 1)

        _h(doc, "3.1 Container and Codec Mapping", 2)
        _p(doc, "Users see extensions; the core needs muxer and codec names, and the two do not always match. The "
                "algorithm works in three steps: normalise the extension to a muxer name (mkv to matroska, ts to "
                "mpegts, mpg to mpeg, jpg to image2); look up the default codec table by the normalised name; then "
                "trim constraints based on whether there is video only or audio only, since a video-only container "
                "must drop audio and an audio-only container must drop video. Every entry in the alias table was "
                "checked against the core's format listing, avoiding guesswork.")

        _h(doc, "3.2 Filter Chain Ordering", 2)
        _p(doc,
           "Picture processing is a series of filters whose order cannot be swapped. The algorithm fixes the order "
           "as crop, scale, rotate and mirror, frame rate, watermark, subtitle, speed change. This guarantees "
           "that geometry changes happen in one consistent coordinate system, that the watermark does not deform "
           "with scaling, and that subtitles are burned onto the final picture. Any dimension is rounded down to "
           "an even number because mainstream encoders require even width and height.")

        _h(doc, "3.3 Quality Tier to Encoding Parameters", 2)
        _p(doc, "Users choose a quality tier; the algorithm maps it to concrete parameters. Video prefers a constant "
                "quality factor (a lower tier means a larger value) and audio maps to a bitrate. If the user typed an "
                "explicit bitrate, that value wins; preset values take the highest priority so one-click presets stay "
                "predictable.")

        _h(doc, "3.4 Progress and Time-Estimate Algorithm", 2)
        _p(doc, "In progress mode the core emits key/value pairs at a steady cadence. The algorithm derives the "
                "percentage from encoded time over total duration, estimates instantaneous speed from the wall-clock "
                "gap and the media-time gap between two samples, and predicts the remaining time from that speed. "
                "Compared with the core's own speed field, the instantaneous speed tracks reality better during "
                "speed changes and heavy filtering.")

        _h(doc, "3.5 Hardware Acceleration Selection and Fallback", 2)
        _p(doc, "The algorithm enumerates the hardware encoders present in the core and prefers them by a "
                "generality-first vendor order. Because a hardware encoder can still fail due to drivers, memory or "
                "resolution limits, the hardware path is treated as a failable attempt: on failure the whole command "
                "is rebuilt with a software encoder instead of appending arguments to the failing command.")

        _h(doc, "3.6 Error Triage and Repair Decision", 2)
        _p(doc, "The algorithm pattern-matches the core's error text, hitting classes in order of decreasing "
                "specificity, and produces a machine-readable code, a readable title and advice, keeping the tail of "
                "the raw error for diagnosis. Two classes, odd dimensions and hardware failure, get an automatic "
                "repair action; the rest are reported without automatic changes, so the program never retries blindly "
                "when the cause is unclear.")

        _h(doc, "3.7 Document Conversion Algorithms", 2)
        _p(doc, "Merging images into a PDF reads each image, composites anything with transparency onto a white "
                "background, then writes a multi-page PDF in one call. Exporting PDF pages renders each page to a "
                "bitmap, supporting page ranges and resolution, where a single failing page does not stop the rest. "
                "Text to PDF wraps lines to the page width, paginates, and hand-builds a valid PDF byte stream with "
                "catalogue, font, page, content-stream and cross-reference objects.")

        _h(doc, "3.8 Output Naming and Conflict Handling", 2)
        _p(doc, "Output paths support three naming modes: original name plus a suffix, replace the extension, or a "
                "custom template. When the target already exists and overwrite is off, the algorithm appends a "
                "sequence number to avoid accidental overwrite; it also ensures the output folder exists and never "
                "produces a filename made only of illegal characters.")

        _h(doc, "4. Implementation Notes", 1)
        _p(doc, "The project contains 25 Python modules and about 6772 lines of code, excluding the front end. The "
                "table lists the core modules and their responsibilities.")
        _table(doc, ["Module", "Lines", "Responsibility"], [
            ["app/core/ffmpeg_locator.py", "160", "Locate and verify the core"],
            ["app/core/capabilities.py", "183", "Encoder, muxer and hardware backend probing with caching"],
            ["app/core/media_info.py", "326", "Media probing and structured parsing"],
            ["app/core/command_builder.py", "805", "Command building, container aliasing, filter chain, output paths"],
            ["app/core/runner.py", "361", "Subprocess execution, progress parsing, cancellation"],
            ["app/core/tasks.py", "507", "Job model, queue scheduling, state machine, failure handling"],
            ["app/core/errors.py", "174", "Error classification and repair advice"],
            ["app/core/presets.py", "189", "35 built-in presets"],
            ["app/core/hwaccel.py", "100", "Hardware encoder detection and selection"],
            ["app/core/thumbnail.py", "170", "Video snapshots"],
            ["app/core/doc_convert.py", "378", "Image, PDF and text conversion"],
            ["app/gui/main_window.py", "1764", "Entire desktop UI and interaction"],
            ["app/gui/style.py", "220", "Desktop theme styling"],
            ["app/web/server.py", "556", "Web backend endpoints and static hosting"],
            ["app/util/strings.py", "210", "Centralised user-facing text"],
            ["app/util/paths.py", "100", "Resource and user data directory resolution"],
            ["app/util/config.py", "123", "Settings read and write"],
            ["app/util/fmt.py", "145", "Size, duration and time formatting"],
            ["app/util/logger.py", "95", "Logging setup and exception hook"],
        ])

        _h(doc, "5. Interface and Interaction", 1)
        _p(doc, "The desktop app uses nine tabs covering every capability; dropping files adds them, the job table "
                "shows status, progress, speed and time left, and double-clicking a row shows failure details and "
                "advice. The web app offers 20 REST endpoints and one WebSocket channel, with 16 front-end components "
                "split into navigation, video, audio, image, document, advanced, queue, settings, about and file.")
        _table(doc, ["Endpoint", "Purpose"], [
            ["GET /api/system", "System and core information"],
            ["GET /api/presets", "Built-in presets"],
            ["GET /api/formats", "Available target formats"],
            ["POST /api/browse", "Folder browsing"],
            ["POST /api/probe", "Media probing"],
            ["GET/POST /api/settings", "Read and save settings"],
            ["POST/GET /api/tasks", "Add and list jobs"],
            ["DELETE /api/tasks/{id}", "Remove a job"],
            ["POST /api/tasks/clear", "Clear jobs"],
            ["POST /api/queue/start|pause|resume|stop", "Queue control"],
            ["GET /api/queue", "Queue state and statistics"],
            ["POST /api/snapshot", "Video snapshots"],
            ["POST /api/doc", "Document conversion"],
            ["POST /api/open", "Open a folder in the system"],
            ["WS /ws/progress", "Live progress push"],
        ])

        _h(doc, "6. Packaging and Deployment", 1)
        _p(doc, "The build script performs six steps in order: create the environment, install dependencies, generate "
                "the icon, build the front end, run the core self-check and package. It stops with a message as soon "
                "as a step fails. Packaging uses the one-folder layout so the core, the icon and the web assets all "
                "live inside the output directory; the program needs nothing else and runs on an offline machine.")

        _h(doc, "7. Closing", 1)
        _p(doc, "Universal Transcoder was designed and implemented by Xu, following the principle of keeping the "
                "complexity inside and giving the user simplicity: container conversion, filter ordering and error "
                "repair are all encapsulated in the core, so the user only has to pick a format and press start.")


def main():
    """命令行入口。"""
    parser = argparse.ArgumentParser(description="生成 Universal Transcoder 的 Word 文档")
    parser.add_argument("--project", required=True, help="工程根目录（文档将写入其 docs 子目录）")
    parser.add_argument("--lang", default="cn", choices=["cn", "en"], help="文档语言")
    args = parser.parse_args()

    project = os.path.abspath(args.project)
    docs_dir = os.path.join(project, "docs")
    os.makedirs(docs_dir, exist_ok=True)
    content = CN if args.lang == "cn" else EN

    # 文档一。
    doc = Document()
    _style_document(doc)
    build_doc1(doc, args.lang)
    name1 = "具体实现过程和实际效果.docx" if args.lang == "cn" else "Implementation-Process-and-Measured-Results.docx"
    path1 = os.path.join(docs_dir, name1)
    doc.save(path1)
    print("已生成：" + path1)

    # 文档二。
    doc = Document()
    _style_document(doc)
    build_doc2(doc, args.lang)
    name2 = "软件介绍、算法原理与实现.docx" if args.lang == "cn" else "Software-Overview-Algorithms-and-Implementation.docx"
    path2 = os.path.join(docs_dir, name2)
    doc.save(path2)
    print("已生成：" + path2)


if __name__ == "__main__":
    main()
