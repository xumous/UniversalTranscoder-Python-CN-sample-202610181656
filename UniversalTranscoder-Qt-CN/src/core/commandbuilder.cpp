// *****************************************************************************
// commandbuilder.cpp —— 命令构建的实现（本项目算法核心）。
// 实现要点与“为什么这样做”：
//   1) 封装名换算（CONTAINER_ALIAS）必须做：ffmpeg 并不存在名为 mkv / ts / mpg 的封装，
//      直接写 `-f mkv` 会被拒绝（Unknown muxer）。本表的每个值都已用真实内核的
//      `ffmpeg -formats` 逐条核对过，因此是可靠映射而非猜测。
//   2) 默认编码器表的键必须是“换算后的封装名”：例如用户输入 mpg 会被换算为 mpeg，
//      因此表里必须有 "mpeg" 键；否则会回退成 libx264，而 MPEG-PS 封装无法承载 H.264，
//      会直接报错——这一点在参考实现的矩阵验证中已被实测确认。
//   3) 图片目标必须单独映射编码器：若沿用视频那套推断逻辑，会被错误推断成 libx264，
//      产出的文件无法作为图片打开。
//   4) image2 封装要求输出文件名含序号模板，或显式声明 -update 1；
//      我们的图片输出都是单个文件，因此统一追加 -frames:v 1 -update 1。
//   5) 滤镜链顺序固定为：裁剪 → 缩放 → 旋转/镜像 → 帧率 → 水印 → 字幕 → 变速。
// *****************************************************************************
#include "commandbuilder.h"

// 内核定位器：取 ffmpeg 路径（命令列表首元素）。
#include "ffmpeg_locator.h"
// 能力矩阵：决定是否显式写 -f 封装名。
#include "capabilities.h"
// 硬件加速：自动挑选硬件编码器。
#include "hwaccel.h"
// QFileInfo / QDir：路径与存在性判断。
#include <QFileInfo>
#include <QDir>
// QHash / QPair / QSet：承载各映射表与坐标表。
#include <QHash>
#include <QPair>
#include <QSet>
// QtMath：提供 qPow 等数学函数。
#include <QtMath>
// QDate：自定义命名模板中的 {date}。
#include <QDate>
// QRegularExpression：时间解析中的分段处理。
#include <QRegularExpression>
// QDebug：排障日志。
#include <QDebug>

// ---------------------- 常量表 ----------------------

// 仅含视频的容器：这些容器不能承载音频流，因此需要主动丢弃音频（否则 ffmpeg 会报错）。
static const QStringList videoOnlyContainers = {
    QStringLiteral("gif"), QStringLiteral("apng"), QStringLiteral("ico"), QStringLiteral("bmp"),
    QStringLiteral("jpg"), QStringLiteral("jpeg"), QStringLiteral("png"), QStringLiteral("tiff"),
    QStringLiteral("webp"), QStringLiteral("avif")
};

// 仅含音频的容器：这些容器不能承载视频流。
// 注意：这里刻意不收录 asf —— asf 既能承载 WMA 音频也能承载 WMV 视频，
// 是否丢弃视频应由用户选择的目标扩展名（wma / wmv）决定，而不是一律强制丢弃。
static const QStringList audioOnlyContainers = {
    QStringLiteral("mp3"), QStringLiteral("wav"), QStringLiteral("flac"), QStringLiteral("ogg"),
    QStringLiteral("oga"), QStringLiteral("opus"), QStringLiteral("aac"), QStringLiteral("ac3"),
    QStringLiteral("m4a"), QStringLiteral("wma"), QStringLiteral("amr"), QStringLiteral("aiff")
};

// 需要 moov 前置（faststart）的容器：主要是 MP4 家族，用于网页边下边播。
static const QStringList faststartContainers = {
    QStringLiteral("mp4"), QStringLiteral("m4v"), QStringLiteral("m4a"), QStringLiteral("mov")
};

// 扩展名与 ffmpeg 封装名不一致时的换算表。
static const QHash<QString, QString> containerAlias()
{
    // 静态表：只构造一次。
    static const QHash<QString, QString> table = {
        {QStringLiteral("mkv"), QStringLiteral("matroska")}, // MKV 的真实封装名是 matroska。
        {QStringLiteral("ts"), QStringLiteral("mpegts")}, // TS 的真实封装名是 mpegts。
        {QStringLiteral("m2ts"), QStringLiteral("mpegts")}, // m2ts 是传输流的另一种写法。
        {QStringLiteral("wmv"), QStringLiteral("asf")}, // WMV 是编码名，其封装是 asf。
        {QStringLiteral("mpg"), QStringLiteral("mpeg")}, // MPG 对应 MPEG program stream。
        {QStringLiteral("mpeg"), QStringLiteral("mpeg")}, // mpeg 自身同名。
        {QStringLiteral("vob"), QStringLiteral("mpeg")}, // VOB 也是 MPEG-PS。
        {QStringLiteral("m4a"), QStringLiteral("ipod")}, // m4a 音频封装（AAC）。
        {QStringLiteral("m4v"), QStringLiteral("mp4")}, // m4v 视频封装。
        {QStringLiteral("jpg"), QStringLiteral("image2")}, // 单帧 JPEG 由 image2 封装承载。
        {QStringLiteral("jpeg"), QStringLiteral("image2")}, // 同上。
        {QStringLiteral("png"), QStringLiteral("image2")}, // 单帧 PNG 同样由 image2 承载。
        {QStringLiteral("bmp"), QStringLiteral("image2")}, // 单帧 BMP。
        {QStringLiteral("tif"), QStringLiteral("image2")}, // 单帧 TIFF。
        {QStringLiteral("tiff"), QStringLiteral("image2")}, // 单帧 TIFF。
        {QStringLiteral("oga"), QStringLiteral("ogg")} // Ogg 音频别名。
    };
    // 返回静态表。
    return table;
}

// 图片目标的“扩展名 →（编码器, 封装名）”映射表。
struct ImageTarget
{
    // 与扩展名匹配的图片编码器。
    QString codec;
    // 承载该图片的封装名。
    QString muxer;
};

// 图片目标映射。
static const QHash<QString, ImageTarget> imageTargets()
{
    // 静态表：只构造一次。
    static const QHash<QString, ImageTarget> table = {
        {QStringLiteral("jpg"), {QStringLiteral("mjpeg"), QStringLiteral("image2")}},
        {QStringLiteral("jpeg"), {QStringLiteral("mjpeg"), QStringLiteral("image2")}},
        {QStringLiteral("png"), {QStringLiteral("png"), QStringLiteral("image2")}},
        {QStringLiteral("bmp"), {QStringLiteral("bmp"), QStringLiteral("image2")}},
        {QStringLiteral("tif"), {QStringLiteral("tiff"), QStringLiteral("image2")}},
        {QStringLiteral("tiff"), {QStringLiteral("tiff"), QStringLiteral("image2")}},
        {QStringLiteral("webp"), {QStringLiteral("libwebp"), QStringLiteral("webp")}},
        {QStringLiteral("gif"), {QStringLiteral("gif"), QStringLiteral("gif")}},
        {QStringLiteral("avif"), {QStringLiteral("libaom-av1"), QStringLiteral("avif")}},
        {QStringLiteral("ico"), {QStringLiteral("bmp"), QStringLiteral("ico")}}
    };
    // 返回静态表。
    return table;
}

// 各容器的默认视频编码器（键为“换算后的封装名”）。
static const QHash<QString, QString> defaultVideoCodec()
{
    // 静态表：只构造一次。
    static const QHash<QString, QString> table = {
        {QStringLiteral("mp4"), QStringLiteral("libx264")},
        {QStringLiteral("m4v"), QStringLiteral("libx264")},
        {QStringLiteral("mkv"), QStringLiteral("libx264")},
        {QStringLiteral("mov"), QStringLiteral("libx264")},
        {QStringLiteral("avi"), QStringLiteral("mpeg4")},
        {QStringLiteral("flv"), QStringLiteral("libx264")},
        {QStringLiteral("ts"), QStringLiteral("libx264")},
        {QStringLiteral("webm"), QStringLiteral("libvpx-vp9")},
        {QStringLiteral("gif"), QStringLiteral("gif")},
        {QStringLiteral("wmv"), QStringLiteral("wmv2")},
        {QStringLiteral("mpg"), QStringLiteral("mpeg2video")},
        {QStringLiteral("3gp"), QStringLiteral("libx264")},
        // 以下为“换算后名称”的条目，覆盖同名扩展名被换算后的情况。
        {QStringLiteral("matroska"), QStringLiteral("libx264")}, // mkv → matroska。
        {QStringLiteral("mpegts"), QStringLiteral("libx264")}, // ts → mpegts。
        {QStringLiteral("mpeg"), QStringLiteral("mpeg2video")}, // mpg → mpeg（MPEG-PS 只接受 MPEG 系编码）。
        {QStringLiteral("asf"), QStringLiteral("libx264")}, // wmv → asf（实测 asf 承载 H.264 可正常工作）。
        {QStringLiteral("ipod"), QStringLiteral("libx264")}, // m4a → ipod（音频场景下不会用到视频编码器）。
        {QStringLiteral("image2"), QStringLiteral("mjpeg")}, // 图片兜底编码器。
        {QStringLiteral("webp"), QStringLiteral("libwebp")}, // 图片 WebP。
        {QStringLiteral("avif"), QStringLiteral("libaom-av1")}, // 图片 AVIF。
        {QStringLiteral("ico"), QStringLiteral("bmp")} // 图片 ICO。
    };
    // 返回静态表。
    return table;
}

