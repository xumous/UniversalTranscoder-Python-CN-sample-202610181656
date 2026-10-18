package com.xu.universaltranscoder.core;

// 引入 Context：转换器需要上下文访问数据源与输出目录。

import android.content.Context;
// 引入 Uri：解析源与输出目录地址。
import android.net.Uri;

// 引入模型类。
import com.xu.universaltranscoder.model.ConversionJob;
import com.xu.universaltranscoder.model.TargetSpec;
// 引入枚举与工具类。
import com.xu.universaltranscoder.util.FormatUtil;
import com.xu.universaltranscoder.util.LogBus;
import com.xu.universaltranscoder.util.MediaOp;
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 File：应用专属输出目录。
import java.io.File;
// 引入 List/ArrayList：跟踪已创建的输出目标，便于失败时清理。
import java.util.ArrayList;
import java.util.List;
// 引入 Locale：字符串处理。
import java.util.Locale;

/**
 * ConversionEngine 是“转换调度中枢”。
 * 它负责四件事：
 * 1) 依据任务的目标规格判断输入是否匹配操作（例如对 PDF 选“图片合成 PDF”应报参数错误而不是崩溃）；
 * 2) 计算输出位置与输出文件名；
 * 3) 把任务分派到对应的具体转换器（VideoTranscoder/VideoRemuxer/AudioConverter/ImageConverter/DocumentConverter/FrameExtractor）；
 * 4) 失败时清理半成品文件，避免用户在输出目录里看到损坏的、无法播放的结果。
 */
public final class ConversionEngine {

    // 私有构造：工具类不允许实例化。
    private ConversionEngine() {
        // 空实现，禁止外部 new。
    }

