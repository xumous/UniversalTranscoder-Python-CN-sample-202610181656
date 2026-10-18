package com.xu.universaltranscoder.core;

// 引入 Files，用于检测系统字体文件与字幕文件是否存在

import java.nio.file.Files;
// 引入 Path，作为输出路径、工作目录与字体路径的类型
import java.nio.file.Path;
// 引入 ArrayList，用于构造命令参数列表
import java.util.ArrayList;
// 引入 List 接口，作为参数列表的类型
import java.util.List;
// 引入 Locale，用于 String.format 固定使用点号小数点，避免系统区域设置导致小数点变逗号
import java.util.Locale;

// 关键字 class：CommandBuilder 把“用户意图 + 媒体信息”编译成 ffmpeg 的参数列表
// 之所以返回 List<String> 而不是整条命令字符串：参数列表能完全绕开 Windows 的引号转义陷阱，避免路径空格导致命令被截断
public final class CommandBuilder {

    // 常用水印字体候选路径，优先使用 Arial，缺失时依次回退到微软雅黑与黑体
    private static final String[] FONT_CANDIDATES = {
            // 英文字体兼容性最好
            "C:/Windows/Fonts/arial.ttf",
            // 中文环境下最常见的无衬线字体
            "C:/Windows/Fonts/msyh.ttc",
            // 微软雅黑的其他文件名形式
            "C:/Windows/Fonts/msyhbd.ttc",
            // 简体中文黑体
            "C:/Windows/Fonts/simhei.ttf",
            // 通用无衬线字体兜底
            "C:/Windows/Fonts/segoeui.ttf"
    };

    // 私有构造方法，禁止实例化这个纯静态命令构建器
    private CommandBuilder() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 编译命令入口：根据请求与媒体信息生成完整的 ffmpeg 参数列表
    public static BuiltCommand build(ConversionRequest request, MediaInfo info) {
        // 取得内核对象，用于判断编码器与封装支持情况
        Toolchain toolchain = Toolchain.get();
        // 按类别与目标扩展名解析封装名
        String muxer = resolveMuxer(request);
        // 解析输出文件路径，冲突时自动加序号
        Path output = OutputNamer.resolve(request);
        // 准备参数列表，首个元素需要是可执行文件路径
        List<String> args = new ArrayList<>();
        // 内核可用时使用真实路径，否则留空由执行器报错
        args.add(toolchain.ffmpeg() == null ? "ffmpeg" : toolchain.ffmpeg().toString());
        // 关闭版本横幅输出，让日志只保留有效信息
        args.add("-hide_banner");
        // 关闭标准输入读取，防止 ffmpeg 误吞界面进程的输入导致卡死
        args.add("-nostdin");
        // 允许覆盖输出文件，因为输出路径已由命名器做过冲突处理
        args.add("-y");
        // 输入文件路径必须使用绝对路径，避免工作目录变化导致找不到源文件
        args.add("-i");
        // 追加输入文件绝对路径
        args.add(request.input().toAbsolutePath().toString());
        // 解析图片目标使用的编码器，图片类别需要单独处理
        boolean isImage = request.category() == ConversionRequest.Category.IMAGE;
        // 解析视频编码器：图片类别取图片编码器，视频类别取视频编码器，音频类别为空
        String videoEncoder = null;
        // 音频类别不需要视频编码器
        if (!request.isAudioOnly()) {
            // 图片类别使用图片专用编码器映射
            videoEncoder = isImage
                    // 图片编码器按扩展名查表并在内核中校验可用性
                    ? resolveImageEncoder(request, toolchain)
                    // 视频编码器按封装候选链与硬件策略解析
                    : resolveVideoEncoder(request, toolchain, muxer);
        }
        // 解析音频编码器：图片类别不需要音频，其余类别按封装候选链解析
        String audioEncoder = null;
        // 图片类别不编码音频，直接保持为空
        if (!isImage) {
            // 按封装候选链与用户显式选择解析音频编码器
            audioEncoder = resolveAudioEncoder(request, toolchain, muxer);
        }
        // 添加流映射参数，实现“仅视频/仅音频容器”的流裁剪
        appendStreamMapping(args, request, muxer, isImage);
        // 添加滤镜参数，顺序固定为裁剪 → 缩放 → 旋转/镜像 → 帧率 → 水印 → 字幕 → 变速
        Path workingDirectory = appendFilters(args, request, info, isImage);
        // 添加视频编码参数（编码器 + 质量 + 像素格式）
        if (videoEncoder != null) {
            // 追加视频编码器选择
            args.add("-c:v");
            // 追加视频编码器名称
            args.add(videoEncoder);
            // 追加质量参数，硬件与软件走不同的参数体系
            appendVideoQuality(args, request, videoEncoder, targetVideoHeight(request, info));
            // 追加像素格式参数，提升 h264/hevc 在常见播放器上的兼容性
            if (shouldForceYuv420p(request, muxer, videoEncoder)) {
                // 强制输出 yuv420p，这是兼容性最好的 8 位 4:2:0 像素格式
                args.add("-pix_fmt");
                // 指定像素格式名称
                args.add("yuv420p");
            }
        }
        // 添加音频编码参数
        if (audioEncoder != null) {
            // 追加音频编码器选择
            args.add("-c:a");
            // 追加音频编码器名称
            args.add(audioEncoder);
            // 无损编码器不接受码率参数，只有有损编码器才追加码率
            if (!FormatTables.isLosslessAudioEncoder(audioEncoder)) {
                // 码率优先使用用户显式指定值，未指定时按质量档位推导
                int kbps = request.audioBitRateKbps() > 0
                        // 用户显式码率优先
                        ? request.audioBitRateKbps()
                        // 未指定时按质量档位推导
                        : request.quality().audioBitRateKbps();
                // 码率大于 0 时才追加码率参数，避免给无损档传 0 造成歧义
                if (kbps > 0) {
                    // 追加音频码率参数
                    args.add("-b:a");
                    // 追加码率数值并带上千比特单位
                    args.add(kbps + "k");
                }
            }
        }
        // 添加元数据处理参数，默认保留原始元数据
        appendMetadata(args, request);
        // 图片输出需要限制只写一帧并启用单文件覆盖写出
        if (isImage) {
            // 只编码一帧画面，图片类别只关心当前帧
            args.add("-frames:v");
            // 帧数固定为 1
            args.add("1");
            // 启用 image2 的单文件更新模式，允许把多帧写到同一个文件名
            args.add("-update");
            // 打开单文件更新
            args.add("1");
        }
        // 添加进度上报参数，位置必须在输出路径之前，属于输出侧选项
        args.add("-progress");
        // 进度通过标准输出以 key=value 形式上报，执行器据此解析
        args.add("pipe:1");
        // 关闭逐帧统计行，减少日志噪声并避免与进度块混淆
        args.add("-nostats");
        // 把日志级别压到 error，仅在真正出错时才输出内容，便于错误分类
        args.add("-loglevel");
        // 指定 error 级别
        args.add("error");
        // 显式指定输出封装，保证扩展名与容器严格对应，也满足图片走 image2 的要求
        args.add("-f");
        // 追加换算后的封装名
        args.add(muxer == null ? "matroska" : muxer);
        // 最后追加输出文件绝对路径
        args.add(output.toAbsolutePath().toString());
        // 返回编译结果，携带输出路径、工作目录与编码器信息
        return new BuiltCommand(args, output, workingDirectory, videoEncoder, audioEncoder, muxer);
    }

