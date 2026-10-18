// 全局状态与动作中心：所有页面共用的数据、后端调用与 WebSocket 分发逻辑都集中在这里。
// 说明：采用 Vue 的 reactive 单例，任何组件 import 同一份 store 即可共享状态并自动触发更新。

import {reactive} from 'vue'                        // 引入响应式对象工厂
import {api} from './api.js'                        // 引入后端接口集合
import {createProgressSocket} from './ws.js'        // 引入进度通道客户端
import {kindOf, extOf, nameOf} from './format.js'   // 引入扩展名分类与格式化工具

// 构造一份完整的转码参数对象（字段与后端 TranscodeSettings 一一对应）。
// 每个类别页面各自持有一份，互不干扰，避免“改视频参数把音频参数也改了”的问题。
export function defaultTranscode(targetFormat) {
    // 返回带默认值的参数对象。
    return {
        source: '',              // 源文件路径（提交时由后端按 sources 逐条覆盖）
        target_format: targetFormat || 'mp4',  // 目标格式（扩展名，不含点）
        preset_key: '',          // 使用的预设 key，用于后端记忆
        video_codec: '',         // 视频编码器，空表示交给容器默认值
        audio_codec: '',         // 音频编码器，空表示交给容器默认值
        quality: 'medium',       // 质量档位：lossless / high / medium / low / custom
        crf: '',                 // 恒定质量因子（数字字符串，优先于视频码率）
        v_bitrate: '',           // 视频码率，例如 "2500k"
        a_bitrate: '',           // 音频码率，例如 "128k"
        pixel_format: '',        // 目标像素格式，例如 yuv420p
        threads: 0,              // 编码线程数，0 表示由 ffmpeg 自动决定
        width: 0,                // 目标宽度，0 表示保持原样
        height: 0,               // 目标高度，0 表示保持原样
        scale_mode: 'fit',       // 缩放模式：fit（完整显示）/ fill（裁切填充）/ stretch（拉伸）
        fps: '',                 // 目标帧率，空表示保持原样
        rotate: 0,               // 旋转角度：0 / 90 / 180 / 270
        flip: 'none',            // 镜像方式：none / h / v / both
        crop: '',                // 裁剪区域，格式 "宽:高:横坐标:纵坐标"
        watermark_path: '',      // 图片水印路径
        watermark_text: '',      // 文字水印内容
        watermark_pos: 'tr',     // 水印位置：tl / tr / bl / br / center
        watermark_scale: 0.15,   // 图片水印相对宽度比例
        watermark_opacity: 0.75, // 水印不透明度（0~1）
        subtitle_path: '',       // 外挂字幕路径（烧录进画面）
        audio_channels: 0,       // 目标声道数，0 表示保持
        sample_rate: 0,          // 目标采样率，0 表示保持
        volume: '',              // 音量滤镜参数，例如 "1.5" 或 "-6dB"
        start: '',               // 起始时间，例如 "00:00:10"
        end: '',                 // 结束时间（绝对时间点）
        duration: '',            // 截取时长（与 end 二选一）
        speed: 1,                // 播放速度倍率
        meta_title: '',          // 元数据：标题
        meta_artist: '',         // 元数据：艺术家
        meta_album: '',          // 元数据：专辑
        meta_comment: '',        // 元数据：备注
        copy_mode: false,        // 是否仅换容器不重编码
        hwaccel: 'none',         // 硬件加速策略：none / auto / 具体编码器名
        hwaccel_vendor: 'auto',  // 硬件厂商偏好：auto / nvidia / intel / amd
        custom_args: [],         // 自定义追加参数数组（高级页使用）
        stream_mode: 'auto',     // 流选择：auto / first / custom
        video_index: -1,         // 指定视频流序号（stream_mode=custom 时有效）
        audio_index: -1,         // 指定音频流序号
        subtitle_index: -1,      // 指定字幕流序号
        drop_audio: false,       // 是否强制丢弃音频
        drop_video: false,       // 是否强制丢弃视频
        output_dir: '',          // 输出目录，空表示与源文件同目录
        output_name: '',         // 显式输出文件名，空表示自动生成
        overwrite: false         // 是否允许覆盖已存在的输出文件
    }
}

