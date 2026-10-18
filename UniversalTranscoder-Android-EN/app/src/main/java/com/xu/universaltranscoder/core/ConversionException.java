package com.xu.universaltranscoder.core;

/**
 * ConversionException 是转换引擎统一的受检异常。
 * 它把“分类结果（ErrorReason）”与“原始技术细节（detail）”打包在一起：
 * - reason 用于界面展示，用户能看懂；
 * - detail 用于日志，保留 MediaCodec/MediaMuxer 的真实报错文本，便于排查。
 */
public class ConversionException extends Exception {

    // 序列化标识：虽然本应用不序列化该异常，但继承 Exception 后仍给出固定值，消除编译告警。
    private static final long serialVersionUID = 20261017L;

    // 分类结果，决定界面提示与建议。
    private final ErrorReason reason;
    // 原始技术细节，可为空字符串。
    private final String detail;

    /**
     * 构造：只给出分类，细节留空。
     */
    public ConversionException(ErrorReason reason) {
        // 调用带细节的构造，细节使用空字符串。
        this(reason, "");
    }

    /**
     * 构造：给出分类与细节。
     */
    public ConversionException(ErrorReason reason, String detail) {
        // 调用父类构造，异常消息统一使用分类的一行文本，便于打印堆栈时也能读懂。
        super(reason.oneLine() + (detail == null || detail.isEmpty() ? "" : " | detail: " + detail));
        // 保存分类结果。
        this.reason = reason;
        // 保存技术细节，null 归一化为空字符串。
        this.detail = detail == null ? "" : detail;
    }

    /**
     * 构造：把底层异常包装成分类异常，detail 自动取底层异常消息。
     */
    public ConversionException(ErrorReason reason, Throwable cause) {
        // 调用父类构造并保留原因链，便于打印完整堆栈。
        super(reason.oneLine() + (cause == null || cause.getMessage() == null ? "" : " | detail: " + cause.getMessage()), cause);
        // 保存分类结果。
        this.reason = reason;
        // 保存底层异常消息作为细节。
        this.detail = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
    }

    /**
     * 返回分类结果。
     */
    public ErrorReason reason() {
        // 直接返回字段。
        return reason;
    }

    /**
     * 返回原始技术细节。
     */
    public String detail() {
        // 直接返回字段。
        return detail;
    }
}