    // 重建为纯软件编码的命令，用于硬件加速失败后的自动补救
    public static BuiltCommand buildSoftwareFallback(ConversionRequest request, MediaInfo info) {
        // 关闭硬件加速标记，迫使解析逻辑选择软件编码器
        request.hardwareAcceleration(false);
        // 清除用户显式指定的硬件编码器，否则会继续沿用失败的硬件编码器
        if (request.videoEncoder() != null && FormatTables.isHardwareVideoEncoder(request.videoEncoder())) {
            // 置为空表示按目标格式重新自动挑选软件编码器
            request.videoEncoder(null);
        }
        // 标记已应用补救，阻止补救逻辑再次触发形成递归
        request.remedyApplied(true);
        // 用软件参数体系重新编译整条命令
        return build(request, info);
    }

    // 解析目标封装名：图片、音频、视频三个类别分别查各自的换算表
    public static String resolveMuxer(ConversionRequest request) {
        // 按类别分派到对应的换算方法
        return switch (request.category()) {
            // 图片类别使用图片封装表
            case IMAGE -> FormatTables.imageMuxer(request.targetExtension());
            // 音频类别使用音频封装表
            case AUDIO -> FormatTables.audioMuxer(request.targetExtension());
            // 视频类别使用视频封装表
            case VIDEO -> FormatTables.videoMuxer(request.targetExtension());
        };
    }

    // 解析图片输出编码器：按扩展名查表，并在内核不支持主编码器时依次回退
    private static String resolveImageEncoder(ConversionRequest request, Toolchain toolchain) {
        // 按扩展名取得首选图片编码器
        String preferred = FormatTables.imageEncoder(request.targetExtension());
        // 首选编码器被内核支持时直接采用
        if (preferred != null && toolchain.hasEncoder(preferred)) {
            // 返回首选编码器
            return preferred;
        }
        // webp 场景下内核可能只有内置 webp 而没有 libwebp，需要回退
        if ("libwebp".equals(preferred) && toolchain.hasEncoder("webp")) {
            // 回退到内置 webp 编码器
            return "webp";
        }
        // png 编码器通常都存在，但仍做一次判断，缺失时回退到 mjpeg
        if (toolchain.hasEncoder("png")) {
            // 回退到 png 编码器
            return "png";
        }
        // 最终兜底使用 mjpeg，几乎所有内核都自带
        return toolchain.hasEncoder("mjpeg") ? "mjpeg" : preferred;
    }

