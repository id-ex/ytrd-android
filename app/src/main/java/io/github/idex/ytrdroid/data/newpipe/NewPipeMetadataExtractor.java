package io.github.idex.ytrdroid.data.newpipe;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import io.github.idex.ytrdroid.model.VideoInfo;

public final class NewPipeMetadataExtractor {

    private NewPipeMetadataExtractor() {}

    /**
     * Extracts full real video metadata and available quality heights via NewPipeExtractor
     * in pure Java within ~1 second.
     */
    public static VideoInfo extract(String url) throws Exception {
        StreamInfo streamInfo = StreamInfo.getInfo(url);

        VideoInfo info = new VideoInfo();
        info.url = url;
        info.title = streamInfo.getName();
        info.uploader = streamInfo.getUploaderName();
        info.duration = streamInfo.getDuration();
        info.ext = "mp4";

        Locale lang = streamInfo.getLanguageInfo();
        info.language = lang != null ? lang.getLanguage() : null;

        List<Image> thumbs = streamInfo.getThumbnails();
        if (thumbs != null && !thumbs.isEmpty()) {
            info.thumbnail = thumbs.get(thumbs.size() - 1).getUrl();
        }

        Set<Integer> heights = new TreeSet<>(Collections.reverseOrder());

        // Check muxed streams
        List<VideoStream> videoStreams = streamInfo.getVideoStreams();
        if (videoStreams != null) {
            for (VideoStream vs : videoStreams) {
                if (vs.getHeight() >= 144) {
                    heights.add(vs.getHeight());
                }
            }
        }

        // Check video-only DASH streams (e.g. 1080p, 1440p, 4K)
        List<VideoStream> videoOnlyStreams = streamInfo.getVideoOnlyStreams();
        if (videoOnlyStreams != null) {
            for (VideoStream vs : videoOnlyStreams) {
                if (vs.getHeight() >= 144) {
                    heights.add(vs.getHeight());
                }
            }
        }

        info.qualities = new ArrayList<>();
        for (int h : heights) {
            info.qualities.add(String.valueOf(h));
        }
        if (info.qualities.isEmpty()) {
            info.qualities.add("auto");
        }

        return info;
    }
}
