// *****************************************************************************
// aboutpage.cpp —— 关于页面的实现。
// 实现要点：
//   1) 信息文本全部来自真实查询：ffmpeg -version 的首行、能力矩阵的统计、PDF 后端的编译期开关。
//   2) 提供一个“Refresh”按钮：用户替换内核后无需重启程序即可看到最新信息。
//   3) 版本号与作者写在本文件顶部常量中，便于统一修改。
// *****************************************************************************
#include "aboutpage.h"

// Qt 控件与布局。
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFormLayout>
#include <QGroupBox>
#include <QLabel>
#include <QPushButton>
#include <QPixmap>
#include <QIcon>
// 内核、能力与文档能力查询。
#include "core/ffmpeg_locator.h"
#include "core/capabilities.h"
#include "core/hwaccel.h"
#include "core/docconvert.h"
#include "core/presets.h"

// 应用版本号（显示给用户，因此单独定义便于统一维护）。
static const QString kAppVersion = QStringLiteral("1.0.0");
// 作者信息。
static const QString kAuthor = QStringLiteral("徐某");

// 构造。
AboutPage::AboutPage(QWidget* parent)
    : QWidget(parent)
{
    // 构建界面。
    buildUi();
    // 刷新信息。
    updateInfo();
}

// 构建界面。
void AboutPage::buildUi()
{
    // 根布局。
    QVBoxLayout* root = new QVBoxLayout(this);
    // 边距。
    root->setContentsMargins(14, 14, 14, 14);
    // 间距。
    root->setSpacing(10);

    // ---- 头部：图标 + 名称 + 版本 ----
    // 头部横向布局。
    QHBoxLayout* header = new QHBoxLayout;
    // 图标标签。
    QLabel* iconLabel = new QLabel;
    // 从资源中加载图标（已编进 exe，无需磁盘文件）。
    const QPixmap iconPix(QStringLiteral(":/app/icon.ico"));
    // 资源存在时设置并缩放到合适尺寸。
    if (!iconPix.isNull())
    {
        // 缩放到 64x64 并保持比例。
        iconLabel->setPixmap(iconPix.scaled(64, 64, Qt::KeepAspectRatio, Qt::SmoothTransformation));
    }
    // 加入头部。
    header->addWidget(iconLabel);
    // 名称与版本竖向容器。
    QVBoxLayout* titleBox = new QVBoxLayout;
    // 应用名标签（大字）。
    QLabel* nameLabel = new QLabel(QStringLiteral("<b style='font-size:18px'>万能转码器</b>"));
    // 版本与作者标签。
    QLabel* verLabel = new QLabel(QStringLiteral("版本 %1 &nbsp;|&nbsp; 作者：%2").arg(kAppVersion, kAuthor));
    // 加入标题容器。
    titleBox->addWidget(nameLabel);
    // 追加版本标签。
    titleBox->addWidget(verLabel);
    // 加入头部。
    header->addLayout(titleBox);
    // 余量留白。
    header->addStretch(1);
    // 加入根布局。
    root->addLayout(header);

    // 简介标签（英文）。
    QLabel* intro = new QLabel;
    // 简介文本：说明本软件的定位。
    intro->setText(
        QStringLiteral("基于 Qt 6 与 FFmpeg 的桌面通用格式转码器。视频、音频、图片、文档与抽帧等流程全部由随本程序分发的真实 FFmpeg 内核驱动；不依赖任何云服务，也没有任何隐藏的伪实现。"));
    // 允许换行。
    intro->setWordWrap(true);
    // 加入根布局。
    root->addWidget(intro);

    // ---- 信息分组 ----
    QGroupBox* infoGroup = new QGroupBox(QStringLiteral("内核信息"));
    // 信息区表单布局。
    QFormLayout* infoForm = new QFormLayout(infoGroup);
    // ffmpeg 版本行。
    QLabel* ffLabel = new QLabel;
    // 允许换行（版本横幅很长）。
    ffLabel->setWordWrap(true);
    // 设置对象名以便 updateInfo 中找到它。
    ffLabel->setObjectName(QStringLiteral("ffLabel"));
    // 加入表单。
    infoForm->addRow(QStringLiteral("FFmpeg"), ffLabel);
    // ffprobe 版本行。
    QLabel* fpLabel = new QLabel;
    // 允许换行。
    fpLabel->setWordWrap(true);
    // 对象名。
    fpLabel->setObjectName(QStringLiteral("fpLabel"));
    // 加入表单。
    infoForm->addRow(QStringLiteral("FFprobe"), fpLabel);
    // 能力摘要行。
    QLabel* capLabel = new QLabel;
    // 允许换行。
    capLabel->setWordWrap(true);
    // 对象名。
    capLabel->setObjectName(QStringLiteral("capLabel"));
    // 加入表单。
    infoForm->addRow(QStringLiteral("能力"), capLabel);
    // 硬件编码器行。
    QLabel* hwLabel = new QLabel;
    // 允许换行。
    hwLabel->setWordWrap(true);
    // 对象名。
    hwLabel->setObjectName(QStringLiteral("hwLabel"));
    // 加入表单。
    infoForm->addRow(QStringLiteral("硬件编码器"), hwLabel);
    // 预设数量行。
    QLabel* presetLabel = new QLabel;
    // 对象名。
    presetLabel->setObjectName(QStringLiteral("presetLabel"));
    // 加入表单。
    infoForm->addRow(QStringLiteral("内置预设"), presetLabel);
    // PDF 后端行。
    QLabel* pdfLabel = new QLabel;
    // 允许换行。
    pdfLabel->setWordWrap(true);
    // 对象名。
    pdfLabel->setObjectName(QStringLiteral("pdfLabel"));
    // 加入表单。
    infoForm->addRow(QStringLiteral("PDF 后端"), pdfLabel);
    // 加入根布局。
    root->addWidget(infoGroup);

    // 刷新按钮。
    QPushButton* refreshBtn = new QPushButton(QStringLiteral("刷新内核信息"));
    // 让按钮不占满整行。
    refreshBtn->setMaximumWidth(220);
    // 加入根布局。
    root->addWidget(refreshBtn);
    // 末尾留白（内容顶部对齐）。
    root->addStretch(1);

    // 刷新按钮信号。
    connect(refreshBtn, &QPushButton::clicked, this, &AboutPage::onRefresh);
}

