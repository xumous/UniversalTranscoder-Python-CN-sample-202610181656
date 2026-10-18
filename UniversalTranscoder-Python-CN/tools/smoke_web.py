# -*- coding: utf-8 -*-
# Web 端后端冒烟测试脚本（开发者自检用）。
# 目的：在真实启动的 Web 服务上，走一遍“读取系统信息 → 读取预设 → 添加任务 → 启动队列 →
#       轮询直到完成 → 校验产物 → 抽帧 → 文档转换”，作为 Web 链路的可复现验证证据。
# 运行前提：先启动服务，例如
#   .venv\Scripts\python.exe -c "from app.web import server; server.run(port=8791, open_browser=False)"
# 运行本脚本：
#   .venv\Scripts\python.exe tools\smoke_web.py http://127.0.0.1:8791

import json
import os
import sys
import time
import urllib.request
import urllib.error

# 服务基址（可由命令行参数覆盖）。
BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8791"
# 样例文件目录。
SAMPLES = os.path.abspath(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "..", "Samples"))
# Web 产物输出目录。
OUT_DIR = os.path.join(SAMPLES, "web_out")


def call(method, path, body=None, timeout=60):
    """发起一次 HTTP 请求并返回解析后的 JSON。

    参数:
        method: "GET" 或 "POST"。
        path: 以 / 开头的接口路径。
        body: POST 的请求体字典；None 表示无体。
        timeout: 超时秒数。
    返回:
        解析后的 Python 对象。
    """
    # 组装 URL。
    url = BASE.rstrip("/") + path
    # 组装请求体（UTF-8 编码，保证中文不乱码）。
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    # 构造请求；POST 时显式声明 JSON 内容类型。
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    # 发起请求并读取响应体（按 UTF-8 解码，这一步同时验证服务端编码是否正确）。
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    return json.loads(raw.decode("utf-8"))


def section(title):
    """打印分节标题。"""
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


def main():
    """执行全部检查，返回退出码。"""
    ok = True

    # ---- 1. 系统信息 ----
    section("1. /api/system（验证 UTF-8 中文编码）")
    sysinfo = call("GET", "/api/system")
    print("软件名称  :", sysinfo["app"])
    print("版本/作者 :", sysinfo["version"], "/", sysinfo["author"])
    print("内核就绪  :", sysinfo["ready"])
    print("视频编码器:", sysinfo["capabilities"]["video_encoders"])
    # 关键断言：中文必须能正确还原（能证明不是乱码）。
    if sysinfo["app"] != "万能转码器":
        print("[失败] 中文编码异常，实际得到：", repr(sysinfo["app"]))
        ok = False
    if not sysinfo["ready"]:
        print("[失败] 内核未就绪。")
        ok = False

    # ---- 2. 预设 ----
    section("2. /api/presets")
    presets = call("GET", "/api/presets")
    print("预设数量  :", len(presets))
    print("首个预设  :", presets[0]["name"])
    if len(presets) < 20:
        print("[失败] 预设数量异常偏少。")
        ok = False

    # ---- 3. 格式列表 ----
    section("3. /api/formats")
    fmts = call("GET", "/api/formats")
    print("视频格式  :", fmts["video"])
    print("音频格式  :", fmts["audio"])

    # ---- 4. 添加任务并运行 ----
    section("4. 添加任务 → 启动队列 → 等待完成")
    src = os.path.join(SAMPLES, "test_video.mp4")
    if not os.path.isfile(src):
        print("[失败] 缺少样例文件：", src)
        return 1
    res = call("POST", "/api/tasks", {
        "sources": [src],
        "settings": {
            "target_format": "webm",  # 转成 WebM（VP9+Opus）
            "video_codec": "libvpx-vp9",
            "audio_codec": "libopus",
            "quality": "low",  # 用低质量档加快测试速度
            "width": 320,  # 缩小到 320 宽
            "scale_mode": "fit",
            "crf": "40",
            "output_dir": OUT_DIR,
            "hwaccel": "none",
        },
    })
    print("添加结果  :", res)
    if res.get("added", 0) != 1:
        print("[失败] 任务未被添加。")
        ok = False

    call("POST", "/api/queue/start")
    # 轮询等待完成（最多 120 秒）。
    deadline = time.time() + 120
    final = None
    while time.time() < deadline:
        state = call("GET", "/api/tasks")
        tasks = state["tasks"]
        if tasks:
            t = tasks[0]
            print(f"  状态={t['status_text']:<6} 进度={t['progress']:6.2f}% 速度={t['speed']:.2f}x")
            if t["status"] in ("done", "failed", "canceled", "skipped"):
                final = t
                break
        time.sleep(1.0)
    if not final:
        print("[失败] 等待任务结束超时。")
        return 1
    print("最终状态  :", final["status_text"])
    print("输出路径  :", final["out_path"])
    print("输出体积  :", final["out_size"])
    if final["status"] != "done" or final["out_size"] <= 0:
        print("[失败] 任务未成功。错误：", final["err_title"], final["err_detail"][-300:])
        ok = False
    elif not os.path.isfile(final["out_path"]):
        print("[失败] 输出文件不存在。")
        ok = False

    # ---- 5. 视频抽帧 ----
    section("5. /api/snapshot（抽帧）")
    snap = call("POST", "/api/snapshot", {
        "source": src, "count": 3, "out_dir": os.path.join(OUT_DIR, "snap"), "ext": "jpg", "width": 240,
    })
    print("是否成功  :", snap["ok"], "共", len(snap["files"]), "张")
    for f in snap["files"]:
        print("   -", os.path.basename(f))
    if not snap["ok"] or len(snap["files"]) != 3:
        print("[失败] 抽帧数量不符合预期。")
        ok = False

    # ---- 6. 文档转换：图片 → PDF ----
    section("6. /api/doc（图片 → PDF）")
    img = os.path.join(SAMPLES, "test_image.png")
    pdf_out = os.path.join(OUT_DIR, "web_doc.pdf")
    doc = call("POST", "/api/doc", {
        "op": "images_to_pdf", "inputs": [img], "out": pdf_out, "dpi": 100,
    })
    print("是否成功  :", doc["ok"], "|", doc["message"] or doc["error"])
    if not doc["ok"] or not os.path.isfile(pdf_out):
        print("[失败] 图片转 PDF 失败。")
        ok = False
    else:
        # 校验 PDF 头部魔数，确认确实是 PDF 文件。
        with open(pdf_out, "rb") as fp:
            head = fp.read(5)
        print("PDF 魔数  :", head)
        if not head.startswith(b"%PDF"):
            print("[失败] 输出不是合法 PDF。")
            ok = False

    # ---- 7. 文档转换：文本 → PDF ----
    section("7. /api/doc（文本 → PDF）")
    txt = os.path.join(OUT_DIR, "hello.txt")
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(txt, "w", encoding="utf-8") as fp:
        fp.write("Hello Universal Transcoder.\n" * 60)
    t2p = call("POST", "/api/doc", {
        "op": "text_to_pdf", "input": txt, "out": os.path.join(OUT_DIR, "web_text.pdf"),
        "font_size": 11, "title": "Report",
    })
    print("是否成功  :", t2p["ok"], "|", t2p["message"] or t2p["error"])
    if not t2p["ok"]:
        print("[失败] 文本转 PDF 失败。")
        ok = False

    # ---- 8. 清除任务 ----
    section("8. /api/tasks/clear")
    cleared = call("POST", "/api/tasks/clear", {"include_running": False})
    print("已移除    :", cleared["removed"])

    section("Web 冒烟测试结果")
    print("全部通过" if ok else "存在失败项，请检查上面的输出")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
