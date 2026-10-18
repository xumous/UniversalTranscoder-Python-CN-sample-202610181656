package com.xu.universaltranscoder.core;

// 引入 ArrayList，用于按优先级顺序收集可用厂商

import java.util.ArrayList;
// 引入 List 接口，作为厂商列表与编码器候选列表的类型
import java.util.List;

// 关键字 class：HardwareAccel 负责硬件加速能力识别与自动策略挑选
// 之所以单独成类：硬件策略会随厂商与驱动变化，把这份易变逻辑与命令构建解耦便于单独调整
public final class HardwareAccel {

    // 私有构造方法，禁止实例化这个纯静态策略类
    private HardwareAccel() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 逐厂商检测当前内核实际提供了哪些硬件编码器，返回按优先级排序的可用厂商列表
    public static List<Vendor> detectedVendors() {
        // 取得当前内核对象
        Toolchain toolchain = Toolchain.get();
        // 使用有序列表保存可用厂商
        List<Vendor> vendors = new ArrayList<>();
        // 逐条探测 NVIDIA 的 h264 与 hevc 编码器是否存在
        if (toolchain.hasEncoder("h264_nvenc") || toolchain.hasEncoder("hevc_nvenc")) {
            // 命中即加入 NVIDIA
            vendors.add(Vendor.NVIDIA);
        }
        // 探测 Intel Quick Sync 编码器
        if (toolchain.hasEncoder("h264_qsv") || toolchain.hasEncoder("hevc_qsv")) {
            // 命中即加入 Intel
            vendors.add(Vendor.INTEL);
        }
        // 探测 AMD AMF 编码器
        if (toolchain.hasEncoder("h264_amf") || toolchain.hasEncoder("hevc_amf")) {
            // 命中即加入 AMD
            vendors.add(Vendor.AMD);
        }
        // 探测 Apple VideoToolbox 编码器
        if (toolchain.hasEncoder("h264_videotoolbox")) {
            // 命中即加入 Apple
            vendors.add(Vendor.APPLE);
        }
        // 探测 Linux VAAPI 编码器
        if (toolchain.hasEncoder("h264_vaapi")) {
            // 命中即加入 VAAPI
            vendors.add(Vendor.VAAPI);
        }
        // 无论是否有硬件编码器，软件路径始终可用，追加到列表末尾作为兜底
        vendors.add(Vendor.SOFTWARE);
        // 返回按优先级排序的厂商列表
        return vendors;
    }

    // 为指定目标编码族挑选硬件编码器名，例如传入 h264 返回 h264_nvenc 或 h264_qsv
    public static String pickEncoder(String family) {
        // 取得按优先级排序的可用厂商
        List<Vendor> vendors = detectedVendors();
        // 逐个厂商尝试构造候选编码器名
        for (Vendor vendor : vendors) {
            // 软件厂商不提供硬件编码器，跳过
            if (vendor == Vendor.SOFTWARE) {
                // 继续尝试下一个厂商
                continue;
            }
            // 按厂商与编码族拼出候选编码器名
            String candidate = encoderName(family, vendor);
            // 候选名非空且内核确实支持时才采用
            if (candidate != null && Toolchain.get().hasEncoder(candidate)) {
                // 返回首个命中的硬件编码器名
                return candidate;
            }
        }
        // 全部厂商都不支持时返回 null，由调用方回退到软件编码器
        return null;
    }

    // 按厂商与编码族构造具体编码器名称
    private static String encoderName(String family, Vendor vendor) {
        // 编码族统一小写，避免大小写差异导致匹配失败
        String fam = family == null ? "" : family.toLowerCase();
        // 按厂商选择后缀
        String suffix = switch (vendor) {
            // NVIDIA 使用 _nvenc 后缀
            case NVIDIA -> "_nvenc";
            // Intel 使用 _qsv 后缀
            case INTEL -> "_qsv";
            // AMD 使用 _amf 后缀
            case AMD -> "_amf";
            // Apple 使用 _videotoolbox 后缀
            case APPLE -> "_videotoolbox";
            // Linux 使用 _vaapi 后缀
            case VAAPI -> "_vaapi";
            // 软件厂商没有后缀
            case SOFTWARE -> "";
        };
        // 软件厂商直接返回空，表示没有硬件编码器可用
        if (suffix.isEmpty()) {
            // 返回 null 让调用方走软件分支
            return null;
        }
        // 按编码族拼出完整名称，只支持 h264/hevc/av1 三种硬件常见编码族
        return switch (fam) {
            // H.264 编码族
            case "h264", "avc" -> "h264" + suffix;
            // H.265 编码族
            case "hevc", "h265" -> "hevc" + suffix;
            // AV1 编码族
            case "av1" -> "av1" + suffix;
            // vp9 只有部分平台提供硬件编码，命名不同故单独处理
            case "vp9" -> "vp9_qsv";
            // 其它编码族没有硬件实现
            default -> null;
        };
    }

