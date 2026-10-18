# -*- coding: utf-8 -*-
# Web 端后端服务（FastAPI）。
# 设计要点：
#   1) 单进程内维护唯一一个 TaskQueue 实例，桌面端与 Web 端若同时运行则各用各的队列（互不干扰）。
#   2) 进度通过 WebSocket 主动推送：内核回调发生在工作线程，这里用一个 asyncio 事件队列 +
#      “广播协程”把消息安全地投递到所有已连接的浏览器，避免在非主线程直接操作事件循环。
#   3) 若前端尚未构建（frontend/dist 不存在），仍然提供 API，并返回一个内置的提示页，
#      而不是直接 404——保证“功能可用优先于界面美观”。
#   4) 静态站点以 `base: './'` 的相对路径构建，因此这里把 dist 挂载到根路径即可正常工作。

import os  # 路径与系统操作（打开目录）
import sys  # 平台判断
import json  # 序列化 WebSocket 消息
import asyncio  # 事件队列与广播
import logging  # 日志
import dataclasses  # 依据字段表过滤前端传入的字典
from typing import Optional, Set, Dict, Any, List  # 类型标注

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Body
from fastapi.responses import JSONResponse, HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles

from app import __app_name__, __version__, __author__
from app.util import paths, config as config_mod, logger as logger_mod
from app.util.config import config
from app.core import (ffmpeg_locator, media_info, capabilities, hwaccel,
                      presets, command_builder, doc_convert, thumbnail, tasks)
from app.core.tasks import TaskQueue, TaskStatus

logger = logging.getLogger(__name__)

# ---------------------- 全局单例 ----------------------
# Web 端专用的任务队列（与桌面端实例相互独立）。
QUEUE = TaskQueue(max_parallel=int(config.get("max_parallel", 1) or 1))
# 已连接的 WebSocket 集合（用于广播）。
_CLIENTS: Set[WebSocket] = set()
# 广播用的 asyncio 事件队列；内核回调往这里塞消息，广播协程消费。
_EVENTS: "asyncio.Queue" = asyncio.Queue()
# 事件循环引用：内核回调在别的线程执行，需要用 call_soon_threadsafe 投递。
_LOOP: Optional[asyncio.AbstractEventLoop] = None
# 回调是否已接线的幂等标志。
_WIRED = False


def _post_event(message: Dict[str, Any]) -> None:
    """线程安全地把一条待广播消息放入事件队列。

    该函数会被内核的工作线程调用，因此必须通过事件循环的线程安全接口投递，
    绝不能直接 await 或操作 asyncio 队列。
    """
    # 事件循环尚未就绪（服务未启动完成）时直接丢弃，避免报错。
    if _LOOP is None:
        return
    try:
        # call_soon_threadsafe 会把“往队列里放消息”这一动作调度到事件循环线程执行。
        _LOOP.call_soon_threadsafe(_EVENTS.put_nowait, message)
    except Exception as exc:
        # 投递失败（如循环已关闭）只记录，不影响转码本身。
        logger.debug("投递 WebSocket 事件失败：%s", exc)


def _wire_queue_callbacks() -> None:
    """把内核队列的回调接到 WebSocket 广播通道上（只接一次）。"""
    # 幂等保护：避免重复注册导致同一事件被推送多次。
    global _WIRED
    if _WIRED:
        return
    _WIRED = True
    # 进度回调：把任务快照发出去。
    QUEUE.on_progress(lambda task, prog: _post_event({"type": "progress", "task": task.to_dict()}))
    # 状态回调：任务状态变化时也刷新一次（便于前端更新表格）。
    QUEUE.on_state(lambda task: _post_event({"type": "state", "task": task.to_dict()}))
    # 全部结束回调。
    QUEUE.on_all_finished(lambda: _post_event({"type": "finished", "stats": QUEUE.stats()}))


async def _broadcast_loop() -> None:
    """广播协程：持续消费事件队列并把消息推给所有已连接客户端。"""
    while True:
        # 阻塞等待下一条事件（不占用 CPU）。
        msg = await _EVENTS.get()
        # 序列化为 JSON 文本。
        text = json.dumps(msg, ensure_ascii=False)
        # 若无客户端连接，直接丢弃（避免队列堆积）。
        if not _CLIENTS:
            continue
        # 复制一份客户端集合，避免迭代期间集合被修改。
        dead = []
        for ws in list(_CLIENTS):
            try:
                # 逐个发送。
                await ws.send_text(text)
            except Exception:
                # 发送失败说明连接已断开，稍后统一清理。
                dead.append(ws)
        # 清理失效连接。
        for ws in dead:
            _CLIENTS.discard(ws)


