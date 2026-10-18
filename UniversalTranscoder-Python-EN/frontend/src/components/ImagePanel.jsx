// 图片转换参数面板：目标格式与预设、质量、尺寸与几何变换、水印与背景色，并内置“视频抽帧”入口。
import {reactive} from 'vue'                                        // 引入响应式工厂（用于抽帧参数）
import {store, runSnapshot, openPath} from '../store.js'            // 引入全局状态与抽帧动作
import {Select, Input, Check, Section, PathInput, Btn} from './ui.jsx'   // 引入通用控件
import {PresetBar, SubmitRow} from './PresetBar.jsx'                // 引入选择条与提交行
import {OutputSection} from './OutputSection.jsx'                   // 引入输出设置区块
import {toInt, toFloat, nameOf} from '../format.js'                 // 引入数值转换与文件名工具
import {
    QUALITY_OPTIONS, SCALE_MODES, ROTATIONS, FLIP_OPTIONS, WATERMARK_POSITIONS
} from '../codecs.js'                                                 // 引入枚举选项表

// 抽帧参数：放在模块级，切换到其它页面再回来时不会丢失已填内容。
const snap = reactive({
    source: '',        // 抽帧源视频路径
    count: 5,          // 抽取张数
    out_dir: '',       // 输出目录（留空由后端放在源文件旁的“快照”目录）
    ext: 'jpg',        // 图片格式
    width: 0           // 输出宽度（0 表示保持原始尺寸）
})

// 抽帧输出格式候选。
const SNAP_EXTS = [
    {value: 'jpg', label: 'JPG'},        // JPEG
    {value: 'png', label: 'PNG'},        // PNG
    {value: 'webp', label: 'WebP'}       // WebP
]

