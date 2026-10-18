package com.xu.universaltranscoder.ui;

// 引入视图类型。

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

// 引入 Fragment 与注解。
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.Locale;

/**
 * ImageFragment 是图片转换页。
 * 支持 PNG / JPEG / WEBP 三种输出、可调质量（有损格式）与缩放（百分比或指定宽高）。
 * 转换过程使用 BitmapFactory 采样解码 + Bitmap.compress 编码，并带有内存保护策略。
 */
public class ImageFragment extends BaseConvertFragment {

    // 质量滑动条。
    private SeekBar sbQuality;
    // 质量数值文本。
    private TextView txtQualityValue;
    // 缩放输入框。
    private EditText etImageScale;

    // 声明布局资源。
    @Override
    protected int layoutRes() {
        // 返回图片页布局。
        return R.layout.fragment_image;
    }

    // 声明媒体大类。
    @Override
    protected MediaOp.Category category() {
        // 图片大类。
        return MediaOp.Category.IMAGE;
    }

    // 声明日志标签。
    @Override
    protected String logTag() {
        // 日志前缀。
        return "图片";
    }

    // 提供目标清单。
    @Override
    protected List<TargetSpec> provideTargets() {
        // 取注册表里的图片目标（PNG/JPEG/WEBP 全部必然受支持）。
        return TargetRegistry.imageTargets();
    }

    // 生命周期回调：创建视图。
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载布局。
        return inflater.inflate(layoutRes(), container, false);
    }

    // 生命周期回调：绑定参数控件。
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 绑定质量滑动条。
        sbQuality = view.findViewById(R.id.sb_quality);
        // 绑定质量数值文本。
        txtQualityValue = view.findViewById(R.id.txt_quality_value);
        // 绑定缩放输入框。
        etImageScale = view.findViewById(R.id.et_image_scale);
        // 监听滑动，实时展示当前质量值，让用户对参数变化有即时反馈。
        sbQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // 拖动过程中更新文本。
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 质量最低 1，避免出现 0（0 在部分编码器上等同于默认值）。
                int value = Math.max(1, progress);
                // 更新文本。
                txtQualityValue.setText(value + " / 100");
            }

            // 开始拖动时无需处理。
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 空实现。
            }

            // 结束拖动时无需处理。
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 空实现。
            }
        });
        // 用默认参数填充界面。
        applyOptionsToUi(new ConvertOptions());
        // 调用父类完成公共初始化。
        super.onViewCreated(view, savedInstanceState);
    }

    // 从界面读取参数。
    @Override
    protected ConvertOptions collectOptions() {
        // 新建参数对象。
        ConvertOptions options = new ConvertOptions();
        // 读取质量（至少 1）。
        options.imageQuality = Math.max(1, sbQuality.getProgress());
        // 解析缩放表达式。
        applyScaleExpression(options, etImageScale.getText().toString());
        // 返回参数对象。
        return options;
    }

    // 把参数写回界面。
    @Override
    protected void applyOptionsToUi(ConvertOptions options) {
        // 视图未就绪时忽略。
        if (sbQuality == null) {
            // 直接返回。
            return;
        }
        // 写入质量值。
        sbQuality.setProgress(Math.max(1, Math.min(100, options.imageQuality)));
        // 同步展示文本。
        txtQualityValue.setText(Math.max(1, Math.min(100, options.imageQuality)) + " / 100");
        // 写入缩放表达式。
        if (options.scalePercent > 0) {
            // 百分比形式。
            etImageScale.setText(options.scalePercent + "%");
        } else if (options.imageTargetWidth > 0 && options.imageTargetHeight > 0) {
            // 宽高形式。
            etImageScale.setText(options.imageTargetWidth + "x" + options.imageTargetHeight);
        } else if (options.imageTargetWidth > 0) {
            // 只给宽度。
            etImageScale.setText(String.valueOf(options.imageTargetWidth));
        } else {
            // 空表示不缩放。
            etImageScale.setText("");
        }
    }

    /**
     * 解析缩放表达式并写入参数对象。
     */
    private void applyScaleExpression(ConvertOptions options, String expression) {
        // 去空白并小写。
        String value = expression == null ? "" : expression.trim().toLowerCase(Locale.US);
        // 空表示不缩放。
        if (value.isEmpty()) {
            // 保持默认。
            return;
        }
        // 百分比形式。
        if (value.endsWith("%")) {
            // 解析百分比数值。
            int percent = parseInt(value.substring(0, value.length() - 1), 0);
            // 合法范围才写入。
            if (percent > 0 && percent <= 1000) {
                // 记录百分比缩放。
                options.scalePercent = percent;
            }
            // 结束处理。
            return;
        }
        // 宽高形式。
        int separator = value.indexOf('x');
        // 存在分隔符时按宽高解析。
        if (separator > 0) {
            // 解析宽。
            int width = parseInt(value.substring(0, separator), 0);
            // 解析高。
            int height = parseInt(value.substring(separator + 1), 0);
            // 两者有效才写入。
            if (width > 0 && height > 0) {
                // 记录宽。
                options.imageTargetWidth = width;
                // 记录高。
                options.imageTargetHeight = height;
            }
            // 结束处理。
            return;
        }
        // 只给宽度。
        int width = parseInt(value, 0);
        // 有效时写入。
        if (width > 0) {
            // 记录宽，高度为 0 表示按比例计算。
            options.imageTargetWidth = width;
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
