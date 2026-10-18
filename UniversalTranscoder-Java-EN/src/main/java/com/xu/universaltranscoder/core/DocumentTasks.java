package com.xu.universaltranscoder.core;

// 引入 PdfMaker，用于图片与文本生成 PDF

import com.xu.universaltranscoder.doc.PdfMaker;
// 引入 PdfRenderer，用于把 PDF 逐页渲染为图片
import com.xu.universaltranscoder.doc.PdfRenderer;
// 引入 IOException，处理文件读写异常
import java.io.IOException;
// 引入 Files，用于读取文本文件与检查产物
import java.nio.file.Files;
// 引入 Path，表示各类输入与输出路径
import java.nio.file.Path;
// 引入 Charset，指定文本读取编码
import java.nio.charset.Charset;
// 引入 StandardCharsets，提供 UTF-8 常量
import java.nio.charset.StandardCharsets;
// 引入 ArrayList，收集图片清单
import java.util.ArrayList;
// 引入 List 接口，作为图片清单类型
import java.util.List;

// 关键字 class：DocumentTasks 提供文档类与抽帧类任务的实现，并给出便捷的建单方法
// 之所以与 MediaJobTask 分开：文档任务不经过 ffmpeg 命令构建，参数结构与媒体任务完全不同
public final class DocumentTasks {

    // 私有构造方法，禁止实例化这个纯静态任务工厂
    private DocumentTasks() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 创建图片合成 PDF 的任务对象
    public static Job newImageToPdfJob(List<Path> images, Path outputFile) {
        // 取第一张图片作为任务的输入代表，便于界面展示来源
        Path first = images.isEmpty() ? null : images.get(0);
        // 构造任务参数
        ImageToPdfOptions options = new ImageToPdfOptions(images, outputFile);
        // 构造任务对象，类型为图片合成 PDF
        return new Job(Job.Kind.IMAGE_TO_PDF, first, "pdf", null, new ImageToPdfTask(options),
                // 去重键由输出路径与图片数量组成，避免同一批图片重复合成为同一文件
                "image2pdf|" + outputFile + "|" + images.size(), 0d);
    }

    // 创建文本生成 PDF 的任务对象
    public static Job newTextToPdfJob(Path textFile, Path outputFile) {
        // 构造任务参数
        TextToPdfOptions options = new TextToPdfOptions(textFile, outputFile);
        // 构造任务对象
        return new Job(Job.Kind.TEXT_TO_PDF, textFile, "pdf", null, new TextToPdfTask(options),
                // 去重键由输入与输出路径组成
                "text2pdf|" + textFile + "|" + outputFile, 0d);
    }

    // 创建 PDF 渲染为图片的任务对象
    public static Job newPdfToImageJob(Path pdfFile, Path outputDirectory, String imageFormat, int dpi, int fromPage, int toPage) {
        // 构造任务参数
        PdfToImageOptions options = new PdfToImageOptions(outputDirectory, imageFormat, dpi, fromPage, toPage);
        // 构造任务对象
        return new Job(Job.Kind.PDF_TO_IMAGE, pdfFile, imageFormat, null, new PdfToImageTask(options),
                // 去重键包含页码范围与分辨率，避免不同参数的请求被误判为重复
                "pdf2image|" + pdfFile + "|" + outputDirectory + "|" + imageFormat + "|" + dpi + "|" + fromPage + "-" + toPage, 0d);
    }

    // 创建视频抽帧的任务对象
    public static Job newFrameExtractJob(Path videoFile, Path outputDirectory, double[] timestamps, String baseName) {
        // 构造任务参数
        FrameOptions options = new FrameOptions(outputDirectory, timestamps, baseName);
        // 构造任务对象
        return new Job(Job.Kind.FRAME_EXTRACT, videoFile, "png", null, new FrameExtractTask(options),
                // 去重键包含时间点数量，避免不同时间点组合被判重
                "frames|" + videoFile + "|" + outputDirectory + "|" + timestamps.length, 0d);
    }

    // 图片合成 PDF 任务的参数载体
    public static final class ImageToPdfOptions {
        // 待合并的图片清单，顺序即页面顺序
        final List<Path> images;
        // 目标 PDF 文件路径
        final Path outputFile;

        // 构造方法：接收图片清单与输出路径
        public ImageToPdfOptions(List<Path> images, Path outputFile) {
            // 保存图片清单副本，避免外部后续修改影响任务
            this.images = new ArrayList<>(images);
            // 保存输出路径
            this.outputFile = outputFile;
        }
    }

    // 文本生成 PDF 任务的参数载体
    public static final class TextToPdfOptions {
        // 源文本文件路径
        final Path textFile;
        // 目标 PDF 文件路径
        final Path outputFile;

