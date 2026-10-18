package com.xu.universaltranscoder.core;

// 引入 Context：打开 content:// 数据源需要上下文。

import android.content.Context;
// 引入 Surface：编码器的输入表面，解码器把画面渲染到它上面，从而完成 GPU 缩放与格式转换。
import android.view.Surface;
// 引入 MediaCodec：解码与编码核心类。
import android.media.MediaCodec;
// 引入 MediaCodecInfo：编解码器能力信息（这里用于日志）。
import android.media.MediaCodecInfo;
// 引入 MediaExtractor：读取源文件的压缩样本。
import android.media.MediaExtractor;
// 引入 MediaFormat：编解码器格式描述。
import android.media.MediaFormat;
// 引入 MediaMuxer：写入目标容器。
import android.media.MediaMuxer;
// 引入 Uri：源地址。
import android.net.Uri;

// 引入 ConvertOptions：读取用户参数。
import com.xu.universaltranscoder.model.ConvertOptions;
// 引入 TargetSpec：目标规格。
import com.xu.universaltranscoder.model.TargetSpec;
// 引入 TargetRegistry：构造编码器 MIME 与容器兼容性判断。
import com.xu.universaltranscoder.model.TargetRegistry;
// 引入 AppSettings：读取“是否优先硬件编解码器”。
import com.xu.universaltranscoder.util.AppSettings;
// 引入 OutputTarget：输出位置抽象。
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 ByteBuffer：编解码器缓冲区。
import java.nio.ByteBuffer;
// 引入 Locale：MIME 小写比较。
import java.util.Locale;

/**
 * VideoTranscoder 实现“视频重编码”：解码 -> （可选缩放）-> 重新编码 -> 封装。
 * <p>
 * 关键实现选择与原因：
 * 1) 使用“解码器输出到编码器输入表面（Surface）”的流水线。
 * 原因：MediaCodec 的 ByteBuffer 输出是设备相关的 YUV 格式（可能是 NV12、I420、私有 layout），
 * 在 Java 层手动转换极易出错且极慢；把解码结果直接渲染到编码器的输入 Surface，
 * 缩放与颜色空间转换全部由硬件/驱动完成，既快又稳。
 * 2) 分辨率缩放通过设置编码器的 KEY_WIDTH/KEY_HEIGHT 实现，解码帧会被自动缩放到该尺寸。
 * 3) 音轨不重编码，而是把源音频样本原样写入目标容器（前提是容器接受源音频编码）。
 * 原因：音视频同时重编码需要两套编解码器与复杂的交错写入逻辑，收益很小；
 * AAC 源放进 MP4/3GP 容器本来就是合法且常见的做法。
 */
public final class VideoTranscoder {

    // 编解码器等待超时（微秒）：10ms 足够让硬件编解码器完成一帧，又不会让循环空转太久。
    private static final long CODEC_TIMEOUT_US = 10_000L;
    // 进度上报节流：每处理这么多帧上报一次。
    private static final int PROGRESS_INTERVAL_FRAMES = 15;
    // 编码器 I 帧间隔（秒）：1 秒一个关键帧，兼顾 seek 精度与压缩率。
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    // 视频编码器允许的最小码率（kbps）：防止用户输入过小导致编码器拒绝配置。
    private static final int MIN_VIDEO_BITRATE_KBPS = 256;

    // 私有构造：工具类不允许实例化。
    private VideoTranscoder() {
        // 空实现，禁止外部 new。
    }