// 各容器的默认音频编码器（键同样为“换算后的封装名”）。
static const QHash<QString, QString> defaultAudioCodec()
{
    // 静态表：只构造一次。
    static const QHash<QString, QString> table = {
        {QStringLiteral("mp4"), QStringLiteral("aac")},
        {QStringLiteral("m4v"), QStringLiteral("aac")},
        {QStringLiteral("mkv"), QStringLiteral("aac")},
        {QStringLiteral("mov"), QStringLiteral("aac")},
        {QStringLiteral("m4a"), QStringLiteral("aac")},
        {QStringLiteral("avi"), QStringLiteral("libmp3lame")},
        {QStringLiteral("flv"), QStringLiteral("aac")},
        {QStringLiteral("ts"), QStringLiteral("aac")},
        {QStringLiteral("webm"), QStringLiteral("libopus")},
        {QStringLiteral("mp3"), QStringLiteral("libmp3lame")},
        {QStringLiteral("wav"), QStringLiteral("pcm_s16le")},
        {QStringLiteral("flac"), QStringLiteral("flac")},
        {QStringLiteral("ogg"), QStringLiteral("libvorbis")},
        {QStringLiteral("oga"), QStringLiteral("libvorbis")},
        {QStringLiteral("opus"), QStringLiteral("libopus")},
        {QStringLiteral("aac"), QStringLiteral("aac")},
        {QStringLiteral("ac3"), QStringLiteral("ac3")},
        {QStringLiteral("wma"), QStringLiteral("wmav2")},
        {QStringLiteral("amr"), QStringLiteral("libopencore_amrnb")},
        {QStringLiteral("aiff"), QStringLiteral("pcm_s16be")},
        {QStringLiteral("mpg"), QStringLiteral("mp2")},
        // 换算后名称的条目。
        {QStringLiteral("matroska"), QStringLiteral("aac")}, // mkv → matroska。
        {QStringLiteral("mpegts"), QStringLiteral("aac")}, // ts → mpegts。
        {QStringLiteral("mpeg"), QStringLiteral("mp2")}, // mpg → mpeg（MPEG-PS 使用 MP2 音频）。
        {QStringLiteral("asf"), QStringLiteral("aac")}, // wmv → asf。
        {QStringLiteral("ipod"), QStringLiteral("aac")}, // m4a → ipod。
        {QStringLiteral("image2"), QString()} // 图片封装不带音频。
    };
    // 返回静态表。
    return table;
}

// 质量档位 → x264 系列 CRF 的映射。
static const QHash<QString, QString> qualityToCrf()
{
    // 静态表：只构造一次。
    static const QHash<QString, QString> table = {
        {QStringLiteral("lossless"), QStringLiteral("0")},
        {QStringLiteral("high"), QStringLiteral("20")},
        {QStringLiteral("medium"), QStringLiteral("23")},
        {QStringLiteral("low"), QStringLiteral("28")}
    };
    // 返回静态表。
    return table;
}

// 质量档位 → 音频码率的映射。
static const QHash<QString, QString> qualityToAbr()
{
    // 静态表：只构造一次。
    static const QHash<QString, QString> table = {
        {QStringLiteral("lossless"), QStringLiteral("320k")},
        {QStringLiteral("high"), QStringLiteral("192k")},
        {QStringLiteral("medium"), QStringLiteral("128k")},
        {QStringLiteral("low"), QStringLiteral("96k")}
    };
    // 返回静态表。
    return table;
}

// 质量档位 → 图片质量（-q:v，数值越小质量越高）的映射。
static const QHash<QString, QString> qualityToImageQv()
{
    // 静态表：只构造一次。
    static const QHash<QString, QString> table = {
        {QStringLiteral("lossless"), QStringLiteral("1")},
        {QStringLiteral("high"), QStringLiteral("2")},
        {QStringLiteral("medium"), QStringLiteral("5")},
        {QStringLiteral("low"), QStringLiteral("12")}
    };
    // 返回静态表。
    return table;
}

// ---------------------- 基础辅助函数 ----------------------

// 封装名换算。
QString resolveContainer(const QString& targetFormat)
{
    // 规整输入：去点、去空白、转小写。
    QString ext = targetFormat.trimmed().toLower();
    // 去掉开头的点号。
    if (ext.startsWith(QLatin1Char('.'))) { ext = ext.mid(1); }
    // 查换算表；没有记录时原样返回（多数情况下扩展名与封装名一致）。
    return containerAlias().value(ext, ext);
}

// 提取安全扩展名。
QString safeExtension(const QString& targetFormat)
{
    // 规整输入：去点、去空白、转小写；兜底 mp4。
    QString ext = targetFormat.trimmed().toLower();
    // 去掉开头的点号。
    if (ext.startsWith(QLatin1Char('.'))) { ext = ext.mid(1); }
    // 只保留字母与数字，过滤掉可能导致奇怪文件名的字符。
    QString filtered;
    // 逐字符筛选。
    for (const QChar c : ext)
    {
        // 是字母或数字才保留。
        if (c.isLetterOrNumber()) { filtered.append(c); }
    }
    // 结果为空时兜底 mp4。
    return filtered.isEmpty() ? QStringLiteral("mp4") : filtered;
}

// 向下取偶数。
int evenValue(int value)
{
    // 最小为 2（宽高为 0 或负数在视频编码中无意义）。
    const int v = qMax(2, value);
    // 偶数直接返回，奇数减一。
    return (v % 2 == 0) ? v : (v - 1);
}

// 时间解析。
bool parseTimeValue(const QString& text, double* secondsOut)
{
    // 输出参数先归零，保证失败时调用方拿到的值确定。
    if (secondsOut) { *secondsOut = 0.0; }
    // 去空白。
    const QString s = text.trimmed();
    // 空字符串视为无效。
    if (s.isEmpty()) { return false; }
    // 含冒号时按“时:分:秒[.毫秒]”解析。
    if (s.contains(QLatin1Char(':')))
    {
        // 按冒号拆分。
        const QStringList parts = s.split(QLatin1Char(':'));
        // 最多支持三段。
        if (parts.size() > 3) { return false; }
        // 累加总秒数。
        double total = 0.0;
        // 逐段换算：第 i 段权重为 60 的 (段数-1-i) 次方。
        for (int i = 0; i < parts.size(); ++i)
        {
            // 单段取值（空段按 0 处理）。
            bool ok = false;
            // 转换该段。
            const double seg = parts.at(i).trimmed().isEmpty() ? 0.0 : parts.at(i).trimmed().toDouble(&ok);
            // 该段不是合法数字则整体解析失败（避免把 "12:ab:cd" 误判为有效时间）。
            if (!ok && !parts.at(i).trimmed().isEmpty()) { return false; }
            // 按权重累加。
            total += seg * qPow(60.0, double(parts.size() - 1 - i));
        }
        // 回填结果。
        if (secondsOut) { *secondsOut = total; }
        // 解析成功。
        return true;
    }
    // 纯数字按秒解析。
    bool ok = false;
    // 执行转换。
    const double v = s.toDouble(&ok);
    // 失败则返回 false。
    if (!ok) { return false; }
    // 回填结果。
    if (secondsOut) { *secondsOut = v; }
    // 成功。
    return true;
}

// 时间格式化。
QString formatTimeValue(double seconds)
{
    // 负值裁剪为 0。
    const double total = qMax(0.0, seconds);
    // 小时部分。
    const int hours = int(total / 3600.0);
    // 分钟部分。
    const int minutes = int((total - hours * 3600.0) / 60.0);
    // 秒部分（保留三位小数，毫秒精度足够）。
    const double secs = total - hours * 3600.0 - minutes * 60.0;
    // 拼接成 ffmpeg 可接受的形式。
    return QStringLiteral("%1:%2:%3")
           .arg(hours, 2, 10, QLatin1Char('0'))
           .arg(minutes, 2, 10, QLatin1Char('0'))
           .arg(secs, 6, 'f', 3, QLatin1Char('0'));
}

