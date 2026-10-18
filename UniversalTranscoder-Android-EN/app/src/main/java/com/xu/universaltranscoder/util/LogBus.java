package com.xu.universaltranscoder.util;

// 引入 Handler/Looper：把日志回调切回主线程，保证界面控件只在主线程被触碰。

import android.os.Handler;
import android.os.Looper;

// 引入 CopyOnWriteArrayList：监听器列表在遍历时可能被增删，使用并发安全容器最省心。
import java.util.concurrent.CopyOnWriteArrayList;
// 引入 ArrayDeque：充当固定容量的环形缓冲，保存最近的日志行。
import java.util.ArrayDeque;
// 引入 List：对外暴露监听器集合的只读视图。
import java.util.List;
// 引入 SimpleDateFormat/Date/Locale：生成时间戳。
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LogBus 是一个进程内的轻量日志总线。
 * 为什么需要它：转换工作线程不能直接更新界面，但界面又需要实时看到执行日志；
 * 因此用一个带缓冲与监听机制的总线：工作线程写入，总线切换到主线程后通知界面追加文本。
 */
public final class LogBus {

    // 日志级别：错误（始终输出）。
    public static final int LEVEL_ERROR = AppSettings.LOG_LEVEL_ERROR;
    // 日志级别：信息（默认级别）。
    public static final int LEVEL_INFO = AppSettings.LOG_LEVEL_INFO;
    // 日志级别：调试（最详细）。
    public static final int LEVEL_DEBUG = AppSettings.LOG_LEVEL_DEBUG;

    // 缓冲区最大行数：超出后丢弃最旧的日志，避免长时间运行占用内存。
    private static final int MAX_LINES = 600;
    // 环形缓冲，保存最近日志行。
    private static final ArrayDeque<String> BUFFER = new ArrayDeque<>();
    // 监听器集合，允许界面同时挂多个监听（例如转换页与队列页都关心日志）。
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    // 主线程 Handler，用于把回调切到主线程。
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    // 时间戳格式：精确到毫秒，便于对齐进度输出。
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    // 当前最小输出级别，由设置页控制。
    private static int minLevel = AppSettings.LOG_LEVEL_INFO;

    // 私有构造：工具类不允许实例化。
    private LogBus() {
        // 空实现，禁止外部 new。
    }

    /**
     * 设置最小输出级别（0=Error,1=Info,2=Debug）。
     */
    public static void setMinLevel(int level) {
        // 裁剪到合法范围后保存。
        minLevel = Math.max(AppSettings.LOG_LEVEL_ERROR, Math.min(AppSettings.LOG_LEVEL_DEBUG, level));
    }

    /**
     * 读取当前最小输出级别。
     */
    public static int minLevel() {
        // 返回当前级别。
        return minLevel;
    }

    /**
     * 写入一行错误级日志。
     */
    public static void error(String tag, String message) {
        // 转发到统一写入方法。
        write(LEVEL_ERROR, tag, message);
    }

    /**
     * 写入一行信息级日志。
     */
    public static void info(String tag, String message) {
        // 转发到统一写入方法。
        write(LEVEL_INFO, tag, message);
    }

    /**
     * 写入一行调试级日志。
     */
    public static void debug(String tag, String message) {
        // 转发到统一写入方法。
        write(LEVEL_DEBUG, tag, message);
    }

    /**
     * 统一写入入口：按级别裁剪后进入缓冲并通知监听器。
     */
    public static void write(int level, String tag, String message) {
        // 低于当前级别的日志直接丢弃，避免无谓开销。
        if (level > minLevel) {
            // 直接返回。
            return;
        }
        // 生成时间戳与级别前缀。
        String levelTag = level == LEVEL_ERROR ? "E" : (level == LEVEL_INFO ? "I" : "D");
        // 拼接完整日志行；synchronized 保护 SimpleDateFormat 的线程安全性。
        String line;
        // 进入同步块生成时间戳。
        synchronized (TIME_FORMAT) {
            // 形如 “12:03:44.512 I/Video: message”。
            line = TIME_FORMAT.format(new Date()) + " " + levelTag + "/" + tag + ": " + message;
        }
        // 加锁保护环形缓冲。
        synchronized (BUFFER) {
            // 超限时移除最旧一行。
            if (BUFFER.size() >= MAX_LINES) {
                // 丢弃队首元素。
                BUFFER.pollFirst();
            }
            // 追加新行。
            BUFFER.addLast(line);
        }
        // 通知所有监听器（切到主线程执行）。
        notifyListeners(line);
    }

    /**
     * 把一行日志投递到主线程并分发给监听器。
     */
    private static void notifyListeners(String line) {
        // 若当前已在主线程，直接分发，省一次消息投递。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 直接遍历分发。
            for (Listener listener : LISTENERS) {
                // 单个监听器异常不应影响其它监听器。
                try {
                    // 回调监听器。
                    listener.onLogLine(line);
                } catch (Throwable ignored) {
                    // 监听器自身问题，忽略即可。
                }
            }
            // 分发完成。
            return;
        }
        // 工作线程场景：通过 Handler 切到主线程。
        MAIN_HANDLER.post(new Runnable() {
            // 匿名任务：在主线程执行分发。
            @Override
            public void run() {
                // 遍历分发。
                for (Listener listener : LISTENERS) {
                    // 保护单个监听器异常。
                    try {
                        // 回调监听器。
                        listener.onLogLine(line);
                    } catch (Throwable ignored) {
                        // 忽略监听器异常，保证日志系统本身稳定。
                    }
                }
            }
        });
    }

    /**
     * 注册监听器；注册后应立刻把已有缓冲回放给监听器，避免界面空白。
     */
    public static void addListener(Listener listener) {
        // 空监听器不注册。
        if (listener == null) {
            // 直接返回。
            return;
        }
        // 加入集合。
        LISTENERS.add(listener);
        // 复制一份缓冲快照，避免在遍历时持有锁。
        String snapshot = dump();
        // 快照非空时才回放。
        if (!snapshot.isEmpty()) {
            // 回放历史日志，让新注册的界面立刻看到上下文。
            listener.onLogLine(snapshot);
        }
    }

    /**
     * 移除监听器，防止界面销毁后仍被回调。
     */
    public static void removeListener(Listener listener) {
        // 移除指定监听器。
        LISTENERS.remove(listener);
    }

    /**
     * 导出当前缓冲的全部内容（按时间顺序拼接）。
     */
    public static String dump() {
        // 使用 StringBuilder 拼接。
        StringBuilder builder = new StringBuilder();
        // 加锁读取缓冲。
        synchronized (BUFFER) {
            // 逐行拼接。
            for (String line : BUFFER) {
                // 追加一行并换行。
                builder.append(line).append('\n');
            }
        }
        // 返回拼接结果。
        return builder.toString();
    }

    /**
     * 清空日志缓冲（界面上的“Clear log”按钮会调用它）。
     */
    public static void clear() {
        // 加锁清空。
        synchronized (BUFFER) {
            // 移除全部行。
            BUFFER.clear();
        }
    }

    /**
     * 日志监听接口：收到的是已经格式化好的整行文本。
     */
    public interface Listener {
        // 在主线程序列中回调。
        void onLogLine(String line);
    }
}