// 全局响应式状态。
export const store = reactive({
    // 当前激活的顶部标签页标识。
    tab: 'video',
    // 顶部轻提示文本（空字符串表示不显示）。
    toast: '',
    // 顶部轻提示的定时器句柄（内部使用）。
    _toastTimer: null,

    // 系统与内核信息（来自 /api/system）。
    system: null,
    // 内置预设列表（来自 /api/presets）。
    presets: [],
    // 各类型可用目标格式（来自 /api/formats）。
    formats: {video: [], audio: [], image: [], document: []},
    // 设置字典（来自 /api/settings）。
    settings: {},
    // 设置是否已经加载完成，用于避免用空值覆盖后端配置。
    settingsLoaded: false,
    // 基础数据是否加载完成（用于界面首次渲染的骨架提示）。
    baseLoaded: false,
    // 内核就绪标志（ffmpeg/ffprobe 是否可用）。
    kernelReady: false,

    // 待转文件列表：元素结构 { path, name, ext, size, kind }。
    files: [],
    // 当前在左侧列表中选中的文件路径（用于显示探测信息）。
    activeFile: '',
    // 当前选中文件的探测结果（来自 /api/probe）。
    probeInfo: null,
    // 探测进行中的标志。
    probing: false,

    // 四个类别的转码参数集合。
    params: {
        video: defaultTranscode('mp4'),      // 视频页参数
        audio: defaultTranscode('mp3'),      // 音频页参数
        image: defaultTranscode('jpg'),      // 图片页参数
        advanced: defaultTranscode('mp4')    // 高级页参数
    },
    // 图片页的 JPEG 质量数值（q:v），会合并进 custom_args。
    imageQv: '',
    // 图片页是否启用背景色填充。
    imageBgEnabled: false,
    // 图片页背景色取值（十六进制颜色）。
    imageBgColor: '#000000',
    // 高级页的自定义参数文本（每行一个参数）。
    advancedText: '',

    // 任务列表与统计。
    tasks: [],
    // 任务统计（后端返回的 stats 字典）。
    stats: {total: 0, ok: 0, fail: 0, skip: 0, canceled: 0, waiting: 0},
    // 队列是否正在运行。
    queueRunning: false,
    // 队列是否处于暂停状态。
    queuePaused: false,
    // 在任务表格中勾选的任务 ID 集合。
    selected: [],
    // 进度通道推送的日志文本（保留最近若干条）。
    logs: [],
    // 进度通道连接状态。
    wsConnected: false,

    // 视频抽帧结果文件列表（来自 /api/snapshot）。
    snapshotFiles: [],
    // 视频抽帧的最近一次错误信息。
    snapshotError: '',
    // 抽帧执行中的标志。
    snapshotBusy: false,

    // 文档页运行结果信息（三个子操作共用）。
    docResult: {ok: false, outputs: [], error: '', message: ''},
    // 文档页是否正在执行。
    docBusy: false,

    // 目录浏览弹窗状态：open / title / mode / callback。
    browser: {open: false, title: '', mode: 'media', callback: null},

    // 图片转 PDF 的已选图片顺序列表（元素为路径）。
    pdfImages: [],
    // 图片转 PDF 的输出目录。
    pdfOutDir: '',
    // 图片转 PDF 的输出文件名（含扩展名）。
    pdfOutName: 'merged.pdf',
    // 图片转 PDF 的 DPI。
    pdfDpi: 100,
    // PDF 转图片的输入文件路径。
    pdfIn: '',
    // PDF 转图片的输出目录。
    pdfOutDir2: '',
    // PDF 转图片的基础文件名。
    pdfBaseName: '',
    // PDF 转图片的渲染 DPI。
    pdfToImgDpi: 150,
    // PDF 转图片的输出格式。
    pdfToImgFmt: 'png',
    // PDF 转图片的页码范围。
    pdfPageRange: '',
    // 文本转 PDF 的输入文件路径。
    txtIn: '',
    // 文本转 PDF 的输出目录。
    txtOutDir: '',
    // 文本转 PDF 的输出文件名（含扩展名）。
    txtOutName: 'document.pdf',
    // 文本转 PDF 的字号。
    txtFontSize: 11,
    // 文本转 PDF 的标题（空表示不添加标题）。
    txtTitle: ''
})

