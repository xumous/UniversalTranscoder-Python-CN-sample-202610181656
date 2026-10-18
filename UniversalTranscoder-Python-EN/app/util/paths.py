# -*- coding: utf-8 -*-
# 路径解析工具。
# 核心难点：PyInstaller 单文件模式运行时，程序被解压到一个临时目录，其路径存放在 sys._MEIPASS。
# 若不做兼容，打包后会出现“找不到 ffmpeg.exe / 找不到图标 / 找不到前端 dist”的经典故障。
# 本模块把“资源根目录”和“用户数据目录”两个概念彻底分开：
#   - 资源根目录(resource_root)：只读，随程序一起分发（ffmpeg、图标、前端静态文件）。
#   - 用户数据目录(user_data_dir)：可写，用于保存配置、日志、缩略图缓存，绝不能写到只读目录里。

import os  # 用于路径拼接与环境变量读取
import sys  # 用于判断是否为冻结(frozen)状态并读取 _MEIPASS


def is_frozen() -> bool:
    """判断当前是否运行在 PyInstaller 等打包器生成的“冻结”环境中。

    返回值:
        True  —— 说明已被打包（源码被封装进可执行文件）。
        False —— 说明是普通的源码运行（python run_gui.py）。
    实现依据：PyInstaller 会在运行时向 sys 注入属性 frozen，且其值为 True。
    使用 getattr 而不是直接访问，避免在普通环境下抛 AttributeError。
    """
    # getattr 的第三个参数是默认值：属性不存在时返回 False，从而安全判断。
    return getattr(sys, "frozen", False)


def resource_root() -> str:
    """返回只读资源根目录的绝对路径。

    规则:
        - 打包运行时：返回 sys._MEIPASS（PyInstaller 解压出的临时根目录）。
        - 源码运行时：返回本文件向上三级目录，即项目根 UniversalTranscoder-CN。
    为什么向上三级：本文件位于 app/util/paths.py，
        dirname(__file__)                    -> .../app/util
        dirname(dirname(...))                -> .../app
        dirname(dirname(dirname(...)))       -> .../UniversalTranscoder-CN（项目根）
    """
    # 先处理打包场景：_MEIPASS 存在即为单文件模式解压根目录。
    if is_frozen():
        # getattr 兜底：极少数打包配置下 _MEIPASS 可能缺失，缺失时退化为可执行文件所在目录。
        meipass = getattr(sys, "_MEIPASS", None)
        if meipass:
            # 归一化为绝对路径，避免后续拼接出相对路径。
            return os.path.abspath(meipass)
        # 退化分支：以可执行文件所在目录作为资源根。
        return os.path.dirname(os.path.abspath(sys.executable))
    # 源码运行分支：基于 __file__ 逐级上溯到项目根目录。
    here = os.path.abspath(__file__)  # 当前文件的绝对路径
    app_dir = os.path.dirname(os.path.dirname(here))  # .../app
    project_root = os.path.dirname(app_dir)  # .../UniversalTranscoder-CN
    return project_root


def resource_path(*parts: str) -> str:
    """在只读资源根目录下拼接子路径。

    参数:
        *parts: 从资源根开始的一级级路径片段，例如 ("resources", "ffmpeg", "ffmpeg.exe")。
    返回:
        拼接后的绝对路径（不做存在性检查，存在与否由调用方决定）。
    说明：使用 *parts 可变参数可以让调用点更直观，也避免手写 os.sep 造成跨平台差异。
    """
    # 先取资源根，再用 os.path.join 依次拼接各片段。
    return os.path.join(resource_root(), *parts)


def bundled_ffmpeg_dir() -> str:
    """返回随程序分发的 ffmpeg 目录路径（只读资源）。"""
    # 该目录在源码与打包两种模式下都由 resource_root() 正确解析。
    return resource_path("resources", "ffmpeg")


def bundled_frontend_dist() -> str:
    """返回随程序分发的前端构建产物目录（Vue 打包出的静态站点）。"""
    # FastAPI 会挂载该目录作为静态站点；若不存在则回退到内置的极简页面。
    return resource_path("frontend", "dist")


def user_data_dir() -> str:
    """返回可写的用户数据目录，用于保存配置、日志与缓存。

    规则:
        Windows 下使用 %APPDATA%\\UniversalTranscoder-CN\\；
        若 %APPDATA% 不可用（极少数受限环境），退化为用户主目录下的隐藏目录。
    设计意图：绝不把可写文件写到程序目录，因为打包后程序目录可能位于只读位置
        （例如 C:\\Program Files），写配置会因权限不足而失败。
    """
    # 读取 Windows 的漫游应用数据目录环境变量。
    appdata = os.environ.get("APPDATA")
    # 若环境变量存在且非空，使用其拼出的目录。
    if appdata and os.path.isdir(appdata):
        base = os.path.join(appdata, "UniversalTranscoder-EN")
    else:
        # 退化分支：使用 expanduser("~") 得到的用户主目录。
        base = os.path.join(os.path.expanduser("~"), ".universal_transcoder_en")
    # 确保目录存在；exist_ok=True 避免并发或多实例启动时的竞态异常。
    os.makedirs(base, exist_ok=True)
    return base


def user_sub_dir(*parts: str) -> str:
    """在用户数据目录下拼接子目录，并保证该目录已创建。

    参数:
        *parts: 子路径片段，例如 ("logs",) 或 ("cache", "thumbs")。
    返回:
        拼接后并已确保存在的绝对路径。
    """
    # 先得到用户数据根目录。
    target = os.path.join(user_data_dir(), *parts)
    # 递归创建，exist_ok=True 保证幂等。
    os.makedirs(target, exist_ok=True)
    return target


def config_file() -> str:
    """返回配置文件（JSON）的完整路径。"""
    # 配置放在用户数据目录根下，命名固定，便于用户手动编辑与备份。
    return os.path.join(user_data_dir(), "settings.json")


def log_file() -> str:
    """返回主日志文件的完整路径。"""
    # 日志统一放在 logs 子目录，便于轮转与清理。
    return os.path.join(user_sub_dir("logs"), "app.log")