    // 为解码阶段挑选硬件加速方式，用于降低解码开销，加速整个转码链路
    public static String pickDecoderAcceleration() {
        // 取得当前内核对象
        Toolchain toolchain = Toolchain.get();
        // 优先使用 cuda，NVIDIA 生态对常用编码支持最完整
        if (toolchain.hasHwaccel("cuda")) {
            // 返回 cuda 作为解码后端
            return "cuda";
        }
        // 其次使用 qsv，Intel 平台上解码效率高
        if (toolchain.hasHwaccel("qsv")) {
            // 返回 qsv 作为解码后端
            return "qsv";
        }
        // Windows 平台优先使用 d3d11va，覆盖面最广
        if (toolchain.hasHwaccel("d3d11va")) {
            // 返回 d3d11va 作为解码后端
            return "d3d11va";
        }
        // 老设备上 dxva2 仍可用
        if (toolchain.hasHwaccel("dxva2")) {
            // 返回 dxva2 作为解码后端
            return "dxva2";
        }
        // 全部硬件后端都不可用时返回 auto，由 ffmpeg 自行决定
        return "auto";
    }

    // 把目标视频编码族从编码器名反推出来，用于硬件策略挑选
    public static String familyOf(String encoder) {
        // 编码器为空时无法反推
        if (encoder == null) {
            // 返回空串表示未知编码族
            return "";
        }
        // 名称以 hevc/h265 开头即属于 H.265 家族
        if (encoder.startsWith("hevc") || encoder.startsWith("h265") || encoder.contains("x265")) {
            // 返回标准族名 hevc
            return "hevc";
        }
        // 名称包含 av1 即属于 AV1 家族
        if (encoder.contains("av1")) {
            // 返回标准族名 av1
            return "av1";
        }
        // 名称包含 vp9 即属于 VP9 家族
        if (encoder.contains("vp9")) {
            // 返回标准族名 vp9
            return "vp9";
        }
        // 其余统一归入 H.264 家族，这是最常见的目标编码
        return "h264";
    }

    // 生成一行硬件能力摘要，供 About 页面与日志展示
    public static String describe() {
        // 取得可用厂商列表
        List<Vendor> vendors = detectedVendors();
        // 使用 StringBuilder 组织摘要文本
        StringBuilder builder = new StringBuilder();
        // 逐个厂商追加名称
        for (Vendor vendor : vendors) {
            // 已有内容时先加分隔符
            if (builder.length() > 0) {
                // 追加中文顿号风格的分隔符，日志中可读性更好
                builder.append(" / ");
            }
            // 追加厂商英文名，因为该文案会显示给用户
            builder.append(vendor.displayName());
        }
        // 追加解码后端信息，便于确认整条链路是否启用硬件加速
        builder.append(" | decode=").append(pickDecoderAcceleration());
        // 返回拼好的摘要
        return builder.toString();
    }

    // 定义硬件厂商枚举，顺序即自动策略的优先级顺序
    public enum Vendor {
        // NVIDIA 显卡，编码器后缀为 _nvenc，通常画质与速度兼顾
        NVIDIA,
        // Intel 核显与独立显卡，编码器后缀为 _qsv
        INTEL,
        // AMD 显卡，编码器后缀为 _amf
        AMD,
        // Apple 平台，编码器后缀为 _videotoolbox
        APPLE,
        // Linux 平台，编码器后缀为 _vaapi
        VAAPI,
        // 无可用硬件编码器时的软件路径
        SOFTWARE;

        // 取得界面展示用的英文名称
        public String displayName() {
            // 使用 switch 表达式映射为可读英文
            return switch (this) {
                // NVIDIA 厂商名
                case NVIDIA -> "NVIDIA NVENC";
                // Intel 厂商名
                case INTEL -> "Intel Quick Sync";
                // AMD 厂商名
                case AMD -> "AMD AMF";
                // Apple 厂商名
                case APPLE -> "Apple VideoToolbox";
                // Linux VAAPI 名称
                case VAAPI -> "Linux VAAPI";
                // 软件路径名称
                case SOFTWARE -> "Software";
            };
        }
    }
}
