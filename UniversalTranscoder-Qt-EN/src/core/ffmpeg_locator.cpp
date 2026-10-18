// *****************************************************************************
// ffmpeg_locator.cpp —— 内核定位与校验的实现。
// 实现要点：
//   1) 候选列表按“用户自定义 → 随包目录 → 程序同级目录 → 系统 PATH”排序，
//      顺序体现了业务优先级：用户手动指定的一定最优先。
//   2) 校验用 `-version`，同时检查“进程成功退出”与“输出含 ffmpeg/ffprobe version 横幅”
//      两个条件——只看文件存在会把同名的无关程序误判为可用内核。
//   3) Windows 上所有子进程都加 CREATE_NO_WINDOW，避免 GUI 程序转码时闪出控制台窗口。
//   4) 锁的使用规则：只在“读写成员字段”时持锁，锁内绝不再调用会加锁的成员函数，
//      以此彻底避免 QMutex 不可重入导致的死锁。
// *****************************************************************************
#include "ffmpeg_locator.h"

// processutil：提供 runCapture（同步捕获）与“不弹窗”配置。
#include "processutil.h"
// QCoreApplication：用于取“应用程序所在目录”，从而定位随包内核。
#include <QCoreApplication>
// QDir：目录与路径规范化处理。
#include <QDir>
// QFileInfo：文件存在性、是否为普通文件等判断。
#include <QFileInfo>
// QStandardPaths：在系统 PATH 中查找可执行文件。
#include <QStandardPaths>
// QMutexLocker：作用域内自动加解锁，避免漏写 unlock。
#include <QMutexLocker>
// QRegularExpression：拆分版本输出文本。
#include <QRegularExpression>
// QDebug：提供 qInfo / qWarning 日志宏。
#include <QDebug>

// 单例实例获取。
FFmpegLocator& FFmpegLocator::instance()
{
    // 函数内静态对象：C++11 起保证线程安全的惰性初始化，且只在首次调用时构造。
    static FFmpegLocator s_instance;
    // 返回引用。
    return s_instance;
}

// 设置用户自定义内核路径。
void FFmpegLocator::setCustomPath(const QString& path)
{
    // 加锁，保证字段写入与缓存清空是原子的。
    QMutexLocker locker(&m_mutex);
    // 记录自定义路径（原样保存，便于界面回显；规范化放在候选构造阶段做）。
    m_custom = path;
    // 自定义路径变化后旧缓存必然失效，这里直接清空。
    m_cache.clear();
}

// 读取用户自定义内核路径。
QString FFmpegLocator::customPath() const
{
    // 加锁读取，保证不会读到“写了一半”的状态。
    QMutexLocker locker(&m_mutex);
    // 返回副本。
    return m_custom;
}

// 取得随包内核目录。
QString FFmpegLocator::bundledDir() const
{
    // 先取应用程序所在目录（cwd 可能是任意位置，因此必须用 applicationDirPath）。
    const QString exeDir = QCoreApplication::applicationDirPath();
    // 随包内核固定放在 resources/ffmpeg 下，这是绿色版分发的标准布局。
    return QDir(exeDir).filePath(QStringLiteral("resources/ffmpeg"));
}

// 查找工具路径（带缓存）。
QString FFmpegLocator::findTool(const QString& tool)
{
    // 第一阶段：只做一次短时加锁，用于“查缓存 + 取自定义路径副本”。
    QString customCopy;
    {
        // 作用域内自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 命中缓存则立即返回：空字符串同样被缓存，以避免反复做失败查找。
        if (m_cache.contains(tool))
        {
            // 返回缓存值（可能是空字符串）。
            return m_cache.value(tool);
        }
        // 取出自定义路径副本，供后面在锁外使用。
        customCopy = m_custom;
    }
    // 第二阶段：在锁外构造候选并逐个真实校验（校验要启动进程，耗时较长，绝不能在锁内做）。
    const QStringList cands = candidatePaths(tool, customCopy);
    // 记录最终结果，默认空字符串表示未找到。
    QString found;
    // 遍历候选列表，返回第一个通过校验的。
    for (const QString& cand : cands)
    {
        // 执行 -version 真实校验。
        if (verify(cand))
        {
            // 记录命中路径。
            found = QDir::cleanPath(cand);
            // 记录日志，便于排障时确认到底用了哪一份内核。
            qInfo("[kernel] located %s at %s", qPrintable(tool), qPrintable(found));
            // 找到即停止遍历。
            break;
        }
    }
    // 若一个都没通过，输出一条警告，提示用户去 Settings 页指定自定义内核。
    if (found.isEmpty())
    {
        // 输出警告。
        qWarning("[kernel] no usable %s found", qPrintable(tool));
    }
    // 第三阶段：再次短时加锁，写入缓存。
    {
        // 作用域内自动加解锁。
        QMutexLocker locker(&m_mutex);
        // 写入缓存（可能为空字符串）。
        m_cache.insert(tool, found);
    }
    // 返回结果。
    return found;
}

