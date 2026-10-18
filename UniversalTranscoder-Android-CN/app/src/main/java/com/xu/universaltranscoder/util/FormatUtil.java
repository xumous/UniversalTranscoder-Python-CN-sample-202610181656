package com.xu.universaltranscoder.util;

// 引入 Bundle：为生成 sha1 的去重键提供稳定的字段容器。

import android.os.Bundle;

/**
 * FormatUtil 收录与“数值/路径/时间格式化”相关的小工具方法。
 * 抽取出来的原因是：日志、状态栏、队列条目都要用同一套格式化规则，避免出现 1 秒显示成 0.9 秒之类的偏差。
 */
public final class FormatUtil {

    // 私有构造：工具类不允许实例化。
    private FormatUtil() {
        // 空实现，禁止外部 new。
    }

    /**
     * 把毫秒时长格式化为 mm:ss 或 hh:mm:ss。
     */
    public static String formatDuration(long millis) {
        // 负数与 0 在展示上统一按 0 处理，避免出现 "-00:01" 这种奇怪文本。
        if (millis <= 0L) {
            // 直接返回零时长文本。
            return "00:00";
        }
        // 计算总秒数。
        long totalSeconds = millis / 1000L;
        // 计算秒位。
        long seconds = totalSeconds % 60L;
        // 计算总分钟数。
        long totalMinutes = totalSeconds / 60L;
        // 计算分钟位。
        long minutes = totalMinutes % 60L;
        // 计算小时位。
        long hours = totalMinutes / 60L;
        // 有小时时使用 hh:mm:ss 格式，否则使用 mm:ss，避免多余前导零。
        if (hours > 0L) {
            // 三段时间格式化。
            return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        // 两段时间格式化。
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }

    /**
     * 把字节数格式化为便于阅读的 KB/MB 单位。
     */
    public static String formatSize(long bytes) {
        // 负数或零统一显示为 0 B。
        if (bytes <= 0L) {
            // 返回零字节文本。
            return "0 B";
        }
        // 小于 1KB 时直接用字节表示。
        if (bytes < 1024L) {
            // 返回字节数。
            return bytes + " B";
        }
        // 小于 1MB 时使用 KB，保留一位小数。
        if (bytes < 1024L * 1024L) {
            // 计算 KB 值并保留一位小数。
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        }
        // 其余情况使用 MB，保留一位小数。
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 把处理速度（字节/秒）格式化为便于阅读的 KB/s 文本。
     */
    public static String formatSpeed(double bytesPerSecond) {
        // 速度无效时返回一个中性横线，界面显示更整洁。
        if (bytesPerSecond <= 0.0) {
            // 返回中性文本。
            return "-";
        }
        // 统一换算成 KB/s 展示，转换吞吐通常落在几十到几千 KB/s 区间。
        return String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0);
    }

    /**
     * 把 0..1 的进度换算成百分比整数字符串。
     */
    public static String formatPercent(double progress) {
        // 把进度限制在 0..1 之间，防止上游计算出 -1 或 1.3 之类的越界值。
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        // 四舍五入到整数百分比。
        return String.format(java.util.Locale.US, "%d%%", (int) Math.round(clamped * 100.0));
    }

    /**
     * 生成带时间戳的文件基名，例如 utc_video_20261017_120530。
     */
    public static String timestampedBaseName(String prefix) {
        // 使用 SimpleDateFormat 产生人类可读且按时间排序的文件名。
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US);
        // 拼接前缀与当前时间。
        return "utc_" + prefix + "_" + format.format(new java.util.Date());
    }

    /**
     * 从 URI 中截取最后一个路径片段，作为粗略的显示文件名。
     */
    public static String baseNameFromUri(String uriString) {
        // URI 为空时给出中性名称，避免空指针。
        if (uriString == null || uriString.isEmpty()) {
            // 返回通用名称。
            return "source";
        }
        // 找到最后一个斜杠的位置。
        int slash = uriString.lastIndexOf('/');
        // 截取斜杠之后的片段。
        String last = slash >= 0 ? uriString.substring(slash + 1) : uriString;
        // 去掉可能存在的查询参数部分。
        int query = last.indexOf('?');
        // 存在查询参数时截断。
        if (query >= 0) {
            // 截断到问号之前。
            last = last.substring(0, query);
        }
        // 再去掉扩展名，得到纯基名。
        int dot = last.lastIndexOf('.');
        // 有点号时截掉扩展名。
        if (dot > 0) {
            // 截取点号之前的部分。
            last = last.substring(0, dot);
        }
        // URI 解码后可能为空的兜底处理。
        return last.isEmpty() ? "source" : last;
    }

    /**
     * 把一组参数折叠成一个稳定的去重键，用于任务去重。
     */
    public static String buildDedupeKey(String sourceUri, String targetKey, Bundle options) {
        // 使用 StringBuilder 拼接各字段，字段之间用 '|' 分隔避免歧义。
        StringBuilder builder = new StringBuilder();
        // 加入源文件地址。
        builder.append(sourceUri == null ? "" : sourceUri);
        // 加入分隔符。
        builder.append('|');
        // 加入目标格式键。
        builder.append(targetKey == null ? "" : targetKey);
        // 加入分隔符。
        builder.append('|');
        // 若提供了额外参数，则按其字符串形式参与拼接（Bundle.toString 对同内容有稳定输出）。
        if (options != null) {
            // 追加参数内容。
            builder.append(options.toString());
        }
        // 返回最终的去重键。
        return builder.toString();
    }
}
