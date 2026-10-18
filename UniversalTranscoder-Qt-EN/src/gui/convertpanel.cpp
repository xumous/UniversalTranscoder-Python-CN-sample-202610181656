// *****************************************************************************
// convertpanel.cpp —— 通用转换面板的实现。
// 布局思路：
//   左侧为“参数区”（放在滚动区域里，小屏也能完整看到全部控件）；
//   右侧为“操作区”：文件列表 + 进度条 + 状态标签 + 日志框 + 动作按钮。
// 实现要点：
//   1) 目标格式与编码器下拉框的内容全部来自真实内核探测（能力矩阵），
//      内核不支持的项直接不列出，从源头避免“选了却做不了”的挫败感。
//   2) 面板只收集参数并把任务交给全局队列；转码过程与进度上报完全由队列负责，
//      面板通过“任务 ID 集合”过滤属于自己的信号。
//   3) 视频抽帧是唯一需要面板自己驱动进程的功能：因为它的产物是若干张图片，
//      而不是一个“转码任务”，不适合塞进任务队列（会污染队列的状态机语义）。
// *****************************************************************************
#include "convertpanel.h"

// Qt 控件与布局。
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFormLayout>
#include <QGridLayout>
#include <QGroupBox>
#include <QScrollArea>
#include <QSplitter>
#include <QLabel>
#include <QLineEdit>
#include <QComboBox>
#include <QSpinBox>
#include <QDoubleSpinBox>
#include <QPushButton>
#include <QProgressBar>
#include <QPlainTextEdit>
#include <QListWidget>
#include <QListWidgetItem>
#include <QFileDialog>
#include <QMessageBox>
#include <QDir>
#include <QFileInfo>
#include <QDesktopServices>
#include <QUrl>
#include <QProcess>
// QTextCursor：把日志框光标移动到末尾。
#include <QTextCursor>
// QRegularExpression：把“额外参数”按空白切分成参数列表。
#include <QRegularExpression>
#include <QDebug>
// 内核能力与预设。
#include "core/capabilities.h"
#include "core/presets.h"
#include "core/hwaccel.h"
#include "core/ffmpeg_locator.h"
#include "core/processutil.h"

// 构造。
ConvertPanel::ConvertPanel(const QString& kind, TaskQueue* queue, QWidget* parent)
    : QWidget(parent)
      , m_kind(kind)
      , m_queue(queue)
{
    // 默认硬件加速策略：软件编码（任何机器上都能成功，最稳妥）。
    m_defaultHwAccel = QStringLiteral("none");
    // 构建界面。
    buildUi();
    // 填充下拉框内容。
    fillFormatCombo();
    // 填充编码器下拉框。
    fillEncoderCombo();
    // 根据类型显示/隐藏专用分组。
    m_videoGroup->setVisible(m_kind == QStringLiteral("video") || m_kind == QStringLiteral("image"));
    // 音频组只在音频与视频面板显示（图片没有音频）。
    m_audioGroup->setVisible(m_kind != QStringLiteral("image"));
    // 水印组只在视频面板显示。
    m_watermarkGroup->setVisible(m_kind == QStringLiteral("video"));
    // 抽帧按钮只在视频面板显示。
    m_frameButton->setVisible(m_kind == QStringLiteral("video"));
    // 初始化状态文本。
    setStatus(QStringLiteral("Ready."));
}

// 返回类型。
QString ConvertPanel::kind() const
{
    // 直接返回成员。
    return m_kind;
}

