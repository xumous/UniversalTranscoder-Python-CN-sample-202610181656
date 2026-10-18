package com.xu.universaltranscoder.model;

/**
 * JobStatus 是任务状态枚举。
 * 之所以单独成枚举：队列界面、任务去重、调度器都需要用同一套状态语义判断“这个任务该不该被执行”。
 */
public enum JobStatus {
    // 排队中：已加入队列但尚未开始。
    PENDING,
    // 运行中：转换器正在处理该任务。
    RUNNING,
    // 已暂停：用户暂停了队列，当前任务被挂起（保留进度）。
    PAUSED,
    // 已完成：转换成功并已产出文件。
    DONE,
    // 已失败：转换失败，失败原因记录在任务对象上。
    FAILED,
    // 已取消：用户主动停止，任务被终止。
    CANCELED;

    /**
     * 判断是否为“终态”，终态任务不会再次被调度执行。
     */
    public boolean isTerminal() {
        // 完成、失败、取消都是终态。
        return this == DONE || this == FAILED || this == CANCELED;
    }
}
