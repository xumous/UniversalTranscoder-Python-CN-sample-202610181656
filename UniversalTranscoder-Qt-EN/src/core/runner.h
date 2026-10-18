// *****************************************************************************
// runner.h —— 子进程执行器（本项目算法核心之二）。
// 职责：执行 ffmpeg 命令，并把“机器可读的进度流”解析成结构化进度事件。
// 关键技术点：
//   1) 使用 `-progress pipe:1`：ffmpeg 会把形如 `key=value` 的进度块按固定节奏写到标准输出，
//      比解析人类可读的 stderr（形如 frame=... time=...）稳定得多，也不受语言环境影响。
//      该选项必须插在**输出文件路径之前**（它属于输出侧选项，放在最后会被判为无效）。
//   2) 进度块的终止标志是 `progress=continue` 或 `progress=end`，据此切分记录。
//   3) 取消机制：先 terminate()，2 秒内未退出再 kill()，
//      确保“Stop”按钮点击后能迅速回收资源，而不是留下继续占满 CPU 的僵尸进程。
//   4) Runner 是一个 QObject，但 run() 是**同步阻塞**的，必须放在工作线程里调用；
//      它发出的信号会自动以队列连接（queued connection）切换到主线程，
//      因此界面层无需自己处理跨线程问题。
// *****************************************************************************
#pragma once

// QObject：信号槽基类。
#include <QObject>
// QString / QStringList：命令与文本。
#include <QString>
#include <QStringList>
// QAtomicInt：跨线程无锁的取消标志。
#include <QAtomicInt>
// QMetaType：让 ProgressInfo 可被队列连接的信号传递。
#include <QMetaType>

// 一次进度更新。
struct ProgressInfo
{
    // 进度百分比（0 ~ 100）。
    double percent = 0.0;
    // 已编码的媒体时间（秒）。
    double outTime = 0.0;
    // 当前编码帧率。
    double fps = 0.0;
    // 相对实时的倍速（1.0 表示 1 倍速）。
    double speed = 0.0;
    // 预计剩余秒数（-1 表示未知）。
    double eta = -1.0;
    // 已编码帧数。
    qint64 frame = 0;
    // 当前输出已写入字节数。
    qint64 totalSize = 0;
    // 当前码率文本。
    QString bitrate;
    // 是否已结束。
    bool done = false;
};

// 让 ProgressInfo 能通过跨线程的队列信号传递。
Q_DECLARE_METATYPE(ProgressInfo)

// 一次执行的结果。
struct RunResult
{
    // 进程返回码（0 表示成功）。
    int returnCode = -1;
    // 收集到的错误输出（用于诊断）。
    QString stderrText;
    // 总耗时（秒）。
    double elapsed = 0.0;
    // 输出文件路径。
    QString outPath;
    // 输出文件大小（字节）。
    qint64 outSize = 0;
    // 是否被用户取消。
    bool canceled = false;

    // 是否成功：返回码为 0、未被取消、且输出文件存在且非空。
    bool ok() const
    {
        // 三项条件同时成立才算成功。
        return returnCode == 0 && !canceled && outSize > 0;
    }
};

// 转码执行器。
class Runner : public QObject
{
    Q_OBJECT

public:
    // 构造：可指定父对象（通常为空，因为对象会被移动到工作线程）。
    explicit Runner(QObject* parent = nullptr);

    // 请求取消当前任务；可在任意线程调用（只置原子标志，不直接碰进程对象）。
    void requestCancel();

    // 是否已被请求取消。
    bool isCancelRequested() const;

    // 同步执行一条 ffmpeg 命令（必须工作线程中调用）。
    // args 为完整参数列表（含 ffmpeg 路径与输出路径）；durationSeconds 为源总时长（0 表示未知）。
    RunResult run(const QStringList& args, double durationSeconds = 0.0);

    signals :
    // 进度更新信号（跨线程时为队列连接）。
    
    void progressChanged(const ProgressInfo& info);

    // 进程已启动信号，携带进程号（用于排障与界面展示）。
    void processStarted(qint64 pid);

private:
    // 取消标志（原子操作，跨线程安全）。
    QAtomicInt m_cancelFlag;
};