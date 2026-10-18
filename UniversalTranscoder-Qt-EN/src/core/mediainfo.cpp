// *****************************************************************************
// mediainfo.cpp —— 媒体信息探测的实现。
// 实现要点：
//   1) 直接用 ffprobe 的 JSON 输出（-print_format json -show_format -show_streams），
//      再交给 QJsonDocument 解析，避免自己写脆弱的文本正则。
//   2) 每个字段都经过 jsonToDouble / jsonToInt64 这两个安全转换函数，
//      因为它们必须同时处理“数字型 JSON 值”和“字符串型 JSON 值”两种形态。
//   3) 解析完容器与流之后，若容器未给时长，则用最长流的时长补齐（TS/裸流常见）。
// *****************************************************************************
#include "mediainfo.h"

// 内核定位器：取 ffprobe 路径。
#include "ffmpeg_locator.h"
// processutil：同步执行并只捕获标准输出（JSON 绝不能被标准错误污染）。
#include "processutil.h"
// QJsonDocument / QJsonObject / QJsonArray / QJsonValue：JSON 解析。
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QJsonValue>
// QFileInfo：文件存在性与大小。
#include <QFileInfo>
// QDir：绝对路径规范化。
#include <QDir>
// QtMath：提供 qRound64（把浮点安全四舍五入成 64 位整数）。
#include <QtMath>

// 流分辨率文本。
QString StreamInfo::resolution() const
{
    // 宽高都大于 0 才有展示意义。
    if (width > 0 && height > 0)
    {
        // 拼成 "宽x高" 形式。
        return QStringLiteral("%1x%2").arg(width).arg(height);
    }
    // 否则返回空串，调用方自行决定是否跳过该项。
    return QString();
}

// 全部视频流。
QVector<StreamInfo> MediaInfo::videoStreams() const
{
    // 结果容器。
    QVector<StreamInfo> out;
    // 遍历全部流并按类型筛选。
    for (const StreamInfo& s : streams)
    {
        // 视频流加入结果。
        if (s.isVideo()) { out.append(s); }
    }
    // 返回结果。
    return out;
}

// 全部音频流。
QVector<StreamInfo> MediaInfo::audioStreams() const
{
    // 结果容器。
    QVector<StreamInfo> out;
    // 遍历筛选。
    for (const StreamInfo& s : streams)
    {
        // 音频流加入结果。
        if (s.isAudio()) { out.append(s); }
    }
    // 返回结果。
    return out;
}

// 全部字幕流。
QVector<StreamInfo> MediaInfo::subtitleStreams() const
{
    // 结果容器。
    QVector<StreamInfo> out;
    // 遍历筛选。
    for (const StreamInfo& s : streams)
    {
        // 字幕流加入结果。
        if (s.isSubtitle()) { out.append(s); }
    }
    // 返回结果。
    return out;
}

// 是否存在视频流。
bool MediaInfo::hasVideo() const
{
    // 遍历到第一条视频流即可返回 true。
    for (const StreamInfo& s : streams)
    {
        // 命中视频流。
        if (s.isVideo()) { return true; }
    }
    // 未命中。
    return false;
}

// 是否存在音频流。
bool MediaInfo::hasAudio() const
{
    // 遍历到第一条音频流即可返回 true。
    for (const StreamInfo& s : streams)
    {
        // 命中音频流。
        if (s.isAudio()) { return true; }
    }
    // 未命中。
    return false;
}

// 主视频流。
StreamInfo MediaInfo::primaryVideo() const
{
    // 遍历全部流。
    for (const StreamInfo& s : streams)
    {
        // 返回第一条视频流。
        if (s.isVideo()) { return s; }
    }
    // 没有视频流时返回默认构造的空对象。
    return StreamInfo();
}

// 主音频流。
StreamInfo MediaInfo::primaryAudio() const
{
    // 遍历全部流。
    for (const StreamInfo& s : streams)
    {
        // 返回第一条音频流。
        if (s.isAudio()) { return s; }
    }
    // 没有音频流时返回空对象。
    return StreamInfo();
}

