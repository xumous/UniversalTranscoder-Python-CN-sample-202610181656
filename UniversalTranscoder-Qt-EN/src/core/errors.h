// *****************************************************************************
// errors.h —— FFmpeg 错误分类与用户可读建议。
// 背景：ffmpeg 的报错文本对普通用户极不友好（例如只有一句 "Invalid argument"、"Conversion failed!"）。
//       本模块把常见失败模式归纳为十几类，给出可读原因与建议，
//       并针对其中两类（奇数分辨率、硬件编码失败）给出“可自动补救重试”的方案。
// 设计原则：分类是“尽力而为”的启发式匹配；匹配不到时归入 UNKNOWN，绝不因此抛异常。
// 注意：本文件中会显示给用户的标题与建议一律使用英文；注释保持中文。
// *****************************************************************************
#pragma once

// QString / QStringList：承载错误文本与补救参数。
#include <QString>
#include <QStringList>
// QMetaType：让 Diagnosis 能通过跨线程的队列信号传递。
#include <QMetaType>

// 一次失败的诊断结果。
struct Diagnosis
{
    // 机器可读的错误码（如 BAD_INPUT / HW_ACCEL）。
    QString code = QStringLiteral("UNKNOWN");
    // 简短标题（英文，会显示给用户）。
    QString title = QStringLiteral("Unknown error");
    // 原始错误摘要（截取 stderr 的最后若干行）。
    QString detail;
    // 给用户的处理建议（英文）。
    QString advice;
    // 建议的自动重试附加参数（空列表表示无）。
    QStringList retryArgs;
    // 该错误是否可以通过“整套或局部调整参数”自动重试一次来补救。
    bool recoverable = false;
};

// 根据 ffmpeg 的标准错误输出生成诊断结果；任何输入都不会导致异常。
Diagnosis classifyError(const QString& stderrText, int returnCode = 1);

// 让 Diagnosis 能通过跨线程的队列信号传递。
Q_DECLARE_METATYPE(Diagnosis)

// 错误码 → 英文短名（供任务列表的 Status 列展示）。
QString errorShortName(const QString& code);