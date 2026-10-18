// *****************************************************************************
// docconvert.cpp —— 文档类转换的实现。
// 实现要点：
//   1) 图片→PDF 与 文本→PDF 全部使用 QPdfWriter + QPainter 手工绘制，
//      这样既能精确控制页面尺寸与留白，又不依赖任何第三方库；
//      文本排版额外借助 QTextDocument::print()，由 Qt 负责自动分页。
//   2) PDF→图片 只有在编译期打开了 HAVE_QTPDF（即链接了 Qt 的 pdf 模块）时才实现；
//      否则必须如实返回“不支持”，绝不用其它手段伪装成功。
//   3) 所有异常路径都返回 DocResult 而不抛出，保证上层界面流程稳定。
// *****************************************************************************
#include "docconvert.h"

// QPdfWriter：写出 PDF 文件。
#include <QPdfWriter>
// QPainter：绘制内容。
#include <QPainter>
// QImage / QImageReader：读取图片（QImageReader 可读取磁盘上的常见图片格式）。
#include <QImage>
#include <QImageReader>
// QPageSize / QPageLayout：页面尺寸与边距。
#include <QPageSize>
#include <QPageLayout>
// QTextDocument：文本排版与自动分页。
#include <QTextDocument>
// QFile / QFileInfo / QDir：文件读写与路径。
#include <QFile>
#include <QFileInfo>
#include <QDir>
// QFont：文本字体。
#include <QFont>
// QMarginsF：边距数值。
#include <QMarginsF>
// QList：承载页索引列表。
#include <QList>
// <algorithm>：std::sort 与 std::unique（用于页码范围去重排序）。
#include <algorithm>
// QDebug：排障日志。
#include <QDebug>

// 条件引入 Qt 的 PDF 读取模块（只有库确实存在时才会被编译进来）。
#ifdef HAVE_QTPDF
// QPdfDocument：读取并渲染 PDF 页面。
#include <QPdfDocument>
// QPdfDocumentRenderOptions：渲染选项。
#include <QPdfDocumentRenderOptions>
#endif

// PDF → 图片 是否受支持。
bool DocConvert::pdfReadSupported()
{
#ifdef HAVE_QTPDF
    // 编译期已链接 Qt 的 pdf 模块，能力可用。
    return true;
#else
    // 未链接该模块时必须如实报告不支持。
    return false;
#endif
}

// PDF 能力说明文本。
QString DocConvert::pdfBackendInfo()
{
#ifdef HAVE_QTPDF
    // 支持时给出模块名，便于用户确认实现方式。
    return QStringLiteral("Qt Pdf 模块（QPdfDocument） - PDF 页面渲染可用");
#else
    // 不支持时给出明确、可执行的说明（而不是含糊其辞）。
    return QStringLiteral("当前构建不可用 Qt Pdf 模块 - 不支持 PDF 转图片");
#endif
}

