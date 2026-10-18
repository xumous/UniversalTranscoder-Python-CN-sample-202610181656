// 视频转码参数面板：目标格式与预设、画面调整、编码参数、水印字幕、剪辑变速、元数据、流与容器控制。
import {store} from '../store.js'                        // 引入全局状态
import {Select, Input, Check, Section, PathInput} from './ui.jsx'   // 引入通用控件
import {PresetBar, SubmitRow, hwaccelOptions} from './PresetBar.jsx'  // 引入选择条与提交行
import {OutputSection} from './OutputSection.jsx'         // 引入输出设置区块
import {toInt, toFloat} from '../format.js'               // 引入数值转换工具
import {
    VIDEO_ENCODERS, AUDIO_ENCODERS, PIXEL_FORMATS, QUALITY_OPTIONS, SCALE_MODES,
    ROTATIONS, FLIP_OPTIONS, WATERMARK_POSITIONS, STREAM_MODES, HWACCEL_VENDORS, codecOptions
} from '../codecs.js'                                       // 引入枚举选项表

// 组件本体。
export function VideoPanel() {
    // 取出视频类参数对象。
    const p = store.params.video
    // 组装视频编码器选项（含后端探测到的硬件编码器）。
    const vCodecOptions = codecOptions(VIDEO_ENCODERS, '自动（由预设或容器决定）')
        .concat(((store.system && store.system.hw_accel) || []).map((h) => ({value: h.name, label: h.label || h.name})))
    // 组装音频编码器选项。
    const aCodecOptions = codecOptions(AUDIO_ENCODERS, '自动（由预设或容器决定）')
    // 组装像素格式选项。
    const pixOptions = codecOptions(PIXEL_FORMATS, '不指定')
    // 渲染面板。
    return (
        // 面板根容器（纵向堆叠多个卡片）
        <div>
            {/* 基础设置卡片 */}
            <Section title="基础：目标格式与预设">
                {/* 目标格式与预设选择条 */}
                <PresetBar pk="video" cat="video" formats={store.formats.video}/>
                {/* 质量档位与线程数的栅格 */}
                <div className="grid mt-8">
                    {/* 质量档位 */}
                    <Select
                        label="质量档位"                                    /* 字段标签 */
                        value={p.quality}                                   /* 当前值 */
                        options={QUALITY_OPTIONS}                           /* 选项 */
                        onChange={(v) => {
                            p.quality = v
                        }}                 /* 变化回写 */
                    />
                    {/* 恒定质量因子 */}
                    <Input
                        label="恒定质量因子 CRF"                            /* 字段标签 */
                        unit="0-51，越小画质越好"                           /* 单位提示 */
                        value={p.crf}                                       /* 当前值 */
                        onChange={(v) => {
                            p.crf = v
                        }}                     /* 变化回写 */
                        placeholder="留空表示使用码率控制"                    /* 提示文字 */
                    />
                    {/* 编码线程数 */}
                    <Input
                        label="编码线程数"                                  /* 字段标签 */
                        type="number"                                       /* 数字输入 */
                        min="0"                                             /* 最小 0 */
                        value={p.threads}                                   /* 当前值 */
                        onChange={(v) => {
                            p.threads = toInt(v, 0)
                        }}       /* 变化回写为整数 */
                    />
                </div>
            </Section>

            {/* 画面调整卡片 */}
            <Section title="画面：分辨率、帧率与几何变换">
                {/* 画面参数栅格 */}
                <div className="grid">
                    {/* 目标宽度 */}
                    <Input
                        label="宽度"                                        /* 字段标签 */
                        unit="像素，0 表示保持"                              /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="0"                                             /* 最小 0 */
                        value={p.width}                                     /* 当前值 */
                        onChange={(v) => {
                            p.width = toInt(v, 0)
                        }}         /* 变化回写 */
                    />
                    {/* 目标高度 */}
                    <Input
                        label="高度"                                        /* 字段标签 */
                        unit="像素，0 表示保持"                              /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="0"                                             /* 最小 0 */
                        value={p.height}                                    /* 当前值 */
                        onChange={(v) => {
                            p.height = toInt(v, 0)
                        }}        /* 变化回写 */
                    />
                    {/* 缩放模式 */}
                    <Select
                        label="缩放模式"                                    /* 字段标签 */
                        value={p.scale_mode}                                /* 当前值 */
                        options={SCALE_MODES}                               /* 选项 */
                        onChange={(v) => {
                            p.scale_mode = v
                        }}              /* 变化回写 */
                    />
                    {/* 目标帧率 */}
                    <Input
                        label="帧率"                                        /* 字段标签 */
                        unit="例如 25、29.97"                                /* 单位提示 */
                        value={p.fps}                                       /* 当前值 */
                        onChange={(v) => {
                            p.fps = v
                        }}                     /* 变化回写 */
                        placeholder="留空表示保持原帧率"                      /* 提示文字 */
                    />
                    {/* 旋转角度 */}
                    <Select
                        label="旋转"                                        /* 字段标签 */
                        value={String(p.rotate)}                            /* 当前值 */
                        options={ROTATIONS}                                 /* 选项 */
                        onChange={(v) => {
                            p.rotate = toInt(v, 0)
                        }}        /* 变化回写为整数 */
                    />
                    {/* 镜像方式 */}
                    <Select
                        label="镜像"                                        /* 字段标签 */
                        value={p.flip}                                      /* 当前值 */
                        options={FLIP_OPTIONS}                              /* 选项 */
                        onChange={(v) => {
                            p.flip = v
                        }}                    /* 变化回写 */
                    />
                    {/* 裁剪区域 */}
                    <Input
                        label="裁剪区域"                                    /* 字段标签 */
                        unit="宽:高:横坐标:纵坐标"                           /* 单位提示 */
                        value={p.crop}                                      /* 当前值 */
                        onChange={(v) => {
                            p.crop = v
                        }}                    /* 变化回写 */
                        placeholder="例如 1280:720:0:0"                     /* 提示文字 */
                        span                                               /* 占两列，便于输入较长的表达式 */
                    />
                    {/* 像素格式 */}
                    <Select
                        label="像素格式"                                    /* 字段标签 */
                        value={p.pixel_format}                              /* 当前值 */
                        options={pixOptions}                                /* 选项 */
                        onChange={(v) => {
                            p.pixel_format = v
                        }}            /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 编码参数卡片 */}
            <Section title="编码：视频与音频编码器、码率、硬件加速">
                {/* 编码参数栅格 */}
                <div className="grid">
                    {/* 视频编码器 */}
                    <Select
                        label="视频编码器"                                  /* 字段标签 */
                        value={p.video_codec}                               /* 当前值 */
                        options={vCodecOptions}                             /* 选项 */
                        onChange={(v) => {
                            p.video_codec = v
                        }}             /* 变化回写 */
                    />
                    {/* 音频编码器 */}
                    <Select
                        label="音频编码器"                                  /* 字段标签 */
                        value={p.audio_codec}                               /* 当前值 */
                        options={aCodecOptions}                             /* 选项 */
                        onChange={(v) => {
                            p.audio_codec = v
                        }}             /* 变化回写 */
                    />
                    {/* 视频码率 */}
                    <Input
                        label="视频码率"                                    /* 字段标签 */
                        unit="例如 2500k 或 8M"                              /* 单位提示 */
                        value={p.v_bitrate}                                 /* 当前值 */
                        onChange={(v) => {
                            p.v_bitrate = v
                        }}               /* 变化回写 */
                        placeholder="留空表示使用 CRF 质量优先"               /* 提示文字 */
                    />
                    {/* 音频码率 */}
                    <Input
                        label="音频码率"                                    /* 字段标签 */
                        unit="例如 128k"                                    /* 单位提示 */
                        value={p.a_bitrate}                                 /* 当前值 */
                        onChange={(v) => {
                            p.a_bitrate = v
                        }}               /* 变化回写 */
                    />
                    {/* 硬件加速策略 */}
                    <Select
                        label="硬件加速"                                    /* 字段标签 */
                        value={p.hwaccel}                                   /* 当前值 */
                        options={hwaccelOptions()}                          /* 选项（含探测到的硬件编码器） */
                        onChange={(v) => {
                            p.hwaccel = v
                        }}                 /* 变化回写 */
                    />
                    {/* 硬件厂商偏好 */}
                    <Select
                        label="硬件厂商偏好"                                /* 字段标签 */
                        value={p.hwaccel_vendor}                            /* 当前值 */
                        options={HWACCEL_VENDORS}                           /* 选项 */
                        onChange={(v) => {
                            p.hwaccel_vendor = v
                        }}          /* 变化回写 */
                    />
                </div>
                {/* 仅换容器开关 */}
                <div className="mt-8">
                    {/* 直接封装复选框 */}
                    <Check
                        label="仅换容器、不重新编码（速度最快、无画质损失；要求源编码与目标容器兼容）"  /* 复选框文字 */
                        checked={p.copy_mode}                               /* 当前值 */
                        onChange={(v) => {
                            p.copy_mode = v
                        }}               /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 水印与字幕卡片 */}
            <Section title="水印与字幕">
                {/* 水印参数栅格 */}
                <div className="grid">
                    {/* 图片水印路径 */}
                    <PathInput
                        label="图片水印"                                    /* 字段标签 */
                        mode="image"                                        /* 只允许选图片 */
                        title="选择水印图片"                                 /* 弹窗标题 */
                        value={p.watermark_path}                            /* 当前值 */
                        onChange={(v) => {
                            p.watermark_path = v
                        }}          /* 变化回写 */
                        placeholder="留空表示不使用图片水印"                   /* 提示文字 */
                    />
                    {/* 文字水印内容 */}
                    <Input
                        label="文字水印"                                    /* 字段标签 */
                        value={p.watermark_text}                            /* 当前值 */
                        onChange={(v) => {
                            p.watermark_text = v
                        }}          /* 变化回写 */
                        placeholder="例如 仅供内部使用"                       /* 提示文字 */
                    />
                    {/* 水印位置 */}
                    <Select
                        label="水印位置"                                    /* 字段标签 */
                        value={p.watermark_pos}                             /* 当前值 */
                        options={WATERMARK_POSITIONS}                       /* 选项 */
                        onChange={(v) => {
                            p.watermark_pos = v
                        }}           /* 变化回写 */
                    />
                    {/* 图片水印缩放比例 */}
                    <Input
                        label="水印缩放"                                    /* 字段标签 */
                        unit="相对画面宽度，0~1"                             /* 单位提示 */
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
                        label="水印不透明度"                                /* 字段标签 */
                        unit="0~1"                                          /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        step="0.05"                                         /* 步长 */
                        min="0"                                             /* 最小 0 */
                        max="1"                                             /* 最大 1 */
                        value={p.watermark_opacity}                         /* 当前值 */
                        onChange={(v) => {
                            p.watermark_opacity = toFloat(v, 0.75)
                        }}  /* 变化回写 */
                    />
                    {/* 字幕文件路径 */}
                    <PathInput
                        label="字幕文件"                                    /* 字段标签 */
                        mode="subtitle"                                     /* 只允许选字幕 */
                        title="选择字幕文件"                                 /* 弹窗标题 */
                        value={p.subtitle_path}                             /* 当前值 */
                        onChange={(v) => {
                            p.subtitle_path = v
                        }}           /* 变化回写 */
                        placeholder="留空表示不烧录字幕"                       /* 提示文字 */
                    />
                </div>
            </Section>

            {/* 剪辑与变速卡片 */}
            <Section title="剪辑与变速">
                {/* 剪辑参数栅格 */}
                <div className="grid">
                    {/* 起始时间 */}
                    <Input
                        label="起始时间"                                    /* 字段标签 */
                        value={p.start}                                     /* 当前值 */
                        onChange={(v) => {
                            p.start = v
                        }}                   /* 变化回写 */
                        placeholder="例如 00:00:10 或 10.5"                  /* 提示文字 */
                    />
                    {/* 截取时长 */}
                    <Input
                        label="截取时长"                                    /* 字段标签 */
                        value={p.duration}                                  /* 当前值 */
                        onChange={(v) => {
                            p.duration = v
                        }}                /* 变化回写 */
                        placeholder="例如 30 或 00:00:30"                    /* 提示文字 */
                    />
                    {/* 结束时间 */}
                    <Input
                        label="结束时间"                                    /* 字段标签 */
                        value={p.end}                                       /* 当前值 */
                        onChange={(v) => {
                            p.end = v
                        }}                     /* 变化回写 */
                        placeholder="与时长二选一，二者都给以时长为准"          /* 提示文字 */
                    />
                    {/* 变速倍率 */}
                    <Input
                        label="播放速度"                                    /* 字段标签 */
                        unit="倍率，例如 0.5 / 1.0 / 2.0"                    /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        step="0.05"                                         /* 步长 */
                        min="0.1"                                           /* 最小 0.1 */
                        value={p.speed}                                     /* 当前值 */
                        onChange={(v) => {
                            p.speed = toFloat(v, 1)
                        }}       /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 元数据卡片 */}
            <Section title="元数据">
                {/* 元数据栅格 */}
                <div className="grid">
                    {/* 标题 */}
                    <Input
                        label="标题"                                        /* 字段标签 */
                        value={p.meta_title}                                /* 当前值 */
                        onChange={(v) => {
                            p.meta_title = v
                        }}              /* 变化回写 */
                    />
                    {/* 艺术家 */}
                    <Input
                        label="艺术家"                                      /* 字段标签 */
                        value={p.meta_artist}                               /* 当前值 */
                        onChange={(v) => {
                            p.meta_artist = v
                        }}             /* 变化回写 */
                    />
                    {/* 专辑 */}
                    <Input
                        label="专辑"                                        /* 字段标签 */
                        value={p.meta_album}                                /* 当前值 */
                        onChange={(v) => {
                            p.meta_album = v
                        }}              /* 变化回写 */
                    />
                    {/* 备注 */}
                    <Input
                        label="备注"                                        /* 字段标签 */
                        value={p.meta_comment}                              /* 当前值 */
                        onChange={(v) => {
                            p.meta_comment = v
                        }}            /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 流与容器卡片 */}
            <Section title="流选择与容器">
                {/* 流选择栅格 */}
                <div className="grid">
                    {/* 流选择模式 */}
                    <Select
                        label="流选择模式"                                  /* 字段标签 */
                        value={p.stream_mode}                               /* 当前值 */
                        options={STREAM_MODES}                              /* 选项 */
                        onChange={(v) => {
                            p.stream_mode = v
                        }}              /* 变化回写 */
                    />
                    {/* 指定视频流序号 */}
                    <Input
                        label="视频流序号"                                  /* 字段标签 */
                        unit="-1 表示自动"                                   /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="-1"                                            /* 最小 -1 */
                        value={p.video_index}                               /* 当前值 */
                        onChange={(v) => {
                            p.video_index = toInt(v, -1)
                        }}  /* 变化回写 */
                        disabled={p.stream_mode !== 'custom'}                /* 仅自定义模式下可编辑 */
                    />
                    {/* 指定音频流序号 */}
                    <Input
                        label="音频流序号"                                  /* 字段标签 */
                        unit="-1 表示自动"                                   /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="-1"                                            /* 最小 -1 */
                        value={p.audio_index}                               /* 当前值 */
                        onChange={(v) => {
                            p.audio_index = toInt(v, -1)
                        }}  /* 变化回写 */
                        disabled={p.stream_mode !== 'custom'}                /* 仅自定义模式下可编辑 */
                    />
                    {/* 指定字幕流序号 */}
                    <Input
                        label="字幕流序号"                                  /* 字段标签 */
                        unit="-1 表示自动"                                   /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        min="-1"                                            /* 最小 -1 */
                        value={p.subtitle_index}                            /* 当前值 */
                        onChange={(v) => {
                            p.subtitle_index = toInt(v, -1)
                        }}  /* 变化回写 */
                        disabled={p.stream_mode !== 'custom'}                /* 仅自定义模式下可编辑 */
                    />
                </div>
                {/* 轨道丢弃开关 */}
                <div className="btn-row mt-8">
                    {/* 丢弃音频 */}
                    <Check
                        label="丢弃音频轨道"                                /* 复选框文字 */
                        checked={p.drop_audio}                              /* 当前值 */
                        onChange={(v) => {
                            p.drop_audio = v
                        }}              /* 变化回写 */
                    />
                    {/* 丢弃视频 */}
                    <Check
                        label="丢弃视频轨道（仅保留音频）"                     /* 复选框文字 */
                        checked={p.drop_video}                              /* 当前值 */
                        onChange={(v) => {
                            p.drop_video = v
                        }}              /* 变化回写 */
                    />
                </div>
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
