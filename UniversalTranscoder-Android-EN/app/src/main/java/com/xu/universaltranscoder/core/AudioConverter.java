package com.xu.universaltranscoder.core;

// 引入 Context：打开 content:// 数据源需要上下文。

import android.content.Context;
// 引入 MediaCodec：音频解码与编码。
import android.media.MediaCodec;
// 引入 MediaCodecInfo：取出 AAC Profile 常量。
import android.media.MediaCodecInfo;
// 引入 MediaExtractor：读取压缩音频样本。
import android.media.MediaExtractor;
// 引入 MediaFormat：编解码器格式。
import android.media.MediaFormat;
// 引入 MediaMuxer：写入目标容器。
import android.media.MediaMuxer;
// 引入 Uri：源地址。

import android.net.Uri;

// 引入 ConvertOptions：音频参数来自这里。
import com.xu.universaltranscoder.model.ConvertOptions;
// 引入 TargetSpec：目标规格。
import com.xu.universaltranscoder.model.TargetSpec;
// 引入 TargetRegistry：容器兼容性判断与 MIME 常量。
import com.xu.universaltranscoder.model.TargetRegistry;
// 引入 AppSettings：读取硬件优先设置。
import com.xu.universaltranscoder.util.AppSettings;
// 引入 OutputTarget：输出位置抽象。
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 ByteBuffer：编解码器缓冲区。
import java.nio.ByteBuffer;
// 引入 Locale：MIME 小写比较。
import java.util.Locale;

/**
 * AudioConverter 同时实现两条音频流水线：
 * 1) 音频重封装（remux）：只换容器，保持原始音频编码；
 * 2) 音频重编码（transcode）：解码为 PCM，按需重采样/变换声道，再编码为 AAC。
 * <p>
 * 关于“采样率/声道可调”的诚实实现说明：
 * MediaCodec 的 AAC 编码器不会为你重采样，它只编码你喂进去的 PCM；
 * 因此当用户要求的采样率或声道数与源不同时，本类会在 Java 层对 16 位 PCM 做线性插值重采样与声道变换，
 * 再把 PCM 送给编码器。这样参数才是真正生效的，而不是“改了参数但结果不变”的假实现。
 */
public final class AudioConverter {

    // 编解码器等待超时（微秒）。
    private static final long CODEC_TIMEOUT_US = 10_000L;
    // 进度上报节流：每处理这么多个样本块上报一次。
    private static final int PROGRESS_INTERVAL_CHUNKS = 40;
    // 单次送入编码器的最大字节数上限，防止超大缓冲造成内存峰值。
    private static final int MAX_CHUNK_BYTES = 16384;

    // 私有构造：工具类不允许实例化。
    private AudioConverter() {
        // 空实现，禁止外部 new。
    }