        // 构造方法：接收文本路径与输出路径
        public TextToPdfOptions(Path textFile, Path outputFile) {
            // 保存文本路径
            this.textFile = textFile;
            // 保存输出路径
            this.outputFile = outputFile;
        }
    }

    // PDF 渲染为图片任务的参数载体
    public static final class PdfToImageOptions {
        // 输出目录
        final Path outputDirectory;
        // 目标图片格式，png 或 jpg
        final String imageFormat;
        // 渲染分辨率
        final int dpi;
        // 起始页码，用户视角为 1 基
        final int fromPage;
        // 结束页码，0 表示直到最后一页
        final int toPage;

        // 构造方法：接收全部渲染参数
        public PdfToImageOptions(Path outputDirectory, String imageFormat, int dpi, int fromPage, int toPage) {
            // 保存输出目录
            this.outputDirectory = outputDirectory;
            // 保存目标图片格式
            this.imageFormat = imageFormat;
            // 保存渲染分辨率
            this.dpi = dpi;
            // 保存起始页码
            this.fromPage = fromPage;
            // 保存结束页码
            this.toPage = toPage;
        }
    }

    // 视频抽帧任务的参数载体
    public static final class FrameOptions {
        // 输出目录
        final Path outputDirectory;
        // 待抽帧的时间点数组（秒）
        final double[] timestamps;
        // 输出文件名前缀
        final String baseName;

        // 构造方法：接收输出目录、时间点与前缀
        public FrameOptions(Path outputDirectory, double[] timestamps, String baseName) {
            // 保存输出目录
            this.outputDirectory = outputDirectory;
            // 保存时间点数组
            this.timestamps = timestamps.clone();
            // 保存文件名前缀
            this.baseName = baseName;
        }
    }

    // 图片合成 PDF 任务：按清单顺序把图片写入一份 PDF
    public static final class ImageToPdfTask implements Job.Task {
        // 任务参数
        private final ImageToPdfOptions options;

        // 构造方法：接收任务参数
        public ImageToPdfTask(ImageToPdfOptions options) {
            // 保存参数对象
            this.options = options;
        }

        // 执行任务：先上报处理进度，再一次性生成 PDF
        @Override
        public void execute(Job job, Job.Context context) throws IOException {
            // 检查是否已被取消，取消时直接返回
            if (context.isCancelled()) {
                // 直接返回，由队列标记取消
                return;
            }
            // 记录任务开始日志，包含图片数量与输出路径
            context.log("Building PDF from " + options.images.size() + " image(s) into " + options.outputFile);
            // 上报 10% 进度，表示已开始读取
            context.progress(10d, 0d, -1d);
            // 调用手写 PDF 生成器完成合成
            PdfMaker.imagesToPdf(options.images, options.outputFile);
            // 校验产物是否存在且非空，以此判定任务是否真实成功
            long size = Files.isRegularFile(options.outputFile) ? Files.size(options.outputFile) : 0L;
            // 产物为空时抛出异常，让队列把任务标记为失败
            if (size <= 0L) {
                // 抛出异常说明产物缺失
                throw new IOException("The PDF file was not created.");
            }
            // 记录产物路径与体积
            job.outputPath(options.outputFile);
            // 记录产物体积
            job.outputSize(size);
            // 进度置为 100
            context.progress(100d, 0d, 0d);
            // 记录成功日志
            context.log("PDF created: " + options.outputFile + " (" + (size / 1024) + " KB)");
        }
    }

    // 文本生成 PDF 任务：读取文本并分页写成 PDF
    public static final class TextToPdfTask implements Job.Task {
        // 任务参数
        private final TextToPdfOptions options;

        // 构造方法：接收任务参数
        public TextToPdfTask(TextToPdfOptions options) {
            // 保存参数对象
            this.options = options;
        }

        // 执行任务：读取文本内容并生成 PDF
        @Override
        public void execute(Job job, Job.Context context) throws IOException {
            // 检查是否已被取消
            if (context.isCancelled()) {
                // 直接返回
                return;
            }
            // 记录开始日志
            context.log("Converting text file to PDF: " + options.textFile);
            // 上报 20% 进度
            context.progress(20d, 0d, -1d);
            // 按 UTF-8 优先读取文本，覆盖绝大多数现代文本文件
            String content = readText(options.textFile);
            // 提取文本内容，调用手写 PDF 生成器完成分页写出
            PdfMaker.textToPdf(content, options.outputFile);
            // 校验产物是否存在且非空
            long size = Files.isRegularFile(options.outputFile) ? Files.size(options.outputFile) : 0L;
            // 产物为空时抛出异常
            if (size <= 0L) {
                // 抛出异常说明产物缺失
                throw new IOException("The PDF file was not created.");
            }
            // 记录产物路径
            job.outputPath(options.outputFile);
            // 记录产物体积
            job.outputSize(size);
            // 进度置为 100
            context.progress(100d, 0d, 0d);
            // 记录成功日志
            context.log("PDF created: " + options.outputFile + " (" + (size / 1024) + " KB)");
        }

