package com.xu.universaltranscoder.ui;

// 引入核心层的各项类型，面板需要它们来构造任务与请求

import com.xu.universaltranscoder.core.ConversionRequest;
// 引入格式映射表，用于把界面格式与内核封装名对应起来
import com.xu.universaltranscoder.core.FormatTables;
// 引入任务类型，用于构造队列任务
import com.xu.universaltranscoder.core.Job;
// 引入媒体任务实现，负责真实转码与自动补救
import com.xu.universaltranscoder.core.MediaJobTask;
// 引入媒体信息，用于展示探测结果
import com.xu.universaltranscoder.core.MediaInfo;
// 引入媒体探测器，用于在添加文件时探测媒体信息
import com.xu.universaltranscoder.core.MediaProbe;
// 引入预设与预设库，实现一键套用
import com.xu.universaltranscoder.core.Preset;
// 引入预设库静态方法
import com.xu.universaltranscoder.core.PresetLibrary;
// 引入质量档位枚举
import com.xu.universaltranscoder.core.Quality;
// 引入内核对象，用于过滤可用格式与编码器
import com.xu.universaltranscoder.core.Toolchain;

// 引入 Swing 各类组件
import javax.swing.BorderFactory;
// 引入按钮组件
import javax.swing.JButton;
// 引入复选框组件
import javax.swing.JCheckBox;
// 引入下拉框组件
import javax.swing.JComboBox;
// 引入文件选择器
import javax.swing.JFileChooser;
// 引入文件选择过滤器的扩展名过滤器
import javax.swing.filechooser.FileNameExtensionFilter;
// 引入标签组件
import javax.swing.JLabel;
// 引入列表组件
import javax.swing.JList;
// 引入面板组件
import javax.swing.JPanel;
// 引入进度条组件
import javax.swing.JProgressBar;
// 引入滚动面板组件
import javax.swing.JScrollPane;
// 引入分割面板，用于左右与上下布局
import javax.swing.JSplitPane;
// 引入文本域，用于日志输出
import javax.swing.JTextArea;
// 引入文本框，用于单行输入
import javax.swing.JTextField;
// 引入默认列表模型，用于维护文件清单
import javax.swing.DefaultListModel;

// 引入布局管理器
import java.awt.BorderLayout;
// 引入卡片布局，用于参数区域的分组
import java.awt.FlowLayout;
// 引入网格袋布局，用于表单式参数排列
import java.awt.GridBagConstraints;
// 引入网格袋布局管理器
import java.awt.GridBagLayout;
// 引入内边距类
import java.awt.Insets;
// 引入维度类，设置控件首选尺寸
import java.awt.Dimension;

// 引入文件路径
import java.nio.file.Files;
// 引入路径接口
import java.nio.file.Path;
// 引入列表容器
import java.util.ArrayList;
// 引入哈希集合，记录本面板创建的任务编号
import java.util.HashSet;
// 引入列表接口
import java.util.List;
// 引入集合接口，作为任务编号集合类型
import java.util.Set;

// 关键字 class：MediaPanel 是视频、音频、图片三个转换页的共用实现，通过类别参数区分行为
// 之所以共用：三个类别的绝大部分参数控件完全一致，共用一套实现可以避免三份高度重复的界面代码
public final class MediaPanel extends JPanel {

    // 面板所属类别，决定参数控件的可见性与可选格式范围
    private final ConversionRequest.Category category;
    // 应用外壳，用于入队任务、写日志与读取设置
    private final AppShell shell;
    // 已添加文件的路径清单，与列表模型一一对应
    private final List<Path> files = new ArrayList<>();
    // 已探测到的媒体信息缓存，避免重复探测
    private final java.util.Map<Path, MediaInfo> mediaCache = new java.util.HashMap<>();
    // 文件列表模型，界面展示文件名与探测摘要
    private final DefaultListModel<String> fileModel = new DefaultListModel<>();
    // 文件列表控件
    private final JList<String> fileList = new JList<>(fileModel);
    // 本面板创建的任务编号集合，用于筛选出属于自己的任务更新
    private final Set<Integer> myJobIds = new HashSet<>();
    // 本面板创建的任务对象引用，用于汇总进度
    private final List<Job> myJobs = new ArrayList<>();

    // 目标格式下拉框
    private final JComboBox<String> targetFormatBox = new JComboBox<>();
    // 质量档位下拉框
    private final JComboBox<String> qualityBox = new JComboBox<>(new String[]{"Low", "Medium", "High", "Very High", "Lossless"});
    // 输出目录输入框
    private final JTextField outputDirField = new JTextField();
    // 命名模式下拉框，使用英文文案
    private final JComboBox<String> namingModeBox = new JComboBox<>(new String[]{"Replace extension", "Keep name and add suffix", "Custom template"});
    // 命名模板输入框
    private final JTextField templateField = new JTextField("{name}_{target}");
    // 预设下拉框
    private final JComboBox<String> presetBox = new JComboBox<>();
    // 视频编码器下拉框
    private final JComboBox<String> videoEncoderBox = new JComboBox<>();
    // 音频编码器下拉框
    private final JComboBox<String> audioEncoderBox = new JComboBox<>();
    // 视频码率输入框，空表示自动
    private final JTextField videoBitRateField = new JTextField();
    // 音频码率输入框，空表示自动
    private final JTextField audioBitRateField = new JTextField();
    // 目标宽度输入框
    private final JTextField widthField = new JTextField();
    // 目标高度输入框
    private final JTextField heightField = new JTextField();
    // 目标帧率输入框
    private final JTextField frameRateField = new JTextField();
    // 裁剪起点横坐标输入框
    private final JTextField cropXField = new JTextField();
    // 裁剪起点纵坐标输入框
    private final JTextField cropYField = new JTextField();
    // 裁剪宽度输入框
    private final JTextField cropWidthField = new JTextField();
    // 裁剪高度输入框
    private final JTextField cropHeightField = new JTextField();
    // 旋转角度下拉框
    private final JComboBox<String> rotateBox = new JComboBox<>(new String[]{"0", "90", "180", "270"});
    // 水平镜像复选框
    private final JCheckBox mirrorHBox = new JCheckBox("Mirror horizontally");
    // 垂直镜像复选框
    private final JCheckBox mirrorVBox = new JCheckBox("Mirror vertically");
    // 水印文本输入框
    private final JTextField watermarkField = new JTextField();
    // 水印位置下拉框
    private final JComboBox<String> watermarkPositionBox = new JComboBox<>(new String[]{"left-top", "right-top", "left-bottom", "right-bottom", "center"});
    // 水印字号输入框
    private final JTextField watermarkSizeField = new JTextField("24");
    // 水印不透明度输入框
    private final JTextField watermarkOpacityField = new JTextField("0.6");
    // 字幕文件路径输入框
    private final JTextField subtitleField = new JTextField();
    // 字幕烧录复选框
    private final JCheckBox burnSubtitleBox = new JCheckBox("Burn subtitles into video");
    // 速度倍率输入框
    private final JTextField speedField = new JTextField("1.0");
    // 元数据模式下拉框
    private final JComboBox<String> metadataBox = new JComboBox<>(new String[]{"keep", "strip"});

