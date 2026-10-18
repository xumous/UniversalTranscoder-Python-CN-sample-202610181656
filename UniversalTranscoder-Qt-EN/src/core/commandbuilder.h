// *****************************************************************************
// commandbuilder.h —— 命令构建模块（本项目算法核心之一）。
// 职责：把“用户在界面上表达的意图”（TranscodeSettings）编译成一条可直接执行的 ffmpeg 参数列表。
// 关键设计：
//   1) 一切以“参数列表 QStringList”而非“命令行字符串”形式产出，
//      彻底避免 Windows / Unix 的引号与空格差异，也避免用户输入造成命令注入。
//   2) 所有可能失败的组合都做前置校验与降级（例如图片格式不允许带音频流 → 自动丢弃音频）。
//   3) 视频滤镜链按固定顺序拼接：裁剪 → 缩放 → 旋转/镜像 → 帧率 → 水印 → 字幕 → 变速。
//      顺序不可随意调换，否则会出现“旋转后坐标错乱”“水印随缩放变形”等隐蔽问题。
// 注意：本文件中会显示给用户的文本一律英文；注释保持中文。
// *****************************************************************************
#pragma once

// QString / QStringList：承载路径、参数与文本。
#include <QString>
#include <QStringList>
// QVector：承载多组命令（抽帧场景）。
#include <QVector>
// MediaInfo：构建命令时需要知道源文件是否有音/视频流。
#include "mediainfo.h"

// 一次转码请求的全部可调参数。
// 字段较多是因为要覆盖视频 / 音频 / 图片三类场景，任一场景只用到其中一部分。
struct TranscodeSettings
{
    // ---- 基本 ----
    // 源文件绝对路径。
    QString source;
    // 目标格式（扩展名，不含点）。
    QString targetFormat = QStringLiteral("mp4");
    // 使用的预设 key（用于回填与记忆）。
    QString presetKey;
    // 输出命名方式：replace（替换扩展名）/ suffix（原名+后缀）/ custom（自定义模板）。
    QString namingMode = QStringLiteral("replace");
    // 自定义命名模板，支持 {name} {ext} {index} {date}。
    QString nameTemplate = QStringLiteral("{name}_{index}");

    // ---- 编码 ----
    // 视频编码器；空表示用容器默认值。
    QString videoCodec;
    // 音频编码器；空表示用容器默认值。
    QString audioCodec;
    // 质量档位：lossless / high / medium / low。
    QString quality = QStringLiteral("medium");
    // 恒定质量因子（优先于视频码率）。
    QString crf;
    // 视频码率（如 "2500k"）。
    QString vBitrate;
    // 音频码率（如 "128k"）。
    QString aBitrate;
    // 目标像素格式（空则用默认 yuv420p）。
    QString pixelFormat;
    // 编码线程数；0 表示由 ffmpeg 自动决定。
    int threads = 0;

    // ---- 画面 ----
    // 目标宽（0 = 保持原样）。
    int width = 0;
    // 目标高（0 = 保持原样）。
    int height = 0;
    // 缩放模式：fit（适应并加边）/ fill（填满并裁剪）/ stretch（拉伸）。
    QString scaleMode = QStringLiteral("fit");
    // 目标帧率（空 = 保持原样；支持 "25" 或 "30000/1001"）。
    QString fps;
    // 旋转角度：0 / 90 / 180 / 270。
    int rotate = 0;
    // 镜像：none / h / v / both。
    QString flip = QStringLiteral("none");
    // 裁剪 "w:h:x:y" 或 "w:h"（空 = 不裁剪）。
    QString crop;
    // 图片水印路径。
    QString watermarkPath;
    // 文字水印内容。
    QString watermarkText;
    // 水印位置：tl / tr / bl / br / tc / bc / center。
    QString watermarkPos = QStringLiteral("tr");
    // 图片水印相对宽度比例（0.05 ~ 1.0）。
    double watermarkScale = 0.15;
    // 水印不透明度（0.0 ~ 1.0）。
    double watermarkOpacity = 0.75;
    // 外挂字幕路径（烧录进画面）。
    QString subtitlePath;

    // ---- 音频 ----
    // 目标声道数（0 = 保持）。
    int audioChannels = 0;
    // 目标采样率（0 = 保持）。
    int sampleRate = 0;
    // 音量滤镜参数（如 "1.5" 或 "-6dB"）。
    QString volume;

    // ---- 时间 ----
    // 起始时间（如 "00:00:10" 或 "10.5"）。
    QString start;
    // 结束时间（绝对时间点）。
    QString end;
    // 截取时长（与 end 二选一；二者都给时以 duration 为准）。
    QString duration;