        // 读取文本文件内容，UTF-8 解码失败时回退到中文常见编码
        private String readText(Path file) throws IOException {
            // 捕获解码异常
            try {
                // 按 UTF-8 读取全部内容
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                // UTF-8 失败时尝试使用 GBK，兼容旧版 Windows 文本文件
                try {
                    // 按 GBK 读取全部内容
                    return Files.readString(file, Charset.forName("GBK"));
                } catch (IOException | RuntimeException inner) {
                    // 两种编码都失败时统一抛出 IO 异常，交由错误提示处理
                    throw new IOException("Unable to read the text file with UTF-8 or GBK.");
                }
            }
        }
    }

    // PDF 渲染为图片任务：逐页渲染并回调进度
    public static final class PdfToImageTask implements Job.Task {
        // 任务参数
        private final PdfToImageOptions options;

        // 构造方法：接收任务参数
        public PdfToImageTask(PdfToImageOptions options) {
            // 保存参数对象
            this.options = options;
        }

        // 执行任务：逐页渲染并写出图片
        @Override
        public void execute(Job job, Job.Context context) throws IOException {
            // 检查是否已被取消
            if (context.isCancelled()) {
                // 直接返回
                return;
            }
            // 记录开始日志，包含页码范围
            context.log("Rendering PDF pages " + options.fromPage + ".." + options.toPage + " at " + options.dpi + " DPI");
            // 调用 PDFBox 渲染器，输入路径取任务自身的输入文件，传入进度回调
            int written = PdfRenderer.renderToImages(job.input(), options.outputDirectory, options.imageFormat,
                    options.dpi, options.fromPage, options.toPage, (completed, total) -> {
                        // 把页数换算成百分比，保留 5% 到 100% 的区间避免进度瞬间跳满
                        double percent = total <= 0 ? 100d : 5d + 95d * completed / total;
                        // 上报进度
                        context.progress(percent, 0d, -1d);
                    });
            // 记录产物为空时抛出异常，让任务状态变为失败
            if (written <= 0) {
                // 抛出异常说明没有渲染出任何页面
                throw new IOException("No page was rendered. Check the page range and the PDF file.");
            }
            // 记录输出目录作为产物位置，供打开输出目录使用
            job.outputPath(options.outputDirectory);
            // 把渲染页数写入任务描述，界面因此能看到具体产出数量
            job.message("Rendered " + written + " page(s)");
            // 进度置为 100
            context.progress(100d, 0d, 0d);
            // 记录成功日志
            context.log("Rendered " + written + " page(s) into " + options.outputDirectory);
        }
    }

    // 视频抽帧任务：按时间点抽取 PNG 图片
    public static final class FrameExtractTask implements Job.Task {
        // 任务参数
        private final FrameOptions options;

        // 构造方法：接收任务参数
        public FrameExtractTask(FrameOptions options) {
            // 保存参数对象
            this.options = options;
        }

        // 执行任务：逐个时间点抽帧
        @Override
        public void execute(Job job, Job.Context context) throws IOException {
            // 检查是否已被取消
            if (context.isCancelled()) {
                // 直接返回
                return;
            }
            // 记录开始日志，说明抽帧数量与输出目录
            context.log("Extracting " + options.timestamps.length + " frame(s) into " + options.outputDirectory);
            // 构造进度监听器，把 ffmpeg 的日志中转给任务上下文
            FfmpegRunner.Listener listener = new FfmpegRunner.Listener() {
                // 抽帧场景不区分进度块，直接忽略
                @Override
                public void onProgress(ProgressParser.ProgressUpdate update) {
                    // 空实现，抽帧速度极快无需展示百分比
                }

                // 转发日志
                @Override
                public void onLog(String line) {
                    // 转交上下文输出
                    context.log(line);
                }
            };
            // 调用抽帧器，传入取消信号与监听器
            int written = FrameExtractor.extractFrames(job.input(), options.outputDirectory, options.timestamps,
                    options.baseName, job.cancelSignal(), listener);
            // 未写出任何图片时抛出异常
            if (written <= 0) {
                // 抛出异常说明抽帧完全失败
                throw new IOException("No frame could be extracted from this file.");
            }
            // 记录输出目录
            job.outputPath(options.outputDirectory);
            // 把抽帧数量写入任务描述，界面因此能看到具体产出数量
            job.message("Extracted " + written + " frame(s)");
            // 进度置为 100
            context.progress(100d, 0d, 0d);
            // 记录成功日志
            context.log("Extracted " + written + " frame(s) into " + options.outputDirectory);
        }
    }
}
