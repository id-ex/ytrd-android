package io.github.idex.ytrdroid.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.domain.model.DownloadError;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import static io.github.idex.ytrdroid.domain.model.TaskSnapshot.State.*;

/**
 * Single owner of queue state. Serial executor MUST be FIFO and single-threaded.
 * Worker completion (not a cancellation request) releases the active slot.
 */
public final class DownloadCoordinator {
    public interface Pipeline {
        TaskSnapshot run(DownloadRequest request, UUID executionId, CancellationToken cancellation,
                         Consumer<TaskSnapshot> progress) throws Exception;
    }
    private final Executor serial;
    private final Executor workers;
    private final Pipeline pipeline;
    private final Consumer<List<TaskSnapshot>> listener;
    private final Map<UUID, TaskSnapshot> tasks = new LinkedHashMap<>();
    private Execution active;
    private boolean closed;
    private boolean executionEnabled = true;

    /** Service destruction pauses work without losing ownership of a stopping worker. */
    public void setExecutionEnabled(boolean enabled) {
        serial.execute(() -> {
            executionEnabled = enabled;
            if (!enabled && active != null) {
                TaskSnapshot old = tasks.get(active.taskId);
                if (old.state != CANCELLING) {
                    tasks.put(active.taskId, state(old, PAUSING, "Остановка службы…"));
                }
                active.cancellation.cancel();
            }
            advance();
        });
    }

    public void stoppingDeadline(UUID executionId) {
        serial.execute(() -> {
            if (active == null || !active.id.equals(executionId)) return;
            TaskSnapshot old = tasks.get(active.taskId);
            if (old.state != PAUSING && old.state != CANCELLING) return;
            tasks.put(active.taskId, new TaskSnapshot(old.request, old.executionId, old.state,
                    old.progress, old.downloadedBytes, old.totalBytes, 0,
                    "Остановка задерживается; следующая загрузка ожидает", old.resultReference,
                    new DownloadError(DownloadError.Category.INTERNAL, DownloadError.Stage.DOWNLOAD,
                            true, "Не удалось быстро остановить загрузку", null)));
            publish();
        });
    }

    private static final class Execution {
        final UUID taskId;
        final UUID id = UUID.randomUUID();
        final CancellationToken cancellation = new CancellationToken();
        Execution(UUID taskId) { this.taskId = taskId; }
    }

    public DownloadCoordinator(Executor serial, Executor workers, Pipeline pipeline,
                               Consumer<List<TaskSnapshot>> listener) {
        this.serial = serial;
        this.workers = workers;
        this.pipeline = pipeline;
        this.listener = listener;
    }

    public void enqueue(DownloadRequest request) {
        serial.execute(() -> {
            if (closed || tasks.containsKey(request.id)) return;
            tasks.put(request.id, new TaskSnapshot(request, null, QUEUED, 0, 0, 0, 0,
                    "В очереди", null, null));
            advance();
        });
    }

    public void pause(UUID taskId) { stop(taskId, true); }
    public void cancel(UUID taskId) { stop(taskId, false); }

    private void stop(UUID taskId, boolean pause) {
        serial.execute(() -> {
            TaskSnapshot old = tasks.get(taskId);
            if (closed || old == null || old.state == DONE || old.state == CANCELLED) return;
            if (active != null && active.taskId.equals(taskId)) {
                if (old.state == CANCELLING || (pause && old.state == PAUSING)) return;
                tasks.put(taskId, state(old, pause ? PAUSING : CANCELLING,
                        pause ? "Остановка для паузы…" : "Отмена…"));
                active.cancellation.cancel();
                publish(); // Do not advance until the worker returns.
            } else {
                tasks.put(taskId, state(old, pause ? PAUSED : CANCELLED,
                        pause ? "На паузе" : "Отменено"));
                advance();
            }
        });
    }

    public void resume(UUID taskId) { requeue(taskId, false); }
    public void retry(UUID taskId) { requeue(taskId, true); }

    private void requeue(UUID taskId, boolean retry) {
        serial.execute(() -> {
            TaskSnapshot old = tasks.get(taskId);
            if (closed || old == null || (retry ? old.state != ERROR && old.state != INTERRUPTED
                    : old.state != PAUSED)) return;
            tasks.put(taskId, new TaskSnapshot(old.request, null, QUEUED, 0, 0, 0, 0,
                    "В очереди", old.resultReference, null));
            advance();
        });
    }

