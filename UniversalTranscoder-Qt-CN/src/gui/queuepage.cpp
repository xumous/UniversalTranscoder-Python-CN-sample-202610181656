// *****************************************************************************
// queuepage.cpp —— 队列页面的实现。
// 实现要点：
//   1) 表格的第一列存储任务 ID（放在 Qt::UserRole 数据角色里），
//      这样任何一次刷新都能精确定位到对应行，不依赖行号顺序。
//   2) 进度刷新走 taskProgressTick 高频信号，只更新“该行的进度/速度/剩余时间”三列，
//      避免每次进度变化都重建整张表（那会让界面在批量任务时明显卡顿）。
//   3) 所有按钮都直接调用任务队列的公开接口，页面本身不持有任何调度状态，
//      因此关闭/重建页面不会影响正在进行的转码。
// *****************************************************************************
#include "queuepage.h"

// Qt 控件与布局。
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGroupBox>
#include <QTableWidget>
#include <QTableWidgetItem>
#include <QHeaderView>
#include <QLabel>
#include <QPushButton>
#include <QMessageBox>
#include <QDesktopServices>
#include <QUrl>
#include <QFileInfo>
#include <QDir>

// 内部辅助：把速度格式化成 "1.85x" 文本；未知时显示 "-"。
static QString formatSpeed(double speed)
{
    // 速度为正才算有效（0 表示尚未开始或无法计算）。
    if (speed > 0.0)
    {
        // 保留两位小数并附上 "x" 后缀（与 ffmpeg 的习惯一致）。
        return QStringLiteral("%1x").arg(QString::number(speed, 'f', 2));
    }
    // 未知时用占位符。
    return QStringLiteral("-");
}

// 内部辅助：把剩余时间格式化成 "mm:ss"；未知时显示 "--:--"。
static QString formatEta(double eta)
{
    // 负值表示未知（Runner 用 -1 表达）。
    if (eta < 0.0)
    {
        // 返回占位符。
        return QStringLiteral("--:--");
    }
    // 取整到秒。
    const int sec = int(eta + 0.5);
    // 分:秒 形式，固定两位。
    return QStringLiteral("%1:%2")
           .arg(sec / 60, 2, 10, QLatin1Char('0'))
           .arg(sec % 60, 2, 10, QLatin1Char('0'));
}

// 设置单元格文本。
void QueuePage::setCellText(int row, int column, const QString& text)
{
    // 取现有单元格。
    QTableWidgetItem* item = m_table->item(row, column);
    // 不存在则创建（必须显式创建，否则表格该格为空）。
    if (item == nullptr)
    {
        // 新建单元格并写入。
        m_table->setItem(row, column, new QTableWidgetItem(text));
        // 返回。
        return;
    }
    // 已存在则只更新文本（比新建控件高效得多）。
    item->setText(text);
}

// 构造。
QueuePage::QueuePage(TaskQueue* queue, QWidget* parent)
    : QWidget(parent)
      , m_queue(queue)
{
    // 构建界面。
    buildUi();
    // 绑定队列信号（自动排队到主线程）。
    connect(m_queue, &TaskQueue::taskAdded, this, &QueuePage::onTaskAdded);
    // 状态变化。
    connect(m_queue, &TaskQueue::taskChanged, this, &QueuePage::onTaskChanged);
    // 进度变化。
    connect(m_queue, &TaskQueue::taskProgressTick, this, &QueuePage::onTaskProgress);
    // 全部结束。
    connect(m_queue, &TaskQueue::allFinished, this, &QueuePage::onAllFinished);
    // 初始化界面内容。
    refreshSummary();
}

