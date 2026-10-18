// *****************************************************************************
// ffmpeg_locator.h —— FFmpeg 内核可执行文件定位与校验。
// 背景：本软件自身不含任何转码算法，全部转码能力来自 FFmpeg；
//       能否找到并正确运行 ffmpeg.exe / ffprobe.exe 是整个软件的第一生命线。
// 查找优先级（从高到低）：
//   1) 用户在“Settings”页显式指定的自定义路径（可指向 exe，也可指向目录）；
//   2) 程序同级的 resources/ffmpeg 目录（绿色版分发的标准布局）；
//   3) 程序同级目录（方便用户自行放置新版内核）；
//   4) 系统 PATH（兼容已把 ffmpeg 装进环境变量的机器）。
// 关键设计：对每个候选都执行 `-version` 做**真实校验**（检查退出码与版本横幅），
//           而不是仅判断“文件是否存在”——同名但无关的程序也会通过存在性检查。
// 结果会缓存，避免每次转码都重复做多轮进程调用。
// *****************************************************************************
#pragma once

// QString：承载路径与版本文本。
#include <QString>
// QStringList：承载候选路径列表。
#include <QStringList>
// QHash：缓存“工具名 → 已验证路径”。
#include <QHash>
// QMutex：保护缓存与自定义路径字段的多线程访问。
#include <QMutex>

// 定位器类：全进程共用同一份缓存，因此设计成单例。
class FFmpegLocator
{
public:
    // 取得全局唯一实例（首次调用时构造，线程安全由 C++11 静态局部变量保证）。
    static FFmpegLocator& instance();

    // 取得 ffmpeg 的绝对路径；找不到返回空字符串。
    QString ffmpegPath();

    // 取得 ffprobe 的绝对路径；找不到返回空字符串。
    QString ffprobePath();

    // 设置用户自定义内核路径（空字符串表示恢复自动查找）。
    void setCustomPath(const QString& path);

    // 读取当前用户自定义内核路径。
    QString customPath() const;

    // 取得“随包内核目录”路径：程序同级目录下的 resources/ffmpeg。
    QString bundledDir() const;

    // 判断内核是否就绪（ffmpeg 与 ffprobe 都可用）。
    bool isReady();

    // 读取 ffmpeg 版本横幅的第一行，用于 Settings/About 页展示。
    QString versionText();

    // 取得 ffprobe 版本横幅的第一行。
    QString ffprobeVersionText();

    // 清空全部缓存（用户修改自定义路径后必须调用，否则仍会命中旧结果）。
    void clearCache();

private:
    // 构造函数私有：禁止外部直接构造，保证单例语义。
    FFmpegLocator() = default;

    // 内部查找实现：tool 取值 "ffmpeg" 或 "ffprobe"。
    QString findTool(const QString& tool);

    // 生成某个工具的全部候选绝对路径（已去重、已按优先级排序）。
    // custom 参数由调用方在“不持锁”的状态下取出并传入，避免锁嵌套导致死锁。
    QStringList candidatePaths(const QString& tool, const QString& custom) const;

    // 对单个候选执行 -version 真实校验。
    bool verify(const QString& exePath, int timeoutMs = 8000) const;

    // 保护缓存与自定义路径字段的互斥量（mutable 使 const 成员函数也能加锁）。
    mutable QMutex m_mutex;

    // 缓存：工具名 → 已验证可用的绝对路径（空字符串表示“已验证过但不可用”）。
    QHash<QString, QString> m_cache;

    // 用户自定义路径。
    QString m_custom;
};