// *****************************************************************************
// main.cpp —— 程序入口。
// 职责：
//   1) 建立 QApplication 并写好组织名/应用名（QSettings 依赖它们定位配置存储位置）；
//   2) 注册跨线程信号需要使用的自定义类型（ProgressInfo / Diagnosis）；
//   3) 安装按“日志级别”过滤的消息处理器（级别由 Settings 页控制）；
//   4) 解析命令行：带 --selftest 时进入无人值守自检分支，否则正常显示主窗口。
// 关于控制台输出：本程序是 Windows“窗体子系统”程序，双击运行时没有控制台。
//   因此自检分支会先尝试附加到父进程的控制台并重定向标准输出，
//   同时把结果写入程序目录下的 selftest.log，双保险保证结果一定可被看到。
// *****************************************************************************
#include <QApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QTextStream>
// QStringConverter：Qt6 中用于指定文本流的编码。
#include <QStringConverter>
#include <QStringList>
#include <QDebug>
// 主窗口、自检与日志级别。
#include "gui/mainwindow.h"
#include "gui/settingspage.h"
#include "selftest.h"

// Windows 专有：附加到父控制台并重定向标准输出。
#ifdef Q_OS_WIN
#  ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN
#  endif
#  include <windows.h>
#  include <cstdio>
#endif

// 内部辅助：把当前进程附加到父进程的控制台，使 printf / fprintf 的输出可见。
static void attachParentConsole()
{
#ifdef Q_OS_WIN
    // 只有当父进程确实拥有控制台时 AttachConsole 才会成功（从 cmd / PowerShell 启动的场景）。
    if (AttachConsole(ATTACH_PARENT_PROCESS) != 0)
    {
        // 重定向标准输出到控制台；freopen 的返回值表示成功与否，这里用变量接收以消除“未使用返回值”的告警。
        FILE* fp = nullptr;
        // 重定向标准输出。
        fp = freopen("CONOUT$", "w", stdout);
        // 显式丢弃返回值（我们无法对失败做有意义的补救）。
        (void)fp;
        // 重定向标准错误。
        fp = freopen("CONOUT$", "w", stderr);
        // 同样丢弃返回值。
        (void)fp;
    }
#endif
}

// 全局消息处理器：按 Settings 页设定的级别过滤 Qt 日志。
static void messageHandler(QtMsgType type, const QMessageLogContext& context, const QString& message)
{
    // 把消息类型映射为 0~3 的序号，数值越小越严重。
    int level = 3;
    // 错误。
    if (type == QtMsgType::QtCriticalMsg || type == QtMsgType::QtFatalMsg) { level = 0; }
    // 警告。
    else if (type == QtMsgType::QtWarningMsg) { level = 1; }
    // 信息。
    else if (type == QtMsgType::QtInfoMsg) { level = 2; }
    // 调试（QtDebugMsg）。
    else { level = 3; }
    // 把级别名映射为阈值。
    const QString configured = globalLogLevel();
    // 默认阈值 2（info）。
    int threshold = 2;
    // 错误级。
    if (configured == QStringLiteral("error")) { threshold = 0; }
    // 警告级。
    else if (configured == QStringLiteral("warning")) { threshold = 1; }
    // 调试级（全部输出）。
    else if (configured == QStringLiteral("debug")) { threshold = 3; }
    // 低于（严重于）阈值则直接丢弃，避免日志刷屏。
    if (level < threshold) { return; }
    // 组装输出文本：只在有上下文文件信息时附加位置，便于排障。
    QString line = message;
    // 附加来源（仅 debug 级时才有价值，避免刷屏）。
    if (threshold >= 3 && context.file != nullptr)
    {
        // 追加文件名与行号。
        line += QStringLiteral("   [%1:%2]").arg(QString::fromUtf8(context.file)).arg(context.line);
    }
    // 输出到标准错误（自检分支下 stderr 已重定向到控制台）。
    fprintf(stderr, "%s\n", line.toLocal8Bit().constData());
    // 手动刷新缓冲，保证长时间转码时的日志能及时看到。
    fflush(stderr);
}

