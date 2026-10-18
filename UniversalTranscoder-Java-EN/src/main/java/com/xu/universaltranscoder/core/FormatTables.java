package com.xu.universaltranscoder.core;

// 引入 LinkedHashMap，保持格式清单的定义顺序，使界面下拉框顺序稳定可预期

import java.util.LinkedHashMap;
// 引入 ArrayList，用于构造编码器候选列表
import java.util.ArrayList;
// 引入 Collections，提供不可变列表包装
import java.util.Collections;
// 引入 LinkedHashSet，保持集合插入顺序并去重
import java.util.LinkedHashSet;
// 引入 List 接口，作为编码器候选与格式清单的类型
import java.util.List;
// 引入 Map 接口，作为扩展名到封装名的换算表类型
import java.util.Map;
// 引入 Set 接口，作为视频/音频/图片目标格式集合的类型
import java.util.Set;

// 关键字 class：FormatTables 集中存放“容器换算表 + 默认编码器表 + 图片映射表”等静态映射
// 之所以集中管理：这些表是命令构建的事实依据，散落在各处会导致同一映射出现多个互相矛盾的版本
public final class FormatTables {

    // 视频目标扩展名到 ffmpeg 封装名的换算表，键是界面展示的扩展名，值是 -f 参数使用的封装名
    private static final Map<String, String> VIDEO_MUXERS = new LinkedHashMap<>();
    // 音频目标扩展名到 ffmpeg 封装名的换算表，键是界面展示的扩展名，值是 -f 参数使用的封装名
    private static final Map<String, String> AUDIO_MUXERS = new LinkedHashMap<>();
    // 图片目标扩展名到 ffmpeg 封装名的换算表，图片多数使用 image2 封装写出单帧
    private static final Map<String, String> IMAGE_MUXERS = new LinkedHashMap<>();
    // 图片目标扩展名到编码器的映射表，与封装名分开维护，因为同一封装可以承载多种图片编码
    private static final Map<String, String> IMAGE_ENCODERS = new LinkedHashMap<>();
    // 封装名到默认视频编码器候选链的映射，键必须是换算后的封装名，保证查表时语义统一
    private static final Map<String, List<String>> VIDEO_ENCODERS = new LinkedHashMap<>();
    // 封装名到默认音频编码器候选链的映射，键同样是换算后的封装名
    private static final Map<String, List<String>> AUDIO_ENCODERS = new LinkedHashMap<>();
    // 仅含视频的封装集合，命令构建据此裁掉音频流，避免写出不兼容的轨道
    private static final Set<String> VIDEO_ONLY_MUXERS = new LinkedHashSet<>();
    // 仅含音频的封装集合，命令构建据此裁掉视频流，避免写出不兼容的轨道
    private static final Set<String> AUDIO_ONLY_MUXERS = new LinkedHashSet<>();
    // 无损音频封装集合，用于质量档位为 Lossless 时切换到无损编码器
    private static final Set<String> LOSSLESS_AUDIO_MUXERS = new LinkedHashSet<>();
    // 需要专业编码参数的视频封装集合，例如 webm 只接受 vp8/vp9/av1 系列
    private static final Set<String> MODERN_VIDEO_MUXERS = new LinkedHashSet<>();

