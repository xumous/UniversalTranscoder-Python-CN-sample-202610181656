// *****************************************************************************
// advancedpage.cpp —— 手工命令行页面的实现。
// 实现要点：
//   1) 命令预览与执行共用同一个 buildArgs()，从根本上杜绝“预览和执行不一致”的坑。
//   2) 自定义参数按“空白切分”的方式解析：每一段就是一个独立的 argv 元素，
//      这样含空格的路径仍可用引号包裹成一段（与命令行直觉一致）。
//   3) 执行使用 QProcess 的“合并通道 + 事件驱动”方式：不阻塞界面，
//      结束时一次性把输出写到日志框，便于用户复盘。
// *****************************************************************************
#include "advancedpage.h"

// Qt 控件与布局。
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFormLayout>
#include <QGroupBox>
#include <QLabel>
#include <QLineEdit>
#include <QComboBox>
#include <QPushButton>
#include <QPlainTextEdit>
#include <QFileDialog>
#include <QMessageBox>
#include <QDir>
#include <QFileInfo>
#include <QTextCursor>
#include <QRegularExpression>
// 内核与命令构建。
#include "core/capabilities.h"
#include "core/commandbuilder.h"
#include "core/ffmpeg_locator.h"
#include "core/processutil.h"

// 构造。
AdvancedPage::AdvancedPage(QWidget* parent)
    : QWidget(parent)
{
    // 构建界面。
    buildUi();
}

