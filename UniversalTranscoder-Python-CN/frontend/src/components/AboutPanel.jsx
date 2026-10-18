// 关于页：展示软件名称、版本、作者、简介、内核版本与免责声明。
import {store} from '../store.js'       // 引入全局状态（读取 /api/system 的信息）
import {Section} from './ui.jsx'        // 引入卡片控件

// 组件本体。
export function AboutPanel() {
    // 取系统信息（可能尚未加载完成）。
    const sys = store.system || {}
    // 取能力统计。
    const cap = sys.capabilities || {}
    // 渲染页面。
    return (
        // 页面根容器
        <div>
            {/* 软件信息卡片 */}
            <Section title="软件信息">
                {/* 软件名 */}
                <div className="kv"><span className="k">软件名称</span><span
                    className="v">{sys.app || '万能转码器'}</span></div>
                {/* 版本号 */}
                <div className="kv"><span className="k">版本号</span><span className="v">{sys.version || '1.0.0'}</span>
                </div>
                {/* 作者 */}
                <div className="kv"><span className="k">作者</span><span className="v">{sys.author || '徐某'}</span>
                </div>
                {/* 一句话介绍 */}
                <div className="kv">
                    <span className="k">一句话介绍</span>
                    <span
                        className="v">一个以 ffmpeg 为内核的万能转码器：视频、音频、图片、文档都能转，参数可控、进度可见、离线可用。</span>
                </div>
            </Section>

            {/* 内核信息卡片 */}
            <Section title="内核信息">
                {/* ffmpeg 路径 */}
                <div className="kv"><span className="k">ffmpeg</span><span
                    className="v">{sys.ffmpeg || '未检测到'}</span></div>
                {/* ffprobe 路径 */}
                <div className="kv"><span className="k">ffprobe</span><span
                    className="v">{sys.ffprobe || '未检测到'}</span></div>
                {/* 内核版本 */}
                <div className="kv"><span className="k">内核版本</span><span
                    className="v">{sys.ffmpeg_version || '未提供（请点击设置页的“检测内核”）'}</span></div>
                {/* 能力统计 */}
                <div className="kv">
                    <span className="k">能力统计</span>
                    <span className="v">
            {/* 把三项能力统计拼成一句可读文字 */}
                        {`视频编码器 ${cap.video_encoders || 0} 个 · 音频编码器 ${cap.audio_encoders || 0} 个 · 支持格式 ${cap.format_total || 0} 种`}
          </span>
                </div>
                {/* 硬件加速能力 */}
                <div className="kv">
                    <span className="k">硬件加速</span>
                    <span className="v">{(cap.hwaccels || []).join('、') || '未检测到可用硬件加速方案'}</span>
                </div>
            </Section>

            {/* 使用说明卡片 */}
            <Section title="使用方式">
                {/* 步骤说明 */}
                <div className="hint">
                    1. 在左侧“待转文件列表”点击“添加文件”，选择要转换的文件；<br/>
                    2. 在右侧参数面板选择目标格式与预设，并按需微调参数；<br/>
                    3. 点击“添加到队列”，然后在底部任务区点击“开始”；<br/>
                    4. 通过进度条与任务表格查看实时进度、速度、剩余时间与体积变化；<br/>
                    5. 失败任务可在“任务队列”页查看错误详情与处置建议。
                </div>
            </Section>

            {/* 免责声明卡片 */}
            <Section title="免责声明">
                {/* 声明正文 */}
                <div className="hint">
                    本软件为本地工具软件，所有转码操作均在你自己的机器上由 ffmpeg 内核完成，不上传任何文件到网络服务器。
                    请确保你对所转换的内容拥有合法使用权；因使用本软件产生的任何内容版权问题、数据损失或设备问题，作者不承担相应责任。
                    批量转换前建议先备份原始文件，并在个别文件上试跑一次以确认参数符合预期。
                </div>
                {/* 版权行 */}
                <div
                    className="hint mt-8">{`© 2026 ${sys.author || '徐某'} · 万能转码器 v${sys.version || '1.0.0'} · 本软件基于 ffmpeg，遵循其各自的开源许可协议。`}</div>
            </Section>
        </div>
    )
}
