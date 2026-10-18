// 后端接口封装层：统一处理请求方法、JSON 编解码与错误抛出，避免各组件重复写 fetch 细节。
// 说明：全部接口都以同源方式访问（基址为空串），因此既能被 FastAPI 直接托管，也能走 Vite 开发代理。

// 同源基址：留空表示与页面同源，构建后由 FastAPI 在根路径托管。
const BASE = ''

// 统一的请求函数。
// 参数 method：HTTP 方法；path：以 /api 开头的路径；body：可选请求体（对象会被序列化为 JSON）。
async function request(method, path, body) {
    // 组装 fetch 选项。
    const options = {
        method,                                     // 请求方法
        headers: {'Content-Type': 'application/json'}  // 统一声明 JSON 请求体
    }
    // 仅在存在请求体时才写入 body，避免 GET 请求携带无意义的负载。
    if (body !== undefined && body !== null) {
        options.body = JSON.stringify(body)         // 序列化为 JSON 文本
    }
    // 发起请求。
    const res = await fetch(BASE + path, options)
    // 读取响应文本（后端全部返回 JSON）。
    const text = await res.text()
    // 解析 JSON；解析失败时给出可读的错误信息而不是抛出底层异常。
    let data = null                              // 存放解析结果
    try {
        data = text ? JSON.parse(text) : null      // 空响应体按 null 处理
    } catch (err) {
        // 非 JSON 响应（例如代理错误页）时，直接抛出状态码与片段，便于排查。
        throw new Error(`接口 ${path} 返回了非 JSON 响应（HTTP ${res.status}）：${text.slice(0, 160)}`)
    }
    // HTTP 层错误处理。
    if (!res.ok) {
        // 优先使用后端返回的 error 字段作为错误信息。
        const msg = (data && (data.error || data.detail || data.message)) || `请求失败：HTTP ${res.status}`
        throw new Error(String(msg))
    }
    // 返回解析后的数据。
    return data
}

// 对外暴露的接口集合：每个方法对应后端一个端点。
export const api = {
    // 读取系统与内核信息（含 ffmpeg 路径、能力统计、硬件编码器列表）。
    system: () => request('GET', '/api/system'),
    // 读取全部内置预设。
    presets: () => request('GET', '/api/presets'),
    // 读取各类型可用的目标格式。
    formats: () => request('GET', '/api/formats'),
    // 浏览服务器所在机器的目录（空路径表示列出盘符/主目录）。
    browse: (path) => request('POST', '/api/browse', {path: path || ''}),
    // 探测单个媒体文件的流信息。
    probe: (path) => request('POST', '/api/probe', {path}),
    // 读取当前设置字典。
    getSettings: () => request('GET', '/api/settings'),
    // 局部更新设置字典。
    saveSettings: (patch) => request('POST', '/api/settings', patch),
    // 批量添加转码任务（sources 为路径数组，settings 为公共参数）。
    addTasks: (sources, settings) => request('POST', '/api/tasks', {sources, settings}),
    // 读取任务列表与统计。
    listTasks: () => request('GET', '/api/tasks'),
    // 移除单个任务（运行中的任务后端会拒绝，返回值 ok 为 false）。
    removeTask: (tid) => request('DELETE', `/api/tasks/${encodeURIComponent(tid)}`),
    // 清空任务列表，可选择是否连同运行中的任务一起清理。
    clearTasks: (includeRunning) => request('POST', '/api/tasks/clear', {include_running: !!includeRunning}),
    // 队列控制：start / pause / resume / stop。
    queue: (action) => request('POST', `/api/queue/${action}`),
    // 读取队列运行状态与统计。
    queueState: () => request('GET', '/api/queue'),
    // 视频抽帧。
    snapshot: (payload) => request('POST', '/api/snapshot', payload),
    // 文档类转换（图片转 PDF / PDF 转图片 / 文本转 PDF）。
    doc: (payload) => request('POST', '/api/doc', payload),
    // 在服务器所在机器上打开目录。
    open: (path) => request('POST', '/api/open', {path})
}