// drawtext 文本转义。
static QString escapeFilterText(const QString& text)
{
    // 结果字符串。
    QString s = text;
    // 第一步：反斜杠最先转义（否则后续步骤产生的反斜杠会被二次转义）。
    s.replace(QStringLiteral("\\"), QStringLiteral("\\\\"));
    // 第二步：冒号是滤镜参数分隔符。
    s.replace(QStringLiteral(":"), QStringLiteral("\\:"));
    // 第三步：单引号（filtergraph 的引号规则）。
    s.replace(QStringLiteral("'"), QStringLiteral("\\'"));
    // 第四步：百分号（drawtext 的 %{...} 文本扩展语法会误解析）。
    s.replace(QStringLiteral("%"), QStringLiteral("\\%"));
    // 第五步：逗号在 filtergraph 中分隔滤镜。
    s.replace(QStringLiteral(","), QStringLiteral("\\,"));
    // 返回转义结果。
    return s;
}

// 文件路径转义为可安全嵌入 filtergraph 的形式。
static QString escapePathForFilter(const QString& path)
{
    // 反斜杠统一换成正斜杠（ffmpeg 在 Windows 上接受正斜杠）。
    QString p = path;
    // 执行替换。
    p.replace(QStringLiteral("\\"), QStringLiteral("/"));
    // 冒号必须转义，尤其是 Windows 盘符那一颗（C: → C\:）。
    p.replace(QStringLiteral(":"), QStringLiteral("\\:"));
    // 返回结果。
    return p;
}

// 水印位置关键字 → overlay 坐标表达式。
static QString positionExpr(const QString& pos)
{
    // 统一小写去空白。
    QString key = pos.trimmed().toLower();
    // 归一化常见的长写法。
    if (key == QStringLiteral("top-right")) { key = QStringLiteral("tr"); }
    // 左上。
    if (key == QStringLiteral("top-left")) { key = QStringLiteral("tl"); }
    // 右下。
    if (key == QStringLiteral("bottom-right")) { key = QStringLiteral("br"); }
    // 左下。
    if (key == QStringLiteral("bottom-left")) { key = QStringLiteral("bl"); }
    // 上中。
    if (key == QStringLiteral("top")) { key = QStringLiteral("tc"); }
    // 下中。
    if (key == QStringLiteral("bottom")) { key = QStringLiteral("bc"); }
    // 坐标映射表（W/H 为底图宽高，w/h 为水印宽高，留 10 像素边距）。
    QHash<QString, QPair<QString, QString>> table;
    // 左上。
    table.insert(QStringLiteral("tl"), {QStringLiteral("10"), QStringLiteral("10")});
    // 右上。
    table.insert(QStringLiteral("tr"), {QStringLiteral("W-w-10"), QStringLiteral("10")});
    // 左下。
    table.insert(QStringLiteral("bl"), {QStringLiteral("10"), QStringLiteral("H-h-10")});
    // 右下。
    table.insert(QStringLiteral("br"), {QStringLiteral("W-w-10"), QStringLiteral("H-h-10")});
    // 上中。
    table.insert(QStringLiteral("tc"), {QStringLiteral("(W-w)/2"), QStringLiteral("10")});
    // 下中。
    table.insert(QStringLiteral("bc"), {QStringLiteral("(W-w)/2"), QStringLiteral("H-h-10")});
    // 正中。
    table.insert(QStringLiteral("center"), {QStringLiteral("(W-w)/2"), QStringLiteral("(H-h)/2")});
    // 取坐标对；未知位置回退到右上角（最不容易遮挡主体）。
    const QPair<QString, QString> xy = table.value(key, table.value(QStringLiteral("tr")));
    // 返回 "x:y" 形式。
    return xy.first + QLatin1Char(':') + xy.second;
}

// 计算文字水印字号。
static int textWatermarkFontSize(int sourceHeight)
{
    // 画面高度未知时用固定兜底字号。
    if (sourceHeight <= 0) { return 24; }
    // 按画面高度的 5% 取字号，并限制在 12 ~ 96 之间（太大遮挡画面，太小看不清）。
    return qBound(12, int(sourceHeight * 0.05), 96);
}

// 查找可用于 drawtext 的字体文件。
static QString findFontFile()
{
    // 优先使用 Arial（几乎所有 Windows 都自带）。
    const QStringList candidates = {
        QStringLiteral("C:/Windows/Fonts/arial.ttf"),
        QStringLiteral("C:/Windows/Fonts/segoeui.ttf"),
        QStringLiteral("C:/Windows/Fonts/msyh.ttc"),
        QStringLiteral("C:/Windows/Fonts/tahoma.ttf")
    };
    // 逐个检查是否存在。
    for (const QString& p : candidates)
    {
        // 文件存在即采用。
        if (QFileInfo::exists(p)) { return p; }
    }
    // 一个都没有时返回空字符串（此时 drawtext 由 ffmpeg 自行选择默认字体）。
    return QString();
}

// ---------------------- 输出路径计算 ----------------------

// 计算输出路径。
QString computeOutputPath(const TranscodeSettings& st, bool overwrite)
{
    // 源文件绝对路径。
    const QString srcAbs = QFileInfo(st.source).absoluteFilePath();
    // 源目录。
    const QString srcDir = QFileInfo(srcAbs).absolutePath();
    // 源文件名（不含任何扩展名的主名）。
    const QString base = QFileInfo(srcAbs).completeBaseName();
    // 目标扩展名。
    const QString ext = safeExtension(st.targetFormat);
    // 输出目录：用户指定优先，否则与源文件同目录。
    const QString outDir = st.outputDir.trimmed().isEmpty()
                               ? srcDir
                               : QFileInfo(st.outputDir).absoluteFilePath();

    // ---- 生成主名（可能含 {index} 占位）----
    QString stem;
    // 情况 A：用户显式指定了输出文件名。
    if (!st.outputName.trimmed().isEmpty())
    {
        // 去空白。
        stem = st.outputName.trimmed();
        // 若用户没写扩展名则自动补上目标扩展名。
        if (QFileInfo(stem).suffix().isEmpty())
        {
            // 补扩展名。
            stem += QLatin1Char('.') + ext;
        }
    }
    else if (st.namingMode == QStringLiteral("suffix"))
    {
        // 情况 B：原名 + 目标格式后缀（如 movie_mp4.mp4）。
        stem = base + QLatin1Char('_') + ext + QLatin1Char('.') + ext;
    }
    else if (st.namingMode == QStringLiteral("custom"))
    {
        // 情况 C：自定义模板。
        QString t = st.nameTemplate;
        // {name} 替换为源主名。
        t.replace(QStringLiteral("{name}"), base);
        // {ext} 替换为目标扩展名。
        t.replace(QStringLiteral("{ext}"), ext);
        // {date} 替换为当天日期。
        t.replace(QStringLiteral("{date}"), QDate::currentDate().toString(QStringLiteral("yyyyMMdd")));
        // 记录模板（{index} 稍后按序号替换）。
        stem = t;
        // 模板若没带扩展名则补上。
        if (!stem.toLower().endsWith(QLatin1Char('.') + ext))
        {
            // 补扩展名。
            stem += QLatin1Char('.') + ext;
        }
    }
    else
    {
        // 情况 D（默认 replace）：直接替换扩展名。
        stem = base + QLatin1Char('.') + ext;
    }

    // ---- 防覆盖：允许覆盖时直接返回；否则若目标已存在则自动追加序号 ----
    // 模板中含 {index} 占位符的情形：序号从 1 开始递增。
    if (stem.contains(QStringLiteral("{index}")))
    {
        // 最多尝试 9999 次，避免极端情况下死循环。
        for (int i = 1; i < 10000; ++i)
        {
            // 用当前序号替换占位符。
            QString name = stem;
            // 执行替换。
            name.replace(QStringLiteral("{index}"), QString::number(i));
            // 拼出完整路径。
            const QString p = QDir(outDir).filePath(name);
            // 允许覆盖，或目标尚不存在，则采用该路径。
            if (overwrite || !QFileInfo::exists(p)) { return p; }
        }
        // 极端情况下返回最后一个候选（调用方会因覆盖失败而报错，属于可接受结果）。
        QString name = stem;
        // 使用最大序号。
        name.replace(QStringLiteral("{index}"), QStringLiteral("9999"));
        // 返回路径。
        return QDir(outDir).filePath(name);
    }

    // 无占位符的情形：先试原路径。
    QString primary = QDir(outDir).filePath(stem);
    // 允许覆盖或路径未被占用则直接返回。
    if (overwrite || !QFileInfo::exists(primary)) { return primary; }
    // 拆分主名与后缀，以便把序号插在扩展名之前（保持扩展名不变）。
    const QString stemBase = stem.left(stem.length() - QFileInfo(stem).suffix().length() - 1);
    // 后缀（含点号）。
    const QString suffix = QLatin1Char('.') + QFileInfo(stem).suffix();
    // 递增序号。
    for (int i = 1; i < 10000; ++i)
    {
        // 生成候选名。
        const QString candidate = stemBase + QLatin1Char('_') + QString::number(i) + suffix;
        // 拼出完整路径。
        const QString p = QDir(outDir).filePath(candidate);
        // 未被占用则返回。
        if (!QFileInfo::exists(p)) { return p; }
    }
    // 全部被占用时返回原路径（交由 ffmpeg 的 -y 处理）。
    return primary;
}