// 显示顶部轻提示。
export function setToast(text, ms) {
    // 写入提示文本。
    store.toast = text || ''
    // 清理上一条提示的定时器，避免新旧提示互相干扰。
    if (store._toastTimer) clearTimeout(store._toastTimer)
    // 只有在有文本时才安排自动清除。
    if (store.toast) {
        // 默认 3.2 秒后自动消失。
        store._toastTimer = setTimeout(function () {
            // 清空提示文本。
            store.toast = ''
            // 重置句柄。
            store._toastTimer = null
        }, ms || 3200)
    }
}

// 打开目录浏览弹窗。
// 参数 mode：'media' 媒体文件 / 'image' 图片 / 'video' 视频 / 'pdf' PDF / 'text' 文本 / 'subtitle' 字幕 / 'dir' 目录 / 'any' 任意文件。
// 参数 multiple：是否允许一次选择多个文件。
// 参数 callback：确认选择后的回调，接收“已选路径数组”。
export function openBrowser(options) {
    // 记录弹窗标题。
    store.browser.title = (options && options.title) || 'Select File'
    // 记录过滤模式。
    store.browser.mode = (options && options.mode) || 'media'
    // 记录是否允许多选。
    store.browser.multiple = !!(options && options.multiple)
    // 记录确认回调。
    store.browser.callback = (options && options.callback) || null
    // 打开弹窗。
    store.browser.open = true
}

// 关闭目录浏览弹窗并清空回调，避免残留引用导致内存泄漏。
export function closeBrowser() {
    // 关闭弹窗标记。
    store.browser.open = false
    // 释放回调引用。
    store.browser.callback = null
}

// 加载基础数据：系统信息、预设、格式表与设置。
export async function loadBase() {
    // 三个彼此独立的请求并行发出，缩短首屏等待时间。
    const [system, presets, formats] = await Promise.all([
        api.system(),          // 系统与内核信息
        api.presets(),         // 内置预设列表
        api.formats()          // 各类型可用格式
    ])
    // 写入系统信息。
    store.system = system
    // 记录内核就绪状态（后端字段名为 ready）。
    store.kernelReady = !!(system && system.ready)
    // 写入预设列表（后端返回数组）。
    store.presets = Array.isArray(presets) ? presets : []
    // 写入格式表，并为缺失字段补空数组，保证界面不会因为空值报错。
    store.formats = {
        video: (formats && formats.video) || [],          // 视频格式
        audio: (formats && formats.audio) || [],          // 音频格式
        image: (formats && formats.image) || [],          // 图片格式
        document: (formats && formats.document) || []     // 文档格式
    }
    // 标记基础数据加载完成。
    store.baseLoaded = true
}

// 加载设置字典。
export async function loadSettings() {
    // 拉取设置。
    const data = await api.getSettings()
    // 写入状态（后端返回的是扁平字典）。
    store.settings = data || {}
    // 标记设置已加载。
    store.settingsLoaded = true
}

// 保存设置（局部更新）。
// 参数 patch：需要更新的键值对；返回值：后端返回的完整设置字典。
export async function saveSettings(patch) {
    // 提交到后端。
    const res = await api.saveSettings(patch)
    // 用后端返回的最新字典覆盖本地，保证前后端一致。
    if (res && res.settings) store.settings = res.settings
    // 返回结果。
    return res
}

// 依据当前激活标签页取得对应的参数对象键名。
export function currentParamKey() {
    // 视频 / 音频 / 图片 / 高级 四个页面各自对应一份参数。
    const map = {video: 'video', audio: 'audio', image: 'image', advanced: 'advanced'}
    // 返回映射结果；文档 / 队列 / 设置 / 关于页没有参数对象。
    return map[store.tab] || null
}

// 判断某个标签页需要提交哪些类型的文件。
export function filesForTab(tab) {
    // 视频页只提交视频文件。
    if (tab === 'video') return store.files.filter((f) => f.kind === 'video')
    // 音频页只提交音频文件。
    if (tab === 'audio') return store.files.filter((f) => f.kind === 'audio')
    // 图片页只提交图片文件。
    if (tab === 'image') return store.files.filter((f) => f.kind === 'image')
    // 高级页不限制类型，全部提交。
    return store.files.slice()
}

// 解析高级页文本为参数数组（每行一个参数，忽略空行与以 # 开头的注释行）。
export function parseAdvancedText() {
    // 按换行切分。
    return String(store.advancedText || '')
        .split(/\r?\n/)                                    // 兼容 Windows 与 Unix 换行
        .map((line) => line.trim())                        // 去掉首尾空白
        .filter((line) => line && line.charAt(0) !== '#')  // 丢弃空行与注释行
}

