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
    const vCodecOptions = codecOptions(VIDEO_ENCODERS, 'Auto (decided by preset or container)')
        .concat(((store.system && store.system.hw_accel) || []).map((h) => ({value: h.name, label: h.label || h.name})))
    // 组装音频编码器选项。
    const aCodecOptions = codecOptions(AUDIO_ENCODERS, 'Auto (decided by preset or container)')
    // 组装像素格式选项。
    const pixOptions = codecOptions(PIXEL_FORMATS, 'Not specified')
    // 渲染面板。
    return (
        // 面板根容器（纵向堆叠多个卡片）
        <div>
            {/* 基础设置卡片 */}
            <Section title="Basic: Target Format & Preset">
                {/* 目标格式与预设选择条 */}
                <PresetBar pk="video" cat="video" formats={store.formats.video}/>
                {/* 质量档位与线程数的栅格 */}
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
                    {/* 恒定质量因子 */}
                    <Input
                        label="Constant quality factor CRF"                 /* 字段标签 */
                        unit="0–51, lower is better quality"                /* 单位提示 */
                        value={p.crf}                                       /* 当前值 */
                        onChange={(v) => {
                            p.crf = v
                        }}                     /* 变化回写 */
                        placeholder="Empty = use bitrate control"           /* 提示文字 */
                    />
                    {/* 编码线程数 */}
                    <Input
                        label="Encoding threads"                            /* 字段标签 */
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
            <Section title="Picture: Resolution, Frame Rate & Geometry">
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
                    {/* 目标帧率 */}
                    <Input
                        label="Frame rate"                                  /* 字段标签 */
                        unit="e.g. 25, 29.97"                                /* 单位提示 */
                        value={p.fps}                                       /* 当前值 */
                        onChange={(v) => {
                            p.fps = v
                        }}                     /* 变化回写 */
                        placeholder="Empty = keep original frame rate"       /* 提示文字 */
                    />
                    {/* 旋转角度 */}
                    <Select
                        label="Rotate"                                      /* 字段标签 */
                        value={String(p.rotate)}                            /* 当前值 */
                        options={ROTATIONS}                                 /* 选项 */
                        onChange={(v) => {
                            p.rotate = toInt(v, 0)
                        }}        /* 变化回写为整数 */
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
                        placeholder="e.g. 1280:720:0:0"                     /* 提示文字 */
                        span                                               /* 占两列，便于输入较长的表达式 */
                    />
                    {/* 像素格式 */}
                    <Select
                        label="Pixel format"                                /* 字段标签 */
                        value={p.pixel_format}                              /* 当前值 */
                        options={pixOptions}                                /* 选项 */
                        onChange={(v) => {
                            p.pixel_format = v
                        }}            /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 编码参数卡片 */}
            <Section title="Encoding: Video & Audio Codecs, Bitrate, Hardware Acceleration">
                {/* 编码参数栅格 */}
                <div className="grid">
                    {/* 视频编码器 */}
                    <Select
                        label="Video codec"                                 /* 字段标签 */
                        value={p.video_codec}                               /* 当前值 */
                        options={vCodecOptions}                             /* 选项 */
                        onChange={(v) => {
                            p.video_codec = v
                        }}             /* 变化回写 */
                    />
                    {/* 音频编码器 */}
                    <Select
                        label="Audio codec"                                 /* 字段标签 */
                        value={p.audio_codec}                               /* 当前值 */
                        options={aCodecOptions}                             /* 选项 */
                        onChange={(v) => {
                            p.audio_codec = v
                        }}             /* 变化回写 */
                    />
                    {/* 视频码率 */}
                    <Input
                        label="Video bitrate"                               /* 字段标签 */
                        unit="e.g. 2500k or 8M"                              /* 单位提示 */
                        value={p.v_bitrate}                                 /* 当前值 */
                        onChange={(v) => {
                            p.v_bitrate = v
                        }}               /* 变化回写 */
                        placeholder="Empty = prefer CRF quality"            /* 提示文字 */
                    />
                    {/* 音频码率 */}
                    <Input
                        label="Audio bitrate"                               /* 字段标签 */
                        unit="e.g. 128k"                                    /* 单位提示 */
                        value={p.a_bitrate}                                 /* 当前值 */
                        onChange={(v) => {
                            p.a_bitrate = v
                        }}               /* 变化回写 */
                    />
                    {/* 硬件加速策略 */}
                    <Select
                        label="Hardware acceleration"                       /* 字段标签 */
                        value={p.hwaccel}                                   /* 当前值 */
                        options={hwaccelOptions()}                          /* 选项（含探测到的硬件编码器） */
                        onChange={(v) => {
                            p.hwaccel = v
                        }}                 /* 变化回写 */
                    />
                    {/* 硬件厂商偏好 */}
                    <Select
                        label="Hardware vendor preference"                  /* 字段标签 */
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
                        label="Remux only, no re-encode (fastest, no quality loss; source codec must be compatible with the target container)"  /* 复选框文字 */
                        checked={p.copy_mode}                               /* 当前值 */
                        onChange={(v) => {
                            p.copy_mode = v
                        }}               /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 水印与字幕卡片 */}
            <Section title="Watermark & Subtitles">
                {/* 水印参数栅格 */}
                <div className="grid">
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
                    {/* 文字水印内容 */}
                    <Input
                        label="Text watermark"                              /* 字段标签 */
                        value={p.watermark_text}                            /* 当前值 */
                        onChange={(v) => {
                            p.watermark_text = v
                        }}          /* 变化回写 */
                        placeholder="e.g. For internal use only"             /* 提示文字 */
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
                    {/* 图片水印缩放比例 */}
                    <Input
                        label="Watermark scale"                             /* 字段标签 */
                        unit="Relative to frame width, 0–1"                 /* 单位提示 */
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
                    {/* 字幕文件路径 */}
                    <PathInput
                        label="Subtitle file"                               /* 字段标签 */
                        mode="subtitle"                                     /* 只允许选字幕 */
                        title="Select Subtitle File"                        /* 弹窗标题 */
                        value={p.subtitle_path}                             /* 当前值 */
                        onChange={(v) => {
                            p.subtitle_path = v
                        }}           /* 变化回写 */
                        placeholder="Empty = do not burn in subtitles"       /* 提示文字 */
                    />
                </div>
            </Section>

            {/* 剪辑与变速卡片 */}
            <Section title="Trimming & Speed">
                {/* 剪辑参数栅格 */}
                <div className="grid">
                    {/* 起始时间 */}
                    <Input
                        label="Start time"                                  /* 字段标签 */
                        value={p.start}                                     /* 当前值 */
                        onChange={(v) => {
                            p.start = v
                        }}                   /* 变化回写 */
                        placeholder="e.g. 00:00:10 or 10.5"                  /* 提示文字 */
                    />
                    {/* 截取时长 */}
                    <Input
                        label="Duration"                                    /* 字段标签 */
                        value={p.duration}                                  /* 当前值 */
                        onChange={(v) => {
                            p.duration = v
                        }}                /* 变化回写 */
                        placeholder="e.g. 30 or 00:00:30"                    /* 提示文字 */
                    />
                    {/* 结束时间 */}
                    <Input
                        label="End time"                                    /* 字段标签 */
                        value={p.end}                                       /* 当前值 */
                        onChange={(v) => {
                            p.end = v
                        }}                     /* 变化回写 */
                        placeholder="Either this or duration; duration wins if both are set"  /* 提示文字 */
                    />
                    {/* 变速倍率 */}
                    <Input
                        label="Playback speed"                              /* 字段标签 */
                        unit="Rate, e.g. 0.5 / 1.0 / 2.0"                   /* 单位提示 */
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
            <Section title="Metadata">
                {/* 元数据栅格 */}
                <div className="grid">
                    {/* 标题 */}
                    <Input
                        label="Title"                                        /* 字段标签 */
                        value={p.meta_title}                                /* 当前值 */
                        onChange={(v) => {
                            p.meta_title = v
                        }}              /* 变化回写 */
                    />
                    {/* 艺术家 */}
                    <Input
                        label="Artist"                                      /* 字段标签 */
                        value={p.meta_artist}                               /* 当前值 */
                        onChange={(v) => {
                            p.meta_artist = v
                        }}             /* 变化回写 */
                    />
                    {/* 专辑 */}
                    <Input
                        label="Album"                                       /* 字段标签 */
                        value={p.meta_album}                                /* 当前值 */
                        onChange={(v) => {
                            p.meta_album = v
                        }}              /* 变化回写 */
                    />
                    {/* 备注 */}
                    <Input
                        label="Comment"                                     /* 字段标签 */
                        value={p.meta_comment}                              /* 当前值 */
                        onChange={(v) => {
                            p.meta_comment = v
                        }}            /* 变化回写 */
                    />
                </div>
            </Section>

            {/* 流与容器卡片 */}
            <Section title="Stream Selection & Container">
                {/* 流选择栅格 */}
                <div className="grid">
                    {/* 流选择模式 */}
                    <Select
                        label="Stream selection mode"                       /* 字段标签 */
                        value={p.stream_mode}                               /* 当前值 */
                        options={STREAM_MODES}                              /* 选项 */
                        onChange={(v) => {
                            p.stream_mode = v
                        }}              /* 变化回写 */
                    />
                    {/* 指定视频流序号 */}
                    <Input
                        label="Video stream index"                          /* 字段标签 */
                        unit="-1 = auto"                                    /* 单位提示 */
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
                        label="Audio stream index"                          /* 字段标签 */
                        unit="-1 = auto"                                    /* 单位提示 */
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
                        label="Subtitle stream index"                       /* 字段标签 */
                        unit="-1 = auto"                                    /* 单位提示 */
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
                        label="Drop audio track"                            /* 复选框文字 */
                        checked={p.drop_audio}                              /* 当前值 */
                        onChange={(v) => {
                            p.drop_audio = v
                        }}              /* 变化回写 */
                    />
                    {/* 丢弃视频 */}
                    <Check
                        label="Drop video track (keep audio only)"             /* 复选框文字 */
                        checked={p.drop_video}                              /* 当前值 */
                        onChange={(v) => {
                            p.drop_video = v
                        }}              /* 变化回写 */
                    />
                </div>
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
