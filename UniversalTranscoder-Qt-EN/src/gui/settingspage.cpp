// *****************************************************************************
// settingspage.cpp —— 设置页面的实现。
// 实现要点：
//   1) 修改自定义内核路径后必须清空 FFmpegLocator 与 Capabilities 的缓存，
//      否则程序会继续使用旧内核（这是最容易出错的地方，故在此显式处理）。
//   2) 并发数、日志级别、默认硬件加速都是“全局行为”，保存后立即应用：
//      并发数交给任务队列，日志级别交给全局消息处理器，硬件加速交给各转换页。
//   3) 设置项全部通过 QSettings 持久化，键名固定且可读，便于用户自行排查。
// *****************************************************************************
#include "settingspage.h"

// Qt 控件与布局。
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFormLayout>
#include <QGroupBox>
#include <QLabel>
#include <QLineEdit>
#include <QSpinBox>
#include <QComboBox>
#include <QPushButton>
#include <QFileDialog>
#include <QMessageBox>
#include <QSettings>
#include <QDir>
#include <QFileInfo>
// 内核、能力与硬件探测。
#include "core/ffmpeg_locator.h"
#include "core/capabilities.h"
#include "core/hwaccel.h"

// 全局日志级别（默认 info）。
static QString g_logLevel = QStringLiteral("info");

// 读取全局日志级别。
QString globalLogLevel()
{
    // 返回当前值。
    return g_logLevel;
}

// 设置全局日志级别。
void setGlobalLogLevel(const QString& level)
{
    // 归一化并写入（只接受四个已知取值之一，其余退回 info）。
    const QString v = level.trimmed().toLower();
    // 判定是否合法。
    const bool valid = (v == QStringLiteral("error") || v == QStringLiteral("warning")
        || v == QStringLiteral("info") || v == QStringLiteral("debug"));
    // 写入。
    g_logLevel = valid ? v : QStringLiteral("info");
}

// 构造。
SettingsPage::SettingsPage(TaskQueue* queue, QWidget* parent)
    : QWidget(parent)
      , m_queue(queue)
{
    // 构建界面。
    buildUi();
    // 载入已保存的设置。
    loadSettings();
    // 刷新内核状态。
    refreshKernelStatus();
}