// 粗粒度归类。
QString MediaInfo::kind() const
{
    // 先看是否存在视频流。
    if (hasVideo())
    {
        // 取主视频流以判断编码类型。
        const StreamInfo v = primaryVideo();
        // 图片类编码（且几乎无时长）判定为图片。
        static const QStringList imageCodecs = {
            QStringLiteral("mjpeg"), QStringLiteral("png"), QStringLiteral("webp"),
            QStringLiteral("bmp"), QStringLiteral("gif"), QStringLiteral("jpeg"),
            QStringLiteral("heif"), QStringLiteral("avif"), QStringLiteral("tiff")
        };
        // 编码名命中图片编码表则归类为图片。
        if (imageCodecs.contains(v.codecName)) { return QStringLiteral("image"); }
        // 时长为 0 或极小（例如单帧 mp4 封面）同样按图片处理。
        if (duration <= 0.05) { return QStringLiteral("image"); }
        // 其余均为视频。
        return QStringLiteral("video");
    }
    // 仅有音频流则归类为音频。
    if (hasAudio()) { return QStringLiteral("audio"); }
    // 两者都没有（例如纯数据文件）归类为未知。
    return QStringLiteral("unknown");
}

// 显示名。
QString MediaInfo::displayName() const
{
    // 路径为空时给出兜底文案（英文，因为会显示给用户）。
    if (path.isEmpty()) { return QStringLiteral("(unknown file)"); }
    // 返回路径的最后一段。
    return QFileInfo(path).fileName();
}

// 一行摘要。
QString MediaInfo::summaryLine() const
{
    // 逐项拼装，缺失项自动跳过，避免出现空白括号。
    QStringList parts;
    // 容器格式。
    if (!formatName.isEmpty())
    {
        // 加入容器名。
        parts << formatName;
    }
    // 视频信息：分辨率 + 编码 + 帧率。
    const StreamInfo v = primaryVideo();
    // 仅当存在视频信息时处理。
    if (v.index >= 0 || !v.codecName.isEmpty())
    {
        // 视频子项容器。
        QStringList vp;
        // 分辨率。
        if (!v.resolution().isEmpty()) { vp << v.resolution(); }
        // 编码名。
        if (!v.codecName.isEmpty()) { vp << v.codecName; }
        // 帧率（保留三位有效小数，形如 23.976）。
        if (v.fps > 0.0) { vp << QStringLiteral("%1 fps").arg(v.fps, 0, 'f', 2); }
        // 有内容才加入。
        if (!vp.isEmpty()) { parts << QStringLiteral("video: ") + vp.join(QLatin1Char('/')); }
    }
    // 音频信息：编码 + 采样率 + 声道。
    const StreamInfo a = primaryAudio();
    // 仅当存在音频信息时处理。
    if (a.index >= 0 || !a.codecName.isEmpty())
    {
        // 音频子项容器。
        QStringList ap;
        // 编码名。
        if (!a.codecName.isEmpty()) { ap << a.codecName; }
        // 采样率。
        if (a.sampleRate > 0) { ap << QStringLiteral("%1 Hz").arg(a.sampleRate); }
        // 声道数。
        if (a.channels > 0) { ap << QStringLiteral("%1 ch").arg(a.channels); }
        // 有内容才加入。
        if (!ap.isEmpty()) { parts << QStringLiteral("audio: ") + ap.join(QLatin1Char('/')); }
    }
    // 时长（统一格式化为 mm:ss）。
    if (duration > 0.0)
    {
        // 计算总秒数。
        const int totalSec = int(duration);
        // 分与秒。
        parts << QStringLiteral("duration: %1:%2")
                 .arg(totalSec / 60, 2, 10, QLatin1Char('0'))
                 .arg(totalSec % 60, 2, 10, QLatin1Char('0'));
    }
    // 全部缺失时给出兜底文案。
    return parts.isEmpty() ? QStringLiteral("no usable information") : parts.join(QStringLiteral(" | "));
}

// 安全转浮点。
double jsonToDouble(const QJsonValue& value, double defaultValue)
{
    // 数字型直接取值。
    if (value.isDouble())
    {
        // 返回数值。
        return value.toDouble(defaultValue);
    }
    // 字符串型需要额外处理 "N/A" 等缺失写法。
    if (value.isString())
    {
        // 去空白。
        const QString s = value.toString().trimmed();
        // 显式拦截常见的缺失写法。
        if (s.isEmpty() || s.compare(QStringLiteral("N/A"), Qt::CaseInsensitive) == 0
            || s.compare(QStringLiteral("NA"), Qt::CaseInsensitive) == 0
            || s.compare(QStringLiteral("unknown"), Qt::CaseInsensitive) == 0)
        {
            // 回退默认值。
            return defaultValue;
        }
        // 尝试转换：QString::toDouble 需要显式的成功标志，因此这里手工判断，失败时回退默认值。
        bool converted = false;
        // 执行转换。
        const double parsed = value.toString().toDouble(&converted);
        // 成功则返回结果，失败则回退默认值。
        return converted ? parsed : defaultValue;
    }
    // 其它类型（null/bool/array/object）一律回退默认值。
    return defaultValue;
}