// 构建界面。
void ConvertPanel::buildUi()
{
    // ---- 参数区左栏 ----
    // 参数容器（放在滚动区域里）。
    QWidget* paramHost = new QWidget;
    // 参数区竖向布局。
    QVBoxLayout* paramLayout = new QVBoxLayout(paramHost);
    // 收紧边距，让有限的高度容纳更多控件。
    paramLayout->setContentsMargins(6, 6, 6, 6);
    // 控件之间的间距。
    paramLayout->setSpacing(8);

    // ---- 预设分组 ----
    QGroupBox* presetGroup = new QGroupBox(QStringLiteral("Preset"));
    // 预设区表单布局。
    QFormLayout* presetForm = new QFormLayout(presetGroup);
    // 预设下拉框。
    m_presetCombo = new QComboBox;
    // 首个条目表示“不使用预设”。
    m_presetCombo->addItem(QStringLiteral("(custom settings)"), QString());
    // 按类别列出内置预设（共 35 套中的本类部分）。
    for (const Preset& p : presetsByCategory(m_kind))
    {
        // 把预设名作为显示文本，key 作为数据。
        m_presetCombo->addItem(p.name, p.key);
    }
    // 加入表单。
    presetForm->addRow(QStringLiteral("Preset"), m_presetCombo);
    // 加入参数区。
    paramLayout->addWidget(presetGroup);

    // ---- 基础分组 ----
    QGroupBox* basicGroup = new QGroupBox(QStringLiteral("Output"));
    // 基础区表单布局。
    QFormLayout* basicForm = new QFormLayout(basicGroup);
    // 目标格式下拉框。
    m_formatCombo = new QComboBox;
    // 加入表单。
    basicForm->addRow(QStringLiteral("Target format"), m_formatCombo);
    // 输出目录输入框 + 浏览按钮（用横向容器并排放置）。
    QWidget* dirRow = new QWidget;
    // 输出目录行布局。
    QHBoxLayout* dirLayout = new QHBoxLayout(dirRow);
    // 去掉边距，让输入框与按钮紧贴。
    dirLayout->setContentsMargins(0, 0, 0, 0);
    // 输出目录输入框。
    m_outputDirEdit = new QLineEdit;
    // 占位提示（英文）。
    m_outputDirEdit->setPlaceholderText(QStringLiteral("Same folder as the source"));
    // 浏览按钮。
    QPushButton* dirBtn = new QPushButton(QStringLiteral("..."));
    // 浏览按钮固定宽度，避免占用过多横向空间。
    dirBtn->setFixedWidth(32);
    // 加入行布局。
    dirLayout->addWidget(m_outputDirEdit, 1);
    // 追加按钮。
    dirLayout->addWidget(dirBtn);
    // 加入表单。
    basicForm->addRow(QStringLiteral("Output folder"), dirRow);
    // 命名方式下拉框。
    m_namingCombo = new QComboBox;
    // 三种命名方式（显示文本用英文）。
    m_namingCombo->addItem(QStringLiteral("Replace extension"), QStringLiteral("replace"));
    // 原名 + 后缀。
    m_namingCombo->addItem(QStringLiteral("Append target format"), QStringLiteral("suffix"));
    // 自定义模板。
    m_namingCombo->addItem(QStringLiteral("Custom template"), QStringLiteral("custom"));
    // 加入表单。
    basicForm->addRow(QStringLiteral("Naming"), m_namingCombo);
    // 命名模板输入框。
    m_templateEdit = new QLineEdit(QStringLiteral("{name}_{index}"));
    // 模板说明（英文提示）。
    m_templateEdit->setToolTip(QStringLiteral("Placeholders: {name} {ext} {index} {date}"));
    // 加入表单。
    basicForm->addRow(QStringLiteral("Template"), m_templateEdit);
    // 加入参数区。
    paramLayout->addWidget(basicGroup);

    // ---- 质量与编码分组 ----
    QGroupBox* codecGroup = new QGroupBox(QStringLiteral("Quality & codec"));
    // 编码区表单布局。
    QFormLayout* codecForm = new QFormLayout(codecGroup);
    // 质量档位下拉框。
    m_qualityCombo = new QComboBox;
    // 四档质量（英文显示）。
    m_qualityCombo->addItem(QStringLiteral("Lossless"), QStringLiteral("lossless"));
    // 高质量。
    m_qualityCombo->addItem(QStringLiteral("High"), QStringLiteral("high"));
    // 中等（默认）。
    m_qualityCombo->addItem(QStringLiteral("Medium"), QStringLiteral("medium"));
    // 低质量（小体积）。
    m_qualityCombo->addItem(QStringLiteral("Low"), QStringLiteral("low"));
    // 默认选中“中等”。
    m_qualityCombo->setCurrentIndex(2);
    // 加入表单。
    codecForm->addRow(QStringLiteral("Quality"), m_qualityCombo);
    // 视频编码器下拉框（音频面板不使用它，因此统一给父对象以避免对象无人接管）。
    m_encoderCombo = new QComboBox(codecGroup);
    // 音频编码器下拉框（图片面板不使用它，同样先给父对象）。
    m_audioEncoderCombo = new QComboBox(codecGroup);
    // 音频面板只显示音频编码器；视频/图片面板显示视频编码器。
    if (m_kind == QStringLiteral("audio"))
    {
        // 音频面板不显示视频编码器。
        codecForm->addRow(QStringLiteral("Audio codec"), m_audioEncoderCombo);
    }
    else
    {
        // 视频/图片面板显示编码器。
        codecForm->addRow(QStringLiteral("Codec"), m_encoderCombo);
        // 视频面板额外显示音频编码器（因为视频文件同时含音频）。
        if (m_kind == QStringLiteral("video"))
        {
            // 追加音频编码器行。
            codecForm->addRow(QStringLiteral("Audio codec"), m_audioEncoderCombo);
        }
    }
    // 视频码率输入框（非视频面板不使用它，统一给父对象避免无人接管）。
    m_bitrateEdit = new QLineEdit(codecGroup);
    // 占位提示：留空表示由质量档位决定。
    m_bitrateEdit->setPlaceholderText(QStringLiteral("Empty = derive from quality"));
    // 仅视频面板需要码率行。
    if (m_kind == QStringLiteral("video"))
    {
        // 加入表单。
        codecForm->addRow(QStringLiteral("Video bitrate"), m_bitrateEdit);
    }
    // CRF 输入框。
    m_crfEdit = new QLineEdit(codecGroup);
    // 占位提示。
    m_crfEdit->setPlaceholderText(QStringLiteral("Empty = derive from quality"));
    // 仅视频面板需要 CRF 行。
    if (m_kind == QStringLiteral("video"))
    {
        // 加入表单。
        codecForm->addRow(QStringLiteral("CRF"), m_crfEdit);
    }
    // 音频码率输入框。
    m_audioBitrateEdit = new QLineEdit(codecGroup);
    // 占位提示。
    m_audioBitrateEdit->setPlaceholderText(QStringLiteral("e.g. 192k"));
    // 非图片面板需要音频码率行。
    if (m_kind != QStringLiteral("image"))
    {
        // 加入表单。
        codecForm->addRow(QStringLiteral("Audio bitrate"), m_audioBitrateEdit);
    }
    // 像素格式输入框。
    m_pixFmtEdit = new QLineEdit(QStringLiteral("yuv420p"), codecGroup);
    // 加入表单（仅视频面板）。
    if (m_kind == QStringLiteral("video"))
    {
        // 追加像素格式行。
        codecForm->addRow(QStringLiteral("Pixel format"), m_pixFmtEdit);
    }
    // 硬件加速下拉框（仅视频面板使用，先给父对象避免无人接管）。
    m_hwCombo = new QComboBox(codecGroup);
    // 加入表单。
    if (m_kind == QStringLiteral("video"))
    {
        // 追加硬件加速行。
        codecForm->addRow(QStringLiteral("Hardware accel"), m_hwCombo);
    }
    // 加入参数区。
    paramLayout->addWidget(codecGroup);

    // ---- 画面分组 ----
    m_videoGroup = new QGroupBox(QStringLiteral("Picture"));
    // 画面区表单布局。
    QFormLayout* picForm = new QFormLayout(m_videoGroup);
    // 宽高输入：用一个横向容器并排放两个数字框。
    QWidget* sizeRow = new QWidget;
    // 宽高行布局。
    QHBoxLayout* sizeLayout = new QHBoxLayout(sizeRow);
    // 去掉边距。
    sizeLayout->setContentsMargins(0, 0, 0, 0);
    // 宽度数字框（0 表示保持原样，上限 8192）。
    m_widthSpin = new QSpinBox;
    // 范围设置。
    m_widthSpin->setRange(0, 8192);
    // 特殊值文本：0 显示为“original”。
    m_widthSpin->setSpecialValueText(QStringLiteral("original"));
    // 高度数字框。
    m_heightSpin = new QSpinBox;
    // 范围设置。
    m_heightSpin->setRange(0, 8192);
    // 特殊值文本。
    m_heightSpin->setSpecialValueText(QStringLiteral("original"));
    // 加入行布局。
    sizeLayout->addWidget(m_widthSpin, 1);
    // 乘号标签。
    sizeLayout->addWidget(new QLabel(QStringLiteral("x")));
    // 高度框加入。
    sizeLayout->addWidget(m_heightSpin, 1);
    // 加入表单。
    picForm->addRow(QStringLiteral("Size (W x H)"), sizeRow);
    // 缩放模式下拉框。
    m_scaleModeCombo = new QComboBox;
    // 适应（默认）。
    m_scaleModeCombo->addItem(QStringLiteral("Fit (letterbox)"), QStringLiteral("fit"));
    // 填充裁剪。
    m_scaleModeCombo->addItem(QStringLiteral("Fill (crop)"), QStringLiteral("fill"));
    // 拉伸。
    m_scaleModeCombo->addItem(QStringLiteral("Stretch"), QStringLiteral("stretch"));
    // 加入表单。
    picForm->addRow(QStringLiteral("Scale mode"), m_scaleModeCombo);
    // 帧率输入框。
    m_fpsEdit = new QLineEdit;
    // 占位提示（支持分数帧率）。
    m_fpsEdit->setPlaceholderText(QStringLiteral("e.g. 25 or 30000/1001"));
    // 加入表单。
    picForm->addRow(QStringLiteral("Frame rate"), m_fpsEdit);
    // 裁剪输入框。
    m_cropEdit = new QLineEdit;
    // 占位提示。
    m_cropEdit->setPlaceholderText(QStringLiteral("w:h:x:y (empty = none)"));
    // 加入表单。
    picForm->addRow(QStringLiteral("Crop"), m_cropEdit);
    // 旋转下拉框。
    m_rotateCombo = new QComboBox;
    // 四个角度。
    m_rotateCombo->addItem(QStringLiteral("None"), 0);
    // 顺时针 90 度。
    m_rotateCombo->addItem(QStringLiteral("90 deg"), 90);
    // 180 度。
    m_rotateCombo->addItem(QStringLiteral("180 deg"), 180);
    // 顺时针 270 度。
    m_rotateCombo->addItem(QStringLiteral("270 deg"), 270);
    // 加入表单。
    picForm->addRow(QStringLiteral("Rotate"), m_rotateCombo);
    // 镜像下拉框。
    m_flipCombo = new QComboBox;
    // 不镜像。
    m_flipCombo->addItem(QStringLiteral("None"), QStringLiteral("none"));
    // 水平镜像。
    m_flipCombo->addItem(QStringLiteral("Horizontal"), QStringLiteral("h"));
    // 垂直镜像。
    m_flipCombo->addItem(QStringLiteral("Vertical"), QStringLiteral("v"));
    // 双向镜像。
    m_flipCombo->addItem(QStringLiteral("Both"), QStringLiteral("both"));
    // 加入表单。
    picForm->addRow(QStringLiteral("Mirror"), m_flipCombo);
    // 加入参数区。
    paramLayout->addWidget(m_videoGroup);

    // ---- 水印与字幕分组 ----
    m_watermarkGroup = new QGroupBox(QStringLiteral("Watermark & subtitle"));
    // 水印区表单布局。
    QFormLayout* wmForm = new QFormLayout(m_watermarkGroup);
    // 水印图片行（输入框 + 浏览按钮）。
    QWidget* wmRow = new QWidget;
    // 水印行布局。
    QHBoxLayout* wmLayout = new QHBoxLayout(wmRow);
    // 去掉边距。
    wmLayout->setContentsMargins(0, 0, 0, 0);
    // 水印图片输入框。
    m_watermarkEdit = new QLineEdit;
    // 占位提示。
    m_watermarkEdit->setPlaceholderText(QStringLiteral("Image file (optional)"));
    // 浏览按钮。
    QPushButton* wmBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    wmBtn->setFixedWidth(32);
    // 加入行布局。
    wmLayout->addWidget(m_watermarkEdit, 1);
    // 追加按钮。
    wmLayout->addWidget(wmBtn);
    // 加入表单。
    wmForm->addRow(QStringLiteral("Image watermark"), wmRow);
    // 水印文字输入框。
    m_watermarkTextEdit = new QLineEdit;
    // 占位提示。
    m_watermarkTextEdit->setPlaceholderText(QStringLiteral("Text watermark (optional)"));
    // 加入表单。
    wmForm->addRow(QStringLiteral("Text watermark"), m_watermarkTextEdit);
    // 水印位置下拉框。
    m_watermarkPosCombo = new QComboBox;
    // 七个位置。
    m_watermarkPosCombo->addItem(QStringLiteral("Top left"), QStringLiteral("tl"));
    // 右上（默认）。
    m_watermarkPosCombo->addItem(QStringLiteral("Top right"), QStringLiteral("tr"));
    // 左下。
    m_watermarkPosCombo->addItem(QStringLiteral("Bottom left"), QStringLiteral("bl"));
    // 右下。
    m_watermarkPosCombo->addItem(QStringLiteral("Bottom right"), QStringLiteral("br"));
    // 上中。
    m_watermarkPosCombo->addItem(QStringLiteral("Top center"), QStringLiteral("tc"));
    // 下中。
    m_watermarkPosCombo->addItem(QStringLiteral("Bottom center"), QStringLiteral("bc"));
    // 正中。
    m_watermarkPosCombo->addItem(QStringLiteral("Center"), QStringLiteral("center"));
    // 默认右上。
    m_watermarkPosCombo->setCurrentIndex(1);
    // 加入表单。
    wmForm->addRow(QStringLiteral("Position"), m_watermarkPosCombo);
    // 水印不透明度。
    m_watermarkOpacitySpin = new QDoubleSpinBox;
    // 范围 0.05 ~ 1.00。
    m_watermarkOpacitySpin->setRange(0.05, 1.00);
    // 步长 0.05。
    m_watermarkOpacitySpin->setSingleStep(0.05);
    // 默认 0.75。
    m_watermarkOpacitySpin->setValue(0.75);
    // 加入表单。
    wmForm->addRow(QStringLiteral("Opacity"), m_watermarkOpacitySpin);
    // 水印宽度比例。
    m_watermarkScaleSpin = new QDoubleSpinBox;
    // 范围 0.05 ~ 1.00。
    m_watermarkScaleSpin->setRange(0.05, 1.00);
    // 步长 0.05。
    m_watermarkScaleSpin->setSingleStep(0.05);
    // 默认 0.15。
    m_watermarkScaleSpin->setValue(0.15);
    // 加入表单。
    wmForm->addRow(QStringLiteral("Image width ratio"), m_watermarkScaleSpin);
    // 字幕行（输入框 + 浏览按钮）。
    QWidget* subRow = new QWidget;
    // 字幕行布局。
    QHBoxLayout* subLayout = new QHBoxLayout(subRow);
    // 去掉边距。
    subLayout->setContentsMargins(0, 0, 0, 0);
    // 字幕输入框。
    m_subtitleEdit = new QLineEdit;
    // 占位提示。
    m_subtitleEdit->setPlaceholderText(QStringLiteral("Subtitle file (burnt in)"));
    // 浏览按钮。
    QPushButton* subBtn = new QPushButton(QStringLiteral("..."));
    // 固定宽度。
    subBtn->setFixedWidth(32);
    // 加入行布局。
    subLayout->addWidget(m_subtitleEdit, 1);
    // 追加按钮。
    subLayout->addWidget(subBtn);
    // 加入表单。
    wmForm->addRow(QStringLiteral("Subtitle"), subRow);
    // 加入参数区。
    paramLayout->addWidget(m_watermarkGroup);

    // ---- 音频分组 ----
    m_audioGroup = new QGroupBox(QStringLiteral("Audio"));
    // 音频区表单布局。
    QFormLayout* audForm = new QFormLayout(m_audioGroup);
    // 音量输入框。
    m_volumeEdit = new QLineEdit;
    // 占位提示。
    m_volumeEdit->setPlaceholderText(QStringLiteral("e.g. 1.5 or -6dB"));
    // 加入表单。
    audForm->addRow(QStringLiteral("Volume"), m_volumeEdit);
    // 采样率数字框（0 表示保持原样）。
    m_sampleRateSpin = new QSpinBox;
    // 范围 0 ~ 192000。
    m_sampleRateSpin->setRange(0, 192000);
    // 步长 1000。
    m_sampleRateSpin->setSingleStep(1000);
    // 特殊值文本。
    m_sampleRateSpin->setSpecialValueText(QStringLiteral("keep"));
    // 加入表单。
    audForm->addRow(QStringLiteral("Sample rate (Hz)"), m_sampleRateSpin);
    // 声道数数字框。
    m_channelsSpin = new QSpinBox;
    // 范围 0 ~ 8。
    m_channelsSpin->setRange(0, 8);
    // 特殊值文本。
    m_channelsSpin->setSpecialValueText(QStringLiteral("keep"));
    // 加入表单。
    audForm->addRow(QStringLiteral("Channels"), m_channelsSpin);
    // 加入参数区。
    paramLayout->addWidget(m_audioGroup);

    // ---- 时间与变速分组 ----
    QGroupBox* timeGroup = new QGroupBox(QStringLiteral("Time & speed"));
    // 时间区表单布局。
    QFormLayout* timeForm = new QFormLayout(timeGroup);
    // 起始时间输入框。
    m_startEdit = new QLineEdit;
    // 占位提示。
    m_startEdit->setPlaceholderText(QStringLiteral("e.g. 00:00:10 or 10.5"));
    // 加入表单。
    timeForm->addRow(QStringLiteral("Start"), m_startEdit);
    // 结束时间输入框。
    m_endEdit = new QLineEdit;
    // 占位提示。
    m_endEdit->setPlaceholderText(QStringLiteral("absolute end time"));
    // 加入表单。
    timeForm->addRow(QStringLiteral("End"), m_endEdit);
    // 时长输入框。
    m_durationEdit = new QLineEdit;
    // 占位提示（优先于 End）。
    m_durationEdit->setPlaceholderText(QStringLiteral("duration (takes priority)"));
    // 加入表单。
    timeForm->addRow(QStringLiteral("Duration"), m_durationEdit);
    // 速度输入框。
    m_speedSpin = new QDoubleSpinBox;
    // 范围 0.25 ~ 4.00。
    m_speedSpin->setRange(0.25, 4.00);
    // 步长 0.25。
    m_speedSpin->setSingleStep(0.25);
    // 默认 1.0（原速）。
    m_speedSpin->setValue(1.0);
    // 加入表单。
    timeForm->addRow(QStringLiteral("Speed"), m_speedSpin);
    // 加入参数区。
    paramLayout->addWidget(timeGroup);

    // ---- 元数据分组 ----
    QGroupBox* metaGroup = new QGroupBox(QStringLiteral("Metadata"));
    // 元数据区表单布局。
    QFormLayout* metaForm = new QFormLayout(metaGroup);
    // 标题。
    m_metaTitleEdit = new QLineEdit;
    // 加入表单。
    metaForm->addRow(QStringLiteral("Title"), m_metaTitleEdit);
    // 艺术家。
    m_metaArtistEdit = new QLineEdit;
    // 加入表单。
    metaForm->addRow(QStringLiteral("Artist"), m_metaArtistEdit);
    // 专辑。
    m_metaAlbumEdit = new QLineEdit;
    // 加入表单。
    metaForm->addRow(QStringLiteral("Album"), m_metaAlbumEdit);
    // 备注。
    m_metaCommentEdit = new QLineEdit;
    // 加入表单。
    metaForm->addRow(QStringLiteral("Comment"), m_metaCommentEdit);
    // 加入参数区。
    paramLayout->addWidget(metaGroup);

    // ---- 高级分组 ----
    QGroupBox* advGroup = new QGroupBox(QStringLiteral("Advanced"));
    // 高级区表单布局。
    QFormLayout* advForm = new QFormLayout(advGroup);
    // 额外参数输入框（原样追加到命令末尾，允许覆盖前面的默认值）。
    m_extraArgsEdit = new QLineEdit;
    // 占位提示（用空格分隔多个参数）。
    m_extraArgsEdit->setPlaceholderText(QStringLiteral("extra ffmpeg args, space separated"));
    // 加入表单。
    advForm->addRow(QStringLiteral("Extra args"), m_extraArgsEdit);
    // 加入参数区。
    paramLayout->addWidget(advGroup);
    // 参数区末尾留白，避免最后一项贴着滚动条。
    paramLayout->addStretch(1);

    // 滚动区域包裹参数区。
    QScrollArea* scroll = new QScrollArea;
    // 允许内部控件随宽度伸缩。
    scroll->setWidgetResizable(true);
    // 设置内部控件。
    scroll->setWidget(paramHost);
    // 滚动区域固定宽度（让右侧操作区获得更多空间）。
    scroll->setMinimumWidth(380);
    // 滚动区域最大宽度，避免参数区过度拉伸。
    scroll->setMaximumWidth(460);

    // ---- 右侧操作区 ----
    // 操作区容器。
    QWidget* actionHost = new QWidget;
    // 操作区竖向布局。
    QVBoxLayout* actionLayout = new QVBoxLayout(actionHost);
    // 边距。
    actionLayout->setContentsMargins(6, 6, 6, 6);
    // 间距。
    actionLayout->setSpacing(8);

    // 文件分组。
    QGroupBox* fileGroup = new QGroupBox(QStringLiteral("Input files"));
    // 文件区竖向布局。
    QVBoxLayout* fileLayout = new QVBoxLayout(fileGroup);
    // 文件列表（可多选，支持拖放操作在代码里另行处理）。
    m_fileList = new QListWidget;
    // 允许扩展选择（按住 Ctrl / Shift 多选）。
    m_fileList->setSelectionMode(QAbstractItemView::ExtendedSelection);
    // 加入布局。
    fileLayout->addWidget(m_fileList, 1);
    // 文件操作按钮行。
    QWidget* fileBtnRow = new QWidget;
    // 按钮行布局。
    QHBoxLayout* fileBtnLayout = new QHBoxLayout(fileBtnRow);
    // 去掉边距。
    fileBtnLayout->setContentsMargins(0, 0, 0, 0);
    // 添加按钮。
    QPushButton* addBtn = new QPushButton(QStringLiteral("Add files"));
    // 移除按钮。
    QPushButton* removeBtn = new QPushButton(QStringLiteral("Remove selected"));
    // 清空按钮。
    QPushButton* clearBtn = new QPushButton(QStringLiteral("Clear list"));
    // 逐个加入。
    fileBtnLayout->addWidget(addBtn);
    // 追加移除按钮。
    fileBtnLayout->addWidget(removeBtn);
    // 追加清空按钮。
    fileBtnLayout->addWidget(clearBtn);
    // 余量留白。
    fileBtnLayout->addStretch(1);
    // 加入文件区。
    fileLayout->addWidget(fileBtnRow);
    // 加入操作区。
    actionLayout->addWidget(fileGroup, 3);

    // 进度与状态分组。
    QGroupBox* progressGroup = new QGroupBox(QStringLiteral("Progress"));
    // 进度区竖向布局。
    QVBoxLayout* progressLayout = new QVBoxLayout(progressGroup);
    // 进度条。
    m_progress = new QProgressBar;
    // 范围 0 ~ 100。
    m_progress->setRange(0, 100);
    // 初始值 0。
    m_progress->setValue(0);
    // 加入布局。
    progressLayout->addWidget(m_progress);
    // 状态标签。
    m_statusLabel = new QLabel;
    // 允许自动换行，长文本不会被截断。
    m_statusLabel->setWordWrap(true);
    // 加入布局。
    progressLayout->addWidget(m_statusLabel);
    // 加入操作区。
    actionLayout->addWidget(progressGroup);

    // 日志分组。
    QGroupBox* logGroup = new QGroupBox(QStringLiteral("Log"));
    // 日志区竖向布局。
    QVBoxLayout* logLayout = new QVBoxLayout(logGroup);
    // 日志框（只读）。
    m_log = new QPlainTextEdit;
    // 设为只读。
    m_log->setReadOnly(true);
    // 关闭自动换行，保持命令行文本对齐。
    m_log->setLineWrapMode(QPlainTextEdit::NoWrap);
    // 加入布局。
    logLayout->addWidget(m_log);
    // 加入操作区。
    actionLayout->addWidget(logGroup, 4);

    // 动作按钮行。
    QWidget* actionBtnRow = new QWidget;
    // 动作行布局。
    QHBoxLayout* actionBtnLayout = new QHBoxLayout(actionBtnRow);
    // 去掉边距。
    actionBtnLayout->setContentsMargins(0, 0, 0, 0);
    // 开始按钮（主按钮）。
    m_startButton = new QPushButton(QStringLiteral("Start conversion"));
    // 标记为主按钮，样式表据此使用强调色。
    m_startButton->setProperty("primary", true);
    // 抽帧按钮。
    m_frameButton = new QPushButton(QStringLiteral("Extract 5 frames (PNG)"));
    // 打开输出目录按钮。
    QPushButton* openOutBtn = new QPushButton(QStringLiteral("Open output folder"));
    // 逐个加入。
    actionBtnLayout->addWidget(m_startButton);
    // 追加抽帧按钮。
    actionBtnLayout->addWidget(m_frameButton);
    // 追加打开目录按钮。
    actionBtnLayout->addWidget(openOutBtn);
    // 余量留白。
    actionBtnLayout->addStretch(1);
    // 加入操作区。
    actionLayout->addWidget(actionBtnRow);

    // ---- 组合左右两栏 ----
    // 整体横向布局。
    QHBoxLayout* rootLayout = new QHBoxLayout(this);
    // 边距。
    rootLayout->setContentsMargins(8, 8, 8, 8);
    // 加入参数区。
    rootLayout->addWidget(scroll, 0);
    // 加入操作区（占据剩余空间）。
    rootLayout->addWidget(actionHost, 1);

    // ---- 信号连接 ----
    // 添加文件按钮 → 添加文件。
    connect(addBtn, &QPushButton::clicked, this, &ConvertPanel::onAddFiles);
    // 移除按钮 → 移除选中文件。
    connect(removeBtn, &QPushButton::clicked, this, &ConvertPanel::onRemoveFiles);
    // 清空按钮 → 清空列表。
    connect(clearBtn, &QPushButton::clicked, this, &ConvertPanel::onClearFiles);
    // 输出目录浏览按钮。
    connect(dirBtn, &QPushButton::clicked, this, &ConvertPanel::onBrowseOutputDir);
    // 水印浏览按钮。
    connect(wmBtn, &QPushButton::clicked, this, &ConvertPanel::onBrowseWatermark);
    // 字幕浏览按钮。
    connect(subBtn, &QPushButton::clicked, this, &ConvertPanel::onBrowseSubtitle);
    // 预设变化 → 套用预设（Qt6 中 currentIndexChanged 只有 int 一种重载，可直接取地址）。
    connect(m_presetCombo, &QComboBox::currentIndexChanged, this, &ConvertPanel::onPresetChanged);
    // 开始按钮。
    connect(m_startButton, &QPushButton::clicked, this, &ConvertPanel::onStart);
    // 抽帧按钮。
    connect(m_frameButton, &QPushButton::clicked, this, &ConvertPanel::onExtractFrames);
    // 打开输出目录按钮。
    connect(openOutBtn, &QPushButton::clicked, this, &ConvertPanel::onOpenOutputDir);
    // 输出目录输入框被手动修改时打上“已自定义”标记，避免被 Settings 的默认值覆盖。
    connect(m_outputDirEdit, &QLineEdit::textEdited, this, [this](const QString&)
    {
        // 记录用户已自定义。
        m_outputDirCustomized = true;
    });
    // 抽帧进程对象（父对象为本面板，随面板销毁）。
    m_frameProcess = new QProcess(this);
    // Windows 下不弹出控制台窗口。
    configureNoWindow(*m_frameProcess);
    // 合并两个通道，便于一次性读取全部输出。
    m_frameProcess->setProcessChannelMode(QProcess::MergedChannels);
    // 进程结束信号。
    connect(m_frameProcess, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished),
            this, &ConvertPanel::onFrameProcessFinished);
    // ---- 硬件加速下拉框填充 ----
    // 软件编码（默认、最稳）。
    m_hwCombo->addItem(QStringLiteral("Software only (stable)"), QStringLiteral("none"));
    // 自动挑选可用硬件。
    m_hwCombo->addItem(QStringLiteral("Auto (use hardware if available)"), QStringLiteral("auto"));
    // 逐个加入内核确实包含的硬件编码器。
    for (const HwEncoderInfo& info : HwAccel::availableDetail())
    {
        // 显示名 = 厂商说明；数据 = 编码器名 + 厂商。
        m_hwCombo->addItem(info.label, info.name);
        // 把厂商标识写入第二数据角色。
        m_hwCombo->setItemData(m_hwCombo->count() - 1, info.vendor, Qt::UserRole + 1);
    }
    // 默认选中软件编码（由 Settings 页的策略决定，这里先给安全默认值）。
    m_hwCombo->setCurrentIndex(0);
}

