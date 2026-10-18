// 格式化与分类工具：把后端返回的原始数值/状态转成界面上可直接显示的文本。

// 视频类扩展名集合（用于文件分类与浏览过滤）。
export const VIDEO_EXT = new Set(['mp4', 'mkv', 'mov', 'avi', 'flv', 'webm', 'ts', 'mpg', 'wmv', '3gp', 'm4v', 'gif', 'mpeg', 'm2ts', 'rmvb', 'vob', 'ogv'])
// 音频类扩展名集合。
export const AUDIO_EXT = new Set(['mp3', 'aac', 'm4a', 'wav', 'flac', 'ogg', 'opus', 'wma', 'ac3', 'amr', 'aiff', 'ape', 'alac', 'mka'])
// 图片类扩展名集合（注意 gif 同时属于视频与图片，浏览时会按场景各自放行）。
export const IMAGE_EXT = new Set(['jpg', 'jpeg', 'png', 'webp', 'bmp', 'tiff', 'tif', 'gif', 'avif', 'ico', 'heic'])
// 文档类扩展名集合。
export const DOC_EXT = new Set(['pdf', 'txt', 'md', 'log', 'csv'])
// 字幕类扩展名集合。
export const SUB_EXT = new Set(['srt', 'ass', 'ssa', 'vtt'])

// 从路径中取出小写扩展名（不含点）。
export function extOf(path) {
    // 空值保护，避免抛异常。
    if (!path) return ''
    // 取最后一个点之后的片段。
    const idx = String(path).lastIndexOf('.')
    // 没有点则视为无扩展名。
    if (idx < 0) return ''
    // 统一小写便于比较。
    return String(path).slice(idx + 1).toLowerCase()
}

// 从路径中取出文件名（兼容 Windows 反斜杠与类 Unix 斜杠）。
export function nameOf(path) {
    // 空值保护。
    if (!path) return ''
    // 用正则统一按两种分隔符切分。
    const parts = String(path).split(/[\\/]/)
    // 返回最后一段。
    return parts[parts.length - 1] || String(path)
}

// 从路径中取出所在目录。
export function dirOf(path) {
    // 空值保护。
    if (!path) return ''
    // 规范化后取最后一个分隔符之前的内容。
    const s = String(path)
    // 找到最后一个分隔符位置。
    const idx = Math.max(s.lastIndexOf('\\'), s.lastIndexOf('/'))
    // 没有分隔符则说明只有文件名，目录视为空。
    if (idx < 0) return ''
    // 返回目录部分。
    return s.slice(0, idx)
}

// 依据扩展名判断文件大类别，用于界面分组与提示。
export function kindOf(ext) {
    // 去掉可能带点的写法。
    const e = String(ext || '').toLowerCase().replace(/^\./, '')
    // 视频优先（gif 归入视频，便于“视频转 GIF”场景）。
    if (VIDEO_EXT.has(e)) return 'video'
    // 其次音频。
    if (AUDIO_EXT.has(e)) return 'audio'
    // 其次图片。
    if (IMAGE_EXT.has(e)) return 'image'
    // 最后文档。
    if (DOC_EXT.has(e)) return 'doc'
    // 其余归为未知。
    return 'other'
}

// 字节数 → 人类可读体积。
export function fmtSize(bytes) {
    // 非法值统一显示为短横线。
    if (bytes === null || bytes === undefined || isNaN(bytes)) return '—'
    // 转为数字。
    const n = Number(bytes)
    // 0 直接显示。
    if (n === 0) return '0 B'
    // 单位阶梯。
    const units = ['B', 'KB', 'MB', 'GB', 'TB']
    // 计算层级索引。
    let i = Math.floor(Math.log(Math.abs(n)) / Math.log(1024))
    // 限制索引范围，避免超出单位表。
    if (i < 0) i = 0
    // 上限保护。
    if (i > units.length - 1) i = units.length - 1
    // 换算数值。
    const v = n / Math.pow(1024, i)
    // 大于 100 时保留整数，否则保留一位小数，兼顾可读性与精度。
    return `${v >= 100 ? v.toFixed(0) : v.toFixed(1)} ${units[i]}`
}

