// *****************************************************************************
// capabilities.cpp —— 能力探测的实现。
// 设计要点：
//   1) 探测结果按集合缓存，避免每次转码都重新跑几条进程。
//   2) 严格遵守“锁内不做耗时操作”的原则：只在读写缓存字段时持锁，
//      真正执行 ffmpeg 查询的过程放在锁外，防止界面首次探测时被长时间阻塞。
//   3) 探测失败（内核缺失 / 超时）时返回空集合，上层据此走“宽松策略”。
// *****************************************************************************
#include "capabilities.h"

// 内核定位器：取得 ffmpeg 路径。
#include "ffmpeg_locator.h"
// processutil：同步执行并合并捕获输出。
#include "processutil.h"
// QRegularExpression：按任意空白切分输出行。
#include <QRegularExpression>
// QMutexLocker：自动加解锁。
#include <QMutexLocker>
// QDebug：日志输出。
#include <QDebug>

// 单例获取。
Capabilities& Capabilities::instance()
{
    // 函数内静态对象，C++11 保证线程安全初始化。
    static Capabilities s_instance;
    // 返回引用。
    return s_instance;
}

// 执行一条能力查询命令。
QString Capabilities::runQuery(const QStringList& args)
{
    // 输出缓冲。
    QByteArray out;
    // 查询成功与否不影响后续解析（失败时 out 为空，解析结果自然为空集合）。
    // 超时设为 20 秒：能力列表通常瞬间返回，但个别慢速磁盘首次加载大 exe 可能略慢。
    runCapture(args, out, 20000, nullptr);
    // 按 UTF-8 宽松解码（编码器描述中含大量非 ASCII 文本，宽松解码可避免乱码异常）。
    return QString::fromUtf8(out);
}

// 获取编码器字典。
QHash<QString, QChar> Capabilities::encoders()
{
    // 第一阶段：读缓存（短时加锁）。
    {
        // 自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 已探测过则直接返回缓存副本。
        if (m_encodersLoaded)
        {
            // 返回副本，避免调用方并发修改破坏缓存。
            return m_encoders;
        }
    }
    // 第二阶段：锁外执行探测。
    QHash<QString, QChar> parsed;
    // 取 ffmpeg 路径（内部有自己的锁，与此处互不冲突）。
    const QString exe = FFmpegLocator::instance().ffmpegPath();
    // 内核存在才探测。
    if (!exe.isEmpty())
    {
        // 组装查询命令：-hide_banner 去掉版权横幅，-encoders 打印编码器列表。
        QStringList args;
        // 程序路径。
        args << exe;
        // 去横幅。
        args << QStringLiteral("-hide_banner");
        // 打印编码器。
        args << QStringLiteral("-encoders");
        // 执行并取得文本。
        const QString text = runQuery(args);
        // 逐行解析。
        const QStringList lines = text.split(QRegularExpression(QStringLiteral("[\\r\\n]+")), Qt::SkipEmptyParts);
        // 遍历每一行。
        for (const QString& rawLine : lines)
        {
            // 去掉首尾空白。
            const QString s = rawLine.trimmed();
            // 空行跳过。
            if (s.isEmpty()) { continue; }
            // 有效数据行首字符必须是类别标志（V/A/S）；分隔线与表头会被这一步过滤掉。
            const QChar first = s.at(0);
            // 只处理 V / A / S 开头的行。
            if (first != QLatin1Char('V') && first != QLatin1Char('A') && first != QLatin1Char('S')) { continue; }
            // 按空白切分：第 0 段是标志串，第 1 段是编码器名。
            const QStringList parts = s.split(QRegularExpression(QStringLiteral("\\s+")), Qt::SkipEmptyParts);
            // 段数不足说明不是有效行。
            if (parts.size() < 2) { continue; }
            // 标志串长度应至少 5（如 "V....."），过短说明格式不符。
            if (parts.at(0).size() < 5) { continue; }
            // 编码器名。
            const QString name = parts.at(1).trimmed();
            // 名称为空则跳过。
            if (name.isEmpty()) { continue; }
            // 判定类别：优先级 V > A > S（同一编码器极少同时具备多类，取最主要的一类即可）。
            QChar kind;
            // 含 V 视为视频编码器。
            if (parts.at(0).contains(QLatin1Char('V')))
            {
                // 记录视频类别。
                kind = QLatin1Char('V');
            }
            else if (parts.at(0).contains(QLatin1Char('A')))
            {
                // 记录音频类别。
                kind = QLatin1Char('A');
            }
            else
            {
                // 其余按字幕类别归类。
                kind = QLatin1Char('S');
            }
            // 同名只登记一次，保持稳定结果。
            if (!parsed.contains(name))
            {
                // 写入解析结果。
                parsed.insert(name, kind);
            }
        }
    }
    // 第三阶段：写缓存（短时加锁）。
    {
        // 自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 写入解析结果。
        m_encoders = parsed;
        // 标记已探测（即使为空也算探测过，避免重复跑进程）。
        m_encodersLoaded = true;
    }
    // 输出一条日志便于确认内核能力。
    qInfo("[caps] encoders: %d", int(parsed.size()));
    // 返回结果。
    return parsed;
}

