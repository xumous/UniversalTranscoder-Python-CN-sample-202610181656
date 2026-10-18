// *****************************************************************************
// advancedpage.h —— Advanced 标签页（手工命令行）。
// 定位：给熟悉 FFmpeg 的用户一个“完全可控”的出口，同时仍然保持工程的两条底线：
//   1) 命令必须以“参数列表”形式构造，绝不拼接 shell 字符串（避免注入与转义地狱）；
//   2) 构造逻辑复用核心的命令构建器，因此预览出来的命令与实际执行的命令完全一致。
// 功能：选择源文件 → 选择目标容器 → 追加自定义参数 → 预览命令 → 执行 / 取消 → 查看输出。
// *****************************************************************************
#pragma once

// QWidget：页面基类。
#include <QWidget>
// QString / QStringList：路径与参数。
#include <QString>
#include <QStringList>
// QProcess：执行命令。
#include <QProcess>

// 前置声明的控件类型。
class QLineEdit; // 单行输入。
class QComboBox; // 下拉框。
class QPlainTextEdit; // 多行文本（自定义参数与命令预览）。
class QLabel; // 状态标签。
class QPushButton; // 按钮。

// 高级页面。
class AdvancedPage : public QWidget
{
    Q_OBJECT

public:
    // 构造。
    explicit AdvancedPage(QWidget* parent = nullptr);

    signals :
    // 向上报告状态文本。
    
    void statusMessage(const QString& text);

private
    slots :
    // 选择源文件。
    
    void onBrowseSource();

    // 选择输出目录。
    void onBrowseOutput();

    // 重新生成命令预览。
    void onBuildPreview();

    // 执行命令。
    void onRun();

    // 取消执行。
    void onCancel();

    // 进程结束。
    void onProcessFinished(int exitCode, QProcess::ExitStatus status);

private:
    // 构建界面。
    void buildUi();

    // 把界面内容组装成参数列表；失败时返回空列表并给出原因。
    QStringList buildArgs(QString* errorOut) const;

    // 追加日志。
    void appendLog(const QString& text);

    // 源文件输入框。
    QLineEdit* m_sourceEdit = nullptr;
    // 目标格式下拉框。
    QComboBox* m_formatCombo = nullptr;
    // 输出目录输入框。
    QLineEdit* m_outputDirEdit = nullptr;
    // 输出文件名输入框。
    QLineEdit* m_outputNameEdit = nullptr;
    // 自定义参数输入框（多行，每行一个参数片段）。
    QPlainTextEdit* m_extraArgsEdit = nullptr;
    // 命令预览框。
    QPlainTextEdit* m_previewEdit = nullptr;
    // 日志框。
    QPlainTextEdit* m_log = nullptr;
    // 状态标签。
    QLabel* m_statusLabel = nullptr;
    // 运行按钮。
    QPushButton* m_runButton = nullptr;
    // 取消按钮。
    QPushButton* m_cancelButton = nullptr;
    // 进程对象。
    QProcess* m_process = nullptr;
    // 当前正在执行的命令（取消时用于展示）。
    QStringList m_currentArgs;
};