// 解析页码范围。
QList<int> DocConvert::parsePageRange(const QString& spec, int totalPages)
{
    // 空串表示全部页。
    if (spec.trimmed().isEmpty())
    {
        // 结果容器。
        QList<int> all;
        // 逐页加入 0 基索引。
        for (int i = 0; i < totalPages; ++i) { all.append(i); }
        // 返回。
        return all;
    }
    // 收集结果。
    QList<int> result;
    // 按逗号切分片段。
    const QStringList chunks = spec.split(QLatin1Char(','), Qt::SkipEmptyParts);
    // 逐片段处理。
    for (const QString& raw : chunks)
    {
        // 去空白。
        const QString seg = raw.trimmed();
        // 空片段跳过。
        if (seg.isEmpty()) { continue; }
        // 含连字符表示区间。
        if (seg.contains(QLatin1Char('-')))
        {
            // 拆成起点与终点。
            const QStringList range = seg.split(QLatin1Char('-'));
            // 必须恰好两段。
            if (range.size() != 2) { continue; }
            // 解析起点。
            bool okA = false;
            // 解析终点。
            bool okB = false;
            // 起点页码（1 基）。
            int a = range.at(0).trimmed().toInt(&okA);
            // 终点页码（1 基）。
            int b = range.at(1).trimmed().toInt(&okB);
            // 任一解析失败则跳过该片段。
            if (!okA || !okB) { continue; }
            // 保证 start <= end（用户写反了也能正常工作）。
            if (a > b)
            {
                const int tmp = a;
                a = b;
                b = tmp;
            }
            // 展开区间并裁剪到合法范围。
            for (int page = a; page <= b; ++page)
            {
                // 页码必须落在 [1, totalPages] 内。
                if (page >= 1 && page <= totalPages)
                {
                    // 转为 0 基索引加入。
                    result.append(page - 1);
                }
            }
        }
        else
        {
            // 单页。
            bool ok = false;
            // 解析页码。
            const int page = seg.toInt(&ok);
            // 解析失败跳过。
            if (!ok) { continue; }
            // 落在合法范围内才加入。
            if (page >= 1 && page <= totalPages)
            {
                // 转为 0 基索引。
                result.append(page - 1);
            }
        }
    }
    // 去重并按升序排列（std::sort + unique 的等价 Qt 写法）。
    std::sort(result.begin(), result.end());
    // 去重迭代器。
    const auto last = std::unique(result.begin(), result.end());
    // 删除尾部重复项。
    result.erase(last, result.end());
    // 返回结果。
    return result;
}

