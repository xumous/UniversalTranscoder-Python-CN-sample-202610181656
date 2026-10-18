// *****************************************************************************
// docconvert.h —— 文档类转换。
// 为什么单独成模块：视频/音频/图片的转码走 FFmpeg，而“文档类”转换
// （图片→PDF、文本→PDF、PDF→图片）用 FFmpeg 并不合适；
// 这里全部使用 Qt 自身的绘图与 PDF 能力实现，完全离线可用，不依赖任何外部办公软件。
// 覆盖能力：
//   1) 图片 → PDF：多张图片各自成页合成一个 PDF；
//   2) 文本 → PDF：按 A4 版式排版并自动分页；
//   3) PDF → 图片：按页渲染为 PNG/JPG（依赖 Qt 的 pdf 模块，缺失时如实报告不支持）。
// 所有函数都对“依赖缺失 / 文件损坏”做容错：返回结果对象而不抛出异常。
// 注意：本文件中会显示给用户的文本一律英文；注释保持中文。
// *****************************************************************************
#pragma once

// QString / QStringList：路径与输出列表。
#include <QString>
#include <QStringList>

// 文档转换结果。
struct DocResult
{
    // 是否成功。
    bool ok = false;
    // 输出文件列表。
    QStringList outputs;
    // 失败原因（英文）。
    QString error;
    // 成功提示（英文）。
    QString message;
};

// 文档转换工具类（全部为静态方法，无状态）。
class DocConvert
{
public:
    // 把多张图片合并为一个多页 PDF。
    static DocResult imagesToPdf(const QStringList& imagePaths, const QString& outPdf, int dpi = 150);

    // 把纯文本文件转换为 PDF（自动分页）。
    static DocResult textToPdf(const QString& textPath, const QString& outPdf, const QString& title);

    // 把 PDF 逐页渲染为图片。
    // fmt 取值 "png" 或 "jpg"；pageRange 形如 "1-3,5"，空串表示全部页。
    static DocResult pdfToImages(const QString& pdfPath, const QString& outDir, const QString& baseName,
                                 int dpi, const QString& fmt, const QString& pageRange);

    // 当前构建是否包含 PDF → 图片 能力（取决于是否链接了 Qt 的 pdf 模块）。
    static bool pdfReadSupported();

    // 返回 PDF 能力的说明文本（英文），用于在界面上如实告知用户。
    static QString pdfBackendInfo();

    // 解析页码范围字符串，返回 0 基的页索引列表；非法片段自动忽略，越界自动裁剪。
    static QList<int> parsePageRange(const QString& spec, int totalPages);
};