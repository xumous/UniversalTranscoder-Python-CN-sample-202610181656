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
 * DocumentFragment 是文档转换页。
 * 支持三种操作：
 * 1) 图片 → 单个多页 PDF（PdfDocument）；
 * 2) PDF → 逐页 PNG（PdfRenderer，支持页码范围）；
 * 3) 纯文本 → PDF（按字号排版并自动分页）。
 * 页面尺寸策略与字号都在这里配置；输入类型与操作的匹配校验由转换引擎负责并给出明确错误。
 */
public class DocumentFragment extends BaseConvertFragment {

    // 页码范围输入框。
    private EditText etPageRange;
    // 正文字号输入框。
    private EditText etTextSize;
    // 页面尺寸策略开关。
    private MaterialSwitch switchAutoPage;

    // 声明布局资源。
    @Override
    protected int layoutRes() {
        // 返回文档页布局。
        return R.layout.fragment_document;
    }

    // 声明媒体大类。
    @Override
    protected MediaOp.Category category() {
        // 文档大类。
        return MediaOp.Category.DOCUMENT;
    }

    // 声明日志标签。
    @Override
    protected String logTag() {
        // 日志前缀。
        return "文档";
    }

    // 提供目标清单。
    @Override
    protected List<TargetSpec> provideTargets() {
        // 取注册表里的文档目标。
        return TargetRegistry.documentTargets();
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
        // 绑定页码范围输入框。
        etPageRange = view.findViewById(R.id.et_page_range);
        // 绑定字号输入框。
        etTextSize = view.findViewById(R.id.et_text_size);
        // 绑定页面尺寸开关。
        switchAutoPage = view.findViewById(R.id.switch_auto_page);
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
        // 读取页码范围表达式（空表示全部页）。
        options.pageRange = etPageRange.getText().toString().trim();
        // 读取正文字号，默认 12pt。
        options.pdfTextSize = parseInt(etTextSize.getText().toString(), 12);
        // 读取页面尺寸策略。
        options.pdfAutoPageSize = switchAutoPage.isChecked();
        // 返回参数对象。
        return options;
    }

    // 把参数写回界面。
    @Override
    protected void applyOptionsToUi(ConvertOptions options) {
        // 视图未就绪时忽略。
        if (etPageRange == null) {
            // 直接返回。
            return;
        }
        // 写入页码范围。
        etPageRange.setText(options.pageRange);
        // 写入字号。
        etTextSize.setText(String.valueOf(options.pdfTextSize));
        // 写入页面尺寸策略。
        switchAutoPage.setChecked(options.pdfAutoPageSize);
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
