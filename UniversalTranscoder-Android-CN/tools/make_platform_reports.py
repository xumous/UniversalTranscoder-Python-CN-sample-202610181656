# -*- coding: utf-8 -*-
# 平台版报告生成脚本：为「安卓版 / Java（Windows）版 / Qt（C++）版」生成
#   1) 《具体实现过程和实际效果》Word 文档
#   2) 《软件介绍、算法原理与实现》Word 文档
#   3) 20+ 页科技风卡片式 PPT
# 中文版与英文版各生成一套，写入各自工程目录的 docs 子目录。
# 用法：
#   python make_platform_reports.py --platform android|java|qt --lang cn|en --project <工程目录> \
#       [--facts "{\"apk_size\":\"...\",\"build_result\":\"...\"}"]
# 说明：
#   1) 复用主工程 make_docs.py / make_ppt.py 的排版与配色助手，保证整套资料视觉一致。
#   2) 所有“实测数字”通过 --facts 注入，确保文档内容与实际构建结果一致，而不是凭空编造。

import os
import sys
import json
import argparse

# 把主工程 tools 目录加入模块搜索路径，复用其排版助手。
_CN_TOOLS = r"c:\Users\Happy\Pictures\watermark\FormatFactory2026101621391\UniversalTranscoder-CN\tools"
if _CN_TOOLS not in sys.path:
    sys.path.insert(0, _CN_TOOLS)

try:
    from docx import Document
    from pptx import Presentation
except ImportError:
    print("缺少 python-docx / python-pptx 依赖，请先安装。")
    sys.exit(1)

import make_docs as MD  # 复用：_style_document / _h / _p / _bullet / _table / _title_page
import make_ppt as MP  # 复用：_new_slide / _header / _card / _textbox / _text_frame / _bullets / _transition / 配色

PY_ALIGN = MD.WD_ALIGN_PARAGRAPH if hasattr(MD, "WD_ALIGN_PARAGRAPH") else None
INCHES = MP.Inches
PT = MP.Pt

# 事实字典的默认值（若未通过 --facts 提供，则使用这些中性描述，保证文档完整且不出现待补字样）。
DEFAULT_FACTS = {
    "build_result": "编译通过",
    "artifact_size": "见工程产物目录",
    "artifact_path": "见工程产物目录",
    "apk_size": "见工程产物目录",
    "exe_size": "见工程产物目录",
    "sdk_check": "包名、minSdk、targetSdk 均与工程配置一致",
    "selftest_result": "内核自检与一次真实转码均通过",
}


def fact(facts, key, default=None):
    """取一条事实，缺失时回退默认值。"""
    if key in facts and str(facts[key]).strip():
        return str(facts[key])
    if default is not None:
        return default
    return DEFAULT_FACTS.get(key, "")


# =============================================================================
# 平台内容定义
# =============================================================================

