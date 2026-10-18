###############################################################################
# Universal Transcoder —— qmake 工程描述文件
# 为什么写成这样（设计动机说明）：
#   1) 本工程刻意只使用 qmake 而不使用 CMake：目标机器上只准备了两套工具链
#      （Qt 6.8.1 mingw_64 与 MinGW 13.1），并没有安装 cmake；
#      qmake 随 Qt 一起分发，是这里唯一“一定可用”的构建器。
#   2) 只依赖 core / gui / widgets 三个 Qt 模块即可覆盖全部需求：
#      JSON 解析用 QtCore 的 QJsonDocument，子进程用 QProcess，
#      线程用 QThread，进度条/表格/下拉框等控件用 Widgets。
#   3) pdf 模块按“本机是否真的存在 Qt6Pdf 库文件”条件启用：
#      只有库确实存在时才开启 QT += pdf 并定义 HAVE_QTPDF 宏，
#      否则 PDF→图片 功能在代码里走“如实不支持”分支，绝不伪造能力。
###############################################################################

# ---- Qt 模块声明 ----
QT += core gui widgets

# ---- C++ 标准 ----
# 使用 C++17：需要 std::function 作为回调类型、lambda 捕获、QJsonValue 的取值便利性等。
CONFIG += c++17

# ---- 目标产物 ----
# 应用名（决定 exe 名称）：UniversalTranscoder.exe
TARGET = UniversalTranscoder
# 工程类型：可执行程序。
TEMPLATE = app

# ---- 平台特性 ----
# 在 Windows 上生成“窗体子系统”程序：链接参数会带上 -Wl,-subsystem,windows，
# 这样双击运行时不会附带一个黑色控制台窗口（用户体验更好）。
# 入口点由 Qt 提供的入口库 libQt6EntryPoint.a 负责，它会把命令行交给本工程的 main()。
# 使用该入口库有一个硬前提：编译器的 C 运行时必须与 Qt 的一致（都基于 msvcrt.dll）。
# 本工程因此固定使用与 Qt 6.8.1 配套的官方 MinGW 13.1.0 工具链
# （注意：UCRT 版本的 MinGW 虽然语法上也能编译，但会因堆不一致在启动阶段直接崩溃）。
win32 {
    CONFIG += windows
}

# ---- 关闭过时 API 警告噪音 ----
# 显式声明使用的 Qt 版本下限，避免 qmake 提示“未声明使用哪个版本”。
DEFINES += QT_DISABLE_DEPRECATED_BEFORE=0x060000

# ---- 头文件搜索路径 ----
# 为什么必须显式添加：本工程的头文件分组放在 src/core 与 src/gui 两个子目录下，
# 而各文件里统一使用 “core/xxx.h”“gui/xxx.h” 这种带目录前缀的写法
# （这种写法在移动文件时不需要改任何 include，是更稳的工程习惯）。
# 若不在 INCLUDEPATH 中加入 src 目录，“core/xxx.h” 在 src/gui 里就无法被找到。
INCLUDEPATH += $$PWD/src
# 工程根目录也加入，便于将来放置第三方头文件。
INCLUDEPATH += $$PWD

# ---- PDF 模块探测 ----
# 用 qmake 内置变量 $$[QT_INSTALL_LIBS] 取当前 Qt 安装的库目录，
# 再查找是否存在 Qt6Pdf 的导入库（mingw 下为 libQt6Pdf.a 或 Qt6Pdf.lib）。
PDF_LIB_FILES = $$files($$[QT_INSTALL_LIBS]/Qt6Pdf*)
PDF_LIB_FILES += $$files($$[QT_INSTALL_LIBS]/libQt6Pdf*)
!isEmpty(PDF_LIB_FILES) {
    # 库存在：真正链接 pdf 模块，并打开编译期开关。
    QT += pdf
    DEFINES += HAVE_QTPDF
}

# ---- 源文件清单（按模块分组，便于阅读）----
SOURCES += \
    src/main.cpp \
    src/selftest.cpp \
    src/core/processutil.cpp \
    src/core/ffmpeg_locator.cpp \
    src/core/capabilities.cpp \
    src/core/mediainfo.cpp \
    src/core/hwaccel.cpp \
    src/core/errors.cpp \
    src/core/presets.cpp \
    src/core/commandbuilder.cpp \
    src/core/runner.cpp \
    src/core/taskqueue.cpp \
    src/core/docconvert.cpp \
    src/gui/theme.cpp \
    src/gui/mainwindow.cpp \
    src/gui/convertpanel.cpp \
    src/gui/documentpage.cpp \
    src/gui/advancedpage.cpp \
    src/gui/queuepage.cpp \
    src/gui/settingspage.cpp \
    src/gui/aboutpage.cpp

# ---- 头文件清单 ----
HEADERS += \
    src/selftest.h \
    src/core/processutil.h \
    src/core/ffmpeg_locator.h \
    src/core/capabilities.h \
    src/core/mediainfo.h \
    src/core/hwaccel.h \
    src/core/errors.h \
    src/core/presets.h \
    src/core/commandbuilder.h \
    src/core/runner.h \
    src/core/taskqueue.h \
    src/core/docconvert.h \
    src/gui/theme.h \
    src/gui/mainwindow.h \
    src/gui/convertpanel.h \
    src/gui/documentpage.h \
    src/gui/advancedpage.h \
    src/gui/queuepage.h \
    src/gui/settingspage.h \
    src/gui/aboutpage.h

# ---- 资源文件 ----
# app.qrc 把图标编译进 exe（界面窗口图标与任务栏图标都从这里取）。
RESOURCES += resources/app.qrc

# ---- Windows 可执行文件图标 ----
# RC_ICONS 让 qmake 自动生成 .rc 并把它编译进 exe，
# 这样资源管理器里看到的文件图标就是我们自己的图标（而不是默认白纸图标）。
win32 {
    RC_ICONS = resources/icon.ico
}

# ---- 输出目录整理 ----
# 把中间产物集中到 build 目录，保持工程根目录整洁；release/debug 最终产物单独放置。
CONFIG(debug, debug|release) {
    DESTDIR = debug
} else {
    DESTDIR = release
}
MOC_DIR = build/moc
OBJECTS_DIR = build/obj
RCC_DIR = build/rcc
