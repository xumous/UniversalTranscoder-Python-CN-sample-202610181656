package com.xu.universaltranscoder.core;

// 引入 BufferedReader，用于按行读取 ffprobe 的 JSON 输出

import java.io.BufferedReader;
// 引入 IOException，处理进程启动与文件读取过程中的输入输出异常
import java.io.IOException;
// 引入 InputStreamReader，把 ffprobe 的字节输出转成字符流
import java.io.InputStreamReader;
// 引入 Files，用于判断待探测文件是否存在以及读取文件大小
import java.nio.file.Files;
// 引入 Path，表示待探测的媒体文件路径
import java.nio.file.Path;
// 引入 ArrayList，用于收集流信息对象
import java.util.ArrayList;
// 引入 List，作为命令参数列表与流列表的类型
import java.util.List;
// 引入 Map，用于承接 JSON 解析后的对象节点
import java.util.Map;
// 引入 TimeUnit，为等待进程结束提供秒级超时单位
import java.util.concurrent.TimeUnit;
// 引入 ConcurrentHashMap，实现线程安全的探测缓存，队列线程与界面线程都会调用探测器
import java.util.concurrent.ConcurrentHashMap;

// 关键字 class：MediaProbe 封装“调用 ffprobe 并解析 JSON”的完整流程
// 之所以做缓存：界面多次刷新同一文件时不应反复拉起 ffprobe 进程，缓存可显著降低等待时间
public final class MediaProbe {

    // 探测超时秒数，超大文件或网络盘上探测可能较慢，这里给足 30 秒
    private static final int TIMEOUT_SECONDS = 30;
    // 探测结果缓存，键由文件绝对路径、最后修改时间与文件大小组成，值是对应的媒体信息
    private static final Map<String, MediaInfo> CACHE = new ConcurrentHashMap<>();

    // 私有构造方法，禁止实例化这个纯静态工具类
    private MediaProbe() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 探测入口：返回文件的媒体信息，失败时返回无效对象而不是抛出异常
    public static MediaInfo probe(Path file) {
        // 文件不存在或不是普通文件时直接返回无效对象
        if (file == null || !Files.isRegularFile(file)) {
            // 返回无效对象，让界面显示可读提示
            return MediaInfo.invalid();
        }
        // 构造缓存键：绝对路径 + 修改时间 + 大小，三者任一变化即视为新文件
        String cacheKey;
        // 读取文件属性过程中可能发生异常，因此需要捕获
        try {
            // 把绝对路径与时间戳、大小拼接成唯一键
            cacheKey = file.toAbsolutePath() + "|" + Files.getLastModifiedTime(file).toMillis() + "|" + Files.size(file);
        } catch (IOException ex) {
            // 属性读取失败时退化为仅使用路径作为键，仍可提供缓存能力
            cacheKey = file.toAbsolutePath().toString();
        }
        // 先查缓存，命中则直接返回，避免重复启动进程
        MediaInfo cached = CACHE.get(cacheKey);
        // 缓存存在时直接返回
        if (cached != null) {
            // 返回缓存的媒体信息
            return cached;
        }
        // 执行真正的探测流程
        MediaInfo probed = runProbe(file);
        // 只有成功的结果才写入缓存，失败结果不缓存以便用户修复后重试
        if (probed.valid()) {
            // 写入缓存，后续调用直接命中
            CACHE.put(cacheKey, probed);
        }
        // 返回探测结果
        return probed;
    }

    // 清空探测缓存，用于设置页切换内核或用户手动刷新时强制重新探测
    public static void clearCache() {
        // 清空全部缓存条目
        CACHE.clear();
    }

