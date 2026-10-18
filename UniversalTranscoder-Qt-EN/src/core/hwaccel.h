// *****************************************************************************
// hwaccel.h —— 硬件编码器探测与选择。
// 目的：充分利用显卡的专用编码单元（NVENC / QSV / AMF / VideoToolbox）大幅提速。
// 关键认识：硬件编码器“存在”不等于“可用”（驱动版本、显存、分辨率限制都可能导致失败），
//           因此本模块只做“该编码器是否被编入当前内核”的静态探测，
//           真正的可用性判断交给运行时容错（见 errors 模块的 HW_ACCEL 分类与自动回退）。
// *****************************************************************************
#pragma once

// QString / QStringList：携带编码器名与详情文本。
#include <QString>
#include <QStringList>
// QVector：承载硬件编码器详情列表。
#include <QVector>

// 单个硬件编码器的详情。
struct HwEncoderInfo
{
    // ffmpeg 中的编码器名，例如 "h264_nvenc"。
    QString name;
    // 厂商标识：nvidia / intel / amd / apple / any。
    QString vendor;
    // 面向用户的说明文本（英文，会显示在界面上）。
    QString label;
};

// 硬件加速辅助类（全部为静态方法，无状态；探测结果在内部按引用内核路径缓存）。
class HwAccel
{
public:
    // 返回当前内核中可用的硬件编码器名列表。
    static QStringList available();

    // 返回可用硬件编码器的详情列表。
    static QVector<HwEncoderInfo> availableDetail();

    // 为 H.264 选择硬件编码器；无可用时返回空字符串（调用方据此回退软件编码）。
    static QString pickH264(const QString& vendor = QStringLiteral("auto"));

    // 为 H.265/HEVC 选择硬件编码器；无可用时返回空字符串。
    static QString pickHevc(const QString& vendor = QStringLiteral("auto"));

    // 是否存在任何可用的硬件编码器。
    static bool hasAny();

    // 清空缓存（更换内核后调用）。
    static void clearCache();
};