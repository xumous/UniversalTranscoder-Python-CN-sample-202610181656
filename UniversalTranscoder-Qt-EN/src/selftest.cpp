// *****************************************************************************
// selftest.cpp —— 自检逻辑的实现。
// 实现要点：
//   1) 源文件路径按“显式参数 → 程序目录相对路径 → 工程根目录相对路径 → 当前工作目录”
//      的顺序搜索，并把最终选中的路径打印出来（保证过程透明，绝不假装找到了文件）。
//   2) 转码使用核心命令构建器与执行器，也就是说自检跑的是与界面完全相同的代码路径，
//      因此自检通过意味着界面上的同类操作也具备可用性。
//   3) 产物校验不止“文件存在”，还会用 ffprobe 重新解析产物并统计时长与流数量，
//      以确认生成的确实是一个可识别的媒体文件（而不是零字节或损坏文件）。
// *****************************************************************************
#include "selftest.h"

// 内核、能力、探测、命令、执行、文档能力：自检要用到的全部核心模块。
#include "core/ffmpeg_locator.h"
#include "core/capabilities.h"
#include "core/hwaccel.h"
#include "core/mediainfo.h"
#include "core/commandbuilder.h"
#include "core/runner.h"
#include "core/errors.h"
#include "core/docconvert.h"
// 预设清单：自检会核对内置预设的总数与分类数量。
#include "core/presets.h"
// QCoreApplication：取程序目录。
#include <QCoreApplication>
// QFileInfo / QDir：路径解析与目录创建。
#include <QFileInfo>
#include <QDir>
// QDebug：输出进度与警告。
#include <QDebug>

// 内部辅助：把一个候选路径规范化后判断是否存在。
static bool existsAsFile(const QString& path)
{
    // 空路径不算存在。
    if (path.isEmpty()) { return false; }
    // 构造文件信息。
    const QFileInfo fi(path);
    // 必须存在且是普通文件。
    return fi.exists() && fi.isFile();
}

// 内部辅助：按固定顺序搜索样例视频，返回第一个存在的绝对路径；都找不到时返回空字符串。
static QString locateSampleVideo(const QStringList& args)
{
    // 候选列表（按优先级排列）。
    QStringList candidates;
    // 候选 1：用户在命令行显式指定的第二个参数（--selftest 之后的位置参数）。
    for (int i = 1; i < args.size(); ++i)
    {
        // 跳过以 "-" 开头的选项。
        if (args.at(i).startsWith(QLatin1Char('-'))) { continue; }
        // 加入第一个位置参数。
        candidates << args.at(i);
        // 只取一个。
        break;
    }
    // 程序目录。
    const QString appDir = QCoreApplication::applicationDirPath();
    // 候选 2：程序目录的上一级 Samples（对应工程布局：release/../Samples）。
    candidates << QDir::cleanPath(appDir + QStringLiteral("/../Samples/test_video.mp4"));
    // 候选 3：程序目录的上两级 Samples（对应工程布局：release/../../Samples）。
    candidates << QDir::cleanPath(appDir + QStringLiteral("/../../Samples/test_video.mp4"));
    // 候选 4：程序目录下的 Samples（绿色版把样例一起分发的情形）。
    candidates << QDir::cleanPath(appDir + QStringLiteral("/Samples/test_video.mp4"));
    // 候选 5：程序同级的 resources 目录下。
    candidates << QDir::cleanPath(appDir + QStringLiteral("/../resources/test_video.mp4"));
    // 候选 6：当前工作目录下的 Samples。
    candidates << QDir::cleanPath(QDir::currentPath() + QStringLiteral("/Samples/test_video.mp4"));
    // 候选 7：当前工作目录的上两级 Samples。
    candidates << QDir::cleanPath(QDir::currentPath() + QStringLiteral("/../../Samples/test_video.mp4"));
    // 逐个检查。
    for (const QString& c : candidates)
    {
        // 命中即返回绝对路径。
        if (existsAsFile(c)) { return QFileInfo(c).absoluteFilePath(); }
    }
    // 全部失败。
    return QString();
}