// 获取封装格式集合。
QSet<QString> Capabilities::formats()
{
    // 第一阶段：读缓存。
    {
        // 自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 已探测过则返回副本。
        if (m_formatsLoaded)
        {
            // 返回缓存副本。
            return m_formats;
        }
    }
    // 第二阶段：锁外探测。
    QSet<QString> parsed;
    // 取 ffmpeg 路径。
    const QString exe = FFmpegLocator::instance().ffmpegPath();
    // 内核存在才探测。
    if (!exe.isEmpty())
    {
        // 组装命令。
        QStringList args;
        // 程序路径。
        args << exe;
        // 去横幅。
        args << QStringLiteral("-hide_banner");
        // 打印封装格式列表。
        args << QStringLiteral("-formats");
        // 执行并取文本。
        const QString text = runQuery(args);
        // 按行拆分。
        const QStringList lines = text.split(QRegularExpression(QStringLiteral("[\\r\\n]+")), Qt::SkipEmptyParts);
        // 遍历行。
        for (const QString& rawLine : lines)
        {
            // 去首尾空白。
            const QString s = rawLine.trimmed();
            // 空行跳过。
            if (s.isEmpty()) { continue; }
            // 按空白切分：第 0 段是标志串（D 解封装 / E 封装），第 1 段是逗号分隔的格式别名。
            const QStringList parts = s.split(QRegularExpression(QStringLiteral("\\s+")), Qt::SkipEmptyParts);
            // 段数不足跳过。
            if (parts.size() < 2) { continue; }
            // 标志串必须只由 D / E 组成（表头行与分隔线会被这一步过滤掉）。
            const QString flags = parts.at(0);
            // 逐个字符检查标志合法性。
            bool flagOk = !flags.isEmpty();
            // 遍历标志串字符。
            for (const QChar c : flags)
            {
                // 出现非 D / E 的字符即判为非法行。
                if (c != QLatin1Char('D') && c != QLatin1Char('E'))
                {
                    // 标记不合法。
                    flagOk = false;
                    // 提前结束该行的字符检查。
                    break;
                }
            }
            // 非法行跳过。
            if (!flagOk) { continue; }
            // 第 1 段是逗号分隔的多个别名，全部登记。
            const QStringList aliases = parts.at(1).split(QLatin1Char(','), Qt::SkipEmptyParts);
            // 遍历别名。
            for (const QString& one : aliases)
            {
                // 去空白转小写后加入集合。
                const QString norm = one.trimmed().toLower();
                // 非空才加入。
                if (!norm.isEmpty())
                {
                    // 写入集合。
                    parsed.insert(norm);
                }
            }
        }
    }
    // 第三阶段：写缓存。
    {
        // 自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 写入结果。
        m_formats = parsed;
        // 标记已探测。
        m_formatsLoaded = true;
    }
    // 日志。
    qInfo("[caps] formats: %d", int(parsed.size()));
    // 返回结果。
    return parsed;
}

// 获取硬件加速后端列表。
QStringList Capabilities::hwaccels()
{
    // 第一阶段：读缓存。
    {
        // 自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 已探测过则返回副本。
        if (m_hwaccelsLoaded)
        {
            // 返回缓存副本。
            return m_hwaccels;
        }
    }
    // 第二阶段：锁外探测。
    QStringList parsed;
    // 取 ffmpeg 路径。
    const QString exe = FFmpegLocator::instance().ffmpegPath();
    // 内核存在才探测。
    if (!exe.isEmpty())
    {
        // 组装命令。
        QStringList args;
        // 程序路径。
        args << exe;
        // 去横幅。
        args << QStringLiteral("-hide_banner");
        // 打印硬件加速后端。
        args << QStringLiteral("-hwaccels");
        // 执行并取文本。
        const QString text = runQuery(args);
        // 按行拆分。
        const QStringList lines = text.split(QRegularExpression(QStringLiteral("[\\r\\n]+")), Qt::SkipEmptyParts);
        // 遍历行。
        for (const QString& rawLine : lines)
        {
            // 去首尾空白。
            const QString s = rawLine.trimmed();
            // 跳过空行。
            if (s.isEmpty()) { continue; }
            // 跳过说明性表头行（形如 "Hardware acceleration methods:" 与 "Hardware acceleration methods"）。
            if (s.startsWith(QStringLiteral("Hardware acceleration"), Qt::CaseInsensitive)) { continue; }
            // 跳过包含冒号的说明行（后端名不会含冒号）。
            if (s.contains(QLatin1Char(':'))) { continue; }
            // 其余每行即一个后端名。
            parsed.append(s);
        }
    }
    // 第三阶段：写缓存。
    {
        // 自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 写入结果。
        m_hwaccels = parsed;
        // 标记已探测。
        m_hwaccelsLoaded = true;
    }
    // 返回结果。
    return parsed;
}

