package com.xu.universaltranscoder.core;

// 引入 Path，表示任务的输入与输出路径

import java.nio.file.Path;
// 引入 AtomicBoolean，为每个任务提供独立的取消信号，队列停止时可以逐个触发
import java.util.concurrent.atomic.AtomicBoolean;
// 引入 AtomicInteger，为任务分配自增编号，保证界面展示顺序稳定
import java.util.concurrent.atomic.AtomicInteger;

// 关键字 class：Job 描述队列中的一个任务，包含输入、参数、状态与实时进度
// 之所以把任务数据与执行逻辑分开：同一个任务对象既可以被队列调度，也可以被界面直接展示，职责清晰
public final class Job {

    // 全局自增编号，保证每个任务在界面中有稳定且唯一的标识
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);
    // 任务编号
    private final int id;
    // 任务类型
    private final Kind kind;
    // 输入文件路径
    private final Path input;
    // 目标格式文本，用于界面展示
    private final String targetLabel;
    // 转换参数，媒体任务会读取它；文档任务为空
    private final ConversionRequest request;
    // 任务执行逻辑
    private final Task task;
    // 去重键，队列据此避免重复入队
    private final String dedupeKey;
    // 独立取消信号，队列停止或用户移除任务时置位
    private final AtomicBoolean cancelSignal = new AtomicBoolean(false);
    // 媒体总时长（秒），用于进度换算
    private final double durationSeconds;
    // 当前状态，使用 volatile 保证跨线程可见
    private volatile Status status = Status.WAITING;
    // 当前进度百分比，取值 0 至 100
    private volatile double percent;
    // 当前处理速度倍率
    private volatile double speed;
    // 预计剩余秒数，负值表示无法估算
    private volatile double remainingSeconds = -1d;
    // 状态描述文本，会显示给用户因此使用英文
    private volatile String message = "Waiting";
    // 输出文件路径，完成后用于打开输出目录与体积展示
    private volatile Path outputPath;
    // 输出文件体积（字节）
    private volatile long outputSize;
    // 已尝试执行次数，自动补救会使其递增
    private volatile int attempts;

    // 构造方法：创建任务对象，编号自动分配
    public Job(Kind kind, Path input, String targetLabel, ConversionRequest request, Task task,
               String dedupeKey, double durationSeconds) {
        // 分配并保存自增编号
        this.id = SEQUENCE.getAndIncrement();
        // 保存任务类型
        this.kind = kind;
        // 保存输入路径
        this.input = input;
        // 保存目标格式标签
        this.targetLabel = targetLabel;
        // 保存转换参数
        this.request = request;
        // 保存执行逻辑
        this.task = task;
        // 保存去重键
        this.dedupeKey = dedupeKey;
        // 保存媒体总时长
        this.durationSeconds = durationSeconds;
    }

    // 获取任务编号
    public int id() {
        // 返回编号
        return id;
    }

    // 获取任务类型
    public Kind kind() {
        // 返回任务类型
        return kind;
    }

    // 获取输入路径
    public Path input() {
        // 返回输入路径
        return input;
    }

    // 获取输入文件名的展示文本
    public String inputName() {
        // 输入路径为空时返回无数据提示文本
        if (input == null) {
            // 返回英文提示，因为该文案会显示给用户
            return "(no input)";
        }
        // 返回文件名文本
        return input.getFileName() == null ? input.toString() : input.getFileName().toString();
    }

    // 获取目标格式标签
    public String targetLabel() {
        // 返回目标格式标签
        return targetLabel;
    }

    // 获取转换参数
    public ConversionRequest request() {
        // 返回转换参数
        return request;
    }

    // 获取任务执行逻辑
    public Task task() {
        // 返回执行逻辑
        return task;
    }

    // 获取去重键
    public String dedupeKey() {
        // 返回去重键
        return dedupeKey;
    }

    // 获取取消信号，供执行器查询是否需要终止外部进程
    public AtomicBoolean cancelSignal() {
        // 返回取消信号对象
        return cancelSignal;
    }

    // 查询是否已被取消
    public boolean isCancelled() {
        // 直接读取取消信号
        return cancelSignal.get();
    }

    // 请求取消本任务
    public void cancel() {
        // 置位取消信号，执行器会在下一个轮询周期终止进程
        cancelSignal.set(true);
    }

    // 获取当前状态
    public Status status() {
        // 返回状态
        return status;
    }

    // 设置当前状态
    public void status(Status value) {
        // 写入状态
        this.status = value;
    }

    // 获取当前进度百分比
    public double percent() {
        // 返回百分比
        return percent;
    }

    // 设置当前进度百分比，同时钳制到合法区间
    public void percent(double value) {
        // 钳制在 0 至 100 之间，避免进度条出现越界取值
        this.percent = Math.min(Math.max(value, 0d), 100d);
    }

    // 获取当前速度倍率
    public double speed() {
        // 返回速度倍率
        return speed;
    }

    // 设置当前速度倍率
    public void speed(double value) {
        // 写入速度倍率
        this.speed = value;
    }

    // 获取预计剩余秒数
    public double remainingSeconds() {
        // 返回剩余秒数，负值表示无法估算
        return remainingSeconds;
    }

    // 设置预计剩余秒数
    public void remainingSeconds(double value) {
        // 写入剩余秒数
        this.remainingSeconds = value;
    }

    // 获取状态描述文本
    public String message() {
        // 返回描述文本
        return message;
    }

    // 设置状态描述文本
    public void message(String value) {
        // 描述为空时使用状态名称兜底，保证界面不会出现空白文本
        this.message = (value == null || value.trim().isEmpty()) ? status.name() : value;
    }

    // 获取输出文件路径
    public Path outputPath() {
        // 返回输出路径
        return outputPath;
    }

    // 设置输出文件路径
    public void outputPath(Path value) {
        // 写入输出路径
        this.outputPath = value;
    }

    // 获取输出文件体积
    public long outputSize() {
        // 返回体积字节数
        return outputSize;
    }

    // 设置输出文件体积
    public void outputSize(long value) {
        // 写入体积字节数
        this.outputSize = value;
    }

    // 获取媒体总时长
    public double durationSeconds() {
        // 返回总时长
        return durationSeconds;
    }

    // 获取已尝试执行次数
    public int attempts() {
        // 返回次数
        return attempts;
    }

    // 尝试次数加一，返回递增后的值
    public int incrementAttempts() {
        // 先自增再返回，调用方可以据此决定是否继续补救
        return ++attempts;
    }

    // 判断任务是否已经处于终态，终态任务不再被调度
    public boolean isTerminal() {
        // 完成、失败、取消、跳过都属于终态
        return status == Status.COMPLETED || status == Status.FAILED
                // 取消与跳过同样属于终态
                || status == Status.CANCELLED || status == Status.SKIPPED;
    }

    // 生成剩余时间的展示文本
    public String remainingText() {
        // 剩余时间不可估算时返回无数据提示文本
        if (remainingSeconds < 0d || !Double.isFinite(remainingSeconds)) {
            // 返回无数据提示文本
            return "--:--";
        }
        // 把剩余秒数取整
        long totalSeconds = Math.round(remainingSeconds);
        // 换算分钟数
        long minutes = totalSeconds / 60L;
        // 换算剩余秒数
        long seconds = totalSeconds % 60L;
        // 按 mm:ss 格式输出，便于界面表格对齐
        return String.format("%02d:%02d", minutes, seconds);
    }

    // 生成进度百分比的展示文本
    public String percentText() {
        // 未运行时展示为等待中提示
        if (status == Status.WAITING) {
            // 返回等待提示文本
            return "--";
        }
        // 保留一位小数展示百分比
        return String.format("%.1f%%", percent);
    }

    // 生成体积的展示文本
    public String sizeText() {
        // 体积为 0 时返回无数据提示文本
        if (outputSize <= 0L) {
            // 返回无数据提示文本
            return "--";
        }
        // 按兆字节展示，保留两位小数便于阅读
        return String.format(java.util.Locale.ROOT, "%.2f MB", outputSize / 1048576d);
    }

    // 生成速度的展示文本
    public String speedText() {
        // 速度不可用时返回无数据提示文本
        if (speed <= 0d || !Double.isFinite(speed)) {
            // 返回无数据提示文本
            return "--";
        }
        // 保留两位小数并带 x 后缀
        return String.format(java.util.Locale.ROOT, "%.2fx", speed);
    }

    // 定义任务状态枚举，覆盖需求中的等待、运行、完成、失败、取消、跳过六种状态
    public enum Status {
        // 已入队但尚未开始
        WAITING,
        // 正在被工作线程执行
        RUNNING,
        // 已成功完成且产物非空
        COMPLETED,
        // 执行失败
        FAILED,
        // 被用户取消
        CANCELLED,
        // 因去重或缺少必要信息被跳过
        SKIPPED
    }

    // 定义任务类型枚举，队列据此选择不同的执行逻辑
    public enum Kind {
        // 媒体互转（视频/音频/图片）
        MEDIA,
        // 图片合成 PDF
        IMAGE_TO_PDF,
        // 文本生成 PDF
        TEXT_TO_PDF,
        // PDF 渲染为图片
        PDF_TO_IMAGE,
        // 视频抽帧
        FRAME_EXTRACT
    }

    // 任务类型执行接口，队列不关心具体实现，只要求执行方通过上下文回报进度
    public interface Task {
        // 执行任务，通过上下文汇报进度与日志；执行失败时抛出异常由队列归类
        void execute(Job job, Context context) throws Exception;
    }

    // 任务执行上下文，向任务提供进度上报、日志输出与取消查询能力
    public interface Context {
        // 上报进度百分比、速度倍率与剩余秒数
        void progress(double percent, double speed, double remainingSeconds);

        // 输出一行日志
        void log(String line);

        // 查询当前是否已被取消
        boolean isCancelled();
    }
}
