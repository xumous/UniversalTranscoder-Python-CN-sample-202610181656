// *****************************************************************************
// documentpage.cpp —— 文档转换页面的实现。
// 实现要点：
//   1) 三种子任务共用同一个输入文件列表控件，切换模式时同步更换文件过滤器与提示文案。
//   2) PDF → 图片 的能力由 DocConvert::pdfReadSupported() 在运行时（实为编译期常量）判断：
//      不支持时按钮保持可用但执行会给出明确原因，绝不用其它手段伪装成功。
//   3) 所有结果（成功/失败）都写入日志框，并同步更新状态标签，保证操作可追溯。
// *****************************************************************************
#include "documentpage.h"

// Qt 控件与布局。
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFormLayout>
#include <QGroupBox>
#include <QLabel>
#include <QLineEdit>
#include <QComboBox>
#include <QSpinBox>
#include <QPushButton>
#include <QPlainTextEdit>
#include <QListWidget>
#include <QListWidgetItem>
#include <QFileDialog>
#include <QMessageBox>
#include <QDir>
#include <QFileInfo>
#include <QTextCursor>
// 文档转换核心。
#include "core/docconvert.h"

// 构造。
DocumentPage::DocumentPage(QWidget* parent)
    : QWidget(parent)
{
    // 构建界面。
    buildUi();
}