    // 解析视频编码器：优先用户选择，其次硬件策略，最后按内核能力从候选链中回退
    private static String resolveVideoEncoder(ConversionRequest request, Toolchain toolchain, String muxer) {
        // 用户显式指定且内核支持时直接尊重用户选择
        if (request.videoEncoder() != null && toolchain.hasEncoder(request.videoEncoder())) {
            // 返回用户选定的编码器
            return request.videoEncoder();
        }
        // 按目标格式取得候选编码器链，键是换算后的封装名
        List<String> candidates = FormatTables.videoCandidatesByMuxer(muxer);
        // 候选链为空说明该封装不允许视频，返回空表示不编码视频
        if (candidates.isEmpty()) {
            // 返回空表示跳过视频编码
            return null;
        }
        // 启用硬件加速时尝试按厂商优先级挑选硬件编码器
        if (request.hardwareAcceleration()) {
            // 逐个候选编码器尝试其所属编码族的硬件实现
            for (String candidate : candidates) {
                // 反推候选编码器所属的编码族
                String family = HardwareAccel.familyOf(candidate);
                // 按厂商优先级挑选该编码族的硬件编码器
                String hardware = HardwareAccel.pickEncoder(family);
                // 命中硬件编码器且内核支持时直接采用
                if (hardware != null && toolchain.hasEncoder(hardware)) {
                    // 返回硬件编码器名称
                    return hardware;
                }
            }
        }
        // 硬件路径不可用时按候选顺序选择内核支持的软件编码器
        for (String candidate : candidates) {
            // 内核支持该编码器时采用它
            if (toolchain.hasEncoder(candidate)) {
                // 返回命中的软件编码器
                return candidate;
            }
        }
        // 候选全部不可用时回退到 libx264，它是兼容性最好的 h264 实现
        if (toolchain.hasEncoder("libx264")) {
            // 返回 libx264 作为最终兜底
            return "libx264";
        }
        // 连 libx264 都没有时返回空，命令将只处理音频，由错误分类给出可读原因
        return null;
    }

    // 解析音频编码器：优先用户选择，其次按封装候选链选择内核支持的编码器
    private static String resolveAudioEncoder(ConversionRequest request, Toolchain toolchain, String muxer) {
        // 用户显式指定且内核支持时直接尊重用户选择
        if (request.audioEncoder() != null && toolchain.hasEncoder(request.audioEncoder())) {
            // 返回用户选定的编码器
            return request.audioEncoder();
        }
        // 按封装名取得音频候选链
        List<String> candidates = FormatTables.audioCandidatesByMuxer(muxer);
        // 候选链为空说明该封装不允许音频，返回空表示不编码音频
        if (candidates.isEmpty()) {
            // 返回空表示跳过音频编码
            return null;
        }
        // 按候选顺序选择内核支持的编码器
        for (String candidate : candidates) {
            // 内核支持该编码器时采用它
            if (toolchain.hasEncoder(candidate)) {
                // 返回命中的音频编码器
                return candidate;
            }
        }
        // 候选全部不可用时回退到 aac，它是兼容性最好的有损音频编码
        if (toolchain.hasEncoder("aac")) {
            // 返回 aac 作为最终兜底
            return "aac";
        }
        // 连 aac 都没有时返回空，由错误分类给出可读原因
        return null;
    }

    // 追加流映射参数：按类别与封装约束裁剪视频流或音频流
    private static void appendStreamMapping(List<String> args, ConversionRequest request, String muxer, boolean isImage) {
        // 图片类别只需要一路视频帧，不需要音频
        if (isImage) {
            // 映射输入的第一路视频流，问号表示该流可以不存在，避免纯音频素材直接报错
            args.add("-map");
            // 明确指定第一路视频流
            args.add("0:v:0?");
            // 显式禁用音频，保证图片输出不携带音轨
            args.add("-an");
            // 映射完成后直接返回，不再处理其它分支
            return;
        }
        // 音频类别只需要音频轨，无论目标封装是否支持视频都裁剪掉画面
        if (request.isAudioOnly()) {
            // 映射输入的第一路音频流，问号保证无音轨素材不直接失败
            args.add("-map");
            // 明确指定第一路音频流
            args.add("0:a:0?");
            // 显式禁用视频，避免往纯音频封装写入画面
            args.add("-vn");
            // 映射完成后直接返回
            return;
        }
        // 视频类别：判断目标封装是否只允许视频轨，例如 gif 动图没有音轨概念
        boolean videoOnly = FormatTables.isVideoOnlyMuxer(muxer);
        // 目标封装只允许视频时只映射视频流并禁用音频
        if (videoOnly) {
            // 映射第一路视频流
            args.add("-map");
            // 明确指定第一路视频流
            args.add("0:v:0?");
            // 禁用音频，满足仅视频容器的约束
            args.add("-an");
            // 分支处理完成直接返回
            return;
        }
        // 常规视频封装：同时映射视频与音频，问号使缺失轨道的素材仍能转换
        args.add("-map");
        // 映射第一路视频流
        args.add("0:v:0?");
        // 追加第二组映射参数
        args.add("-map");
        // 映射第一路音频流
        args.add("0:a:0?");
    }

