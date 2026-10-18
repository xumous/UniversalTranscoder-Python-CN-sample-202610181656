package com.xu.universaltranscoder.ui;

// 引入 Files，用于创建配置目录与读写配置文件的属性

import java.nio.file.Files;
// 引入 Path，表示配置文件路径
import java.nio.file.Path;
// 引入 Paths，用于拼接配置目录
import java.nio.file.Paths;
// 引入 IOException，处理配置文件读写异常
import java.io.IOException;
// 引入 InputStream，读取配置文件的字节流
import java.io.InputStream;
// 引入 OutputStream，写出配置文件的字节流
import java.io.OutputStream;
// 引入 Properties，使用 JDK 标准键值格式持久化设置
import java.util.Properties;

// 关键字 class：AppSettings 保存应用级设置并负责持久化到用户目录
// 之所以用 Properties 而不是自定义格式：键值格式解析简单可靠，且人工查看与修改都很方便
public final class AppSettings {

    // 配置文件所在目录的名称，位于用户漫游配置目录下
    private static final String CONFIG_DIR_NAME = "UniversalTranscoder";
    // 配置文件名
    private static final String CONFIG_FILE_NAME = "settings.properties";

    // 用户自定义的 ffmpeg 可执行文件或所在目录路径，空串表示按自动顺序查找
    private String customFfmpegPath = "";
    // 默认输出目录，空串表示与输入文件同目录
    private String defaultOutputDir = "";
    // 队列并发上限，默认 2 兼顾吞吐与资源占用
    private int concurrency = 2;
    // 内核日志级别，界面主要作为记录用途，实际命令固定使用 error 级别
    private String logLevel = "仅错误";
    // 主题名称，浅色或深色
    private String theme = "浅色";
    // 是否默认启用硬件加速编码
    private boolean hardwareAcceleration;
    // 默认输出命名模式，取值 replace / suffix / template，与命名枚举一一对应
    private String namingMode = "replace";
    // 默认自定义命名模板
    private String nameTemplate = "{name}_{target}";
    // 默认元数据模式，取值保留或去除
    private String metadataMode = "保留";
    // 默认质量档位名称，与 Quality 枚举的展示名一致
    private String defaultQuality = "中";

    // 私有构造方法，强制通过 load 工厂方法创建实例
    private AppSettings() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 从磁盘加载设置，文件不存在或读取失败时返回带默认值的实例
    public static AppSettings load() {
        // 创建默认实例
        AppSettings settings = new AppSettings();
        // 计算配置文件路径
        Path configFile = configFilePath();
        // 文件不存在时直接返回默认设置
        if (!Files.isRegularFile(configFile)) {
            // 返回默认设置
            return settings;
        }
        // 预置属性对象用于承接文件内容
        Properties properties = new Properties();
        // 使用 try-with-resources 打开输入流，确保句柄释放
        try (InputStream input = Files.newInputStream(configFile)) {
            // 加载属性文件
            properties.load(input);
        } catch (IOException ex) {
            // 读取失败时回退到默认设置，避免界面因配置损坏无法启动
            return settings;
        }
        // 逐个读取属性并写入字段，缺失键保持默认值
        settings.customFfmpegPath = properties.getProperty("customFfmpegPath", settings.customFfmpegPath);
        // 读取默认输出目录
        settings.defaultOutputDir = properties.getProperty("defaultOutputDir", settings.defaultOutputDir);
        // 读取并发数并做安全解析，非法值保持默认
        settings.concurrency = parseInt(properties.getProperty("concurrency"), settings.concurrency);
        // 读取日志级别
        settings.logLevel = properties.getProperty("logLevel", settings.logLevel);
        // 读取主题名称
        settings.theme = properties.getProperty("theme", settings.theme);
        // 读取硬件加速开关
        settings.hardwareAcceleration = Boolean.parseBoolean(properties.getProperty("hardwareAcceleration",
                // 默认值转成字符串传入
                String.valueOf(settings.hardwareAcceleration)));
        // 读取默认命名模式
        settings.namingMode = properties.getProperty("namingMode", settings.namingMode);
        // 读取默认命名模板
        settings.nameTemplate = properties.getProperty("nameTemplate", settings.nameTemplate);
        // 读取默认元数据模式
        settings.metadataMode = properties.getProperty("metadataMode", settings.metadataMode);
        // 读取默认质量档位
        settings.defaultQuality = properties.getProperty("defaultQuality", settings.defaultQuality);
        // 返回加载完成的设置
        return settings;
    }

    // 计算配置文件的绝对路径，优先使用用户配置目录，缺失时回退到用户主目录
    private static Path configFilePath() {
        // 读取 APPDATA 环境变量
        String appData = System.getenv("APPDATA");
        // 环境变量缺失时回退到 user.home
        String baseDir = (appData == null || appData.isEmpty())
                // 使用用户主目录兜底
                ? System.getProperty("user.home")
                // 优先使用 APPDATA
                : appData;
        // 拼接配置目录与文件名
        return Paths.get(baseDir, CONFIG_DIR_NAME, CONFIG_FILE_NAME);
    }

    // 解析整数字符串，失败时返回默认值
    private static int parseInt(String text, int fallback) {
        // 文本为空时返回默认值
        if (text == null || text.trim().isEmpty()) {
            // 返回默认值
            return fallback;
        }
        // 捕获解析异常
        try {
            // 返回解析结果
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            // 解析失败返回默认值
            return fallback;
        }
    }

