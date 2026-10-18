package com.xu.universaltranscoder.core;

// 引入 Context：打开 content:// 数据源需要上下文。

import android.content.Context;
// 引入 Uri：源地址。
import android.net.Uri;
// 引入 Bitmap：位图对象。
import android.graphics.Bitmap;
// 引入 BitmapFactory：解码图片并支持采样率控制。
import android.graphics.BitmapFactory;

// 引入 ConvertOptions：读取质量与缩放参数。
import com.xu.universaltranscoder.model.ConvertOptions;
// 引入 TargetSpec：目标图片格式。
import com.xu.universaltranscoder.model.TargetSpec;
// 引入 OutputTarget：输出位置抽象。
import com.xu.universaltranscoder.util.OutputTarget;

// 引入 InputStream：读取源图片字节流。
import java.io.InputStream;
// 引入 OutputStream：写出目标图片。
import java.io.OutputStream;
// 引入 Locale：MIME 比较。

/**
 * ImageConverter 用 BitmapFactory + Bitmap.compress 完成图片格式转换。
 * <p>
 * 实现要点与原因：
 * 1) 先用 inJustDecodeBounds 只读取图片尺寸（不分配像素内存），再据此决定采样率。
 * 原因：手机相机照片动辄 8000x6000（约 190MB 内存），直接整张解码极易 OOM。
 * 2) 采样率 inSampleSize 取 2 的幂次，BitmapFactory 才能高效利用硬件解码。
 * 3) 需要精确尺寸时，再对采样后的位图做一次 createScaledBitmap。
 * 4) 压缩格式与质量直接来自目标规格与用户参数，PNG 会忽略质量参数（无损格式）。
 */
public final class ImageConverter {

    // 未指定目标尺寸时的最大边长限制：4096 足够覆盖绝大多数用途，同时把内存峰值控制在可接受范围。
    private static final int MAX_UNSPECIFIED_DIMENSION = 4096;
    // 解码时的最大像素数预算（约 16M 像素 ≈ 64MB 位图内存），超过则继续提高采样率。
    private static final long MAX_PIXEL_BUDGET = 16L * 1000L * 1000L;

    // 私有构造：工具类不允许实例化。
    private ImageConverter() {
        // 空实现，禁止外部 new。
    }

