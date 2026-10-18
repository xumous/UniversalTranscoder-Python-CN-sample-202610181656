// *****************************************************************************
// capabilities.h —— FFmpeg 内核能力探测（能力矩阵）。
// 为什么需要它：
//   不同版本/不同编译选项的 FFmpeg 支持的编码器与封装差异极大（例如有的不含 libx265）。
//   如果界面无条件列出所有格式，用户一旦选中内核不支持的组合就会转码失败。
//   因此启动时向 ffmpeg 询问真实能力，并据此过滤界面上的可选格式。
// 实现方式（各执行一次并缓存）：
//   `ffmpeg -encoders`  → 全部编码器（行首标志位表示视频 V / 音频 A / 字幕 S）
//   `ffmpeg -formats`   → 全部封装与解封装格式（行首 D 解封装 / E 封装）
//   `ffmpeg -hwaccels`  → 可用的硬件加速后端名
// *****************************************************************************
#pragma once

// QString / QStringList / QHash / QSet：承载解析结果。
#include <QString>
#include <QStringList>
#include <QHash>
#include <QSet>
// QChar：表示编码器类别标志（V/A/S）。
#include <QChar>
// QMutex：保护缓存。
#include <QMutex>

// 能力矩阵单例。
class Capabilities
{
public:
    // 全局实例。
    static Capabilities& instance();

    // 返回可用编码器字典：编码器名 → 类别（'V' 视频 / 'A' 音频 / 'S' 字幕）。
    QHash<QString, QChar> encoders();

    // 返回可用封装/解封装格式名集合（全部小写）。
    QSet<QString> formats();

    // 返回可用的硬件加速后端名列表。
    QStringList hwaccels();

    // 过滤出某一类别的全部编码器名（已排序）。
    QStringList encodersOf(QChar kind);

    // 判断内核是否包含某编码器。
    bool supportsEncoder(const QString& name);

    // 判断内核是否支持某封装名（带常见别名等价匹配）。
    bool supportsFormat(const QString& name);

    // 能力摘要（用于 About / Settings 页展示）。
    struct Summary
    {
        // 编码器总数。
        int encoderTotal = 0;
        // 视频编码器数量。
        int videoEncoders = 0;
        // 音频编码器数量。
        int audioEncoders = 0;
        // 字幕编码器数量。
        int subtitleEncoders = 0;
        // 封装格式总数。
        int formatTotal = 0;
        // 硬件加速后端名列表。
        QStringList hwaccels;
    };

    // 生成并返回摘要。
    Summary summary();

    // 清空缓存（更换内核或修改自定义路径后必须调用）。
    void clearCache();

private:
    // 私有构造：单例。
    Capabilities() = default;

    // 同步执行一条 ffmpeg 查询命令并返回其合并输出文本；失败返回空字符串。
    QString runQuery(const QStringList& args);

    // 缓存互斥量。
    mutable QMutex m_mutex;

    // 缓存标志：对应集合是否已经探测过。
    bool m_encodersLoaded = false;
    bool m_formatsLoaded = false;
    bool m_hwaccelsLoaded = false;

    // 缓存数据。
    QHash<QString, QChar> m_encoders;
    QSet<QString> m_formats;
    QStringList m_hwaccels;
};