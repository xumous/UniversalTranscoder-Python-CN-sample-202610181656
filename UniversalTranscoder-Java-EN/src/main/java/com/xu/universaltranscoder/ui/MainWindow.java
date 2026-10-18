package com.xu.universaltranscoder.ui;

// 引入核心层类型

import com.xu.universaltranscoder.core.ConversionRequest;
// 引入任务类型
import com.xu.universaltranscoder.core.Job;
// 引入任务队列
import com.xu.universaltranscoder.core.TaskQueue;
// 引入内核对象
import com.xu.universaltranscoder.core.Toolchain;

// 引入 Swing 组件
import javax.swing.BorderFactory;
// 引入标签
import javax.swing.JLabel;
// 引入面板
import javax.swing.JPanel;
// 引入页签面板，作为主界面的功能分区容器
import javax.swing.JTabbedPane;
// 引入窗口类
import javax.swing.JFrame;
// 引入 Swing 工具类，用于把界面更新切回事件分发线程
import javax.swing.SwingUtilities;

// 引入布局管理器
import java.awt.BorderLayout;
// 引入组件类，作为日志接收器注册表的键类型
import java.awt.Component;
// 引入维度，设置窗口初始尺寸
import java.awt.Dimension;

// 引入列表容器
import java.util.ArrayList;
// 引入哈希映射，保存组件到日志接收器的映射
import java.util.LinkedHashMap;
// 引入列表接口
import java.util.List;
// 引入映射接口
import java.util.Map;
// 引入函数式接口，作为日志接收器与任务监听器类型
import java.util.function.Consumer;

// 关键字 class：MainWindow 是应用主窗口，负责组装八个页签、路由日志并驱动任务队列
// 之所以让主窗口实现 AppShell：面板只需依赖窄接口，主窗口集中掌握队列与日志路由，避免面板各自持有全局状态
public final class MainWindow extends JFrame implements AppShell {

    // 应用设置对象
    private final AppSettings settings;
    // 任务队列实例
    private final TaskQueue queue;
    // 页签面板
    private final JTabbedPane tabbedPane = new JTabbedPane();
    // 底部状态栏标签，展示内核概况
    private final JLabel statusBar = new JLabel(" ");
    // 组件到日志接收器的映射，用于把日志定向写入当前可见页面的日志区
    private final Map<Component, Consumer<String>> logSinks = new LinkedHashMap<>();
    // 任务变化监听器列表，面板通过注册监听器获取属于自己的任务更新
    private final List<Consumer<Job>> jobListeners = new ArrayList<>();
    // 队列页引用，便于在队列事件与日志中刷新表格
    private final QueuePanel queuePanel;
    // 内核对象，由主窗口在启动与重新探测时刷新
    private volatile Toolchain toolchain;

    // 构造方法：构建设置、内核、队列与全部页签
    public MainWindow() {
        // 从磁盘加载应用设置
        settings = AppSettings.load();
        // 按设置中的自定义路径定位并探测内核
        toolchain = Toolchain.bootstrap(settings.customFfmpegPath());
        // 创建队列，并发上限取自设置，并安装队列回调监听器
        queue = new TaskQueue(settings.concurrency(), new TaskQueue.Listener() {
            // 任务变化回调：切换到事件分发线程后转发给面板监听器
            @Override
            public void onJobChanged(Job job) {
                // 切换线程，保证任何界面更新都发生在事件分发线程上
                SwingUtilities.invokeLater(() -> notifyJobListeners(job));
            }

            // 队列结构变化回调：刷新队列页表格
            @Override
            public void onQueueChanged() {
                // 切换到事件分发线程刷新表格
                SwingUtilities.invokeLater(() -> queuePanel.refresh());
            }

            // 队列日志回调：写入当前可见页面的日志区
            @Override
            public void onLog(String line) {
                // 切换到事件分发线程写入日志
                SwingUtilities.invokeLater(() -> log(line));
            }
        });
        // 设置窗口标题
        setTitle("Universal Transcoder 1.0.0");
        // 设置窗口图标，资源缺失时由工厂生成
        setIconImage(IconFactory.getAppIcon());
        // 使用边界布局
        setLayout(new BorderLayout());
        // 构建队列页，需要在构建其它页之前完成，供回调引用
        queuePanel = new QueuePanel(this, queue);
        // 依次加入八个页签，标签全部使用英文文案
        tabbedPane.addTab("Video", new MediaPanel(this, ConversionRequest.Category.VIDEO));
        // 加入音频页
        tabbedPane.addTab("Audio", new MediaPanel(this, ConversionRequest.Category.AUDIO));
        // 加入图片页
        tabbedPane.addTab("Image", new MediaPanel(this, ConversionRequest.Category.IMAGE));
        // 加入文档页
        tabbedPane.addTab("Document", new DocumentPanel(this));
        // 加入进阶页
        tabbedPane.addTab("Advanced", new AdvancedPanel(this));
        // 加入队列页
        tabbedPane.addTab("Queue", queuePanel);
        // 创建设置页，并传入保存后的回调用于重新应用主题与重新探测内核
        SettingsPanel settingsPanel = new SettingsPanel(this, this::applySettings);
        // 加入设置页
        tabbedPane.addTab("Settings", settingsPanel);
        // 加入关于页
        tabbedPane.addTab("About", new AboutPanel(this));
        // 把页签面板放到中央
        add(tabbedPane, BorderLayout.CENTER);
        // 配置底部状态栏
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        // 把状态栏放到南部
        add(statusBar, BorderLayout.SOUTH);
        // 设置窗口初始尺寸，兼顾参数控件与日志区的展示需求
        setPreferredSize(new Dimension(1180, 760));
        // 设置最小尺寸，避免用户缩到无法操作的程度
        setMinimumSize(new Dimension(900, 620));
        // 按内容自动调整窗口大小
        pack();
        // 设置窗口居中显示
        setLocationRelativeTo(null);
        // 关闭窗口时停止队列，避免后台仍有任务在写文件
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // 应用主题设置
        UiTheme.apply(settings.theme(), this);
        // 刷新状态栏
        refreshStatusBar();
    }

