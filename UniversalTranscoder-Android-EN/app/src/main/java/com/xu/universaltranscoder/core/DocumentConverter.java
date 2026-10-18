package com.xu.universaltranscoder.core;

// 引入 Context：打开 content:// 数据源需要上下文。

import android.content.Context;
// 引入 ContentResolver/ParcelFileDescriptor：PdfRenderer 需要一个可随机访问的文件描述符。
import android.content.ContentResolver;
import android.os.ParcelFileDescriptor;
// 引入 Uri：源地址。
import android.net.Uri;
// 引入 Bitmap：页面渲染与图片解码。
import android.graphics.Bitmap;
// 引入 BitmapFactory：解码图片。
import android.graphics.BitmapFactory;
// 引入 Canvas：在 PDF 页面上绘制内容。
import android.graphics.Canvas;
// 引入 Color：设置背景色。
import android.graphics.Color;
// 引入 Paint：绘制文字与图片。
import android.graphics.Paint;
// 引入 Rect/RectF：计算绘制区域。
import android.graphics.Rect;
import android.graphics.RectF;
// 引入 PdfDocument：创建 PDF。
import android.graphics.pdf.PdfDocument;
// 引入 PdfRenderer：渲染既有 PDF。
import android.graphics.pdf.PdfRenderer;

// 引入 ConvertOptions：读取页面策略与字号。
import com.xu.universaltranscoder.model.ConvertOptions;
// 引入 TargetSpec：确认操作类型。
import com.xu.universaltranscoder.model.TargetSpec;
// 引入 OutputTarget：输出位置抽象。
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 InputStream/OutputStream：读写文件内容。
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
// 引入 ArrayList：收集页码与文本行。
import java.util.ArrayList;
// 引入 List：列表类型。
import java.util.List;
// 引入 Locale：字符串处理。
import java.util.Locale;

/**
 * DocumentConverter 用 Android 自带的图形与 PDF API 完成文档类转换：
 * 1) 图片 -> 单个多页 PDF（PdfDocument）；
 * 2) PDF -> 逐页 PNG 图片（PdfRenderer）；
 * 3) 纯文本 -> PDF（PdfDocument 逐页排版绘制）。
 * <p>
 * 为什么全部使用系统 API：这三件事在 Android 上都有原生实现，无需引入任何第三方 PDF 库，
 * 既满足“离线、无第三方依赖”的要求，也避免了额外体积与授权问题。
 */
public final class DocumentConverter {

    // A4 纸张尺寸（单位为点，1 点 = 1/72 英寸）：595x842 是标准 A4。
    private static final float A4_WIDTH_POINTS = 595f;
    // A4 高度。
    private static final float A4_HEIGHT_POINTS = 842f;
    // 页面内边距（点）。
    private static final float PAGE_MARGIN_POINTS = 24f;
    // PDF 渲染为图片的放大倍数：2 倍约等于 144dpi，文字清晰且体积可控。
    private static final int PDF_RENDER_SCALE = 2;

    // 私有构造：工具类不允许实例化。
    private DocumentConverter() {
        // 空实现，禁止外部 new。
    }

