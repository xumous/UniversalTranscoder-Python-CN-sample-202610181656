package com.xu.universaltranscoder.core;

// 引入 BufferedReader，用于按行读取标准输出与错误输出

import java.io.BufferedReader;
// 引入 IOException，处理进程启动与流读取异常
import java.io.IOException;
// 引入 InputStreamReader，把字节流转换为字符流
import java.io.InputStreamReader;
// 引入 StandardCharsets，显式指定字符编码为 UTF-8，避免平台默认编码导致中文路径乱码
import java.nio.charset.StandardCharsets;
// 引入 Files，用于校验输出文件是否存在以及读取体积
import java.nio.file.Files;
// 引入 Path，作为输出路径的类型
import java.nio.file.Path;
// 引入 ArrayList，用于收集错误行
import java.util.ArrayList;
// 引入 Collections，把错误行集合包装为线程安全的同步列表
import java.util.Collections;
// 引入 List 接口，作为错误行集合的类型
import java.util.List;
// 引入 TimeUnit，为等待超时提供时间单位
import java.util.concurrent.TimeUnit;
// 引入 AtomicBoolean，用于跨线程传递取消信号
import java.util.concurrent.atomic.AtomicBoolean;

// 关键字 class：FfmpegRunner 负责真实启动 ffmpeg 进程、解析进度、收集错误并在需要时强制终止
// 之所以把执行与命令构建分离：同一条命令可能被重试（自动补救），执行器需要能被重复调用而不关心参数细节
public final class FfmpegRunner {

    // 请求取消后等待进程自行退出的时间（毫秒），超过该时间就强制杀死
    private static final long GRACEFUL_STOP_MILLIS = 5000L;
    // 取消监视线程的轮询间隔（毫秒），间隔过小会浪费 CPU，过大则取消响应迟钝
    private static final long CANCEL_POLL_MILLIS = 200L;

