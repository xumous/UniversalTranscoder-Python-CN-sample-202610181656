// *****************************************************************************
// taskqueue.cpp —— 任务队列与调度器的实现。
// 实现要点：
//   1) 所有任务列表的读写都发生在“队列对象所属线程”（也就是主线程），
//      因此不需要为列表本身加锁；跨线程只通过原子标志与信号槽交互。
//   2) 每个任务启动一个独立的 QThread，并把 TranscodeJob 移动进去执行；
//      QThread::finished 时自动 deleteLater 回收执行体与线程对象（Qt 的标准范式）。
//   3) 失败补救分两类，且必须区别对待：
//      · 奇数分辨率 → 把 pad 滤镜插入到输出路径之前，重试一次；
//      · 硬件编码失败 → 仅追加 -hwaccel none 无效（那只关闭硬件解码），
//        必须“整套替换为软件编码器”后重建命令再重试一次。
// *****************************************************************************
#include "taskqueue.h"

// QThread：工作线程。
#include <QThread>
// QFileInfo / QDir：源文件大小、创建输出目录。
#include <QFileInfo>
#include <QDir>
// QDateTime：生成单调递增的任务 ID 前缀。
#include <QDateTime>
// QDebug：排障日志。
#include <QDebug>
// 内核定位器与能力矩阵：构建命令时需要。
#include "ffmpeg_locator.h"

// 状态短名表。
QString taskStatusText(TaskStatus status)
{
    // 逐项映射（值均为英文，会显示在队列表格中）。
    switch (status)
    {
    // 等待。
    case TaskStatus::Waiting: return QStringLiteral("Waiting");
    // 运行中。
    case TaskStatus::Running: return QStringLiteral("Running");
    // 完成。
    case TaskStatus::Done: return QStringLiteral("Done");
    // 失败。
    case TaskStatus::Failed: return QStringLiteral("Failed");
    // 已取消。
    case TaskStatus::Canceled: return QStringLiteral("Canceled");
    // 已跳过。
    case TaskStatus::Skipped: return QStringLiteral("Skipped");
    }
    // 兜底（理论上不可达，但必须返回值以免编译器告警）。
    return QStringLiteral("Unknown");
}

// 任务是否已进入终态。
bool Task::isFinished() const
{
    // 四种终态之一即视为结束。
    return status == TaskStatus::Done || status == TaskStatus::Failed
        || status == TaskStatus::Canceled || status == TaskStatus::Skipped;
}

// ---------------------- 执行体 ----------------------

// 构造。
TranscodeJob::TranscodeJob(const QString& tid, const TranscodeSettings& settings, QObject* parent)
    : QObject(parent)
      , m_tid(tid)
      , m_settings(settings)
{
    // 取消标志初始为 0。
    m_cancelRequested.storeRelaxed(0);
    // 执行器指针初始为空。
    m_runner.storeRelaxed(nullptr);
}

// 请求取消。
void TranscodeJob::requestCancel()
{
    // 置位取消标志。
    m_cancelRequested.storeRelease(1);
    // 取出当前执行器（以获取语义读取，保证能看到另一个线程的写入）。
    Runner* r = m_runner.loadAcquire();
    // 执行器存在时把取消请求转发给它（Runner::requestCancel 只做原子写，跨线程安全）。
    if (r != nullptr)
    {
        // 转发取消。
        r->requestCancel();
    }
}

// 执行一次命令并记录执行器句柄。
RunResult TranscodeJob::runOnce(const QStringList& args, double durationSeconds)
{
    // 新建执行器（父对象为本对象，随本对象销毁一并回收）。
    Runner* runner = new Runner(this);
    // 登记句柄，使 requestCancel 能命中它。
    m_runner.storeRelease(runner);
    // 把执行器的进度信号转成本对象的进度信号（跨线程时自动排队到主线程）。
    connect(runner, &Runner::progressChanged, this,
            [this](const ProgressInfo& info)
            {
                // 转发进度（附带任务 ID 便于界面过滤）。
                emit progress(m_tid, info);
            });
    // 同步执行（阻塞本工作线程，直到进程结束或被取消）。
    const RunResult res = runner->run(args, durationSeconds);
    // 注销句柄（避免取消请求误发到已结束的执行器）。
    m_runner.storeRelease(nullptr);
    // 删除执行器（不再需要）。
    runner->deleteLater();
    // 返回结果。
    return res;
}