    /**
     * 把一组图片合成一个 PDF。
     * 页面尺寸策略由 options.pdfAutoPageSize 决定：true 表示跟随图片比例，false 表示统一 A4。
     */
    public static String imagesToPdf(Context context, List<Uri> sources, ConvertOptions options,
                                     OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 输入为空时属于参数错误。
        if (sources == null || sources.isEmpty()) {
            // 抛出输入不匹配错误。
            throw new ConversionException(ErrorReason.INPUT_MISMATCH, "No image provided");
        }
        // 新建 PDF 文档对象。
        PdfDocument document = new PdfDocument();
        // 记录成功写入的页数。
        int pagesWritten = 0;
        // 总页数用于进度换算。
        int totalPages = sources.size();
        // 逐张图片处理。
        for (int i = 0; i < sources.size(); i++) {
            // 检查取消。
            if (callback.shouldCancel()) {
                // 关闭文档并抛出取消异常。
                document.close();
                // 抛出取消。
                throw new ConversionException(ErrorReason.CANCELED);
            }
            // 上报当前进度。
            callback.reportProgress(0.05 + 0.8 * i / (double) totalPages, 0.0,
                    "Rendering page " + (i + 1) + "/" + totalPages);
            // 解码当前图片（限制最大边长，避免超大图把 PDF 页面尺寸撑到不可用）。
            Bitmap bitmap = decodeForPdf(context, sources.get(i));
            // 解码失败时跳过该图片并继续，保证整体任务不会因为一张坏图而完全失败。
            if (bitmap == null) {
                // 记录日志说明跳过。
                com.xu.universaltranscoder.util.LogBus.info("Document", "Skipped an image that could not be decoded");
                // 继续下一张。
                continue;
            }
            try {
                // 计算页面尺寸（点）。
                int pageWidth;
                // 计算页面高度（点）。
                int pageHeight;
                // 跟随图片比例：把像素当点使用，但限制最大边长以控制文件体积。
                if (options.pdfAutoPageSize) {
                    // 页面宽度取图片宽度。
                    pageWidth = bitmap.getWidth();
                    // 页面高度取图片高度。
                    pageHeight = bitmap.getHeight();
                } else {
                    // 统一 A4 尺寸（取整为 int，PdfDocument 要求整数页尺寸）。
                    pageWidth = (int) A4_WIDTH_POINTS;
                    // A4 高度。
                    pageHeight = (int) A4_HEIGHT_POINTS;
                }
                // 构造页面信息。
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pagesWritten + 1).create();
                // 开始绘制该页。
                PdfDocument.Page page = document.startPage(pageInfo);
                // 取得页面画布。
                Canvas canvas = page.getCanvas();
                // 计算绘制目标矩形。
                RectF target;
                // 跟随图片比例时铺满整页。
                if (options.pdfAutoPageSize) {
                    // 铺满页面。
                    target = new RectF(0f, 0f, pageWidth, pageHeight);
                } else {
                    // A4 时按比例居中放缩，保留页边距。
                    float availableWidth = pageWidth - 2f * PAGE_MARGIN_POINTS;
                    // 可用高度。
                    float availableHeight = pageHeight - 2f * PAGE_MARGIN_POINTS;
                    // 计算等比缩放系数（取较小值保证完整放入）。
                    float scale = Math.min(availableWidth / bitmap.getWidth(), availableHeight / bitmap.getHeight());
                    // 计算绘制宽。
                    float drawWidth = bitmap.getWidth() * scale;
                    // 计算绘制高。
                    float drawHeight = bitmap.getHeight() * scale;
                    // 计算水平居中偏移。
                    float left = (pageWidth - drawWidth) / 2f;
                    // 计算垂直居中偏移。
                    float top = (pageHeight - drawHeight) / 2f;
                    // 构造目标矩形。
                    target = new RectF(left, top, left + drawWidth, top + drawHeight);
                }
                // 先铺白底，避免透明区域在部分阅读器里显示为黑色。
                canvas.drawColor(Color.WHITE);
                // 把图片绘制到页面。
                canvas.drawBitmap(bitmap, null, target, null);
                // 结束该页绘制。
                document.finishPage(page);
                // 页数递增。
                pagesWritten++;
            } finally {
                // 回收位图释放内存。
                if (!bitmap.isRecycled()) {
                    // 执行回收。
                    bitmap.recycle();
                }
            }
        }
        // 一页都没写成说明输入完全不可用。
        if (pagesWritten == 0) {
            // 关闭文档。
            document.close();
            // 抛出解码失败分类错误。
            throw new ConversionException(ErrorReason.DECODE_FAILED, "None of the images could be decoded");
        }
        // 上报写出进度。
        callback.reportProgress(0.9, 0.0, "Writing PDF");
        // 打开输出流并写出 PDF。
        OutputStream out = null;
        try {
            // 打开输出。
            out = output.openStream();
            // 写出文档。
            document.writeTo(out);
            // 刷新缓冲。
            out.flush();
        } catch (java.io.IOException error) {
            // IO 失败分类上报。
            throw classifyIoError(error);
        } finally {
            // 关闭输出流。
            closeQuietly(out);
            // 关闭文档释放资源。
            document.close();
        }
        // 上报完成。
        callback.reportProgress(1.0, 0.0, "Saved " + output.fileName() + " (" + pagesWritten + " pages)");
        // 返回输出路径。
        return output.displayPath();
    }

    /**
     * 把 PDF 逐页渲染为 PNG 图片。
     * 页码范围由 options.pageRange 决定，支持 "1-3,5" 这类表达式；为空表示全部页。
     */
    public static String pdfToImages(Context context, Uri sourceUri, ConvertOptions options,
                                     OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 取得内容解析器。
        ContentResolver resolver = context.getContentResolver();
        // 声明文件描述符，PdfRenderer 必须通过它打开文档。
        ParcelFileDescriptor pfd = null;
        // 声明渲染器。
        PdfRenderer renderer = null;
        // 记录成功导出的页数。
        int exported = 0;
        try {
            // 以只读模式打开 PDF。
            pfd = resolver.openFileDescriptor(sourceUri, "r");
            // 打开失败说明文件不可读。
            if (pfd == null) {
                // 抛出权限分类错误。
                throw new ConversionException(ErrorReason.PERMISSION_DENIED, "Cannot open PDF file descriptor");
            }
            // 创建渲染器。
            try {
                // 构造 PdfRenderer。
                renderer = new PdfRenderer(pfd);
            } catch (java.io.IOException error) {
                // 文档不是合法 PDF 时归类为输入不匹配。
                throw new ConversionException(ErrorReason.INPUT_MISMATCH, error);
            }
            // 取总页数。
            int pageCount = renderer.getPageCount();
            // 页数为 0 说明 PDF 没有内容。
            if (pageCount <= 0) {
                // 抛出输入不匹配错误。
                throw new ConversionException(ErrorReason.INPUT_MISMATCH, "The PDF contains no pages");
            }
            // 解析用户指定的页码集合。
            List<Integer> pages = parsePageRange(options.pageRange, pageCount);
            // 页码集合为空说明表达式非法。
            if (pages.isEmpty()) {
                // 抛出参数错误。
                throw new ConversionException(ErrorReason.INVALID_PARAMETER, "Page range matches no page: '" + options.pageRange + "'");
            }
            // 逐页渲染。
            for (int i = 0; i < pages.size(); i++) {
                // 检查取消。
                if (callback.shouldCancel()) {
                    // 抛出取消异常。
                    throw new ConversionException(ErrorReason.CANCELED);
                }
                // 页码从 0 开始。
                int pageIndex = pages.get(i);
                // 上报进度。
                callback.reportProgress(0.05 + 0.9 * i / (double) pages.size(), 0.0,
                        "Rendering page " + (pageIndex + 1) + "/" + pageCount);
                // 打开该页。
                PdfRenderer.Page page = renderer.openPage(pageIndex);
                // 页面位图引用。
                Bitmap bitmap = null;
                try {
                    // 按放大倍数计算输出尺寸（点 -> 像素）。
                    int width = page.getWidth() * PDF_RENDER_SCALE;
                    // 计算高度。
                    int height = page.getHeight() * PDF_RENDER_SCALE;
                    // 分配 ARGB 位图，PdfRenderer 的渲染模式要求该格式。
                    bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
                    // 先铺白底：PDF 页面本身可能透明，白底更接近纸质阅读效果。
                    bitmap.eraseColor(Color.WHITE);
                    // 执行渲染。
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    // 为该页派生一个输出文件，命名带上页码便于识别。
                    OutputTarget pageTarget = output.sibling(output.fileName().replaceAll("\\.[^.]+$", "") + "_p" + (pageIndex + 1),
                            "png", "image/png");
                    // 打开输出流。
                    OutputStream out = null;
                    try {
                        // 打开目标文档。
                        out = pageTarget.openStream();
                        // 以 PNG 无损失写出。
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        // 刷新。
                        out.flush();
                    } finally {
                        // 关闭输出流。
                        closeQuietly(out);
                    }
                    // 导出计数递增。
                    exported++;
                    // 记录本页输出路径，便于在队列详情里查看。
                    callback.reportProgress(0.05 + 0.9 * (i + 1) / (double) pages.size(), 0.0, "Saved " + pageTarget.fileName());
                } finally {
                    // 关闭页面。
                    page.close();
                    // 回收位图。
                    if (bitmap != null && !bitmap.isRecycled()) {
                        // 执行回收。
                        bitmap.recycle();
                    }
                }
            }
            // 上报完成。
            callback.reportProgress(1.0, 0.0, "Exported " + exported + " page(s)");
            // 返回输出目录描述与首个文件名，便于日志展示。
            return output.describeDirectory() + "/" + output.fileName().replaceAll("\\.[^.]+$", "") + "_p*.png";
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (SecurityException error) {
            // 权限问题分类上报。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (java.io.IOException error) {
            // IO 失败细分。
            throw classifyIoError(error);
        } finally {
            // 关闭渲染器。
            if (renderer != null) {
                // 关闭并忽略异常。
                try {
                    // 关闭渲染器。
                    renderer.close();
                } catch (Throwable ignored) {
                    // 忽略。
                }
            }
            // 关闭文件描述符。
            if (pfd != null) {
                // 关闭并忽略异常。
                try {
                    // 关闭描述符。
                    pfd.close();
                } catch (Throwable ignored) {
                    // 忽略。
                }
            }
        }
    }

    /**
     * 把纯文本文件排版成 PDF。
     * 排版规则：A4 页面、可配置字号、按可用宽度自动换行、按可用高度自动分页。
     */
    public static String textToPdf(Context context, Uri sourceUri, ConvertOptions options,
                                   OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 上报开始读取文本。
        callback.reportProgress(0.05, 0.0, "Reading text");
        // 读取文本内容（限制最大长度，避免把超大日志文件一次性读入内存）。
        StringBuilder textBuilder = new StringBuilder();
        // 输入流引用。
        InputStream input = null;
        // 使用 UTF-8 读取，兼容中文等多字节内容。
        BufferedReader reader = null;
        try {
            // 打开源文件。
            input = context.getContentResolver().openInputStream(sourceUri);
            // 打开失败说明不可读。
            if (input == null) {
                // 抛出权限分类错误。
                throw new ConversionException(ErrorReason.PERMISSION_DENIED, "Cannot open text file");
            }
            // 包装为字符流。
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            // 逐行读取。
            String line;
            // 统计已读字符数。
            int totalChars = 0;
            // 循环读取直到结束。
            while ((line = reader.readLine()) != null) {
                // 累计字符数。
                totalChars += line.length() + 1;
                // 超过上限（约 4MB 文本）时停止，避免内存与页数失控。
                if (totalChars > 4 * 1024 * 1024) {
                    // 记录日志提示已截断。
                    com.xu.universaltranscoder.util.LogBus.info("Document", "Text truncated at 4 MB to protect memory");
                    // 停止读取。
                    break;
                }
                // 追加该行并补回换行。
                textBuilder.append(line).append('\n');
            }
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (java.io.IOException error) {
            // IO 失败细分。
            throw classifyIoError(error);
        } finally {
            // 关闭字符流。
            closeQuietly(reader);
            // 关闭输入流。
            closeQuietly(input);
        }
        // 构造文字画笔。
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // 设置字号（点）。
        paint.setTextSize(Math.max(6, Math.min(48, options.pdfTextSize)));
        // 使用黑色文字。
        paint.setColor(Color.BLACK);
        // 设计等宽字体更接近“文本文件”的观感。
        paint.setTypeface(android.graphics.Typeface.MONOSPACE);
        // 计算可用宽度。
        float availableWidth = A4_WIDTH_POINTS - 2f * PAGE_MARGIN_POINTS;
        // 计算行高（1.4 倍字号，阅读更舒适）。
        float lineHeight = paint.getTextSize() * 1.4f;
        // 计算可用高度。
        float availableHeight = A4_HEIGHT_POINTS - 2f * PAGE_MARGIN_POINTS;
        // 计算每页可容纳行数。
        int linesPerPage = Math.max(1, (int) Math.floor(availableHeight / lineHeight));
        // 逐行做宽度换行，得到实际要绘制的行列表。
        List<String> wrapped = new ArrayList<>();
        // 按行切分原文。
        String[] rawLines = textBuilder.toString().split("\n", -1);
        // 遍历原始行。
        for (String rawLine : rawLines) {
            // 空行直接保留，用于体现段落间隔。
            if (rawLine.isEmpty()) {
                // 加入空行。
                wrapped.add("");
                // 继续下一行。
                continue;
            }
            // 剩余待处理文本。
            String remaining = rawLine;
            // 循环按宽度切分。
            while (!remaining.isEmpty()) {
                // 计算当前宽度下能放置的字符数。
                int count = paint.breakText(remaining, true, availableWidth, null);
                // 至少保证切出一个字符，避免极端情况下死循环。
                if (count <= 0) {
                    // 强制切一个字符。
                    count = 1;
                }
                // 取出这一段。
                wrapped.add(remaining.substring(0, count));
                // 更新剩余文本。
                remaining = remaining.substring(count);
            }
        }
        // 新建 PDF 文档。
        PdfDocument document = new PdfDocument();
        // 计算总页数。
        int pageCount = Math.max(1, (int) Math.ceil(wrapped.size() / (double) linesPerPage));
        try {
            // 逐页绘制。
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                // 检查取消。
                if (callback.shouldCancel()) {
                    // 抛出取消异常（finally 中会关闭文档）。
                    throw new ConversionException(ErrorReason.CANCELED);
                }
                // 上报排版进度。
                callback.reportProgress(0.05 + 0.85 * pageIndex / (double) pageCount, 0.0,
                        "Laying out page " + (pageIndex + 1) + "/" + pageCount);
                // 构造页面信息。
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder((int) A4_WIDTH_POINTS, (int) A4_HEIGHT_POINTS, pageIndex + 1).create();
                // 开始绘制。
                PdfDocument.Page page = document.startPage(pageInfo);
                // 取画布。
                Canvas canvas = page.getCanvas();
                // 铺白底。
                canvas.drawColor(Color.WHITE);
                // 计算本页行区间。
                int startLine = pageIndex * linesPerPage;
                // 计算结束行（不含）。
                int endLine = Math.min(wrapped.size(), startLine + linesPerPage);
                // 逐行绘制。
                for (int lineIndex = startLine; lineIndex < endLine; lineIndex++) {
                    // 计算基线 y（第 0 行也要留出上边距）。
                    float baselineY = PAGE_MARGIN_POINTS + paint.getTextSize() + (lineIndex - startLine) * lineHeight;
                    // 绘制该行文本。
                    canvas.drawText(wrapped.get(lineIndex), PAGE_MARGIN_POINTS, baselineY, paint);
                }
                // 结束该页。
                document.finishPage(page);
            }
            // 上报写出进度。
            callback.reportProgress(0.95, 0.0, "Writing PDF");
            // 打开输出流。
            OutputStream out = null;
            try {
                // 打开目标。
                out = output.openStream();
                // 写出 PDF。
                document.writeTo(out);
                // 刷新。
                out.flush();
            } finally {
                // 关闭输出流。
                closeQuietly(out);
            }
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (java.io.IOException error) {
            // IO 失败细分。
            throw classifyIoError(error);
        } finally {
            // 关闭文档。
            document.close();
        }
        // 上报完成。
        callback.reportProgress(1.0, 0.0, "Saved " + output.fileName() + " (" + pageCount + " pages)");
        // 返回输出路径。
        return output.displayPath();
    }

    /**
     * 为 PDF 合成解码一张图片，限制最大边长以避免超大页面。
     */
    private static Bitmap decodeForPdf(Context context, Uri uri) {
        // 先读取边界。
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        // 只取边界。
        bounds.inJustDecodeBounds = true;
        // 边界流。
        InputStream stream = null;
        try {
            // 打开源。
            stream = context.getContentResolver().openInputStream(uri);
            // 打开失败返回 null。
            if (stream == null) {
                // 返回 null 由上层跳过。
                return null;
            }
            // 解析边界。
            BitmapFactory.decodeStream(stream, null, bounds);
        } catch (Throwable error) {
            // 任何异常都返回 null，避免单张图片导致整体失败。
            return null;
        } finally {
            // 关闭边界流。
            closeQuietly(stream);
        }
        // 边界非法时返回 null。
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // 返回 null。
            return null;
        }
        // 计算采样率：把最长边限制在 2400 点以内，既清晰又不会产生超大 PDF。
        BitmapFactory.Options decode = new BitmapFactory.Options();
        // 使用 ARGB_8888，保证 PDF 里颜色与透明信息完整。
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // 计算采样率。
        decode.inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, 2400, 2400);
        // 解码流。
        InputStream decodeStream = null;
        try {
            // 重新打开源。
            decodeStream = context.getContentResolver().openInputStream(uri);
            // 打开失败返回 null。
            if (decodeStream == null) {
                // 返回 null。
                return null;
            }
            // 执行解码。
            return BitmapFactory.decodeStream(decodeStream, null, decode);
        } catch (Throwable error) {
            // 解码异常返回 null。
            return null;
        } finally {
            // 关闭解码流。
            closeQuietly(decodeStream);
        }
    }

    /**
     * 计算 2 的幂次采样率。
     */
    private static int computeSampleSize(int width, int height, int limitWidth, int limitHeight) {
        // 采样率从 1 开始。
        int sampleSize = 1;
        // 循环直到满足限制。
        while (true) {
            // 按当前采样率估算尺寸。
            int sampleWidth = (width + sampleSize - 1) / sampleSize;
            // 估算高度。
            int sampleHeight = (height + sampleSize - 1) / sampleSize;
            // 满足限制则停止。
            if (sampleWidth <= limitWidth && sampleHeight <= limitHeight) {
                // 跳出。
                break;
            }
            // 采样率翻倍。
            sampleSize *= 2;
            // 上限保护。
            if (sampleSize > 32) {
                // 跳出。
                break;
            }
        }
        // 返回采样率。
        return sampleSize;
    }

    /**
     * 解析页码表达式（1 基），返回 0 基页码列表。
     */
    private static List<Integer> parsePageRange(String expression, int pageCount) throws ConversionException {
        // 结果列表。
        List<Integer> result = new ArrayList<>();
        // 表达式为空表示全部页。
        if (expression == null || expression.trim().isEmpty()) {
            // 逐页加入。
            for (int i = 0; i < pageCount; i++) {
                // 加入页码。
                result.add(i);
            }
            // 返回全部页。
            return result;
        }
        // 按逗号切分片段。
        String[] parts = expression.split(",");
        // 遍历片段。
        for (String part : parts) {
            // 去掉空白。
            String trimmed = part.trim();
            // 跳过空片段。
            if (trimmed.isEmpty()) {
                // 继续下一段。
                continue;
            }
            // 处理 “起始-结束” 形式。
            if (trimmed.contains("-")) {
                // 切分区间端点。
                String[] ends = trimmed.split("-");
                // 端点数量不是 2 时表达式非法。
                if (ends.length != 2) {
                    // 抛出参数错误。
                    throw new ConversionException(ErrorReason.INVALID_PARAMETER, "Bad page range segment: " + trimmed);
                }
                // 解析起始页。
                int from = parseIntSafe(ends[0].trim());
                // 解析结束页。
                int to = parseIntSafe(ends[1].trim());
                // 解析失败时抛错。
                if (from <= 0 || to <= 0) {
                    // 抛出参数错误。
                    throw new ConversionException(ErrorReason.INVALID_PARAMETER, "Bad page range segment: " + trimmed);
                }
                // 保证从小到大。
                int start = Math.min(from, to);
                // 上界。
                int end = Math.max(from, to);
                // 逐页加入（并裁剪到文档页码范围）。
                for (int page = start; page <= end; page++) {
                    // 在范围内时才加入。
                    if (page >= 1 && page <= pageCount) {
                        // 加入 0 基页码并去重。
                        if (!result.contains(page - 1)) {
                            // 记录页码。
                            result.add(page - 1);
                        }
                    }
                }
            } else {
                // 处理单页形式。
                int page = parseIntSafe(trimmed);
                // 在范围内时才加入。
                if (page >= 1 && page <= pageCount && !result.contains(page - 1)) {
                    // 记录页码。
                    result.add(page - 1);
                }
            }
        }
        // 返回解析结果。
        return result;
    }

    /**
     * 安全解析整数，失败返回 -1。
     */
    private static int parseIntSafe(String value) {
        // 尝试解析。
        try {
            // 返回解析结果。
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // 解析失败返回 -1，由调用方判定为非法输入。
            return -1;
        }
    }

    /**
     * 安静地关闭资源。
     */
    private static void closeQuietly(java.io.Closeable closeable) {
        // 空值直接返回。
        if (closeable == null) {
            // 无操作。
            return;
        }
        // 关闭并忽略异常。
        try {
            // 执行关闭。
            closeable.close();
        } catch (Throwable ignored) {
            // 忽略关闭异常。
        }
    }

    /**
     * 把 IO 异常细分为空间不足、权限与一般 IO 错误。
     */
    private static ConversionException classifyIoError(java.io.IOException error) {
        // 取消息文本。
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
        // 空间不足。
        if (message.contains("enospc") || message.contains("no space") || message.contains("insufficient")) {
            // 返回空间不足分类。
            return new ConversionException(ErrorReason.STORAGE_FULL, error);
        }
        // 权限问题。
        if (message.contains("permission") || message.contains("eacces")) {
            // 返回权限分类。
            return new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        }
        // 其它。
        return new ConversionException(ErrorReason.IO_ERROR, error);
    }
}
