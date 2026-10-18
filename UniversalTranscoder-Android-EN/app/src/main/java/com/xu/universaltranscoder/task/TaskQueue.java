package com.xu.universaltranscoder.task;

// 引入 Context：调度器需要上下文访问设置与转换引擎。

import android.content.Context;
// 引入 ContentResolver/Cursor/OpenableColumns：查询源文件大小用于估算处理速度。
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

// 引入转换引擎与异常类型。
import com.xu.universaltranscoder.core.ConversionEngine;
import com.xu.universaltranscoder.core.ConversionException;
import com.xu.universaltranscoder.core.ErrorReason;
import com.xu.universaltranscoder.core.ProgressCallback;
// 引入模型类。
import com.xu.universaltranscoder.model.ConversionJob;
import com.xu.universaltranscoder.model.JobStatus;
// 引入设置与日志。
import com.xu.universaltranscoder.util.AppSettings;
import com.xu.universaltranscoder.util.LogBus;

// 引入并发容器与线程池。
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TaskQueue 是转换任务的调度中心。
 * <p>
 * 它实现的需求要点：
 * - 串行调度：默认并发上限为 1，用单线程 ExecutorService 串行执行，避免多个任务争抢硬件编解码器；
 * 并发数可在设置页调整（1..4），调度器会按新并发数重建线程池。
 * - 暂停/继续：暂停时正在运行的任务会在“安全点”（一个样本/一帧处理完之后）阻塞，恢复后继续，进度不丢失。
 * - 停止：取消全部待运行与运行中的任务，并把状态标记为已取消。
 * - 去重：同一源文件 + 同一目标 + 同一参数的任务只保留一个，重复入队会被拒绝并计数。
 * - 独立进度与状态：每个任务对象自带进度、速度、耗时与状态，界面按任务逐条绘制。
 */
public final class TaskQueue {