// 构建界面。
void SettingsPage::buildUi()
{
    // 根布局。
    QVBoxLayout* root = new QVBoxLayout(this);
    // 边距。
    root->setContentsMargins(10, 10, 10, 10);
    // 间距。
    root->setSpacing(8);

    // 说明标签（英文）。
    QLabel* intro = new QLabel;
    // 说明文本。
    intro->setText(QStringLiteral(
        "These settings apply globally. The FFmpeg location can be an executable or a folder; the bundled copy under 'resources/ffmpeg' is used when the field is empty."));
    // 允许换行。
    intro->setWordWrap(true);
    // 加入根布局。
    root->addWidget(intro);

    // ---- 内核分组 ----
    QGroupBox* kernelGroup = new QGroupBox(QStringLiteral("FFmpeg kernel"));
    // 内核区表单布局。
    QFormLayout* kernelForm = new QFormLayout(kernelGroup);
    // 自定义路径行（输入框 + 浏览 + 应用按钮）。
    QWidget* pathRow = new QWidget;
    // 路径行布局。
    QHBoxLayout* pathLayout = new QHBoxLayout(pathRow);
    // 去掉边距。
    pathLayout->setContentsMargins(0, 0, 0, 0);
    // 路径输入框。
    m_ffmpegPathEdit = new QLineEdit;
    // 占位提示。
    m_ffmpegPathEdit->setPlaceholderText(QStringLiteral("Custom ffmpeg.exe / ffprobe folder (empty = bundled copy)"));
    // 浏览按钮。
    QPushButton* ffBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    ffBtn->setFixedWidth(32);
    // 应用按钮。
    QPushButton* applyBtn = new QPushButton(QStringLiteral("Apply"));
    // 调整宽度。
    applyBtn->setFixedWidth(70);
    // 加入行布局。
    pathLayout->addWidget(m_ffmpegPathEdit, 1);
    // 追加浏览按钮。
    pathLayout->addWidget(ffBtn);
    // 追加应用按钮。
    pathLayout->addWidget(applyBtn);
    // 加入表单。
    kernelForm->addRow(QStringLiteral("Location"), pathRow);
    // 内核状态标签。
    m_kernelLabel = new QLabel;
    // 允许换行。
    m_kernelLabel->setWordWrap(true);
    // 加入表单。
    kernelForm->addRow(QStringLiteral("Status"), m_kernelLabel);
    // 能力摘要标签。
    m_capabilityLabel = new QLabel;
    // 允许换行。
    m_capabilityLabel->setWordWrap(true);
    // 加入表单。
    kernelForm->addRow(QStringLiteral("Capabilities"), m_capabilityLabel);
    // 加入根布局。
    root->addWidget(kernelGroup);

    // ---- 行为分组 ----
    QGroupBox* behaviourGroup = new QGroupBox(QStringLiteral("Behaviour"));
    // 行为区表单布局。
    QFormLayout* behaviourForm = new QFormLayout(behaviourGroup);
    // 默认输出目录行。
    QWidget* outRow = new QWidget;
    // 输出目录行布局。
    QHBoxLayout* outLayout = new QHBoxLayout(outRow);
    // 去掉边距。
    outLayout->setContentsMargins(0, 0, 0, 0);
    // 目录输入框。
    m_outputDirEdit = new QLineEdit;
    // 占位提示（空表示与源文件同目录）。
    m_outputDirEdit->setPlaceholderText(QStringLiteral("Empty = same folder as each source file"));
    // 浏览按钮。
    QPushButton* outBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    outBtn->setFixedWidth(32);
    // 加入行布局。
    outLayout->addWidget(m_outputDirEdit, 1);
    // 追加按钮。
    outLayout->addWidget(outBtn);
    // 加入表单。
    behaviourForm->addRow(QStringLiteral("Default output folder"), outRow);
    // 并发数输入框。
    m_concurrencySpin = new QSpinBox;
    // 范围 1 ~ 8（更高并发在机械硬盘上会互相拖慢，收益为负）。
    m_concurrencySpin->setRange(1, 8);
    // 默认 1。
    m_concurrencySpin->setValue(1);
    // 加入表单。
    behaviourForm->addRow(QStringLiteral("Max parallel tasks"), m_concurrencySpin);
    // 日志级别下拉框。
    m_logLevelCombo = new QComboBox;
    // 四个级别（英文显示）。
    m_logLevelCombo->addItem(QStringLiteral("Error"), QStringLiteral("error"));
    // 警告。
    m_logLevelCombo->addItem(QStringLiteral("Warning"), QStringLiteral("warning"));
    // 信息（默认）。
    m_logLevelCombo->addItem(QStringLiteral("Info"), QStringLiteral("info"));
    // 调试（排障用，输出最详细）。
    m_logLevelCombo->addItem(QStringLiteral("Debug"), QStringLiteral("debug"));
    // 默认信息级。
    m_logLevelCombo->setCurrentIndex(2);
    // 加入表单。
    behaviourForm->addRow(QStringLiteral("Log level"), m_logLevelCombo);
    // 默认硬件加速下拉框。
    m_hwCombo = new QComboBox;
    // 软件编码（默认，任何机器都能成功）。
    m_hwCombo->addItem(QStringLiteral("Software only (stable)"), QStringLiteral("none"));
    // 自动挑选硬件。
    m_hwCombo->addItem(QStringLiteral("Auto (use hardware if available)"), QStringLiteral("auto"));
    // 加入表单。
    behaviourForm->addRow(QStringLiteral("Default hardware accel"), m_hwCombo);
    // 加入根布局。
    root->addWidget(behaviourGroup);

    // ---- 按钮行 ----
    // 按钮容器。
    QWidget* btnRow = new QWidget;
    // 按钮行布局。
    QHBoxLayout* btnLayout = new QHBoxLayout(btnRow);
    // 去掉边距。
    btnLayout->setContentsMargins(0, 0, 0, 0);
    // 保存按钮（主按钮）。
    QPushButton* saveBtn = new QPushButton(QStringLiteral("Save settings"));
    // 标记为主按钮。
    saveBtn->setProperty("primary", true);
    // 恢复默认按钮。
    QPushButton* resetBtn = new QPushButton(QStringLiteral("Reset to defaults"));
    // 加入。
    btnLayout->addWidget(saveBtn);
    // 追加恢复按钮。
    btnLayout->addWidget(resetBtn);
    // 余量留白。
    btnLayout->addStretch(1);
    // 加入根布局。
    root->addWidget(btnRow);
    // 末尾留白，让内容顶部对齐。
    root->addStretch(1);

    // ---- 信号连接 ----
    // 浏览内核路径。
    connect(ffBtn, &QPushButton::clicked, this, &SettingsPage::onBrowseFfmpeg);
    // 应用内核路径。
    connect(applyBtn, &QPushButton::clicked, this, &SettingsPage::onApplyKernel);
    // 浏览默认输出目录。
    connect(outBtn, &QPushButton::clicked, this, &SettingsPage::onBrowseOutput);
    // 保存。
    connect(saveBtn, &QPushButton::clicked, this, &SettingsPage::onSave);
    // 恢复默认。
    connect(resetBtn, &QPushButton::clicked, this, &SettingsPage::onResetDefaults);
    // 并发数变化时立即生效，用户可即时看到队列上限的更新。
    connect(m_concurrencySpin, QOverload<int>::of(&QSpinBox::valueChanged), this, [this](int value)
    {
        // 应用到任务队列。
        m_queue->setMaxParallel(value);
    });
    // 日志级别变化时立即写入全局变量。
    connect(m_logLevelCombo, &QComboBox::currentIndexChanged, this, [this](int index)
    {
        // 设置全局级别。
        setGlobalLogLevel(m_logLevelCombo->itemData(index).toString());
    });
}