// 构建界面。
void DocumentPage::buildUi()
{
    // 根布局。
    QVBoxLayout* root = new QVBoxLayout(this);
    // 边距。
    root->setContentsMargins(10, 10, 10, 10);
    // 间距。
    root->setSpacing(8);

    // ---- 说明区 ----
    // 顶部说明标签（英文，向用户解释本页能力与限制）。
    QLabel* intro = new QLabel;
    // 说明文本：明确列出三种能力，并如实说明 PDF→图片 的构建依赖。
    intro->setText(QStringLiteral(
        "Document conversions (no FFmpeg involved). Images to PDF merges images into one document; Text to PDF lays out plain text on A4 pages; PDF to Images renders each page as PNG or JPG."));
    // 允许换行。
    intro->setWordWrap(true);
    // 加入根布局。
    root->addWidget(intro);

    // ---- 参数区 ----
    // 参数分组框。
    QGroupBox* cfgGroup = new QGroupBox(QStringLiteral("Settings"));
    // 参数表单布局。
    QFormLayout* cfgForm = new QFormLayout(cfgGroup);
    // 记录到成员，供模式切换时整行显隐使用。
    m_cfgForm = cfgForm;
    // 子任务下拉框。
    m_modeCombo = new QComboBox;
    // 三个子任务（英文显示）。
    m_modeCombo->addItem(QStringLiteral("Images to PDF"), 0);
    // 文本转 PDF。
    m_modeCombo->addItem(QStringLiteral("Text to PDF"), 1);
    // PDF 转图片。
    m_modeCombo->addItem(QStringLiteral("PDF to Images"), 2);
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Task"), m_modeCombo);
    // PDF 标题输入框（仅 Images to PDF 使用）。
    m_titleEdit = new QLineEdit;
    // 占位提示。
    m_titleEdit->setPlaceholderText(QStringLiteral("PDF title (optional)"));
    // 加入表单（同一行会随模式切换而显示/隐藏，做法简单且直观）。
    cfgForm->addRow(QStringLiteral("Title"), m_titleEdit);
    // 输出图片格式下拉框（仅 PDF → 图片 使用）。
    m_imageFormatCombo = new QComboBox;
    // PNG（无损，默认）。
    m_imageFormatCombo->addItem(QStringLiteral("PNG"), QStringLiteral("png"));
    // JPG（体积小）。
    m_imageFormatCombo->addItem(QStringLiteral("JPG"), QStringLiteral("jpg"));
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Image format"), m_imageFormatCombo);
    // 渲染 DPI 输入框。
    m_dpiSpin = new QSpinBox;
    // 范围 36 ~ 600（对应 DocConvert 内部做的区间限制，避免用户填出离谱数值）。
    m_dpiSpin->setRange(36, 600);
    // 默认 150 DPI（清晰度与体积的平衡点）。
    m_dpiSpin->setValue(150);
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Render DPI"), m_dpiSpin);
    // 页码范围输入框。
    m_pageRangeEdit = new QLineEdit;
    // 占位提示（给出示例格式，避免用户猜）。
    m_pageRangeEdit->setPlaceholderText(QStringLiteral("e.g. 1-3,5 (empty = all pages)"));
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Page range"), m_pageRangeEdit);
    // 加入根布局。
    root->addWidget(cfgGroup);

    // ---- 输入文件区 ----
    // 输入分组框。
    QGroupBox* inputGroup = new QGroupBox(QStringLiteral("Input files"));
    // 输入区竖向布局。
    QVBoxLayout* inputLayout = new QVBoxLayout(inputGroup);
    // 文件列表（支持多选）。
    m_fileList = new QListWidget;
    // 允许扩展选择。
    m_fileList->setSelectionMode(QAbstractItemView::ExtendedSelection);
    // 加入布局。
    inputLayout->addWidget(m_fileList, 1);
    // 按钮行。
    QWidget* btnRow = new QWidget;
    // 按钮行布局。
    QHBoxLayout* btnLayout = new QHBoxLayout(btnRow);
    // 去掉边距。
    btnLayout->setContentsMargins(0, 0, 0, 0);
    // 添加按钮。
    QPushButton* addBtn = new QPushButton(QStringLiteral("Add files"));
    // 清空按钮。
    QPushButton* clearBtn = new QPushButton(QStringLiteral("Clear list"));
    // 加入。
    btnLayout->addWidget(addBtn);
    // 追加清空按钮。
    btnLayout->addWidget(clearBtn);
    // 余量留白。
    btnLayout->addStretch(1);
    // 加入输入区。
    inputLayout->addWidget(btnRow);
    // 加入根布局（占主要高度）。
    root->addWidget(inputGroup, 3);

    // ---- 输出区 ----
    // 输出分组框。
    QGroupBox* outGroup = new QGroupBox(QStringLiteral("Output"));
    // 输出区竖向布局。
    QVBoxLayout* outLayout = new QVBoxLayout(outGroup);
    // 输出行容器。
    m_outputRow = new QWidget;
    // 输出行布局。
    QHBoxLayout* outRowLayout = new QHBoxLayout(m_outputRow);
    // 去掉边距。
    outRowLayout->setContentsMargins(0, 0, 0, 0);
    // 输出标签（措辞随模式变化）。
    m_outputLabel = new QLabel(QStringLiteral("PDF file"));
    // 输出路径输入框。
    m_outputEdit = new QLineEdit;
    // 浏览按钮。
    QPushButton* outBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    outBtn->setFixedWidth(32);
    // 加入行布局。
    outRowLayout->addWidget(m_outputLabel);
    // 追加输入框。
    outRowLayout->addWidget(m_outputEdit, 1);
    // 追加按钮。
    outRowLayout->addWidget(outBtn);
    // 加入输出区。
    outLayout->addWidget(m_outputRow);
    // 运行按钮（主按钮）。
    QPushButton* runBtn = new QPushButton(QStringLiteral("Run conversion"));
    // 标记为主按钮。
    runBtn->setProperty("primary", true);
    // 加入输出区。
    outLayout->addWidget(runBtn);
    // 状态标签。
    m_statusLabel = new QLabel;
    // 允许换行。
    m_statusLabel->setWordWrap(true);
    // 加入输出区。
    outLayout->addWidget(m_statusLabel);
    // 加入根布局。
    root->addWidget(outGroup);

    // ---- 日志区 ----
    // 日志分组框。
    QGroupBox* logGroup = new QGroupBox(QStringLiteral("Log"));
    // 日志区竖向布局。
    QVBoxLayout* logLayout = new QVBoxLayout(logGroup);
    // 日志框（只读）。
    m_log = new QPlainTextEdit;
    // 设为只读。
    m_log->setReadOnly(true);
    // 关闭自动换行。
    m_log->setLineWrapMode(QPlainTextEdit::NoWrap);
    // 加入布局。
    logLayout->addWidget(m_log);
    // 加入根布局。
    root->addWidget(logGroup, 2);

    // ---- 信号连接 ----
    // 添加文件。
    connect(addBtn, &QPushButton::clicked, this, &DocumentPage::onAddFiles);
    // 清空列表。
    connect(clearBtn, &QPushButton::clicked, this, &DocumentPage::onClearFiles);
    // 浏览输出。
    connect(outBtn, &QPushButton::clicked, this, &DocumentPage::onBrowseOutput);
    // 运行。
    connect(runBtn, &QPushButton::clicked, this, &DocumentPage::onRun);
    // 模式变化。
    connect(m_modeCombo, &QComboBox::currentIndexChanged, this, &DocumentPage::onModeChanged);

    // 记录 PDF 后端能力（便于用户在第一眼就了解是否存在限制）。
    appendLog(QStringLiteral("> PDF rendering backend: %1").arg(DocConvert::pdfBackendInfo()));
    // 初始化界面状态。
    onModeChanged(0);
}

