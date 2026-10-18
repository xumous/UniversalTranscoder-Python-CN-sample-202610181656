package com.xu.universaltranscoder.core;

// 引入 Context：MediaExtractor/MediaMuxer 需要通过 Context 打开 content:// 数据源。

import android.content.Context;
// 引入 MediaCodec：复用其 BUFFER_FLAG_KEY_FRAME 常量标记同步帧。
import android.media.MediaCodec;
// 引入 MediaExtractor：读取源文件的压缩样本。
import android.media.MediaExtractor;
// 引入 MediaFormat：轨道格式描述。
import android.media.MediaFormat;
// 引入 MediaMuxer：把样本写入目标容器。
import android.media.MediaMuxer;

// 引入 TargetSpec：目标容器信息。
import com.xu.universaltranscoder.model.TargetSpec;
// 引入 TargetRegistry：校验“编码能否放进该容器”。
import com.xu.universaltranscoder.model.TargetRegistry;
// 引入 OutputTarget：输出位置抽象。
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 ByteBuffer：样本数据载体。
import java.nio.ByteBuffer;
// 引入 ArrayList：记录选中的轨道下标。
import java.util.ArrayList;
// 引入 List：轨道下标列表。
import java.util.List;
// 引入 Locale：MIME 小写比较。
import java.util.Locale;

/**
 * VideoRemuxer 实现“视频重封装（remux）”：只更换容器，不重新编码。
 * <p>
 * 为什么 remux 有价值：它不需要解码与编码，速度接近文件复制（毫秒级完成一部影片），
 * 画质零损失，是“换容器”场景下最理想的方案。
 * 实现要点：MediaExtractor 读出压缩样本 -> MediaMuxer 原样写入新容器；
 * 唯一硬约束是目标容器必须接受源编码（例如 WebM 不接受 H.264），因此必须做编码兼容校验。
 */
public final class VideoRemuxer {

    // 样本缓冲区大小：4MB 足以容纳绝大多数 4K 关键帧，避免大关键帧读取失败。
    private static final int SAMPLE_BUFFER_SIZE = 4 * 1024 * 1024;
    // 进度上报节流：每处理 64 个样本上报一次，避免高频回调把主线程刷爆。
    private static final int PROGRESS_INTERVAL_SAMPLES = 64;

    // 私有构造：工具类不允许实例化。
    private VideoRemuxer() {
        // 空实现，禁止外部 new。
    }

