// 底部任务停靠区：总进度条 + 队列控制按钮 + 任务表格，始终可见，便于随时掌握转码进展。
import {store, queueAction, clearTasks, removeSelected, openOutputDir, overallProgress, localStats} from '../store.js'  // 引入状态与动作
import {fmtPercent} from '../format.js'      // 引入百分比格式化工具
import {Btn} from './ui.jsx'                 // 引入按钮控件
import {TaskTable} from './TaskTable.jsx'    // 引入任务表格

// 停靠区组件本体。
export function Dock() {
    // 计算整体进度百分比。
    const progress = overallProgress()
    // 计算统计数字。
    const stats = localStats()
    // 判断是否存在等待中的任务（用于“开始”按钮的可用性）。
    const hasWaiting = store.tasks.some((t) => t.status === 'waiting')
    // 渲染停靠区。
    return (
        // 停靠区容器
        <section className="dock">
            {/* 顶部操作行 */}
            <div className="dock-top">
                {/* 队列控制按钮组 */}
                <div className="btn-row">
                    {/* 开始执行 */}
                    <Btn kind="primary" size="sm" disabled={!hasWaiting || store.queueRunning}
                         onClick={() => queueAction('start')}>开始</Btn>
                    {/* 暂停（仅运行中可用） */}
                    <Btn size="sm" disabled={!store.queueRunning} onClick={() => queueAction('pause')}>暂停</Btn>
                    {/* 继续（仅暂停后可用；这里以“已暂停”标记判断） */}
                    <Btn size="sm" disabled={!store.queuePaused} onClick={() => queueAction('resume')}>继续</Btn>
                    {/* 停止（运行中或暂停中可用） */}
                    <Btn kind="danger" size="sm" disabled={!store.queueRunning && !store.queuePaused}
                         onClick={() => queueAction('stop')}>停止</Btn>
                    {/* 清空（不包含运行中的任务） */}
                    <Btn size="sm" onClick={() => clearTasks(false)} disabled={!store.tasks.length}>清空</Btn>
                    {/* 移除勾选 */}
                    <Btn size="sm" onClick={() => removeSelected()} disabled={!store.selected.length}>移除选中</Btn>
                    {/* 打开输出目录 */}
                    <Btn size="sm" kind="blue" onClick={() => openOutputDir()}>打开输出目录</Btn>
                </div>
                {/* 进度与统计区 */}
                <div className="dock-progress">
                    {/* 进度条 */}
                    <div className="progress">
                        {/* 进度填充 */}
                        <div className="progress-fill" style={{width: fmtPercent(progress)}}></div>
                    </div>
                    {/* 统计文字 */}
                    <div className="stats">
                        {/* 总进度 */}
                        <span>总进度 <b>{fmtPercent(progress)}</b></span>
                        {/* 任务总数 */}
                        <span>任务 <b>{stats.total}</b></span>
                        {/* 成功数 */}
                        <span>成功 <b>{stats.ok}</b></span>
                        {/* 失败数 */}
                        <span>失败 <b>{stats.fail}</b></span>
                        {/* 跳过数 */}
                        <span>跳过 <b>{stats.skip}</b></span>
                        {/* 等待数 */}
                        <span>等待 <b>{stats.waiting}</b></span>
                        {/* 连接状态 */}
                        <span>
              {/* 通道状态指示 */}
                            {store.wsConnected ? '进度通道：已连接' : '进度通道：重连中'}
            </span>
                    </div>
                </div>
            </div>
            {/* 任务表格区域 */}
            <div className="dock-table">
                {/* 任务表格 */}
                <TaskTable tasks={store.tasks}/>
            </div>
        </section>
    )
}