// 填充目标格式下拉框。
void ConvertPanel::fillFormatCombo()
{
    // 按类型给出候选扩展名列表（顺序体现常用程度）。
    QStringList candidates;
    // 视频。
    if (m_kind == QStringLiteral("video"))
    {
        // 常见视频容器。
        candidates << QStringLiteral("mp4") << QStringLiteral("mkv") << QStringLiteral("mov")
            << QStringLiteral("avi") << QStringLiteral("flv") << QStringLiteral("webm")
            << QStringLiteral("ts") << QStringLiteral("mpg") << QStringLiteral("wmv")
            << QStringLiteral("3gp") << QStringLiteral("m4v") << QStringLiteral("gif");
    }
    else if (m_kind == QStringLiteral("audio"))
    {
        // 常见音频容器。
        candidates << QStringLiteral("mp3") << QStringLiteral("m4a") << QStringLiteral("aac")
            << QStringLiteral("wav") << QStringLiteral("flac") << QStringLiteral("ogg")
            << QStringLiteral("opus") << QStringLiteral("ac3") << QStringLiteral("wma")
            << QStringLiteral("amr") << QStringLiteral("aiff");
    }
    else
    {
        // 图片格式（全部由内核的图片编码器承载）。
        candidates << QStringLiteral("jpg") << QStringLiteral("png") << QStringLiteral("webp")
            << QStringLiteral("gif") << QStringLiteral("bmp") << QStringLiteral("tiff")
            << QStringLiteral("avif") << QStringLiteral("ico");
    }
    // 清空下拉框，保证可以重复调用。
    m_formatCombo->clear();
    // 逐个检查内核支持情况并加入。
    for (const QString& ext : candidates)
    {
        // 只有内核确认支持（或探测失败时的宽松策略）才列出。
        if (Capabilities::instance().supportsFormat(ext))
        {
            // 显示名用大写扩展名，数据为小写扩展名。
            m_formatCombo->addItem(ext.toUpper(), ext);
        }
    }
    // 一个都没通过检查（例如内核缺失）时给出兜底项，避免下拉框为空导致无法操作。
    if (m_formatCombo->count() == 0)
    {
        // 追加兜底项。
        m_formatCombo->addItem(QStringLiteral("MP4"), QStringLiteral("mp4"));
    }
    // 默认选中第一个（通常是 mp4 / mp3 / jpg）。
    m_formatCombo->setCurrentIndex(0);
}