// 载入设置。
void SettingsPage::loadSettings()
{
    // 打开设置对象（组织名与应用名在 main.cpp 中设置）。
    QSettings s;
    // 自定义内核路径。
    m_ffmpegPathEdit->setText(s.value(QStringLiteral("kernel/customPath")).toString());
    // 默认输出目录。
    m_outputDirEdit->setText(s.value(QStringLiteral("output/defaultDir")).toString());
    // 并发数。
    m_concurrencySpin->setValue(s.value(QStringLiteral("queue/maxParallel"), 1).toInt());
    // 日志级别：按数据值查找并选中。
    const QString level = s.value(QStringLiteral("log/level"), QStringLiteral("info")).toString();
    // 遍历选项。
    for (int i = 0; i < m_logLevelCombo->count(); ++i)
    {
        // 命中即选中。
        if (m_logLevelCombo->itemData(i).toString() == level)
        {
            // 选中。
            m_logLevelCombo->setCurrentIndex(i);
            // 结束。
            break;
        }
    }
    // 默认硬件加速。
    const QString hw = s.value(QStringLiteral("hwaccel/default"), QStringLiteral("none")).toString();
    // 遍历选项。
    for (int i = 0; i < m_hwCombo->count(); ++i)
    {
        // 命中即选中。
        if (m_hwCombo->itemData(i).toString() == hw)
        {
            // 选中。
            m_hwCombo->setCurrentIndex(i);
            // 结束。
            break;
        }
    }
    // 立即应用内核路径、并发数与日志级别，保证程序启动后行为与设置一致。
    FFmpegLocator::instance().setCustomPath(m_ffmpegPathEdit->text().trimmed());
    // 应用并发。
    m_queue->setMaxParallel(m_concurrencySpin->value());
    // 应用日志级别。
    setGlobalLogLevel(m_logLevelCombo->currentData().toString());
}