// 依据某个类别的参数构造提交给后端的 settings 对象（纯数据、不含响应式代理）。
export function buildSettings(key) {
    // 取出对应类别的参数对象。
    const p = store.params[key] || defaultTranscode('mp4')
    // 组装基础字段（逐项显式拷贝，避免把响应式代理直接交给后端）。
    const s = {
        target_format: p.target_format,          // 目标格式
        preset_key: p.preset_key,                // 预设标识
        video_codec: p.video_codec,              // 视频编码器
        audio_codec: p.audio_codec,              // 音频编码器
        quality: p.quality,                      // 质量档位
        crf: String(p.crf || ''),                // 恒定质量因子
        v_bitrate: p.v_bitrate,                  // 视频码率
        a_bitrate: p.a_bitrate,                  // 音频码率
        pixel_format: p.pixel_format,            // 像素格式
        threads: Number(store.settings.threads || 0) || Number(p.threads || 0),  // 线程数（优先取全局设置）
        width: Number(p.width || 0),             // 目标宽度
        height: Number(p.height || 0),           // 目标高度
        scale_mode: p.scale_mode,                // 缩放模式
        fps: String(p.fps || ''),                // 目标帧率
        rotate: Number(p.rotate || 0),           // 旋转角度
        flip: p.flip,                            // 镜像方式
        crop: p.crop,                            // 裁剪区域
        watermark_path: p.watermark_path,        // 图片水印路径
        watermark_text: p.watermark_text,        // 文字水印内容
        watermark_pos: p.watermark_pos,          // 水印位置
        watermark_scale: Number(p.watermark_scale || 0),     // 图片水印比例
        watermark_opacity: Number(p.watermark_opacity || 0), // 水印不透明度
        subtitle_path: p.subtitle_path,          // 字幕路径
        audio_channels: Number(p.audio_channels || 0),  // 目标声道数
        sample_rate: Number(p.sample_rate || 0),        // 目标采样率
        volume: p.volume,                        // 音量滤镜参数
        start: p.start,                          // 起始时间
        end: p.end,                              // 结束时间
        duration: p.duration,                    // 截取时长
        speed: Number(p.speed || 1),             // 变速倍率
        meta_title: p.meta_title,                // 元数据标题
        meta_artist: p.meta_artist,              // 元数据艺术家
        meta_album: p.meta_album,                // 元数据专辑
        meta_comment: p.meta_comment,            // 元数据备注
        copy_mode: !!p.copy_mode,                // 仅换容器不重编码
        hwaccel: p.hwaccel,                      // 硬件加速策略
        hwaccel_vendor: p.hwaccel_vendor,        // 硬件厂商偏好
        custom_args: Array.isArray(p.custom_args) ? p.custom_args.slice() : [],  // 自定义参数（图片与高级页会在此基础上继续追加）
        stream_mode: p.stream_mode,              // 流选择模式
        video_index: Number(p.video_index),      // 指定视频流序号
        audio_index: Number(p.audio_index),      // 指定音频流序号
        subtitle_index: Number(p.subtitle_index),// 指定字幕流序号
        drop_audio: !!p.drop_audio,              // 丢弃音频
        drop_video: !!p.drop_video,              // 丢弃视频
        output_dir: p.output_dir,                // 输出目录
        output_name: p.output_name,              // 输出文件名
        overwrite: !!p.overwrite                 // 是否覆盖
    }
    // 高级页：自定义参数来自多行文本编辑器。
    if (key === 'advanced') {
        // 解析文本为参数数组。
        s.custom_args = parseAdvancedText()
    }
    // 图片页：在预设自带参数的基础上追加用户的质量数值与背景色。
    if (key === 'image') {
        // 以预设自带的附加参数（如 PNG 的压缩级别、WebP 的无损开关）为基础。
        const base = s.custom_args.slice()
        // 判断用户是否显式填写了质量数值。
        const hasQv = String(store.imageQv || '').trim() !== ''
        // 准备最终参数数组。
        const extra = []
        // 用户显式填写质量数值时，需要先剔除预设里的 -q:v 及其取值，避免出现两个同名参数。
        for (let i = 0; i < base.length; i += 1) {
            // 命中 -q:v 时连同其后的数值一起跳过。
            if (hasQv && base[i] === '-q:v') {
                // 跳过取值。
                i += 1
                // 继续下一项。
                continue
            }
            // 保留其余参数。
            extra.push(base[i])
        }
        // 用户填写了质量数值时追加 -q:v 与数值。
        if (hasQv) {
            // 追加质量参数的两个词元。
            extra.push('-q:v', String(store.imageQv).trim())
        }
        // 用户启用背景色时追加 -background_color。
        if (store.imageBgEnabled && store.imageBgColor) {
            // 追加背景色参数的两个词元。
            extra.push('-background_color', String(store.imageBgColor))
        }
        // 写回自定义参数数组。
        s.custom_args = extra
    }
    // 返回组装结果。
    return s
}

