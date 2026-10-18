package com.xu.universaltranscoder.core;

// 引入 Context：用于获取缓存目录来创建探测用的临时文件。

import android.content.Context;
// 引入 MediaCodecInfo：描述系统里每个编解码器的能力信息。
import android.media.MediaCodecInfo;
// 引入 MediaCodecList：枚举设备上全部编解码器。
import android.media.MediaCodecList;
// 引入 MediaMuxer：探测目标容器是否被系统封装器支持。
import android.media.MediaMuxer;
// 引入注解：标记返回值可为空。
import androidx.annotation.Nullable;

// 引入 File：探测时创建的临时文件。
import java.io.File;
// 引入 HashMap/Map：缓存探测结果，避免重复探测拖慢界面。
import java.util.HashMap;
// 引入 ArrayList：收集候选编解码器名字。
import java.util.ArrayList;
// 引入 List：返回候选列表。
import java.util.List;

/**
 * Capabilities 负责“在真实设备上探测到底支持什么”。
 * 为什么必须探测而不是写死：MediaMuxer 的容器支持度与 MediaCodec 的编解码器支持度在不同厂商、
 * 不同系统版本上差异极大（例如有的设备 MediaMuxer 不接受 WEBM，有的设备没有 HEVC 编码器），
 * 因此界面只能列出探测通过的目标，否则就会出现“点了转换必然失败”的糟糕体验。
 */
public final class Capabilities {

    // 容器探测结果缓存：key 为 MediaMuxer 的 OutputFormat 常量。
    private static final HashMap<Integer, Boolean> MUXER_SUPPORT_CACHE = new HashMap<>();
    // 缓存编解码器列表时所用的锁对象，保证多线程下只初始化一次。
    private static final Object CODEC_CACHE_LOCK = new Object();
    // 编解码器列表缓存：整个进程只取一次 MediaCodecList，避免每次转换都重新枚举。
    private static MediaCodecInfo[] codecInfoCache;

    // 私有构造：工具类不允许实例化。
    private Capabilities() {
        // 空实现，禁止外部 new。
    }

    /**
     * 探测当前设备是否支持某个 MediaMuxer 输出容器。
     * 实现方式：直接在缓存目录尝试构造一次 MediaMuxer。构造失败（抛 IllegalArgumentException/IOException 等）
     * 即视为“设备不支持该容器”，这与文档描述的“不支持时构造方法会抛异常”一致。
     */
    public static boolean isMuxerFormatSupported(Context context, int muxerFormat) {
        // 先查缓存，命中则直接返回，避免重复 IO。
        Boolean cached = MUXER_SUPPORT_CACHE.get(muxerFormat);
        // 缓存存在时直接返回结果。
        if (cached != null) {
            // 返回已探测的布尔结果。
            return cached;
        }
        // 准备一个探测用临时文件路径。
        File probeFile = new File(context.getCacheDir(), "utc_probe_" + muxerFormat + ".tmp");
        // 默认假定不支持，只有构造成功才改为支持。
        boolean supported = false;
        // MediaMuxer 实例先置空，便于 finally 中安全释放。
        MediaMuxer muxer = null;
        try {
            // 尝试以目标容器格式构造封装器；这是系统唯一能给出的“容器是否可用”的判定入口。
            muxer = new MediaMuxer(probeFile.getAbsolutePath(), muxerFormat);
            // 构造成功说明该容器被系统封装器接受。
            supported = true;
        } catch (Throwable error) {
            // 任何异常（参数非法、平台不支持、IO 受限）都归为“不支持”。
            supported = false;
        } finally {
            // 释放封装器，忽略释放异常。
            if (muxer != null) {
                // 释放资源，避免文件句柄泄漏。
                try {
                    // 调用 release 关闭底层资源。
                    muxer.release();
                } catch (Throwable ignored) {
                    // 释放失败不影响探测结论。
                }
            }
            // 删除探测临时文件。
            if (probeFile.exists()) {
                // 执行删除。
                probeFile.delete();
            }
        }
        // 把结论写入缓存，后续调用立即返回。
        MUXER_SUPPORT_CACHE.put(muxerFormat, supported);
        // 返回最终结论。
        return supported;
    }

    /**
     * 获取设备上全部编解码器信息，带进程级缓存。
     */
    private static MediaCodecInfo[] codecInfos() {
        // 第一次检查：已初始化则直接返回。
        if (codecInfoCache != null) {
            // 返回缓存数组。
            return codecInfoCache;
        }
        // 加锁，避免并发重复枚举。
        synchronized (CODEC_CACHE_LOCK) {
            // 第二次检查：可能已被其它线程初始化。
            if (codecInfoCache == null) {
                // 只枚举“常规编解码器”，排除别名与冗余项，结果更贴近实际可用集合。
                MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                // 保存枚举结果。
                codecInfoCache = list.getCodecInfos();
            }
        }
        // 返回初始化完成的数组。
        return codecInfoCache;
    }

    /**
     * 判断系统里是否存在能处理给定 MIME 的编解码器。
     * wantEncoder 为 true 时查编码器，false 时查解码器。
     */
    public static boolean hasCodec(String mimeType, boolean wantEncoder) {
        // 找到第一个匹配的编解码器即认为存在。
        return findCodecName(mimeType, wantEncoder, false) != null;
    }