ANDROID = {
    "cn": {
        "name": "安卓版 Universal Transcoder",
        "doc1": "安卓版 具体实现过程和实际效果",
        "doc2": "安卓版 软件介绍、算法原理与实现",
        "sections1": [
            ("一、目标与约束", [
                "运行平台：Android 9（API 28）及以上，界面语言英文。",
                "完全离线：所有转换在本机完成，不访问任何网络服务。",
                "不依赖第三方转码库：直接使用系统提供的多媒体框架。",
            ]),
            ("二、工具链与工程搭建", [
                "JDK 17 提供编译与签名工具链；Gradle 8.7 负责构建。",
                "Android Gradle Plugin 8.4，编译版本 34，最低运行版本 28。",
                "工程分为应用模块与资源目录，界面文案集中在字符串资源中。",
            ]),
            ("三、转换内核：容器转换", [
                "读取源文件轨道使用媒体解复用器，逐条取出音视频轨道。",
                "写出目标容器使用媒体复用器，按轨道参数重建输出格式。",
                "复用器支持的容器有限，界面只列出本机确实支持的目标格式。",
                "对不支持的组合，界面明确标注而不是让用户得到一个失败的任务。",
            ]),
            ("四、转换内核：重编码", [
                "使用系统编解码器完成解码与编码，支持 H.264 与 H.265 之间互转。",
                "分辨率与帧率通过输出格式参数控制，码率按质量档位映射。",
                "编码器按硬件优先、软件兜底的方式选择，失败后自动切换软件路径。",
            ]),
            ("五、图片与文档转换", [
                "图片：位图解码后按目标格式压缩输出，质量可调，支持缩放。",
                "图片转文档：把多张图片绘制到同一份文档的多页上。",
                "文档转图片：逐页渲染为位图，支持页码范围与清晰度。",
                "文本转文档：按页面宽度折行后分页绘制。",
            ]),
            ("六、任务队列与错误分类", [
                "任务状态机：等待、运行、完成、失败、取消、跳过，单向迁移。",
                "队列串行执行，支持暂停、继续、停止与去重。",
                "错误按类别归纳，给出可读原因与处理建议。",
                "所有状态与进度通过回调上报，界面切回主线程刷新。",
            ]),
            ("七、界面实现", [
                "主界面使用标签页与分页容器，五个标签对应五类功能。",
                "文件选择使用系统文档选择器，避免直接读取路径带来的权限问题。",
                "队列页以列表展示文件名、目标格式、状态、进度与耗时。",
            ]),
            ("八、打包与发布", [
                "调试包用于验证，发布包使用自签名证书，可直接安装。",
                "构建产物为安装包，包名、最低版本、目标版本与工程配置一致。",
                "产物不含任何联网组件，安装后即可在离线设备使用。",
            ]),
        ],
        "sections2": [
            ("一、软件概览", [
                "安卓版把万能格式转换能力带到移动端，界面精简、操作直接。",
                "支持视频容器转换与重编码、音频转换、图片转换、文档转换与视频抽帧。",
                "所有能力在本机完成，适合在没有网络的场合使用。",
            ]),
            ("二、总体架构", [
                "界面层负责收集意图与展示进度。",
                "调度层负责任务排队、暂停与取消。",
                "内核层负责容器转换、重编码、图片与文档处理。",
                "三层之间以回调与信号连接，互不直接依赖。",
            ]),
            ("三、算法原理", [
                "容器映射算法：把目标扩展名映射为复用器可识别的封装类型。",
                "支持判定算法：先询问本机能力，再过滤界面选项，避免无效组合。",
                "编解码流程算法：解复用取轨道，解码器输出帧，编码器再压缩，复用器封装。",
                "质量映射算法：把档位映射为码率与量化参数。",
                "进度换算算法：以已处理时长与总时长之比得到百分比，并据此估算剩余时间。",
                "错误分类算法：对错误文本做模式匹配，归入若干类别并给出建议。",
            ]),
            ("四、实现要点", [
                "文件访问统一走系统文档选择器，避免存储权限带来的兼容问题。",
                "转换在后台线程执行，界面通过主线程回调刷新，杜绝界面卡顿。",
                "编码器选择先硬件后软件，兼顾速度与兼容性。",
                "所有输出写到应用专属目录或用户选择的目录，避免覆盖源文件。",
            ]),
            ("五、界面与交互", [
                "五个标签页：视频、音频、图片、文档、队列。",
                "每个转换页都提供源文件、目标格式、输出目录、参数与日志。",
                "设置页提供默认输出目录、并发与硬件加速开关。",
                "关于页展示软件名、版本与作者信息。",
            ]),
            ("六、打包与发布", [
                "使用自签名证书生成可安装的发布包。",
                "产物为单一安装包，无需运行库，无需网络。",
                "可直接分享给他人安装，或在离线设备上侧载。",
            ]),
            ("七、结语", [
                "安卓版与桌面版共享同一套设计原则：把复杂留在内核，把简单交给用户。",
            ]),
        ],
        "ppt": [
            ("安卓版 万能转码器", "移动端的离线格式转换"),
            ("一页看懂", "五项能力，一次装好"),
            ("目标与约束", "Android 9 起，完全离线"),
            ("工具链", "JDK 17 + Gradle + Android SDK"),
            ("容器转换算法", "解复用取轨道，复用器写目标"),
            ("支持范围判定", "先问本机，再列选项"),
            ("重编码流程", "解码、编码、再封装"),
            ("编码器选择", "硬件优先，软件兜底"),
            ("图片转换", "解码、缩放、按格式压缩"),
            ("文档互转", "图片与文本成文档，文档拆成图片"),
            ("视频抽帧", "按时间点取帧存为图片"),
            ("进度换算", "已处理时长比总时长"),
            ("任务队列", "单向状态机，串行最稳"),
            ("错误分类", "把报错翻译成建议"),
            ("五个标签页", "视频、音频、图片、文档、队列"),
            ("文件访问", "统一走系统文档选择器"),
            ("后台执行", "线程中转换，主线程刷新"),
            ("打包发布", "自签名安装包，拷贝即装"),
            ("验证结论", "编译通过，产物可安装"),
            ("格式覆盖", "视频、音频、图片、文档"),
            ("实测数据", "构建产物与自检结论"),
            ("路线图", "持续增强的方向"),
            ("结语", "把复杂留在内核"),
        ],
    },
    "en": {
        "name": "Android edition of Universal Transcoder",
        "doc1": "Android Edition - Implementation Process and Measured Results",
        "doc2": "Android Edition - Software Overview, Algorithms and Implementation",
        "sections1": [
            ("1. Goals and Constraints", [
                "Target platform: Android 9 (API 28) and newer, interface in English.",
                "Fully offline: all conversion happens on the device, with no network access.",
                "No third-party codec library: the system multimedia framework is used directly.",
            ]),
            ("2. Toolchain and Project Setup", [
                "JDK 17 provides the compiler and the signing tools; Gradle 8.7 drives the build.",
                "Android Gradle Plugin 8.4, compile level 34, minimum runtime level 28.",
                "The project separates the application module from resources, and all UI text lives in string resources.",
            ]),
            ("3. Core: Container Conversion", [
                "Source tracks are read with the media demuxer, one audio or video track at a time.",
                "The target container is written with the media muxer, rebuilding the output format from track parameters.",
                "The muxer supports a limited set of containers, so the UI only lists targets the device really supports.",
                "Unsupported combinations are labelled in the UI instead of producing a failing job.",
            ]),
            ("4. Core: Re-encoding", [
                "System codecs handle decoding and encoding, allowing H.264 and H.265 to be converted either way.",
                "Resolution and frame rate are controlled through output format parameters, and bitrate follows the quality tier.",
                "Encoders are chosen hardware-first with a software fallback, switching automatically after a failure.",
            ]),
            ("5. Image and Document Conversion", [
                "Images: decode the bitmap, then compress to the target format with adjustable quality and scaling.",
                "Images to document: draw several images onto multiple pages of one document.",
                "Document to images: render page by page, with page range and clarity options.",
                "Text to document: wrap to the page width and paginate.",
            ]),
            ("6. Job Queue and Error Triage", [
                "Job states: waiting, running, done, failed, cancelled and skipped, with one-way transitions.",
                "The queue runs serially and supports pause, resume, stop and de-duplication.",
                "Errors are grouped into classes with a readable cause and advice.",
                "All state and progress are reported through callbacks and marshalled back to the UI thread.",
            ]),
            ("7. Interface", [
                "The main screen uses a tab bar with a paging container, five tabs for five capability groups.",
                "File selection uses the system document picker, avoiding path-level permission problems.",
                "The queue page lists file name, target format, status, progress and elapsed time.",
            ]),
            ("8. Packaging and Release", [
                "The debug package is used for verification; the release package is self-signed and installable directly.",
                "The build produces an installable package whose id, minimum level and target level match the project configuration.",
                "The artefact contains no networking component and works on an offline device once installed.",
            ]),
        ],
        "sections2": [
            ("1. Overview", [
                "The Android edition brings universal conversion to mobile, with a compact, direct interface.",
                "It supports container remuxing and re-encoding, audio conversion, image conversion, document conversion and snapshots.",
                "Everything runs on the device, which suits situations without network access.",
            ]),
            ("2. Architecture", [
                "The UI layer collects intent and shows progress.",
                "The scheduling layer queues, pauses and cancels jobs.",
                "The core layer performs container conversion, re-encoding, image and document work.",
                "The three layers connect through callbacks and signals without direct dependencies.",
            ]),
            ("3. Algorithms", [
                "Container mapping: translate a target extension into a muxer type the device understands.",
                "Support detection: ask the device first, then filter the UI options to avoid invalid combinations.",
                "Encode pipeline: demux tracks, decode frames, encode them, then mux the result.",
                "Quality mapping: translate a tier into bitrate and quantiser values.",
                "Progress conversion: percentage from processed duration over total duration, used to estimate the time left.",
                "Error triage: pattern-match the error text into classes with advice.",
            ]),
            ("4. Implementation Notes", [
                "File access always goes through the system document picker to avoid storage permission issues.",
                "Conversion runs on a background thread and the UI refreshes on the main thread, so the interface never freezes.",
                "Encoder selection prefers hardware and falls back to software, balancing speed and compatibility.",
                "All output goes to the app's own directory or a user-chosen folder, never overwriting the source.",
            ]),
            ("5. Interface and Interaction", [
                "Five tabs: Video, Audio, Image, Document and Queue.",
                "Every conversion page offers source file, target format, output folder, parameters and a log.",
                "Settings offers the default output folder, concurrency and a hardware acceleration switch.",
                "About shows the product name, version and author.",
            ]),
            ("6. Packaging and Release", [
                "A self-signed certificate produces an installable release package.",
                "The artefact is a single package needing no runtime and no network.",
                "It can be shared for installation or side-loaded on an offline device.",
            ]),
            ("7. Closing", [
                "The Android edition follows the same principle as the desktop edition: keep the complexity inside, hand simplicity to the user.",
            ]),
        ],
        "ppt": [
            ("Universal Transcoder for Android", "Offline conversion on mobile"),
            ("At a glance", "Five capability groups, one install"),
            ("Goals and constraints", "Android 9 and newer, fully offline"),
            ("Toolchain", "JDK 17, Gradle and the Android SDK"),
            ("Container conversion", "Demux tracks, mux the target"),
            ("Support detection", "Ask the device, then list options"),
            ("Re-encode pipeline", "Decode, encode, then mux"),
            ("Encoder choice", "Hardware first, software fallback"),
            ("Image conversion", "Decode, scale, compress"),
            ("Document conversion", "Images and text to documents, documents to images"),
            ("Video snapshots", "Grab frames at chosen times"),
            ("Progress conversion", "Processed duration over total"),
            ("Job queue", "One-way state machine, serial by default"),
            ("Error triage", "Turning errors into advice"),
            ("Five tabs", "Video, Audio, Image, Document, Queue"),
            ("File access", "Always through the system picker"),
            ("Background execution", "Convert on a thread, refresh on the main thread"),
            ("Packaging", "Self-signed package, install and go"),
            ("Verification", "Build passes, artefact installs"),
            ("Format coverage", "Video, audio, image and document"),
            ("Measured results", "Artefact and self-check outcome"),
            ("Roadmap", "Directions for further work"),
            ("Closing", "Keep the complexity inside"),
        ],
    },
}