// 当前模式。
int DocumentPage::mode() const
{
    // 取出下拉框的数据值。
    return m_modeCombo->currentData().toInt();
}

// 模式切换。
void DocumentPage::onModeChanged(int index)
{
    // 忽略参数 index，直接读取数据值（避免依赖下拉框顺序）。
    Q_UNUSED(index);
    // 取当前模式。
    const int m = mode();
    // 模式 2（PDF → 图片）才需要图片格式、DPI、页码范围。
    const bool pdfToImage = (m == 2);
    // 使用表单布局的“整行显隐”能力，把标签与输入框一起隐藏，界面更干净。
    m_cfgForm->setRowVisible(m_imageFormatCombo, pdfToImage);
    // DPI 行显隐。
    m_cfgForm->setRowVisible(m_dpiSpin, pdfToImage);
    // 页码范围行显隐。
    m_cfgForm->setRowVisible(m_pageRangeEdit, pdfToImage);
    // PDF 标题行只在 Images to PDF 模式显示。
    m_cfgForm->setRowVisible(m_titleEdit, m == 0);
    // 输出标签措辞随模式变化。
    m_outputLabel->setText(pdfToImage ? QStringLiteral("Output folder") : QStringLiteral("PDF file"));
    // 输出路径提示语也同步更新。
    m_outputEdit->setPlaceholderText(pdfToImage
                                         ? QStringLiteral("Folder for the rendered images")
                                         : QStringLiteral("Path of the PDF to create"));
    // 更新状态提示。
    setStatus(pdfToImage && !DocConvert::pdfReadSupported()
                  ? QStringLiteral(
                      "PDF to Images is not available in this build (the Qt Pdf module was not installed).")
                  : QStringLiteral("Ready."));
}

// 添加文件。
void DocumentPage::onAddFiles()
{
    // 按模式选择文件过滤器。
    QString filter;
    // 模式 1 选文本文件。
    if (mode() == 1)
    {
        // 文本过滤器。
        filter = QStringLiteral("Text files (*.txt *.log *.csv *.md);;All files (*)");
    }
    else if (mode() == 2)
    {
        // PDF 过滤器。
        filter = QStringLiteral("PDF files (*.pdf);;All files (*)");
    }
    else
    {
        // 图片过滤器。
        filter = QStringLiteral(
            "Image files (*.jpg *.jpeg *.png *.webp *.gif *.bmp *.tif *.tiff *.avif);;All files (*)");
    }
    // 打开多选对话框。
    const QStringList files = QFileDialog::getOpenFileNames(this, QStringLiteral("Select input files"), QString(),
                                                            filter);
    // 逐个加入。
    for (const QString& f : files)
    {
        // 规范为绝对路径。
        const QString abs = QFileInfo(f).absoluteFilePath();
        // 新建列表项。
        QListWidgetItem* item = new QListWidgetItem(QFileInfo(abs).fileName());
        // 存入完整路径。
        item->setData(Qt::UserRole, abs);
        // 悬停显示完整路径。
        item->setToolTip(abs);
        // 加入列表。
        m_fileList->addItem(item);
    }
    // 更新状态。
    setStatus(QStringLiteral("%1 file(s) selected.").arg(m_fileList->count()));
}

// 清空列表。
void DocumentPage::onClearFiles()
{
    // 清空。
    m_fileList->clear();
    // 更新状态。
    setStatus(QStringLiteral("Input list cleared."));
}