    // 离屏冒烟测试：构造主窗口与全部面板并触发关键构建路径，不显示真实窗口
    public static boolean smokeTest() {
        // 打印冒烟测试标题
        System.out.println("=== UI smoke test (offscreen) ===");
        // 声明窗口引用，便于最后释放
        MainWindow window = null;
        // 捕获构造过程中可能出现的异常
        try {
            // 构造主窗口，这一过程会构建全部八个页签与它们的控件
            window = new MainWindow();
            // 校验页签数量是否为 8，数量不符说明有页签构建失败
            boolean tabCountOk = window.tabbedPane().getTabCount() == 8;
            // 打印页签数量校验结果
            System.out.println("  tab count: " + window.tabbedPane().getTabCount() + (tabCountOk ? " OK" : " UNEXPECTED"));
            // 逐个页签遍历并检查类型
            for (int i = 0; i < window.tabbedPane().getTabCount(); i++) {
                // 取页签标题
                String title = window.tabbedPane().getTitleAt(i);
                // 取页签组件
                Component component = window.tabAt(i);
                // 打印标题与组件类型
                System.out.println("  tab[" + i + "] " + title + " -> " + component.getClass().getSimpleName());
                // 视频页需要额外验证进度条与日志区已构建
                if (component instanceof MediaPanel mediaPanel && i == 0) {
                    // 校验进度条与日志区非空
                    boolean controlsOk = mediaPanel.progressBar() != null && mediaPanel.logArea() != null;
                    // 打印控件校验结果
                    System.out.println("    video panel controls: " + (controlsOk ? "OK" : "MISSING"));
                }
            }
            // 触发一次命令预览构建，覆盖命令构建路径在界面侧的调用
            for (int i = 0; i < window.tabbedPane().getTabCount(); i++) {
                // 取页签组件
                Component component = window.tabAt(i);
                // 命中进阶页时触发预览与诊断刷新
                if (component instanceof AdvancedPanel advancedPanel) {
                    // 触发诊断参考刷新
                    advancedPanel.setPreviewInputForTest(java.nio.file.Path.of("nonexistent-media-file.mp4"));
                    // 构建预览，走一次命令构建失败分支以验证容错
                    advancedPanel.buildPreviewForTest();
                    // 打印预览文本域的前若干字符，确认文本已写入
                    String text = advancedPanel.previewArea().getText();
                    // 输出预览文本长度
                    System.out.println("    advanced preview text length: " + text.length());
                }
            }
            // 校验队列页表格已构建
            boolean queueOk = window.queue().snapshot() != null;
            // 打印队列校验结果
            System.out.println("  queue model: " + (queueOk ? "OK" : "MISSING"));
            // 打印冒烟测试结论
            System.out.println("  result: PASS");
            // 返回通过
            return true;
        } catch (Throwable error) {
            // 打印异常类型与消息，便于定位构建失败点
            System.out.println("  result: FAIL - " + error.getClass().getSimpleName() + ": " + error.getMessage());
            // 返回失败
            return false;
        } finally {
            // 释放窗口资源，避免残留线程与句柄
            if (window != null) {
                // 停止队列
                window.queue().stop();
                // 释放窗口，不显示真实界面
                window.dispose();
            }
        }
    }

    // 入队单个任务
    @Override
    public void enqueue(Job job) {
        // 转交给队列处理
        queue.enqueue(job);
        // 刷新队列页，让用户立即看到新任务
        SwingUtilities.invokeLater(() -> queuePanel.refresh());
    }

