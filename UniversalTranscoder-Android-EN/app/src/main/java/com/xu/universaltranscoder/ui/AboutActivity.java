package com.xu.universaltranscoder.ui;

// 引入 Bundle：创建界面。

import android.os.Bundle;
// 引入 MenuItem：处理返回箭头。
import android.view.MenuItem;
// 引入 TextView：填充版本、作者与设备支持清单。
import android.widget.TextView;

// 引入 AppCompatActivity 与注解。
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

// 引入 Material 工具栏。
import com.google.android.material.appbar.MaterialToolbar;

// 引入应用资源与构建信息。
import com.xu.universaltranscoder.BuildConfig;
import com.xu.universaltranscoder.R;
// 引入容器能力探测。
import com.xu.universaltranscoder.core.Capabilities;

// 引入 MediaMuxer：把容器常量翻译成可读名称。
import android.media.MediaMuxer;

// 引入列表类型。
import java.util.List;

/**
 * AboutActivity 展示应用身份信息与三条重要说明：
 * 1) 完全离线；
 * 2) 使用的真实系统 API（转换引擎实现方式）；
 * 3) 本设备实际支持的容器清单（由运行时探测得出，绝不虚标）。
 */
public class AboutActivity extends AppCompatActivity {

    // 生命周期回调：创建界面。
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类实现。
        super.onCreate(savedInstanceState);
        // 绑定布局。
        setContentView(R.layout.activity_about);
        // 安装工具栏并启用返回箭头。
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // 设为支持 ActionBar。
        setSupportActionBar(toolbar);
        // 启用返回箭头。
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // 监听返回箭头点击。
        toolbar.setNavigationOnClickListener(v -> finish());
        // 填充版本文本：读取 BuildConfig 中的 versionName，保证与实际打包版本一致。
        TextView txtVersion = findViewById(R.id.txt_version);
        // 写入版本信息。
        txtVersion.setText(getString(R.string.about_version_label) + ": " + BuildConfig.VERSION_NAME);
        // 填充作者文本。
        TextView txtAuthor = findViewById(R.id.txt_author);
        // 写入作者信息。
        txtAuthor.setText(getString(R.string.about_author_label) + ": " + getString(R.string.about_author_value));
        // 填充设备支持清单。
        TextView txtSupport = findViewById(R.id.txt_support_list);
        // 生成探测结果文本。
        txtSupport.setText(buildSupportSummary());
    }

    // 处理返回箭头。
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // home 项表示返回。
        if (item.getItemId() == android.R.id.home) {
            // 结束页面。
            finish();
            // 消费事件。
            return true;
        }
        // 其它交给父类。
        return super.onOptionsItemSelected(item);
    }

    /**
     * 生成“本设备实际支持的容器”文本。
     */
    private String buildSupportSummary() {
        // 使用 StringBuilder 拼接多行文本。
        StringBuilder builder = new StringBuilder();
        // 探测设备支持的容器清单。
        List<Integer> formats = Capabilities.supportedMuxerFormats(this);
        // 逐个翻译为可读名称。
        for (Integer format : formats) {
            // 追加容器名。
            builder.append("• ").append(containerName(format.intValue())).append('\n');
        }
        // 清单为空时说明设备封装器异常。
        if (formats.isEmpty()) {
            // 追加说明。
            builder.append("• No MediaMuxer container is accepted by this device.\n");
        }
        // 说明 Matroska 在任何 Android 版本上都没有封装支持，避免用户以为只是本机缺失。
        builder.append("• Matroska (MKV) is not available: Android MediaMuxer has no Matroska output.\n");
        // 说明 Ogg 容器从 API 29 起才被 MediaMuxer 支持，而本应用的目标版本为 API 28。
        builder.append("• Ogg is not listed: MediaMuxer Ogg output requires API 29 or newer.\n");
        // 返回拼接结果。
        return builder.toString();
    }

    /**
     * 把 MediaMuxer 容器常量翻译为可读名称。
     */
    private String containerName(int format) {
        // MP4 容器。
        if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) {
            // 返回名称。
            return "MPEG-4 (.mp4 / .m4a)";
        }
        // WebM 容器。
        if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM) {
            // 返回名称。
            return "WebM (.webm)";
        }
        // 3GPP 容器。
        if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            // 返回名称。
            return "3GPP (.3gp)";
        }
        // 其它容器直接展示常量值。
        return "Container format " + format;
    }
}