// 执行整个任务。
void TranscodeJob::execute()
{
    // 一个“统一失败”的辅助 lambda，减少重复代码。
    auto failWith = [this](const Diagnosis& diag)
    {
        // 记录诊断信息。
        m_diagnosis = diag;
        // 上报失败结果。
        emit finished(m_tid, false, false, m_outPath, 0, m_elapsed, m_diagnosis);
    };

    // ---- 1. 读取 ffmpeg 路径 ----
    const QString ffmpegExe = FFmpegLocator::instance().ffmpegPath();
    // 内核缺失时直接失败（这条信息必须对用户可见，因此用英文）。
    if (ffmpegExe.isEmpty())
    {
        // 构造诊断。
        Diagnosis diag;
        // 错误码。
        diag.code = QStringLiteral("BAD_ARGS");
        // 标题。
        diag.title = QStringLiteral("FFmpeg was not found");
        // 建议。
        diag.advice = QStringLiteral("Open the Settings tab and point it to a valid ffmpeg/ffprobe location.");
        // 上报失败。
        failWith(diag);
        // 结束执行。
        return;
    }

    // ---- 2. 探测源文件 ----
    emit logLine(m_tid, QStringLiteral("Probing source: %1").arg(m_settings.source));
    // 执行探测（工作线程内的阻塞调用）。
    const MediaInfo info = probeMedia(m_settings.source);
    // 记录探测摘要（若探测失败，摘要文本为空，则输出错误原因）。
    emit logLine(m_tid, info.probeOk
                            ? QStringLiteral("Source info: %1").arg(info.summaryLine())
                            : QStringLiteral("Probe failed: %1").arg(info.error));
    // 源文件不存在：直接判失败，不必调用 ffmpeg。
    if (!info.exists)
    {
        // 构造诊断。
        Diagnosis diag;
        // 错误码。
        diag.code = QStringLiteral("FILE_MISSING");
        // 标题。
        diag.title = QStringLiteral("File missing or unreadable");
        // 建议。
        diag.advice = QStringLiteral("Make sure the file still exists and is readable.");
        // 上报失败。
        failWith(diag);
        // 结束执行。
        return;
    }

    // ---- 3. 计算输出路径 ----
    m_outPath = computeOutputPath(m_settings, m_settings.overwrite);
    // 确保输出目录存在（用户可能指定了尚未创建的目录）。
    const QString outDir = QFileInfo(m_outPath).absolutePath();
    // 目录非空才创建。
    if (!outDir.isEmpty())
    {
        // 递归创建。
        QDir().mkpath(outDir);
    }
    // 日志记录输出路径。
    emit logLine(m_tid, QStringLiteral("Output: %1").arg(m_outPath));

    // ---- 4. 构建命令 ----
    const QStringList args = buildCommand(m_settings, &info, m_outPath);
    // 把命令文本写入日志，方便用户核对参数（这是本项目“可审计”的关键）。
    emit logLine(m_tid, QStringLiteral("Command: %1").arg(describeCommand(args)));

    // ---- 5. 执行（第一次尝试）----
    RunResult result = runOnce(args, info.duration);
    // 累加耗时。
    m_elapsed += result.elapsed;

    // 用户取消：直接按取消上报（不影响其它任务）。
    if (result.canceled)
    {
        // 构造一个“用户取消”的诊断。
        Diagnosis diag;
        // 错误码。
        diag.code = QStringLiteral("CANCELED");
        // 标题。
        diag.title = QStringLiteral("Canceled by user");
        // 上报取消。
        emit finished(m_tid, false, true, m_outPath, 0, m_elapsed, diag);
        // 结束执行。
        return;
    }

    // 成功：上报完成。
    if (result.ok())
    {
        // 记录产物信息。
        emit logLine(m_tid, QStringLiteral("Success: %1 bytes written.").arg(result.outSize));
        // 上报成功。
        emit finished(m_tid, true, false, m_outPath, result.outSize, m_elapsed, Diagnosis());
        // 结束执行。
        return;
    }

    // 返回码正常但没有产出（例如源文件无有效流可转换）。
    if (result.returnCode == 0 && result.outSize == 0)
    {
        // 构造诊断。
        Diagnosis diag;
        // 错误码。
        diag.code = QStringLiteral("NO_STREAM");
        // 标题。
        diag.title = QStringLiteral("Output is empty");
        // 建议。
        diag.advice = QStringLiteral(
            "The source may contain no convertible audio/video stream, or the target format does not fit this content.");
        // 详情。
        diag.detail = result.stderrText.right(1500);
        // 上报失败。
        failWith(diag);
        // 结束执行。
        return;
    }

    // ---- 6. 错误分类与自动补救 ----
    Diagnosis diag = classifyError(result.stderrText, result.returnCode);
    // 记录首次失败的原因。
    emit logLine(m_tid, QStringLiteral("Attempt 1 failed: %1").arg(diag.title));

    // 命中可自动补救的错误类型且用户没有中途取消时才重试。
    if (diag.recoverable && !m_cancelRequested.loadAcquire())
    {
        // 准备第二次尝试的命令。
        QStringList retryArgs;
        // 区分两类补救方式。
        if (diag.code == QStringLiteral("HW_ACCEL"))
        {
            // 硬件编码失败：整套替换为软件编码器后重建命令。
            const TranscodeSettings fallback = softwareFallback(m_settings);
            // 重建命令（沿用同一个输出路径）。
            retryArgs = buildCommand(fallback, &info, m_outPath);
            // 记录补救决策。
            emit logLine(m_tid, QStringLiteral("Retry: rebuilding the command with a software encoder (%1).")
                         .arg(fallback.videoCodec));
        }
        else
        {
            // 其它可恢复错误（奇数分辨率）：把补救参数插入到输出路径之前。
            // 注意：命令构建器保证输出路径是最后一个参数。
            retryArgs = args;
            // 取出输出路径（最后一个元素）。
            const QString lastOut = retryArgs.takeLast();
            // 逐个插入补救参数。
            for (const QString& a : diag.retryArgs)
            {
                // 追加到末尾（此时末尾就是原先输出路径的位置）。
                retryArgs << a;
            }
            // 把输出路径放回最后。
            retryArgs << lastOut;
            // 记录补救决策。
            emit logLine(
                m_tid, QStringLiteral("Retry: applying corrective filters (%1).").arg(
                    diag.retryArgs.join(QLatin1Char(' '))));
        }
        // 把重试命令也写入日志，保证过程可审计。
        emit logLine(m_tid, QStringLiteral("Retry command: %1").arg(describeCommand(retryArgs)));
        // 执行第二次尝试。
        RunResult result2 = runOnce(retryArgs, info.duration);
        // 累加耗时。
        m_elapsed += result2.elapsed;
        // 用户在这期间取消：按取消上报。
        if (result2.canceled)
        {
            // 构造取消诊断。
            Diagnosis cancelDiag;
            // 错误码。
            cancelDiag.code = QStringLiteral("CANCELED");
            // 标题。
            cancelDiag.title = QStringLiteral("Canceled by user");
            // 上报取消。
            emit finished(m_tid, false, true, m_outPath, 0, m_elapsed, cancelDiag);
            // 结束执行。
            return;
        }
        // 第二次尝试成功：按成功上报。
        if (result2.ok())
        {
            // 记录成功日志。
            emit logLine(m_tid, QStringLiteral("Retry succeeded: %1 bytes written.").arg(result2.outSize));
            // 上报成功。
            emit finished(m_tid, true, false, m_outPath, result2.outSize, m_elapsed, Diagnosis());
            // 结束执行。
            return;
        }
        // 第二次仍失败：用第二次的错误信息覆盖诊断，保证用户看到的是最新的原因。
        diag = classifyError(result2.stderrText, result2.returnCode);
        // 记录重试失败。
        emit logLine(m_tid, QStringLiteral("Retry failed: %1").arg(diag.title));
        // 更新结果对象以便后面统一处理。
        result = result2;
    }

    // ---- 7. 统一失败上报 ----
    failWith(diag);
}