    // 追加滤镜参数：按固定顺序裁剪 → 缩放 → 旋转/镜像 → 帧率 → 水印 → 字幕 → 变速
    private static Path appendFilters(List<String> args, ConversionRequest request, MediaInfo info, boolean isImage) {
        // 图片类别只取一帧，缩放与像素格式仍有意义，但帧率与变速没有意义
        List<String> videoFilters = new ArrayList<>();
        // 取出源视频流，可能为空表示输入没有画面
        MediaInfo.Stream sourceVideo = info == null ? null : info.firstStream("video");
        // 第一步：裁剪，用户填写了完整的裁剪宽高才生效
        if (request.cropWidth() > 0 && request.cropHeight() > 0) {
            // 使用 crop 滤镜，宽高与起点均向下取偶数，保证后续编码器不会因奇数尺寸失败
            videoFilters.add("crop=" + evenFloor(request.cropWidth()) + ":" + evenFloor(request.cropHeight())
                    // 追加裁剪起点横坐标，同样取偶数
                    + ":" + evenFloor(request.cropX())
                    // 追加裁剪起点纵坐标
                    + ":" + evenFloor(request.cropY()));
        }
        // 第二步：缩放；未指定目标尺寸但源尺寸为奇数时也需要缩放来取偶数
        String scaleFilter = buildScaleFilter(request, sourceVideo);
        // 缩放滤镜非空时加入滤镜链
        if (scaleFilter != null) {
            // 加入缩放滤镜
            videoFilters.add(scaleFilter);
        }
        // 第三步：旋转与镜像，顺序固定在缩放之后，避免旋转后缩放导致比例计算混乱
        if (request.rotateDegrees() == 90) {
            // transpose=1 表示顺时针旋转 90 度
            videoFilters.add("transpose=1");
        } else if (request.rotateDegrees() == 180) {
            // 用两次 90 度旋转组合出 180 度，这是 transpose 的通用做法
            videoFilters.add("transpose=1");
            // 第二次顺时针 90 度旋转
            videoFilters.add("transpose=1");
        } else if (request.rotateDegrees() == 270) {
            // transpose=2 表示逆时针旋转 90 度，等价于顺时针 270 度
            videoFilters.add("transpose=2");
        }
        // 水平镜像使用 hflip 滤镜
        if (request.mirrorHorizontal()) {
            // 加入水平镜像滤镜
            videoFilters.add("hflip");
        }
        // 垂直镜像使用 vflip 滤镜
        if (request.mirrorVertical()) {
            // 加入垂直镜像滤镜
            videoFilters.add("vflip");
        }
        // 第四步：帧率变换，指定目标帧率时生效，图片输出不需要帧率变换
        if (!isImage && request.frameRate() > 0d) {
            // 使用 fps 滤镜统一帧率，保留三位小数足以表达 23.976 这类非整数帧率
            videoFilters.add("fps=" + formatDecimal(request.frameRate()));
        }
        // 第五步：水印，使用 drawtext 滤镜把文本烙进画面
        if (request.watermarkText() != null) {
            // 构造水印滤镜并加入滤镜链
            videoFilters.add(buildWatermarkFilter(request));
        }
        // 第六步：字幕，烧录模式且提供了字幕文件时才生效
        Path workingDirectory = null;
        // 仅在烧录模式且字幕文件存在时添加字幕滤镜
        if (request.burnSubtitles() && request.subtitleFile() != null && Files.isRegularFile(request.subtitleFile())) {
            // 取字幕文件所在目录作为进程工作目录，这样滤镜里只需写简短的文件名，避开复杂路径转义
            Path subtitleDir = request.subtitleFile().toAbsolutePath().getParent();
            // 工作目录非空时采用它
            if (subtitleDir != null) {
                // 记录工作目录供执行器使用
                workingDirectory = subtitleDir;
                // 使用 subtitles 滤镜烧录字幕，文件名使用单引号包裹并转义特殊字符
                videoFilters.add("subtitles=filename='" + escapeFilterValue(request.subtitleFile().getFileName().toString()) + "'");
            }
        }
        // 第七步：变速，视频侧使用 setpts 调整时间戳实现画面变速
        if (!isImage && Math.abs(request.speedFactor() - 1.0d) > 1e-6) {
            // setpts 除以倍率即可让画面加速；倍率大于 1 时时间戳被压缩
            videoFilters.add("setpts=PTS/" + formatDecimal(request.speedFactor()));
        }
        // 自动补救时在链条末尾追加 pad，把奇数分辨率向上补齐为偶数，这是奇数尺寸失败的标准修法
        if (request.padToEven()) {
            // pad 必须放在最末尾，否则前面的滤镜会先按奇数尺寸运算而失败
            videoFilters.add("pad=ceil(iw/2)*2:ceil(ih/2)*2");
        }
        // 滤镜链非空时追加为 -vf 参数，空链不追加以免产生无效参数
        if (!videoFilters.isEmpty()) {
            // 追加视频滤镜参数名
            args.add("-vf");
            // 用逗号连接全部滤镜，逗号是 ffmpeg 滤镜链的标准分隔符
            args.add(String.join(",", videoFilters));
        }
        // 音频侧变速使用 atempo 滤镜，变速不会发生在图片输出上
        if (!isImage && Math.abs(request.speedFactor() - 1.0d) > 1e-6) {
            // 构造 atempo 链，倍率超出单次可用区间时自动拆成多次
            args.add("-af");
            // 追加 atempo 滤镜链
            args.add(buildAtempoChain(request.speedFactor()));
        }
        // 返回工作目录，供执行器设置进程工作目录
        return workingDirectory;
    }

