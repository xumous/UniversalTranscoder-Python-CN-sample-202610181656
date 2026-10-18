// *****************************************************************************
// runner.cpp —— 执行器的实现。
// 实现要点：
//   1) 由于 run() 在工作线程中被同步调用（该线程没有事件循环），
//      这里全部使用 QProcess 的阻塞式等待函数（waitForStarted / waitForReadyRead），
//      并以 50 毫秒为步长轮询，既能及时读取进度，也能及时响应取消请求。
//   2) 两个通道都必须持续读取：进度流在标准输出，错误文本在标准错误。
//      若只读其中一个，另一个的管道缓冲区写满会让 ffmpeg 阻塞，表现为“卡住不动”。
//   3) 取消采用“先 terminate 再 kill”的两段式策略，兼顾优雅退出与确定性回收。
// *****************************************************************************
#include "runner.h"

// processutil：Windows 下“不弹出控制台窗口”的统一配置。
#include "processutil.h"
// QProcess：进程控制。
#include <QProcess>
// QFileInfo / QDir：统计输出文件大小、防御式创建输出目录。
#include <QFileInfo>
#include <QDir>
// QHash：累积一个进度块内的 key=value 字段。
#include <QHash>
// QElapsedTimer：精确计时（比 QDateTime 更适合度量耗时）。
#include <QElapsedTimer>
// QRegularExpression：拆分进度行。
#include <QRegularExpression>
// QDebug：排障日志。
#include <QDebug>
// QtMath：qRound64 等。
#include <QtMath>

// 构造。
Runner::Runner(QObject* parent)
    : QObject(parent)
{
    // 初始化取消标志为 0（未取消）。
    m_cancelFlag.storeRelaxed(0);
}

// 请求取消。
void Runner::requestCancel()
{
    // 以“释放语义”写入标志，保证工作线程能立即看到变化。
    m_cancelFlag.storeRelease(1);
}

// 查询是否已请求取消。
bool Runner::isCancelRequested() const
{
    // 以“获取语义”读取标志。
    return m_cancelFlag.loadAcquire() != 0;
}

// 内部辅助：把“进度相关选项”插入到输出路径之前。
static QStringList insertProgressOptions(const QStringList& args)
{
    // 拷贝一份，避免修改调用方传入的列表（调用方可能还要展示原始命令）。
    QStringList out = args;
    // 找到最后一个“非选项参数”的索引，即输出文件路径的位置。
    int outIdx = -1;
    // 从末尾向前扫描。
    for (int i = out.size() - 1; i >= 0; --i)
    {
        // 不以 "-" 开头的项即为位置参数（本工程的命令构建器保证输出路径是最后一个位置参数）。
        if (!out.at(i).startsWith(QLatin1Char('-')))
        {
            // 记录索引。
            outIdx = i;
            // 命中即停止扫描。
            break;
        }
    }
    // 要插入的标记：进度写到标准输出、关闭统计行、只保留错误级别日志。
    const QStringList marker = {
        QStringLiteral("-progress"), QStringLiteral("pipe:1"),
        QStringLiteral("-nostats"),
        QStringLiteral("-loglevel"), QStringLiteral("error")
    };
    // 未找到输出位置（异常情况）时追加到末尾即可。
    if (outIdx < 0)
    {
        // 追加到末尾。
        out += marker;
        // 返回。
        return out;
    }
    // 把标记逐个插入到输出路径之前：从后往前插入可保持相对顺序。
    for (int i = marker.size() - 1; i >= 0; --i)
    {
        // 在 outIdx 位置插入该标记。
        out.insert(outIdx, marker.at(i));
    }
    // 返回新列表。
    return out;
}

// 内部辅助：安全转浮点（处理 ffmpeg 常见的 "N/A"）。
static double toDoubleSafe(const QString& value, double defaultValue = 0.0)
{
    // 去空白。
    const QString s = value.trimmed();
    // 空值与 N/A 一律回退默认值。
    if (s.isEmpty() || s.compare(QStringLiteral("N/A"), Qt::CaseInsensitive) == 0
        || s.compare(QStringLiteral("NA"), Qt::CaseInsensitive) == 0)
    {
        // 回退。
        return defaultValue;
    }
    // 正常转换。
    bool ok = false;
    // 执行转换。
    const double v = s.toDouble(&ok);
    // 失败则回退。
    return ok ? v : defaultValue;
}