// 安全转整数。
qint64 jsonToInt64(const QJsonValue& value, qint64 defaultValue)
{
    // 先走浮点解析，兼容 "48000.000000" 这类带小数点的整数字符串。
    const double d = jsonToDouble(value, double(defaultValue));
    // 四舍五入后取整，最后转成 64 位整数。
    return qint64(qRound64(d));
}

// 解析帧率。
double parseFps(const QJsonValue& value)
{
    // 字符串形式可能是分数 "30000/1001"。
    if (value.isString())
    {
        // 取出文本。
        const QString s = value.toString().trimmed();
        // 含斜杠则按分数解析。
        if (s.contains(QLatin1Char('/')))
        {
            // 拆分分子分母。
            const QStringList seg = s.split(QLatin1Char('/'));
            // 必须恰好两段才有效。
            if (seg.size() == 2)
            {
                // 分母数值。
                const double den = seg.at(1).trimmed().toDouble();
                // 分母为 0 表示可变帧率或未知，返回 0 交由上层忽略。
                if (qFuzzyIsNull(den)) { return 0.0; }
                // 分子数值。
                const double num = seg.at(0).trimmed().toDouble();
                // 返回换算结果。
                return num / den;
            }
            // 格式异常时返回 0。
            return 0.0;
        }
        // 非分数形式直接走安全转换。
        return jsonToDouble(value, 0.0);
    }
    // 数字型直接取值。
    return jsonToDouble(value, 0.0);
}

