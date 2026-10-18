// *****************************************************************************
// errors.cpp —— 错误分类的实现。
// 实现要点：
//   1) 模式表按“越具体越靠前”的顺序排列，避免通用模式抢先命中导致误判。
//      （例如 HW_ACCEL 的匹配放在 BAD_ARGS 之后，因为 "nvenc" 也常出现在参数错误文本里。）
//   2) 错误摘要只截取最后 8 行并限制总长度，防止超长文本把界面撑爆。
//   3) 可自动补救的两类错误在诊断结果中直接带上补救参数，由任务队列负责执行第二次尝试。
// *****************************************************************************
#include "errors.h"

// QRegularExpression：执行忽略大小写的模式匹配。
#include <QRegularExpression>
// QStringList：拆分错误文本的行。
#include <QStringList>
// QVector：承载模式表。
#include <QVector>
// QHash：承载错误码到短名的映射。
#include <QHash>

// 模式表的一项。
struct ErrorPattern
{
    // 匹配用的正则表达式。
    QString pattern;
    // 错误码。
    QString code;
    // 英文标题。
    QString title;
    // 英文建议。
    QString advice;
    // 是否可通过自动补救重试。
    bool recoverable;
};

// 错误模式表。
static const QVector<ErrorPattern>& errorPatterns()
{
    // 函数内静态表：只构造一次。
    static const QVector<ErrorPattern> table = {
        // 输入文件损坏或无法识别（moov 缺失是最典型的症状）。
        {
            QStringLiteral("moov atom not found|Invalid data found when processing input"),
            QStringLiteral("BAD_INPUT"),
            QStringLiteral("Input file is damaged or not recognized"),
            QStringLiteral(
                "The source may be corrupted, or its extension does not match its real content. Repair it or try another file."),
            false
        },
        // 文件不存在或无法读取。
        {
            QStringLiteral("No such file or directory|could not find codec parameters"),
            QStringLiteral("FILE_MISSING"),
            QStringLiteral("File missing or unreadable"),
            QStringLiteral("Make sure the file still exists, has not been moved, and is readable by this program."),
            false
        },
        // 读写权限不足。
        {
            QStringLiteral("Permission denied|Access is denied"),
            QStringLiteral("PERMISSION"),
            QStringLiteral("No read/write permission"),
            QStringLiteral(
                "The output folder may be locked or read-only. Choose another folder, or close the program holding the file."),
            false
        },
        // 参数或编码器不被当前内核支持。
        {
            QStringLiteral("Invalid argument|Error number -22|Invalid encoder|Unknown encoder"),
            QStringLiteral("BAD_ARGS"),
            QStringLiteral("Unsupported argument or encoder"),
            QStringLiteral("Change the encoder/container combination, or point Settings to a different FFmpeg build."),
            false
        },
        // 编码器初始化失败（分辨率/像素格式不被支持是常见原因）。
        {
            QStringLiteral("Conversion failed|Error while opening encoder|Error initializing output stream"),
            QStringLiteral("ENCODER_INIT"),
            QStringLiteral("Encoder initialization failed"),
            QStringLiteral(
                "Often caused by an unsupported resolution or pixel format. Try yuv420p, or select a general encoder such as libx264."),
            false
        },
        // 磁盘空间不足。
        {
            QStringLiteral("No space left on device|disk full"),
            QStringLiteral("NO_SPACE"),
            QStringLiteral("Not enough disk space"),
            QStringLiteral("Free up space on the target disk, or write to a folder on a roomier drive."),
            false
        },
        // 奇数分辨率：H.264/H.265 要求宽高为偶数。
        {
            QStringLiteral("width not divisible by 2|height not divisible by 2"),
            QStringLiteral("ODD_DIMENSION"),
            QStringLiteral("Odd resolution (width or height is not divisible by 2)"),
            QStringLiteral(
                "H.264/H.265 require even dimensions. The app will pad to the next even size and retry automatically."),
            true
        },
        // 输出不含任何流（容器与内容不匹配）。
        {
            QStringLiteral("does not contain any stream|Output file does not contain any stream"),
            QStringLiteral("NO_STREAM"),
            QStringLiteral("Output contains no media stream"),
            QStringLiteral(
                "The chosen container cannot hold the source streams (for example a video inside an audio-only container). Pick another format."),
            false
        },
        // 输出文件已存在（在未开启覆盖时会出现）。
        {
            QStringLiteral("already exists. Overwrite|File .* already exists"),
            QStringLiteral("OUTPUT_EXISTS"),
            QStringLiteral("Output file already exists"),
            QStringLiteral("Enable overwrite, or let the app append a sequence number automatically."),
            false
        },
        // 缺少解码器。
        {
            QStringLiteral("Unknown decoder|Decoder .* not found"),
            QStringLiteral("NO_DECODER"),
            QStringLiteral("Missing decoder"),
            QStringLiteral(
                "The source uses a codec this FFmpeg build does not include. Switch to another build, or convert through a common format first."),
            false
        },
        // 硬件加速失败：这类错误的唯一有效补救是“整套换成软件编码器”，而不是加参数。
        {
            QStringLiteral("Hardware|nvenc|qsv|vaapi|amf|d3d11va"),
            QStringLiteral("HW_ACCEL"),
            QStringLiteral("Hardware acceleration failed"),
            QStringLiteral(
                "The GPU driver or hardware encoder is unavailable. The app will rebuild the command with a software encoder and retry."),
            true
        },
        // 操作超时。
        {
            QStringLiteral("Timed out|timeout|Operation timed out"),
            QStringLiteral("TIMEOUT"),
            QStringLiteral("Operation timed out"),
            QStringLiteral(
                "The file may be very large or the disk very slow. Retry, or choose a faster preset / lower bitrate."),
            false
        },
        // 码率取值不合法。
        {
            QStringLiteral("Invalid .* bitrate|bitrate .* out of range"),
            QStringLiteral("BAD_BITRATE"),
            QStringLiteral("Invalid bitrate value"),
            QStringLiteral("Use a sane range: video usually 0.2-50 Mb/s, audio 32-512 kb/s."),
            false
        },
        // 流选择与实际流不匹配。
        {
            QStringLiteral("Stream map .* matches no streams|Invalid stream specifier"),
            QStringLiteral("BAD_MAP"),
            QStringLiteral("Stream mapping does not match the source"),
            QStringLiteral(
                "The source may not have the audio/subtitle track you selected. Switch stream selection back to automatic."),
            false
        },
        // 字幕处理失败。
        {
            QStringLiteral("Subtitle|subtitle|Subtitles"),
            QStringLiteral("SUBTITLE"),
            QStringLiteral("Subtitle processing failed"),
            QStringLiteral(
                "The subtitle encoding or format is unsupported. Re-save it as UTF-8 SRT, or turn subtitle burning off."),
            false
        }
    };
    // 返回静态表。
    return table;
}