// ---------------------- 滤镜链 ----------------------

// 视频滤镜链。
QString buildFilterChain(const TranscodeSettings& st, bool withTextWatermark, int sourceHeight)
{
    // 收集滤镜片段。
    QStringList parts;

    // ---- 1. 裁剪（先裁剪，后续缩放基于裁剪后的画面，语义更直观）----
    const QString crop = st.crop.trimmed();
    // 仅当用户确实填写了裁剪参数时处理。
    if (!crop.isEmpty())
    {
        // 支持 "w:h:x:y" 或 "w:h"（后者默认居中）。
        const QStringList seg = crop.split(QLatin1Char(':'));
        // 四段：宽高高宽全给出。
        if (seg.size() >= 4)
        {
            // 直接使用用户给出的坐标。
            parts << QStringLiteral("crop=%1:%2:%3:%4").arg(seg.at(0), seg.at(1), seg.at(2), seg.at(3));
        }
        else if (seg.size() == 2)
        {
            // 只给宽高：用 (iw-w)/2 与 (ih-h)/2 居中裁剪。
            parts << QStringLiteral("crop=%1:%2:(iw-%1)/2:(ih-%2)/2").arg(seg.at(0), seg.at(1));
        }
    }

    // ---- 2. 缩放（宽高一律向下取偶数，避免编码器因奇数尺寸报错）----
    if (st.width > 0 && st.height > 0)
    {
        // 目标宽高都给定。
        const int w = evenValue(st.width);
        // 目标高。
        const int h = evenValue(st.height);
        // 按缩放模式选择滤镜组合。
        if (st.scaleMode == QStringLiteral("fill"))
        {
            // 填充模式：等比放大到覆盖目标框，再居中裁剪掉多余部分。
            parts << QStringLiteral("scale=%1:%2:force_original_aspect_ratio=increase,crop=%1:%2").arg(w).arg(h);
        }
        else if (st.scaleMode == QStringLiteral("stretch"))
        {
            // 拉伸模式：不保持比例直接拉伸。
            parts << QStringLiteral("scale=%1:%2").arg(w).arg(h);
        }
        else
        {
            // 适应模式（默认）：等比缩小到完整放入目标框，再用 pad 补齐黑边。
            parts << QStringLiteral("scale=%1:%2:force_original_aspect_ratio=decrease,pad=%1:%2:(ow-iw)/2:(oh-ih)/2").
                     arg(w).arg(h);
        }
    }
    else if (st.width > 0)
    {
        // 只给宽度：高度自动（-2 保证为偶数）。
        parts << QStringLiteral("scale=%1:-2").arg(evenValue(st.width));
    }
    else if (st.height > 0)
    {
        // 只给高度：宽度自动（-2 保证为偶数）。
        parts << QStringLiteral("scale=-2:%1").arg(evenValue(st.height));
    }

    // ---- 3. 旋转与镜像（优先用 transpose 滤镜：精确、无插值损失）----
    // 归一化角度，避免负数与超过 360 的值造成判断遗漏。
    const int rot = ((st.rotate % 360) + 360) % 360;
    // 顺时针 90°。
    if (rot == 90)
    {
        // transpose=1 表示顺时针旋转 90 度。
        parts << QStringLiteral("transpose=1");
    }
    else if (rot == 180)
    {
        // 180° 用两次顺时针 90° 叠加实现（比 transpose=2,transpose=2 更兼容）。
        parts << QStringLiteral("transpose=2,transpose=2");
    }
    else if (rot == 270)
    {
        // transpose=2 表示逆时针旋转 90°（等价于顺时针 270°）。
        parts << QStringLiteral("transpose=2");
    }
    // 镜像：水平。
    if (st.flip == QStringLiteral("h"))
    {
        // 水平翻转。
        parts << QStringLiteral("hflip");
    }
    else if (st.flip == QStringLiteral("v"))
    {
        // 垂直翻转。
        parts << QStringLiteral("vflip");
    }
    else if (st.flip == QStringLiteral("both"))
    {
        // 双向翻转。
        parts << QStringLiteral("hflip,vflip");
    }

    // ---- 4. 帧率 ----
    if (!st.fps.trimmed().isEmpty())
    {
        // 直接把用户给出的值透传给 fps 滤镜（支持 "25" 与 "30000/1001" 两种写法）。
        parts << QStringLiteral("fps=%1").arg(st.fps.trimmed());
    }

    // ---- 5. 水印（文字水印位置固定在此槽位；图片水印在 filter_complex 中处理，但同样位于此顺序点）----
    if (withTextWatermark && !st.watermarkText.trimmed().isEmpty())
    {
        // 水印字号。
        const int fontSize = textWatermarkFontSize(sourceHeight);
        // 不透明度（限制在 0 ~ 1）。
        const double opacity = qBound(0.0, st.watermarkOpacity, 1.0);
        // 位置表达式：drawtext 使用 x/y 表达式，这里直接复用同一套坐标表（把 W/w 换成 w/h 语义）。
        // 说明：drawtext 中主画面宽高是 w/h，文字宽高是 text_w/text_h。
        QString pos = st.watermarkPos.trimmed().toLower();
        // 归一化简写。
        if (pos == QStringLiteral("top-right")) { pos = QStringLiteral("tr"); }
        // 左上。
        if (pos == QStringLiteral("top-left")) { pos = QStringLiteral("tl"); }
        // 右下。
        if (pos == QStringLiteral("bottom-right")) { pos = QStringLiteral("br"); }
        // 左下。
        if (pos == QStringLiteral("bottom-left")) { pos = QStringLiteral("bl"); }
        // 上中。
        if (pos == QStringLiteral("top")) { pos = QStringLiteral("tc"); }
        // 下中。
        if (pos == QStringLiteral("bottom")) { pos = QStringLiteral("bc"); }
        // 坐标表（drawtext 的 w/h 表示主画面宽高，text_w/text_h 表示文字宽高）。
        QHash<QString, QPair<QString, QString>> table;
        // 左上。
        table.insert(QStringLiteral("tl"), {QStringLiteral("10"), QStringLiteral("10")});
        // 右上。
        table.insert(QStringLiteral("tr"), {QStringLiteral("w-text_w-10"), QStringLiteral("10")});
        // 左下。
        table.insert(QStringLiteral("bl"), {QStringLiteral("10"), QStringLiteral("h-text_h-10")});
        // 右下。
        table.insert(QStringLiteral("br"), {QStringLiteral("w-text_w-10"), QStringLiteral("h-text_h-10")});
        // 上中。
        table.insert(QStringLiteral("tc"), {QStringLiteral("(w-text_w)/2"), QStringLiteral("10")});
        // 下中。
        table.insert(QStringLiteral("bc"), {QStringLiteral("(w-text_w)/2"), QStringLiteral("h-text_h-10")});
        // 正中。
        table.insert(QStringLiteral("center"), {QStringLiteral("(w-text_w)/2"), QStringLiteral("(h-text_h)/2")});
        // 取坐标（未知位置回退右上）。
        const QPair<QString, QString> xy = table.value(pos, table.value(QStringLiteral("tr")));
        // 拼接 drawtext 描述：冒号是参数分隔符，坐标表达式里的斜杠无需转义。
        QString draw = QStringLiteral("drawtext=");
        // 若找到可用字体文件则显式指定 fontfile，避免 ffmpeg 找不到默认字体而报错。
        const QString fontFile = findFontFile();
        // 有字体文件才追加 fontfile 参数。
        if (!fontFile.isEmpty())
        {
            // 路径需要转义为 filtergraph 安全形式。
            draw += QStringLiteral("fontfile='%1':").arg(escapePathForFilter(fontFile));
        }
        // 文本内容（已转义）。
        draw += QStringLiteral("text='%1':").arg(escapeFilterText(st.watermarkText.trimmed()));
        // 字号。
        draw += QStringLiteral("fontsize=%1:").arg(fontSize);
        // 颜色与不透明度（白色半透明最通用）。
        draw += QStringLiteral("fontcolor=white@%1:").arg(QString::number(opacity, 'f', 2));
        // 添加 1 像素阴影，保证在浅色画面上也看得清。
        draw += QStringLiteral("shadowcolor=black@%1:shadowx=1:shadowy=1:").arg(QString::number(opacity * 0.6, 'f', 2));
        // x 坐标。
        draw += QStringLiteral("x=%1:").arg(xy.first);
        // y 坐标。
        draw += QStringLiteral("y=%1").arg(xy.second);
        // 追加到滤镜链。
        parts << draw;
    }

    // ---- 6. 字幕（在 filter_complex 中处理，位置固定在水印之后）----
    // 本函数只负责“简单链”的部分，字幕由于需要单独的滤镜节点，由 buildCommand 处理。

    // ---- 7. 变速（放在最后，保证前面的裁剪/缩放/帧率都基于“原始时间轴”计算）----
    const double sp = st.speed;
    // 仅在速度明显不等于 1 时追加 setpts，避免无谓的重编码滤镜。
    if (qAbs(sp - 1.0) > 1e-6 && sp > 0.0)
    {
        // setpts 的系数是“原时间戳 × (1/速度)”。
        parts << QStringLiteral("setpts=%1*PTS").arg(QString::number(1.0 / sp, 'f', 6));
    }

    // 用逗号连接成完整链；无任何滤镜时返回空串。
    return parts.join(QLatin1Char(','));
}

