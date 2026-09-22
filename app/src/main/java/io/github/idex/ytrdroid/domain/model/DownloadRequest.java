package io.github.idex.ytrdroid.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Immutable enqueue-time configuration, independent of Android and execution state. */
public final class DownloadRequest {
    public enum ResultType { VIDEO, AUDIO }
    public enum Container { MP4, MKV, MP3 }
    public enum Voice { STANDARD, LIVE }
    public enum AudioMode { ORIGINAL, MIX, DUAL, TRANSLATION_ONLY }
    public enum Subtitles { NONE, RU_THEN_EN }

    public final UUID id;
    public final String videoId;
    public final String url;
    public final String title;
    public final String thumbnail;
    /** Null means automatic video quality; audio never has a height. */
    public final Integer height;
    public final ResultType resultType;
    public final Container container;
    public final boolean translate;
    public final Voice voice;
    public final AudioMode audioMode;
    public final Subtitles subtitles;
    /** Requested destination, never replaced with a published output path. */
    public final String destination;
    public final double duration;
    public final String language;

    public DownloadRequest(UUID id, String videoId, String title, String thumbnail,
            Integer height, ResultType resultType, Container container, boolean translate,
            Voice voice, AudioMode audioMode, Subtitles subtitles, String destination,
            double duration, String language) {
        if (id == null || videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            throw new ValidationError("Некорректный идентификатор видео или задачи");
        }
        if (resultType == null || container == null || voice == null || audioMode == null
                || subtitles == null || !Double.isFinite(duration) || duration < 0) {
            throw new ValidationError("Некорректные параметры загрузки");
        }
        if (height != null && height <= 0) throw new ValidationError("Некорректное качество");
        if (resultType == ResultType.AUDIO && (height != null || container != Container.MP3
                || subtitles != Subtitles.NONE)) {
            throw new ValidationError("Аудио несовместимо с параметрами видео");
        }
        if (resultType == ResultType.VIDEO && container == Container.MP3) {
            throw new ValidationError("MP3 не поддерживает видео");
        }
        if (!translate && (voice != Voice.STANDARD || audioMode != AudioMode.ORIGINAL)) {
            throw new ValidationError("Режим перевода выбран без перевода");
        }
        if (translate && (resultType == ResultType.AUDIO
                ? audioMode != AudioMode.TRANSLATION_ONLY
                : audioMode != AudioMode.MIX && audioMode != AudioMode.DUAL)) {
            throw new ValidationError("Несовместимый режим аудиодорожек");
        }
        this.id = id;
        this.videoId = videoId;
        this.url = "https://www.youtube.com/watch?v=" + videoId;
        this.title = title;
        this.thumbnail = thumbnail;
        this.height = height;
        this.resultType = resultType;
        this.container = container;
        this.translate = translate;
        this.voice = voice;
        this.audioMode = audioMode;
        this.subtitles = subtitles;
        this.destination = destination;
        this.duration = duration;
        this.language = language;
    }

    public static final class ValidationError extends IllegalArgumentException {
        public ValidationError(String message) { super(message); }
    }
}
