package com.xu.universaltranscoder.ui;

// 引入 Android 基础类型。

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

// 引入 Activity Result API：以类型安全的方式请求系统文件选择器。
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
// 引入 Fragment 与注解。
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// 引入 Material 按钮。
import com.google.android.material.button.MaterialButton;

// 引入应用类。
import com.xu.universaltranscoder.MainActivity;
import com.xu.universaltranscoder.R;
// 引入核心与模型类。
import com.xu.universaltranscoder.core.MediaProbe;
import com.xu.universaltranscoder.model.ConvertOptions;
import com.xu.universaltranscoder.model.ConversionJob;
import com.xu.universaltranscoder.model.JobStatus;
import com.xu.universaltranscoder.model.MediaInfo;
import com.xu.universaltranscoder.model.Preset;
import com.xu.universaltranscoder.model.PresetRepository;
import com.xu.universaltranscoder.model.TargetSpec;
// 引入任务队列与日志。
import com.xu.universaltranscoder.task.TaskQueue;
import com.xu.universaltranscoder.util.AppSettings;
import com.xu.universaltranscoder.util.FormatUtil;
import com.xu.universaltranscoder.util.LogBus;
import com.xu.universaltranscoder.util.MediaOp;

// 引入集合类型。
import java.util.ArrayList;
import java.util.List;

/**
 * BaseConvertFragment 抽象了四个转换页（视频/音频/图片/文档）共用的行为：
 * 选择源文件（支持多选）、选择目标格式、选择输出目录、套用预设、展示媒体信息、
 * 一键入队并启动调度、展示进度与执行日志。
 * <p>
 * 之所以抽出基类：四个页面的“操作路径”完全一致，只有可选的参数项不同；
 * 把公共逻辑集中在这里，子类只需声明“我属于哪一类、可选哪些目标、参数如何读写”，代码量与出错面都大幅减少。
 */
public abstract class BaseConvertFragment extends Fragment {

    // 已选择的源文件列表。
    protected final List<Uri> selectedUris = new ArrayList<>();
    // 当前的输出目录树 URI；空字符串表示使用应用专属外部目录。
    protected String outputTreeUri = "";