// 音频滤镜链。
QString buildAudioFilterChain(const TranscodeSettings& st)
{
    // 收集滤镜片段。
    QStringList parts;
    // ---- 音量 ----
    const QString vol = st.volume.trimmed();
    // 用户填写了音量参数才追加（允许 "1.5"（倍数）或 "-6dB"（分贝））。
    if (!vol.isEmpty())
    {
        // 追加音量滤镜。
        parts << QStringLiteral("volume=%1").arg(vol);
    }
    // ---- 变速（音频侧用 atempo）----
    const double sp = st.speed;
    // 仅在速度明显不等于 1 时处理。
    if (qAbs(sp - 1.0) > 1e-6 && sp > 0.0)
    {
        // atempo 单次仅支持 0.5 ~ 2.0，超出范围必须串联多次。
        double remaining = sp;
        // 大于 2 的倍率：反复除以 2。
        while (remaining > 2.0 + 1e-9)
        {
            // 追加一个 2.0 倍的 atempo。
            parts << QStringLiteral("atempo=2.0");
            // 剩余倍率减半。
            remaining /= 2.0;
        }
        // 小于 0.5 的倍率：反复除以 0.5。
        while (remaining < 0.5 - 1e-9)
        {
            // 追加一个 0.5 倍的 atempo。
            parts << QStringLiteral("atempo=0.5");
            // 剩余倍率乘 2。
            remaining /= 0.5;
        }
        // 处理剩下的倍率（保留 6 位小数足够精确）。
        parts << QStringLiteral("atempo=%1").arg(QString::number(remaining, 'f', 6));
    }
    // 逗号连接；无滤镜时返回空串。
    return parts.join(QLatin1Char(','));
}

// ---------------------- 视频编码器选择 ----------------------

// 内部辅助：决定最终使用的视频编码器（含硬件加速与自动降级逻辑）。
static QString chooseVideoEncoder(const QString& container, const QString& userCodec,
                                  const TranscodeSettings& st, bool hasVideo)
{
    // 用户显式指定的编码器优先级最高。
    QString codec = userCodec.trimmed();
    // 特殊标记：预设里的 auto_hw_h264 表示“自动挑一个可用的硬件 H.264 编码器”。
    if (codec == QStringLiteral("auto_hw_h264"))
    {
        // 尝试挑选硬件编码器。
        const QString picked = HwAccel::pickH264(st.hwaccelVendor);
        // 若挑不到则回退 libx264（保证功能可用，而不是直接报错）。
        if (picked.isEmpty())
        {
            // 记录降级日志。
            qInfo("[builder] no hardware H.264 encoder, falling back to libx264");
            // 回退软件编码。
            return QStringLiteral("libx264");
        }
        // 返回挑到的硬件编码器。
        return picked;
    }
    // 用户的硬件加速策略为“指定编码器名”时直接采用。
    if (!st.hwaccel.isEmpty() && st.hwaccel != QStringLiteral("auto") && st.hwaccel != QStringLiteral("none"))
    {
        // 直接返回该编码器名。
        return st.hwaccel;
    }
    // 未指定编码器：用容器默认值（图片目标在调用前已被填好对应的图片编码器）。
    if (codec.isEmpty())
    {
        // 查默认表；兜底 libx264。
        codec = defaultVideoCodec().value(container, hasVideo ? QStringLiteral("libx264") : QString());
    }
    // 若用户开启了自动硬件加速且编码器是 libx264/libx265，则尝试替换为硬件版本。
    if (st.hwaccel == QStringLiteral("auto")
        && (codec == QStringLiteral("libx264") || codec == QStringLiteral("libx265")))
    {
        // 取对应族的硬件编码器。
        const QString picked = (codec == QStringLiteral("libx264"))
                                   ? HwAccel::pickH264(st.hwaccelVendor)
                                   : HwAccel::pickHevc(st.hwaccelVendor);
        // 挑到就替换。
        if (!picked.isEmpty())
        {
            // 记录替换日志。
            qInfo("[builder] auto hardware acceleration: %s -> %s", qPrintable(codec), qPrintable(picked));
            // 返回硬件编码器。
            return picked;
        }
    }
    // 返回最终编码器。
    return codec;
}

// 判断是否为硬件编码器。
static bool isHardwareEncoder(const QString& codec)
{
    // 按 ffmpeg 的命名约定判断：硬件编码器的名字带固定后缀。
    return codec.endsWith(QStringLiteral("_nvenc"))
        || codec.endsWith(QStringLiteral("_qsv"))
        || codec.endsWith(QStringLiteral("_amf"))
        || codec.endsWith(QStringLiteral("_vaapi"))
        || codec.endsWith(QStringLiteral("_videotoolbox"))
        || codec.endsWith(QStringLiteral("_d3d12va"));
}

// ---------------------- 命令构建 ----------------------

