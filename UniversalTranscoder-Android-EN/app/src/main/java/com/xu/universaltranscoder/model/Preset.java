package com.xu.universaltranscoder.model;

// 引入 MediaOp：预设需要声明自己属于哪个大类。

import com.xu.universaltranscoder.util.MediaOp;

/**
 * Preset 描述一套“一键套用”的参数组合。
 * 预设立足于“用户不想懂参数”：选一个预设，界面就把目标格式与全部参数设置为该预设的取值。
 */
public final class Preset {

    // 预设名称（英文），直接展示在下拉框中。
    public final String name;
    // 预设所属大类，用于在对应页面的下拉框里过滤。
    public final MediaOp.Category category;
    // 预设期望选中的目标规格键；若该目标在当前设备不受支持，界面会回退到第一个可用目标并给出日志提示。
    public final String targetKey;
    // 预设携带的参数，套用时会被复制一份，避免与预设本身共享可变状态。
    public final ConvertOptions options;

    // 构造：一次性绑定四个字段。
    public Preset(String name, MediaOp.Category category, String targetKey, ConvertOptions options) {
        // 保存名称。
        this.name = name;
        // 保存大类。
        this.category = category;
        // 保存目标键。
        this.targetKey = targetKey;
        // 保存参数对象。
        this.options = options;
    }

    /**
     * 返回下拉框展示文本：名称 + 目标键，便于用户理解预设会输出什么。
     */
    public String displayText() {
        // 拼接名称与目标键。
        return name;
    }
}
