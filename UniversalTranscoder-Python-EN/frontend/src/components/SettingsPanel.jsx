// 设置页：完整呈现后端设置字典的每一项，并提供“检测内核”按钮查看 ffmpeg 路径、版本与能力统计。
import {defineComponent, reactive, onMounted, watch} from 'vue'      // 引入组件定义、响应式与生命周期 API
import {store, saveSettings, setToast} from '../store.js'             // 引入状态与设置保存动作
import {api} from '../api.js'                                         // 引入接口集合（用于检测内核）
import {Select, Input, Check, Section, PathInput, Btn} from './ui.jsx'  // 引入通用控件
import {hwaccelOptions} from './PresetBar.jsx'                        // 引入硬件加速选项
import {LOG_LEVELS, LANGUAGES, THEMES, OUTPUT_NAME_MODES, HWACCEL_VENDORS} from '../codecs.js'  // 引入枚举选项
import {toInt} from '../format.js'                                    // 引入数值转换工具

// 组件：使用 defineComponent 以便在 setup 中使用 onMounted 与 watch。
export const SettingsPanel = defineComponent({
    // 组件名，便于调试工具识别。
    name: 'SettingsPanel',
    // 组合式入口。
    setup() {
        // 本地表单：与后端设置字典字段一一对应。
        const form = reactive({
            language: 'zh-CN',            // 界面语言
            theme: 'dark',                // 界面主题
            output_dir: '',               // 默认输出目录
            output_name_mode: 'suffix',   // 输出命名策略
            output_name_template: '',     // 自定义命名模板
            overwrite: false,             // 是否覆盖已存在文件
            max_parallel: 1,              // 最大并发任务数
            auto_open_output: false,      // 全部完成后是否自动打开输出目录
            ffmpeg_custom_path: '',       // 自定义 ffmpeg 路径
            hwaccel: 'none',              // 硬件加速策略
            hwaccel_vendor: 'auto',       // 硬件厂商偏好
            log_level: 'INFO',            // 日志级别
            snapshot_count: 5,            // 默认抽帧张数
            threads: 0,                   // 默认编码线程数
            remember_last_dir: true,      // 是否记住上次目录
            last_input_dir: '',           // 上次输入目录
            last_output_dir: '',          // 上次输出目录
            web_port: 8765,               // Web 服务端口
            web_host: '127.0.0.1',        // Web 服务监听地址
            web_auto_open: true           // Web 服务启动后是否自动打开浏览器
        })

        // 把后端返回的设置同步进本地表单（只覆盖已有字段，避免写入 undefined）。
        function syncForm() {
            // 取后端设置字典。
            const s = store.settings || {}
            // 遍历表单键名。
            Object.keys(form).forEach((k) => {
                // 后端存在该键时同步取值。
                if (s[k] !== undefined && s[k] !== null) form[k] = s[k]
            })
        }

        // 组件挂载后先同步一次表单。
        onMounted(syncForm)
        // 监听设置加载完成事件，加载后再同步一次，保证拿到后端真实值。
        watch(() => store.settingsLoaded, (v) => {
            if (v) syncForm()
        })

        // 保存设置到后端。
        async function save() {
            // 组装提交补丁（数值字段做一次安全转换）。
            const patch = Object.assign({}, form, {
                // 并发数至少为 1。
                max_parallel: Math.max(1, toInt(form.max_parallel, 1)),
                // 抽帧张数至少为 1。
                snapshot_count: Math.max(1, toInt(form.snapshot_count, 5)),
                // 线程数允许为 0（表示自动）。
                threads: Math.max(0, toInt(form.threads, 0)),
                // 端口范围为 1~65535。
                web_port: Math.min(65535, Math.max(1, toInt(form.web_port, 8765)))
            })
            try {
                // 提交。
                await saveSettings(patch)
                // 成功提示。
                setToast('Settings saved.')
                // 再次同步，拿到后端归一化后的结果。
                syncForm()
            } catch (err) {
                // 失败提示。
                setToast(`Failed to save settings: ${err.message}`)
            }
        }

        // 检测内核：重新拉取系统信息并刷新界面。
        async function detect() {
            try {
                // 拉取系统信息。
                const res = await api.system()
                // 写入状态。
                store.system = res
                // 更新内核就绪标志。
                store.kernelReady = !!(res && res.ready)
                // 提示检测结果。
                setToast(store.kernelReady ? 'Kernel detected, ffmpeg is available.' : 'No usable ffmpeg found. Check the kernel path.')
            } catch (err) {
                // 失败提示。
                setToast(`Kernel detection failed: ${err.message}`)
            }
        }

        // 返回渲染函数。
        return () => {
            // 取系统信息（可能尚未加载）。
            const sys = store.system || {}
            // 取能力统计。
            const cap = sys.capabilities || {}
            // 取硬件编码器列表。
            const hw = sys.hw_accel || []
            // 渲染页面。
            return (
                // 页面根容器
                <div>
                    {/* 内核检测卡片 */}
                    <Section
                        title="Kernel Detection"
                        toolbar={() => (
                            // 标题右侧操作区
                            <div className="btn-row">
                                {/* 检测按钮 */}
                                <Btn size="sm" kind="primary" onClick={detect}>Detect Kernel</Btn>
                            </div>
                        )}
                    >
                        {/* 内核就绪状态 */}
                        <div className="hint">
                            <span className={'dot ' + (store.kernelReady ? 'ok' : 'bad')}></span>
                            {store.kernelReady ? ' Kernel is ready. You can start transcoding.' : ' Kernel is not ready. Check the ffmpeg path or reinstall.'}
                        </div>
                        {/* 基本信息键值表 */}
                        <div className="mt-8">
                            {/* 软件名称 */}
                            <div className="kv"><span className="k">Software Name</span><span
                                className="v">{sys.app || 'Universal Transcoder'}</span></div>
                            {/* 软件版本 */}
                            <div className="kv"><span className="k">Software Version</span><span
                                className="v">{sys.version || '1.0.0'}</span></div>
                            {/* 作者 */}
                            <div className="kv"><span className="k">Author</span><span
                                className="v">{sys.author || 'Unknown'}</span></div>
                            {/* ffmpeg 路径 */}
                            <div className="kv"><span className="k">ffmpeg</span><span
                                className="v">{sys.ffmpeg || 'Not detected'}</span></div>
                            {/* ffprobe 路径 */}
                            <div className="kv"><span className="k">ffprobe</span><span
                                className="v">{sys.ffprobe || 'Not detected'}</span></div>
                            {/* 内核版本文本 */}
                            <div className="kv"><span className="k">Kernel Version</span><span
                                className="v">{sys.ffmpeg_version || 'Not available'}</span></div>
                            {/* 视频编码器数量 */}
                            <div className="kv"><span className="k">Video Encoders</span><span
                                className="v">{`${cap.video_encoders || 0}`}</span></div>
                            {/* 音频编码器数量 */}
                            <div className="kv"><span className="k">Audio Encoders</span><span
                                className="v">{`${cap.audio_encoders || 0}`}</span></div>
                            {/* 支持格式总数 */}
                            <div className="kv"><span className="k">Supported Formats</span><span
                                className="v">{`${cap.format_total || 0}`}</span></div>
                            {/* 可用硬件加速方案 */}
                            <div className="kv"><span className="k">Hardware Acceleration</span><span
                                className="v">{(cap.hwaccels || []).join(', ') || 'Not detected'}</span></div>
                        </div>
                        {/* 硬件编码器列表 */}
                        {hw.length ? (
                            // 硬件编码器区块
                            <div className="mt-12">
                                {/* 小标题 */}
                                <div className="section-title">Available Hardware Encoders</div>
                                {/* 逐个列出 */}
                                {hw.map((h) => (
                                    // 单个硬件编码器行
                                    <div className="kv" key={h.name}>
                                        {/* 编码器名 */}
                                        <span className="k">{h.name}</span>
                                        {/* 厂商与中文说明 */}
                                        <span className="v">{`${h.vendor || ''} ${h.label || ''}`.trim()}</span>
                                    </div>
                                ))}
                            </div>
                        ) : null}
                    </Section>

                    {/* 常规设置卡片 */}
                    <Section title="General Settings">
                        {/* 设置项栅格 */}
                        <div className="grid">
                            {/* 界面语言 */}
                            <Select
                                label="UI language"                                  /* 字段标签 */
                                value={form.language}                               /* 当前值 */
                                options={LANGUAGES}                                 /* 选项 */
                                onChange={(v) => {
                                    form.language = v
                                }}             /* 变化回写 */
                            />
                            {/* 界面主题 */}
                            <Select
                                label="UI theme"                                     /* 字段标签 */
                                value={form.theme}                                  /* 当前值 */
                                options={THEMES}                                    /* 选项 */
                                onChange={(v) => {
                                    form.theme = v
                                }}                /* 变化回写 */
                            />
                            {/* 日志级别 */}
                            <Select
                                label="Log level"                                    /* 字段标签 */
                                value={form.log_level}                              /* 当前值 */
                                options={LOG_LEVELS}                                /* 选项 */
                                onChange={(v) => {
                                    form.log_level = v
                                }}            /* 变化回写 */
                            />
                            {/* 默认输出目录 */}
                            <PathInput
                                label="Default output folder"                        /* 字段标签 */
                                mode="dir"                                          /* 目录浏览 */
                                title="Select Default Output Folder"                /* 弹窗标题 */
                                value={form.output_dir}                             /* 当前值 */
                                onChange={(v) => {
                                    form.output_dir = v
                                }}           /* 变化回写 */
                                placeholder="Empty = same folder as source"         /* 提示文字 */
                            />
                        </div>
                    </Section>

                    {/* 输出命名与覆盖卡片 */}
                    <Section title="Output Naming & Overwrite">
                        {/* 命名设置栅格 */}
                        <div className="grid">
                            {/* 命名策略 */}
                            <Select
                                label="Naming policy"                                /* 字段标签 */
                                value={form.output_name_mode}                       /* 当前值 */
                                options={OUTPUT_NAME_MODES}                         /* 选项 */
                                onChange={(v) => {
                                    form.output_name_mode = v
                                }}     /* 变化回写 */
                            />
                            {/* 命名模板 */}
                            <Input
                                label="Naming template"                              /* 字段标签 */
                                unit="{name} original / {index} index / {ext} extension"  /* 单位提示 */
                                value={form.output_name_template}                   /* 当前值 */
                                onChange={(v) => {
                                    form.output_name_template = v
                                }} /* 变化回写 */
                                span                                               /* 占两列 */
                            />
                            {/* 覆盖策略 */}
                            <div className="field">
                                {/* 字段标签 */}
                                <span className="label">Overwrite policy</span>
                                {/* 覆盖复选框 */}
                                <Check
                                    label="Overwrite existing output files"             /* 复选框文字 */
                                    checked={form.overwrite}                          /* 当前值 */
                                    onChange={(v) => {
                                        form.overwrite = v
                                    }}          /* 变化回写 */
                                />
                            </div>
                            {/* 自动打开输出目录 */}
                            <div className="field">
                                {/* 字段标签 */}
                                <span className="label">On completion</span>
                                {/* 自动打开复选框 */}
                                <Check
                                    label="Open the output folder when all tasks finish"  /* 复选框文字 */
                                    checked={form.auto_open_output}                   /* 当前值 */
                                    onChange={(v) => {
                                        form.auto_open_output = v
                                    }}   /* 变化回写 */
                                />
                            </div>
                        </div>
                    </Section>

                    {/* 性能与内核卡片 */}
                    <Section title="Performance & Kernel">
                        {/* 性能设置栅格 */}
                        <div className="grid">
                            {/* 最大并发数 */}
                            <Input
                                label="Max parallel tasks"                           /* 字段标签 */
                                unit="1 is safest; keep 1 for HDDs"                  /* 单位提示 */
                                type="number"                                       /* 数字输入 */
                                min="1"                                             /* 最小 1 */
                                max="16"                                            /* 最大 16 */
                                value={form.max_parallel}                           /* 当前值 */
                                onChange={(v) => {
                                    form.max_parallel = toInt(v, 1)
                                }}  /* 变化回写 */
                            />
                            {/* 编码线程数 */}
                            <Input
                                label="Encoding threads"                             /* 字段标签 */
                                unit="0 = auto"                                      /* 单位提示 */
                                type="number"                                       /* 数字输入 */
                                min="0"                                             /* 最小 0 */
                                value={form.threads}                                /* 当前值 */
                                onChange={(v) => {
                                    form.threads = toInt(v, 0)
                                }}    /* 变化回写 */
                            />
                            {/* 默认抽帧张数 */}
                            <Input
                                label="Default snapshot count"                       /* 字段标签 */
                                type="number"                                       /* 数字输入 */
                                min="1"                                             /* 最小 1 */
                                max="100"                                           /* 最大 100 */
                                value={form.snapshot_count}                         /* 当前值 */
                                onChange={(v) => {
                                    form.snapshot_count = toInt(v, 5)
                                }}  /* 变化回写 */
                            />
                            {/* 硬件加速策略 */}
                            <Select
                                label="Default hardware acceleration"                /* 字段标签 */
                                value={form.hwaccel}                                /* 当前值 */
                                options={hwaccelOptions()}                          /* 选项 */
                                onChange={(v) => {
                                    form.hwaccel = v
                                }}              /* 变化回写 */
                            />
                            {/* 硬件厂商偏好 */}
                            <Select
                                label="Hardware vendor preference"                   /* 字段标签 */
                                value={form.hwaccel_vendor}                         /* 当前值 */
                                options={HWACCEL_VENDORS}                           /* 选项 */
                                onChange={(v) => {
                                    form.hwaccel_vendor = v
                                }}       /* 变化回写 */
                            />
                            {/* 自定义内核路径 */}
                            <PathInput
                                label="Custom ffmpeg path"                           /* 字段标签 */
                                mode="any"                                          /* 允许任意文件 */
                                title="Select ffmpeg Executable"                    /* 弹窗标题 */
                                value={form.ffmpeg_custom_path}                     /* 当前值 */
                                onChange={(v) => {
                                    form.ffmpeg_custom_path = v
                                }}   /* 变化回写 */
                                placeholder="Empty = use the bundled kernel"        /* 提示文字 */
                                span                                               /* 占两列 */
                            />
                        </div>
                    </Section>

                    {/* 目录记忆卡片 */}
                    <Section title="Folder Memory">
                        {/* 记忆设置栅格 */}
                        <div className="grid">
                            {/* 记住上次目录开关 */}
                            <div className="field">
                                {/* 字段标签 */}
                                <span className="label">Folder memory</span>
                                {/* 开关 */}
                                <Check
                                    label="Remember the last input/output folders"       /* 复选框文字 */
                                    checked={form.remember_last_dir}                   /* 当前值 */
                                    onChange={(v) => {
                                        form.remember_last_dir = v
                                    }}   /* 变化回写 */
                                />
                            </div>
                            {/* 上次输入目录 */}
                            <PathInput
                                label="Last input folder"                            /* 字段标签 */
                                mode="dir"                                          /* 目录浏览 */
                                title="Select Last Input Folder"                    /* 弹窗标题 */
                                value={form.last_input_dir}                         /* 当前值 */
                                onChange={(v) => {
                                    form.last_input_dir = v
                                }}       /* 变化回写 */
                            />
                            {/* 上次输出目录 */}
                            <PathInput
                                label="Last output folder"                           /* 字段标签 */
                                mode="dir"                                          /* 目录浏览 */
                                title="Select Last Output Folder"                   /* 弹窗标题 */
                                value={form.last_output_dir}                        /* 当前值 */
                                onChange={(v) => {
                                    form.last_output_dir = v
                                }}      /* 变化回写 */
                            />
                        </div>
                    </Section>

                    {/* Web 服务卡片 */}
                    <Section title="Web Service">
                        {/* Web 设置栅格 */}
                        <div className="grid">
                            {/* 监听地址 */}
                            <Input
                                label="Listen address"                               /* 字段标签 */
                                unit="127.0.0.1 = local access only"                 /* 单位提示 */
                                value={form.web_host}                               /* 当前值 */
                                onChange={(v) => {
                                    form.web_host = v
                                }}             /* 变化回写 */
                            />
                            {/* 监听端口 */}
                            <Input
                                label="Listen port"                                  /* 字段标签 */
                                unit="1–65535"                                       /* 单位提示 */
                                type="number"                                       /* 数字输入 */
                                min="1"                                             /* 最小 1 */
                                max="65535"                                         /* 最大 65535 */
                                value={form.web_port}                               /* 当前值 */
                                onChange={(v) => {
                                    form.web_port = toInt(v, 8765)
                                }}  /* 变化回写 */
                            />
                            {/* 启动时自动打开浏览器 */}
                            <div className="field">
                                {/* 字段标签 */}
                                <span className="label">Startup behavior</span>
                                {/* 复选框 */}
                                <Check
                                    label="Open the browser automatically when the service starts"  /* 复选框文字 */
                                    checked={form.web_auto_open}                        /* 当前值 */
                                    onChange={(v) => {
                                        form.web_auto_open = v
                                    }}        /* 变化回写 */
                                />
                            </div>
                        </div>
                        {/* 保存按钮行 */}
                        <div className="btn-row mt-12">
                            {/* 保存设置 */}
                            <Btn kind="primary" onClick={save}>Save Settings</Btn>
                            {/* 撤销本地修改（重新从后端同步） */}
                            <Btn onClick={syncForm}>Revert Changes</Btn>
                            {/* 说明文字 */}
                            <span className="hint">Saving writes to the backend config file immediately and takes effect right away (the parallel count is synced to the queue instantly).</span>
                        </div>
                    </Section>
                </div>
            )
        }
    }
})
