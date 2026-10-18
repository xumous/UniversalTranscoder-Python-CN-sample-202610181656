# -*- coding: utf-8 -*-
# core 包：与界面无关的纯业务内核。
# 该包不依赖 PyQt，也不依赖 FastAPI，因此可被桌面端、Web 端、命令行、单元测试共同复用。
# 这种分层保证了“同一套转码逻辑，两种界面形态”，也是本项目的核心架构原则。

# 显式列出对外主要模块，作为该包的能力地图。
__all__ = [
    "ffmpeg_locator",  # 定位与校验 ffmpeg/ffprobe 可执行文件
    "media_info",  # 调用 ffprobe 解析媒体元信息
    "capabilities",  # 探测当前内核支持的格式/编码器（能力矩阵）
    "errors",  # ffmpeg 错误分类与用户可读建议
    "command_builder",  # 把“用户意图”编译成 ffmpeg 参数列表
    "runner",  # 带进度解析、可取消的子进程执行器
    "presets",  # 内置预设方案
    "hwaccel",  # 硬件加速探测
    "thumbnail",  # 视频快照抽取
    "tasks",  # 任务模型、队列状态机
]