    // 单例实例。
    private static TaskQueue instance;
    // 任务编号发生器，保证编号全进程唯一。
    private final AtomicLong idGenerator = new AtomicLong(1L);
    // 应用上下文。
    private final Context appContext;
    // 任务表：使用 LinkedHashMap 保持入队顺序，同时便于按编号查找。
    private final Map<Long, ConversionJob> jobs = new LinkedHashMap<>();
    // 任务表访问锁：调度器在后台线程读写，界面在主线程读取，必须加锁保护。
    private final Object jobsLock = new Object();
    // 监听器集合：界面注册后会在任务状态变化时收到通知。
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    // 主线程 Handler：把通知切回主线程执行。
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 暂停/恢复的同步对象与条件标记。
    private final Object pauseLock = new Object();
    // 已被请求取消的任务编号集合。
    private final Set<Long> cancelRequests = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Long, Boolean>());
    // 线程池引用，按并发设置创建。
    private volatile ExecutorService executor;
    // 当前线程池使用的并发数，用于判断设置变化后是否需要重建线程池。
    private volatile int executorConcurrency = -1;
    // 暂停标记：为 true 时所有任务在安全点等待。
    private volatile boolean paused = false;
    // 因去重而被跳过的任务计数，供界面提示。
    private volatile int lastSkippedDuplicates = 0;

    // 私有构造：外部必须通过 get() 获取单例。
    private TaskQueue(Context context) {
        // 保存应用上下文，避免持有 Activity 导致内存泄漏。
        appContext = context.getApplicationContext();
    }

    /**
     * 取得单例。
     */
    public static synchronized TaskQueue get(Context context) {
        // 尚未创建时创建。
        if (instance == null) {
            // 构造单例。
            instance = new TaskQueue(context);
        }
        // 返回单例。
        return instance;
    }

    /**
     * 注册监听器。
     */
    public void addListener(Listener listener) {
        // 空值忽略。
        if (listener == null) {
            // 直接返回。
            return;
        }
        // 加入集合。
        listeners.add(listener);
    }

    /**
     * 注销监听器。
     */
    public void removeListener(Listener listener) {
        // 从集合移除。
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器（切到主线程）。
     */
    private void notifyChanged() {
        // 直接投递到主线程执行，避免在后台线程操作界面。
        mainHandler.post(new Runnable() {
            // 匿名任务：分发通知。
            @Override
            public void run() {
                // 遍历监听器。
                for (Listener listener : listeners) {
                    // 单个监听器异常不应影响其它监听器。
                    try {
                        // 回调。
                        listener.onQueueChanged();
                    } catch (Throwable ignored) {
                        // 忽略监听器异常。
                    }
                }
            }
        });
    }

    /**
     * 加入一个任务。
     *
     * @return true 表示已入队；false 表示因为重复而被跳过。
     */
    public boolean enqueue(String sourceUri, String sourceName, com.xu.universaltranscoder.model.TargetSpec target,
                           com.xu.universaltranscoder.model.ConvertOptions options, String outputTreeUri) {
        // 计算去重键。
        String dedupeKey = sourceUri + "|" + target.key + "|" + options.describe();
        // 加锁检查并写入。
        synchronized (jobsLock) {
            // 遍历现有任务做去重判断。
            for (ConversionJob existing : jobs.values()) {
                // 只与“尚未结束”的任务比较：已完成的任务允许再次转换。
                if (!existing.status.isTerminal() && existing.dedupeKey.equals(dedupeKey)) {
                    // 重复任务直接跳过。
                    return false;
                }
            }
            // 分配新编号。
            long id = idGenerator.getAndIncrement();
            // 创建任务对象。
            ConversionJob job = new ConversionJob(id, sourceUri, sourceName, target, options, outputTreeUri, dedupeKey);
            // 默认状态为排队中。
            job.status = JobStatus.PENDING;
            // 写入任务表。
            jobs.put(id, job);
        }
        // 通知界面刷新。
        notifyChanged();
        // 返回入队成功。
        return true;
    }

    /**
     * 记录本批入队被跳过的重复任务数量（界面据此给一次提示）。
     */
    public void setSkippedDuplicates(int count) {
        // 保存计数。
        lastSkippedDuplicates = count;
    }

    /**
     * 读取上一次入队被跳过的重复任务数量。
     */
    public int skippedDuplicates() {
        // 返回计数。
        return lastSkippedDuplicates;
    }

    /**
     * 返回任务列表快照（按入队顺序），供界面绘制。
     */
    public List<ConversionJob> snapshot() {
        // 加锁复制一份，避免界面遍历时任务表被修改。
        synchronized (jobsLock) {
            // 返回副本。
            return new ArrayList<>(jobs.values());
        }
    }

    /**
     * 返回处于排队状态的任务数量。
     */
    public int pendingCount() {
        // 计数。
        int count = 0;
        // 加锁遍历。
        synchronized (jobsLock) {
            // 统计。
            for (ConversionJob job : jobs.values()) {
                // 累加排队任务。
                if (job.status == JobStatus.PENDING) {
                    // 计数递增。
                    count++;
                }
            }
        }
        // 返回统计结果。
        return count;
    }

    /**
     * 返回处于终态的任务数量（用于界面汇总）。
     */
    public int terminalCount(JobStatus status) {
        // 计数。
        int count = 0;
        // 加锁遍历。
        synchronized (jobsLock) {
            // 统计。
            for (ConversionJob job : jobs.values()) {
                // 命中目标状态时累加。
                if (job.status == status) {
                    // 计数递增。
                    count++;
                }
            }
        }
        // 返回统计结果。
        return count;
    }

    /**
     * 启动调度：把所有排队中的任务提交给线程池。
     * 线程池大小取自设置页的并发数；并发数变化时会重建线程池。
     */
    public void start() {
        // 读取期望并发数。
        int concurrency = AppSettings.get(appContext).concurrency();
        // 需要重建线程池时先重建。
        ensureExecutor(concurrency);
        // 清除暂停标记，让任务可以立即跑起来。
        resume();
        // 取出当前所有排队任务。
        List<ConversionJob> pendingJobs = new ArrayList<>();
        // 加锁筛选。
        synchronized (jobsLock) {
            // 遍历任务表。
            for (ConversionJob job : jobs.values()) {
                // 只提交排队中的任务。
                if (job.status == JobStatus.PENDING) {
                    // 加入待提交列表。
                    pendingJobs.add(job);
                }
            }
        }
        // 逐个提交。
        for (ConversionJob job : pendingJobs) {
            // 提交给线程池执行。
            executor.execute(new JobRunner(job));
            // 记录日志。
            LogBus.info("Queue", "Job #" + job.id + " submitted (" + job.target.key + ")");
        }
        // 若存在待提交任务则通知界面刷新。
        if (!pendingJobs.isEmpty()) {
            // 通知界面。
            notifyChanged();
        }
    }

    /**
     * 按需创建或重建线程池。
     */
    private void ensureExecutor(int concurrency) {
        // 并发数未变化且线程池可用时无需重建。
        if (executor != null && !executor.isShutdown() && executorConcurrency == concurrency) {
            // 直接返回。
            return;
        }
        // 旧线程池存在时先优雅关闭，避免线程泄漏。
        if (executor != null && !executor.isShutdown()) {
            // 关闭旧线程池（不等待已提交任务完成，因为此时应已无运行任务）。
            executor.shutdown();
        }
        // 创建固定线程池；并发数为 1 时即为严格串行调度。
        executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        // 记录当前并发数。
        executorConcurrency = concurrency;
        // 记录日志。
        LogBus.info("Queue", "Scheduler ready with concurrency=" + concurrency);
    }

    /**
     * 暂停调度：正在运行的任务会在安全点阻塞。
     */
    public void pause() {
        // 设置暂停标记。
        paused = true;
        // 记录日志。
        LogBus.info("Queue", "Queue paused");
        // 通知界面刷新按钮可用状态。
        notifyChanged();
    }

    /**
     * 恢复调度：唤醒所有等待中的任务。
     */
    public void resume() {
        // 清除暂停标记。
        paused = false;
        // 唤醒所有等待线程。
        synchronized (pauseLock) {
            // 广播唤醒。
            pauseLock.notifyAll();
        }
        // 记录日志。
        LogBus.info("Queue", "Queue resumed");
        // 通知界面刷新。
        notifyChanged();
    }

    /**
     * 停止：取消所有未完成任务。
     */
    public void stop() {
        // 取出所有未完成任务编号。
        List<ConversionJob> active = new ArrayList<>();
        // 加锁筛选。
        synchronized (jobsLock) {
            // 遍历任务表。
            for (ConversionJob job : jobs.values()) {
                // 非终态任务都需要处理。
                if (!job.status.isTerminal()) {
                    // 加入待处理列表。
                    active.add(job);
                }
            }
        }
        // 逐个请求取消。
        for (ConversionJob job : active) {
            // 标记取消请求：运行中的任务会在安全点退出。
            cancelRequests.add(job.id);
            // 排队中的任务可以直接标记为已取消，因为它还没有开始执行。
            if (job.status == JobStatus.PENDING) {
                // 更新状态。
                job.status = JobStatus.CANCELED;
                // 补充状态文本。
                job.statusText = "Canceled before start";
                // 记录结束时间。
                job.finishedAtMs = System.currentTimeMillis();
            }
        }
        // 保证等待中的线程被唤醒，从而尽快看到取消标记。
        synchronized (pauseLock) {
            // 广播唤醒。
            pauseLock.notifyAll();
        }
        // 记录日志。
        LogBus.info("Queue", "Stop requested for " + active.size() + " job(s)");
        // 通知界面刷新。
        notifyChanged();
    }

    /**
     * 清理：移除所有已处于终态的任务（保留正在运行与排队的任务）。
     */
    public void clearFinished() {
        // 记录移除数量。
        int removed = 0;
        // 加锁修改任务表。
        synchronized (jobsLock) {
            // 使用迭代器安全删除。
            java.util.Iterator<Map.Entry<Long, ConversionJob>> iterator = jobs.entrySet().iterator();
            // 遍历。
            while (iterator.hasNext()) {
                // 取条目。
                Map.Entry<Long, ConversionJob> entry = iterator.next();
                // 终态任务可以移除。
                if (entry.getValue().status.isTerminal()) {
                    // 删除该任务。
                    iterator.remove();
                    // 计数。
                    removed++;
                    // 同时清理其取消标记，避免编号集合无限增长。
                    cancelRequests.remove(entry.getKey());
                }
            }
        }
        // 记录日志。
        LogBus.info("Queue", "Cleared " + removed + " finished job(s)");
        // 通知界面刷新。
        notifyChanged();
    }

    /**
     * 返回任务是否已被请求取消。
     */
    private boolean isCancellationRequested(long jobId) {
        // 查询集合。
        return cancelRequests.contains(jobId);
    }

    /**
     * 查询源文件字节大小；查询失败时返回 0（速度展示会退化为不可用，但任务本身不受影响）。
     */
    private long querySourceSize(String uriString) {
        // 源地址为空时返回 0。
        if (uriString == null || uriString.isEmpty()) {
            // 返回 0。
            return 0L;
        }
        // 取得内容解析器。
        ContentResolver resolver = appContext.getContentResolver();
        // 游标引用。
        Cursor cursor = null;
        // 尝试查询。
        try {
            // 查询 SIZE 列。
            cursor = resolver.query(Uri.parse(uriString), new String[]{OpenableColumns.SIZE}, null, null, null);
            // 有结果时读取第一行。
            if (cursor != null && cursor.moveToFirst()) {
                // 取列下标。
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                // 列存在且非空时返回。
                if (index >= 0 && !cursor.isNull(index)) {
                    // 返回文件大小。
                    return cursor.getLong(index);
                }
            }
        } catch (Throwable ignored) {
            // 查询失败（提供者未实现 SIZE 列等）时忽略，返回 0。
        } finally {
            // 关闭游标。
            if (cursor != null) {
                // 释放资源。
                cursor.close();
            }
        }
        // 返回 0 表示大小未知。
        return 0L;
    }

    /**
     * 队列变化监听器：界面实现它来刷新列表。
     */
    public interface Listener {
        // 队列内容或任务状态发生变化时回调（已在主线程）。
        void onQueueChanged();
    }

    /**
     * JobRunner 是单个任务的执行体，同时实现 ProgressCallback，
     * 这样它既能把进度从转换器内部“接出来”，又能把暂停/取消语义“传进去”。
     */
    private final class JobRunner implements Runnable, ProgressCallback {

        // 本次负责的任务。
        private final ConversionJob job;
        // 源文件字节大小，用于把“进度百分比”换算成估算吞吐速度。
        private long sourceBytes;
        // 上一次采样时间（纳秒），用于计算速度。
        private long lastSampleNanos;
        // 上一次采样时估算已处理的字节数。
        private long lastProcessedBytes;
        // 平滑后的吞吐速度（字节/秒），使用指数移动平均，避免数字剧烈跳动。
        private double smoothedBytesPerSecond;

        // 构造：绑定任务。
        JobRunner(ConversionJob job) {
            // 保存任务引用。
            this.job = job;
        }

        // 线程执行体：完成一个任务的完整生命周期管理。
        @Override
        public void run() {
            // 查询源文件大小，用于速度估算。
            sourceBytes = querySourceSize(job.sourceUri);
            // 标记进入运行状态。
            job.status = JobStatus.RUNNING;
            // 记录开始时间。
            job.startedAtMs = System.currentTimeMillis();
            // 初始化状态文本。
            job.statusText = "Starting";
            // 初始化采样基准时间。
            lastSampleNanos = System.nanoTime();
            // 初始化已处理字节估算。
            lastProcessedBytes = 0L;
            // 通知界面刷新。
            notifyChanged();
            // 记录日志。
            LogBus.info("Job#" + job.id, "Started: " + job.sourceName + " -> " + job.target.label);
            // 执行转换。
            try {
                // 把本对象作为进度回调交给引擎。
                ConversionEngine.execute(appContext, job, this);
                // 成功后标记完成。
                job.status = JobStatus.DONE;
                // 进度固定为 100%。
                job.progress = 1.0;
                // 状态文本说明产出位置。
                job.statusText = "Done"
                        + (job.outputPaths.isEmpty() ? "" : " -> " + job.outputPaths.get(0));
                // 记录日志。
                LogBus.info("Job#" + job.id, "Finished successfully");
            } catch (ConversionException error) {
                // 用户取消时标记为已取消。
                if (error.reason() == ErrorReason.CANCELED) {
                    // 更新状态。
                    job.status = JobStatus.CANCELED;
                    // 更新状态文本。
                    job.statusText = "Canceled";
                    // 记录日志。
                    LogBus.info("Job#" + job.id, "Canceled");
                } else {
                    // 其它失败标记为失败并保存分类结果。
                    job.status = JobStatus.FAILED;
                    // 保存分类。
                    job.errorReason = error.reason();
                    // 保存技术细节。
                    job.errorDetail = error.detail();
                    // 状态文本使用分类的英文原因，便于用户直接阅读。
                    job.statusText = error.reason().reason;
                    // 记录错误日志。
                    LogBus.error("Job#" + job.id, error.reason().oneLine()
                            + (error.detail().isEmpty() ? "" : " | " + error.detail()));
                }
            } catch (Throwable error) {
                // 未预期异常统一归类为未知错误。
                job.status = JobStatus.FAILED;
                // 保存分类。
                job.errorReason = ErrorReason.UNKNOWN;
                // 保存细节。
                job.errorDetail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                // 状态文本兜底。
                job.statusText = ErrorReason.UNKNOWN.reason;
                // 记录错误日志。
                LogBus.error("Job#" + job.id, ErrorReason.UNKNOWN.oneLine() + " | " + job.errorDetail);
            } finally {
                // 记录结束时间。
                job.finishedAtMs = System.currentTimeMillis();
                // 从取消集合移除，避免长期占用。
                cancelRequests.remove(job.id);
                // 通知界面刷新。
                notifyChanged();
            }
        }

        // 进度上报：更新进度、时间与估算速度。
        @Override
        public void reportProgress(double progress, double bytesPerSecond, String statusText) {
            // 把进度限制在 0..1。
            job.progress = Math.max(0.0, Math.min(1.0, progress));
            // 状态文本非空时更新。
            if (statusText != null && !statusText.isEmpty()) {
                // 保存状态文本。
                job.statusText = statusText;
            }
            // 计算当前估算已处理字节数：用进度乘以源文件大小。
            long processedBytes = (long) (job.progress * (double) Math.max(0L, sourceBytes));
            // 取当前时间。
            long nowNanos = System.nanoTime();
            // 计算两次采样之间的时间差（秒）。
            double deltaSeconds = (nowNanos - lastSampleNanos) / 1_000_000_000.0;
            // 时间差过大或过小时直接跳过速度计算，避免数值抖动。
            if (deltaSeconds >= 0.25) {
                // 计算字节增量。
                long deltaBytes = processedBytes - lastProcessedBytes;
                // 增量为正时计算瞬时速度。
                double instant = deltaBytes > 0L ? deltaBytes / deltaSeconds : 0.0;
                // 使用指数移动平均平滑速度，系数 0.4 兼顾响应与稳定。
                smoothedBytesPerSecond = smoothedBytesPerSecond <= 0.0 ? instant
                        : smoothedBytesPerSecond * 0.6 + instant * 0.4;
                // 写回任务对象供界面展示。
                job.bytesPerSecond = smoothedBytesPerSecond;
                // 更新采样基准。
                lastSampleNanos = nowNanos;
                // 更新已处理字节基准。
                lastProcessedBytes = processedBytes;
                // 通知界面刷新（节流后的刷新频率完全够用）。
                notifyChanged();
            }
        }

        // 取消判断：查询取消集合。
        @Override
        public boolean shouldCancel() {
            // 返回任务是否被请求取消。
            return isCancellationRequested(job.id);
        }

        // 暂停检查：暂停时在安全点等待。
        @Override
        public void checkPaused() throws ConversionException {
            // 先判断取消，取消优先于暂停，保证“停止”能立刻生效。
            if (shouldCancel()) {
                // 抛出取消异常。
                throw new ConversionException(ErrorReason.CANCELED);
            }
            // 未被暂停时直接返回，避免任何额外开销。
            if (!paused) {
                // 直接返回。
                return;
            }
            // 标记任务进入暂停状态。
            job.status = JobStatus.PAUSED;
            // 状态文本说明暂停。
            job.statusText = "Paused";
            // 通知界面刷新。
            notifyChanged();
            // 在锁上等待，直到恢复或被取消。
            synchronized (pauseLock) {
                // 循环等待：退出条件为“已恢复”或“已取消”。
                while (paused && !shouldCancel()) {
                    // 等待最多 200ms 再检查一次，避免极端情况下的长时间挂起。
                    try {
                        // 等待唤醒。
                        pauseLock.wait(200L);
                    } catch (InterruptedException interrupted) {
                        // 线程被中断时恢复中断标志并视为取消，便于快速退出。
                        Thread.currentThread().interrupt();
                        // 抛出取消异常。
                        throw new ConversionException(ErrorReason.CANCELED, "Interrupted while paused");
                    }
                }
            }
            // 等待结束后再次判断取消。
            if (shouldCancel()) {
                // 抛出取消异常。
                throw new ConversionException(ErrorReason.CANCELED);
            }
            // 恢复为运行状态。
            job.status = JobStatus.RUNNING;
            // 状态文本恢复。
            job.statusText = "Resumed";
            // 通知界面刷新。
            notifyChanged();
        }
    }
}
