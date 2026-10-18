// 高级页：直接编辑自定义 ffmpeg 参数数组（每行一个参数），并预览将执行的命令文本。
import {store} from '../store.js'                          // 引入全局状态
import {previewCommand} from '../preview.js'               // 引入命令预览推导
import {Select, Textarea, Check, Section} from './ui.jsx'  // 引入通用控件
import {OutputSection} from './OutputSection.jsx'          // 引入输出设置区块
import {hwaccelOptions, SubmitRow} from './PresetBar.jsx'  // 引入硬件加速选项与提交行
import {HWACCEL_VENDORS} from '../codecs.js'               // 引入硬件厂商选项
import {nameOf, extOf} from '../format.js'                 // 引入文件名与扩展名工具

// 连接目录与文件名（自动匹配 Windows 反斜杠或类 Unix斜杠）。
function joinPath(dir, name) {
    // 目录为空时直接返回文件名。
    if (!dir) return name
    // 依据目录风格选择分隔符。
    const sep = dir.indexOf('\\') >= 0 || /^[A-Za-z]:/.test(dir) ? '\\' : '/'
    // 去掉目录末尾分隔符后拼接。
    return dir.replace(/[\\/]+$/, '') + sep + name
}

// 去掉文件名末尾的扩展名（用于推导默认输出名）。
function stripExt(name) {
    // 找到最后一个点。
    const i = String(name).lastIndexOf('.')
    // 没有点则原样返回。
    if (i <= 0) return String(name)
    // 返回点之前的部分。
    return String(name).slice(0, i)
}

// 组件本体。
export function AdvancedPanel() {
    // 取出高级页参数对象。
    const p = store.params.advanced
    // 汇总所有类型的目标格式，供高级页自由选择。
    const allFormats = (store.formats.video || [])
        .concat(store.formats.audio || [])
        .concat(store.formats.image || [])
        .concat(store.formats.document || [])
    // 去重后构造下拉选项。
    const formatOptions = Array.from(new Set(allFormats)).map((f) => ({value: f, label: String(f).toUpperCase()}))
    // 取列表中第一个文件作为预览用输入。
    const first = store.files[0]
    // 计算预览用输入路径（没有文件时留空）。
    const src = first ? first.path : ''
    // 推导默认输出文件名：源文件名主体 + _out + 目标扩展名。
    const outName = p.output_name || `${first ? stripExt(nameOf(first.path)) : '输入文件'}_out.${p.target_format}`
    // 计算预览用输出完整路径。
    const out = joinPath(p.output_dir, outName)
    // 生成命令预览文本。
    const preview = previewCommand('advanced', src, out)
    // 参数编辑区的示例内容（真实换行）。
    const sample = '-hide_banner\n-map 0:v:0\n-metadata:s:v:0 rotate=0'
    // 渲染面板。
    return (
        // 面板根容器
        <div>
            {/* 自定义参数卡片 */}
            <Section title="自定义 ffmpeg 参数">
                {/* 使用说明 */}
                <div className="hint">
                    每行填写一个参数（例如 <code>-b:v</code> 与 <code>4000k</code> 各占一行）；以 # 开头的行会被忽略。
                    这里的参数会追加到内核自动生成的命令之后，可用于覆盖默认行为或补充内核未提供的开关。
                </div>
                {/* 参数编辑区 */}
                <div className="mt-8">
                    {/* 多行参数编辑器 */}
                    <Textarea
                        label="参数数组（每行一个参数）"                       /* 字段标签 */
                        rows={9}                                             /* 行数 */
                        mono                                                 /* 使用等宽字体 */
                        value={store.advancedText}                           /* 当前值 */
                        onChange={(v) => {
                            store.advancedText = v
                        }}         /* 变化回写 */
                        placeholder={sample}                                 /* 示例内容 */
                        span                                                 /* 占满整行 */
                    />
                </div>
            </Section>

            {/* 基础参数卡片 */}
            <Section title="基础参数">
                {/* 参数栅格 */}
                <div className="grid">
                    {/* 目标格式 */}
                    <Select
                        label="目标格式"                                      /* 字段标签 */
                        value={p.target_format}                              /* 当前值 */
                        options={formatOptions}                              /* 选项 */
                        onChange={(v) => {
                            p.target_format = v
                        }}            /* 变化回写 */
                    />
                    {/* 硬件加速策略 */}
                    <Select
                        label="硬件加速"                                      /* 字段标签 */
                        value={p.hwaccel}                                    /* 当前值 */
                        options={hwaccelOptions()}                           /* 选项 */
                        onChange={(v) => {
                            p.hwaccel = v
                        }}                  /* 变化回写 */
                    />
                    {/* 硬件厂商偏好 */}
                    <Select
                        label="硬件厂商偏好"                                  /* 字段标签 */
                        value={p.hwaccel_vendor}                             /* 当前值 */
                        options={HWACCEL_VENDORS}                            /* 选项 */
                        onChange={(v) => {
                            p.hwaccel_vendor = v
                        }}           /* 变化回写 */
                    />
                </div>
                {/* 直接封装开关 */}
                <div className="mt-8">
                    {/* 直接封装复选框 */}
                    <Check
                        label="仅换容器、不重新编码（等价于所有流使用 copy）"      /* 复选框文字 */
                        checked={p.copy_mode}                                /* 当前值 */
                        onChange={(v) => {
                            p.copy_mode = v
                        }}                /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 命令预览卡片 */}
            <Section title="命令预览">
                {/* 预览说明 */}
                <div className="hint">以下命令由前端依据当前参数推导，用于提交前复核；最终执行命令以后端内核为准。</div>
                {/* 预览内容 */}
                <div className="preview mt-8">{preview}</div>
                {/* 预览用输入输出提示 */}
                <div className="hint mt-8">{`预览输入：${src || '（待转列表为空）'}`}</div>
                {/* 预览用输出提示 */}
                <div className="hint">{`预览输出：${out}`}</div>
            </Section>

            {/* 输出设置卡片 */}
            <Section title="输出设置">
                {/* 输出目录、文件名与覆盖策略 */}
                <OutputSection model={p}/>
                {/* 提交入口 */}
                <SubmitRow/>
            </Section>
        </div>
    )
}
