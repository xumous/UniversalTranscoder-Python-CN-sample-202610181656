package com.xu.universaltranscoder.ui;

// 引入任务类型

import com.xu.universaltranscoder.core.Job;
// 引入任务队列类型
import com.xu.universaltranscoder.core.TaskQueue;

// 引入 Swing 组件
import javax.swing.BorderFactory;
// 引入按钮
import javax.swing.JButton;
// 引入面板
import javax.swing.JPanel;
// 引入滚动面板
import javax.swing.JScrollPane;
// 引入表格
import javax.swing.JTable;
// 引入表格模型基类，用于自定义只读模型
import javax.swing.table.AbstractTableModel;
// 引入定时器，用于周期性刷新表格
import javax.swing.Timer;

// 引入布局管理器
import java.awt.BorderLayout;
// 引入流式布局
import java.awt.FlowLayout;
// 引入桌面工具类，用于打开输出目录
import java.awt.Desktop;
// 引入文件类
import java.io.File;
// 引入路径
import java.nio.file.Path;
// 引入列表容器
import java.util.ArrayList;
// 引入列表接口
import java.util.List;

// 关键字 class：QueuePanel 展示任务队列并提供开始、暂停、继续、停止、清理与移除操作
// 之所以用定时轮询刷新而不是每次事件都重绘整表：队列在高频上报进度时会产生大量事件，节流刷新可以显著降低界面负担
public final class QueuePanel extends JPanel {

    // 应用外壳引用
    private final AppShell shell;
    // 任务队列引用
    private final TaskQueue queue;
    // 当前表格数据快照，供表格模型读取
    private final List<Job> rows = new ArrayList<>();
    // 自定义只读表格模型
    private final AbstractTableModel model;
    // 表格控件
    private final JTable table = new JTable();
    // 状态标签，展示队列运行状态
    private final javax.swing.JLabel statusLabel = new javax.swing.JLabel("空闲");