// 构建界面。
void QueuePage::buildUi()
{
    // 根布局。
    QVBoxLayout* root = new QVBoxLayout(this);
    // 边距。
    root->setContentsMargins(10, 10, 10, 10);
    // 间距。
    root->setSpacing(8);

    // 控制按钮分组。
    QGroupBox* ctlGroup = new QGroupBox(QStringLiteral("队列控制"));
    // 控制区横向布局（按钮较多，用横向排布节省纵向空间）。
    QHBoxLayout* ctlLayout = new QHBoxLayout(ctlGroup);
    // 启动按钮（主按钮）。
    QPushButton* startBtn = new QPushButton(QStringLiteral("开始"));
    // 标记为主按钮。
    startBtn->setProperty("primary", true);
    // 暂停按钮。
    QPushButton* pauseBtn = new QPushButton(QStringLiteral("暂停"));
    // 恢复按钮。
    QPushButton* resumeBtn = new QPushButton(QStringLiteral("继续"));
    // 停止按钮。
    QPushButton* stopBtn = new QPushButton(QStringLiteral("全部停止"));
    // 清空按钮。
    QPushButton* clearBtn = new QPushButton(QStringLiteral("清空已完成"));
    // 移除选中按钮。
    QPushButton* removeBtn = new QPushButton(QStringLiteral("移除选中"));
    // 打开输出目录按钮。
    QPushButton* openBtn = new QPushButton(QStringLiteral("打开输出目录"));
    // 逐个加入。
    ctlLayout->addWidget(startBtn);
    // 暂停。
    ctlLayout->addWidget(pauseBtn);
    // 恢复。
    ctlLayout->addWidget(resumeBtn);
    // 停止。
    ctlLayout->addWidget(stopBtn);
    // 清空。
    ctlLayout->addWidget(clearBtn);
    // 移除。
    ctlLayout->addWidget(removeBtn);
    // 打开目录。
    ctlLayout->addWidget(openBtn);
    // 余量留白。
    ctlLayout->addStretch(1);
    // 加入根布局。
    root->addWidget(ctlGroup);

    // 汇总标签。
    m_summaryLabel = new QLabel;
    // 允许换行。
    m_summaryLabel->setWordWrap(true);
    // 加入根布局。
    root->addWidget(m_summaryLabel);

    // 任务表格（7 列）。
    m_table = new QTableWidget;
    // 列数固定为 6：文件名 / 目标格式 / 状态 / 进度 / 速度 / 剩余时间。
    m_table->setColumnCount(6);
    // 表头文本（英文）。
    m_table->setHorizontalHeaderLabels(QStringList{
        QStringLiteral("文件名"),
        QStringLiteral("目标格式"),
        QStringLiteral("状态"),
        QStringLiteral("进度"),
        QStringLiteral("速度"),
        QStringLiteral("剩余时间")
    });
    // 交替行底色，便于横向读表。
    m_table->setAlternatingRowColors(true);
    // 只允许选中整行。
    m_table->setSelectionBehavior(QAbstractItemView::SelectRows);
    // 禁止直接编辑单元格。
    m_table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    // 允许多选（便于批量移除）。
    m_table->setSelectionMode(QAbstractItemView::ExtendedSelection);
    // 关闭内置排序：任务顺序应与提交顺序一致，排序会打乱状态感知。
    m_table->setSortingEnabled(false);
    // 隐藏行号列。
    m_table->verticalHeader()->setVisible(false);
    // 文件名列自动伸展，其余列按内容宽度。
    m_table->horizontalHeader()->setSectionResizeMode(0, QHeaderView::Stretch);
    // 目标格式列。
    m_table->horizontalHeader()->setSectionResizeMode(1, QHeaderView::ResizeToContents);
    // 状态列。
    m_table->horizontalHeader()->setSectionResizeMode(2, QHeaderView::ResizeToContents);
    // 进度列。
    m_table->horizontalHeader()->setSectionResizeMode(3, QHeaderView::ResizeToContents);
    // 速度列。
    m_table->horizontalHeader()->setSectionResizeMode(4, QHeaderView::ResizeToContents);
    // 剩余时间列。
    m_table->horizontalHeader()->setSectionResizeMode(5, QHeaderView::ResizeToContents);
    // 加入根布局（占满剩余空间）。
    root->addWidget(m_table, 1);

    // ---- 信号连接 ----
    // 启动。
    connect(startBtn, &QPushButton::clicked, this, &QueuePage::onStart);
    // 暂停。
    connect(pauseBtn, &QPushButton::clicked, this, &QueuePage::onPause);
    // 恢复。
    connect(resumeBtn, &QPushButton::clicked, this, &QueuePage::onResume);
    // 停止。
    connect(stopBtn, &QPushButton::clicked, this, &QueuePage::onStop);
    // 清空。
    connect(clearBtn, &QPushButton::clicked, this, &QueuePage::onClear);
    // 移除。
    connect(removeBtn, &QPushButton::clicked, this, &QueuePage::onRemoveSelected);
    // 打开输出目录。
    connect(openBtn, &QPushButton::clicked, this, &QueuePage::onOpenOutputDir);
}

