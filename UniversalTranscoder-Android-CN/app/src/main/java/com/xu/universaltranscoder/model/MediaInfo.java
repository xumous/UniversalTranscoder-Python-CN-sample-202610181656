package com.xu.universaltranscoder.model;

// 引入 ArrayList：收集轨道信息。

import java.util.ArrayList;
// 引入 Collections：返回只读列表。
import java.util.Collections;
// 引入 List：轨道列表类型。
import java.util.List;

/**
 * MediaInfo 是“媒体元信息”的数据载体。
 * 它同时承载两类来源的信息：
 * 1) MediaMetadataRetriever 给出的整体信息（时长、时长分辨率、总码率、旋转角）；
 * 2) MediaExtractor 给出的逐轨道信息（轨迹 MIME、采样率、声道数），这是重封装与重编码必须知道的。
 */
public final class MediaInfo {

    // 全部轨道信息。
    public final List<Track> tracks = new ArrayList<>();
    // 展示用文件名。
    public String displayName = "";
    // 源文件 URI 字符串。
    public String uriString = "";
    // 总时长（微秒），未知为 0。
    public long durationUs;
    // 视频宽（取第一条视频轨），未知为 0。
    public int width;
    // 视频高（取第一条视频轨），未知为 0。
    public int height;
    // 总码率（比特/秒），未知为 0。
    public long bitrate;
    // 旋转角度（0/90/180/270），未知为 0。
    public int rotation;

    /**
     * 是否存在视频轨。
     */
    public boolean hasVideo() {
        // 遍历轨道查找视频轨。
        for (Track track : tracks) {
            // 找到即返回真。
            if (track.video) {
                // 返回存在。
                return true;
            }
        }
        // 未找到。
        return false;
    }

    /**
     * 是否存在音频轨。
     */
    public boolean hasAudio() {
        // 遍历轨道查找音频轨。
        for (Track track : tracks) {
            // 找到即返回真。
            if (track.audio) {
                // 返回存在。
                return true;
            }
        }
        // 未找到。
        return false;
    }

    /**
     * 返回第一条视频轨，没有则返回 null。
     */
    public Track firstVideo() {
        // 遍历轨道。
        for (Track track : tracks) {
            // 命中视频轨即返回。
            if (track.video) {
                // 返回该轨道。
                return track;
            }
        }
        // 无视频轨。
        return null;
    }

    /**
     * 返回第一条音频轨，没有则返回 null。
     */
    public Track firstAudio() {
        // 遍历轨道。
        for (Track track : tracks) {
            // 命中音频轨即返回。
            if (track.audio) {
                // 返回该轨道。
                return track;
            }
        }
        // 无音频轨。
        return null;
    }

    /**
     * 返回只读的轨道列表，防止界面误改。
     */
    public List<Track> tracksView() {
        // 包装成只读列表返回。
        return Collections.unmodifiableList(tracks);
    }

    /**
     * 生成供界面展示的多行文本（中文）。
     */
    public String describe() {
        // 使用 StringBuilder 逐行拼接，保证换行在 TextView 中可读。
        StringBuilder builder = new StringBuilder();
        // 第一行：文件名。
        builder.append("文件：").append(displayName).append('\n');
        // 第二行：容器/主类型 MIME（取第一条轨道 MIME 作为参考）。
        builder.append("轨道数：").append(tracks.size()).append('\n');
        // 第三行：时长。
        builder.append("时长：").append(com.xu.universaltranscoder.util.FormatUtil.formatDuration(durationUs / 1000L)).append('\n');
        // 第四行：分辨率（仅在有视频轨时输出）。
        if (hasVideo()) {
            // 输出宽高与旋转角。
            builder.append("视频：").append(width).append('x').append(height)
                    .append(" 旋转 ").append(rotation).append("°\n");
        }
        // 第五行：音频参数（仅在有音频轨时输出）。
        Track audio = firstAudio();
        // 判断是否存在音频轨。
        if (audio != null) {
            // 输出采样率与声道数。
            builder.append("音频：").append(audio.sampleRate).append(" Hz，")
                    .append(audio.channelCount).append(" 声道").append('\n');
        }
        // 第六行：总码率。
        builder.append("码率：").append(bitrate > 0 ? (bitrate / 1000L) + " kbps" : "未知").append('\n');
        // 逐条列出轨道的 MIME，这是判断“能否重封装”的关键信息。
        for (Track track : tracks) {
            // 输出形如 “#0 video/avc (video)”。
            builder.append("  #").append(track.index).append(' ').append(track.mime)
                    .append(track.video ? "（视频）" : (track.audio ? "（音频）" : "（其它）"))
                    .append('\n');
        }
        // 返回拼接结果。
        return builder.toString();
    }

    /**
     * 单条轨道的信息。
     */
    public static final class Track {
        // 轨道在源文件中的下标，重封装/重编码时需要按此下标 selectTrack。
        public final int index;
        // 轨道 MIME，例如 video/avc。
        public final String mime;
        // 是否为视频轨。
        public final boolean video;
        // 是否为音频轨。
        public final boolean audio;
        // 视频宽（仅视频轨有效）。
        public final int width;
        // 视频高（仅视频轨有效）。
        public final int height;
        // 音频采样率（仅音频轨有效）。
        public final int sampleRate;
        // 音频声道数（仅音频轨有效）。
        public final int channelCount;
        // 轨道语言（部分文件不提供，可能为空字符串）。
        public final String language;

        // 构造：字段较多但都是只读值对象，直接一一赋值。
        public Track(int index, String mime, boolean video, boolean audio, int width, int height,
                     int sampleRate, int channelCount, String language) {
            // 保存轨道下标。
            this.index = index;
            // 保存 MIME。
            this.mime = mime;
            // 保存视频标记。
            this.video = video;
            // 保存音频标记。
            this.audio = audio;
            // 保存宽度。
            this.width = width;
            // 保存高度。
            this.height = height;
            // 保存采样率。
            this.sampleRate = sampleRate;
            // 保存声道数。
            this.channelCount = channelCount;
            // 保存语言。
            this.language = language;
        }
    }
}