// 构建完整命令。
QStringList buildCommand(const TranscodeSettings& st, const MediaInfo* info, const QString& outPath)
{
    // 取 ffmpeg 路径。
    const QString exe = FFmpegLocator::instance().ffmpegPath();
    // 目标扩展名。
    const QString ext = safeExtension(st.targetFormat);
    // 换算后的封装名。
    const QString container = resolveContainer(st.targetFormat);
    // 图片目标信息（不是图片目标时 codec 为空）。
    const ImageTarget imgTarget = imageTargets().value(ext, ImageTarget{QString(), QString()});

    // 复制一份设置，避免污染调用方对象（预设与图片编码器的补写都在副本上进行）。
    TranscodeSettings local = st;
    // 若目标是图片且用户没指定视频编码器，则填与该图片格式匹配的编码器。
    if (!imgTarget.codec.isEmpty() && local.videoCodec.trimmed().isEmpty())
    {
        // 补写图片编码器。
        local.videoCodec = imgTarget.codec;
    }

    // 源是否含视频/音频（info 为空时保守按“都有”处理，交由 ffmpeg 自行判断）。
    const bool hasVideoSrc = (info == nullptr) ? true : info->hasVideo();
    // 源是否含音频。
    const bool hasAudioSrc = (info == nullptr) ? true : info->hasAudio();
    // 根据容器类型决定最终是否保留视频/音频。
    bool keepVideo = hasVideoSrc && !st.dropVideo;
    // 音频同理。
    bool keepAudio = hasAudioSrc && !st.dropAudio;
    // 纯图片容器强制丢弃音频。
    if (videoOnlyContainers.contains(ext)) { keepAudio = false; }
    // 纯音频容器强制丢弃视频。
    if (audioOnlyContainers.contains(ext)) { keepVideo = false; }

    // 水印/字幕路径的存在性（路径不存在时静默忽略，避免命令里出现无效输入）。
    const bool hasWatermarkImage = keepVideo
        && !st.watermarkPath.trimmed().isEmpty()
        && QFileInfo::exists(st.watermarkPath.trimmed());
    // 字幕文件存在性。
    const bool hasSubtitle = keepVideo
        && !st.subtitlePath.trimmed().isEmpty()
        && QFileInfo::exists(st.subtitlePath.trimmed());
    // 文字水印是否需要（有内容即需要）。
    const bool hasTextWatermark = keepVideo && !st.watermarkText.trimmed().isEmpty();
    // 是否必须使用 filter_complex（图片水印与字幕都需要“多输入/多标签”，普通 -vf 无法表达）。
    const bool needComplex = hasWatermarkImage || hasSubtitle;

    // 源主视频流高度（用于推算文字水印字号）；未知时为 0。
    const int srcHeight = (info != nullptr && info->hasVideo()) ? info->primaryVideo().height : 0;

    // 开始组装参数列表，首元素是 ffmpeg 可执行文件路径。
    QStringList args;
    // 首元素。
    args << exe;
    // 全局选项：-hide_banner 去掉版权横幅，-y 允许覆盖（不覆盖的诉求已由输出路径自动加序号满足）。
    args << QStringLiteral("-hide_banner") << QStringLiteral("-y");

    // ---- 输入侧时间裁剪（放在 -i 之前做快速定位，速度更快）----
    double startSec = 0.0;
    // 解析起始时间。
    const bool hasStart = parseTimeValue(st.start, &startSec);
    // 只有起始时间明确大于 0 时才加 -ss，避免把普通转码也变成“快速定位”。
    if (hasStart && startSec > 0.0)
    {
        // 追加起始时间。
        args << QStringLiteral("-ss") << formatTimeValue(startSec);
    }

    // ---- 输入文件 ----
    args << QStringLiteral("-i") << QFileInfo(st.source).absoluteFilePath();

    // ---- 图片水印作为第二输入 ----
    if (hasWatermarkImage)
    {
        // 追加水印图片输入。
        args << QStringLiteral("-i") << QFileInfo(st.watermarkPath.trimmed()).absoluteFilePath();
    }
    // ---- 外挂字幕作为额外输入（可选）----
    if (hasSubtitle)
    {
        // 追加字幕输入。
        args << QStringLiteral("-i") << QFileInfo(st.subtitlePath.trimmed()).absoluteFilePath();
    }

    // ---- 输出侧时长裁剪 ----
    double durSec = 0.0;
    // 解析时长。
    const bool hasDur = parseTimeValue(st.duration, &durSec);
    // 解析结束时间。
    double endSec = 0.0;
    // 结束时间是否有效。
    const bool hasEnd = parseTimeValue(st.end, &endSec);
    // 明确时长优先。
    if (hasDur && durSec > 0.0)
    {
        // 追加时长限制。
        args << QStringLiteral("-t") << formatTimeValue(durSec);
    }
    else if (hasEnd && endSec > 0.0)
    {
        // 给出的是绝对结束时间：换算成相对于起始点的时长。
        const double base = (hasStart && startSec > 0.0) ? startSec : 0.0;
        // 计算跨度。
        const double span = endSec - base;
        // 跨度为正才追加。
        if (span > 0.0)
        {
            // 追加时长限制。
            args << QStringLiteral("-t") << formatTimeValue(span);
        }
    }

    // ---- 流选择 ----
    if (st.streamMode == QStringLiteral("first"))
    {
        // 只映射第一条视频与第一条音频，自动丢弃多音轨/多字幕。
        // 注意：若本次使用 filter_complex，视频流由滤镜输出标签负责映射，这里必须跳过视频映射。
        if (keepVideo && !needComplex)
        {
            // 映射首个视频流。
            args << QStringLiteral("-map") << QStringLiteral("0:v:0");
        }
        // 映射首个音频流。
        if (keepAudio)
        {
            // 追加音频映射。
            args << QStringLiteral("-map") << QStringLiteral("0:a:0");
        }
    }
    else if (st.streamMode == QStringLiteral("custom"))
    {
        // 用户显式指定流序号；-1 表示该类型不映射。
        if (keepVideo && !needComplex && st.videoIndex >= 0)
        {
            // 映射指定视频流。
            args << QStringLiteral("-map") << QStringLiteral("0:%1").arg(st.videoIndex);
        }
        // 映射指定音频流。
        if (keepAudio && st.audioIndex >= 0)
        {
            // 追加音频映射。
            args << QStringLiteral("-map") << QStringLiteral("0:%1").arg(st.audioIndex);
        }
        // 映射指定字幕流。
        if (st.subtitleIndex >= 0)
        {
            // 追加字幕映射。
            args << QStringLiteral("-map") << QStringLiteral("0:%1").arg(st.subtitleIndex);
        }
    }
    // 说明：streamMode 为 auto 时不加 -map，交给 ffmpeg 的默认策略（通常取第一条视频 + 第一条音频）。

    // ---- 明确丢弃不需要的流 ----
    if (!keepVideo)
    {
        // -vn 表示不输出视频流。
        args << QStringLiteral("-vn");
    }
    // 不需要音频时同理。
    if (!keepAudio)
    {
        // -an 表示不输出音频流。
        args << QStringLiteral("-an");
    }

    // ---- 视频编码 ----
    if (keepVideo)
    {
        // 决定编码器。
        QString vcodec = chooseVideoEncoder(container, local.videoCodec, local, true);
        // copy 模式：用户明确要求且编码器就是 copy 时，直接流复制。
        if (st.copyMode && local.videoCodec == QStringLiteral("copy"))
        {
            // 使用流复制。
            vcodec = QStringLiteral("copy");
        }
        // 安全护栏：流复制与任何视频滤镜/水印/字幕互斥（压缩后的流无法直接施加滤镜）。
        // 这里主动把 copy 降级为“重新编码”，保证任务能跑完（宁可慢，也不报错）。
        if (vcodec == QStringLiteral("copy")
            && (!buildFilterChain(local, hasTextWatermark, srcHeight).isEmpty() || needComplex))
        {
            // 换回容器默认编码器。
            vcodec = defaultVideoCodec().value(container, QStringLiteral("libx264"));
            // 记录降级决策。
            qInfo("[builder] stream copy conflicts with filters, switching to re-encode with %s", qPrintable(vcodec));
        }
        // 非空才写 -c:v。
        if (!vcodec.isEmpty())
        {
            // 指定视频编码器。
            args << QStringLiteral("-c:v") << vcodec;
        }

        // ---- 质量策略：CRF 优先，其次码率，最后按质量档位推断 ----
        if (!vcodec.isEmpty() && vcodec != QStringLiteral("copy"))
        {
            // 取出用户给定的 CRF 与码率。
            QString crf = local.crf.trimmed();
            // 视频码率。
            const QString vbr = local.vBitrate.trimmed();
            // 两者都没给时按质量档位推断 CRF。
            if (crf.isEmpty() && vbr.isEmpty())
            {
                // 查档位表；兜底中等档。
                crf = qualityToCrf().value(local.quality, QStringLiteral("23"));
            }
            // 判断是否硬件编码器（质量参数名与软件编码器完全不同）。
            const bool isHw = isHardwareEncoder(vcodec);
            // 软件 x264/x265 特有的速度预设与无损处理。
            if (vcodec == QStringLiteral("libx264") || vcodec == QStringLiteral("libx265"))
            {
                // 编码速度预设：质量档位越高越偏向慢速高压缩比。
                QString preset = QStringLiteral("medium");
                // 无损档。
                if (local.quality == QStringLiteral("lossless")) { preset = QStringLiteral("veryslow"); }
                // 高质量档。
                else if (local.quality == QStringLiteral("high")) { preset = QStringLiteral("slow"); }
                // 低质量档。
                else if (local.quality == QStringLiteral("low")) { preset = QStringLiteral("faster"); }
                // 追加速度预设。
                args << QStringLiteral("-preset") << preset;
                // 无损档位使用无损量化（此时 CRF 无意义，清空以免冲突）。
                if (local.quality == QStringLiteral("lossless"))
                {
                    // -qp 0 表示无损。
                    args << QStringLiteral("-qp") << QStringLiteral("0");
                    // 清空 CRF。
                    crf.clear();
                }
            }
            // 应用 CRF：软件编码器与图片编码器的写法不同。
            if (!crf.isEmpty() && !isHw)
            {
                // 支持 -crf 的软件编码器白名单。
                static const QStringList crfEncoders = {
                    QStringLiteral("libx264"), QStringLiteral("libx265"), QStringLiteral("libvpx"),
                    QStringLiteral("libvpx-vp9"), QStringLiteral("libaom-av1"), QStringLiteral("libsvtav1"),
                    QStringLiteral("librav1e"), QStringLiteral("libxvid"), QStringLiteral("mpeg4"),
                    QStringLiteral("mpeg2video"), QStringLiteral("mpeg1video")
                };
                // 命中则用 -crf。
                if (crfEncoders.contains(vcodec))
                {
                    // 追加 CRF。
                    args << QStringLiteral("-crf") << crf;
                }
                else if (vcodec == QStringLiteral("mjpeg") || vcodec == QStringLiteral("png")
                    || vcodec == QStringLiteral("libwebp") || vcodec == QStringLiteral("gif")
                    || vcodec == QStringLiteral("tiff") || vcodec == QStringLiteral("bmp"))
                {
                    // 图片类编码器不支持 CRF，改用 -q:v（数值越小质量越高）。
                    args << QStringLiteral("-q:v") << qualityToImageQv().value(local.quality, QStringLiteral("5"));
                }
            }
            else if (!crf.isEmpty() && isHw)
            {
                // 硬件编码器的质量因子参数名各不相同。
                if (vcodec.endsWith(QStringLiteral("_nvenc")))
                {
                    // NVENC 用 -cq 并配合 vbr 速率控制。
                    args << QStringLiteral("-cq") << crf;
                    // 速率控制模式。
                    args << QStringLiteral("-rc") << QStringLiteral("vbr");
                }
                else if (vcodec.endsWith(QStringLiteral("_qsv")))
                {
                    // QSV 用 global_quality。
                    args << QStringLiteral("-global_quality") << crf;
                }
                else if (vcodec.endsWith(QStringLiteral("_amf")))
                {
                    // AMF 用 QP（I 帧与 P 帧分别指定）。
                    args << QStringLiteral("-qp_i") << crf << QStringLiteral("-qp_p") << crf;
                }
                else if (vcodec.endsWith(QStringLiteral("_vaapi")))
                {
                    // VAAPI 用 -qp。
                    args << QStringLiteral("-qp") << crf;
                }
            }
            // 应用码率（用户显式给出码率时覆盖 CRF 策略）。
            if (!vbr.isEmpty())
            {
                // 指定目标码率。
                args << QStringLiteral("-b:v") << vbr;
                // 解析数值部分以推算码率上限与缓冲区。
                QString numText;
                // 逐字符提取数字与小数点。
                for (const QChar c : vbr)
                {
                    // 只保留数字与小数点。
                    if (c.isDigit() || c == QLatin1Char('.')) { numText.append(c); }
                }
                // 转换数值。
                bool ok = false;
                // 执行转换。
                const double num = numText.toDouble(&ok);
                // 仅在解析成功且数值为正时设置上限，避免生成非法参数。
                if (ok && num > 0.0)
                {
                    // 最大码率取 1.5 倍。
                    args << QStringLiteral("-maxrate") << QStringLiteral("%1k").arg(int(num * 1.5));
                    // 缓冲区取 3 倍。
                    args << QStringLiteral("-bufsize") << QStringLiteral("%1k").arg(int(num * 3));
                }
            }
            // 像素格式（兼容性关键：绝大多数播放器只认 yuv420p 系列的 8bit 格式）。
            QString pixFmt = local.pixelFormat.trimmed();
            // 未指定时使用最通用的 yuv420p。
            if (pixFmt.isEmpty()) { pixFmt = QStringLiteral("yuv420p"); }
            // 图片编码器（如 png/libwebp）自行管理像素格式，附加 -pix_fmt 反而可能报错；
            // 这里只对“确实支持 -pix_fmt”的常规视频编码器添加。
            if (!imgTarget.codec.isEmpty() && vcodec == imgTarget.codec)
            {
                // 图片目标：不写 -pix_fmt（保留 alpha 通道等由编码器自行决定）。
            }
            else
            {
                // 视频编码器：写入目标像素格式。
                args << QStringLiteral("-pix_fmt") << pixFmt;
            }
            // 线程数（用户显式指定才写）。
            if (local.threads > 0)
            {
                // 指定编码线程数。
                args << QStringLiteral("-threads") << QString::number(local.threads);
            }
        }

        // ---- 视频滤镜与多输入滤镜（水印 / 字幕）----
        // 简单链（含文字水印，但排除图片水印与字幕）。
        const QString vf = buildFilterChain(local, hasTextWatermark, srcHeight);
        // 当需要图片水印或字幕时，必须改用 filter_complex。
        if (needComplex)
        {
            // 收集 filter_complex 的各个节点。
            QStringList nodes;
            // 图片水印输入的下标（1 表示第二个输入；若同时有字幕，水印仍是 1）。
            if (hasWatermarkImage)
            {
                // 水印相对宽度（限制在 5% ~ 100%，避免参数失控）。
                const double scaleRatio = qBound(0.05, local.watermarkScale, 1.0);
                // 不透明度（限制在 0 ~ 1）。
                const double opacity = qBound(0.0, local.watermarkOpacity, 1.0);
                // 水印流先缩放到相对宽度、再转成 rgba 以便应用整体不透明度。
                nodes << QStringLiteral("[1:v]scale=iw*%1:-1,format=rgba,colorchannelmixer=aa=%2[wm]")
                    .arg(QString::number(scaleRatio, 'f', 3), QString::number(opacity, 'f', 2));
            }
            // 主画面链：若本身没有滤镜，则用 null 保留标签（null 只是透传，不产生额外开销）。
            if (vf.isEmpty())
            {
                // 无滤镜时用 null 透传并打标签。
                nodes << QStringLiteral("[0:v]null[base]");
            }
            else
            {
                // 有滤镜时把链挂上并打标签。
                nodes << QStringLiteral("[0:v]%1[base]").arg(vf);
            }
            // 水印叠加节点（顺序：主画面 → 水印，仍然位于“帧率之后、字幕之前”的槽位）。
            if (hasWatermarkImage)
            {
                // overlay 坐标表达式。
                const QString pos = positionExpr(local.watermarkPos);
                // 叠加并打上 [wmout] 标签。
                nodes << QStringLiteral("[base][wm]overlay=%1[wmout]").arg(pos);
            }
            // 字幕烧录节点。
            if (hasSubtitle)
            {
                // 路径必须转义（Windows 盘符冒号与反斜杠都会让 filtergraph 解析失败）。
                const QString subPath = escapePathForFilter(QFileInfo(local.subtitlePath.trimmed()).absoluteFilePath());
                // 字幕的输入标签：有水印时接水印输出，否则接主画面链输出。
                const QString inLabel = hasWatermarkImage ? QStringLiteral("[wmout]") : QStringLiteral("[base]");
                // 追加字幕节点。
                nodes << QStringLiteral("%1subtitles=filename='%2'[vsub]").arg(inLabel, subPath);
            }
            // 变速节点：固定放在整条链的最后（字幕之后）。
            QString finalLabel;
            // 最后一个有效标签。
            if (hasSubtitle)
            {
                // 字幕输出标签。
                finalLabel = QStringLiteral("[vsub]");
            }
            else if (hasWatermarkImage)
            {
                // 水印输出标签。
                finalLabel = QStringLiteral("[wmout]");
            }
            else
            {
                // 仅文字水印的情形不会走到这里，但为完备性保留主画面标签。
                finalLabel = QStringLiteral("[base]");
            }
            // 速度不等于 1 时，在最终标签后追加 setpts 并重新打标签。
            const double sp = local.speed;
            // 需要变速才追加。
            if (qAbs(sp - 1.0) > 1e-6 && sp > 0.0)
            {
                // 追加变速节点。
                nodes << QStringLiteral("%1setpts=%2*PTS[vout]").arg(finalLabel, QString::number(1.0 / sp, 'f', 6));
                // 最终标签改为 [vout]。
                finalLabel = QStringLiteral("[vout]");
            }
            // 写入 filter_complex（节点之间用分号分隔）。
            args << QStringLiteral("-filter_complex") << nodes.join(QLatin1Char(';'));
            // 显式映射最终视频标签。
            args << QStringLiteral("-map") << finalLabel;
            // 关键补充：一旦使用了 -map，ffmpeg 就会关闭“自动流选择”，
            // 因此必须显式把音频也映射上，否则成片会没有声音。
            // 使用 "0:a:0?" 末尾的问号表示“若源没有音频则忽略该映射”，避免无音频源直接报错。
            if (keepAudio)
            {
                // 映射第一条音频流（可选）。
                args << QStringLiteral("-map") << QStringLiteral("0:a:0?");
            }
        }
        else if (!vf.isEmpty())
        {
            // 只有普通滤镜时直接使用 -vf。
            args << QStringLiteral("-vf") << vf;
        }
    }

    // ---- 音频编码 ----
    if (keepAudio)
    {
        // 用户指定的音频编码器优先，否则用容器默认。
        QString acodec = local.audioCodec.trimmed();
        // 空则查默认表；兜底 aac。
        if (acodec.isEmpty())
        {
            // 取默认值。
            acodec = defaultAudioCodec().value(container, QStringLiteral("aac"));
        }
        // copy 模式：用户要求且编码器不是 copy 时，改成流复制。
        if (st.copyMode && !acodec.isEmpty() && acodec != QStringLiteral("copy"))
        {
            // 使用流复制。
            acodec = QStringLiteral("copy");
        }
        // 空编码器（例如误选了不带音频的容器）时直接跳过音频段。
        if (!acodec.isEmpty())
        {
            // 指定音频编码器。
            args << QStringLiteral("-c:a") << acodec;
            // 非流复制才需要设置码率/声道/采样率。
            if (acodec != QStringLiteral("copy"))
            {
                // 音频码率：用户显式优先，否则按质量档位推断。
                QString abr = local.aBitrate.trimmed();
                // 未指定则查档位表。
                if (abr.isEmpty())
                {
                    // 取默认码率。
                    abr = qualityToAbr().value(local.quality, QStringLiteral("128k"));
                }
                // 无损音频编码器不接受 -b:a，必须跳过（否则 ffmpeg 会报“不支持的选项”）。
                static const QStringList losslessAudio = {
                    QStringLiteral("flac"), QStringLiteral("pcm_s16le"), QStringLiteral("pcm_s16be"),
                    QStringLiteral("pcm_s24le"), QStringLiteral("pcm_s32le"), QStringLiteral("alac"),
                    QStringLiteral("tta"), QStringLiteral("wavpack")
                };
                // 非无损编码器才写码率。
                if (!losslessAudio.contains(acodec))
                {
                    // 指定音频码率。
                    args << QStringLiteral("-b:a") << abr;
                }
                // 目标声道数与采样率。
                int chTarget = local.audioChannels;
                // 目标采样率。
                int srTarget = local.sampleRate;
                // 特殊编码器存在硬性约束：AMR-NB 只支持 8kHz/单声道，AMR-WB 只支持 16kHz/单声道，
                // 若不强制覆盖，ffmpeg 会因“采样率/声道不满足编码器要求”直接报错。
                if (acodec == QStringLiteral("libopencore_amrnb"))
                {
                    // AMR-NB 约束。
                    srTarget = 8000;
                    // 单声道。
                    chTarget = 1;
                }
                else if (acodec == QStringLiteral("libopencore_amrwb"))
                {
                    // AMR-WB 约束。
                    srTarget = 16000;
                    // 单声道。
                    chTarget = 1;
                }
                // 声道数。
                if (chTarget > 0)
                {
                    // 指定声道数。
                    args << QStringLiteral("-ac") << QString::number(chTarget);
                }
                // 采样率。
                if (srTarget > 0)
                {
                    // 指定采样率。
                    args << QStringLiteral("-ar") << QString::number(srTarget);
                }
                // 音频滤镜（音量与变速）。
                const QString af = buildAudioFilterChain(local);
                // 非空才写。
                if (!af.isEmpty())
                {
                    // 指定音频滤镜链。
                    args << QStringLiteral("-af") << af;
                }
            }
        }
        else
        {
            // 编码器为空：不写 -c:a，改为让 ffmpeg 依据容器自行决定（并保持既有行为）。
            qInfo("[builder] no audio encoder resolved for container %s", qPrintable(container));
        }
    }

    // ---- 元数据（逐项写入，空值跳过）----
    if (!local.metaTitle.trimmed().isEmpty())
    {
        // 标题。
        args << QStringLiteral("-metadata") << QStringLiteral("title=%1").arg(local.metaTitle.trimmed());
    }
    // 艺术家。
    if (!local.metaArtist.trimmed().isEmpty())
    {
        // 追加艺术家。
        args << QStringLiteral("-metadata") << QStringLiteral("artist=%1").arg(local.metaArtist.trimmed());
    }
    // 专辑。
    if (!local.metaAlbum.trimmed().isEmpty())
    {
        // 追加专辑。
        args << QStringLiteral("-metadata") << QStringLiteral("album=%1").arg(local.metaAlbum.trimmed());
    }
    // 备注。
    if (!local.metaComment.trimmed().isEmpty())
    {
        // 追加备注。
        args << QStringLiteral("-metadata") << QStringLiteral("comment=%1").arg(local.metaComment.trimmed());
    }

    // ---- 封装与容器细节 ----
    if (faststartContainers.contains(container))
    {
        // 把索引(moov)前移，便于网页边下边播。
        args << QStringLiteral("-movflags") << QStringLiteral("+faststart");
    }
    // 图片输出：image2 封装要求显式声明“只写一帧且持续更新同一文件”，否则会因缺少 %d 序号模板而报错。
    if (imgTarget.muxer == QStringLiteral("image2"))
    {
        // 只输出一帧。
        args << QStringLiteral("-frames:v") << QStringLiteral("1");
        // 允许把这一帧写回同一文件。
        args << QStringLiteral("-update") << QStringLiteral("1");
    }
    // 显式指定目标封装：仅在内核确实支持该封装名时才写 -f，
    // 否则宁可让 ffmpeg 依据输出扩展名自行推断，也不要传一个内核不认识的封装名（那会直接失败）。
    if (!container.isEmpty())
    {
        // 询问能力矩阵。
        if (Capabilities::instance().supportsFormat(container))
        {
            // 写入封装名。
            args << QStringLiteral("-f") << container;
        }
        else
        {
            // 记录降级决策（便于排障）。
            qInfo("[builder] container %s not confirmed by the kernel, let ffmpeg infer from the output name",
                  qPrintable(container));
        }
    }

    // ---- 额外追加参数（预设附加项与用户高级参数）----
    for (const QString& extra : local.extraArgs)
    {
        // 过滤空字符串，避免生成多余参数。
        if (!extra.trimmed().isEmpty())
        {
            // 逐项追加（保持用户给出的顺序）。
            args << extra;
        }
    }

    // ---- 输出路径（必须是最后一个位置参数）----
    const QString finalOut = outPath.isEmpty() ? computeOutputPath(local, true) : outPath;
    // 追加输出路径。
    args << finalOut;

    // 返回完整参数列表。
    return args;
}

