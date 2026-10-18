package com.xu.universaltranscoder.core;

// 引入 ArrayList，用于在构建信息对象时收集流列表

import java.util.ArrayList;
// 引入 Collections，提供不可变列表包装，防止外部修改内部集合
import java.util.Collections;
// 引入 List 接口，作为流列表的类型
import java.util.List;

// 关键字 class：MediaInfo 承载一次 ffprobe 探测的结果，是命令构建器的输入数据源
// 之所以把容器信息与流信息放在一个对象里：命令构建既要看总时长，也要看每条流的类型与参数
public final class MediaInfo {

    // 封装格式名称，例如 mov,mp4,m4a,3gp,3g2,mj2 或 matroska,webm
    private final String formatName;
    // 媒体总时长（秒），用于进度百分比换算与时长显示
    private final double durationSeconds;
    // 媒体总码率（比特每秒），探测不到时为 0
    private final long bitRate;
    // 文件大小（字节），探测不到时为 0
    private final long sizeBytes;
    // 全部流信息列表，顺序与 ffprobe 输出一致
    private final List<Stream> streams;
    // 探测是否成功的标记，失败时上层需要给出可读提示
    private final boolean valid;

    // 构造方法：接收已经解析完成的各项属性
    public MediaInfo(String formatName, double durationSeconds, long bitRate, long sizeBytes, List<Stream> streams, boolean valid) {
        // 保存封装格式名，界面与命令构建都会读取
        this.formatName = formatName;
        // 保存总时长，进度换算依赖它
        this.durationSeconds = durationSeconds;
        // 保存总码率，作为码率自动档位的参考
        this.bitRate = bitRate;
        // 保存文件大小，队列页显示体积时使用
        this.sizeBytes = sizeBytes;
        // 保存流列表的不可变包装，避免外部意外修改
        this.streams = Collections.unmodifiableList(streams);
        // 保存有效标记，无效时界面会提示探测失败原因
        this.valid = valid;
    }

    // 创建一个表示探测失败的空对象，避免返回 null 造成调用方大量判空
    public static MediaInfo invalid() {
        // 传入空集合与 false 标记构造无效对象
        return new MediaInfo("", 0d, 0L, 0L, new ArrayList<>(), false);
    }

    // 获取封装格式名称
    public String formatName() {
        // 返回封装名
        return formatName;
    }

    // 获取媒体总时长（秒）
    public double durationSeconds() {
        // 返回总时长
        return durationSeconds;
    }

    // 获取媒体总码率
    public long bitRate() {
        // 返回总码率
        return bitRate;
    }

    // 获取文件大小
    public long sizeBytes() {
        // 返回文件字节数
        return sizeBytes;
    }

    // 获取全部流信息
    public List<Stream> streams() {
        // 返回不可变流列表
        return streams;
    }

    // 判断探测是否有效
    public boolean valid() {
        // 返回有效标记
        return valid;
    }

    // 判断是否包含视频流，决定界面是否允许选择视频相关参数
    public boolean hasVideo() {
        // 任取一条流判断其类型是否为 video
        return firstStream("video") != null;
    }

    // 判断是否包含音频流，决定界面是否允许选择音频相关参数
    public boolean hasAudio() {
        // 任取一条流判断其类型是否为 audio
        return firstStream("audio") != null;
    }

    // 判断是否包含字幕流，决定字幕相关控件是否可用
    public boolean hasSubtitle() {
        // 任取一条流判断其类型是否为 subtitle
        return firstStream("subtitle") != null;
    }

    // 按类型查找第一条流，找不到返回 null
    public Stream firstStream(String codecType) {
        // 遍历全部流
        for (Stream stream : streams) {
            // 类型匹配即返回该流
            if (codecType.equals(stream.codecType())) {
                // 返回匹配到的流对象
                return stream;
            }
        }
        // 未找到时返回 null
        return null;
    }

    // 统计指定类型的流数量，用于判断多音轨等复杂情况
    public int countStreams(String codecType) {
        // 计数器初始为 0
        int count = 0;
        // 遍历全部流
        for (Stream stream : streams) {
            // 类型匹配时计数加一
            if (codecType.equals(stream.codecType())) {
                // 累加计数
                count++;
            }
        }
        // 返回统计结果
        return count;
    }

    // 生成用于日志的摘要文本，包含时长、分辨率与编码等关键事实
    public String describe() {
        // 无效对象直接返回失败说明
        if (!valid) {
            // 返回英文提示，因为该文案可能显示给用户
            return "No media information available.";
        }
        // 使用 StringBuilder 组织摘要文本
        StringBuilder builder = new StringBuilder();
        // 追加封装名
        builder.append("format=").append(formatName);
        // 追加时长，保留三位小数便于查看毫秒级精度
        builder.append(", duration=").append(String.format("%.3f", durationSeconds)).append("s");
        // 取视频流，可能为 null
        Stream video = firstStream("video");
        // 视频流存在时追加分辨率与帧率信息
        if (video != null) {
            // 追加分辨率与帧率，帮助用户确认输入参数
            builder.append(", video=").append(video.width()).append("x").append(video.height())
                    // 追加帧率文本
                    .append('@').append(video.frameRateText())
                    // 追加视频编码名
                    .append('/').append(video.codecName());
        }
        // 取音频流，可能为 null
        Stream audio = firstStream("audio");
        // 音频流存在时追加采样率与声道信息
        if (audio != null) {
            // 追加采样率与声道数与编码名
            builder.append(", audio=").append(audio.sampleRate()).append("Hz/")
                    // 追加声道数
                    .append(audio.channels()).append("ch/")
                    // 追加音频编码名
                    .append(audio.codecName());
        }
        // 返回拼好的摘要文本
        return builder.toString();
    }

