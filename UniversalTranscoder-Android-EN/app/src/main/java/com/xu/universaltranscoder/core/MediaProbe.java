package com.xu.universaltranscoder.core;

// 引入 Context：MediaMetadataRetriever/MediaExtractor 需要通过 Context 打开 content:// 数据源。

import android.content.Context;
// 引入 ContentResolver：查询文件名等元数据。
import android.content.ContentResolver;
// 引入 Uri：源文件地址。
import android.net.Uri;
// 引入 OpenableColumns：标准列名，随提供者实现变化但列名规范固定。
import android.provider.OpenableColumns;
// 引入 Cursor：读取查询结果。
import android.database.Cursor;
// 引入 MediaExtractor：逐轨道读取格式信息。
import android.media.MediaExtractor;
// 引入 MediaFormat：从 TrackFormat 中取宽高、采样率等。
import android.media.MediaFormat;
// 引入 MediaMetadataRetriever：读取时长、码率、旋转角等整体信息。
import android.media.MediaMetadataRetriever;

// 引入 MediaInfo：本类负责填充的数据模型。
import com.xu.universaltranscoder.model.MediaInfo;

// 引入 File：判断剩余空间。
import java.io.File;
// 引入 Locale：MIME 小写比较。
import java.util.Locale;

/**
 * MediaProbe 用系统 API 读取媒体文件的真实元信息。
 * 它是“媒体信息展示”功能与“转换前置校验”的共同基础：
 * 转换前先探测轨道，才能判断“这次重封装会不会因为容器不接受该编码而必然失败”。
 */
public final class MediaProbe {

    // 私有构造：工具类不允许实例化。
    private MediaProbe() {
        // 空实现，禁止外部 new。
    }