// 某一类别的编码器名列表。
QStringList Capabilities::encodersOf(QChar kind)
{
    // 取得全部编码器（可能触发一次探测）。
    const QHash<QString, QChar> all = encoders();
    // 结果容器。
    QStringList names;
    // 遍历字典，挑出指定类别。
    for (auto it = all.constBegin(); it != all.constEnd(); ++it)
    {
        // 类别匹配则收集名称。
        if (it.value() == kind)
        {
            // 追加名称。
            names.append(it.key());
        }
    }
    // 排序后返回，保证界面下拉框顺序稳定。
    names.sort();
    // 返回结果。
    return names;
}

// 判断某编码器是否存在。
bool Capabilities::supportsEncoder(const QString& name)
{
    // 直接查字典。
    return encoders().contains(name);
}

// 判断某封装是否受支持。
bool Capabilities::supportsFormat(const QString& name)
{
    // 规范化输入：去点、去空白、转小写。
    const QString target = name.trimmed().toLower();
    // 空名称视为不支持。
    if (target.isEmpty()) { return false; }
    // 去掉可能存在的开头点号。
    const QString key = target.startsWith(QLatin1Char('.')) ? target.mid(1) : target;
    // 取得封装集合。
    const QSet<QString> fmts = formats();
    // 探测失败（集合为空）时采取宽松策略：一律返回 true，
    // 以免因一次探测失败就把用户可选的格式全部禁用（转码时 ffmpeg 会按扩展名自行推断）。
    if (fmts.isEmpty()) { return true; }
    // 精确命中即支持。
    if (fmts.contains(key)) { return true; }
    // 常见别名等价映射：内核里封装名与用户习惯写法不同的情况。
    QHash<QString, QString> aliases;
    // mkv 的真实封装名是 matroska（不存在名为 mkv 的封装）。
    aliases.insert(QStringLiteral("mkv"), QStringLiteral("matroska"));
    // m4v / m4a 都属于 mp4 家族。
    aliases.insert(QStringLiteral("m4v"), QStringLiteral("mp4"));
    // m4a 音频封装在 ffmpeg 中登记为 ipod，但 mp4 同样可承载。
    aliases.insert(QStringLiteral("m4a"), QStringLiteral("ipod"));
    // aac 裸流封装名为 adts。
    aliases.insert(QStringLiteral("aac"), QStringLiteral("adts"));
    // 单帧图片统一由 image2 承载。
    aliases.insert(QStringLiteral("jpg"), QStringLiteral("image2"));
    // jpeg 同上。
    aliases.insert(QStringLiteral("jpeg"), QStringLiteral("image2"));
    // png 单帧同样由 image2 承载。
    aliases.insert(QStringLiteral("png"), QStringLiteral("image2"));
    // 传输流别名。
    aliases.insert(QStringLiteral("ts"), QStringLiteral("mpegts"));
    // m2ts 也是传输流的另一种写法。
    aliases.insert(QStringLiteral("m2ts"), QStringLiteral("mpegts"));
    // mpg 对应 MPEG program stream。
    aliases.insert(QStringLiteral("mpg"), QStringLiteral("mpeg"));
    // wmv 的真实封装是 asf。
    aliases.insert(QStringLiteral("wmv"), QStringLiteral("asf"));
    // 查别名表。
    const QString alt = aliases.value(key);
    // 别名命中则视为支持。
    if (!alt.isEmpty() && fmts.contains(alt)) { return true; }
    // 以上都不命中则判定不支持。
    return false;
}

// 生成能力摘要。
Capabilities::Summary Capabilities::summary()
{
    // 结果对象。
    Summary s;
    // 取得编码器字典（可能触发一次探测）。
    const QHash<QString, QChar> enc = encoders();
    // 编码器总数。
    s.encoderTotal = int(enc.size());
    // 遍历统计各类数量。
    for (auto it = enc.constBegin(); it != enc.constEnd(); ++it)
    {
        // 视频编码器计数。
        if (it.value() == QLatin1Char('V')) { s.videoEncoders++; }
        // 音频编码器计数。
        else if (it.value() == QLatin1Char('A')) { s.audioEncoders++; }
        // 字幕编码器计数。
        else if (it.value() == QLatin1Char('S')) { s.subtitleEncoders++; }
    }
    // 封装格式总数。
    s.formatTotal = int(formats().size());
    // 硬件加速后端列表。
    s.hwaccels = hwaccels();
    // 返回摘要。
    return s;
}

// 清空缓存。
void Capabilities::clearCache()
{
    // 加锁后整体重置。
    QMutexLocker locker(&m_mutex);
    // 清空各集合。
    m_encoders.clear();
    // 清空封装集合。
    m_formats.clear();
    // 清空硬件后端列表。
    m_hwaccels.clear();
    // 重置三个“已探测”标志。
    m_encodersLoaded = false;
    // 同上。
    m_formatsLoaded = false;
    // 同上。
    m_hwaccelsLoaded = false;
}