    // 构造方法：接收应用外壳与队列实例
    public QueuePanel(AppShell shell, TaskQueue queue) {
        // 保存应用外壳引用
        this.shell = shell;
        // 保存队列引用
        this.queue = queue;
        // 使用边界布局
        setLayout(new BorderLayout(6, 6));
        // 设置四周留白
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // 构建表格模型，列名全部使用英文文案
        model = new AbstractTableModel() {
            // 列名数组，顺序与取值方法一致
            private final String[] columns = {"文件", "目标", "状态", "进度", "速度", "剩余"};

            // 返回行数
            @Override
            public int getRowCount() {
                // 行数即任务快照大小
                return rows.size();
            }

            // 返回列数
            @Override
            public int getColumnCount() {
                // 固定六列
                return columns.length;
            }

            // 返回列名
            @Override
            public String getColumnName(int column) {
                // 按索引返回列名
                return columns[column];
            }

            // 返回单元格值，全部转换成字符串以便统一渲染
            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                // 行索引越界时返回空串，避免并发刷新时短暂越界
                if (rowIndex < 0 || rowIndex >= rows.size()) {
                    // 返回空串
                    return "";
                }
                // 取对应任务
                Job job = rows.get(rowIndex);
                // 按列返回展示文本
                return switch (columnIndex) {
                    // 第一列展示输入文件名
                    case 0 -> job.inputName();
                    // 第二列展示目标格式
                    case 1 -> job.targetLabel();
                    // 第三列展示中文状态名，附带失败原因等描述
                    case 2 -> job.status().displayName() + (job.message().isEmpty()
                            // 描述与状态同名时只展示状态
                            || job.message().equals(job.status().displayName())
                            // 兼容描述仍为枚举名的旧情形
                            || job.message().equals(job.status().name()) ? "" : " - " + job.message());
                    // 第四列展示进度百分比
                    case 3 -> job.percentText();
                    // 第五列展示速度
                    case 4 -> job.speedText();
                    // 第六列展示剩余时间
                    default -> job.remainingText();
                };
            }
        };
        // 把模型绑定到表格
        table.setModel(model);
        // 关闭列自动重排，保持列顺序稳定
        table.getTableHeader().setReorderingAllowed(false);
        // 设置行高，提升可读性
        table.setRowHeight(22);
        // 把表格放入滚动面板并放到中央
        add(new JScrollPane(table), BorderLayout.CENTER);
        // 构建底部操作条
        add(buildActionBar(), BorderLayout.SOUTH);
        // 注册任务监听器，收到任务变化时只更新状态标签，表格交给定时器刷新
        shell.registerJobListener(job -> updateStatusLabel());
        // 创建定时器，每 500 毫秒刷新一次表格数据
        Timer timer = new Timer(500, event -> refresh());
        // 启动定时器
        timer.start();
    }

    // 构建底部操作条与状态标签
    private JPanel buildActionBar() {
        // 使用边界布局，按钮在左状态在右
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        // 按钮容器使用流式布局
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        // 创建开始按钮
        JButton startButton = new JButton("开始");
        // 绑定开始动作
        startButton.addActionListener(event -> {
            // 启动队列
            queue.start();
            // 刷新界面
            refresh();
        });
        // 创建暂停按钮
        JButton pauseButton = new JButton("暂停");
        // 绑定暂停动作
        pauseButton.addActionListener(event -> {
            // 暂停队列
            queue.pause();
            // 刷新界面
            refresh();
        });
        // 创建继续按钮
        JButton resumeButton = new JButton("继续");
        // 绑定继续动作
        resumeButton.addActionListener(event -> {
            // 继续队列
            queue.resume();
            // 刷新界面
            refresh();
        });
        // 创建停止按钮
        JButton stopButton = new JButton("停止");
        // 绑定停止动作
        stopButton.addActionListener(event -> {
            // 停止队列并取消未完成任务
            queue.stop();
            // 刷新界面
            refresh();
        });
        // 创建清理按钮
        JButton clearButton = new JButton("清理已完成");
        // 绑定清理动作
        clearButton.addActionListener(event -> {
            // 清理已完成任务
            queue.clearFinished();
            // 刷新界面
            refresh();
        });
        // 创建移除选中按钮
        JButton removeButton = new JButton("移除选中");
        // 绑定移除动作
        removeButton.addActionListener(event -> removeSelected());
        // 创建打开输出目录按钮
        JButton openButton = new JButton("打开输出目录");
        // 绑定打开动作
        openButton.addActionListener(event -> openOutputFolder());
        // 依次加入按钮
        buttons.add(startButton);
        // 加入暂停按钮
        buttons.add(pauseButton);
        // 加入继续按钮
        buttons.add(resumeButton);
        // 加入停止按钮
        buttons.add(stopButton);
        // 加入清理按钮
        buttons.add(clearButton);
        // 加入移除按钮
        buttons.add(removeButton);
        // 加入打开输出目录按钮
        buttons.add(openButton);
        // 把按钮容器放到左侧
        panel.add(buttons, BorderLayout.CENTER);
        // 把状态标签放到右侧
        panel.add(statusLabel, BorderLayout.EAST);
        // 返回操作条
        return panel;
    }

    // 刷新表格数据与状态标签
    public void refresh() {
        // 取队列任务快照
        List<Job> snapshot = queue.snapshot();
        // 同步替换行数据，保证表格模型读取到一致内容
        synchronized (rows) {
            // 清空旧数据
            rows.clear();
            // 写入新数据
            rows.addAll(snapshot);
        }
        // 通知表格模型数据已变化
        model.fireTableDataChanged();
        // 更新状态标签
        updateStatusLabel();
    }

    // 更新状态标签，展示队列运行状态与任务计数
    private void updateStatusLabel() {
        // 统计各状态数量
        int waiting = 0;
        // 运行中数量
        int running = 0;
        // 完成数量
        int completed = 0;
        // 失败数量
        int failed = 0;
        // 遍历任务快照
        for (Job job : queue.snapshot()) {
            // 按状态分类统计
            switch (job.status()) {
                // 等待中
                case WAITING -> waiting++;
                // 运行中
                case RUNNING -> running++;
                // 已完成
                case COMPLETED -> completed++;
                // 失败
                case FAILED -> failed++;
                // 其余状态不单独统计
                default -> {
                    // 无需处理
                }
            }
        }
        // 组装状态文本，包含运行状态与计数
        String state = queue.isRunning() ? (queue.isPaused() ? "已暂停" : "运行中") : "空闲";
        // 写入状态标签
        statusLabel.setText(state + " | 等待中 " + waiting + " | 转换中 " + running
                // 追加完成与失败计数
                + " | 完成 " + completed + " | 失败 " + failed);
    }

    // 移除表格中选中的任务
    private void removeSelected() {
        // 取选中行
        int[] selectedRows = table.getSelectedRows();
        // 逐个移除，注意行索引在移除后可能变化，因此先收集任务引用
        List<Job> toRemove = new ArrayList<>();
        // 遍历选中行
        for (int row : selectedRows) {
            // 行索引越界时跳过
            if (row < 0 || row >= rows.size()) {
                // 继续处理下一行
                continue;
            }
            // 收集该行对应的任务
            toRemove.add(rows.get(row));
        }
        // 逐个调用队列的移除方法
        for (Job job : toRemove) {
            // 从队列移除该任务
            queue.removeJob(job);
        }
        // 刷新界面
        refresh();
    }

    // 打开选中任务的输出目录，未选中时打开最后完成任务所在目录
    private void openOutputFolder() {
        // 取选中行
        int selectedRow = table.getSelectedRow();
        // 声明目标目录路径
        Path target = null;
        // 有选中行时按该行任务确定目录
        if (selectedRow >= 0 && selectedRow < rows.size()) {
            // 取该任务的输出路径
            Path output = rows.get(selectedRow).outputPath();
            // 输出路径存在时取其目录
            if (output != null) {
                // 输出是目录时直接使用
                target = java.nio.file.Files.isDirectory(output) ? output : output.getParent();
            }
        }
        // 未确定目录时在全部任务中倒序查找最近的输出位置
        if (target == null) {
            // 倒序遍历任务快照
            for (int i = rows.size() - 1; i >= 0; i--) {
                // 取任务输出路径
                Path output = rows.get(i).outputPath();
                // 找到有效输出即确定为目录
                if (output != null) {
                    // 输出是目录时直接使用
                    target = java.nio.file.Files.isDirectory(output) ? output : output.getParent();
                    // 找到后结束查找
                    break;
                }
            }
        }
        // 仍未确定目录时给出提示
        if (target == null) {
            // 输出日志提示用户先完成一个任务
            shell.log("暂无可用输出目录。");
            // 结束处理
            return;
        }
        // 尝试用系统文件管理器打开目录
        try {
            // 目录必须是存在的普通目录
            if (java.nio.file.Files.isDirectory(target) && Desktop.isDesktopSupported()) {
                // 调用桌面接口打开目录
                Desktop.getDesktop().open(target.toFile());
            } else {
                // 目录不存在时退回打开其父目录
                File parent = target.toFile().getParentFile();
                // 父目录存在且桌面可用时打开
                if (parent != null && parent.exists() && Desktop.isDesktopSupported()) {
                    // 打开父目录
                    Desktop.getDesktop().open(parent);
                }
            }
        } catch (Exception ex) {
            // 打开失败时记录日志，便于用户手动定位目录
            shell.log("无法打开输出目录：" + ex.getMessage() + " | 路径=" + target);
        }
    }

    // 获取表格控件，供冒烟测试检查
    public JTable table() {
        // 返回表格
        return table;
    }

    // 获取状态标签，供冒烟测试检查
    public javax.swing.JLabel statusLabel() {
        // 返回状态标签
        return statusLabel;
    }
}