    // 静态初始化块：一次性把全部映射关系写入内存，类加载后即可直接查表
    static {
        // === 视频目标：逐条登记扩展名与封装名的对应关系 ===
        // mp4 使用原生 mp4 封装，兼容性最好
        VIDEO_MUXERS.put("mp4", "mp4");
        // mkv 对应 matroska 封装，ffmpeg 的封装名与常见后缀并不一致，必须显式换算
        VIDEO_MUXERS.put("mkv", "matroska");
        // mov 使用 mov 封装，内部的 QuickTime 兼容性由封装自动处理
        VIDEO_MUXERS.put("mov", "mov");
        // avi 使用 avi 封装，体积大但兼容老旧播放器
        VIDEO_MUXERS.put("avi", "avi");
        // webm 使用 webm 封装，只接受 vp8/vp9/av1 视频与 vorbis/opus 音频
        VIDEO_MUXERS.put("webm", "webm");
        // flv 使用 flv 封装，常用于网络直播录制
        VIDEO_MUXERS.put("flv", "flv");
        // wmv 需要换算成 asf 封装，ffmpeg 没有名为 wmv 的封装
        VIDEO_MUXERS.put("wmv", "asf");
        // mpg 需要换算成 mpeg 封装，ffmpeg 没有名为 mpg 的封装
        VIDEO_MUXERS.put("mpg", "mpeg");
        // mpeg 同样换算成 mpeg 封装
        VIDEO_MUXERS.put("mpeg", "mpeg");
        // ts 需要换算成 mpegts 封装，ffmpeg 没有名为 ts 的封装
        VIDEO_MUXERS.put("ts", "mpegts");
        // m2ts 同样换算成 mpegts 封装，两者在 ffmpeg 中由同一封装承担
        VIDEO_MUXERS.put("m2ts", "mpegts");
        // m4v 换算成 mp4 封装，m4v 只是 mp4 的一种扩展名
        VIDEO_MUXERS.put("m4v", "mp4");
        // 3gp 使用 3gp 封装，面向移动端小体积场景
        VIDEO_MUXERS.put("3gp", "3gp");
        // ogv 换算成 ogg 封装，ffmpeg 的封装名是 ogg
        VIDEO_MUXERS.put("ogv", "ogg");
        // vob 换算成 mpeg 封装，vob 本质是加了私有扩展的 MPEG-PS
        VIDEO_MUXERS.put("vob", "mpeg");
        // gif 使用 gif 封装，可以生成动图
        VIDEO_MUXERS.put("gif", "gif");

        // === 音频目标：逐条登记扩展名与封装名的对应关系 ===
        // mp3 使用 mp3 封装，编码器必须是 mp3 系列
        AUDIO_MUXERS.put("mp3", "mp3");
        // m4a 需要换算成 ipod 封装，ffmpeg 使用 ipod 表示 m4a 容器
        AUDIO_MUXERS.put("m4a", "ipod");
        // aac 需要换算成 adts 封装，裸 AAC 流使用 ADTS 头
        AUDIO_MUXERS.put("aac", "adts");
        // wav 使用 wav 封装，通常搭配 PCM 无损编码
        AUDIO_MUXERS.put("wav", "wav");
        // flac 使用 flac 封装，只接受 flac 编码器
        AUDIO_MUXERS.put("flac", "flac");
        // ogg 使用 ogg 封装，常搭配 vorbis 或 opus
        AUDIO_MUXERS.put("ogg", "ogg");
        // oga 同样换算成 ogg 封装
        AUDIO_MUXERS.put("oga", "ogg");
        // opus 使用 opus 封装，裸 Opus 流需要该封装
        AUDIO_MUXERS.put("opus", "opus");
        // wma 需要换算成 asf 封装，与 wmv 共用容器
        AUDIO_MUXERS.put("wma", "asf");
        // mka 对应 matroska 封装，是 matroska 的纯音频用法
        AUDIO_MUXERS.put("mka", "matroska");
        // ac3 使用 ac3 封装，面向杜比数字音轨
        AUDIO_MUXERS.put("ac3", "ac3");
        // aiff 使用 aiff 封装，Apple 平台的常见无损容器
        AUDIO_MUXERS.put("aiff", "aiff");
        // au 使用 au 封装，Sun 音频格式，兼容性一般但仍在支持列表内
        AUDIO_MUXERS.put("au", "au");
        // amr 使用 amr 封装，面向窄带语音
        AUDIO_MUXERS.put("amr", "amr");
        // wv 使用 wv 封装，对应 WavPack 无损编码
        AUDIO_MUXERS.put("wv", "wv");
        // tta 使用 tta 封装，对应无损音频编码
        AUDIO_MUXERS.put("tta", "tta");
        // mp2 使用 mp2 封装，面向广播与老设备
        AUDIO_MUXERS.put("mp2", "mp2");

        // === 图片目标：登记扩展名到封装名的映射，绝大多数图片走 image2 封装 ===
        // jpg 使用 image2 封装由 mjpeg 编码器写出单帧
        IMAGE_MUXERS.put("jpg", "image2");
        // png 使用 image2 封装由 png 编码器写出单帧
        IMAGE_MUXERS.put("png", "image2");
        // bmp 使用 image2 封装由 bmp 编码器写出单帧
        IMAGE_MUXERS.put("bmp", "image2");
        // tiff 使用 image2 封装由 tiff 编码器写出单帧
        IMAGE_MUXERS.put("tiff", "image2");
        // tga 使用 image2 封装由 targa 编码器写出单帧
        IMAGE_MUXERS.put("tga", "image2");
        // ppm 使用 image2 封装由 ppm 编码器写出单帧
        IMAGE_MUXERS.put("ppm", "image2");
        // pgm 使用 image2 封装由 pgm 编码器写出单帧
        IMAGE_MUXERS.put("pgm", "image2");
        // exr 使用 image2 封装由 exr 编码器写出单帧，保留高动态范围
        IMAGE_MUXERS.put("exr", "image2");
        // webp 使用专用 webp 封装，因为 image2 无法承载 webp
        IMAGE_MUXERS.put("webp", "webp");
        // gif 使用专用 gif 封装，可以输出静态或动画图片
        IMAGE_MUXERS.put("gif", "gif");

        // === 图片目标：登记扩展名到编码器的独立映射，与封装名解耦 ===
        // jpg 对应 mjpeg 编码器，这是 JPEG 在 ffmpeg 中的标准实现
        IMAGE_ENCODERS.put("jpg", "mjpeg");
        // png 对应 png 编码器
        IMAGE_ENCODERS.put("png", "png");
        // bmp 对应 bmp 编码器
        IMAGE_ENCODERS.put("bmp", "bmp");
        // tiff 对应 tiff 编码器
        IMAGE_ENCODERS.put("tiff", "tiff");
        // tga 对应 targa 编码器，ffmpeg 使用 targa 这个名字
        IMAGE_ENCODERS.put("tga", "targa");
        // ppm 对应 ppm 编码器
        IMAGE_ENCODERS.put("ppm", "ppm");
        // pgm 对应 pgm 编码器
        IMAGE_ENCODERS.put("pgm", "pgm");
        // exr 对应 exr 编码器
        IMAGE_ENCODERS.put("exr", "exr");
        // webp 优先使用 libwebp 编码器，压缩效率明显优于内置 webp
        IMAGE_ENCODERS.put("webp", "libwebp");
        // gif 对应 gif 编码器
        IMAGE_ENCODERS.put("gif", "gif");

        // === 封装名到默认视频编码器候选链：键统一使用换算后的封装名 ===
        // mp4 优先 h264，因为兼容性最广，hevc 作为次选
        VIDEO_ENCODERS.put("mp4", List.of("libx264", "libx265", "mpeg4"));
        // mov 与 mp4 同源，编码器候选保持一致
        VIDEO_ENCODERS.put("mov", List.of("libx264", "libx265", "mpeg4"));
        // matroska 允许绝大多数编码，h264 作为默认
        VIDEO_ENCODERS.put("matroska", List.of("libx264", "libx265", "libvpx-vp9"));
        // avi 面向老旧播放器，mpeg4 兼容性最好，h264 作为次选
        VIDEO_ENCODERS.put("avi", List.of("mpeg4", "libx264", "mjpeg"));
        // webm 只接受 vp8/vp9/av1，vp9 作为默认
        VIDEO_ENCODERS.put("webm", List.of("libvpx-vp9", "libvpx", "libaom-av1"));
        // flv 常见搭配是 h264，sorenson 已过时不再考虑
        VIDEO_ENCODERS.put("flv", List.of("libx264", "flv"));
        // asf 容器传统上承载 wmv 系列，wmv2 作为默认
        VIDEO_ENCODERS.put("asf", List.of("wmv2", "msmpeg4v3", "libx264"));
        // mpeg 封装面向 MPEG-1/2 播放器，mpeg2video 作为默认
        VIDEO_ENCODERS.put("mpeg", List.of("mpeg2video", "mpeg1video", "libx264"));
        // mpegts 用于数字电视传输，mpeg2video 兼容机顶盒
        VIDEO_ENCODERS.put("mpegts", List.of("mpeg2video", "libx264", "libx265"));
        // 3gp 面向移动端，h263 与 mpeg4 是标准搭配
        VIDEO_ENCODERS.put("3gp", List.of("h263", "mpeg4", "libx264"));
        // ogg 容器承载 theora 视频，vp8 作为次选
        VIDEO_ENCODERS.put("ogg", List.of("libtheora", "libvpx"));
        // gif 容器承载 gif 编码器，用于输出动图
        VIDEO_ENCODERS.put("gif", List.of("gif"));

        // === 封装名到默认音频编码器候选链：同样使用换算后的封装名作为键 ===
        // mp4 默认 aac，兼容性最好
        AUDIO_ENCODERS.put("mp4", List.of("aac", "libmp3lame"));
        // mov 与 mp4 一致
        AUDIO_ENCODERS.put("mov", List.of("aac", "libmp3lame"));
        // matroska 允许几乎所有音频编码，aac 作为默认
        AUDIO_ENCODERS.put("matroska", List.of("aac", "libopus", "libvorbis"));
        // avi 传统搭配 mp3，因此优先 libmp3lame
        AUDIO_ENCODERS.put("avi", List.of("libmp3lame", "mp3", "pcm_s16le"));
        // webm 只接受 vorbis 与 opus，opus 作为默认
        AUDIO_ENCODERS.put("webm", List.of("libopus", "libvorbis"));
        // flv 支持 aac 与 mp3，aac 作为默认
        AUDIO_ENCODERS.put("flv", List.of("aac", "libmp3lame"));
        // asf 承载 wma 系列，wmav2 作为默认
        AUDIO_ENCODERS.put("asf", List.of("wmav2", "libmp3lame"));
        // mpeg 封装承载 mp2 音轨，这是 MPEG-PS 的标准搭配
        AUDIO_ENCODERS.put("mpeg", List.of("mp2", "mp3", "libmp3lame"));
        // mpegts 在广播中常配 mp2 或 aac，mp2 作为默认
        AUDIO_ENCODERS.put("mpegts", List.of("mp2", "aac", "libmp3lame"));
        // 3gp 使用 aac 或 amr，aac 作为默认
        AUDIO_ENCODERS.put("3gp", List.of("aac", "libopencore_amrnb"));
        // ogg 默认 vorbis，opus 作为次选
        AUDIO_ENCODERS.put("ogg", List.of("libvorbis", "libopus"));
        // gif 容器通常没有音轨，这里给出空候选列表由命令构建裁剪掉音频
        AUDIO_ENCODERS.put("gif", List.of());
        // mp3 封装只接受 mp3 编码器
        AUDIO_ENCODERS.put("mp3", List.of("libmp3lame", "mp3"));
        // ipod 即 m4a，默认 aac
        AUDIO_ENCODERS.put("ipod", List.of("aac"));
        // adts 是裸 AAC 流，只接受 aac
        AUDIO_ENCODERS.put("adts", List.of("aac"));
        // wav 默认 16 位有符号小端 PCM，兼容性最好
        AUDIO_ENCODERS.put("wav", List.of("pcm_s16le"));
        // flac 只接受 flac 编码器
        AUDIO_ENCODERS.put("flac", List.of("flac"));
        // opus 封装只接受 libopus，纯 opus 编码器也可用
        AUDIO_ENCODERS.put("opus", List.of("libopus", "opus"));
        // ac3 只接受 ac3 编码器
        AUDIO_ENCODERS.put("ac3", List.of("ac3"));
        // aiff 默认大端 PCM
        AUDIO_ENCODERS.put("aiff", List.of("pcm_s16be"));
        // au 默认大端 PCM，与 aiff 一致
        AUDIO_ENCODERS.put("au", List.of("pcm_s16be"));
        // amr 只接受窄带编码器
        AUDIO_ENCODERS.put("amr", List.of("libopencore_amrnb"));
        // wv 只接受 wavpack 编码器
        AUDIO_ENCODERS.put("wv", List.of("wavpack"));
        // tta 只接受 tta 编码器
        AUDIO_ENCODERS.put("tta", List.of("tta"));
        // mp2 只接受 mp2 编码器
        AUDIO_ENCODERS.put("mp2", List.of("mp2"));

        // === 仅视频封装集合：这些容器不允许承载音频轨，命令构建必须裁掉音频 ===
        // gif 动图没有音轨概念
        VIDEO_ONLY_MUXERS.add("gif");

        // === 仅音频封装集合：这些容器不允许承载视频轨，命令构建必须裁掉视频 ===
        // mp3 是纯音频封装
        AUDIO_ONLY_MUXERS.add("mp3");
        // ipod 在纯音频场景下作为 m4a 使用
        AUDIO_ONLY_MUXERS.add("ipod");
        // adts 是裸音频流封装
        AUDIO_ONLY_MUXERS.add("adts");
        // wav 是纯音频封装
        AUDIO_ONLY_MUXERS.add("wav");
        // flac 是纯音频封装
        AUDIO_ONLY_MUXERS.add("flac");
        // opus 是纯音频封装
        AUDIO_ONLY_MUXERS.add("opus");
        // ac3 是纯音频封装
        AUDIO_ONLY_MUXERS.add("ac3");
        // aiff 是纯音频封装
        AUDIO_ONLY_MUXERS.add("aiff");
        // au 是纯音频封装
        AUDIO_ONLY_MUXERS.add("au");
        // amr 是纯音频封装
        AUDIO_ONLY_MUXERS.add("amr");
        // wv 是纯音频封装
        AUDIO_ONLY_MUXERS.add("wv");
        // tta 是纯音频封装
        AUDIO_ONLY_MUXERS.add("tta");
        // mp2 是纯音频封装
        AUDIO_ONLY_MUXERS.add("mp2");

        // === 无损音频封装集合：质量档位为 Lossless 时需要切换到无损编码器 ===
        // wav 通过 PCM 实现无损
        LOSSLESS_AUDIO_MUXERS.add("wav");
        // flac 本身就是无损格式
        LOSSLESS_AUDIO_MUXERS.add("flac");
        // aiff 通过 PCM 实现无损
        LOSSLESS_AUDIO_MUXERS.add("aiff");
        // au 通过 PCM 实现无损
        LOSSLESS_AUDIO_MUXERS.add("au");
        // wv 是 WavPack 无损格式
        LOSSLESS_AUDIO_MUXERS.add("wv");
        // tta 是无损格式
        LOSSLESS_AUDIO_MUXERS.add("tta");

        // === 现代编码器封装集合：这些容器对编码器有强约束，需要显式指定编码器避免自动选择失败 ===
        // webm 对编码器有强约束
        MODERN_VIDEO_MUXERS.add("webm");
        // ogg 只接受 theora 与 vp8
        MODERN_VIDEO_MUXERS.add("ogg");
        // gif 只接受 gif 编码器
        MODERN_VIDEO_MUXERS.add("gif");
    }

