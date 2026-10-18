// 左侧“待转文件列表”组件：支持添加文件、移除单个文件、清空列表，并展示选中文件的探测信息。
import {store, openBrowser, addFiles, removeFile, clearFiles, probeFile} from '../store.js'  // 引入状态与动作
import {fmtSize, fmtDuration} from '../format.js'    // 引入体积与时长格式化工具
import {Btn, Section} from './ui.jsx'                // 引入通用控件

// 依据当前标签页决定浏览弹窗的文件过滤模式。
function browseModeForTab(tab) {
    // 视频页只允许挑视频文件。
    if (tab === 'video') return 'video'
    // 音频页只允许挑音频文件。
    if (tab === 'audio') return 'audio'
    // 图片页只允许挑图片文件。
    if (tab === 'image') return 'image'
    // 其余页面允许挑全部常见媒体与文档文件。
    return 'media'
}

// 打开文件选择弹窗并支持多选。
function handleAdd() {
    // 打开弹窗。
    openBrowser({
        // 弹窗标题
        title: '添加待转文件（可多选）',
        // 过滤模式随当前页面变化
        mode: browseModeForTab(store.tab),
        // 允许一次选择多个文件
        multiple: true,
        // 选择完成后的回调
        callback: function (paths) {
            // 把选中的路径加入列表。
            addFiles(paths)
        }
    })
}

// 文件列表组件本体。
export function FilePanel() {
    // 渲染面板。
    return (
        // 面板卡片，标题为“待转文件列表”
        <Section
            title="待转文件列表"                                       /* 卡片标题 */
            toolbar={() => (                                          /* 标题右侧工具区 */
                // 工具按钮组
                <div className="btn-row">
                    {/* 添加文件按钮 */}
                    <Btn size="sm" kind="primary" onClick={handleAdd}>添加文件</Btn>
                    {/* 清空列表按钮 */}
                    <Btn size="sm" kind="danger" onClick={() => clearFiles()} disabled={!store.files.length}>清空</Btn>
                </div>
            )}
        >
            {/* 文件列表容器 */}
            <div className="filelist">
                {/* 可滚动的列表主体 */}
                <div className="filelist-body">
                    {/* 列表为空时显示提示 */}
                    {!store.files.length ? (
                        // 空状态提示
                        <div className="empty">列表中还没有文件，点击上方“添加文件”开始。</div>
                    ) : (
                        // 逐项渲染文件
                        store.files.map((f) => (
                            // 单个文件行
                            <div
                                key={f.path}                                    /* 以路径作为稳定 key */
                                className="file-item"                           /* 行样式 */
                                onClick={() => probeFile(f.path)}               /* 点击行触发探测 */
                                title={f.path}                                  /* 悬停显示完整路径 */
                            >
                                {/* 扩展名角标 */}
                                <span className="file-ext">{f.ext || '?'}</span>
                                {/* 文件信息区 */}
                                <div className="file-info">
                                    {/* 文件名 */}
                                    <div className="file-name">{f.name}</div>
                                    {/* 次要信息：大类与体积 */}
                                    <div className="file-meta">
                                        {/* 大类文字与体积 */}
                                        {`${{
                                            video: '视频',
                                            audio: '音频',
                                            image: '图片',
                                            doc: '文档',
                                            other: '其它'
                                        }[f.kind] || '其它'} · ${fmtSize(f.size)}`}
                                    </div>
                                </div>
                                {/* 移除按钮 */}
                                <Btn
                                    size="sm"                                      /* 小尺寸 */
                                    kind="ghost"                                   /* 幽灵样式 */
                                    onClick={() => removeFile(f.path)}             /* 移除该文件 */
                                    title="从列表中移除"                            /* 悬停提示 */
                                >
                                    移除
                                </Btn>
                            </div>
                        ))
                    )}
                </div>
                {/* 探测信息区：仅在探测中或有结果时渲染 */}
                {store.probing || store.probeInfo ? (
                    // 探测信息卡片
                    <div className="hint">
                        {/* 探测中提示 */}
                        {store.probing ? '正在读取媒体信息…' : null}
                        {/* 探测结果 */}
                        {!store.probing && store.probeInfo ? (
                            // 结果内容
                            <div>
                                {/* 第一行：容器格式与时长 */}
                                <div>{`容器：${store.probeInfo.format_name || '未知'} · 时长：${fmtDuration(store.probeInfo.duration)}`}</div>
                                {/* 第二行：视频流摘要（可能为多路） */}
                                {(store.probeInfo.video || []).map((v, i) => (
                                    // 单条视频流
                                    <div
                                        key={'v' + i}>{`视频流：${v.codec} · ${v.resolution} · ${v.fps || 0} fps · ${v.pix_fmt || ''}`}</div>
                                ))}
                                {/* 第三行：音频流摘要（可能为多路） */}
                                {(store.probeInfo.audio || []).map((a, i) => (
                                    // 单条音频流
                                    <div
                                        key={'a' + i}>{`音频流：${a.codec} · ${a.sample_rate || 0} Hz · ${a.channels || 0} 声道 · ${a.layout || ''}`}</div>
                                ))}
                                {/* 探测失败时展示原因 */}
                                {store.probeInfo.probe_ok === false ?
                                    <div>探测失败：{store.probeInfo.error || '未知原因'}</div> : null}
                            </div>
                        ) : null}
                    </div>
                ) : null}
            </div>
        </Section>
    )
}