    // 目标格式下拉框。
    protected Spinner spinnerTarget;
    // 预设下拉框。
    protected Spinner spinnerPreset;
    // 已选文件展示文本。
    protected TextView txtSelectedFiles;
    // 不受支持目标的提示文本。
    protected TextView txtTargetHint;
    // 输出目录展示文本。
    protected TextView txtOutputDir;
    // 目录选择器启动器：返回一个树 URI（用户选定的目录）。
    private final ActivityResultLauncher<Uri> pickOutputLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onOutputPicked);
    // 媒体信息文本。
    protected TextView txtMediaInfo;
    // 文件选择器启动器：OpenMultipleDocuments 允许一次选多个文件，返回值是 URI 列表。
    private final ActivityResultLauncher<String[]> pickFilesLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::onFilesPicked);
    // 状态文本。
    protected TextView txtStatus;
    // 日志文本。
    protected TextView txtLog;
    // 日志监听器：把日志总线的内容追加到本页日志框。
    private final LogBus.Listener logListener = this::appendLog;
    // 进度条。
    protected ProgressBar progressBar;
    // 队列监听器：把整体进度与状态同步到本页。
    private final TaskQueue.Listener queueListener = this::refreshFromQueue;
    // 开始按钮。
    protected MaterialButton btnStart;
    // 当前可用的目标清单（子类提供）。
    private List<TargetSpec> targets = new ArrayList<>();
    // 当前大类可用的预设清单。
    private List<Preset> presets = new ArrayList<>();

    // 子类声明的布局资源 id。
    protected abstract int layoutRes();

    // 子类声明的媒体大类。
    protected abstract MediaOp.Category category();

    // 子类声明的日志标签。
    protected abstract String logTag();

    // 子类提供的目标清单（必须按设备能力过滤）。
    protected abstract List<TargetSpec> provideTargets();

    // 子类实现：从界面读取转换参数。
    protected abstract ConvertOptions collectOptions();

    // 子类实现：把预设参数写回界面控件。
    protected abstract void applyOptionsToUi(ConvertOptions options);

    // 生命周期回调：创建视图。
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 调用父类实现。
        super.onViewCreated(view, savedInstanceState);
        // 绑定公共控件。
        spinnerTarget = view.findViewById(R.id.spinner_target);
        // 绑定预设下拉框。
        spinnerPreset = view.findViewById(R.id.spinner_preset);
        // 绑定已选文件文本。
        txtSelectedFiles = view.findViewById(R.id.txt_selected_files);
        // 绑定目标提示文本。
        txtTargetHint = view.findViewById(R.id.txt_target_hint);
        // 绑定输出目录文本。
        txtOutputDir = view.findViewById(R.id.txt_output_dir);
        // 绑定媒体信息文本。
        txtMediaInfo = view.findViewById(R.id.txt_media_info);
        // 绑定状态文本。
        txtStatus = view.findViewById(R.id.txt_status);
        // 绑定日志文本。
        txtLog = view.findViewById(R.id.txt_log);
        // 绑定进度条。
        progressBar = view.findViewById(R.id.progress_bar);
        // 绑定开始按钮。
        btnStart = view.findViewById(R.id.btn_start);
        // 绑定按钮事件。
        MaterialButton btnPickFiles = view.findViewById(R.id.btn_pick_files);
        // 选择文件：传入 "*/*" 让系统文件选择器展示全部文件；不同提供者对视频/文本的 MIME 归类不一致，
        // 用通配符可以避免“文件明明存在却在列表里看不到”的困扰；选错类型时引擎会给出明确分类错误。
        btnPickFiles.setOnClickListener(v -> pickFilesLauncher.launch(new String[]{"*/*"}));
        // 绑定输出目录按钮。
        MaterialButton btnPickOutput = view.findViewById(R.id.btn_pick_output);
        // 选择输出目录。
        btnPickOutput.setOnClickListener(v -> pickOutputLauncher.launch(null));
        // 绑定“读取媒体信息”按钮。
        MaterialButton btnShowInfo = view.findViewById(R.id.btn_show_info);
        // 读取首个源文件的媒体信息。
        btnShowInfo.setOnClickListener(v -> loadMediaInfo());
        // 绑定“清空日志”按钮。
        MaterialButton btnClearLog = view.findViewById(R.id.btn_clear_log);
        // 清空日志缓冲与界面文本。
        btnClearLog.setOnClickListener(v -> {
            // 清空全局日志缓冲。
            LogBus.clear();
            // 清空界面日志框。
            txtLog.setText("");
        });
        // 绑定开始按钮。
        btnStart.setOnClickListener(v -> startConversion());
        // 初始化目标下拉框。
        setupTargetSpinner();
        // 初始化预设下拉框。
        setupPresetSpinner();
        // 恢复输出目录设置：默认使用设置页里保存的目录，未设置时保持应用专属目录。
        Uri saved = AppSettings.get(requireContext()).outputTreeUriOrNull();
        // 有保存值时展示出来。
        if (saved != null) {
            // 记录到本页状态，保证入队时使用同一目录。
            outputTreeUri = saved.toString();
            // 更新界面文本。
            txtOutputDir.setText(outputTreeUri);
        }
        // 初始化进度条为 0。
        progressBar.setProgress(0);
    }

    // 生命周期回调：界面可见时开始接收日志与队列通知。
    @Override
    public void onStart() {
        // 调用父类实现。
        super.onStart();
        // 先清空日志框，再注册监听，避免每次回到页面都重复回放同一批历史日志。
        if (txtLog != null) {
            // 清空界面文本。
            txtLog.setText("");
        }
        // 注册日志监听（内部会立刻回放当前缓冲）。
        LogBus.addListener(logListener);
        // 注册队列监听。
        TaskQueue.get(requireContext()).addListener(queueListener);
        // 立即同步一次队列状态。
        refreshFromQueue();
    }

    // 生命周期回调：界面不可见时注销监听，避免内存泄漏与无谓刷新。
    @Override
    public void onStop() {
        // 注销日志监听。
        LogBus.removeListener(logListener);
        // 注销队列监听。
        TaskQueue.get(requireContext()).removeListener(queueListener);
        // 调用父类实现。
        super.onStop();
    }

    /**
     * 把日志追加到界面日志框并滚动到底部。
     */
    protected void appendLog(String line) {
        // 视图尚未就绪时忽略。
        if (txtLog == null || line == null) {
            // 直接返回。
            return;
        }
        // 追加文本。
        txtLog.append(line.endsWith("\n") ? line : line + "\n");
        // 让布局先完成测量，再把滚动位置移到末尾。
        txtLog.post(() -> {
            // 日志文本是 TextView，通过计算行数定位到底部。
            int scrollAmount = txtLog.getLayout() == null ? 0
                    : txtLog.getLayout().getLineTop(txtLog.getLineCount()) - txtLog.getHeight();
            // 负数归零，避免过度滚动。
            if (scrollAmount > 0) {
                // 执行滚动。
                txtLog.scrollTo(0, scrollAmount);
            }
        });
    }

    /**
     * 设置目标下拉框的数据与选择回调。
     */
    private void setupTargetSpinner() {
        // 取得子类提供的目标清单。
        targets = provideTargets();
        // 构造展示文本数组。
        List<String> labels = new ArrayList<>();
        // 逐个目标生成展示文本（不受支持的目标会附加原因）。
        for (TargetSpec spec : targets) {
            // 加入标签。
            labels.add(spec.displayText());
        }
        // 目标清单为空说明设备能力探测异常，给出明确提示而不是让界面空白。
        if (labels.isEmpty()) {
            // 加入一条说明性条目。
            labels.add(getString(R.string.hint_no_supported_format));
        }
        // 构造适配器。
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        // 使用下拉样式。
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 设置适配器。
        spinnerTarget.setAdapter(adapter);
        // 设置选择回调：切换目标时更新不受支持的提示。
        spinnerTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            // 选中时更新提示。
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 更新提示文本。
                updateTargetHint(position);
            }

            // 未选中时不做处理。
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 空实现：下拉框总会有选中项。
            }
        });
        // 默认选中第一个受支持的目标，减少用户手动调整。
        selectFirstSupportedTarget();
    }

    /**
     * 默认选中第一个受支持的目标。
     */
    private void selectFirstSupportedTarget() {
        // 遍历目标清单。
        for (int i = 0; i < targets.size(); i++) {
            // 找到第一个受支持的目标。
            if (targets.get(i).supported) {
                // 设置选中位置。
                spinnerTarget.setSelection(i);
                // 结束查找。
                return;
            }
        }
        // 全部不支持时保持第 0 项。
        spinnerTarget.setSelection(0);
    }

    /**
     * 根据选中位置更新“不受支持”提示。
     */
    private void updateTargetHint(int position) {
        // 目标清单为空或位置越界时隐藏提示。
        if (position < 0 || position >= targets.size()) {
            // 隐藏提示。
            txtTargetHint.setVisibility(View.GONE);
            // 结束处理。
            return;
        }
        // 取选中的目标。
        TargetSpec spec = targets.get(position);
        // 受支持时隐藏提示。
        if (spec.supported) {
            // 隐藏。
            txtTargetHint.setVisibility(View.GONE);
            // 结束处理。
            return;
        }
        // 不受支持时展示明确原因，避免用户点击开始后才发现问题。
        txtTargetHint.setText(getString(R.string.hint_unsupported_target) + " — " + spec.unsupportedReason);
        // 显示提示。
        txtTargetHint.setVisibility(View.VISIBLE);
    }

    /**
     * 设置预设下拉框，并在选中时套用参数与目标格式。
     */
    private void setupPresetSpinner() {
        // 取得本大类的预设。
        presets = PresetRepository.forCategory(category());
        // 构造展示文本，首项为“不套用预设”。
        List<String> labels = new ArrayList<>();
        // 首项代表手动设置。
        labels.add("自定义（手动设置）");
        // 逐个加入预设名。
        for (Preset preset : presets) {
            // 加入名称。
            labels.add(preset.displayText());
        }
        // 构造适配器。
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        // 使用下拉样式。
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 设置适配器。
        spinnerPreset.setAdapter(adapter);
        // 设置选择回调。
        spinnerPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            // 选中时套用预设。
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 第 0 项表示不套用。
                if (position <= 0 || position - 1 >= presets.size()) {
                    // 不套用任何参数。
                    return;
                }
                // 取预设。
                Preset preset = presets.get(position - 1);
                // 套用预设。
                applyPreset(preset);
            }

            // 未选中时不做处理。
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 空实现。
            }
        });
    }

    /**
     * 套用一个预设：先尝试选中它期望的目标格式，再把参数写回界面。
     */
    private void applyPreset(Preset preset) {
        // 复制参数，避免预设对象被界面修改。
        ConvertOptions options = preset.options.copy();
        // 在目标清单里查找预设期望的目标。
        for (int i = 0; i < targets.size(); i++) {
            // 键相等即为命中。
            if (targets.get(i).key.equals(preset.targetKey)) {
                // 若该目标在当前设备受支持，直接选中。
                if (targets.get(i).supported) {
                    // 设置选中位置。
                    spinnerTarget.setSelection(i);
                } else {
                    // 不受支持时回退到第一个可用目标，并记录日志说明原因（不伪造可用性）。
                    LogBus.info(logTag(), "预设目标“" + preset.targetKey + "”在当前设备不受支持，已保留当前目标");
                }
                // 命中后结束查找。
                break;
            }
        }
        // 去掉预设名里的前缀，仅用于日志与参数摘要。
        options.presetName = preset.name;
        // 把参数写回界面控件。
        applyOptionsToUi(options);
        // 记录日志。
        LogBus.info(logTag(), "已套用预设：" + preset.name + " [" + options.describe() + "]");
    }

    /**
     * 文件选择完成回调。
     */
    private void onFilesPicked(List<Uri> uris) {
        // 空结果（用户取消）时直接返回。
        if (uris == null || uris.isEmpty()) {
            // 无操作。
            return;
        }
        // 清空旧选择并写入新选择。
        selectedUris.clear();
        // 加入全部新选择。
        selectedUris.addAll(uris);
        // 更新界面展示（最多列出前若干项，避免文本过长）。
        StringBuilder builder = new StringBuilder();
        // 记录已列出的数量。
        int shown = 0;
        // 遍历选择。
        for (Uri uri : selectedUris) {
            // 每个文件占一行。
            if (shown > 0) {
                // 追加换行。
                builder.append('\n');
            }
            // 追加文件名（查询真实显示名，比 URI 更可读）。
            builder.append("• ").append(MediaProbe.queryDisplayName(requireContext(), uri));
            // 计数递增。
            shown++;
            // 最多展示 6 行。
            if (shown >= 6) {
                // 跳出循环。
                break;
            }
        }
        // 数量超过展示上限时补充说明。
        if (selectedUris.size() > shown) {
            // 追加剩余数量提示。
            builder.append("\n… 另有 ").append(selectedUris.size() - shown).append(" 个");
        }
        // 写入界面。
        txtSelectedFiles.setText(builder.toString());
        // 记录日志。
        LogBus.info(logTag(), getString(R.string.hint_files_selected, selectedUris.size()));
        // 清空旧的媒体信息展示，提示用户重新读取。
        txtMediaInfo.setText("");
    }

    /**
     * 输出目录选择完成回调。
     */
    private void onOutputPicked(Uri uri) {
        // 用户取消时保持原状态。
        if (uri == null) {
            // 无操作。
            return;
        }
        // 尝试取得持久化读写授权，这样下次启动应用还能继续写入该目录。
        try {
            // 申请读权限与写权限的持久化授权。
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException error) {
            // 部分提供者不支持持久化授权，此时仍可在本次会话内写入，因此只记录日志。
            LogBus.info(logTag(), "未取得持久化授权；该目录仅在本次会话内可用");
        }
        // 保存到本页状态。
        outputTreeUri = uri.toString();
        // 更新界面文本。
        txtOutputDir.setText(outputTreeUri);
        // 写入设置，作为后续任务的默认输出目录。
        AppSettings.get(requireContext()).setOutputTreeUri(outputTreeUri);
        // 记录日志。
        LogBus.info(logTag(), "输出目录已设为 " + outputTreeUri);
    }

    /**
     * 读取首个源文件的媒体信息并展示。
     */
    private void loadMediaInfo() {
        // 没有选择文件时给出提示。
        if (selectedUris.isEmpty()) {
            // 更新状态文本。
            txtStatus.setText(getString(R.string.hint_no_file_selected));
            // 结束处理。
            return;
        }
        // 取首个文件。
        Uri uri = selectedUris.get(0);
        // 提示正在读取。
        txtMediaInfo.setText("正在读取媒体信息…");
        // 在后台线程读取，避免阻塞界面（SAF 读取与解封装可能较慢）。
        new Thread(() -> {
            // 使用非空默认值，保证异常路径也有内容可展示。
            String text;
            // 尝试探测。
            try {
                // 执行探测。
                MediaInfo info = MediaProbe.probe(requireContext().getApplicationContext(), uri);
                // 生成展示文本。
                text = info.describe();
            } catch (Throwable error) {
                // 探测失败时展示错误消息（异常消息里已包含分类原因与建议）。
                text = "媒体信息不可用：\n" + error.getMessage();
            }
            // 赋值给 lambda 捕获的局部变量需要 final 语义，这里用数组绕过。
            final String result = text;
            // 回到主线程更新界面。
            if (isAdded()) {
                // 切回主线程。
                requireActivity().runOnUiThread(() -> {
                    // 视图仍有效时更新。
                    if (txtMediaInfo != null) {
                        // 写入文本。
                        txtMediaInfo.setText(result);
                    }
                });
            }
        }).start();
    }

    /**
     * 开始转换：把选择拆成任务入队并启动调度。
     */
    protected void startConversion() {
        // 没有源文件时提示并返回。
        if (selectedUris.isEmpty()) {
            // 更新状态文本。
            txtStatus.setText(getString(R.string.msg_no_job));
            // 记录日志。
            LogBus.info(logTag(), "已忽略开始操作：尚未选择源文件");
            // 结束处理。
            return;
        }
        // 取选中的目标。
        int position = spinnerTarget.getSelectedItemPosition();
        // 目标清单有效时才继续。
        if (targets.isEmpty() || position < 0 || position >= targets.size()) {
            // 提示没有可用目标。
            txtStatus.setText(getString(R.string.hint_no_supported_format));
            // 结束处理。
            return;
        }
        // 取目标规格。
        TargetSpec target = targets.get(position);
        // 目标不受支持时给出明确提示并阻止启动（诚实原则：不做注定失败的尝试）。
        if (!target.supported) {
            // 更新状态文本。
            txtStatus.setText(getString(R.string.hint_unsupported_target));
            // 记录日志说明具体原因。
            LogBus.error(logTag(), "目标“" + target.label + "”不受支持：" + target.unsupportedReason);
            // 结束处理。
            return;
        }
        // 读取界面参数。
        ConvertOptions options = collectOptions();
        // 取得队列单例。
        TaskQueue queue = TaskQueue.get(requireContext());
        // 记录被去重跳过的数量。
        int skipped = 0;
        // 逐个源文件入队：一个文件对应一个任务，这样每个任务都有独立的进度与状态。
        for (Uri uri : selectedUris) {
            // 查询显示名用于输出命名与日志。
            String displayName = MediaProbe.queryDisplayName(requireContext(), uri);
            // 为每个任务复制一份参数，避免多个任务共享同一个可变对象。
            ConvertOptions jobOptions = options.copy();
            // 入队；返回 false 表示因为与已有任务重复而被跳过。
            boolean accepted = queue.enqueue(uri.toString(), displayName, target, jobOptions, outputTreeUri);
            // 被跳过时计数。
            if (!accepted) {
                // 计数递增。
                skipped++;
            }
        }
        // 记录跳过数量，供界面提示。
        queue.setSkippedDuplicates(skipped);
        // 有跳过时给出一次提示。
        if (skipped > 0) {
            // 记录日志。
            LogBus.info(logTag(), getString(R.string.msg_duplicate_skipped, skipped));
        }
        // 启动调度（会按设置页的并发数执行）。
        queue.start();
        // 更新状态文本为“已排队”。
        txtStatus.setText(getString(R.string.status_queued, queue.pendingCount()));
        // 记录日志。
        LogBus.info(logTag(), "已为 " + target.label + " 加入 " + (selectedUris.size() - skipped) + " 个任务");
        // 自动切换到队列标签页，让用户立即看到进展。
        if (getActivity() instanceof MainActivity) {
            // 调用主界面切换方法。
            ((MainActivity) getActivity()).showQueueTab();
        }
    }

    /**
     * 依据队列状态刷新本页的进度条与状态文本。
     */
    protected void refreshFromQueue() {
        // 视图不可用时忽略。
        if (progressBar == null || getContext() == null) {
            // 直接返回。
            return;
        }
        // 取队列快照。
        List<ConversionJob> jobs = TaskQueue.get(requireContext()).snapshot();
        // 队列为空时重置展示。
        if (jobs.isEmpty()) {
            // 进度归零。
            progressBar.setProgress(0);
            // 状态文本恢复空闲。
            txtStatus.setText(getString(R.string.status_idle));
            // 结束处理。
            return;
        }
        // 统计各状态数量并累加进度。
        double progressSum = 0.0;
        // 运行中数量。
        int running = 0;
        // 排队中数量。
        int pending = 0;
        // 已完成数量。
        int done = 0;
        // 失败数量。
        int failed = 0;
        // 遍历任务。
        for (ConversionJob job : jobs) {
            // 累加进度。
            progressSum += job.progress;
            // 按状态分类。
            switch (job.status) {
                // 运行中。
                case RUNNING:
                    // 计数。
                    running++;
                    // 结束该分支。
                    break;
                // 排队中。
                case PENDING:
                    // 计数。
                    pending++;
                    // 结束该分支。
                    break;
                // 已完成。
                case DONE:
                    // 计数。
                    done++;
                    // 结束该分支。
                    break;
                // 失败。
                case FAILED:
                    // 计数。
                    failed++;
                    // 结束该分支。
                    break;
                // 其它状态（暂停/取消）不计入分类统计。
                default:
                    // 无操作。
                    break;
            }
        }
        // 计算平均进度。
        int percent = (int) Math.round(progressSum / jobs.size() * 100.0);
        // 更新进度条。
        progressBar.setProgress(Math.max(0, Math.min(100, percent)));
        // 构造状态文本：优先展示失败信息，其次运行信息，最后完成信息。
        if (failed > 0) {
            // 展示失败汇总与最近一次失败原因。
            txtStatus.setText(getString(R.string.status_failed, describeLatestFailure(jobs)));
        } else if (running > 0 || pending > 0) {
            // 展示运行汇总。
            txtStatus.setText(getString(R.string.status_working, running + " 个运行中, " + pending + " 个排队中, " + percent + "%"));
        } else {
            // 展示完成汇总。
            txtStatus.setText(getString(R.string.status_finished, done + " 个已完成, " + percent + "%"));
        }
    }

    /**
     * 找到最近一次失败的原因文本。
     */
    private String describeLatestFailure(List<ConversionJob> jobs) {
        // 从后往前找，最近的任务最有参考价值。
        for (int i = jobs.size() - 1; i >= 0; i--) {
            // 取任务。
            ConversionJob job = jobs.get(i);
            // 命中失败任务时返回原因。
            if (job.status == JobStatus.FAILED && job.errorReason != null) {
                // 返回分类原因。
                return job.errorReason.reason;
            }
        }
        // 没有失败原因时返回通用文本。
        return "未知原因";
    }

    /**
     * 便捷方法：把 long 型耗时格式化为可读文本。
     */
    protected String durationText(long millis) {
        // 使用工具类格式化。
        return FormatUtil.formatDuration(millis);
    }

    /**
     * 便捷方法：判断文本是否为空。
     */
    protected boolean isBlank(String value) {
        // 使用 TextUtils 判断（含空白字符）。
        return TextUtils.isEmpty(value) || value.trim().isEmpty();
    }
}