// ---------------------- 队列 ----------------------

// 构造。
TaskQueue::TaskQueue(QObject* parent)
    : QObject(parent)
{
    // 默认并发上限为 1：机械硬盘上串行最稳，也便于用户观察进度。
    m_maxParallel = 1;
}

// 析构。
TaskQueue::~TaskQueue()
{
    // 先请求停止。
    stopAll();
    // 取出所有活动线程。
    const QList<ActiveJob> jobs = m_active.values();
    // 逐个退出并等待回收，避免程序退出时留下仍在写文件的后台线程。
    for (const ActiveJob& aj : jobs)
    {
        // 线程存在则请求退出。
        if (aj.thread != nullptr)
        {
            // 请求退出事件循环。
            aj.thread->quit();
            // 最多等 3 秒（避免退出被无限拖延）。
            aj.thread->wait(3000);
        }
    }
    // 清空活动表。
    m_active.clear();
}

// 添加任务。
QString TaskQueue::addTask(const TranscodeSettings& settings, bool dedup, bool* skipped)
{
    // 先把“是否被跳过”的输出参数置为假，保证调用方读到确定的值。
    if (skipped) { *skipped = false; }
    // 源文件绝对路径（用于去重比对与显示）。
    const QString srcAbs = QFileInfo(settings.source).absoluteFilePath();
    // 判定本次提交是否命中重复。
    bool isDuplicate = false;
    // ---- 去重检查 ----
    if (dedup)
    {
        // 遍历现有任务。
        for (const Task& t : m_tasks)
        {
            // 比对“同一源 + 同一目标格式 + 未结束”。
            if (QFileInfo(t.settings.source).absoluteFilePath() == srcAbs
                && t.settings.targetFormat == settings.targetFormat
                && !t.isFinished())
            {
                // 命中重复。
                isDuplicate = true;
                // 记录日志。
                qInfo("[queue] duplicate detected: %s -> %s", qPrintable(settings.source),
                      qPrintable(settings.targetFormat));
                // 结束比对。
                break;
            }
        }
    }
    // ---- 建立任务 ----
    Task task;
    // 生成唯一 ID：时间戳 + 递增序号，保证单调且可读。
    task.tid = QStringLiteral("t%1_%2").arg(QDateTime::currentMSecsSinceEpoch()).arg(++m_seq);
    // 记录源文件显示名。
    task.fileName = QFileInfo(srcAbs).fileName();
    // 复制设置（隔离外部对象，避免调用方后续改动影响队列内任务）。
    task.settings = settings;
    // 状态：重复任务直接进入 Skipped 终态，正常任务进入 Waiting。
    task.status = isDuplicate ? TaskStatus::Skipped : TaskStatus::Waiting;
    // 重复任务的错误标题用于解释“为什么被跳过”。
    if (isDuplicate)
    {
        // 说明文本（英文，会显示在队列表格中）。
        task.errTitle = QStringLiteral("Skipped: an identical task is already queued");
        // 错误码用于状态列的分类展示。
        task.errCode = QStringLiteral("DUPLICATE");
    }
    // 把“被跳过”回填给调用方。
    if (skipped) { *skipped = isDuplicate; }
    // 记录源文件大小（可读时）。
    const QFileInfo srcInfo(srcAbs);
    // 文件存在才取大小。
    if (srcInfo.exists() && srcInfo.isFile())
    {
        // 记录字节数。
        task.inSize = srcInfo.size();
    }
    // 追加到任务列表。
    m_tasks.append(task);
    // 只有正常任务才进入待办队列；被跳过的任务不应被执行。
    if (!isDuplicate)
    {
        // 追加到待办队列。
        m_pending.append(task.tid);
    }
    // 通知界面新增。
    emit taskAdded(task.tid);
    // 再通知一次状态（重复任务的终态需要被界面刷新出来）。
    if (isDuplicate)
    {
        // 通知状态变化。
        emit taskChanged(task.tid);
    }
    // 若调度已处于运行状态，立刻尝试启动它（被跳过的任务不会进入待办队列，因此不会被启动）。
    pump();
    // 返回新任务 ID。
    return task.tid;
}

