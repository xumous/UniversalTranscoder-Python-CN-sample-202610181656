package com.xu.universaltranscoder.ui;

// 引入视图类型。

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

// 引入注解。
import androidx.annotation.NonNull;
// 引入 RecyclerView 适配器基类。
import androidx.recyclerview.widget.RecyclerView;

// 引入应用资源与模型。
import com.xu.universaltranscoder.R;
import com.xu.universaltranscoder.model.ConversionJob;
import com.xu.universaltranscoder.model.JobStatus;
// 引入格式化工具。
import com.xu.universaltranscoder.util.FormatUtil;

// 引入列表与集合。
import java.util.ArrayList;
import java.util.List;

/**
 * QueueAdapter 负责把任务列表画到 RecyclerView 上。
 * 每行展示：文件名、目标格式与状态、进度条、百分比，以及速度/耗时。
 * 状态颜色按语义区分（运行中蓝色、成功绿色、失败红色、其它灰色），让用户一眼看出哪些任务需要关注。
 */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.JobHolder> {

    // 当前数据源；使用副本避免外部修改影响列表绘制。
    private final List<ConversionJob> jobs = new ArrayList<>();

    /**
     * 用最新任务列表替换数据源。
     */
    public void submit(List<ConversionJob> latest) {
        // 清空旧数据。
        jobs.clear();
        // 写入新数据。
        jobs.addAll(latest);
        // 通知整表刷新（任务数量有限，整表刷新开销可控且逻辑最简单）。
        notifyDataSetChanged();
    }

    // 创建条目视图。
    @NonNull
    @Override
    public JobHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载条目布局。
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue_task, parent, false);
        // 包装为 ViewHolder。
        return new JobHolder(view);
    }

    // 绑定数据到条目。
    @Override
    public void onBindViewHolder(@NonNull JobHolder holder, int position) {
        // 取任务。
        ConversionJob job = jobs.get(position);
        // 写入文件名。
        holder.title.setText(job.sourceName);
        // 写入目标格式与状态文本。
        holder.subtitle.setText(job.target.label + " · " + (job.statusText == null || job.statusText.isEmpty()
                ? job.status.name() : job.statusText));
        // 写入进度。
        holder.progressBar.setProgress((int) Math.round(job.progress * 100.0));
        // 写入百分比文本。
        holder.progressText.setText(FormatUtil.formatPercent(job.progress));
        // 写入速度与耗时。
        holder.perfText.setText(FormatUtil.formatSpeed(job.bytesPerSecond) + " · " + FormatUtil.formatDuration(job.elapsedMs()));
        // 根据状态设置颜色。
        holder.title.setTextColor(statusColor(holder, job.status));
    }

    // 返回条目数量。
    @Override
    public int getItemCount() {
        // 返回数据条数。
        return jobs.size();
    }

    /**
     * 取得状态对应的颜色值，使用主题中的语义色资源。
     */
    private int statusColor(JobHolder holder, JobStatus status) {
        // 取上下文以便读取颜色资源。
        android.content.Context context = holder.itemView.getContext();
        // 按状态选择颜色。
        switch (status) {
            // 运行中显示蓝色。
            case RUNNING:
                // 返回运行中颜色。
                return context.getColor(R.color.status_running);
            // 完成显示绿色。
            case DONE:
                // 返回成功颜色。
                return context.getColor(R.color.status_success);
            // 失败显示红色。
            case FAILED:
                // 返回失败颜色。
                return context.getColor(R.color.status_error);
            // 暂停显示橙色。
            case PAUSED:
                // 返回警告颜色。
                return context.getColor(R.color.status_warning);
            // 其它状态使用主要文字色。
            default:
                // 返回默认文字色。
                return context.getColor(R.color.text_primary);
        }
    }

    /**
     * 条目 ViewHolder：缓存各控件引用，避免重复查找。
     */
    static final class JobHolder extends RecyclerView.ViewHolder {

        // 文件名文本。
        final TextView title;
        // 目标格式与状态文本。
        final TextView subtitle;
        // 进度条。
        final ProgressBar progressBar;
        // 百分比文本。
        final TextView progressText;
        // 速度与耗时文本。
        final TextView perfText;

        // 构造：绑定控件。
        JobHolder(@NonNull View itemView) {
            // 调用父类构造。
            super(itemView);
            // 绑定文件名文本。
            title = itemView.findViewById(R.id.txt_item_title);
            // 绑定副标题文本。
            subtitle = itemView.findViewById(R.id.txt_item_subtitle);
            // 绑定进度条。
            progressBar = itemView.findViewById(R.id.pb_item);
            // 绑定百分比文本。
            progressText = itemView.findViewById(R.id.txt_item_progress);
            // 绑定速度耗时文本。
            perfText = itemView.findViewById(R.id.txt_item_perf);
        }
    }
}
