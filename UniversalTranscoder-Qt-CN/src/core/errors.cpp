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
            QStringLiteral("输入文件已损坏或无法识别"),
            QStringLiteral("源文件可能已损坏，或扩展名与实际内容不符。请修复该文件或改用其它文件。"),
            false
        },
        // 文件不存在或无法读取。
        {
            QStringLiteral("No such file or directory|could not find codec parameters"),
            QStringLiteral("FILE_MISSING"),
            QStringLiteral("文件缺失或无法读取"),
            QStringLiteral("请确认文件仍然存在、未被移动，并且可被本程序读取。"),
            false
        },
        // 读写权限不足。
        {
            QStringLiteral("Permission denied|Access is denied"),
            QStringLiteral("PERMISSION"),
            QStringLiteral("没有读写权限"),
            QStringLiteral("输出目录可能被占用或为只读。请更换目录，或关闭正占用该文件的程序。"),
            false
        },
        // 参数或编码器不被当前内核支持。
        {
            QStringLiteral("Invalid argument|Error number -22|Invalid encoder|Unknown encoder"),
            QStringLiteral("BAD_ARGS"),
            QStringLiteral("参数或编码器不受支持"),
            QStringLiteral("请更换编码器与容器组合，或在设置页指定另一个 FFmpeg 内核。"),
            false
        },
        // 编码器初始化失败（分辨率/像素格式不被支持是常见原因）。
        {
            QStringLiteral("Conversion failed|Error while opening encoder|Error initializing output stream"),
            QStringLiteral("ENCODER_INIT"),
            QStringLiteral("编码器初始化失败"),
            QStringLiteral("常见原因是分辨率或像素格式不受支持。可尝试 yuv420p，或选择 libx264 这类通用编码器。"),
            false
        },
        // 磁盘空间不足。
        {
            QStringLiteral("No space left on device|disk full"),
            QStringLiteral("NO_SPACE"),
            QStringLiteral("磁盘空间不足"),
            QStringLiteral("请清理目标磁盘空间，或改写到空间更充裕的磁盘目录。"),
            false
        },
        // 奇数分辨率：H.264/H.265 要求宽高为偶数。
        {
            QStringLiteral("width not divisible by 2|height not divisible by 2"),
            QStringLiteral("ODD_DIMENSION"),
            QStringLiteral("奇数分辨率（宽或高不能被 2 整除）"),
            QStringLiteral("H.264/H.265 要求宽高为偶数。程序会自动补边到下一个偶数尺寸并重试。"),
            true
        },
        // 输出不含任何流（容器与内容不匹配）。
        {
            QStringLiteral("does not contain any stream|Output file does not contain any stream"),
            QStringLiteral("NO_STREAM"),
            QStringLiteral("输出不含任何媒体流"),
            QStringLiteral("所选容器无法承载源文件的流（例如把视频放进纯音频容器）。请改选其它格式。"),
            false
        },
        // 输出文件已存在（在未开启覆盖时会出现）。
        {
            QStringLiteral("already exists. Overwrite|File .* already exists"),
            QStringLiteral("OUTPUT_EXISTS"),
            QStringLiteral("输出文件已存在"),
            QStringLiteral("请允许覆盖，或让程序自动追加序号。"),
            false
        },
        // 缺少解码器。
        {
            QStringLiteral("Unknown decoder|Decoder .* not found"),
            QStringLiteral("NO_DECODER"),
            QStringLiteral("缺少解码器"),
            QStringLiteral("源文件使用了当前 FFmpeg 内核未包含的编码。请更换内核，或先经通用格式中转。"),
            false
        },
        // 硬件加速失败：这类错误的唯一有效补救是“整套换成软件编码器”，而不是加参数。
        {
            QStringLiteral("Hardware|nvenc|qsv|vaapi|amf|d3d11va"),
            QStringLiteral("HW_ACCEL"),
            QStringLiteral("硬件加速失败"),
            QStringLiteral("GPU 驱动或硬件编码器不可用。程序将改用软件编码器重建命令并重试。"),
            true
        },
        // 操作超时。
        {
            QStringLiteral("Timed out|timeout|Operation timed out"),
            QStringLiteral("TIMEOUT"),
            QStringLiteral("操作超时"),
            QStringLiteral("文件可能过大或磁盘过慢。请重试，或选择更快的预设 / 更低的码率。"),
            false
        },
        // 码率取值不合法。
        {
            QStringLiteral("Invalid .* bitrate|bitrate .* out of range"),
            QStringLiteral("BAD_BITRATE"),
            QStringLiteral("码率取值非法"),
            QStringLiteral("请使用合理范围：视频通常 0.2-50 Mb/s，音频 32-512 kb/s。"),
            false
        },
        // 流选择与实际流不匹配。
        {
            QStringLiteral("Stream map .* matches no streams|Invalid stream specifier"),
            QStringLiteral("BAD_MAP"),
            QStringLiteral("流映射与源文件不匹配"),
            QStringLiteral("源文件可能没有你选择的音频/字幕轨道。请把流选择改回自动。"),
            false
        },
        // 字幕处理失败。
        {
            QStringLiteral("Subtitle|subtitle|Subtitles"),
            QStringLiteral("SUBTITLE"),
            QStringLiteral("字幕处理失败"),
            QStringLiteral("字幕编码或格式不受支持。请另存为 UTF-8 的 SRT，或关闭字幕烧录。"),
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
        QStringLiteral("无法自动识别确切原因。请展开下方的原始错误，或在设置页把日志级别改为 DEBUG 后重试。");
    // 返回码为 0 却进入诊断流程属于异常情况（通常是产物为空），单独提示。
    if (returnCode == 0)
    {
        // 构造特殊诊断。
        diag.code = QStringLiteral("UNKNOWN");
        // 标题说明“返回码正常但输出异常”。
        diag.title = QStringLiteral("进程正常退出，但输出异常");
        // 建议文本。
        diag.advice = genericAdvice;
        // 返回。
        return diag;
    }
    // 常规的“原因未识别”诊断。
    diag.code = QStringLiteral("UNKNOWN");
    // 标题。
    diag.title = QStringLiteral("转换失败（原因未识别）");
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
        {QStringLiteral("BAD_INPUT"), QStringLiteral("输入损坏")},
        {QStringLiteral("FILE_MISSING"), QStringLiteral("文件缺失")},
        {QStringLiteral("PERMISSION"), QStringLiteral("无权限")},
        {QStringLiteral("BAD_ARGS"), QStringLiteral("参数不支持")},
        {QStringLiteral("ENCODER_INIT"), QStringLiteral("编码器初始化失败")},
        {QStringLiteral("NO_SPACE"), QStringLiteral("磁盘已满")},
        {QStringLiteral("ODD_DIMENSION"), QStringLiteral("奇数分辨率")},
        {QStringLiteral("NO_STREAM"), QStringLiteral("无有效流")},
        {QStringLiteral("OUTPUT_EXISTS"), QStringLiteral("输出已存在")},
        {QStringLiteral("NO_DECODER"), QStringLiteral("缺少解码器")},
        {QStringLiteral("HW_ACCEL"), QStringLiteral("硬件加速失败")},
        {QStringLiteral("TIMEOUT"), QStringLiteral("超时")},
        {QStringLiteral("BAD_BITRATE"), QStringLiteral("码率非法")},
        {QStringLiteral("BAD_MAP"), QStringLiteral("流映射不匹配")},
        {QStringLiteral("SUBTITLE"), QStringLiteral("字幕处理失败")},
        {QStringLiteral("UNKNOWN"), QStringLiteral("未知错误")}
    };
    // 命中则返回映射值，否则返回通用“未知错误”。
    return map.value(code, QStringLiteral("未知错误"));
}