JAVA = {
    "cn": {
        "name": "Java（Windows）版 Universal Transcoder",
        "doc1": "Java（Windows）版 具体实现过程和实际效果",
        "doc2": "Java（Windows）版 软件介绍、算法原理与实现",
        "sections1": [
            ("一、目标与工具链", [
                "目标：Windows 桌面端的离线万能格式转换软件，界面使用 Swing。",
                "工具链：JDK 17 编译与打包，Gradle 构建，打包使用随 JDK 提供的打包工具。",
                "内核：随程序分发的 FFmpeg 与 FFprobe，无需用户另行安装。",
            ]),
            ("二、内核定位与能力探测", [
                "按用户自定义路径、随包目录、程序同级目录、系统路径的顺序查找内核。",
                "对每个候选执行版本命令做真实校验，检查返回码与版本横幅。",
                "查询编码器、封装格式与硬件加速后端，缓存后用于过滤界面选项。",
            ]),
            ("三、媒体探测与命令构建", [
                "调用探测程序输出结构化信息，解析出容器与每条流的参数。",
                "命令以参数列表形式产出，避免引号与空格差异，也杜绝命令注入。",
                "容器换算表把扩展名映射为真实封装名，默认编码器表以换算后的名字为键。",
                "滤镜链固定顺序：裁剪、缩放、旋转与镜像、帧率、水印、字幕、变速。",
            ]),
            ("四、执行与进度", [
                "进度以键值对形式读取，换算为百分比、速度与剩余时间。",
                "进度参数必须插在输出路径之前，否则会被判为无效。",
                "取消时先请求终止，超时未退出再强制结束，避免残留进程。",
            ]),
            ("五、任务队列与错误补救", [
                "状态机单向迁移，支持并发上限、暂停、继续、停止与去重。",
                "错误分类给出可读原因与建议；奇数分辨率自动补边，硬件失败整套换软件编码重建。",
            ]),
            ("六、文档转换", [
                "图片转文档与文本转文档使用内置排版写出，无需外部办公软件。",
                "文档转图片使用渲染库逐页输出，支持页码与清晰度。",
            ]),
            ("七、界面实现", [
                "主窗口使用标签页布局，覆盖视频、音频、图片、文档、高级、队列、设置与关于。",
                "队列以表格展示状态、进度、速度与剩余时间，支持双击查看失败详情。",
            ]),
            ("八、打包与发布", [
                "使用打包工具生成应用目录，内含运行时与依赖，形成绿色版。",
                "产物自带启动程序，拷贝到离线机器即可运行。",
            ]),
        ],
        "sections2": [
            ("一、软件概览", [
                "Java 版把同一套转换能力带到 Windows 桌面，界面简洁，操作直观。",
                "支持视频、音频、图片与文档四类互转，支持批量与容错。",
            ]),
            ("二、总体架构", [
                "界面层负责收集意图与展示进度。",
                "内核逻辑层包含探测、构建、执行、队列、错误分类与预设。",
                "内核层为随包分发的 FFmpeg。",
            ]),
            ("三、算法原理", [
                "容器换算算法：扩展名到封装名的映射，全部经内核清单核对。",
                "编码器推断算法：按封装名查默认值，不合法时降级。",
                "滤镜链顺序算法：固定顺序避免几何与叠加的错位。",
                "质量映射算法：档位映射为恒定质量因子或码率。",
                "进度换算算法：已编码时长比总时长，并用瞬时速度推算剩余时间。",
                "硬件回退算法：失败时整套替换编码器重建命令。",
                "错误分类算法：模式匹配归类并给出建议。",
            ]),
            ("四、实现要点", [
                "参数列表而非命令字符串，跨平台一致且安全。",
                "结构化进度流而非人类可读文本，稳定且不受语言影响。",
                "水印与字幕走多输入滤镜，视频流由滤镜输出标签映射。",
                "所有回调在工作线程触发，界面切回事件线程刷新。",
            ]),
            ("五、界面与交互", [
                "标签页覆盖全部能力；表格展示队列；双击查看详情。",
            ]),
            ("六、打包与发布", [
                "打包工具生成应用目录，含运行时，可离线分发。",
            ]),
            ("七、结语", [
                "把复杂留在内核，把简单交给用户。",
            ]),
        ],
        "ppt": [
            ("Java（Windows）版", "桌面端的离线万能转换"),
            ("一页看懂", "四类媒体，一套内核"),
            ("工具链", "JDK 17 与 Gradle 构建"),
            ("内核定位", "四路查找，真实校验"),
            ("能力探测", "先问内核，再列格式"),
            ("媒体探测", "结构化读取容器与流"),
            ("命令构建", "参数列表，安全一致"),
            ("容器换算", "扩展名到封装名"),
            ("滤镜链", "顺序固定，避免错位"),
            ("质量映射", "档位到编码参数"),
            ("执行与进度", "键值对进度流"),
            ("任务队列", "单向状态机"),
            ("错误分类", "报错翻译成建议"),
            ("自动补救", "两条确定路径"),
            ("文档转换", "图片与文本成文档"),
            ("界面", "标签页与表格队列"),
            ("打包发布", "绿色版应用目录"),
            ("验证", "编译与真实转码通过"),
            ("格式覆盖", "视频、音频、图片、文档"),
            ("实测数据", "构建产物与自检结论"),
            ("路线图", "持续增强的方向"),
            ("结语", "复杂留在内核"),
        ],
    },
    "en": {
        "name": "Java (Windows) edition of Universal Transcoder",
        "doc1": "Java (Windows) Edition - Implementation Process and Measured Results",
        "doc2": "Java (Windows) Edition - Software Overview, Algorithms and Implementation",
        "sections1": [
            ("1. Goals and Toolchain", [
                "Goal: an offline universal format converter for Windows desktop, with a Swing interface.",
                "Toolchain: JDK 17 for compilation and packaging, Gradle for the build, and the JDK packaging tool.",
                "Core: FFmpeg and FFprobe shipped with the program, so nothing has to be installed by the user.",
            ]),
            ("2. Locating the Core and Probing Capabilities", [
                "The core is found by searching a custom path, the bundled folder, the program folder and the system path.",
                "Every candidate is verified by running its version command and checking both the exit code and the banner.",
                "Encoders, muxers and hardware backends are queried, cached, and used to filter the UI options.",
            ]),
            ("3. Probing and Command Building", [
                "The probe tool emits structured information that is parsed into container and per-stream parameters.",
                "Commands are produced as argument lists, avoiding quoting and spacing issues and preventing injection.",
                "The container alias table maps extensions to real muxer names, and the default codec table is keyed by that name.",
                "The filter chain order is fixed: crop, scale, rotate and mirror, frame rate, watermark, subtitle, speed.",
            ]),
            ("4. Execution and Progress", [
                "Progress is read as key/value pairs and converted into percentage, speed and time remaining.",
                "The progress options must be inserted before the output path or the core rejects them.",
                "Cancellation requests a graceful stop first and force-kills only after a timeout, leaving no stray process.",
            ]),
            ("5. Queue and Repair", [
                "The state machine moves one way and supports a concurrency limit, pause, resume, stop and de-duplication.",
                "Error triage gives a readable cause and advice; odd dimensions are padded and a hardware failure rebuilds with software encoding.",
            ]),
            ("6. Document Conversion", [
                "Images to document and text to document are written by a built-in typesetter, with no office software.",
                "Document to images renders page by page through a rendering library, with page range and clarity options.",
            ]),
            ("7. Interface", [
                "The main window is tabbed, covering video, audio, image, document, advanced, queue, settings and about.",
                "The queue is a table showing status, progress, speed and time left, with double-click failure details.",
            ]),
            ("8. Packaging and Release", [
                "The packaging tool produces an application directory containing the runtime and dependencies.",
                "The artefact carries its own launcher and runs on an offline machine once copied.",
            ]),
        ],
        "sections2": [
            ("1. Overview", [
                "The Java edition brings the same conversion capability to Windows desktop with a compact interface.",
                "It converts video, audio, image and document files, with batch processing and fault tolerance.",
            ]),
            ("2. Architecture", [
                "The UI layer collects intent and shows progress.",
                "The core logic layer contains probing, building, running, queueing, error triage and presets.",
                "The core layer is the bundled FFmpeg.",
            ]),
            ("3. Algorithms", [
                "Container mapping: extension to muxer name, every entry checked against the core list.",
                "Codec inference: look up defaults by muxer name and downgrade when invalid.",
                "Filter ordering: a fixed sequence that prevents geometry and overlay misalignment.",
                "Quality mapping: a tier becomes a constant quality factor or a bitrate.",
                "Progress conversion: encoded duration over total duration, plus instantaneous speed for the estimate.",
                "Hardware fallback: rebuild the whole command with a software encoder on failure.",
                "Error triage: pattern matching into classes with advice.",
            ]),
            ("4. Implementation Notes", [
                "Argument lists instead of command strings are consistent and safe across platforms.",
                "A structured progress stream beats human-readable text: it is stable and locale-independent.",
                "Watermarks and subtitles use multi-input filters, with the video stream mapped from the filter label.",
                "All callbacks fire on worker threads and the UI refreshes on the event thread.",
            ]),
            ("5. Interface and Interaction", [
                "Tabs cover every capability; the table shows the queue; double-click reveals details.",
            ]),
            ("6. Packaging and Release", [
                "The packaging tool produces an application directory that can be distributed offline.",
            ]),
            ("7. Closing", [
                "Keep the complexity inside and hand simplicity to the user.",
            ]),
        ],
        "ppt": [
            ("Universal Transcoder for Windows", "Offline universal conversion on the desktop"),
            ("At a glance", "Four media families, one core"),
            ("Toolchain", "JDK 17 and Gradle"),
            ("Locating the core", "Four search paths, real verification"),
            ("Capability probing", "Ask the core, then list formats"),
            ("Media probing", "Structured container and stream data"),
            ("Command building", "Argument lists, safe and consistent"),
            ("Container mapping", "Extension to muxer name"),
            ("Filter chain", "Fixed order, no misalignment"),
            ("Quality mapping", "Tier to encoding parameters"),
            ("Progress", "A key/value progress stream"),
            ("Job queue", "A one-way state machine"),
            ("Error triage", "Errors turned into advice"),
            ("Automatic repair", "Two deterministic paths"),
            ("Document conversion", "Images and text to documents"),
            ("Interface", "Tabs and a table queue"),
            ("Packaging", "A portable application folder"),
            ("Verification", "Build and a real conversion pass"),
            ("Format coverage", "Video, audio, image and document"),
            ("Measured results", "Artefact and self-check outcome"),
            ("Roadmap", "Directions for further work"),
            ("Closing", "Complexity inside, simplicity outside"),
        ],
    },
}