// 行号查找。
int QueuePage::rowOf(const QString& tid) const
{
    // 遍历全部行。
    for (int row = 0; row < m_table->rowCount(); ++row)
    {
        // 第 0 列单元格存在才读取数据。
        QTableWidgetItem* item = m_table->item(row, 0);
        // 空单元格跳过。
        if (item == nullptr) { continue; }
        // 比对存储的任务 ID。
        if (item->data(Qt::UserRole).toString() == tid) { return row; }
    }
    // 未找到。
    return -1;
}

// 重建表格。
void QueuePage::rebuildTable()
{
    // 取出任务快照。
    const QVector<Task> tasks = m_queue->tasks();
    // 暂时关闭重绘，避免批量填充时界面闪烁。
    m_table->setUpdatesEnabled(false);
    // 设置行数。
    m_table->setRowCount(tasks.size());
    // 逐行填充。
    for (int row = 0; row < tasks.size(); ++row)
    {
        // 取任务。
        const Task& t = tasks.at(row);
        // 第 0 列：文件名（同时把任务 ID 存入数据角色）。
        QTableWidgetItem* nameItem = new QTableWidgetItem(t.fileName);
        // 存入任务 ID。
        nameItem->setData(Qt::UserRole, t.tid);
        // 悬停显示完整源路径。
        nameItem->setToolTip(t.settings.source);
        // 写入单元格。
        m_table->setItem(row, 0, nameItem);
        // 第 1 列：目标格式（大写显示）。
        m_table->setItem(row, 1, new QTableWidgetItem(t.settings.targetFormat.toUpper()));
        // 第 2 列：状态。
        m_table->setItem(row, 2, new QTableWidgetItem(taskStatusText(t.status)));
        // 第 3 列：进度百分比。
        m_table->setItem(row, 3, new QTableWidgetItem(QStringLiteral("%1%").arg(int(t.progress + 0.5))));
        // 第 4 列：速度。
        m_table->setItem(row, 4, new QTableWidgetItem(formatSpeed(t.speed)));
        // 第 5 列：剩余时间。
        m_table->setItem(row, 5, new QTableWidgetItem(formatEta(t.eta)));
    }
    // 恢复重绘。
    m_table->setUpdatesEnabled(true);
    // 刷新汇总。
    refreshSummary();
}

// 刷新单行。
void QueuePage::refreshRow(const QString& tid)
{
    // 定位行号。
    const int row = rowOf(tid);
    // 未找到时重建整张表（例如任务刚被加入而表格还没有该行）。
    if (row < 0)
    {
        // 重建表格。
        rebuildTable();
        // 返回。
        return;
    }
    // 取任务的最新状态。
    const Task t = m_queue->taskById(tid);
    // 任务已不存在（被清理）时重建表格。
    if (t.tid.isEmpty())
    {
        // 重建。
        rebuildTable();
        // 返回。
        return;
    }
    // 更新文件名单元格（同时保证任务 ID 仍在）。
    QTableWidgetItem* nameItem = m_table->item(row, 0);
    // 单元格不存在则补一个。
    if (nameItem == nullptr)
    {
        // 新建单元格。
        nameItem = new QTableWidgetItem(t.fileName);
        // 写入。
        m_table->setItem(row, 0, nameItem);
    }
    // 保证任务 ID 与显示名同步。
    nameItem->setData(Qt::UserRole, t.tid);
    // 更新显示名。
    nameItem->setText(t.fileName);
    // 状态列。
    setCellText(row, 2, taskStatusText(t.status));
    // 进度列。
    setCellText(row, 3, QStringLiteral("%1%").arg(int(t.progress + 0.5)));
    // 速度列。
    setCellText(row, 4, formatSpeed(t.speed));
    // 剩余时间列。
    setCellText(row, 5, formatEta(t.eta));
    // 失败时把原因写入状态列的工具提示，鼠标悬停即可看到。
    if (t.status == TaskStatus::Failed)
    {
        // 取状态单元格。
        QTableWidgetItem* stItem = m_table->item(row, 2);
        // 存在则设置提示。
        if (stItem != nullptr)
        {
            // 提示文本 = 标题 + 建议 + 原始详情。
            stItem->setToolTip(QStringLiteral("%1\n%2\n%3").arg(t.errTitle, t.errAdvice, t.errDetail));
        }
    }
    // 刷新汇总。
    refreshSummary();
}

