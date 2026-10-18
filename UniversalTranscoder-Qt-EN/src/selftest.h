// *****************************************************************************
// selftest.h —— 无人值守自检（--selftest 命令行分支）。
// 目的：在没有界面的环境下，一次性验证两件最核心的事情：
//   1) 内核自检：ffmpeg / ffprobe 是否可定位、可运行，能力矩阵是否能真实探测；
//   2) 一次真实转码：把样例视频转成 mp4，并校验产物确实存在且非空。
// 返回结构包含“是否通过”和“全部结果文本行”，由 main.cpp 负责打印与写日志文件，
// 这样自检逻辑本身不依赖控制台，便于将来复用到其它入口。
// *****************************************************************************
#pragma once

// QString / QStringList：结果文本。
#include <QString>
#include <QStringList>

// 自检结果。
struct SelfTestResult
{
    // 是否全部通过。
    bool ok = false;
    // 全部信息行（按顺序输出即可，无需再做格式化）。
    QStringList lines;
    // 退出码（0 表示通过，非 0 表示失败）。
    int exitCode = 1;
};

// 运行自检；args 为命令行参数（用于允许用户指定源文件与输出目录）。
SelfTestResult runSelfTest(const QStringList& args);