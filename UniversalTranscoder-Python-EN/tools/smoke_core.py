# -*- coding: utf-8 -*-
# 内核冒烟测试脚本（开发者自检用）。
# 目的：在不启动图形界面的前提下，验证“定位 FFmpeg → 探测媒体 → 构建命令 → 执行转码 → 校验产物”
#       这一整条链路是否通畅。它同时充当“逐步调试”的可复现证据。
# 运行方式：.venv\Scripts\python.exe tools\smoke_core.py

import os
import sys

# 把项目根目录加入模块搜索路径，使 `import app.*` 可用（脚本位于 tools/ 子目录）。
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROJECT_ROOT)

# 初始化日志（控制台可见）。
from app.util import logger as app_logger

app_logger.configure("INFO")

from app.core import ffmpeg_locator, media_info, command_builder, runner as runner_mod, capabilities, hwaccel

# 样例文件目录（位于工作区 Samples）。
SAMPLES = os.path.abspath(os.path.join(PROJECT_ROOT, "..", "Samples"))


def show(title):
    """打印分节标题，方便阅读测试输出。"""
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


def main():
    """依次执行各阶段自检。"""
    ok = True

    # ---- 1. FFmpeg 定位 ----
    show("1. FFmpeg 内核定位")
    print("ffmpeg  :", ffmpeg_locator.ffmpeg_path() or "(未找到)")
    print("ffprobe :", ffmpeg_locator.ffprobe_path() or "(未找到)")
    print("版本    :", ffmpeg_locator.version_text())
    if not ffmpeg_locator.is_ready():
        print("[失败] 内核未就绪，后续测试无法进行。")
        return 1

    # ---- 2. 能力矩阵 ----
    show("2. 内核能力矩阵")
    cap = capabilities.summary()
    print("视频编码器数量:", cap["video_encoders"])
    print("音频编码器数量:", cap["audio_encoders"])
    print("封装格式数量  :", cap["format_total"])
    print("硬件加速后端  :", cap["hwaccels"])
    print("硬件编码器    :", [x["name"] for x in hwaccel.available_detail()])
    # 关键编码器可用性断言。
    for enc in ("libx264", "aac", "libmp3lame"):
        present = capabilities.supports_encoder(enc)
        print(f"  - {enc:<12} {'可用' if present else '缺失'}")
        ok = ok and present

    # ---- 3. 媒体探测 ----
    show("3. 媒体探测")
    src = os.path.join(SAMPLES, "test_video.mp4")
    info = media_info.probe(src)
    print("文件      :", info.display_name, f"({info.size} 字节)")
    print("探测成功  :", info.probe_ok)
    print("容器      :", info.format_name)
    print("时长      :", f"{info.duration:.3f}s")
    print("归类      :", info.kind)
    print("摘要      :", info.summary_line())
    if not info.probe_ok or info.kind != "video":
        print("[失败] 视频探测结果异常。")
        ok = False

    # ---- 4. 命令构建（含缩放、裁剪、水印、变速、元数据）----
    show("4. 命令构建")
    st = command_builder.TranscodeSettings(
        source=src,
        target_format="mp4",
        video_codec="libx264",
        audio_codec="aac",
        quality="high",
        width=480,  # 等比缩放到宽 480
        scale_mode="fit",
        fps="25",
        speed=1.5,  # 1.5 倍速
        meta_title="自检样例",
        meta_artist="徐某",
        output_dir=os.path.join(SAMPLES, "out"),
    )
    out_path = command_builder.compute_output_path(st, mode="suffix", overwrite=True)
    args = command_builder.build_command(st, info, out_path=out_path)
    print("输出路径  :", out_path)
    print("命令预览  :", command_builder.describe_command(args)[:400], "...")

    # ---- 5. 实际执行 ----
    show("5. 实际执行（带进度回调）")
    rn = runner_mod.Runner()
    seen = {"count": 0, "max": 0.0}

    def on_prog(p):
        # 记录进度回调次数与最高百分比，用于验证进度解析确实在工作。
        seen["count"] += 1
        seen["max"] = max(seen["max"], p.percent)

    res = rn.run(args, duration=info.duration, on_progress=on_prog)
    print("返回码    :", res.returncode)
    print("耗时      :", f"{res.elapsed:.2f}s")
    print("输出体积  :", res.out_size, "字节")
    print("进度回调次数:", seen["count"], "最高百分比:", f"{seen['max']:.1f}%")
    print("成功      :", res.ok)
    if not res.ok:
        print("[失败] 转码未成功，stderr 尾部：")
        print(res.stderr[-800:])
        ok = False

    # ---- 6. 产物二次探测（验证输出确实是合法视频）----
    if res.ok:
        show("6. 产物校验")
        oi = media_info.probe(res.out_path)
        print("产物容器  :", oi.format_name, "时长:", f"{oi.duration:.3f}s",
              "分辨率:", (oi.primary_video.resolution if oi.primary_video else "-"))
        if not oi.probe_ok or not oi.has_video:
            print("[失败] 产物不是合法视频。")
            ok = False

    # ---- 7. 容错用例：不存在的输入应被优雅判为 FILE_MISSING ----
    show("7. 容错用例（输入不存在）")
    bad = command_builder.TranscodeSettings(source=os.path.join(SAMPLES, "不存在的文件.mp4"),
                                            target_format="mp4")
    from app.core import tasks as tasks_mod
    q = tasks_mod.TaskQueue()
    t = q.add(bad)
    # 直接同步执行单个任务（不走调度线程），便于断言。
    q._run_one(t)
    print("状态      :", t.status_text)
    print("错误码    :", t.err_code, "/", t.err_title)
    if t.err_code != "FILE_MISSING":
        print("[失败] 容错用例未得到预期的 FILE_MISSING。")
        ok = False

    show("冒烟测试结果")
    print("全部通过" if ok else "存在失败项，请检查上面的输出")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