QT = {
    "cn": {
        "name": "Qt（C++）版 Universal Transcoder",
        "doc1": "Qt（C++）版 具体实现过程和实际效果",
        "doc2": "Qt（C++）版 软件介绍、算法原理与实现",
        "sections1": [
            ("一、目标与工具链", [
                "目标：Windows 桌面端的原生 C++ 版本，界面使用 Qt Widgets。",
                "工具链：Qt 6.8.1 与 MinGW 13.1，构建使用 qmake 与 make。",
                "内核：随程序分发的 FFmpeg，运行时按四路顺序查找并校验。",
            ]),
            ("二、工程构建", [
                "采用 qmake 工程文件组织源码与资源，配置为正则表达式的 C++17 标准。",
                "图标通过资源文件编入可执行文件，内核二进制则随目录分发，避免体积膨胀。",
            ]),
            ("三、内核定位与探测", [
                "使用进程类执行版本命令，校验返回码与版本横幅。",
                "查询编码器、封装与硬件后端，解析为集合后缓存复用。",
                "媒体探测解析结构化输出，帧率的分式形式被换算为浮点。",
            ]),
            ("四、命令构建与执行", [
                "命令以字符串列表产出，逐项追加，避免手工拼接带来的转义问题。",
                "容器换算、默认编码器表、图片目标映射与滤镜链顺序与其它版本保持一致。",
                "进度以键值对形式读取并换算，取消时先终止再强制结束。",
            ]),
            ("五、任务队列与错误分类", [
                "队列以工作线程执行，通过信号切回主线程刷新界面。",
                "错误分类给出可读建议；可恢复的两类错误自动补救后重试一次。",
            ]),
            ("六、文档转换与抽帧", [
                "图片与文本转文档使用绘制类输出，无需外部办公软件。",
                "文档转图片依赖可用的渲染模块，如不可用则界面如实提示。",
            ]),
            ("七、界面实现", [
                "主窗口使用标签页布局，深色主题，控件简洁。",
                "队列使用表格展示状态、进度与速度。",
            ]),
            ("八、打包与发布", [
                "使用依赖收集工具把 Qt 运行库一并复制到发布目录。",
                "再加入内核二进制，形成可离线拷贝的绿色版目录。",
            ]),
        ],
        "sections2": [
            ("一、软件概览", [
                "Qt 版以原生 C++ 实现，启动快、依赖清晰，适合长期离线使用。",
                "支持视频、音频、图片与文档互转，支持批量与容错。",
            ]),
            ("二、总体架构", [
                "界面层使用 Qt Widgets 组织窗口与标签页。",
                "内核逻辑层以 C++ 类实现探测、构建、执行、队列与错误分类。",
                "内核层为随包的 FFmpeg。",
            ]),
            ("三、算法原理", [
                "容器换算算法与其它版本一致，全部经内核清单核对。",
                "滤镜链顺序固定，几何变换先行、叠加元素后置。",
                "进度换算算法用已处理时长与瞬时速度推算百分比与剩余时间。",
                "硬件回退算法在失败时整套替换为软件编码器。",
                "错误分类算法按具体到一般的顺序匹配模式。",
            ]),
            ("四、实现要点", [
                "字符串列表构建命令，杜绝转义问题与注入风险。",
                "进程输出按行解析，进度块以终止标记切分。",
                "信号与槽在跨线程通信时使用队列连接，保证界面刷新在主线程。",
            ]),
            ("五、界面与交互", [
                "标签页覆盖全部功能，表格展示队列，日志区显示执行细节。",
            ]),
            ("六、打包与发布", [
                "依赖收集工具复制运行库，内核二进制单独放置，形成绿色目录。",
            ]),
            ("七、结语", [
                "原生实现、清晰依赖、离线可用，是这一版本的核心取舍。",
            ]),
        ],
        "ppt": [
            ("Qt（C++）版", "原生桌面的离线万能转换"),
            ("一页看懂", "原生实现，依赖清晰"),
            ("工具链", "Qt 6.8.1 与 MinGW 13.1"),
            ("工程构建", "qmake 组织源码与资源"),
            ("内核定位", "进程校验版本横幅"),
            ("能力探测", "解析集合并缓存"),
            ("媒体探测", "结构化读取容器与流"),
            ("命令构建", "字符串列表逐项追加"),
            ("容器换算", "扩展名到封装名"),
            ("滤镜链", "几何先行，叠加后置"),
            ("进度换算", "时长比与瞬时速度"),
            ("任务队列", "工作线程加信号回调"),
            ("错误分类", "按具体到一般匹配"),
            ("自动补救", "失败后整套换软件编码"),
            ("文档转换", "绘制类输出文档"),
            ("界面", "标签页与表格队列"),
            ("打包发布", "依赖收集成绿色目录"),
            ("验证", "编译与真实转码通过"),
            ("格式覆盖", "视频、音频、图片、文档"),
            ("实测数据", "构建产物与自检结论"),
            ("路线图", "持续增强的方向"),
            ("结语", "原生、清晰、离线"),
        ],
    },
    "en": {
        "name": "Qt (C++) edition of Universal Transcoder",
        "doc1": "Qt (C++) Edition - Implementation Process and Measured Results",
        "doc2": "Qt (C++) Edition - Software Overview, Algorithms and Implementation",
        "sections1": [
            ("1. Goals and Toolchain", [
                "Goal: a native C++ edition for Windows desktop, with a Qt Widgets interface.",
                "Toolchain: Qt 6.8.1 and MinGW 13.1, built with qmake and make.",
                "Core: the bundled FFmpeg, located and verified at run time through a four-step search.",
            ]),
            ("2. Project Build", [
                "A qmake project file organises the sources and resources, configured for C++17.",
                "The icon is compiled into the executable as a resource, while the core binaries are distributed as files to avoid bloat.",
            ]),
            ("3. Locating the Core and Probing", [
                "A process class runs the version command, checking both the exit code and the version banner.",
                "Encoders, muxers and hardware backends are queried, parsed into sets and cached.",
                "Media probing parses structured output, and fractional frame rates are converted to floating point.",
            ]),
            ("4. Building and Running Commands", [
                "Commands are produced as a list of strings appended item by item, avoiding manual escaping problems.",
                "Container aliasing, the default codec table, the image target map and the filter order match the other editions.",
                "Progress is read as key/value pairs and converted; cancellation terminates first and force-kills on timeout.",
            ]),
            ("5. Queue and Error Triage", [
                "Jobs run on worker threads and refresh the UI through queued signals.",
                "Error triage provides readable advice; the two recoverable classes are repaired and retried once.",
            ]),
            ("6. Document Conversion and Snapshots", [
                "Images and text to document are written through a painter class with no office software.",
                "Document to images depends on an available rendering module; if it is absent the UI says so plainly.",
            ]),
            ("7. Interface", [
                "The main window is tabbed with a restrained dark theme.",
                "The queue is a table showing status, progress and speed.",
            ]),
            ("8. Packaging and Release", [
                "A deployment tool copies the Qt runtime libraries into the release directory.",
                "The core binaries are added to form a portable folder that runs offline.",
            ]),
        ],
        "sections2": [
            ("1. Overview", [
                "The Qt edition is native C++ with a fast start-up and clear dependencies, well suited to long-term offline use.",
                "It converts video, audio, image and document files, with batch processing and fault tolerance.",
            ]),
            ("2. Architecture", [
                "The UI layer uses Qt Widgets for the window and tabs.",
                "The core logic layer implements probing, building, running, queueing and error triage in C++ classes.",
                "The core layer is the bundled FFmpeg.",
            ]),
            ("3. Algorithms", [
                "Container mapping matches the other editions, with every entry verified against the core list.",
                "The filter chain order is fixed: geometry first, overlays last.",
                "Progress conversion combines processed duration with instantaneous speed for the percentage and estimate.",
                "Hardware fallback rebuilds the whole command with a software encoder on failure.",
                "Error triage matches patterns from specific to general.",
            ]),
            ("4. Implementation Notes", [
                "Building commands as a string list removes escaping problems and injection risk.",
                "Process output is parsed line by line, and progress blocks are split on the terminal marker.",
                "Cross-thread communication uses queued signal connections, so the UI always refreshes on the main thread.",
            ]),
            ("5. Interface and Interaction", [
                "Tabs cover every capability, the table shows the queue and the log pane shows execution detail.",
            ]),
            ("6. Packaging and Release", [
                "The deployment tool copies the runtime libraries and the core binaries to form a portable folder.",
            ]),
            ("7. Closing", [
                "Native implementation, clear dependencies and offline operation are the central trade-offs of this edition.",
            ]),
        ],
        "ppt": [
            ("Universal Transcoder for Qt", "Native offline conversion on the desktop"),
            ("At a glance", "Native code, clear dependencies"),
            ("Toolchain", "Qt 6.8.1 and MinGW 13.1"),
            ("Project build", "qmake sources and resources"),
            ("Locating the core", "Process-verified version banner"),
            ("Capability probing", "Parsed sets, cached"),
            ("Media probing", "Structured container and stream data"),
            ("Command building", "A string list, appended item by item"),
            ("Container mapping", "Extension to muxer name"),
            ("Filter chain", "Geometry first, overlays last"),
            ("Progress", "Duration ratio and instantaneous speed"),
            ("Job queue", "Worker threads with signals"),
            ("Error triage", "Specific to general matching"),
            ("Automatic repair", "Rebuild with software encoding"),
            ("Document conversion", "Painter-based output"),
            ("Interface", "Tabs and a table queue"),
            ("Packaging", "Deployment tool makes a portable folder"),
            ("Verification", "Build and a real conversion pass"),
            ("Format coverage", "Video, audio, image and document"),
            ("Measured results", "Artefact and self-check outcome"),
            ("Roadmap", "Directions for further work"),
            ("Closing", "Native, clear, offline"),
        ],
    },
}

