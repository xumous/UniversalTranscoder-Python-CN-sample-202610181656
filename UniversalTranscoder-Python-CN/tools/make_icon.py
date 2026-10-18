# -*- coding: utf-8 -*-
# 应用图标生成脚本（一次性工具，构建前运行即可）。
# 目的：不依赖任何美术素材，用代码画出一个干净、扁平、有辨识度的图标，
#       并输出为 Windows 需要的 .ico（内含多分辨率）与 PNG。
# 设计说明：
#   - 主体：深蓝圆角方块（与界面主题一致）。
#   - 图形：两条对比色弧形箭头首尾相连，表达“格式转换/循环”的语义。
#   - 不含文字（小尺寸下文字会糊），保证 16x16 也清晰可辨。
# 运行：.venv\Scripts\python.exe tools\make_icon.py

import os
import sys

# 项目根目录与输出路径。
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(PROJECT_ROOT, "assets")


def main() -> int:
    """生成图标文件。"""
    # 依赖 Pillow 绘图。
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("缺少 Pillow，请先执行 pip install Pillow")
        return 1

    # 输出目录。
    os.makedirs(ASSETS_DIR, exist_ok=True)

    # 在 4 倍尺寸上绘制再缩小，获得平滑边缘（超采样抗锯齿）。
    S = 1024
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # ---- 背景：深蓝圆角方块 ----
    pad = int(S * 0.055)  # 四周留白
    radius = int(S * 0.22)  # 圆角半径
    d.rounded_rectangle([pad, pad, S - pad, S - pad], radius=radius, fill=(15, 22, 36, 255))

    # ---- 内层：稍亮的圆角矩形，制造层次 ----
    inner_pad = int(S * 0.115)
    d.rounded_rectangle([inner_pad, inner_pad, S - inner_pad, S - inner_pad],
                        radius=int(S * 0.155), fill=(22, 32, 47, 255),
                        outline=(58, 160, 255, 255), width=int(S * 0.012))

    # ---- 主体图形：两条弧形箭头（转换循环） ----
    cx, cy = S / 2, S / 2  # 圆心
    r_out = S * 0.29  # 外弧半径
    width = int(S * 0.085)  # 弧线粗细

    # 上半弧（青色）：从左到右，顺时针。
    d.arc([cx - r_out, cy - r_out, cx + r_out, cy + r_out],
          start=196, end=344, fill=(111, 211, 255, 255), width=width)
    # 下半弧（蓝色）：从右到左，顺时针。
    d.arc([cx - r_out, cy - r_out, cx + r_out, cy + r_out],
          start=16, end=164, fill=(58, 160, 255, 255), width=width)

    # ---- 箭头头部：用多边形三角表示方向 ----
    # 右侧箭头（指向上方）：用三角形表示箭头方向。
    tip = (cx + r_out, cy - r_out * 0.02)
    d.polygon([
        (tip[0] + S * 0.075, tip[1] - S * 0.005),
        (tip[0] - S * 0.015, tip[1] - S * 0.075),
        (tip[0] - S * 0.015, tip[1] + S * 0.065),
    ], fill=(111, 211, 255, 255))
    # 左侧箭头（指向下方）。
    tip2 = (cx - r_out, cy + r_out * 0.02)
    d.polygon([
        (tip2[0] - S * 0.075, tip2[1] + S * 0.005),
        (tip2[0] + S * 0.015, tip2[1] + S * 0.075),
        (tip2[0] + S * 0.015, tip2[1] - S * 0.065),
    ], fill=(58, 160, 255, 255))

    # ---- 中心：一颗播放/转换的花蕊方块 ----
    box = int(S * 0.115)
    d.rounded_rectangle([cx - box, cy - box, cx + box, cy + box],
                        radius=int(S * 0.035), fill=(219, 228, 240, 235))

    # 保存为多尺寸 ICO（Windows 图标标准尺寸集合）。
    sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (24, 24), (16, 16)]
    ico_path = os.path.join(ASSETS_DIR, "icon.ico")
    img.resize((256, 256), Image.LANCZOS).save(ico_path, format="ICO", sizes=sizes)
    print("已生成：", ico_path)

    # 同时输出 PNG（供文档/PPT 使用）。
    png_path = os.path.join(ASSETS_DIR, "icon.png")
    img.resize((512, 512), Image.LANCZOS).save(png_path, format="PNG")
    print("已生成：", png_path)

    # 额外输出不同尺寸 PNG，便于文档排版。
    for sz in (32, 64, 128, 256):
        p = os.path.join(ASSETS_DIR, f"icon_{sz}.png")
        img.resize((sz, sz), Image.LANCZOS).save(p, format="PNG")
    print("已生成多尺寸 PNG：icon_32/64/128/256.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
