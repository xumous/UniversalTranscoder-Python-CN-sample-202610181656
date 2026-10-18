package com.xu.universaltranscoder.ui;

// 引入命令构建器，用于生成命令预览

import com.xu.universaltranscoder.core.CommandBuilder;
// 引入转换请求，用于构造预览参数
import com.xu.universaltranscoder.core.ConversionRequest;
// 引入文档任务工厂，用于创建抽帧任务
import com.xu.universaltranscoder.core.DocumentTasks;
// 引入错误分类器的类别枚举，用于展示诊断参考
import com.xu.universaltranscoder.core.ErrorClassifier;
// 引入格式映射表，用于获取可选格式
import com.xu.universaltranscoder.core.FormatTables;
// 引入帧抽取器的时间点生成方法
import com.xu.universaltranscoder.core.FrameExtractor;
// 引入硬件加速策略类，用于展示检测结果
import com.xu.universaltranscoder.core.HardwareAccel;
// 引入媒体信息
import com.xu.universaltranscoder.core.MediaInfo;
// 引入媒体探测器
import com.xu.universaltranscoder.core.MediaProbe;
// 引入质量档位
import com.xu.universaltranscoder.core.Quality;
// 引入内核对象
import com.xu.universaltranscoder.core.Toolchain;

// 引入 Swing 组件
import javax.swing.BorderFactory;
// 引入按钮
import javax.swing.JButton;
// 引入下拉框
import javax.swing.JComboBox;
// 引入文件选择器
import javax.swing.JFileChooser;
// 引入标签
import javax.swing.JLabel;
// 引入面板
import javax.swing.JPanel;
// 引入滚动面板
import javax.swing.JScrollPane;
// 引入文本域
import javax.swing.JTextArea;
// 引入文本框
import javax.swing.JTextField;
// 引入扩展名过滤器
import javax.swing.filechooser.FileNameExtensionFilter;

// 引入布局管理器
import java.awt.BorderLayout;
// 引入流式布局
import java.awt.FlowLayout;
// 引入网格布局，用于把三个区块纵向排列
import java.awt.GridLayout;
// 引入网格袋布局
import java.awt.GridBagConstraints;
// 引入网格袋布局管理器
import java.awt.GridBagLayout;
// 引入内边距
import java.awt.Insets;

// 引入文件工具类
import java.nio.file.Files;
// 引入路径
import java.nio.file.Path;
// 引入列表容器
import java.util.ArrayList;
// 引入列表接口
import java.util.List;

// 关键字 class：AdvancedPanel 提供视频抽帧、命令预览与诊断参考三项进阶能力
// 之所以把命令预览放在这里：用户可以直观看到内核将执行的参数列表，便于理解参数如何影响输出
public final class AdvancedPanel extends JPanel {

    // 应用外壳引用
    private final AppShell shell;

    // === 抽帧区块控件 ===
    // 源视频路径输入框
    private final JTextField frameInputField = new JTextField();
    // 输出目录输入框
    private final JTextField frameOutputField = new JTextField();
    // 抽帧数量输入框
    private final JTextField frameCountField = new JTextField("6");
    // 显式时间点输入框，逗号分隔，留空表示按数量均匀抽取
    private final JTextField frameTimesField = new JTextField();

    // === 命令预览区块控件 ===
    // 预览输入路径输入框
    private final JTextField previewInputField = new JTextField();
    // 预览目标格式下拉框
    private final JComboBox<String> previewFormatBox = new JComboBox<>();
    // 预览输出文本域
    private final JTextArea previewArea = new JTextArea(10, 80);

    // === 诊断参考区块控件 ===
    // 参考信息文本域
    private final JTextArea diagnosticsArea = new JTextArea(10, 80);

    // 构造方法：构建三个区块
    public AdvancedPanel(AppShell shell) {
        // 保存应用外壳引用
        this.shell = shell;
        // 使用边界布局
        setLayout(new BorderLayout(6, 6));
        // 设置四周留白
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // 创建纵向容器，三个区块各占一行
        JPanel container = new JPanel(new GridLayout(3, 1, 6, 6));
        // 加入抽帧区块
        container.add(buildFrameSection());
        // 加入命令预览区块
        container.add(buildPreviewSection());
        // 加入诊断参考区块
        container.add(buildDiagnosticsSection());
        // 把容器放入滚动面板，窗口较小时可滚动查看
        add(new JScrollPane(container), BorderLayout.CENTER);
        // 填充预览格式下拉框
        populatePreviewFormats();
        // 初始化诊断参考内容
        refreshDiagnostics();
    }

