# -*- mode: python ; coding: utf-8 -*-
# PyInstaller 打包配置（中文版）。
# 设计目标：打出一个“拷到什么都没有的离线 Windows 机器上就能跑”的程序。
# 关键决策与原因：
#   1) 采用 one-folder（COLLECT）而非 one-file：
#      one-file 每次启动都要把上百 MB 的 Qt 与 FFmpeg 解压到临时目录，启动很慢，
#      且部分杀毒软件会对自解压行为误报。one-folder 启动快、行为稳定，更适合本类工具。
#   2) 必须把 resources/ffmpeg 与 frontend/dist 作为 data 打进包里：
#      前者是转码内核（缺失则完全不可用），后者是 Web 端界面（缺失会退化为提示页）。
#   3) console=False：桌面端不弹黑框；Web 模式下的提示信息通过浏览器与界面呈现。
#   4) 显式声明 uvicorn / PIL / pypdfium2 等隐藏导入：
#      这些库大量使用动态导入与运行时代码加载，PyInstaller 的静态分析常常漏掉，
#      不显式声明会出现“本机能跑、打包后一启动 Web 端就报 ModuleNotFoundError”的经典问题。
# 构建命令：
#   pyinstaller build/UniversalTranscoder.spec --noconfirm
# 产物位置：
#   dist/万能转码器/  （整个目录即为可分发的绿色版）

import os

# 项目根目录。
# PyInstaller 在执行 spec 时会注入变量 SPECPATH（即本 spec 文件所在的目录，也就是 <root>/build），
# 因此项目根目录就是它的上一级。使用 SPECPATH 比用 __file__ 更可靠，也无需额外的兼容判断。
PROJECT_ROOT = os.path.dirname(os.path.abspath(SPECPATH))

# 需要随包分发的“只读资源”。
datas = [
    # FFmpeg 内核（缺一不可）。
    (os.path.join(PROJECT_ROOT, "resources", "ffmpeg", "ffmpeg.exe"), "resources/ffmpeg"),
    (os.path.join(PROJECT_ROOT, "resources", "ffmpeg", "ffprobe.exe"), "resources/ffmpeg"),
    # 应用图标。
    (os.path.join(PROJECT_ROOT, "assets", "icon.ico"), "assets"),
    (os.path.join(PROJECT_ROOT, "assets", "icon.png"), "assets"),
]

# 前端静态站点：仅在已构建（存在 dist 目录）时才打包。
_front_dist = os.path.join(PROJECT_ROOT, "frontend", "dist")
if os.path.isdir(_front_dist):
    datas.append((_front_dist, "frontend/dist"))

# 隐藏导入：静态分析覆盖不到的模块。
hiddenimports = [
    # ---- Web 端（uvicorn 家族大量使用运行时动态导入）----
    "uvicorn",
    "uvicorn.logging",
    "uvicorn.loops",
    "uvicorn.loops.auto",
    "uvicorn.loops.asyncio",
    "uvicorn.protocols",
    "uvicorn.protocols.http",
    "uvicorn.protocols.http.auto",
    "uvicorn.protocols.http.h11_impl",
    "uvicorn.protocols.websockets",
    "uvicorn.protocols.websockets.auto",
    "uvicorn.lifespan",
    "uvicorn.lifespan.on",
    "websockets",
    "websockets.legacy",
    "websockets.legacy.server",
    "starlette",
    "starlette.responses",
    "fastapi",
    "anyio",
    "anyio._backends",
    "anyio._backends._asyncio",
    "h11",
    "multipart",
    # ---- 文档转换 ----
    "PIL",
    "PIL.Image",
    "PIL.PdfImagePlugin",
    "pypdfium2",
    "pypdfium2_raw",
    # ---- Qt 相关（PyQt6 的 sip 与平台插件需显式包含）----
    "PyQt6.sip",
    "PyQt6.QtCore",
    "PyQt6.QtGui",
    "PyQt6.QtWidgets",
]

# 明确排除用不到的重量级模块，显著减小体积。
excludes = [
    "tkinter",            # Tk 界面（本项目不使用）
    "matplotlib",         # 绘图库（不使用）
    "numpy",              # 数值库（本内核不依赖；若被间接引入会显著增大体积）
    "scipy",
    "pandas",
    "test",               # 标准库测试套件
    "unittest",
    "pydoc_data",
    "IPython",
    "notebook",
]

# 分析入口脚本（桌面端；同一个 exe 通过 --web 参数也能启动 Web 端）。
a = Analysis(
    [os.path.join(PROJECT_ROOT, "run_gui.py")],
    pathex=[PROJECT_ROOT],          # 保证 app 包能被找到
    binaries=[],                    # 二进制依赖（FFmpeg 已作为 data 处理）
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=excludes,
    noarchive=False,                # 保持归档（体积更小、启动更快）
)

# 纯 Python 模块编译为字节码。
pyz = PYZ(a.pure)

# 可执行文件（console=False：桌面端不显示控制台窗口）。
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,          # one-folder 模式：二进制放在 COLLECT 目录
    name="万能转码器",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,                      # 不使用 UPX：压缩后易被杀软误报，且对启动速度有负面影响
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=os.path.join(PROJECT_ROOT, "assets", "icon.ico"),
)

# 收集所有依赖到输出目录（one-folder 绿色版）。
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="万能转码器",
)