// 把参数变化应用到界面上（补零等），此处仅做兜底校验。
function sanitize(key) {
    // 取出参数对象。
    const p = store.params[key]
    // 不存在则直接返回。
    if (!p) return
    // 宽度与高度不能为负数。
    if (Number(p.width) < 0) p.width = 0
    // 高度同样不能为负数。
    if (Number(p.height) < 0) p.height = 0
    // 不透明度限定在 0~1 之间。
    if (Number(p.watermark_opacity) < 0) p.watermark_opacity = 0
    // 不透明度上限。
    if (Number(p.watermark_opacity) > 1) p.watermark_opacity = 1
    // 水印比例限定在 0~1 之间。
    if (Number(p.watermark_scale) < 0) p.watermark_scale = 0
    // 水印比例上限。
    if (Number(p.watermark_scale) > 1) p.watermark_scale = 1
}

// 把当前页面的参数提交到后端，生成转码任务。
export async function submitTasks() {
    // 取得当前页面对应的参数键名。
    const key = currentParamKey()
    // 非工作页面不允许提交。
    if (!key) {
        // 提示用户切换到可提交的页面。
        setToast('Please add tasks on the Video / Audio / Image / Advanced page first.')
        // 提前返回。
        return
    }
    // 取出该页面应当提交的文件列表。
    const files = filesForTab(store.tab)
    // 没有可用文件时给出明确提示。
    if (!files.length) {
        // 提示需要先添加文件。
        setToast('No files of this type in the list. Add them first with "Add Files".')
        // 提前返回。
        return
    }
    // 做一次参数兜底校验。
    sanitize(key)
    // 构造公共设置。
    const settings = buildSettings(key)
    // 由后端逐条写入 source 字段，因此这里不传 source。
    try {
        // 提交任务。
        const res = await api.addTasks(files.map((f) => f.path), settings)
        // 提示添加结果。
        setToast(`Added ${res.added || 0} task(s)${res.skipped ? `, skipped ${res.skipped}` : ''}.`)
        // 立即刷新任务列表，避免等待 WebSocket 推送造成界面延迟。
        await fetchTasks()
    } catch (err) {
        // 失败时把错误原样提示给用户。
        setToast(`Failed to add tasks: ${err.message}`)
    }
}

// 刷新任务列表与统计。
export async function fetchTasks() {
    // 拉取任务数据。
    const data = await api.listTasks()
    // 写入任务数组。
    store.tasks = (data && data.tasks) || []
    // 写入统计。
    store.stats = (data && data.stats) || store.stats
    // 清理已被移除任务在选中集合中的残留项。
    const ids = new Set(store.tasks.map((t) => t.tid))
    // 过滤选中集合。
    store.selected = store.selected.filter((tid) => ids.has(tid))
}

// 依据 tid 插入或更新单个任务（WebSocket 推送使用）。
export function upsertTask(task) {
    // 任务无效则忽略。
    if (!task || !task.tid) return
    // 查找已存在的索引。
    const idx = store.tasks.findIndex((t) => t.tid === task.tid)
    // 已存在则原地替换，保持表格行顺序稳定。
    if (idx >= 0) {
        // 替换该行数据。
        store.tasks[idx] = task
    } else {
        // 新任务追加到末尾。
        store.tasks.push(task)
    }
}

// 用整批任务覆盖本地列表（WebSocket 首帧 init 使用）。
export function replaceTasks(tasks, stats, running) {
    // 覆盖任务数组。
    if (Array.isArray(tasks)) store.tasks = tasks
    // 覆盖统计。
    if (stats) store.stats = stats
    // 覆盖运行状态。
    if (typeof running === 'boolean') store.queueRunning = running
}

