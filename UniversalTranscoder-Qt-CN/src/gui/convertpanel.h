// *****************************************************************************
// convertpanel.h —— 通用转换面板（Video / Audio / Image 三个标签页共用同一个实现）。
// 为什么三页共用一个类：三者的控件集合高度重合（文件列表、目标格式、质量、缩放、
// 水印、字幕、元数据、日志……），差异只在“哪些组可见”与“可选格式/编码器列表”。
// 用同一个类加 kind 参数生成三个页面，既减少重复代码，也保证三页行为完全一致。
// 线程模型：面板只负责“收集参数 → 交给任务队列”；具体执行与进度回传由 TaskQueue 完成，
//           队列信号通过主窗口转发到本面板的槽函数（都在主线程执行，可安全操作控件）。
// *****************************************************************************
#pragma once

// QWidget：面板基类。
#include <QWidget>
// QString / QStringList：文本与路径。
#include <QString>
#include <QStringList>
// QSet：记录本面板提交过的任务 ID。
#include <QSet>
// QProcess：抽帧功能的顺序执行。
#include <QProcess>
// 命令构建与任务队列。
#include "core/commandbuilder.h"
#include "core/taskqueue.h"
// 预设定义：applyPresetToUi 的参数类型需要完整定义（不能只做前向声明，因为要按值/引用使用其成员）。
#include "core/presets.h"

// 前置声明全部控件类型，减少头文件依赖、加快编译。
class QListWidget; // 文件列表。
class QComboBox; // 下拉框。
class QLineEdit; // 单行输入。
class QSpinBox; // 整数输入。
class QDoubleSpinBox; // 浮点输入。
class QPushButton; // 按钮。
class QProgressBar; // 进度条。
class QLabel; // 状态标签。
class QPlainTextEdit; // 日志框。
class QGroupBox; // 分组框。

// 通用转换面板。
class ConvertPanel : public QWidget
{
    Q_OBJECT

public:
    // 构造：kind 取 "video" / "audio" / "image"；queue 为全局任务队列（不接管其所有权）。
    explicit ConvertPanel(const QString& kind, TaskQueue* queue, QWidget* parent = nullptr);

    // 返回本面板的类型。
    QString kind() const;

    // 设置默认输出目录（来自 Settings 页；用户在面板里手动改过则不再覆盖）。
    void setDefaultOutputDir(const QString& dir);

    // 设置默认硬件加速策略（来自 Settings 页）。
    void setDefaultHwAccel(const QString& policy);

    // 队列进度信号转发入口。
    void onTaskProgress(const QString& tid, double percent, double speed, double eta);

    // 队列状态变化转发入口。
    void onTaskChanged(const QString& tid);

    // 队列日志转发入口。
    void onTaskLog(const QString& tid, const QString& text);

    signals :
    // 向上报告一条状态文本（显示在主窗口状态栏）。
    
    void statusMessage(const QString& text);

private
    slots :
    // 添加文件。
    
    void onAddFiles();

    // 移除选中文件。
    void onRemoveFiles();

    // 清空文件列表。
    void onClearFiles();

    // 选择输出目录。
    void onBrowseOutputDir();

    // 选择水印图片。
    void onBrowseWatermark();

    // 选择字幕文件。
    void onBrowseSubtitle();

    // 套用预设。
    void onPresetChanged(int index);

    // 开始转码。
    void onStart();

    // 打开最后一个输出文件所在目录。
    void onOpenOutputDir();

    // 视频抽帧（仅 video 页可用）。
    void onExtractFrames();

    // 抽帧进程结束。
    void onFrameProcessFinished(int exitCode, QProcess::ExitStatus status);

private:
    // 构建界面。
    void buildUi();

    // 填充目标格式下拉框。
    void fillFormatCombo();

    // 填充编码器下拉框。
    void fillEncoderCombo();

    // 把界面上的值收集成转码设置。
    TranscodeSettings collectSettings() const;

    // 把界面值回填成“预设所描述的状态”。
    void applyPresetToUi(const Preset& preset);

    // 记录一条本面板自己的日志（以 "> " 开头表示本地信息）。
    void appendLog(const QString& text);

    // 更新状态标签。
    void setStatus(const QString& text);

    // 向上层转发任务日志时使用的前缀处理。
    QString logPrefixFor(const QString& tid) const;

    // 面板类型。
    QString m_kind;
    // 任务队列。
    TaskQueue* m_queue = nullptr;
    // 本面板提交过的任务 ID 集合（用于过滤队列信号）。
    QSet<QString> m_myTasks;
    // 是否已由用户在界面里手动修改过输出目录。
    bool m_outputDirCustomized = false;
    // 默认输出目录。
    QString m_defaultOutDir;
    // 默认硬件加速策略。
    QString m_defaultHwAccel;