// 任务快照。
QVector<Task> TaskQueue::tasks() const
{
    // 直接按值返回副本。
    return m_tasks;
}

// 按 ID 查找。
Task TaskQueue::taskById(const QString& tid) const
{
    // 线性查找（任务规模有限，无需哈希索引）。
    for (const Task& t : m_tasks)
    {
        // 命中即返回。
        if (t.tid == tid) { return t; }
    }
    // 未找到时返回默认构造的空任务（其 tid 为空）。
    return Task();
}

// 移除任务。
bool TaskQueue::removeTask(const QString& tid)
{
    // 正在运行的任务不可直接移除。
    if (m_active.contains(tid)) { return false; }
    // 记录是否真的移除了任务。
    bool removed = false;
    // 重建任务列表（跳过目标项）。
    QVector<Task> kept;
    // 遍历。
    for (const Task& t : m_tasks)
    {
        // 命中目标则丢弃并标记。
        if (t.tid == tid)
        {
            // 标记已移除。
            removed = true;
            // 跳过该项。
            continue;
        }
        // 保留该项。
        kept.append(t);
    }
    // 若未命中则直接返回失败。
    if (!removed) { return false; }
    // 用保留后的列表替换原列表。
    m_tasks = kept;
    // 从待办队列中移除该 ID。
    m_pending.removeAll(tid);
    // 通知界面刷新。
    emit taskChanged(tid);
    // 检查是否已全部结束。
    checkAllFinished();
    // 返回成功。
    return true;
}

