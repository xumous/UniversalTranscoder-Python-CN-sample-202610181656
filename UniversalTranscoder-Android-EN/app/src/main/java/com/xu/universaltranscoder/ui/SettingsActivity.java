package com.xu.universaltranscoder.ui;

// 引入 Bundle：创建 Activity。

import android.os.Bundle;
// 引入 Uri/Intent：目录选择与权限标志。
import android.content.Intent;
import android.net.Uri;
// 引入 MenuItem：处理工具栏返回箭头。
import android.view.MenuItem;
// 引入视图类型。
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

// 引入 Activity Result API。
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
// 引入 AppCompatActivity 与注解。
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

// 引入 Material 组件。
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

// 引入应用资源。
import com.xu.universaltranscoder.R;
// 引入设置与日志。
import com.xu.universaltranscoder.util.AppSettings;
import com.xu.universaltranscoder.util.LogBus;

// 引入列表类型。
import java.util.Arrays;
import java.util.List;

/**
 * SettingsActivity 提供四项可持久化配置：
 * 1) 并发任务数（1..4）；
 * 2) 默认输出目录（SAF 目录或应用专属目录）；
 * 3) 日志级别（Error / Info / Debug）；
 * 4) 是否优先使用硬件编解码器。
 * 这些配置直接影响调度行为与转换策略，因此保存后立即生效（日志级别即时生效，并发数在下次启动调度时生效）。
 */
public class SettingsActivity extends AppCompatActivity {