# ---------------------- FastAPI 应用 ----------------------
# 导入 asynccontextmanager 用于定义 lifespan（应用生命周期钩子）。
from contextlib import asynccontextmanager


@asynccontextmanager
async def _lifespan(application):
    """应用生命周期钩子。

    为什么用 lifespan 而不是已废弃的 @app.on_event("startup")：
        新版 Starlette 已移除 on_event 机制，若不改用 lifespan，服务可能无法启动。
    """
    # 记录当前事件循环，供跨线程投递使用。
    global _LOOP
    _LOOP = asyncio.get_running_loop()
    # 接线内核回调（幂等）。
    _wire_queue_callbacks()
    # 启动常驻的广播协程。
    broadcast_task = asyncio.create_task(_broadcast_loop())
    logger.info("Web 端服务已启动。")
    try:
        # 交出控制权，应用开始处理请求。
        yield
    finally:
        # 应用关闭时取消广播协程，避免遗留任务。
        broadcast_task.cancel()


app = FastAPI(title=__app_name__, version=__version__, docs_url="/api/docs", redoc_url=None,
              lifespan=_lifespan)


# ---------------------- 工具函数 ----------------------
def _settings_from_dict(data: Dict[str, Any]) -> command_builder.TranscodeSettings:
    """把前端传来的字典转换为 TranscodeSettings（只接收合法字段，忽略未知键）。

    这样做既保证前端可以“少传字段”（走默认值），也防止未知键导致异常。
    """
    # 取出数据类允许的字段名集合。
    valid = {f.name for f in dataclasses.fields(command_builder.TranscodeSettings)}
    # 过滤出合法键。
    payload = {k: v for k, v in (data or {}).items() if k in valid}
    # 类型宽松处理：custom_args 必须是列表；数值字段做安全转换在后端算法层已完成，
    # 这里仅做最基本的容器类型校正。
    if "custom_args" in payload and not isinstance(payload["custom_args"], list):
        payload["custom_args"] = []
    try:
        return command_builder.TranscodeSettings(**payload)
    except TypeError:
        # 极端情况下（例如某个字段传了不可接受的类型）退化为最小可用设置，避免 500。
        return command_builder.TranscodeSettings(source=str(payload.get("source", "")))


def _media_to_dict(info: media_info.MediaInfo) -> Dict[str, Any]:
    """把 MediaInfo 转成前端友好的字典。"""
    # 视频流摘要。
    videos = []
    for s in info.video_streams:
        videos.append({
            "codec": s.codec_name,
            "resolution": s.resolution,
            "fps": s.fps,
            "pix_fmt": s.pix_fmt,
            "index": s.index,
        })
    # 音频流摘要。
    audios = []
    for s in info.audio_streams:
        audios.append({
            "codec": s.codec_name,
            "sample_rate": s.sample_rate,
            "channels": s.channels,
            "layout": s.channel_layout,
            "index": s.index,
        })
    # 组装。
    return {
        "name": info.display_name,
        "path": info.path,
        "exists": info.exists,
        "size": info.size,
        "format_name": info.format_name,
        "duration": info.duration,
        "bitrate": info.bit_rate,
        "kind": info.kind,
        "summary": info.summary_line(),
        "probe_ok": info.probe_ok,
        "error": info.error,
        "video": videos,
        "audio": audios,
    }


# 各类型可用格式（界面下拉框使用）。
FORMATS_VIDEO = ["mp4", "mkv", "mov", "avi", "flv", "webm", "ts", "mpg", "wmv", "3gp", "m4v", "gif"]
FORMATS_AUDIO = ["mp3", "aac", "m4a", "wav", "flac", "ogg", "opus", "wma", "ac3", "amr", "aiff"]
FORMATS_IMAGE = ["jpg", "png", "webp", "bmp", "tiff", "gif", "avif", "ico"]
FORMATS_DOC = ["pdf", "txt"]


# ---------------------- API：系统信息 ----------------------
@app.get("/api/system")
async def api_system() -> Dict[str, Any]:
    """返回内核与软件信息。"""
    # 能力矩阵摘要。
    cap = capabilities.summary()
    # 硬件编码器详情。
    hw = hwaccel.available_detail()
    return {
        "app": __app_name__,
        "version": __version__,
        "author": __author__,
        "ffmpeg": ffmpeg_locator.ffmpeg_path(),
        "ffprobe": ffmpeg_locator.ffprobe_path(),
        "ffmpeg_version": ffmpeg_locator.version_text(),
        "ready": ffmpeg_locator.is_ready(),
        "capabilities": cap,
        "hw_accel": hw,
    }


