package com.xu.universaltranscoder.ui;

// 引入 Job，作为应用外壳暴露的任务类型

import com.xu.universaltranscoder.core.Job;
// 引入 Toolchain，作为应用外壳暴露的内核对象类型
import com.xu.universaltranscoder.core.Toolchain;
// 引入 List 接口，作为批量入队的参数类型
import java.util.List;
// 引入 Consumer，作为任务变化监听器的函数式类型
import java.util.function.Consumer;

// 关键字 interface：AppShell 定义主窗口向各功能面板暴露的能力
// 之所以引入这层接口：面板只需依赖一个窄接口，不必知道主窗口内部如何组织队列与日志，降低耦合
public interface AppShell {

    // 入队单个任务
    void enqueue(Job job);

    // 批量入队任务，返回成功入队数量
    int enqueueBatch(List<Job> jobs);

    // 输出一行日志到当前可见页面的日志区域
    void log(String line);

    // 获取应用设置对象
    AppSettings settings();

    // 获取内核对象
    Toolchain toolchain();

    // 注册任务变化监听器，回调保证在事件分发线程上执行
    void registerJobListener(Consumer<Job> listener);

    // 刷新队列页的表格内容
    void refreshQueue();
}