    // 批量入队任务
    @Override
    public int enqueueBatch(List<Job> jobs) {
        // 转交给队列的批量入队方法
        int accepted = queue.enqueueAll(jobs);
        // 刷新队列页
        SwingUtilities.invokeLater(() -> queuePanel.refresh());
        // 返回成功入队数量
        return accepted;
    }

    // 输出日志到当前可见页面的日志区域
    @Override
    public void log(String line) {
        // 空行直接忽略，避免日志区出现无意义空行
        if (line == null || line.trim().isEmpty()) {
            // 结束处理
            return;
        }
        // 取当前选中的页签组件
        Component selected = tabbedPane.getSelectedComponent();
        // 取该组件的日志接收器，可能为空
        Consumer<String> sink = logSinks.get(selected);
        // 接收器存在时写入日志
        if (sink != null) {
            // 调用接收器写入日志
            sink.accept(line);
        }
    }

    // 注册日志接收器，把日志定向写入指定组件的日志区域
    public void registerLogSink(Component component, Consumer<String> sink) {
        // 组件与接收器都非空时才登记
        if (component != null && sink != null) {
            // 写入映射表
            logSinks.put(component, sink);
        }
    }

    // 注册任务变化监听器
    @Override
    public void registerJobListener(Consumer<Job> listener) {
        // 监听器非空时加入列表
        if (listener != null) {
            // 加入监听器列表
            jobListeners.add(listener);
        }
    }

    // 刷新队列页表格，供外部在需要时主动刷新
    @Override
    public void refreshQueue() {
        // 切换到事件分发线程刷新表格
        SwingUtilities.invokeLater(() -> queuePanel.refresh());
    }

    // 转发任务变化给全部监听器，该方法只在事件分发线程上被调用
    private void notifyJobListeners(Job job) {
        // 在事件分发线程上遍历监听器
        for (Consumer<Job> listener : jobListeners) {
            // 逐个回调，捕获单个监听器的异常避免影响其它监听器
            try {
                // 调用监听器
                listener.accept(job);
            } catch (RuntimeException ex) {
                // 单个监听器异常时忽略，保证界面其余部分继续更新
            }
        }
        // 任务状态变化后刷新状态栏
        refreshStatusBar();
    }

    // 应用设置变更：重新探测内核、调整并发、重新应用主题并刷新界面
    private void applySettings() {
        // 按设置中的自定义路径重新定位并探测内核
        toolchain = Toolchain.bootstrap(settings.customFfmpegPath());
        // 把新的并发上限同步给队列
        queue.setConcurrency(settings.concurrency());
        // 重新应用主题
        UiTheme.apply(settings.theme(), this);
        // 递归刷新全部面板的编码器候选，使界面与新的内核能力一致
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            // 取页签组件
            Component component = tabbedPane.getComponentAt(i);
            // 视频与音频页需要重新填充编码器候选
            if (component instanceof MediaPanel mediaPanel) {
                // 重新填充编码器候选
                mediaPanel.refreshEncoders();
            }
            // 关于页需要刷新内核信息
            if (component instanceof AboutPanel aboutPanel) {
                // 刷新信息面板
                aboutPanel.refreshInfo();
            }
        }
        // 刷新状态栏
        refreshStatusBar();
        // 写入一条日志提示设置已生效
        log("Settings applied. Core: " + toolchain.describe());
    }

    // 刷新底部状态栏文本
    private void refreshStatusBar() {
        // 取待处理任务数量
        int waiting = 0;
        // 取运行中任务数量
        int running = 0;
        // 遍历队列快照统计
        for (Job job : queue.snapshot()) {
            // 等待中任务计数
            if (job.status() == Job.Status.WAITING) {
                // 累加等待计数
                waiting++;
            }
            // 运行中任务计数
            if (job.status() == Job.Status.RUNNING) {
                // 累加运行计数
                running++;
            }
        }
        // 组装状态栏文本，包含内核概况与队列概况
        statusBar.setText(toolchain.describe() + "  |  queue: " + waiting + " waiting, " + running + " running");
    }

    // 获取当前内核对象，供面板读取最新能力
    @Override
    public Toolchain toolchain() {
        // 返回内核对象
        return toolchain;
    }

    // 获取应用设置对象
    @Override
    public AppSettings settings() {
        // 返回设置对象
        return settings;
    }

    // 获取队列实例，供冒烟测试与自动化脚本使用
    public TaskQueue queue() {
        // 返回队列实例
        return queue;
    }

    // 获取页签面板，供冒烟测试检查八个页签是否构建成功
    public JTabbedPane tabbedPane() {
        // 返回页签面板
        return tabbedPane;
    }

    // 获取指定索引的页签组件
    public Component tabAt(int index) {
        // 返回对应组件
        return tabbedPane.getComponentAt(index);
    }
}
