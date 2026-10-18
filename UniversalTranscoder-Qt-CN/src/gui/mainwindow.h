// *****************************************************************************
// mainwindow.h —— 主窗口。
// 结构：QMainWindow + QTabWidget，共 8 个标签页：
//   Video / Audio / Image / Document / Advanced / Queue / Settings / About。
// 职责：持有唯一的任务队列实例，把各转换页提交的任务统一汇聚到队列页展示，
//       并把队列的进度/日志信号转发给提交任务的页面（页面据此过滤属于自己的任务）。
// 为什么这样分工：任务队列是全局单例式的共享资源，必须由主窗口统一持有，
//                 各页面只做“参数收集 + 展示”，职责单一且容易维护。
// *****************************************************************************
#pragma once

// QMainWindow：主窗口基类。
#include <QMainWindow>
// QString：文本。
#include <QString>
// 任务队列。
#include "core/taskqueue.h"

// 前置声明的页面类型。
class QTabWidget; // 标签容器。
class ConvertPanel; // 通用转换面板。
class DocumentPage; // 文档页面。
class AdvancedPage; // 高级页面。
class QueuePage; // 队列页面。
class SettingsPage; // 设置页面。
class AboutPage; // 关于页面。

// 主窗口类。
class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    // 构造。
    explicit MainWindow(QWidget* parent = nullptr);

    // 析构：停止队列并回收线程。
    ~MainWindow() override;

private
    slots :
    // 状态栏文本更新（接收各页面的 statusMessage 信号）。
    
    void onStatusMessage(const QString& text);

    // 设置变化后同步各转换页的默认值。
    void onSettingsChanged(const QString& defaultOutputDir, const QString& hwAccel, int concurrency,
                           const QString& logLevel);

private:
    // 构建界面。
    void buildUi();

    // 创建全部标签页。
    void createTabs();

    // 连接各页面与队列之间的信号。
    void wireSignals();

    // 任务队列（全局唯一实例，由主窗口持有）。
    TaskQueue* m_queue = nullptr;
    // 标签容器。
    QTabWidget* m_tabs = nullptr;
    // 三个转换页。
    ConvertPanel* m_videoPage = nullptr;
    // 音频页。
    ConvertPanel* m_audioPage = nullptr;
    // 图片页。
    ConvertPanel* m_imagePage = nullptr;
    // 文档页。
    DocumentPage* m_docPage = nullptr;
    // 高级页。
    AdvancedPage* m_advPage = nullptr;
    // 队列页。
    QueuePage* m_queuePage = nullptr;
    // 设置页。
    SettingsPage* m_settingsPage = nullptr;
    // 关于页。
    AboutPage* m_aboutPage = nullptr;
};