// 刷新汇总。
void QueuePage::refreshSummary()
{
    // 取统计。
    const QMap<QString, int> s = m_queue->stats();
    // 组装文本（英文）。
    m_summaryLabel->setText(
        QStringLiteral(
            "总计：%1   |   已完成：%2   |   失败：%3   |   已跳过：%4   |   已取消：%5   |   等待中：%6   |   运行中：%7   |   最大并发：%8")
        .arg(s.value(QStringLiteral("total")))
        .arg(s.value(QStringLiteral("ok")))
        .arg(s.value(QStringLiteral("fail")))
        .arg(s.value(QStringLiteral("skip")))
        .arg(s.value(QStringLiteral("canceled")))
        .arg(s.value(QStringLiteral("waiting")))
        .arg(s.value(QStringLiteral("running")))
        .arg(m_queue->maxParallel()));
}

// 新增任务。
void QueuePage::onTaskAdded(const QString& tid)
{
    // 忽略参数的直接使用（仅为了触发刷新）。
    Q_UNUSED(tid);
    // 重建表格（新增任务会改变行数）。
    rebuildTable();
    // 向上层报告。
    emit statusMessage(QStringLiteral("已向队列加入一个新任务。"));
}

// 状态变化。
void QueuePage::onTaskChanged(const QString& tid)
{
    // 刷新该行。
    refreshRow(tid);
}

// 进度变化。
void QueuePage::onTaskProgress(const QString& tid, double percent, double speed, double eta)
{
    // 定位行号。
    const int row = rowOf(tid);
    // 找不到行时说明表格尚未包含该任务，直接返回（taskChanged 会负责补行）。
    if (row < 0) { return; }
    // 更新进度列（这是最高频的更新，只改文本而不重建控件）。
    setCellText(row, 3, QStringLiteral("%1%").arg(int(percent + 0.5)));
    // 更新速度列。
    setCellText(row, 4, formatSpeed(speed));
    // 更新剩余时间列。
    setCellText(row, 5, formatEta(eta));
}

// 全部结束。
void QueuePage::onAllFinished()
{
    // 刷新整张表，保证所有行的终态都准确。
    rebuildTable();
    // 向上层报告。
    emit statusMessage(QStringLiteral("队列中的所有任务均已结束。"));
}

// 启动。
void QueuePage::onStart()
{
    // 启动调度。
    m_queue->start();
    // 更新状态。
    emit statusMessage(QStringLiteral("队列已启动。"));
    // 刷新汇总。
    refreshSummary();
}

// 暂停。
void QueuePage::onPause()
{
    // 暂停调度（正在运行的任务会继续跑完）。
    m_queue->pause();
    // 更新状态。
    emit statusMessage(QStringLiteral("队列已暂停（正在运行的任务会先跑完）。"));
}

// 恢复。
void QueuePage::onResume()
{
    // 恢复调度。
    m_queue->resume();
    // 更新状态。
    emit statusMessage(QStringLiteral("队列已继续。"));
}

