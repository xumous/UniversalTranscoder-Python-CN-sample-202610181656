package com.xu.universaltranscoder.doc;

// 引入 BufferedImage，作为图像处理流程中的内存位图类型

import java.awt.image.BufferedImage;
// 引入 Graphics2D，用于把带透明通道的图片压到白色背景上
import java.awt.Graphics2D;
// 引入 RenderingHints，设置高质量绘制提示
import java.awt.RenderingHints;
// 引入 ByteArrayOutputStream，在内存中累积 PDF 字节，最后一次性落盘
import java.io.ByteArrayOutputStream;
// 引入 IOException，处理文件读写异常
import java.io.IOException;
// 引入 Files，用于写出 PDF 文件
import java.nio.file.Files;
// 引入 Path，表示输出 PDF 路径
import java.nio.file.Path;
// 引入 ArrayList，收集分页后的内容流
import java.util.ArrayList;
// 引入 List 接口，作为分页内容与图片清单的类型
import java.util.List;
// 引入 StandardCharsets，显式指定文本编码
import java.nio.charset.StandardCharsets;
// 引入 ImageIO，使用 JDK 标准图像解码与编码能力
import javax.imageio.ImageIO;

// 关键字 class：PdfMaker 用最精简的 PDF 语法手写生成 PDF 文件，不依赖任何第三方库
// 之所以手写：PDF 的基础对象结构稳定且简单，手写可以避免为“文本转 PDF、图片转 PDF”引入额外依赖
public final class PdfMaker {

    // A4 页面宽度（点），595 点约等于 210 毫米
    private static final double PAGE_WIDTH = 595d;
    // A4 页面高度（点），842 点约等于 297 毫米
    private static final double PAGE_HEIGHT = 842d;
    // 页面左边距（点）
    private static final double MARGIN_LEFT = 46d;
    // 页面上边距（点）
    private static final double MARGIN_TOP = 60d;
    // 页面下边距（点）
    private static final double MARGIN_BOTTOM = 56d;
    // 正文字号（点）
    private static final double BODY_FONT_SIZE = 11d;
    // 行高（点），比字号略大以保证可读性
    private static final double LINE_HEIGHT = 15d;

    // 私有构造方法，禁止实例化这个纯静态生成器
    private PdfMaker() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 把纯文本写入 PDF 文件，自动按行分页
    public static void textToPdf(String text, Path output) throws IOException {
        // 文本为空时按单个空串处理，保证仍能生成有效 PDF
        String content = text == null ? "" : text;
        // 统一换行符，先处理 Windows 的 \r\n
        content = content.replace("\r\n", "\n");
        // 再处理遗留的单独回车字符
        content = content.replace('\r', '\n');
        // 按换行切分成行数组
        String[] lines = content.split("\n", -1);
        // 计算去除上下边距后每页可容纳的最大行数
        int linesPerPage = (int) Math.floor((PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM) / LINE_HEIGHT);
        // 每页至少容纳一行，避免极端参数下除以零
        linesPerPage = Math.max(linesPerPage, 1);
        // 准备分页后的内容流集合
        List<String> pageContents = new ArrayList<>();
        // 当前页的文本构建器
        StringBuilder page = new StringBuilder();
        // 已写入当前页的行数
        int lineCount = 0;
        // 逐行写入
        for (String line : lines) {
            // 当前页已满时先落盘当前页并开启新页
            if (lineCount >= linesPerPage) {
                // 保存当前页内容
                pageContents.add(page.toString());
                // 重置页面构建器
                page = new StringBuilder();
                // 重置行计数
                lineCount = 0;
            }
            // 使用 PDF 文本绘制语法追加一行：设置字体、定位、写字、换行
            page.append("BT /F1 ").append(formatNumber(BODY_FONT_SIZE)).append(" Tf ")
                    // 定位到当前行的坐标，y 轴从下往上计算，因此用页高减行号乘行高
                    .append(formatNumber(MARGIN_LEFT)).append(' ')
                    // 计算该行的纵坐标
                    .append(formatNumber(PAGE_HEIGHT - MARGIN_TOP - (lineCount + 1) * LINE_HEIGHT))
                    // 追加文本显示指令的起始标记
                    .append(" Td (").append(escapePdfText(line)).append(") Tj ET\n");
            // 行计数加一
            lineCount++;
        }
        // 追加最后一页内容，即使为空也保证至少有一页
        pageContents.add(page.toString());
        // 写入 PDF 文件
        writePdf(pageContents, output);
    }