    /**
     * 探测指定 URI 的媒体信息。
     * 任何失败都会转换为带分类的 ConversionException，交由上层给出可读提示。
     */
    public static MediaInfo probe(Context context, Uri uri) throws ConversionException {
        // 新建结果对象。
        MediaInfo info = new MediaInfo();
        // 记录 URI 字符串，便于日志展示。
        info.uriString = uri == null ? "" : uri.toString();
        // 查询显示名；查询失败时退回从 URI 路径推断。
        info.displayName = queryDisplayName(context, uri);
        // 用 MediaMetadataRetriever 读取整体信息。
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            // 绑定数据源；对 content:// 使用带 Context 的重载，由系统打开文件描述符。
            retriever.setDataSource(context, uri);
            // 读取时长（毫秒），读取失败返回 "0"。
            info.durationUs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000L;
            // 读取视频宽。
            info.width = (int) parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            // 读取视频高。
            info.height = (int) parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            // 读取总码率（比特/秒）。
            info.bitrate = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
            // 读取旋转角。
            info.rotation = (int) parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        } catch (IllegalArgumentException error) {
            // 数据源无法识别：多数情况是文件已不存在、或不是本设备可解析的媒体格式。
            throw new ConversionException(ErrorReason.FILE_NOT_FOUND, error);
        } catch (SecurityException error) {
            // SAF 授权失效或被系统拒绝。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (Throwable error) {
            // 其它异常归类为解码/格式问题。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, error);
        } finally {
            // 释放检索器，避免文件句柄泄漏。
            releaseQuietly(retriever);
        }
        // 用 MediaExtractor 读取逐轨道信息。
        MediaExtractor extractor = new MediaExtractor();
        try {
            // 绑定数据源。
            extractor.setDataSource(context, uri, null);
            // 遍历全部轨道。
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                // 取得该轨道的格式描述。
                MediaFormat format = extractor.getTrackFormat(i);
                // 读取 MIME；缺失时用空字符串兜底。
                String mime = format.containsKey(MediaFormat.KEY_MIME) ? format.getString(MediaFormat.KEY_MIME) : "";
                // 统一小写便于比较。
                String lower = mime == null ? "" : mime.toLowerCase(Locale.US);
                // 判断轨道类型。
                boolean isVideo = lower.startsWith("video/");
                // 判断是否为音频轨。
                boolean isAudio = lower.startsWith("audio/");
                // 读取视频宽（仅视频轨含该键）。
                int trackWidth = format.containsKey(MediaFormat.KEY_WIDTH) ? format.getInteger(MediaFormat.KEY_WIDTH) : 0;
                // 读取视频高。
                int trackHeight = format.containsKey(MediaFormat.KEY_HEIGHT) ? format.getInteger(MediaFormat.KEY_HEIGHT) : 0;
                // 读取音频采样率。
                int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 0;
                // 读取音频声道数。
                int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 0;
                // 读取轨道语言（可能不存在）。
                String language = format.containsKey(MediaFormat.KEY_LANGUAGE) ? format.getString(MediaFormat.KEY_LANGUAGE) : "";
                // 加入轨道列表。
                info.tracks.add(new MediaInfo.Track(i, mime, isVideo, isAudio, trackWidth, trackHeight, sampleRate, channelCount, language));
                // 如果整体信息的宽高缺失而轨道上有，则用轨道值补上，保证界面展示不缺数据。
                if (isVideo && (info.width <= 0 || info.height <= 0)) {
                    // 补齐宽度。
                    info.width = trackWidth;
                    // 补齐高度。
                    info.height = trackHeight;
                }
            }
        } catch (IllegalArgumentException error) {
            // 数据源非法：直接归类为文件不可读。
            throw new ConversionException(ErrorReason.FILE_NOT_FOUND, error);
        } catch (SecurityException error) {
            // 权限问题。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } catch (Throwable error) {
            // 其它异常归类为格式不支持。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, error);
        } finally {
            // 释放抽取器。
            extractor.release();
        }
        // 轨道数量为 0 说明文件里没有可用流。
        if (info.tracks.isEmpty()) {
            // 抛出“无轨道”分类错误。
            throw new ConversionException(ErrorReason.NO_TRACK, "MediaExtractor reported zero tracks");
        }
        // 返回探测结果。
        return info;
    }

    /**
     * 查询 SAF 文档的显示名。
     */
    public static String queryDisplayName(Context context, Uri uri) {
        // 非 content:// 地址时直接从路径推断。
        if (uri == null || !"content".equals(uri.getScheme())) {
            // 返回推断出的名字。
            return com.xu.universaltranscoder.util.FormatUtil.baseNameFromUri(uri == null ? "" : uri.toString());
        }
        // 准备游标。
        Cursor cursor = null;
        try {
            // 查询 DISPLAY_NAME 列。
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            // 有结果行时读取第一行。
            if (cursor != null && cursor.moveToFirst()) {
                // 读取列下标。
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                // 列存在则读取字符串。
                if (index >= 0) {
                    // 取得显示名。
                    String name = cursor.getString(index);
                    // 非空则直接返回，并去掉扩展名留给调用方自行拼接。
                    if (name != null && !name.isEmpty()) {
                        // 返回完整显示名（含扩展名，因为界面展示需要）。
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 查询失败（提供者不支持该列等）时退回路径推断，不向上抛异常。
        } finally {
            // 关闭游标。
            if (cursor != null) {
                // 释放资源。
                cursor.close();
            }
        }
        // 查询无果时退回路径推断。
        return com.xu.universaltranscoder.util.FormatUtil.baseNameFromUri(uri.toString());
    }

    /**
     * 判断输出目录所在卷的剩余空间是否足够。
     * 之所以要提前判断：编码到一半才因空间不足失败，用户会白等很久，而且容易留下半成品文件。
     */
    public static boolean hasFreeSpace(Context context, File directory, long requiredBytes) {
        // 目录为空时退化为检查应用缓存目录。
        File target = directory == null ? context.getCacheDir() : directory;
        // 得到可用字节数。
        long usable = target.getUsableSpace();
        // 可用空间大于所需字节数加 8MB 余量时认为足够。
        return usable > requiredBytes + 8L * 1024L * 1024L;
    }

    /**
     * 把 MediaMetadataRetriever 返回的字符串安全转换为 long。
     */
    private static long parseLong(String value) {
        // 空值或非法值统一返回 0。
        if (value == null) {
            // 返回 0。
            return 0L;
        }
        // 解析并捕获异常。
        try {
            // 返回解析结果。
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            // 解析失败返回 0。
            return 0L;
        }
    }

    /**
     * 安静地释放 MediaMetadataRetriever。
     */
    private static void releaseQuietly(MediaMetadataRetriever retriever) {
        // 检索器非空才释放。
        if (retriever == null) {
            // 无需处理。
            return;
        }
        // 释放可能抛异常（部分设备在无数据源时释放会报错），这里忽略。
        try {
            // 执行释放。
            retriever.release();
        } catch (Throwable ignored) {
            // 忽略释放异常。
        }
    }

    /**
     * 判断源文件的扩展名是否表示 PDF，用于文档转换的输入校验。
     */
    public static boolean isPdf(String displayNameOrPath) {
        // 空值直接判定不是 PDF。
        if (displayNameOrPath == null) {
            // 返回假。
            return false;
        }
        // 统一小写后判断后缀。
        return displayNameOrPath.toLowerCase(Locale.US).endsWith(".pdf");
    }

    /**
     * 判断源文件是否可能是纯文本，用于文本转 PDF 的输入校验。
     */
    public static boolean isPlainText(String displayNameOrPath) {
        // 空值直接判定不是文本。
        if (displayNameOrPath == null) {
            // 返回假。
            return false;
        }
        // 统一小写后判断常见文本后缀。
        String lower = displayNameOrPath.toLowerCase(Locale.US);
        // 逐个比较。
        return lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md")
                || lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".xml");
    }

    /**
     * 判断源文件是否属于图片后缀集合，用于“图片合成 PDF”的输入校验。
     */
    public static boolean isImageFile(String displayNameOrPath) {
        // 空值直接判定不是图片。
        if (displayNameOrPath == null) {
            // 返回假。
            return false;
        }
        // 统一小写后判断常见图片后缀。
        String lower = displayNameOrPath.toLowerCase(Locale.US);
        // 逐个比较。
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".heic")
                || lower.endsWith(".heif") || lower.endsWith(".gif");
    }
}