    // 解析逗号分隔的时间点文本，返回秒数数组
    private static double[] parseTimestamps(String text) {
        // 文本为空时返回空数组，表示改用数量模式
        if (text == null || text.trim().isEmpty()) {
            // 返回空数组
            return new double[0];
        }
        // 按逗号切分
        String[] parts = text.split(",");
        // 使用列表收集有效时间点
        List<Double> values = new ArrayList<>();
        // 逐个解析
        for (String part : parts) {
            // 去掉空白
            String trimmed = part.trim();
            // 空片段跳过
            if (trimmed.isEmpty()) {
                // 继续处理下一个片段
                continue;
            }
            // 捕获解析异常
            try {
                // 解析为双精度秒数
                double value = Double.parseDouble(trimmed);
                // 负值归零，避免非法定位
                values.add(Math.max(value, 0d));
            } catch (NumberFormatException ex) {
                // 非法片段跳过，保证整体解析仍然可用
            }
        }
        // 把列表转成数组
        double[] result = new double[values.size()];
        // 逐个复制数值
        for (int i = 0; i < values.size(); i++) {
            // 写入数组
            result[i] = values.get(i);
        }
        // 返回数组
        return result;
    }

    // 解析抽帧数量，非法时返回默认值并做上下限钳制
    private static int parseCount(String text, int fallback) {
        // 空值时返回默认值
        if (text == null || text.trim().isEmpty()) {
            // 返回默认值
            return fallback;
        }
        // 捕获解析异常
        try {
            // 解析整数
            int value = Integer.parseInt(text.trim());
            // 数量钳制在 1 至 60 之间，避免生成过多文件
            return Math.min(Math.max(value, 1), 60);
        } catch (NumberFormatException ex) {
            // 非法值返回默认值
            return fallback;
        }
    }