    // 把当前设置写回磁盘，写失败时静默忽略并在界面日志中提示
    public void save() {
        // 计算配置文件路径
        Path configFile = configFilePath();
        // 取配置文件所在目录
        Path dir = configFile.getParent();
        // 捕获目录创建与写入异常
        try {
            // 确保配置目录存在
            if (dir != null) {
                // 递归创建目录，已存在时忽略
                Files.createDirectories(dir);
            }
            // 构造属性对象
            Properties properties = new Properties();
            // 写入自定义内核路径
            properties.setProperty("customFfmpegPath", customFfmpegPath == null ? "" : customFfmpegPath);
            // 写入默认输出目录
            properties.setProperty("defaultOutputDir", defaultOutputDir == null ? "" : defaultOutputDir);
            // 写入并发数
            properties.setProperty("concurrency", String.valueOf(concurrency));
            // 写入日志级别
            properties.setProperty("logLevel", logLevel == null ? "仅错误" : logLevel);
            // 写入主题名称
            properties.setProperty("theme", theme == null ? "浅色" : theme);
            // 写入硬件加速开关
            properties.setProperty("hardwareAcceleration", String.valueOf(hardwareAcceleration));
            // 写入默认命名模式
            properties.setProperty("namingMode", namingMode == null ? "replace" : namingMode);
            // 写入默认命名模板
            properties.setProperty("nameTemplate", nameTemplate == null ? "{name}_{target}" : nameTemplate);
            // 写入默认元数据模式
            properties.setProperty("metadataMode", metadataMode == null ? "保留" : metadataMode);
            // 写入默认质量档位
            properties.setProperty("defaultQuality", defaultQuality == null ? "中" : defaultQuality);
            // 使用 try-with-resources 打开输出流
            try (OutputStream output = Files.newOutputStream(configFile)) {
                // 写出属性，附带一行注释说明文件用途
                properties.store(output, "万能转码器设置");
            }
        } catch (IOException ex) {
            // 写失败时不做处理，界面会通过日志面板提示用户手动检查目录权限
        }
    }

    // 获取自定义内核路径
    public String customFfmpegPath() {
        // 返回路径文本
        return customFfmpegPath;
    }

    // 设置自定义内核路径
    public void customFfmpegPath(String value) {
        // 空值归一化为空串表示自动查找
        this.customFfmpegPath = value == null ? "" : value.trim();
    }

    // 获取默认输出目录
    public String defaultOutputDir() {
        // 返回目录文本
        return defaultOutputDir;
    }

    // 设置默认输出目录
    public void defaultOutputDir(String value) {
        // 空值归一化为空串表示与输入同目录
        this.defaultOutputDir = value == null ? "" : value.trim();
    }

    // 获取并发上限
    public int concurrency() {
        // 返回并发数
        return concurrency;
    }

    // 设置并发上限
    public void concurrency(int value) {
        // 并发数钳制在 1 至 8 之间，避免用户设置过大值导致机器资源耗尽
        this.concurrency = Math.min(Math.max(value, 1), 8);
    }

    // 获取日志级别
    public String logLevel() {
        // 返回日志级别
        return logLevel;
    }

    // 设置日志级别
    public void logLevel(String value) {
        // 空值归一化为仅错误
        this.logLevel = (value == null || value.trim().isEmpty()) ? "仅错误" : value.trim();
    }

    // 获取主题名称
    public String theme() {
        // 返回主题名称
        return theme;
    }

    // 设置主题名称
    public void theme(String value) {
        // 空值归一化为浅色
        this.theme = (value == null || value.trim().isEmpty()) ? "浅色" : value.trim();
    }

    // 判断是否启用硬件加速
    public boolean hardwareAcceleration() {
        // 返回硬件加速标记
        return hardwareAcceleration;
    }

    // 设置硬件加速开关
    public void hardwareAcceleration(boolean value) {
        // 写入开关值
        this.hardwareAcceleration = value;
    }

    // 获取默认命名模式
    public String namingMode() {
        // 返回命名模式文本
        return namingMode;
    }

    // 设置默认命名模式
    public void namingMode(String value) {
        // 空值归一化为 replace
        this.namingMode = (value == null || value.trim().isEmpty()) ? "replace" : value.trim().toLowerCase();
    }

    // 获取默认命名模板
    public String nameTemplate() {
        // 返回模板文本
        return nameTemplate;
    }

    // 设置默认命名模板
    public void nameTemplate(String value) {
        // 空值归一化为默认模板
        this.nameTemplate = (value == null || value.trim().isEmpty()) ? "{name}_{target}" : value.trim();
    }

    // 获取默认元数据模式
    public String metadataMode() {
        // 返回元数据模式
        return metadataMode;
    }

    // 设置默认元数据模式
    public void metadataMode(String value) {
        // 空值归一化为保留
        this.metadataMode = (value == null || value.trim().isEmpty()) ? "保留" : value.trim();
    }

    // 获取默认质量档位名称
    public String defaultQuality() {
        // 返回质量名称
        return defaultQuality;
    }

    // 设置默认质量档位名称
    public void defaultQuality(String value) {
        // 空值归一化为中档名称
        this.defaultQuality = (value == null || value.trim().isEmpty()) ? "中" : value.trim();
    }
}