// 探测一个媒体文件。
MediaInfo probeMedia(const QString& path, int timeoutMs)
{
    // 构造结果对象。
    MediaInfo info;
    // 统一转成绝对路径，避免子进程在不同工作目录下解析相对路径出错。
    info.path = QDir::cleanPath(QFileInfo(path).absoluteFilePath());
    // 构造文件信息。
    const QFileInfo fi(info.path);
    // 记录文件是否存在。
    info.exists = fi.exists() && fi.isFile();
    // 记录文件大小（存在时才有意义）。
    if (info.exists)
    {
        // 取字节数。
        info.size = fi.size();
    }
    // 取 ffprobe 路径。
    const QString probeExe = FFmpegLocator::instance().ffprobePath();
    // 没有 ffprobe 时直接返回（错误文本为英文，会显示给用户）。
    if (probeExe.isEmpty())
    {
        // 记录错误原因。
        info.error = QStringLiteral("FFprobe not found");
        // 返回低置信度结果。
        return info;
    }
    // 文件不存在时省去一次注定失败的进程调用。
    if (!info.exists)
    {
        // 记录错误原因。
        info.error = QStringLiteral("File does not exist");
        // 返回低置信度结果。
        return info;
    }
    // 组装 ffprobe 参数。
    QStringList args;
    // 程序路径。
    args << probeExe;
    // -v quiet：只保留我们需要的信息，屏蔽噪声。
    args << QStringLiteral("-v") << QStringLiteral("quiet");
    // 输出 JSON 格式便于解析。
    args << QStringLiteral("-print_format") << QStringLiteral("json");
    // 输出容器层面信息。
    args << QStringLiteral("-show_format");
    // 输出各流信息。
    args << QStringLiteral("-show_streams");
    // 输出章节信息（部分容器有章节，供高级功能使用）。
    args << QStringLiteral("-show_chapters");
    // 目标文件路径。
    args << info.path;
    // 标准输出缓冲（只取标准输出，保证 JSON 干净）。
    QByteArray outBytes;
    // 进程退出码。
    int rc = -1;
    // 执行探测。
    const bool runOk = runCaptureStdout(args, outBytes, timeoutMs, &rc);
    // 输出为空说明探测失败（例如目标根本不是媒体文件）。
    if (outBytes.trimmed().isEmpty())
    {
        // 记录错误文本。
        info.error = runOk
                         ? QStringLiteral("FFprobe produced no output")
                         : QStringLiteral("FFprobe failed to run or timed out");
        // 返回低置信度结果。
        return info;
    }
    // 解析 JSON。
    QJsonParseError parseError;
    // 从字节流构造文档；容错模式允许解析不严格的 JSON。
    const QJsonDocument doc = QJsonDocument::fromJson(outBytes, &parseError);
    // 解析失败时返回低置信度结果并带上错误描述。
    if (parseError.error != QJsonParseError::NoError || !doc.isObject())
    {
        // 记录错误文本。
        info.error = QStringLiteral("Failed to parse FFprobe output: %1").arg(parseError.errorString());
        // 返回低置信度结果。
        return info;
    }
    // 保留原始 JSON，供高级功能读取冷门字段。
    info.raw = doc.object();
    // ---- 解析容器信息 ----
    const QJsonObject fmt = info.raw.value(QStringLiteral("format")).toObject();
    // 容器格式名。
    info.formatName = fmt.value(QStringLiteral("format_name")).toString();
    // 容器全称。
    info.formatLongName = fmt.value(QStringLiteral("format_long_name")).toString();
    // 总时长。
    info.duration = jsonToDouble(fmt.value(QStringLiteral("duration")), 0.0);
    // 总码率。
    info.bitRate = jsonToInt64(fmt.value(QStringLiteral("bit_rate")), 0);
    // 若文件系统层面拿不到大小，则用容器报告的大小补上。
    if (info.size <= 0)
    {
        // 用 JSON 中的 size 字段补齐。
        info.size = jsonToInt64(fmt.value(QStringLiteral("size")), 0);
    }
    // ---- 逐个解析流 ----
    const QJsonArray streams = info.raw.value(QStringLiteral("streams")).toArray();
    // 遍历流数组。
    for (const QJsonValue& sv : streams)
    {
        // 防御：非对象项直接跳过（异常文件可能出现）。
        if (!sv.isObject()) { continue; }
        // 取该流的 JSON 对象。
        const QJsonObject so = sv.toObject();
        // 构造流信息对象。
        StreamInfo st;
        // 流序号。
        st.index = int(jsonToInt64(so.value(QStringLiteral("index")), -1));
        // 流类型。
        st.codecType = so.value(QStringLiteral("codec_type")).toString();
        // 编码名。
        st.codecName = so.value(QStringLiteral("codec_name")).toString();
        // 编码全称。
        st.codecLongName = so.value(QStringLiteral("codec_long_name")).toString();
        // 视频宽度。
        st.width = int(jsonToInt64(so.value(QStringLiteral("width")), 0));
        // 视频高度。
        st.height = int(jsonToInt64(so.value(QStringLiteral("height")), 0));
        // 帧率：优先平均帧率，缺失时退回即时帧率。
        st.fps = parseFps(so.contains(QStringLiteral("avg_frame_rate"))
                              ? so.value(QStringLiteral("avg_frame_rate"))
                              : so.value(QStringLiteral("r_frame_rate")));
        // 该流码率。
        st.bitRate = jsonToInt64(so.value(QStringLiteral("bit_rate")), 0);
        // 音频采样率。
        st.sampleRate = int(jsonToInt64(so.value(QStringLiteral("sample_rate")), 0));
        // 音频声道数。
        st.channels = int(jsonToInt64(so.value(QStringLiteral("channels")), 0));
        // 声道布局。
        st.channelLayout = so.value(QStringLiteral("channel_layout")).toString();
        // 该流时长。
        st.duration = jsonToDouble(so.value(QStringLiteral("duration")), 0.0);
        // 像素格式。
        st.pixFmt = so.value(QStringLiteral("pix_fmt")).toString();
        // 标签对象（多语言音轨/字幕依赖它）。
        const QJsonObject tags = so.value(QStringLiteral("tags")).toObject();
        // 语言标记。
        st.language = tags.value(QStringLiteral("language")).toString();
        // 流标题。
        st.title = tags.value(QStringLiteral("title")).toString();
        // 追加到结果列表。
        info.streams.append(st);
    }
    // ---- 时长兜底：容器未给时长时用最长流补上（常见于 TS 与裸流）----
    if (info.duration <= 0.0)
    {
        // 收集所有正时长。
        double maxDur = 0.0;
        // 遍历流。
        for (const StreamInfo& s : info.streams)
        {
            // 取最大值。
            if (s.duration > maxDur) { maxDur = s.duration; }
        }
        // 回填容器时长。
        info.duration = maxDur;
    }
    // 标记探测成功。
    info.probeOk = true;
    // 返回结果。
    return info;
}