    // 构造缩放滤镜文本，返回 null 表示不需要缩放
    private static String buildScaleFilter(ConversionRequest request, MediaInfo.Stream sourceVideo) {
        // 取得用户指定的目标宽高
        int width = request.width();
        // 取得用户指定的目标高度
        int height = request.height();
        // 宽高都指定时按精确尺寸缩放，并向下取偶数满足多数编码器的尺寸要求
        if (width > 0 && height > 0) {
            // 返回按精确尺寸缩放的滤镜表达式
            return "scale=trunc(" + evenFloor(width) + "/2)*2:trunc(" + evenFloor(height) + "/2)*2:flags=lanczos";
        }
        // 只指定宽度时按宽度等比缩放，高度用 -2 自动计算并保证偶数
        if (width > 0) {
            // 返回等比缩放表达式，-2 表示按比例推算并强制偶数
            return "scale=trunc(" + evenFloor(width) + "/2)*2:-2:flags=lanczos";
        }
        // 只指定高度时按高度等比缩放，宽度用 -2 自动计算并保证偶数
        if (height > 0) {
            // 返回等比缩放表达式
            return "scale=-2:trunc(" + evenFloor(height) + "/2)*2:flags=lanczos";
        }
        // 未指定尺寸时检查源分辨率是否为奇数，奇数分辨率会导致多数编码器直接失败
        if (sourceVideo != null && (sourceVideo.hasOddWidth() || sourceVideo.hasOddHeight())) {
            // 用表达式按源尺寸向下取偶数，这是“奇数分辨率自动补救”的基础
            return "scale=trunc(iw/2)*2:trunc(ih/2)*2:flags=lanczos";
        }
        // 尺寸合法且无需缩放时返回 null
        return null;
    }

    // 构造水印滤镜文本，位置与字号透明度由请求决定
    private static String buildWatermarkFilter(ConversionRequest request) {
        // 使用 StringBuilder 拼接滤镜参数，每个参数之间用冒号分隔
        StringBuilder builder = new StringBuilder("drawtext=");
        // 查找可用的系统字体，找到时指定 fontfile 以获得一致的渲染结果
        String font = findFontFile();
        // 找到字体时追加 fontfile 参数，路径中的冒号需要转义
        if (font != null) {
            // 追加字体文件参数，冒号在滤镜参数中必须转义
            builder.append("fontfile='").append(escapeFilterValue(font)).append("':");
        }
        // 追加水印文本参数，使用单引号包裹并转义内部特殊字符
        builder.append("text='").append(escapeFilterValue(request.watermarkText())).append("':");
        // 追加字体颜色，白色配合不透明度更容易与画面融合
        builder.append("fontcolor=white@").append(formatDecimal(request.watermarkOpacity())).append(':');
        // 追加字号参数
        builder.append("fontsize=").append(request.watermarkFontSize()).append(':');
        // 追加边框描边，提升浅色背景下的可读性
        builder.append("borderw=2:bordercolor=black@").append(formatDecimal(request.watermarkOpacity())).append(':');
        // 按位置名称计算横坐标表达式
        String x = switch (request.watermarkPosition()) {
            // 左上角留出 10 像素边距
            case "left-top", "left-bottom" -> "10";
            // 居中时按画面宽度减文本宽度的一半计算
            case "center" -> "(w-tw)/2";
            // 右侧位置按画面宽度减文本宽度再留边距
            default -> "w-tw-10";
        };
        // 按位置名称计算纵坐标表达式
        String y = switch (request.watermarkPosition()) {
            // 顶部位置留出 10 像素边距
            case "left-top", "right-top" -> "10";
            // 居中时按画面高度减文本高度的一半计算
            case "center" -> "(h-th)/2";
            // 底部位置按画面高度减文本高度再留边距
            default -> "h-th-10";
        };
        // 追加横坐标表达式
        builder.append("x=").append(x).append(':');
        // 追加纵坐标表达式
        builder.append("y=").append(y);
        // 返回完整的水印滤镜文本
        return builder.toString();
    }

    // 查找可用的系统字体文件，全部缺失时返回 null 由 ffmpeg 自行选择默认字体
    private static String findFontFile() {
        // 逐个候选路径判断文件是否存在
        for (String candidate : FONT_CANDIDATES) {
            // 存在普通文件即采用该字体
            if (Files.isRegularFile(Path.of(candidate))) {
                // 返回字体文件路径
                return candidate;
            }
        }
        // 全部候选都不存在时返回 null
        return null;
    }

    // 构造 atempo 滤镜链，倍率超出单次可用区间时按数学分解串联多个 atempo
    private static String buildAtempoChain(double speed) {
        // 使用 StringBuilder 累积滤镜链
        StringBuilder builder = new StringBuilder();
        // 剩余倍率从目标倍率开始
        double remaining = speed;
        // atempo 单次支持 0.5 至 2.0，超出部分需要拆分；倍率大于 2 时循环除以 2
        while (remaining > 2.0d) {
            // 追加一段 2.0 倍的加速
            appendAtempo(builder, 2.0d);
            // 剩余倍率折半
            remaining /= 2.0d;
        }
        // 倍率小于 0.5 时循环乘以 2，把多次慢放串起来
        while (remaining < 0.5d) {
            // 追加一段 0.5 倍的减速
            appendAtempo(builder, 0.5d);
            // 剩余倍率翻倍
            remaining /= 0.5d;
        }
        // 追加最后一段落在可用区间内的倍率
        appendAtempo(builder, remaining);
        // 返回拼好的滤镜链
        return builder.toString();
    }