    // 私有构造方法，禁止实例化这个纯静态执行器
    private FfmpegRunner() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 执行一次转码命令并返回结果，执行过程中通过回调持续上报进度
    public static RunResult run(CommandBuilder.BuiltCommand command, double totalDurationSeconds,
                                AtomicBoolean cancelSignal, Listener listener) {
        // 记录开始时间，用于计算耗时
        long startedAt = System.currentTimeMillis();
        // 错误行使用同步列表，保证读取线程与主线程并发访问安全
        List<String> errorLines = Collections.synchronizedList(new ArrayList<>());
        // 创建进度解析器实例，每个进程独享一份解析状态
        ProgressParser progressParser = new ProgressParser();
        // 构建进程命令，使用参数列表形式避免 Windows 引号转义问题
        ProcessBuilder builder = new ProcessBuilder(command.arguments());
        // 烧录字幕时把进程工作目录切到字幕所在目录，使滤镜只需引用简短文件名
        if (command.workingDirectory() != null) {
            // 设置工作目录
            builder.directory(command.workingDirectory().toFile());
        }
        // 声明进程引用，便于在异常路径下清理
        Process process = null;
        // 声明标准输出读取线程引用
        Thread stdoutThread = null;
        // 声明错误输出读取线程引用
        Thread stderrThread = null;
        // 声明取消监视线程引用
        Thread cancelThread = null;
        // 尝试启动进程并完成读取
        try {
            // 启动 ffmpeg 子进程
            process = builder.start();
            // 把进程引用交给取消监视线程使用，lambda 需要最终变量因此先复制一份
            Process runningProcess = process;
            // 创建取消监视线程：轮询取消信号，收到后先优雅终止再强制终止
            cancelThread = new Thread(() -> {
                // 循环直到进程结束
                while (runningProcess.isAlive()) {
                    // 检测到取消信号时开始终止流程
                    if (cancelSignal != null && cancelSignal.get()) {
                        // 先请求优雅终止，给 ffmpeg 机会写完尾部数据
                        runningProcess.destroy();
                        // 捕获等待异常，保证监视线程不会因异常退出
                        try {
                            // 等待进程自行退出，最多等待固定时长
                            if (!runningProcess.waitFor(GRACEFUL_STOP_MILLIS, TimeUnit.MILLISECONDS)) {
                                // 超时仍未退出则强制杀死，避免残留进程占用文件
                                runningProcess.destroyForcibly();
                            }
                        } catch (InterruptedException ex) {
                            // 恢复中断标记，遵循中断处理约定
                            Thread.currentThread().interrupt();
                        }
                        // 终止流程完成后结束监视线程
                        return;
                    }
                    // 未收到取消信号时短暂休眠再检查，避免空转消耗 CPU
                    try {
                        // 休眠固定轮询间隔
                        Thread.sleep(CANCEL_POLL_MILLIS);
                    } catch (InterruptedException ex) {
                        // 恢复中断标记并结束监视线程
                        Thread.currentThread().interrupt();
                        // 结束监视循环
                        return;
                    }
                }
            }, "ffmpeg-cancel-watcher");
            // 把监视线程设为守护线程，保证它不会阻止程序退出
            cancelThread.setDaemon(true);
            // 启动取消监视线程
            cancelThread.start();
            // 创建标准输出读取线程：逐行读取进度块并交给解析器
            stdoutThread = new Thread(() -> {
                // 使用 UTF-8 编码读取，保证含中文的路径与标签不乱码
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    // 逐行读取进度输出
                    String line;
                    // 循环直到输出结束
                    while ((line = reader.readLine()) != null) {
                        // 把该行交给进度解析器，解析出完整块后回调上层
                        progressParser.feed(line, update -> {
                            // 回调非空时才派发，避免空指针
                            if (listener != null) {
                                // 派发进度更新
                                listener.onProgress(update);
                            }
                        });
                    }
                } catch (IOException ex) {
                    // 流读取异常通常发生在进程被强制终止时，属于预期情况，不额外处理
                }
            }, "ffmpeg-stdout-reader");
            // 设为守护线程，避免影响程序退出
            stdoutThread.setDaemon(true);
            // 启动标准输出读取线程
            stdoutThread.start();
            // 创建错误输出读取线程：逐行收集错误信息
            stderrThread = new Thread(() -> {
                // 使用 UTF-8 编码读取错误输出
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    // 逐行读取
                    String line;
                    // 循环直到错误输出结束
                    while ((line = reader.readLine()) != null) {
                        // 把错误行加入同步集合，供错误分类使用
                        errorLines.add(line);
                        // 同步把错误行作为日志上报，便于界面实时显示进度之外的提示
                        if (listener != null && !line.trim().isEmpty()) {
                            // 派发日志行
                            listener.onLog(line);
                        }
                    }
                } catch (IOException ex) {
                    // 流读取异常属于预期情况，不额外处理
                }
            }, "ffmpeg-stderr-reader");
            // 设为守护线程
            stderrThread.setDaemon(true);
            // 启动错误读取线程
            stderrThread.start();
            // 等待进程结束，不设超时以保证长视频能完整转码
            int exitCode = process.waitFor();
            // 等待两个读取线程收尾，保证错误行集合完整
            joinQuietly(stdoutThread);
            // 等待错误读取线程收尾
            joinQuietly(stderrThread);
            // 计算总耗时
            long elapsed = System.currentTimeMillis() - startedAt;
            // 判断本次是否被用户取消
            boolean cancelled = cancelSignal != null && cancelSignal.get();
            // 计算输出文件体积，文件不存在时返回 0
            long outputSize = sizeOf(command.outputPath());
            // 判定成功条件：退出码为 0 且产物存在且体积大于 0，三项同时满足才算真实成功
            boolean success = !cancelled && exitCode == 0 && outputSize > 0;
            // 组装执行结果并返回
            return new RunResult(success, cancelled, exitCode, errorLines, elapsed, command.outputPath(), outputSize);
        } catch (IOException ex) {
            // 进程启动失败时把异常信息作为错误行，便于错误分类给出可读原因
            errorLines.add("Failed to start FFmpeg: " + ex.getMessage());
            // 返回失败结果
            return new RunResult(false, false, -1, errorLines, System.currentTimeMillis() - startedAt,
                    // 输出路径照常返回，供界面显示预期位置
                    command.outputPath(),
                    // 体积为 0 表示没有产物
                    0L);
        } catch (InterruptedException ex) {
            // 线程被中断时恢复中断标记
            Thread.currentThread().interrupt();
            // 尽力终止仍在运行的进程，避免残留
            if (process != null && process.isAlive()) {
                // 强制终止残留进程
                process.destroyForcibly();
            }
            // 把中断事实写入错误行，便于界面提示
            errorLines.add("Interrupted while waiting for FFmpeg.");
            // 返回失败结果
            return new RunResult(false, true, -1, errorLines, System.currentTimeMillis() - startedAt,
                    // 输出路径照常返回
                    command.outputPath(),
                    // 体积为 0
                    0L);
        } finally {
            // 进程仍存活时做兜底清理，防止文件句柄泄漏
            if (process != null && process.isAlive()) {
                // 强制终止残留进程
                process.destroyForcibly();
            }
        }
    }

    // 静默等待一个线程结束，忽略中断异常并恢复中断标记
    private static void joinQuietly(Thread thread) {
        // 线程为空时无需等待
        if (thread == null) {
            // 直接返回
            return;
        }
        // 尝试等待最多五秒，避免读取线程异常悬挂
        try {
            // 等待线程结束
            thread.join(GRACEFUL_STOP_MILLIS);
        } catch (InterruptedException ex) {
            // 恢复中断标记
            Thread.currentThread().interrupt();
        }
    }

    // 读取文件体积，文件不存在或读取失败时返回 0
    private static long sizeOf(Path path) {
        // 路径为空时返回 0
        if (path == null) {
            // 返回 0 表示无法读取
            return 0L;
        }
        // 捕获属性读取异常，保证执行流程不中断
        try {
            // 文件存在时返回字节数
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ex) {
            // 读取失败时返回 0
            return 0L;
        }
    }

    // 进度与日志回调接口，执行器通过它在转换过程中向上层持续汇报
    public interface Listener {
        // 收到一个完整进度块时调用
        void onProgress(ProgressParser.ProgressUpdate update);

        // 收到一行原始日志时调用，便于界面展示内核输出
        void onLog(String line);
    }

    // 静态嵌套类 RunResult：承载一次执行的完整结果
    public static final class RunResult {

        // 是否执行成功（退出码为 0 且产物存在）
        private final boolean success;
        // 是否被用户取消
        private final boolean cancelled;
        // 进程退出码，取消时为负值或 0 由平台决定
        private final int exitCode;
        // 错误输出行集合，用于错误分类与日志展示
        private final List<String> errorLines;
        // 执行耗时（毫秒），用于展示速度信息
        private final long elapsedMillis;
        // 输出文件路径
        private final Path outputPath;
        // 输出文件体积（字节），为 0 表示文件缺失
        private final long outputSize;

        // 构造方法：一次性接收全部执行结果
        RunResult(boolean success, boolean cancelled, int exitCode, List<String> errorLines,
                  long elapsedMillis, Path outputPath, long outputSize) {
            // 保存成功标记
            this.success = success;
            // 保存取消标记
            this.cancelled = cancelled;
            // 保存退出码
            this.exitCode = exitCode;
            // 保存错误行集合
            this.errorLines = errorLines;
            // 保存耗时
            this.elapsedMillis = elapsedMillis;
            // 保存输出路径
            this.outputPath = outputPath;
            // 保存输出体积
            this.outputSize = outputSize;
        }

        // 判断是否成功
        public boolean success() {
            // 返回成功标记
            return success;
        }

        // 判断是否被取消
        public boolean cancelled() {
            // 返回取消标记
            return cancelled;
        }

        // 获取退出码
        public int exitCode() {
            // 返回退出码
            return exitCode;
        }

        // 获取错误行集合
        public List<String> errorLines() {
            // 返回错误行集合
            return errorLines;
        }

        // 获取执行耗时
        public long elapsedMillis() {
            // 返回耗时毫秒数
            return elapsedMillis;
        }

        // 获取输出文件路径
        public Path outputPath() {
            // 返回输出路径
            return outputPath;
        }

        // 获取输出文件体积
        public long outputSize() {
            // 返回体积字节数
            return outputSize;
        }

        // 生成一行结果摘要，供日志与状态标签展示
        public String describe() {
            // 取消场景优先描述取消
            if (cancelled) {
                // 返回英文取消提示，因为该文案会显示给用户
                return "Cancelled.";
            }
            // 成功场景展示体积与耗时
            if (success) {
                // 按兆字节展示体积，保留两位小数便于阅读
                return "Completed in " + elapsedMillis + " ms, output size "
                        // 换算成兆字节
                        + String.format(java.util.Locale.ROOT, "%.2f", outputSize / 1048576d) + " MB.";
            }
            // 失败场景展示退出码
            return "Failed with exit code " + exitCode + ".";
        }
    }
}
