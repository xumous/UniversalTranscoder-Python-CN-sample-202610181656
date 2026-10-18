package com.xu.universaltranscoder.core;

// 引入 ArrayList，用于保存任务列表与生成快照

import java.util.ArrayList;
// 引入 List 接口，作为任务列表与快照的类型
import java.util.List;
// 引入 HashSet，用于维护去重键集合
import java.util.HashSet;
// 引入 Set 接口，作为去重键集合的类型
import java.util.Set;
// 引入 ExecutorService，提供工作线程池
import java.util.concurrent.ExecutorService;
// 引入 Executors，用于创建固定大小的线程池
import java.util.concurrent.Executors;
// 引入 TimeUnit，为线程池关闭提供时间单位
import java.util.concurrent.TimeUnit;

// 关键字 class：TaskQueue 实现不依赖界面的任务队列，支持并发上限、暂停、继续、停止与去重
// 之所以把队列与界面彻底解耦：队列需在后台线程运行，界面只订阅回调，避免任何 Swing 组件被后台线程直接触碰
public final class TaskQueue {

    // 任务列表，保存全部任务并维持插入顺序，界面按该顺序展示
    private final List<Job> jobs = new ArrayList<>();
    // 去重键集合，避免同一输入与同一目标被重复入队
    private final Set<String> dedupeKeys = new HashSet<>();
    // 回调监听器，可为空表示不关心通知
    private final Listener listener;
    // 队列锁对象，所有对任务列表与计数的修改都必须持有它
    private final Object lock = new Object();
    // 并发上限，决定同时运行的任务数量
    private int concurrency;
    // 是否处于暂停状态，暂停时调度器不再派发新任务但已运行任务会继续完成
    private volatile boolean paused;
    // 是否处于停止状态，停止后不再接受派发
    private volatile boolean stopped;
    // 是否已经启动过调度流程
    private volatile boolean started;
    // 当前正在运行的任务数量，用于控制并发上限
    private int activeCount;
    // 工作线程池，大小等于并发上限
    private ExecutorService executor;
    // 调度线程，负责挑选等待中的任务并提交给线程池
    private Thread dispatcher;

    // 构造方法：接收并发上限与回调监听器
    public TaskQueue(int concurrency, Listener listener) {
        // 并发上限至少为 1，否则队列永远不会派发任务
        this.concurrency = Math.max(concurrency, 1);
        // 保存回调监听器
        this.listener = listener;
    }

    // 设置并发上限，下一次启动时生效，避免运行中动态调整线程池带来的复杂性
    public void setConcurrency(int value) {
        // 同步修改，保证读写可见性
        synchronized (lock) {
            // 并发上限至少为 1
            this.concurrency = Math.max(value, 1);
        }
    }

    // 获取当前并发上限
    public int concurrency() {
        // 读取并发上限
        synchronized (lock) {
            // 返回并发上限
            return concurrency;
        }
    }

    // 入队一个任务，返回是否成功入队
    public boolean enqueue(Job job) {
        // 任务为空时视为入队失败
        if (job == null) {
            // 返回 false 表示未入队
            return false;
        }
        // 同步修改任务列表与去重集合
        synchronized (lock) {
            // 去重键已存在时说明该输入与目标组合已经排队，直接跳过
            if (dedupeKeys.contains(job.dedupeKey())) {
                // 标记任务为跳过状态，界面可以展示去重结果
                job.status(Job.Status.SKIPPED);
                // 更新描述文本
                job.message("重复任务已跳过");
                // 返回 false 表示未真正入队
                return false;
            }
            // 登记去重键
            dedupeKeys.add(job.dedupeKey());
            // 把任务加入列表
            jobs.add(job);
        }
        // 通知界面队列结构发生变化
        notifyQueueChanged();
        // 通知界面该任务已入队
        notifyJobChanged(job);
        // 返回 true 表示入队成功
        return true;
    }

    // 批量入队，返回成功入队的数量
    public int enqueueAll(List<Job> batch) {
        // 计数器从 0 开始
        int accepted = 0;
        // 逐个尝试入队，复用单任务入队的去重逻辑
        for (Job job : batch) {
            // 入队成功时计数加一
            if (enqueue(job)) {
                // 累加计数
                accepted++;
            }
        }
        // 返回成功入队数量
        return accepted;
    }