// 内部辅助：把 "HH:MM:SS.ffffff" 解析为秒。
static double hmsToSeconds(const QString& text)
{
    // 按冒号拆分。
    const QStringList parts = text.trimmed().split(QLatin1Char(':'));
    // 必须是三段（时:分:秒）。
    if (parts.size() != 3) { return 0.0; }
    // 分别取值。
    const double h = toDoubleSafe(parts.at(0));
    // 分。
    const double m = toDoubleSafe(parts.at(1));
    // 秒。
    const double s = toDoubleSafe(parts.at(2));
    // 换算为总秒数。
    return h * 3600.0 + m * 60.0 + s;
}

// 内部辅助：把一个进度块（key → value）解析为 ProgressInfo。
static ProgressInfo parseProgressBlock(const QHash<QString, QString>& block, double durationSeconds)
{
    // 结果对象。
    ProgressInfo p;
    // 已编码时间优先取 out_time_us（微秒，精度最高）。
    double outTime = 0.0;
    // 有 out_time_us 时按微秒换算。
    if (block.contains(QStringLiteral("out_time_us")))
    {
        // 微秒 → 秒。
        outTime = toDoubleSafe(block.value(QStringLiteral("out_time_us"))) / 1000000.0;
    }
    else if (block.contains(QStringLiteral("out_time_ms")))
    {
        // 注意：部分 ffmpeg 版本该字段名虽为 ms 但数值实为微秒，按微秒处理更稳。
        outTime = toDoubleSafe(block.value(QStringLiteral("out_time_ms"))) / 1000000.0;
    }
    else if (block.contains(QStringLiteral("out_time")))
    {
        // 形如 "00:00:05.000000"。
        outTime = hmsToSeconds(block.value(QStringLiteral("out_time")));
    }
    // 负值归零。
    p.outTime = qMax(0.0, outTime);
    // 百分比：有总时长才能换算，并限制在 0 ~ 99（100 由结束标志给出）。
    if (durationSeconds > 0.0)
    {
        // 换算百分比。
        p.percent = qBound(0.0, p.outTime / durationSeconds * 100.0, 99.0);
    }
    // 帧率。
    p.fps = toDoubleSafe(block.value(QStringLiteral("fps")));
    // 帧数。
    p.frame = qint64(toDoubleSafe(block.value(QStringLiteral("frame"))));
    // 速度字段形如 "1.5x"，需要去掉末尾的 x。
    QString speedRaw = block.value(QStringLiteral("speed")).trimmed();
    // 以 x 结尾时截掉它。
    if (speedRaw.endsWith(QLatin1Char('x')))
    {
        // 去掉最后一个字符后解析。
        speedRaw.chop(1);
        // 记录速度。
        p.speed = toDoubleSafe(speedRaw);
    }
    // 码率文本原样保留。
    p.bitrate = block.value(QStringLiteral("bitrate")).trimmed();
    // 已写入字节数。
    p.totalSize = qint64(toDoubleSafe(block.value(QStringLiteral("total_size"))));
    // 用 ffmpeg 报告的速度粗算剩余时间（主循环稍后会用瞬时速度精修）。
    if (durationSeconds > 0.0 && p.speed > 0.0)
    {
        // 剩余时长 = (总时长 - 已编码时长) / 速度。
        p.eta = qMax(0.0, (durationSeconds - p.outTime) / p.speed);
    }
    // 返回结果。
    return p;
}

