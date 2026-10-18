// *****************************************************************************
// mediainfo.h —— 媒体信息探测（ffprobe 封装）。
// 职责：调用 ffprobe 以 JSON 形式读取任意文件的容器/流信息，并解析成结构化对象。
// 容错要点（这些是实测踩坑后固化的规则）：
//   1) 损坏文件或纯图片会让 ffprobe 报错甚至返回空输出；此时绝不抛异常，只返回“低置信度”结果。
//   2) 时长/码率字段经常缺失或直接写成字符串 "N/A"，因此每个字段都必须单独兜底。
//   3) 帧率形如 "30000/1001"（分数），必须换算为浮点；"0/0"（可变帧率）要归零。
//   4) 解析采取“宽松策略”：任何单字段解析失败都不影响其它字段。
// *****************************************************************************
#pragma once

// QString / QStringList：承载路径与文本字段。
#include <QString>
#include <QStringList>
// QVector：承载流列表。
#include <QVector>
// QJsonObject：保留 ffprobe 原始 JSON，供高级功能读取冷门字段。
#include <QJsonObject>
// QJsonValue：安全转换函数的参数类型（直接引入以免使用前向声明带来的歧义）。
#include <QJsonValue>

// 单个流（视频/音频/字幕）的信息。
struct StreamInfo
{
    // 流序号（ffprobe 的 index），构造 -map 参数时使用。
    int index = -1;
    // 流类型：video / audio / subtitle / data。
    QString codecType;
    // 编码名：h264 / hevc / aac / mp3 等。
    QString codecName;
    // 编码全称，用于展示。
    QString codecLongName;
    // 视频宽度（像素）。
    int width = 0;
    // 视频高度（像素）。
    int height = 0;
    // 视频帧率（已把分数解析为浮点）。
    double fps = 0.0;
    // 该流码率（bit/s），经常缺失。
    qint64 bitRate = 0;
    // 音频采样率（Hz）。
    int sampleRate = 0;
    // 音频声道数。
    int channels = 0;
    // 声道布局：stereo / mono / 5.1 等。
    QString channelLayout;
    // 该流时长（秒）。
    double duration = 0.0;
    // 像素格式：yuv420p / rgba 等。
    QString pixFmt;
    // 流语言标记（多语言音轨/字幕常用）。
    QString language;
    // 流标题。
    QString title;

    // 是否为视频流。
    bool isVideo() const { return codecType == QStringLiteral("video"); }
    // 是否为音频流。
    bool isAudio() const { return codecType == QStringLiteral("audio"); }
    // 是否为字幕流。
    bool isSubtitle() const { return codecType == QStringLiteral("subtitle"); }
    // 返回 "宽x高" 形式的分辨率文本；未知时返回空串。
    QString resolution() const;
};

// 一个媒体文件的整体信息。
struct MediaInfo
{
    // 文件绝对路径。
    QString path;
    // 文件是否存在。
    bool exists = false;
    // 文件字节数。
    qint64 size = 0;
    // 容器格式名（逗号分隔，如 "mov,mp4,m4a"）。
    QString formatName;
    // 容器全称。
    QString formatLongName;
    // 总时长（秒）。
    double duration = 0.0;
    // 总码率（bit/s）。
    qint64 bitRate = 0;
    // 全部流。
    QVector<StreamInfo> streams;
    // 探测是否成功（置信度标志）。
    bool probeOk = false;
    // 探测失败时的错误文本。
    QString error;
    // ffprobe 原始 JSON。
    QJsonObject raw;

    // 全部视频流。
    QVector<StreamInfo> videoStreams() const;
    // 全部音频流。
    QVector<StreamInfo> audioStreams() const;
    // 全部字幕流。
    QVector<StreamInfo> subtitleStreams() const;
    // 是否存在视频流。
    bool hasVideo() const;
    // 是否存在音频流。
    bool hasAudio() const;
    // 主视频流（第一条）；不存在时返回一个 isDefault 为真的空对象。
    StreamInfo primaryVideo() const;
    // 主音频流（第一条）。
    StreamInfo primaryAudio() const;
    // 粗粒度归类：video / audio / image / unknown。
    QString kind() const;
    // 用于列表展示的短名称（文件名）。
    QString displayName() const;
    // 生成一行人类可读摘要（英文，因为会显示给用户）。
    QString summaryLine() const;
};

// 探测一个媒体文件；任何失败情况都返回“对象可用但 probeOk 为 false”的结果。
MediaInfo probeMedia(const QString& path, int timeoutMs = 30000);

// 把任意 JSON 值安全转为浮点；"N/A"、空串、null 等一律回退默认值。
double jsonToDouble(const QJsonValue& value, double defaultValue = 0.0);

// 把任意 JSON 值安全转为整数。
qint64 jsonToInt64(const QJsonValue& value, qint64 defaultValue = 0);

// 把 ffprobe 的帧率字段（可能是 "30000/1001" 分数）解析为浮点。
double parseFps(const QJsonValue& value);