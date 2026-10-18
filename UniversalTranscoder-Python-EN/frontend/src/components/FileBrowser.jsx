// 目录浏览弹窗：通过后端 /api/browse 在“服务器所在机器”上选择文件或目录（Web 端无法直接取得本地路径）。
import {defineComponent, reactive, onMounted} from 'vue'   // 引入组件定义、响应式与生命周期 API
import {api} from '../api.js'                              // 引入浏览接口
import {store, closeBrowser} from '../store.js'            // 引入弹窗状态与关闭动作
import {fmtSize, extOf, VIDEO_EXT, AUDIO_EXT, IMAGE_EXT, DOC_EXT, SUB_EXT} from '../format.js'  // 引入扩展名集合与格式化工具
import {Btn} from './ui.jsx'                               // 引入按钮控件

// 构造“允许显示的扩展名集合”；null 表示不做限制。
function allowedSet(mode) {
    // 目录模式不显示任何文件（只用于选择文件夹）。
    if (mode === 'dir') return new Set()
    // 任意文件模式不限制扩展名。
    if (mode === 'any') return null
    // 视频模式。
    if (mode === 'video') return new Set(VIDEO_EXT)
    // 音频模式。
    if (mode === 'audio') return new Set(AUDIO_EXT)
    // 图片模式。
    if (mode === 'image') return new Set(IMAGE_EXT)
    // PDF 模式（PDF 转图片场景使用）。
    if (mode === 'pdf') return new Set(['pdf'])
    // 文本模式（文本转 PDF 场景使用）。
    if (mode === 'text') return new Set(['txt', 'md', 'log', 'csv'])
    // 字幕模式。
    if (mode === 'subtitle') return new Set(SUB_EXT)
    // 默认（媒体模式）合并视频、音频、图片、文档与字幕。
    const merged = new Set()
    // 合并视频扩展名。
    VIDEO_EXT.forEach((e) => merged.add(e))
    // 合并音频扩展名。
    AUDIO_EXT.forEach((e) => merged.add(e))
    // 合并图片扩展名。
    IMAGE_EXT.forEach((e) => merged.add(e))
    // 合并文档扩展名。
    DOC_EXT.forEach((e) => merged.add(e))
    // 合并字幕扩展名。
    SUB_EXT.forEach((e) => merged.add(e))
    // 返回合并结果。
    return merged
}

// 依据模式过滤文件列表。
function filterFiles(files, mode) {
    // 取得允许集合。
    const allow = allowedSet(mode)
    // 目录模式下一律不显示文件。
    if (allow && allow.size === 0) return []
    // 不限制时原样返回。
    if (!allow) return files
    // 按扩展名过滤。
    return files.filter((f) => allow.has(String(f.ext || extOf(f.name)).toLowerCase()))
}