// 生成软件编码回退设置。
TranscodeSettings softwareFallback(const TranscodeSettings& st)
{
    // 复制一份设置（避免污染调用方对象）。
    TranscodeSettings out = st;
    // 识别原编码器属于哪一档硬件实现，并替换成对应的软件实现。
    const QString vc = st.videoCodec.trimmed().toLower();
    // H.265/HEVC 族 → libx265。
    if (vc.contains(QStringLiteral("hevc")) || vc.contains(QStringLiteral("h265")))
    {
        // 替换为软件 HEVC 编码器。
        out.videoCodec = QStringLiteral("libx265");
    }
    else if (vc.contains(QStringLiteral("av1")))
    {
        // AV1 族：SVT-AV1 速度与兼容性都较好；内核没有时退回 libaom-av1。
        out.videoCodec = Capabilities::instance().supportsEncoder(QStringLiteral("libsvtav1"))
                             ? QStringLiteral("libsvtav1")
                             : QStringLiteral("libaom-av1");
    }
    else
    {
        // 其余一律按 H.264 处理（覆盖面最广）。
        out.videoCodec = QStringLiteral("libx264");
    }
    // 关闭自动硬件加速，防止再次被升级成硬件编码器。
    out.hwaccel = QStringLiteral("none");
    // 返回副本。
    return out;
}

