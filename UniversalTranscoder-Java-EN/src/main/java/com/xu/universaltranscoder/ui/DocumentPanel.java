package com.xu.universaltranscoder.ui;

// 引入文档任务工厂，用于创建图片转 PDF、文本转 PDF 与 PDF 转图片任务

import com.xu.universaltranscoder.core.DocumentTasks;
// 引入任务类型
import com.xu.universaltranscoder.core.Job;

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
// 引入列表
import javax.swing.JList;
// 引入面板
import javax.swing.JPanel;
// 引入滚动面板
import javax.swing.JScrollPane;
// 引入页签面板，用于在文档页内切换三种子功能
import javax.swing.JTabbedPane;
// 引入文本框
import javax.swing.JTextField;
// 引入默认列表模型
import javax.swing.DefaultListModel;
// 引入扩展名过滤器
import javax.swing.filechooser.FileNameExtensionFilter;

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

// 引入文件工具类
import java.nio.file.Files;
// 引入路径
import java.nio.file.Path;
// 引入列表容器
import java.util.ArrayList;
// 引入列表接口
import java.util.List;

// 关键字 class：DocumentPanel 提供图片转 PDF、文本转 PDF 与 PDF 转图片三项文档能力
// 之所以集中在一个页签内用子页签组织：三项功能都围绕文档，放在一起可以减少顶部页签数量又不相互干扰
public final class DocumentPanel extends JPanel {

    // 应用外壳引用
    private final AppShell shell;

    // === 图片转 PDF 子页的控件 ===
    // 待合并图片清单
    private final List<Path> imageFiles = new ArrayList<>();
    // 图片文件列表模型
    private final DefaultListModel<String> imageModel = new DefaultListModel<>();
    // 图片列表控件
    private final JList<String> imageList = new JList<>(imageModel);
    // 目标 PDF 路径输入框
    private final JTextField pdfOutputField = new JTextField();

    // === 文本转 PDF 子页的控件 ===
    // 源文本文件输入框
    private final JTextField textInputField = new JTextField();
    // 目标 PDF 路径输入框
    private final JTextField textPdfOutputField = new JTextField();

    // === PDF 转图片子页的控件 ===
    // 源 PDF 文件输入框
    private final JTextField pdfInputField = new JTextField();
    // 输出目录输入框
    private final JTextField pdfImageOutputField = new JTextField();
    // 目标图片格式下拉框
    private final JComboBox<String> pdfImageFormatBox = new JComboBox<>(new String[]{"png", "jpg"});
    // 渲染分辨率输入框
    private final JTextField dpiField = new JTextField("150");
    // 起始页码输入框
    private final JTextField fromPageField = new JTextField("1");
    // 结束页码输入框，0 表示到最后一页
    private final JTextField toPageField = new JTextField("0");

    // 构造方法：构建三个子页签
    public DocumentPanel(AppShell shell) {
        // 保存应用外壳引用
        this.shell = shell;
        // 使用边界布局
        setLayout(new BorderLayout(6, 6));
        // 设置四周留白
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // 创建子页签面板
        JTabbedPane tabs = new JTabbedPane();
        // 加入图片转 PDF 页
        tabs.addTab("Images to PDF", buildImagesToPdfTab());
        // 加入文本转 PDF 页
        tabs.addTab("Text to PDF", buildTextToPdfTab());
        // 加入 PDF 转图片页
        tabs.addTab("PDF to Images", buildPdfToImagesTab());
        // 把页签面板放入中央
        add(tabs, BorderLayout.CENTER);
    }