// 填充编码器下拉框。
void ConvertPanel::fillEncoderCombo()
{
    // 视频/图片面板的视频编码器候选（按“通用性优先”排序）。
    QStringList videoCandidates;
    // 视频面板用通用视频编码器白名单。
    if (m_kind == QStringLiteral("video"))
    {
        // 通用性最好的软件编码器排在最前。
        videoCandidates << QStringLiteral("libx264") << QStringLiteral("libx265")
            << QStringLiteral("libvpx-vp9") << QStringLiteral("libvpx")
            << QStringLiteral("libaom-av1") << QStringLiteral("libsvtav1")
            << QStringLiteral("mpeg4") << QStringLiteral("mpeg2video")
            << QStringLiteral("mpeg1video") << QStringLiteral("prores_ks")
            << QStringLiteral("dnxhd") << QStringLiteral("wmv2")
            << QStringLiteral("h264_nvenc") << QStringLiteral("hevc_nvenc")
            << QStringLiteral("h264_qsv") << QStringLiteral("hevc_qsv")
            << QStringLiteral("h264_amf") << QStringLiteral("hevc_amf");
    }
    else
    {
        // 图片面板用图片编码器白名单。
        videoCandidates << QStringLiteral("mjpeg") << QStringLiteral("png")
            << QStringLiteral("libwebp") << QStringLiteral("gif")
            << QStringLiteral("tiff") << QStringLiteral("bmp")
            << QStringLiteral("libaom-av1");
    }
    // 图片面板的编码器下拉框也要填充（用 m_encoderCombo）。
    if (m_encoderCombo != nullptr && m_kind != QStringLiteral("audio"))
    {
        // 清空。
        m_encoderCombo->clear();
        // 首项为“自动”：由命令构建器按目标容器选择默认编码器。
        m_encoderCombo->addItem(QStringLiteral("Auto (container default)"), QString());
        // 逐个检查内核支持情况。
        for (const QString& enc : videoCandidates)
        {
            // 内核包含才加入。
            if (Capabilities::instance().supportsEncoder(enc))
            {
                // 加入下拉框。
                m_encoderCombo->addItem(enc, enc);
            }
        }
        // 视频面板额外提供“直接复制视频流”的选项：仅换容器、不重编码，速度极快且无画质损失。
        // 说明：命令构建器会在“同时启用了滤镜/水印/字幕”时自动把它降级为重新编码，
        //      因此该选项在任何组合下都不会导致任务失败。
        if (m_kind == QStringLiteral("video"))
        {
            // 追加复制项。
            m_encoderCombo->addItem(QStringLiteral("Copy stream (remux only)"), QStringLiteral("copy"));
        }
        // 默认选中“自动”。
        m_encoderCombo->setCurrentIndex(0);
    }

    // 音频编码器候选。
    QStringList audioCandidates;
    // 音频面板与视频面板都需要音频编码器。
    if (m_kind == QStringLiteral("audio"))
    {
        // 音频专用白名单。
        audioCandidates << QStringLiteral("libmp3lame") << QStringLiteral("aac")
            << QStringLiteral("flac") << QStringLiteral("pcm_s16le")
            << QStringLiteral("pcm_s16be") << QStringLiteral("libvorbis")
            << QStringLiteral("libopus") << QStringLiteral("ac3")
            << QStringLiteral("wmav2") << QStringLiteral("libopencore_amrnb");
    }
    else if (m_kind == QStringLiteral("video"))
    {
        // 视频面板的音频轨道编码器白名单。
        audioCandidates << QStringLiteral("aac") << QStringLiteral("libmp3lame")
            << QStringLiteral("libopus") << QStringLiteral("libvorbis")
            << QStringLiteral("ac3") << QStringLiteral("pcm_s16le");
    }
    // 非图片面板才填充音频编码器下拉框。
    if (m_audioEncoderCombo != nullptr && m_kind != QStringLiteral("image"))
    {
        // 清空。
        m_audioEncoderCombo->clear();
        // 首项为“自动”。
        m_audioEncoderCombo->addItem(QStringLiteral("Auto (container default)"), QString());
        // 逐个检查内核支持情况。
        for (const QString& enc : audioCandidates)
        {
            // 内核包含才加入。
            if (Capabilities::instance().supportsEncoder(enc))
            {
                // 加入。
                m_audioEncoderCombo->addItem(enc, enc);
            }
        }
        // 允许“直接复制音频流”这一特殊选项（音频面板才有意义）。
        if (m_kind == QStringLiteral("audio"))
        {
            // 追加复制项。
            m_audioEncoderCombo->addItem(QStringLiteral("Copy stream (no re-encode)"), QStringLiteral("copy"));
        }
        // 默认选中“自动”。
        m_audioEncoderCombo->setCurrentIndex(0);
    }
}

