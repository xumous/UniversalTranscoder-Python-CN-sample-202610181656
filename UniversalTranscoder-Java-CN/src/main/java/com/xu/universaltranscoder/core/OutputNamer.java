package com.xu.universaltranscoder.core;

// 引入 IOException，处理目录创建与文件存在性判断中的异常

import java.io.IOException;
// 引入 Files 工具类，用于创建输出目录与判断文件是否已存在
import java.nio.file.Files;
// 引入 Path 接口，表示输出文件路径
import java.nio.file.Path;
// 引入 DateTimeFormatter，用于在自定义模板中格式化日期与时间
import java.time.format.DateTimeFormatter;
// 引入 LocalDateTime，提供当前时间来源
import java.time.LocalDateTime;

// 关键字 class：OutputNamer 负责把“用户命名意图”解析成具体的输出文件路径，并处理同名冲突
// 之所以独立成类：命名规则与冲突策略是用户可见行为，需要能被单独测试与复用
public final class OutputNamer {

    // 日期格式化器，模板替换标记 {date} 使用 yyyyMMdd 形式
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 时间格式化器，模板替换标记 {time} 使用 HHmmss 形式
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    // 冲突序号的最大尝试次数，防止极端情况下无限循环
    private static final int MAX_CONFLICT_TRIES = 9999;

    // 私有构造方法，禁止实例化这个纯静态工具类
    private OutputNamer() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 解析输出文件路径：确定目录、文件名与扩展名，并在冲突时自动加序号
    public static Path resolve(ConversionRequest request) {
        // 取出输入文件路径，调用方必须先保证输入非空
        Path input = request.input();
        // 确定输出目录：用户指定则用指定的，否则与输入文件同目录
        Path directory = request.outputDirectory() != null
                // 用户指定目录时直接使用
                ? request.outputDirectory()
                // 未指定时回落到输入文件的父目录
                : input.getParent();
        // 目录仍为空（输入是相对路径且无父目录）时回落到当前工作目录
        if (directory == null) {
            // 使用当前工作目录作为兜底输出目录
            directory = Path.of(System.getProperty("user.dir"));
        }
        // 取得输入文件名文本，例如 movie.mp4
        String inputFileName = input.getFileName().toString();
        // 去掉最后一个点号之后的扩展名，得到主名，例如 movie
        String baseName = stripExtension(inputFileName);
        // 统一的目标扩展名文本，去掉点号并转小写
        String target = FormatTables.normalize(request.targetExtension());
        // 按命名模式计算输出主文件名（不含扩展名）
        String outBase = switch (request.namingMode()) {
            // 保留原名并追加后缀模式：主名取完整输入文件名，例如 movie.mp4
            case KEEP_NAME_ADD_SUFFIX -> inputFileName;
            // 替换扩展名模式：主名取去掉扩展名后的输入文件名
            case REPLACE_EXTENSION -> baseName;
            // 自定义模板模式：按模板替换标记生成文件名
            case CUSTOM_TEMPLATE -> applyTemplate(request.nameTemplate(), baseName, inputFileName, target, request);
        };
        // 拼接初始输出文件名，例如 movie.mp4.mkv 或 movie.mkv
        String candidateName = outBase + "." + target;
        // 构造初始输出路径
        Path candidate = directory.resolve(candidateName);
        // 尝试创建输出目录，保证后续 ffmpeg 能够写出文件
        try {
            // 创建目录树，已存在时不做任何改动
            Files.createDirectories(directory);
        } catch (IOException ex) {
            // 目录创建失败时不中断流程，后续 ffmpeg 会给出明确错误
        }
        // 处理同名冲突：若目标已存在，或与输入文件完全相同，则追加序号
        int index = 1;
        // 循环寻找可用文件名，最多尝试固定次数
        while (index <= MAX_CONFLICT_TRIES) {
            // 判断当前候选是否与输入文件是同一路径（规范化后比较，避免大小写与相对路径干扰）
            boolean sameAsInput = sameFile(candidate, input);
            // 候选文件已存在或与输入相同都视为冲突
            boolean conflict = Files.exists(candidate) || sameAsInput;
            // 无冲突时可以直接使用该候选路径
            if (!conflict) {
                // 返回最终确定的输出路径
                return candidate;
            }
            // 有冲突时在扩展名前插入序号，例如 movie (1).mkv
            candidateName = outBase + " (" + index + ")." + target;
            // 重新构造候选路径
            candidate = directory.resolve(candidateName);
            // 序号递增，准备下一轮判断
            index++;
        }
        // 极端情况下仍冲突，返回最后一次候选路径，交由 ffmpeg 覆盖写出
        return candidate;
    }

