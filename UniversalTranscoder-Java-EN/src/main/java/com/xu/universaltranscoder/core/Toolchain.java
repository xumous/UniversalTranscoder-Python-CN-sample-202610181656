package com.xu.universaltranscoder.core;

// 引入 BufferedReader，用于按行读取子进程的标准输出

import java.io.BufferedReader;
// 引入 IOException，用于处理文件解压与进程启动中的输入输出异常
import java.io.IOException;
// 引入 InputStream，用于读取类路径中的内置可执行文件
import java.io.InputStream;
// 引入 InputStreamReader，把字节流转成字符流以便按行读取
import java.io.InputStreamReader;
// 引入 OutputStream，用于把内置资源写出到本地磁盘
import java.io.OutputStream;
// 引入 Files 工具类，提供路径复制、属性判断与临时目录获取能力
import java.nio.file.Files;
// 引入 Path，作为本工程统一的路径抽象类型，避免混用 String 与 File
import java.nio.file.Path;
// 引入 Paths，用于把字符串拼接结果转成 Path 对象
import java.nio.file.Paths;
// 引入 StandardCopyOption，复制内置资源时用于覆盖已存在的旧文件
import java.nio.file.StandardCopyOption;
// 引入 ArrayList，用于在候选搜集阶段动态追加路径
import java.util.ArrayList;
// 引入 LinkedHashSet，保留插入顺序的同时实现去重，便于日志展示内核支持的编码器
import java.util.LinkedHashSet;
// 引入 List，作为候选目录与候选文件的容器类型
import java.util.List;
// 引入 Set，暴露不可变的编码器/封装/硬件后端集合
import java.util.Set;
// 引入 TimeUnit，用于把等待超时表达成秒级时间单位
import java.util.concurrent.TimeUnit;
// 引入正则 Pattern，用于解析 ffmpeg 能力清单的固定列格式
import java.util.regex.Pattern;
// 引入正则 Matcher，用于对每一行清单做匹配提取
import java.util.regex.Matcher;

// 关键字 class：Toolchain 负责“找到内核 + 校验内核 + 探测内核能力”，是全工程唯一的 FFmpeg 入口
// 之所以做成单例：内核路径与能力清单属于进程级共享信息，重复探测会浪费启动时间
public final class Toolchain {

    // 内置资源在 jar 中的目录名，与工程 resources/ffmpeg 相对应
    private static final String BUNDLED_DIR = "ffmpeg";
    // Windows 平台可执行文件后缀，拼接后用于定位 ffmpeg.exe / ffprobe.exe
    private static final String EXE_SUFFIX = ".exe";
    // 进程探测的超时秒数，避免损坏的内核文件导致界面永久卡死
    private static final int PROBE_TIMEOUT_SECONDS = 20;
    // ffmpeg 版本横幅的特征串，用于校验候选可执行文件确实是 ffmpeg 而不是同名无关程序
    private static final String FFMPEG_BANNER = "ffmpeg version";
    // ffprobe 版本横幅的特征串，用于校验候选可执行文件确实是 ffprobe
    private static final String FFPROBE_BANNER = "ffprobe version";
    // 硬件后端清单行的正则：整行就是一个后端名，允许前后有空白
    private static final Pattern HWACCEL_LINE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*$");

    // 单例引用，使用 volatile 保证多线程下可见性，界面与队列线程都会访问
    private static volatile Toolchain instance;
    // 记录当前单例对应的用户自定义路径，自定义路径变化时需要重新构建内核对象
    private static volatile String instanceCustomPath;