// 添加文件。
void ConvertPanel::onAddFiles()
{
    // 按类型给出文件过滤器（英文）。
    QString filter;
    // 视频。
    if (m_kind == QStringLiteral("video"))
    {
        // 常见视频扩展名。
        filter = QStringLiteral(
            "Media files (*.mp4 *.mkv *.mov *.avi *.flv *.webm *.ts *.mpg *.wmv *.m4v *.3gp *.gif);;All files (*)");
    }
    else if (m_kind == QStringLiteral("audio"))
    {
        // 常见音频扩展名。
        filter = QStringLiteral(
            "Audio files (*.mp3 *.m4a *.aac *.wav *.flac *.ogg *.opus *.ac3 *.wma *.aiff *.amr);;All files (*)");
    }
    else
    {
        // 常见图片扩展名。
        filter = QStringLiteral(
            "Image files (*.jpg *.jpeg *.png *.webp *.gif *.bmp *.tif *.tiff *.avif *.ico);;All files (*)");
    }
    // 打开多选文件对话框。
    const QStringList files = QFileDialog::getOpenFileNames(this, QStringLiteral("Select input files"), QString(),
                                                            filter);
    // 逐个加入列表（自动去重）。
    for (const QString& f : files)
    {
        // 规范为绝对路径。
        const QString abs = QFileInfo(f).absoluteFilePath();
        // 检查是否已在列表中。
        bool exists = false;
        // 遍历现有项。
        for (int i = 0; i < m_fileList->count(); ++i)
        {
            // 比对存储的完整路径。
            if (m_fileList->item(i)->data(Qt::UserRole).toString() == abs)
            {
                // 标记已存在。
                exists = true;
                // 结束循环。
                break;
            }
        }
        // 已存在则跳过。
        if (exists) { continue; }
        // 新建列表项（显示文件名，避免长路径把列表撑宽）。
        QListWidgetItem* item = new QListWidgetItem(QFileInfo(abs).fileName());
        // 把完整路径存入数据角色。
        item->setData(Qt::UserRole, abs);
        // 悬停时显示完整路径。
        item->setToolTip(abs);
        // 加入列表。
        m_fileList->addItem(item);
    }
    // 更新状态。
    setStatus(QStringLiteral("%1 file(s) in the list.").arg(m_fileList->count()));
    // 向上层报告。
    emit statusMessage(QStringLiteral("%1: %2 file(s) queued for input.").arg(m_kind).arg(m_fileList->count()));
}

// 移除选中文件。
void ConvertPanel::onRemoveFiles()
{
    // 取出选中项。
    const QList<QListWidgetItem*> selected = m_fileList->selectedItems();
    // 逐个移除（必须从后往前删以避免索引错位；这里用 delete 后 Qt 会自动从列表移除）。
    for (QListWidgetItem* item : selected)
    {
        // 取出行号。
        const int row = m_fileList->row(item);
        // 取出一项并从列表移除。
        delete m_fileList->takeItem(row);
    }
    // 更新状态。
    setStatus(QStringLiteral("%1 file(s) remaining.").arg(m_fileList->count()));
}

// 清空列表。
void ConvertPanel::onClearFiles()
{
    // 清空全部项。
    m_fileList->clear();
    // 更新状态。
    setStatus(QStringLiteral("File list cleared."));
}