    // 把若干图片写入 PDF 文件，每张图片独立占一页，按比例缩放到页面内
    public static void imagesToPdf(List<Path> images, Path output) throws IOException {
        // 准备分页内容流集合
        List<String> pageContents = new ArrayList<>();
        // 准备每页需要嵌入的图像对象字节，顺序与内容流一一对应
        List<byte[]> embeddedImages = new ArrayList<>();
        // 准备每页图像的像素宽高清单，用于内容流计算显示尺寸
        List<int[]> imageSizes = new ArrayList<>();
        // 逐张处理图片
        for (Path imagePath : images) {
            // 使用 JDK 标准解码器读取图片，覆盖常见格式
            BufferedImage image = ImageIO.read(imagePath.toFile());
            // 读取失败时跳过该文件，保证批量任务不会因单张损坏图片整体失败
            if (image == null) {
                // 继续处理下一张
                continue;
            }
            // 把图片压到白色背景上并编码为 JPEG 字节，JPEG 可以直接作为 PDF 的 DCTDecode 数据流
            byte[] jpegBytes = encodeJpeg(image);
            // 编码失败时跳过该文件
            if (jpegBytes == null || jpegBytes.length == 0) {
                // 继续处理下一张
                continue;
            }
            // 计算图片在页面内的显示尺寸，保持原始宽高比
            double[] display = fitIntoPage(image.getWidth(), image.getHeight());
            // 记录显示宽高
            int width = image.getWidth();
            // 记录像素高度
            int height = image.getHeight();
            // 记录图像字节
            embeddedImages.add(jpegBytes);
            // 记录像素尺寸
            imageSizes.add(new int[]{width, height});
            // 构造该页的内容流：把图像按显示尺寸绘制到页面正中
            StringBuilder content = new StringBuilder();
            // 保存绘图状态
            content.append("q\n");
            // 设置变换矩阵，把 1x1 的单位图像缩放并平移到页面中的位置
            content.append(formatNumber(display[2])).append(" 0 0 ").append(formatNumber(display[3]))
                    // 追加平移量，x 轴居中，y 轴居中
                    .append(' ').append(formatNumber(display[0])).append(' ').append(formatNumber(display[1]))
                    // 结束矩阵设置
                    .append(" cm\n");
            // 绘制图像对象
            content.append("/Im0 Do\n");
            // 恢复绘图状态
            content.append("Q\n");
            // 保存该页内容流
            pageContents.add(content.toString());
        }
        // 使用带图像的分支写出 PDF
        writePdfWithImages(pageContents, embeddedImages, imageSizes, output);
    }

    // 把图片编码为 JPEG 字节，带透明通道的图片会先压到白色背景
    private static byte[] encodeJpeg(BufferedImage source) {
        // 判断图片是否带透明通道，JPEG 不支持透明需要先处理
        boolean hasAlpha = source.getColorModel().hasAlpha();
        // 需要处理的图片先转换到不带透明的 RGB 缓冲
        BufferedImage rgbImage = hasAlpha ? flattenAlpha(source) : toRgb(source);
        // 使用内存流接收编码结果
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // 捕获编码异常
        try {
            // 调用 JDK 标准 JPEG 编码器写出字节
            boolean written = ImageIO.write(rgbImage, "jpg", buffer);
            // 编码器缺失或写失败时返回 null
            if (!written) {
                // 返回 null 表示编码失败
                return null;
            }
        } catch (IOException ex) {
            // 编码异常时返回 null，交由上层跳过该图片
            return null;
        }
        // 返回编码后的字节数组
        return buffer.toByteArray();
    }

    // 把带透明通道的图片压到白色背景上
    private static BufferedImage flattenAlpha(BufferedImage source) {
        // 创建不带透明通道的 RGB 缓冲
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        // 取得绘制上下文
        Graphics2D graphics = target.createGraphics();
        // 把背景填充为白色，透明区域会显示为白底
        graphics.setColor(java.awt.Color.WHITE);
        // 填满整个画布
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        // 设置高质量插值，保证缩放与合成清晰
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // 把源图画到白底之上
        graphics.drawImage(source, 0, 0, null);
        // 释放绘图上下文资源
        graphics.dispose();
        // 返回处理后的图片
        return target;
    }