// 同步执行一条命令。
RunResult Runner::run(const QStringList& args, double durationSeconds)
{
    // 结果对象。
    RunResult result;
    // 重置取消标志，使 Runner 对象可以被复用。
    m_cancelFlag.storeRelaxed(0);
    // 参数列表为空属于编程错误，直接返回失败。
    if (args.isEmpty())
    {
        // 记录错误文本。
        result.stderrText = QStringLiteral("未向执行器提供任何参数。");
        // 返回。
        return result;
    }
    // 记录输出路径：命令构建器保证输出路径是最后一个参数。
    result.outPath = args.last();

    // 防御式创建输出目录：目录缺失会让 ffmpeg 直接报 “No such file or directory”。
    // 主流程（任务队列）也会创建目录，这里是双保险，确保 Runner 被独立调用时也能工作。
    const QFileInfo outInfo(result.outPath);
    // 目录路径。
    const QString outDir = outInfo.absolutePath();
    // 目录非空才尝试创建。
    if (!outDir.isEmpty())
    {
        // 递归创建目录（已存在时返回 true，不会报错）。
        QDir().mkpath(outDir);
    }

    // 在输出路径之前插入进度相关选项。
    const QStringList runArgs = insertProgressOptions(args);

    // 构造 QProcess（栈上对象，函数返回时自动析构并回收句柄）。
    QProcess proc;
    // 分离两个通道：进度在标准输出，错误在标准错误。
    proc.setProcessChannelMode(QProcess::SeparateChannels);
    // 关闭标准输入：避免 ffmpeg 因等待输入而挂起。
    proc.setStandardInputFile(QProcess::nullDevice());
    // Windows 下不弹出控制台窗口。
    configureNoWindow(proc);
    // 指定程序路径（首元素）。
    proc.setProgram(runArgs.first());
    // 指定其余参数。
    proc.setArguments(runArgs.mid(1));

    // 计时器（记录总耗时）。
    QElapsedTimer timer;
    // 开始计时。
    timer.start();

    // 启动进程。
    proc.start();
    // 等待启动；10 秒余量足够（首次调用可能被杀毒软件扫描拖慢）。
    if (!proc.waitForStarted(10000))
    {
        // 记录错误文本（这是会显示给用户的信息，使用英文）。
        result.stderrText = QStringLiteral("启动 FFmpeg 失败：%1").arg(proc.errorString());
        // 记录耗时。
        result.elapsed = double(timer.elapsed()) / 1000.0;
        // 返回失败结果，并把取消状态带上。
        result.canceled = isCancelRequested();
        // 返回。
        return result;
    }
    // 通知调用方进程已启动（携带进程号）。
    emit processStarted(qint64(proc.processId()));

    // ---- 主循环：读取输出 + 检查取消 ----
    // 标准错误累积缓冲。
    QByteArray errBuf;
    // 标准输出累积缓冲（进度流的残余文本）。
    QByteArray outBuf;
    // 当前正在累积的进度块。
    QHash<QString, QString> cur;
    // 上一次上报的已编码时长（用于计算瞬时速度）。
    double lastOutTime = 0.0;
    // 上一次上报的墙钟时间（秒）。
    double lastReportWall = 0.0;
    // 循环直到进程结束。
    while (proc.state() != QProcess::NotRunning)
    {
        // 有数据可读则读取；最长阻塞 50 毫秒，以便及时响应取消。
        if (proc.waitForReadyRead(50))
        {
            // 读出标准输出中已到达的全部数据。
            outBuf.append(proc.readAllStandardOutput());
            // 读出标准错误中已到达的全部数据（必须持续读取，否则管道写满会让子进程阻塞）。
            errBuf.append(proc.readAllStandardError());
            // 从标准输出缓冲中按行解析进度块。
            qsizetype nl = -1;
            // 只要还有完整行就继续处理。
            while ((nl = outBuf.indexOf('\n')) >= 0)
            {
                // 取出一行（不含换行符）并转成字符串。
                const QString line = QString::fromUtf8(outBuf.left(nl)).trimmed();
                // 从缓冲中移除该行（含换行符，即 nl + 1 个字节）。
                outBuf.remove(0, nl + 1);
                // 空行跳过。
                if (line.isEmpty()) { continue; }
                // 只处理形如 key=value 的字段。
                const int eq = line.indexOf(QLatin1Char('='));
                // 没有等号的行不是进度字段。
                if (eq < 0) { continue; }
                // 键。
                const QString key = line.left(eq).trimmed();
                // 值。
                const QString value = line.mid(eq + 1).trimmed();
                // 记录到当前块。
                cur.insert(key, value);
                // progress 行标志着一个进度块结束。
                if (key == QStringLiteral("progress"))
                {
                    // 解析出结构化进度。
                    ProgressInfo p = parseProgressBlock(cur, durationSeconds);
                    // 用瞬时速度修正 ffmpeg 偶尔给出的 "N/A" 速度（取当前墙钟时间）。
                    const double nowWall = double(timer.elapsed()) / 1000.0;
                    // 已编码时长的增量。
                    const double deltaTime = p.outTime - lastOutTime;
                    // 墙钟耗时增量（至少给一个极小值防止除零）。
                    const double deltaWall = qMax(1e-6, nowWall - lastReportWall);
                    // 仅有增量时才用瞬时速度覆盖。
                    if (deltaTime > 0.0)
                    {
                        // 瞬时速度 = 媒体时间增量 / 真实耗时增量。
                        p.speed = deltaTime / deltaWall;
                        // 用更精确的瞬时速度重算剩余时间。
                        if (durationSeconds > 0.0 && p.speed > 0.0)
                        {
                            // 重算 ETA。
                            p.eta = qMax(0.0, (durationSeconds - p.outTime) / p.speed);
                        }
                    }
                    // 记录本次上报的时间基准。
                    lastOutTime = p.outTime;
                    // 记录本次上报的墙钟基准。
                    lastReportWall = nowWall;
                    // 标记是否结束。
                    p.done = (value == QStringLiteral("end"));
                    // 结束标志出现时把百分比推到 100。
                    if (p.done) { p.percent = 100.0; }
                    // 发出进度信号（跨线程时由 Qt 自动排队）。
                    emit progressChanged(p);
                    // 清空当前块，准备接收下一块。
                    cur.clear();
                }
            }
        }
        // 检查取消请求。
        if (isCancelRequested())
        {
            // 先温和请求终止。
            proc.terminate();
            // 等待最多 2 秒。
            if (!proc.waitForFinished(2000))
            {
                // 仍未退出则强制结束，避免残留进程继续占满 CPU。
                proc.kill();
                // 再等 2 秒回收。
                proc.waitForFinished(2000);
            }
            // 跳出主循环。
            break;
        }
    }

    // ---- 收尾：确保进程结束并读取残留输出 ----
    // 进程仍在运行时（正常结束路径不会走到这里）强制回收。
    if (proc.state() != QProcess::NotRunning)
    {
        // 请求终止。
        proc.terminate();
        // 等待 2 秒。
        if (!proc.waitForFinished(2000))
        {
            // 强杀。
            proc.kill();
            // 再等。
            proc.waitForFinished(2000);
        }
    }
    // 读取最后残留的数据。
    outBuf.append(proc.readAllStandardOutput());
    // 读取残留的错误数据。
    errBuf.append(proc.readAllStandardError());

    // 记录返回码（进程未回收时 exitCode 为 -1，用 exitStatus 兜底判断）。
    result.returnCode = (proc.exitStatus() == QProcess::NormalExit) ? proc.exitCode() : -1;
    // 记录错误文本。
    result.stderrText = QString::fromUtf8(errBuf);
    // 记录总耗时。
    result.elapsed = double(timer.elapsed()) / 1000.0;
    // 记录取消状态。
    result.canceled = isCancelRequested();
    // 统计输出文件大小（存在才统计）。
    const QFileInfo fi(result.outPath);
    // 文件确实存在才取大小。
    if (fi.exists() && fi.isFile())
    {
        // 记录字节数。
        result.outSize = fi.size();
    }
    // 被取消的任务不应把“没写完的产物”当作成功，这里把大小清零以避免上层误判。
    if (result.canceled)
    {
        // 清零产物大小。
        result.outSize = 0;
    }
    // 返回结果。
    return result;
}