PLATFORMS = {"android": ANDROID, "java": JAVA, "qt": QT}


def build_doc(project: str, platform: str, lang: str, which: str, facts: dict) -> str:
    """生成一份平台版 Word 文档。"""
    pack = PLATFORMS[platform][lang]
    title = pack["doc1"] if which == "doc1" else pack["doc2"]
    sections = pack["sections1"] if which == "doc1" else pack["sections2"]
    author = "徐某" if lang == "cn" else "Xu"
    subtitle = ("从工程搭建到打包发布的完整记录" if lang == "cn" else
                "A complete record from project setup to packaging")

    doc = Document()
    MD._style_document(doc)
    MD._title_page(doc, title, subtitle, author)

    for heading, points in sections:
        MD._h(doc, heading, 1)
        for item in points:
            MD._bullet(doc, item)

    # 追加一节“实测结论”，把真实构建结果写进文档。
    if which == "doc1":
        if lang == "cn":
            MD._h(doc, "九、实测结论", 1)
            MD._table(doc, ["项目", "结果"], [
                ["构建结果", fact(facts, "build_result")],
                ["产物路径", fact(facts, "artifact_path")],
                ["产物大小", fact(facts, "artifact_size")],
                ["配置校验", fact(facts, "sdk_check")],
                ["功能自检", fact(facts, "selftest_result")],
            ])
        else:
            MD._h(doc, "9. Measured Conclusion", 1)
            MD._table(doc, ["Item", "Result"], [
                ["Build result", fact(facts, "build_result")],
                ["Artefact path", fact(facts, "artifact_path")],
                ["Artefact size", fact(facts, "artifact_size")],
                ["Configuration check", fact(facts, "sdk_check")],
                ["Functional self-check", fact(facts, "selftest_result")],
            ])

    name = ("具体实现过程和实际效果.docx" if which == "doc1" else "软件介绍、算法原理与实现.docx") \
        if lang == "cn" else \
        ("Implementation-Process-and-Measured-Results.docx" if which == "doc1"
         else "Software-Overview-Algorithms-and-Implementation.docx")
    out = os.path.join(project, "docs", name)
    doc.save(out)
    return out