    public void remove(UUID taskId) {
        serial.execute(() -> {
            if (active != null && active.taskId.equals(taskId)) {
                // Active removal is cancellation; retain identity until acknowledgement.
                cancel(taskId);
                return;
            }
            tasks.remove(taskId);
            publish();
        });
    }

    public void close() {
        serial.execute(() -> {
            closed = true;
            if (active != null) {
                TaskSnapshot old = tasks.get(active.taskId);
                tasks.put(active.taskId, state(old, CANCELLING, "Остановка службы…"));
                active.cancellation.cancel();
            }
        });
    }

    private void advance() {
        if (!closed && executionEnabled && active == null) {
            for (TaskSnapshot task : tasks.values()) {
                if (task.state != QUEUED) continue;
                Execution execution = new Execution(task.request.id);
                active = execution;
                TaskSnapshot starting = new TaskSnapshot(task.request, execution.id,
                        task.request.translate ? TRANSLATING : DOWNLOADING,
                        -1, 0, 0, 0, "Подготовка…", null, null);
                tasks.put(task.request.id, starting);
                try {
                    workers.execute(() -> run(execution, task.request));
                } catch (RuntimeException failure) {
                    tasks.put(task.request.id, failed(starting, failure));
                    active = null;
                }
                break;
            }
        }
        publish();
    }

    private void run(Execution execution, DownloadRequest request) {
        TaskSnapshot result;
        try {
            execution.cancellation.throwIfCancelled();
            result = pipeline.run(request, execution.id, execution.cancellation,
                    event -> serial.execute(() -> progress(execution, event)));
            if (!execution.cancellation.isCancelled() && (result == null || (result.state != DONE && result.state != ERROR))) {
                throw new IllegalStateException("Pipeline returned without a terminal result");
            }
        } catch (Exception failure) {
            result = failed(new TaskSnapshot(request, execution.id, ERROR, 0, 0, 0, 0,
                    null, null, null), failure);
        } finally {
            // Never let the interrupt flag leak into another execution on this pool.
            Thread.interrupted();
        }
        TaskSnapshot completed = result;
        serial.execute(() -> finish(execution, completed));
    }

    private void progress(Execution execution, TaskSnapshot event) {
        if (!owns(execution) || event == null || !execution.id.equals(event.executionId)
                || !execution.taskId.equals(event.request.id)) return;
        TaskSnapshot old = tasks.get(execution.taskId);
        if (old.state == PAUSING || old.state == CANCELLING) return;
        if (event.state != TRANSLATING && event.state != DOWNLOADING && event.state != PROCESSING) return;
        tasks.put(execution.taskId, event);
        publish();
    }

    private void finish(Execution execution, TaskSnapshot result) {
        if (!owns(execution)) return;
        TaskSnapshot old = tasks.get(execution.taskId);
        if (old.state == PAUSING) tasks.put(execution.taskId, state(old, PAUSED, "На паузе"));
        else if (old.state == CANCELLING) tasks.put(execution.taskId, state(old, CANCELLED, "Отменено"));
        else if (!execution.id.equals(result.executionId) || !execution.taskId.equals(result.request.id)) {
            tasks.put(execution.taskId, failed(old, new IllegalStateException("Wrong execution result")));
        } else tasks.put(execution.taskId, result);
        active = null;
        advance();
    }

    private boolean owns(Execution execution) { return active == execution; }

    private static TaskSnapshot failed(TaskSnapshot old, Exception failure) {
        failure.printStackTrace();
        String message = (failure.getMessage() != null && !failure.getMessage().trim().isEmpty())
                ? failure.getMessage()
                : "Не удалось завершить загрузку";
        DownloadError.Category category = (failure instanceof java.io.IOException)
                ? DownloadError.Category.NETWORK
                : DownloadError.Category.INTERNAL;
        return new TaskSnapshot(old.request, old.executionId, ERROR, old.progress,
                old.downloadedBytes, old.totalBytes, 0, "Ошибка загрузки", old.resultReference,
                new DownloadError(category, DownloadError.Stage.DOWNLOAD,
                        true, message, failure.getClass().getSimpleName()));
    }

    private static TaskSnapshot state(TaskSnapshot old, TaskSnapshot.State state, String message) {
        return new TaskSnapshot(old.request, old.executionId, state, old.progress,
                old.downloadedBytes, old.totalBytes, 0, message, old.resultReference, old.error);
    }

    private void publish() {
        if (!closed) listener.accept(Collections.unmodifiableList(new ArrayList<>(tasks.values())));
    }
}