// 清空任务列表。
int TaskQueue::clear(bool includeRunning)
{
    // 需要连运行中的任务一起清理时先取消全部。
    if (includeRunning)
    {
        // 取消所有活动任务。
        stopAll();
    }
    // 统计清理数量。
    int removed = 0;
    // 保留列表。
    QVector<Task> kept;
    // 遍历。
    for (const Task& t : m_tasks)
    {
        // 不清理运行中的任务时，保留“等待中”和“运行中”的任务。
        if (!includeRunning && (t.status == TaskStatus::Waiting || t.status == TaskStatus::Running))
        {
            // 保留。
            kept.append(t);
            // 跳过计数。
            continue;
        }
        // 计入清理数量。
        ++removed;
    }
    // 替换任务列表。
    m_tasks = kept;
    // 重建待办队列：只保留仍然存在且处于等待状态的任务。
    QStringList pending;
    // 遍历保留后的任务。
    for (const Task& t : kept)
    {
        // 等待中的才入队。
        if (t.status == TaskStatus::Waiting)
        {
            // 追加。
            pending.append(t.tid);
        }
    }
    // 替换待办队列。
    m_pending = pending;
    // 检查是否全部结束。
    checkAllFinished();
    // 返回清理数量。
    return removed;
}

// 设置并发上限。
void TaskQueue::setMaxParallel(int n)
{
    // 至少为 1。
    m_maxParallel = qMax(1, n);
    // 上限放宽后立即尝试多启动几个任务。
    pump();
}

