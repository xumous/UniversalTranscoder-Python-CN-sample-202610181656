// *****************************************************************************
// queuepage.h —— Queue 标签页（任务队列视图与控制）。
// 定位：这是整个软件“可观测性”的核心页面——所有由 Video/Audio/Image 页提交的任务
//       都在这里集中展示状态、进度、速度与剩余时间，并从这里统一调度。
// 线程安全：本页面所有槽函数都由主线程调用（队列信号会自动排队到主线程），
//           因此可以安全地直接操作控件。
// *****************************************************************************
#pragma once

// QWidget：页面基类。
#include <QWidget>
// QString：文本。
#include <QString>
// 任务队列。
#include "core/taskqueue.h"

// 前置声明的控件类型。
class QTableWidget; // 任务表格。
class QLabel; // 汇总标签。
class QPushButton; // 按钮。

// 队列页面。
class QueuePage : public QWidget
{
    Q_OBJECT

public:
    // 构造。
    explicit QueuePage(TaskQueue* queue, QWidget* parent = nullptr);

    signals :
    // 向上报告状态文本。
    
    void statusMessage(const QString& text);

public
    slots :
    // 新增任务时刷新表格。
    
    void onTaskAdded(const QString& tid);

    // 任务状态变化时刷新该行。
    void onTaskChanged(const QString& tid);

    // 任务进度变化时刷新该行的进度、速度与剩余时间。
    void onTaskProgress(const QString& tid, double percent, double speed, double eta);

    // 全部任务结束时刷新汇总。
    void onAllFinished();

private
    slots :
    // 启动调度。
    
    void onStart();

    // 暂停调度。
    void onPause();

    // 恢复调度。
    void onResume();

    // 停止并取消全部。
    void onStop();

    // 清空已完成任务。
    void onClear();

    // 移除选中任务。
    void onRemoveSelected();

    // 打开选中任务的输出目录。
    void onOpenOutputDir();

private:
    // 构建界面。
    void buildUi();

    // 重建整张表格（任务集合发生变化时使用）。
    void rebuildTable();

    // 刷新某一行（按任务 ID 定位）。
    void refreshRow(const QString& tid);

    // 刷新顶部汇总标签。
    void refreshSummary();

    // 任务在表格中的行号；找不到返回 -1。
    int rowOf(const QString& tid) const;

    // 设置某个单元格的文本（单元格不存在时自动创建，避免空指针判断散落各处）。
    void setCellText(int row, int column, const QString& text);

    // 任务队列。
    TaskQueue* m_queue = nullptr;
    // 任务表格。
    QTableWidget* m_table = nullptr;
    // 汇总标签。
    QLabel* m_summaryLabel = nullptr;
};