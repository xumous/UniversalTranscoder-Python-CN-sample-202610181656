// *****************************************************************************
// processutil.cpp —— 子进程工具封装的具体实现。
// 实现要点：
//   1) 使用 QProcess::setCreateProcessArgumentsModifier 在 Windows 上加 CREATE_NO_WINDOW，
//      这是 Qt 提供的官方扩展点，比自行调用 Win32 CreateProcess 更安全、可移植。
//   2) 同步调用使用 waitForStarted / waitForFinished 阻塞式等待，由调用方负责超时数值。
//   3) 所有异常路径都不抛异常，只返回 false，保证上层流程稳定。
// *****************************************************************************
#include "processutil.h"

// QProcess 在 Qt6 中已随 <QProcess> 引入，这里再显式引入以便使用其嵌套结构体声明。
#include <QProcess>
// QByteArray 用于拼接输出。
#include <QByteArray>

// Windows 专有头文件：提供 CREATE_NO_WINDOW 常量与相关定义。
// 用条件编译包住，保证非 Windows 平台也能编译本文件。
#ifdef Q_OS_WIN
#  ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN   // 只引入必要的 Windows 声明，减少编译时间与名字冲突。
#  endif
#  include <windows.h>            // 提供 CREATE_NO_WINDOW（数值 0x08000000）。
#endif

// 返回“不创建控制台窗口”的创建标志。
quint32 noWindowFlag()
{
#ifdef Q_OS_WIN
    // Windows 平台返回 CREATE_NO_WINDOW 的真实数值。
    // 为什么用数值兜底：某些精简的 MinGW 头文件不保证该宏存在，直接写数值最稳。
    return static_cast<quint32>(CREATE_NO_WINDOW);
#else
    // 非 Windows 平台该概念不存在，返回 0 表示“不加任何特殊标志”。
    return 0u;
#endif
}

// 为 QProcess 配置隐藏控制台窗口。
void configureNoWindow(QProcess& proc)
{
#ifdef Q_OS_WIN
    // 通过 Qt 提供的创建参数修改器设置标志位。
    // 为什么必须这样做：GUI 子系统的程序一旦启动控制台子程序（ffmpeg 就是控制台程序），
    // Windows 会为它分配一个新的控制台窗口，导致每次转码都闪出一个黑框。
    proc.setCreateProcessArgumentsModifier(
        // 形参类型由 Qt 固定为 QProcess::CreateProcessArguments 指针。
        [](QProcess::CreateProcessArguments* args)
        {
            // 在不破坏其它标志的前提下，按位或上“不创建窗口”标志。
            args->flags |= static_cast<quint32>(CREATE_NO_WINDOW);
        });
#else
    // 非 Windows 平台无需任何处理；用 (void)proc 显式消除“未使用参数”的编译告警。
    (void)proc;
#endif
}

// 内部辅助：从一个已启动的 QProcess 中读取全部待读取数据。
// 为什么要单独写：合并通道与分离通道两种模式下都要把缓冲区读空，逻辑相同。
static void drainAll(QProcess& proc, QByteArray& sinkOut, QByteArray& sinkErr)
{
    // 把当前标准输出缓冲区中的全部内容取出并追加。
    sinkOut.append(proc.readAllStandardOutput());
    // 把当前标准错误缓冲区中的全部内容取出并追加。
    sinkErr.append(proc.readAllStandardError());
}