    /**
     * 执行一个转换任务。
     * 任何失败都会以带分类的 ConversionException 抛出，由调度器写入任务状态。
     */
    public static void execute(Context context, ConversionJob job, ProgressCallback callback) throws ConversionException {
        // 目标规格。
        TargetSpec target = job.target;
        // 源地址（单个）。
        Uri sourceUri = Uri.parse(job.sourceUri);
        // 记录本次创建的全部输出目标，失败时逐一清理。
        List<OutputTarget> createdTargets = new ArrayList<>();
        // 记录日志：说明本次要做什么。
        LogBus.info("引擎", "任务 #" + job.id + " 操作=" + target.operation + " 目标=" + target.key
                + " 源=" + job.sourceName + " 参数[" + job.options.describe() + "]");
        // 计算输出文件基名（源文件名 + 时间戳），保证同名源多次运行不会互相覆盖。
        String baseName = FormatUtil.timestampedBaseName(sanitize(FormatUtil.baseNameFromUri(job.sourceName)));
        // 用 try 包裹整个分派过程，以便在失败时统一清理半成品。
        try {
            // 依据目标与本任务参数决定操作。
            MediaOp.Operation operation = target.operation;
            // 单文件操作按操作类型分派。
            switch (operation) {
                // 视频重封装。
                case VIDEO_REMUX: {
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行重封装，并记录写入的轨道数。
                    int tracks = VideoRemuxer.remux(context, sourceUri, target, output, callback,
                            job.options.keepVideo, job.options.keepAudio);
                    // 记录产出文件路径。
                    job.outputPaths.add(output.displayPath());
                    // 记录日志。
                    LogBus.info("引擎", "重封装完成，已写入 " + tracks + " 条轨道到 " + output.displayPath());
                    // 结束该分支。
                    break;
                }
                // 视频重编码。
                case VIDEO_TRANSCODE: {
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行重编码。
                    int tracks = VideoTranscoder.transcode(context, sourceUri, target, job.options, output, callback);
                    // 记录产出文件路径。
                    job.outputPaths.add(output.displayPath());
                    // 记录日志。
                    LogBus.info("引擎", "重编码完成，已写入 " + tracks + " 条轨道到 " + output.displayPath());
                    // 结束该分支。
                    break;
                }
                // 视频抽帧。
                case EXTRACT_FRAMES: {
                    // 构造输出目标（抽帧会派生多个文件，基名用于统一命名）。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行抽帧。
                    String description = FrameExtractor.extractFrames(context, sourceUri, job.options, output, callback);
                    // 记录产出描述。
                    job.outputPaths.add(description);
                    // 记录日志。
                    LogBus.info("引擎", "抽帧完成：" + description);
                    // 结束该分支。
                    break;
                }
                // 音频重封装。
                case AUDIO_REMUX: {
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行音频重封装。
                    int tracks = AudioConverter.remuxAudio(context, sourceUri, target, output, callback);
                    // 记录产出文件路径。
                    job.outputPaths.add(output.displayPath());
                    // 记录日志。
                    LogBus.info("引擎", "音频重封装完成，已写入 " + tracks + " 条轨道到 " + output.displayPath());
                    // 结束该分支。
                    break;
                }
                // 音频重编码。
                case AUDIO_TRANSCODE: {
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行音频重编码。
                    int tracks = AudioConverter.transcodeAudio(context, sourceUri, target, job.options, output, callback);
                    // 记录产出文件路径。
                    job.outputPaths.add(output.displayPath());
                    // 记录日志。
                    LogBus.info("引擎", "音频重编码完成，已写入 " + tracks + " 条轨道到 " + output.displayPath());
                    // 结束该分支。
                    break;
                }
                // 图片转换。
                case IMAGE_CONVERT: {
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行图片转换。
                    String path = ImageConverter.convert(context, sourceUri, target, job.options, output, callback);
                    // 记录产出文件路径。
                    job.outputPaths.add(path);
                    // 记录日志。
                    LogBus.info("引擎", "图片转换完成：" + path);
                    // 结束该分支。
                    break;
                }
                // 图片合成 PDF。
                case IMAGES_TO_PDF: {
                    // 校验输入文件类型：必须是图片后缀，否则属于“选错操作”。
                    List<Uri> images = collectInputs(job, true);
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行合成。
                    String path = DocumentConverter.imagesToPdf(context, images, job.options, output, callback);
                    // 记录产出文件路径。
                    job.outputPaths.add(path);
                    // 记录日志。
                    LogBus.info("引擎", "图片合成 PDF 完成：" + path);
                    // 结束该分支。
                    break;
                }
                // PDF 渲染为图片。
                case PDF_TO_IMAGES: {
                    // 校验输入必须是 PDF。
                    if (!MediaProbe.isPdf(job.sourceName)) {
                        // 抛出输入不匹配错误并给出建议。
                        throw new ConversionException(ErrorReason.INPUT_MISMATCH,
                                "所选文件不是 PDF：" + job.sourceName);
                    }
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行渲染。
                    String description = DocumentConverter.pdfToImages(context, sourceUri, job.options, output, callback);
                    // 记录产出描述。
                    job.outputPaths.add(description);
                    // 记录日志。
                    LogBus.info("引擎", "PDF 页面渲染完成：" + description);
                    // 结束该分支。
                    break;
                }
                // 文本转 PDF。
                case TEXT_TO_PDF: {
                    // 校验输入必须是纯文本。
                    if (!MediaProbe.isPlainText(job.sourceName)) {
                        // 抛出输入不匹配错误并给出建议。
                        throw new ConversionException(ErrorReason.INPUT_MISMATCH,
                                "所选文件不是纯文本文件：" + job.sourceName);
                    }
                    // 构造输出目标并登记以便清理。
                    OutputTarget output = track(createdTargets, createOutput(context, job, baseName, target.extension, target.mimeType));
                    // 执行转换。
                    String path = DocumentConverter.textToPdf(context, sourceUri, job.options, output, callback);
                    // 记录产出文件路径。
                    job.outputPaths.add(path);
                    // 记录日志。
                    LogBus.info("引擎", "文本转 PDF 完成：" + path);
                    // 结束该分支。
                    break;
                }
                // 未实现的操作类型（正常路径不会走到这里）。
                default: {
                    // 抛出格式不支持错误，明确说明操作未实现。
                    throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, "不支持的操作：" + operation);
                }
            }
        } catch (ConversionException error) {
            // 失败时清理所有已创建的输出，避免留下半成品文件。
            cleanup(createdTargets);
            // 把分类异常继续向上抛出，由调度器记录任务失败原因。
            throw error;
        } catch (Throwable error) {
            // 兜底的运行时异常同样需要清理，再包装为分类异常。
            cleanup(createdTargets);
            // 包装为未知错误分类。
            throw new ConversionException(ErrorReason.UNKNOWN, error);
        }
    }

    /**
     * 把输出目标登记到跟踪列表，便于失败时清理。
     */
    private static OutputTarget track(List<OutputTarget> list, OutputTarget target) {
        // 登记目标。
        list.add(target);
        // 原样返回，方便链式写法。
        return target;
    }

    /**
     * 清理所有已登记的输出目标。
     */
    private static void cleanup(List<OutputTarget> list) {
        // 逐个清理，单个失败不影响其它。
        for (OutputTarget target : list) {
            // 执行清理，内部已捕获异常。
            target.cleanupOnFailure();
        }
    }

    /**
     * 构造输出目标：优先使用任务自带的输出目录（用户在界面上选择的自定义目录），
     * 没有配置时回落到应用专属外部目录（无需任何存储权限，且随应用卸载自动清理）。
     */
    public static OutputTarget createOutput(Context context, ConversionJob job, String baseName,
                                            String extension, String mimeType) {
        // 任务指定了 SAF 目录时使用它。
        if (job.outputTreeUri != null && !job.outputTreeUri.isEmpty()) {
            // 构造 SAF 输出目标。
            return OutputTarget.forTree(context, Uri.parse(job.outputTreeUri), baseName, extension, mimeType);
        }
        // 否则使用应用专属外部目录。
        return OutputTarget.forFileDir(context, appOutputDir(context), baseName, extension, mimeType);
    }

    /**
     * 返回应用专属外部输出目录（不存在时创建）。
     */
    public static File appOutputDir(Context context) {
        // 取应用专属外部文件目录下的 UniversalTranscoder 子目录；该目录无需权限且对用户可见。
        File dir = new File(context.getExternalFilesDir(null), "UniversalTranscoder");
        // 目录不存在时创建。
        if (!dir.exists()) {
            // 创建目录（失败时后续写文件会抛出可分类的 IO 错误）。
            dir.mkdirs();
        }
        // 返回目录。
        return dir;
    }

    /**
     * 把任务输入整理成 URI 列表。
     * 目前每个任务只承载一个源文件（界面会把多选拆成多个任务），
     * 这里仍保留列表接口，是为了让“图片合成 PDF”等操作可以明确表达“输入是多个文件”。
     */
    private static List<Uri> collectInputs(ConversionJob job, boolean requireImages) throws ConversionException {
        // 新建列表。
        List<Uri> list = new ArrayList<>();
        // 需要图片时先校验类型。
        if (requireImages && !MediaProbe.isImageFile(job.sourceName)) {
            // 抛出输入不匹配错误。
            throw new ConversionException(ErrorReason.INPUT_MISMATCH,
                    "所选文件不是图片：" + job.sourceName);
        }
        // 加入当前任务的源地址。
        list.add(Uri.parse(job.sourceUri));
        // 返回列表。
        return list;
    }

    /**
     * 把文件名中的可疑字符替换掉，保证输出文件名在各文件系统上都合法。
     */
    private static String sanitize(String name) {
        // 空值兜底。
        if (name == null || name.isEmpty()) {
            // 返回通用名。
            return "output";
        }
        // 只保留字母、数字、下划线、短横线与点，其余替换为下划线。
        String cleaned = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        // 截断过长名称，避免某些文件系统对文件名长度敏感。
        if (cleaned.length() > 40) {
            // 截断。
            cleaned = cleaned.substring(0, 40);
        }
        // 返回小写清洗结果。
        return cleaned.toLowerCase(Locale.US);
    }
}