// 停止。
void QueuePage::onStop()
{
    // 停止并取消全部活动任务。
    m_queue->stopAll();
    // 刷新表格。
    rebuildTable();
    // 更新状态。
    emit statusMessage(QStringLiteral("队列已停止，所有活动任务均已取消。"));
}

// 清空已完成。
void QueuePage::onClear()
{
    // 只清空已进入终态的任务（保留等待与运行中的）。
    const int removed = m_queue->clear(false);
    // 刷新表格。
    rebuildTable();
    // 更新状态。
    emit statusMessage(QStringLiteral("已移除 %1 个已完成任务。").arg(removed));
}

// 移除选中。
void QueuePage::onRemoveSelected()
{
    // 取选中行的集合。
    const QList<QTableWidgetItem*> selected = m_table->selectedItems();
    // 用集合收集要移除的任务 ID（选中单元格会重复报告同一行）。
    QStringList tids;
    // 遍历选中单元格。
    for (QTableWidgetItem* item : selected)
    {
        // 取该行第 0 列的任务 ID。
        QTableWidgetItem* nameItem = m_table->item(item->row(), 0);
        // 空则跳过。
        if (nameItem == nullptr) { continue; }
        // 取任务 ID。
        const QString tid = nameItem->data(Qt::UserRole).toString();
        // 去重后追加。
        if (!tids.contains(tid)) { tids.append(tid); }
    }
    // 一个都没选时提示。
    if (tids.isEmpty())
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("未选择"), QStringLiteral("请先在队列表格中选择一行或多行。"));
        // 返回。
        return;
    }
    // 统计结果。
    int removed = 0;
    // 不可移除（正在运行）的任务数量。
    int blocked = 0;
    // 逐个移除。
    for (const QString& tid : tids)
    {
        // 尝试移除。
        if (m_queue->removeTask(tid))
        {
            // 计数。
            ++removed;
        }
        else
        {
            // 记录被阻止（通常是因为任务正在运行）。
            ++blocked;
        }
    }
    // 刷新表格。
    rebuildTable();
    // 更新状态。
    emit statusMessage(QStringLiteral("已移除 %1 个任务；另有 %2 个因正在运行而无法移除。").arg(removed).arg(blocked));
}

// 打开选中任务的输出目录。
void QueuePage::onOpenOutputDir()
{
    // 优先使用选中行的任务。
    QString dir;
    // 取选中项。
    const QList<QTableWidgetItem*> selected = m_table->selectedItems();
    // 有选中则取第一条。
    if (!selected.isEmpty())
    {
        // 取任务 ID。
        QTableWidgetItem* nameItem = m_table->item(selected.first()->row(), 0);
        // 存在则取任务。
        if (nameItem != nullptr)
        {
            // 取任务详情。
            const Task t = m_queue->taskById(nameItem->data(Qt::UserRole).toString());
            // 有输出路径时取其所在目录。
            if (!t.outPath.isEmpty())
            {
                // 取目录。
                dir = QFileInfo(t.outPath).absolutePath();
            }
            else if (!t.settings.outputDir.isEmpty())
            {
                // 否则用设置里的输出目录。
                dir = t.settings.outputDir;
            }
        }
    }
    // 仍然为空时退回“第一个任务的输出目录 / 源目录”。
    if (dir.isEmpty())
    {
        // 取全部任务。
        const QVector<Task> all = m_queue->tasks();
        // 有任务时推导目录。
        if (!all.isEmpty())
        {
            // 优先用显式输出目录。
            dir = all.first().settings.outputDir;
            // 为空则用源文件目录。
            if (dir.isEmpty() && !all.first().settings.source.isEmpty())
            {
                // 取源目录。
                dir = QFileInfo(all.first().settings.source).absolutePath();
            }
        }
    }
    // 目录为空无法打开。
    if (dir.isEmpty())
    {
        // 提示（英文）。
        QMessageBox::information(this, QStringLiteral("无目录"), QStringLiteral("暂时无法确定输出目录。请添加任务，或选择一行已知输出路径的任务。"));
        // 返回。
        return;
    }
    // 用系统文件管理器打开。
    QDesktopServices::openUrl(QUrl::fromLocalFile(dir));
}