    // 向滤镜链追加一段 atempo 参数，自动处理逗号分隔
    private static void appendAtempo(StringBuilder builder, double value) {
        // 已有内容时先追加逗号分隔符
        if (builder.length() > 0) {
            // 追加滤镜分隔符
            builder.append(',');
        }
        // 追加 atempo 滤镜与倍率数值
        builder.append("atempo=").append(formatDecimal(value));
    }

    // 追加视频质量参数，按编码器类型选择 CRF、cq、qscale 或码率参数体系
    private static void appendVideoQuality(List<String> args, ConversionRequest request, String encoder, int targetHeight) {
        // 用户显式指定码率时优先使用码率模式，符合“用户说了算”的原则
        if (request.videoBitRateKbps() > 0) {
            // 追加视频码率参数
            args.add("-b:v");
            // 追加码率数值并带千比特单位
            args.add(request.videoBitRateKbps() + "k");
            // 码率模式下不再追加质量参数，避免两套参数互相冲突
            return;
        }
        // 取质量档位，后续所有分支都基于它推导具体数值
        Quality quality = request.quality();
        // 硬件编码器使用各厂商的质量参数，不同厂商参数名不同必须分别处理
        if (FormatTables.isHardwareVideoEncoder(encoder)) {
            // 追加硬件编码器专用的质量参数
            appendHardwareQuality(args, encoder, quality);
            // 硬件分支处理完成
            return;
        }
        // gif 编码器不支持 crf 与 qscale，只通过调色板与循环次数控制输出
        if ("gif".equals(encoder)) {
            // 循环次数设为 0 表示无限循环，符合动图的常见预期
            args.add("-loop");
            // 追加循环参数值
            args.add("0");
            // gif 分支处理完成
            return;
        }
        // x264 与 x265 使用 CRF 模式，这是画质与体积平衡最好的方式
        if (FormatTables.isX26xEncoder(encoder)) {
            // 追加预设参数，medium 在速度与压缩率之间取得平衡
            args.add("-preset");
            // 指定 medium 预设
            args.add("medium");
            // 追加恒定质量参数
            args.add("-crf");
            // 追加由质量档位推导出的 CRF 值
            args.add(String.valueOf(quality.x26xCrf()));
            // 无损档额外声明 tune 参数，保证无色彩二次损失
            if (quality.isLossless()) {
                // 追加无损调优参数
                args.add("-tune");
                // 指定无损调优
                args.add("lossless");
            }
            // x26x 分支处理完成
            return;
        }
        // vp9 与 av1 使用更宽的 CRF 区间，且必须把目标码率设为 0 才能真正启用恒定质量
        if (FormatTables.isVp9OrAv1(encoder)) {
            // 追加恒定质量参数
            args.add("-crf");
            // 追加由质量档位推导出的 CRF 值
            args.add(String.valueOf(quality.vpxCrf()));
            // 追加目标码率参数，0 表示让 CRF 完全接管码率控制
            args.add("-b:v");
            // 目标码率置零
            args.add("0");
            // vp9 支持专用无损开关，无损档时显式打开
            if (quality.isLossless() && encoder.startsWith("libvpx")) {
                // 追加无损参数
                args.add("-lossless");
                // 打开无损模式
                args.add("1");
            }
            // vp9/av1 分支处理完成
            return;
        }
        // 老式编码器（mpeg 系列、h263、wmv、flv、mjpeg 等）使用 qscale 控制质量
        if (FormatTables.isMpegFamilyEncoder(encoder)) {
            // 追加 qscale 参数
            args.add("-qscale:v");
            // 追加由质量档位推导出的量化值
            args.add(String.valueOf(quality.qscale()));
            // 老式编码器分支处理完成
            return;
        }
        // theora 等其余编码器按码率模式处理，码率由目标高度与档位共同估算
        args.add("-b:v");
        // 追加估算出的码率并带千比特单位
        args.add(quality.videoBitRateKbps(targetHeight) + "k");
    }

    // 追加硬件编码器的质量参数，按厂商后缀分派到各自支持的参数名
    private static void appendHardwareQuality(List<String> args, String encoder, Quality quality) {
        // 取由质量档位推导出的恒定质量值
        int cq = quality.hardwareCq();
        // NVIDIA NVENC 使用 -rc vbr 与 -cq 组合，并把目标码率置零让 cq 完全接管
        if (encoder.endsWith("_nvenc")) {
            // 追加码率控制模式参数
            args.add("-rc");
            // 指定可变码率模式
            args.add("vbr");
            // 追加恒定质量参数
            args.add("-cq");
            // 追加恒定质量数值
            args.add(String.valueOf(cq));
            // 追加目标码率参数
            args.add("-b:v");
            // 目标码率置零
            args.add("0");
            // NVENC 分支处理完成
            return;
        }
        // Intel Quick Sync 使用 -global_quality 表达恒定质量
        if (encoder.endsWith("_qsv")) {
            // 追加全局质量参数
            args.add("-global_quality");
            // 追加质量数值
            args.add(String.valueOf(cq));
            // QSV 分支处理完成
            return;
        }
        // AMD AMF 使用 CQP 模式，同时给出 I 帧与 P 帧量化参数
        if (encoder.endsWith("_amf")) {
            // 追加码率控制模式参数
            args.add("-rc");
            // 指定恒定量化模式
            args.add("cqp");
            // 追加 I 帧量化参数
            args.add("-qp_i");
            // 追加量化数值
            args.add(String.valueOf(cq));
            // 追加 P 帧量化参数
            args.add("-qp_p");
            // 追加量化数值
            args.add(String.valueOf(cq));
            // AMF 分支处理完成
            return;
        }
        // VAAPI 使用 -qp 表达恒定质量
        if (encoder.endsWith("_vaapi")) {
            // 追加量化参数
            args.add("-qp");
            // 追加量化数值
            args.add(String.valueOf(cq));
            // VAAPI 分支处理完成
            return;
        }
        // 其余硬件编码器（例如 VideoToolbox）使用码率模式，兼容性最好
        args.add("-b:v");
        // 按 1080 高度估算码率并带千比特单位
        args.add(quality.videoBitRateKbps(1080) + "k");
    }