    // 私有构造方法，禁止实例化这个纯静态映射表容器
    private FormatTables() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 把界面上的视频目标扩展名换算为 ffmpeg 封装名
    public static String videoMuxer(String extension) {
        // 扩展名统一转小写并去掉可能的点号前缀，容忍用户输入 .MP4 这类写法
        return VIDEO_MUXERS.get(normalize(extension));
    }

    // 把界面上的音频目标扩展名换算为 ffmpeg 封装名
    public static String audioMuxer(String extension) {
        // 归一化后查表
        return AUDIO_MUXERS.get(normalize(extension));
    }

    // 把界面上的图片目标扩展名换算为 ffmpeg 封装名
    public static String imageMuxer(String extension) {
        // 归一化后查表
        return IMAGE_MUXERS.get(normalize(extension));
    }

    // 取得图片目标对应的编码器名
    public static String imageEncoder(String extension) {
        // 归一化后查表
        return IMAGE_ENCODERS.get(normalize(extension));
    }

    // 判断某扩展名是否属于图片目标格式
    public static boolean isImageTarget(String extension) {
        // 存在于图片封装表中即视为图片目标
        return IMAGE_MUXERS.containsKey(normalize(extension));
    }

    // 获取指定视频目标格式的默认视频编码器候选链，返回只读副本
    public static List<String> videoEncoderCandidates(String extension) {
        // 先把扩展名换算成封装名，查表时必须使用换算后的名字
        String muxer = videoMuxer(extension);
        // 封装名为空时返回空候选，说明该扩展名不在视频目标清单内
        if (muxer == null) {
            // 返回空列表表示没有可用视频编码器
            return List.of();
        }
        // 用封装名查候选链
        return candidates(VIDEO_ENCODERS, muxer);
    }

