// 前端命令预览：依据当前参数推导出将执行的 ffmpeg 命令行文本，便于用户在提交前复核参数。
// 说明：这是“前端近似推导”，真实命令由后端的内核按更严谨的规则组装，因此界面会明确标注这一点。

import {buildSettings} from './store.js'    // 引入参数构造逻辑，保证预览与实际提交使用同一份字段
import {nameOf} from './format.js'          // 引入文件名提取工具，用于推导默认输出名

// 判断一个词元是否需要加引号（包含空格或常见 shell 特殊字符时）。
function quote(token) {
    // 转为字符串。
    const s = String(token)
    // 空串需要保留为空引号。
    if (!s) return '""'
    // 含空格或特殊字符时使用双引号包裹。
    if (/[\s"'&|<>()^]/.test(s)) return `"${s}"`
    // 普通词元原样返回。
    return s
}

// 依据参数对象与源文件路径推导将执行的命令词元数组。
// 参数 key：类别键名（video/audio/image/advanced）；参数 source/out：用于展示的输入输出路径。
export function previewTokens(key, source, out) {
    // 取出与提交完全一致的设置对象。
    const s = buildSettings(key)
    // 准备词元数组，首个词元是可执行文件名。
    const tokens = ['ffmpeg']
    // 覆盖已有文件。
    if (s.overwrite) tokens.push('-y')
    // 起始时间（输入侧快速定位）。
    if (s.start) tokens.push('-ss', s.start)
    // 截取时长（优先于结束时间）。
    if (s.duration) {
        // 追加时长参数。
        tokens.push('-t', s.duration)
    } else if (s.end) {
        // 未给时长时使用结束时间点。
        tokens.push('-to', s.end)
    }
    // 输入文件。
    tokens.push('-i', source || '<input file>')
    // 视频编码：直接复制或指定编码器。
    if (s.copy_mode) {
        // 仅换容器：视频与音频都走 copy。
        tokens.push('-c:v', 'copy', '-c:a', 'copy')
    } else {
        // 指定视频编码器（为空则交给容器默认值，此处不输出该参数）。
        if (s.video_codec) tokens.push('-c:v', s.video_codec)
        // 指定音频编码器。
        if (s.audio_codec) tokens.push('-c:a', s.audio_codec)
    }
    // 丢弃音频轨道。
    if (s.drop_audio) tokens.push('-an')
    // 丢弃视频轨道。
    if (s.drop_video) tokens.push('-vn')
    // 恒定质量因子。
    if (s.crf) tokens.push('-crf', String(s.crf))
    // 视频码率。
    if (s.v_bitrate) tokens.push('-b:v', s.v_bitrate)
    // 音频码率。
    if (s.a_bitrate) tokens.push('-b:a', s.a_bitrate)
    // 目标分辨率（宽高都给出时才输出）。
    if (s.width && s.height) tokens.push('-s', `${s.width}x${s.height}`)
    // 目标帧率。
    if (s.fps) tokens.push('-r', String(s.fps))
    // 像素格式。
    if (s.pixel_format) tokens.push('-pix_fmt', s.pixel_format)
    // 目标声道数。
    if (s.audio_channels) tokens.push('-ac', String(s.audio_channels))
    // 目标采样率。
    if (s.sample_rate) tokens.push('-ar', String(s.sample_rate))
    // 收集视频滤镜（旋转、镜像、裁剪、水印、字幕等会合并进一条 -vf）。
    const vf = []
    // 裁剪区域。
    if (s.crop) vf.push(`crop=${s.crop}`)
    // 旋转（90/270 时交换宽高，此处仅示意）。
    if (s.rotate) vf.push(`rotate=${s.rotate}*PI/180`)
    // 镜像：水平。
    if (s.flip === 'h' || s.flip === 'both') vf.push('hflip')
    // 镜像：垂直。
    if (s.flip === 'v' || s.flip === 'both') vf.push('vflip')
    // 缩放（宽高给出时按缩放模式生成）。
    if (s.width || s.height) {
        // 按缩放模式选择表达式。
        const mode = s.scale_mode === 'stretch' ? 'stretch' : s.scale_mode === 'fill' ? 'fill' : 'fit'
        // 追加缩放滤镜示意。
        vf.push(`scale=${s.width || -2}:${s.height || -2} (${mode})`)
    }
    // 文字水印。
    if (s.watermark_text) vf.push(`drawtext=text='${s.watermark_text}':pos=${s.watermark_pos}:opacity=${s.watermark_opacity}`)
    // 图片水印。
    if (s.watermark_path) vf.push(`movie=${nameOf(s.watermark_path)}:pos=${s.watermark_pos}:scale=${s.watermark_scale}`)
    // 字幕烧录。
    if (s.subtitle_path) vf.push(`subtitles=${nameOf(s.subtitle_path)}`)
    // 有视频滤镜时追加 -vf 参数。
    if (vf.length) tokens.push('-vf', vf.join(','))
    // 收集音频滤镜（变速与音量）。
    const af = []
    // 变速（仅示意，真实实现可能同时作用于音视频）。
    if (s.speed && Number(s.speed) !== 1) {
        // 音视频同步变速示意。
        af.push(`atempo=${Number(s.speed)}`)
    }
    // 音量。
    if (s.volume) af.push(`volume=${s.volume}`)
    // 有音频滤镜时追加 -af 参数。
    if (af.length) tokens.push('-af', af.join(','))
    // 元数据项逐个追加。
    if (s.meta_title) tokens.push('-metadata', `title=${s.meta_title}`)
    // 元数据艺术家。
    if (s.meta_artist) tokens.push('-metadata', `artist=${s.meta_artist}`)
    // 元数据专辑。
    if (s.meta_album) tokens.push('-metadata', `album=${s.meta_album}`)
    // 元数据备注。
    if (s.meta_comment) tokens.push('-metadata', `comment=${s.meta_comment}`)
    // 流选择模式为 custom 时指定各流序号。
    if (s.stream_mode === 'custom') {
        // 视频流序号。
        if (s.video_index >= 0) tokens.push('-map', `0:${s.video_index}`)
        // 音频流序号。
        if (s.audio_index >= 0) tokens.push('-map', `0:${s.audio_index}`)
        // 字幕流序号。
        if (s.subtitle_index >= 0) tokens.push('-map', `0:${s.subtitle_index}`)
    }
    // 自定义参数逐项追加（高级页与图片质量参数都走这里）。
    for (const arg of s.custom_args || []) {
        // 追加单个词元。
        tokens.push(arg)
    }
    // 输出文件路径。
    tokens.push(out || `<output file>.${s.target_format}`)
    // 返回词元数组。
    return tokens
}

// 生成可直接显示的完整命令文本。
export function previewCommand(key, source, out) {
    // 取词元数组并逐个加引号。
    return previewTokens(key, source, out).map(quote).join(' ')
}