    /**
     * 查找可用的编解码器名字。
     *
     * @param mimeType       目标 MIME，例如 video/avc、audio/mp4a-latm。
     * @param wantEncoder    true 查编码器，false 查解码器。
     * @param preferHardware true 时优先返回硬件编解码器（若设备存在）。
     * @return 编解码器名字，供 MediaCodec.createByCodecName 使用；找不到返回 null。
     */
    @Nullable
    public static String findCodecName(String mimeType, boolean wantEncoder, boolean preferHardware) {
        // 收集所有匹配的候选编解码器名字。
        List<String> hardware = new ArrayList<>();
        // 收集所有软件编解码器名字，作为硬件不可用时的兜底。
        List<String> software = new ArrayList<>();
        // 遍历系统编解码器列表。
        for (MediaCodecInfo info : codecInfos()) {
            // 编码器与解码器必须区分，否则会拿到方向错误的编解码器。
            if (info.isEncoder() != wantEncoder) {
                // 方向不匹配则跳过。
                continue;
            }
            // 遍历该编解码器支持的所有 MIME 类型。
            for (String type : info.getSupportedTypes()) {
                // 逐个比较 MIME（忽略大小写，不同厂商大小写不一致）。
                if (type.equalsIgnoreCase(mimeType)) {
                    // 判断该编解码器是否属于硬件加速实现。
                    if (isHardwareAccelerated(info)) {
                        // 加入硬件候选列表。
                        hardware.add(info.getName());
                    } else {
                        // 加入软件候选列表。
                        software.add(info.getName());
                    }
                    // 该编解码器已记录，跳出内层循环继续看下一个编解码器。
                    break;
                }
            }
        }
        // 若希望优先硬件且存在硬件实现，直接返回第一个硬件编解码器。
        if (preferHardware && !hardware.isEmpty()) {
            // 返回硬件编解码器名字。
            return hardware.get(0);
        }
        // 没有硬件可用时退回软件实现。
        if (!software.isEmpty()) {
            // 返回软件编解码器名字。
            return software.get(0);
        }
        // 若调用方不要求优先硬件但只存在硬件实现，则返回硬件实现。
        if (!hardware.isEmpty()) {
            // 返回硬件编解码器名字。
            return hardware.get(0);
        }
        // 完全找不到匹配编解码器时返回 null，由上层给出“编解码器不可用”的错误分类。
        return null;
    }

    /**
     * 判断 MediaCodecInfo 是否为硬件加速实现。
     * API 29 起系统提供了 isHardwareAccelerated()/isSoftwareOnly()，但本应用要兼容 API 28，
     * 因此在低版本上用“反射 + 命名启发式”来推断，避免直接调用不存在的方法导致 NoSuchMethodError。
     */
    public static boolean isHardwareAccelerated(MediaCodecInfo info) {
        // 先尝试反射调用 API 29 才有的方法，成功则直接采用系统结论。
        try {
            // 反射获取 isHardwareAccelerated 方法。
            java.lang.reflect.Method method = MediaCodecInfo.class.getMethod("isHardwareAccelerated");
            // 反射调用取得返回值。
            Object result = method.invoke(info);
            // 结果类型为 Boolean 时转换并返回。
            if (result instanceof Boolean) {
                // 返回系统给出的硬件加速判定。
                return ((Boolean) result).booleanValue();
            }
        } catch (Throwable ignored) {
            // 低版本系统没有该方法，落到下面的命名启发式。
        }
        // 再尝试反射调用 isSoftwareOnly() 做反向判断。
        try {
            // 反射获取 isSoftwareOnly 方法。
            java.lang.reflect.Method method = MediaCodecInfo.class.getMethod("isSoftwareOnly");
            // 反射调用取得返回值。
            Object result = method.invoke(info);
            // 结果为 true 表示纯软件实现，于是“硬件加速”为 false。
            if (result instanceof Boolean) {
                // 返回取反后的硬件加速判定。
                return !((Boolean) result).booleanValue();
            }
        } catch (Throwable ignored) {
            // 方法不存在时继续使用命名启发式。
        }
        // 命名启发式：Android 上的软件编解码器名字通常带有这些前缀/片段。
        String name = info.getName() == null ? "" : info.getName().toLowerCase(java.util.Locale.US);
        // OMX.google.* 是 AOSP 提供的软件实现。
        if (name.startsWith("omx.google.")) {
            // 判定为软件实现。
            return false;
        }
        // c2.android.* 是 Codec2 架构下的软件实现。
        if (name.startsWith("c2.android.")) {
            // 判定为软件实现。
            return false;
        }
        // 名字中带 ffmpeg 的通常是软件实现（部分 ROM 会烧录软解）。
        if (name.contains("ffmpeg")) {
            // 判定为软件实现。
            return false;
        }
        // 其余情况默认认为是硬件实现，这是 Android 上更常见的分布。
        return true;
    }

    /**
     * 返回设备上 MediaMuxer 支持的全部容器格式常量，供界面列出可选目标时参考。
     */
    public static List<Integer> supportedMuxerFormats(Context context) {
        // 结果列表。
        List<Integer> result = new ArrayList<>();
        // 依次探测 MP4、WEBM、3GPP 三种 MediaMuxer 官方支持的常见容器。
        int[] candidates = new int[]{
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
        };
        // 逐个探测并收集支持项。
        for (int candidate : candidates) {
            // 探测通过则加入结果。
            if (isMuxerFormatSupported(context, candidate)) {
                // 记录支持的容器常量。
                result.add(candidate);
            }
        }
        // 返回最终支持列表。
        return result;
    }
}