// 移除单个任务。
export async function removeTask(tid) {
    try {
        // 调用后端删除接口。
        const res = await api.removeTask(tid)
        // 后端返回 ok=false 时说明该任务正在运行，不允许删除。
        if (res && res.ok === false) {
            // 给出提示。
            setToast('Running tasks cannot be removed. Pause or stop the queue first.')
        }
        // 刷新列表。
        await fetchTasks()
    } catch (err) {
        // 提示错误。
        setToast(`Failed to remove: ${err.message}`)
    }
}

// 移除所有已勾选的任务。
export async function removeSelected() {
    // 没有选中项时提示用户。
    if (!store.selected.length) {
        // 提示先勾选。
        setToast('Select the tasks to remove first.')
        // 提前返回。
        return
    }
    // 逐个删除（后端没有批量删除接口，这里串行处理以保证顺序）。
    for (const tid of store.selected.slice()) {
        // 捕获单个失败继续处理后续项。
        try {
            // 调用删除接口。
            await api.removeTask(tid)
        } catch (err) {
            // 忽略单个失败，最后统一刷新。
        }
    }
    // 清空选中集合。
    store.selected = []
    // 刷新列表。
    await fetchTasks()
    // 提示完成。
    setToast('Removed selected tasks.')
}

// 清空任务列表。
export async function clearTasks(includeRunning) {
    try {
        // 调用清空接口。
        const res = await api.clearTasks(!!includeRunning)
        // 清空本地选中集合。
        store.selected = []
        // 刷新列表。
        await fetchTasks()
        // 提示移除了多少条。
        setToast(`Cleared ${res.removed || 0} task(s).`)
    } catch (err) {
        // 提示错误。
        setToast(`Failed to clear: ${err.message}`)
    }
}

// 队列控制：action 取 start / pause / resume / stop。
export async function queueAction(action) {
    try {
        // 调用控制接口。
        const res = await api.queue(action)
        // 同步运行与暂停状态。
        store.queueRunning = !!(res && res.running)
        // 同步暂停状态。
        store.queuePaused = !!(res && res.paused)
        // 刷新一次列表以获得最新状态。
        await fetchTasks()
        // 依据动作给出对应中文提示。
        const label = {start: 'Started', pause: 'Paused', resume: 'Resumed', stop: 'Stopped'}
        // 显示提示。
        setToast(label[action] || 'Done.')
    } catch (err) {
        // 提示错误。
        setToast(`Queue operation failed: ${err.message}`)
    }
}

// 在服务器所在机器上打开目录。
export async function openPath(path) {
    try {
        // 调用打开接口。
        const res = await api.open(path)
        // 失败时给出后端返回的错误。
        if (res && res.ok === false) setToast(`Failed to open: ${res.error || 'path does not exist'}`)
    } catch (err) {
        // 提示错误。
        setToast(`Failed to open: ${err.message}`)
    }
}

// 打开任务输出所在目录（优先使用输出文件所在目录，其次使用输出目录设置）。
export function openOutputDir() {
    // 从已完成任务中找出第一个输出文件。
    const done = store.tasks.find((t) => t.out_path)
    // 有输出文件则打开其所在目录。
    if (done) {
        // 使用输出文件路径。
        openPath(done.out_path)
        // 结束。
        return
    }
    // 退化到全局输出目录设置。
    const dir = store.settings.output_dir || ''
    // 目录为空时提示用户。
    if (!dir) {
        // 提示需要先指定输出目录。
        setToast('No output directory set and no completed task output available.')
        // 结束。
        return
    }
    // 打开设置的输出目录。
    openPath(dir)
}

// 添加文件到待转列表（去重）。
export function addFiles(paths) {
    // 统计新加入数量。
    let added = 0
    // 逐条处理。
    for (const raw of paths || []) {
        // 规范化路径字符串。
        const path = String(raw || '').trim()
        // 空路径跳过。
        if (!path) continue
        // 已在列表中的路径跳过。
        if (store.files.some((f) => f.path === path)) continue
        // 推断扩展名。
        const ext = extOf(path)
        // 加入列表项。
        store.files.push({
            path,                    // 完整路径
            name: nameOf(path),      // 文件名
            ext,                     // 扩展名
            size: 0,                 // 体积（浏览接口已给出，此处保持 0 由探测再补）
            kind: kindOf(ext)        // 文件大类
        })
        // 计数加一。
        added += 1
    }
    // 提示加入结果。
    setToast(added ? `Added ${added} file(s).` : 'Selected files are already in the list.')
}