// 生成诊断结果。
Diagnosis classifyError(const QString& stderrText, int returnCode)
{
    // 结果对象。
    Diagnosis diag;
    // 空值保护：nullptr 传入时当作空字符串处理。
    const QString text = stderrText.isNull() ? QString() : stderrText;
    // ---- 提取“原始错误摘要” ----
    // 按行拆分并去掉空行；ffmpeg 真正的原因通常在最后几行。
    const QStringList allLines = text.split(QRegularExpression(QStringLiteral("[\\r\\n]+")), Qt::SkipEmptyParts);
    // 只保留最后 8 行。
    const int startIdx = qMax(0, allLines.size() - 8);
    // 逐行拼接成摘要。
    QStringList tail;
    // 从 startIdx 开始收集。
    for (int i = startIdx; i < allLines.size(); ++i)
    {
        // 追加该行（去掉首尾空白）。
        tail << allLines.at(i).trimmed();
    }
    // 拼接后限制总长度为 1500 字符，避免界面被超长文本撑爆。
    const QString joined = tail.join(QLatin1Char('\n'));
    // 超出长度则只保留末尾部分（错误原因总是在最后）。
    diag.detail = joined.size() > 1500 ? joined.right(1500) : joined;

    // ---- 逐一匹配模式表 ----
    const QVector<ErrorPattern>& table = errorPatterns();
    // 遍历模式。
    for (const ErrorPattern& pat : table)
    {
        // 编译正则；损坏的模式不应让整个诊断崩掉，因此捕获异常式的写法改用构造检查。
        const QRegularExpression re(pat.pattern, QRegularExpression::CaseInsensitiveOption);
        // 正则无效时跳过该条。
        if (!re.isValid()) { continue; }
        // 在错误文本中查找匹配。
        if (re.match(text).hasMatch())
        {
            // 记录错误码。
            diag.code = pat.code;
            // 记录英文标题。
            diag.title = pat.title;
            // 记录英文建议。
            diag.advice = pat.advice;
            // 记录是否可自动补救。
            diag.recoverable = pat.recoverable;
            // 针对可自动补救的两类错误，给出具体的补救手段。
            if (pat.code == QStringLiteral("ODD_DIMENSION"))
            {
                // 奇数分辨率：追加 pad 滤镜把画面垫到下一个偶数尺寸。
                diag.retryArgs = QStringList{QStringLiteral("-vf"), QStringLiteral("pad=ceil(iw/2)*2:ceil(ih/2)*2")};
            }
            else if (pat.code == QStringLiteral("HW_ACCEL"))
            {
                // 硬件加速失败：仅追加 -hwaccel none 是不够的（那只关闭硬件解码），
                // 真正有效的做法是把编码器整体换成软件实现，因此这里用一个特殊标记占位，
                // 由任务队列识别该标记并调用“软件编码回退”重建整套命令。
                diag.retryArgs = QStringList{QStringLiteral("__SW_FALLBACK__")};
            }
            // 命中即返回。
            return diag;
        }
    }

    // ---- 未命中任何模式：给出通用诊断 ----
    // 通用建议（英文）。
    const QString genericAdvice =
        QStringLiteral(
            "The exact cause could not be identified automatically. Expand the raw error below, or switch the log level to DEBUG in Settings and retry.");
    // 返回码为 0 却进入诊断流程属于异常情况（通常是产物为空），单独提示。
    if (returnCode == 0)
    {
        // 构造特殊诊断。
        diag.code = QStringLiteral("UNKNOWN");
        // 标题说明“返回码正常但输出异常”。
        diag.title = QStringLiteral("Output looks abnormal although the process exited normally");
        // 建议文本。
        diag.advice = genericAdvice;
        // 返回。
        return diag;
    }
    // 常规的“原因未识别”诊断。
    diag.code = QStringLiteral("UNKNOWN");
    // 标题。
    diag.title = QStringLiteral("Conversion failed (cause not identified)");
    // 建议。
    diag.advice = genericAdvice;
    // 返回。
    return diag;
}