// 读取并发上限。
int TaskQueue::maxParallel() const
{
    // 返回当前值。
    return m_maxParallel;
}

// 启动调度。
void TaskQueue::start()
{
    // 已运行则不重复启动。
    if (m_running && !m_stopped) { return; }
    // 重置停止与暂停标志。
    m_stopped = false;
    // 暂停标志清零。
    m_paused = false;
    // 标记运行中。
    m_running = true;
    // 尝试启动任务。
    pump();
}

// 暂停。
void TaskQueue::pause()
{
    // 只置暂停标志：正在运行的任务会继续跑完，符合用户直觉。
    m_paused = true;
}

// 恢复。
void TaskQueue::resume()
{
    // 清除暂停标志。
    m_paused = false;
    // 若调度此前未启动则一并启动。
    m_running = true;
    // 立即尝试启动任务。
    pump();
}

// 停止全部。
void TaskQueue::stopAll()
{
    // 标记已停止。
    m_stopped = true;
    // 停止调度标志。
    m_running = false;
    // 取消全部活动任务。
    const QList<QString> tids = m_active.keys();
    // 逐个取消。
    for (const QString& tid : tids)
    {
        // 触发取消。
        cancelTask(tid);
    }
    // 把等待中的任务标记为已取消（它们不会被执行）。
    for (Task& t : m_tasks)
    {
        // 仅处理等待中的。
        if (t.status == TaskStatus::Waiting)
        {
            // 置为已取消。
            t.status = TaskStatus::Canceled;
            // 通知界面。
            emit taskChanged(t.tid);
        }
    }
    // 清空待办队列（所有等待项都已被取消）。
    m_pending.clear();
    // 检查是否全部结束。
    checkAllFinished();
}

// 是否运行中。
bool TaskQueue::isRunning() const
{
    // 同时满足“已启动”与“未被停止”。
    return m_running && !m_stopped;
}

// 是否暂停。
bool TaskQueue::isPaused() const
{
    // 返回暂停标志。
    return m_paused;
}

// 取消单个任务。
void TaskQueue::cancelTask(const QString& tid)
{
    // 先处理活动任务。
    if (m_active.contains(tid))
    {
        // 取出执行体并请求取消。
        const ActiveJob aj = m_active.value(tid);
        // 存在即请求取消（原子写，跨线程安全）。
        if (aj.job != nullptr)
        {
            // 转发取消请求。
            aj.job->requestCancel();
        }
        // 取消动作完成（最终状态由执行体上报后更新）。
        return;
    }
    // 处理等待中的任务：直接标记为已取消并移出待办队列。
    for (Task& t : m_tasks)
    {
        // 命中且处于等待状态。
        if (t.tid == tid && t.status == TaskStatus::Waiting)
        {
            // 置为已取消。
            t.status = TaskStatus::Canceled;
            // 从待办队列移除。
            m_pending.removeAll(tid);
            // 通知界面。
            emit taskChanged(tid);
            // 检查是否全部结束。
            checkAllFinished();
            // 结束。
            return;
        }
    }
}

