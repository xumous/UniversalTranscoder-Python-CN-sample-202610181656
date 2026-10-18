// 音频转码参数面板：目标格式与预设、音频编码参数、音量与变速、剪辑、元数据，并提供“直接封装”开关。
import {store} from '../store.js'                                  // 引入全局状态
import {Select, Input, Check, Section} from './ui.jsx'             // 引入通用控件
import {PresetBar, SubmitRow} from './PresetBar.jsx'               // 引入选择条与提交行
import {OutputSection} from './OutputSection.jsx'                  // 引入输出设置区块
import {toInt, toFloat} from '../format.js'                        // 引入数值转换工具
import {
    AUDIO_ENCODERS, QUALITY_OPTIONS, CHANNEL_OPTIONS, SAMPLE_RATE_OPTIONS, codecOptions
} from '../codecs.js'                                                 // 引入枚举选项表

// 组件本体。
export function AudioPanel() {
    // 取出音频类参数对象。
    const p = store.params.audio
    // 组装音频编码器选项。
    const aCodecOptions = codecOptions(AUDIO_ENCODERS, '自动（由预设或容器决定）')
    // 判断当前是否处于“直接封装”状态（copy_mode 或编码器为 copy）。
    const isCopy = !!p.copy_mode || p.audio_codec === 'copy'
    // 渲染面板。
    return (
        // 面板根容器
        <div>
            {/* 基础设置卡片 */}
            <Section title="基础：目标格式与预设">
                {/* 目标格式与预设选择条 */}
                <PresetBar pk="audio" cat="audio" formats={store.formats.audio}/>
                {/* 质量档位 */}
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
                </div>
            </Section>

            {/* 编码参数卡片 */}
            <Section title="编码：编码器与码率">
                {/* 编码参数栅格 */}
                <div className="grid">
                    {/* 音频编码器 */}
                    <Select
                        label="音频编码器"                                  /* 字段标签 */
                        value={p.audio_codec}                               /* 当前值 */
                        options={aCodecOptions}                             /* 选项 */
                        onChange={(v) => {
                            p.audio_codec = v
                        }}             /* 变化回写 */
                        disabled={!!p.copy_mode}                            /* 直接封装时编码器不可改 */
                    />
                    {/* 音频码率 */}
                    <Input
                        label="音频码率"                                    /* 字段标签 */
                        unit="例如 192k 或 320k"                             /* 单位提示 */
                        value={p.a_bitrate}                                 /* 当前值 */
                        onChange={(v) => {
                            p.a_bitrate = v
                        }}               /* 变化回写 */
                        disabled={isCopy}                                   /* 直接封装时码率无意义 */
                    />
                    {/* 声道数 */}
                    <Select
                        label="声道数"                                      /* 字段标签 */
                        value={String(p.audio_channels || 0)}               /* 当前值 */
                        options={CHANNEL_OPTIONS}                           /* 选项 */
                        onChange={(v) => {
                            p.audio_channels = toInt(v, 0)
                        }}/* 变化回写 */
                    />
                    {/* 采样率 */}
                    <Select
                        label="采样率"                                      /* 字段标签 */
                        value={String(p.sample_rate || 0)}                  /* 当前值 */
                        options={SAMPLE_RATE_OPTIONS}                       /* 选项 */
                        onChange={(v) => {
                            p.sample_rate = toInt(v, 0)
                        }}   /* 变化回写 */
                    />
                </div>
                {/* 直接封装开关 */}
                <div className="mt-8">
                    {/* 直接封装复选框 */}
                    <Check
                        label="直接封装（仅更换容器，不重新编码；源为 AAC 转 M4A 等场景最快且无损）"  /* 复选框文字 */
                        checked={isCopy}                                    /* 当前值 */
                        onChange={(v) => {                                  /* 变化回写 */
                            // 同步 copy_mode 开关。
                            p.copy_mode = v
                            // 开启时把编码器设为 copy，关闭时恢复为空（交给容器默认值）。
                            p.audio_codec = v ? 'copy' : (p.audio_codec === 'copy' ? '' : p.audio_codec)
                        }}
                    />
                </div>
            </Section>

            {/* 音量与变速卡片 */}
            <Section title="音量、变速与剪辑">
                {/* 处理参数栅格 */}
                <div className="grid">
                    {/* 音量调整 */}
                    <Input
                        label="音量"                                        /* 字段标签 */
                        unit="倍数或分贝，例如 1.5 或 -6dB"                    /* 单位提示 */
                        value={p.volume}                                    /* 当前值 */
                        onChange={(v) => {
                            p.volume = v
                        }}                  /* 变化回写 */
                        placeholder="留空表示不调整音量"                       /* 提示文字 */
                    />
                    {/* 播放速度 */}
                    <Input
                        label="播放速度"                                    /* 字段标签 */
                        unit="倍率，例如 0.5 / 1.0 / 2.0"                     /* 单位提示 */
                        type="number"                                       /* 数字输入 */
                        step="0.05"                                         /* 步长 */
                        min="0.1"                                           /* 最小 0.1 */
                        value={p.speed}                                     /* 当前值 */
                        onChange={(v) => {
                            p.speed = toFloat(v, 1)
                        }}       /* 变化回写 */
                    />
                    {/* 起始时间 */}
                    <Input
                        label="起始时间"                                    /* 字段标签 */
                        value={p.start}                                     /* 当前值 */
                        onChange={(v) => {
                            p.start = v
                        }}                   /* 变化回写 */
                        placeholder="例如 00:00:10"                          /* 提示文字 */
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
                        placeholder="与时长二选一"                            /* 提示文字 */
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
