// *****************************************************************************
// settingspage.h —— Settings 标签页（全局设置）。
// 覆盖项：自定义 FFmpeg 内核路径、默认输出目录、并发数、日志级别、默认硬件加速策略。
// 持久化：全部通过 QSettings 保存到注册表（Windows 上的标准做法），重启后自动恢复。
// 生效方式：修改后立即应用到内核定位器、能力矩阵与任务队列，无需重启程序。
// *****************************************************************************
#pragma once

// QWidget：页面基类。
#include <QWidget>
// QString：文本。
#include <QString>
// 任务队列（并发上限需要它）。
#include "core/taskqueue.h"

// 前置声明的控件类型。
class QLineEdit; // 单行输入。
class QSpinBox; // 数值输入。
class QComboBox; // 下拉框。
class QLabel; // 状态标签。
class QPushButton; // 按钮。

// 设置页面。
class SettingsPage : public QWidget
{
    Q_OBJECT

public:
    // 构造。
    explicit SettingsPage(TaskQueue* queue, QWidget* parent = nullptr);

    // 读取默认输出目录（供其它页面使用）。
    QString defaultOutputDir() const;

    // 读取默认硬件加速策略。
    QString defaultHwAccel() const;

    // 读取并发上限。
    int concurrency() const;

    // 读取日志级别。
    QString logLevel() const;

    signals :
    // 设置发生变化（主窗口据此同步其它页面）。
    
    void settingsChanged(const QString& defaultOutputDir, const QString& hwAccel, int concurrency,
                         const QString& logLevel);

    // 向上报告状态文本。
    void statusMessage(const QString& text);

private
    slots :
    // 选择自定义内核路径（可以是 exe 也可以是目录）。
    
    void onBrowseFfmpeg();

    // 选择默认输出目录。
    void onBrowseOutput();

    // 应用自定义内核路径（会清空内核与能力缓存并重新校验）。
    void onApplyKernel();

    // 保存全部设置。
    void onSave();

    // 恢复默认设置。
    void onResetDefaults();

private:
    // 构建界面。
    void buildUi();

    // 从 QSettings 载入设置到界面。
    void loadSettings();

    // 把界面上的设置写回 QSettings 并应用。
    void persistSettings();

    // 刷新内核状态标签。
    void refreshKernelStatus();

    // 任务队列。
    TaskQueue* m_queue = nullptr;
    // 自定义内核路径输入框。
    QLineEdit* m_ffmpegPathEdit = nullptr;
    // 默认输出目录输入框。
    QLineEdit* m_outputDirEdit = nullptr;
    // 并发数输入框。
    QSpinBox* m_concurrencySpin = nullptr;
    // 日志级别下拉框。
    QComboBox* m_logLevelCombo = nullptr;
    // 默认硬件加速下拉框。
    QComboBox* m_hwCombo = nullptr;
    // 内核状态标签。
    QLabel* m_kernelLabel = nullptr;
    // 内核能力摘要标签。
    QLabel* m_capabilityLabel = nullptr;
};

// 全局日志级别（取值 error / warning / info / debug），供 main.cpp 的消息处理器使用。
// 之所以做成自由函数：消息处理器是全局状态，无法放在某个对象里。
QString globalLogLevel();

// 设置全局日志级别。
void setGlobalLogLevel(const QString& level);