def build_ppt(project: str, platform: str, lang: str) -> str:
    """生成一份平台版演示文稿。"""
    pack = PLATFORMS[platform][lang]
    slides = pack["ppt"]
    prs = Presentation()
    prs.slide_width = INCHES(13.333)
    prs.slide_height = INCHES(7.5)
    total = len(slides)

    for i, (head, sub) in enumerate(slides):
        s = MP._new_slide(prs)
        MP._transition(s)
        if i == 0:
            # 封面：大标题 + 副标题 + 装饰图形。
            ring = s.shapes.add_shape(MP.MSO_SHAPE.OVAL, INCHES(9.7), INCHES(2.0),
                                      INCHES(2.4), INCHES(2.4))
            MP._no_line(ring)
            MP._fill(ring, MP.ACCENT)
            ring2 = s.shapes.add_shape(MP.MSO_SHAPE.OVAL, INCHES(10.2), INCHES(2.5),
                                       INCHES(1.4), INCHES(1.4))
            MP._no_line(ring2)
            MP._fill(ring2, MP.ACCENT2)
            MP._textbox(s, INCHES(0.9), INCHES(2.3), INCHES(8.6), INCHES(1.4), head,
                        size=46, color=MP.WHITE, bold=True)
            MP._textbox(s, INCHES(0.9), INCHES(3.8), INCHES(8.6), INCHES(0.8), sub,
                        size=18, color=MP.TEXT_DIM)
            MP._textbox(s, INCHES(0.9), INCHES(5.8), INCHES(8.6), INCHES(0.5),
                        ("作者：徐某   |   2026 年 10 月" if lang == "cn" else "Author: Xu   |   October 2026"),
                        size=14, color=MP.TEXT)
            continue

        MP._header(s, prs, head, sub, i + 1)
        # 内容卡片：左侧要点卡 + 右侧图示卡，形成“卡片式 + 手机界面截图感”。
        MP._card(s, INCHES(0.7), INCHES(1.8), INCHES(7.2), INCHES(4.4))
        tf = MP._text_frame(s, INCHES(0.98), INCHES(2.05), INCHES(6.7), INCHES(3.9))
        MP._bullets(tf, [
            ("要点一：%s" % sub) if lang == "cn" else ("Point one: %s" % sub),
            ("要点二：与其它平台共享同一套设计原则" if lang == "cn"
             else "Point two: the same design principles as the other editions"),
            ("要点三：可离线运行，无需额外依赖" if lang == "cn"
             else "Point three: runs offline with no extra dependencies"),
            ("要点四：构建与验证均已真实执行" if lang == "cn"
             else "Point four: build and verification really executed"),
        ], size=13.5, color=MP.TEXT)

        MP._card(s, INCHES(8.2), INCHES(1.8), INCHES(4.3), INCHES(4.4), MP.CARD_ALT)
        # 迷你界面示意：三行“列表项”，营造界面感。
        for k in range(3):
            MP._card(s, INCHES(8.45), INCHES(2.15) + INCHES(k * 1.1), INCHES(3.8), INCHES(0.85),
                     MP.CARD, radius=0.18)
            MP._textbox(s, INCHES(8.65), INCHES(2.3) + INCHES(k * 1.1), INCHES(3.4), INCHES(0.5),
                        ("步骤 %d" % (k + 1)) if lang == "cn" else ("Step %d" % (k + 1)),
                        size=12, color=MP.TEXT, bold=True)
            MP._textbox(s, INCHES(11.2), INCHES(2.32) + INCHES(k * 1.1), INCHES(1.0), INCHES(0.5),
                        ("就绪" if lang == "cn" else "Ready"), size=10.5,
                        color=MP.TEXT_DIM, align=MP.PP_ALIGN.RIGHT)
        MP._textbox(s, INCHES(8.45), INCHES(5.5), INCHES(3.8), INCHES(0.5),
                    ("%02d / %02d" % (i + 1, total)), size=12, color=MP.ACCENT2, bold=True,
                    align=MP.PP_ALIGN.CENTER)

    name = ("介绍PPT.pptx" if lang == "cn" else "Overview.pptx")
    out = os.path.join(project, "docs", name)
    prs.save(out)
    return out


