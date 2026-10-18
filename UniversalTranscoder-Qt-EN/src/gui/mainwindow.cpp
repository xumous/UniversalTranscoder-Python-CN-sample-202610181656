// *****************************************************************************
// mainwindow.cpp —— 主窗口的实现。
// 实现要点：
//   1) 任务队列在主窗口构造函数中创建，标签页创建完成后立即完成信号布线，
//      因此任何页面提交任务都能被队列页与对应转换页同时感知。
//   2) 队列→页面的转发使用“队列的信号 → 每个页面的槽”这一形式。
//      由于信号发送者在队列线程（主线程），接收者也在主线程，连接为直接调用；
//      而队列内部来自工作线程的信号已先排队到主线程，因此全程线程安全。
//   3) 窗口图标取自编译进 exe 的资源（:/app/icon.ico），无需磁盘文件。
// *****************************************************************************
#include "mainwindow.h"

// Qt 控件。
#include <QTabWidget>
#include <QStatusBar>
#include <QLabel>
#include <QIcon>
#include <QMessageBox>
#include <QCloseEvent>
// 各标签页。
#include "gui/convertpanel.h"
#include "gui/documentpage.h"
#include "gui/advancedpage.h"
#include "gui/queuepage.h"
#include "gui/settingspage.h"
#include "gui/aboutpage.h"
#include "gui/theme.h"
// 内核信息（状态栏与标题需要）。
#include "core/ffmpeg_locator.h"

// 构造。
MainWindow::MainWindow(QWidget* parent)
    : QMainWindow(parent)
{
    // 创建任务队列（父对象为主窗口，随窗口销毁）。
    m_queue = new TaskQueue(this);
    // 构建界面。
    buildUi();
    // 创建标签页。
    createTabs();
    // 完成信号布线。
    wireSignals();
    // 应用深色主题（在这里应用可以覆盖所有子控件）。
    setStyleSheet(Theme::darkStyleSheet());
    // 设置窗口标题（英文，会显示在任务栏与窗口标题栏）。
    setWindowTitle(QStringLiteral("Universal Transcoder 1.0.0"));
    // 设置窗口图标（从资源读取）。
    setWindowIcon(QIcon(QStringLiteral(":/app/icon.ico")));
    // 初始尺寸：足够宽以容纳参数区与操作区并排显示。
    resize(1180, 780);
    // 最小尺寸：低于这个尺寸两栏布局会挤在一起。
    setMinimumSize(960, 640);
    // 底部状态栏显示内核状态（这是用户最需要一眼看到的信息）。
    statusBar()->showMessage(QStringLiteral("Kernel: %1").arg(FFmpegLocator::instance().versionText()));
}

// 析构。
MainWindow::~MainWindow()
{
    // 停止队列并取消所有活动任务（析构函数内部还会等待线程退出）。
    if (m_queue != nullptr)
    {
        // 停止调度。
        m_queue->stopAll();
    }
}

// 构建界面。
void MainWindow::buildUi()
{
    // 标签容器。
    m_tabs = new QTabWidget;
    // 标签可关闭性关闭（这是固定功能分区，不需要关闭）。
    m_tabs->setTabsClosable(false);
    // 设置为中心控件。
    setCentralWidget(m_tabs);
}

// 创建标签页。
void MainWindow::createTabs()
{
    // 视频页（类型 "video"）。
    m_videoPage = new ConvertPanel(QStringLiteral("video"), m_queue);
    // 加入标签。
    m_tabs->addTab(m_videoPage, QStringLiteral("Video"));
    // 音频页。
    m_audioPage = new ConvertPanel(QStringLiteral("audio"), m_queue);
    // 加入标签。
    m_tabs->addTab(m_audioPage, QStringLiteral("Audio"));
    // 图片页。
    m_imagePage = new ConvertPanel(QStringLiteral("image"), m_queue);
    // 加入标签。
    m_tabs->addTab(m_imagePage, QStringLiteral("Image"));
    // 文档页。
    m_docPage = new DocumentPage;
    // 加入标签。
    m_tabs->addTab(m_docPage, QStringLiteral("Document"));
    // 高级页。
    m_advPage = new AdvancedPage;
    // 加入标签。
    m_tabs->addTab(m_advPage, QStringLiteral("Advanced"));
    // 队列页。
    m_queuePage = new QueuePage(m_queue);
    // 加入标签。
    m_tabs->addTab(m_queuePage, QStringLiteral("Queue"));
    // 设置页。
    m_settingsPage = new SettingsPage(m_queue);
    // 加入标签。
    m_tabs->addTab(m_settingsPage, QStringLiteral("Settings"));
    // 关于页。
    m_aboutPage = new AboutPage;
    // 加入标签。
    m_tabs->addTab(m_aboutPage, QStringLiteral("About"));
}

