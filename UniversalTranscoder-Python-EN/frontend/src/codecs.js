// 编码器、容器与各类枚举选项表：集中定义界面下拉框可选项，避免散落在各页面里难以维护。

// 视频编码器候选（第一项为空字符串，表示交给容器默认值）。
export const VIDEO_ENCODERS = [
    '',                    // 自动（容器默认）
    'libx264',             // H.264 软件编码，兼容性最佳
    'libx265',             // H.265/HEVC 软件编码，压缩率更高
    'libvpx-vp9',          // VP9 软件的 WebM 编码器
    'libvpx',              // VP8 软件编码器
    'mpeg4',               // 老式 MPEG-4 Part 2（Xvid 同族）
    'prores_ks',           // ProRes 剪辑中间格式
    'dnxhd',               // DNxHD 广播级中间格式
    'mpeg2video',          // MPEG-2，用于老式 DVD/广播场景
    'gif',                 // 动图编码
    'png',                 // 无损单帧序列（用于图片序列容器）
    'h264_nvenc',          // NVIDIA NVENC 的 H.264
    'hevc_nvenc',          // NVIDIA NVENC 的 H.265
    'h264_qsv',            // Intel QuickSync 的 H.264
    'hevc_qsv',            // Intel QuickSync 的 H.265
    'h264_amf',            // AMD AMF 的 H.264
    'hevc_amf',            // AMD AMF 的 H.265
    'h264_videotoolbox',   // Apple 硬件编码
    'hevc_videotoolbox'    // Apple 硬件 H.265
]

// 音频编码器候选（第一项空表示容器默认，copy 表示直接封装不重编码）。
export const AUDIO_ENCODERS = [
    '',                    // 自动（容器默认）
    'copy',                // 直接复制音频流，不重编码
    'aac',                 // AAC，通用性最好
    'libmp3lame',          // MP3
    'libopus',             // Opus，高压缩
    'libvorbis',           // Vorbis
    'flac',                // 无损压缩
    'alac',                // Apple 无损
    'pcm_s16le',           // 16 位有符号小端 PCM（WAV）
    'pcm_s24le',           // 24 位有符号小端 PCM
    'ac3',                 // 杜比数字
    'eac3',                // 杜比数字增强版
    'wmav2',               // Windows Media Audio
    'mp2'                  // 老式 MPEG-1 音频
]

// 像素格式候选（空表示不指定，由内核按预设决定）。
export const PIXEL_FORMATS = [
    '',                    // 不指定
    'yuv420p',             // 最通用的 8 位 4:2:0
    'yuv422p',             // 4:2:2
    'yuv444p',             // 4:4:4，色彩保留最好
    'yuv420p10le',         // 10 位 4:2:0
    'yuv422p10le',         // 10 位 4:2:2
    'rgb24',               // 24 位 RGB
    'rgba',                // 带透明通道
    'gray',                // 灰度
    'pal8'                 // 8 位调色板（GIF 常用）
]

// 质量档位候选。
export const QUALITY_OPTIONS = [
    {value: 'lossless', label: 'Lossless (best quality)'},   // 无损档
    {value: 'high', label: 'High quality'},                  // 高质量档
    {value: 'medium', label: 'Standard (recommended)'},      // 默认档
    {value: 'low', label: 'Small size'},                     // 小体积档
    {value: 'custom', label: 'Custom'}                       // 完全手动指定
]

// 缩放模式候选。
export const SCALE_MODES = [
    {value: 'fit', label: 'Fit (keep aspect ratio)'},        // 保持比例，完整显示画面
    {value: 'fill', label: 'Fill (crop to fit)'},            // 保持比例，裁掉多余部分
    {value: 'stretch', label: 'Stretch (ignore aspect ratio)'} // 直接拉伸到目标尺寸
]

// 旋转角度候选。
export const ROTATIONS = [
    {value: '0', label: 'No rotation'},        // 不旋转
    {value: '90', label: '90° clockwise'},     // 顺时针 90 度
    {value: '180', label: '180°'},             // 旋转 180 度
    {value: '270', label: '270° clockwise'}   // 顺时针 270 度
]