// 保存设置。
void SettingsPage::persistSettings()
{
    // 打开设置对象。
    QSettings s;
    // 写入自定义内核路径（使用原生分隔符便于用户阅读）。
    s.setValue(QStringLiteral("kernel/customPath"), QDir::fromNativeSeparators(m_ffmpegPathEdit->text().trimmed()));
    // 写入默认输出目录。
    s.setValue(QStringLiteral("output/defaultDir"), QDir::fromNativeSeparators(m_outputDirEdit->text().trimmed()));
    // 写入并发数。
    s.setValue(QStringLiteral("queue/maxParallel"), m_concurrencySpin->value());
    // 写入日志级别。
    s.setValue(QStringLiteral("log/level"), m_logLevelCombo->currentData().toString());
    // 写入默认硬件加速。
    s.setValue(QStringLiteral("hwaccel/default"), m_hwCombo->currentData().toString());
    // 立即应用（无需重启）。
    FFmpegLocator::instance().setCustomPath(m_ffmpegPathEdit->text().trimmed());
    // 应用并发。
    m_queue->setMaxParallel(m_concurrencySpin->value());
    // 应用日志级别。
    setGlobalLogLevel(m_logLevelCombo->currentData().toString());
}

// 选择内核路径。
void SettingsPage::onBrowseFfmpeg()
{
    // 先让用户选择 ffmpeg 可执行文件（最常见的方式）。
    const QString file = QFileDialog::getOpenFileName(this, QStringLiteral("Select ffmpeg executable or pick a folder"),
                                                      m_ffmpegPathEdit->text(),
                                                      QStringLiteral("FFmpeg executable (ffmpeg.exe);;All files (*)"));
    // 用户取消时返回值基于当前文本；这里直接判断是否选择了文件。
    if (!file.isEmpty())
    {
        // 回填为文件路径。
        m_ffmpegPathEdit->setText(QDir::toNativeSeparators(file));
        // 结束。
        return;
    }
    // 用户可能想指定一个目录（目录里同时放着 ffmpeg.exe 与 ffprobe.exe），这里再提供一次目录选择。
    const QString dir = QFileDialog::getExistingDirectory(
        this, QStringLiteral("Or select the folder containing ffmpeg.exe and ffprobe.exe"), m_ffmpegPathEdit->text());
    // 取消时返回。
    if (dir.isEmpty()) { return; }
    // 回填目录路径。
    m_ffmpegPathEdit->setText(QDir::toNativeSeparators(dir));
}

// 选择默认输出目录。
void SettingsPage::onBrowseOutput()
{
    // 打开目录选择对话框。
    const QString dir = QFileDialog::getExistingDirectory(this, QStringLiteral("Select default output folder"),
                                                          m_outputDirEdit->text());
    // 取消时返回。
    if (dir.isEmpty()) { return; }
    // 回填。
    m_outputDirEdit->setText(QDir::toNativeSeparators(dir));
}

// 应用内核路径。
void SettingsPage::onApplyKernel()
{
    // 设置自定义路径（内部会清空定位器缓存）。
    FFmpegLocator::instance().setCustomPath(m_ffmpegPathEdit->text().trimmed());
    // 清空能力矩阵缓存（内核换了，能力必然要重新探测）。
    Capabilities::instance().clearCache();
    // 清空硬件编码器缓存。
    HwAccel::clearCache();
    // 刷新状态标签。
    refreshKernelStatus();
    // 保存设置（让“应用”与“保存”行为一致，避免用户疑惑为什么重启后又变回去）。
    persistSettings();
    // 弹出提示（英文）。
    QMessageBox::information(this, QStringLiteral("Kernel re-checked"),
                             QStringLiteral(
                                 "The FFmpeg location has been re-validated. See the status line above for the result."));
    // 向上层报告。
    emit statusMessage(QStringLiteral("FFmpeg kernel location applied and re-validated."));
    // 通知其它页面同步。
    emit settingsChanged(defaultOutputDir(), defaultHwAccel(), concurrency(), logLevel());
}