    // 解析整数，非法时返回默认值
    private static int parseIntOr(String text, int fallback) {
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
            // 非法值返回默认值
            return fallback;
        }
    }

    // 构建图片转 PDF 子页
    private JPanel buildImagesToPdfTab() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        // 顶部操作条
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建添加图片按钮
        JButton addButton = new JButton("Add Images...");
        // 绑定添加图片动作
        addButton.addActionListener(event -> chooseImages());
        // 创建移除选中按钮
        JButton removeButton = new JButton("Remove Selected");
        // 绑定移除动作
        removeButton.addActionListener(event -> removeSelectedImages());
        // 创建清空按钮
        JButton clearButton = new JButton("Clear List");
        // 绑定清空动作
        clearButton.addActionListener(event -> clearImages());
        // 把按钮加入操作条
        top.add(addButton);
        // 加入移除按钮
        top.add(removeButton);
        // 加入清空按钮
        top.add(clearButton);
        // 把操作条放到顶部
        panel.add(top, BorderLayout.NORTH);
        // 图片列表放入滚动面板并加标题
        JScrollPane listScroll = new JScrollPane(imageList);
        // 设置标题边框
        listScroll.setBorder(BorderFactory.createTitledBorder("Images (merged in this order)"));
        // 把列表放到中央
        panel.add(listScroll, BorderLayout.CENTER);
        // 底部参数区
        JPanel bottom = new JPanel(new GridBagLayout());
        // 第 0 行为目标 PDF 路径
        addFormRow(bottom, 0, "Output PDF:", pdfOutputField, createFileBrowseButton(pdfOutputField, "PDF files", "pdf", true));
        // 第 1 行为开始按钮所在的容器
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建开始按钮
        JButton startButton = new JButton("Start");
        // 绑定开始动作
        startButton.addActionListener(event -> startImagesToPdf());
        // 加入开始按钮
        actionPanel.add(startButton);
        // 构造按钮容器的布局约束
        GridBagConstraints actionConstraints = new GridBagConstraints();
        // 放在第二列
        actionConstraints.gridx = 1;
        // 放在第二行
        actionConstraints.gridy = 1;
        // 横向填充
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        // 设置内边距
        actionConstraints.insets = new Insets(3, 0, 3, 6);
        // 加入动作容器
        bottom.add(actionPanel, actionConstraints);
        // 把参数区放到南部
        panel.add(bottom, BorderLayout.SOUTH);
        // 返回子页
        return panel;
    }

    // 构建文本转 PDF 子页
    private JPanel buildTextToPdfTab() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        // 参数区使用网格袋布局
        JPanel form = new JPanel(new GridBagLayout());
        // 第 0 行为源文本文件
        addFormRow(form, 0, "Text file:", textInputField, createFileBrowseButton(textInputField, "Text files", "txt", false));
        // 第 1 行为目标 PDF 路径
        addFormRow(form, 1, "Output PDF:", textPdfOutputField, createFileBrowseButton(textPdfOutputField, "PDF files", "pdf", true));
        // 构造按钮容器
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建开始按钮
        JButton startButton = new JButton("Start");
        // 绑定开始动作
        startButton.addActionListener(event -> startTextToPdf());
        // 加入开始按钮
        actionPanel.add(startButton);
        // 构造按钮容器的约束
        GridBagConstraints actionConstraints = new GridBagConstraints();
        // 放在第二列
        actionConstraints.gridx = 1;
        // 放在第三行
        actionConstraints.gridy = 2;
        // 横向填充
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        // 设置内边距
        actionConstraints.insets = new Insets(3, 0, 3, 6);
        // 加入动作容器
        form.add(actionPanel, actionConstraints);
        // 表单放进滚动面板，参数多时也能完整查看
        panel.add(new JScrollPane(form), BorderLayout.NORTH);
        // 返回子页
        return panel;
    }

    // 构建 PDF 转图片子页
    private JPanel buildPdfToImagesTab() {
        // 使用边界布局
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        // 参数区使用网格袋布局
        JPanel form = new JPanel(new GridBagLayout());
        // 第 0 行为源 PDF 文件
        addFormRow(form, 0, "PDF file:", pdfInputField, createFileBrowseButton(pdfInputField, "PDF files", "pdf", false));
        // 第 1 行为输出目录
        addFormRow(form, 1, "Output folder:", pdfImageOutputField, createDirectoryBrowseButton(pdfImageOutputField));
        // 第 2 行为目标图片格式
        addFormRow(form, 2, "Image format:", pdfImageFormatBox, null);
        // 第 3 行为渲染分辨率
        addFormRow(form, 3, "Resolution (DPI):", dpiField, null);
        // 第 4 行为起始页码
        addFormRow(form, 4, "From page:", fromPageField, null);
        // 第 5 行为结束页码
        addFormRow(form, 5, "To page (0 = last):", toPageField, null);
        // 构造按钮容器
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建开始按钮
        JButton startButton = new JButton("Start");
        // 绑定开始动作
        startButton.addActionListener(event -> startPdfToImages());
        // 加入开始按钮
        actionPanel.add(startButton);
        // 构造按钮容器的约束
        GridBagConstraints actionConstraints = new GridBagConstraints();
        // 放在第二列
        actionConstraints.gridx = 1;
        // 放在第六行
        actionConstraints.gridy = 6;
        // 横向填充
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        // 设置内边距
        actionConstraints.insets = new Insets(3, 0, 3, 6);
        // 加入动作容器
        form.add(actionPanel, actionConstraints);
        // 表单放进滚动面板
        panel.add(new JScrollPane(form), BorderLayout.NORTH);
        // 返回子页
        return panel;
    }

    // 向表单追加一行：标签占第一列，输入控件占第二列，附加控件占第三列
    private void addFormRow(JPanel panel, int row, String label, javax.swing.JComponent main, javax.swing.JComponent extra) {
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
        // 统一控件高度，保证表单整齐
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
            extraConstraints.insets = new Insets(3, 0, 3, 4);
            // 加入附加控件
            panel.add(extra, extraConstraints);
        }
    }

    // 创建选择文件的浏览按钮
    private JButton createFileBrowseButton(JTextField target, String description, String extension, boolean saveMode) {
        // 创建按钮，保存与打开使用不同文案
        JButton button = new JButton(saveMode ? "Save as..." : "Choose...");
        // 绑定动作
        button.addActionListener(event -> {
            // 创建文件选择器
            JFileChooser chooser = new JFileChooser();
            // 设置扩展名过滤器
            chooser.setFileFilter(new FileNameExtensionFilter(description + " (*." + extension + ")", extension));
            // 保存模式时使用保存对话框
            if (saveMode) {
                // 预填默认文件名，减少用户输入
                chooser.setSelectedFile(new java.io.File("output." + extension));
                // 弹出保存对话框
                int result = chooser.showSaveDialog(this);
                // 用户确认后写回输入框
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    // 写入选中路径
                    target.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            } else {
                // 文件模式时使用打开对话框
                int result = chooser.showOpenDialog(this);
                // 用户确认后写回输入框
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    // 写入选中路径
                    target.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            }
        });
        // 返回按钮
        return button;
    }

    // 创建选择目录的浏览按钮
    private JButton createDirectoryBrowseButton(JTextField target) {
        // 创建按钮
        JButton button = new JButton("Browse...");
        // 绑定动作
        button.addActionListener(event -> {
            // 创建文件选择器
            JFileChooser chooser = new JFileChooser();
            // 只允许选择目录
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            // 弹出打开对话框
            int result = chooser.showOpenDialog(this);
            // 用户确认后写回输入框
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                // 写入选中目录
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        // 返回按钮
        return button;
    }

    // 弹出选择器添加图片
    private void chooseImages() {
        // 创建文件选择器
        JFileChooser chooser = new JFileChooser();
        // 允许多选
        chooser.setMultiSelectionEnabled(true);
        // 限制为常见图片格式
        chooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "bmp", "gif", "tiff", "tif", "webp"));
        // 弹出对话框
        int result = chooser.showOpenDialog(this);
        // 用户取消时返回
        if (result != JFileChooser.APPROVE_OPTION) {
            // 结束处理
            return;
        }
        // 取选中文件
        java.io.File[] selected = chooser.getSelectedFiles();
        // 无多选结果时退化到单选
        if (selected == null || selected.length == 0) {
            // 构造单元素数组
            selected = chooser.getSelectedFile() == null ? new java.io.File[0] : new java.io.File[]{chooser.getSelectedFile()};
        }
        // 逐个加入清单并刷新列表
        for (java.io.File file : selected) {
            // 路径转成 Path 加入清单
            Path path = file.toPath();
            // 已存在时跳过，避免重复合并同一张图片
            if (imageFiles.contains(path)) {
                // 继续处理下一个文件
                continue;
            }
            // 加入清单
            imageFiles.add(path);
            // 加入列表模型
            imageModel.addElement(path.getFileName().toString());
        }
        // 输出日志
        shell.log("Added " + selected.length + " image(s) for PDF assembly.");
        // 首次添加时自动给出一个合理的输出位置
        suggestPdfOutput();
    }

    // 移除列表中选中的图片
    private void removeSelectedImages() {
        // 取选中索引
        int[] indices = imageList.getSelectedIndices();
        // 从后向前删除
        for (int i = indices.length - 1; i >= 0; i--) {
            // 索引越界时跳过
            if (indices[i] < 0 || indices[i] >= imageFiles.size()) {
                // 继续处理下一个
                continue;
            }
            // 从清单移除
            imageFiles.remove(indices[i]);
            // 从列表模型移除
            imageModel.remove(indices[i]);
        }
    }

    // 清空图片清单
    private void clearImages() {
        // 清空清单
        imageFiles.clear();
        // 清空列表模型
        imageModel.clear();
    }

    // 首次添加图片后自动建议输出 PDF 路径
    private void suggestPdfOutput() {
        // 已有输出路径时不覆盖用户输入
        if (!pdfOutputField.getText().trim().isEmpty()) {
            // 结束处理
            return;
        }
        // 清单为空时无法建议
        if (imageFiles.isEmpty()) {
            // 结束处理
            return;
        }
        // 取第一张图片的父目录作为输出目录
        Path parent = imageFiles.get(0).getParent();
        // 父目录存在时构造建议路径
        if (parent != null) {
            // 建议输出到父目录下的 merged.pdf
            pdfOutputField.setText(parent.resolve("merged.pdf").toString());
        }
    }

    // 开始图片合成 PDF
    private void startImagesToPdf() {
        // 清单为空时提示
        if (imageFiles.isEmpty()) {
            // 输出日志提示
            shell.log("Add at least one image first.");
            // 结束处理
            return;
        }
        // 取输出路径
        String output = pdfOutputField.getText().trim();
        // 输出路径为空时提示
        if (output.isEmpty()) {
            // 输出日志提示
            shell.log("Choose an output PDF path first.");
            // 结束处理
            return;
        }
        // 创建任务并入队
        shell.enqueue(DocumentTasks.newImageToPdfJob(new ArrayList<>(imageFiles), Path.of(output)));
        // 输出入队日志
        shell.log("Queued image to PDF task (" + imageFiles.size() + " image(s)).");
    }

    // 开始文本转 PDF
    private void startTextToPdf() {
        // 取源文本路径
        String input = textInputField.getText().trim();
        // 取输出路径
        String output = textPdfOutputField.getText().trim();
        // 任一为空或源文件不存在时提示
        if (input.isEmpty() || !Files.isRegularFile(Path.of(input))) {
            // 输出日志提示
            shell.log("Choose an existing text file first.");
            // 结束处理
            return;
        }
        // 输出路径为空时提示
        if (output.isEmpty()) {
            // 输出日志提示
            shell.log("Choose an output PDF path first.");
            // 结束处理
            return;
        }
        // 创建任务并入队
        shell.enqueue(DocumentTasks.newTextToPdfJob(Path.of(input), Path.of(output)));
        // 输出入队日志
        shell.log("Queued text to PDF task.");
    }

    // 开始 PDF 转图片
    private void startPdfToImages() {
        // 取源 PDF 路径
        String input = pdfInputField.getText().trim();
        // 源文件不存在时提示
        if (input.isEmpty() || !Files.isRegularFile(Path.of(input))) {
            // 输出日志提示
            shell.log("Choose an existing PDF file first.");
            // 结束处理
            return;
        }
        // 取输出目录
        String output = pdfImageOutputField.getText().trim();
        // 输出目录为空时提示
        if (output.isEmpty()) {
            // 输出日志提示
            shell.log("Choose an output folder first.");
            // 结束处理
            return;
        }
        // 解析页码范围与分辨率，非法输入回落到默认值
        int dpi = parseIntOr(dpiField.getText(), 150);
        // 解析起始页
        int from = parseIntOr(fromPageField.getText(), 1);
        // 解析结束页
        int to = parseIntOr(toPageField.getText(), 0);
        // 创建任务并入队
        shell.enqueue(DocumentTasks.newPdfToImageJob(Path.of(input), Path.of(output),
                // 传入目标图片格式
                String.valueOf(pdfImageFormatBox.getSelectedItem()),
                // 传入分辨率
                dpi,
                // 传入起始页
                from,
                // 传入结束页
                to));
        // 输出入队日志
        shell.log("Queued PDF to image task (" + String.valueOf(pdfImageFormatBox.getSelectedItem()) + ", " + dpi + " DPI).");
    }

    // 以编程方式添加图片，供冒烟测试与自动化脚本使用
    public void addImageForTest(Path path) {
        // 已存在时直接返回
        if (imageFiles.contains(path)) {
            // 结束处理
            return;
        }
        // 加入清单
        imageFiles.add(path);
        // 加入列表模型
        imageModel.addElement(path.getFileName().toString());
        // 建议输出位置
        suggestPdfOutput();
    }

    // 获取目标 PDF 输入框，供冒烟测试设置输出路径
    public JTextField pdfOutputField() {
        // 返回输入框
        return pdfOutputField;
    }

    // 获取起始页码输入框，供冒烟测试与自动化使用
    public JTextField fromPageField() {
        // 返回输入框
        return fromPageField;
    }

    // 获取源 PDF 输入框，供冒烟测试设置输入路径
    public JTextField pdfInputField() {
        // 返回输入框
        return pdfInputField;
    }

    // 获取输出目录输入框，供冒烟测试设置输出路径
    public JTextField pdfImageOutputField() {
        // 返回输入框
        return pdfImageOutputField;
    }
}