// 内部辅助：把自检结果写入程序目录下的 selftest.log。
static void writeSelfTestLog(const SelfTestResult& result)
{
    // 日志文件路径：程序目录下的 selftest.log（绿色版随包分发，便于用户反馈问题）。
    const QString logPath = QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("selftest.log"));
    // 以只写方式打开（会自动截断旧内容，保证每次都是本次运行的记录）。
    QFile file(logPath);
    // 打开失败时只能放弃写文件，但仍会打印到控制台。
    if (!file.open(QIODevice::WriteOnly | QIODevice::Truncate | QIODevice::Text)) { return; }
    // 用文本流写入，保证换行在 Windows 下正确。
    QTextStream ts(&file);
    // 设置编码为 UTF-8（含中文注释或路径时不至于乱码）。
    ts.setEncoding(QStringConverter::Utf8);
    // 逐行写入。
    for (const QString& line : result.lines)
    {
        // 写入该行并换行。
        ts << line << '\n';
    }
    // 关闭文件。
    file.close();
}

// 程序入口。
int main(int argc, char* argv[])
{
    // 创建应用对象。
    QApplication app(argc, argv);
    // 设置组织名与应用名：QSettings 据此定位配置存储位置（Windows 上为注册表）。
    QApplication::setOrganizationName(QStringLiteral("Xu"));
    // 应用名。
    QApplication::setApplicationName(QStringLiteral("UniversalTranscoder"));
    // 显示给用户的程序名（窗口标题、任务管理器中的描述都由它决定）。
    QApplication::setApplicationDisplayName(QStringLiteral("万能转码器"));
    // 版本号。
    QApplication::setApplicationVersion(QStringLiteral("1.0.0"));

    // 注册自定义类型，使它们可以通过跨线程的队列信号传递（任务队列依赖这一点）。
    qRegisterMetaType<ProgressInfo>("ProgressInfo");
    // 注册诊断类型。
    qRegisterMetaType<Diagnosis>("Diagnosis");

    // 安装消息处理器（日志级别过滤）。
    qInstallMessageHandler(messageHandler);
    // 默认日志级别为 info（与 Settings 页默认值一致）。
    setGlobalLogLevel(QStringLiteral("info"));

    // 读取命令行参数。
    const QStringList args = QApplication::arguments();
    // 判断是否请求自检。
    bool selfTest = false;
    // 遍历参数。
    for (const QString& a : args)
    {
        // 命中 --selftest 即进入自检分支。
        if (a.compare(QStringLiteral("--selftest"), Qt::CaseInsensitive) == 0)
        {
            // 标记。
            selfTest = true;
            // 结束遍历。
            break;
        }
    }

    // ---- 自检分支 ----
    if (selfTest)
    {
        // 先把输出接到父控制台，让用户能在命令行里看到结果。
        attachParentConsole();
        // 运行自检。
        const SelfTestResult result = runSelfTest(args);
        // 逐行打印结果。
        for (const QString& line : result.lines)
        {
            // 每行都以换行结尾，便于在控制台按行阅读。
            printf("%s\n", line.toLocal8Bit().constData());
        }
        // 刷新标准输出。
        fflush(stdout);
        // 写入日志文件（双保险：控制台不可见时仍能确认结果）。
        writeSelfTestLog(result);
        // 打印日志文件路径，方便用户直接把日志发给我们排障。
        printf("自检日志已写入: %s\n",
               QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("selftest.log")).toLocal8Bit().
                                                            constData());
        // 刷新输出。
        fflush(stdout);
        // 返回自检退出码（0 表示通过）。
        return result.exitCode;
    }

    // ---- 正常界面分支 ----
    // 创建主窗口。
    MainWindow window;
    // 显示窗口。
    window.show();
    // 进入事件循环。
    return app.exec();
}