    // ---- 控件 ----
    // 预设下拉框。
    QComboBox* m_presetCombo = nullptr;
    // 目标格式下拉框。
    QComboBox* m_formatCombo = nullptr;
    // 输出目录输入框。
    QLineEdit* m_outputDirEdit = nullptr;
    // 命名方式下拉框。
    QComboBox* m_namingCombo = nullptr;
    // 命名模板输入框。
    QLineEdit* m_templateEdit = nullptr;
    // 质量档位下拉框。
    QComboBox* m_qualityCombo = nullptr;
    // 编码器下拉框（视频或音频，取决于面板类型）。
    QComboBox* m_encoderCombo = nullptr;
    // 视频码率输入框。
    QLineEdit* m_bitrateEdit = nullptr;
    // CRF 输入框。
    QLineEdit* m_crfEdit = nullptr;
    // 音频码率输入框。
    QLineEdit* m_audioBitrateEdit = nullptr;
    // 音频编码器下拉框。
    QComboBox* m_audioEncoderCombo = nullptr;
    // 宽度输入框。
    QSpinBox* m_widthSpin = nullptr;
    // 高度输入框。
    QSpinBox* m_heightSpin = nullptr;
    // 缩放模式下拉框。
    QComboBox* m_scaleModeCombo = nullptr;
    // 帧率输入框。
    QLineEdit* m_fpsEdit = nullptr;
    // 裁剪输入框。
    QLineEdit* m_cropEdit = nullptr;
    // 旋转下拉框。
    QComboBox* m_rotateCombo = nullptr;
    // 镜像下拉框。
    QComboBox* m_flipCombo = nullptr;
    // 水印图片输入框。
    QLineEdit* m_watermarkEdit = nullptr;
    // 水印文字输入框。
    QLineEdit* m_watermarkTextEdit = nullptr;
    // 水印位置下拉框。
    QComboBox* m_watermarkPosCombo = nullptr;
    // 水印不透明度输入框。
    QDoubleSpinBox* m_watermarkOpacitySpin = nullptr;
    // 水印宽度比例输入框。
    QDoubleSpinBox* m_watermarkScaleSpin = nullptr;
    // 字幕文件输入框。
    QLineEdit* m_subtitleEdit = nullptr;
    // 起始时间输入框。
    QLineEdit* m_startEdit = nullptr;
    // 结束时间输入框。
    QLineEdit* m_endEdit = nullptr;
    // 时长输入框。
    QLineEdit* m_durationEdit = nullptr;
    // 速度输入框。
    QDoubleSpinBox* m_speedSpin = nullptr;
    // 音量输入框。
    QLineEdit* m_volumeEdit = nullptr;
    // 采样率输入框。
    QSpinBox* m_sampleRateSpin = nullptr;
    // 声道数输入框。
    QSpinBox* m_channelsSpin = nullptr;
    // 标题输入框。
    QLineEdit* m_metaTitleEdit = nullptr;
    // 艺术家输入框。
    QLineEdit* m_metaArtistEdit = nullptr;
    // 专辑输入框。
    QLineEdit* m_metaAlbumEdit = nullptr;
    // 备注输入框。
    QLineEdit* m_metaCommentEdit = nullptr;
    // 硬件加速下拉框。
    QComboBox* m_hwCombo = nullptr;
    // 额外参数输入框。
    QLineEdit* m_extraArgsEdit = nullptr;
    // 像素格式输入框。
    QLineEdit* m_pixFmtEdit = nullptr;
    // 文件列表。
    QListWidget* m_fileList = nullptr;
    // 进度条。
    QProgressBar* m_progress = nullptr;
    // 状态标签。
    QLabel* m_statusLabel = nullptr;
    // 日志框。
    QPlainTextEdit* m_log = nullptr;
    // 开始按钮。
    QPushButton* m_startButton = nullptr;
    // 抽帧按钮。
    QPushButton* m_frameButton = nullptr;
    // 视频专用分组框（音频/图片面板会隐藏它）。
    QGroupBox* m_videoGroup = nullptr;
    // 音频专用分组框。
    QGroupBox* m_audioGroup = nullptr;
    // 水印分组框。
    QGroupBox* m_watermarkGroup = nullptr;

    // ---- 抽帧状态 ----
    // 抽帧用进程对象。
    QProcess* m_frameProcess = nullptr;
    // 待执行的命令队列。
    QVector<QStringList> m_frameCommands;
    // 当前执行到第几条命令。
    int m_frameIndex = 0;
    // 抽帧输出文件列表。
    QStringList m_frameOutputs;
};