// 保存。
void SettingsPage::onSave()
{
    // 写入并应用。
    persistSettings();
    // 清空缓存以保证新的内核路径立即生效。
    Capabilities::instance().clearCache();
    // 清空硬件缓存。
    HwAccel::clearCache();
    // 刷新状态。
    refreshKernelStatus();
    // 提示（英文）。
    QMessageBox::information(this, QStringLiteral("Saved"), QStringLiteral("Settings saved and applied immediately."));
    // 向上层报告。
    emit statusMessage(QStringLiteral("Settings saved."));
    // 通知其它页面同步。
    emit settingsChanged(defaultOutputDir(), defaultHwAccel(), concurrency(), logLevel());
}

// 恢复默认。
void SettingsPage::onResetDefaults()
{
    // 清空自定义内核路径（回到随包内核）。
    m_ffmpegPathEdit->clear();
    // 清空默认输出目录。
    m_outputDirEdit->clear();
    // 并发数恢复为 1。
    m_concurrencySpin->setValue(1);
    // 日志级别恢复为 Info。
    m_logLevelCombo->setCurrentIndex(2);
    // 硬件加速恢复为软件编码。
    m_hwCombo->setCurrentIndex(0);
    // 写入并应用。
    persistSettings();
    // 清空缓存。
    Capabilities::instance().clearCache();
    // 清空硬件缓存。
    HwAccel::clearCache();
    // 刷新状态。
    refreshKernelStatus();
    // 向上层报告。
    emit statusMessage(QStringLiteral("Settings restored to defaults."));
    // 通知其它页面同步。
    emit settingsChanged(defaultOutputDir(), defaultHwAccel(), concurrency(), logLevel());
}

// 刷新内核状态。
void SettingsPage::refreshKernelStatus()
{
    // 取 ffmpeg 与 ffprobe 路径。
    const QString ff = FFmpegLocator::instance().ffmpegPath();
    // ffprobe 路径。
    const QString fp = FFmpegLocator::instance().ffprobePath();
    // 两者都可用才算就绪。
    if (!ff.isEmpty() && !fp.isEmpty())
    {
        // 状态标签：显示两个实际路径（这是最有价值的信息）。
        m_kernelLabel->setText(QStringLiteral("OK\nffmpeg: %1\nffprobe: %2").arg(ff, fp));
    }
    else
    {
        // 未就绪时给出可执行的建议。
        m_kernelLabel->setText(QStringLiteral(
            "Not ready. Put ffmpeg.exe and ffprobe.exe into the 'resources/ffmpeg' folder next to the executable, or set a custom location above."));
    }
    // 取能力摘要。
    const Capabilities::Summary sum = Capabilities::instance().summary();
    // 组装能力摘要文本（英文）。
    m_capabilityLabel->setText(
        QStringLiteral("Encoders: %1 (video %2, audio %3, subtitle %4)   |   Muxers/demuxers: %5   |   HW backends: %6")
        .arg(sum.encoderTotal)
        .arg(sum.videoEncoders)
        .arg(sum.audioEncoders)
        .arg(sum.subtitleEncoders)
        .arg(sum.formatTotal)
        .arg(sum.hwaccels.isEmpty() ? QStringLiteral("none") : sum.hwaccels.join(QLatin1Char(','))));
}

// 读取默认输出目录。
QString SettingsPage::defaultOutputDir() const
{
    // 返回规范化后的值。
    return QDir::fromNativeSeparators(m_outputDirEdit->text().trimmed());
}

// 读取默认硬件加速。
QString SettingsPage::defaultHwAccel() const
{
    // 返回下拉框当前数据值。
    return m_hwCombo->currentData().toString();
}

// 读取并发数。
int SettingsPage::concurrency() const
{
    // 返回数值。
    return m_concurrencySpin->value();
}

// 读取日志级别。
QString SettingsPage::logLevel() const
{
    // 返回下拉框当前数据值。
    return m_logLevelCombo->currentData().toString();
}