    /**
     * 执行视频重封装。
     *
     * @param context   上下文，用于打开 content:// 数据源。
     * @param sourceUri 源视频地址。
     * @param target    目标规格（必须为 VIDEO_REMUX 且容器受支持）。
     * @param output    输出位置。
     * @param callback  进度/暂停/取消回调。
     * @param keepVideo 是否保留视频轨。
     * @param keepAudio 是否保留音频轨。
     * @return 实际写到输出文件的轨道数量，便于日志说明。
     */
    public static int remux(Context context, android.net.Uri sourceUri, TargetSpec target, OutputTarget output,
                            ProgressCallback callback, boolean keepVideo, boolean keepAudio) throws ConversionException {
        // 目标容器不受支持时直接失败：绝不假装支持，避免产出损坏文件。
        if (!target.supported) {
            // 抛出格式不支持分类错误。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, target.unsupportedReason);
        }
        // 新建抽取器。
        MediaExtractor extractor = new MediaExtractor();
        // 新建封装器引用，延迟到拿到轨道后再创建。
        MediaMuxer muxer = null;
        // 声明源轨道下标到封装轨道下标的映射数组（长度等于源轨道数）。
        int[] trackMap = null;
        // 记录成功开始写入的封装轨道数。
        int muxerTrackCount = 0;
        // 统计源文件的总时长（微秒），用于进度计算；0 表示未知。
        long durationUs = 0L;
        // 用于进度换算的已处理媒体时间（微秒）。
        long processedUs = 0L;
        // 累计已处理样本数，用于节流上报。
        int sampleCounter = 0;
        try {
            // 绑定数据源；失败时按分类抛出。
            extractor.setDataSource(context, sourceUri, null);
            // 记录源轨道数。
            int trackCount = extractor.getTrackCount();
            // 初始化映射数组。
            trackMap = new int[trackCount];
            // 默认全部映射为 -1（未使用）。
            for (int i = 0; i < trackCount; i++) {
                // 填写未使用标记。
                trackMap[i] = -1;
            }
            // 先扫描一遍全部轨道，找出可用的视频轨与音频轨，并累加时长。
            for (int i = 0; i < trackCount; i++) {
                // 取轨道格式。
                MediaFormat format = extractor.getTrackFormat(i);
                // 判断轨道类型。
                boolean isVideo = format.getString(MediaFormat.KEY_MIME) != null
                        && format.getString(MediaFormat.KEY_MIME).toLowerCase(Locale.US).startsWith("video/");
                // 判断是否音频轨。
                boolean isAudio = format.getString(MediaFormat.KEY_MIME) != null
                        && format.getString(MediaFormat.KEY_MIME).toLowerCase(Locale.US).startsWith("audio/");
                // 轨道时长（微秒）用于进度分母，取所有轨道的最大值。
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    // 读取时长。
                    long trackDuration = format.getLong(MediaFormat.KEY_DURATION);
                    // 取最大值。
                    if (trackDuration > durationUs) {
                        // 更新总时长。
                        durationUs = trackDuration;
                    }
                }
                // 视频轨：仅当用户要求保留、且容器接受该编码时才使用。
                if (isVideo && keepVideo) {
                    // 校验容器兼容性。
                    if (!TargetRegistry.isCodecAllowedInContainer(target.muxerFormat, format.getString(MediaFormat.KEY_MIME))) {
                        // 容器不接受该视频编码时跳过该轨道，并在日志层面由上层解释。
                        continue;
                    }
                    // 记录映射关系，-2 暂时表示“待分配封装轨道下标”。
                    trackMap[i] = -2;
                }
                // 音频轨：仅当用户要求保留、且容器接受该编码时才使用。
                if (isAudio && keepAudio) {
                    // 校验容器兼容性。
                    if (!TargetRegistry.isCodecAllowedInContainer(target.muxerFormat, format.getString(MediaFormat.KEY_MIME))) {
                        // 容器不接受该音频编码时跳过。
                        continue;
                    }
                    // 记录映射关系。
                    trackMap[i] = -2;
                }
            }
            // 没有任何轨道被选中时给出明确的失败原因，而不是产出一个空文件。
            boolean anySelected = false;
            // 遍历映射数组判断是否存在待处理轨道。
            for (int i = 0; i < trackCount; i++) {
                // 发现待处理标记即认为有轨道被选中。
                if (trackMap[i] == -2) {
                    // 设置标志。
                    anySelected = true;
                }
            }
            // 没有可用轨道时抛错。
            if (!anySelected) {
                // 如果源里根本没有所选类型的轨道，报“无轨道”；否则报编码不兼容。
                if (!(hasTypeTrack(extractor, "video/") || hasTypeTrack(extractor, "audio/"))) {
                    // 源文件确实没有音视频轨。
                    throw new ConversionException(ErrorReason.NO_TRACK, "源文件中没有音频或视频轨");
                }
                // 有轨道但容器不接受其编码。
                throw new ConversionException(ErrorReason.CODEC_INCOMPATIBLE,
                        "源编码无法存入 " + target.label + " 容器");
            }
            // 创建封装器（内部会为 SAF 输出自动使用缓存临时文件中转）。
            muxer = output.openMuxer(target.muxerFormat);
            // 若源带旋转信息，把旋转角写入目标容器，保证播放器显示方向正确。
            int rotation = readRotation(context, sourceUri);
            // 仅当旋转角有效时才设置，避免写入非法值。
            if (rotation == 90 || rotation == 180 || rotation == 270) {
                // 设置方向提示。
                muxer.setOrientationHint(rotation);
            }
            // 为每个待处理轨道分配封装轨道下标。
            for (int i = 0; i < trackCount; i++) {
                // 只处理标记为待分配的轨道。
                if (trackMap[i] == -2) {
                    // 取轨道格式。
                    MediaFormat format = extractor.getTrackFormat(i);
                    // 添加轨道并取得封装轨道下标。
                    trackMap[i] = muxer.addTrack(format);
                    // 统计已添加的轨道数。
                    muxerTrackCount++;
                }
            }
            // 启动封装器；此后才能写入样本。
            muxer.start();
            // 记录已处理的媒体时间，用于计算整体进度。
            long totalProcessedUs = 0L;
            // 逐轨道顺序处理：先写完整条轨道再写另一条，MediaMuxer 允许这种写入顺序。
            for (int i = 0; i < trackCount; i++) {
                // 跳过未被选中的轨道。
                if (trackMap[i] < 0) {
                    // 继续下一条轨道。
                    continue;
                }
                // 让抽取器聚焦到该轨道。
                extractor.selectTrack(i);
                // 分配样本缓冲区（每次轨道处理复用同一块，减少内存分配）。
                ByteBuffer buffer = ByteBuffer.allocate(SAMPLE_BUFFER_SIZE);
                // 记录该轨道已经历的媒体时间（微秒），用于进度相加。
                long trackProcessedUs = 0L;
                // 循环读取样本。
                while (true) {
                    // 每个样本前清空缓冲区，确保写入的是本样本数据。
                    buffer.clear();
                    // 读取样本数据，返回实际字节数，-1 表示该轨道结束。
                    int size = extractor.readSampleData(buffer, 0);
                    // 到达轨道末尾时退出循环。
                    if (size < 0) {
                        // 结束当前轨道的处理。
                        break;
                    }
                    // 取样本的展示时间戳。
                    long sampleTime = extractor.getSampleTime();
                    // 构造样本元信息。
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    // 设置偏移（从 0 开始）。
                    info.offset = 0;
                    // 设置长度。
                    info.size = size;
                    // 设置时间戳（微秒）。
                    info.presentationTimeUs = sampleTime;
                    // 保留同步帧标记，这对播放器正确 seek 至关重要。
                    info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                            : 0;
                    // 写入目标容器。
                    muxer.writeSampleData(trackMap[i], buffer, info);
                    // 推进到下一个样本。
                    extractor.advance();
                    // 累计该轨道已处理的媒体时间。
                    trackProcessedUs = Math.max(trackProcessedUs, sampleTime);
                    // 累计样本计数。
                    sampleCounter++;
                    // 按节流间隔上报进度。
                    if (sampleCounter % PROGRESS_INTERVAL_SAMPLES == 0) {
                        // 进度分母优先使用总时长，缺失时退化为“无法计算百分比”。
                        totalProcessedUs = processedUs + trackProcessedUs;
                        // 上报进度与状态。
                        callback.reportProgress(durationUs > 0L ? Math.min(1.0, (double) totalProcessedUs / durationUs) : 0.0,
                                0.0, "正在重封装 " + target.label);
                        // 检查暂停/取消，落在此处是安全点（样本已完整写入）。
                        callback.checkPaused();
                    }
                }
                // 累加该轨道的媒体时间，作为下一条轨道进度的起点。
                processedUs += trackProcessedUs;
                // 取消该轨道的选择，准备处理下一条。
                extractor.unselectTrack(i);
            }
            // 所有轨道写完后上报 100% 进度。
            callback.reportProgress(1.0, 0.0, "正在收尾 " + target.label);
            // 完成封装：写入索引信息。
            muxer.stop();
            // 释放封装器资源。
            muxer.release();
            // 置空引用，避免 finally 重复释放。
            muxer = null;
            // 若输出走的是缓存临时文件，这里把结果搬运到用户目录。
            output.finalizeOutput();
            // 返回实际写入的轨道数。
            return muxerTrackCount;
        } catch (ConversionException error) {
            // 分类异常直接向上传递。
            throw error;
        } catch (SecurityException error) {
            // 权限问题分类上报。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (IllegalArgumentException error) {
            // MediaMuxer 拒绝参数（容器/轨道不被接受）归类为格式不支持。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, error);
        } catch (java.io.IOException error) {
            // IO 失败：区分“空间不足”与一般 IO 错误。
            throw classifyIoError(error);
        } catch (Throwable error) {
            // 其它异常归类为封装器错误。
            throw new ConversionException(ErrorReason.MUXER_ERROR, error);
        } finally {
            // 释放封装器（异常路径）。
            if (muxer != null) {
                // 尝试 stop 以尽量落盘，再释放。
                try {
                    // stop 可能在异常路径再次抛错，这里忽略。
                    muxer.stop();
                } catch (Throwable ignored) {
                    // 忽略 stop 异常。
                }
                // 释放资源。
                try {
                    // 释放封装器。
                    muxer.release();
                } catch (Throwable ignored) {
                    // 忽略释放异常。
                }
            }
            // 释放抽取器。
            extractor.release();
        }
    }

    /**
     * 判断源中是否存在指定前缀类型的轨道。
     */
    private static boolean hasTypeTrack(MediaExtractor extractor, String prefix) {
        // 遍历全部轨道。
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            // 取 MIME。
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            // 该轨道 MIME 匹配前缀时返回真。
            if (mime != null && mime.toLowerCase(Locale.US).startsWith(prefix)) {
                // 找到匹配轨道。
                return true;
            }
        }
        // 未找到。
        return false;
    }

    /**
     * 读取源视频的旋转角（度）。
     */
    private static int readRotation(Context context, android.net.Uri uri) {
        // 新建检索器。
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            // 绑定数据源。
            retriever.setDataSource(context, uri);
            // 读取旋转元数据。
            String value = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            // 无值时按 0 度处理。
            if (value == null) {
                // 返回 0。
                return 0;
            }
            // 解析为整数。
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            // 读取失败按 0 度处理，不影响主要流程。
            return 0;
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
     * 把 IO 异常细分为“空间不足”与一般 IO 错误。
     * 之所以要细分：空间不足是用户能自己解决的问题（清理空间后重试），提示应当明确。
     */
    private static ConversionException classifyIoError(java.io.IOException error) {
        // 读取异常消息，统一小写便于匹配。
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
        // 常见的空间不足关键字。
        if (message.contains("enospc") || message.contains("no space") || message.contains("insufficient")) {
            // 归类为存储空间不足。
            return new ConversionException(ErrorReason.STORAGE_FULL, error);
        }
        // 权限相关关键字。
        if (message.contains("permission") || message.contains("eacces")) {
            // 归类为权限问题。
            return new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        }
        // 其余按一般 IO 错误处理。
        return new ConversionException(ErrorReason.IO_ERROR, error);
    }
}