// 错误码短名映射。
QString errorShortName(const QString& code)
{
    // 静态映射表（值为英文，用于任务列表展示）。
    static const QHash<QString, QString> map = {
        {QStringLiteral("BAD_INPUT"), QStringLiteral("Damaged input")},
        {QStringLiteral("FILE_MISSING"), QStringLiteral("File missing")},
        {QStringLiteral("PERMISSION"), QStringLiteral("Permission denied")},
        {QStringLiteral("BAD_ARGS"), QStringLiteral("Unsupported args")},
        {QStringLiteral("ENCODER_INIT"), QStringLiteral("Encoder init failed")},
        {QStringLiteral("NO_SPACE"), QStringLiteral("Disk full")},
        {QStringLiteral("ODD_DIMENSION"), QStringLiteral("Odd resolution")},
        {QStringLiteral("NO_STREAM"), QStringLiteral("No valid stream")},
        {QStringLiteral("OUTPUT_EXISTS"), QStringLiteral("Output exists")},
        {QStringLiteral("NO_DECODER"), QStringLiteral("Missing decoder")},
        {QStringLiteral("HW_ACCEL"), QStringLiteral("Hardware accel failed")},
        {QStringLiteral("TIMEOUT"), QStringLiteral("Timeout")},
        {QStringLiteral("BAD_BITRATE"), QStringLiteral("Invalid bitrate")},
        {QStringLiteral("BAD_MAP"), QStringLiteral("Stream map mismatch")},
        {QStringLiteral("SUBTITLE"), QStringLiteral("Subtitle failed")},
        {QStringLiteral("UNKNOWN"), QStringLiteral("Unknown error")}
    };
    // 命中则返回映射值，否则返回通用“未知错误”。
    return map.value(code, QStringLiteral("Unknown error"));
}