// 生成候选路径列表。
QStringList FFmpegLocator::candidatePaths(const QString& tool, const QString& custom) const
{
    // 结果容器。
    QStringList cands;
    // 局部辅助 lambda：仅接受非空路径并去重，避免对同一文件重复执行 -version。
    auto add = [&cands](const QString& p)
    {
        // 空路径直接忽略。
        if (p.isEmpty()) { return; }
        // 已存在则忽略（保持先出现的更高优先级）。
        if (cands.contains(p)) { return; }
        // 追加到候选尾部。
        cands.append(p);
    };

    // Windows 下可执行文件带 .exe 后缀；其它平台不带，因此用条件编译。
#ifdef Q_OS_WIN
    // 固定的后缀常量。
    const QString suffix = QStringLiteral(".exe");
#else
    // 其它平台无后缀。
    const QString suffix = QString();
#endif

    // ---- 候选 1：用户自定义路径 ----
    // 只有在自定义路径非空时才处理。
    if (!custom.isEmpty())
    {
        // 规范化路径，兼容用户输入中 / 与 \ 混用的情况。
        const QString clean = QDir::fromNativeSeparators(custom.trimmed());
        // 构造文件信息以判断它是目录还是文件。
        const QFileInfo fi(clean);
        // 情况 A：用户给的是目录 → 在目录中拼出工具文件名。
        if (fi.isDir())
        {
            // 目录 + 工具名 + 后缀。
            add(QDir(clean).filePath(tool + suffix));
            // 兼容“用户把内核放在自定义目录的子目录 ffmpeg 下”的情形。
            add(QDir(clean).filePath(QStringLiteral("ffmpeg/") + tool + suffix));
        }
        else if (fi.isFile())
        {
            // 情况 B：用户给的是文件 → 只有文件名确实包含同名工具时才采纳，避免误用。
            if (fi.fileName().toLower().contains(tool.toLower()))
            {
                // 直接采纳该文件路径。
                add(clean);
            }
        }
    }

    // ---- 候选 2：随包内核目录（resources/ffmpeg）----
    // 这是绿色版分发后的标准位置，优先级仅次于用户自定义。
    add(QDir(bundledDir()).filePath(tool + suffix));

    // ---- 候选 3：程序同级目录 ----
    // 方便用户直接把新版内核丢在 exe 旁边替换（无需进 resources 子目录）。
    const QString exeDir = QCoreApplication::applicationDirPath();
    // 同级目录下的同名文件。
    add(QDir(exeDir).filePath(tool + suffix));
    // 同级目录下再套一层 ffmpeg 子目录的情况。
    add(QDir(exeDir).filePath(QStringLiteral("ffmpeg/") + tool + suffix));
    // 开发期常见的“工程根目录 + resources/ffmpeg”布局：exe 位于 release 子目录时向上找一层。
    add(QDir::cleanPath(QDir(exeDir).filePath(QStringLiteral("../resources/ffmpeg/") + tool + suffix)));

    // ---- 候选 4：系统 PATH ----
    // QStandardPaths::findExecutable 会按 PATH 顺序查找，未找到时返回空字符串。
    const QString inPath = QStandardPaths::findExecutable(tool);
    // 只有确实找到才追加，避免加入无效的空路径。
    if (!inPath.isEmpty())
    {
        // 追加 PATH 中找到的路径。
        add(QDir::cleanPath(inPath));
    }

    // 返回候选列表。
    return cands;
}