    // 追加元数据处理参数
    private static void appendMetadata(List<String> args, ConversionRequest request) {
        // 去除元数据模式使用 -map_metadata -1，这是 ffmpeg 表示清空元数据的固定写法
        if ("strip".equals(request.metadataMode())) {
            // 追加元数据映射参数
            args.add("-map_metadata");
            // 负一表示丢弃全部元数据
            args.add("-1");
            // 分支处理完成
            return;
        }
        // 其余模式统一保留原始元数据，保证作者信息与拍摄信息不丢失
        args.add("-map_metadata");
        // 零表示沿用输入文件的第一路元数据
        args.add("0");
    }

    // 判断是否需要强制 yuv420p 像素格式
    private static boolean shouldForceYuv420p(ConversionRequest request, String muxer, String encoder) {
        // 用户关闭强制像素格式时不做任何处理
        if (!request.forceYuv420p()) {
            // 返回 false 表示不追加像素格式参数
            return false;
        }
        // 音频类别没有画面，不需要设置像素格式
        if (request.isAudioOnly()) {
            // 返回 false 表示不追加
            return false;
        }
        // 封装名必须属于常见视频容器，这些容器对 yuv420p 的兼容性最好
        boolean mainstreamMuxer = "mp4".equals(muxer)
                // mov 与 mp4 同族
                || "mov".equals(muxer)
                // flv 主要面向流媒体，yuv420p 兼容性最好
                || "flv".equals(muxer)
                // mpegts 面向机顶盒，yuv420p 最稳妥
                || "mpegts".equals(muxer)
                // 3gp 面向移动端，yuv420p 是标准搭配
                || "3gp".equals(muxer)
                // mpeg 封装配 mpeg2video 时同样需要 yuv420p
                || "mpeg".equals(muxer);
        // 封装不在主流集合内时不处理，避免破坏 webm 等容器的像素格式约束
        if (!mainstreamMuxer) {
            // 返回 false 表示不追加
            return false;
        }
        // 编码器必须是 h264/hevc/mpeg4/mpeg2video 系列，这些编码器对 yuv420p 支持最完整
        return encoder.startsWith("libx264")
                // libx265 是软件 hevc 实现
                || encoder.startsWith("libx265")
                // 硬件 h264/hevc 编码器
                || encoder.startsWith("h264")
                // 硬件 hevc 编码器
                || encoder.startsWith("hevc")
                // 老式 mpeg4 编码器
                || encoder.startsWith("mpeg4")
                // mpeg2video 编码器
                || encoder.startsWith("mpeg2video");
    }

    // 计算目标视频高度，用于按分辨率估算码率
    private static int targetVideoHeight(ConversionRequest request, MediaInfo info) {
        // 用户显式指定高度时直接使用
        if (request.height() > 0) {
            // 返回用户指定高度
            return request.height();
        }
        // 取源视频流用于推断高度
        MediaInfo.Stream sourceVideo = info == null ? null : info.firstStream("video");
        // 源分辨率为奇数或在 90/270 度旋转时，宽高会发生交换
        if (sourceVideo != null) {
            // 判断旋转是否导致宽高互换
            boolean swapped = request.rotateDegrees() == 90 || request.rotateDegrees() == 270;
            // 宽度已指定时按旋转关系推算高度
            if (request.width() > 0) {
                // 旋转 90/270 度时输出高度等于用户指定宽度
                return swapped ? request.width() : Math.round(request.width() * sourceVideo.height() / (float) Math.max(sourceVideo.width(), 1));
            }
            // 未指定宽度时使用源高度，旋转时改用源宽度
            return swapped ? sourceVideo.width() : sourceVideo.height();
        }
        // 无源视频信息时按 720 估算，作为码率计算的默认高度
        return 720;
    }

    // 把数值向下取到偶数，返回大于等于 2 的偶数
    private static int evenFloor(int value) {
        // 先取绝对值避免负数参与取偶数运算
        int positive = Math.abs(value);
        // 向下取偶数，即清掉最低二进制位
        int even = positive - (positive % 2);
        // 结果过小时强制为 2，保证滤镜表达式合法
        return Math.max(even, 2);
    }