    // 启动队列：创建线程池与调度线程并开始派发任务
    public void start() {
        // 同步检查与修改启动状态
        synchronized (lock) {
            // 已经启动过时不再重复启动，避免出现两套调度线程
            if (started) {
                // 已启动则直接返回
                return;
            }
            // 标记为已启动
            started = true;
            // 清除停止标记，允许继续运行
            stopped = false;
            // 清除暂停标记，启动后立即开始派发
            paused = false;
            // 按并发上限创建固定大小线程池，线程池名称便于在调试时识别
            executor = Executors.newFixedThreadPool(concurrency, runnable -> {
                // 创建线程对象
                Thread thread = new Thread(runnable, "transcoder-worker");
                // 设为守护线程，保证程序退出时不会被工作线程阻塞
                thread.setDaemon(true);
                // 返回配置好的线程
                return thread;
            });
        }
        // 创建调度线程，负责持续挑选等待任务
        dispatcher = new Thread(this::dispatchLoop, "transcoder-dispatcher");
        // 设为守护线程
        dispatcher.setDaemon(true);
        // 启动调度线程
        dispatcher.start();
        // 通知界面队列已开始运行
        notifyQueueChanged();
        // 输出一行启动日志
        log("队列已启动，并发上限 " + concurrency() + "。");
    }

    // 暂停队列：调度器停止派发新任务，已在运行的任务继续完成
    public void pause() {
        // 写入暂停标记
        paused = true;
        // 通知界面队列状态变化
        notifyQueueChanged();
        // 输出一行暂停日志
        log("队列已暂停。");
    }

    // 继续队列：恢复派发等待中的任务
    public void resume() {
        // 清除暂停标记
        paused = false;
        // 通知界面队列状态变化
        notifyQueueChanged();
        // 输出一行继续日志
        log("队列已继续。");
    }

    // 停止队列：取消全部未完成的任务并关闭线程池
    public void stop() {
        // 声明需要取消的任务快照，避免持锁期间执行回调
        List<Job> toCancel = new ArrayList<>();
        // 同步修改状态并收集需要取消的任务
        synchronized (lock) {
            // 写入停止标记，调度器将退出循环
            stopped = true;
            // 清除暂停标记，保证调度器能及时退出而不是被暂停卡住
            paused = false;
            // 遍历全部任务，收集未完成的任务
            for (Job job : jobs) {
                // 终态任务无需取消
                if (!job.isTerminal()) {
                    // 加入待取消列表
                    toCancel.add(job);
                }
            }
        }
        // 逐个置位取消信号，运行中的任务会据此终止外部进程
        for (Job job : toCancel) {
            // 置位取消信号
            job.cancel();
            // 对仍在等待的任务直接标记为取消状态，避免它们永远停在等待状态
            if (job.status() == Job.Status.WAITING || job.status() == Job.Status.SKIPPED) {
                // 标记为取消
                job.status(Job.Status.CANCELLED);
                // 更新描述文本
                job.message("开始前已取消");
                // 通知界面该任务状态变化
                notifyJobChanged(job);
            }
        }
        // 关闭线程池，允许正在运行的任务完成收尾
        shutdownExecutor();
        // 标记为未启动，允许后续重新启动队列
        synchronized (lock) {
            // 复位启动标记
            started = false;
        }
        // 通知界面队列状态变化
        notifyQueueChanged();
        // 输出一行停止日志
        log("队列已停止。");
    }

    // 关闭线程池并等待最多若干秒
    private void shutdownExecutor() {
        // 取出线程池引用并清空字段，防止重复关闭
        ExecutorService current = executor;
        // 引用为空时无需关闭
        if (current == null) {
            // 直接返回
            return;
        }
        // 请求关闭，已提交任务会继续执行
        current.shutdown();
        // 捕获等待中断异常
        try {
            // 最多等待五秒，保证运行中的任务有时间响应取消信号
            if (!current.awaitTermination(5, TimeUnit.SECONDS)) {
                // 超时后强制关闭，避免线程泄漏
                current.shutdownNow();
            }
        } catch (InterruptedException ex) {
            // 恢复中断标记并强制关闭
            Thread.currentThread().interrupt();
            // 强制关闭线程池
            current.shutdownNow();
        }
        // 清空线程池字段
        executor = null;
    }