// 执行 -version 真实校验。
bool FFmpegLocator::verify(const QString& exePath, int timeoutMs) const
{
    // 路径为空直接失败。
    if (exePath.isEmpty()) { return false; }
    // 规范化路径（把 ../ 之类的相对片段折叠掉，便于缓存与展示）。
    const QString clean = QDir::cleanPath(exePath);
    // 构造文件信息。
    const QFileInfo fi(clean);
    // 必须存在于文件系统且是普通文件（isFile 会排除目录）。
    if (!fi.exists() || !fi.isFile()) { return false; }
    // 组装参数：-version 是 FFmpeg 家族统一支持的“打印版本并退出”选项。
    QStringList args;
    // 首元素必须是程序路径。
    args << clean;
    // 追加版本查询选项。
    args << QStringLiteral("-version");
    // 合并捕获输出（版本横幅可能出现在任一通道，合并最稳）。
    QByteArray out;
    // 进程退出码（本函数中仅用于诊断，不参与判定）。
    int rc = -1;
    // 执行；runCapture 返回 false 表示无法启动、超时或非零退出，三种情况都应判定校验失败。
    if (!runCapture(args, out, timeoutMs, &rc))
    {
        // 返回失败。
        return false;
    }
    // 把输出按 UTF-8 宽松解码（版本信息为纯 ASCII，宽松解码足够）。
    const QString text = QString::fromUtf8(out);
    // 判定条件：出现 FFmpeg 家族的版本横幅，说明它确实是我们期望的程序。
    const bool hasBanner = text.contains(QStringLiteral("ffmpeg version"), Qt::CaseInsensitive)
        || text.contains(QStringLiteral("ffprobe version"), Qt::CaseInsensitive);
    // 返回判定结果。
    return hasBanner;
}

// 便捷方法：ffmpeg 路径。
QString FFmpegLocator::ffmpegPath()
{
    // 委托给通用查找。
    return findTool(QStringLiteral("ffmpeg"));
}

// 便捷方法：ffprobe 路径。
QString FFmpegLocator::ffprobePath()
{
    // 委托给通用查找。
    return findTool(QStringLiteral("ffprobe"));
}

// 判断内核是否就绪。
bool FFmpegLocator::isReady()
{
    // 探测能力（ffprobe）与转码能力（ffmpeg）缺一不可，因此两个都必须找到。
    return !ffmpegPath().isEmpty() && !ffprobePath().isEmpty();
}

// 读取 ffmpeg 版本首行。
QString FFmpegLocator::versionText()
{
    // 取 ffmpeg 路径。
    const QString exe = ffmpegPath();
    // 未找到内核时给出明确提示（英文，因为该文本会显示给用户）。
    if (exe.isEmpty()) { return QStringLiteral("FFmpeg not found"); }
    // 组装参数。
    QStringList args;
    // 程序路径。
    args << exe;
    // 版本选项。
    args << QStringLiteral("-version");
    // 输出缓冲。
    QByteArray out;
    // 执行（8 秒超时足够打印版本）。
    if (!runCapture(args, out, 8000, nullptr))
    {
        // 读不到版本时返回失败提示。
        return QStringLiteral("Failed to read FFmpeg version");
    }
    // 解码并按行拆分（同时兼容 \r\n 与 \n）。
    const QStringList lines = QString::fromUtf8(out).split(QRegularExpression(QStringLiteral("[\\r\\n]+")),
                                                           Qt::SkipEmptyParts);
    // 返回第一行（即版本横幅）；输出为空时给出兜底文案。
    return lines.isEmpty() ? QStringLiteral("Unknown FFmpeg version") : lines.first().trimmed();
}

// 读取 ffprobe 版本首行。
QString FFmpegLocator::ffprobeVersionText()
{
    // 取 ffprobe 路径。
    const QString exe = ffprobePath();
    // 未找到时返回提示。
    if (exe.isEmpty()) { return QStringLiteral("FFprobe not found"); }
    // 组装参数。
    QStringList args;
    // 程序路径。
    args << exe;
    // 版本选项。
    args << QStringLiteral("-version");
    // 输出缓冲。
    QByteArray out;
    // 执行。
    if (!runCapture(args, out, 8000, nullptr))
    {
        // 失败提示。
        return QStringLiteral("Failed to read FFprobe version");
    }
    // 解码后按行拆分。
    const QStringList lines = QString::fromUtf8(out).split(QRegularExpression(QStringLiteral("[\\r\\n]+")),
                                                           Qt::SkipEmptyParts);
    // 返回第一行；为空时给出兜底文案。
    return lines.isEmpty() ? QStringLiteral("Unknown FFprobe version") : lines.first().trimmed();
}

// 清空缓存。
void FFmpegLocator::clearCache()
{
    // 加锁后整体清空。
    QMutexLocker locker(&m_mutex);
    // 清空缓存映射。
    m_cache.clear();
}