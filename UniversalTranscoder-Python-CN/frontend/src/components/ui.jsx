// 通用界面控件集合：把表单、按钮、卡片等重复结构抽成小组件，保证各功能页风格与行为一致。
// 说明：全部为函数式组件，子内容通过默认插槽传入（JSX 子元素即默认插槽）。
import {openBrowser} from '../store.js'   // 引入目录浏览弹窗的打开动作（供路径输入框使用）

// 字段容器：负责“标签 + 控件”的竖向排布。
export function Field(props, ctx) {
    // 计算是否需要跨列（用于较宽的输入框）。
    const cls = 'field' + (props.span ? ' span-2' : '')
    // 返回字段结构。
    return (
        // 字段外层容器
        <div className={cls}>
            {/* 标签行：仅在提供 label 时渲染 */}
            {props.label ? (
                // 标签元素
                <label className="label">
                    {/* 标签文字 */}
                    {props.label}
                    {/* 单位提示（可选） */}
                    {props.unit ? <span className="unit">{props.unit}</span> : null}
                </label>
            ) : null}
            {/* 控件区域：由调用方通过子元素提供 */}
            {ctx.slots.default ? ctx.slots.default() : null}
        </div>
    )
}

// 下拉选择框。
// props: label / unit / value / options([{value,label}]) / onChange / disabled / span
export function Select(props) {
    // 统一处理值变化。
    function handle(e) {
        // 把选中值回传调用方。
        if (props.onChange) props.onChange(e.target.value)
    }

    // 渲染字段与下拉框。
    return (
        // 复用字段容器
        <Field label={props.label} unit={props.unit} span={props.span}>
            {/* 下拉框本体 */}
            <select
                className="control"                        /* 统一控件样式 */
                value={props.value === undefined || props.value === null ? '' : String(props.value)}  /* 绑定当前值 */
                onChange={handle}                          /* 变化回调 */
                disabled={props.disabled}                  /* 是否禁用 */
            >
                {/* 逐项渲染选项 */}
                {props.options.map((o) => (
                    // 单个选项
                    <option key={String(o.value)} value={String(o.value)}>
                        {/* 选项显示文本 */}
                        {o.label}
                    </option>
                ))}
            </select>
        </Field>
    )
}

// 单行输入框。
// props: label / unit / value / onChange / type / placeholder / disabled / span / step / min / max
export function Input(props) {
    // 统一处理输入变化。
    function handle(e) {
        // 把输入值回传调用方。
        if (props.onChange) props.onChange(e.target.value)
    }

    // 渲染字段与输入框。
    return (
        // 复用字段容器
        <Field label={props.label} unit={props.unit} span={props.span}>
            {/* 输入框本体 */}
            <input
                className="control"                        /* 统一控件样式 */
                type={props.type || 'text'}                /* 输入类型 */
                value={props.value === undefined || props.value === null ? '' : String(props.value)}  /* 绑定当前值 */
                placeholder={props.placeholder || ''}      /* 提示文字 */
                step={props.step}                          /* 数字步长（可选） */
                min={props.min}                            /* 最小值（可选） */
                max={props.max}                            /* 最大值（可选） */
                disabled={props.disabled}                  /* 是否禁用 */
                onInput={handle}                           /* 输入回调 */
            />
        </Field>
    )
}

// 多行文本域（用于自定义参数与长说明）。
// props: label / unit / value / onChange / rows / placeholder / span / mono
export function Textarea(props) {
    // 统一处理输入变化。
    function handle(e) {
        // 把输入值回传调用方。
        if (props.onChange) props.onChange(e.target.value)
    }

    // 渲染字段与文本域。
    return (
        // 复用字段容器
        <Field label={props.label} unit={props.unit} span={props.span}>
            {/* 文本域本体 */}
            <textarea
                className={'control' + (props.mono ? ' mono' : '')}  /* 可选等宽字体 */
                rows={props.rows || 4}                     /* 行数 */
                value={props.value === undefined || props.value === null ? '' : String(props.value)}  /* 绑定当前值 */
                placeholder={props.placeholder || ''}      /* 提示文字 */
                onInput={handle}                           /* 输入回调 */
            />
        </Field>
    )
}