    // 获取指定音频目标格式的默认音频编码器候选链，返回只读副本
    public static List<String> audioEncoderCandidates(String extension) {
        // 先把扩展名换算成封装名
        String muxer = audioMuxer(extension);
        // 封装名为空时返回空候选
        if (muxer == null) {
            // 返回空列表表示没有可用音频编码器
            return List.of();
        }
        // 用封装名查候选链
        return candidates(AUDIO_ENCODERS, muxer);
    }

    // 按封装名取得视频编码器候选链，用于视频目标与 mkv 这类跨类别封装的共用查询
    public static List<String> videoCandidatesByMuxer(String muxer) {
        // 直接按封装名查表
        return candidates(VIDEO_ENCODERS, muxer);
    }

    // 按封装名取得音频编码器候选链
    public static List<String> audioCandidatesByMuxer(String muxer) {
        // 直接按封装名查表
        return candidates(AUDIO_ENCODERS, muxer);
    }

    // 内部工具方法：从候选表中取出列表并包装成不可变副本，避免外部修改静态表
    private static List<String> candidates(Map<String, List<String>> table, String muxer) {
        // 按键取出候选列表，可能为 null
        List<String> found = table.get(muxer);
        // 未登记时返回空列表而不是 null，简化调用方的判空逻辑
        if (found == null) {
            // 返回空不可变列表
            return List.of();
        }
        // 用新列表包装，防止外部对静态表内容造成影响
        return Collections.unmodifiableList(new ArrayList<>(found));
    }

