package com.xu.universaltranscoder.ui;

// 引入视图类型。

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

// 引入 Fragment 与注解。
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// 引入 Material 开关。
import com.google.android.material.materialswitch.MaterialSwitch;

// 引入应用类。
import com.xu.universaltranscoder.R;
// 引入模型与注册表。
import com.xu.universaltranscoder.model.ConvertOptions;
import com.xu.universaltranscoder.model.TargetRegistry;
import com.xu.universaltranscoder.model.TargetSpec;
// 引入枚举。
import com.xu.universaltranscoder.util.MediaOp;

// 引入列表类型。
import java.util.List;

/**
 * VideoFragment 是视频转换页。
 * 它支持三类操作：重封装（换容器不重编码）、重编码（H.264/H.265 + 分辨率/码率/帧率）、抽帧（导出 PNG 帧）。
 * 这些操作在公共目标清单里以“目标格式”的形式呈现，用户选择哪一项就决定走哪条流水线。
 */
public class VideoFragment extends BaseConvertFragment {

    // 码率输入框。
    private EditText etBitrate;
    // 帧率输入框。
    private EditText etFps;
    // 分辨率输入框。
    private EditText etResolution;
    // 保留音轨开关。
    private MaterialSwitch switchKeepAudio;
    // 抽帧时间点输入框。
    private EditText etFrameTimes;
    // 抽帧数量输入框。
    private EditText etFrameCount;

    // 声明布局资源。
    @Override
    protected int layoutRes() {
        // 返回视频页布局。
        return R.layout.fragment_video;
    }

    // 声明媒体大类。
    @Override
    protected MediaOp.Category category() {
        // 视频大类。
        return MediaOp.Category.VIDEO;
    }

    // 声明日志标签。
    @Override
    protected String logTag() {
        // 日志前缀。
        return "视频";
    }

    // 提供目标清单。
    @Override
    protected List<TargetSpec> provideTargets() {
        // 直接取注册表里的视频目标（含抽帧）。
        return TargetRegistry.videoTargets();
    }

    // 生命周期回调：创建视图。
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载布局。
        return inflater.inflate(layoutRes(), container, false);
    }

    // 生命周期回调：视图创建完成后绑定参数控件。
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 先初始化公共控件与参数控件，再调用父类完成公共逻辑。
        etBitrate = view.findViewById(R.id.et_bitrate);
        // 绑定帧率输入框。
        etFps = view.findViewById(R.id.et_fps);
        // 绑定分辨率输入框。
        etResolution = view.findViewById(R.id.et_resolution);
        // 绑定音轨开关。
        switchKeepAudio = view.findViewById(R.id.switch_keep_audio);
        // 绑定抽帧时间点输入框。
        etFrameTimes = view.findViewById(R.id.et_frame_times);
        // 绑定抽帧数量输入框。
        etFrameCount = view.findViewById(R.id.et_frame_count);
        // 用默认参数填充界面，保证界面一打开就有可用的取值。
        applyOptionsToUi(new ConvertOptions());
        // 调用父类完成公共初始化。
        super.onViewCreated(view, savedInstanceState);
    }

    // 从界面读取参数。
    @Override
    protected ConvertOptions collectOptions() {
        // 新建参数对象。
        ConvertOptions options = new ConvertOptions();
        // 读取码率，默认 4000 kbps。
        options.videoBitrateKbps = parseInt(etBitrate.getText().toString(), ConvertOptions.DEFAULT_VIDEO_BITRATE_KBPS);
        // 读取帧率，默认 30。
        options.videoFrameRate = parseInt(etFps.getText().toString(), ConvertOptions.DEFAULT_VIDEO_FPS);
        // 解析分辨率表达式：支持 "1280x720"、"1280"（只给宽）与 "50%"（百分比缩放）。
        applyResolutionExpression(options, etResolution.getText().toString());
        // 记录是否保留音轨。
        options.keepAudio = switchKeepAudio.isChecked();
        // 重封装时始终保留视频轨。
        options.keepVideo = true;
        // 记录抽帧时间点。
        options.frameTimesSeconds = etFrameTimes.getText().toString().trim();
        // 记录抽帧数量，默认 5。
        options.frameCount = parseInt(etFrameCount.getText().toString(), ConvertOptions.DEFAULT_FRAME_COUNT);
        // 返回参数对象。
        return options;
    }

    // 把参数写回界面。
    @Override
    protected void applyOptionsToUi(ConvertOptions options) {
        // 视图未就绪时忽略。
        if (etBitrate == null) {
            // 直接返回。
            return;
        }
        // 写入码率。
        etBitrate.setText(String.valueOf(options.videoBitrateKbps));
        // 写入帧率。
        etFps.setText(String.valueOf(options.videoFrameRate));
        // 写入分辨率表达式：优先展示百分比，其次展示宽高。
        if (options.scalePercent > 0) {
            // 展示百分比形式。
            etResolution.setText(options.scalePercent + "%");
        } else if (options.targetWidth > 0) {
            // 展示宽高形式。
            etResolution.setText(options.targetWidth + "x" + options.targetHeight);
        } else {
            // 空字符串表示沿用源分辨率。
            etResolution.setText("");
        }
        // 写入音轨开关。
        switchKeepAudio.setChecked(options.keepAudio);
        // 写入抽帧时间点。
        etFrameTimes.setText(options.frameTimesSeconds);
        // 写入抽帧数量。
        etFrameCount.setText(String.valueOf(options.frameCount));
    }

    /**
     * 解析分辨率/缩放表达式并写入参数对象。
     */
    private void applyResolutionExpression(ConvertOptions options, String expression) {
        // 去掉空白。
        String value = expression == null ? "" : expression.trim().toLowerCase(java.util.Locale.US);
        // 空字符串表示不缩放。
        if (value.isEmpty()) {
            // 保持默认为 0，即沿用源分辨率。
            return;
        }
        // 百分比形式（以 % 结尾）。
        if (value.endsWith("%")) {
            // 去掉百分号并解析数字。
            int percent = parseInt(value.substring(0, value.length() - 1), 0);
            // 合法范围才写入。
            if (percent > 0 && percent <= 1000) {
                // 记录百分比缩放。
                options.scalePercent = percent;
            }
            // 结束处理。
            return;
        }
        // 宽高形式（包含 x 或 X）。
        int separator = value.indexOf('x');
        // 存在分隔符时按宽高解析。
        if (separator > 0) {
            // 解析宽度。
            int width = parseInt(value.substring(0, separator), 0);
            // 解析高度。
            int height = parseInt(value.substring(separator + 1), 0);
            // 两者都有效时写入。
            if (width > 0 && height > 0) {
                // 记录目标宽。
                options.targetWidth = width;
                // 记录目标高。
                options.targetHeight = height;
            }
            // 结束处理。
            return;
        }
        // 只给一个数字时按“目标宽度”处理，高度按比例自动计算。
        int width = parseInt(value, 0);
        // 有效时写入宽度。
        if (width > 0) {
            // 记录目标宽。
            options.targetWidth = width;
            // 高度保持 0，表示按比例计算。
            options.targetHeight = 0;
        }
    }

    /**
     * 安全解析整数，失败返回默认值。
     */
    private int parseInt(String value, int defaultValue) {
        // 空值返回默认值。
        if (value == null || value.trim().isEmpty()) {
            // 返回默认值。
            return defaultValue;
        }
        // 尝试解析。
        try {
            // 返回解析结果。
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            // 解析失败返回默认值。
            return defaultValue;
        }
    }
}
