// WebSocket 进度通道客户端：负责连接 /ws/progress、分发消息，并在断线后按指数退避自动重连。

// 重连起始延迟（毫秒）。
const BASE_DELAY = 500
// 重连最大延迟（毫秒），题目要求最长 10 秒。
const MAX_DELAY = 10000
// 退避倍率。
const FACTOR = 1.8
// 心跳发送间隔（毫秒），用于保持连接不被中间代理判定为空闲而断开。
const HEARTBEAT_MS = 20000

// 组装 WebSocket 地址：与页面同源，自动匹配 ws / wss 协议。
function socketUrl() {
    // 依据页面协议选择加密或非加密的 WebSocket 协议。
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // 使用同源主机与端口，避免硬编码端口导致部署后失联。
    return `${proto}//${window.location.host}/ws/progress`
}

// 创建进度通道。
// 参数 handlers：{ onMessage(msg), onStatus(connected) }，均为可选回调。
export function createProgressSocket(handlers) {
    // 合并默认回调，避免调用方未传时出错。
    const onMessage = (handlers && handlers.onMessage) || function () {
    }
    // 连接状态回调。
    const onStatus = (handlers && handlers.onStatus) || function () {
    }
    // 当前 WebSocket 实例。
    let ws = null
    // 下一次重连的延迟值。
    let delay = BASE_DELAY
    // 是否已被调用方主动关闭（主动关闭后不再重连）。
    let closed = false
    // 重连定时器句柄。
    let retryTimer = null
    // 心跳定时器句柄。
    let beatTimer = null

    // 清理定时器，避免重复定时导致连接抖动。
    function clearTimers() {
        // 清理重连定时器。
        if (retryTimer) {
            clearTimeout(retryTimer);
            retryTimer = null
        }
        // 清理心跳定时器。
        if (beatTimer) {
            clearInterval(beatTimer);
            beatTimer = null
        }
    }

    // 建立连接。
    function connect() {
        // 已主动关闭则不再连接。
        if (closed) return
        // 清理上一次的定时器。
        clearTimers()
        // 创建 WebSocket 实例。
        try {
            ws = new WebSocket(socketUrl())      // 使用同源地址
        } catch (err) {
            // 构造失败（例如地址非法）时直接进入重连流程。
            scheduleRetry()
            return
        }
        // 连接建立后：通知状态、重置退避、发起心跳。
        ws.onopen = function () {
            // 重置退避延迟为初始值，说明连接已恢复健康。
            delay = BASE_DELAY
            // 通知外部已连接。
            onStatus(true)
            // 主动问候一次（后端接受任意文本消息，也用于验证链路畅通）。
            safeSend({type: 'hello'})
            // 启动心跳定时器。
            beatTimer = setInterval(function () {
                // 周期性发送心跳文本。
                safeSend({type: 'ping'})
            }, HEARTBEAT_MS)
        }
        // 收到消息：解析 JSON 并分发。
        ws.onmessage = function (ev) {
            // 后端推送的是 JSON 文本。
            const raw = ev && ev.data
            // 空消息直接忽略。
            if (!raw) return
            // 单条推送解析失败只忽略该条，不打断连接。
            try {
                // 解析为对象。
                const msg = JSON.parse(raw)
                // 交给上层处理。
                onMessage(msg)
            } catch (err) {
                // 非 JSON 消息（如纯文本日志）同样忽略，保证通道稳定。
            }
        }
        // 连接关闭：通知状态并安排重连。
        ws.onclose = function () {
            // 通知外部连接已断开。
            onStatus(false)
            // 主动关闭时不重连。
            if (!closed) scheduleRetry()
        }
        // 连接错误：不额外处理，交由 onclose 统一安排重连（错误后浏览器必然触发关闭）。
        ws.onerror = function () {
            // 仅尝试关闭以触发 onclose 流程，避免部分浏览器的错误态悬挂。
            try {
                if (ws) ws.close()
            } catch (err) { /* 忽略关闭异常 */
            }
        }
    }

    // 发送消息（内部使用，自动做状态判断与异常吞噬）。
    function safeSend(obj) {
        // 未建立连接或状态不为 OPEN 时直接跳过。
        if (!ws || ws.readyState !== WebSocket.OPEN) return
        // 序列化并发送。
        try {
            ws.send(JSON.stringify(obj))               // 文本帧
        } catch (err) {
            // 发送失败不影响后续重连逻辑。
        }
    }

    // 安排一次指数退避重连。
    function scheduleRetry() {
        // 已主动关闭则不安排。
        if (closed) return
        // 避免重复安排。
        if (retryTimer) return
        // 记录本次使用的延迟值。
        const wait = delay
        // 设置下一次的延迟（指数增长并封顶 10 秒）。
        delay = Math.min(Math.round(delay * FACTOR), MAX_DELAY)
        // 定时重连。
        retryTimer = setTimeout(function () {
            // 清空句柄，允许下一次重新安排。
            retryTimer = null
            // 发起连接。
            connect()
        }, wait)
    }

    // 主动关闭通道。
    function close() {
        // 标记为主动关闭，阻止后续重连。
        closed = true
        // 清理所有定时器。
        clearTimers()
        // 关闭连接（存在且处于开启/连接中状态时）。
        try {
            if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
                ws.close()
            }
        } catch (err) {
            // 忽略关闭异常。
        }
    }

    // 立即启动连接。
    connect()

    // 对外返回控制句柄。
    return {close}
}