// 镜像方式候选。
export const FLIP_OPTIONS = [
    {value: 'none', label: 'None'},              // 不镜像
    {value: 'h', label: 'Horizontal flip'},      // 左右翻转
    {value: 'v', label: 'Vertical flip'},        // 上下翻转
    {value: 'both', label: 'Horizontal + vertical'}  // 同时翻转
]

// 水印位置候选。
export const WATERMARK_POSITIONS = [
    {value: 'tl', label: 'Top left'},        // 左上
    {value: 'tr', label: 'Top right'},       // 右上
    {value: 'bl', label: 'Bottom left'},     // 左下
    {value: 'br', label: 'Bottom right'},    // 右下
    {value: 'center', label: 'Center'}       // 居中
]

// 声道数候选（0 表示保持原样）。
export const CHANNEL_OPTIONS = [
    {value: '0', label: 'Keep original'},        // 保持
    {value: '1', label: 'Mono 1'},               // 单声道
    {value: '2', label: 'Stereo 2'},             // 立体声
    {value: '4', label: 'Quad 4'},               // 四声道
    {value: '6', label: '5.1 channels 6'},       // 5.1
    {value: '8', label: '7.1 channels 8'}        // 7.1
]

// 采样率候选（0 表示保持原样）。
export const SAMPLE_RATE_OPTIONS = [
    {value: '0', label: 'Keep original'},        // 保持
    {value: '8000', label: '8000 Hz'},      // 电话音质
    {value: '16000', label: '16000 Hz'},    // 宽带语音
    {value: '22050', label: '22050 Hz'},    // 低采样音乐
    {value: '32000', label: '32000 Hz'},    // 中等采样
    {value: '44100', label: '44100 Hz'},    // CD 采样率
    {value: '48000', label: '48000 Hz'},    // 视频常用采样率
    {value: '96000', label: '96000 Hz'}     // 高解析采样
]

// 流选择模式候选。
export const STREAM_MODES = [
    {value: 'auto', label: 'Auto (kernel decides)'},        // 自动映射
    {value: 'first', label: 'First video/audio only'},      // 取第一组
    {value: 'custom', label: 'Specify stream indexes'}      // 自定义流序号
]

// 硬件加速策略候选（none / auto 为固定项，其余由 /api/system 动态补充）。
export const HWACCEL_BASE = [
    {value: 'none', label: 'No hardware acceleration (most stable)'},   // 关闭
    {value: 'auto', label: 'Auto-select available hardware encoder'}    // 自动
]

// 硬件厂商偏好候选。
export const HWACCEL_VENDORS = [
    {value: 'auto', label: 'Auto'},          // 自动
    {value: 'nvidia', label: 'NVIDIA'},      // 英伟达
    {value: 'intel', label: 'Intel'},        // 英特尔
    {value: 'amd', label: 'AMD'}             // AMD
]

// 输出命名策略候选（设置页使用）。
export const OUTPUT_NAME_MODES = [
    {value: 'suffix', label: 'Original name + suffix'},      // 加后缀
    {value: 'replace', label: 'Replace extension'},          // 替换扩展名
    {value: 'custom', label: 'Custom template'}              // 自定义模板
]

// 日志级别候选（设置页使用）。
export const LOG_LEVELS = [
    {value: 'DEBUG', label: 'DEBUG (most verbose)'},   // 调试
    {value: 'INFO', label: 'INFO (normal)'},           // 常规
    {value: 'WARNING', label: 'WARNING (warnings only)'},  // 警告
    {value: 'ERROR', label: 'ERROR (errors only)'}     // 错误
]

// 界面语言候选。
export const LANGUAGES = [
    {value: 'zh-CN', label: 'Chinese (Simplified)'},   // 中文
    {value: 'en-US', label: 'English'}         // 英文
]

// 界面主题候选。
export const THEMES = [
    {value: 'dark', label: 'Dark (default)'},  // 深色
    {value: 'light', label: 'Light'}           // 浅色
]

// 把候选数组转换为下拉框需要的 { value, label } 结构；空字符串项使用“自动”文案。
export function codecOptions(list, autoLabel) {
    // 生成选项数组。
    return list.map((item) => {
        // 空字符串表示自动。
        if (item === '') return {value: '', label: autoLabel || 'Auto'}
        // 其余项的值与显示名相同。
        return {value: item, label: item}
    })
}
