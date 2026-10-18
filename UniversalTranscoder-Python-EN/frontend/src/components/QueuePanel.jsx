// 任务队列页：统计概览、队列控制、失败任务详情与进度日志（任务明细表格位于页面底部常驻区域）。
import {store, queueAction, clearTasks, removeSelected, openOutputDir, localStats, overallProgress} from '../store.js'  // 引入状态与队列动作
import {fmtPercent, fmtDuration} from '../format.js'   // 引入百分比与时长格式化工具
import {Section, Btn} from './ui.jsx'                  // 引入通用控件

// 单个统计小方块。
function StatBox(props) {
    // 渲染统计方块。
    return (
        // 方块容器
        <div className="stat-box">
            {/* 统计项名称 */}
            <div className="k">{props.label}</div>
            {/* 统计项数值 */}
            <div className="v">{props.value}</div>
        </div>
    )
}

// 组件本体。
export function QueuePanel() {
    // 取统计数字。
    const stats = localStats()
    // 计算整体进度。
    const progress = overallProgress()
    // 过滤出失败任务，用于展示错误详情。
    const failed = store.tasks.filter((t) => t.status === 'failed')
    // 判断是否还存在未结束的任务。
    const pending = store.tasks.some((t) => t.status === 'waiting' || t.status === 'running')
    // 判断是否存在已完成且带产物的任务。
    const hasDone = store.tasks.some((t) => t.status === 'done' && t.out_path)
    // 计算已完成任务的累计耗时。
    const elapsedTotal = store.tasks
        .filter((t) => t.status === 'done')
        .reduce((acc, t) => acc + Number(t.elapsed || 0), 0)
    // 渲染页面。
    return (
        // 页面根容器
        <div>
            {/* 统计概览卡片 */}
            <Section title="Queue Overview">
                {/* 统计方块行 */}
                <div className="btn-row">
                    {/* 任务总数 */}
                    <StatBox label="Total Tasks" value={stats.total}/>
                    {/* 成功数量 */}
                    <StatBox label="Completed" value={stats.ok}/>
                    {/* 失败数量 */}
                    <StatBox label="Failed" value={stats.fail}/>
                    {/* 跳过数量 */}
                    <StatBox label="Skipped" value={stats.skip}/>
                    {/* 取消数量 */}
                    <StatBox label="Canceled" value={stats.canceled}/>
                </div>
                {/* 总进度条 */}
                <div className="progress mt-12">
                    {/* 进度填充 */}
                    <div className="progress-fill" style={{width: fmtPercent(progress)}}></div>
                </div>
                {/* 进度与状态文字 */}
                <div className="stats mt-8">
                    {/* 整体进度 */}
                    <span>Overall Progress <b>{fmtPercent(progress)}</b></span>
                    {/* 累计耗时 */}
                    <span>Total Elapsed <b>{fmtDuration(elapsedTotal)}</b></span>
                    {/* 队列运行状态 */}
                    <span>Queue Status <b>{store.queueRunning ? (store.queuePaused ? 'Paused' : 'Running') : 'Idle'}</b></span>
                    {/* 进度通道状态 */}
                    <span>Progress Channel <b>{store.wsConnected ? 'Connected' : 'Reconnecting'}</b></span>
                </div>
            </Section>

            {/* 队列控制卡片 */}
            <Section title="Queue Control">
                {/* 控制按钮行 */}
                <div className="btn-row">
                    {/* 开始执行 */}
                    <Btn kind="primary" onClick={() => queueAction('start')} disabled={store.queueRunning}>Start</Btn>
                    {/* 暂停 */}
                    <Btn onClick={() => queueAction('pause')} disabled={!store.queueRunning}>Pause</Btn>
                    {/* 继续 */}
                    <Btn onClick={() => queueAction('resume')} disabled={!store.queuePaused}>Resume</Btn>
                    {/* 停止 */}
                    <Btn kind="danger" onClick={() => queueAction('stop')}
                         disabled={!store.queueRunning && !store.queuePaused}>Stop</Btn>
                    {/* 移除勾选的任务 */}
                    <Btn onClick={() => removeSelected()} disabled={!store.selected.length}>Remove Selected</Btn>
                    {/* 清空（保留运行中） */}
                    <Btn onClick={() => clearTasks(false)} disabled={!store.tasks.length}>Clear Completed</Btn>
                    {/* 清空（含运行中） */}
                    <Btn kind="danger" onClick={() => clearTasks(true)} disabled={!store.tasks.length}>Clear All (incl.
                        running)</Btn>
                    {/* 打开输出目录 */}
                    <Btn kind="blue" onClick={() => openOutputDir()}>Open Output Folder</Btn>
                </div>
                {/* 使用说明 */}
                <div className="hint mt-8">
                    The task table is in the docked area at the bottom of the page. Check the "Sel" column on the left
                    to use "Remove Selected". Running tasks cannot be removed, so pause or stop the queue first.
                </div>
                {/* 全部结束提示 */}
                {!pending && hasDone ? (
                    // 完成提示文字
                    <div className="hint mt-8">All tasks have finished. Use "Open Output Folder" to view the
                        outputs.</div>
                ) : null}
            </Section>

            {/* 失败任务详情卡片 */}
            {failed.length ? (
                // 失败详情区块
                <Section title={`Failed Task Details (${failed.length})`}>
                    {/* 逐条展示失败原因 */}
                    {failed.map((t) => (
                        // 单个失败任务块
                        <div key={t.tid}>
                            {/* 任务文件名 */}
                            <div className="hint">{t.name || t.source}</div>
                            {/* 失败详情面板 */}
                            <div className="detail">
                                {/* 错误标题 */}
                                <div className="title">{t.err_title || 'Conversion failed'}</div>
                                {/* 错误详情 */}
                                <div
                                    className="line">{`Details: ${t.err_detail || 'kernel provided no further info'}`}</div>
                                {/* 处置建议 */}
                                <div
                                    className="line">{`Advice: ${t.err_advice || 'try another codec or container and retry'}`}</div>
                                {/* 源文件路径 */}
                                <div className="line">{`Source: ${t.source}`}</div>
                            </div>
                        </div>
                    ))}
                </Section>
            ) : null}

            {/* 进度日志卡片：有日志推送时显示 */}
            {store.logs.length ? (
                // 日志区块
                <Section title="Kernel Log">
                    {/* 日志内容（最近若干条） */}
                    <div className="preview">
                        {/* 逐行拼接日志文本 */}
                        {store.logs.slice(-40).join('\n')}
                    </div>
                </Section>
            ) : null}
        </div>
    )
}
