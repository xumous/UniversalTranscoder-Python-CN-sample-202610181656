# -*- coding: utf-8 -*-
# 界面文本集中定义文件（中文版）。
# 设计目的：
#   1) 全项目所有“会显示给用户看的文字”都集中在这里，不散落在各业务模块中。
#   2) 制作英文版时，只需把本文件整体替换为英文内容（其余代码零改动），
#      从而保证“中文版 / 英文版”是两个真正独立、可分别运行的项目。
#   3) 也方便日后扩展多语言：把本文件换成按 locale 分文件的目录即可。
# 约定：代码注释保持中文（需求明确说明注释不翻译），仅“界面文案”在此处切换。

from app import __app_name__, __version__, __author__, __copyright_year__


class S:
    """所有界面显示文本的命名空间。

    组织方式：按功能域分成嵌套类，避免出现上千个平铺的全局常量。
    """

    # ---------------- 应用级信息 ----------------
    APP_NAME = __app_name__  # 软件名称
    APP_VERSION = __version__  # 版本号
    APP_AUTHOR = __author__  # 作者署名
    APP_COPYRIGHT = f"版权所有 © {__copyright_year__} {__author__}"  # 版权行
    APP_SLOGAN = "什么都能转 · 离线可用 · 批量稳定"  # 副标题/标语
    ABOUT_TITLE = "关于 万能转码器"  # 关于对话框标题
    ABOUT_BODY = (
        f"{__app_name__}  v{__version__}\n"
        f"作者：{__author__}\n\n"
        "一款完全离线的万能格式转换工具：\n"
        "视频 / 音频 / 图片 / 文档，批量、容错、可扩展。\n\n"
        "内核基于 FFmpeg，界面提供 PyQt 桌面端与浏览器 Web 端两种形态。"
    )

    # ---------------- 主窗口与导航 ----------------
    class Win:
        MAIN_TITLE = f"{__app_name__}  v{__version__}"  # 主窗口标题
        TAB_VIDEO = "视频"  # 视频转换页
        TAB_AUDIO = "音频"  # 音频转换页
        TAB_IMAGE = "图片"  # 图片转换页
        TAB_DOC = "文档"  # 文档转换页
        TAB_DEVICE = "设备"  # 光驱/移动设备页
        TAB_ADVANCED = "高级"  # 自定义命令页
        TAB_QUEUE = "任务队列"  # 队列页
        TAB_SETTINGS = "设置"  # 设置页
        TAB_ABOUT = "关于"  # 关于页
        STATUS_READY = "就绪"  # 状态栏空闲文案
        STATUS_RUNNING = "正在转换"  # 状态栏运行中
        STATUS_PAUSED = "已暂停"  # 状态栏暂停
        STATUS_DONE = "已完成"  # 状态栏完成
        STATUS_ERROR = "出错"  # 状态栏错误
        BTN_START = "开始"  # 启动任务
        BTN_PAUSE = "暂停"  # 暂停队列
        BTN_RESUME = "继续"  # 继续队列
        BTN_STOP = "停止"  # 停止当前任务
        BTN_CLEAR = "清空列表"  # 清空队列
        BTN_REMOVE = "移除选中"  # 移除队列选中项
        BTN_OPEN_OUT = "打开输出目录"  # 打开输出目录
        BTN_ADD_FILES = "添加文件"  # 添加待转文件
        BTN_ADD_DIR = "添加文件夹"  # 递归添加文件夹
        BTN_BROWSE = "浏览…"  # 通用浏览按钮
        BTN_APPLY = "应用"  # 应用设置
        BTN_RESET = "恢复默认"  # 恢复默认设置
        BTN_SAVE = "保存"  # 保存
        BTN_PREVIEW = "预览命令"  # 预览将要执行的命令
        BTN_COPY = "复制命令"  # 复制命令到剪贴板
        BTN_WEB = "启动 Web 端"  # 启动 Web 服务
        LABEL_START_BROWSER = "启动后自动打开浏览器"  # Web 选项

    # ---------------- 通用标签 ----------------
    class Lbl:
        INPUT = "输入文件"  # 输入
        OUTPUT_DIR = "输出目录"  # 输出目录
        OUTPUT_FORMAT = "输出格式"  # 目标格式
        QUALITY = "质量"  # 质量档位
        PRESET = "预设方案"  # 预设
        RESOLUTION = "分辨率"  # 分辨率
        FRAMERATE = "帧率"  # 帧率
        VIDEO_CODEC = "视频编码"  # 视频编码器
        AUDIO_CODEC = "音频编码"  # 音频编码器
        VIDEO_BITRATE = "视频码率"  # 视频码率
        AUDIO_BITRATE = "音频码率"  # 音频码率
        AUDIO_CHANNELS = "声道"  # 声道数
        SAMPLE_RATE = "采样率"  # 采样率
        VOLUME = "音量"  # 音量增益
        START_TIME = "起始时间"  # 裁剪起点
        END_TIME = "结束时间"  # 裁剪终点
        DURATION = "时长"  # 时长
        SPEED = "速度"  # 变速倍率
        ROTATE = "旋转"  # 旋转角度
        FLIP = "镜像"  # 翻转方式
        CROP = "裁剪区域"  # 画面裁剪
        SCALE_MODE = "缩放模式"  # 缩放策略
        WATERMARK = "水印"  # 水印
        SUBTITLE = "字幕"  # 字幕
        METADATA_TITLE = "标题"  # 元数据标题
        METADATA_ARTIST = "艺术家"  # 元数据艺术家
        METADATA_ALBUM = "专辑"  # 元数据专辑
        SNAPSHOT = "视频快照"  # 快照
        MERGE = "合并"  # 合并
        SPLIT = "分割"  # 分割
        PAGE_RANGE = "页码范围"  # 文档页码范围
        IMAGE_QUALITY = "图片质量"  # JPEG/WebP 质量
        BACKGROUND = "背景色"  # 透明图转不透明时的底色
        DEVICE = "设备列表"  # 设备
        ADV_ARGS = "自定义参数"  # 高级参数
        THREADS = "线程数"  # 编码线程
        LOG_LEVEL = "日志级别"  # 日志级别
        THEME = "主题"  # 主题
        HWACCEL = "硬件加速"  # 硬件加速
        OUT_NAME_MODE = "命名方式"  # 输出命名方式
        MAX_PARALLEL = "并发任务数"  # 并发
        WEB_HOST = "监听地址"  # Web 监听地址
        WEB_PORT = "监听端口"  # Web 端口

    # ---------------- 选项值文案 ----------------
    class Opt:
        SAME_AS_SOURCE = "与源文件相同"  # 分辨率/码率保持原样
        AUTO = "自动"  # 自动
        NONE = "无"  # 无/关闭
        ORIGINAL = "保持原样"  # 保持原样
        ON = "开启"  # 开
        OFF = "关闭"  # 关
        THEME_DARK = "深色"  # 深色主题
        THEME_LIGHT = "浅色"  # 浅色主题
        NAME_SUFFIX = "原名+后缀"  # 命名：原名加后缀
        NAME_REPLACE = "替换扩展名"  # 命名：替换扩展名
        NAME_CUSTOM = "自定义模板"  # 命名：自定义
        SCALE_FIT = "等比缩放（完整画面）"  # 缩放：fit（可能留黑边）
        SCALE_FILL = "等比填充（裁剪）"  # 缩放：fill（裁剪）
        SCALE_STRETCH = "拉伸（可能变形）"  # 缩放：拉伸
        ROTATE_0 = "不旋转"  # 旋转 0
        ROTATE_90 = "顺时针 90°"  # 旋转 90
        ROTATE_180 = "旋转 180°"  # 旋转 180
        ROTATE_270 = "顺时针 270°"  # 旋转 270
        FLIP_NONE = "不镜像"  # 无镜像
        FLIP_H = "水平镜像"  # 水平
        FLIP_V = "垂直镜像"  # 垂直
        FLIP_BOTH = "水平+垂直"  # 双向
        START_NOW = "立即开始"  # 队列操作
        CLOSE = "关闭"  # 关闭
        OK = "确定"  # 确定
        CANCEL = "取消"  # 取消
        YES = "是"  # 是
        NO = "否"  # 否

    # ---------------- 质量档位 ----------------
    class Quality:
        TITLE = "质量"  # 质量
        LOSSLESS = "无损（体积最大）"  # 无损
        HIGH = "高质量"  # 高
        MEDIUM = "标准（推荐）"  # 中
        LOW = "小体积"  # 低
        CUSTOM = "自定义"  # 自定义
        CRF = "恒定质量 CRF"  # CRF
        LOW_HINT = "0 为无损，数值越大画质越低、体积越小"  # 提示

    # ---------------- 队列与进度 ----------------
    class Queue:
        COL_INDEX = "#"  # 序号列
        COL_FILE = "文件"  # 文件名列
        COL_TARGET = "目标格式"  # 目标格式列
        COL_STATUS = "状态"  # 状态列
        COL_PROGRESS = "进度"  # 进度列
        COL_SPEED = "速度"  # 速度列
        COL_ETA = "剩余时间"  # 剩余时间列
        COL_SIZE = "体积变化"  # 体积列
        STATUS_WAITING = "等待中"  # 等待
        STATUS_RUNNING = "转换中"  # 运行
        STATUS_DONE = "完成"  # 完成
        STATUS_FAILED = "失败"  # 失败
        STATUS_CANCELED = "已取消"  # 取消
        STATUS_SKIPPED = "已跳过"  # 跳过
        EMPTY_TIP = "把文件拖到这里，或点击“添加文件”开始"  # 空列表提示
        SPEED_UNIT = "倍速"  # 速度单位
        REMAIN_UNKNOWN = "计算中…"  # 剩余时间未知
        SUMMARY = "共 {total} 个任务：成功 {ok}，失败 {fail}，跳过 {skip}"  # 汇总

    # ---------------- 弹窗与提示 ----------------
    class Msg:
        NO_INPUT = "请先添加至少一个待转换的文件。"  # 未添加文件
        NO_FORMAT = "请先选择目标格式。"  # 未选格式
        NO_OUTPUT_DIR = "请选择输出目录，或使用“与源文件相同”。"  # 未选输出
        DIR_NOT_EXIST = "目录不存在或不可写：{path}"  # 目录不可用
        FFMPEG_MISSING = "未找到可用的 FFmpeg，请检查 resources/ffmpeg 目录或自定义路径。"  # 内核缺失
        FFMPEG_OK = "FFmpeg 可用：{path}"  # 内核可用
        TASK_ADDED = "已添加 {count} 个文件。"  # 添加成功
        TASK_DUPLICATED = "已自动跳过 {count} 个重复文件。"  # 去重提示
        CONFIRM_CLEAR = "确定要清空当前任务列表吗？"  # 清空确认
        CONFIRM_STOP = "确定要停止当前正在进行的任务吗？"  # 停止确认
        FORMAT_UNSUPPORTED = "当前内核不支持该格式：{fmt}"  # 不支持格式
        SAVE_OK = "配置已保存。"  # 保存成功
        SAVE_FAIL = "配置保存失败：{err}"  # 保存失败
        RESET_OK = "已恢复默认设置。"  # 恢复默认
        OPEN_FAIL = "无法打开目录：{err}"  # 打开失败
        COPY_OK = "命令已复制到剪贴板。"  # 复制成功
        WEB_STARTED = "Web 端已启动：{url}"  # Web 启动
        WEB_FAILED = "Web 端启动失败：{err}"  # Web 失败
        OVERWRITE_ASK = "输出文件已存在，是否覆盖？\n{path}"  # 覆盖询问
        DETAIL_TITLE = "任务详情"  # 详情标题
        ERROR_TITLE = "任务失败详情"  # 错误标题
        LOG_OPENED = "已打开日志文件：{path}"  # 日志位置
        ADD_DIR_EMPTY = "该文件夹内未找到可转换的文件。"  # 空目录
        PARTIAL_FAIL = "部分任务失败，详情见日志。"  # 部分失败

    # ---------------- 运行期阶段名（进度展示用） ----------------
    class Phase:
        ANALYZING = "正在分析源文件…"  # 探测阶段
        PREPARING = "正在准备…"  # 准备命令
        TRANSCODING = "正在转换…"  # 转码阶段
        MUXING = "正在封装…"  # 封装/收尾
        FINALIZING = "正在收尾…"  # 收尾
        DONE = "已完成"  # 完成

    # ---------------- 各类支持的格式（界面分组标题） ----------------
    class Group:
        VIDEO_COMMON = "常用视频"  # 视频常用
        VIDEO_PRO = "专业/中间格式"  # 专业格式
        AUDIO_COMMON = "常用音频"  # 音频常用
        AUDIO_LOSSLESS = "无损音频"  # 无损
        IMAGE_COMMON = "常用图片"  # 图片常用
        IMAGE_SPECIAL = "特殊用途图片"  # 特殊图片
        DOC_CONVERT = "文档互转"  # 文档
        ANIM = "动图/序列"  # 动图