    // 进度条控件
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    // 状态标签，展示当前批次运行情况
    private final JLabel statusLabel = new JLabel("Ready");
    // 日志文本域，展示内核输出与诊断结论
    private final JTextArea logArea = new JTextArea(8, 60);

    // 构造方法：按类别构建界面
    public MediaPanel(AppShell shell, ConversionRequest.Category category) {
        // 保存应用外壳引用
        this.shell = shell;
        // 保存类别
        this.category = category;
        // 使用边界布局组织整页结构
        setLayout(new BorderLayout(6, 6));
        // 设置面板四周留白，避免控件贴边
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // 添加顶部操作条
        add(buildTopBar(), BorderLayout.NORTH);
        // 构建参数区与文件区
        JPanel middle = buildMiddleSection();
        // 构建底部进度与日志区
        JPanel bottom = buildBottomSection();
        // 使用垂直分割面板把中部与底部组合，用户可拖动分界调整空间
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, middle, bottom);
        // 设置分割条位置，让参数区占主要空间
        vertical.setResizeWeight(0.68d);
        // 关闭边框绘制，使界面更简洁
        vertical.setBorder(null);
        // 把分割面板放入中央区域
        add(vertical, BorderLayout.CENTER);
        // 填充可选格式与编码器
        populateFormats();
        // 填充预设下拉框
        populatePresets();
        // 初始化参数控件的默认值
        initializeDefaults();
        // 注册任务监听器，收到属于本面板的任务更新时刷新进度与状态
        shell.registerJobListener(this::onJobChanged);
        // 注册日志接收器，让主窗口把日志写入本页的日志区域
        registerLogSink();
        // 更新状态标签的内核提示
        refreshCoreHint();
    }

    // 解析整数字符串，空值与非法值返回默认值
    private static int parseInt(String text, int fallback) {
        // 空值时返回默认值
        if (text == null || text.trim().isEmpty()) {
            // 返回默认值
            return fallback;
        }
        // 捕获解析异常
        try {
            // 返回解析结果
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            // 非法值返回默认值，避免误输入导致任务失败
            return fallback;
        }
    }

    // 解析双精度字符串，空值与非法值返回默认值
    private static double parseDouble(String text, double fallback) {
        // 空值时返回默认值
        if (text == null || text.trim().isEmpty()) {
            // 返回默认值
            return fallback;
        }
        // 捕获解析异常
        try {
            // 返回解析结果
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            // 非法值返回默认值
            return fallback;
        }
    }

    // 把双精度数值格式化为最简文本
    private static String formatNumber(double value) {
        // 整数时直接输出整数形式
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            // 返回整数文本
            return String.valueOf((long) value);
        }
        // 其它情况保留三位小数
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    // 构建顶部操作条：添加文件、移除、清空、套用预设
    private JPanel buildTopBar() {
        // 使用流式布局左对齐摆放按钮
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建添加文件按钮
        JButton addButton = new JButton("Add Files...");
        // 绑定添加文件动作
        addButton.addActionListener(event -> chooseFiles());
        // 创建移除选中按钮
        JButton removeButton = new JButton("Remove Selected");
        // 绑定移除动作
        removeButton.addActionListener(event -> removeSelected());
        // 创建清空按钮
        JButton clearButton = new JButton("Clear List");
        // 绑定清空动作
        clearButton.addActionListener(event -> clearFiles());
        // 创建预设标签
        JLabel presetLabel = new JLabel("Preset:");
        // 创建套用预设按钮
        JButton applyPresetButton = new JButton("Apply");
        // 绑定套用动作
        applyPresetButton.addActionListener(event -> applySelectedPreset());
        // 创建开始转换按钮，这是本页的主操作
        JButton startButton = new JButton("Start");
        // 绑定开始动作，把当前参数编译成任务并入队
        startButton.addActionListener(event -> startConversion());
        // 依次加入控件
        panel.add(addButton);
        // 加入移除按钮
        panel.add(removeButton);
        // 加入清空按钮
        panel.add(clearButton);
        // 加入预设标签
        panel.add(presetLabel);
        // 设置预设下拉框的首选宽度
        presetBox.setPreferredSize(new Dimension(220, 24));
        // 加入预设下拉框
        panel.add(presetBox);
        // 加入套用按钮
        panel.add(applyPresetButton);
        // 加入开始按钮，放在操作条最右侧
        panel.add(startButton);
        // 返回操作条
        return panel;
    }

    // 构建中部区域：左侧文件列表，右侧参数表单
    private JPanel buildMiddleSection() {
        // 文件列表放入滚动面板
        JScrollPane fileScroll = new JScrollPane(fileList);
        // 给滚动面板加标题边框
        fileScroll.setBorder(BorderFactory.createTitledBorder("Source files"));
        // 设置文件列表首选宽度，保证左侧不占过多空间
        fileScroll.setPreferredSize(new Dimension(360, 320));
        // 构建参数表单
        JScrollPane paramScroll = new JScrollPane(buildParameterForm());
        // 给参数区加标题边框
        paramScroll.setBorder(BorderFactory.createTitledBorder("Conversion parameters"));
        // 使用水平分割面板组合左右区域
        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileScroll, paramScroll);
        // 设置分割条位置，参数区获得更多空间
        horizontal.setResizeWeight(0.32d);
        // 关闭边框绘制
        horizontal.setBorder(null);
        // 用面板包裹分割面板以便返回 JPanel 类型
        JPanel wrapper = new JPanel(new BorderLayout());
        // 把分割面板放入包裹面板中央
        wrapper.add(horizontal, BorderLayout.CENTER);
        // 返回包裹面板
        return wrapper;
    }

    // 构建参数表单，按类别决定包含哪些控件
    private JPanel buildParameterForm() {
        // 使用网格袋布局实现标签与输入的整齐对齐
        JPanel panel = new JPanel(new GridBagLayout());
        // 行号从 0 开始
        int row = 0;
        // 输出目录行，三个类别共用
        row = addRow(panel, row, "Output folder:", outputDirField, createBrowseButton(outputDirField, true));
        // 目标格式行，三个类别共用
        row = addRow(panel, row, "Target format:", targetFormatBox, null);
        // 质量档位行，三个类别共用
        row = addRow(panel, row, "Quality:", qualityBox, null);
        // 命名模式行，三个类别共用
        row = addRow(panel, row, "Naming:", namingModeBox, null);
        // 命名模板行，仅在自定义模板模式下有意义
        row = addRow(panel, row, "Name template:", templateField, null);
        // 音频类别不需要视频相关参数
        if (category != ConversionRequest.Category.VIDEO) {
            // 音频编码器行
            row = addRow(panel, row, "Audio encoder:", audioEncoderBox, null);
            // 音频码率行
            row = addRow(panel, row, "Audio bitrate (kbps):", audioBitRateField, null);
        }
        // 视频类别需要全部视频参数
        if (category == ConversionRequest.Category.VIDEO) {
            // 视频编码器行
            row = addRow(panel, row, "Video encoder:", videoEncoderBox, null);
            // 音频编码器行
            row = addRow(panel, row, "Audio encoder:", audioEncoderBox, null);
            // 视频码率行
            row = addRow(panel, row, "Video bitrate (kbps):", videoBitRateField, null);
            // 音频码率行
            row = addRow(panel, row, "Audio bitrate (kbps):", audioBitRateField, null);
            // 目标宽度行
            row = addRow(panel, row, "Width (px):", widthField, null);
            // 目标高度行
            row = addRow(panel, row, "Height (px):", heightField, null);
            // 目标帧率行
            row = addRow(panel, row, "Frame rate (fps):", frameRateField, null);
            // 裁剪区域行，四个输入框横向排列
            row = addRow(panel, row, "Crop x/y/w/h:", buildCropPanel(), null);
            // 旋转行
            row = addRow(panel, row, "Rotate (deg):", rotateBox, null);
            // 镜像行
            row = addRow(panel, row, "Mirror:", buildMirrorPanel(), null);
            // 水印文本行
            row = addRow(panel, row, "Watermark text:", watermarkField, null);
            // 水印位置行
            row = addRow(panel, row, "Watermark position:", watermarkPositionBox, null);
            // 水印字号行
            row = addRow(panel, row, "Watermark font size:", watermarkSizeField, null);
            // 水印不透明度行
            row = addRow(panel, row, "Watermark opacity:", watermarkOpacityField, null);
            // 字幕文件行
            row = addRow(panel, row, "Subtitle file:", subtitleField, createBrowseButton(subtitleField, false));
            // 字幕烧录行
            row = addRow(panel, row, "Subtitles:", burnSubtitleBox, null);
            // 速度倍率行
            row = addRow(panel, row, "Speed factor:", speedField, null);
            // 元数据行
            row = addRow(panel, row, "Metadata:", metadataBox, null);
        }
        // 图片类别只需要尺寸参数，用于缩放图片
        if (category == ConversionRequest.Category.IMAGE) {
            // 目标宽度行
            row = addRow(panel, row, "Width (px, 0 = keep):", widthField, null);
            // 目标高度行
            row = addRow(panel, row, "Height (px, 0 = keep):", heightField, null);
            // 元数据行
            row = addRow(panel, row, "Metadata:", metadataBox, null);
        }
        // 追加一个可伸缩的空白行，把表单内容顶到上方
        GridBagConstraints filler = new GridBagConstraints();
        // 占满整行
        filler.gridx = 0;
        // 放在最后一行的下一行
        filler.gridy = row;
        // 横向跨越三列
        filler.gridwidth = 3;
        // 纵向与横向都可拉伸，吸收多余空间
        filler.weightx = 1d;
        // 纵向权重设为 1，使空白行吸收多余高度
        filler.weighty = 1d;
        // 允许填充
        filler.fill = GridBagConstraints.BOTH;
        // 加入空白组件
        panel.add(new JLabel(""), filler);
        // 返回表单面板
        return panel;
    }

    // 构建裁剪参数面板
    private JPanel buildCropPanel() {
        // 使用流式布局横向排列四个输入框
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        // 设置各输入框的宽度，避免输入数字时空间不足
        cropXField.setPreferredSize(new Dimension(60, 24));
        // 设置纵坐标输入框宽度
        cropYField.setPreferredSize(new Dimension(60, 24));
        // 设置宽度输入框宽度
        cropWidthField.setPreferredSize(new Dimension(60, 24));
        // 设置高度输入框宽度
        cropHeightField.setPreferredSize(new Dimension(60, 24));
        // 加入横坐标输入框
        panel.add(cropXField);
        // 加入纵坐标输入框
        panel.add(cropYField);
        // 加入宽度输入框
        panel.add(cropWidthField);
        // 加入高度输入框
        panel.add(cropHeightField);
        // 返回裁剪面板
        return panel;
    }

    // 构建镜像复选框面板
    private JPanel buildMirrorPanel() {
        // 使用流式布局横向排列两个复选框
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        // 加入水平镜像复选框
        panel.add(mirrorHBox);
        // 加入垂直镜像复选框
        panel.add(mirrorVBox);
        // 返回镜像面板
        return panel;
    }

    // 构建底部区域：进度条、状态标签与日志文本域
    private JPanel buildBottomSection() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        // 创建进度与状态容器
        JPanel progressPanel = new JPanel(new BorderLayout(6, 4));
        // 进度条显示百分比文本
        progressBar.setStringPainted(true);
        // 进度条初始值 0
        progressBar.setValue(0);
        // 把进度条放到左侧并占主要宽度
        progressPanel.add(progressBar, BorderLayout.CENTER);
        // 状态标签放到右侧
        progressPanel.add(statusLabel, BorderLayout.EAST);
        // 把进度容器放到顶部
        panel.add(progressPanel, BorderLayout.NORTH);
        // 日志文本域设为只读，用户只能查看不能编辑
        logArea.setEditable(false);
        // 日志文本域自动换行，避免长行撑出横向滚动条
        logArea.setLineWrap(true);
        // 换行时保持整词不被打断，提升可读性
        logArea.setWrapStyleWord(true);
        // 把日志区域放入滚动面板并加标题
        JScrollPane logScroll = new JScrollPane(logArea);
        // 设置标题边框
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        // 把日志区域放到中央
        panel.add(logScroll, BorderLayout.CENTER);
        // 返回底部面板
        return panel;
    }

    // 向表单追加一行：标签占第一列，主控件占第二列，附加控件占第三列
    private int addRow(JPanel panel, int row, String label, javax.swing.JComponent main, javax.swing.JComponent extra) {
        // 创建标签控件
        JLabel labelComponent = new JLabel(label);
        // 构造标签的布局约束
        GridBagConstraints labelConstraints = new GridBagConstraints();
        // 放在第一列
        labelConstraints.gridx = 0;
        // 放在指定行
        labelConstraints.gridy = row;
        // 靠西对齐
        labelConstraints.anchor = GridBagConstraints.WEST;
        // 设置上下内边距，行间距均匀
        labelConstraints.insets = new Insets(3, 4, 3, 6);
        // 加入标签
        panel.add(labelComponent, labelConstraints);
        // 构造主控件的布局约束
        GridBagConstraints mainConstraints = new GridBagConstraints();
        // 放在第二列
        mainConstraints.gridx = 1;
        // 放在指定行
        mainConstraints.gridy = row;
        // 横向填充，输入框占满可用宽度
        mainConstraints.fill = GridBagConstraints.HORIZONTAL;
        // 横向权重设为 1，让输入框吸收多余宽度
        mainConstraints.weightx = 1d;
        // 设置内边距
        mainConstraints.insets = new Insets(3, 0, 3, 6);
        // 设置主控件的最小高度，避免在部分主题下过矮
        main.setPreferredSize(new Dimension(main.getPreferredSize().width, 24));
        // 加入主控件
        panel.add(main, mainConstraints);
        // 附加控件存在时才占第三列
        if (extra != null) {
            // 构造附加控件的布局约束
            GridBagConstraints extraConstraints = new GridBagConstraints();
            // 放在第三列
            extraConstraints.gridx = 2;
            // 放在指定行
            extraConstraints.gridy = row;
            // 靠东对齐，紧贴右侧
            extraConstraints.anchor = GridBagConstraints.EAST;
            // 设置内边距
            extraConstraints.insets = new Insets(3, 0, 3, 4);
            // 加入附加控件
            panel.add(extra, extraConstraints);
        } else {
            // 没有附加控件时补一个空标签，保持列宽稳定
            GridBagConstraints spacerConstraints = new GridBagConstraints();
            // 放在第三列
            spacerConstraints.gridx = 2;
            // 放在指定行
            spacerConstraints.gridy = row;
            // 加入空标签以保持列宽稳定
            panel.add(new JLabel(""), spacerConstraints);
        }
        // 返回下一行的行号
        return row + 1;
    }

    // 创建一个浏览按钮，点击后弹出文件或目录选择器并把结果写入目标输入框
    private JButton createBrowseButton(JTextField target, boolean directory) {
        // 创建按钮，目录选择与文件选择使用不同文案
        JButton button = new JButton(directory ? "Browse..." : "Choose...");
        // 绑定动作：打开选择器并把选中路径写入输入框
        button.addActionListener(event -> {
            // 创建文件选择器
            JFileChooser chooser = new JFileChooser();
            // 目录模式时只允许选择目录
            if (directory) {
                // 设置只能选择目录
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else {
                // 文件模式时只允许选择文件
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                // 限制为文本与字幕文件，避免用户选到无关文件
                chooser.setFileFilter(new FileNameExtensionFilter("Subtitle files (*.srt, *.ass, *.ssa, *.vtt)", "srt", "ass", "ssa", "vtt"));
            }
            // 当前输入框内容作为初始目录或文件
            String current = target.getText().trim();
            // 当前内容非空时设置初始选择目标
            if (!current.isEmpty()) {
                // 构造初始路径
                Path initial = Path.of(current);
                // 目录存在时设为当前目录
                if (Files.isDirectory(initial)) {
                    // 设置当前目录
                    chooser.setCurrentDirectory(initial.toFile());
                } else if (initial.getParent() != null && Files.isDirectory(initial.getParent())) {
                    // 文件的父目录存在时设为当前目录
                    chooser.setCurrentDirectory(initial.getParent().toFile());
                }
            }
            // 弹出选择对话框
            int result = chooser.showOpenDialog(this);
            // 用户确认后才写回输入框
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                // 把选中路径写入输入框
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        // 返回按钮
        return button;
    }

    // 填充可选目标格式，按内核能力过滤
    private void populateFormats() {
        // 清空已有条目，避免重复构建时出现重复项
        targetFormatBox.removeAllItems();
        // 取得内核对象
        Toolchain toolchain = shell.toolchain();
        // 按类别取出候选格式集合
        java.util.Set<String> candidates = switch (category) {
            // 视频类别取视频格式集合
            case VIDEO -> FormatTables.videoTargets();
            // 音频类别取音频格式集合
            case AUDIO -> FormatTables.audioTargets();
            // 图片类别取图片格式集合
            case IMAGE -> FormatTables.imageTargets();
        };
        // 逐个候选判断内核是否支持
        for (String extension : candidates) {
            // 按类别换算出封装名
            String muxer = switch (category) {
                // 视频格式换算封装名
                case VIDEO -> FormatTables.videoMuxer(extension);
                // 音频格式换算封装名
                case AUDIO -> FormatTables.audioMuxer(extension);
                // 图片格式换算封装名
                case IMAGE -> FormatTables.imageMuxer(extension);
            };
            // 内核不可用时不做过滤，让用户仍能看到格式清单
            if (!toolchain.isAvailable() || toolchain.hasFormat(muxer)) {
                // 加入下拉框
                targetFormatBox.addItem(extension);
            }
        }
        // 条目为空时兜底加入一个默认格式，避免下拉框为空白
        if (targetFormatBox.getItemCount() == 0) {
            // 按类别加入默认格式
            targetFormatBox.addItem(category == ConversionRequest.Category.AUDIO ? "mp3"
                    // 图片类别默认 jpg
                    : category == ConversionRequest.Category.IMAGE ? "jpg" : "mp4");
        }
        // 选中第一项，保证界面有明确默认值
        targetFormatBox.setSelectedIndex(0);
    }

    // 填充可选编码器，仅视频与音频类别需要
    private void populateEncoders() {
        // 清空视频编码器下拉框
        videoEncoderBox.removeAllItems();
        // 自动项放在首位，作为默认选择
        videoEncoderBox.addItem("auto");
        // 清空音频编码器下拉框
        audioEncoderBox.removeAllItems();
        // 自动项放在首位
        audioEncoderBox.addItem("auto");
        // 取得内核对象
        Toolchain toolchain = shell.toolchain();
        // 视频类别按当前目标格式列出可用编码器候选
        if (category == ConversionRequest.Category.VIDEO) {
            // 取当前选中的目标格式
            Object selected = targetFormatBox.getSelectedItem();
            // 选中项存在时列出候选
            if (selected != null) {
                // 逐个候选编码器加入下拉框
                for (String encoder : FormatTables.videoEncoderCandidates(selected.toString())) {
                    // 内核支持时才加入候选
                    if (toolchain.hasEncoder(encoder)) {
                        // 加入视频编码器下拉框
                        videoEncoderBox.addItem(encoder);
                    }
                }
            }
        }
        // 视频、音频、图片以外的类别不需要音频编码器
        if (category != ConversionRequest.Category.IMAGE) {
            // 按目标格式列出音频编码器候选
            Object selected = targetFormatBox.getSelectedItem();
            // 选中项存在时列出候选
            if (selected != null) {
                // 按类别取音频候选链
                List<String> audioCandidates = category == ConversionRequest.Category.VIDEO
                        // 视频类别按封装名查音频候选
                        ? FormatTables.audioCandidatesByMuxer(FormatTables.videoMuxer(selected.toString()))
                        // 音频类别按封装名查音频候选
                        : FormatTables.audioEncoderCandidates(selected.toString());
                // 逐个候选加入下拉框
                for (String encoder : audioCandidates) {
                    // 内核支持时才加入
                    if (toolchain.hasEncoder(encoder)) {
                        // 加入音频编码器下拉框
                        audioEncoderBox.addItem(encoder);
                    }
                }
            }
        }
    }

    // 填充预设下拉框，仅列出与当前类别匹配的预设
    private void populatePresets() {
        // 清空已有条目
        presetBox.removeAllItems();
        // 遍历该类别下的全部预设
        for (Preset preset : PresetLibrary.byCategory(category)) {
            // 加入预设名称
            presetBox.addItem(preset.name());
        }
        // 有预设时默认选中第一项
        if (presetBox.getItemCount() > 0) {
            // 选中第一项
            presetBox.setSelectedIndex(0);
        }
    }

    // 初始化参数控件的默认值，来源为应用设置与内核能力
    private void initializeDefaults() {
        // 输出目录使用设置中的默认值
        outputDirField.setText(shell.settings().defaultOutputDir());
        // 质量档位使用设置中的默认值
        qualityBox.setSelectedItem(shell.settings().defaultQuality());
        // 命名模式按设置恢复
        String namingMode = shell.settings().namingMode();
        // 映射命名模式文本到下拉框选项
        if ("suffix".equals(namingMode)) {
            // 选择保留原名追后缀
            namingModeBox.setSelectedItem("Keep name and add suffix");
        } else if ("template".equals(namingMode)) {
            // 选择自定义模板
            namingModeBox.setSelectedItem("Custom template");
        } else {
            // 默认选择替换扩展名
            namingModeBox.setSelectedItem("Replace extension");
        }
        // 命名模板使用设置中的默认值
        templateField.setText(shell.settings().nameTemplate());
        // 元数据模式按设置恢复
        metadataBox.setSelectedItem(shell.settings().metadataMode());
        // 水印位置默认右下角
        watermarkPositionBox.setSelectedItem("right-bottom");
        // 填充编码器下拉框
        populateEncoders();
        // 目标格式变化时需要重新填充编码器候选，保证候选与封装匹配
        targetFormatBox.addActionListener(event -> populateEncoders());
    }

    // 刷新内核提示文本，内核不可用时在状态标签上给出明确指引
    private void refreshCoreHint() {
        // 取内核对象
        Toolchain toolchain = shell.toolchain();
        // 内核不可用时提示用户配置路径
        if (!toolchain.isAvailable()) {
            // 设置英文提示文本
            statusLabel.setText("FFmpeg core not found - set the path in Settings");
        } else {
            // 内核可用时显示就绪
            statusLabel.setText("Ready - " + toolchain.encoders().size() + " encoders available");
        }
    }

    // 注册日志接收器，把主窗口派发到本页的日志写入日志文本域
    private void registerLogSink() {
        // 仅在主窗口实现了日志注册能力时才注册
        if (shell instanceof MainWindow mainWindow) {
            // 注册接收器，把日志追加到文本域
            mainWindow.registerLogSink(this, line -> {
                // 追加日志行并换行
                logArea.append(line + System.lineSeparator());
                // 把光标移动到末尾，保证始终看到最新日志
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    // 弹出文件选择器并把用户选中的文件加入清单
    private void chooseFiles() {
        // 创建文件选择器
        JFileChooser chooser = new JFileChooser();
        // 允许多选，满足批量转换需求
        chooser.setMultiSelectionEnabled(true);
        // 按类别设置扩展名过滤器
        chooser.setFileFilter(new FileNameExtensionFilter(categoryFilterDescription(), categoryExtensions()));
        // 输出目录或首个已添加文件的目录作为初始位置
        String currentOutput = outputDirField.getText().trim();
        // 已添加文件时优先从其目录开始
        if (!files.isEmpty()) {
            // 取首个已添加文件的父目录
            Path parent = files.get(0).getParent();
            // 父目录存在时设为当前目录
            if (parent != null && Files.isDirectory(parent)) {
                // 设置当前目录
                chooser.setCurrentDirectory(parent.toFile());
            }
        } else if (!currentOutput.isEmpty() && Files.isDirectory(Path.of(currentOutput))) {
            // 输出目录存在时设为当前目录
            chooser.setCurrentDirectory(Path.of(currentOutput).toFile());
        }
        // 弹出选择对话框
        int result = chooser.showOpenDialog(this);
        // 用户取消时直接返回
        if (result != JFileChooser.APPROVE_OPTION) {
            // 结束处理
            return;
        }
        // 取全部选中文件
        java.io.File[] selected = chooser.getSelectedFiles();
        // 未取到多选结果时退化到单选结果
        if (selected == null || selected.length == 0) {
            // 单选结果非空时构造成单元素数组
            selected = chooser.getSelectedFile() == null ? new java.io.File[0] : new java.io.File[]{chooser.getSelectedFile()};
        }
        // 逐个加入清单
        for (java.io.File file : selected) {
            // 加入文件
            addFile(file.toPath());
        }
        // 添加完成后刷新状态
        refreshStatusSummary();
    }

    // 把单个文件加入清单并探测其媒体信息
    private void addFile(Path path) {
        // 路径为空或文件不存在时忽略
        if (path == null || !Files.isRegularFile(path)) {
            // 直接返回
            return;
        }
        // 已存在相同文件时避免重复添加
        for (Path existing : files) {
            // 路径相同时视为重复
            if (existing.toAbsolutePath().normalize().toString().equalsIgnoreCase(path.toAbsolutePath().normalize().toString())) {
                // 重复时直接返回
                return;
            }
        }
        // 加入文件清单
        files.add(path);
        // 探测媒体信息并缓存
        MediaInfo info = MediaProbe.probe(path);
        // 写入缓存
        mediaCache.put(path, info);
        // 构造列表展示文本，包含文件名与探测摘要
        String display = path.getFileName().toString() + (info.valid() ? "  [" + info.describe() + "]" : "  [probe failed]");
        // 加入列表模型
        fileModel.addElement(display);
        // 输出一行添加日志
        shell.log("Added: " + path.getFileName() + " -> " + (info.valid() ? info.describe() : "probe failed"));
    }

    // 移除列表中选中的文件
    private void removeSelected() {
        // 取全部选中索引，降序删除以避免索引错位
        int[] indices = fileList.getSelectedIndices();
        // 从后向前逐个删除
        for (int i = indices.length - 1; i >= 0; i--) {
            // 索引越界时跳过
            if (indices[i] < 0 || indices[i] >= files.size()) {
                // 继续处理下一个索引
                continue;
            }
            // 取对应路径用于清理缓存
            Path removed = files.remove(indices[i]);
            // 从缓存中移除该路径
            mediaCache.remove(removed);
            // 从列表模型移除该元素
            fileModel.remove(indices[i]);
        }
        // 刷新状态
        refreshStatusSummary();
    }

    // 清空文件清单
    private void clearFiles() {
        // 清空路径清单
        files.clear();
        // 清空信息缓存
        mediaCache.clear();
        // 清空列表模型
        fileModel.clear();
        // 清空本面板任务记录，避免进度统计残留
        myJobs.clear();
        // 清空任务编号集合
        myJobIds.clear();
        // 进度条归零
        progressBar.setValue(0);
        // 刷新状态
        refreshStatusSummary();
    }

    // 套用当前选中的预设
    private void applySelectedPreset() {
        // 取选中的预设名称
        Object selected = presetBox.getSelectedItem();
        // 未选中时直接返回
        if (selected == null) {
            // 结束处理
            return;
        }
        // 按名称查找预设
        Preset preset = PresetLibrary.byName(selected.toString());
        // 未找到时直接返回
        if (preset == null) {
            // 结束处理
            return;
        }
        // 把预设参数写入界面控件
        targetFormatBox.setSelectedItem(preset.targetExtension());
        // 写入质量档位
        qualityBox.setSelectedItem(preset.quality().displayName());
        // 写入宽度
        widthField.setText(preset.width() > 0 ? String.valueOf(preset.width()) : "");
        // 写入高度
        heightField.setText(preset.height() > 0 ? String.valueOf(preset.height()) : "");
        // 写入帧率
        frameRateField.setText(preset.frameRate() > 0d ? formatNumber(preset.frameRate()) : "");
        // 写入视频码率
        videoBitRateField.setText(preset.videoBitRateKbps() > 0 ? String.valueOf(preset.videoBitRateKbps()) : "");
        // 写入音频码率
        audioBitRateField.setText(preset.audioBitRateKbps() > 0 ? String.valueOf(preset.audioBitRateKbps()) : "");
        // 写入速度倍率
        speedField.setText(formatNumber(preset.speedFactor()));
        // 预设指定了视频编码器时写入下拉框
        if (!preset.videoEncoder().isEmpty()) {
            // 先确保候选存在，再写入选择
            ensureComboItem(videoEncoderBox, preset.videoEncoder());
        }
        // 预设指定了音频编码器时写入下拉框
        if (!preset.audioEncoder().isEmpty()) {
            // 先确保候选存在，再写入选择
            ensureComboItem(audioEncoderBox, preset.audioEncoder());
        }
        // 输出一行套用日志
        shell.log("Preset applied: " + preset.name() + " (" + preset.description() + ")");
    }

    // 确保下拉框中存在指定条目，然后选中它
    private void ensureComboItem(JComboBox<String> box, String value) {
        // 遍历现有条目查找
        for (int i = 0; i < box.getItemCount(); i++) {
            // 命中即选中并返回
            if (value.equalsIgnoreCase(box.getItemAt(i))) {
                // 选中该条目
                box.setSelectedIndex(i);
                // 结束处理
                return;
            }
        }
        // 未命中时把条目加入下拉框，仍能让命令构建采用用户的预设值
        box.addItem(value);
        // 选中新增条目
        box.setSelectedItem(value);
    }

    // 刷新状态标签，展示已添加文件数量
    private void refreshStatusSummary() {
        // 已添加文件时展示数量
        if (!files.isEmpty()) {
            // 展示文件数量与就绪提示
            statusLabel.setText(files.size() + " file(s) ready");
        } else {
            // 无文件时回到内核提示
            refreshCoreHint();
        }
    }

    // 公开方法：按当前控件参数构建任务并入队，供开始按钮与自检调用
    public int startConversion() {
        // 未添加文件时给出提示并返回
        if (files.isEmpty()) {
            // 写入日志提示用户先添加文件
            shell.log("No source file was added.");
            // 返回 0 表示没有任务入队
            return 0;
        }
        // 构建任务列表
        List<Job> jobs = new ArrayList<>();
        // 逐个文件构建请求与任务
        for (Path file : files) {
            // 取或探测媒体信息
            MediaInfo info = mediaCache.computeIfAbsent(file, MediaProbe::probe);
            // 按类别构造转换请求
            ConversionRequest request = buildRequest(file, info);
            // 构造媒体任务，携带媒体信息供命令构建使用
            Job job = new Job(Job.Kind.MEDIA, file, request.targetExtension(), request, new MediaJobTask(info),
                    // 去重键由输入路径、目标格式、类别共同组成
                    "media|" + file.toAbsolutePath() + "|" + request.targetExtension() + "|" + category, info.durationSeconds());
            // 加入任务列表
            jobs.add(job);
        }
        // 批量入队
        int accepted = shell.enqueueBatch(jobs);
        // 记录本面板创建的任务，用于进度汇总
        for (Job job : jobs) {
            // 加入任务引用列表
            myJobs.add(job);
            // 记录任务编号
            myJobIds.add(job.id());
        }
        // 进度条归零，准备反映新一批任务
        progressBar.setValue(0);
        // 写入入队结果日志
        shell.log("Queued " + accepted + " task(s) for conversion.");
        // 返回成功入队数量
        return accepted;
    }

    // 按当前控件参数构造转换请求
    private ConversionRequest buildRequest(Path file, MediaInfo info) {
        // 创建请求对象并逐项写入控件值
        ConversionRequest request = new ConversionRequest();
        // 写入输入文件
        request.input(file);
        // 写入类别
        request.category(category);
        // 写入目标格式
        request.targetExtension(String.valueOf(targetFormatBox.getSelectedItem()));
        // 写入输出目录，空值时交由命名器回落到输入目录
        String outputDir = outputDirField.getText().trim();
        // 目录非空时写入请求
        if (!outputDir.isEmpty()) {
            // 写入输出目录路径
            request.outputDirectory(Path.of(outputDir));
        }
        // 按下拉框选择映射命名模式
        Object namingMode = namingModeBox.getSelectedItem();
        // 保留原名追后缀模式
        if ("Keep name and add suffix".equals(namingMode)) {
            // 写入命名模式
            request.namingMode(ConversionRequest.NamingMode.KEEP_NAME_ADD_SUFFIX);
        } else if ("Custom template".equals(namingMode)) {
            // 写入自定义模板模式
            request.namingMode(ConversionRequest.NamingMode.CUSTOM_TEMPLATE);
            // 写入模板文本
            request.nameTemplate(templateField.getText());
        } else {
            // 默认写入替换扩展名模式
            request.namingMode(ConversionRequest.NamingMode.REPLACE_EXTENSION);
        }
        // 写入质量档位
        request.quality(Quality.fromName(String.valueOf(qualityBox.getSelectedItem())));
        // 写入视频编码器，自动项会被请求对象归一化为空
        request.videoEncoder(String.valueOf(videoEncoderBox.getSelectedItem()));
        // 写入音频编码器
        request.audioEncoder(String.valueOf(audioEncoderBox.getSelectedItem()));
        // 写入视频码率
        request.videoBitRateKbps(parseInt(videoBitRateField.getText(), 0));
        // 写入音频码率
        request.audioBitRateKbps(parseInt(audioBitRateField.getText(), 0));
        // 写入目标宽度
        request.width(parseInt(widthField.getText(), 0));
        // 写入目标高度
        request.height(parseInt(heightField.getText(), 0));
        // 写入目标帧率
        request.frameRate(parseDouble(frameRateField.getText(), 0d));
        // 写入裁剪区域
        request.crop(parseInt(cropXField.getText(), 0), parseInt(cropYField.getText(), 0),
                // 写入裁剪宽度
                parseInt(cropWidthField.getText(), 0),
                // 写入裁剪高度
                parseInt(cropHeightField.getText(), 0));
        // 写入旋转角度
        request.rotateDegrees(parseInt(String.valueOf(rotateBox.getSelectedItem()), 0));
        // 写入水平镜像
        request.mirrorHorizontal(mirrorHBox.isSelected());
        // 写入垂直镜像
        request.mirrorVertical(mirrorVBox.isSelected());
        // 写入水印文本
        request.watermarkText(watermarkField.getText());
        // 写入水印位置
        request.watermarkPosition(String.valueOf(watermarkPositionBox.getSelectedItem()));
        // 写入水印字号
        request.watermarkFontSize(parseInt(watermarkSizeField.getText(), 24));
        // 写入水印不透明度
        request.watermarkOpacity(parseDouble(watermarkOpacityField.getText(), 0.6d));
        // 写入字幕文件路径
        String subtitle = subtitleField.getText().trim();
        // 路径非空时写入请求
        if (!subtitle.isEmpty()) {
            // 写入字幕路径
            request.subtitleFile(Path.of(subtitle));
        }
        // 写入字幕烧录标记
        request.burnSubtitles(burnSubtitleBox.isSelected());
        // 写入速度倍率
        request.speedFactor(parseDouble(speedField.getText(), 1.0d));
        // 写入元数据模式
        request.metadataMode(String.valueOf(metadataBox.getSelectedItem()));
        // 写入硬件加速开关，取自应用设置
        request.hardwareAcceleration(shell.settings().hardwareAcceleration());
        // 视频与音频类别强制 yuv420p，提升播放器兼容性
        request.forceYuv420p(category != ConversionRequest.Category.IMAGE);
        // 返回构造好的请求
        return request;
    }

    // 处理任务变化回调，汇总本面板任务的进度与状态
    private void onJobChanged(Job job) {
        // 不属于本面板的任务直接忽略
        if (!myJobIds.contains(job.id())) {
            // 结束处理
            return;
        }
        // 统计各状态数量
        int running = 0;
        // 完成数量
        int completed = 0;
        // 失败数量
        int failed = 0;
        // 取消数量
        int cancelled = 0;
        // 进度总和
        double percentSum = 0d;
        // 参与统计的任务数量
        int counted = 0;
        // 遍历本面板任务
        for (Job current : myJobs) {
            // 按状态分类统计
            switch (current.status()) {
                // 运行中
                case RUNNING -> running++;
                // 已完成
                case COMPLETED -> completed++;
                // 失败
                case FAILED -> failed++;
                // 取消
                case CANCELLED -> cancelled++;
                // 其余状态不单独统计
                default -> {
                    // 无需处理
                }
            }
            // 终态任务的进度按 100 计入，使总进度能到达满值
            boolean terminal = current.isTerminal();
            // 累加进度百分比
            percentSum += terminal ? 100d : current.percent();
            // 计数加一
            counted++;
        }
        // 计算平均进度，任务数量为 0 时按 0 处理
        int average = counted == 0 ? 0 : (int) Math.round(percentSum / counted);
        // 更新进度条
        progressBar.setValue(average);
        // 组装状态文本，展示各类计数
        statusLabel.setText(running + " running | " + completed + " done | " + failed + " failed | " + cancelled + " cancelled");
    }

    // 按类别返回文件选择器的扩展名数组
    private String[] categoryExtensions() {
        // 视频类别返回常见视频扩展名
        if (category == ConversionRequest.Category.VIDEO) {
            // 返回视频扩展名数组
            return new String[]{"mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "mpg", "mpeg", "ts", "m2ts", "m4v", "3gp", "ogv", "vob", "gif"};
        }
        // 音频类别返回常见音频扩展名
        if (category == ConversionRequest.Category.AUDIO) {
            // 返回音频扩展名数组
            return new String[]{"mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "mka", "ac3", "aiff", "au", "amr", "wv", "tta", "mp2"};
        }
        // 图片类别返回常见图片扩展名
        return new String[]{"jpg", "jpeg", "png", "bmp", "tiff", "tif", "webp", "tga", "ppm", "pgm", "exr", "gif"};
    }

    // 返回文件选择器的描述文本（英文，会显示给用户）
    private String categoryFilterDescription() {
        // 按类别返回描述
        if (category == ConversionRequest.Category.VIDEO) {
            // 视频描述
            return "Video files";
        }
        // 音频描述
        if (category == ConversionRequest.Category.AUDIO) {
            // 返回音频描述
            return "Audio files";
        }
        // 图片描述
        return "Image files";
    }

    // 供主窗口在参数变化后重新填充编码器候选
    public void refreshEncoders() {
        // 复用内部填充逻辑
        populateEncoders();
    }

    // 获取本面板类别，供主窗口在日志分发与测试时判断
    public ConversionRequest.Category category() {
        // 返回类别
        return category;
    }

    // 获取日志文本域，供冒烟测试检查控件是否构建成功
    public JTextArea logArea() {
        // 返回日志文本域
        return logArea;
    }

    // 获取进度条控件，供冒烟测试检查控件是否构建成功
    public JProgressBar progressBar() {
        // 返回进度条
        return progressBar;
    }

    // 获取状态标签控件，供冒烟测试检查控件是否构建成功
    public JLabel statusLabel() {
        // 返回状态标签
        return statusLabel;
    }

    // 以编程方式添加文件，供冒烟测试与自动化脚本在不弹窗的情况下准备输入
    public void addFileForTest(Path path) {
        // 直接调用内部添加逻辑
        addFile(path);
        // 刷新状态摘要
        refreshStatusSummary();
    }
}