// 图片 → PDF。
DocResult DocConvert::imagesToPdf(const QStringList& imagePaths, const QString& outPdf, int dpi)
{
    // 结果对象。
    DocResult res;
    // 过滤出确实存在的图片。
    QStringList valid;
    // 遍历输入。
    for (const QString& p : imagePaths)
    {
        // 非空且存在才保留。
        if (!p.trimmed().isEmpty() && QFileInfo::exists(p))
        {
            // 追加到有效列表。
            valid.append(p);
        }
    }
    // 一张可用图片都没有时直接返回失败。
    if (valid.isEmpty())
    {
        // 错误文本（英文）。
        res.error = QStringLiteral("未提供可用的图片文件。");
        // 返回。
        return res;
    }
    // 输出目录必须存在，否则 QPdfWriter 无法创建文件。
    const QString outDir = QFileInfo(outPdf).absolutePath();
    // 目录非空才创建。
    if (!outDir.isEmpty())
    {
        // 递归创建目录。
        QDir().mkpath(outDir);
    }
    // 创建 PDF 写入器。
    QPdfWriter writer(outPdf);
    // A4 纸张（照片类文档最常用）。
    writer.setPageSize(QPageSize(QPageSize::A4));
    // 分辨率：数值越大图像越清晰、文件越大；把 dpi 限制在合理区间。
    writer.setResolution(qBound(72, dpi, 600));
    // 页边距设为 0，让图片尽可能占满页面（图片自身按比例居中留白）。
    writer.setPageMargins(QMarginsF(0.0, 0.0, 0.0, 0.0), QPageLayout::Point);
    // PDF 标题元信息。
    writer.setTitle(QFileInfo(outPdf).completeBaseName());
    // 开始绘制。
    QPainter painter;
    // 若画笔无法启动（例如输出路径不可写），返回明确的失败结果。
    if (!painter.begin(&writer))
    {
        // 错误文本。
        res.error = QStringLiteral("无法打开用于写入的输出 PDF：%1").arg(outPdf);
        // 返回。
        return res;
    }
    // 记录成功写入的页数。
    int pages = 0;
    // 页面可绘制区域（设备坐标）。
    const QRect pageRect = painter.viewport();
    // 逐张图片处理。
    for (const QString& path : valid)
    {
        // 用 QImageReader 读取图片（它会自动识别格式并尊重 EXIF 方向）。
        QImageReader reader(path);
        // 开启自动旋转变换，避免手机照片方向颠倒。
        reader.setAutoTransform(true);
        // 读取图像。
        const QImage img = reader.read();
        // 读取失败则跳过该图片（不影响其它页）。
        if (img.isNull())
        {
            // 记录警告日志。
            qWarning("[doc] cannot read image, skipped: %s (%s)", qPrintable(path), qPrintable(reader.errorString()));
            // 继续下一张。
            continue;
        }
        // 非首页时另起一页。
        if (pages > 0)
        {
            // 新建页面。
            writer.newPage();
        }
        // 计算等比缩放后的目标矩形（居中并留出少量边距）。
        const double margin = pageRect.width() * 0.02;
        // 可用区域。
        const double availW = pageRect.width() - margin * 2;
        // 可用高度。
        const double availH = pageRect.height() - margin * 2;
        // 缩放比例（取宽高比例中较小者，保证完整显示）。
        const double ratio = qMin(availW / double(img.width()), availH / double(img.height()));
        // 绘制宽高。
        const double drawW = img.width() * ratio;
        // 绘制高度。
        const double drawH = img.height() * ratio;
        // 目标矩形（居中）。
        const QRectF target(pageRect.x() + (pageRect.width() - drawW) / 2.0,
                            pageRect.y() + (pageRect.height() - drawH) / 2.0,
                            drawW, drawH);
        // 绘制图片（缩放由 drawImage 的重载完成，内部使用平滑插值）。
        painter.drawImage(target, img);
        // 页数加一。
        ++pages;
    }
    // 结束绘制（必须调用，否则 PDF 可能不完整）。
    painter.end();
    // 一张都没画成功时返回失败。
    if (pages == 0)
    {
        // 错误文本。
        res.error = QStringLiteral("所有图片都无法解码。");
        // 返回。
        return res;
    }
    // 校验产物存在且非空，避免“看起来成功实则失败”。
    const QFileInfo outInfo(outPdf);
    // 文件必须存在且大小大于 0。
    if (!outInfo.exists() || outInfo.size() <= 0)
    {
        // 错误文本。
        res.error = QStringLiteral("PDF 文件未能正确写入。");
        // 返回。
        return res;
    }
    // 标记成功。
    res.ok = true;
    // 记录输出。
    res.outputs << outPdf;
    // 成功提示（英文）。
    res.message = QStringLiteral("已生成 %1 页的 PDF：%2").arg(pages).arg(outPdf);
    // 返回。
    return res;
}