// 组件本体。
export function ImagePanel() {
    // 取出图片类参数对象。
    const p = store.params.image
    // 取出待转列表中的视频文件，供抽帧快速选择。
    const videoFiles = store.files.filter((f) => f.kind === 'video')
    // 渲染面板。
    return (
        // 面板根容器
        <div>
            {/* 基础设置卡片 */}
            <Section title="Basic: Target Format & Preset">
                {/* 目标格式与预设选择条 */}
                <PresetBar pk="image" cat="image" formats={store.formats.image}/>
                {/* 质量参数栅格 */}
                <div className="grid mt-8">
                    {/* 质量档位 */}
                    <Select
                        label="Quality"                                     /* 字段标签 */
                        value={p.quality}                                   /* 当前值 */
                        options={QUALITY_OPTIONS}                           /* 选项 */
                        onChange={(v) => {
                            p.quality = v
                        }}                 /* 变化回写 */
                    />
                    {/* JPEG/WebP 质量数值 */}
                    <Input
                        label="Quality value q:v"                           /* 字段标签 */
                        unit="JPG: 2–31 (lower is better), WebP: 1–100"      /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="1"                                             /* 最小值 */
                        max="100"                                           /* 最大值 */
                        value={store.imageQv}                               /* 当前值 */
                        onChange={(v) => {
                            store.imageQv = v
                        }}             /* 变化回写 */
                        placeholder="Empty = use the preset's own quality"   /* 提示文字 */
                    />
                </div>
            </Section>

            {/* 画面调整卡片 */}
            <Section title="Image: Size, Geometry & Background">
                {/* 画面参数栅格 */}
                <div className="grid">
                    {/* 目标宽度 */}
                    <Input
                        label="Width"                                       /* 字段标签 */
                        unit="px, 0 = keep"                                 /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="0"                                             /* 最小 0 */
                        value={p.width}                                     /* 当前值 */
                        onChange={(v) => {
                            p.width = toInt(v, 0)
                        }}         /* 变化回写 */
                    />
                    {/* 目标高度 */}
                    <Input
                        label="Height"                                      /* 字段标签 */
                        unit="px, 0 = keep"                                 /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="0"                                             /* 最小 0 */
                        value={p.height}                                    /* 当前值 */
                        onChange={(v) => {
                            p.height = toInt(v, 0)
                        }}        /* 变化回写 */
                    />
                    {/* 缩放模式 */}
                    <Select
                        label="Scale mode"                                  /* 字段标签 */
                        value={p.scale_mode}                                /* 当前值 */
                        options={SCALE_MODES}                               /* 选项 */
                        onChange={(v) => {
                            p.scale_mode = v
                        }}              /* 变化回写 */
                    />
                    {/* 旋转角度 */}
                    <Select
                        label="Rotate"                                      /* 字段标签 */
                        value={String(p.rotate)}                            /* 当前值 */
                        options={ROTATIONS}                                 /* 选项 */
                        onChange={(v) => {
                            p.rotate = toInt(v, 0)
                        }}        /* 变化回写 */
                    />
                    {/* 镜像方式 */}
                    <Select
                        label="Flip"                                        /* 字段标签 */
                        value={p.flip}                                      /* 当前值 */
                        options={FLIP_OPTIONS}                              /* 选项 */
                        onChange={(v) => {
                            p.flip = v
                        }}                    /* 变化回写 */
                    />
                    {/* 裁剪区域 */}
                    <Input
                        label="Crop area"                                   /* 字段标签 */
                        unit="w:h:x:y"                                      /* 单位提示 */
                        value={p.crop}                                      /* 当前值 */
                        onChange={(v) => {
                            p.crop = v
                        }}                    /* 变化回写 */
                        placeholder="e.g. 800:600:0:0"                       /* 提示文字 */
                        span                                               /* 占两列 */
                    />
                    {/* 背景色（需要时启用） */}
                    <div className="field">
                        {/* 字段标签 */}
                        <span className="label">Background fill</span>
                        {/* 启用开关与取色器 */}
                        <div className="input-row">
                            {/* 启用复选框 */}
                            <Check
                                label="Enable"                                   /* 复选框文字 */
                                checked={store.imageBgEnabled}                   /* 当前值 */
                                onChange={(v) => {
                                    store.imageBgEnabled = v
                                }}   /* 变化回写 */
                            />
                            {/* 颜色选择器 */}
                            <input
                                className="control"                              /* 统一控件样式 */
                                type="color"                                     /* 原生取色器 */
                                value={store.imageBgColor}                       /* 当前颜色 */
                                disabled={!store.imageBgEnabled}                 /* 未启用时禁用 */
                                onInput={(e) => {
                                    store.imageBgColor = e.target.value
                                }}  /* 变化回写 */
                                style="max-width:64px;padding:2px"                /* 压缩宽度，避免占用过多空间 */
                            />
                        </div>
                    </div>
                </div>
            </Section>

            {/* 水印卡片 */}
            <Section title="Watermark">
                {/* 水印参数栅格 */}
                <div className="grid">
                    {/* 文字水印 */}
                    <Input
                        label="Text watermark"                              /* 字段标签 */
                        value={p.watermark_text}                            /* 当前值 */
                        onChange={(v) => {
                            p.watermark_text = v
                        }}          /* 变化回写 */
                        placeholder="e.g. SAMPLE"                            /* 提示文字 */
                    />
                    {/* 图片水印路径 */}
                    <PathInput
                        label="Image watermark"                             /* 字段标签 */
                        mode="image"                                        /* 只允许选图片 */
                        title="Select Watermark Image"                      /* 弹窗标题 */
                        value={p.watermark_path}                            /* 当前值 */
                        onChange={(v) => {
                            p.watermark_path = v
                        }}          /* 变化回写 */
                        placeholder="Empty = no image watermark"             /* 提示文字 */
                    />
                    {/* 水印位置 */}
                    <Select
                        label="Watermark position"                          /* 字段标签 */
                        value={p.watermark_pos}                             /* 当前值 */
                        options={WATERMARK_POSITIONS}                       /* 选项 */
                        onChange={(v) => {
                            p.watermark_pos = v
                        }}           /* 变化回写 */
                    />
                    {/* 水印缩放比例 */}
                    <Input
                        label="Watermark scale"                             /* 字段标签 */
                        unit="Relative width, 0–1"                          /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        step="0.05"                                         /* 步长 */
                        min="0"                                             /* 最小 0 */
                        max="1"                                             /* 最大 1 */
                        value={p.watermark_scale}                           /* 当前值 */
                        onChange={(v) => {
                            p.watermark_scale = toFloat(v, 0.15)
                        }}  /* 变化回写 */
                    />
                    {/* 水印不透明度 */}
                    <Input
                        label="Watermark opacity"                           /* 字段标签 */
                        unit="0–1"                                          /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        step="0.05"                                         /* 步长 */
                        min="0"                                             /* 最小 0 */
                        max="1"                                             /* 最大 1 */
                        value={p.watermark_opacity}                         /* 当前值 */
                        onChange={(v) => {
                            p.watermark_opacity = toFloat(v, 0.75)
                        }}  /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 视频抽帧卡片 */}
            <Section title="Video Snapshots: Export Images from a Video">
                {/* 抽帧参数栅格 */}
                <div className="grid">
                    {/* 从待转列表快速选择视频 */}
                    <Select
                        label="Pick a video from the list"                   /* 字段标签 */
                        value=""                                            /* 该下拉仅用于快速填充，不保存状态 */
                        options={[{value: '', label: '(Optional) click to select'}].concat(
                            // 逐条列出已加入的视频文件
                            videoFiles.map((f) => ({value: f.path, label: f.name}))
                        )}
                        onChange={(v) => {
                            if (v) snap.source = v
                        }}         /* 选择后写入源路径 */
                    />
                    {/* 抽帧源视频路径 */}
                    <PathInput
                        label="Source video"                                /* 字段标签 */
                        mode="video"                                        /* 只允许选视频 */
                        title="Select Video to Extract Frames"              /* 弹窗标题 */
                        value={snap.source}                                 /* 当前值 */
                        onChange={(v) => {
                            snap.source = v
                        }}                /* 变化回写 */
                    />
                    {/* 抽取张数 */}
                    <Input
                        label="Frame count"                                 /* 字段标签 */
                        type="number"                                       /* 数字输入 */
                        min="1"                                             /* 最小 1 张 */
                        value={snap.count}                                  /* 当前值 */
                        onChange={(v) => {
                            snap.count = toInt(v, 5)
                        }}      /* 变化回写 */
                    />
                    {/* 输出图片格式 */}
                    <Select
                        label="Output format"                               /* 字段标签 */
                        value={snap.ext}                                    /* 当前值 */
                        options={SNAP_EXTS}                                 /* 选项 */
                        onChange={(v) => {
                            snap.ext = v
                        }}                  /* 变化回写 */
                    />
                    {/* 输出宽度 */}
                    <Input
                        label="Output width"                                /* 字段标签 */
                        unit="px, 0 = original size"                         /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="0"                                             /* 最小 0 */
                        value={snap.width}                                  /* 当前值 */
                        onChange={(v) => {
                            snap.width = toInt(v, 0)
                        }}      /* 变化回写 */
                    />
                    {/* 输出目录 */}
                    <PathInput
                        label="Output folder"                               /* 字段标签 */
                        mode="dir"                                          /* 浏览模式为目录 */
                        title="Select Snapshot Output Folder"               /* 弹窗标题 */
                        value={snap.out_dir}                                /* 当前值 */
                        onChange={(v) => {
                            snap.out_dir = v
                        }}              /* 变化回写 */
                        placeholder='Empty = a "snapshots" folder next to the source'  /* 提示文字 */
                    />
                </div>
                {/* 抽帧操作行 */}
                <div className="btn-row mt-8">
                    {/* 执行按钮 */}
                    <Btn
                        kind="primary"                                      /* 主按钮样式 */
                        disabled={store.snapshotBusy || !snap.source}        /* 执行中或未选源时禁用 */
                        onClick={() => runSnapshot({                          /* 点击执行抽帧 */
                            // 源视频路径
                            source: snap.source,
                            // 抽取张数
                            count: Number(snap.count) || 5,
                            // 输出目录
                            out_dir: snap.out_dir,
                            // 输出格式
                            ext: snap.ext,
                            // 输出宽度
                            width: Number(snap.width) || 0
                        })}
                    >
                        {store.snapshotBusy ? 'Extracting…' : 'Extract Frames'}
                    </Btn>
                    {/* 打开输出目录按钮 */}
                    <Btn size="sm" kind="blue" disabled={!store.snapshotFiles.length}
                         onClick={() => openPath(store.snapshotFiles[0])}>Open Output Folder</Btn>
                    {/* 结果数量提示 */}
                    <span
                        className="hint">{store.snapshotFiles.length ? `${store.snapshotFiles.length} snapshot(s) generated.` : 'No snapshots yet.'}</span>
                </div>
                {/* 抽帧错误提示 */}
                {store.snapshotError ? <div className="hint">Snapshot failed: {store.snapshotError}</div> : null}
                {/* 抽帧结果列表 */}
                {store.snapshotFiles.length ? (
                    // 结果容器
                    <div className="mt-8">
                        {/* 逐条列出生成的文件名 */}
                        {store.snapshotFiles.map((f, i) => (
                            // 单个产物行
                            <div className="kv" key={f}>
                                {/* 序号 */}
                                <span className="k">{`#${i + 1}`}</span>
                                {/* 文件名与完整路径 */}
                                <span className="v" title={f}>{nameOf(f)}</span>
                            </div>
                        ))}
                    </div>
                ) : null}
            </Section>

            {/* 输出设置卡片 */}
            <Section title="Output Settings">
                {/* 输出目录、文件名与覆盖策略 */}
                <OutputSection model={p}/>
                {/* 提交入口 */}
                <SubmitRow/>
            </Section>
        </div>
    )
}
