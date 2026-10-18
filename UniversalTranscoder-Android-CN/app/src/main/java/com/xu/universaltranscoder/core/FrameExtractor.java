package com.xu.universaltranscoder.core;

// 引入 Context：打开 content:// 数据源需要上下文。

import android.content.Context;
// 引入 Uri：源地址。
import android.net.Uri;
// 引入 Bitmap：抽出的帧。
import android.graphics.Bitmap;
// 引入 MediaMetadataRetriever：取帧的核心 API。
import android.media.MediaMetadataRetriever;

// 引入 ConvertOptions：读取抽帧模式参数。
import com.xu.universaltranscoder.model.ConvertOptions;
// 引入 OutputTarget：输出位置抽象。
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 ArrayList：收集时间点。
import java.util.ArrayList;
// 引入 List：列表类型。
import java.util.List;
// 引入 Locale：数值格式化。

/**
 * FrameExtractor 用 MediaMetadataRetriever.getFrameAtTime 在指定时间点抽取视频帧并存为 PNG。
 * <p>
 * 实现要点：
 * 1) 取帧使用 OPTION_CLOSEST_SYNC，即“最接近该时间点的关键帧”。
 * 原因：只有关键帧才能被精确、快速地解码；非关键帧取帧在部分设备上会退化为近似结果且明显更慢。
 * 2) 支持两种模式：指定秒数列表（如 "1,5,12.5"）或按数量均匀分布。
 * 均匀分布刻意避开视频首尾，避免第 0 帧与结束边界取帧失败。
 */
public final class FrameExtractor {

    // 私有构造：工具类不允许实例化。
    private FrameExtractor() {
        // 空实现，禁止外部 new。
    }