// 复选框。
// props: label / checked / onChange / title
export function Check(props) {
    // 统一处理勾选变化。
    function handle(e) {
        // 把布尔值回传调用方。
        if (props.onChange) props.onChange(e.target.checked)
    }

    // 渲染复选框行。
    return (
        // 可点击的标签行
        <label className="check" title={props.title || ''}>
            {/* 复选框本体 */}
            <input type="checkbox" checked={!!props.checked} onChange={handle}/>
            {/* 复选框文字 */}
            <span>{props.label}</span>
        </label>
    )
}

// 按钮。
// props: kind(''|'primary'|'blue'|'danger'|'ghost') / size(''|'sm') / disabled / onClick / label / title
export function Btn(props, ctx) {
    // 组合类名。
    const cls = 'btn' + (props.kind ? ' ' + props.kind : '') + (props.size === 'sm' ? ' sm' : '')

    // 统一处理点击。
    function handle() {
        // 禁用状态下不触发。
        if (props.disabled) return
        // 调用调用方的点击回调。
        if (props.onClick) props.onClick()
    }

    // 渲染按钮。
    return (
        // 按钮本体
        <button className={cls} disabled={props.disabled} onClick={handle} title={props.title || ''}>
            {/* 按钮内容：优先使用子元素，其次使用 label 属性 */}
            {ctx.slots.default ? ctx.slots.default() : props.label}
        </button>
    )
}

// 卡片区块：统一的标题样式与内容容器。
// props: title / span / right(自定义右侧节点通过插槽传递时可用工具栏) / toolbar(渲染函数)
export function Section(props, ctx) {
    // 渲染卡片。
    return (
        // 卡片容器
        <section className="card">
            {/* 卡片标题行：仅在提供 title 时渲染 */}
            {props.title ? (
                // 标题行容器
                <div className="card-head">
                    {/* 标题文字（带左侧色条） */}
                    <div className="card-title">{props.title}</div>
                    {/* 标题右侧操作区：由 toolbar 或右侧插槽提供 */}
                    {props.toolbar ? props.toolbar() : (ctx.slots.right ? ctx.slots.right() : null)}
                </div>
            ) : null}
            {/* 卡片正文 */}
            {ctx.slots.default ? ctx.slots.default() : null}
        </section>
    )
}

// 路径输入框：输入框 + 浏览按钮的组合控件。
// props: label / value / onChange / mode(浏览过滤模式) / title(弹窗标题) / multiple / placeholder / span
export function PathInput(props) {
    // 打开浏览器弹窗。
    function pick() {
        // 打开弹窗并注册回调。
        openBrowser({
            // 弹窗标题
            title: props.title || '选择文件',
            // 过滤模式
            mode: props.mode || 'media',
            // 是否多选
            multiple: !!props.multiple,
            // 选择回调：单选取第一项，多选时交给调用方另行处理
            callback: function (paths) {
                // 有回调且拿到路径时回传。
                if (props.onChange && paths && paths.length) props.onChange(paths[0])
            }
        })
    }

    // 渲染字段与组合控件。
    return (
        // 复用字段容器
        <Field label={props.label} span={props.span}>
            {/* 输入框与按钮的横向组合 */}
            <div className="input-row">
                {/* 路径输入框 */}
                <input
                    className="control"                      /* 统一控件样式 */
                    type="text"                              /* 文本类型 */
                    value={props.value === undefined || props.value === null ? '' : String(props.value)}  /* 绑定当前值 */
                    placeholder={props.placeholder || ''}    /* 提示文字 */
                    onInput={(e) => props.onChange && props.onChange(e.target.value)}  /* 输入回调 */
                />
                {/* 浏览按钮 */}
                <Btn size="sm" kind="blue" onClick={pick}>浏览</Btn>
            </div>
        </Field>
    )
}