    // ffmpeg 可执行文件的绝对路径，所有转码命令都由它执行
    private final Path ffmpegPath;
    // ffprobe 可执行文件的绝对路径，所有媒体探测都由它执行
    private final Path ffprobePath;
    // ffmpeg 的版本横幅文本，用于 About 页面与日志展示内核版本信息
    private final String ffmpegVersion;
    // ffprobe 的版本横幅文本，与 ffmpeg 版本互相印证
    private final String ffprobeVersion;
    // 内核支持的编码器名称集合，界面据此过滤可选编码器
    private final Set<String> encoders;
    // 内核支持的封装名称集合，界面据此过滤可选目标格式
    private final Set<String> muxFormats;
    // 内核支持的硬件加速后端集合，硬件策略据此挑选可用后端
    private final Set<String> hwaccels;
    // 内核是否可用，为 false 时界面会禁用开始按钮并提示先配置内核路径
    private final boolean available;

    // 私有构造方法，只允许通过 bootstrap 工厂方法创建，保证单例语义
    private Toolchain(Path ffmpegPath, Path ffprobePath, String ffmpegVersion, String ffprobeVersion,
                      Set<String> encoders, Set<String> muxFormats, Set<String> hwaccels, boolean available) {
        // 保存 ffmpeg 路径供执行器使用
        this.ffmpegPath = ffmpegPath;
        // 保存 ffprobe 路径供探测器使用
        this.ffprobePath = ffprobePath;
        // 保存 ffmpeg 版本横幅，About 页面会原样显示
        this.ffmpegVersion = ffmpegVersion;
        // 保存 ffprobe 版本横幅，用于确认探测链路完整
        this.ffprobeVersion = ffprobeVersion;
        // 保存编码器集合，作为只读视图对外暴露
        this.encoders = encoders;
        // 保存封装集合，界面格式下拉框依赖它
        this.muxFormats = muxFormats;
        // 保存硬件后端集合，硬件策略依赖它
        this.hwaccels = hwaccels;
        // 保存可用标记，界面依据它决定是否放行转换操作
        this.available = available;
    }

    // 获取当前单例，若尚未创建则使用空的自定义路径初始化一次
    public static Toolchain get() {
        // 读取当前缓存的单例引用
        Toolchain current = instance;
        // 缓存为空时说明还没有初始化过
        if (current == null) {
            // 走一次完整的定位与探测流程，自定义路径为空
            current = bootstrap(null);
        }
        // 返回可用的单例
        return current;
    }

    // 工厂方法：按用户自定义路径重建内核定位与能力探测，并缓存结果到静态字段
    public static synchronized Toolchain bootstrap(String customFfmpegPath) {
        // 规范化自定义路径文本，null 视为空串以便与缓存值比较
        String requested = customFfmpegPath == null ? "" : customFfmpegPath.trim();
        // 若已有单例且自定义路径未变化，直接复用缓存结果，避免重复拉起 ffmpeg 进程
        if (instance != null && requested.equals(instanceCustomPath)) {
            // 返回已缓存的内核对象
            return instance;
        }
        // 定位 ffmpeg 可执行文件，按“自定义 → 随包 → 程序同级 → 系统 PATH”的顺序逐个验证
        Path ffmpeg = locateBinary("ffmpeg", requested, FFMPEG_BANNER);
        // 定位 ffprobe 可执行文件，其中还会尝试从 ffmpeg 同目录推断，提升命中率
        Path ffprobe = locateProbe(requested, ffmpeg);
        // 声明 ffmpeg 版本横幅变量，定位失败时保存空串
        String ffmpegVersion = "";
        // 声明 ffprobe 版本横幅变量，定位失败时保存空串
        String ffprobeVersion = "";
        // 声明编码器集合变量，定位失败时保持空集合
        Set<String> encoders = new LinkedHashSet<>();
        // 声明封装集合变量，定位失败时保持空集合
        Set<String> muxFormats = new LinkedHashSet<>();
        // 声明硬件后端集合变量，定位失败时保持空集合
        Set<String> hwaccels = new LinkedHashSet<>();
        // 只有 ffmpeg 定位成功才继续做能力探测，否则后续命令都会失败
        if (ffmpeg != null) {
            // 执行 ffmpeg -version 获取完整版本横幅
            ffmpegVersion = firstMatchingLine(runCapture(ffmpeg, "-version"), FFMPEG_BANNER);
            // 解析 ffmpeg -encoders 输出，得到全部编码器名称
            encoders = parseEncoders(runCapture(ffmpeg, "-encoders"));
            // 解析 ffmpeg -formats 输出，得到全部支持封装的名称
            muxFormats = parseFormats(runCapture(ffmpeg, "-formats"));
            // 解析 ffmpeg -hwaccels 输出，得到全部硬件加速后端名称
            hwaccels = parseHwaccels(runCapture(ffmpeg, "-hwaccels"));
        }
        // ffprobe 定位成功时同样获取版本横幅，供界面显示探测链路是否完整
        if (ffprobe != null) {
            // 执行 ffprobe -version 并抽取版本行
            ffprobeVersion = firstMatchingLine(runCapture(ffprobe, "-version"), FFPROBE_BANNER);
        }
        // 判定内核可用：ffmpeg 存在且编码器清单非空，才认为内核真正可用
        boolean available = ffmpeg != null && !encoders.isEmpty();
        // 创建新的内核对象，携带全部定位与探测结果
        Toolchain created = new Toolchain(ffmpeg, ffprobe, ffmpegVersion, ffprobeVersion,
                // 编码器集合转成不可变视图，防止外部误改
                Set.copyOf(encoders),
                // 封装集合同样转成不可变视图
                Set.copyOf(muxFormats),
                // 硬件后端集合转成不可变视图
                Set.copyOf(hwaccels),
                // 传入可用标记
                available);
        // 更新单例引用，后续调用直接命中缓存
        instance = created;
        // 记录本次使用的自定义路径，作为缓存失效判断依据
        instanceCustomPath = requested;
        // 返回构建好的内核对象
        return created;
    }

