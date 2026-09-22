package io.github.idex.ytrdroid.domain.model;

/**
 * Deterministic execution plan for container, codecs, and stream mappings.
 * Unified between UI display and FFmpeg execution pipeline.
 */
public final class MediaPlan {
    public enum ContainerType { MP4, MKV, MP3 }
    public enum VideoCodec { COPY, NONE }
    public enum AudioCodec { COPY, AAC, MP3 }
    public enum SubtitleFormat { MOV_TEXT, SRT, NONE }

    public final ContainerType container;
    public final VideoCodec videoCodec;
    public final AudioCodec audioCodec;
    public final SubtitleFormat subtitleFormat;
    public final boolean isMix;
    public final boolean isDual;
    public final boolean isAudioOnly;

    public MediaPlan(ContainerType container, VideoCodec videoCodec, AudioCodec audioCodec,
                     SubtitleFormat subtitleFormat, boolean isMix, boolean isDual, boolean isAudioOnly) {
        this.container = container;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.subtitleFormat = subtitleFormat;
        this.isMix = isMix;
        this.isDual = isDual;
        this.isAudioOnly = isAudioOnly;
    }

    /** Derives the media plan from a validated download request. */
    public static MediaPlan fromRequest(DownloadRequest request) {
        if (request == null) throw new IllegalArgumentException("Request must not be null");

        if (request.resultType == DownloadRequest.ResultType.AUDIO) {
            return new MediaPlan(ContainerType.MP3, VideoCodec.NONE, AudioCodec.MP3,
                    SubtitleFormat.NONE, false, false, true);
        }

        ContainerType container = request.container == DownloadRequest.Container.MKV
                ? ContainerType.MKV : ContainerType.MP4;

        SubtitleFormat subFormat = SubtitleFormat.NONE;
        if (request.subtitles != DownloadRequest.Subtitles.NONE) {
            subFormat = container == ContainerType.MKV ? SubtitleFormat.SRT : SubtitleFormat.MOV_TEXT;
        }

        boolean mix = request.audioMode == DownloadRequest.AudioMode.MIX;
        boolean dual = request.audioMode == DownloadRequest.AudioMode.DUAL;
        AudioCodec aCodec = mix ? AudioCodec.AAC : AudioCodec.COPY;

        return new MediaPlan(container, VideoCodec.COPY, aCodec, subFormat, mix, dual, false);
    }
}