@app.get("/api/presets")
async def api_presets() -> List[Dict[str, Any]]:
    """返回全部内置预设。"""
    return presets.as_dicts()


@app.get("/api/formats")
async def api_formats() -> Dict[str, List[str]]:
    """返回各类型可选格式。"""
    return {"video": FORMATS_VIDEO, "audio": FORMATS_AUDIO,
            "image": FORMATS_IMAGE, "document": FORMATS_DOC}


# ---------------------- API：文件浏览与探测 ----------------------
@app.post("/api/browse")
async def api_browse(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """列出目录内容（用于 Web 端选文件/选输出目录）。"""
    # 取目标路径；空串表示“列出本机磁盘根”。
    target = str(payload.get("path", "") or "")
    # 空路径：Windows 下列出所有盘符；其它平台列出用户主目录。
    if not target:
        if os.name == "nt":
            # 枚举 A-Z 中存在的盘符。
            dirs = []
            for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
                root = f"{letter}:\\"
                if os.path.exists(root):
                    dirs.append({"name": root, "path": root})
            return {"dir": "", "parent": "", "dirs": dirs, "files": []}
        # 非 Windows：直接列主目录。
        target = os.path.expanduser("~")
    # 归一化为绝对路径；不存在的路径回退到其父目录，保证可继续浏览。
    path = os.path.abspath(target)
    if not os.path.isdir(path):
        path = os.path.dirname(path) or path
    # 12 / 计算父目录。
    parent = os.path.dirname(path)
    dirs, files = [], []
    try:
        # 逐条读取目录（不排序会让顺序随机，这里排序以便展示稳定）。
        for name in sorted(os.listdir(path), key=lambda s: s.lower()):
            full = os.path.join(path, name)
            try:
                if os.path.isdir(full):
                    dirs.append({"name": name, "path": full})
                else:
                    # 只返回常见媒体/文档扩展名，避免把系统文件全列出来造成困扰。
                    ext = os.path.splitext(name)[1].lower().lstrip(".")
                    if ext in set(FORMATS_VIDEO + FORMATS_AUDIO + FORMATS_IMAGE + ["pdf", "txt", "srt", "ass"]):
                        files.append({"name": name, "path": full,
                                      "size": os.path.getsize(full), "ext": ext})
            except OSError:
                # 单个条目无权限读取时跳过。
                continue
    except OSError as exc:
        return {"dir": path, "parent": parent, "dirs": [], "files": [], "error": str(exc)}
    return {"dir": path, "parent": parent, "dirs": dirs, "files": files}


@app.post("/api/probe")
async def api_probe(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """探测一个媒体文件。"""
    # 取路径并规范化。
    src = str(payload.get("path", "") or "")
    # 空路径直接返回空结果，避免无谓的进程调用。
    if not src:
        return _media_to_dict(media_info.MediaInfo())
    # 执行探测（阻塞式，但耗时通常 < 1 秒）。
    info = media_info.probe(src)
    return _media_to_dict(info)


# ---------------------- API：设置 ----------------------
@app.get("/api/settings")
async def api_get_settings() -> Dict[str, Any]:
    """返回当前设置。"""
    return config.as_dict()


@app.post("/api/settings")
async def api_set_settings(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """批量更新设置并落盘。"""
    # 仅接受已知键，避免前端误传垃圾数据污染配置。
    known = set(config_mod.DEFAULT_SETTINGS.keys())
    patch = {k: v for k, v in (payload or {}).items() if k in known}
    # 合并到内存配置。
    config.update(patch)
    # 落盘。
    config.save()
    # 若并发数被修改，同步到队列。
    if "max_parallel" in patch:
        try:
            QUEUE.set_max_parallel(int(patch["max_parallel"]))
        except (TypeError, ValueError):
            pass
    # 若自定义内核路径被修改，清空缓存，下次调用重新定位。
    if "ffmpeg_custom_path" in patch:
        ffmpeg_locator.clear_cache()
        capabilities.clear_cache()
        hwaccel.clear_cache()
    return {"ok": True, "settings": config.as_dict()}


# ---------------------- API：任务与队列 ----------------------
@app.post("/api/tasks")
async def api_add_tasks(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """批量添加任务。"""
    # 支持 sources（数组）与 source（单个）两种写法。
    sources = payload.get("sources") or ([payload["source"]] if payload.get("source") else [])
    # 公共设置。
    base = payload.get("settings", {}) or {}
    added, skipped = 0, 0
    # 逐源文件创建任务。
    for src in sources:
        # 单文件覆盖：允许为每个源单独指定 target_format。
        # 这里简单处理：把源路径写进设置再创建任务。
        st_data = dict(base)
        st_data["source"] = str(src)
        st = _settings_from_dict(st_data)
        # 输出目录为空时沿用设置的默认值。
        if not st.output_dir:
            st.output_dir = str(config.get("output_dir", "") or "")
        task = QUEUE.add(st)
        if task:
            added += 1
        else:
            skipped += 1
    return {"added": added, "skipped": skipped}


@app.get("/api/tasks")
async def api_list_tasks() -> Dict[str, Any]:
    """返回全部任务与统计。"""
    return {"tasks": [t.to_dict() for t in QUEUE.tasks()], "stats": QUEUE.stats()}


@app.delete("/api/tasks/{tid}")
async def api_remove_task(tid: str) -> Dict[str, Any]:
    """移除一个任务（运行中的不可移除）。"""
    return {"ok": QUEUE.remove(tid)}


@app.post("/api/tasks/clear")
async def api_clear_tasks(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """清空任务列表。"""
    include_running = bool(payload.get("include_running", False))
    # 若要求连运行中的一起清，先停队列。
    if include_running:
        QUEUE.stop_all()
    return {"removed": QUEUE.clear(include_running=include_running)}


@app.post("/api/queue/start")
async def api_queue_start() -> Dict[str, Any]:
    """开始/恢复执行队列。"""
    QUEUE.resume()
    QUEUE.start()
    return {"ok": True, "running": QUEUE.is_running(), "paused": False}


@app.post("/api/queue/pause")
async def api_queue_pause() -> Dict[str, Any]:
    """暂停取新任务。"""
    QUEUE.pause()
    return {"ok": True, "running": QUEUE.is_running(), "paused": True}


@app.post("/api/queue/resume")
async def api_queue_resume() -> Dict[str, Any]:
    """继续调度。"""
    QUEUE.resume()
    return {"ok": True, "running": QUEUE.is_running(), "paused": False}


@app.post("/api/queue/stop")
async def api_queue_stop() -> Dict[str, Any]:
    """停止当前任务并终止调度。"""
    QUEUE.stop_all()
    return {"ok": True, "running": False, "paused": False}


@app.get("/api/queue")
async def api_queue_state() -> Dict[str, Any]:
    """返回队列状态与统计。"""
    return {"running": QUEUE.is_running(), "paused": False, "stats": QUEUE.stats()}


# ---------------------- API：快照与文档 ----------------------
@app.post("/api/snapshot")
async def api_snapshot(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """抽取视频快照。"""
    # 解析参数。
    src = str(payload.get("source", "") or "")
    count = int(payload.get("count", 5) or 5)
    out_dir = str(payload.get("out_dir", "") or "")
    ext = str(payload.get("ext", "jpg") or "jpg")
    width = int(payload.get("width", 0) or 0)
    # 源文件校验。
    if not src or not os.path.isfile(src):
        return {"ok": False, "files": [], "error": "源文件不存在。"}
    # 未指定输出目录时，默认在源文件旁的“快照”子目录。
    if not out_dir:
        out_dir = os.path.join(os.path.dirname(os.path.abspath(src)), "快照")
    # 探测时长用于计算时间点。
    info = media_info.probe(src)
    # 执行抽取。
    files = thumbnail.extract_snapshots(src, out_dir,
                                        base_name=os.path.splitext(os.path.basename(src))[0],
                                        duration=info.duration, count=count,
                                        target_ext=ext, width=width)
    return {"ok": bool(files), "files": files,
            "error": "" if files else "未能抽取到任何快照。"}


@app.post("/api/doc")
async def api_doc(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """文档类转换分发。"""
    op = str(payload.get("op", "") or "")
    # 图片 → PDF。
    if op == "images_to_pdf":
        inputs = payload.get("inputs") or []
        out = str(payload.get("out", "") or "")
        if not out:
            return {"ok": False, "outputs": [], "error": "未指定输出 PDF 路径。", "message": ""}
        res = doc_convert.images_to_pdf([str(p) for p in inputs], out,
                                        dpi=int(payload.get("dpi", 100) or 100))
        return {"ok": res.ok, "outputs": res.outputs, "error": res.error, "message": res.message}
    # PDF → 图片。
    if op == "pdf_to_images":
        inp = str(payload.get("input", "") or "")
        out_dir = str(payload.get("out_dir", "") or "")
        if not inp or not out_dir:
            return {"ok": False, "outputs": [], "error": "缺少输入 PDF 或输出目录。", "message": ""}
        res = doc_convert.pdf_to_images(inp, out_dir,
                                        base_name=str(payload.get("base_name", "") or ""),
                                        dpi=int(payload.get("dpi", 150) or 150),
                                        fmt=str(payload.get("fmt", "png") or "png"),
                                        page_range=str(payload.get("page_range", "") or ""))
        return {"ok": res.ok, "outputs": res.outputs, "error": res.error, "message": res.message}
    # 文本 → PDF。
    if op == "text_to_pdf":
        inp = str(payload.get("input", "") or "")
        out = str(payload.get("out", "") or "")
        if not inp or not out:
            return {"ok": False, "outputs": [], "error": "缺少输入文本或输出 PDF 路径。", "message": ""}
        res = doc_convert.text_to_pdf(inp, out,
                                      font_size=int(payload.get("font_size", 11) or 11),
                                      title=str(payload.get("title", "") or ""))
        return {"ok": res.ok, "outputs": res.outputs, "error": res.error, "message": res.message}
    return {"ok": False, "outputs": [], "error": f"未知的文档操作：{op}", "message": ""}


@app.post("/api/open")
async def api_open(payload: Dict[str, Any] = Body(default={})) -> Dict[str, Any]:
    """在服务器所在机器上打开一个目录（方便用户查看产物）。"""
    target = str(payload.get("path", "") or "")
    # 目标不存在时取其父目录。
    if target and not os.path.exists(target):
        target = os.path.dirname(target)
    if not target or not os.path.exists(target):
        return {"ok": False, "error": "路径不存在。"}
    try:
        # Windows 用资源管理器打开。
        if os.name == "nt":
            os.startfile(target)  # type: ignore[attr-defined]
        elif sys.platform == "darwin":
            os.system(f'open "{target}"')
        else:
            os.system(f'xdg-open "{target}"')
        return {"ok": True}
    except Exception as exc:
        return {"ok": False, "error": str(exc)}


# ---------------------- WebSocket：进度推送 ----------------------
@app.websocket("/ws/progress")
async def ws_progress(ws: WebSocket) -> None:
    """进度推送 WebSocket。"""
    # 接受连接。
    await ws.accept()
    # 登记到客户端集合。
    _CLIENTS.add(ws)
    # 连接建立后立刻推送一次全量任务与状态，避免前端首屏为空。
    try:
        await ws.send_text(json.dumps({"type": "init", "tasks": [t.to_dict() for t in QUEUE.tasks()],
                                       "stats": QUEUE.stats(), "running": QUEUE.is_running()},
                                      ensure_ascii=False))
    except Exception:
        pass
    try:
        # 保持连接：等待客户端消息（本项目前端不需要发指令，仅用于维持连接与心跳）。
        while True:
            await ws.receive_text()
    except WebSocketDisconnect:
        # 正常断开。
        pass
    except Exception:
        # 其它异常同样按断开处理。
        pass
    finally:
        # 清理登记。
        _CLIENTS.discard(ws)


# ---------------------- 静态站点托管 ----------------------
# 说明：挂载顺序很重要——先注册所有 /api 与 /ws 路由，再挂载静态文件到根路径，
# 否则静态挂载会拦截 API 请求。
_DIST_DIR = paths.bundled_frontend_dist()
# 若 dist 存在则挂载；否则提供内置提示页。
if os.path.isdir(_DIST_DIR):
    # 把 assets 等静态资源挂到 /assets（仅当该子目录存在时才挂载）。
    _ASSETS_DIR = os.path.join(_DIST_DIR, "assets")
    if os.path.isdir(_ASSETS_DIR):
        app.mount("/assets", StaticFiles(directory=_ASSETS_DIR), name="assets")


    @app.get("/")
    async def _index() -> FileResponse:
        """返回前端首页。"""
        return FileResponse(os.path.join(_DIST_DIR, "index.html"))


    @app.get("/{full_path:path}")
    async def _spa_fallback(full_path: str):
        """SPA 回退：非 API 路径一律返回 index.html，交给前端路由处理。"""
        # 若请求的是实际存在的静态文件，直接返回该文件。
        candidate = os.path.join(_DIST_DIR, full_path)
        if full_path and os.path.isfile(candidate):
            return FileResponse(candidate)
        # 否则返回首页（前端为单页应用）。
        index_file = os.path.join(_DIST_DIR, "index.html")
        if os.path.isfile(index_file):
            return FileResponse(index_file)
        return HTMLResponse(_fallback_html())
else:
    @app.get("/")
    async def _no_frontend() -> HTMLResponse:
        """前端尚未构建时的提示页。"""
        return HTMLResponse(_fallback_html())


def _fallback_html() -> str:
    """当 frontend/dist 缺失时展示的内置提示页。

    目的：保证 Web 端后端在“前端未构建”的情况下依然可用（API 全部可访问），
    同时明确告知用户如何构建前端，而不是给出一个 404。
    """
    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>{__app_name__} · Web 端</title>
<style>
 body{{font-family:"Microsoft YaHei",sans-serif;background:#0f1624;color:#dbe4f0;margin:0;padding:48px;}}
 .card{{max-width:760px;margin:0 auto;background:#16202f;border:1px solid #23324a;border-radius:12px;padding:32px;}}
 h1{{margin:0 0 8px;font-size:22px;}} p{{line-height:1.8;color:#9fb0c8;}}
 code{{background:#0b1220;padding:2px 6px;border-radius:4px;color:#6fd3ff;}}
 a{{color:#6fd3ff;text-decoration:none;}}
 ul{{line-height:2;}}
</style></head><body><div class="card">
<h1>{__app_name__} · Web 端后端已就绪</h1>
<p>当前未检测到已构建的前端静态资源目录，因此显示此提示页。<b>所有 API 均可正常访问</b>，例如
 <a href="/api/system">/api/system</a>、<a href="/api/presets">/api/presets</a>、<a href="/api/docs">/api/docs</a>。</p>
<p>如需完整网页界面，请在项目根目录执行：</p>
<ul>
 <li><code>cd frontend</code></li>
 <li><code>npm install</code></li>
 <li><code>npm run build</code></li>
</ul>
<p>构建完成后，产物会出现在 <code>frontend/dist</code>，重新启动本服务即可看到完整界面。</p>
<p>软件版本 v{__version__} · 作者 {__author__}</p>
</div></body></html>"""


# ---------------------- 启动入口 ----------------------
def run(host: str = "", port: int = 0, open_browser: bool = True) -> None:
    """启动 Web 服务。

    参数:
        host: 监听地址；空则读取配置。
        port: 监听端口；0 则读取配置。
        open_browser: 是否自动打开浏览器。
    """
    # 兜底取值。
    host = host or str(config.get("web_host", "127.0.0.1"))
    port = int(port or config.get("web_port", 8765) or 8765)
    # 延迟导入 uvicorn（只有真正启动 Web 时才需要）。
    import uvicorn
    # 延迟导入 webbrowser 用于自动打开浏览器。
    if open_browser:
        try:
            import webbrowser
            import threading
            # 延迟 1.5 秒打开，等待服务完成启动，避免浏览器打开到空白页。
            url = f"http://{'127.0.0.1' if host in ('0.0.0.0', '::') else host}:{port}/"
            threading.Timer(1.5, lambda: webbrowser.open(url)).start()
        except Exception:
            # 打不开浏览器不影响服务本身。
            pass
    # 打印一行便于用户知道访问地址。
    display_host = "127.0.0.1" if host in ("0.0.0.0", "::") else host
    print(f"[{__app_name__}] Web 端地址：http://{display_host}:{port}/")
    print(f"[{__app_name__}] 按 Ctrl+C 停止服务。")
    # 启动 uvicorn。
    # 关键参数说明（这些都是在“打包成无控制台程序”后才会暴露的坑）：
    #   log_config=None —— 让 uvicorn **不要**去配置自己的日志。
    #       原因：uvicorn 的默认日志配置会调用 sys.stdout.isatty()。而 PyInstaller 的
    #       无控制台程序里 sys.stdout 为 None，会直接抛出
    #       "AttributeError: 'NoneType' object has no attribute 'isatty'" 使服务无法启动。
    #       关闭它以后，日志统一交给本项目的根日志器（已在 app.main._prepare 中初始化好，
    #       输出到 %APPDATA% 下的 app.log）。
    #   access_log=False —— 关闭逐条请求日志，避免高频轮询把日志文件快速撑满（日志是轮转的，
    #       但仍没必要记录每一次前端轮询）。
    uvicorn.run(app, host=host, port=port, log_level="warning",
                log_config=None, access_log=False)