    // 定位 ffmpeg 文本型可执行文件：按候选优先级逐个执行版本校验
    private static Path locateBinary(String baseName, String customPath, String banner) {
        // 收集全部候选路径，顺序即优先级顺序
        List<Path> candidates = new ArrayList<>();
        // 第一优先级：用户自定义路径；允许传入目录或直接的 exe 文件路径
        if (!customPath.isEmpty()) {
            // 把自定义路径字符串转成 Path 对象
            Path custom = Paths.get(customPath);
            // 自定义路径是目录时，在其下拼接可执行文件名
            if (Files.isDirectory(custom)) {
                // 目录形式追加带后缀的文件名
                candidates.add(custom.resolve(baseName + EXE_SUFFIX));
            } else {
                // 文件形式直接使用该路径，兼容用户直接选中 exe 的情况
                candidates.add(custom);
                // 同时追加同目录下的目标二进制，处理用户误选了另一个 exe 的情况
                Path parent = custom.getParent();
                // 父目录存在时补充同名后缀候选
                if (parent != null) {
                    // 在同目录下寻找目标可执行文件
                    candidates.add(parent.resolve(baseName + EXE_SUFFIX));
                }
            }
        }
        // 第二优先级：随包分发资源；先看工程与安装目录里已经落地的副本，再考虑从 jar 内解压
        Path devBundled = Paths.get("resources", BUNDLED_DIR, baseName + EXE_SUFFIX).toAbsolutePath();
        // 开发态候选存在时优先加入，避免为了取一个已经存在的文件而付出解压大文件的代价
        if (Files.isRegularFile(devBundled)) {
            // 加入开发态随包路径
            candidates.add(devBundled);
        }
        // 程序同级目录下已经放置好的随包副本同样优先于解压
        for (Path dir : programDirectories()) {
            // 同级目录下的 resources/ffmpeg 子目录情况
            Path nested = dir.resolve("resources").resolve(BUNDLED_DIR).resolve(baseName + EXE_SUFFIX);
            // 副本存在时加入候选
            if (Files.isRegularFile(nested)) {
                // 加入已落地的随包路径
                candidates.add(nested);
            }
        }
        // 上述副本都不存在时才从内嵌资源解压到本地缓存目录
        Path bundledLocal = bundledLocalPath(baseName);
        // 解压结果存在时加入候选列表
        if (bundledLocal != null) {
            // 加入随包解压后的可执行文件路径
            candidates.add(bundledLocal);
        }
        // 第三优先级：程序同级目录，包含应用根目录与其 resources 子目录
        for (Path dir : programDirectories()) {
            // 同级目录下直接放置可执行文件的情况
            candidates.add(dir.resolve(baseName + EXE_SUFFIX));
            // 同级目录下的 resources/ffmpeg 子目录情况
            candidates.add(dir.resolve(BUNDLED_DIR).resolve(baseName + EXE_SUFFIX));
            // 同级目录下 resources/ffmpeg 的完整嵌套路径情况
            candidates.add(dir.resolve("resources").resolve(BUNDLED_DIR).resolve(baseName + EXE_SUFFIX));
        }
        // 第四优先级：系统 PATH 环境变量中的可执行文件
        Path onPath = searchPath(baseName + EXE_SUFFIX);
        // PATH 命中时加入候选列表
        if (onPath != null) {
            // 加入 PATH 中找到的路径
            candidates.add(onPath);
        }
        // 逐个候选执行版本校验，第一个通过校验的即为最终内核
        for (Path candidate : candidates) {
            // 候选必须存在且是普通文件，否则直接跳过，避免无意义的进程创建
            if (candidate == null || !Files.isRegularFile(candidate)) {
                // 跳过不存在的候选
                continue;
            }
            // 执行 -version 并检查返回码与版本横幅，做真实校验而不是仅看文件是否存在
            if (isValidBinary(candidate, banner)) {
                // 校验通过则返回该路径，并终止后续候选的探测
                return candidate.toAbsolutePath();
            }
        }
        // 全部候选都不合格时返回 null，表示内核不可用
        return null;
    }

