package io.github.idex.ytrdroid.data.persistence;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

public final class TaskRepository {
    private final TaskDao dao;
    private final Executor executor;

    public TaskRepository(TaskDao dao) {
        this(dao, Executors.newSingleThreadExecutor(r -> new Thread(r, "task-db")));
    }

    public TaskRepository(TaskDao dao, Executor executor) {
        this.dao = dao;
        this.executor = executor;
    }

    public void save(TaskSnapshot snapshot, Runnable onComplete) {
        if (snapshot == null) return;
        executor.execute(() -> {
            TaskEntity entity = toEntity(snapshot);
            dao.upsert(entity);
            if (onComplete != null) onComplete.run();
        });
    }

    public void markActiveAsInterrupted(Runnable onComplete) {
        executor.execute(() -> {
            dao.markActiveAsInterrupted();
            if (onComplete != null) onComplete.run();
        });
    }

    public void getAll(Consumer<List<TaskEntity>> callback) {
        executor.execute(() -> {
            List<TaskEntity> items = dao.getAll();
            callback.accept(items);
        });
    }

    private static TaskEntity toEntity(TaskSnapshot s) {
        TaskEntity e = new TaskEntity();
        e.id = s.request.id.toString();
        e.videoId = s.request.videoId;
        e.url = s.request.url;
        e.title = s.request.title;
        e.thumbnail = s.request.thumbnail;
        e.height = s.request.height;
        e.resultType = s.request.resultType.name();
        e.container = s.request.container.name();
        e.translate = s.request.translate;
        e.voice = s.request.voice.name();
        e.audioMode = s.request.audioMode.name();
        e.subtitles = s.request.subtitles.name();
        e.destination = s.request.destination;
        e.duration = s.request.duration;
        e.language = s.request.language;

        e.state = s.state.name();
        e.executionId = s.executionId != null ? s.executionId.toString() : null;
        e.progress = s.progress;
        e.downloadedBytes = s.downloadedBytes;
        e.totalBytes = s.totalBytes;
        e.speed = s.speed;
        e.stageText = s.stageText;
        e.resultReference = s.resultReference;
        e.errorMessage = s.error != null ? s.error.message : null;
        e.updatedAt = System.currentTimeMillis();
        return e;
    }
}
