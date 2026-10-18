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
            <Section title="Software Info">
                {/* 软件名 */}
                <div className="kv"><span className="k">Software Name</span><span
                    className="v">{sys.app || 'Universal Transcoder'}</span></div>
                {/* 版本号 */}
                <div className="kv"><span className="k">Version</span><span
                    className="v">{sys.version || '1.0.0'}</span></div>
                {/* 作者 */}
                <div className="kv"><span className="k">Author</span><span
                    className="v">{sys.author || 'Unknown'}</span></div>
                {/* 一句话介绍 */}
                <div className="kv">
                    <span className="k">Tagline</span>
                    <span className="v">An all-in-one transcoder powered by ffmpeg: converts video, audio, images and documents, with controllable parameters, visible progress and offline operation.</span>
                </div>
            </Section>

            {/* 内核信息卡片 */}
            <Section title="Kernel Info">
                {/* ffmpeg 路径 */}
                <div className="kv"><span className="k">ffmpeg</span><span
                    className="v">{sys.ffmpeg || 'Not detected'}</span></div>
                {/* ffprobe 路径 */}
                <div className="kv"><span className="k">ffprobe</span><span
                    className="v">{sys.ffprobe || 'Not detected'}</span></div>
                {/* 内核版本 */}
                <div className="kv"><span className="k">Kernel Version</span><span
                    className="v">{sys.ffmpeg_version || 'Not available (click "Detect Kernel" on the Settings page)'}</span>
                </div>
                {/* 能力统计 */}
                <div className="kv">
                    <span className="k">Capabilities</span>
                    <span className="v">
            {/* 把三项能力统计拼成一句可读文字 */}
                        {`${cap.video_encoders || 0} video encoders · ${cap.audio_encoders || 0} audio encoders · ${cap.format_total || 0} supported formats`}
          </span>
                </div>
                {/* 硬件加速能力 */}
                <div className="kv">
                    <span className="k">Hardware Acceleration</span>
                    <span className="v">{(cap.hwaccels || []).join(', ') || 'No hardware acceleration available'}</span>
                </div>
            </Section>

            {/* 使用说明卡片 */}
            <Section title="How to Use">
                {/* 步骤说明 */}
                <div className="hint">
                    1. Click "Add Files" in the "File List" on the left and select the files to convert;<br/>
                    2. Pick the target format and preset in the parameter panel on the right, and fine-tune as
                    needed;<br/>
                    3. Click "Add to Queue", then click "Start" in the task area at the bottom;<br/>
                    4. Watch live progress, speed, time left and size change in the progress bar and task table;<br/>
                    5. Failed tasks can be reviewed with error details and advice on the "Task Queue" page.
                </div>
            </Section>

            {/* 免责声明卡片 */}
            <Section title="Disclaimer">
                {/* 声明正文 */}
                <div className="hint">
                    This is a local tool. All transcoding is performed by the ffmpeg kernel on your own machine; no
                    files are uploaded to any network server.
                    Please make sure you have the legal right to use the content you convert. The author accepts no
                    responsibility for any copyright issues, data loss or device problems arising from the use of this
                    software.
                    Before batch converting, back up your original files and do a trial run on a few files to confirm
                    the parameters behave as expected.
                </div>
                {/* 版权行 */}
                <div
                    className="hint mt-8">{`© 2026 ${sys.author || 'Unknown'} · Universal Transcoder v${sys.version || '1.0.0'} · This software is based on ffmpeg and follows their respective open-source licenses.`}</div>
            </Section>
        </div>
    )
}
