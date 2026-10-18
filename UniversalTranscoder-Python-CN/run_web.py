# -*- coding: utf-8 -*-
# Web 端启动脚本。
# 直接运行：.venv\Scripts\python.exe run_web.py
# 指定端口：.venv\Scripts\python.exe run_web.py --port 9000

import os
import sys

# 把项目根目录加入模块搜索路径。
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.main import main

if __name__ == "__main__":
    # 强制走 Web 模式：在参数最前面插入 --web。
    sys.exit(main(["--web"] + sys.argv[1:]))