// 统计各状态数量。
QMap<QString, int> TaskQueue::stats() const
{
    // 结果容器。
    QMap<QString, int> s;
    // 初始化各计数（size() 返回 qsizetype，这里显式转成 int 以匹配映射的取值类型）。
    s.insert(QStringLiteral("total"), int(m_tasks.size()));
    // 完成数。
    s.insert(QStringLiteral("ok"), 0);
    // 失败数。
    s.insert(QStringLiteral("fail"), 0);
    // 跳过数。
    s.insert(QStringLiteral("skip"), 0);
    // 取消数。
    s.insert(QStringLiteral("canceled"), 0);
    // 等待数。
    s.insert(QStringLiteral("waiting"), 0);
    // 运行数。
    s.insert(QStringLiteral("running"), 0);
    // 遍历任务累计。
    for (const Task& t : m_tasks)
    {
        // 完成。
        if (t.status == TaskStatus::Done) { s[QStringLiteral("ok")] += 1; }
        // 失败。
        else if (t.status == TaskStatus::Failed) { s[QStringLiteral("fail")] += 1; }
        // 跳过。
        else if (t.status == TaskStatus::Skipped) { s[QStringLiteral("skip")] += 1; }
        // 取消。
        else if (t.status == TaskStatus::Canceled) { s[QStringLiteral("canceled")] += 1; }
        // 等待。
        else if (t.status == TaskStatus::Waiting) { s[QStringLiteral("waiting")] += 1; }
        // 运行。
        else if (t.status == TaskStatus::Running) { s[QStringLiteral("running")] += 1; }
    }
    // 返回统计。
    return s;
}

// 调度核心：按并发上限启动等待中的任务。
void TaskQueue::pump()
{
    // 未运行、已停止或被暂停时不取新任务。
    if (!m_running || m_stopped || m_paused) { return; }
    // 逐个尝试启动，直到达到并发上限或没有可启动的任务。
    while (!m_pending.isEmpty() && m_active.size() < m_maxParallel)
    {
        // 取出第一个待办任务 ID。
        const QString tid = m_pending.takeFirst();
        // 找到对应的任务记录。
        Task* taskPtr = nullptr;
        // 遍历查找。
        for (int i = 0; i < m_tasks.size(); ++i)
        {
            // 命中即记录指针。
            if (m_tasks[i].tid == tid)
            {
                taskPtr = &m_tasks[i];
                break;
            }
        }
        // 任务已被移除或状态不再是等待：跳过它。
        if (taskPtr == nullptr || taskPtr->status != TaskStatus::Waiting) { continue; }
        // 标记为运行中。
        taskPtr->status = TaskStatus::Running;
        // 进度归零（重新开始时保证界面显示一致）。
        taskPtr->progress = 0.0;
        // 速度归零。
        taskPtr->speed = 0.0;
        // 剩余时间重置为未知。
        taskPtr->eta = -1.0;
        // 复制设置（避免在工作线程中共享可变对象）。
        const TranscodeSettings settingsCopy = taskPtr->settings;
        // 通知界面状态已变更。
        emit taskChanged(tid);

        // 创建工作线程。
        QThread* thread = new QThread(this);
        // 创建执行体（无父对象，随后移动到工作线程）。
        TranscodeJob * job = new TranscodeJob(tid, settingsCopy, nullptr);
        // 把执行体移入工作线程。
        job->moveToThread(thread);
        // 线程启动后立即执行任务。
        connect(thread, &QThread::started, job, &TranscodeJob::execute);
        // 执行体进度信号 → 队列槽（自动队列连接，切回本线程）。
        connect(job, &TranscodeJob::progress, this, &TaskQueue::onJobProgress);
        // 执行体日志信号 → 队列槽。
        connect(job, &TranscodeJob::logLine, this, &TaskQueue::onJobLog);
        // 执行体结束信号 → 队列槽。
        connect(job, &TranscodeJob::finished, this, &TaskQueue::onJobFinished);
        // 线程结束后自动回收执行体对象。
        connect(thread, &QThread::finished, job, &QObject::deleteLater);
        // 线程结束后自动回收线程对象本身。
        connect(thread, &QThread::finished, thread, &QObject::deleteLater);
        // 登记到活动表。
        ActiveJob aj;
        // 记录线程。
        aj.thread = thread;
        // 记录执行体。
        aj.job = job;
        // 写入活动表。
        m_active.insert(tid, aj);
        // 启动线程。
        thread->start();
    }
}

