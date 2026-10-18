// *****************************************************************************
// taskqueue.h —— 任务模型与任务队列（本项目算法核心之三）。
// 职责：
//   1) 用 Task 描述“一个待转码文件 + 它的转码设置 + 运行状态 + 结果”。
//   2) 用 TaskQueue 负责调度：按并发上限取任务、驱动执行器、把进度/状态变化上报给界面。
//   3) 维护状态机：Waiting → Running →（Done | Failed | Canceled | Skipped），
//      状态迁移单向且幂等。
// 线程模型（这是本模块最关键的约定）：
//   - 队列对象本身只存在于主线程；所有任务列表的写入都在主线程完成，因此无需额外加锁。
//   - 每个任务在独立的工作线程（QThread）里执行，执行体是 TranscodeJob。
//   - 执行体通过信号上报进度与结果；由于发送者在工作线程、接收者在主线程，
//     Qt 会自动使用队列连接（queued connection）把回调切回主线程，界面因此可以安全地更新控件。
//   - “取消”通过原子标志传递，绝不跨线程直接操作 QProcess 对象。
// *****************************************************************************
#pragma once

// QObject：信号槽基类。
#include <QObject>
// QString / QStringList：文本与参数。
#include <QString>
#include <QStringList>
// QVector / QHash / QMap：承载任务列表与统计。
#include <QVector>
#include <QHash>
#include <QMap>
// QAtomicInt / QAtomicPointer：跨线程的取消标志与执行器句柄。
#include <QAtomicInt>
#include <QAtomicPointer>
// 设置结构与探测结果。
#include "commandbuilder.h"
#include "mediainfo.h"
#include "runner.h"
#include "errors.h"

// 任务状态枚举。
enum class TaskStatus
{
    // 等待执行。
    Waiting,
    // 正在执行。
    Running,
    // 成功完成。
    Done,
    // 失败。
    Failed,
    // 被取消。
    Canceled,
    // 被跳过（如去重命中）。
    Skipped
};

// 状态 → 英文短名（会显示在队列表格的 Status 列）。
QString taskStatusText(TaskStatus status);

// 单个转码任务。
struct Task
{
    // 任务唯一 ID。
    QString tid;
    // 源文件显示名（文件名部分）。
    QString fileName;
    // 转码设置。
    TranscodeSettings settings;
    // 当前状态。
    TaskStatus status = TaskStatus::Waiting;
    // 进度百分比（0 ~ 100）。
    double progress = 0.0;
    // 当前速度倍率。
    double speed = 0.0;
    // 预计剩余秒数（-1 表示未知）。
    double eta = -1.0;
    // 计算出的输出路径。
    QString outPath;
    // 错误码。
    QString errCode;
    // 错误标题。
    QString errTitle;
    // 错误详情（原始 stderr 尾部）。
    QString errDetail;
    // 处理建议。
    QString errAdvice;
    // 耗时（秒）。
    double elapsed = 0.0;
    // 源文件大小。
    qint64 inSize = 0;
    // 输出文件大小。
    qint64 outSize = 0;
    // 是否已进入终态。
    bool isFinished() const;
};

// 工作线程中的执行体：完成“探测 → 计算输出 → 构建命令 → 执行 → 失败补救 → 结束”的全过程。
class TranscodeJob : public QObject
{
    Q_OBJECT

public:
    // 构造：tid 为任务 ID，settings 为转码设置（构造时复制一份，线程启动后不再被外部修改）。
    TranscodeJob(const QString& tid, const TranscodeSettings& settings, QObject* parent = nullptr);

    // 请求取消；可在任意线程调用。
    void requestCancel();

public
    slots :
    // 在工作线程中执行整个任务。
    
    void execute();

    signals :
    // 进度上报。
    
    void progress(const QString& tid, const ProgressInfo& info);

    // 日志上报（携带任务 ID，便于界面把日志追加到对应面板）。
    void logLine(const QString& tid, const QString& text);

    // 任务结束上报：成功时 ok 为真；失败时携带诊断信息。
    void finished(const QString& tid, bool ok, bool canceled, const QString& outPath,
                  qint64 outSize, double elapsed, const Diagnosis& diagnosis);

private:
    // 任务 ID。
    QString m_tid;
    // 设置副本。
    TranscodeSettings m_settings;
    // 取消请求标志。
    QAtomicInt m_cancelRequested;
    // 当前执行器指针（原子指针，保证跨线程读写安全）。
    QAtomicPointer<Runner> m_runner;
    // 记录首个失败诊断（用于最终上报）。
    Diagnosis m_diagnosis;
    // 记录输出路径。
    QString m_outPath;
    // 记录耗时。
    double m_elapsed = 0.0;