    // 执行 ffprobe 命令并解析其 JSON 输出
    private static MediaInfo runProbe(Path file) {
        // 获取当前内核对象
        Toolchain toolchain = Toolchain.get();
        // 内核不可用或 ffprobe 未定位到时无法探测
        if (!toolchain.isAvailable() || toolchain.ffprobe() == null) {
            // 返回无效对象，界面会提示用户配置内核路径
            return MediaInfo.invalid();
        }
        // 构建 ffprobe 参数列表，使用结构化输出便于解析
        List<String> command = new ArrayList<>();
        // 加入 ffprobe 绝对路径，保证与选定的内核版本一致
        command.add(toolchain.ffprobe().toString());
        // 关闭冗余日志，只输出错误信息，避免干扰 JSON
        command.add("-v");
        // error 级别仅输出错误，保证 stdout 是纯净的 JSON
        command.add("error");
        // 指定输出格式为 JSON
        command.add("-print_format");
        // 指定 json 格式
        command.add("json");
        // 请求输出封装层信息
        command.add("-show_format");
        // 请求输出全部流信息
        command.add("-show_streams");
        // 追加待探测文件的绝对路径，避免相对路径在不同工作目录下失效
        command.add(file.toAbsolutePath().toString());
        // 创建进程构建器
        ProcessBuilder builder = new ProcessBuilder(command);
        // 合并错误流到标准流，便于一次性读取全部输出
        builder.redirectErrorStream(true);
        // 声明进程引用，便于异常路径下清理
        Process process = null;
        // 使用 StringBuilder 累积完整 JSON 文本
        StringBuilder json = new StringBuilder();
        // 尝试启动进程并读取输出
        try {
            // 启动 ffprobe 子进程
            process = builder.start();
            // 使用 try-with-resources 打开读取器，确保流被正确关闭
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // 逐行读取输出，readLine 返回 null 表示结束
                String line;
                // 循环直到输出结束
                while ((line = reader.readLine()) != null) {
                    // 追加当前行到 JSON 缓冲区
                    json.append(line);
                    // 追加换行符，保持 JSON 结构可读且不影响解析
                    json.append('\n');
                }
            }
            // 等待进程结束，最多等待固定超时
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // 超时未结束时强制终止，防止残留进程
            if (!finished) {
                // 强制杀死进程
                process.destroyForcibly();
                // 返回无效对象表示探测失败
                return MediaInfo.invalid();
            }
        } catch (IOException ex) {
            // 启动失败时返回无效对象，交由界面提示
            return MediaInfo.invalid();
        } catch (InterruptedException ex) {
            // 线程被中断时恢复中断标记，遵循中断处理约定
            Thread.currentThread().interrupt();
            // 返回无效对象表示探测失败
            return MediaInfo.invalid();
        } finally {
            // 进程仍存活时做一次兜底清理
            if (process != null && process.isAlive()) {
                // 强制终止残留进程
                process.destroyForcibly();
            }
        }
        // 返回解析后的媒体信息
        return parse(json.toString());
    }

    // 把 ffprobe 的 JSON 文本解析为 MediaInfo 对象
    public static MediaInfo parse(String jsonText) {
        // 文本为空时直接返回无效对象
        if (jsonText == null || jsonText.trim().isEmpty()) {
            // 返回无效对象
            return MediaInfo.invalid();
        }
        // 声明根节点引用
        Map<String, Object> root;
        // 解析 JSON 过程中可能出现格式异常，需要捕获以避免线程崩溃
        try {
            // 调用自研 JSON 解析器得到根对象
            Object parsed = Json.parse(jsonText);
            // 根节点不是对象时说明输出异常，返回无效对象
            if (!(parsed instanceof Map)) {
                // 返回无效对象
                return MediaInfo.invalid();
            }
            // 安全强转根节点为 Map，解析器保证对象节点类型一致
            @SuppressWarnings("unchecked")
            // 局部变量承载强转结果
            Map<String, Object> typed = (Map<String, Object>) parsed;
            // 赋值给根节点引用
            root = typed;
        } catch (RuntimeException ex) {
            // 解析失败时返回无效对象，界面显示探测失败
            return MediaInfo.invalid();
        }
        // 取出 format 节点，可能不存在
        Map<String, Object> format = Json.getObject(root, "format");
        // 读取封装格式名，缺失时填空串
        String formatName = Json.getString(format, "format_name", "");
        // 读取总时长（秒），缺失时按 0 处理
        double duration = Json.getDouble(format, "duration", 0d);
        // 读取总码率，缺失时按 0 处理
        long bitRate = (long) Json.getDouble(format, "bit_rate", 0d);
        // 读取文件大小，缺失时按 0 处理
        long size = (long) Json.getDouble(format, "size", 0d);
        // 取出 streams 数组节点，可能不存在
        List<Object> streamNodes = Json.getArray(root, "streams");
        // 准备流信息列表
        List<MediaInfo.Stream> streams = new ArrayList<>();
        // streams 存在时逐条解析
        if (streamNodes != null) {
            // 遍历数组中的每个元素
            for (Object node : streamNodes) {
                // 元素不是对象时跳过，保证解析健壮
                if (!(node instanceof Map)) {
                    // 继续处理下一个元素
                    continue;
                }
                // 安全强转元素为 Map
                @SuppressWarnings("unchecked")
                // 局部变量承载强转结果
                Map<String, Object> streamNode = (Map<String, Object>) node;
                // 解析单条流并加入列表
                streams.add(parseStream(streamNode));
            }
        }
        // 时长缺失时尝试用视频流或音频流的时长补齐，提升进度条准确性
        if (duration <= 0d) {
            // 遍历已解析的流寻找可用时长
            for (MediaInfo.Stream stream : streams) {
                // 流时长大于 0 时采用它
                if (stream.durationSeconds() > 0d) {
                    // 采用该流时长作为总时长
                    duration = stream.durationSeconds();
                    // 找到即停止，避免被更短的流覆盖
                    break;
                }
            }
        }
        // 返回构建好的媒体信息对象，有效标记为 true
        return new MediaInfo(formatName, duration, bitRate, size, streams, true);
    }

    // 解析单条流的 JSON 对象，得到流属性集合
    private static MediaInfo.Stream parseStream(Map<String, Object> node) {
        // 读取流序号，用于日志标识与映射
        int index = Json.getInt(node, "index", -1);
        // 读取流类型，缺失时填空串
        String codecType = Json.getString(node, "codec_type", "");
        // 读取编码名，缺失时填空串
        String codecName = Json.getString(node, "codec_name", "");
        // 读取像素格式，缺失时填空串
        String pixelFormat = Json.getString(node, "pix_fmt", "");
        // 读取宽度，非视频流通常缺失，按 0 处理
        int width = Json.getInt(node, "width", 0);
        // 读取高度，非视频流通常缺失，按 0 处理
        int height = Json.getInt(node, "height", 0);
        // 读取平均帧率原始文本，常见形式为分子/分母
        String frameRateText = Json.getString(node, "avg_frame_rate", "");
        // 帧率文本为空时退化使用 r_frame_rate，部分容器只提供后者
        if (frameRateText.isEmpty()) {
            // 读取 r_frame_rate 作为替代
            frameRateText = Json.getString(node, "r_frame_rate", "");
        }
        // 把帧率分数文本换算成浮点值，无法换算时按 0 处理
        double frameRate = parseRatio(frameRateText);
        // 读取采样率，非音频流按 0 处理
        int sampleRate = Json.getInt(node, "sample_rate", 0);
        // 读取声道数，非音频流按 0 处理
        int channels = Json.getInt(node, "channels", 0);
        // 读取流码率，缺失时按 0 处理
        long bitRate = (long) Json.getDouble(node, "bit_rate", 0d);
        // 读取流时长，缺失时按 0 处理
        double streamDuration = Json.getDouble(node, "duration", 0d);
        // 取出 tags 节点，用于提取语言与标题
        Map<String, Object> tags = Json.getObject(node, "tags");
        // 读取语言标签，缺失时填空串
        String language = Json.getString(tags, "language", "");
        // 读取标题标签，缺失时填空串
        String title = Json.getString(tags, "title", "");
        // 标题缺失时退化使用 handler_name，很多容器用它存放轨道名
        if (title.isEmpty()) {
            // 读取 handler_name 作为替代
            title = Json.getString(tags, "handler_name", "");
        }
        // 构造流信息对象并返回
        return new MediaInfo.Stream(index, codecType, codecName, pixelFormat, width, height,
                // 传入帧率浮点值
                frameRate,
                // 传入帧率原始文本
                frameRateText,
                // 传入采样率
                sampleRate,
                // 传入声道数
                channels,
                // 传入流码率
                bitRate,
                // 传入流时长
                streamDuration,
                // 传入语言
                language,
                // 传入标题
                title);
    }

    // 把 "30000/1001" 形式的分数文本换算成浮点值
    public static double parseRatio(String text) {
        // 文本为空时按 0 处理
        if (text == null || text.isEmpty()) {
            // 返回 0 表示未知帧率
            return 0d;
        }
        // 使用 trim 去掉可能存在的空白
        String trimmed = text.trim();
        // 查找斜杠位置，区分分数与普通数字两种形式
        int slash = trimmed.indexOf('/');
        // 捕获数值解析异常，避免脏数据导致流程中断
        try {
            // 不含斜杠时按普通数字解析
            if (slash < 0) {
                // 直接解析为双精度浮点
                return Double.parseDouble(trimmed);
            }
            // 分子文本
            String numerator = trimmed.substring(0, slash);
            // 分母文本
            String denominator = trimmed.substring(slash + 1);
            // 解析分子
            double num = Double.parseDouble(numerator);
            // 解析分母
            double den = Double.parseDouble(denominator);
            // 分母为 0 时属于非法帧率，按 0 处理
            if (den == 0d) {
                // 返回 0 表示未知帧率
                return 0d;
            }
            // 返回分子除以分母的结果，即真实帧率
            return num / den;
        } catch (NumberFormatException ex) {
            // 解析失败时返回 0，界面会显示未知帧率
            return 0d;
        }
    }
}
