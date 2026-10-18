# -*- coding: utf-8 -*-
# 配置读写工具。
# 设计目标：
#   1) 所有可调参数集中于一处，界面“设置”页与命令行、Web 端共用同一份配置。
#   2) 采用 JSON 明文存储，便于用户查看与手工修改；读取失败时绝不崩溃，而是回退到默认值。
#   3) 任何新增配置项都必须同时给出默认值，保证老配置文件升级后依然可用（向后兼容）。

import json  # 用于序列化/反序列化配置文件
import os  # 用于判断文件是否存在
import threading  # 用于加锁，保证多线程（GUI 与 Web 同时读写）时的数据一致性

from app.util import paths  # 复用路径解析（配置文件位置）

# 配置项的默认值表。
# 采用“键 -> 默认值”的扁平结构，方便逐项合并与写回。
DEFAULT_SETTINGS = {
    "language": "en-US",  # 界面语言；中文版固定 zh-CN，英文版为 en-US（仅影响极少处动态文案）
    "theme": "dark",  # 界面主题：dark（深色）/ light（浅色）
    "output_dir": "",  # 默认输出目录；为空表示“与源文件同目录”
    "output_name_mode": "suffix",  # 输出命名策略：suffix（原名加后缀）/ replace（替换扩展名）/ custom（自定义模板）
    "output_name_template": "{name}_{index}",  # 自定义命名模板；{name} 原名，{index} 序号，{ext} 目标扩展名
    "overwrite": False,  # 是否覆盖已存在的输出文件（False 时自动追加序号避免覆盖）
    "max_parallel": 1,  # 同时执行的任务数上限（1 最稳，机械硬盘上并发会显著降速）
    "auto_open_output": False,  # 全部完成后是否自动打开输出目录
    "keep_source": True,  # 是否保留源文件（本项目永远保留，仅作占位以免覆盖）
    "ffmpeg_custom_path": "",  # 用户自定义 ffmpeg.exe 路径；为空则自动查找随包副本
    "hwaccel": "none",  # 硬件加速策略：auto / none / 指定编码器名（如 h264_nvenc）
    # 默认关闭是有意为之：硬件编码器“编入内核”不等于“本机可用”，
    # 默认走软件编码（libx264）才能保证在任何机器上都必定成功。
    "hwaccel_vendor": "auto",  # 硬件厂商偏好：auto / nvidia / intel / amd
    "log_level": "INFO",  # 日志级别：DEBUG / INFO / WARNING / ERROR
    "snapshot_count": 5,  # 视频快照默认张数（含第 0 帧与定时帧）
    "threads": 0,  # 编码线程数；0 表示交给 ffmpeg 自动决定
    "crt_effect": False,  # 是否对本程序界面启用 CRT/扫描线之类的复古滤镜（默认关闭，保持简洁）
    "remember_last_dir": True,  # 是否记住上次打开/输出目录
    "last_input_dir": "",  # 上次使用的输入目录
    "last_output_dir": "",  # 上次使用的输出目录
    "web_port": 8765,  # Web 端监听端口（默认 8765，避免与常见 8000/8080 冲突）
    "web_host": "127.0.0.1",  # Web 端监听地址（默认仅本机，安全起见不默认对公网开放）
    "web_auto_open": True,  # Web 端启动后是否自动拉起浏览器
}


class ConfigManager:
    """配置管理器：负责读取、合并、写回与线程安全访问配置。

    采用“类内字典 + 读写锁”的简单实现，避免引入额外依赖。
    """

    def __init__(self) -> None:
        # 保存配置文件的路径，统一从 paths 模块取得。
        self._path = paths.config_file()
        # 存放内存中的当前配置快照（键值对字典）。
        self._data = dict(DEFAULT_SETTINGS)
        # 指向“绝对串行”的读写锁，保证 GUI 线程与 Web 线程同时修改时不互相破坏。
        self._lock = threading.RLock()

    def load(self) -> None:
        """从磁盘读取配置并与默认值合并。

        容错策略：
            - 文件不存在：直接使用默认值（首次运行）。
            - JSON 解析失败（用户手改坏了）：使用默认值并写回一份干净的配置。
            - 存在未知键：保留它们（多版本共存时不丢信息）。
            - 缺少新键：从默认值补齐。
        """
        # 临界区：整个读取合并过程都需要加锁。
        with self._lock:
            # 若配置文件不存在，说明首次运行，直接返回使用默认值。
            if not os.path.isfile(self._path):
                return
            try:
                # 以 UTF-8 打开，避免中文路径/中文值在 GBK 环境下乱码。
                with open(self._path, "r", encoding="utf-8") as fp:
                    raw = json.load(fp)
            except Exception:
                # 解析失败时静默回退：保留默认值即可，不向用户弹窗打断启动流程。
                return
            # 仅当读到的是字典时才进行合并，防御损坏文件写入的是数组或标量。
            if isinstance(raw, dict):
                # 以默认值作为基底，再用用户文件中的值覆盖，实现“补齐缺失键、保留未知键”。
                merged = dict(DEFAULT_SETTINGS)  # 复制默认值，避免直接修改全局常量
                merged.update(raw)  # 用户值覆盖默认值
                self._data = merged  # 原子替换内存快照

    def save(self) -> None:
        """把当前配置写回磁盘（原子写：先写临时文件再替换，避免断电损坏）。"""
        # 临界区：写盘期间不加锁可能读到半更新状态。
        with self._lock:
            # 临时文件与目标文件同目录，保证 os.replace 是同分区的原子操作。
            tmp = self._path + ".tmp"
            try:
                # 序列化为带缩进的 JSON，方便人工阅读；ensure_ascii=False 保留中文原样。
                with open(tmp, "w", encoding="utf-8") as fp:
                    json.dump(self._data, fp, ensure_ascii=False, indent=2)
                # 原子替换：Windows 上 os.replace 会覆盖已存在文件。
                os.replace(tmp, self._path)
            except Exception:
                # 写失败（如磁盘只读）不应导致程序崩溃；保留内存配置即可。
                # 若临时文件已产生，尝试清理，避免留下垃圾文件。
                try:
                    if os.path.isfile(tmp):
                        os.remove(tmp)
                except Exception:
                    pass

    def get(self, key: str, default=None):
        """读取单个配置项。不存在时返回调用方给出的 default（再兜底到默认值表）。"""
        with self._lock:
            # 优先取内存快照；快照中没有则取默认值表；再没有则用调用方传入的 default。
            if key in self._data:
                return self._data[key]
            return DEFAULT_SETTINGS.get(key, default)

    def set(self, key: str, value) -> None:
        """设置单个配置项（仅内存），需显式调用 save() 才会落盘。"""
        with self._lock:
            self._data[key] = value

    def update(self, mapping: dict) -> None:
        """批量设置多个配置项（仅内存）。"""
        with self._lock:
            # 逐个写入，保证未知键也被接受（便于前向兼容）。
            for k, v in mapping.items():
                self._data[k] = v

    def as_dict(self) -> dict:
        """返回当前配置的浅拷贝，供界面展示或 API 返回。"""
        with self._lock:
            # 返回副本，防止调用方直接改动内部字典绕过锁。
            return dict(self._data)

    def reset(self) -> None:
        """恢复默认配置（仅内存），随后应调用 save() 落盘。"""
        with self._lock:
            self._data = dict(DEFAULT_SETTINGS)


# 模块级单例：整个进程共享一个配置管理器，避免多处实例导致配置漂移。
config = ConfigManager()