// 检查是否全部结束。
void TaskQueue::checkAllFinished()
{
    // 还有活动任务或待办任务时不视为结束。
    if (!m_active.isEmpty() || !m_pending.isEmpty()) { return; }
    // 还有等待中的任务时不视为结束（例如被暂停时未取出的任务）。
    for (const Task& t : m_tasks)
    {
        // 命中等待状态即返回。
        if (t.status == TaskStatus::Waiting) { return; }
    }
    // 发出“全部结束”信号。
    emit allFinished();
}

// 响应进度信号。
void TaskQueue::onJobProgress(const QString& tid, const ProgressInfo& info)
{
    // 找到任务并更新进度字段。
    for (int i = 0; i < m_tasks.size(); ++i)
    {
        // 命中目标任务。
        if (m_tasks[i].tid == tid)
        {
            // 更新百分比（结束时归 100）。
            m_tasks[i].progress = info.done ? 100.0 : info.percent;
            // 更新速度。
            m_tasks[i].speed = info.speed;
            // 更新剩余时间。
            m_tasks[i].eta = info.eta;
            // 通知界面刷新进度列（高频信号，界面应做轻量刷新）。
            emit taskProgressTick(tid, m_tasks[i].progress, m_tasks[i].speed, m_tasks[i].eta);
            // 找到即结束循环。
            break;
        }
    }
}

// 响应日志信号。
void TaskQueue::onJobLog(const QString& tid, const QString& text)
{
    // 转发日志到界面。
    emit taskLog(tid, text);
}

// 响应结束信号。
void TaskQueue::onJobFinished(const QString& tid, bool ok, bool canceled, const QString& outPath,
                              qint64 outSize, double elapsed, const Diagnosis& diagnosis)
{
    // 更新任务结果字段。
    for (int i = 0; i < m_tasks.size(); ++i)
    {
        // 命中目标任务。
        if (m_tasks[i].tid != tid) { continue; }
        // 记录输出路径。
        m_tasks[i].outPath = outPath;
        // 记录耗时。
        m_tasks[i].elapsed = elapsed;
        // 成功路径。
        if (ok)
        {
            // 置为完成。
            m_tasks[i].status = TaskStatus::Done;
            // 进度推到 100%。
            m_tasks[i].progress = 100.0;
            // 记录产物大小。
            m_tasks[i].outSize = outSize;
            // 速度与剩余时间不再有意义。
            m_tasks[i].eta = 0.0;
        }
        else if (canceled)
        {
            // 置为已取消。
            m_tasks[i].status = TaskStatus::Canceled;
            // 记录错误标题。
            m_tasks[i].errTitle = diagnosis.title;
            // 记录错误码。
            m_tasks[i].errCode = diagnosis.code;
        }
        else
        {
            // 置为失败。
            m_tasks[i].status = TaskStatus::Failed;
            // 记录错误码。
            m_tasks[i].errCode = diagnosis.code;
            // 记录错误标题（为空时用错误码的短名兜底）。
            m_tasks[i].errTitle = diagnosis.title.isEmpty() ? errorShortName(diagnosis.code) : diagnosis.title;
            // 记录建议。
            m_tasks[i].errAdvice = diagnosis.advice;
            // 记录详情。
            m_tasks[i].errDetail = diagnosis.detail;
        }
        // 通知界面状态变化。
        emit taskChanged(tid);
        // 找到即结束循环。
        break;
    }

    // 从活动表取出该任务并回收线程。
    if (m_active.contains(tid))
    {
        // 取出记录。
        const ActiveJob aj = m_active.take(tid);
        // 请求线程退出事件循环。
        if (aj.thread != nullptr)
        {
            // 退出（随后自动触发 finished，从而回收两个对象）。
            aj.thread->quit();
        }
    }

    // 继续启动后续任务（调度可能因为并发上限而暂停在此）。
    pump();
    // 检查是否全部结束。
    checkAllFinished();
}