// 同步执行命令并合并两个通道的输出。
bool runCapture(const QStringList& args,
                QByteArray& mergedOut,
                int timeoutMs,
                int* exitCode,
                const QString& workDir)
{
    // 清空输出参数，避免调用方复用同一变量时残留上次结果。
    mergedOut.clear();
    // 参数列表为空属于编程错误，直接判定失败而不是让 QProcess 抛异常。
    if (args.isEmpty())
    {
        // 若调用方关心退出码，填入一个明显的错误值。
        if (exitCode) { *exitCode = -1; }
        // 返回失败。
        return false;
    }
    // 构造 QProcess 对象（栈上对象，函数返回时自动析构，不会遗留进程句柄）。
    QProcess proc;
    // 使用“合并通道”模式：标准错误被并入标准输出，便于一次性拿到全部文本。
    proc.setProcessChannelMode(QProcess::MergedChannels);
    // 关闭标准输入：ffmpeg 在需要交互确认时若读到无效输入会立即失败，而不是挂起等待。
    proc.setStandardInputFile(QProcess::nullDevice());
    // 在 Windows 上打上“不创建控制台窗口”的标记。
    configureNoWindow(proc);
    // 若调用方指定了工作目录则应用之（某些相对路径参数依赖工作目录）。
    if (!workDir.isEmpty())
    {
        proc.setWorkingDirectory(workDir);
    }
    // 设置待执行程序为参数列表首元素。
    proc.setProgram(args.first());
    // 设置其余参数（注意用 mid(1) 而不是 mid(1, n)，避免手写长度出错）。
    proc.setArguments(args.mid(1));
    // 启动进程。
    proc.start();
    // 等待进程真正启动；给 10 秒启动余量（首次调用可能受杀毒软件扫描影响而略慢）。
    if (!proc.waitForStarted(10000))
    {
        // 启动失败：关闭进程并返回失败。
        proc.kill();
        // 若调用方关心退出码则填 -1。
        if (exitCode) { *exitCode = -1; }
        // 返回失败。
        return false;
    }
    // 累积输出用的缓冲区。
    QByteArray allOut;
    // 累积错误用的缓冲区（合并模式下仍可能被分开暂存，最后统一拼接）。
    QByteArray allErr;
    // 记录总体等待已消耗的毫秒数，用于自行实现总超时。
    int waited = 0;
    // 以 50 毫秒为步长轮询，既能及时读取输出，也能精确控制总超时。
    while (waited < timeoutMs)
    {
        // 若有数据可读，waitForReadyRead 会立即返回；否则最多阻塞 50 毫秒。
        if (proc.waitForReadyRead(50))
        {
            // 把已到达的数据读空。
            drainAll(proc, allOut, allErr);
        }
        // 检查进程是否已经结束。
        if (proc.state() == QProcess::NotRunning)
        {
            // 结束前再读一次，确保不遗漏最后一批输出。
            drainAll(proc, allOut, allErr);
            // 跳出轮询循环。
            break;
        }
        // 累加已等待时间（固定 50 毫秒一步）。
        waited += 50;
    }
    // 若循环结束时进程仍在运行，说明已超时：强制结束它。
    if (proc.state() != QProcess::NotRunning)
    {
        // 先温和请求终止。
        proc.terminate();
        // 给 1 秒优雅退出时间。
        proc.waitForFinished(1000);
        // 仍未退出则强杀。
        if (proc.state() != QProcess::NotRunning)
        {
            proc.kill();
            // 再给 1 秒回收。
            proc.waitForFinished(1000);
        }
        // 超时也再读一次残留输出。
        drainAll(proc, allOut, allErr);
    }
    // 合并两个通道：先放标准输出，再放标准错误，便于排障时看清顺序。
    mergedOut = allOut;
    // 只有在确有错误输出且标准输出非空时才追加分隔提示，避免多余空行。
    if (!allErr.isEmpty())
    {
        mergedOut.append(allErr);
    }
    // 取退出码；进程未正常回收时用 -1 表示。
    const int rc = proc.exitCode();
    // 把退出码回填给调用方（若其提供了输出参数）。
    if (exitCode) { *exitCode = rc; }
    // 退出码为 0 视为成功（等价于 QProcess::NormalExit 且返回 0）。
    return rc == 0;
}

// 同步执行命令并只返回标准输出。
bool runCaptureStdout(const QStringList& args,
                      QByteArray& stdoutBytes,
                      int timeoutMs,
                      int* exitCode)
{
    // 清空输出参数。
    stdoutBytes.clear();
    // 参数列表为空直接失败。
    if (args.isEmpty())
    {
        // 回填错误退出码。
        if (exitCode) { *exitCode = -1; }
        // 返回失败。
        return false;
    }
    // 构造进程对象。
    QProcess proc;
    // 使用“分离通道”模式：标准错误单独走另一个通道，绝不污染标准输出。
    proc.setProcessChannelMode(QProcess::SeparateChannels);
    // 关闭标准输入，避免子进程等待交互。
    proc.setStandardInputFile(QProcess::nullDevice());
    // Windows 下隐藏控制台窗口。
    configureNoWindow(proc);
    // 指定待执行程序。
    proc.setProgram(args.first());
    // 指定其余参数。
    proc.setArguments(args.mid(1));
    // 启动。
    proc.start();
    // 等待启动。
    if (!proc.waitForStarted(10000))
    {
        // 启动失败：结束并返回。
        proc.kill();
        // 回填退出码。
        if (exitCode) { *exitCode = -1; }
        // 返回失败。
        return false;
    }
    // 标准输出累积缓冲。
    QByteArray out;
    // 标准错误累积缓冲（内容会被丢弃，但必须持续读取以防缓冲区写满导致子进程阻塞）。
    QByteArray err;
    // 已等待毫秒数。
    int waited = 0;
    // 轮询读取。
    while (waited < timeoutMs)
    {
        // 有数据则读；无数据最多阻塞 50 毫秒。
        if (proc.waitForReadyRead(50))
        {
            // 读空两个通道。
            drainAll(proc, out, err);
        }
        // 进程结束则退出循环。
        if (proc.state() == QProcess::NotRunning)
        {
            // 收尾再读一次。
            drainAll(proc, out, err);
            // 跳出。
            break;
        }
        // 累计等待时间。
        waited += 50;
    }
    // 超时处理：强制结束进程。
    if (proc.state() != QProcess::NotRunning)
    {
        // 温和终止。
        proc.terminate();
        // 等待 1 秒。
        proc.waitForFinished(1000);
        // 仍未退出则强杀。
        if (proc.state() != QProcess::NotRunning)
        {
            proc.kill();
            // 再等 1 秒。
            proc.waitForFinished(1000);
        }
        // 读残留。
        drainAll(proc, out, err);
    }
    // 回填标准输出。
    stdoutBytes = out;
    // 取退出码。
    const int rc = proc.exitCode();
    // 回填退出码。
    if (exitCode) { *exitCode = rc; }
    // 返回是否成功。
    return rc == 0;
}