    // 定位 ffprobe：除常规候选外，额外尝试与已定位 ffmpeg 同目录的文件
    private static Path locateProbe(String customPath, Path ffmpeg) {
        // ffmpeg 定位成功时先尝试同目录 ffprobe，因为随包与 PATH 场景下两者通常成对出现
        if (ffmpeg != null) {
            // 取得 ffmpeg 所在目录
            Path dir = ffmpeg.getParent();
            // 目录非空时拼接 ffprobe 文件名
            if (dir != null) {
                // 构造同目录候选路径
                Path sibling = dir.resolve("ffprobe" + EXE_SUFFIX);
                // 候选存在且通过版本校验时直接返回
                if (Files.isRegularFile(sibling) && isValidBinary(sibling, FFPROBE_BANNER)) {
                    // 返回同目录下的 ffprobe 绝对路径
                    return sibling.toAbsolutePath();
                }
            }
        }
        // 常规流程：按用户自定义路径与后续优先级定位 ffprobe
        return locateBinary("ffprobe", customPath, FFPROBE_BANNER);
    }

    // 判断候选是否真的是可用的 ffmpeg 系列程序：要求返回码为 0 且输出包含版本横幅
    private static boolean isValidBinary(Path candidate, String banner) {
        // 执行 -version 并捕获合并后的输出
        List<String> output = runCapture(candidate, "-version");
        // 输出为空说明进程无法启动或无任何输出，直接判定无效
        if (output.isEmpty()) {
            // 返回无效，继续尝试下一个候选
            return false;
        }
        // 逐行检查是否包含版本横幅特征串
        for (String line : output) {
            // 命中横幅说明该程序确实是目标内核
            if (line.contains(banner)) {
                // 返回有效
                return true;
            }
        }
        // 没有横幅则判定为无效候选
        return false;
    }