// 渲染可读命令文本。
QString describeCommand(const QStringList& args)
{
    // 结果容器。
    QStringList out;
    // 逐个参数处理。
    for (const QString& a : args)
    {
        // 含空格的参数补上双引号，读起来更接近真实命令行。
        if (a.contains(QLatin1Char(' ')))
        {
            // 加引号后追加。
            out << QStringLiteral("\"%1\"").arg(a);
        }
        else
        {
            // 原样追加。
            out << a;
        }
    }
    // 用空格连接。
    return out.join(QLatin1Char(' '));
}

// 构建抽帧计划。
FrameExtractionPlan buildFrameExtractionPlan(const QString& sourcePath,
                                             double durationSeconds,
                                             int count,
                                             const QString& outDir,
                                             const QString& baseName)
{
    // 结果对象。
    FrameExtractionPlan plan;
    // 抽帧数量限制在 1 ~ 60（过多没有实际意义且会显著变慢）。
    const int n = qBound(1, count, 60);
    // 确保输出目录存在（否则 ffmpeg 会直接报 “No such file or directory”）。
    QDir().mkpath(outDir);
    // 文件名主名（为空时退回源文件主名）。
    const QString stem = baseName.trimmed().isEmpty()
                             ? QFileInfo(sourcePath).completeBaseName()
                             : baseName.trimmed();
    // 取 ffmpeg 路径。
    const QString exe = FFmpegLocator::instance().ffmpegPath();
    // 逐个时间点生成命令。
    for (int i = 0; i < n; ++i)
    {
        // 时间点：把时长均匀分成 n+1 段后取第 i+1 个分点，避开片头与片尾的黑场。
        const double t = (durationSeconds > 0.0)
                             ? durationSeconds * double(i + 1) / double(n + 1)
                             : 0.0;
        // 输出文件路径（三位序号，保证排序稳定）。
        const QString outPath = QDir(outDir).filePath(
            QStringLiteral("%1_%2.png").arg(stem).arg(i + 1, 3, 10, QLatin1Char('0')));
        // 组装该帧的命令。
        QStringList args;
        // 程序路径。
        args << exe;
        // 去横幅与允许覆盖。
        args << QStringLiteral("-hide_banner") << QStringLiteral("-y");
        // 快速定位到目标时间点（-ss 放在 -i 之前效率最高）。
        args << QStringLiteral("-ss") << formatTimeValue(t);
        // 源文件。
        args << QStringLiteral("-i") << QFileInfo(sourcePath).absoluteFilePath();
        // 只取一帧。
        args << QStringLiteral("-frames:v") << QStringLiteral("1");
        // 显式声明 image2 封装与更新同一文件（与单文件输出配套使用）。
        args << QStringLiteral("-f") << QStringLiteral("image2");
        // 允许把这一帧写回同一文件。
        args << QStringLiteral("-update") << QStringLiteral("1");
        // 输出路径（最后一个位置参数）。
        args << outPath;
        // 记录输出路径。
        plan.outPaths << outPath;
        // 记录命令。
        plan.commands << args;
    }
    // 返回计划。
    return plan;
}