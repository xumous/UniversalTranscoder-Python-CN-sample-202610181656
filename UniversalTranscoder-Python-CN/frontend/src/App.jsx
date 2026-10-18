// 应用外壳：装配顶部品牌栏、导航标签页、左右两栏工作区、底部任务停靠区与全局弹窗。
import {defineComponent, onMounted, onBeforeUnmount} from 'vue'   // 引入组件定义与生命周期钩子
import {
    store, loadBase, loadSettings, fetchTasks, initProgress, startTaskSync, openOutputDir, setToast
} from './store.js'                                               // 引入全局状态与动作
import {NavTabs} from './components/NavTabs.jsx'               // 顶部导航标签页
import {FilePanel} from './components/FilePanel.jsx'           // 左侧待转文件列表
import {VideoPanel} from './components/VideoPanel.jsx'         // 视频参数面板
import {AudioPanel} from './components/AudioPanel.jsx'         // 音频参数面板
import {ImagePanel} from './components/ImagePanel.jsx'         // 图片参数面板
import {DocPanel} from './components/DocPanel.jsx'             // 文档转换页
import {AdvancedPanel} from './components/AdvancedPanel.jsx'   // 高级参数页
import {QueuePanel} from './components/QueuePanel.jsx'         // 任务队列页
import {SettingsPanel} from './components/SettingsPanel.jsx'   // 设置页
import {AboutPanel} from './components/AboutPanel.jsx'         // 关于页
import {Dock} from './components/Dock.jsx'                     // 底部任务停靠区
import {FileBrowser} from './components/FileBrowser.jsx'       // 目录浏览弹窗

// 需要“左列表 + 右参数”两栏布局的标签页。
const WORKSPACE_TABS = ['video', 'audio', 'image', 'advanced']

// 依据当前标签页返回右侧/主区内容。
function renderPanel() {
    // 视频页。
    if (store.tab === 'video') return <VideoPanel/>
    // 音频页。
    if (store.tab === 'audio') return <AudioPanel/>
    // 图片页。
    if (store.tab === 'image') return <ImagePanel/>
    // 文档页。
    if (store.tab === 'doc') return <DocPanel/>
    // 高级页。
    if (store.tab === 'advanced') return <AdvancedPanel/>
    // 任务队列页。
    if (store.tab === 'queue') return <QueuePanel/>
    // 设置页。
    if (store.tab === 'settings') return <SettingsPanel/>
    // 关于页（也是其它未知标签的兜底页）。
    return <AboutPanel/>
}

// 根组件：使用 defineComponent 以便在 setup 中注册生命周期钩子（函数式组件无法使用生命周期）。
export const App = defineComponent({
    // 组件名，便于调试工具识别。
    name: 'App',
    // 组合式入口。
    setup() {
        // 进度通道句柄（组件卸载时关闭）。
        let sock = null
        // 轮询兜底定时器句柄。
        let syncTimer = null

        // 挂载后初始化数据与进度通道。
        onMounted(async () => {
            // 加载系统信息、预设与格式表。
            try {
                await loadBase()
            } catch (err) {
                // 加载失败时提示（界面其余部分仍可使用）。
                setToast(`基础数据加载失败：${err.message}`)
            }
            // 加载设置字典。
            try {
                await loadSettings()
            } catch (err) {
                // 设置加载失败只提示，不影响转码表单使用默认值。
                setToast(`设置加载失败：${err.message}`)
            }
            // 拉取一次任务列表，保证首屏非空。
            try {
                await fetchTasks()
            } catch (err) {
                // 首次拉取失败可忽略，进度通道连接后会推送全量数据。
            }
            // 建立进度通道；全部完成时按设置决定是否自动打开输出目录。
            sock = initProgress(function () {
                // 读取设置项。
                if (store.settings && store.settings.auto_open_output) openOutputDir()
            })
            // 启动轮询兜底（仅在通道断开时生效）。
            syncTimer = startTaskSync()
        })

        // 卸载前清理资源，避免定时器与连接泄漏。
        onBeforeUnmount(() => {
            // 关闭进度通道。
            if (sock) sock.close()
            // 清理轮询定时器。
            if (syncTimer) clearInterval(syncTimer)
        })

        // 返回渲染函数。
        return () => {
            // 判断当前是否使用两栏工作区布局。
            const isWorkspace = WORKSPACE_TABS.indexOf(store.tab) >= 0
            // 生成主区内容节点。
            const panel = renderPanel()
            // 取系统信息用于顶部展示。
            const sys = store.system || {}
            // 渲染外壳。
            return (
                // 应用外壳容器
                <div className="shell">
                    {/* 顶部品牌栏 */}
                    <header className="topbar">
                        {/* 品牌区 */}
                        <div className="brand">
                            {/* 品牌图标 */}
                            <div className="brand-mark">转</div>
                            {/* 文字区 */}
                            <div>
                                {/* 主标题 */}
                                <div className="brand-title">{sys.app || '万能转码器'}</div>
                                {/* 副标题：版本与作者 */}
                                <div
                                    className="brand-sub">{`v${sys.version || '1.0.0'} · ${sys.author || '徐某'} · ffmpeg 内核`}</div>
                            </div>
                        </div>
                        {/* 右侧状态区 */}
                        <div className="topbar-right">
                            {/* 内核状态胶囊 */}
                            <div className="kernel-pill" title={sys.ffmpeg || ''}>
                                {/* 状态圆点 */}
                                <span className={'dot ' + (store.kernelReady ? 'ok' : 'bad')}></span>
                                {/* 状态文字 */}
                                {store.kernelReady ? '内核就绪' : '内核未就绪'}
                            </div>
                            {/* 队列运行状态胶囊 */}
                            <div className="kernel-pill">
                                {/* 状态圆点 */}
                                <span className={'dot ' + (store.queueRunning ? 'ok' : 'bad')}></span>
                                {/* 状态文字 */}
                                {store.queueRunning ? (store.queuePaused ? '队列已暂停' : '队列运行中') : '队列空闲'}
                            </div>
                        </div>
                    </header>
                    {/* 顶部导航标签页 */}
                    <NavTabs/>
                    {/* 轻提示条（有内容时显示） */}
                    {store.toast ? (
                        // 提示条本体
                        <div className="toast">{store.toast}</div>
                    ) : null}
                    {/* 主体区域 */}
                    <div className="body">
                        {/* 页内容滚动区 */}
                        <div className="content">
                            {/* 两栏布局页 */}
                            {isWorkspace ? (
                                // 工作区：左侧文件列表 + 右侧参数面板
                                <div className="workspace">
                                    {/* 左侧待转文件列表 */}
                                    <FilePanel/>
                                    {/* 右侧参数面板 */}
                                    <div>{panel}</div>
                                </div>
                            ) : (
                                // 单栏页：文档 / 队列 / 设置 / 关于
                                <div>{panel}</div>
                            )}
                        </div>
                        {/* 底部任务停靠区 */}
                        <Dock/>
                    </div>
                    {/* 目录浏览弹窗（按需渲染） */}
                    {store.browser.open ? <FileBrowser/> : null}
                </div>
            )
        }
    }
})
