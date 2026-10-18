// *****************************************************************************
// documentpage.h —— Document 标签页（文档类转换）。
// 覆盖三种子任务：
//   1) Images → PDF：把多张图片各自成页合成一个 PDF；
//   2) Text → PDF：把纯文本按 A4 版式排版成 PDF；
//   3) PDF → Images：把 PDF 逐页渲染成图片（依赖 Qt 的 pdf 模块，缺失时如实提示）。
// 说明：这三类转换不使用 FFmpeg，因此不进入任务队列，由本页面直接执行并展示结果。
// *****************************************************************************
#pragma once

// QWidget：页面基类。
#include <QWidget>
// QString / QStringList：路径与结果。
#include <QString>
#include <QStringList>

// 前置声明的控件类型。
class QComboBox;        // 子任务选择。
class QListWidget;      // 输入文件列表。
class QLineEdit;        // 路径输入。
class QSpinBox;         // 数值输入。
class QLabel;           // 状态标签。
class QPlainTextEdit;   // 日志框。
class QFormLayout;      // 参数表单布局（用于整行显隐）。

// 文档转换页面。
class DocumentPage : public QWidget
{
    Q_OBJECT
public:
    // 构造。
    explicit DocumentPage(QWidget *parent = nullptr);

signals:
    // 向上报告状态文本。
    void statusMessage(const QString &text);

private slots:
    // 添加输入文件（按当前模式使用不同的文件过滤器）。
    void onAddFiles();

    // 清空输入列表。
    void onClearFiles();

    // 选择输出文件（模式 1、2）或输出目录（模式 3）。
    void onBrowseOutput();

    // 切换子任务时刷新界面（标题、控件可见性、提示）。
    void onModeChanged(int index);

    // 执行当前子任务。
    void onRun();

private:
    // 构建界面。
    void buildUi();

    // 追加日志。
    void appendLog(const QString &text);

    // 更新状态标签。
    void setStatus(const QString &text);

    // 当前模式（0 = Images to PDF，1 = Text to PDF，2 = PDF to Images）。
    int mode() const;

    // 子任务选择下拉框。
    QComboBox *m_modeCombo = nullptr;
    // 参数表单布局（用于按模式整行显隐控件与标签）。
    QFormLayout *m_cfgForm = nullptr;
    // 输入文件列表。
    QListWidget *m_fileList = nullptr;
    // 输出路径输入框（PDF 或输出目录）。
    QLineEdit *m_outputEdit = nullptr;
    // 输出路径标签（随模式变化措辞）。
    QLabel *m_outputLabel = nullptr;
    // 输出图片格式下拉框（仅模式 2 使用）。
    QComboBox *m_imageFormatCombo = nullptr;
    // 渲染 DPI 输入框（仅模式 2 使用）。
    QSpinBox *m_dpiSpin = nullptr;
    // 页码范围输入框（仅模式 2 使用）。
    QLineEdit *m_pageRangeEdit = nullptr;
    // PDF 标题输入框（仅模式 1 使用）。
    QLineEdit *m_titleEdit = nullptr;
    // 状态标签。
    QLabel *m_statusLabel = nullptr;
    // 日志框。
    QPlainTextEdit *m_log = nullptr;
    // 输出路径相关控件的容器（便于整体显示/隐藏）。
    QWidget *m_outputRow = nullptr;
    // 模式 2 专用控件的容器。
    QWidget *m_pdfToImageRow = nullptr;
};