// 秒数 → 小时:分:秒（用于时长展示）。
export function fmtDuration(seconds) {
    // 非数字或负数统一显示为短横线。
    if (seconds === null || seconds === undefined || isNaN(seconds) || seconds < 0) return '—'
    // 取整秒。
    const total = Math.floor(Number(seconds))
    // 计算小时。
    const h = Math.floor(total / 3600)
    // 计算分钟。
    const m = Math.floor((total % 3600) / 60)
    // 计算秒。
    const s = total % 60
    // 补零函数。
    const p = (x) => String(x).padStart(2, '0')
    // 小时大于 0 时输出三段，否则输出两段。
    return h > 0 ? `${h}:${p(m)}:${p(s)}` : `${p(m)}:${p(s)}`
}

// 秒数 → 简短的“剩余时间”文本。
export function fmtEta(seconds) {
    // 非正数视为未知。
    if (!seconds || isNaN(seconds) || Number(seconds) <= 0) return '—'
    // 复用时长格式化结果。
    return fmtDuration(seconds)
}

// 进度值 → 百分比文本。
export function fmtPercent(v) {
    // 非数字显示为 0%。
    if (v === null || v === undefined || isNaN(v)) return '0%'
    // 限制在 0~100 之间并保留一位小数。
    return `${Math.max(0, Math.min(100, Number(v))).toFixed(1)}%`
}

// 速度值 → 倍速文本。
export function fmtSpeed(v) {
    // 非数字或 0 显示为占位。
    if (!v || isNaN(v) || Number(v) <= 0) return '—'
    // 保留两位小数加 x 后缀，表示实时倍速。
    return `${Number(v).toFixed(2)}x`
}

// 体积变化 → 百分比增减文本。
export function fmtVolume(inSize, outSize) {
    // 输出体积尚未产生时无法计算。
    if (!outSize || isNaN(outSize)) return '—'
    // 输入体积缺失或为 0 时只能显示输出体积。
    if (!inSize || isNaN(inSize) || Number(inSize) <= 0) return fmtSize(outSize)
    // 计算变化率。
    const rate = (Number(outSize) - Number(inSize)) / Number(inSize) * 100
    // 组合“输出体积（增减百分比）”的展示形式。
    return `${fmtSize(outSize)}（${rate >= 0 ? '+' : ''}${rate.toFixed(0)}%）`
}

// 后端状态码 → 中文文字（优先使用后端给出的 status_text）。
export function statusText(task) {
    // 后端已给出中文状态文字时直接采用。
    if (task && task.status_text) return task.status_text
    // 兜底映射表。
    const map = {
        waiting: '等待中',      // 尚未开始
        running: '运行中',      // 正在转码
        done: '已完成',         // 成功结束
        failed: '失败',         // 出错
        canceled: '已取消',     // 用户取消
        skipped: '已跳过'       // 重复或无效任务
    }
    // 返回映射结果，未知状态原样输出。
    return map[task && task.status] || (task && task.status) || '未知'
}

// 状态 → CSS 类名后缀。
export function statusClass(status) {
    // 已知状态直接作为类名后缀使用（与样式表一致）。
    const known = ['waiting', 'running', 'done', 'failed', 'canceled', 'skipped']
    // 命中则返回，未命中回退到 waiting。
    return known.indexOf(status) >= 0 ? status : 'waiting'
}

// 把字符串安全转换为整数（用于数值型表单字段）。
export function toInt(value, fallback) {
    // 转换为数字。
    const n = parseInt(value, 10)
    // 非数字时返回兜底值。
    return isNaN(n) ? fallback : n
}

// 把字符串安全转换为浮点数（用于比例、倍速等字段）。
export function toFloat(value, fallback) {
    // 转换为数字。
    const n = parseFloat(value)
    // 非数字时返回兜底值。
    return isNaN(n) ? fallback : n
}