    // 判断两个路径是否指向同一文件：优先用规范化绝对路径比较，其次用文件名比较兜底
    private static boolean sameFile(Path a, Path b) {
        // 任一为空时无法比较，直接判定不同
        if (a == null || b == null) {
            // 返回 false 表示不同
            return false;
        }
        // 尝试用规范化绝对路径比较，这是最可靠的判断方式
        try {
            // 同时规范化两个路径并比较文本，忽略大小写以适配 Windows 文件系统
            return a.toAbsolutePath().normalize().toString().equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
        } catch (RuntimeException ex) {
            // 路径非法时退化为文件名比较，仍能覆盖大多数场景
            return a.getFileName() != null && b.getFileName() != null
                    // 文件名忽略大小写比较
                    && a.getFileName().toString().equalsIgnoreCase(b.getFileName().toString());
        }
    }

    // 应用自定义命名模板：把模板中的替换标记换成真实值
    private static String applyTemplate(String template, String baseName, String inputFileName, String target, ConversionRequest request) {
        // 模板为空时退化为“主名_目标格式”，保证总能得到非空文件名
        String effective = (template == null || template.isEmpty()) ? "{name}_{target}" : template;
        // 依次替换全部替换标记，生成最终主文件名
        String result = effective
                // {name} 替换为去掉扩展名的输入文件名
                .replace("{name}", baseName)
                // {ext} 替换为输入文件的原始扩展名
                .replace("{ext}", extensionOf(inputFileName))
                // {target} 替换为目标格式扩展名
                .replace("{target}", target)
                // {date} 替换为当前日期
                .replace("{date}", LocalDateTime.now().format(DATE_FORMAT))
                // {time} 替换为当前时间
                .replace("{time}", LocalDateTime.now().format(TIME_FORMAT))
                // {quality} 替换为质量档位的英文标签
                .replace("{quality}", request.quality().displayName())
                // {category} 替换为类别名称
                .replace("{category}", request.category().name().toLowerCase());
        // 去掉 Windows 文件名非法字符，避免创建文件失败
        return sanitize(result);
    }

    // 去掉文件名中的非法字符，Windows 下不允许出现这些字符
    private static String sanitize(String name) {
        // 用下划线替换路径分隔符与控制字符，避免生成带目录层级或不可见字符的文件名
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        // 去掉首尾空白与点号，Windows 不允许文件名以点号结尾
        cleaned = cleaned.replaceAll("^[\\s.]+", "").replaceAll("[\\s.]+$", "");
        // 清理后为空时给出默认名，避免生成仅含扩展名的文件
        return cleaned.isEmpty() ? "output" : cleaned;
    }

    // 去掉文件名的扩展名部分，没有扩展名时原样返回
    private static String stripExtension(String fileName) {
        // 查找最后一个点号位置
        int dot = fileName.lastIndexOf('.');
        // 点号不存在或位于首位时视为没有扩展名
        if (dot <= 0) {
            // 原样返回文件名
            return fileName;
        }
        // 截取点号之前的部分作为主名
        return fileName.substring(0, dot);
    }

    // 取文件名的扩展名部分，没有扩展名时返回空串
    private static String extensionOf(String fileName) {
        // 查找最后一个点号位置
        int dot = fileName.lastIndexOf('.');
        // 点号不存在时返回空串
        if (dot <= 0 || dot == fileName.length() - 1) {
            // 返回空串表示无扩展名
            return "";
        }
        // 截取点号之后的部分作为扩展名
        return fileName.substring(dot + 1);
    }
}