    /**
     * 执行图片转换。
     *
     * @return 输出文件的展示路径，便于写入日志。
     */
    public static String convert(Context context, Uri sourceUri, TargetSpec target, ConvertOptions options,
                                 OutputTarget output, ProgressCallback callback) throws ConversionException {
        // 目标不受支持时立即失败。
        if (!target.supported || target.compressFormat == null) {
            // 抛出格式不支持分类错误。
            throw new ConversionException(ErrorReason.FORMAT_UNSUPPORTED, target.unsupportedReason);
        }
        // 上报开始解码。
        callback.reportProgress(0.05, 0.0, "正在解码图片");
        // 检查取消。
        if (callback.shouldCancel()) {
            // 抛出取消异常。
            throw new ConversionException(ErrorReason.CANCELED);
        }
        // 第一次解码：只读取尺寸。
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        // 只取边界，不分配像素内存，这是避免 OOM 的关键开关。
        boundsOptions.inJustDecodeBounds = true;
        // 打开输入流读取边界信息。
        InputStream boundsStream = null;
        try {
            // 打开源文件的输入流。
            boundsStream = context.getContentResolver().openInputStream(sourceUri);
            // 源打开失败说明文件不可读。
            if (boundsStream == null) {
                // 抛出权限分类错误（多数情况是 SAF 授权失效）。
                throw new ConversionException(ErrorReason.PERMISSION_DENIED, "无法打开源文件输入流");
            }
            // 解析边界。
            BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (java.io.FileNotFoundException error) {
            // 文件不存在。
            throw new ConversionException(ErrorReason.FILE_NOT_FOUND, error);
        } catch (SecurityException error) {
            // 权限问题。
            throw new ConversionException(ErrorReason.PERMISSION_DENIED, error);
        } finally {
            // 关闭边界流。
            closeQuietly(boundsStream);
        }
        // 源图片宽高。
        int sourceWidth = boundsOptions.outWidth;
        // 源图片高。
        int sourceHeight = boundsOptions.outHeight;
        // 尺寸非法说明解码器无法识别该图片。
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            // 抛出解码失败分类错误。
            throw new ConversionException(ErrorReason.DECODE_FAILED, "BitmapFactory 返回了无效的尺寸");
        }
        // 计算目标解码上限：用户指定了宽或高就按用户意图，否则按默认上限。
        int limitWidth = options.imageTargetWidth > 0 ? options.imageTargetWidth : MAX_UNSPECIFIED_DIMENSION;
        // 计算目标高度上限。
        int limitHeight = options.imageTargetHeight > 0 ? options.imageTargetHeight : MAX_UNSPECIFIED_DIMENSION;
        // 计算采样率（2 的幂次）。
        int sampleSize = computeSampleSize(sourceWidth, sourceHeight, limitWidth, limitHeight);
        // 第二次解码：按采样率读取真实像素。
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        // 使用 RGB_8888 保证 PNG 透明度与 WEBP 质量；如果内存紧张可换 565，但会丢失透明度。
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // 指定采样率。
        decodeOptions.inSampleSize = sampleSize;
        // 上报解码进度。
        callback.reportProgress(0.35, 0.0, "正在解码图片（采样 1/" + sampleSize + "）");
        // 打开输入流进行解码。
        InputStream decodeStream = null;
        // 位图引用。
        Bitmap decoded = null;
        try {
            // 打开源输入流（需要重新打开，因为前一个流已被消费）。
            decodeStream = context.getContentResolver().openInputStream(sourceUri);
            // 源打开失败说明文件不可读。
            if (decodeStream == null) {
                // 抛出权限分类错误。
                throw new ConversionException(ErrorReason.PERMISSION_DENIED, "无法重新打开源文件输入流");
            }
            // 执行解码。
            decoded = BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (OutOfMemoryError error) {
            // 内存不足时给出明确提示：建议用户在界面上设置更小的目标尺寸。
            throw new ConversionException(ErrorReason.DECODE_FAILED,
                    "解码时内存不足，请设置更小的目标尺寸");
        } catch (java.io.IOException error) {
            // IO 失败。
            throw new ConversionException(ErrorReason.IO_ERROR, error);
        } finally {
            // 关闭解码流。
            closeQuietly(decodeStream);
        }
        // 解码结果为 null 说明文件损坏或格式不被支持。
        if (decoded == null) {
            // 抛出解码失败分类错误。
            throw new ConversionException(ErrorReason.DECODE_FAILED, "BitmapFactory 返回空结果");
        }
        // 需要在解码后声明输出位图，便于在 finally 中回收。
        Bitmap finalBitmap = decoded;
        try {
            // 判断是否需要二次精确缩放：用户指定了尺寸或百分比且与当前位图不一致时进行。
            int desiredWidth;
            // 计算期望高度。
            int desiredHeight;
            // 百分比缩放优先：这是界面 "<数字>%" 表达式的实现路径。
            if (options.scalePercent > 0) {
                // 按百分比换算目标宽。
                desiredWidth = Math.max(1, (int) Math.round(decoded.getWidth() * options.scalePercent / 100.0));
                // 按百分比换算目标高。
                desiredHeight = Math.max(1, (int) Math.round(decoded.getHeight() * options.scalePercent / 100.0));
            } else {
                // 未启用百分比时使用显式宽高。
                desiredWidth = options.imageTargetWidth > 0 ? options.imageTargetWidth : decoded.getWidth();
                // 用户只给了宽度时按比例推算高度，避免拉伸变形。
                desiredHeight = options.imageTargetHeight > 0 ? options.imageTargetHeight
                        : (options.imageTargetWidth > 0
                        ? Math.max(1, (int) Math.round(decoded.getHeight() * (double) desiredWidth / decoded.getWidth()))
                        : decoded.getHeight());
            }
            // 仅当尺寸确实不同才做缩放，避免无意义的内存拷贝。
            if (desiredWidth != decoded.getWidth() || desiredHeight != decoded.getHeight()) {
                // 上报缩放进度。
                callback.reportProgress(0.6, 0.0, "正在缩放到 " + desiredWidth + "x" + desiredHeight);
                // 执行双线性缩放。
                Bitmap scaled = Bitmap.createScaledBitmap(decoded, desiredWidth, desiredHeight, true);
                // 缩放产生新对象时替换引用。
                finalBitmap = scaled;
            }
            // 检查取消。
            if (callback.shouldCancel()) {
                // 抛出取消异常。
                throw new ConversionException(ErrorReason.CANCELED);
            }
            // 上报编码进度。
            callback.reportProgress(0.8, 0.0, "正在编码 " + target.label);
            // 打开输出流。
            OutputStream out = output.openStream();
            // 编码结果标记。
            boolean ok = false;
            try {
                // 执行压缩；PNG 为无损格式，质量参数被忽略但仍需传入合法值。
                ok = finalBitmap.compress(target.compressFormat, clampQuality(options.imageQuality), out);
                // 强制刷出缓冲。
                out.flush();
            } finally {
                // 关闭输出流。
                closeQuietly(out);
            }
            // 压缩返回 false 说明编码失败。
            if (!ok) {
                // 抛出 IO 错误并说明是编码阶段失败。
                throw new ConversionException(ErrorReason.IO_ERROR, "Bitmap.compress 返回失败");
            }
            // 上报完成。
            callback.reportProgress(1.0, 0.0, "已保存 " + output.fileName());
            // 返回输出路径用于日志。
            return output.displayPath();
        } catch (ConversionException error) {
            // 分类异常上抛。
            throw error;
        } catch (OutOfMemoryError error) {
            // 缩放/编码阶段的内存不足。
            throw new ConversionException(ErrorReason.IO_ERROR, "缩放或编码时内存不足");
        } catch (java.io.IOException error) {
            // IO 失败细分。
            throw classifyIoError(error);
        } finally {
            // 回收位图，及时释放像素内存。
            if (finalBitmap != null && !finalBitmap.isRecycled()) {
                // 执行回收。
                finalBitmap.recycle();
            }
            // 被替换掉的原始位图也要回收。
            if (finalBitmap != decoded && decoded != null && !decoded.isRecycled()) {
                // 执行回收。
                decoded.recycle();
            }
        }
    }

    /**
     * 计算 BitmapFactory 需要的 2 的幂次采样率。
     */
    private static int computeSampleSize(int width, int height, int limitWidth, int limitHeight) {
        // 采样率从 1 开始。
        int sampleSize = 1;
        // 循环条件：任一维度超出限制，或总像素数超出预算。
        while (true) {
            // 计算按当前采样率解码后的尺寸（向上取整，宁可多留一点）。
            int sampleWidth = (width + sampleSize - 1) / sampleSize;
            // 计算高度。
            int sampleHeight = (height + sampleSize - 1) / sampleSize;
            // 判断是否满足尺寸与像素预算。
            boolean sizeOk = sampleWidth <= limitWidth && sampleHeight <= limitHeight;
            // 判断像素预算。
            boolean pixelsOk = (long) sampleWidth * sampleHeight <= MAX_PIXEL_BUDGET;
            // 两个条件都满足时停止提高采样率。
            if (sizeOk && pixelsOk) {
                // 跳出循环。
                break;
            }
            // 采样率翻倍（保持 2 的幂次，BitmapFactory 才能高效处理）。
            sampleSize *= 2;
            // 安全上限：采样率过大已无意义，直接返回避免死循环。
            if (sampleSize > 64) {
                // 返回上限值。
                break;
            }
        }
        // 返回最终采样率。
        return sampleSize;
    }

    /**
     * 把用户质量参数裁剪到 1..100。
     */
    private static int clampQuality(int quality) {
        // 小于 1 时取 1，大于 100 时取 100。
        return Math.max(1, Math.min(100, quality));
    }

    /**
     * 安静地关闭流。
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
        // 取消息文本并小写化。
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(java.util.Locale.US);
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