    /**
     * 执行抽帧。
     *
     * @return 输出文件的展示描述，便于日志与队列详情展示。
     */
    public static String extractFrames(Context context, Uri sourceUri, ConvertOptions options,
                                       OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 新建检索器。
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        // 记录已导出的帧数。
        int extracted = 0;
        try {
            // 绑定数据源。
            retriever.setDataSource(context, sourceUri);
            // 读取视频总时长（毫秒）。
            long durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            // 判断源里是否真的有视频轨（没有视频轨时抽帧没有意义）。
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == null) {
                // 抛出无视频轨错误。
                throw new ConversionException(ErrorReason.NO_VIDEO_TRACK, "源文件没有视频轨");
            }
            // 计算要抽取的时间点列表（单位：毫秒）。
            List<Long> timestampsMs = resolveTimestamps(options, durationMs);
            // 时间点为空说明参数非法。
            if (timestampsMs.isEmpty()) {
                // 抛出参数错误。
                throw new ConversionException(ErrorReason.INVALID_PARAMETER, "无法解析出任何抽帧位置");
            }
            // 逐时间点取帧。
            for (int i = 0; i < timestampsMs.size(); i++) {
                // 检查取消。
                if (callback.shouldCancel()) {
                    // 抛出取消异常。
                    throw new ConversionException(ErrorReason.CANCELED);
                }
                // 取当前时间点。
                long timeMs = timestampsMs.get(i);
                // 上报进度。
                callback.reportProgress(0.05 + 0.9 * i / (double) timestampsMs.size(), 0.0,
                        "正在抽取 " + timeMs + " ms 处的帧");
                // 取帧：毫秒转微秒；优先使用缩放版取帧以减少内存占用。
                Bitmap frame;
                // 若用户指定了图片目标尺寸，则使用缩放取帧，避免超大帧占内存。
                if (options.imageTargetWidth > 0 || options.imageTargetHeight > 0) {
                    // 计算缩放目标宽（未指定时沿用源宽，这里用较大值保证等比）。
                    int targetW = options.imageTargetWidth > 0 ? options.imageTargetWidth : options.imageTargetHeight;
                    // 计算缩放目标高。
                    int targetH = options.imageTargetHeight > 0 ? options.imageTargetHeight : options.imageTargetWidth;
                    // 使用缩放取帧 API。
                    frame = retriever.getScaledFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            Math.max(1, targetW), Math.max(1, targetH));
                } else {
                    // 使用原始尺寸取帧。
                    frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }
                // 取帧失败时跳过该时间点，避免整批任务失败。
                if (frame == null) {
                    // 记录日志说明跳过。
                    com.xu.universaltranscoder.util.LogBus.info("抽帧", "已跳过 " + timeMs + " ms 处的帧（解码器返回空）");
                    // 继续下一个时间点。
                    continue;
                }
                try {
                    // 为该帧派生输出文件，文件名带上序号与时间点，便于对应回原视频。
                    OutputTarget frameTarget = output.sibling(
                            output.fileName().replaceAll("\\.[^.]+$", "") + "_" + (i + 1) + "_" + timeMs + "ms",
                            "png", "image/png");
                    // 打开输出流。
                    java.io.OutputStream out = null;
                    try {
                        // 打开目标文档。
                        out = frameTarget.openStream();
                        // 以 PNG 无损写出。
                        frame.compress(Bitmap.CompressFormat.PNG, 100, out);
                        // 刷新缓冲。
                        out.flush();
                    } finally {
                        // 关闭输出流。
                        if (out != null) {
                            // 关闭并忽略异常。
                            try {
                                // 执行关闭。
                                out.close();
                            } catch (Throwable ignored) {
                                // 忽略关闭异常。
                            }
                        }
                    }
                    // 导出计数递增。
                    extracted++;
                } finally {
                    // 回收帧位图释放内存。
                    if (!frame.isRecycled()) {
                        // 执行回收。
                        frame.recycle();
                    }
                }
            }
            // 一帧都没取到说明源无法解码。
            if (extracted == 0) {
                // 抛出解码失败分类错误。
                throw new ConversionException(ErrorReason.DECODE_FAILED, "无法从该视频中解码出任何帧");
            }
            // 上报完成。
            callback.reportProgress(1.0, 0.0, "已导出 " + extracted + " 帧");
            // 返回输出说明。
            return output.describeDirectory() + "/" + output.fileName().replaceAll("\\.[^.]+$", "") + "_*.png";
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (SecurityException error) {
            // 权限问题分类上报。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (IllegalArgumentException error) {
            // 数据源非法（文件损坏或格式不支持）。
            throw new ConversionException(ErrorReason.FILE_NOT_FOUND, error);
        } catch (Throwable error) {
            // 其它归类为解码失败。
            throw new ConversionException(ErrorReason.DECODE_FAILED, error);
        } finally {
            // 释放检索器。
            try {
                // 释放资源。
                retriever.release();
            } catch (Throwable ignored) {
                // 忽略释放异常。
            }
        }
    }

    /**
     * 根据参数解析出要抽取的时间点（毫秒）。
     */
    private static List<Long> resolveTimestamps(ConvertOptions options, long durationMs) throws ConversionException {
        // 结果列表。
        List<Long> result = new ArrayList<>();
        // 优先使用“指定时间点”模式。
        String explicit = options.frameTimesSeconds == null ? "" : options.frameTimesSeconds.trim();
        // 非空时按逗号解析。
        if (!explicit.isEmpty()) {
            // 按逗号切分。
            String[] parts = explicit.split(",");
            // 逐个解析。
            for (String part : parts) {
                // 去掉空白。
                String trimmed = part.trim();
                // 空片段跳过。
                if (trimmed.isEmpty()) {
                    // 继续下一段。
                    continue;
                }
                // 解析秒数（允许小数）。
                double seconds;
                // 解析并捕获异常。
                try {
                    // 执行解析。
                    seconds = Double.parseDouble(trimmed);
                } catch (NumberFormatException error) {
                    // 表达式非法时抛出参数错误。
                    throw new ConversionException(ErrorReason.INVALID_PARAMETER, "无效的抽帧位置：" + trimmed);
                }
                // 负数非法。
                if (seconds < 0.0) {
                    // 抛出参数错误。
                    throw new ConversionException(ErrorReason.INVALID_PARAMETER, "抽帧位置不能为负数：" + trimmed);
                }
                // 换算为毫秒并加入列表。
                result.add((long) (seconds * 1000.0));
            }
            // 返回解析结果。
            return result;
        }
        // 均匀分布模式。
        int count = Math.max(1, Math.min(50, options.frameCount));
        // 只有一帧时取中点，避免落在易失败的首尾边界。
        if (count == 1) {
            // 加入中点时间。
            result.add(Math.max(0L, durationMs / 2L));
            // 返回结果。
            return result;
        }
        // 多帧时按 (i+1)/(count+1) 的比例分布，刻意避开 0% 与 100% 两个边界。
        for (int i = 1; i <= count; i++) {
            // 计算比例。
            double fraction = (double) i / (count + 1);
            // 换算为毫秒并加入。
            result.add(Math.max(0L, (long) (durationMs * fraction)));
        }
        // 返回结果。
        return result;
    }

    /**
     * 安全解析 long，失败返回 0。
     */
    private static long parseLong(String value) {
        // 空值返回 0。
        if (value == null) {
            // 返回 0。
            return 0L;
        }
        // 尝试解析。
        try {
            // 返回解析结果。
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            // 解析失败返回 0。
            return 0L;
        }
    }
}