    // 设置读写对象。
    private AppSettings settings;
    // 并发数下拉框。
    private Spinner spinnerConcurrency;
    // 日志级别下拉框。
    private Spinner spinnerLogLevel;
    // 输出目录展示文本。
    private TextView txtOutput;
    // 目录选择器启动器。
    private final ActivityResultLauncher<Uri> pickOutputLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onOutputPicked);
    // 硬件加速开关。
    private MaterialSwitch switchHardware;

    // 生命周期回调：创建界面。
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类实现。
        super.onCreate(savedInstanceState);
        // 绑定布局。
        setContentView(R.layout.activity_settings);
        // 取得设置对象。
        settings = AppSettings.get(this);
        // 安装工具栏并启用返回箭头。
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // 设为支持 ActionBar。
        setSupportActionBar(toolbar);
        // 启用返回箭头。
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // 监听返回箭头点击。
        toolbar.setNavigationOnClickListener(v -> finish());
        // 绑定并发数下拉框。
        spinnerConcurrency = findViewById(R.id.spinner_concurrency);
        // 绑定日志级别下拉框。
        spinnerLogLevel = findViewById(R.id.spinner_log_level);
        // 绑定输出目录文本。
        txtOutput = findViewById(R.id.txt_settings_output);
        // 绑定硬件加速开关。
        switchHardware = findViewById(R.id.switch_hw);
        // 构造并发数选项：1 到 4（默认 1 即串行执行）。
        List<String> concurrencyOptions = Arrays.asList("1 (serial)", "2", "3", "4");
        // 安装并发适配器。
        spinnerConcurrency.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, concurrencyOptions));
        // 构造日志级别选项。
        List<String> logOptions = Arrays.asList(getString(R.string.log_level_error), getString(R.string.log_level_info),
                getString(R.string.log_level_debug));
        // 安装日志适配器。
        spinnerLogLevel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, logOptions));
        // 回填当前设置。
        spinnerConcurrency.setSelection(Math.max(0, Math.min(3, settings.concurrency() - 1)));
        // 回填日志级别。
        spinnerLogLevel.setSelection(Math.max(0, Math.min(2, settings.logLevel())));
        // 回填硬件加速开关。
        switchHardware.setChecked(settings.preferHardware());
        // 回填输出目录展示。
        updateOutputLabel();
        // 保存并即时生效的选择回调：下拉框选择后立刻持久化，避免用户忘记点保存。
        spinnerConcurrency.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            // 位置 0..3 对应并发数 1..4。
            settings.setConcurrency(position + 1);
            // 记录日志。
            LogBus.info("Settings", "Concurrency set to " + (position + 1));
            // 提示已保存。
            toastSaved();
        }));
        // 日志级别变化回调。
        spinnerLogLevel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            // 位置即日志级别常量（0=Error,1=Info,2=Debug）。
            settings.setLogLevel(position);
            // 立刻应用到日志总线，后续日志按新级别输出。
            LogBus.setMinLevel(position);
            // 记录日志。
            LogBus.info("Settings", "Log level set to " + position);
            // 提示已保存。
            toastSaved();
        }));
        // 硬件加速开关回调。
        switchHardware.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 持久化设置。
            settings.setPreferHardware(isChecked);
            // 记录日志。
            LogBus.info("Settings", "Hardware codecs preferred = " + isChecked);
        });
        // 绑定选择默认输出目录按钮。
        MaterialButton btnOutput = findViewById(R.id.btn_settings_output);
        // 打开目录选择器。
        btnOutput.setOnClickListener(v -> pickOutputLauncher.launch(null));
        // 绑定恢复默认按钮。
        MaterialButton btnReset = findViewById(R.id.btn_settings_reset);
        // 恢复默认设置。
        btnReset.setOnClickListener(v -> {
            // 清空偏好文件。
            settings.reset();
            // 同步界面控件到默认值。
            spinnerConcurrency.setSelection(0);
            // 日志级别回到 Info。
            spinnerLogLevel.setSelection(AppSettings.LOG_LEVEL_INFO);
            // 硬件加速回到开启。
            switchHardware.setChecked(settings.preferHardware());
            // 应用日志级别。
            LogBus.setMinLevel(settings.logLevel());
            // 更新输出目录展示。
            updateOutputLabel();
            // 记录日志。
            LogBus.info("Settings", "Settings reset to defaults");
            // 提示用户。
            toastSaved();
        });
    }

    // 处理工具栏返回箭头（通过 ActionBar 的 home 项）。
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // home 项表示返回。
        if (item.getItemId() == android.R.id.home) {
            // 结束当前页面。
            finish();
            // 消费事件。
            return true;
        }
        // 其它交给父类。
        return super.onOptionsItemSelected(item);
    }

    /**
     * 目录选择完成回调：保存为默认输出目录。
     */
    private void onOutputPicked(Uri uri) {
        // 用户取消时忽略。
        if (uri == null) {
            // 直接返回。
            return;
        }
        // 尝试取得持久化读写授权。
        try {
            // 申请持久化授权，保证应用重启后仍可写入。
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException error) {
            // 部分提供者不支持持久化授权，记录日志但不阻断流程。
            LogBus.info("Settings", "Persistable permission unavailable for the selected folder");
        }
        // 持久化保存。
        settings.setOutputTreeUri(uri.toString());
        // 更新界面展示。
        updateOutputLabel();
        // 记录日志。
        LogBus.info("Settings", "Default output directory set to " + uri);
        // 提示用户。
        toastSaved();
    }

    /**
     * 刷新输出目录展示文本。
     */
    private void updateOutputLabel() {
        // 取得已保存的目录。
        Uri saved = settings.outputTreeUriOrNull();
        // 有值时展示自定义目录。
        if (saved != null) {
            // 展示目录地址。
            txtOutput.setText(getString(R.string.settings_custom_dir_value, saved.toString()));
            // 结束处理。
            return;
        }
        // 无值时展示应用专属目录说明。
        txtOutput.setText(getString(R.string.settings_default_dir_value));
    }

    /**
     * 统一的“已保存”提示。
     */
    private void toastSaved() {
        // 短提示即可，避免打扰。
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
    }

    /**
     * SimpleItemSelectedListener 把 Spinner 的选择回调简化成“只关心位置”的 lambda。
     * 之所以需要它：Spinner 的 OnItemSelectedListener 在设置适配器后会立刻触发一次回调，
     * 若不做任何处理，界面上显示的默认值会被当成“用户刚做的修改”而重复写盘。
     * 这里通过忽略下标相同、且仅在用户交互后才回调的方式，避免这种无意义的写入；
     * 实现上用一个内部标记区分“初始化触发”与“用户选择”。
     */
    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {

        // 位置回调。
        private final java.util.function.IntConsumer onSelected;
        // 最近一次已通知的位置，用于抑制重复回调。
        private int lastNotified = -1;

        // 构造：保存回调。
        SimpleItemSelectedListener(java.util.function.IntConsumer onSelected) {
            // 保存回调引用。
            this.onSelected = onSelected;
        }

        // 选中回调。
        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            // 与上次相同的位置不再重复回调，避免初始化时的自动触发造成写盘。
            if (position == lastNotified) {
                // 直接返回。
                return;
            }
            // 记录位置。
            lastNotified = position;
            // 执行回调。
            onSelected.accept(position);
        }

        // 未选中回调：不处理。
        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
            // 空实现。
        }
    }
}