// 选择输出目录。
void ConvertPanel::onBrowseOutputDir()
{
    // 打开目录选择对话框，起始目录为当前输入框内容。
    const QString dir = QFileDialog::getExistingDirectory(this, QStringLiteral("Select output folder"),
                                                          m_outputDirEdit->text());
    // 用户取消时返回空字符串。
    if (dir.isEmpty()) { return; }
    // 回填输入框。
    m_outputDirEdit->setText(QDir::toNativeSeparators(dir));
    // 标记为已自定义。
    m_outputDirCustomized = true;
}

// 选择水印图片。
void ConvertPanel::onBrowseWatermark()
{
    // 图片过滤器。
    const QString filter = QStringLiteral("Image files (*.png *.jpg *.jpeg *.webp *.bmp *.gif);;All files (*)");
    // 打开文件对话框。
    const QString file =
        QFileDialog::getOpenFileName(this, QStringLiteral("Select watermark image"), QString(), filter);
    // 取消时返回。
    if (file.isEmpty()) { return; }
    // 回填输入框。
    m_watermarkEdit->setText(QDir::toNativeSeparators(file));
}

// 选择字幕文件。
void ConvertPanel::onBrowseSubtitle()
{
    // 字幕过滤器。
    const QString filter = QStringLiteral("Subtitle files (*.srt *.ass *.ssa *.vtt);;All files (*)");
    // 打开文件对话框。
    const QString file = QFileDialog::getOpenFileName(this, QStringLiteral("Select subtitle file"), QString(), filter);
    // 取消时返回。
    if (file.isEmpty()) { return; }
    // 回填输入框。
    m_subtitleEdit->setText(QDir::toNativeSeparators(file));
}

// 套用预设。
void ConvertPanel::onPresetChanged(int index)
{
    // 取出该条目的 key。
    const QString key = m_presetCombo->itemData(index).toString();
    // key 为空表示“不使用预设”，无需处理。
    if (key.isEmpty()) { return; }
    // 查出预设定义。
    const Preset preset = presetByKey(key);
    // 未找到（理论上不可达）时直接返回。
    if (preset.key.isEmpty()) { return; }
    // 回填到界面。
    applyPresetToUi(preset);
    // 记录日志与状态。
    appendLog(QStringLiteral("> Preset applied: %1 (%2)").arg(preset.name, preset.note));
    // 更新状态。
    setStatus(QStringLiteral("Preset applied: %1").arg(preset.name));
}

// 回填预设到界面。
void ConvertPanel::applyPresetToUi(const Preset& preset)
{
    // 目标格式：按数据匹配（不区分大小写）。
    for (int i = 0; i < m_formatCombo->count(); ++i)
    {
        // 命中即选中。
        if (m_formatCombo->itemData(i).toString() == preset.container)
        {
            // 选中该项。
            m_formatCombo->setCurrentIndex(i);
            // 结束。
            break;
        }
    }
    // 质量档位。
    for (int i = 0; i < m_qualityCombo->count(); ++i)
    {
        // 命中即选中。
        if (m_qualityCombo->itemData(i).toString() == preset.quality)
        {
            // 选中。
            m_qualityCombo->setCurrentIndex(i);
            // 结束。
            break;
        }
    }
    // 视频编码器：仅在没有对应下拉框或值为空时跳过。
    if (m_encoderCombo != nullptr && m_kind != QStringLiteral("audio") && !preset.videoCodec.isEmpty())
    {
        // 先恢复为“自动”。
        m_encoderCombo->setCurrentIndex(0);
        // 尝试匹配预设编码器。
        for (int i = 0; i < m_encoderCombo->count(); ++i)
        {
            // 命中即选中。
            if (m_encoderCombo->itemData(i).toString() == preset.videoCodec)
            {
                // 选中。
                m_encoderCombo->setCurrentIndex(i);
                // 结束。
                break;
            }
        }
    }
    // 音频编码器。
    if (m_audioEncoderCombo != nullptr && m_kind != QStringLiteral("image") && !preset.audioCodec.isEmpty())
    {
        // 恢复为自动。
        m_audioEncoderCombo->setCurrentIndex(0);
        // 尝试匹配。
        for (int i = 0; i < m_audioEncoderCombo->count(); ++i)
        {
            // 命中即选中。
            if (m_audioEncoderCombo->itemData(i).toString() == preset.audioCodec)
            {
                // 选中。
                m_audioEncoderCombo->setCurrentIndex(i);
                // 结束。
                break;
            }
        }
    }
    // 码率与 CRF。
    m_bitrateEdit->setText(preset.vBitrate);
    // CRF 回填。
    m_crfEdit->setText(preset.crf);
    // 音频码率回填。
    m_audioBitrateEdit->setText(preset.aBitrate);
    // 像素格式回填（预设未指定时保留界面当前值）。
    if (!preset.pixelFormat.isEmpty())
    {
        // 回填。
        m_pixFmtEdit->setText(preset.pixelFormat);
    }
    // 附加参数（例如 JPG 的 -q:v 2、ICO 的 -vf scale=256:256）以“空格分隔”的形式放进额外参数框。
    if (!preset.extra.isEmpty())
    {
        // 直接拼接（这些参数由预设作者控制，不含需要转义的引号）。
        m_extraArgsEdit->setText(preset.extra.join(QLatin1Char(' ')));
    }
}

// 开始转码。
void ConvertPanel::onStart()
{
    // 若用户选中了部分文件，则只转换这些；否则转换列表中的全部文件（批量是最常见诉求）。
    QStringList targets;
    // 取出选中项。
    const QList<QListWidgetItem*> selected = m_fileList->selectedItems();
    // 有选中项时优先使用。
    if (!selected.isEmpty())
    {
        // 收集选中项的完整路径。
        for (QListWidgetItem* item : selected)
        {
            // 追加路径。
            targets << item->data(Qt::UserRole).toString();
        }
    }
    else
    {
        // 未选中时使用全部文件。
        for (int i = 0; i < m_fileList->count(); ++i)
        {
            // 追加路径。
            targets << m_fileList->item(i)->data(Qt::UserRole).toString();
        }
    }
    // 没有任何输入时给出提示（英文）。
    if (targets.isEmpty())
    {
        // 弹出提示框。
        QMessageBox::information(this, QStringLiteral("No input"),
                                 QStringLiteral("Add at least one input file first."));
        // 返回。
        return;
    }
    // 内核必须就绪，否则任何操作都无意义。
    if (!FFmpegLocator::instance().isReady())
    {
        // 弹出提示框（给出可执行的下一步）。
        QMessageBox::warning(this, QStringLiteral("FFmpeg not found"),
                             QStringLiteral(
                                 "FFmpeg/FFprobe could not be located. Put them in the 'resources/ffmpeg' folder next to the executable, or set a custom path on the Settings tab."));
        // 返回。
        return;
    }
    // 收集一次公共参数（每个文件只覆盖 source 字段）。
    const TranscodeSettings base = collectSettings();
    // 记录本次真正提交的任务数。
    int submitted = 0;
    // 记录因去重被跳过的任务数。
    int skippedCount = 0;
    // 逐个提交。
    for (const QString& src : targets)
    {
        // 复制公共参数。
        TranscodeSettings st = base;
        // 覆盖源文件。
        st.source = src;
        // 是否因去重被跳过（由 addTask 回填）。
        bool skipped = false;
        // 提交到队列（开启去重）；被跳过时仍会生成一条 Skipped 记录，便于用户在队列里看到。
        const QString tid = m_queue->addTask(st, true, &skipped);
        // 极端情况下没有拿到任务 ID：记录并跳过。
        if (tid.isEmpty())
        {
            // 记录日志。
            appendLog(QStringLiteral("> Could not queue: %1").arg(src));
            // 继续下一个。
            continue;
        }
        // 记录任务归属。
        m_myTasks.insert(tid);
        // 被去重跳过时只计数并记日志（不启动转码）。
        if (skipped)
        {
            // 计数。
            ++skippedCount;
            // 记录日志。
            appendLog(QStringLiteral("> Skipped duplicate: %1").arg(src));
            // 继续下一个。
            continue;
        }
        // 真正提交的任务计数。
        ++submitted;
    }
    // 全部被跳过时给出明确提示（避免用户以为程序没反应）。
    if (submitted == 0 && skippedCount > 0)
    {
        // 更新状态。
        setStatus(QStringLiteral("All selected files were skipped as duplicates."));
        // 提示信息（英文）。
        QMessageBox::information(this, QStringLiteral("Nothing to do"),
                                 QStringLiteral("All selected files are already queued with the same target format."));
        // 返回。
        return;
    }
    // 进度条归零。
    m_progress->setValue(0);
    // 更新状态。
    setStatus(QStringLiteral("Queued %1 task(s)%2.").arg(submitted)
                                                    .arg(skippedCount > 0
                                                             ? QStringLiteral(" (%1 skipped as duplicates)").arg(
                                                                 skippedCount)
                                                             : QString()));
    // 记录日志。
    appendLog(QStringLiteral("> Queued %1 task(s) to the shared queue.").arg(submitted));
    // 启动队列调度。
    m_queue->start();
    // 向上层报告（便于主窗口切到 Queue 页观察）。
    emit statusMessage(QStringLiteral("Queued %1 task(s) from the %2 tab.").arg(submitted).arg(m_kind));
}