    // 把图片转换为不含透明通道的 RGB 类型，避免 JPEG 编码器拒绝带 alpha 的输入
    private static BufferedImage toRgb(BufferedImage source) {
        // 已经是 RGB 类型时直接返回，省去一次绘制
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            // 返回原图
            return source;
        }
        // 创建新的 RGB 缓冲
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        // 取得绘制上下文
        Graphics2D graphics = target.createGraphics();
        // 填白底，保证与透明处理保持一致
        graphics.setColor(java.awt.Color.WHITE);
        // 填满画布
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        // 绘制源图
        graphics.drawImage(source, 0, 0, null);
        // 释放绘图上下文
        graphics.dispose();
        // 返回转换后的图片
        return target;
    }

    // 计算图片在页面内的显示位置与尺寸，返回数组依次为 x 偏移、y 偏移、显示宽、显示高
    private static double[] fitIntoPage(int pixelWidth, int pixelHeight) {
        // 可用绘制区域宽度
        double availableWidth = PAGE_WIDTH - MARGIN_LEFT * 2d;
        // 可用绘制区域高度
        double availableHeight = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;
        // 计算宽度与高度两个方向的缩放比例，取较小者以保证整张图片可见
        double scale = Math.min(availableWidth / Math.max(pixelWidth, 1), availableHeight / Math.max(pixelHeight, 1));
        // 计算缩放后的显示宽度
        double displayWidth = pixelWidth * scale;
        // 计算缩放后的显示高度
        double displayHeight = pixelHeight * scale;
        // 计算水平居中偏移
        double offsetX = (PAGE_WIDTH - displayWidth) / 2d;
        // 计算垂直居中偏移
        double offsetY = (PAGE_HEIGHT - displayHeight) / 2d;
        // 返回位置与尺寸
        return new double[]{offsetX, offsetY, displayWidth, displayHeight};
    }

    // 写出不含图像的 PDF 文件
    private static void writePdf(List<String> pageContents, Path output) throws IOException {
        // 使用字节缓冲累积 PDF 内容
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // 对象编号从 1 开始，依次为 Catalog、Pages、Font、内容流与页面对象
        List<Integer> offsets = new ArrayList<>();
        // 预置哨兵元素，使列表下标与对象编号一一对应
        offsets.add(0);
        // 写入 PDF 头部
        writeAscii(buffer, "%PDF-1.4\n");
        // 记录 1 号对象的偏移
        offsets.add(buffer.size());
        // 写入文档目录对象，指向页面树
        writeAscii(buffer, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        // 记录 2 号对象的偏移
        offsets.add(buffer.size());
        // 构造页面树对象，Kids 引用全部页面对象
        StringBuilder kids = new StringBuilder();
        // 页面对象编号从 5 开始，前四个编号被目录、页面树与字体占用
        int firstPageObject = 5;
        // 逐个收集页面对象引用
        for (int i = 0; i < pageContents.size(); i++) {
            // 非首个引用时加空格分隔
            if (kids.length() > 0) {
                // 追加分隔空格
                kids.append(' ');
            }
            // 追加页面对象引用
            kids.append(firstPageObject + i).append(" 0 R");
        }
        // 写入页面树对象
        writeAscii(buffer, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pageContents.size() + " >>\nendobj\n");
        // 记录 3 号对象偏移
        offsets.add(buffer.size());
        // 写入内置字体对象，Helvetica 是 PDF 标准 14 号字体之一，无需嵌入字库
        writeAscii(buffer, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");
        // 记录 4 号对象偏移
        offsets.add(buffer.size());
        // 写入页面资源对象，引用字体
        writeAscii(buffer, "4 0 obj\n<< /Font << /F1 3 0 R >> >>\nendobj\n");
        // 逐个写入页面对象与其内容流
        for (int i = 0; i < pageContents.size(); i++) {
            // 页面内容流的字节形式
            byte[] contentBytes = pageContents.get(i).getBytes(StandardCharsets.US_ASCII);
            // 记录页面对象偏移
            offsets.add(buffer.size());
            // 得到当前页面对应的内容流对象编号
            int contentObject = firstPageObject + pageContents.size() + i;
            // 写入页面对象，引用资源对象与内容流对象
            writeAscii(buffer, (firstPageObject + i) + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    // 追加页面宽高
                    + formatNumber(PAGE_WIDTH) + " " + formatNumber(PAGE_HEIGHT)
                    // 引用资源对象
                    + "] /Resources 4 0 R /Contents " + contentObject + " 0 R >>\nendobj\n");
        }
        // 逐个写入内容流对象
        for (int i = 0; i < pageContents.size(); i++) {
            // 页面内容流的字节形式
            byte[] contentBytes = pageContents.get(i).getBytes(StandardCharsets.US_ASCII);
            // 记录内容流对象偏移
            offsets.add(buffer.size());
            // 写入内容流对象头，声明流长度
            writeAscii(buffer, (firstPageObject + pageContents.size() + i) + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            // 写入流数据
            buffer.write(contentBytes, 0, contentBytes.length);
            // 写入流结束标记
            writeAscii(buffer, "\nendstream\nendobj\n");
        }
        // 计算交叉引用表起始位置
        int xrefOffset = buffer.size();
        // 写入交叉引用表
        writeXref(buffer, offsets);
        // 写入 trailer 与结尾标记
        writeAscii(buffer, "trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");
        // 确保输出目录存在
        ensureParent(output);
        // 把字节缓冲一次性写入文件，避免半截文件
        Files.write(output, buffer.toByteArray());
    }

    // 写出包含图像的 PDF 文件，每页嵌入一张 JPEG 图像
    private static void writePdfWithImages(List<String> pageContents, List<byte[]> embeddedImages,
                                           List<int[]> imageSizes, Path output) throws IOException {
        // 页数为内容流数量，若为空则至少生成一页空白页
        int pageCount = Math.max(pageContents.size(), 1);
        // 使用字节缓冲累积 PDF 内容
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // 对象偏移表，索引与对象编号对应
        List<Integer> offsets = new ArrayList<>();
        // 预置哨兵元素，使下标 1 对应 1 号对象
        offsets.add(0);
        // 写入 PDF 头部
        writeAscii(buffer, "%PDF-1.4\n");
        // 记录 1 号对象偏移
        offsets.add(buffer.size());
        // 写入文档目录对象
        writeAscii(buffer, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        // 记录 2 号对象偏移
        offsets.add(buffer.size());
        // 构造页面引用串，页面对象编号从 3 开始
        StringBuilder kids = new StringBuilder();
        // 逐页构造引用
        for (int i = 0; i < pageCount; i++) {
            // 非首个引用时加空格
            if (kids.length() > 0) {
                // 追加分隔空格
                kids.append(' ');
            }
            // 追加页面对象引用
            kids.append((3 + i)).append(" 0 R");
        }
        // 写入页面树对象
        writeAscii(buffer, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>\nendobj\n");
        // 为每页写入页面对象与内容流对象，再写入图像对象
        for (int i = 0; i < pageCount; i++) {
            // 页面对象编号
            int pageObject = 3 + i;
            // 记录页面对象偏移
            offsets.add(buffer.size());
            // 写入页面对象，声明 XObject 资源以便引用图像；对象编号必须与文件中的物理写入顺序一致
            writeAscii(buffer, pageObject + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    // 追加页面宽高
                    + formatNumber(PAGE_WIDTH) + " " + formatNumber(PAGE_HEIGHT)
                    // 声明图像资源名 Im0，其对象编号排在全部内容流之后
                    + "] /Resources << /XObject << /Im0 " + (3 + pageCount * 2 + i) + " 0 R >> >> /Contents "
                    // 内容流对象编号紧跟页面对象之后
                    + (3 + pageCount + i) + " 0 R >>\nendobj\n");
        }
        // 为每页写入内容流对象，编号紧随页面对象，保证编号顺序与物理顺序一致
        for (int i = 0; i < pageCount; i++) {
            // 取该页内容流文本，缺页时使用空串
            String content = i < pageContents.size() ? pageContents.get(i) : "";
            // 转为字节
            byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);
            // 记录内容流对象偏移
            offsets.add(buffer.size());
            // 写入内容流对象头
            writeAscii(buffer, (3 + pageCount + i) + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            // 写入流数据
            buffer.write(contentBytes, 0, contentBytes.length);
            // 写入流结束标记
            writeAscii(buffer, "\nendstream\nendobj\n");
        }
        // 为每页写入图像 XObject，使用 DCTDecode 直接嵌入 JPEG 数据，无需解码重编码
        for (int i = 0; i < pageCount; i++) {
            // 取该页图像字节，缺页时使用空数组
            byte[] imageBytes = i < embeddedImages.size() ? embeddedImages.get(i) : new byte[0];
            // 取该页图像像素尺寸，缺页时使用 1x1 兜底尺寸
            int[] size = i < imageSizes.size() ? imageSizes.get(i) : new int[]{1, 1};
            // 记录图像对象偏移
            offsets.add(buffer.size());
            // 写入图像对象头，声明滤镜为 DCTDecode 且颜色空间为设备 RGB
            writeAscii(buffer, (3 + pageCount * 2 + i) + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + size[0]
                    // 追加像素高度
                    + " /Height " + size[1]
                    // 声明每像素 8 位
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + imageBytes.length
                    // 结束对象字典
                    + " >>\nstream\n");
            // 写入图像字节
            buffer.write(imageBytes, 0, imageBytes.length);
            // 写入流结束标记
            writeAscii(buffer, "\nendstream\nendobj\n");
        }
        // 计算交叉引用表起始位置
        int xrefOffset = buffer.size();
        // 写入交叉引用表
        writeXref(buffer, offsets);
        // 写入 trailer 与结尾标记
        writeAscii(buffer, "trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");
        // 确保输出目录存在
        ensureParent(output);
        // 把字节缓冲一次性写入文件
        Files.write(output, buffer.toByteArray());
    }

    // 写入交叉引用表，表项必须严格按对象编号顺序排列
    private static void writeXref(ByteArrayOutputStream buffer, List<Integer> offsets) {
        // 写入交叉引用表头，首个条目固定为 0 号自由对象
        writeAscii(buffer, "xref\n0 " + offsets.size() + "\n");
        // 写入 0 号自由对象条目
        writeAscii(buffer, "0000000000 65535 f \n");
        // 从 1 号对象开始逐个写入偏移
        for (int i = 1; i < offsets.size(); i++) {
            // 偏移格式固定为十位数字加一个空格加五位代号
            writeAscii(buffer, String.format("%010d 00000 n \n", offsets.get(i)));
        }
    }

    // 确保输出文件的父目录存在
    private static void ensureParent(Path output) throws IOException {
        // 取父目录
        Path parent = output.toAbsolutePath().getParent();
        // 父目录存在时直接返回
        if (parent == null) {
            // 无父目录无需创建
            return;
        }
        // 创建目录树，已存在时不报错
        Files.createDirectories(parent);
    }

    // 把 ASCII 文本写入字节缓冲，PDF 的结构部分全部基于 ASCII
    private static void writeAscii(ByteArrayOutputStream buffer, String text) {
        // 转成 ASCII 字节
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        // 写入缓冲
        buffer.write(bytes, 0, bytes.length);
    }

    // 转义 PDF 文本中的特殊字符，避免括号与反斜杠破坏语法
    private static String escapePdfText(String line) {
        // 使用 StringBuilder 逐字符处理
        StringBuilder builder = new StringBuilder();
        // 逐字符遍历
        for (int i = 0; i < line.length(); i++) {
            // 取出字符
            char ch = line.charAt(i);
            // 反斜杠与圆括号在 PDF 字符串中需要转义
            if (ch == '\\' || ch == '(' || ch == ')') {
                // 写入反斜杠前缀
                builder.append('\\');
                // 写入原字符
                builder.append(ch);
                // 继续处理下一个字符
                continue;
            }
            // 非可打印 ASCII 字符替换为问号，因为内置 Helvetica 字体只覆盖 WinAnsi 字符集
            if (ch < 32 || ch > 126) {
                // 用问号替代不可映射字符，保证 PDF 结构合法
                builder.append('?');
                // 继续处理下一个字符
                continue;
            }
            // 其余可打印字符原样写入
            builder.append(ch);
        }
        // 返回转义后的文本
        return builder.toString();
    }

    // 把双精度数值格式化为 PDF 可接受的最简形式，最多保留三位小数
    private static String formatNumber(double value) {
        // 使用固定区域设置，保证小数点符号稳定
        String text = String.format(java.util.Locale.ROOT, "%.3f", value);
        // 去掉多余的尾随零
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            // 裁掉末尾字符
            text = text.substring(0, text.length() - 1);
        }
        // 返回最简数值文本
        return text;
    }
}