    // 判断封装是否只允许视频轨
    public static boolean isVideoOnlyMuxer(String muxer) {
        // 集合中包含该封装名即为仅视频
        return muxer != null && VIDEO_ONLY_MUXERS.contains(muxer);
    }

    // 判断封装是否只允许音频轨
    public static boolean isAudioOnlyMuxer(String muxer) {
        // 集合中包含该封装名即为仅音频
        return muxer != null && AUDIO_ONLY_MUXERS.contains(muxer);
    }

    // 判断封装是否属于无损音频容器
    public static boolean isLosslessAudioMuxer(String muxer) {
        // 集合中包含该封装名即为无损容器
        return muxer != null && LOSSLESS_AUDIO_MUXERS.contains(muxer);
    }

    // 判断封装是否属于对编码器有强约束的现代容器
    public static boolean isModernVideoMuxer(String muxer) {
        // 集合中包含该封装名即需要显式指定编码器
        return muxer != null && MODERN_VIDEO_MUXERS.contains(muxer);
    }

    // 判断某编码器是否属于无损音频编码器，用于避免给无损编码器传码率参数
    public static boolean isLosslessAudioEncoder(String encoder) {
        // 编码器为空时视为非无损
        if (encoder == null) {
            // 返回 false 走有损分支
            return false;
        }
        // PCM 系列、flac、wavpack、tta 都属于无损编码器
        return encoder.startsWith("pcm_")
                // flac 是无损编码
                || encoder.equals("flac")
                // wavpack 是无损编码
                || encoder.equals("wavpack")
                // tta 是无损编码
                || encoder.equals("tta")
                // alac 是无损编码
                || encoder.equals("alac");
    }

