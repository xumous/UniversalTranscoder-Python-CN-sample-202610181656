// *****************************************************************************
// aboutpage.h —— About 标签页。
// 展示：应用名、版本号、作者、内核版本信息、能力摘要与 PDF 后端说明。
// 设计取向：只展示“真实探测到的信息”，不写任何好看但无法验证的内容——
//           例如 PDF→图片 是否可用，一律由编译期开关决定的运行时查询给出结论。
// *****************************************************************************
#pragma once

// QWidget：页面基类。
#include <QWidget>

// 关于页面。
class AboutPage : public QWidget
{
    Q_OBJECT
public:
    // 构造。
    explicit AboutPage(QWidget *parent = nullptr);

private slots:
    // 刷新页面上的内核信息（用户更换内核后可点击刷新）。
    void onRefresh();

private:
    // 构建界面。
    void buildUi();

    // 更新信息文本。
    void updateInfo();
};
