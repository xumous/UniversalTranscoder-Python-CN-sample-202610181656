// *****************************************************************************
// hwaccel.cpp —— 硬件编码器探测与选择的实现。
// 实现要点：
//   1) 可用性判断完全建立在 capabilities 模块（即真实内核的 -encoders 输出）之上，
//      绝不凭“机器上装了什么显卡”臆测。
//   2) 自动策略的厂商优先级为 NVIDIA → Intel → AMD → Apple：
//      NVENC 在各类机器上的可用性与兼容性口碑最好，因此排第一。
//   3) 结果带缓存，避免每次构建命令都重跑能力矩阵。
// *****************************************************************************
#include "hwaccel.h"

// 能力矩阵：提供内核真实包含的编码器集合。
#include "capabilities.h"
// QVector：返回详情列表。
#include <QVector>
// QSet / QHash：集合查找与厂商映射。
#include <QSet>
#include <QHash>
// QMutex / QMutexLocker：保护缓存。
#include <QMutex>
#include <QMutexLocker>
// QDebug：日志。
#include <QDebug>

// 已知硬件编码器 → (厂商, 说明文本) 的映射表。
// 说明文本用英文是因为它会显示在 Settings 页的硬件加速下拉框里。
static const QVector<HwEncoderInfo>& hwEncoderTable()
{
    // 函数内静态表：只构造一次，避免每次调用都重建容器。
    static const QVector<HwEncoderInfo> table = {
        // NVIDIA NVENC —— H.264。
        {QStringLiteral("h264_nvenc"), QStringLiteral("nvidia"), QStringLiteral("NVIDIA NVENC（H.264）")},
        // NVIDIA NVENC —— H.265/HEVC。
        {QStringLiteral("hevc_nvenc"), QStringLiteral("nvidia"), QStringLiteral("NVIDIA NVENC（H.265/HEVC）")},
        // NVIDIA NVENC —— AV1。
        {QStringLiteral("av1_nvenc"), QStringLiteral("nvidia"), QStringLiteral("NVIDIA NVENC（AV1）")},
        // Intel Quick Sync Video —— H.264。
        {QStringLiteral("h264_qsv"), QStringLiteral("intel"), QStringLiteral("Intel Quick Sync Video（H.264）")},
        // Intel Quick Sync Video —— H.265/HEVC。
        {QStringLiteral("hevc_qsv"), QStringLiteral("intel"), QStringLiteral("Intel Quick Sync Video（H.265/HEVC）")},
        // Intel Quick Sync Video —— AV1。
        {QStringLiteral("av1_qsv"), QStringLiteral("intel"), QStringLiteral("Intel Quick Sync Video（AV1）")},
        // AMD AMF —— H.264。
        {QStringLiteral("h264_amf"), QStringLiteral("amd"), QStringLiteral("AMD AMF（H.264）")},
        // AMD AMF —— H.265/HEVC。
        {QStringLiteral("hevc_amf"), QStringLiteral("amd"), QStringLiteral("AMD AMF（H.265/HEVC）")},
        // AMD AMF —— AV1。
        {QStringLiteral("av1_amf"), QStringLiteral("amd"), QStringLiteral("AMD AMF（AV1）")},
        // VAAPI —— H.264（Linux 与部分驱动场景）。
        {QStringLiteral("h264_vaapi"), QStringLiteral("intel"), QStringLiteral("VAAPI（H.264）")},
        // VAAPI —— H.265。
        {QStringLiteral("hevc_vaapi"), QStringLiteral("intel"), QStringLiteral("VAAPI（H.265/HEVC）")},
        // Apple VideoToolbox —— H.264（macOS）。
        {QStringLiteral("h264_videotoolbox"), QStringLiteral("apple"), QStringLiteral("VideoToolbox（H.264）")},
        // Apple VideoToolbox —— H.265（macOS）。
        {QStringLiteral("hevc_videotoolbox"), QStringLiteral("apple"), QStringLiteral("VideoToolbox（H.265/HEVC）")},
        // Windows Direct3D 12 视频编码 —— H.264。
        {QStringLiteral("h264_d3d12va"), QStringLiteral("any"), QStringLiteral("Direct3D 12 Video（H.264）")},
        // Windows Direct3D 12 视频编码 —— H.265。
        {QStringLiteral("hevc_d3d12va"), QStringLiteral("any"), QStringLiteral("Direct3D 12 Video（H.265/HEVC）")}
    };
    // 返回静态表引用。
    return table;
}

// 缓存与锁。
static QMutex g_hwMutex;
// 缓存标志：是否已探测过硬件编码器集合。
static bool g_hwLoaded = false;
// 缓存的可硬件编码器名集合。
static QStringList g_hwAvailable;