    // 判断某编码器是否属于硬件编码器，用于决定质量参数使用 cq 还是 crf
    public static boolean isHardwareVideoEncoder(String encoder) {
        // 编码器为空时视为软件编码
        if (encoder == null) {
            // 返回 false 走软件分支
            return false;
        }
        // 常见硬件编码器后缀覆盖 nvenc、qsv、amf、videotoolbox、vaapi 等厂商
        return encoder.endsWith("_nvenc")
                // Intel Quick Sync 编码器
                || encoder.endsWith("_qsv")
                // AMD AMF 编码器
                || encoder.endsWith("_amf")
                // Apple VideoToolbox 编码器
                || encoder.endsWith("_videotoolbox")
                // Linux VAAPI 编码器
                || encoder.endsWith("_vaapi")
                // 硬件加速 v4l2 编码器
                || encoder.endsWith("_v4l2m2m");
    }

    // 判断某编码器是否属于 x264/x265 系列，用于决定 CRF 取值区间
    public static boolean isX26xEncoder(String encoder) {
        // libx264 与 libx265 使用相同的 CRF 语义
        return "libx264".equals(encoder) || "libx265".equals(encoder);
    }

    // 判断某编码器是否属于 vp9/av1 系列，这类编码器的 CRF 区间更宽
    public static boolean isVp9OrAv1(String encoder) {
        // 覆盖 vp9、av1 与它们的别名
        return "libvpx-vp9".equals(encoder)
                // libvpx 表示 vp8，CRF 区间与 vp9 接近
                || "libvpx".equals(encoder)
                // libaom-av1 是 AV1 的软件实现
                || "libaom-av1".equals(encoder)
                // libsvtav1 是 AV1 的快速实现
                || "libsvtav1".equals(encoder);
    }

