package com.xu.universaltranscoder.doc;

// 引入 BufferedImage，接收 PDFBox 渲染出的位图

import java.awt.image.BufferedImage;
// 引入 IOException，处理 PDF 读取与图片写出异常
import java.io.IOException;
// 引入 Files，用于创建输出目录与写出图片
import java.nio.file.Files;
// 引入 Path，表示 PDF 输入与图片输出路径
import java.nio.file.Path;
// 引入 ImageIO，使用 JDK 标准图像编码器写出 PNG 与 JPG
import javax.imageio.ImageIO;
// 引入 PDFBox 渲染器，逐页把 PDF 转为位图
import org.apache.pdfbox.rendering.PDFRenderer;
// 引入 PDFBox 图像类型枚举，指定渲染为 RGB 或灰度
import org.apache.pdfbox.rendering.ImageType;
// 引入 PDDocument，表示已加载的 PDF 文档
import org.apache.pdfbox.pdmodel.PDDocument;
// 引入 MemoryUsageSetting，把 PDF 的主内存占用限制为临时文件模式，防止大文件耗尽堆内存
import org.apache.pdfbox.io.MemoryUsageSetting;

// 关键字 class：PdfRenderer 用 PDFBox 把 PDF 逐页渲染为图片，支持页码范围与目标格式
// 之所以用 PDFBox 而不是自研渲染：PDF 的绘制模型包含字体、路径与压缩流，自研渲染的成本远超收益
public final class PdfRenderer {

    // 私有构造方法，禁止实例化这个纯静态渲染器
    private PdfRenderer() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 把 PDF 渲染为图片，返回实际写出的图片数量
    public static int renderToImages(Path pdfPath, Path outputDirectory, String imageFormat, int dpi,
                                     int fromPage, int toPage, Progress progress) throws IOException {
        // 规范化目标格式，默认使用 png 因为它无损且支持透明通道
        String format = (imageFormat == null || imageFormat.trim().isEmpty()) ? "png" : imageFormat.trim().toLowerCase();
        // 渲染分辨率做上下限钳制，过低不可读过高会占用海量内存
        int safeDpi = Math.min(Math.max(dpi, 36), 600);
        // 确保输出目录存在
        Files.createDirectories(outputDirectory);
        // 以临时文件内存模式加载 PDF，避免整份文档驻留堆内存
        try (PDDocument document = PDDocument.load(pdfPath.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            // 取文档总页数
            int totalPages = document.getNumberOfPages();
            // 文档没有页面时直接返回 0，避免后续除零与空数组访问
            if (totalPages <= 0) {
                // 返回 0 表示没有产物
                return 0;
            }
            // 规范化起始页：用户输入为 1 基，非法值回退到第一页
            int start = (fromPage <= 0 || fromPage > totalPages) ? 1 : fromPage;
            // 规范化结束页：非法值回退到最后一页
            int end = (toPage <= 0 || toPage > totalPages) ? totalPages : toPage;
            // 结束页小于起始页时交换，容忍用户把范围写反
            if (end < start) {
                // 交换两个边界，保证区间有效
                int swap = start;
                // 起始页取较小值
                start = end;
                // 结束页取较大值
                end = swap;
            }
            // 创建渲染器，绑定当前文档
            PDFRenderer renderer = new PDFRenderer(document);
            // 计算本次需要渲染的页数
            int pageCount = end - start + 1;
            // 已写出的图片数量
            int written = 0;
            // 取输入 PDF 的主文件名作为图片文件名的前缀
            String baseName = stripExtension(pdfPath.getFileName().toString());
            // 逐页渲染
            for (int pageIndex = start - 1; pageIndex < end; pageIndex++) {
                // 取消检查交给上层，这里只负责渲染与写出
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, safeDpi,
                        // 使用 RGB 图像类型，兼容 PNG 与 JPG 两种输出
                        ImageType.RGB);
                // 按格式选择扩展名，jpg 输出使用 jpg 后缀
                String extension = "jpg".equals(format) || "jpeg".equals(format) ? "jpg" : "png";
                // 构造输出文件名，包含页码便于区分
                Path outputFile = outputDirectory.resolve(baseName + "-" + (pageIndex + 1) + "." + extension);
                // 写出图片文件
                ImageIO.write(image, extension, outputFile.toFile());
                // 写出计数加一
                written++;
                // 回调进度，供队列把页数换算成百分比
                if (progress != null) {
                    // 报告已完成页数与本次总页数
                    progress.onPage(written, pageCount);
                }
            }
            // 返回写出的图片数量
            return written;
        }
    }

    // 读取 PDF 页数，供界面在渲染前展示范围提示
    public static int pageCount(Path pdfPath) throws IOException {
        // 以临时文件内存模式加载，保持与渲染流程一致的内存策略
        try (PDDocument document = PDDocument.load(pdfPath.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            // 返回文档页数
            return document.getNumberOfPages();
        }
    }

    // 去掉文件名的扩展名，没有扩展名时原样返回
    private static String stripExtension(String fileName) {
        // 查找最后一个点号
        int dot = fileName.lastIndexOf('.');
        // 点号不存在或位于首位时视为无扩展名
        if (dot <= 0) {
            // 原样返回
            return fileName;
        }
        // 返回点号之前的部分
        return fileName.substring(0, dot);
    }

    // 渲染进度回调接口，每渲染完一页调用一次，供队列换算百分比
    public interface Progress {
        // 报告已完成的页数与总页数
        void onPage(int completed, int total);
    }
}
