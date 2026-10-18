package com.xu.universaltranscoder.util;

// 引入 Context：读取与写入 SharedPreferences 需要上下文。

import android.content.Context;
// 引入 SharedPreferences：以键值对形式持久化用户设置。
import android.content.SharedPreferences;
// 引入 Uri：保存用户选择的输出目录树地址。
import android.net.Uri;

/**
 * AppSettings 封装“设置页”里全部可持久化的选项。
 * 之所以用 SharedPreferences 而不是数据库：设置项少、结构简单、需要同步读取，键值存储最合适。
 */
public final class AppSettings {

    // 日志级别常量：只输出错误。
    public static final int LOG_LEVEL_ERROR = 0;
    // 日志级别常量：输出信息与错误（默认）。
    public static final int LOG_LEVEL_INFO = 1;
    // 日志级别常量：输出全部细节，含调试信息。
    public static final int LOG_LEVEL_DEBUG = 2;
    // SharedPreferences 文件名。
    private static final String PREF_NAME = "utc_settings";
    // 键：并发任务数。
    private static final String KEY_CONCURRENCY = "concurrency";
    // 键：输出目录树 URI（空字符串表示使用应用专属目录）。
    private static final String KEY_OUTPUT_TREE = "output_tree";
    // 键：日志级别（0=Error,1=Info,2=Debug）。
    private static final String KEY_LOG_LEVEL = "log_level";
    // 键：是否优先硬件编解码器。
    private static final String KEY_PREFER_HW = "prefer_hw";
    // 单例实例，避免每次读取都新建对象。
    private static AppSettings instance;
    // 底层偏好存储对象。
    private final SharedPreferences prefs;

    // 私有构造：从系统取得偏好存储。
    private AppSettings(Context context) {
        // 使用应用上下文访问偏好文件。
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 取得单例（首次调用时创建）。
     */
    public static synchronized AppSettings get(Context context) {
        // 尚未创建时创建。
        if (instance == null) {
            // 以传入上下文构造单例。
            instance = new AppSettings(context);
        }
        // 返回单例。
        return instance;
    }

    /**
     * 读取并发任务数，范围限制在 1..4 之间（过多并发会争抢硬件编解码器导致互相失败）。
     */
    public int concurrency() {
        // 读取并做上下限裁剪。
        int value = prefs.getInt(KEY_CONCURRENCY, 1);
        // 小于 1 时纠正为 1。
        if (value < 1) {
            // 返回最小值。
            return 1;
        }
        // 大于 4 时纠正为 4。
        if (value > 4) {
            // 返回最大值。
            return 4;
        }
        // 返回合法值。
        return value;
    }

    /**
     * 写入并发任务数。
     */
    public void setConcurrency(int value) {
        // 先做相同的范围裁剪再保存，保证磁盘上的值始终合法。
        int clamped = Math.max(1, Math.min(4, value));
        // 提交写入。
        prefs.edit().putInt(KEY_CONCURRENCY, clamped).apply();
    }

    /**
     * 读取默认输出目录树 URI；返回空字符串表示使用应用专属目录。
     */
    public String outputTreeUri() {
        // 缺省为空字符串。
        return prefs.getString(KEY_OUTPUT_TREE, "");
    }

    /**
     * 写入默认输出目录树 URI。
     */
    public void setOutputTreeUri(String uri) {
        // 空值归一化为空字符串。
        prefs.edit().putString(KEY_OUTPUT_TREE, uri == null ? "" : uri).apply();
    }

    /**
     * 解析输出目录 Uri 对象，无有效配置时返回 null。
     */
    public Uri outputTreeUriOrNull() {
        // 读取字符串配置。
        String value = outputTreeUri();
        // 空字符串视为未配置。
        if (value.isEmpty()) {
            // 返回 null 让调用方使用应用专属目录。
            return null;
        }
        // 解析并返回。
        return Uri.parse(value);
    }

    /**
     * 读取日志级别。
     */
    public int logLevel() {
        // 默认 Info 级别。
        return prefs.getInt(KEY_LOG_LEVEL, LOG_LEVEL_INFO);
    }

    /**
     * 写入日志级别。
     */
    public void setLogLevel(int level) {
        // 裁剪到 0..2。
        int clamped = Math.max(LOG_LEVEL_ERROR, Math.min(LOG_LEVEL_DEBUG, level));
        // 提交写入。
        prefs.edit().putInt(KEY_LOG_LEVEL, clamped).apply();
    }

    /**
     * 读取“是否优先硬件编解码器”。
     */
    public boolean preferHardware() {
        // 默认开启硬件优先：绝大多数设备硬编硬解更快。
        return prefs.getBoolean(KEY_PREFER_HW, true);
    }

    /**
     * 写入“是否优先硬件编解码器”。
     */
    public void setPreferHardware(boolean value) {
        // 提交写入。
        prefs.edit().putBoolean(KEY_PREFER_HW, value).apply();
    }

    /**
     * 把全部设置恢复为默认值。
     */
    public void reset() {
        // 清空偏好文件即可回到默认状态。
        prefs.edit().clear().apply();
    }
}
