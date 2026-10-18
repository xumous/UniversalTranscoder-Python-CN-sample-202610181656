// 任务表格组件：展示序号、文件、目标格式、状态、进度、速度、剩余时间、体积变化与错误信息。
import {store, removeTask, openPath} from '../store.js'   // 引入状态与行级动作
import {fmtPercent, fmtSpeed, fmtEta, fmtVolume, statusText, statusClass} from '../format.js'  // 引入展示格式化工具
import {Btn} from './ui.jsx'                              // 引入按钮控件

// 切换某个任务的勾选状态。
function toggleSelect(tid) {
    // 找到当前在选中集合中的位置。
    const i = store.selected.indexOf(tid)
    // 已选中则取消。
    if (i >= 0) store.selected.splice(i, 1)
    // 未选中则加入。
    else store.selected.push(tid)
}

// 表格组件本体。
// 参数 tasks：要展示的任务数组（由调用方传入，便于复用在停靠区与队列页）。
export function TaskTable(props) {
    // 取任务数组，缺省为空数组。
    const tasks = props.tasks || []
    // 渲染表格。
    return (
        // 可滚动表格容器
        <div className="table-wrap">
            {/* 表格本体 */}
            <table className="table">
                {/* 表头 */}
                <thead>
                {/* 表头行 */}
                <tr>
                    {/* 勾选列 */}
                    <th style="width:34px">Sel</th>
                    {/* 序号列 */}
                    <th style="width:44px">#</th>
                    {/* 文件列 */}
                    <th>File</th>
                    {/* 目标格式列 */}
                    <th style="width:74px">Target</th>
                    {/* 状态列 */}
                    <th style="width:74px">Status</th>
                    {/* 进度列 */}
                    <th style="width:112px">Progress</th>
                    {/* 速度列 */}
                    <th style="width:74px">Speed</th>
                    {/* 剩余时间列 */}
                    <th style="width:78px">Left</th>
                    {/* 体积变化列 */}
                    <th style="width:132px">Size Change</th>
                    {/* 错误列 */}
                    <th style="width:150px">Error</th>
                    {/* 操作列 */}
                    <th style="width:110px">Actions</th>
                </tr>
                </thead>
                {/* 表体 */}
                <tbody>
                {/* 无任务时显示一行空态 */}
                {!tasks.length ? (
                    // 空态行
                    <tr>
                        {/* 跨全部列的空态单元格 */}
                        <td colSpan="11">
                            {/* 空态文字 */}
                            <div className="empty">No tasks yet. Add files, then click "Add to Queue" in the parameter
                                panel.
                            </div>
                        </td>
                    </tr>
                ) : (
                    // 逐行渲染任务
                    tasks.map((t, i) => (
                        // 单个任务行
                        <tr key={t.tid}>
                            {/* 勾选单元格 */}
                            <td>
                                {/* 勾选框 */}
                                <input
                                    type="checkbox"                              /* 复选框 */
                                    checked={store.selected.indexOf(t.tid) >= 0} /* 选中状态 */
                                    onChange={() => toggleSelect(t.tid)}          /* 切换勾选 */
                                />
                            </td>
                            {/* 序号单元格 */}
                            <td className="cell-num">{i + 1}</td>
                            {/* 文件名单元格 */}
                            <td className="cell-name" title={t.source}>{t.name || t.source}</td>
                            {/* 目标格式单元格 */}
                            <td>{t.target}</td>
                            {/* 状态单元格 */}
                            <td>
                                {/* 状态徽标 */}
                                <span className={'badge ' + statusClass(t.status)}>{statusText(t)}</span>
                            </td>
                            {/* 进度单元格 */}
                            <td>
                                {/* 迷你进度条 */}
                                <div className="mini-bar">
                                    {/* 进度填充 */}
                                    <div className="mini-bar-fill" style={{width: fmtPercent(t.progress)}}></div>
                                    {/* 进度百分比文字 */}
                                    <span className="mini-bar-text">{fmtPercent(t.progress)}</span>
                                </div>
                            </td>
                            {/* 速度单元格 */}
                            <td className="cell-num">{fmtSpeed(t.speed)}</td>
                            {/* 剩余时间单元格 */}
                            <td className="cell-num">{fmtEta(t.eta)}</td>
                            {/* 体积变化单元格 */}
                            <td className="cell-num">{fmtVolume(t.in_size, t.out_size)}</td>
                            {/* 错误单元格 */}
                            <td className="cell-err"
                                title={t.err_detail || t.err_title || ''}>{t.err_title || (t.status === 'failed' ? 'Conversion failed' : '—')}</td>
                            {/* 操作单元格 */}
                            <td>
                                {/* 操作按钮组 */}
                                <div className="btn-row">
                                    {/* 打开输出目录按钮（存在产物时可用） */}
                                    {t.out_path ? (
                                        // 打开按钮
                                        <Btn size="sm" kind="ghost" onClick={() => openPath(t.out_path)}>Open</Btn>
                                    ) : null}
                                    {/* 移除按钮 */}
                                    <Btn
                                        size="sm"                                   /* 小尺寸 */
                                        kind="ghost"                                /* 幽灵样式 */
                                        disabled={t.status === 'running'}           /* 运行中的任务不允许移除 */
                                        onClick={() => removeTask(t.tid)}           /* 移除该任务 */
                                    >
                                        Remove
                                    </Btn>
                                </div>
                            </td>
                        </tr>
                    ))
                )}
                </tbody>
            </table>
        </div>
    )
}