    // 把双精度数值格式化为最多三位小数，并强制使用点号作为小数点
    private static String formatDecimal(double value) {
        // 使用 Locale.ROOT 固定小数点为点号，避免某些区域设置输出逗号导致 ffmpeg 解析失败
        String text = String.format(Locale.ROOT, "%.3f", value);
        // 去掉多余的尾随零，例如 25.000 变成 25
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            // 逐个裁掉末尾字符
            text = text.substring(0, text.length() - 1);
        }
        // 返回清理后的数值文本
        return text;
    }

    // 转义滤镜参数值中的特殊字符，避免冒号、逗号、引号与反斜杠破坏滤镜语法
    private static String escapeFilterValue(String value) {
        // 空值返回空串，避免后续空指针
        if (value == null) {
            // 返回空串
            return "";
        }
        // 使用 StringBuilder 逐字符处理
        StringBuilder builder = new StringBuilder();
        // 遍历每个字符
        for (int i = 0; i < value.length(); i++) {
            // 取出当前字符
            char ch = value.charAt(i);
            // 反斜杠需要自身转义，否则会吞掉后续字符
            if (ch == '\\') {
                // 写入两个反斜杠
                builder.append("\\\\");
                // 继续处理下一个字符
                continue;
            }
            // 冒号是滤镜参数分隔符，必须转义
            if (ch == ':') {
                // 写入转义后的冒号
                builder.append("\\:");
                // 继续处理下一个字符
                continue;
            }
            // 逗号是滤镜链分隔符，必须转义
            if (ch == ',') {
                // 写入转义后的逗号
                builder.append("\\,");
                // 继续处理下一个字符
                continue;
            }
            // 单引号是参数包裹符，必须转义
            if (ch == '\'') {
                // 写入转义后的单引号
                builder.append("\\'");
                // 继续处理下一个字符
                continue;
            }
            // 百分号在 drawtext 中表示表达式前缀，需要转义成字面量
            if (ch == '%') {
                // 写入转义后的百分号
                builder.append("\\%");
                // 继续处理下一个字符
                continue;
            }
            // 方括号在滤镜链中表示输入输出标签，需要转义成字面量
            if (ch == '[' || ch == ']') {
                // 写入转义后的方括号
                builder.append('\\').append(ch);
                // 继续处理下一个字符
                continue;
            }
            // 其余字符原样写入
            builder.append(ch);
        }
        // 返回转义后的文本
        return builder.toString();
    }

    // 静态嵌套类 BuiltCommand：承载编译结果，包含参数列表、输出路径与进程工作目录
    public static final class BuiltCommand {

        // 完整的命令参数列表，首个元素是 ffmpeg 可执行文件路径
        private final List<String> arguments;
        // 输出文件路径，执行完成后用于校验产物
        private final Path outputPath;
        // 进程工作目录，烧录字幕时会被设置为字幕所在目录以便用简短的相对路径引用字幕
        private final Path workingDirectory;
        // 视频编码器名称，用于进度换算与错误分类
        private final String videoEncoder;
        // 音频编码器名称，用于错误分类与日志说明
        private final String audioEncoder;
        // 目标封装名，用于错误分类
        private final String muxer;

        // 构造方法：一次性接收全部编译结果
        BuiltCommand(List<String> arguments, Path outputPath, Path workingDirectory,
                     String videoEncoder, String audioEncoder, String muxer) {
            // 保存参数列表
            this.arguments = arguments;
            // 保存输出路径
            this.outputPath = outputPath;
            // 保存工作目录
            this.workingDirectory = workingDirectory;
            // 保存视频编码器名
            this.videoEncoder = videoEncoder;
            // 保存音频编码器名
            this.audioEncoder = audioEncoder;
            // 保存封装名
            this.muxer = muxer;
        }

        // 获取参数列表，返回不可变副本防止外部修改
        public List<String> arguments() {
            // 返回参数列表的只读视图
            return List.copyOf(arguments);
        }

        // 获取输出文件路径
        public Path outputPath() {
            // 返回输出路径
            return outputPath;
        }

        // 获取进程工作目录
        public Path workingDirectory() {
            // 返回工作目录，可能为空表示使用默认工作目录
            return workingDirectory;
        }

        // 获取视频编码器名
        public String videoEncoder() {
            // 返回视频编码器名，可能为空表示不编码视频
            return videoEncoder;
        }

        // 获取音频编码器名
        public String audioEncoder() {
            // 返回音频编码器名，可能为空表示不编码音频
            return audioEncoder;
        }

        // 获取目标封装名
        public String muxer() {
            // 返回封装名
            return muxer;
        }

        // 生成可读的命令文本，用于日志展示；对含空格的参数加引号，便于人工复制排查
        public String display() {
            // 使用 StringBuilder 组织文本
            StringBuilder builder = new StringBuilder();
            // 逐个参数拼接
            for (String argument : arguments) {
                // 已有内容时先加空格分隔
                if (builder.length() > 0) {
                    // 追加分隔空格
                    builder.append(' ');
                }
                // 参数含空格或特殊字符时用双引号包裹
                if (argument.indexOf(' ') >= 0 || argument.indexOf('"') >= 0) {
                    // 追加带引号的参数
                    builder.append('"').append(argument).append('"');
                } else {
                    // 直接追加参数
                    builder.append(argument);
                }
            }
            // 返回拼好的命令文本
            return builder.toString();
        }
    }
}