// 从待转列表移除一个文件。
export function removeFile(path) {
    // 过滤掉目标路径。
    store.files = store.files.filter((f) => f.path !== path)
    // 若移除的正是当前探测的文件，同时清空探测信息。
    if (store.activeFile === path) {
        // 清空选中标记。
        store.activeFile = ''
        // 清空探测结果。
        store.probeInfo = null
    }
}

// 清空待转列表。
export function clearFiles() {
    // 重置为空数组。
    store.files = []
    // 清空选中标记。
    store.activeFile = ''
    // 清空探测结果。
    store.probeInfo = null
    // 给出提示。
    setToast('File list cleared.')
}

// 探测某个文件并展示其媒体信息。
export async function probeFile(path) {
    // 记录当前选中路径。
    store.activeFile = path
    // 打开探测中的标志。
    store.probing = true
    // 先清空上一次结果，避免显示旧数据造成误读。
    store.probeInfo = null
    try {
        // 调用探测接口。
        store.probeInfo = await api.probe(path)
    } catch (err) {
        // 探测失败时构造一个最小的结果对象，界面无需额外分支。
        store.probeInfo = {exists: false, probe_ok: false, error: err.message, name: nameOf(path), path}
    } finally {
        // 关闭探测中标志。
        store.probing = false
    }
}

// 执行视频抽帧。
export async function runSnapshot(payload) {
    // 打开执行中标志。
    store.snapshotBusy = true
    // 清空上一次的错误信息。
    store.snapshotError = ''
    try {
        // 调用抽帧接口。
        const res = await api.snapshot(payload)
        // 写入结果文件列表。
        store.snapshotFiles = (res && res.files) || []
        // 写入错误信息。
        store.snapshotError = (res && res.error) || ''
        // 依据结果给出提示。
        if (res && res.ok) {
            // 成功提示。
            setToast(`Extracted ${store.snapshotFiles.length} snapshot(s).`)
        } else {
            // 失败提示。
            setToast(`Snapshot failed: ${store.snapshotError || 'unknown error'}`)
        }
    } catch (err) {
        // 记录异常信息。
        store.snapshotError = err.message
        // 提示错误。
        setToast(`Snapshot failed: ${err.message}`)
    } finally {
        // 关闭执行中标志。
        store.snapshotBusy = false
    }
}

// 执行文档类转换。
export async function runDoc(payload) {
    // 打开执行中标志。
    store.docBusy = true
    try {
        // 调用文档接口。
        const res = await api.doc(payload)
        // 写入结果对象。
        store.docResult = {
            ok: !!(res && res.ok),                       // 是否成功
            outputs: (res && res.outputs) || [],         // 产物路径列表
            error: (res && res.error) || '',             // 错误信息
            message: (res && res.message) || ''          // 附加说明
        }
        // 给出提示。
        if (store.docResult.ok) {
            // 成功提示。
            setToast(`Conversion complete, ${store.docResult.outputs.length} output(s).`)
        } else {
            // 失败提示。
            setToast(`Conversion failed: ${store.docResult.error || 'unknown error'}`)
        }
    } catch (err) {
        // 记录异常。
        store.docResult = {ok: false, outputs: [], error: err.message, message: ''}
        // 提示错误。
        setToast(`Conversion failed: ${err.message}`)
    } finally {
        // 关闭执行中标志。
        store.docBusy = false
    }
}

