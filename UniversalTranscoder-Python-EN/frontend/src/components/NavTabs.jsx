// 顶部导航标签页组件：负责在八个功能页之间切换。
import {store} from '../store.js'    // 引入全局状态（当前标签页与任务数量）

// 标签定义表：key 与 store.tab 对应，label 为界面显示名。
const TABS = [
    {key: 'video', label: 'Video'},        // 视频转码页
    {key: 'audio', label: 'Audio'},        // 音频转码页
    {key: 'image', label: 'Image'},        // 图片转换页
    {key: 'doc', label: 'Document'},       // 文档转换页
    {key: 'advanced', label: 'Advanced'},  // 自定义参数页
    {key: 'queue', label: 'Task Queue'},   // 队列管理页
    {key: 'settings', label: 'Settings'},  // 设置页
    {key: 'about', label: 'About'}         // 关于页
]

// 导航组件本体。
export function NavTabs() {
    // 渲染导航栏。
    return (
        // 导航容器
        <nav className="nav">
            {/* 逐项渲染标签按钮 */}
            {TABS.map((t) => (
                // 单个标签按钮
                <button
                    key={t.key}                                                    // 列表渲染需要稳定 key
                    className={'nav-tab' + (store.tab === t.key ? ' active' : '')}  // 选中态加 active
                    onClick={() => {
                        store.tab = t.key
                    }}                           // 点击切换当前页
                >
                    {/* 标签文字 */}
                    {t.label}
                    {/* 队列标签额外显示任务数量徽标 */}
                    {t.key === 'queue' && store.tasks.length ? (
                        // 数量徽标
                        <span className="nav-count">{store.tasks.length}</span>
                    ) : null}
                </button>
            ))}
        </nav>
    )
}