// 构建界面。
void AdvancedPage::buildUi()
{
    // 根布局。
    QVBoxLayout* root = new QVBoxLayout(this);
    // 边距。
    root->setContentsMargins(10, 10, 10, 10);
    // 间距。
    root->setSpacing(8);

    // 说明标签（英文）。
    QLabel* intro = new QLabel;
    // 说明文本：强调预览与实际执行完全一致。
    intro->setText(QStringLiteral(
        "Build a command from the same engine used by the other tabs, inspect it, then run it. Extra arguments are appended right before the output path and may override earlier defaults."));
    // 允许换行。
    intro->setWordWrap(true);
    // 加入根布局。
    root->addWidget(intro);

    // 参数分组。
    QGroupBox* cfgGroup = new QGroupBox(QStringLiteral("Command"));
    // 参数表单布局。
    QFormLayout* cfgForm = new QFormLayout(cfgGroup);

    // 源文件行（输入框 + 浏览按钮）。
    QWidget* srcRow = new QWidget;
    // 源文件行布局。
    QHBoxLayout* srcLayout = new QHBoxLayout(srcRow);
    // 去掉边距。
    srcLayout->setContentsMargins(0, 0, 0, 0);
    // 源文件输入框。
    m_sourceEdit = new QLineEdit;
    // 占位提示。
    m_sourceEdit->setPlaceholderText(QStringLiteral("Source file"));
    // 浏览按钮。
    QPushButton* srcBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    srcBtn->setFixedWidth(32);
    // 加入行布局。
    srcLayout->addWidget(m_sourceEdit, 1);
    // 追加按钮。
    srcLayout->addWidget(srcBtn);
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Source"), srcRow);

    // 目标格式下拉框。
    m_formatCombo = new QComboBox;
    // 允许编辑，用户可以直接输入任意扩展名（内核会依据扩展名推断封装）。
    m_formatCombo->setEditable(true);
    // 列出常见格式作为建议值。
    const QStringList suggested = {
        QStringLiteral("mp4"), QStringLiteral("mkv"), QStringLiteral("mov"), QStringLiteral("avi"),
        QStringLiteral("webm"), QStringLiteral("flv"), QStringLiteral("ts"), QStringLiteral("gif"),
        QStringLiteral("mp3"), QStringLiteral("m4a"), QStringLiteral("wav"), QStringLiteral("flac"),
        QStringLiteral("ogg"), QStringLiteral("opus"), QStringLiteral("jpg"), QStringLiteral("png"),
        QStringLiteral("webp"), QStringLiteral("bmp"), QStringLiteral("tiff"), QStringLiteral("avif")
    };
    // 逐个加入（这些只是建议，是否受支持由能力矩阵与 ffmpeg 自行判断）。
    for (const QString& ext : suggested)
    {
        // 加入。
        m_formatCombo->addItem(ext);
    }
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Target format"), m_formatCombo);

    // 输出目录行。
    QWidget* outRow = new QWidget;
    // 输出目录行布局。
    QHBoxLayout* outLayout = new QHBoxLayout(outRow);
    // 去掉边距。
    outLayout->setContentsMargins(0, 0, 0, 0);
    // 输出目录输入框。
    m_outputDirEdit = new QLineEdit;
    // 占位提示（空表示与源文件同目录）。
    m_outputDirEdit->setPlaceholderText(QStringLiteral("Empty = source folder"));
    // 浏览按钮。
    QPushButton* outBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    outBtn->setFixedWidth(32);
    // 加入行布局。
    outLayout->addWidget(m_outputDirEdit, 1);
    // 追加上按钮。
    outLayout->addWidget(outBtn);
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Output folder"), outRow);

    // 输出文件名输入框。
    m_outputNameEdit = new QLineEdit;
    // 占位提示。
    m_outputNameEdit->setPlaceholderText(QStringLiteral("Empty = auto generated"));
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Output name"), m_outputNameEdit);

    // 自定义参数输入框（多行）。
    m_extraArgsEdit = new QPlainTextEdit;
    // 设置合适的初始高度。
    m_extraArgsEdit->setFixedHeight(70);
    // 占位提示。
    m_extraArgsEdit->setPlaceholderText(QStringLiteral("-c:v libx264 -crf 23 -preset slow"));
    // 加入表单。
    cfgForm->addRow(QStringLiteral("Extra ffmpeg args"), m_extraArgsEdit);
    // 加入根布局。
    root->addWidget(cfgGroup);

    // 预览分组。
    QGroupBox* previewGroup = new QGroupBox(QStringLiteral("Preview"));
    // 预览区竖向布局。
    QVBoxLayout* previewLayout = new QVBoxLayout(previewGroup);
    // 预览框（只读）。
    m_previewEdit = new QPlainTextEdit;
    // 设为只读。
    m_previewEdit->setReadOnly(true);
    // 关闭自动换行（命令很长时便于横向查看）。
    m_previewEdit->setLineWrapMode(QPlainTextEdit::NoWrap);
    // 固定高度。
    m_previewEdit->setFixedHeight(90);
    // 加入布局。
    previewLayout->addWidget(m_previewEdit);
    // 按钮行。
    QWidget* previewBtnRow = new QWidget;
    // 按钮行布局。
    QHBoxLayout* previewBtnLayout = new QHBoxLayout(previewBtnRow);
    // 去掉边距。
    previewBtnLayout->setContentsMargins(0, 0, 0, 0);
    // 预览按钮。
    QPushButton* previewBtn = new QPushButton(QStringLiteral("Build preview"));
    // 运行按钮（主按钮）。
    m_runButton = new QPushButton(QStringLiteral("Run"));
    // 标记为主按钮。
    m_runButton->setProperty("primary", true);
    // 取消按钮。
    m_cancelButton = new QPushButton(QStringLiteral("Cancel"));
    // 取消按钮初始禁用（没有任务可取消）。
    m_cancelButton->setEnabled(false);
    // 逐个加入。
    previewBtnLayout->addWidget(previewBtn);
    // 追加运行按钮。
    previewBtnLayout->addWidget(m_runButton);
    // 追加取消按钮。
    previewBtnLayout->addWidget(m_cancelButton);
    // 余量留白。
    previewBtnLayout->addStretch(1);
    // 加入预览区。
    previewLayout->addWidget(previewBtnRow);
    // 加入根布局。
    root->addWidget(previewGroup);

    // 日志分组。
    QGroupBox* logGroup = new QGroupBox(QStringLiteral("Output"));
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
    root->addWidget(logGroup, 1);

    // 状态标签。
    m_statusLabel = new QLabel;
    // 允许换行。
    m_statusLabel->setWordWrap(true);
    // 加入根布局。
    root->addWidget(m_statusLabel);

    // ---- 信号连接 ----
    // 源文件浏览。
    connect(srcBtn, &QPushButton::clicked, this, &AdvancedPage::onBrowseSource);
    // 输出目录浏览。
    connect(outBtn, &QPushButton::clicked, this, &AdvancedPage::onBrowseOutput);
    // 生成预览。
    connect(previewBtn, &QPushButton::clicked, this, &AdvancedPage::onBuildPreview);
    // 运行。
    connect(m_runButton, &QPushButton::clicked, this, &AdvancedPage::onRun);
    // 取消。
    connect(m_cancelButton, &QPushButton::clicked, this, &AdvancedPage::onCancel);

    // 进程对象（父对象为本页面）。
    m_process = new QProcess(this);
    // Windows 下不弹出控制台窗口。
    configureNoWindow(*m_process);
    // 合并两个通道，一次性读取全部输出。
    m_process->setProcessChannelMode(QProcess::MergedChannels);
    // 进程结束信号。
    connect(m_process, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            this, &AdvancedPage::onProcessFinished);
    // 有可读输出时立刻追加到日志框，让长任务的过程可见。
    connect(m_process, &QProcess::readyRead, this, [this]()
    {
        // 读取本次到达的全部输出。
        const QString chunk = QString::fromUtf8(m_process->readAll());
        // 非空才写入。
        if (!chunk.trimmed().isEmpty())
        {
            // 追加（去掉尾部多余空行）。
            appendLog(chunk.trimmed());
        }
    });

    // 初始化状态文本。
    m_statusLabel->setText(QStringLiteral("Ready. Build a preview to see the exact argument list."));
}