// 预设回填：把选中的预设字段复制进对应类别的参数对象。
export function applyPreset(key, presetKey) {
    // 取出参数对象。
    const p = store.params[key]
    // 参数对象不存在则忽略。
    if (!p) return
    // 记录选中的预设标识。
    p.preset_key = presetKey
    // 查找预设定义。
    const preset = store.presets.find((x) => x.key === presetKey)
    // 未找到（例如选择了“不使用预设”）则只记录标识并返回。
    if (!preset) return
    // 目标格式随预设回填。
    p.target_format = preset.container || p.target_format
    // 视频编码器回填。
    p.video_codec = preset.video_codec || ''
    // 音频编码器回填。
    p.audio_codec = preset.audio_codec || ''
    // 质量档位回填。
    p.quality = preset.quality || 'medium'
    // 恒定质量因子回填。
    p.crf = preset.crf || ''
    // 视频码率回填。
    p.v_bitrate = preset.v_bitrate || ''
    // 音频码率回填。
    p.a_bitrate = preset.a_bitrate || ''
    // 像素格式回填。
    p.pixel_format = preset.pixel_format || ''
    // 预设中的附加参数（图片预设常用 -q:v）追加进自定义参数。
    p.custom_args = Array.isArray(preset.extra) ? preset.extra.slice() : []
    // 图片页：把预设中的 -q:v 值同步到界面的质量输入框，便于用户查看。
    if (key === 'image' && Array.isArray(preset.extra)) {
        // 找到 -q:v 之后紧跟的数值。
        const i = preset.extra.indexOf('-q:v')
        // 记录数值（未找到则清空）。
        store.imageQv = i >= 0 && preset.extra[i + 1] !== undefined ? String(preset.extra[i + 1]) : ''
    }
}

// 计算整体进度百分比（仅统计未取消的任务，避免取消任务拉低进度造成误导）。
export function overallProgress() {
    // 取需要统计的任务集合。
    const list = store.tasks.filter((t) => t.status !== 'canceled')
    // 没有任务时进度为 0。
    if (!list.length) return 0
    // 累加每个任务的进度（已完成的按 100 计算）。
    const sum = list.reduce((acc, t) => acc + (t.status === 'done' ? 100 : Number(t.progress || 0)), 0)
    // 返回平均值。
    return sum / list.length
}

// 计算用于展示的统计数字（优先使用后端 stats，缺失时依据任务列表推算）。
export function localStats() {
    // 复制后端统计作为基底。
    const s = Object.assign({total: 0, ok: 0, fail: 0, skip: 0, canceled: 0, waiting: 0}, store.stats || {})
    // 若后端统计缺失 total，则用本地任务数量补齐。
    if (!s.total && store.tasks.length) s.total = store.tasks.length
    // 返回统计对象。
    return s
}

// 初始化 WebSocket 连接并绑定消息分发。
// 参数 onFinished：全部完成时的回调（用于自动打开输出目录等后续动作）。
export function initProgress(onFinished) {
    // 创建通道并注册回调。
    const sock = createProgressSocket({
        // 消息分发处理。
        onMessage: function (msg) {
            // 空消息忽略。
            if (!msg || !msg.type) return
            // 进度推送：更新对应任务行。
            if (msg.type === 'progress' || msg.type === 'state') {
                // 更新任务。
                upsertTask(msg.task)
            } else if (msg.type === 'init') {
                // 首帧全量数据：覆盖任务、统计与运行状态。
                replaceTasks(msg.tasks, msg.stats, msg.running)
            } else if (msg.type === 'finished') {
                // 全部结束：同步统计并交给上层做后续动作。
                if (msg.stats) store.stats = msg.stats
                // 刷新一次列表，拿到最终状态与输出体积。
                fetchTasks()
                // 调用上层回调。
                if (typeof onFinished === 'function') onFinished()
            } else if (msg.type === 'log') {
                // 日志类消息（可选）：保留最近 200 条，供队列页展示。
                store.logs.push(String(msg.text || ''))
                // 超出上限时裁掉最旧的部分。
                if (store.logs.length > 200) store.logs = store.logs.slice(-200)
            }
        },
        // 连接状态变化处理。
        onStatus: function (connected) {
            // 记录连接状态，供界面显示与轮询兜底判断使用。
            store.wsConnected = connected
        }
    })
    // 返回句柄以便页面卸载时关闭。
    return sock
}

// 启动任务列表轮询兜底：仅在 WebSocket 未连接时生效，避免推送失效导致界面停留在旧状态。
export function startTaskSync() {
    // 每 3 秒检查一次。
    const timer = setInterval(function () {
        // 连接正常时不需要轮询。
        if (store.wsConnected) return
        // 静默刷新，失败时忽略（避免网络抖动时刷屏提示）。
        fetchTasks().catch(function () {
        })
    }, 3000)
    // 返回句柄以便停止。
    return timer
}
