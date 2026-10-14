package io.github.idex.ytrdroid.data.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.domain.model.DownloadError;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

public final class TaskRepository {
    private final TaskDao dao;
    private final Executor executor;
    private final Map<UUID, Long> lastPersistTimes = new ConcurrentHashMap<>();
    private final Map<UUID, TaskSnapshot.State> lastPersistStates = new ConcurrentHashMap<>();

    public TaskRepository(TaskDao dao) {
        this(dao, Executors.newSingleThreadExecutor(r -> new Thread(r, "task-db")));
    }

    public TaskRepository(TaskDao dao, Executor executor) {
        this.dao = dao;
        this.executor = executor;
    }

    public void save(TaskSnapshot snapshot, Runnable onComplete) {
        if (snapshot == null) return;
        UUID id = snapshot.request.id;
        TaskSnapshot.State state = snapshot.state;
        long now = System.currentTimeMillis();

        Long lastTime = lastPersistTimes.get(id);
        TaskSnapshot.State lastState = lastPersistStates.get(id);

        boolean isTerminalOrStateChange = (lastState != state) || isTerminalState(state);
        boolean timeElapsed = (lastTime == null) || (now - lastTime >= 1000);

        if (!isTerminalOrStateChange && !timeElapsed) {
            if (onComplete != null) onComplete.run();
            return;
        }

        lastPersistTimes.put(id, now);
        lastPersistStates.put(id, state);

        executor.execute(() -> {
            TaskEntity entity = toEntity(snapshot);
            dao.upsert(entity);
            if (onComplete != null) onComplete.run();
        });
    }

    public void delete(UUID taskId, Runnable onComplete) {
        if (taskId == null) return;
        lastPersistTimes.remove(taskId);
        lastPersistStates.remove(taskId);
        executor.execute(() -> {
            dao.delete(taskId.toString());
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

    public void getAllSnapshots(Consumer<List<TaskSnapshot>> callback) {
        executor.execute(() -> {
            List<TaskEntity> items = dao.getAll();
            List<TaskSnapshot> snapshots = new ArrayList<>();
            for (TaskEntity item : items) {
                try {
                    TaskSnapshot s = toSnapshot(item);
                    if (s != null) snapshots.add(s);
                } catch (Exception ignored) {}
            }
            callback.accept(snapshots);
        });
    }

    private static boolean isTerminalState(TaskSnapshot.State state) {
        return state == TaskSnapshot.State.DONE
                || state == TaskSnapshot.State.ERROR
                || state == TaskSnapshot.State.CANCELLED
                || state == TaskSnapshot.State.INTERRUPTED
                || state == TaskSnapshot.State.PAUSED;
    }

    public static TaskSnapshot toSnapshot(TaskEntity e) {
        if (e == null || e.id == null) return null;
        UUID id = UUID.fromString(e.id);
        DownloadRequest.ResultType resultType = e.resultType != null
                ? DownloadRequest.ResultType.valueOf(e.resultType)
                : DownloadRequest.ResultType.VIDEO;
        DownloadRequest.Container container = e.container != null
                ? DownloadRequest.Container.valueOf(e.container)
                : DownloadRequest.Container.MP4;
        DownloadRequest.Voice voice = e.voice != null
                ? DownloadRequest.Voice.valueOf(e.voice)
                : DownloadRequest.Voice.STANDARD;
        DownloadRequest.AudioMode audioMode = e.audioMode != null
                ? DownloadRequest.AudioMode.valueOf(e.audioMode)
                : DownloadRequest.AudioMode.ORIGINAL;
        DownloadRequest.Subtitles subtitles = e.subtitles != null
                ? DownloadRequest.Subtitles.valueOf(e.subtitles)
                : DownloadRequest.Subtitles.NONE;

        DownloadRequest request = new DownloadRequest(
                id,
                e.videoId != null ? e.videoId : "unknown",
                e.title,
                e.thumbnail,
                e.height,
                resultType,
                container,
                e.translate,
                voice,
                audioMode,
                subtitles,
                e.destination,
                e.duration,
                e.language
        );

        UUID executionId = e.executionId != null ? UUID.fromString(e.executionId) : null;
        TaskSnapshot.State state = e.state != null
                ? TaskSnapshot.State.valueOf(e.state)
                : TaskSnapshot.State.INTERRUPTED;
        DownloadError error = e.errorMessage != null
                ? new DownloadError(DownloadError.Category.INTERNAL, DownloadError.Stage.DOWNLOAD, true, e.errorMessage, null)
                : null;

        return new TaskSnapshot(
                request,
                executionId,
                state,
                e.progress,
                e.downloadedBytes,
                e.totalBytes,
                e.speed,
                e.stageText,
                e.resultReference,
                error
        );
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