// 打开输出目录。
void ConvertPanel::onOpenOutputDir()
{
    // 优先使用界面上的输出目录。
    QString dir = m_outputDirEdit->text().trimmed();
    // 未指定时退回默认输出目录。
    if (dir.isEmpty()) { dir = m_defaultOutDir; }
    // 仍未指定时退回第一个源文件所在目录。
    if (dir.isEmpty() && m_fileList->count() > 0)
    {
        // 取第一项的目录。
        dir = QFileInfo(m_fileList->item(0)->data(Qt::UserRole).toString()).absolutePath();
    }
    // 目录为空则无法打开。
    if (dir.isEmpty())
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("No folder"),
                                 QStringLiteral("No output folder is available yet."));
        // 返回。
        return;
    }
    // 用系统文件管理器打开该目录。
    QDesktopServices::openUrl(QUrl::fromLocalFile(dir));
}

// 视频抽帧。
void ConvertPanel::onExtractFrames()
{
    // 仅在视频面板可用（其它面板按钮已隐藏，这里再兜底判断一次）。
    if (m_kind != QStringLiteral("video")) { return; }
    // 取第一个文件作为抽帧源。
    QString src;
    // 有选中项则优先用选中项的第一个。
    if (!m_fileList->selectedItems().isEmpty())
    {
        // 取选中项路径。
        src = m_fileList->selectedItems().first()->data(Qt::UserRole).toString();
    }
    else if (m_fileList->count() > 0)
    {
        // 否则取列表第一项。
        src = m_fileList->item(0)->data(Qt::UserRole).toString();
    }
    // 没有可用源文件则提示。
    if (src.isEmpty())
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("No input"),
                                 QStringLiteral("Add a video file to extract frames from."));
        // 返回。
        return;
    }
    // 抽帧进程正在运行时不允许重复触发。
    if (m_frameProcess->state() != QProcess::NotRunning)
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("Busy"), QStringLiteral("Frame extraction is already running."));
        // 返回。
        return;
    }
    // 探测源时长（这一步会短暂阻塞界面，耗时通常在 1 秒以内，可接受）。
    const MediaInfo info = probeMedia(src);
    // 探测失败且文件不存在时提示。
    if (!info.exists)
    {
        // 提示（英文）。
        QMessageBox::warning(this, QStringLiteral("Probe failed"),
                             QStringLiteral("The selected file could not be read."));
        // 返回。
        return;
    }
    // 输出目录：用户指定目录下的 frames 子目录，未指定时用源目录下的 frames。
    const QString baseDir = m_outputDirEdit->text().trimmed().isEmpty()
                                ? QFileInfo(src).absolutePath()
                                : m_outputDirEdit->text().trimmed();
    // 拼接 frames 子目录。
    const QString outDir = QDir(baseDir).filePath(QStringLiteral("frames"));
    // 构建抽帧计划（默认抽 5 帧）。
    const FrameExtractionPlan plan = buildFrameExtractionPlan(src, info.duration, 5, outDir,
                                                              QFileInfo(src).completeBaseName());
    // 计划为空说明内核缺失。
    if (plan.commands.isEmpty())
    {
        // 提示（英文）。
        QMessageBox::warning(this, QStringLiteral("FFmpeg not found"),
                             QStringLiteral("FFmpeg is required to extract frames."));
        // 返回。
        return;
    }
    // 记录待执行的命令。
    m_frameCommands = plan.commands;
    // 记录预期的输出文件。
    m_frameOutputs = plan.outPaths;
    // 从第一条开始。
    m_frameIndex = 0;
    // 更新状态与日志。
    setStatus(QStringLiteral("Extracting %1 frames to %2").arg(plan.commands.size()).arg(outDir));
    // 记录日志。
    appendLog(QStringLiteral("> Extracting %1 frames: %2").arg(plan.commands.size()).arg(outDir));
    // 记录第一条命令。
    appendLog(QStringLiteral("> %1").arg(describeCommand(plan.commands.first())));
    // 启动第一条命令。
    m_frameProcess->setProgram(plan.commands.first().first());
    // 设置参数。
    m_frameProcess->setArguments(plan.commands.first().mid(1));
    // 启动。
    m_frameProcess->start();
}

// 抽帧进程结束。
void ConvertPanel::onFrameProcessFinished(int exitCode, QProcess::ExitStatus status)
{
    // index 越界说明已经没有更多命令（防御性判断）。
    if (m_frameIndex >= m_frameCommands.size()) { return; }
    // 记录本条命令的结果。
    if (exitCode == 0 && status == QProcess::NormalExit)
    {
        // 成功：记录对应的输出文件路径。
        appendLog(QStringLiteral("> Frame written: %1").arg(m_frameOutputs.value(m_frameIndex)));
    }
    else
    {
        // 失败：把 ffmpeg 的输出摘要记录下来，便于排障。
        appendLog(QStringLiteral("> Frame %1 failed (exit code %2)").arg(m_frameIndex + 1).arg(exitCode));
        // 记录输出片段（截取末尾 500 字符）。
        const QString out = QString::fromUtf8(m_frameProcess->readAll()).trimmed();
        // 有内容才记录。
        if (!out.isEmpty())
        {
            // 截取末尾。
            appendLog(QStringLiteral("> %1").arg(out.size() > 500 ? out.right(500) : out));
        }
    }
    // 递增索引。
    ++m_frameIndex;
    // 还有命令则继续执行下一条。
    if (m_frameIndex < m_frameCommands.size())
    {
        // 取下一条命令。
        const QStringList cmd = m_frameCommands.at(m_frameIndex);
        // 记录命令文本。
        appendLog(QStringLiteral("> %1").arg(describeCommand(cmd)));
        // 设置程序。
        m_frameProcess->setProgram(cmd.first());
        // 设置参数。
        m_frameProcess->setArguments(cmd.mid(1));
        // 启动。
        m_frameProcess->start();
        // 返回。
        return;
    }
    // 全部执行完毕：统计实际生成的帧数。
    int created = 0;
    // 逐个检查产物是否存在且非空。
    for (const QString& p : m_frameOutputs)
    {
        // 存在且大小大于 0 才计数。
        if (QFileInfo(p).exists() && QFileInfo(p).size() > 0) { ++created; }
    }
    // 更新状态。
    setStatus(
        QStringLiteral("Frame extraction finished: %1 of %2 frames created.").arg(created).arg(m_frameOutputs.size()));
    // 记录日志。
    appendLog(
        QStringLiteral("> Frame extraction finished: %1 of %2 frames created.").arg(created).arg(
            m_frameOutputs.size()));
    // 向上层报告。
    emit statusMessage(QStringLiteral("Frame extraction finished (%1 frames).").arg(created));
}

// 进度信号处理。
void ConvertPanel::onTaskProgress(const QString& tid, double percent, double speed, double eta)
{
    // 只处理属于本面板的任务。
    if (!m_myTasks.contains(tid)) { return; }
    // 更新进度条（四舍五入到整数）。
    m_progress->setValue(int(percent + 0.5));
    // 取任务以显示文件名。
    const Task t = m_queue->taskById(tid);
    // 组装状态文本：文件名 + 百分比 + 速度 + 剩余时间。
    QString text = QStringLiteral("%1 - %2%").arg(t.fileName).arg(int(percent + 0.5));
    // 速度有效时追加。
    if (speed > 0.0)
    {
        // 追加速度。
        text += QStringLiteral(" @ %1x").arg(QString::number(speed, 'f', 2));
    }
    // 剩余时间有效时追加（格式化为 分:秒）。
    if (eta >= 0.0)
    {
        // 计算总秒数。
        const int sec = int(eta + 0.5);
        // 追加 ETA。
        text += QStringLiteral(" - ETA %1:%2")
                .arg(sec / 60, 2, 10, QLatin1Char('0'))
                .arg(sec % 60, 2, 10, QLatin1Char('0'));
    }
    // 更新状态标签。
    setStatus(text);
}

