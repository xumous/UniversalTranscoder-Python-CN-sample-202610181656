package com.xu.universaltranscoder.ui;

// 引入视图类型。

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// 引入 Fragment 与注解。
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
// 引入 RecyclerView 及其布局管理器。
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// 引入 Material 按钮。
import com.google.android.material.button.MaterialButton;

// 引入应用资源与模型。
import com.xu.universaltranscoder.R;
import com.xu.universaltranscoder.model.ConversionJob;
import com.xu.universaltranscoder.model.JobStatus;
// 引入任务队列。
import com.xu.universaltranscoder.task.TaskQueue;

// 引入列表类型。
import java.util.List;

/**
 * QueueFragment 是任务队列页。
 * 它提供五个控制按钮（Start / Pause / Resume / Stop / Clear）与一个任务列表，
 * 并通过 TaskQueue 的监听器做到“任务状态一变，界面即刷新”。
 */
public class QueueFragment extends Fragment {

    // 适配器实例。
    private final QueueAdapter adapter = new QueueAdapter();
    // 汇总文本。
    private TextView txtSummary;
    // 空状态文本。
    private TextView txtEmpty;
    // 队列监听器：收到通知后刷新列表与汇总。
    private final TaskQueue.Listener queueListener = this::refresh;

    // 生命周期回调：创建视图。
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载队列页布局。
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    // 生命周期回调：绑定控件与事件。
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 调用父类实现。
        super.onViewCreated(view, savedInstanceState);
        // 绑定汇总文本。
        txtSummary = view.findViewById(R.id.txt_queue_summary);
        // 绑定空状态文本。
        txtEmpty = view.findViewById(R.id.txt_queue_empty);
        // 绑定列表。
        RecyclerView recyclerView = view.findViewById(R.id.rv_queue);
        // 使用纵向线性布局。
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // 安装适配器。
        recyclerView.setAdapter(adapter);
        // 绑定启动按钮。
        MaterialButton btnStart = view.findViewById(R.id.btn_queue_start);
        // 启动调度。
        btnStart.setOnClickListener(v -> TaskQueue.get(requireContext()).start());
        // 绑定暂停按钮。
        MaterialButton btnPause = view.findViewById(R.id.btn_queue_pause);
        // 暂停调度。
        btnPause.setOnClickListener(v -> TaskQueue.get(requireContext()).pause());
        // 绑定继续按钮。
        MaterialButton btnResume = view.findViewById(R.id.btn_queue_resume);
        // 恢复调度。
        btnResume.setOnClickListener(v -> TaskQueue.get(requireContext()).resume());
        // 绑定停止按钮。
        MaterialButton btnStop = view.findViewById(R.id.btn_queue_stop);
        // 停止：取消所有未完成任务。
        btnStop.setOnClickListener(v -> TaskQueue.get(requireContext()).stop());
        // 绑定清空按钮。
        MaterialButton btnClear = view.findViewById(R.id.btn_queue_clear);
        // 清空：移除已结束的任务条目。
        btnClear.setOnClickListener(v -> TaskQueue.get(requireContext()).clearFinished());
        // 首次进入即刷新一次。
        refresh();
    }

    // 生命周期回调：页面可见时注册监听。
    @Override
    public void onStart() {
        // 调用父类实现。
        super.onStart();
        // 注册队列监听。
        TaskQueue.get(requireContext()).addListener(queueListener);
        // 立即刷新。
        refresh();
    }

    // 生命周期回调：页面不可见时注销监听。
    @Override
    public void onStop() {
        // 注销监听。
        TaskQueue.get(requireContext()).removeListener(queueListener);
        // 调用父类实现。
        super.onStop();
    }

    /**
     * 依据队列最新状态刷新列表与汇总文本。
     */
    private void refresh() {
        // 视图未就绪时忽略。
        if (txtSummary == null || getContext() == null) {
            // 直接返回。
            return;
        }
        // 取队列快照。
        List<ConversionJob> jobs = TaskQueue.get(requireContext()).snapshot();
        // 更新适配器数据。
        adapter.submit(jobs);
        // 队列为空时展示空状态并隐藏汇总统计。
        if (jobs.isEmpty()) {
            // 显示空状态文本。
            txtEmpty.setVisibility(View.VISIBLE);
            // 汇总文本置为通用提示。
            txtSummary.setText(getString(R.string.queue_empty));
            // 结束处理。
            return;
        }
        // 有任务时隐藏空状态。
        txtEmpty.setVisibility(View.GONE);
        // 统计各状态数量。
        int done = 0;
        // 失败数量。
        int failed = 0;
        // 待运行数量（排队 + 运行 + 暂停）。
        int pending = 0;
        // 遍历任务。
        for (ConversionJob job : jobs) {
            // 完成计数。
            if (job.status == JobStatus.DONE) {
                // 递增。
                done++;
            }
            // 失败计数。
            if (job.status == JobStatus.FAILED) {
                // 递增。
                failed++;
            }
            // 待运行计数（未进入终态的任务）。
            if (!job.status.isTerminal()) {
                // 递增。
                pending++;
            }
        }
        // 写入汇总文本。
        txtSummary.setText(getString(R.string.queue_summary, jobs.size(), done, failed, pending));
    }
}