// 选择输出。
void DocumentPage::onBrowseOutput()
{
    // PDF → 图片 模式需要目录。
    if (mode() == 2)
    {
        // 目录选择对话框。
        const QString dir = QFileDialog::getExistingDirectory(this, QStringLiteral("Select output folder"),
                                                              m_outputEdit->text());
        // 用户取消。
        if (dir.isEmpty()) { return; }
        // 回填。
        m_outputEdit->setText(QDir::toNativeSeparators(dir));
        // 返回。
        return;
    }
    // 其它两种模式需要 PDF 文件路径（这里用保存对话框，默认扩展名 pdf）。
    const QString file = QFileDialog::getSaveFileName(this, QStringLiteral("Save PDF as"), m_outputEdit->text(),
                                                      QStringLiteral("PDF files (*.pdf)"));
    // 用户取消。
    if (file.isEmpty()) { return; }
    // 回填。
    m_outputEdit->setText(QDir::toNativeSeparators(file));
}

// 执行。
void DocumentPage::onRun()
{
    // 收集输入文件。
    QStringList inputs;
    // 遍历列表。
    for (int i = 0; i < m_fileList->count(); ++i)
    {
        // 追加路径。
        inputs << m_fileList->item(i)->data(Qt::UserRole).toString();
    }
    // 没有输入时提示。
    if (inputs.isEmpty())
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("No input"),
                                 QStringLiteral("Add at least one input file first."));
        // 返回。
        return;
    }
    // 输出路径。
    QString out = QDir::fromNativeSeparators(m_outputEdit->text().trimmed());
    // 输出路径为空时按源文件推导（给用户一个合理默认值，避免因此报错）。
    if (out.isEmpty())
    {
        // 取第一个源文件目录。
        const QString dir = QFileInfo(inputs.first()).absolutePath();
        // 模式 2 → 目录。
        if (mode() == 2)
        {
            // 目录直接用源目录下的 images 子目录。
            out = QDir(dir).filePath(QStringLiteral("images"));
        }
        else
        {
            // 其它模式 → 生成 PDF 路径。
            out = QDir(dir).filePath(QStringLiteral("document.pdf"));
        }
        // 回填界面，让用户看到实际使用的路径。
        m_outputEdit->setText(QDir::toNativeSeparators(out));
    }
    // 结果对象。
    DocResult res;
    // 分支执行。
    if (mode() == 0)
    {
        // 图片 → PDF。
        res = DocConvert::imagesToPdf(inputs, out, m_dpiSpin->value());
    }
    else if (mode() == 1)
    {
        // 文本 → PDF（本模式的输入文件通常只有一个，这里取第一个）。
        res = DocConvert::textToPdf(inputs.first(), out, m_titleEdit->text().trimmed());
    }
    else
    {
        // PDF → 图片。
        res = DocConvert::pdfToImages(inputs.first(), out, QString(), m_dpiSpin->value(),
                                      m_imageFormatCombo->currentData().toString(), m_pageRangeEdit->text().trimmed());
    }
    // 把结果写入日志与状态标签。
    if (res.ok)
    {
        // 记录成功提示。
        appendLog(QStringLiteral("> %1").arg(res.message));
        // 逐个记录产物路径。
        for (const QString& p : res.outputs)
        {
            // 记录路径。
            appendLog(QStringLiteral("> Output: %1").arg(p));
        }
        // 更新状态。
        setStatus(res.message);
        // 向上层报告。
        emit statusMessage(res.message);
        // 弹出成功提示（含产物数量）。
        QMessageBox::information(this, QStringLiteral("Done"), res.message);
    }
    else
    {
        // 记录失败原因。
        appendLog(QStringLiteral("> FAILED: %1").arg(res.error));
        // 更新状态。
        setStatus(res.error);
        // 向上层报告。
        emit statusMessage(res.error);
        // 弹出失败提示。
        QMessageBox::warning(this, QStringLiteral("Conversion failed"), res.error);
    }
}

// 追加日志。
void DocumentPage::appendLog(const QString& text)
{
    // 追加一行文本。
    m_log->appendPlainText(text);
    // 光标移到末尾。
    m_log->moveCursor(QTextCursor::End);
}

// 更新状态。
void DocumentPage::setStatus(const QString& text)
{
    // 更新标签。
    m_statusLabel->setText(text);
}