    /**
     * 音频重封装：把源音频轨原样写入目标容器。
     * 适用场景：MP4 -> M4A、MP4 -> 3GP 这类“只换壳”的需求，速度快且无损。
     */
    public static int remuxAudio(Context context, Uri sourceUri, TargetSpec target, OutputTarget output,
                                 ProgressCallback callback) throws ConversionException {
        // 目标不受支持时立即失败。
        if (!target.supported) {
            // 抛出格式不支持分类错误。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, target.unsupportedReason);
        }
        // 新建抽取器。
        MediaExtractor extractor = new MediaExtractor();
        // 声明封装器引用。
        MediaMuxer muxer = null;
        // 记录总时长（微秒）。
        long durationUs = 0L;
        try {
            // 绑定数据源。
            extractor.setDataSource(context, sourceUri, null);
            // 记录音频轨下标。
            int audioTrack = -1;
            // 记录音频格式。
            MediaFormat audioFormat = null;
            // 遍历轨道找第一条音频轨并统计时长。
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                // 取轨道格式。
                MediaFormat format = extractor.getTrackFormat(i);
                // 取 MIME。
                String mime = format.getString(MediaFormat.KEY_MIME);
                // 统一小写。
                String lower = mime == null ? "" : mime.toLowerCase(Locale.US);
                // 命中音频轨且尚未记录时保存。
                if (lower.startsWith("audio/") && audioTrack < 0) {
                    // 记录下标。
                    audioTrack = i;
                    // 记录格式。
                    audioFormat = format;
                }
                // 统计最长时长作为进度分母。
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    // 读取时长。
                    long trackDuration = format.getLong(MediaFormat.KEY_DURATION);
                    // 取最大值。
                    if (trackDuration > durationUs) {
                        // 更新时长。
                        durationUs = trackDuration;
                    }
                }
            }
            // 没有音频轨时给出明确错误。
            if (audioTrack < 0 || audioFormat == null) {
                // 抛出无音频轨错误。
                throw new ConversionException(ErrorReason.NO_AUDIO_TRACK, "Source has no audio track");
            }
            // 取音频编码 MIME。
            String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
            // 校验目标容器是否接受该音频编码（例如 WebM 不接受 AAC）。
            if (!TargetRegistry.isCodecAllowedInContainer(target.muxerFormat, audioMime)) {
                // 抛出编码不兼容错误，并说明具体原因。
                throw new ConversionException(ErrorReason.CODEC_INCOMPATIBLE,
                        "The source audio codec " + audioMime + " cannot be stored in " + target.label);
            }
            // 创建封装器。
            muxer = output.openMuxer(target.muxerFormat);
            // 添加音频轨并记录下标。
            int muxerTrack = muxer.addTrack(audioFormat);
            // 启动封装器。
            muxer.start();
            // 选中音频轨。
            extractor.selectTrack(audioTrack);
            // 分配样本缓冲。
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            // 记录样本计数用于节流。
            int counter = 0;
            // 循环搬运样本。
            while (true) {
                // 清空缓冲。
                buffer.clear();
                // 读取样本。
                int size = extractor.readSampleData(buffer, 0);
                // 结束时退出。
                if (size < 0) {
                    // 跳出循环。
                    break;
                }
                // 构造样本元信息。
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                // 设置偏移。
                info.offset = 0;
                // 设置长度。
                info.size = size;
                // 设置时间戳。
                info.presentationTimeUs = extractor.getSampleTime();
                // 保留同步标记。
                info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                        : 0;
                // 写入容器。
                muxer.writeSampleData(muxerTrack, buffer, info);
                // 推进样本。
                extractor.advance();
                // 计数并节流上报。
                counter++;
                // 到达间隔时上报进度并检查暂停。
                if (counter % PROGRESS_INTERVAL_CHUNKS == 0) {
                    // 上报进度。
                    callback.reportProgress(durationUs > 0L ? Math.min(1.0, (double) info.presentationTimeUs / durationUs) : 0.0,
                            0.0, "Remuxing audio " + target.label);
                    // 检查暂停/取消。
                    callback.checkPaused();
                }
            }
            // 上报完成。
            callback.reportProgress(1.0, 0.0, "Finalising " + target.label);
            // 结束封装。
            muxer.stop();
            // 释放封装器。
            muxer.release();
            // 置空引用。
            muxer = null;
            // 必要时搬运临时文件。
            output.finalizeOutput();
            // 返回写入轨道数。
            return 1;
        } catch (ConversionException error) {
            // 分类异常直接上抛。
            throw error;
        } catch (SecurityException error) {
            // 权限问题分类上报。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (IllegalArgumentException error) {
            // 参数被拒绝归类为格式不支持。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, error);
        } catch (java.io.IOException error) {
            // IO 失败细分上报。
            throw classifyIoError(error);
        } catch (Throwable error) {
            // 其它归类为封装器错误。
            throw new ConversionException(ErrorReason.MUXER_ERROR, error);
        } finally {
            // 释放封装器（异常路径）。
            if (muxer != null) {
                // 尝试停止。
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
     * 音频重编码：解码 -> PCM 变换（重采样/声道）-> AAC 编码 -> 封装。
     * 目标音频编码由 target.audioMime 决定（当前实现支持 AAC）。
     */
    public static int transcodeAudio(Context context, Uri sourceUri, TargetSpec target, ConvertOptions options,
                                     OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 目标不受支持时立即失败。
        if (!target.supported) {
            // 抛出格式不支持分类错误。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, target.unsupportedReason);
        }
        // 目前仅实现到 AAC 编码；面向 Opus 的目标在注册表里已按设备能力限制，此处再校验一次以防误用。
        if (target.audioMime == null || !target.audioMime.equals(TargetRegistry.MIME_AUDIO_AAC)) {
            // 抛出格式不支持并说明实现范围。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED,
                    "Audio re-encode currently targets AAC only");
        }
        // 读取硬件优先设置。
        boolean preferHardware = AppSettings.get(context).preferHardware();
        // 新建抽取器。
        MediaExtractor extractor = new MediaExtractor();
        // 声明解码器。
        MediaCodec decoder = null;
        // 声明编码器。
        MediaCodec encoder = null;
        // 声明封装器。
        MediaMuxer muxer = null;
        // 总时长（微秒）。
        long durationUs = 0L;
        // 已处理时间（微秒）。
        long processedUs = 0L;
        try {
            // 绑定数据源。
            extractor.setDataSource(context, sourceUri, null);
            // 音频轨下标。
            int audioTrack = -1;
            // 音频格式。
            MediaFormat audioFormat = null;
            // 遍历轨道。
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                // 取格式。
                MediaFormat format = extractor.getTrackFormat(i);
                // 取 MIME。
                String mime = format.getString(MediaFormat.KEY_MIME);
                // 小写化。
                String lower = mime == null ? "" : mime.toLowerCase(Locale.US);
                // 记录第一条音频轨。
                if (lower.startsWith("audio/") && audioTrack < 0) {
                    // 记录下标。
                    audioTrack = i;
                    // 记录格式。
                    audioFormat = format;
                }
                // 统计时长。
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    // 读取。
                    long trackDuration = format.getLong(MediaFormat.KEY_DURATION);
                    // 取最大。
                    if (trackDuration > durationUs) {
                        // 更新。
                        durationUs = trackDuration;
                    }
                }
            }
            // 无音频轨时报错。
            if (audioTrack < 0 || audioFormat == null) {
                // 抛出无音频轨错误。
                throw new ConversionException(ErrorReason.NO_AUDIO_TRACK, "Source has no audio track");
            }
            // 取源音频 MIME。
            String sourceAudioMime = audioFormat.getString(MediaFormat.KEY_MIME);
            // 源采样率（解码器输入格式里一定带有该键）。
            int sourceSampleRate = audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : options.sampleRate;
            // 源声道数。
            int sourceChannels = audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : options.channels;
            // 用户期望的采样率，做合法性裁剪（AAC 支持 8k~48k）。
            int targetSampleRate = clampSampleRate(options.sampleRate);
            // 用户期望的声道数，裁剪到 1..2。
            int targetChannels = Math.max(1, Math.min(2, options.channels));
            // 用户期望的码率（kbps -> bps），做下限保护。
            int targetBitrate = Math.max(16, options.audioBitrateKbps) * 1000;
            // 查找解码器名字。
            String decoderName = Capabilities.findCodecName(sourceAudioMime, false, preferHardware);
            // 找不到解码器时报错。
            if (decoderName == null) {
                // 抛出解码失败。
                throw new ConversionException(ErrorReason.DECODE_FAILED, "No decoder for " + sourceAudioMime);
            }
            // 查找 AAC 编码器名字。
            String encoderName = Capabilities.findCodecName(target.audioMime, true, preferHardware);
            // 找不到编码器时报错。
            if (encoderName == null) {
                // 抛出编解码器初始化失败。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, "No encoder for " + target.audioMime);
            }
            // 创建解码器。
            try {
                // 按名字创建，避免选到不可用实现。
                decoder = MediaCodec.createByCodecName(decoderName);
            } catch (Throwable error) {
                // 创建失败分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 配置并启动解码器：无输出 Surface，使用 ByteBuffer 模式得到 PCM。
            try {
                // 配置。
                decoder.configure(audioFormat, null, null, 0);
                // 启动。
                decoder.start();
            } catch (Throwable error) {
                // 失败分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 创建编码器。
            try {
                // 按名字创建。
                encoder = MediaCodec.createByCodecName(encoderName);
            } catch (Throwable error) {
                // 失败分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 构造编码器格式。
            MediaFormat encoderFormat = MediaFormat.createAudioFormat(target.audioMime, targetSampleRate, targetChannels);
            // 设置 AAC Profile 为 LC：兼容性最好的 AAC 档次。
            encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            // 设置码率。
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate);
            // 设置单次输入上限，避免编码器要求巨大缓冲导致内存浪费。
            encoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_CHUNK_BYTES);
            // 配置并启动编码器。
            try {
                // 配置。
                encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                // 启动。
                encoder.start();
            } catch (Throwable error) {
                // 失败分类上报。
                throw new ConversionException(ErrorReason.CODEC_INIT_FAILED, error);
            }
            // 创建封装器。
            try {
                // 打开输出。
                muxer = output.openMuxer(target.muxerFormat);
            } catch (java.io.IOException error) {
                // IO 失败分类上报。
                throw new ConversionException(ErrorReason.IO_ERROR, error);
            } catch (Throwable error) {
                // 其它归类为封装器错误。
                throw new ConversionException(ErrorReason.MUXER_ERROR, error);
            }
            // 是否需要 PCM 变换：采样率或声道与源不同时才需要。
            boolean needPcmWork = targetSampleRate != sourceSampleRate || targetChannels != sourceChannels;
            // 构造 PCM 变换器（内部按需重采样与声道变换）。
            PcmProcessor processor = new PcmProcessor(sourceSampleRate, sourceChannels, targetSampleRate, targetChannels);
            // 选中音频轨。
            extractor.selectTrack(audioTrack);
            // 复用缓冲元信息。
            MediaCodec.BufferInfo decoderInfo = new MediaCodec.BufferInfo();
            // 复用编码输出元信息。
            MediaCodec.BufferInfo encoderInfo = new MediaCodec.BufferInfo();
            // 输入是否结束。
            boolean inputDone = false;
            // 编码是否结束。
            boolean encoderDone = false;
            // 封装器是否已启动。
            boolean muxerStarted = false;
            // 音频轨在封装器中的下标。
            int muxerTrack = -1;
            // 待送入编码器的 PCM 数据（可能因编码器输入缓冲较小而分多次送入）。
            byte[] pending = new byte[64 * 1024];
            // 待送入数据的有效长度。
            int pendingLength = 0;
            // 待送入数据的起始偏移。
            int pendingOffset = 0;
            // 待送入数据的时间戳（微秒）。
            long pendingPts = 0L;
            // 是否已经有待送入数据。
            boolean hasPending = false;
            // 进度节流计数。
            int chunkCounter = 0;
            // 主循环。
            while (!encoderDone) {
                // 阶段一：把压缩样本喂给解码器。
                if (!inputDone) {
                    // 取解码器输入缓冲。
                    int inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    // 可用时投喂。
                    if (inputIndex >= 0) {
                        // 取缓冲对象。
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        // 读取样本。
                        int sampleSize = inputBuffer == null ? -1 : extractor.readSampleData(inputBuffer, 0);
                        // 到达末尾时送入 EOS。
                        if (sampleSize < 0) {
                            // 送入空缓冲 + EOS。
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            // 标记输入结束。
                            inputDone = true;
                        } else {
                            // 取时间戳。
                            long sampleTime = extractor.getSampleTime();
                            // 送入解码器。
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                            // 推进。
                            extractor.advance();
                            // 记录进度。
                            processedUs = sampleTime;
                        }
                    }
                }
                // 阶段二：排空解码器，得到 PCM 并把 PCM 送入编码器。
                while (true) {
                    // 取解码输出。
                    int outputIndex = decoder.dequeueOutputBuffer(decoderInfo, 0L);
                    // 输出格式变化：此时可校验 PCM 编码类型是否为 16 位。
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // 取输出格式。
                        MediaFormat outFormat = decoder.getOutputFormat();
                        // 若设备明确给出 PCM 编码，则要求它是 16 位，否则无法做本层的重采样。
                        if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            // 读取 PCM 编码常量。
                            int pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                            // 非 16 位时给出明确错误，避免产出杂音。
                            if (pcmEncoding != 2) {
                                // 抛出解码失败并说明具体编码值。
                                throw new ConversionException(ErrorReason.DECODE_FAILED,
                                        "Decoder produced non-16-bit PCM (pcm encoding " + pcmEncoding + ")");
                            }
                        }
                        // 顺便校对源采样率：若解码器给出的采样率与输入格式不同，以解码器为准。
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            // 更新源采样率。
                            sourceSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        }
                        // 继续排空。
                        continue;
                    }
                    // 无输出时结束本次排空。
                    if (outputIndex < 0) {
                        // 跳出循环。
                        break;
                    }
                    // 取输出缓冲。
                    ByteBuffer decoded = decoder.getOutputBuffer(outputIndex);
                    // 判断是否为 EOS 标志。
                    boolean decodeEos = (decoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    // 有实际数据时取出为字节数组并做 PCM 变换。
                    if (decoderInfo.size > 0 && decoded != null) {
                        // 让缓冲指向本段数据。
                        decoded.position(decoderInfo.offset);
                        // 设置读取上限。
                        decoded.limit(decoderInfo.offset + decoderInfo.size);
                        // 取出原始 PCM 字节。
                        byte[] raw = new byte[decoderInfo.size];
                        // 执行读取。
                        decoded.get(raw);
                        // 按需做重采样与声道变换；不需要时直接使用原始字节，避免无意义拷贝。
                        byte[] pcm = needPcmWork ? processor.process(raw) : raw;
                        // 结果长度大于 0 时才进入待送队列。
                        if (pcm.length > 0) {
                            // 确保待送缓冲足够大。
                            if (pending.length < pcm.length) {
                                // 扩容为原来的两倍或刚好容纳。
                                pending = new byte[Math.max(pcm.length, pending.length * 2)];
                            }
                            // 拷贝到待送缓冲。
                            System.arraycopy(pcm, 0, pending, 0, pcm.length);
                            // 记录有效长度。
                            pendingLength = pcm.length;
                            // 重置偏移。
                            pendingOffset = 0;
                            // 记录时间戳（沿用解码输出的时间戳，保证时间轴连续）。
                            pendingPts = decoderInfo.presentationTimeUs;
                            // 标记有待送数据。
                            hasPending = true;
                        }
                    }
                    // 释放解码输出缓冲（ByteBuffer 模式不需要渲染）。
                    decoder.releaseOutputBuffer(outputIndex, false);
                    // 收到 EOS 时把剩余 PCM 送完后通知编码器输入结束。
                    if (decodeEos) {
                        // 先把待送数据全部送入编码器。
                        while (hasPending) {
                            // 尝试取编码器输入缓冲。
                            int encIdx = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                            // 拿不到就继续尝试（此处可能循环几次，属于正常情况）。
                            if (encIdx < 0) {
                                // 继续重试。
                                continue;
                            }
                            // 取缓冲对象。
                            ByteBuffer encBuf = encoder.getInputBuffer(encIdx);
                            // 缓冲不可用时释放并继续。
                            if (encBuf == null) {
                                // 释放该次申请。
                                encoder.releaseOutputBuffer(encIdx, false);
                                // 继续重试。
                                continue;
                            }
                            // 清空缓冲准备写入。
                            encBuf.clear();
                            // 计算本次可拷贝长度。
                            int copy = Math.min(pendingLength - pendingOffset, encBuf.remaining());
                            // 写入数据。
                            encBuf.put(pending, pendingOffset, copy);
                            // 送入编码器。
                            encoder.queueInputBuffer(encIdx, 0, copy, pendingPts, 0);
                            // 推进偏移。
                            pendingOffset += copy;
                            // 数据送完后清除标记。
                            if (pendingOffset >= pendingLength) {
                                // 标记没有待送数据。
                                hasPending = false;
                            }
                        }
                        // 送入编码器 EOS。
                        int encIdx = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                        // 拿到缓冲时送入结束标志。
                        if (encIdx >= 0) {
                            // 送入空缓冲 + EOS。
                            encoder.queueInputBuffer(encIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                    }
                }
                // 阶段三：把待送 PCM 送入编码器（正常路径，非 EOS 时也要及时送，避免延迟）。
                while (hasPending) {
                    // 取编码器输入缓冲。
                    int encIdx = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    // 不可用时跳出，等待下一轮循环继续送。
                    if (encIdx < 0) {
                        // 结束本次送数据。
                        break;
                    }
                    // 取缓冲对象。
                    ByteBuffer encBuf = encoder.getInputBuffer(encIdx);
                    // 缓冲不可用时释放并跳出。
                    if (encBuf == null) {
                        // 释放申请。
                        encoder.releaseOutputBuffer(encIdx, false);
                        // 跳出等待下一轮。
                        break;
                    }
                    // 清空缓冲。
                    encBuf.clear();
                    // 计算本次可拷贝长度。
                    int copy = Math.min(pendingLength - pendingOffset, encBuf.remaining());
                    // 写入数据。
                    encBuf.put(pending, pendingOffset, copy);
                    // 送入编码器。
                    encoder.queueInputBuffer(encIdx, 0, copy, pendingPts, 0);
                    // 推进偏移。
                    pendingOffset += copy;
                    // 数据送完后清除标记。
                    if (pendingOffset >= pendingLength) {
                        // 标记无待送数据。
                        hasPending = false;
                    }
                }
                // 阶段四：排空编码器，把 AAC 样本写入封装器。
                while (true) {
                    // 取编码输出。
                    int encodedIndex = encoder.dequeueOutputBuffer(encoderInfo, CODEC_TIMEOUT_US);
                    // 输出格式变化：添加轨道并启动封装器。
                    if (encodedIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // 取编码器输出格式。
                        MediaFormat encodedFormat = encoder.getOutputFormat();
                        // 添加音频轨。
                        muxerTrack = muxer.addTrack(encodedFormat);
                        // 启动封装器。
                        muxer.start();
                        // 标记已启动。
                        muxerStarted = true;
                        // 继续排空。
                        continue;
                    }
                    // 无输出时结束排空。
                    if (encodedIndex < 0) {
                        // 跳出。
                        break;
                    }
                    // 取输出缓冲。
                    ByteBuffer encodedBuf = encoder.getOutputBuffer(encodedIndex);
                    // CSD 数据已经在输出格式里交给封装器，此处跳过。
                    if ((encoderInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // 长度清零。
                        encoderInfo.size = 0;
                    }
                    // 有效数据且封装器已启动时写入。
                    if (muxerStarted && encodedBuf != null && encoderInfo.size > 0 && muxerTrack >= 0) {
                        // 限定范围。
                        encodedBuf.position(encoderInfo.offset);
                        // 设置上限。
                        encodedBuf.limit(encoderInfo.offset + encoderInfo.size);
                        // 写入容器。
                        muxer.writeSampleData(muxerTrack, encodedBuf, encoderInfo);
                        // 节流上报进度。
                        chunkCounter++;
                        // 到达间隔时上报。
                        if (chunkCounter % PROGRESS_INTERVAL_CHUNKS == 0) {
                            // 上报进度。
                            callback.reportProgress(durationUs > 0L ? Math.min(0.99, (double) processedUs / durationUs) : 0.0,
                                    0.0, "Re-encoding audio " + target.label);
                            // 检查暂停/取消。
                            callback.checkPaused();
                        }
                    }
                    // 判断是否为 EOS。
                    boolean encoderEos = (encoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    // 释放输出缓冲。
                    encoder.releaseOutputBuffer(encodedIndex, false);
                    // 收到 EOS 时结束主循环。
                    if (encoderEos) {
                        // 标记结束。
                        encoderDone = true;
                    }
                }
                // 每轮检查取消。
                if (callback.shouldCancel()) {
                    // 抛出取消异常。
                    throw new ConversionException(ErrorReason.CANCELED, "Canceled during audio transcode");
                }
            }
            // 上报完成。
            callback.reportProgress(1.0, 0.0, "Finalising " + target.label);
            // 结束封装。
            muxer.stop();
            // 释放封装器。
            muxer.release();
            // 置空引用。
            muxer = null;
            // 必要时搬运临时文件。
            output.finalizeOutput();
            // 返回轨道数。
            return 1;
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (SecurityException error) {
            // 权限问题分类上报。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (IllegalStateException error) {
            // 编解码器状态异常归类为编解码器错误。
            throw new ConversionException(ErrorReason.CODEC_ERROR, error);
        } catch (IllegalArgumentException error) {
            // 参数被拒绝归类为格式不支持。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, error);
        } catch (java.io.IOException error) {
            // IO 失败细分。
            throw classifyIoError(error);
        } catch (Throwable error) {
            // 其它归类为未知。
            throw new ConversionException(ErrorReason.UNKNOWN, error);
        } finally {
            // 释放解码器。
            if (decoder != null) {
                // 停止。
                try {
                    // 停止解码器。
                    decoder.stop();
                } catch (Throwable ignored) {
                    // 忽略。
                }
                // 释放。
                try {
                    // 释放资源。
                    decoder.release();
                } catch (Throwable ignored) {
                    // 忽略。
                }
            }
            // 释放编码器。
            if (encoder != null) {
                // 停止。
                try {
                    // 停止编码器。
                    encoder.stop();
                } catch (Throwable ignored) {
                    // 忽略。
                }
                // 释放。
                try {
                    // 释放资源。
                    encoder.release();
                } catch (Throwable ignored) {
                    // 忽略。
                }
            }
            // 释放封装器（异常路径）。
            if (muxer != null) {
                // 尝试停止。
                try {
                    // 停止封装。
                    muxer.stop();
                } catch (Throwable ignored) {
                    // 忽略。
                }
                // 释放。
                try {
                    // 释放资源。
                    muxer.release();
                } catch (Throwable ignored) {
                    // 忽略。
                }
            }
            // 释放抽取器。
            extractor.release();
        }
    }

    /**
     * 把采样率裁剪到 AAC 编码器支持的常见取值（更稳妥的做法是直接对齐到标准采样率表）。
     */
    private static int clampSampleRate(int requested) {
        // AAC 支持的标准采样率集合。
        int[] standard = new int[]{8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};
        // 找到与请求值最接近的标准采样率，保证编码器一定接受该配置。
        int best = standard[0];
        // 记录最小差值。
        int bestDiff = Integer.MAX_VALUE;
        // 遍历标准表。
        for (int value : standard) {
            // 计算差值。
            int diff = Math.abs(value - requested);
            // 更接近时更新。
            if (diff < bestDiff) {
                // 更新最小差值。
                bestDiff = diff;
                // 更新最优值。
                best = value;
            }
        }
        // 返回最接近的标准值。
        return best;
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

    /**
     * PcmProcessor 对 16 位小端交错 PCM 做重采样与声道变换。
     * <p>
     * 为什么需要它：MediaCodec 的 AAC 编码器不会重采样，也不会做声道上混/下混。
     * 如果不做这一步，“采样率/声道可调”就只是界面上好看、实际无效的假参数。
     * 重采样使用线性插值：算法简单、无额外依赖，对语音与音乐都有可接受的听感。
     */
    private static final class PcmProcessor {

        // 源采样率。
        private final int srcRate;
        // 源声道数。
        private final int srcChannels;
        // 目标采样率。
        private final int dstRate;
        // 目标声道数。
        private final int dstChannels;
        // 重采样进度累加器（以“输出采样位置”为单位，跨块保留以消除块边界断裂）。
        private double position = 0.0;

        // 构造：保存两端参数。
        PcmProcessor(int srcRate, int srcChannels, int dstRate, int dstChannels) {
            // 源采样率兜底为 44100，避免出现 0 导致除零。
            this.srcRate = srcRate > 0 ? srcRate : 44100;
            // 源声道数兜底为 2。
            this.srcChannels = srcChannels > 0 ? srcChannels : 2;
            // 目标采样率。
            this.dstRate = dstRate > 0 ? dstRate : this.srcRate;
            // 目标声道数。
            this.dstChannels = dstChannels > 0 ? dstChannels : this.srcChannels;
        }

        /**
         * 处理一整块 PCM，返回处理后的字节数组（可能长度不同）。
         */
        byte[] process(byte[] input) {
            // 输入长度必须是（2 字节 * 声道数）的整数倍，截断掉尾部不完整样本。
            int frameBytesSrc = 2 * srcChannels;
            // 计算完整帧数。
            int srcFrames = input.length / frameBytesSrc;
            // 没有任何完整帧时返回空数组。
            if (srcFrames <= 0) {
                // 返回空结果。
                return new byte[0];
            }
            // 计算重采样比例。
            double ratio = (double) dstRate / (double) srcRate;
            // 估算需要的输出帧数（多算 2 帧作为插值余量）。
            int maxOutFrames = (int) Math.ceil(srcFrames * ratio) + 2;
            // 分配输出缓冲。
            byte[] output = new byte[maxOutFrames * 2 * dstChannels];
            // 输出帧计数。
            int outFrames = 0;
            // 输入读取位置（帧为单位，小数部分通过线性插值体现）。
            double srcPos = position;
            // 循环生成输出帧。
            while (srcPos < srcFrames && outFrames < maxOutFrames) {
                // 取左端点索引。
                int idx0 = (int) Math.floor(srcPos);
                // 取右端点索引。
                int idx1 = Math.min(srcFrames - 1, idx0 + 1);
                // 计算插值权重。
                double frac = srcPos - idx0;
                // 逐目标声道填充数据。
                for (int ch = 0; ch < dstChannels; ch++) {
                    // 计算该目标声道在源中的取值（先按声道映射取样）。
                    double left = readSample(input, idx0, ch, frameBytesSrc);
                    // 读取右端点样本。
                    double right = readSample(input, idx1, ch, frameBytesSrc);
                    // 线性插值。
                    double value = left + (right - left) * frac;
                    // 做幅度保护，避免插值越界造成削顶噪声。
                    int sample = (int) Math.round(Math.max(-32768.0, Math.min(32767.0, value)));
                    // 写入小端 16 位。
                    int outOffset = (outFrames * dstChannels + ch) * 2;
                    // 低字节。
                    output[outOffset] = (byte) (sample & 0xFF);
                    // 高字节。
                    output[outOffset + 1] = (byte) ((sample >> 8) & 0xFF);
                }
                // 输出帧计数递增。
                outFrames++;
                // 推进源位置。
                srcPos += 1.0 / ratio;
            }
            // 保存剩余位置，供下一块继续插值，从而避免块边界出现咔哒声。
            position = srcPos - srcFrames;
            // 位置为负时归零，防止下一块从负索引开始。
            if (position < 0.0) {
                // 归零。
                position = 0.0;
            }
            // 计算实际输出字节数。
            int usedBytes = outFrames * 2 * dstChannels;
            // 长度一致时直接返回。
            if (usedBytes == output.length) {
                // 返回完整数组。
                return output;
            }
            // 否则截断到实际使用长度。
            byte[] trimmed = new byte[usedBytes];
            // 复制有效部分。
            System.arraycopy(output, 0, trimmed, 0, usedBytes);
            // 返回截断结果。
            return trimmed;
        }

        /**
         * 按目标声道序号从源帧中取一个样本值（负责上混/下混映射）。
         */
        private double readSample(byte[] input, int frameIndex, int dstChannel, int frameBytesSrc) {
            // 计算源帧起始偏移。
            int base = frameIndex * frameBytesSrc;
            // 单声道源：两个目标声道都取同一个样本。
            if (srcChannels == 1) {
                // 直接读取唯一声道。
                return readShort(input, base);
            }
            // 立体声源 + 单声道目标：取左右声道平均值做下混，避免只取单边造成音量偏小。
            if (dstChannels == 1) {
                // 读取左声道。
                double left = readShort(input, base);
                // 读取右声道。
                double right = readShort(input, base + 2);
                // 返回平均值。
                return (left + right) / 2.0;
            }
            // 多声道源：目标声道序号超出源声道数时循环映射，保证不越界。
            int srcChannel = Math.min(srcChannels - 1, dstChannel);
            // 返回对应声道样本。
            return readShort(input, base + srcChannel * 2);
        }

        /**
         * 从字节数组读取一个小端 16 位有符号整数。
         */
        private double readShort(byte[] input, int offset) {
            // 偏移越界时返回 0，避免数组越界异常。
            if (offset < 0 || offset + 1 >= input.length) {
                // 返回静音。
                return 0.0;
            }
            // 取低字节。
            int low = input[offset] & 0xFF;
            // 取高字节。
            int high = input[offset + 1] & 0xFF;
            // 组合为有符号 16 位。
            int value = (short) ((high << 8) | low);
            // 返回样本值。
            return value;
        }
    }
}