// 选择源文件。
void AdvancedPage::onBrowseSource()
{
    // 打开文件对话框（任意文件都允许，因为本页面向高级用户）。
    const QString file = QFileDialog::getOpenFileName(this, QStringLiteral("Select source file"), QString(),
                                                      QStringLiteral("All files (*)"));
    // 取消时返回。
    if (file.isEmpty()) { return; }
    // 回填。
    m_sourceEdit->setText(QDir::toNativeSeparators(file));
    // 自动生成预览，减少一次点击。
    onBuildPreview();
}

// 选择输出目录。
void AdvancedPage::onBrowseOutput()
{
    // 目录选择对话框。
    const QString dir = QFileDialog::getExistingDirectory(this, QStringLiteral("Select output folder"),
                                                          m_outputDirEdit->text());
    // 取消时返回。
    if (dir.isEmpty()) { return; }
    // 回填。
    m_outputDirEdit->setText(QDir::toNativeSeparators(dir));
    // 重新生成预览。
    onBuildPreview();
}

// 组装参数列表。
QStringList AdvancedPage::buildArgs(QString* errorOut) const
{
    // 清空错误文本。
    if (errorOut != nullptr) { errorOut->clear(); }
    // 源文件必须填写。
    const QString src = m_sourceEdit->text().trimmed();
    // 未填写时给出错误。
    if (src.isEmpty())
    {
        // 记录错误。
        if (errorOut != nullptr) { *errorOut = QStringLiteral("Select a source file first."); }
        // 返回空列表。
        return QStringList();
    }
    // 源文件必须存在，否则 ffmpeg 一定会失败。
    if (!QFileInfo::exists(src))
    {
        // 记录错误。
        if (errorOut != nullptr) { *errorOut = QStringLiteral("The source file does not exist."); }
        // 返回空列表。
        return QStringList();
    }
    // 内核必须就绪。
    if (FFmpegLocator::instance().ffmpegPath().isEmpty())
    {
        // 记录错误。
        if (errorOut != nullptr) { *errorOut = QStringLiteral("FFmpeg could not be located."); }
        // 返回空列表。
        return QStringList();
    }
    // 组装设置对象：只填必要的字段，其余保持默认。
    TranscodeSettings st;
    // 源文件。
    st.source = src;
    // 目标格式（可编辑下拉框的当前文本）。
    st.targetFormat = m_formatCombo->currentText().trimmed();
    // 目标格式为空时兜底 mp4。
    if (st.targetFormat.isEmpty()) { st.targetFormat = QStringLiteral("mp4"); }
    // 输出目录。
    st.outputDir = QDir::fromNativeSeparators(m_outputDirEdit->text().trimmed());
    // 输出文件名。
    st.outputName = m_outputNameEdit->text().trimmed();
    // 自定义参数：先把每一行合并，再按空白切分成参数片段。
    // 说明：这里刻意提供多行输入框，用户可以按“每行一个逻辑片段”书写，便于阅读。
    const QString extraText = m_extraArgsEdit->toPlainText().replace(QLatin1Char('\n'), QLatin1Char(' '));
    // 切分成参数列表。
    st.extraArgs = extraText.split(QRegularExpression(QStringLiteral("\\s+")), Qt::SkipEmptyParts);
    // 允许覆盖已存在的产物（本页面向高级用户，覆盖是预期行为）。
    st.overwrite = false;
    // 用命令构建器生成参数列表（info 传 nullptr，表示“源可能既有视频也有音频”，交由 ffmpeg 判断）。
    const QStringList args = buildCommand(st, nullptr, QString());
    // 参数列表为空属于异常（构建器保证至少返回程序路径与输出路径）。
    if (args.isEmpty())
    {
        // 记录错误。
        if (errorOut != nullptr) { *errorOut = QStringLiteral("Failed to build the command."); }
        // 返回空列表。
        return QStringList();
    }
    // 返回参数列表。
    return args;
}

// 生成预览。
void AdvancedPage::onBuildPreview()
{
    // 组装参数。
    QString error;
    // 执行构建。
    const QStringList args = buildArgs(&error);
    // 构建失败时展示原因。
    if (args.isEmpty())
    {
        // 预览框显示错误提示。
        m_previewEdit->setPlainText(QStringLiteral("# %1").arg(error));
        // 更新状态。
        m_statusLabel->setText(error);
        // 返回。
        return;
    }
    // 预览框显示命令文本（与实际执行完全一致）。
    m_previewEdit->setPlainText(describeCommand(args));
    // 更新状态。
    m_statusLabel->setText(QStringLiteral("Preview built: %1 argument(s).").arg(args.size()));
}