    // 清空已完成的任务，保留等待与运行中的任务
    public void clearFinished() {
        // 同步修改任务列表与去重集合
        synchronized (lock) {
            // 使用迭代器安全的移除方式，避免并发修改异常
            jobs.removeIf(job -> {
                // 终态任务才允许移除
                boolean removable = job.isTerminal();
                // 移除时同时清理去重键，允许用户稍后重新入队同一任务
                if (removable) {
                    // 从去重集合中移除对应键
                    dedupeKeys.remove(job.dedupeKey());
                }
                // 返回是否移除该任务
                return removable;
            });
        }
        // 通知界面队列结构变化
        notifyQueueChanged();
        // 输出一行清理日志
        log("已清理完成任务。");
    }

    // 清空全部任务，正在运行的任务会被取消
    public void clearAll() {
        // 先停止队列以取消运行中的任务
        stop();
        // 同步清空任务列表与去重集合
        synchronized (lock) {
            // 清空任务列表
            jobs.clear();
            // 清空去重集合
            dedupeKeys.clear();
        }
        // 通知界面队列结构变化
        notifyQueueChanged();
        // 输出一行清空日志
        log("已清空全部任务。");
    }

    // 移除指定任务，等待中的任务直接移除，运行中的任务会被取消
    public boolean removeJob(Job job) {
        // 任务为空时移除失败
        if (job == null) {
            // 返回 false 表示未移除
            return false;
        }
        // 同步修改任务列表与去重集合
        synchronized (lock) {
            // 从列表中移除该任务，未找到时返回 false
            if (!jobs.remove(job)) {
                // 返回 false 表示未移除
                return false;
            }
            // 清理去重键，允许后续重新入队
            dedupeKeys.remove(job.dedupeKey());
        }
        // 置位取消信号，运行中的任务会据此终止外部进程
        job.cancel();
        // 更新描述文本并标记取消状态，保证界面立即反映变化
        if (job.status() == Job.Status.WAITING) {
            // 标记为取消
            job.status(Job.Status.CANCELLED);
            // 更新描述文本
            job.message("已从队列移除");
        }
        // 通知界面该任务状态变化
        notifyJobChanged(job);
        // 通知界面队列结构变化
        notifyQueueChanged();
        // 返回 true 表示已移除
        return true;
    }

    // 获取任务列表快照，返回新列表避免外部修改内部结构
    public List<Job> snapshot() {
        // 同步复制任务列表
        synchronized (lock) {
            // 返回新列表副本
            return new ArrayList<>(jobs);
        }
    }

    // 判断队列是否正在运行
    public boolean isRunning() {
        // 返回启动标记与停止标记的组合结果
        return started && !stopped;
    }

    // 判断队列是否处于暂停状态
    public boolean isPaused() {
        // 返回暂停标记
        return paused;
    }

    // 调度循环：持续挑选等待中的任务并提交给线程池
    private void dispatchLoop() {
        // 只要没有停止就持续运行
        while (!stopped) {
            // 暂停期间不派发任务，但保持循环存活以便继续后立即恢复
            if (paused) {
                // 短暂休眠，避免空转消耗 CPU
                sleepQuietly(150L);
                // 本轮不派发任何任务
                continue;
            }
            // 取出下一个可派发的任务，无任务时返回空
            Job next = takeNextWaitingJob();
            // 没有可派发任务时短暂休眠后重试
            if (next == null) {
                // 短暂休眠，避免忙等待
                sleepQuietly(120L);
                // 进入下一轮循环
                continue;
            }
            // 取出当前线程池引用，可能在停止过程中已被清空
            ExecutorService current = executor;
            // 线程池已被关闭时退出调度循环
            if (current == null) {
                // 结束调度循环
                return;
            }
            // 提交任务到线程池执行
            current.submit(() -> runJob(next));
        }
    }

    // 在锁内挑选一个等待中的任务，同时检查并发上限
    private Job takeNextWaitingJob() {
        // 同步访问任务列表与运行计数
        synchronized (lock) {
            // 运行中的任务已达到并发上限时不再派发
            if (activeCount >= concurrency) {
                // 返回空表示暂时没有派发额度
                return null;
            }
            // 按插入顺序查找第一个等待中的任务
            for (Job job : jobs) {
                // 命中等待状态的任务即开始执行
                if (job.status() == Job.Status.WAITING) {
                    // 立即改为运行状态，防止被重复派发
                    job.status(Job.Status.RUNNING);
                    // 更新描述文本
                    job.message("转换中");
                    // 运行计数加一
                    activeCount++;
                    // 返回该任务
                    return job;
                }
            }
            // 没有等待中的任务时返回空
            return null;
        }
    }

