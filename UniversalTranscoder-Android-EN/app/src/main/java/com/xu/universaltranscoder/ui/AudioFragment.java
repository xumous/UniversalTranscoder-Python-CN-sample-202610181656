package com.xu.universaltranscoder.ui;

// 引入视图类型。

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

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
import java.util.Arrays;
import java.util.List;

/**
 * AudioFragment 是音频转换页。
 * 支持两类操作：音频重封装（换容器不改编码）与音频重编码（解码后按目标采样率/声道/码率编码为 AAC）。
 * 采样率与声道参数会在底层通过 PCM 重采样与声道变换真正生效，而不是只改界面显示。
 */
public class AudioFragment extends BaseConvertFragment {

    // 码率输入框。
    private EditText etAudioBitrate;
    // 采样率输入框。
    private EditText etSampleRate;
    // 声道下拉框。
    private Spinner spinnerChannels;

    // 声明布局资源。
    @Override
    protected int layoutRes() {
        // 返回音频页布局。
        return R.layout.fragment_audio;
    }

    // 声明媒体大类。
    @Override
    protected MediaOp.Category category() {
        // 音频大类。
        return MediaOp.Category.AUDIO;
    }

    // 声明日志标签。
    @Override
    protected String logTag() {
        // 日志前缀。
        return "Audio";
    }

    // 提供目标清单。
    @Override
    protected List<TargetSpec> provideTargets() {
        // 取注册表里的音频目标。
        return TargetRegistry.audioTargets();
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
        // 绑定码率输入框。
        etAudioBitrate = view.findViewById(R.id.et_audio_bitrate);
        // 绑定采样率输入框。
        etSampleRate = view.findViewById(R.id.et_sample_rate);
        // 绑定声道下拉框。
        spinnerChannels = view.findViewById(R.id.spinner_channels);
        // 构造声道选项：单声道与立体声。
        List<String> channelLabels = Arrays.asList(getString(R.string.channels_mono), getString(R.string.channels_stereo));
        // 构造适配器。
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, channelLabels);
        // 使用下拉样式。
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 设置适配器。
        spinnerChannels.setAdapter(adapter);
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
        // 读取音频码率。
        options.audioBitrateKbps = parseInt(etAudioBitrate.getText().toString(), ConvertOptions.DEFAULT_AUDIO_BITRATE_KBPS);
        // 读取采样率。
        options.sampleRate = parseInt(etSampleRate.getText().toString(), ConvertOptions.DEFAULT_SAMPLE_RATE);
        // 读取声道数：下拉框第 0 项为单声道，其余为立体声。
        options.channels = spinnerChannels.getSelectedItemPosition() == 0 ? 1 : 2;
        // 返回参数对象。
        return options;
    }

    // 把参数写回界面。
    @Override
    protected void applyOptionsToUi(ConvertOptions options) {
        // 视图未就绪时忽略。
        if (etAudioBitrate == null) {
            // 直接返回。
            return;
        }
        // 写入码率。
        etAudioBitrate.setText(String.valueOf(options.audioBitrateKbps));
        // 写入采样率。
        etSampleRate.setText(String.valueOf(options.sampleRate));
        // 写入声道选择：1 -> 位置 0；2 -> 位置 1。
        spinnerChannels.setSelection(options.channels == 1 ? 0 : 1);
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
