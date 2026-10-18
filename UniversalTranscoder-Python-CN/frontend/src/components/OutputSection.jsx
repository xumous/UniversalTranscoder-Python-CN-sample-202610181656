// 输出设置区块：输出目录、输出文件名与覆盖开关，各功能页共用。
import {PathInput, Input, Check} from './ui.jsx'    // 引入通用控件

// 组件本体。
// 参数 model：对应类别的参数对象（响应式），组件直接修改其字段。
export function OutputSection(props) {
    // 取出参数对象。
    const m = props.model
    // 渲染输出设置。
    return (
        // 输出设置栅格
        <div className="grid">
            {/* 输出目录（可浏览选择） */}
            <PathInput
                label="输出目录"                                  /* 字段标签 */
                mode="dir"                                      /* 浏览模式为目录 */
                title="选择输出目录"                             /* 弹窗标题 */
                value={m.output_dir}                            /* 当前值 */
                onChange={(v) => {
                    m.output_dir = v
                }}          /* 变化回写 */
                placeholder="留空表示与源文件同目录"               /* 提示文字 */
            />
            {/* 输出文件名 */}
            <Input
                label="输出文件名"                               /* 字段标签 */
                value={m.output_name}                           /* 当前值 */
                onChange={(v) => {
                    m.output_name = v
                }}         /* 变化回写 */
                placeholder="留空表示自动命名"                    /* 提示文字 */
            />
            {/* 覆盖开关 */}
            <div className="field">
                {/* 字段标签 */}
                <span className="label">覆盖策略</span>
                {/* 覆盖复选框 */}
                <Check
                    label="允许覆盖已存在的输出文件"                  /* 复选框文字 */
                    checked={m.overwrite}                           /* 当前值 */
                    onChange={(v) => {
                        m.overwrite = v
                    }}           /* 变化回写 */
                />
            </div>
        </div>
    )
}
