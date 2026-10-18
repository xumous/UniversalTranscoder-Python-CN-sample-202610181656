package com.xu.universaltranscoder.core;

// 关键字 enum：Quality 把“质量档位”建模成有限枚举，避免界面传入任意整数导致参数组合不可控
// 之所以用枚举而不是常量表：档位数量固定，枚举可以在编译期发现拼写错误，也让质量映射集中在一处
public enum Quality {

    // 低档：追求最小体积，画质可接受即可
    LOW,
    // 中档：体积与画质平衡，作为默认档位
    MEDIUM,
    // 高档：画质优先，体积明显增大
    HIGH,
    // 极高档：接近源画质，体积很大
    VERY_HIGH,
    // 无损档：画质最大化，体积最大
    LOSSLESS;

    // 把界面索引映射为质量档位，索引越界时回退到中档，保证界面异常不会导致崩溃
    public static Quality fromIndex(int index) {
        // 取出全部枚举常量
        Quality[] values = values();
        // 索引非法时返回默认中档
        if (index < 0 || index >= values.length) {
            // 返回中档作为兜底
            return MEDIUM;
        }
        // 返回对应档位
        return values[index];
    }

    // 把英文标签反解为档位，用于设置持久化后恢复界面状态
    public static Quality fromName(String name) {
        // 遍历全部档位尝试匹配
        for (Quality quality : values()) {
            // 忽略大小写比较，提高容错
            if (quality.displayName().equalsIgnoreCase(name)) {
                // 命中即返回该档位
                return quality;
            }
        }
        // 未命中时返回默认中档
        return MEDIUM;
    }

    // 取得界面展示用的英文标签，用户只看英文文案
    public String displayName() {
        // 使用 switch 表达式把枚举映射为可读英文
        return switch (this) {
            // 低档标签
            case LOW -> "低";
            // 中档标签
            case MEDIUM -> "中";
            // 高档标签
            case HIGH -> "高";
            // 极高档标签
            case VERY_HIGH -> "极高";
            // 无损标签
            case LOSSLESS -> "无损";
        };
    }

    // 计算 x264/x265 系列使用的 CRF 值，数值越小画质越高
    public int x26xCrf() {
        // 按档位给出经验值，这些取值经过大量实践验证能在体积与画质间取得良好平衡
        return switch (this) {
            // 低档 CRF 28，压缩力度大
            case LOW -> 28;
            // 中档 CRF 23，x264 官方推荐默认值
            case MEDIUM -> 23;
            // 高档 CRF 20
            case HIGH -> 20;
            // 极高档 CRF 17
            case VERY_HIGH -> 17;
            // 无损 CRF 0，x264 中以 0 表示数学无损
            case LOSSLESS -> 0;
        };
    }

    // 计算 vp9/av1 系列使用的 CRF 值，取值范围比 x26x 更宽
    public int vpxCrf() {
        // vp9 的 CRF 区间为 0 至 63，经验值整体高于 x264
        return switch (this) {
            // 低档 CRF 40
            case LOW -> 40;
            // 中档 CRF 33
            case MEDIUM -> 33;
            // 高档 CRF 28
            case HIGH -> 28;
            // 极高档 CRF 22
            case VERY_HIGH -> 22;
            // 无损 CRF 0
            case LOSSLESS -> 0;
        };
    }

    // 计算硬件编码器（nvenc/qsv/amf）使用的恒定质量值 cq
    public int hardwareCq() {
        // 硬件编码器的 cq 区间通常为 0 至 51，语义与 CRF 接近
        return switch (this) {
            // 低档 cq 32
            case LOW -> 32;
            // 中档 cq 27
            case MEDIUM -> 27;
            // 高档 cq 23
            case HIGH -> 23;
            // 极高档 cq 19
            case VERY_HIGH -> 19;
            // 无损 cq 0，部分硬件编码器以 1 为最小值，命令构建会再钳制下限
            case LOSSLESS -> 1;
        };
    }

    // 计算老式编码器使用的 qscale 值，数值越小画质越高
    public int qscale() {
        // 这类编码器通过 qscale 控制质量，区间通常为 1 至 31
        return switch (this) {
            // 低档 qscale 8
            case LOW -> 8;
            // 中档 qscale 5
            case MEDIUM -> 5;
            // 高档 qscale 3
            case HIGH -> 3;
            // 极高档 qscale 2
            case VERY_HIGH -> 2;
            // 无损 qscale 1，取值下限
            case LOSSLESS -> 1;
        };
    }

    // 计算音频目标码率（千比特每秒），无损档返回 0 表示不使用码率参数
    public int audioBitRateKbps() {
        // 有损音频编码器的常见码率档位
        return switch (this) {
            // 低档 96k，语音与移动端常用
            case LOW -> 96;
            // 中档 160k，接近多数音乐平台的默认值
            case MEDIUM -> 160;
            // 高档 256k，明显优于 128k 的听感
            case HIGH -> 256;
            // 极高档 320k，主流有损编码的上限
            case VERY_HIGH -> 320;
            // 无损档返回 0，表示应切换到无损编码而不是指定码率
            case LOSSLESS -> 0;
        };
    }

    // 按目标高度估算视频码率（千比特每秒），用于不支持 CRF 的编码器与硬件编码器兜底
    public int videoBitRateKbps(int targetHeight) {
        // 目标高度非法时按 720 估算，避免出现零码率导致画质崩塌
        int height = targetHeight > 0 ? targetHeight : 720;
        // 按档位给出每像素行码率系数，系数乘以高度得到近似码率
        double factor = switch (this) {
            // 低档系数 1.2，压缩力度最大
            case LOW -> 1.2d;
            // 中档系数 2.0
            case MEDIUM -> 2.0d;
            // 高档系数 3.2
            case HIGH -> 3.2d;
            // 极高档系数 5.0
            case VERY_HIGH -> 5.0d;
            // 无损档系数 8.0，逼近视觉无损的码率水平
            case LOSSLESS -> 8.0d;
        };
        // 计算码率并四舍五入成整数
        int kbps = (int) Math.round(height * factor);
        // 对码率做下限保护，避免极低分辨率下码率过小
        return Math.max(kbps, 100);
    }

    // 判断当前档位是否为无损档，命令构建据此决定是否加无损专用参数
    public boolean isLossless() {
        // 枚举自身等于 LOSSLESS 即表示无损
        return this == LOSSLESS;
    }
}
