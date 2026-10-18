# -*- coding: utf-8 -*-
# 界面文本集中定义文件（英文版）。
# 设计目的：
#   1) 全项目所有“会显示给用户看的文字”都集中在这里，不散落在各业务模块中。
#   2) 英文版与中文版是两个真正独立、可分别运行的项目：两者仅有本文件与少量
#      界面文案不同，业务代码完全一致。
#   3) 也方便日后扩展多语言：把本文件换成按 locale 分文件的目录即可。
# 约定：代码注释保持中文（需求明确说明注释不翻译），仅“界面文案”在此处切换为英文。

from app import __app_name__, __version__, __author__, __copyright_year__


class S:
    """所有界面显示文本的命名空间（英文）。

    组织方式：按功能域分成嵌套类，避免出现上千个平铺的全局常量。
    """

    # ---------------- 应用级信息 ----------------
    APP_NAME = __app_name__  # 软件名称
    APP_VERSION = __version__  # 版本号
    APP_AUTHOR = __author__  # 作者署名
    APP_COPYRIGHT = f"Copyright (c) {__copyright_year__} {__author__}"  # 版权行
    APP_SLOGAN = "Convert anything - Fully offline - Batch stable"  # 副标题/标语
    ABOUT_TITLE = f"About {__app_name__}"  # 关于对话框标题
    ABOUT_BODY = (
        f"{__app_name__}  v{__version__}\n"
        f"Author: {__author__}\n\n"
        "A fully offline universal format converter:\n"
        "video / audio / image / document, batch, fault-tolerant, extensible.\n\n"
        "Powered by FFmpeg. Two front ends: a PyQt desktop app and a browser web app."
    )

    # ---------------- 主窗口与导航 ----------------
    class Win:
        MAIN_TITLE = f"{__app_name__}  v{__version__}"  # 主窗口标题
        TAB_VIDEO = "Video"  # 视频转换页
        TAB_AUDIO = "Audio"  # 音频转换页
        TAB_IMAGE = "Image"  # 图片转换页
        TAB_DOC = "Document"  # 文档转换页
        TAB_DEVICE = "Device"  # 光驱/移动设备页
        TAB_ADVANCED = "Advanced"  # 自定义命令页
        TAB_QUEUE = "Queue"  # 队列页
        TAB_SETTINGS = "Settings"  # 设置页
        TAB_ABOUT = "About"  # 关于页
        STATUS_READY = "Ready"  # 状态栏空闲文案
        STATUS_RUNNING = "Converting"  # 状态栏运行中
        STATUS_PAUSED = "Paused"  # 状态栏暂停
        STATUS_DONE = "Completed"  # 状态栏完成
        STATUS_ERROR = "Error"  # 状态栏错误
        BTN_START = "Start"  # 启动任务
        BTN_PAUSE = "Pause"  # 暂停队列
        BTN_RESUME = "Resume"  # 继续队列
        BTN_STOP = "Stop"  # 停止当前任务
        BTN_CLEAR = "Clear list"  # 清空队列
        BTN_REMOVE = "Remove selected"  # 移除队列选中项
        BTN_OPEN_OUT = "Open output folder"  # 打开输出目录
        BTN_ADD_FILES = "Add files"  # 添加待转文件
        BTN_ADD_DIR = "Add folder"  # 递归添加文件夹
        BTN_BROWSE = "Browse..."  # 通用浏览按钮
        BTN_APPLY = "Apply"  # 应用设置
        BTN_RESET = "Restore defaults"  # 恢复默认设置
        BTN_SAVE = "Save"  # 保存
        BTN_PREVIEW = "Preview command"  # 预览将要执行的命令
        BTN_COPY = "Copy command"  # 复制命令到剪贴板
        BTN_WEB = "Start web app"  # 启动 Web 服务
        LABEL_START_BROWSER = "Open browser automatically on start"  # Web 选项

    # ---------------- 通用标签 ----------------
    class Lbl:
        INPUT = "Input file"  # 输入
        OUTPUT_DIR = "Output folder"  # 输出目录
        OUTPUT_FORMAT = "Output format"  # 目标格式
        QUALITY = "Quality"  # 质量档位
        PRESET = "Preset"  # 预设
        RESOLUTION = "Resolution"  # 分辨率
        FRAMERATE = "Frame rate"  # 帧率
        VIDEO_CODEC = "Video codec"  # 视频编码器
        AUDIO_CODEC = "Audio codec"  # 音频编码器
        VIDEO_BITRATE = "Video bitrate"  # 视频码率
        AUDIO_BITRATE = "Audio bitrate"  # 音频码率
        AUDIO_CHANNELS = "Channels"  # 声道数
        SAMPLE_RATE = "Sample rate"  # 采样率
        VOLUME = "Volume"  # 音量增益
        START_TIME = "Start time"  # 裁剪起点
        END_TIME = "End time"  # 裁剪终点
        DURATION = "Duration"  # 时长
        SPEED = "Speed"  # 变速倍率
        ROTATE = "Rotate"  # 旋转角度
        FLIP = "Mirror"  # 翻转方式
        CROP = "Crop area"  # 画面裁剪
        SCALE_MODE = "Scale mode"  # 缩放策略
        WATERMARK = "Watermark"  # 水印
        SUBTITLE = "Subtitle"  # 字幕
        METADATA_TITLE = "Title"  # 元数据标题
        METADATA_ARTIST = "Artist"  # 元数据艺术家
        METADATA_ALBUM = "Album"  # 元数据专辑
        SNAPSHOT = "Video snapshot"  # 快照
        MERGE = "Merge"  # 合并
        SPLIT = "Split"  # 分割
        PAGE_RANGE = "Page range"  # 文档页码范围
        IMAGE_QUALITY = "Image quality"  # JPEG/WebP 质量
        BACKGROUND = "Background color"  # 透明图转不透明时的底色
        DEVICE = "Device list"  # 设备
        ADV_ARGS = "Custom arguments"  # 高级参数
        THREADS = "Threads"  # 编码线程
        LOG_LEVEL = "Log level"  # 日志级别
        THEME = "Theme"  # 主题
        HWACCEL = "Hardware acceleration"  # 硬件加速
        OUT_NAME_MODE = "Naming mode"  # 输出命名方式
        MAX_PARALLEL = "Concurrent jobs"  # 并发
        WEB_HOST = "Listen address"  # Web 监听地址
        WEB_PORT = "Listen port"  # Web 端口

    # ---------------- 选项值文案 ----------------
    class Opt:
        SAME_AS_SOURCE = "Same as source"  # 分辨率/码率保持原样
        AUTO = "Auto"  # 自动
        NONE = "None"  # 无/关闭
        ORIGINAL = "Keep original"  # 保持原样
        ON = "On"  # 开
        OFF = "Off"  # 关
        THEME_DARK = "Dark"  # 深色主题
        THEME_LIGHT = "Light"  # 浅色主题
        NAME_SUFFIX = "Original name + suffix"  # 命名：原名加后缀
        NAME_REPLACE = "Replace extension"  # 命名：替换扩展名
        NAME_CUSTOM = "Custom template"  # 命名：自定义
        SCALE_FIT = "Fit (keep whole frame)"  # 缩放：fit（可能留黑边）
        SCALE_FILL = "Fill (crop)"  # 缩放：fill（裁剪）
        SCALE_STRETCH = "Stretch (may distort)"  # 缩放：拉伸
        ROTATE_0 = "No rotation"  # 旋转 0
        ROTATE_90 = "90 deg clockwise"  # 旋转 90
        ROTATE_180 = "180 deg"  # 旋转 180
        ROTATE_270 = "270 deg clockwise"  # 旋转 270
        FLIP_NONE = "No mirror"  # 无镜像
        FLIP_H = "Horizontal mirror"  # 水平
        FLIP_V = "Vertical mirror"  # 垂直
        FLIP_BOTH = "Horizontal + vertical"  # 双向
        START_NOW = "Start now"  # 队列操作
        CLOSE = "Close"  # 关闭
        OK = "OK"  # 确定
        CANCEL = "Cancel"  # 取消
        YES = "Yes"  # 是
        NO = "No"  # 否

    # ---------------- 质量档位 ----------------
    class Quality:
        TITLE = "Quality"  # 质量
        LOSSLESS = "Lossless (largest file)"  # 无损
        HIGH = "High"  # 高
        MEDIUM = "Standard (recommended)"  # 中
        LOW = "Small size"  # 低
        CUSTOM = "Custom"  # 自定义
        CRF = "Constant quality CRF"  # CRF
        LOW_HINT = "0 = lossless; larger value = lower quality and smaller file"  # 提示

    # ---------------- 队列与进度 ----------------
    class Queue:
        COL_INDEX = "#"  # 序号列
        COL_FILE = "File"  # 文件名列
        COL_TARGET = "Target format"  # 目标格式列
        COL_STATUS = "Status"  # 状态列
        COL_PROGRESS = "Progress"  # 进度列
        COL_SPEED = "Speed"  # 速度列
        COL_ETA = "Time left"  # 剩余时间列
        COL_SIZE = "Size change"  # 体积列
        STATUS_WAITING = "Waiting"  # 等待
        STATUS_RUNNING = "Converting"  # 运行
        STATUS_DONE = "Done"  # 完成
        STATUS_FAILED = "Failed"  # 失败
        STATUS_CANCELED = "Canceled"  # 取消
        STATUS_SKIPPED = "Skipped"  # 跳过
        EMPTY_TIP = "Drop files here, or click \"Add files\" to begin"  # 空列表提示
        SPEED_UNIT = "x realtime"  # 速度单位
        REMAIN_UNKNOWN = "Calculating..."  # 剩余时间未知
        SUMMARY = "{total} jobs: {ok} succeeded, {fail} failed, {skip} skipped"  # 汇总

    # ---------------- 弹窗与提示 ----------------
    class Msg:
        NO_INPUT = "Please add at least one file to convert."  # 未添加文件
        NO_FORMAT = "Please choose a target format."  # 未选格式
        NO_OUTPUT_DIR = "Please choose an output folder, or use \"Same as source\"."  # 未选输出
        DIR_NOT_EXIST = "Folder does not exist or is not writable: {path}"  # 目录不可用
        FFMPEG_MISSING = "No usable FFmpeg found. Check the resources/ffmpeg folder or set a custom path."  # 内核缺失
        FFMPEG_OK = "FFmpeg is available: {path}"  # 内核可用
        TASK_ADDED = "Added {count} file(s)."  # 添加成功
        TASK_DUPLICATED = "Automatically skipped {count} duplicate file(s)."  # 去重提示
        CONFIRM_CLEAR = "Clear the whole job list?"  # 清空确认
        CONFIRM_STOP = "Stop the job that is currently running?"  # 停止确认
        FORMAT_UNSUPPORTED = "The current core does not support this format: {fmt}"  # 不支持格式
        SAVE_OK = "Settings saved."  # 保存成功
        SAVE_FAIL = "Failed to save settings: {err}"  # 保存失败
        RESET_OK = "Default settings restored."  # 恢复默认
        OPEN_FAIL = "Cannot open the folder: {err}"  # 打开失败
        COPY_OK = "Command copied to clipboard."  # 复制成功
        WEB_STARTED = "Web app started at: {url}"  # Web 启动
        WEB_FAILED = "Failed to start the web app: {err}"  # Web 失败
        OVERWRITE_ASK = "The output file already exists. Overwrite it?\n{path}"  # 覆盖询问
        DETAIL_TITLE = "Job details"  # 详情标题
        ERROR_TITLE = "Job failure details"  # 错误标题
        LOG_OPENED = "Log file opened: {path}"  # 日志位置
        ADD_DIR_EMPTY = "No convertible files were found in that folder."  # 空目录
        PARTIAL_FAIL = "Some jobs failed. See the log for details."  # 部分失败

    # ---------------- 运行期阶段名（进度展示用） ----------------
    class Phase:
        ANALYZING = "Analyzing source file..."  # 探测阶段
        PREPARING = "Preparing..."  # 准备命令
        TRANSCODING = "Transcoding..."  # 转码阶段
        MUXING = "Muxing..."  # 封装/收尾
        FINALIZING = "Finalizing..."  # 收尾
        DONE = "Completed"  # 完成

    # ---------------- 各类支持的格式（界面分组标题） ----------------
    class Group:
        VIDEO_COMMON = "Common video"  # 视频常用
        VIDEO_PRO = "Professional / intermediate"  # 专业格式
        AUDIO_COMMON = "Common audio"  # 音频常用
        AUDIO_LOSSLESS = "Lossless audio"  # 无损
        IMAGE_COMMON = "Common image"  # 图片常用
        IMAGE_SPECIAL = "Special-purpose image"  # 特殊图片
        DOC_CONVERT = "Document conversion"  # 文档
        ANIM = "Animated image / sequence"  # 动图