// 刷新。
void AboutPage::onRefresh()
{
    // 清空内核与能力缓存后重新查询，保证显示的是最新状态。
    FFmpegLocator::instance().clearCache();
    // 清空能力缓存。
    Capabilities::instance().clearCache();
    // 清空硬件缓存。
    HwAccel::clearCache();
    // 更新信息文本。
    updateInfo();
}

// 更新信息。
void AboutPage::updateInfo()
{
    // 逐个查找标签并写入文本。
    QLabel* ffLabel = findChild<QLabel*>(QStringLiteral("ffLabel"));
    // ffprobe 标签。
    QLabel* fpLabel = findChild<QLabel*>(QStringLiteral("fpLabel"));
    // 能力标签。
    QLabel* capLabel = findChild<QLabel*>(QStringLiteral("capLabel"));
    // 硬件标签。
    QLabel* hwLabel = findChild<QLabel*>(QStringLiteral("hwLabel"));
    // 预设标签。
    QLabel* presetLabel = findChild<QLabel*>(QStringLiteral("presetLabel"));
    // PDF 标签。
    QLabel* pdfLabel = findChild<QLabel*>(QStringLiteral("pdfLabel"));

    // ffmpeg 版本（首个标签）。
    if (ffLabel != nullptr)
    {
        // 写入版本横幅。
        ffLabel->setText(FFmpegLocator::instance().versionText());
    }
    // ffprobe 版本。
    if (fpLabel != nullptr)
    {
        // 写入版本横幅。
        fpLabel->setText(FFmpegLocator::instance().ffprobeVersionText());
    }
    // 能力摘要。
    if (capLabel != nullptr)
    {
        // 取摘要。
        const Capabilities::Summary sum = Capabilities::instance().summary();
        // 组装文本。
        capLabel->setText(QStringLiteral("编码器 %1（视频 %2，音频 %3，字幕 %4） | 封装/解封装格式 %5")
                          .arg(sum.encoderTotal)
                          .arg(sum.videoEncoders)
                          .arg(sum.audioEncoders)
                          .arg(sum.subtitleEncoders)
                          .arg(sum.formatTotal));
    }
    // 硬件编码器。
    if (hwLabel != nullptr)
    {
        // 取详情列表。
        const QVector<HwEncoderInfo> hw = HwAccel::availableDetail();
        // 列表为空时明确说明“无”。
        if (hw.isEmpty())
        {
            // 写入提示。
            hwLabel->setText(QStringLiteral("当前内核未检测到硬件编码器（将使用软件编码）。"));
        }
        else
        {
            // 收集名称。
            QStringList names;
            // 遍历。
            for (const HwEncoderInfo& h : hw)
            {
                // 追加“名称（厂商）”形式。
                names << QStringLiteral("%1 (%2)").arg(h.name, h.vendor);
            }
            // 写入完整列表。
            hwLabel->setText(names.join(QStringLiteral(", ")));
        }
    }
    // 预设数量。
    if (presetLabel != nullptr)
    {
        // 统计三类的数量。
        const int v = presetsByCategory(QStringLiteral("video")).size();
        // 音频。
        const int a = presetsByCategory(QStringLiteral("audio")).size();
        // 图片。
        const int i = presetsByCategory(QStringLiteral("image")).size();
        // 写入文本（同时给出总计，便于与需求核对）。
        presetLabel->setText(QStringLiteral("共 %1 套（视频 %2 / 音频 %3 / 图片 %4）").arg(v + a + i).arg(v).arg(a).arg(i));
    }
    // PDF 后端。
    if (pdfLabel != nullptr)
    {
        // 写入后端说明（如实反映编译期能力）。
        pdfLabel->setText(DocConvert::pdfBackendInfo());
    }
}