    // 启动进程执行参数并合并标准输出与错误输出，返回逐行结果
    private static List<String> runCapture(Path executable, String... args) {
        // 结果列表，按行保存进程输出
        List<String> lines = new ArrayList<>();
        // 构建命令参数列表：首个元素是可执行文件绝对路径，随后是参数
        List<String> command = new ArrayList<>();
        // 加入可执行文件路径，使用绝对路径避免工作目录影响
        command.add(executable.toString());
        // 追加调用方传入的全部参数
        for (String arg : args) {
            // 逐个参数加入命令列表
            command.add(arg);
        }
        // 创建进程构建器，使用参数列表形式避免 Windows 引号转义问题
        ProcessBuilder builder = new ProcessBuilder(command);
        // 把错误流合并到标准流，便于一次性读取完整输出
        builder.redirectErrorStream(true);
        // 声明进程引用，便于在异常路径下也能清理
        Process process = null;
        // 尝试启动进程并读取输出
        try {
            // 启动子进程
            process = builder.start();
            // 使用 try-with-resources 包装输入流，确保读取结束后自动关闭
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // 逐行读取输出，readLine 返回 null 表示流结束
                String line;
                // 循环直到没有更多输出
                while ((line = reader.readLine()) != null) {
                    // 把读取到的行加入结果列表
                    lines.add(line);
                }
            }
            // 等待进程结束，最多等待固定超时时间
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // 超时未结束说明内核异常，需要强制终止
            if (!finished) {
                // 强制杀死进程，避免僵尸进程残留
                process.destroyForcibly();
                // 清空结果表示探测失败
                lines.clear();
            } else if (process.exitValue() != 0) {
                // 返回码非 0 表示命令执行失败，清空结果表示探测失败
                lines.clear();
            }
        } catch (IOException ex) {
            // 启动失败（例如文件损坏）时清空结果，交由上层继续尝试下一个候选
            lines.clear();
        } catch (InterruptedException ex) {
            // 线程被中断时恢复中断标记并清空结果，符合中断处理规范
            Thread.currentThread().interrupt();
            // 清空结果表示本次探测失败
            lines.clear();
        } finally {
            // 进程仍存活时做一次兜底清理
            if (process != null && process.isAlive()) {
                // 强制终止残留进程
                process.destroyForcibly();
            }
        }
        // 返回逐行输出结果
        return lines;
    }

    // 从输出中挑出包含指定特征串的第一行，用于抽取版本横幅
    private static String firstMatchingLine(List<String> lines, String token) {
        // 遍历所有输出行
        for (String line : lines) {
            // 命中特征串即返回该行
            if (line.contains(token)) {
                // 返回版本横幅整行文本
                return line.trim();
            }
        }
        // 未命中时返回空串，表示版本信息缺失
        return "";
    }

    // 解析 -encoders 输出，提取全部编码器名称
    private static Set<String> parseEncoders(List<String> lines) {
        // 使用有序集合保存编码器名，便于界面按内核原始顺序展示
        Set<String> result = new LinkedHashSet<>();
        // 遍历清单每一行
        for (String line : lines) {
            // 编码器行的类型标记固定在第 1 列，名称固定从第 7 列开始，用固定列解析可避开空白数量差异
            String name = columnName(line, 1, 7, "VAS");
            // 解析出名称时加入结果集合
            if (name != null) {
                // 加入编码器名
                result.add(name);
            }
        }
        // 返回解析出的编码器集合
        return result;
    }

    // 解析 -formats 输出，提取支持封装的名称（即标记位含 E 的封装）
    private static Set<String> parseFormats(List<String> lines) {
        // 使用有序集合保存封装名
        Set<String> result = new LinkedHashSet<>();
        // 遍历清单每一行
        for (String line : lines) {
            // 只有第 2 列为 E 的行才表示支持写出，其余行要么仅支持读取要么是表头
            if (line != null && line.length() > 7 && line.charAt(2) == 'E') {
                // 封装行的名称固定从第 4 列开始，第 1 列是读取标记位，允许为空格
                String name = columnName(line, 1, 4, "DE. ");
                // 解析出名称时加入结果集合
                if (name != null) {
                    // 加入封装名
                    result.add(name);
                }
            }
        }
        // 返回解析出的封装集合
        return result;
    }

    // 按固定列位置从能力清单行中提取名称，返回 null 表示该行不是有效数据行
    private static String columnName(String line, int typeIndex, int nameStart, String allowedTypes) {
        // 行本身为空或长度不足时无法解析
        if (line == null || line.length() <= nameStart || typeIndex >= line.length()) {
            // 返回 null 表示不是有效数据行
            return null;
        }
        // 取出类型标记字符
        char type = line.charAt(typeIndex);
        // 类型字符不在允许集合内时说明这是表头或分隔行
        if (allowedTypes.indexOf(type) < 0) {
            // 返回 null 表示不是有效数据行
            return null;
        }
        // 截取名称区域并去掉两侧空白
        String rest = line.substring(nameStart).trim();
        // 名称为空时无法解析
        if (rest.isEmpty()) {
            // 返回 null 表示不是有效数据行
            return null;
        }
        // 取出名称区域首字符
        char first = rest.charAt(0);
        // 首字符是表头标志字符时跳过，这些行属于说明性文本
        if (first == '=' || first == '.' || first == '-' || first == ' ') {
            // 返回 null 表示不是有效数据行
            return null;
        }
        // 查找名称中第一个空白字符，用于截断后面的描述文字
        int space = rest.indexOf(' ');
        // 存在空白时只取第一段作为名称
        return space > 0 ? rest.substring(0, space) : rest;
    }

    // 解析 -hwaccels 输出，提取硬件加速后端名称
    private static Set<String> parseHwaccels(List<String> lines) {
        // 使用有序集合保存后端名
        Set<String> result = new LinkedHashSet<>();
        // 标记是否已经越过了“Hardware acceleration methods:”表头行
        boolean inside = false;
        // 遍历输出每一行
        for (String line : lines) {
            // 遇到表头行时把标记置为真，之后的行才是真正的后端清单
            if (line.startsWith("Hardware acceleration")) {
                // 进入清单区域
                inside = true;
                // 表头行本身不需要解析
                continue;
            }
            // 还没进入清单区域时跳过该行
            if (!inside) {
                // 继续读取下一行
                continue;
            }
            // 用正则匹配纯标识符行，排除空行与说明文字
            Matcher matcher = HWACCEL_LINE.matcher(line);
            // 匹配成功说明这是一个后端名
            if (matcher.find()) {
                // 加入结果集合
                result.add(matcher.group(1));
            }
        }
        // 返回解析出的后端集合
        return result;
    }

    // 计算随包资源解压到本地的目标路径，仅在存在内置资源时返回非空
    private static Path bundledLocalPath(String baseName) {
        // 计算本地缓存根目录：Windows 使用 LOCALAPPDATA，其它系统退化为用户主目录
        String localAppData = System.getenv("LOCALAPPDATA");
        // 环境变量缺失时退化为 user.home，保证跨平台可用
        String baseDir = (localAppData == null || localAppData.isEmpty())
                // 使用用户主目录作为兜底
                ? System.getProperty("user.home")
                // 使用 LOCALAPPDATA 作为首选
                : localAppData;
        // 构造本应用的缓存目录，命名为 UniversalTranscoder 便于用户识别
        Path cacheDir = Paths.get(baseDir, "UniversalTranscoder", BUNDLED_DIR);
        // 目标文件路径，即解压后的可执行文件位置
        Path target = cacheDir.resolve(baseName + EXE_SUFFIX);
        // 目标已存在且是可执行文件时无需再解压，直接返回
        if (Files.isRegularFile(target)) {
            // 返回已解压好的路径
            return target;
        }
        // 从类路径读取内置资源，路径与 jar 内目录结构一致
        String resourcePath = "/" + BUNDLED_DIR + "/" + baseName + EXE_SUFFIX;
        // 尝试打开资源流，资源不存在时返回 null
        try (InputStream stream = Toolchain.class.getResourceAsStream(resourcePath)) {
            // 资源为空说明当前运行方式没有随包分发内核，交由后续候选处理
            if (stream == null) {
                // 返回 null 表示没有内置资源
                return null;
            }
            // 创建缓存目录，若已存在则忽略
            Files.createDirectories(cacheDir);
            // 写入临时文件再改名，避免解压中断留下半截文件被误判为可用
            Path temp = cacheDir.resolve(baseName + EXE_SUFFIX + ".part");
            // 使用 try-with-resources 打开输出流写出资源内容
            try (OutputStream out = Files.newOutputStream(temp)) {
                // 使用固定大小缓冲区做流式复制，控制内存占用
                byte[] buffer = new byte[1 << 16];
                // 记录每次实际读取的字节数
                int read;
                // 循环读取直到流结束
                while ((read = stream.read(buffer)) > 0) {
                    // 把本次读取的内容写入临时文件
                    out.write(buffer, 0, read);
                }
            }
            // 把临时文件移动到最终位置，并允许覆盖旧文件
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            // 返回解压完成后的可执行文件路径
            return target;
        } catch (IOException ex) {
            // 解压失败（例如磁盘空间不足）时返回 null，让后续候选继续尝试
            return null;
        }
    }

    // 计算程序所在目录候选集合，覆盖开发态、jpackage app-image 与安装版布局
    private static List<Path> programDirectories() {
        // 使用有序列表保存候选目录
        List<Path> dirs = new ArrayList<>();
        // 加入当前工作目录，开发态直接运行时通常就是工程根目录
        dirs.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
        // 读取 jpackage 启动时注入的应用路径属性，安装版会有该属性
        String appPath = System.getProperty("jpackage.app-path");
        // 属性存在时加入其所在目录
        if (appPath != null && !appPath.isEmpty()) {
            // 把应用路径转成 Path 并取父目录
            Path p = Paths.get(appPath).toAbsolutePath().getParent();
            // 父目录非空时加入候选
            if (p != null) {
                // 加入应用可执行文件所在目录
                dirs.add(p);
            }
        }
        // 通过当前 JVM 的 java.home 推断运行时目录，app-image 下 runtime 的父目录即应用根目录
        Path javaHome = Paths.get(System.getProperty("java.home")).toAbsolutePath();
        // java.home 的父目录通常是应用根目录或 JDK 安装目录
        Path javaHomeParent = javaHome.getParent();
        // 父目录非空时加入候选
        if (javaHomeParent != null) {
            // 加入运行时上级目录
            dirs.add(javaHomeParent);
            // 再上一级也加入，覆盖 runtime 被放在子目录的布局
            Path grandParent = javaHomeParent.getParent();
            // 祖父目录非空时加入
            if (grandParent != null) {
                // 加入祖父目录作为兜底候选
                dirs.add(grandParent);
            }
        }
        // 通过代码源位置推断 jar 所在目录，app-image 下 jar 位于 <app>/app 目录
        try {
            // 取得本类所在代码源的路径
            Path codeSource = Paths.get(Toolchain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // jar 的父目录即 app 目录
            Path jarParent = codeSource.getParent();
            // 父目录非空时加入候选
            if (jarParent != null) {
                // 加入 jar 所在目录
                dirs.add(jarParent);
                // jar 上级目录即应用根目录，也加入候选
                Path appRoot = jarParent.getParent();
                // 应用根目录非空时加入
                if (appRoot != null) {
                    // 加入应用根目录
                    dirs.add(appRoot);
                }
            }
        } catch (Exception ex) {
            // 代码源不可用时忽略该推断路径，不影响其它候选
        }
        // 返回候选目录列表
        return dirs;
    }

    // 在系统 PATH 环境变量中搜索指定名称的可执行文件
    private static Path searchPath(String fileName) {
        // 读取 PATH 环境变量，可能为空
        String path = System.getenv("PATH");
        // PATH 为空时无法搜索，直接返回 null
        if (path == null || path.isEmpty()) {
            // 返回 null 表示未找到
            return null;
        }
        // 按平台路径分隔符切分目录列表
        String[] entries = path.split(java.io.File.pathSeparator);
        // 逐个目录查找目标文件
        for (String entry : entries) {
            // 目录为空串时跳过，避免解析出相对路径
            if (entry == null || entry.trim().isEmpty()) {
                // 继续查找下一个目录
                continue;
            }
            // 跳过带扩展前缀的路径，这类路径含问号等非法字符且不是常规文件位置
            String trimmed = entry.trim().replace("\"", "");
            // 以反斜杠反斜杠问号开头的路径跳过，避免把非法字符交给路径解析器
            if (trimmed.startsWith("\\\\?\\")) {
                // 继续查找下一个目录
                continue;
            }
            // 路径解析可能因非法字符失败，需要捕获异常以保持整体流程可用
            try {
                // 拼接完整候选路径
                Path candidate = Paths.get(trimmed, fileName);
                // 命中普通文件即返回，交由版本校验确认其真实性
                if (Files.isRegularFile(candidate)) {
                    // 返回 PATH 中找到的路径
                    return candidate;
                }
            } catch (RuntimeException ex) {
                // 单个 PATH 条目非法时跳过，继续检查后续条目
            }
        }
        // 全部目录都未命中时返回 null
        return null;
    }

    // 获取 ffmpeg 可执行文件路径，内核不可用时返回 null
    public Path ffmpeg() {
        // 直接返回构造时确定并缓存的路径
        return ffmpegPath;
    }

    // 获取 ffprobe 可执行文件路径，定位失败时返回 null
    public Path ffprobe() {
        // 直接返回构造时确定并缓存的路径
        return ffprobePath;
    }

    // 获取 ffmpeg 版本横幅文本
    public String ffmpegVersion() {
        // 返回缓存中的版本文本
        return ffmpegVersion;
    }

    // 获取 ffprobe 版本横幅文本
    public String ffprobeVersion() {
        // 返回缓存中的版本文本
        return ffprobeVersion;
    }

    // 获取内核支持的编码器集合，界面据此过滤编码器下拉框
    public Set<String> encoders() {
        // 返回不可变集合视图
        return encoders;
    }

    // 获取内核支持的封装集合，界面据此过滤目标格式下拉框
    public Set<String> muxFormats() {
        // 返回不可变集合视图
        return muxFormats;
    }

    // 获取内核支持的硬件加速后端集合
    public Set<String> hwaccels() {
        // 返回不可变集合视图
        return hwaccels;
    }

    // 判断内核是否支持指定编码器
    public boolean hasEncoder(String encoder) {
        // 集合中直接查找名称即可，编码器名大小写固定，无需归一化
        return encoder != null && encoders.contains(encoder);
    }

    // 判断内核是否支持指定封装
    public boolean hasFormat(String format) {
        // 集合中直接查找封装名
        return format != null && muxFormats.contains(format);
    }

    // 判断内核是否声明支持指定硬件后端
    public boolean hasHwaccel(String name) {
        // 集合中直接查找后端名
        return name != null && hwaccels.contains(name);
    }

    // 判断内核整体是否可用
    public boolean isAvailable() {
        // 返回构造时计算的可用标记
        return available;
    }

    // 生成一句话描述，用于界面状态栏与 About 页面展示内核概况
    public String describe() {
        // 内核不可用时给出明确提示，指导用户到 Settings 页配置路径
        if (!available) {
            // 返回英文提示，因为该文案会显示给用户
            return "FFmpeg core not found. Set the path in Settings.";
        }
        // 可用时拼出路径、版本与能力数量，便于快速确认内核状态
        return "Core: " + ffmpegPath + " | encoders: " + encoders.size()
                // 追加封装数量
                + " | muxers: " + muxFormats.size()
                // 追加硬件后端数量
                + " | hwaccels: " + hwaccels.size();
    }
}
