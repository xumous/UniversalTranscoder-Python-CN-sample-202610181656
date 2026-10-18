# -*- coding: utf-8 -*-
# 转换矩阵验证脚本（开发者自检用）。
# 目的：把“视频 / 音频 / 图片”三大类的目标格式逐个真实转一遍，逐一核对产物是否合法，
#       以此暴露封装名错误、编码器缺失、参数约束冲突等“只有真跑才会发现”的问题。
# 说明：这是本项目“反复、多方验证”的主要手段之一——不是抽查几个格式，而是穷举界面提供的全部格式。
# 运行：.venv\Scripts\python.exe tools\test_matrix.py

import os
import sys

# 项目根目录与样例目录。
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROJECT_ROOT)
SAMPLES = os.path.abspath(os.path.join(PROJECT_ROOT, "..", "Samples"))
OUT_ROOT = os.path.join(SAMPLES, "matrix_out")

from app.util import logger as app_logger
from app.core import (ffmpeg_locator, media_info, command_builder,
                      runner as runner_mod, capabilities)

# 需要验证的目标格式（与界面下拉框保持一致）。
VIDEO_TARGETS = ["mp4", "mkv", "mov", "avi", "flv", "webm", "ts", "mpg", "wmv", "3gp", "m4v", "gif"]
AUDIO_TARGETS = ["mp3", "m4a", "wav", "flac", "ogg", "opus", "ac3", "aiff", "amr", "wma"]
IMAGE_TARGETS = ["jpg", "png", "webp", "bmp", "tiff", "gif", "avif"]


def run_one(label, st, duration, expect_kind):
    """执行一次转换并校验产物。

    参数:
        label: 用于显示的说明。
        st: TranscodeSettings。
        duration: 源时长（用于进度换算）。
        expect_kind: 期望产物的归类（video/audio/image），用于校验“产物确实是目标类型”。
    返回:
        (是否成功, 说明文本)。
    """
    # 计算输出路径（统一放到矩阵输出目录，并按格式分子目录，便于人工检查）。
    st.output_dir = os.path.join(OUT_ROOT, label.split("/")[0])
    out_path = command_builder.compute_output_path(st, mode="replace", overwrite=True)
    # 构建命令。
    info = media_info.probe(st.source)
    args = command_builder.build_command(st, info, out_path=out_path)
    # 执行。
    rn = runner_mod.Runner()
    res = rn.run(args, duration=duration)
    # 失败时返回 stderr 尾部，便于定位。
    if not res.ok:
        tail = "\n".join([l for l in res.stderr.splitlines() if l.strip()][-4:])
        return False, f"失败（rc={res.returncode}）：{tail}"
    # 产物二次探测：确认它确实是期望的类型（这是最关键的校验，避免“命令成功但产物是垃圾”）。
    oi = media_info.probe(res.out_path)
    if not oi.probe_ok:
        return False, "产物无法被 ffprobe 识别"
    if expect_kind == "video" and not oi.has_video:
        return False, "产物不含视频流"
    if expect_kind == "audio" and not oi.has_audio:
        return False, "产物不含音频流"
    if expect_kind == "image" and oi.kind != "image":
        return False, f"产物归类为 {oi.kind}，不是图片"
    # 通过：返回体积与摘要。
    return True, f"OK  体积={res.out_size:>9} 字节 | {oi.summary_line()[:64]}"


def main() -> int:
    """遍历全部目标格式并汇总结果。"""
    # 初始化日志（WARNING 级别，避免刷屏；失败信息由本脚本自行打印）。
    app_logger.configure("WARNING")

    # 内核自检。
    if not ffmpeg_locator.is_ready():
        print("内核不可用，无法进行矩阵验证。")
        return 1

    # 源文件准备。
    vid_src = os.path.join(SAMPLES, "test_video.mp4")  # 视频/音频矩阵用的源
    aud_src = os.path.join(SAMPLES, "test_audio.mp3")  # 音频矩阵用的源
    img_src = os.path.join(SAMPLES, "test_image.png")  # 图片矩阵用的源
    for p in (vid_src, aud_src, img_src):
        if not os.path.isfile(p):
            print("缺少样例文件：", p)
            return 1

    # 结果统计。
    results = []

    print("\n" + "=" * 84)
    print("一、视频目标格式（源：test_video.mp4，统一缩放为 128 宽、截取 1 秒、低质量）")
    print("=" * 84)
    for fmt in VIDEO_TARGETS:
        st = command_builder.TranscodeSettings(
            source=vid_src, target_format=fmt, quality="low",
            width=128, scale_mode="fit", duration="1",
            hwaccel="none", overwrite=True,
        )
        ok, msg = run_one(f"video/{fmt}", st, 6.0, "video")
        # GIF 的产物是动图，ffprobe 仍归为 video，因此 expect_kind 用 video 即可。
        results.append((f"video → {fmt}", ok, msg))
        print(f"  {fmt:<6} {'✔' if ok else '✘'}  {msg}")

    print("\n" + "=" * 84)
    print("二、音频目标格式（源：test_audio.mp3，截取 1 秒）")
    print("=" * 84)
    for fmt in AUDIO_TARGETS:
        st = command_builder.TranscodeSettings(
            source=aud_src, target_format=fmt, quality="low",
            duration="1", hwaccel="none", overwrite=True,
        )
        ok, msg = run_one(f"audio/{fmt}", st, 4.0, "audio")
        results.append((f"audio → {fmt}", ok, msg))
        print(f"  {fmt:<6} {'✔' if ok else '✘'}  {msg}")

    print("\n" + "=" * 84)
    print("三、图片目标格式（源：test_image.png）")
    print("=" * 84)
    for fmt in IMAGE_TARGETS:
        st = command_builder.TranscodeSettings(
            source=img_src, target_format=fmt, quality="medium",
            width=128, scale_mode="fit", hwaccel="none", overwrite=True,
        )
        ok, msg = run_one(f"image/{fmt}", st, 0.0, "image")
        results.append((f"image → {fmt}", ok, msg))
        print(f"  {fmt:<6} {'✔' if ok else '✘'}  {msg}")

    print("\n" + "=" * 84)
    print("四、视频 → 图片：抽帧")
    print("=" * 84)
    from app.core import thumbnail
    info = media_info.probe(vid_src)
    snaps = thumbnail.extract_snapshots(vid_src, os.path.join(OUT_ROOT, "snap"),
                                        "test_video", duration=info.duration, count=4,
                                        target_ext="png", width=160)
    ok = len(snaps) == 4
    results.append(("视频抽帧", ok, f"生成 {len(snaps)} 张"))
    print(f"  抽帧     {'✔' if ok else '✘'}  生成 {len(snaps)} 张 PNG")

    # ---- 汇总 ----
    print("\n" + "=" * 84)
    print("汇总")
    print("=" * 84)
    passed = sum(1 for _n, o, _m in results if o)
    total = len(results)
    for name, ok, _msg in results:
        if not ok:
            print(f"  失败项：{name}")
    print(f"\n通过 {passed} / {total}")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