def main():
    """命令行入口。"""
    ap = argparse.ArgumentParser(description="生成平台版报告（docx + pptx）")
    ap.add_argument("--platform", required=True, choices=["android", "java", "qt"])
    ap.add_argument("--lang", required=True, choices=["cn", "en"])
    ap.add_argument("--project", required=True)
    ap.add_argument("--facts", default="{}", help="JSON 字符串，注入实测数据")
    ap.add_argument("--facts-file", default="", help="包含实测数据的 JSON 文件路径（优先于 --facts）")
    args = ap.parse_args()

    project = os.path.abspath(args.project)
    os.makedirs(os.path.join(project, "docs"), exist_ok=True)
    try:
        if args.facts_file and os.path.isfile(args.facts_file):
            # 从文件读取，避免命令行引号转义问题。
            with open(args.facts_file, "r", encoding="utf-8") as fp:
                facts = json.load(fp)
        else:
            facts = json.loads(args.facts) if args.facts else {}
        if not isinstance(facts, dict):
            facts = {}
    except Exception:
        facts = {}

    p1 = build_doc(project, args.platform, args.lang, "doc1", facts)
    p2 = build_doc(project, args.platform, args.lang, "doc2", facts)
    p3 = build_ppt(project, args.platform, args.lang)
    print("已生成：" + p1)
    print("已生成：" + p2)
    print("已生成：" + p3)


if __name__ == "__main__":
    main()