// 文本 → PDF。
DocResult DocConvert::textToPdf(const QString& textPath, const QString& outPdf, const QString& title)
{
    // 结果对象。
    DocResult res;
    // 源文件必须存在。
    if (!QFileInfo::exists(textPath))
    {
        // 错误文本。
        res.error = QStringLiteral("文本文件不存在：%1").arg(textPath);
        // 返回。
        return res;
    }
    // 打开源文件。
    QFile in(textPath);
    // 只读模式打开。
    if (!in.open(QIODevice::ReadOnly))
    {
        // 错误文本。
        res.error = QStringLiteral("无法打开文本文件进行读取。");
        // 返回。
        return res;
    }
    // 读取全部字节。
    const QByteArray raw = in.readAll();
    // 关闭文件。
    in.close();
    // 解码文本：先尝试 UTF-8；若含替换字符则退回本地编码（兼容 GBK 环境的文本文件）。
    QString content = QString::fromUtf8(raw);
    // 判断是否需要回退：出现替换字符说明 UTF-8 解码不干净。
    if (content.contains(QChar(0xFFFD)))
    {
        // 用本地 8 位编码重新解码。
        content = QString::fromLocal8Bit(raw);
    }
    // 输出目录必须存在。
    const QString outDir = QFileInfo(outPdf).absolutePath();
    // 目录非空才创建。
    if (!outDir.isEmpty())
    {
        // 递归创建。
        QDir().mkpath(outDir);
    }
    // 创建 PDF 写入器。
    QPdfWriter writer(outPdf);
    // A4 纸张。
    writer.setPageSize(QPageSize(QPageSize::A4));
    // 分辨率 300 dpi（文本清晰且文件不大）。
    writer.setResolution(300);
    // 页边距 18 毫米（常规文档版心）。
    writer.setPageMargins(QMarginsF(18.0, 18.0, 18.0, 18.0), QPageLayout::Millimeter);
    // 文档标题元信息（为空时用输出文件名）。
    writer.setTitle(title.trimmed().isEmpty() ? QFileInfo(outPdf).completeBaseName() : title.trimmed());
    // 构造文本文档对象：用 QTextDocument 承载排版，它能处理换行、分页与字体度量。
    QTextDocument doc;
    // 正文字体：优先使用系统自带的等宽感较好的无衬线字体。
    QFont font(QStringLiteral("Segoe UI"));
    // 字号 11 点。
    font.setPointSize(11);
    // 设为文档默认字体。
    doc.setDefaultFont(font);
    // 组装正文：标题单独成段并加大字号。
    QString body;
    // 标题非空时加入标题行。
    if (!title.trimmed().isEmpty())
    {
        // 用 HTML 设置标题样式（大字号 + 加粗），这样无需额外的绘制代码。
        body += QStringLiteral("<h2>%1</h2>").arg(title.trimmed().toHtmlEscaped());
    }
    // 正文内容：转成 HTML 保留换行（\n → <br/>），避免所有文本挤成一段。
    body += QStringLiteral("<p>%1</p>").
        arg(content.toHtmlEscaped().replace(QLatin1Char('\n'), QStringLiteral("<br/>")));
    // 设置 HTML 内容。
    doc.setHtml(body);
    // 使用 QTextDocument 的打印能力写到 PDF：Qt 会自动完成分页。
    doc.print(&writer);
    // 校验产物。
    const QFileInfo outInfo(outPdf);
    // 文件必须存在且非空。
    if (!outInfo.exists() || outInfo.size() <= 0)
    {
        // 错误文本。
        res.error = QStringLiteral("PDF 文件未能正确写入。");
        // 返回。
        return res;
    }
    // 标记成功。
    res.ok = true;
    // 记录输出。
    res.outputs << outPdf;
    // 成功提示。
    res.message = QStringLiteral("文本已转换为 PDF：%1").arg(outPdf);
    // 返回。
    return res;
}