    // 判断某编码器是否属于 mpeg4/mpeg2 系列，这些编码器使用 qscale 而不是 crf
    public static boolean isMpegFamilyEncoder(String encoder) {
        // mpeg4 使用 qscale 质量量化
        return "mpeg4".equals(encoder)
                // mpeg2video 使用 qscale 质量量化
                || "mpeg2video".equals(encoder)
                // mpeg1video 使用 qscale 质量量化
                || "mpeg1video".equals(encoder)
                // msmpeg4v3 属于微软 MPEG-4 变体
                || "msmpeg4v3".equals(encoder)
                // wmv2 属于微软编解码家族
                || "wmv2".equals(encoder)
                // h263 属于老式低码率编码
                || "h263".equals(encoder)
                // flv 属于老式 Flash 编码
                || "flv".equals(encoder)
                // mjpeg 使用 qscale 质量量化
                || "mjpeg".equals(encoder);
    }

    // 获取全部视频目标扩展名集合，供界面构建下拉框
    public static Set<String> videoTargets() {
        // 使用有序集合包装键集，保持定义顺序
        return Collections.unmodifiableSet(new LinkedHashSet<>(VIDEO_MUXERS.keySet()));
    }

    // 获取全部音频目标扩展名集合
    public static Set<String> audioTargets() {
        // 使用有序集合包装键集
        return Collections.unmodifiableSet(new LinkedHashSet<>(AUDIO_MUXERS.keySet()));
    }

    // 获取全部图片目标扩展名集合
    public static Set<String> imageTargets() {
        // 使用有序集合包装键集
        return Collections.unmodifiableSet(new LinkedHashSet<>(IMAGE_MUXERS.keySet()));
    }

    // 归一化扩展名：去掉前导点号并统一小写
    public static String normalize(String extension) {
        // 空输入返回空串，避免后续空指针
        if (extension == null) {
            // 返回空串表示无效扩展名
            return "";
        }
        // 去掉首尾空白
        String trimmed = extension.trim();
        // 去掉可能存在的点号前缀，兼容 ".jpg" 写法
        if (trimmed.startsWith(".")) {
            // 截取点号之后的文本
            trimmed = trimmed.substring(1);
        }
        // 统一转小写，兼容 "PNG" 这类输入
        return trimmed.toLowerCase();
    }
}