// 执行。
void AdvancedPage::onRun()
{
    // 运行中不允许重复触发。
    if (m_process->state() != QProcess::NotRunning)
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("Busy"), QStringLiteral("A command is already running."));
        // 返回。
        return;
    }
    // 组装参数。
    QString error;
    // 执行构建。
    const QStringList args = buildArgs(&error);
    // 构建失败时提示。
    if (args.isEmpty())
    {
        // 弹框提示。
        QMessageBox::warning(this, QStringLiteral("Cannot run"), error);
        // 更新状态与预览。
        m_statusLabel->setText(error);
        // 返回。
        return;
    }
    // 记录当前命令，供取消时展示。
    m_currentArgs = args;
    // 记录日志：本次执行的命令。
    appendLog(QStringLiteral("> Running: %1").arg(describeCommand(args)));
    // 设置程序。
    m_process->setProgram(args.first());
    // 设置参数。
    m_process->setArguments(args.mid(1));
    // 启动。
    m_process->start();
    // 按钮状态切换：运行中禁用运行、启用取消。
    m_runButton->setEnabled(false);
    // 启用取消。
    m_cancelButton->setEnabled(true);
    // 更新状态。
    m_statusLabel->setText(QStringLiteral("Running..."));
    // 向上层报告。
    emit statusMessage(QStringLiteral("Advanced: running a raw FFmpeg command."));
}

// 取消。
void AdvancedPage::onCancel()
{
    // 进程未运行时无需取消。
    if (m_process->state() == QProcess::NotRunning) { return; }
    // 先温和终止。
    m_process->terminate();
    // 记录日志。
    appendLog(QStringLiteral("> Cancel requested."));
    // 若 2 秒内未退出则强制结束（保证按钮永远有效）。
    if (!m_process->waitForFinished(2000))
    {
        // 强杀。
        m_process->kill();
        // 记录日志。
        appendLog(QStringLiteral("> Process killed after timeout."));
    }
}

// 进程结束。
void AdvancedPage::onProcessFinished(int exitCode, QProcess::ExitStatus status)
{
    // 读取可能尚未被上一条 readyRead 消化掉的输出。
    const QString tail = QString::fromUtf8(m_process->readAll());
    // 非空才记录。
    if (!tail.trimmed().isEmpty())
    {
        // 追加。
        appendLog(tail.trimmed());
    }
    // 汇总结果。
    if (status == QProcess::NormalExit && exitCode == 0)
    {
        // 从命令中取出输出路径（最后一个参数）以校验产物。
        const QString outPath = m_currentArgs.isEmpty() ? QString() : m_currentArgs.last();
        // 统计产物体积。
        const QFileInfo fi(outPath);
        // 存在且非空才算真正成功。
        if (fi.exists() && fi.size() > 0)
        {
            // 记录成功。
            appendLog(QStringLiteral("> Finished successfully. Output: %1 (%2 bytes)").arg(outPath).arg(fi.size()));
            // 更新状态。
            m_statusLabel->setText(QStringLiteral("Finished: %1 bytes written.").arg(fi.size()));
            // 向上层报告。
            emit statusMessage(QStringLiteral("Advanced: command finished successfully."));
        }
        else
        {
            // 返回码为 0 但没有产物：给出明确警示（这是易被忽略的失败形态）。
            appendLog(QStringLiteral("> The process exited with code 0 but produced no non-empty output file."));
            // 更新状态。
            m_statusLabel->setText(QStringLiteral("Finished with no usable output."));
            // 向上层报告。
            emit statusMessage(QStringLiteral("Advanced: command produced no usable output."));
        }
    }
    else
    {
        // 失败：记录退出码与状态。
        appendLog(QStringLiteral("> Failed. Exit code: %1").arg(exitCode));
        // 更新状态。
        m_statusLabel->setText(QStringLiteral("Failed with exit code %1.").arg(exitCode));
        // 向上层报告。
        emit statusMessage(QStringLiteral("Advanced: command failed (exit code %1).").arg(exitCode));
    }
    // 恢复按钮状态。
    m_runButton->setEnabled(true);
    // 禁用取消。
    m_cancelButton->setEnabled(false);
    // 清空当前命令记录。
    m_currentArgs.clear();
}

// 追加日志。
void AdvancedPage::appendLog(const QString& text)
{
    // 追加文本。
    m_log->appendPlainText(text);
    // 光标移到末尾。
    m_log->moveCursor(QTextCursor::End);
}