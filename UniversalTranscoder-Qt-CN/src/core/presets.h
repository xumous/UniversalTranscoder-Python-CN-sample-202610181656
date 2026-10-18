// *****************************************************************************
// presets.h —— 内置转码预设（共 35 套：视频 14 / 音频 11 / 图片 10）。
// 预设 = “一套开箱即用的完整转码参数”，让不了解编码细节的用户一键得到合理结果。
// 设计原则：
//   1) 预设只是纯数据结构，界面可以列出、套用、微调，命令行构建器也能直接消费。
//   2) 优先选用“通用性最好”的编码器（H.264 用 libx264，音频用 aac/mp3），保证跨设备可播。
//   3) 预设中的 name 与 note 会显示给用户，因此一律使用英文；本文件注释保持中文。
// *****************************************************************************
#pragma once

// QString / QStringList：承载各字段与附加参数。
#include <QString>
#include <QStringList>
// QVector：承载预设列表。
#include <QVector>

// 一个转码预设。
struct Preset
{
    // 唯一标识（英文短名，作为配置记忆键）。
    QString key;
    // 显示名（英文）。
    QString name;
    // 所属类别：video / audio / image。
    QString category;
    // 目标容器/扩展名（不含点，如 "mp4"）。
    QString container;
    // 视频编码器；为空表示无视频（音频预设）。
    QString videoCodec;
    // 音频编码器；为空表示无音频（图片预设）。
    QString audioCodec;
    // 质量档位：lossless / high / medium / low。
    QString quality = QStringLiteral("medium");
    // 视频码率（如 "2500k"）；为空表示使用 CRF 策略。
    QString vBitrate;
    // 音频码率（如 "128k"）。
    QString aBitrate;
    // 恒定质量因子（x264/x265 系列有效）。
    QString crf;
    // 附加视频滤镜（如 "scale=-2:720"）。
    QString extraVf;
    // 附加音频滤镜。
    QString extraAf;
    // 是否启用 MP4 网络快启动（moov 前置）。
    bool faststart = false;
    // 目标像素格式（最大兼容性）。
    QString pixelFormat = QStringLiteral("yuv420p");
    // 面向用户的一句话说明（英文）。
    QString note;
    // 其它原样追加的 ffmpeg 参数。
    QStringList extra;
};

// 返回全部预设（视频 + 音频 + 图片）。
QVector<Preset> allPresets();

// 按类别筛选预设。
QVector<Preset> presetsByCategory(const QString& category);

// 按 key 查找预设；找不到返回一个默认构造的空预设（key 为空）。
Preset presetByKey(const QString& key);