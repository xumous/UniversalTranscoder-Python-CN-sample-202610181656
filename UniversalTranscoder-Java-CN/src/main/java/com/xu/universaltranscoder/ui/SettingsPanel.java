package com.xu.universaltranscoder.ui;

// 引入内核对象，用于重新探测与展示结果

import com.xu.universaltranscoder.core.Toolchain;

// 引入 Swing 组件
import javax.swing.BorderFactory;
// 引入按钮
import javax.swing.JButton;
// 引入复选框
import javax.swing.JCheckBox;
// 引入下拉框
import javax.swing.JComboBox;
// 引入文件选择器
import javax.swing.JFileChooser;
// 引入标签
import javax.swing.JLabel;
// 引入面板
import javax.swing.JPanel;
// 引入微调器，用于限制数值型设置
import javax.swing.JSpinner;
// 引入文本框
import javax.swing.JTextField;
// 引入微调器数值模型，设置上下限
import javax.swing.SpinnerNumberModel;
// 引入滚动面板
import javax.swing.JScrollPane;

// 引入布局管理器
import java.awt.BorderLayout;
// 引入流式布局
import java.awt.FlowLayout;
// 引入网格袋布局
import java.awt.GridBagConstraints;
// 引入网格袋布局管理器
import java.awt.GridBagLayout;
// 引入内边距
import java.awt.Insets;
// 引入维度
import java.awt.Dimension;

// 关键字 class：SettingsPanel 提供内核路径、输出目录、并发、日志级别、主题与硬件加速等设置
// 之所以把“应用并保存”作为显式动作：用户可能只是想临时改一项后立即生效，或改完重新探测内核
public final class SettingsPanel extends JPanel {

    // 应用外壳引用
    private final AppShell shell;
    // 应用回调，用于在设置保存后重新应用主题与重新探测内核
    private final Runnable onApply;

    // 自定义内核路径输入框
    private final JTextField ffmpegPathField = new JTextField();
    // 默认输出目录输入框
    private final JTextField defaultOutputField = new JTextField();
    // 并发数微调器
    private final JSpinner concurrencySpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    // 日志级别下拉框
    private final JComboBox<String> logLevelBox = new JComboBox<>(new String[]{"仅错误", "警告", "信息", "详细"});
    // 主题下拉框
    private final JComboBox<String> themeBox = new JComboBox<>(new String[]{"浅色", "深色"});
    // 硬件加速复选框
    private final JCheckBox hardwareBox = new JCheckBox("启用硬件加速（NVENC / QSV / AMF）");
    // 内核状态标签，实时展示探测结果
    private final JLabel coreStatusLabel = new JLabel();

    // 构造方法：接收应用外壳与保存回调
    public SettingsPanel(AppShell shell, Runnable onApply) {
        // 保存应用外壳引用
        this.shell = shell;
        // 保存保存回调引用
        this.onApply = onApply;
        // 使用边界布局
        setLayout(new BorderLayout(6, 6));
        // 设置四周留白
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // 构建表单
        JPanel form = new JPanel(new GridBagLayout());
        // 第 0 行为自定义内核路径
        addRow(form, 0, "FFmpeg 路径：", ffmpegPathField, buildPathButtons());
        // 第 1 行为默认输出目录
        addRow(form, 1, "默认输出目录：", defaultOutputField, buildOutputButtons());
        // 第 2 行为并发数
        addRow(form, 2, "并发任务数：", concurrencySpinner, null);
        // 第 3 行为日志级别
        addRow(form, 3, "日志级别：", logLevelBox, null);
        // 第 4 行为主题
        addRow(form, 4, "主题：", themeBox, null);
        // 第 5 行为硬件加速开关
        addRow(form, 5, "硬件加速：", hardwareBox, null);
        // 使用可滚动容器包裹表单，窗口较小时可滚动
        add(new JScrollPane(form), BorderLayout.CENTER);
        // 构建底部动作条
        add(buildActionBar(), BorderLayout.SOUTH);
        // 从应用设置加载当前值到控件
        loadFromSettings();
    }

