package com.xu.universaltranscoder.model;

// 引入 ArrayList：保存本次任务产出的全部文件路径。

import java.util.ArrayList;
// 引入 List：对外暴露产出列表。
import java.util.List;

// 引入 ErrorReason：失败原因分类。
import com.xu.universaltranscoder.core.ErrorReason;
// 引入 MediaOp：任务的媒体大类。
import com.xu.universaltranscoder.util.MediaOp;

/**
 * ConversionJob 是“一个待转换任务”的完整描述与运行期状态。
 * 它同时承担两个角色：
 * 1) 输入描述：源文件、目标格式、参数、输出位置；
 * 2) 运行状态：进度、速度、耗时、状态、错误原因、产出文件。
 * 把两者放一起，队列页只需要持有任务列表即可完成全部绘制。
 */
public final class ConversionJob {

    // 自增编号，用于队列展示与日志定位；由 TaskQueue 分配。
    public final long id;
    // 源文件 URI 字符串（content:// 形式）。
    public final String sourceUri;
    // 源文件显示名，用于界面与输出文件命名。
    public final String sourceName;
    // 目标规格，包含操作类型与容器/编码信息。
    public final TargetSpec target;
    // 本次任务的转换参数。
    public final ConvertOptions options;
    // 输出目录的 SAF 树 URI 字符串；为空表示使用应用专属输出目录。
    public final String outputTreeUri;
    // 去重键：由源 URI + 目标键 + 参数构成。
    public final String dedupeKey;
    // 本次任务产出的文件路径（可能多个：抽帧、PDF 逐页导出）。
    public final List<String> outputPaths = new ArrayList<>();
    // 当前状态，默认排队中。
    public volatile JobStatus status = JobStatus.PENDING;
    // 当前进度（0..1）。
    public volatile double progress = 0.0;
    // 处理速度（字节/秒），仅对重封装/重编码有意义；0 表示未知。
    public volatile double bytesPerSecond = 0.0;
    // 开始时间（毫秒时间戳），0 表示尚未开始。
    public volatile long startedAtMs = 0L;
    // 结束时间（毫秒时间戳），0 表示尚未结束。
    public volatile long finishedAtMs = 0L;
    // 面向用户的简短状态文本，例如 “Encoding 42%”。
    public volatile String statusText = "";
    // 失败原因分类，成功时为 null。
    public volatile ErrorReason errorReason;
    // 失败的技术细节文本，便于排查。
    public volatile String errorDetail = "";

    // 构造：输入描述一旦确定就不再变化，因此全部声明为 final。
    public ConversionJob(long id, String sourceUri, String sourceName, TargetSpec target,
                         ConvertOptions options, String outputTreeUri, String dedupeKey) {
        // 保存任务编号。
        this.id = id;
        // 保存源 URI。
        this.sourceUri = sourceUri;
        // 保存源文件名。
        this.sourceName = sourceName;
        // 保存目标规格。
        this.target = target;
        // 保存参数。
        this.options = options;
        // 保存输出目录。
        this.outputTreeUri = outputTreeUri;
        // 保存去重键。
        this.dedupeKey = dedupeKey;
    }

    /**
     * 计算已耗时（毫秒）：运行中算到当前时刻，结束后用结束时间。
     */
    public long elapsedMs() {
        // 尚未开始则耗时为 0。
        if (startedAtMs <= 0L) {
            // 返回 0。
            return 0L;
        }
        // 已结束则用结束时间减开始时间。
        if (finishedAtMs > 0L) {
            // 返回固定耗时。
            return finishedAtMs - startedAtMs;
        }
        // 运行中：用当前时间减开始时间。
        return System.currentTimeMillis() - startedAtMs;
    }
}