// 状态变化处理。
void ConvertPanel::onTaskChanged(const QString& tid)
{
    // 只处理属于本面板的任务。
    if (!m_myTasks.contains(tid)) { return; }
    // 取任务当前状态。
    const Task t = m_queue->taskById(tid);
    // 未找到（可能已被清理）时忽略。
    if (t.tid.isEmpty()) { return; }
    // 组装状态文本。
    QString text = QStringLiteral("%1 - %2").arg(t.fileName, taskStatusText(t.status));
    // 失败时附上原因与建议。
    if (t.status == TaskStatus::Failed)
    {
        // 追加错误标题。
        text += QStringLiteral(": %1").arg(t.errTitle);
        // 记录到日志（含建议与原始详情）。
        appendLog(QStringLiteral("[%1] FAILED: %2").arg(t.fileName, t.errTitle));
        // 建议非空时记录。
        if (!t.errAdvice.isEmpty())
        {
            // 记录建议。
            appendLog(QStringLiteral("[%1] Advice: %2").arg(t.fileName, t.errAdvice));
        }
        // 详情非空时记录。
        if (!t.errDetail.isEmpty())
        {
            // 记录原始错误尾部。
            appendLog(QStringLiteral("[%1] Detail: %2").arg(t.fileName, t.errDetail));
        }
    }
    else if (t.status == TaskStatus::Done)
    {
        // 完成时把产物信息写进日志。
        appendLog(QStringLiteral("[%1] DONE: %2 (%3 bytes, %4 s)")
                  .arg(t.fileName, t.outPath)
                  .arg(t.outSize)
                  .arg(QString::number(t.elapsed, 'f', 1)));
        // 完成后把进度条拉满。
        m_progress->setValue(100);
    }
    else if (t.status == TaskStatus::Canceled)
    {
        // 取消时记录日志。
        appendLog(QStringLiteral("[%1] CANCELED").arg(t.fileName));
    }
    // 更新状态标签。
    setStatus(text);
}

// 日志转发处理。
void ConvertPanel::onTaskLog(const QString& tid, const QString& text)
{
    // 只处理属于本面板的任务。
    if (!m_myTasks.contains(tid)) { return; }
    // 追加到日志框（线程安全：本槽由队列信号在主线程序列化调用）。
    appendLog(logPrefixFor(tid) + text);
}

// 生成日志前缀。
QString ConvertPanel::logPrefixFor(const QString& tid) const
{
    // 取出任务的文件名，便于在多任务交织时区分来源。
    const Task t = m_queue->taskById(tid);
    // 组装前缀（形如 "[movie.mp4] "）。
    return QStringLiteral("[%1] ").arg(t.fileName.isEmpty() ? tid : t.fileName);
}

// 追加日志。
void ConvertPanel::appendLog(const QString& text)
{
    // 直接追加一行（appendPlainText 会自动处理换行）。
    m_log->appendPlainText(text);
    // 把光标移到末尾，保证最新内容可见。
    m_log->moveCursor(QTextCursor::End);
}

// 更新状态标签。
void ConvertPanel::setStatus(const QString& text)
{
    // 更新标签文本。
    m_statusLabel->setText(text);
}

// 设置默认输出目录。
void ConvertPanel::setDefaultOutputDir(const QString& dir)
{
    // 记录默认值（供“打开输出目录”按钮兜底使用）。
    m_defaultOutDir = dir;
    // 用户没有手动改过输入框时才回填，避免覆盖用户的选择。
    if (!m_outputDirCustomized && m_outputDirEdit != nullptr)
    {
        // 回填为原生分隔符形式，便于阅读。
        m_outputDirEdit->setText(QDir::toNativeSeparators(dir));
    }
}

// 设置默认硬件加速策略。
void ConvertPanel::setDefaultHwAccel(const QString& policy)
{
    // 记录默认策略。
    m_defaultHwAccel = policy;
    // 硬件加速下拉框存在时同步选中项。
    if (m_hwCombo != nullptr)
    {
        // 遍历选项按数据匹配。
        for (int i = 0; i < m_hwCombo->count(); ++i)
        {
            // 命中即选中。
            if (m_hwCombo->itemData(i).toString() == policy)
            {
                // 选中。
                m_hwCombo->setCurrentIndex(i);
                // 结束。
                break;
            }
        }
    }
}

// 收集界面参数。
TranscodeSettings ConvertPanel::collectSettings() const
{
    // 结果对象。
    TranscodeSettings st;
    // 目标格式。
    st.targetFormat = m_formatCombo->currentData().toString();
    // 预设 key（为空表示自定义）。
    st.presetKey = m_presetCombo->currentData().toString();
    // 命名方式。
    st.namingMode = m_namingCombo->currentData().toString();
    // 命名模板。
    st.nameTemplate = m_templateEdit->text().trimmed().isEmpty()
                          ? QStringLiteral("{name}_{index}")
                          : m_templateEdit->text().trimmed();
    // 输出目录（原生分隔符转成 ffmpeg/Qt 都接受的统一形式）。
    st.outputDir = QDir::fromNativeSeparators(m_outputDirEdit->text().trimmed());
    // 质量档位。
    st.quality = m_qualityCombo->currentData().toString();
    // 视频编码器（音频面板没有该控件时保持为空 = 用容器默认）。
    st.videoCodec = (m_encoderCombo != nullptr && m_kind != QStringLiteral("audio"))
                        ? m_encoderCombo->currentData().toString()
                        : QString();
    // 音频编码器（图片面板没有该控件）。
    st.audioCodec = (m_audioEncoderCombo != nullptr && m_kind != QStringLiteral("image"))
                        ? m_audioEncoderCombo->currentData().toString()
                        : QString();
    // 视频码率。
    st.vBitrate = m_bitrateEdit->text().trimmed();
    // CRF。
    st.crf = m_crfEdit->text().trimmed();
    // 音频码率。
    st.aBitrate = m_audioBitrateEdit->text().trimmed();
    // 像素格式。
    st.pixelFormat = m_pixFmtEdit->text().trimmed();
    // 宽高。
    st.width = m_widthSpin->value();
    // 高度。
    st.height = m_heightSpin->value();
    // 缩放模式。
    st.scaleMode = m_scaleModeCombo->currentData().toString();
    // 帧率。
    st.fps = m_fpsEdit->text().trimmed();
    // 裁剪。
    st.crop = m_cropEdit->text().trimmed();
    // 旋转角度。
    st.rotate = m_rotateCombo->currentData().toInt();
    // 镜像方式。
    st.flip = m_flipCombo->currentData().toString();
    // 水印图片（原生分隔符转换）。
    st.watermarkPath = QDir::fromNativeSeparators(m_watermarkEdit->text().trimmed());
    // 水印文字。
    st.watermarkText = m_watermarkTextEdit->text().trimmed();
    // 水印位置。
    st.watermarkPos = m_watermarkPosCombo->currentData().toString();
    // 水印不透明度。
    st.watermarkOpacity = m_watermarkOpacitySpin->value();
    // 水印宽度比例。
    st.watermarkScale = m_watermarkScaleSpin->value();
    // 字幕路径。
    st.subtitlePath = QDir::fromNativeSeparators(m_subtitleEdit->text().trimmed());
    // 起始时间。
    st.start = m_startEdit->text().trimmed();
    // 结束时间。
    st.end = m_endEdit->text().trimmed();
    // 时长。
    st.duration = m_durationEdit->text().trimmed();
    // 速度。
    st.speed = m_speedSpin->value();
    // 音量。
    st.volume = m_volumeEdit->text().trimmed();
    // 采样率。
    st.sampleRate = m_sampleRateSpin->value();
    // 声道数。
    st.audioChannels = m_channelsSpin->value();
    // 元数据。
    st.metaTitle = m_metaTitleEdit->text().trimmed();
    // 艺术家。
    st.metaArtist = m_metaArtistEdit->text().trimmed();
    // 专辑。
    st.metaAlbum = m_metaAlbumEdit->text().trimmed();
    // 备注。
    st.metaComment = m_metaCommentEdit->text().trimmed();
    // 硬件加速策略。
    st.hwaccel = m_hwCombo->currentData().toString();
    // 硬件厂商偏好（仅自动策略时有意义）。
    st.hwaccelVendor = m_hwCombo->currentData(Qt::UserRole + 1).toString();
    // 厂商为空时兜底 auto。
    if (st.hwaccelVendor.isEmpty()) { st.hwaccelVendor = QStringLiteral("auto"); }
    // 额外参数：按空白切分成参数列表（用户自己填的内容，不做引号解析）。
    st.extraArgs = m_extraArgsEdit->text().split(QRegularExpression(QStringLiteral("\\s+")), Qt::SkipEmptyParts);
    // 音频面板：强制丢弃视频。
    if (m_kind == QStringLiteral("audio"))
    {
        // 丢弃视频流。
        st.dropVideo = true;
    }
    // 图片面板：强制丢弃音频。
    if (m_kind == QStringLiteral("image"))
    {
        // 丢弃音频流。
        st.dropAudio = true;
    }
    // 返回结果。
    return st;
}