    // 构建路径选择与自动探测按钮
    private JPanel buildPathButtons() {
        // 使用流式布局
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        // 创建选择文件按钮
        JButton browseButton = new JButton("选择…");
        // 绑定选择动作
        browseButton.addActionListener(event -> {
            // 创建文件选择器
            JFileChooser chooser = new JFileChooser();
            // 只允许选择文件
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            // 弹出对话框
            int result = chooser.showOpenDialog(this);
            // 用户确认后写回输入框
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                // 写入选中路径
                ffmpegPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        // 创建自动探测按钮
        JButton autoButton = new JButton("自动");
        // 绑定自动探测动作：清空路径让内核按内置顺序查找
        autoButton.addActionListener(event -> ffmpegPathField.setText(""));
        // 加入选择按钮
        panel.add(browseButton);
        // 加入自动按钮
        panel.add(autoButton);
        // 返回按钮容器
        return panel;
    }

    // 构建输出目录选择按钮
    private JPanel buildOutputButtons() {
        // 使用流式布局
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        // 创建浏览按钮
        JButton browseButton = new JButton("浏览…");
        // 绑定浏览动作
        browseButton.addActionListener(event -> {
            // 创建文件选择器
            JFileChooser chooser = new JFileChooser();
            // 只允许选择目录
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            // 弹出对话框
            int result = chooser.showOpenDialog(this);
            // 用户确认后写回输入框
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                // 写入选中目录
                defaultOutputField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        // 创建清空按钮，用于回到与输入同目录
        JButton clearButton = new JButton("与输入同目录");
        // 绑定清空动作
        clearButton.addActionListener(event -> defaultOutputField.setText(""));
        // 加入浏览按钮
        panel.add(browseButton);
        // 加入清空按钮
        panel.add(clearButton);
        // 返回按钮容器
        return panel;
    }

    // 构建底部动作条
    private JPanel buildActionBar() {
        // 使用边界布局，按钮在左状态在右
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        // 按钮容器使用流式布局
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建保存按钮
        JButton saveButton = new JButton("保存并应用");
        // 绑定保存动作
        saveButton.addActionListener(event -> saveAndApply());
        // 创建重新探测按钮
        JButton reprobeButton = new JButton("重新探测内核");
        // 绑定重新探测动作
        reprobeButton.addActionListener(event -> reprobe());
        // 创建恢复默认按钮
        JButton resetButton = new JButton("恢复默认");
        // 绑定恢复默认动作
        resetButton.addActionListener(event -> resetDefaults());
        // 依次加入按钮
        buttons.add(saveButton);
        // 加入重新探测按钮
        buttons.add(reprobeButton);
        // 加入恢复默认按钮
        buttons.add(resetButton);
        // 把按钮容器放到左侧
        panel.add(buttons, BorderLayout.CENTER);
        // 把内核状态标签放到右侧
        panel.add(coreStatusLabel, BorderLayout.EAST);
        // 返回动作条
        return panel;
    }

    // 向表单追加一行
    private void addRow(JPanel panel, int row, String label, javax.swing.JComponent main, javax.swing.JComponent extra) {
        // 创建标签
        JLabel labelComponent = new JLabel(label);
        // 构造标签约束
        GridBagConstraints labelConstraints = new GridBagConstraints();
        // 第一列
        labelConstraints.gridx = 0;
        // 指定行
        labelConstraints.gridy = row;
        // 靠西对齐
        labelConstraints.anchor = GridBagConstraints.WEST;
        // 设置内边距
        labelConstraints.insets = new Insets(4, 4, 4, 6);
        // 加入标签
        panel.add(labelComponent, labelConstraints);
        // 构造主控件约束
        GridBagConstraints mainConstraints = new GridBagConstraints();
        // 第二列
        mainConstraints.gridx = 1;
        // 指定行
        mainConstraints.gridy = row;
        // 横向填充
        mainConstraints.fill = GridBagConstraints.HORIZONTAL;
        // 横向权重设为 1
        mainConstraints.weightx = 1d;
        // 设置内边距
        mainConstraints.insets = new Insets(4, 0, 4, 6);
        // 统一控件高度
        main.setPreferredSize(new Dimension(main.getPreferredSize().width, 24));
        // 加入主控件
        panel.add(main, mainConstraints);
        // 附加控件存在时才占第三列
        if (extra != null) {
            // 构造附加控件约束
            GridBagConstraints extraConstraints = new GridBagConstraints();
            // 第三列
            extraConstraints.gridx = 2;
            // 指定行
            extraConstraints.gridy = row;
            // 靠东对齐
            extraConstraints.anchor = GridBagConstraints.EAST;
            // 设置内边距
            extraConstraints.insets = new Insets(4, 0, 4, 4);
            // 加入附加控件
            panel.add(extra, extraConstraints);
        }
    }

    // 从应用设置加载当前值到控件
    public void loadFromSettings() {
        // 写入自定义内核路径
        ffmpegPathField.setText(shell.settings().customFfmpegPath());
        // 写入默认输出目录
        defaultOutputField.setText(shell.settings().defaultOutputDir());
        // 写入并发数
        concurrencySpinner.setValue(shell.settings().concurrency());
        // 写入日志级别
        logLevelBox.setSelectedItem(shell.settings().logLevel());
        // 写入主题
        themeBox.setSelectedItem(shell.settings().theme());
        // 写入硬件加速开关
        hardwareBox.setSelected(shell.settings().hardwareAcceleration());
        // 刷新内核状态标签
        updateCoreStatus();
    }

    // 保存设置并应用
    private void saveAndApply() {
        // 写入自定义内核路径
        shell.settings().customFfmpegPath(ffmpegPathField.getText());
        // 写入默认输出目录
        shell.settings().defaultOutputDir(defaultOutputField.getText());
        // 写入并发数，值来自微调器
        shell.settings().concurrency((Integer) concurrencySpinner.getValue());
        // 写入日志级别
        shell.settings().logLevel(String.valueOf(logLevelBox.getSelectedItem()));
        // 写入主题
        shell.settings().theme(String.valueOf(themeBox.getSelectedItem()));
        // 写入硬件加速开关
        shell.settings().hardwareAcceleration(hardwareBox.isSelected());
        // 持久化到磁盘
        shell.settings().save();
        // 输出保存日志
        shell.log("设置已保存：并发=" + shell.settings().concurrency()
                // 追加主题与硬件加速状态
                + "，主题=" + shell.settings().theme()
                // 追加硬件加速状态
                + "，硬件加速=" + shell.settings().hardwareAcceleration());
        // 触发应用回调，让主窗口重新应用主题并重新探测内核
        if (onApply != null) {
            // 执行回调
            onApply.run();
        }
        // 刷新内核状态标签
        updateCoreStatus();
    }

    // 重新探测内核并展示结果
    private void reprobe() {
        // 使用当前输入框中的路径重新探测内核
        Toolchain toolchain = Toolchain.bootstrap(ffmpegPathField.getText());
        // 输出探测结果日志
        shell.log("内核已重新探测：" + toolchain.describe());
        // 刷新状态标签
        updateCoreStatus();
    }

    // 恢复出厂默认值到控件（不立即保存）
    private void resetDefaults() {
        // 清空自定义内核路径
        ffmpegPathField.setText("");
        // 清空默认输出目录
        defaultOutputField.setText("");
        // 并发恢复为 2
        concurrencySpinner.setValue(2);
        // 日志级别恢复为仅错误
        logLevelBox.setSelectedItem("仅错误");
        // 主题恢复为浅色
        themeBox.setSelectedItem("浅色");
        // 硬件加速关闭
        hardwareBox.setSelected(false);
        // 输出日志提示
        shell.log("表单已恢复默认值，点击“保存并应用”后生效。");
    }

    // 更新内核状态标签文本
    private void updateCoreStatus() {
        // 取内核对象
        Toolchain toolchain = shell.toolchain();
        // 内核可用时展示路径与能力数量
        if (toolchain.isAvailable()) {
            // 设置中文状态文本
            coreStatusLabel.setText("内核正常 | 编码器 " + toolchain.encoders().size()
                    // 追加封装数量
                    + " | 封装 " + toolchain.muxFormats().size());
        } else {
            // 内核不可用时给出明确提示
            coreStatusLabel.setText("未找到内核 | 请在上方配置 FFmpeg 路径");
        }
    }

    // 获取主题下拉框，供冒烟测试检查
    public JComboBox<String> themeBox() {
        // 返回下拉框
        return themeBox;
    }

    // 获取硬件加速复选框，供冒烟测试检查
    public JCheckBox hardwareBox() {
        // 返回复选框
        return hardwareBox;
    }
}
