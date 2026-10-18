# -*- coding: utf-8 -*-
# 程序统一入口脚本（桌面端与 Web 端共用）。
# 这是 PyInstaller 打包的目标脚本，因此必须支持全部命令行参数：
#   万能转码器.exe               → 启动桌面端（PyQt6 图形界面）
#   万能转码器.exe --web         → 启动 Web 端（浏览器界面）
#   万能转码器.exe --web --port 9000 --no-browser
#
# 注意（曾经踩过的坑）：
#   早期版本这里直接调用 run_gui()，导致打包后的程序**完全忽略命令行参数**，
#   用 --web 启动时仍然会去开图形界面，Web 端根本起不来。
#   正确做法是统一交给 app.main.main()，由它解析参数后再分派。

import os
import sys

# 把项目根目录加入模块搜索路径，保证以“直接运行脚本”的方式启动时能找到 app 包。
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 导入总入口的 main（负责参数解析与分派）。
from app.main import main

if __name__ == "__main__":
    # 把命令行参数交给 main()，并以它的返回值作为进程退出码。
    sys.exit(main())
