// 目标格式 + 预设选择条，以及“添加到队列”提交行：所有转码类页面共用的顶部控件。
import {store, applyPreset, submitTasks, filesForTab} from '../store.js'   // 引入状态与动作
import {Select, Btn} from './ui.jsx'                                       // 引入通用控件
import {HWACCEL_BASE} from '../codecs.js'                                  // 引入硬件加速固定选项

// 组装硬件加速下拉选项：固定项 + 后端探测到的可用硬件编码器。
export function hwaccelOptions() {
    // 从系统信息中取出硬件编码器列表。
    const list = (store.system && store.system.hw_accel) || []
    // 把硬件编码器转换为选项项。
    const extra = list.map((h) => ({
        // 选项值使用编码器名
        value: h.name,
        // 显示名优先使用后端给出的中文标签
        label: h.label || h.name
    }))
    // 合并固定项与硬件项。
    return HWACCEL_BASE.concat(extra)
}

// 目标格式 + 预设选择条。
// 参数 pk：参数对象键名；参数 cat：预设类别；参数 formats：可用目标格式数组。
export function PresetBar(props) {
    // 取出该类别参数对象。
    const p = store.params[props.pk]
    // 过滤出该类别预设。
    const presets = store.presets.filter((x) => x.category === props.cat)
    // 构造预设下拉选项，首项为“不使用预设”。
    const presetOptions = [{value: '', label: '不使用预设（手动设置）'}].concat(
        // 逐条转换内置预设
        presets.map((x) => ({value: x.key, label: x.name}))
    )
    // 构造目标格式下拉选项。
    const formatOptions = (props.formats || []).map((f) => ({value: f, label: String(f).toUpperCase()}))
    // 找到当前选中的预设，用于显示说明文字。
    const active = presets.find((x) => x.key === p.preset_key)
    // 渲染选择条。
    return (
        // 选择条栅格
        <div className="grid">
            {/* 目标格式 */}
            <Select
                label="目标格式"                                        /* 字段标签 */
                value={p.target_format}                                /* 当前值 */
                options={formatOptions}                                /* 选项 */
                onChange={(v) => {
                    p.target_format = v
                }}              /* 变化回写 */
            />
            {/* 参数预设 */}
            <Select
                label="参数预设"                                        /* 字段标签 */
                value={p.preset_key}                                   /* 当前值 */
                options={presetOptions}                                /* 选项 */
                onChange={(v) => applyPreset(props.pk, v)}             /* 切换预设时自动回填参数 */
            />
            {/* 预设说明文字（仅在选中预设时显示） */}
            {active && active.note ? (
                // 说明文字占满整行
                <div className="hint span-2">{active.note}</div>
            ) : null}
        </div>
    )
}

// 提交行：按钮 + 待提交文件数量提示。
// 参数 pk：参数对象键名（仅用于提示，实际提交在 store.submitTasks 中完成）。
export function SubmitRow() {
    // 计算当前页面将要提交的文件数量。
    const count = filesForTab(store.tab).length
    // 渲染提交行。
    return (
        // 提交行容器
        <div className="btn-row mt-12">
            {/* 主提交按钮 */}
            <Btn kind="primary" onClick={() => submitTasks()}>添加到队列</Btn>
            {/* 数量提示 */}
            <span className="hint">
        {count ? `将提交待转列表中 ${count} 个同类型文件。` : '列表中暂无该类型文件，请先在左侧添加。'}
      </span>
        </div>
    )
}