    /**
     * 执行视频重编码。
     *
     * @return 写入的轨道数量（视频 1 + 可能的音频 1）。
     */
    public static int transcode(Context context, Uri sourceUri, TargetSpec target, ConvertOptions options,
                                OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 目标不受支持时直接失败，不做无意义的尝试。
        if (!target.supported) {
            // 抛出格式不支持分类错误。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, target.unsupportedReason);
        }
        // 读取设置：是否优先硬件编解码器。
        boolean preferHardware = AppSettings.get(context).preferHardware();
        // 新建抽取器。
        MediaExtractor extractor = new MediaExtractor();
        // 声明解码器引用。
        MediaCodec decoder = null;
        // 声明编码器引用。
        MediaCodec encoder = null;
        // 声明编码器输入表面引用。
        Surface inputSurface = null;
        // 声明封装器引用。
        MediaMuxer muxer = null;
        // 记录总时长（微秒）用于进度。
        long durationUs = 0L;
        // 记录已处理的媒体时间（微秒）。
        long processedUs = 0L;
        // 视频轨在封装器中的下标，-1 表示尚未添加。
        int videoMuxerTrack = -1;
        // 音频轨在封装器中的下标，-1 表示没有音频轨。
        int audioMuxerTrack = -1;
        // 是否需要封装音频。
        boolean muxAudio = false;
        // 封装器是否已 start（必须等所有轨道都添加完才能 start）。
        boolean muxerStarted = false;
        try {
            // 绑定数据源。
            extractor.setDataSource(context, sourceUri, null);
            // 记录源轨道数。
            int trackCount = extractor.getTrackCount();
            // 待处理的视频轨下标。
            int videoTrack = -1;
            // 待处理的音频轨下标。
            int audioTrackIndexInSource = -1;
            // 视频轨格式。
            MediaFormat videoFormat = null;
            // 音频轨格式。
            MediaFormat audioFormat = null;
            // 遍历全部轨道。
            for (int i = 0; i < trackCount; i++) {
                // 取轨道格式。
                MediaFormat format = extractor.getTrackFormat(i);
                // 取 MIME。
                String mime = format.getString(MediaFormat.KEY_MIME);
                // 统一小写。
                String lower = mime == null ? "" : mime.toLowerCase(Locale.US);
                // 视频轨：只取第一条。
                if (lower.startsWith("video/") && videoTrack < 0) {
                    // 记录下标。
                    videoTrack = i;
                    // 记录格式。
                    videoFormat = format;
                }
                // 音频轨：只取第一条。
                if (lower.startsWith("audio/") && audioTrackIndexInSource < 0) {
                    // 记录下标。
                    audioTrackIndexInSource = i;
                    // 记录格式。
                    audioFormat = format;
                }
                // 读取轨道时长并取最大值作为总时长。
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    // 读取时长。
                    long trackDuration = format.getLong(MediaFormat.KEY_DURATION);
                    // 更新最大值。
                    if (trackDuration > durationUs) {
                        // 保存最大时长。
                        durationUs = trackDuration;
                    }
                }
            }
            // 没有视频轨时无法做视频重编码。
            if (videoTrack < 0 || videoFormat == null) {
                // 抛出无视频轨错误。
                throw new ConversionException(ErrorReason.NO_VIDEO_TRACK, "源文件没有视频轨");
            }
            // 取得源视频 MIME。
            String sourceVideoMime = videoFormat.getString(MediaFormat.KEY_MIME);
            // 计算源视频宽高。
            int sourceWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
            // 计算源视频高。
            int sourceHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            // 计算目标尺寸：用户指定时采用用户值，否则沿用源尺寸。
            int targetWidth = options.targetWidth > 0 ? options.targetWidth : sourceWidth;
            // 计算目标高（先按显式值，再在下面按比例或百分比修正）。
            int targetHeight = options.targetHeight > 0 ? options.targetHeight : sourceHeight;
            // 百分比缩放：当用户没有给出显式宽高时，"50%" 这类表达式按源分辨率换算。
            if (options.scalePercent > 0 && options.targetWidth <= 0 && options.targetHeight <= 0) {
                // 按百分比换算目标宽。
                targetWidth = Math.max(16, (int) Math.round(sourceWidth * options.scalePercent / 100.0));
                // 按百分比换算目标高。
                targetHeight = Math.max(16, (int) Math.round(sourceHeight * options.scalePercent / 100.0));
            } else if (options.targetWidth > 0 && options.targetHeight <= 0) {
                // 只给了宽度时按源宽高比推算高度，避免画面被拉伸。
                targetHeight = Math.max(16, (int) Math.round(sourceHeight * (double) targetWidth / sourceWidth));
            }
            // 记录是否确实需要缩放（尺寸变化）。
            boolean needsResize = targetWidth != sourceWidth || targetHeight != sourceHeight;
            // 需要缩放时把尺寸对齐到 16 的倍数：部分硬件编码器要求宽高是 16 的倍数，否则配置失败。
            if (needsResize) {
                // 向下对齐宽度到 16 的倍数。
                targetWidth = Math.max(16, (targetWidth / 16) * 16);
                // 向下对齐高度到 16 的倍数。
                targetHeight = Math.max(16, (targetHeight / 16) * 16);
            }
            // 查找目标视频编码器名字（按设置决定是否优先硬件）。
            String videoEncoderName = Capabilities.findCodecName(target.videoMime, true, preferHardware);
            // 找不到编码器时给出明确的分类错误。
            if (videoEncoderName == null) {
                // 抛出编解码器初始化失败分类错误，并带上具体编码类型。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, "没有可用于 " + target.videoMime + " 的编码器");
            }
            // 查找源视频解码器名字。
            String videoDecoderName = Capabilities.findCodecName(sourceVideoMime, false, preferHardware);
            // 找不到解码器时说明设备无法解码该源编码。
            if (videoDecoderName == null) {
                // 抛出解码失败分类错误。
                throw new ConversionException(ErrorReason.DECODE_FAILED, "没有可用于 " + sourceVideoMime + " 的解码器");
            }
            // 记录编码器实际使用的码率（做过下限保护）。
            int bitrate = Math.max(MIN_VIDEO_BITRATE_KBPS, options.videoBitrateKbps) * 1000;
            // 记录帧率（做下限保护，至少 1fps）。
            int frameRate = Math.max(1, options.videoFrameRate);
            // 通过编解码器名字创建编码器实例，避免 createEncoderByType 选到不可用的实现。
            try {
                // 创建编码器。
                encoder = MediaCodec.createByCodecName(videoEncoderName);
            } catch (Throwable error) {
                // 创建失败按分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 构造编码器输入格式。
            MediaFormat encoderFormat = MediaFormat.createVideoFormat(target.videoMime, targetWidth, targetHeight);
            // 指定颜色格式为 Surface，这是使用输入表面的前提。
            encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // 设置目标码率。
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            // 设置目标帧率。
            encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            // 设置 I 帧间隔。
            encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
            // 尝试设置码率控制模式为 VBR：多数设备支持，不支持时捕获异常继续。
            try {
                // 设置为可变码率，更贴合“尽量小体积”的目标。
                encoderFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            } catch (Throwable ignored) {
                // 设备不支持该模式键时忽略，编码器会使用默认模式。
            }
            // 配置编码器：无输入输出 Surface（输入用 createInputSurface 得到的表面），无加密。
            try {
                // 执行配置；此处最容易因参数不被接受而失败，因此单独捕获并给出分类错误。
                encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Throwable error) {
                // 配置失败归类为编解码器初始化失败。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 创建编码器输入表面，解码器将把画面渲染到它上面。
            try {
                // 取得输入表面。
                inputSurface = encoder.createInputSurface();
            } catch (Throwable error) {
                // 创建表面失败同样归类为编解码器初始化失败。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 启动编码器。
            try {
                // 进入执行状态。
                encoder.start();
            } catch (Throwable error) {
                // 启动失败按分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 通过编解码器名字创建解码器实例。
            try {
                // 创建解码器。
                decoder = MediaCodec.createByCodecName(videoDecoderName);
            } catch (Throwable error) {
                // 创建失败按分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 复制源视频格式用于解码器配置；直接使用抽取器给出的格式即可，它已包含 csd 等信息。
            try {
                // 配置解码器：输出到编码器输入表面，自动完成缩放与颜色转换。
                decoder.configure(videoFormat, inputSurface, null, 0);
            } catch (Throwable error) {
                // 配置失败按分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 启动解码器。
            try {
                // 进入执行状态。
                decoder.start();
            } catch (Throwable error) {
                // 启动失败按分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 创建封装器。
            try {
                // 打开目标输出（SAF 输出会自动使用缓存临时文件）。
                muxer = output.openMuxer(target.muxerFormat);
            } catch (java.io.IOException error) {
                // IO 失败按分类上报。
                throw new ConversionException(ErrorReason.IO_ERROR, error);
            } catch (Throwable error) {
                // 其它异常按封装器错误上报。
                throw new ConversionException(ErrorReason.MUXER_ERROR, error);
            }
            // 若源带旋转信息，把旋转角写入目标容器，保持播放方向与源一致。
            int rotation = readRotation(context, sourceUri);
            // 仅当旋转角有效时设置。
            if (rotation == 90 || rotation == 180 || rotation == 270) {
                // 写入方向提示。
                muxer.setOrientationHint(rotation);
            }
            // 判断音频是否可以原样放入目标容器。
            if (audioTrackIndexInSource >= 0 && audioFormat != null) {
                // 读取源音频 MIME。
                String sourceAudioMime = audioFormat.getString(MediaFormat.KEY_MIME);
                // 容器接受该音频编码时才封装音频。
                muxAudio = TargetRegistry.isCodecAllowedInContainer(target.muxerFormat, sourceAudioMime);
                // 接受时立即添加音频轨（音频格式在源里已完整可知）。
                if (muxAudio) {
                    // 添加音频轨并记录下标。
                    audioMuxerTrack = muxer.addTrack(audioFormat);
                }
            }
            // 选择源视频轨，后续 readSampleData 只会读到视频样本。
            extractor.selectTrack(videoTrack);
            // 构造编解码器缓冲元信息对象，循环内复用。
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            // 源输入是否已全部送入解码器。
            boolean inputDone = false;
            // 编码器是否已输出结束标志。
            boolean encoderDone = false;
            // 记录已编码帧数，用于进度节流。
            int frameCounter = 0;
            // 主循环：持续搬运数据，直到编码器报告结束。
            while (!encoderDone) {
                // 若源数据还没送完，尝试向解码器投喂一个样本。
                if (!inputDone) {
                    // 取出一个可用的解码器输入缓冲。
                    int inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    // 有可用缓冲时才投喂。
                    if (inputIndex >= 0) {
                        // 取得输入缓冲区对象。
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        // 从抽取器读取样本数据到该缓冲区。
                        int sampleSize = inputBuffer == null ? -1 : extractor.readSampleData(inputBuffer, 0);
                        // 样本读取失败（到达末尾）时送入结束标志。
                        if (sampleSize < 0) {
                            // 送入空缓冲并带 EOS 标志，通知解码器没有更多输入。
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            // 标记输入结束。
                            inputDone = true;
                        } else {
                            // 读取样本时间戳。
                            long sampleTime = extractor.getSampleTime();
                            // 送入解码器，时间戳保持源时间轴。
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                            // 推进到下一个样本。
                            extractor.advance();
                            // 记录已处理的媒体时间。
                            processedUs = sampleTime;
                            // 按帧数节流上报进度（这里用输入帧数近似，足以反映推进速度）。
                            frameCounter++;
                            // 到达间隔时上报。
                            if (frameCounter % PROGRESS_INTERVAL_FRAMES == 0) {
                                // 上报进度与状态。
                                callback.reportProgress(durationUs > 0L ? Math.min(0.99, (double) processedUs / durationUs) : 0.0,
                                        0.0, "正在重编码视频 " + target.label);
                                // 检查暂停/取消（此处输入样本已完整送入，是安全点）。
                                callback.checkPaused();
                            }
                        }
                    }
                }
                // 排空解码器输出：把解码帧渲染到编码器输入表面。
                while (true) {
                    // 取出一个解码输出缓冲。
                    int outputIndex = decoder.dequeueOutputBuffer(info, 0L);
                    // 有时间戳变化或缓冲变化等特殊返回码时结束本次排空。
                    if (outputIndex < 0) {
                        // 跳出排空循环。
                        break;
                    }
                    // 判断是否为结束标志。
                    boolean endOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    // 只有当缓冲里确实有画面数据时才渲染；EOS 空缓冲不能渲染，否则会触发非法渲染。
                    boolean render = info.size > 0;
                    // 释放输出缓冲：render=true 表示把画面提交到编码器输入表面。
                    decoder.releaseOutputBuffer(outputIndex, render);
                    // 遇到 EOS 时通知编码器“输入已结束”，这是 Surface 输入模式下的正确结束方式。
                    if (endOfStream) {
                        // 向编码器发送结束信号，编码器会把剩余帧刷出并输出 EOS。
                        encoder.signalEndOfInputStream();
                    }
                }
                // 排空编码器输出：把编码后的样本写入封装器。
                while (true) {
                    // 取出一个编码输出缓冲。
                    int encodedIndex = encoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                    // 输出格式变化：此时才能拿到编码器的最终格式（含 csd），需要添加视频轨并启动封装器。
                    if (encodedIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // 取得编码器输出格式。
                        MediaFormat encodedFormat = encoder.getOutputFormat();
                        // 添加视频轨并记录下标。
                        videoMuxerTrack = muxer.addTrack(encodedFormat);
                        // 所有轨道都已添加完毕，可以启动封装器。
                        muxer.start();
                        // 标记已启动，后续才允许写入样本。
                        muxerStarted = true;
                        // 继续排空编码器输出。
                        continue;
                    }
                    // 暂时没有可用输出时结束本次排空。
                    if (encodedIndex < 0) {
                        // 跳出循环。
                        break;
                    }
                    // 取得编码数据缓冲；部分设备在极少数索引上返回 null，需要判空。
                    ByteBuffer encodedBuffer = encoder.getOutputBuffer(encodedIndex);
                    // 配置数据（csd-0/csd-1）已经在输出格式里提供给封装器，此处不应作为样本写入，故长度置零跳过。
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // 把长度清零，避免把 SPS/PPS 当成一帧写进容器导致播放器异常。
                        info.size = 0;
                    }
                    // 只有在封装器已启动、缓冲有效且长度大于 0 时才写入。
                    if (muxerStarted && encodedBuffer != null && info.size > 0 && videoMuxerTrack >= 0) {
                        // 把缓冲位置限制在本帧数据范围内。
                        encodedBuffer.position(info.offset);
                        // 设置可读上限。
                        encodedBuffer.limit(info.offset + info.size);
                        // 写入封装器。
                        muxer.writeSampleData(videoMuxerTrack, encodedBuffer, info);
                    }
                    // 判断是否为编码结束标志。
                    boolean encoderEos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    // 释放编码输出缓冲（不需要渲染，故 render=false）。
                    encoder.releaseOutputBuffer(encodedIndex, false);
                    // 收到 EOS 时标记循环结束条件。
                    if (encoderEos) {
                        // 视频编码阶段完成。
                        encoderDone = true;
                    }
                }
                // 每轮循环都检查一次取消：用户停止后应尽快退出。
                if (callback.shouldCancel()) {
                    // 抛出取消分类异常，由调度器标记任务为已取消。
                    throw new ConversionException(ErrorReason.CANCELED, "视频重编码过程中被取消");
                }
            }
            // 视频阶段结束后，若需要封装音频，则把源音频样本原样写入目标容器。
            if (muxAudio && audioMuxerTrack >= 0 && audioTrackIndexInSource >= 0) {
                // 通知用户正在搬运音轨。
                callback.reportProgress(Math.min(0.99, durationUs > 0L ? (double) processedUs / durationUs : 0.0), 0.0,
                        "正在封装音轨");
                // 切换到音频轨。
                extractor.unselectTrack(videoTrack);
                // 选中音频轨。
                extractor.selectTrack(audioTrackIndexInSource);
                // 分配音频样本缓冲（1MB 足够容纳任何一帧 AAC）。
                ByteBuffer audioBuffer = ByteBuffer.allocate(1024 * 1024);
                // 循环搬运音频样本。
                while (true) {
                    // 清空缓冲。
                    audioBuffer.clear();
                    // 读取样本。
                    int size = extractor.readSampleData(audioBuffer, 0);
                    // 到达末尾时结束。
                    if (size < 0) {
                        // 跳出循环。
                        break;
                    }
                    // 构造样本元信息。
                    MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
                    // 设置偏移。
                    audioInfo.offset = 0;
                    // 设置长度。
                    audioInfo.size = size;
                    // 设置时间戳。
                    audioInfo.presentationTimeUs = extractor.getSampleTime();
                    // 保留同步帧标记。
                    audioInfo.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                            : 0;
                    // 写入封装器。
                    muxer.writeSampleData(audioMuxerTrack, audioBuffer, audioInfo);
                    // 推进样本。
                    extractor.advance();
                    // 检查取消。
                    if (callback.shouldCancel()) {
                        // 抛出取消异常。
                        throw new ConversionException(ErrorReason.CANCELED, "封装音频过程中被取消");
                    }
                }
            }
            // 上报完成进度。
            callback.reportProgress(1.0, 0.0, "正在收尾 " + target.label);
            // 结束封装，写入索引。
            muxer.stop();
            // 释放封装器。
            muxer.release();
            // 置空引用避免重复释放。
            muxer = null;
            // 若输出经由缓存临时文件，则把结果搬运到用户目录。
            output.finalizeOutput();
            // 返回写入的轨道数（视频恒为 1）。
            return muxAudio ? 2 : 1;
        } catch (ConversionException error) {
            // 分类异常直接向上传递。
            throw error;
        } catch (SecurityException error) {
            // 权限问题分类上报。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (IllegalStateException error) {
            // 编解码器状态机异常（多因配置参数不被接受）归类为编解码器错误。
            throw new ConversionException(ErrorReason.CODEC_ERROR, error);
        } catch (IllegalArgumentException error) {
            // 参数被拒绝（例如容器不接受该编码）归类为格式不支持。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, error);
        } catch (java.io.IOException error) {
            // IO 失败按空间/权限细分。
            throw classifyIoError(error);
        } catch (Throwable error) {
            // 其它异常归类为未知错误，但保留原始细节供日志排查。
            throw new ConversionException(ErrorReason.UNKNOWN, error);
        } finally {
            // 释放解码器。
            if (decoder != null) {
                // 停止并释放，忽略异常。
                try {
                    // 停止解码器。
                    decoder.stop();
                } catch (Throwable ignored) {
                    // 忽略停止异常。
                }
                // 释放解码器。
                try {
                    // 释放资源。
                    decoder.release();
                } catch (Throwable ignored) {
                    // 忽略释放异常。
                }
            }
            // 释放编码器。
            if (encoder != null) {
                // 停止编码器。
                try {
                    // 停止。
                    encoder.stop();
                } catch (Throwable ignored) {
                    // 忽略停止异常。
                }
                // 释放编码器。
                try {
                    // 释放资源。
                    encoder.release();
                } catch (Throwable ignored) {
                    // 忽略释放异常。
                }
            }
            // 释放输入表面。
            if (inputSurface != null) {
                // 释放表面资源。
                try {
                    // 释放。
                    inputSurface.release();
                } catch (Throwable ignored) {
                    // 忽略释放异常。
                }
            }
            // 释放封装器（异常路径）。
            if (muxer != null) {
                // 尝试 stop 让已写入数据尽量落盘。
                try {
                    // 停止封装。
                    muxer.stop();
                } catch (Throwable ignored) {
                    // 忽略停止异常。
                }
                // 释放封装器。
                try {
                    // 释放资源。
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
     * 读取源视频旋转角。
     */
    private static int readRotation(Context context, Uri uri) {
        // 新建检索器。
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            // 绑定数据源。
            retriever.setDataSource(context, uri);
            // 读取旋转元数据。
            String value = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            // 无值按 0 处理。
            if (value == null) {
                // 返回 0。
                return 0;
            }
            // 解析角度。
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            // 读取失败按 0 处理。
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
     * 把 IO 异常细分为空间不足、权限与一般 IO 错误。
     */
    private static ConversionException classifyIoError(java.io.IOException error) {
        // 读取消息文本。
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
        // 空间不足关键字。
        if (message.contains("enospc") || message.contains("no space") || message.contains("insufficient")) {
            // 归类为空间不足。
            return new ConversionException(ErrorReason.STORAGE_FULL, error);
        }
        // 权限关键字。
        if (message.contains("permission") || message.contains("eacces")) {
            // 归类为权限问题。
            return new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        }
        // 其它按一般 IO 错误。
        return new ConversionException(ErrorReason.IO_ERROR, error);
    }
}