    // 在工作线程中执行单个任务，并在结束后更新状态与计数
    private void runJob(Job job) {
        // 通知界面任务已进入运行状态
        notifyJobChanged(job);
        // 输出一行任务开始日志
        log("开始：" + job.inputName() + " → " + job.targetLabel());
        // 构建任务执行上下文，把进度、日志与取消查询接驳到任务对象与回调
        Job.Context context = new Job.Context() {
            // 上报进度
            @Override
            public void progress(double percent, double speed, double remainingSeconds) {
                // 写入进度百分比
                job.percent(percent);
                // 写入速度倍率
                job.speed(speed);
                // 写入剩余秒数
                job.remainingSeconds(remainingSeconds);
                // 通知界面刷新该行
                notifyJobChanged(job);
            }

            // 输出日志
            @Override
            public void log(String line) {
                // 转交给队列日志回调
                TaskQueue.this.log(line);
            }

            // 查询取消状态
            @Override
            public boolean isCancelled() {
                // 返回任务自身的取消信号
                return job.isCancelled();
            }
        };
        // 声明执行异常引用，便于在 catch 块中读取
        Exception failure = null;
        // 尝试执行任务逻辑
        try {
            // 调用任务实现，具体转码或文档处理逻辑在实现类中
            job.task().execute(job, context);
        } catch (Exception ex) {
            // 保存异常，稍后统一处理状态与描述
            failure = ex;
        }
        // 同步更新最终状态与运行计数
        synchronized (lock) {
            // 运行计数减一，释放并发额度
            activeCount--;
            // 任务已被取消时状态保持取消，不覆盖为失败
            if (job.isCancelled()) {
                // 标记为取消
                job.status(Job.Status.CANCELLED);
                // 更新描述文本
                job.message("已取消");
            } else if (failure != null) {
                // 标记为失败
                job.status(Job.Status.FAILED);
                // 更新描述文本，失败原因由任务实现写入
                job.message("失败");
            } else if (job.status() == Job.Status.RUNNING) {
                // 任务实现未显式设置状态时按完成处理
                job.status(Job.Status.COMPLETED);
                // 仅当描述仍停留在派发时的默认文本时，才写入通用完成描述，避免覆盖任务自定义结果说明
                if ("转换中".equals(job.message())) {
                    // 更新为通用完成描述
                    job.message("完成");
                }
            }
        }
        // 输出一行任务结束日志，附带状态与体积
        log("结束：" + job.inputName() + " | " + job.status().displayName() + " | " + job.message());
        // 通知界面任务状态变化
        notifyJobChanged(job);
        // 通知界面队列结构变化，便于刷新统计信息
        notifyQueueChanged();
    }

    // 静默休眠指定毫秒，忽略中断异常并恢复中断标记
    private void sleepQuietly(long millis) {
        // 尝试休眠
        try {
            // 休眠指定时长
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            // 恢复中断标记，遵循中断处理约定
            Thread.currentThread().interrupt();
        }
    }

    // 通知界面某个任务发生变化
    private void notifyJobChanged(Job job) {
        // 监听器存在时才回调，避免空指针
        if (listener != null) {
            // 回调任务变化事件
            listener.onJobChanged(job);
        }
    }

    // 通知界面队列结构发生变化
    private void notifyQueueChanged() {
        // 监听器存在时才回调
        if (listener != null) {
            // 回调队列变化事件
            listener.onQueueChanged();
        }
    }

    // 输出一行队列级日志
    private void log(String line) {
        // 监听器存在时才回调
        if (listener != null) {
            // 回调日志事件
            listener.onLog(line);
        }
    }

    // 队列回调接口，所有回调都在工作线程或调度线程上触发，界面实现必须自行切回事件分发线程
    public interface Listener {
        // 某个任务的状态或进度发生变化时调用
        void onJobChanged(Job job);

        // 队列整体结构发生变化（入队、清空、开始、停止）时调用
        void onQueueChanged();

        // 产生一行日志时调用
        void onLog(String line);
    }
}