// 布线信号。
void MainWindow::wireSignals()
{
    // ---- 队列 → 各转换页（进度、状态、日志）----
    // 视频页接收进度。
    connect(m_queue, &TaskQueue::taskProgressTick, m_videoPage, &ConvertPanel::onTaskProgress);
    // 音频页。
    connect(m_queue, &TaskQueue::taskProgressTick, m_audioPage, &ConvertPanel::onTaskProgress);
    // 图片页。
    connect(m_queue, &TaskQueue::taskProgressTick, m_imagePage, &ConvertPanel::onTaskProgress);
    // 视频页接收状态变化。
    connect(m_queue, &TaskQueue::taskChanged, m_videoPage, &ConvertPanel::onTaskChanged);
    // 音频页。
    connect(m_queue, &TaskQueue::taskChanged, m_audioPage, &ConvertPanel::onTaskChanged);
    // 图片页。
    connect(m_queue, &TaskQueue::taskChanged, m_imagePage, &ConvertPanel::onTaskChanged);
    // 视频页接收日志。
    connect(m_queue, &TaskQueue::taskLog, m_videoPage, &ConvertPanel::onTaskLog);
    // 音频页。
    connect(m_queue, &TaskQueue::taskLog, m_audioPage, &ConvertPanel::onTaskLog);
    // 图片页。
    connect(m_queue, &TaskQueue::taskLog, m_imagePage, &ConvertPanel::onTaskLog);

    // ---- 各页面 → 状态栏 ----
    // 视频页。
    connect(m_videoPage, &ConvertPanel::statusMessage, this, &MainWindow::onStatusMessage);
    // 音频页。
    connect(m_audioPage, &ConvertPanel::statusMessage, this, &MainWindow::onStatusMessage);
    // 图片页。
    connect(m_imagePage, &ConvertPanel::statusMessage, this, &MainWindow::onStatusMessage);
    // 文档页。
    connect(m_docPage, &DocumentPage::statusMessage, this, &MainWindow::onStatusMessage);
    // 高级页。
    connect(m_advPage, &AdvancedPage::statusMessage, this, &MainWindow::onStatusMessage);
    // 队列页。
    connect(m_queuePage, &QueuePage::statusMessage, this, &MainWindow::onStatusMessage);
    // 设置页。
    connect(m_settingsPage, &SettingsPage::statusMessage, this, &MainWindow::onStatusMessage);

    // ---- 设置页 → 三个转换页（同步默认值）----
    connect(m_settingsPage, &SettingsPage::settingsChanged, this, &MainWindow::onSettingsChanged);

    // ---- 队列任务加入时自动切到队列页，让用户立刻看到任务状态 ----
    connect(m_queue, &TaskQueue::taskAdded, this, [this](const QString&)
    {
        // 若当前不在队列页，则切换过去（批处理时用户最关心的是队列）。
        if (m_tabs->currentWidget() != m_queuePage)
        {
            // 切换标签。
            m_tabs->setCurrentWidget(m_queuePage);
        }
    });

    // 用设置页已保存的默认值初始化三个转换页（保证启动时行为与设置一致）。
    onSettingsChanged(m_settingsPage->defaultOutputDir(), m_settingsPage->defaultHwAccel(),
                      m_settingsPage->concurrency(), m_settingsPage->logLevel());
}

// 状态栏更新。
void MainWindow::onStatusMessage(const QString& text)
{
    // 显示状态文本（超长文本会被状态栏自动省略，这是可接受的）。
    statusBar()->showMessage(text);
}

// 设置同步。
void MainWindow::onSettingsChanged(const QString& defaultOutputDir, const QString& hwAccel, int concurrency,
                                   const QString& logLevel)
{
    // 同步默认输出目录到三个转换页。
    m_videoPage->setDefaultOutputDir(defaultOutputDir);
    // 音频页。
    m_audioPage->setDefaultOutputDir(defaultOutputDir);
    // 图片页。
    m_imagePage->setDefaultOutputDir(defaultOutputDir);
    // 同步默认硬件加速策略。
    m_videoPage->setDefaultHwAccel(hwAccel);
    // 音频页（音频页没有硬件加速控件，但同步调用是安全的：内部会检查控件是否存在）。
    m_audioPage->setDefaultHwAccel(hwAccel);
    // 图片页。
    m_imagePage->setDefaultHwAccel(hwAccel);
    // 同步并发上限。
    m_queue->setMaxParallel(concurrency);
    // 同步日志级别（全局状态）。
    setGlobalLogLevel(logLevel);
    // 在状态栏提示设置已生效。
    statusBar()->showMessage(
        QStringLiteral("Settings applied: max parallel = %1, hardware accel = %2, log level = %3").arg(concurrency).arg(
            hwAccel, logLevel));
}