// 弹窗组件：使用 defineComponent 以便在 setup 中使用生命周期钩子。
export const FileBrowser = defineComponent({
    // 组件名，便于调试工具识别。
    name: 'FileBrowser',
    // 组合式入口。
    setup() {
        // 从全局状态中取出弹窗配置（响应式代理，读取其字段可触发重渲染）。
        const cfg = store.browser
        // 本地视图状态。
        const state = reactive({
            dir: '',                                      // 当前目录
            parent: '',                                   // 上级目录
            dirs: [],                                     // 子目录列表
            files: [],                                    // 文件列表
            loading: false,                               // 是否正在加载
            error: '',                                    // 错误信息
            selected: []                                  // 已选路径数组
        })

        // 加载指定目录内容。
        async function load(path) {
            // 打开加载标志。
            state.loading = true
            // 清空上一次的错误。
            state.error = ''
            try {
                // 调用后端浏览接口（空字符串表示列出盘符/主目录）。
                const res = await api.browse(path)
                // 写入当前目录。
                state.dir = res.dir || ''
                // 写入上级目录。
                state.parent = res.parent || ''
                // 写入子目录列表。
                state.dirs = res.dirs || []
                // 过滤出允许显示的文件。
                state.files = filterFiles(res.files || [], cfg.mode)
                // 后端可能返回目录读取错误（例如无权限）。
                if (res.error) state.error = res.error
                // 切换目录时清空已选，避免跨目录误选。
                state.selected = []
            } catch (err) {
                // 记录错误信息。
                state.error = err.message
            } finally {
                // 关闭加载标志。
                state.loading = false
            }
        }

        // 切换某个文件的选中状态。
        function toggle(path) {
            // 多选模式：在数组中增删。
            if (cfg.multiple) {
                // 找到已有位置。
                const i = state.selected.indexOf(path)
                // 已选中则移除。
                if (i >= 0) state.selected.splice(i, 1)
                // 未选中则加入。
                else state.selected.push(path)
            } else {
                // 单选模式：直接覆盖。
                state.selected = [path]
            }
        }

        // 确认选择并回调。
        function confirm() {
            // 目录模式：把当前目录作为结果。
            const result = cfg.mode === 'dir' ? [state.dir] : state.selected.slice()
            // 未选择任何内容时不做任何事。
            if (!result.length || !result[0]) return
            // 有回调则交给调用方处理。
            if (typeof cfg.callback === 'function') cfg.callback(result)
            // 关闭弹窗。
            closeBrowser()
        }

        // 组件挂载后立即列出盘符/主目录。
        onMounted(() => {
            load('')
        })

        // 返回渲染函数。
        return () => (
            // 遮罩层（点击遮罩不关闭，避免误操作丢失选择）
            <div className="mask">
                {/* 弹窗主体 */}
                <div className="modal">
                    {/* 弹窗头部 */}
                    <div className="modal-head">
                        {/* 弹窗标题 */}
                        <div className="modal-title">{cfg.title}</div>
                        {/* 关闭按钮 */}
                        <Btn size="sm" onClick={() => closeBrowser()}>Close</Btn>
                    </div>
                    {/* 弹窗正文 */}
                    <div className="modal-body">
                        {/* 路径条 */}
                        <div className="pathbar">
                            {/* 返回上级按钮（没有上级时禁用） */}
                            <Btn size="sm" onClick={() => load(state.parent)} disabled={!state.parent}>Up</Btn>
                            {/* 当前路径文字 */}
                            <div className="path"
                                 title={state.dir || '(root)'}>{state.dir || 'Select a drive or folder'}</div>
                            {/* 刷新按钮 */}
                            <Btn size="sm" onClick={() => load(state.dir)}>Refresh</Btn>
                        </div>
                        {/* 加载中提示 */}
                        {state.loading ? <div className="hint">Reading folder…</div> : null}
                        {/* 错误提示 */}
                        {state.error ? <div className="hint">Failed to read: {state.error}</div> : null}
                        {/* 子目录列表 */}
                        {state.dirs.map((d) => (
                            // 单行目录
                            <div
                                key={d.path}                                  /* 以路径作为 key */
                                className="browse-row"                         /* 行样式 */
                                onClick={() => load(d.path)}                   /* 点击进入该目录 */
                                title={d.path}                                 /* 悬停显示完整路径 */
                            >
                                {/* 目录标记文字 */}
                                <span className="browse-icon">DIR</span>
                                {/* 目录名 */}
                                <span className="browse-name">{d.name}</span>
                            </div>
                        ))}
                        {/* 文件列表 */}
                        {state.files.map((f) => (
                            // 单行文件
                            <div
                                key={f.path}                                                   /* 以路径作为 key */
                                className="browse-row"                                          /* 行样式 */
                                onClick={() => toggle(f.path)}                                  /* 点击切换选中 */
                                title={f.path}                                                  /* 悬停显示完整路径 */
                            >
                                {/* 勾选标记：用文字表示是否已选 */}
                                <span className="browse-icon">{state.selected.indexOf(f.path) >= 0 ? '✓' : '○'}</span>
                                {/* 文件名 */}
                                <span className="browse-name">{f.name}</span>
                                {/* 文件体积 */}
                                <span className="browse-size">{fmtSize(f.size)}</span>
                            </div>
                        ))}
                        {/* 空目录提示 */}
                        {!state.loading && !state.dirs.length && !state.files.length ? (
                            // 空状态文字
                            <div className="empty">No selectable items in this folder.</div>
                        ) : null}
                    </div>
                    {/* 弹窗底部操作区 */}
                    <div className="modal-foot">
                        {/* 已选数量提示 */}
                        <span className="hint">
              {cfg.mode === 'dir' ? `Current folder: ${state.dir || '(none selected)'}` : `${state.selected.length} selected`}
            </span>
                        {/* 取消按钮 */}
                        <Btn onClick={() => closeBrowser()}>Cancel</Btn>
                        {/* 确认按钮 */}
                        <Btn kind="primary" onClick={confirm}>
                            {cfg.mode === 'dir' ? 'Select This Folder' : 'Add Selected'}
                        </Btn>
                    </div>
                </div>
            </div>
        )
    }
})