// PDF → 图片。
DocResult DocConvert::pdfToImages(const QString& pdfPath, const QString& outDir, const QString& baseName,
                                  int dpi, const QString& fmt, const QString& pageRange)
{
    // 结果对象。
    DocResult res;
    // 源文件必须存在。
    if (!QFileInfo::exists(pdfPath))
    {
        // 错误文本。
        res.error = QStringLiteral("源 PDF 不存在：%1").arg(pdfPath);
        // 返回。
        return res;
    }
    // 输出目录必须存在。
    if (!outDir.trimmed().isEmpty())
    {
        // 递归创建。
        QDir().mkpath(outDir);
    }
    // 归一化输出格式（只支持 PNG 与 JPG）。
    const QString ext = (fmt.trimmed().toLower() == QStringLiteral("jpg")
                            || fmt.trimmed().toLower() == QStringLiteral("jpeg"))
                            ? QStringLiteral("jpg")
                            : QStringLiteral("png");
    // 输出主名（为空时用源文件名）。
    const QString stem = baseName.trimmed().isEmpty()
                             ? QFileInfo(pdfPath).completeBaseName()
                             : baseName.trimmed();

#ifdef HAVE_QTPDF
    // ---- 已链接 Qt pdf 模块：使用 QPdfDocument 真实渲染 ----
    // 创建 PDF 文档对象。
    QPdfDocument doc;
    // 载入文件；返回错误码非 None 时说明打开失败。
    const QPdfDocument::Error err = doc.load(pdfPath);
    // 打开失败时返回明确原因。
    if (err != QPdfDocument::Error::None)
    {
        // 错误文本（把错误码转成可读文本由调用方界面负责，这里给出必要上下文）。
        res.error = QStringLiteral("打开 PDF 文档失败（错误码 %1）。").arg(int(err));
        // 返回。
        return res;
    }
    // 总页数。
    const int total = doc.pageCount();
    // 空文档无法导出。
    if (total <= 0)
    {
        // 错误文本。
        res.error = QStringLiteral("该 PDF 文档没有任何页面。");
        // 返回。
        return res;
    }
    // 解析要导出的页索引。
    const QList<int> pages = parsePageRange(pageRange, total);
    // 没有选中任何页时返回失败。
    if (pages.isEmpty())
    {
        // 错误文本。
        res.error = QStringLiteral("页码范围与该文档中的任何页面都不匹配。");
        // 返回。
        return res;
    }
    // 逐页渲染。
    for (const int pageIndex : pages)
    {
        // 取页面原始尺寸（点为单位，1 点 = 1/72 英寸）。
        const QSizeF pageSizePt = doc.pagePointSize(pageIndex);
        // 按目标 dpi 换算像素尺寸（dpi 限制在 36 ~ 600 之间，避免生成超大位图耗尽内存）。
        const double scale = qBound(36, dpi, 600) / 72.0;
        // 目标像素宽（至少 1 像素）。
        const int pxW = qMax(1, int(pageSizePt.width() * scale));
        // 目标像素高。
        const int pxH = qMax(1, int(pageSizePt.height() * scale));
        // 渲染为位图（QPdfDocument 内部使用 PDFium，渲染质量稳定）。
        const QImage img = doc.render(pageIndex, QSize(pxW, pxH));
        // 渲染失败则跳过该页。
        if (img.isNull())
        {
            // 记录警告。
            qWarning("[doc] failed to render PDF page %d", pageIndex + 1);
            // 继续下一页。
            continue;
        }
        // 输出文件路径（三位页码，保证排序稳定）。
        const QString outPath = QDir(outDir).filePath(
            QStringLiteral("%1_%2.%3").arg(stem).arg(pageIndex + 1, 3, 10, QLatin1Char('0')).arg(ext));
        // JPG 不支持透明通道，需要先转成不透明的 RGB 图像。
        QImage finalImage = img;
        // 需要转换时才转。
        if (ext == QStringLiteral("jpg") && img.hasAlphaChannel())
        {
            // 转换格式（转换后 alpha 通道会被丢弃并合成到黑底，这是 PDF 页面的正常表现）。
            finalImage = img.convertToFormat(QImage::Format_RGB32);
        }
        // 保存图片。
        if (!finalImage.save(outPath))
        {
            // 记录警告并跳过。
            qWarning("[doc] failed to save rendered page: %s", qPrintable(outPath));
            // 继续下一页。
            continue;
        }
        // 记录输出文件。
        res.outputs << outPath;
    }
    // 一页都没导出成功时返回失败。
    if (res.outputs.isEmpty())
    {
        // 错误文本。
        res.error = QStringLiteral("无法从该 PDF 渲染出任何页面。");
        // 返回。
        return res;
    }
    // 标记成功。
    res.ok = true;
    // 成功提示。
    res.message = QStringLiteral("已将 %2 页中的 %1 页导出为图片。").arg(res.outputs.size()).arg(total);
    // 返回。
    return res;
#else
    // ---- 未链接 Qt pdf 模块：如实报告不支持，绝不伪造能力 ----
    // 错误文本：明确指出这是构建能力限制，并给出替代建议。
    res.error = QStringLiteral("由于编译期缺少 Qt Pdf 模块，当前构建不支持 PDF 转图片。图片转 PDF 与文本转 PDF 仍然完全支持。");
    // 返回。
    return res;
#endif
}