    // 内部辅助：执行一次命令并返回结果；同时把该次执行关联到本对象的取消标志。
    RunResult runOnce(const QStringList& args, double durationSeconds);
};

// 任务队列与调度器。
class TaskQueue : public QObject
{
    Q_OBJECT

public:
    // 构造；parent 用于 Qt 对象树管理。
    explicit TaskQueue(QObject* parent = nullptr);

    // 析构：停止调度并回收所有工作线程。
    ~TaskQueue() override;

    // 添加一个任务；dedup 为真时对“同一源文件 + 同一目标格式”的未完成任务去重。
    // 返回新任务的 ID（即使因去重被跳过，也会返回一个任务 ID，该任务以 Skipped 状态入列，
    // 这样界面上能看到“确实提交过但这个任务被跳过了”，而不是毫无反馈地消失）。
    // skipped 为可选输出参数：为真表示本次提交因去重被跳过。
    // 返回空字符串只会在极端情况下出现（例如源文件为空）。
    QString addTask(const TranscodeSettings& settings, bool dedup = true, bool* skipped = nullptr);

    // 返回当前全部任务的快照。
    QVector<Task> tasks() const;

    // 按 ID 查找任务；不存在时返回一个 tid 为空的任务。
    Task taskById(const QString& tid) const;

    // 移除一个尚未开始执行的任务；正在运行的任务不可移除（需先取消）。
    bool removeTask(const QString& tid);

    // 清空任务列表；includeRunning 为真时先取消所有活动任务。
    int clear(bool includeRunning = false);

    // 设置并发上限。
    void setMaxParallel(int n);

    // 读取并发上限。
    int maxParallel() const;

    // 启动调度。
    void start();

    // 暂停（只阻止取新任务，不影响正在运行的任务）。
    void pause();

    // 恢复调度。
    void resume();

    // 停止调度并取消全部活动任务。
    void stopAll();

    // 取消单个任务（等待中的直接置为已取消，运行中的触发取消）。
    void cancelTask(const QString& tid);

    // 是否处于调度状态。
    bool isRunning() const;

    // 是否处于暂停状态。
    bool isPaused() const;

    // 统计各状态数量（键为 total / ok / fail / skip / canceled / waiting / running）。
    QMap<QString, int> stats() const;

    signals :
    // 新任务加入。
    
    void taskAdded(const QString& tid);

    // 任务状态（或结果字段）发生变化。
    void taskChanged(const QString& tid);

    // 任务进度更新（高频信号，界面据此刷新进度列）。
    void taskProgressTick(const QString& tid, double percent, double speed, double eta);

    // 任务日志（供界面把文本追加到日志框）。
    void taskLog(const QString& tid, const QString& text);

    // 全部任务结束（队列空闲且无待办）。
    void allFinished();

private
    slots :
    // 响应执行体的进度信号（在队列所属线程执行）。
    
    void onJobProgress(const QString& tid, const ProgressInfo& info);

    // 响应执行体的日志信号。
    void onJobLog(const QString& tid, const QString& text);

    // 响应执行体的结束信号。
    void onJobFinished(const QString& tid, bool ok, bool canceled, const QString& outPath,
                       qint64 outSize, double elapsed, const Diagnosis& diagnosis);

private:
    // 内部辅助：尝试按并发上限启动下一个等待中的任务。
    void pump();

    // 内部辅助：检查是否所有任务都已结束并发出 allFinished 信号。
    void checkAllFinished();

    // 全部任务（含已完成）。
    QVector<Task> m_tasks;
    // 等待执行的任务 ID（按加入顺序）。
    QStringList m_pending;
    // 并发上限。
    int m_maxParallel = 1;
    // 调度是否处于运行状态。
    bool m_running = false;
    // 调度是否被暂停。
    bool m_paused = false;
    // 是否已被要求停止。
    bool m_stopped = false;

    // 活动任务：tid →（线程, 执行体）。
    struct ActiveJob
    {
        // 工作线程。
        class QThread* thread = nullptr;
        // 执行体。
        TranscodeJob* job = nullptr;
    };

    // 活动任务表。
    QHash<QString, ActiveJob> m_active;
    // 任务序号（用于生成唯一 ID）。
    int m_seq = 0;
};