    // ---- 变速 ----
    // 播放速度倍率（常用 0.25 ~ 4.0）。
    double speed = 1.0;

    // ---- 元数据 ----
    // 标题。
    QString metaTitle;
    // 艺术家。
    QString metaArtist;
    // 专辑。
    QString metaAlbum;
    // 备注。
    QString metaComment;

    // ---- 行为 ----
    // 是否直接复制流（仅换容器、不重编码）。
    bool copyMode = false;
    // 硬件加速策略：none（默认，纯软件）/ auto（自动挑硬件）/ 具体编码器名。
    QString hwaccel = QStringLiteral("none");
    // 硬件厂商偏好：auto / nvidia / intel / amd / apple。
    QString hwaccelVendor = QStringLiteral("auto");
    // 流选择：auto（自动）/ first（取第一组）/ custom（按序号）。
    QString streamMode = QStringLiteral("auto");
    // 指定视频流序号（仅在 streamMode 为 custom 时有效）。
    int videoIndex = -1;
    // 指定音频流序号。
    int audioIndex = -1;
    // 指定字幕流序号。
    int subtitleIndex = -1;
    // 强制丢弃音频。
    bool dropAudio = false;
    // 强制丢弃视频。
    bool dropVideo = false;
    // 额外追加的 ffmpeg 参数（预设里的附加参数也会合并到这里）。
    QStringList extraArgs;

    // ---- 输出 ----
    // 输出目录（空 = 与源文件同目录）。
    QString outputDir;
    // 显式输出文件名（含扩展名）；空则按命名方式自动生成。
    QString outputName;
    // 是否允许覆盖已存在的输出文件。
    bool overwrite = false;
};

// 把用户给的目标扩展名换算成 ffmpeg 的封装格式名（如 mkv→matroska、ts→mpegts）。
QString resolveContainer(const QString& targetFormat);

// 从目标格式中提取安全扩展名（仅保留字母数字，兜底 mp4）。
QString safeExtension(const QString& targetFormat);

// 向下取偶数（最小为 2）；多数视频编码器要求宽高为偶数。
int evenValue(int value);

// 把时间字符串解析为秒数；无法解析时返回 false 并把结果置 0。
bool parseTimeValue(const QString& text, double* secondsOut);

// 把秒数格式化为 ffmpeg 可接受的 "HH:MM:SS.mmm"。
QString formatTimeValue(double seconds);

// 计算输出文件路径（含防覆盖自动追加序号）。
QString computeOutputPath(const TranscodeSettings& st, bool overwrite = false);

// 构建视频滤镜链（不含图片水印与字幕这两类“需要多输入”的处理）。
// withTextWatermark 为真时把文字水印（drawtext）插入到“水印”槽位。
// sourceHeight 用于按画面高度推算文字水印的字号（0 表示未知，则使用固定兜底字号）。
QString buildFilterChain(const TranscodeSettings& st, bool withTextWatermark, int sourceHeight = 0);

// 构建音频滤镜链（音量 + 变速）。
QString buildAudioFilterChain(const TranscodeSettings& st);

// 把设置编译为完整的 ffmpeg 参数列表（首元素为 ffmpeg 可执行文件路径）。
// info 为 nullptr 时按“源既有视频又有音频”保守处理（交由 ffmpeg 自行判断）。
QStringList buildCommand(const TranscodeSettings& st, const MediaInfo* info, const QString& outPath = QString());

// 生成一份“强制软件编码”的设置副本，用于硬件编码失败后的自动回退。
TranscodeSettings softwareFallback(const TranscodeSettings& st);

// 把参数列表渲染成一行便于阅读的命令文本（仅用于界面展示与复制，不用于执行）。
QString describeCommand(const QStringList& args);

// 视频抽帧计划：在若干时间点各抽一帧保存为 PNG。
struct FrameExtractionPlan
{
    // 每一帧的输出文件绝对路径（与 commands 一一对应）。
    QStringList outPaths;
    // 与 outPaths 一一对应的 ffmpeg 命令（完整参数列表，含 -ss 精确定位）。
    QVector<QStringList> commands;
};

// 构建“视频抽帧”计划：count 为抽帧数量；durationSeconds 为源时长（<=0 时退化为只抽首帧）。
FrameExtractionPlan buildFrameExtractionPlan(const QString& sourcePath,
                                             double durationSeconds,
                                             int count,
                                             const QString& outDir,
                                             const QString& baseName);