    // 静态嵌套类 Stream：描述单条媒体流的属性
    public static final class Stream {

        // 流序号，命令构建中的 -map 与流裁剪会用到
        private final int index;
        // 流类型：video / audio / subtitle / data
        private final String codecType;
        // 编码名称，例如 h264 / aac / mp3 / png
        private final String codecName;
        // 像素格式，例如 yuv420p，用于判断是否需要加 pad 补救
        private final String pixelFormat;
        // 画面宽度，非视频流为 0
        private final int width;
        // 画面高度，非视频流为 0
        private final int height;
        // 平均帧率（浮点），非视频流为 0
        private final double frameRate;
        // 平均帧率的原始文本，例如 30000/1001，便于日志展示
        private final String frameRateText;
        // 采样率（赫兹），非音频流为 0
        private final int sampleRate;
        // 声道数，非音频流为 0
        private final int channels;
        // 该流码率，探测不到为 0
        private final long bitRate;
        // 该流时长（秒），部分容器只在流级给出时长
        private final double durationSeconds;
        // 语言标签，提取自 tags.language
        private final String language;
        // 标题标签，提取自 tags.title 或 tags.handler_name
        private final String title;

        // 构造方法：一次性接收全部流属性，避免大量 setter 造成对象可变异
        public Stream(int index, String codecType, String codecName, String pixelFormat, int width, int height,
                      double frameRate, String frameRateText, int sampleRate, int channels, long bitRate,
                      double durationSeconds, String language, String title) {
            // 保存流序号
            this.index = index;
            // 保存流类型
            this.codecType = codecType;
            // 保存编码名
            this.codecName = codecName;
            // 保存像素格式
            this.pixelFormat = pixelFormat;
            // 保存宽度
            this.width = width;
            // 保存高度
            this.height = height;
            // 保存帧率浮点值
            this.frameRate = frameRate;
            // 保存帧率原始文本
            this.frameRateText = frameRateText;
            // 保存采样率
            this.sampleRate = sampleRate;
            // 保存声道数
            this.channels = channels;
            // 保存流码率
            this.bitRate = bitRate;
            // 保存流时长
            this.durationSeconds = durationSeconds;
            // 保存语言标签
            this.language = language;
            // 保存标题标签
            this.title = title;
        }

        // 获取流序号
        public int index() {
            // 返回序号
            return index;
        }

        // 获取流类型
        public String codecType() {
            // 返回类型文本
            return codecType;
        }

        // 获取编码名
        public String codecName() {
            // 返回编码名
            return codecName;
        }

        // 获取像素格式
        public String pixelFormat() {
            // 返回像素格式文本
            return pixelFormat;
        }

        // 获取画面宽度
        public int width() {
            // 返回宽度
            return width;
        }

        // 获取画面高度
        public int height() {
            // 返回高度
            return height;
        }

        // 获取平均帧率（浮点）
        public double frameRate() {
            // 返回帧率浮点值
            return frameRate;
        }

        // 获取平均帧率原始文本
        public String frameRateText() {
            // 返回帧率原始文本
            return frameRateText;
        }

        // 获取采样率
        public int sampleRate() {
            // 返回采样率
            return sampleRate;
        }

        // 获取声道数
        public int channels() {
            // 返回声道数
            return channels;
        }

        // 获取流码率
        public long bitRate() {
            // 返回流码率
            return bitRate;
        }

        // 获取流时长
        public double durationSeconds() {
            // 返回流时长
            return durationSeconds;
        }

        // 获取语言标签
        public String language() {
            // 返回语言文本
            return language;
        }

        // 获取标题标签
        public String title() {
            // 返回标题文本
            return title;
        }

        // 判断宽度是否为奇数，奇数分辨率会导致部分编码器失败，需要触发 pad 补救
        public boolean hasOddWidth() {
            // 宽度非零且为奇数时返回 true
            return width > 0 && (width % 2 != 0);
        }

        // 判断高度是否为奇数，奇数分辨率会导致部分编码器失败，需要触发 pad 补救
        public boolean hasOddHeight() {
            // 高度非零且为奇数时返回 true
            return height > 0 && (height % 2 != 0);
        }

        // 生成一行便于日志阅读的流描述
        public String describe() {
            // 使用 StringBuilder 组织文本
            StringBuilder builder = new StringBuilder();
            // 追加序号与类型
            builder.append('#').append(index).append(' ').append(codecType);
            // 追加编码名
            builder.append('/').append(codecName);
            // 视频流补充分辨率与帧率
            if ("video".equals(codecType)) {
                // 追加分辨率与帧率文本
                builder.append(' ').append(width).append('x').append(height).append('@').append(frameRateText);
            }
            // 音频流补充采样率与声道数
            if ("audio".equals(codecType)) {
                // 追加采样率与声道信息
                builder.append(' ').append(sampleRate).append("Hz ").append(channels).append("ch");
            }
            // 返回拼好的文本
            return builder.toString();
        }
    }
}
