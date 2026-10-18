// *****************************************************************************
// processutil.h —— 子进程工具的公共封装（本项目所有外部进程调用的唯一入口）。
// 为什么需要单独抽出这一层：
//   1) 本项目要反复调用 ffmpeg / ffprobe（定位校验、能力探测、媒体探测、真实转码），
//      每一处都需要“不弹出黑色控制台窗口”这一 Windows 专有处理，重复写会散落各处。
//   2) 同步捕获输出的逻辑（waitForFinished + 合并两个通道）只写一次，避免行为不一致。
//   3) 用一个统一入口也便于在排障时集中开关日志。
// *****************************************************************************
#pragma once

// QStringList：承载“参数列表”形式的命令（不用命令行字符串，彻底规避引号与转义问题）。
#include <QStringList>
// QByteArray：承载子进程的原始输出字节。
#include <QByteArray>
// QProcess：真正的进程封装类。
#include <QProcess>
// QtGlobal：使用 quint32 等跨平台整数类型。
#include <QtGlobal>

// 返回 Windows 平台“不创建控制台窗口”的创建标志数值；非 Windows 返回 0。
// 之所以要自己返回数值而不是直接依赖 windows.h：让本头文件保持平台中立，
// 只有实现文件在 Windows 下才包含 windows.h。
quint32 noWindowFlag();

// 为一个即将启动的 QProcess 配置“隐藏控制台窗口”的行为。
// 传入引用而非指针，是为了让调用处代码更短、更不易写错。
void configureNoWindow(QProcess & proc);

// 同步执行一条命令并捕获“标准输出 + 标准错误”合并后的内容。
// 参数说明：
//   args        —— 完整参数列表，首元素是待执行程序路径（可能是绝对路径）。
//   mergedOut   —— 输出参数：合并后的原始字节（调用方自行按 UTF-8 解码）。
//   timeoutMs   —— 最长等待毫秒数，超时后强制结束进程，避免界面被卡死。
//   exitCode    —— 可选输出参数：进程退出码；传 nullptr 表示不关心。
//   workDir     —— 可选工作目录；为空表示继承当前进程的工作目录。
// 返回 true 表示进程成功启动并正常退出（退出码为 0），false 表示启动失败、超时或异常退出。
bool runCapture(const QStringList& args,
                QByteArray& mergedOut,
                int timeoutMs,
                int* exitCode = nullptr,
                const QString& workDir = QString());

// 同步执行一条命令并返回其“纯标准输出”内容（不合并标准错误）。
// 为什么需要与上面那个区分开：ffprobe 的 JSON 必须只取标准输出，
// 一旦把标准错误混进来，JSON 文本会被污染导致解析失败。
bool runCaptureStdout(const QStringList& args,
                      QByteArray& stdoutBytes,
                      int timeoutMs,
                      int* exitCode = nullptr);