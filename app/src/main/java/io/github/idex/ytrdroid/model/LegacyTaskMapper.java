package io.github.idex.ytrdroid.model;

import java.util.UUID;

import io.github.idex.ytrdroid.domain.model.DownloadError;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import io.github.idex.ytrdroid.util.UrlUtil;

/** Temporary boundary for the legacy UI/engine. New code uses immutable models. */
public final class LegacyTaskMapper {
    private LegacyTaskMapper() {}

    public static DownloadRequest freeze(DownloadTask task) {
        String videoId = UrlUtil.extractVideoId(task.url);
        boolean audio = "audio".equals(task.quality) || "mp3".equals(task.ext);
        Integer height = null;
        if (!audio && task.quality != null && !"auto".equals(task.quality)) {
            try { height = Integer.valueOf(task.quality); }
            catch (NumberFormatException e) { throw new DownloadRequest.ValidationError("Некорректное качество"); }
        }
        DownloadRequest.Container container;
        if (audio) container = DownloadRequest.Container.MP3;
        else if (task.ext == null || "mp4".equals(task.ext)) container = DownloadRequest.Container.MP4;
        else if ("mkv".equals(task.ext)) container = DownloadRequest.Container.MKV;
        else throw new DownloadRequest.ValidationError("Неизвестный контейнер");
        DownloadRequest.AudioMode mode = DownloadRequest.AudioMode.ORIGINAL;
        if (task.translate) {
            if (audio) mode = DownloadRequest.AudioMode.TRANSLATION_ONLY;
            else if ("dual".equals(task.audioMode)) mode = DownloadRequest.AudioMode.DUAL;
            else if (task.audioMode == null || "mix".equals(task.audioMode)) mode = DownloadRequest.AudioMode.MIX;
            else throw new DownloadRequest.ValidationError("Неизвестный режим звука");
        }
        return new DownloadRequest(UUID.randomUUID(), videoId, task.title, task.thumbnail,
                height, audio ? DownloadRequest.ResultType.AUDIO : DownloadRequest.ResultType.VIDEO,
                container, task.translate,
                task.translate && task.liveVoice ? DownloadRequest.Voice.LIVE : DownloadRequest.Voice.STANDARD,
                mode, task.subtitles ? DownloadRequest.Subtitles.RU_THEN_EN : DownloadRequest.Subtitles.NONE,
                task.destinationPath, task.duration, task.language);
    }

    /** Allocate a private worker/UI copy; outputPath always means a result. */
    public static DownloadTask fromRequest(DownloadRequest request, long legacyId) {
        DownloadTask task = new DownloadTask();
        task.bindRequest(request);
        task.id = legacyId;
        task.url = request.url;
        task.title = request.title;
        task.thumbnail = request.thumbnail;
        task.quality = request.resultType == DownloadRequest.ResultType.AUDIO ? "audio"
                : request.height == null ? "auto" : request.height.toString();
        task.ext = request.container.name().toLowerCase(java.util.Locale.ROOT);
        task.translate = request.translate;
        task.liveVoice = request.voice == DownloadRequest.Voice.LIVE;
        task.audioMode = request.audioMode == DownloadRequest.AudioMode.DUAL ? "dual" : "mix";
        task.subtitles = request.subtitles != DownloadRequest.Subtitles.NONE;
        task.destinationPath = request.destination;
        task.duration = request.duration;
        task.language = request.language;
        return task;
    }

    public static TaskSnapshot snapshot(DownloadTask task) {
        DownloadError error = task.state == DownloadTask.State.ERROR
                ? new DownloadError(DownloadError.Category.INTERNAL, DownloadError.Stage.DOWNLOAD,
                    true, "Не удалось завершить загрузку", null) : null;
        return new TaskSnapshot(task.getRequest(), task.executionId,
                TaskSnapshot.State.valueOf(task.state.name()), task.progress,
                task.downloadedBytes, task.totalBytes, task.speed, task.stageText, task.outputPath, error);
    }

    public static DownloadTask fromSnapshot(TaskSnapshot snapshot) {
        DownloadTask task = fromRequest(snapshot.request, snapshot.request.id.getMostSignificantBits() & Long.MAX_VALUE);
        task.executionId = snapshot.executionId;
        task.state = DownloadTask.State.valueOf(snapshot.state.name());
        task.progress = snapshot.progress;
        task.downloadedBytes = snapshot.downloadedBytes;
        task.totalBytes = snapshot.totalBytes;
        task.speed = snapshot.speed;
        task.stageText = snapshot.stageText;
        task.outputPath = snapshot.resultReference;
        task.errorMessage = snapshot.error == null ? null : snapshot.error.message;
        return task;
    }

    public static DownloadTask detached(DownloadTask source) {
        DownloadTask copy = fromRequest(source.getRequest(), source.id);
        copy.executionId = source.executionId;
        copy.state = source.state;
        copy.progress = source.progress;
        copy.downloadedBytes = source.downloadedBytes;
        copy.totalBytes = source.totalBytes;
        copy.speed = source.speed;
        copy.stageText = source.stageText;
        copy.errorMessage = source.errorMessage;
        copy.outputPath = source.outputPath;
        return copy;
    }
}