    // 构建抽帧区块
    private JPanel buildFrameSection() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        // 设置标题边框
        panel.setBorder(BorderFactory.createTitledBorder("Video frame extraction"));
        // 表单使用网格袋布局
        JPanel form = new JPanel(new GridBagLayout());
        // 第 0 行为源视频
        addRow(form, 0, "Video file:", frameInputField, createBrowseButton(frameInputField, false, "Video files", "mp4", "mkv", "mov", "avi", "webm"));
        // 第 1 行为输出目录
        addRow(form, 1, "Output folder:", frameOutputField, createBrowseButton(frameOutputField, true, null));
        // 第 2 行为抽帧数量
        addRow(form, 2, "Frame count:", frameCountField, null);
        // 第 3 行为显式时间点
        addRow(form, 3, "Timestamps (s, comma):", frameTimesField, null);
        // 把表单放到中央
        panel.add(form, BorderLayout.CENTER);
        // 底部动作条
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建开始按钮
        JButton startButton = new JButton("Extract Frames");
        // 绑定开始动作
        startButton.addActionListener(event -> startFrameExtraction());
        // 加入开始按钮
        actions.add(startButton);
        // 把动作条放到南部
        panel.add(actions, BorderLayout.SOUTH);
        // 返回区块
        return panel;
    }

    // 构建命令预览区块
    private JPanel buildPreviewSection() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        // 设置标题边框
        panel.setBorder(BorderFactory.createTitledBorder("Command preview"));
        // 表单使用网格袋布局
        JPanel form = new JPanel(new GridBagLayout());
        // 第 0 行为输入文件
        addRow(form, 0, "Input file:", previewInputField, createBrowseButton(previewInputField, false, "Media files", "mp4", "mkv", "mov", "avi", "webm", "flv", "mp3", "m4a", "wav", "flac", "png", "jpg"));
        // 第 1 行为目标格式
        addRow(form, 1, "Target format:", previewFormatBox, null);
        // 预览文本域设为只读并自动换行
        previewArea.setEditable(false);
        // 开启自动换行，避免长命令撑出横向滚动条
        previewArea.setLineWrap(true);
        // 把预览文本域放入滚动面板
        JScrollPane previewScroll = new JScrollPane(previewArea);
        // 把表单放到北部
        panel.add(form, BorderLayout.NORTH);
        // 把预览区域放到中央
        panel.add(previewScroll, BorderLayout.CENTER);
        // 底部动作条
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建预览按钮
        JButton previewButton = new JButton("Build Command");
        // 绑定预览动作
        previewButton.addActionListener(event -> buildPreview());
        // 加入预览按钮
        actions.add(previewButton);
        // 把动作条放到南部
        panel.add(actions, BorderLayout.SOUTH);
        // 返回区块
        return panel;
    }

    // 构建诊断参考区块
    private JPanel buildDiagnosticsSection() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        // 设置标题边框
        panel.setBorder(BorderFactory.createTitledBorder("Core diagnostics reference"));
        // 参考文本域设为只读并自动换行
        diagnosticsArea.setEditable(false);
        // 开启自动换行
        diagnosticsArea.setLineWrap(true);
        // 把参考文本域放入滚动面板
        panel.add(new JScrollPane(diagnosticsArea), BorderLayout.CENTER);
        // 底部动作条
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建刷新按钮
        JButton refreshButton = new JButton("Re-probe Core");
        // 绑定刷新动作
        refreshButton.addActionListener(event -> refreshDiagnostics());
        // 加入刷新按钮
        actions.add(refreshButton);
        // 把动作条放到南部
        panel.add(actions, BorderLayout.SOUTH);
        // 返回区块
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
        labelConstraints.insets = new Insets(3, 4, 3, 6);
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
        mainConstraints.insets = new Insets(3, 0, 3, 6);
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
            extraConstraints.insets = new Insets(3, 0, 3, 4);
            // 加入附加控件
            panel.add(extra, extraConstraints);
        }
    }

    // 创建浏览按钮，目录模式与文件模式共用
    private JButton createBrowseButton(JTextField target, boolean directory, String description, String... extensions) {
        // 创建按钮
        JButton button = new JButton(directory ? "Browse..." : "Choose...");
        // 绑定动作
        button.addActionListener(event -> {
            // 创建文件选择器
            JFileChooser chooser = new JFileChooser();
            // 目录模式只允许选择目录
            if (directory) {
                // 设置选择模式
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else {
                // 文件模式只允许选择文件
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                // 设置扩展名过滤器
                chooser.setFileFilter(new FileNameExtensionFilter(description == null ? "All files" : description, extensions));
            }
            // 弹出对话框
            int result = chooser.showOpenDialog(this);
            // 用户确认后写回输入框
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                // 写入选中路径
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        // 返回按钮
        return button;
    }

    // 填充预览目标格式下拉框，按内核能力过滤
    private void populatePreviewFormats() {
        // 清空已有条目
        previewFormatBox.removeAllItems();
        // 逐个视频格式判断内核支持
        for (String extension : FormatTables.videoTargets()) {
            // 换算封装名
            String muxer = FormatTables.videoMuxer(extension);
            // 内核支持时加入下拉框
            if (shell.toolchain().hasFormat(muxer)) {
                // 加入条目
                previewFormatBox.addItem(extension);
            }
        }
        // 无可用格式时兜底加入 mp4
        if (previewFormatBox.getItemCount() == 0) {
            // 加入默认格式
            previewFormatBox.addItem("mp4");
        }
        // 默认选中 mp4
        previewFormatBox.setSelectedItem("mp4");
    }

    // 开始抽帧任务
    private void startFrameExtraction() {
        // 取源视频路径
        String input = frameInputField.getText().trim();
        // 源文件不存在时提示
        if (input.isEmpty() || !Files.isRegularFile(Path.of(input))) {
            // 输出日志提示
            shell.log("Choose an existing video file first.");
            // 结束处理
            return;
        }
        // 取输出目录
        String output = frameOutputField.getText().trim();
        // 输出目录为空时提示
        if (output.isEmpty()) {
            // 输出日志提示
            shell.log("Choose an output folder first.");
            // 结束处理
            return;
        }
        // 探测源视频以获取时长
        MediaInfo info = MediaProbe.probe(Path.of(input));
        // 解析显式时间点文本
        double[] timestamps = parseTimestamps(frameTimesField.getText());
        // 未提供显式时间点时按数量均匀生成
        if (timestamps.length == 0) {
            // 解析抽帧数量，非法值回落到 6
            int count = parseCount(frameCountField.getText(), 6);
            // 按时长与数量生成均匀时间点
            timestamps = FrameExtractor.evenlySpacedTimestamps(info.durationSeconds(), count);
        }
        // 取输入文件主名作为输出前缀
        String baseName = Path.of(input).getFileName().toString();
        // 去掉扩展名得到纯主名
        int dot = baseName.lastIndexOf('.');
        // 存在扩展名时截断
        if (dot > 0) {
            // 截取主名
            baseName = baseName.substring(0, dot);
        }
        // 创建抽帧任务并入队
        shell.enqueue(DocumentTasks.newFrameExtractJob(Path.of(input), Path.of(output), timestamps, baseName));
        // 输出入队日志
        shell.log("Queued frame extraction task (" + timestamps.length + " frame(s)).");
    }

    // 构建命令预览并展示到文本域
    private void buildPreview() {
        // 取输入文件路径
        String input = previewInputField.getText().trim();
        // 源文件不存在时提示
        if (input.isEmpty() || !Files.isRegularFile(Path.of(input))) {
            // 把提示写入预览区域，因为该文案会显示给用户
            previewArea.setText("Choose an existing media file first.");
            // 结束处理
            return;
        }
        // 探测媒体信息
        MediaInfo info = MediaProbe.probe(Path.of(input));
        // 取目标格式
        Object format = previewFormatBox.getSelectedItem();
        // 目标格式缺失时提示
        if (format == null) {
            // 写入提示文本
            previewArea.setText("No target format is available.");
            // 结束处理
            return;
        }
        // 构造预览用的请求，使用默认参数以便展示最简命令
        ConversionRequest request = new ConversionRequest()
                // 输入文件
                .input(Path.of(input))
                // 视频类别
                .category(ConversionRequest.Category.VIDEO)
                // 目标格式
                .targetExtension(format.toString())
                // 质量中档
                .quality(Quality.MEDIUM)
                // 输出目录与输入相同
                .outputDirectory(Path.of(input).getParent())
                // 保留元数据
                .metadataMode("keep")
                // 使用设置中的硬件加速开关
                .hardwareAcceleration(shell.settings().hardwareAcceleration());
        // 构建命令
        CommandBuilder.BuiltCommand command = CommandBuilder.build(request, info);
        // 组装预览文本：先写探测摘要，再写命令与输出路径
        StringBuilder builder = new StringBuilder();
        // 追加探测摘要
        builder.append("Source: ").append(info.describe()).append(System.lineSeparator());
        // 追加目标封装名，帮助用户理解格式换算结果
        builder.append("Muxer: ").append(command.muxer()).append(" | video encoder: ").append(command.videoEncoder())
                // 追加音频编码器
                .append(" | audio encoder: ").append(command.audioEncoder()).append(System.lineSeparator());
        // 追加输出路径
        builder.append("Output: ").append(command.outputPath()).append(System.lineSeparator());
        // 追加分隔线
        builder.append("Command:").append(System.lineSeparator());
        // 逐个参数换行展示，便于逐项核对
        for (String argument : command.arguments()) {
            // 追加参数行
            builder.append("  ").append(argument).append(System.lineSeparator());
        }
        // 把结果写入预览区域
        previewArea.setText(builder.toString());
        // 把光标移到开头，方便用户从头阅读
        previewArea.setCaretPosition(0);
    }

    // 刷新诊断参考内容：内核能力、硬件检测与错误类别清单
    private void refreshDiagnostics() {
        // 重新定位并探测内核，把最新结果缓存到内核单例
        Toolchain toolchain = Toolchain.bootstrap(shell.settings().customFfmpegPath());
        // 组装参考文本
        StringBuilder builder = new StringBuilder();
        // 追加内核概述
        builder.append("Core: ").append(toolchain.describe()).append(System.lineSeparator());
        // 追加 ffmpeg 版本
        builder.append("ffmpeg: ").append(toolchain.ffmpegVersion()).append(System.lineSeparator());
        // 追加 ffprobe 版本
        builder.append("ffprobe: ").append(toolchain.ffprobeVersion()).append(System.lineSeparator());
        // 追加硬件加速检测结果
        builder.append("Hardware: ").append(HardwareAccel.describe()).append(System.lineSeparator());
        // 追加解码加速方式
        builder.append("Decode acceleration: ").append(HardwareAccel.pickDecoderAcceleration()).append(System.lineSeparator());
        // 追加编码器种类数量
        builder.append("Encoders: ").append(toolchain.encoders().size()).append(" kinds available.").append(System.lineSeparator());
        // 追加封装数量
        builder.append("Muxers: ").append(toolchain.muxFormats().size()).append(" kinds available.").append(System.lineSeparator());
        // 追加分隔线
        builder.append(System.lineSeparator()).append("Error categories handled by the built-in classifier:").append(System.lineSeparator());
        // 逐个列出错误类别名称，便于用户对照日志判断问题
        for (ErrorClassifier.Category category : ErrorClassifier.Category.values()) {
            // 追加类别名称
            builder.append("  - ").append(category.name()).append(System.lineSeparator());
        }
        // 把结果写入参考区域
        diagnosticsArea.setText(builder.toString());
        // 把光标移到开头
        diagnosticsArea.setCaretPosition(0);
    }

    // 以编程方式设置抽帧输入，供冒烟测试使用
    public void setFrameInputForTest(Path video, Path outputDirectory) {
        // 写入源视频路径
        frameInputField.setText(video.toString());
        // 写入输出目录
        frameOutputField.setText(outputDirectory.toString());
    }

    // 以编程方式设置预览输入，供冒烟测试使用
    public void setPreviewInputForTest(Path file) {
        // 写入预览输入路径
        previewInputField.setText(file.toString());
    }

    // 获取预览文本域，供冒烟测试检查内容
    public JTextArea previewArea() {
        // 返回预览文本域
        return previewArea;
    }

    // 公开构建预览方法，供冒烟测试不点击按钮也能触发预览
    public void buildPreviewForTest() {
        // 直接调用内部预览逻辑
        buildPreview();
    }
}