// 获取可用硬件编码器列表。
QStringList HwAccel::available()
{
    // 第一阶段：读缓存。
    {
        // 自动加解锁。
        QMutexLocker locker(&g_hwMutex);
        // 已探测过则直接返回副本。
        if (g_hwLoaded)
        {
            // 返回缓存副本（按值返回，调用方修改不会影响缓存）。
            return g_hwAvailable;
        }
    }
    // 第二阶段：锁外探测（能力矩阵内部可能启动 ffmpeg 进程，绝不能在锁内做）。
    QStringList result;
    // 取得内核中全部视频编码器名。
    const QStringList videoEncoders = Capabilities::instance().encodersOf(QLatin1Char('V'));
    // 把视频编码器名转成集合以便 O(1) 查找。
    const QSet<QString> videoSet(videoEncoders.begin(), videoEncoders.end());
    // 遍历已知硬件编码器表，只保留内核确实包含的那些。
    for (const HwEncoderInfo& info : hwEncoderTable())
    {
        // 命中则收集。
        if (videoSet.contains(info.name))
        {
            // 追加编码器名。
            result.append(info.name);
        }
    }
    // 第三阶段：写缓存。
    {
        // 自动加解锁。
        QMutexLocker locker(&g_hwMutex);
        // 写入结果。
        g_hwAvailable = result;
        // 标记已探测。
        g_hwLoaded = true;
    }
    // 记录日志，便于排障时确认硬件能力。
    qInfo("[hwaccel] available encoders: %s", result.isEmpty() ? "none" : qPrintable(result.join(QLatin1Char(','))));
    // 返回结果。
    return result;
}

// 获取可用硬件编码器详情。
QVector<HwEncoderInfo> HwAccel::availableDetail()
{
    // 结果容器。
    QVector<HwEncoderInfo> out;
    // 取得可用名称列表。
    const QStringList names = available();
    // 建立名称集合便于比对。
    const QSet<QString> nameSet(names.begin(), names.end());
    // 遍历硬件编码器表。
    for (const HwEncoderInfo& info : hwEncoderTable())
    {
        // 只收集可用的那些。
        if (nameSet.contains(info.name))
        {
            // 追加详情。
            out.append(info);
        }
    }
    // 返回结果。
    return out;
}

// 内部辅助：按厂商优先级挑选。
static QString pickByVendor(const QStringList& availableList,
                            const QString& vendor,
                            const QHash<QString, QString>& vendorToH264)
{
    // 建立集合便于查找。
    const QSet<QString> av(availableList.begin(), availableList.end());
    // 情况 A：自动策略（或厂商为空）时按固定优先级遍历。
    if (vendor.isEmpty() || vendor == QStringLiteral("auto"))
    {
        // 优先级顺序：NVIDIA → Intel → AMD → Apple。
        static const QStringList order = {
            QStringLiteral("nvidia"), QStringLiteral("intel"),
            QStringLiteral("amd"), QStringLiteral("apple")
        };
        // 逐个厂商尝试。
        for (const QString& v : order)
        {
            // 取该厂商对应的编码器名。
            const QString cand = vendorToH264.value(v);
            // 该厂商有对应编码器且内核确实包含，则返回。
            if (!cand.isEmpty() && av.contains(cand))
            {
                // 命中即返回。
                return cand;
            }
        }
        // 一个都没命中则返回空串（调用方回退软件编码）。
        return QString();
    }
    // 情况 B：用户指定了具体厂商 —— 只取其对应编码器。
    const QString cand = vendorToH264.value(vendor);
    // 内核包含才返回，否则返回空串。
    return (!cand.isEmpty() && av.contains(cand)) ? cand : QString();
}

// 选择 H.264 硬件编码器。
QString HwAccel::pickH264(const QString& vendor)
{
    // 厂商 → H.264 硬件编码器的映射表。
    static const QHash<QString, QString> map = {
        {QStringLiteral("nvidia"), QStringLiteral("h264_nvenc")},
        {QStringLiteral("intel"), QStringLiteral("h264_qsv")},
        {QStringLiteral("amd"), QStringLiteral("h264_amf")},
        {QStringLiteral("apple"), QStringLiteral("h264_videotoolbox")}
    };
    // 委托给通用挑选函数。
    return pickByVendor(available(), vendor, map);
}

// 选择 H.265/HEVC 硬件编码器。
QString HwAccel::pickHevc(const QString& vendor)
{
    // 厂商 → H.265 硬件编码器的映射表。
    static const QHash<QString, QString> map = {
        {QStringLiteral("nvidia"), QStringLiteral("hevc_nvenc")},
        {QStringLiteral("intel"), QStringLiteral("hevc_qsv")},
        {QStringLiteral("amd"), QStringLiteral("hevc_amf")},
        {QStringLiteral("apple"), QStringLiteral("hevc_videotoolbox")}
    };
    // 委托给通用挑选函数。
    return pickByVendor(available(), vendor, map);
}

// 是否存在任何硬件编码器。
bool HwAccel::hasAny()
{
    // 直接判断可用列表是否非空。
    return !available().isEmpty();
}

// 清空缓存。
void HwAccel::clearCache()
{
    // 加锁后重置。
    QMutexLocker locker(&g_hwMutex);
    // 清空列表。
    g_hwAvailable.clear();
    // 重置标志。
    g_hwLoaded = false;
}