// 运行自检。
SelfTestResult runSelfTest(const QStringList& args)
{
    // 结果对象。
    SelfTestResult res;
    // 追加信息行的辅助 lambda（统一格式，避免散落的字符串拼接）。
    auto add = [&res](const QString& line)
    {
        // 追加到结果列表。
        res.lines << line;
    };
    // 追加分隔线。
    add(QStringLiteral("============================================================"));
    // 标题。
    add(QStringLiteral(" Universal Transcoder 1.0.0 - self test"));
    // 分隔线。
    add(QStringLiteral("============================================================"));
    // Qt 运行时版本（同时验证 Qt 库确实被正确加载）。
    add(QStringLiteral("Qt runtime version : %1").arg(QString::fromLatin1(qVersion())));
    // 程序目录（排障时最常需要的信息）。
    add(QStringLiteral("Application dir   : %1").arg(QCoreApplication::applicationDirPath()));
    // 追加空行。
    add(QString());

    // ---- 第 1 步：内核定位 ----
    add(QStringLiteral("[1/5] Locating the FFmpeg kernel (with real -version validation)"));
    // 取 ffmpeg 路径。
    const QString ffmpegPath = FFmpegLocator::instance().ffmpegPath();
    // 取 ffprobe 路径。
    const QString ffprobePath = FFmpegLocator::instance().ffprobePath();
    // 记录 ffmpeg 路径。
    add(QStringLiteral("      ffmpeg  : %1").arg(ffmpegPath.isEmpty() ? QStringLiteral("NOT FOUND") : ffmpegPath));
    // 记录 ffprobe 路径。
    add(QStringLiteral("      ffprobe : %1").arg(ffprobePath.isEmpty() ? QStringLiteral("NOT FOUND") : ffprobePath));
    // 记录版本横幅（真实验证通过才会返回横幅文本）。
    add(QStringLiteral("      version : %1").arg(FFmpegLocator::instance().versionText()));
    // 内核不完整时直接判定失败，后续步骤没有意义。
    if (ffmpegPath.isEmpty() || ffprobePath.isEmpty())
    {
        // 追加失败原因。
        add(QStringLiteral("      FAILED: ffmpeg and/or ffprobe could not be validated."));
        // 给出可执行的建议。
        add(QStringLiteral("      Put ffmpeg.exe and ffprobe.exe into 'resources/ffmpeg' next to the executable."));
        // 结束自检。
        add(QString());
        // 结论。
        add(QStringLiteral("RESULT: FAIL"));
        // 通过标志。
        res.ok = false;
        // 退出码。
        res.exitCode = 1;
        // 返回。
        return res;
    }
    // 记录内核就绪。
    add(QStringLiteral("      OK: both tools are present and validated."));
    // 空行。
    add(QString());

    // ---- 第 2 步：能力探测 ----
    add(QStringLiteral("[2/5] Probing kernel capabilities (-encoders / -formats / -hwaccels)"));
    // 取能力摘要。
    const Capabilities::Summary sum = Capabilities::instance().summary();
    // 记录编码器统计。
    add(QStringLiteral("      encoders: %1 (video %2, audio %3, subtitle %4)")
        .arg(sum.encoderTotal).arg(sum.videoEncoders).arg(sum.audioEncoders).arg(sum.subtitleEncoders));
    // 记录封装统计。
    add(QStringLiteral("      formats : %1").arg(sum.formatTotal));
    // 记录硬件加速后端。
    add(QStringLiteral("      hwaccels: %1").arg(sum.hwaccels.isEmpty()
                                                     ? QStringLiteral("none")
                                                     : sum.hwaccels.join(QLatin1Char(','))));
    // 记录可用的硬件编码器。
    const QVector<HwEncoderInfo> hw = HwAccel::availableDetail();
    // 收集名称。
    QStringList hwNames;
    // 遍历。
    for (const HwEncoderInfo& h : hw)
    {
        // 追加“名称（厂商）”。
        hwNames << QStringLiteral("%1(%2)").arg(h.name, h.vendor);
    }
    // 记录硬件编码器列表。
    add(QStringLiteral("      hardware encoders: %1").arg(
        hwNames.isEmpty() ? QStringLiteral("none") : hwNames.join(QLatin1Char(' '))));
    // 记录 PDF 后端能力（如实反映编译期限制）。
    add(QStringLiteral("      PDF backend: %1").arg(DocConvert::pdfBackendInfo()));
    // 记录预设数量（用于核对“35 套”这一需求）。
    add(QStringLiteral("      presets  : %1 (video %2 / audio %3 / image %4)")
        .arg(allPresets().size())
        .arg(presetsByCategory(QStringLiteral("video")).size())
        .arg(presetsByCategory(QStringLiteral("audio")).size())
        .arg(presetsByCategory(QStringLiteral("image")).size()));
    // 能力探测失败（编码器为 0）时判定失败：这说明内核虽在但不可用。
    if (sum.encoderTotal <= 0)
    {
        // 记录失败原因。
        add(QStringLiteral("      FAILED: the kernel reported no encoders at all."));
        // 空行。
        add(QString());
        // 结论。
        add(QStringLiteral("RESULT: FAIL"));
        // 设置标志。
        res.ok = false;
        // 退出码。
        res.exitCode = 1;
        // 返回。
        return res;
    }
    // 记录成功。
    add(QStringLiteral("      OK: the kernel answered all capability queries."));
    // 空行。
    add(QString());

    // ---- 第 3 步：定位样例素材 ----
    add(QStringLiteral("[3/5] Locating the sample video"));
    // 搜索样例。
    const QString sample = locateSampleVideo(args);
    // 找不到时判定失败（自检必须使用真实文件，不能用合成数据蒙混过关）。
    if (sample.isEmpty())
    {
        // 记录失败原因与搜索范围。
        add(QStringLiteral("      FAILED: test_video.mp4 was not found in any of the expected locations."));
        // 提示可执行的操作。
        add(QStringLiteral("      Pass a path explicitly: UniversalTranscoder.exe --selftest <video file>"));
        // 空行。
        add(QString());
        // 结论。
        add(QStringLiteral("RESULT: FAIL"));
        // 设置标志。
        res.ok = false;
        // 退出码。
        res.exitCode = 1;
        // 返回。
        return res;
    }
    // 记录选中的源文件路径。
    add(QStringLiteral("      source: %1").arg(sample));
    // 空行。
    add(QString());

    // ---- 第 4 步：真实转码 ----
    add(QStringLiteral("[4/5] Running a real transcode (MP4 / H.264 + AAC)"));
    // 探测源文件（拿到时长与流信息，供命令构建与进度换算使用）。
    const MediaInfo info = probeMedia(sample);
    // 记录探测结果。
    add(QStringLiteral("      probe : %1").arg(info.probeOk
                                                   ? info.summaryLine()
                                                   : QStringLiteral("FAILED: %1").arg(info.error)));
    // 探测失败时判定失败（后续命令一定跑不通）。
    if (!info.probeOk)
    {
        // 空行。
        add(QString());
        // 结论。
        add(QStringLiteral("RESULT: FAIL"));
        // 设置标志。
        res.ok = false;
        // 退出码。
        res.exitCode = 1;
        // 返回。
        return res;
    }
    // 组装转码设置：目标 mp4 + H.264 + AAC + 中等质量。
    TranscodeSettings st;
    // 源文件。
    st.source = sample;
    // 目标格式。
    st.targetFormat = QStringLiteral("mp4");
    // 视频编码器：优先 libx264（通用性最好）；内核没有时留空让容器默认逻辑接手。
    st.videoCodec = Capabilities::instance().supportsEncoder(QStringLiteral("libx264"))
                        ? QStringLiteral("libx264")
                        : QString();
    // 音频编码器：优先 aac。
    st.audioCodec = Capabilities::instance().supportsEncoder(QStringLiteral("aac")) ? QStringLiteral("aac") : QString();
    // 质量档位。
    st.quality = QStringLiteral("medium");
    // 使用一个较低的 CRF，缩短自检耗时（自检关注“能否跑通”，不追求画质）。
    st.crf = QStringLiteral("30");
    // 输出目录：程序目录下的 selftest_out（便于自检失败后直接查看产物）。
    st.outputDir = QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("selftest_out"));
    // 输出文件名固定，便于重复运行时的覆盖行为可预测。
    st.outputName = QStringLiteral("selftest_video.mp4");
    // 允许覆盖（自检需要可重复执行）。
    st.overwrite = true;
    // 额外参数：把输出分辨率限制到 640 宽，显著缩短自检耗时（同时也能验证 scale 滤镜链可用）。
    st.extraArgs = QStringList{QStringLiteral("-vf"), QStringLiteral("scale=640:-2")};
    // 确保输出目录存在。
    QDir().mkpath(st.outputDir);
    // 计算输出路径。
    const QString outPath = computeOutputPath(st, true);
    // 构建命令。
    const QStringList cmdArgs = buildCommand(st, &info, outPath);
    // 记录输出路径。
    add(QStringLiteral("      output: %1").arg(outPath));
    // 记录完整命令（这是自检最有价值的信息之一：出问题时可直接复制到命令行复现）。
    add(QStringLiteral("      command: %1").arg(describeCommand(cmdArgs)));

    // 构造执行器并执行。
    Runner runner;
    // 记录最后一个进度百分比（用于结论展示）。
    double lastPercent = 0.0;
    // 记录已经打印过的里程碑，避免刷屏。
    int lastMilestone = 0;
    // 连接进度信号：由于 Runner 与自检在同一线程，这里是直接调用，无需注册元类型。
    QObject::connect(&runner, &Runner::progressChanged, &runner, [&lastPercent, &lastMilestone](const ProgressInfo& p)
    {
        // 记录最新百分比。
        lastPercent = p.percent;
        // 每跨过 25% 打印一次，让运行者能感知“确实在推进”。
        const int milestone = int(p.percent / 25.0);
        // 有新里程碑时输出。
        if (milestone > lastMilestone && milestone <= 4)
        {
            // 更新已打印的里程碑。
            lastMilestone = milestone;
            // 输出到标准错误（与结果文本分开，避免混淆）。
            fprintf(stderr, "      ...progress %d%%\n", milestone * 25);
            // 刷新缓冲，保证长时间运行时能看到。
            fflush(stderr);
        }
    });
    // 执行转码（同步阻塞，直到结束或被取消）。
    const RunResult run = runner.run(cmdArgs, info.duration);
    // 记录退出码与耗时。
    add(QStringLiteral("      exit code: %1, elapsed: %2 s, progress reached %3%")
        .arg(run.returnCode)
        .arg(QString::number(run.elapsed, 'f', 2))
        .arg(QString::number(lastPercent, 'f', 1)));
    // 失败时记录错误诊断（给出可读原因与建议）。
    if (!run.ok())
    {
        // 用错误分类模块生成诊断。
        const Diagnosis diag = classifyError(run.stderrText, run.returnCode);
        // 记录标题。
        add(QStringLiteral("      FAILED: %1").arg(diag.title));
        // 记录建议。
        add(QStringLiteral("      advice: %1").arg(diag.advice));
        // 记录原始错误尾部（截取，避免超长）。
        add(QStringLiteral("      detail: %1").arg(
            QString(diag.detail).replace(QLatin1Char('\n'), QStringLiteral(" | "))));
        // 空行。
        add(QString());
        // 结论。
        add(QStringLiteral("RESULT: FAIL"));
        // 设置标志。
        res.ok = false;
        // 退出码。
        res.exitCode = 1;
        // 返回。
        return res;
    }
    // 记录产物大小。
    add(QStringLiteral("      output size: %1 bytes (%2 KB)").arg(run.outSize).arg(run.outSize / 1024));
    // 空行。
    add(QString());

    // ---- 第 5 步：校验产物 ----
    add(QStringLiteral("[5/5] Verifying the produced file"));
    // 重新用 ffprobe 解析产物。
    const MediaInfo outInfo = probeMedia(outPath);
    // 记录产物的探测摘要。
    add(QStringLiteral("      re-probe: %1").arg(outInfo.probeOk
                                                     ? outInfo.summaryLine()
                                                     : QStringLiteral("FAILED: %1").arg(outInfo.error)));
    // 记录流数量。
    add(QStringLiteral("      streams : %1 (video %2, audio %3)")
        .arg(outInfo.streams.size()).arg(outInfo.videoStreams().size()).arg(outInfo.audioStreams().size()));
    // 三项硬性判定：文件存在、非空、且能被重新解析出至少一条流。
    const QFileInfo outFileInfo(outPath);
    // 文件存在且大小大于 0。
    const bool fileOk = outFileInfo.exists() && outFileInfo.size() > 0;
    // 能被解析出流。
    const bool streamOk = outInfo.probeOk && !outInfo.streams.isEmpty();
    // 综合判定。
    const bool verified = fileOk && streamOk && run.outSize > 0;
    // 记录结论。
    add(QStringLiteral("      file exists and is non-empty: %1").arg(
        fileOk ? QStringLiteral("yes") : QStringLiteral("no")));
    // 记录可解析性。
    add(QStringLiteral("      recognizable media file  : %1").arg(
        streamOk ? QStringLiteral("yes") : QStringLiteral("no")));
    // 空行。
    add(QString());

    // ---- 结论 ----
    if (verified)
    {
        // 通过。
        add(QStringLiteral("RESULT: PASS - kernel validated and a real transcode produced a usable file."));
        // 设置标志。
        res.ok = true;
        // 退出码。
        res.exitCode = 0;
    }
    else
    {
        // 失败。
        add(QStringLiteral("RESULT: FAIL - the transcode finished but the output could not be verified."));
        // 设置标志。
        res.ok = false;
        // 退出码。
        res.exitCode = 1;
    }
    // 返回结果。
    return res;
}