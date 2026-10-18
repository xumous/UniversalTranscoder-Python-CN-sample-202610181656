package com.xu.universaltranscoder.ui;

// 引入硬件加速策略类，用于展示检测结果

import com.xu.universaltranscoder.core.HardwareAccel;
// 引入预设库，用于展示内置预设数量
import com.xu.universaltranscoder.core.PresetLibrary;
// 引入内核对象，用于展示内核版本信息
import com.xu.universaltranscoder.core.Toolchain;

// 引入 Swing 组件
import javax.swing.BorderFactory;
// 引入盒式布局
import javax.swing.BoxLayout;
// 引入按钮
import javax.swing.JButton;
// 引入标签
import javax.swing.JLabel;
// 引入面板
import javax.swing.JPanel;
// 引入滚动面板
import javax.swing.JScrollPane;
// 引入图标控件，用于展示应用图标
import javax.swing.ImageIcon;

// 引入布局管理器
import java.awt.BorderLayout;
// 引入流式布局
import java.awt.FlowLayout;
// 引入字体
import java.awt.Font;

// 关键字 class：AboutPanel 展示应用名、版本、作者、内核版本与运行环境信息
// 之所以把这些信息集中展示：用户报障时可以直接复制这些事实，便于快速定位环境差异
public final class AboutPanel extends JPanel {

    // 应用外壳引用
    private final AppShell shell;
    // 内核信息文本域，采用标签列表方式展示，刷新时整体重建
    private final JPanel infoPanel = new JPanel();

    // 构造方法：接收应用外壳
    public AboutPanel(AppShell shell) {
        // 保存应用外壳引用
        this.shell = shell;
        // 使用边界布局
        setLayout(new BorderLayout(8, 8));
        // 设置四周留白
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        // 顶部区域使用流式布局排列图标与大标题
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        // 创建图标控件，尺寸限制为 64 像素便于布局
        JLabel iconLabel = new JLabel(new ImageIcon(IconFactory.getAppIcon().getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH)));
        // 加入图标
        header.add(iconLabel);
        // 创建标题标签，使用较大字号
        JLabel titleLabel = new JLabel("Universal Transcoder");
        // 设置标题字号，突出应用名
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        // 加入标题
        header.add(titleLabel);
        // 创建版本标签
        JLabel versionLabel = new JLabel("Version 1.0.0");
        // 设置版本字号
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 14f));
        // 加入版本标签
        header.add(versionLabel);
        // 把顶部区域放到北部
        add(header, BorderLayout.NORTH);
        // 信息面板使用纵向盒式布局
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        // 设置标题边框
        infoPanel.setBorder(BorderFactory.createTitledBorder("Build and core information"));
        // 把信息面板放入滚动面板并放到中央
        add(new JScrollPane(infoPanel), BorderLayout.CENTER);
        // 构建底部动作条
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建刷新按钮
        JButton refreshButton = new JButton("Refresh");
        // 绑定刷新动作
        refreshButton.addActionListener(event -> refreshInfo());
        // 加入刷新按钮
        actions.add(refreshButton);
        // 把动作条放到南部
        add(actions, BorderLayout.SOUTH);
        // 首次填充信息
        refreshInfo();
    }

    // 刷新信息面板内容
    public void refreshInfo() {
        // 清空旧内容
        infoPanel.removeAll();
        // 取内核对象
        Toolchain toolchain = shell.toolchain();
        // 逐条追加信息行
        appendLine("Application", "Universal Transcoder");
        // 追加版本
        appendLine("Version", "1.0.0");
        // 追加作者
        appendLine("Author", "Xu");
        // 追加界面技术栈说明，便于用户理解运行方式
        appendLine("User interface", "Java Swing (JFrame + JTabbedPane), no JavaFX runtime required");
        // 追加唯一外部依赖说明
        appendLine("External dependency", "Apache PDFBox 2.0.31 (PDF rendering only)");
        // 追加内置预设数量
        appendLine("Built-in presets", PresetLibrary.all().size() + " (video 14 / audio 11 / image 10)");
        // 追加内核路径
        appendLine("FFmpeg path", toolchain.ffmpeg() == null ? "not found" : toolchain.ffmpeg().toString());
        // 追加 ffprobe 路径
        appendLine("FFprobe path", toolchain.ffprobe() == null ? "not found" : toolchain.ffprobe().toString());
        // 追加 ffmpeg 版本横幅
        appendLine("FFmpeg version", toolchain.ffmpegVersion().isEmpty() ? "unknown" : toolchain.ffmpegVersion());
        // 追加 ffprobe 版本横幅
        appendLine("FFprobe version", toolchain.ffprobeVersion().isEmpty() ? "unknown" : toolchain.ffprobeVersion());
        // 追加编码器数量
        appendLine("Encoders", toolchain.encoders().size() + " kinds");
        // 追加封装数量
        appendLine("Muxers", toolchain.muxFormats().size() + " kinds");
        // 追加硬件后端清单
        appendLine("Hardware accels", toolchain.hwaccels().isEmpty() ? "none reported" : String.join(", ", toolchain.hwaccels()));
        // 追加硬件厂商检测结果
        appendLine("Hardware vendors", HardwareAccel.describe());
        // 追加运行时 Java 版本
        appendLine("Java runtime", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        // 追加操作系统信息
        appendLine("Operating system", System.getProperty("os.name") + " " + System.getProperty("os.version")
                // 追加体系结构
                + " / " + System.getProperty("os.arch"));
        // 追加处理器数量
        appendLine("CPU cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        // 追加最大堆内存
        appendLine("Max heap", (Runtime.getRuntime().maxMemory() / 1048576L) + " MB");
        // 重新计算布局
        infoPanel.revalidate();
        // 重绘面板
        infoPanel.repaint();
    }

    // 追加一行信息：左侧为名称，右侧为值
    private void appendLine(String name, String value) {
        // 使用边界布局组织单行内容
        JPanel row = new JPanel(new BorderLayout(8, 2));
        // 创建名称标签，加粗显示
        JLabel nameLabel = new JLabel(name + ":");
        // 设置名称字号与加粗
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        // 设置名称标签首选宽度，让值对齐
        nameLabel.setPreferredSize(new java.awt.Dimension(170, 20));
        // 创建值标签
        JLabel valueLabel = new JLabel(value == null ? "" : value);
        // 名称放到西侧
        row.add(nameLabel, BorderLayout.WEST);
        // 值放到中央
        row.add(valueLabel, BorderLayout.CENTER);
        // 设置行的最大高度，避免纵向被拉伸
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 24));
        // 把行加入信息面板
        infoPanel.add(row);
    }

    // 获取信息面板，供冒烟测试检查
    public JPanel infoPanel() {
        // 返回信息面板
        return infoPanel;
    }
}
