package com.xu.universaltranscoder.core;

/**
 * ProgressCallback 是转换器与调度器之间的回调契约。
 * 设计动机：所有转换器都在工作线程里跑长循环，它们需要三件事——
 * 1) 上报进度给界面；
 * 2) 询问“用户是否取消”，从而尽快退出循环；
 * 3) 在用户暂停时阻塞，恢复后继续。
 * 把这三件事抽成接口，可以让每个转换器实现保持极简，也让调度器集中管理暂停/取消语义。
 */
public interface ProgressCallback {

    /**
     * 上报进度。progress 取值 0..1；bytesPerSecond 为吞吐速度，未知时传 0；statusText 为面向用户的状态文本。
     */
    void reportProgress(double progress, double bytesPerSecond, String statusText);

    /**
     * 是否应当取消：返回 true 时转换器应在安全点结束并抛出 CANCELED。
     */
    boolean shouldCancel();

    /**
     * 检查暂停状态：若队列被暂停则阻塞直到恢复；若期间被取消则抛出 CANCELED。
     * 之所以让转换器在循环里主动调用，是为了让暂停能精确落在“一个样本处理完”的安全点上。
     */
    void checkPaused() throws ConversionException;

    /**
     * 便捷方法：把原始字节数与总字节数换算为进度并上报。
     */
    default void reportBytes(long processedBytes, long totalBytes, String statusText) {
        // 总字节数为 0 时无法计算百分比，进度按 0 上报。
        double progress = totalBytes <= 0L ? 0.0 : Math.min(1.0, (double) processedBytes / (double) totalBytes);
        // 上报名义吞吐速度统一按 0 处理，真实速度由调度器按时间差计算，更准确。
        reportProgress(progress, 0.0, statusText);
    }
}
