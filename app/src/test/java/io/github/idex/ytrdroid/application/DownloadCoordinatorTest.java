package io.github.idex.ytrdroid.application;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import static io.github.idex.ytrdroid.domain.model.TaskSnapshot.State.*;
import static org.junit.Assert.*;

public class DownloadCoordinatorTest {
    static final class ManualExecutor implements Executor {
        final Queue<Runnable> pending = new ArrayDeque<>();
        public void execute(Runnable work) { pending.add(work); }
        void next() { pending.remove().run(); }
        void drain() { while (!pending.isEmpty()) next(); }
    }
    final ManualExecutor serial = new ManualExecutor();
    final ManualExecutor workers = new ManualExecutor();
    List<TaskSnapshot> latest = new ArrayList<>();
    final List<UUID> executions = new ArrayList<>();
    Consumer<TaskSnapshot> oldCallback;
    TaskSnapshot oldEvent;
    Runnable duringRun = () -> {};
    boolean fail;
    final DownloadCoordinator coordinator = new DownloadCoordinator(serial, workers,
            (request, execution, cancellation, progress) -> {
                executions.add(execution);
                TaskSnapshot event = snapshot(request, execution, DOWNLOADING);
                oldCallback = progress;
                oldEvent = event;
                progress.accept(event);
                duringRun.run();
                if (fail) throw new IllegalStateException("fake failure");
                // Deliberately ignores cancellation to model a late successful completion.
                return snapshot(request, execution, DONE);
            }, snapshots -> latest = snapshots);

    static DownloadRequest request() {
        return new DownloadRequest(UUID.randomUUID(), "abcdefghijk", "Video", null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    static TaskSnapshot snapshot(DownloadRequest request, UUID execution, TaskSnapshot.State state) {
        return new TaskSnapshot(request, execution, state, 10, 1, 10, 1,
                "stage", state == DONE ? "/legacy/result.mp4" : null, null);
    }
    TaskSnapshot task(DownloadRequest request) {
        return latest.stream().filter(s -> s.request.id.equals(request.id)).findFirst().get();
    }
    void enqueue(DownloadRequest... requests) {
        for (DownloadRequest request : requests) coordinator.enqueue(request);
        serial.drain();
    }
    void finish() { workers.next(); serial.drain(); }

    @Test public void pauseWaitsForTerminationAndDoesNotRestartOnlyTask() {
        DownloadRequest a = request();
        enqueue(a);
        duringRun = () -> {
            coordinator.pause(a.id);
            serial.drain();
            assertEquals(PAUSING, task(a).state);
            assertEquals(0, workers.pending.size());
        };
        finish();
        assertEquals(PAUSED, task(a).state);
        assertTrue(workers.pending.isEmpty());
    }

    @Test public void pausedTaskIsSkippedAndResumeCreatesNewExecution() {
        DownloadRequest a = request(), b = request();
        enqueue(a, b);
        duringRun = () -> { coordinator.pause(a.id); serial.drain(); };
        finish();
        UUID first = task(a).executionId;
        assertEquals(PAUSED, task(a).state);
        assertEquals(DOWNLOADING, task(b).state);
        duringRun = () -> {};
        coordinator.resume(a.id);
        serial.drain();
        assertEquals(QUEUED, task(a).state);
        assertEquals(1, workers.pending.size());
        finish();
        assertNotEquals(first, task(a).executionId);
        finish();
        assertEquals(DONE, task(a).state);
    }

    @Test public void cancelAThenLateProgressCannotReleaseBAndStartC() {
        DownloadRequest a = request(), b = request(), c = request();
        enqueue(a, b, c);
        duringRun = () -> {
            coordinator.cancel(a.id);
            serial.drain();
            assertEquals(CANCELLING, task(a).state);
            assertEquals(QUEUED, task(b).state);
            assertTrue(workers.pending.isEmpty());
        };
        finish();
        Consumer<TaskSnapshot> callbackA = oldCallback;
        TaskSnapshot eventA = oldEvent;
        assertEquals(CANCELLED, task(a).state);
        assertEquals(DOWNLOADING, task(b).state);
        callbackA.accept(eventA);
        callbackA.accept(snapshot(a, eventA.executionId, DONE));
        serial.drain();
        assertEquals(CANCELLED, task(a).state);
        assertEquals(QUEUED, task(c).state);
        assertEquals(1, workers.pending.size());
        duringRun = () -> {};
        finish();
        assertEquals(DOWNLOADING, task(c).state);
    }

    @Test public void retryOnlyAcceptsFailedTaskAndCreatesNewExecution() {
        DownloadRequest a = request();
        enqueue(a);
        coordinator.retry(a.id);
        serial.drain();
        assertEquals(1, workers.pending.size());
        fail = true;
        finish();
        UUID first = task(a).executionId;
        assertEquals(ERROR, task(a).state);
        assertNotNull(task(a).error);
        coordinator.retry(a.id);
        coordinator.retry(a.id);
        serial.drain();
        assertEquals(1, workers.pending.size());
        assertNotEquals(first, task(a).executionId);
        fail = false;
        finish();
        assertEquals(DONE, task(a).state);
    }

    @Test public void cancelledBeforeWorkerStartsDoesNotRunPipeline() {
        DownloadRequest a = request();
        enqueue(a);
        coordinator.cancel(a.id);
        serial.drain();
        finish();
        assertTrue(executions.isEmpty());
        assertEquals(CANCELLED, task(a).state);
    }

    @Test public void stoppingDeadlineKeepsSlotAndReportsError() {
        DownloadRequest a = request(), b = request();
        enqueue(a, b);
        coordinator.pause(a.id);
        serial.drain();
        coordinator.stoppingDeadline(task(a).executionId);
        serial.drain();
        assertEquals(PAUSING, task(a).state);
        assertNotNull(task(a).error);
        assertEquals(QUEUED, task(b).state);
        assertEquals(1, workers.pending.size());
    }

    @Test public void serviceRecreationCannotStartAnotherWorkerBeforeOldAcknowledgement() {
        DownloadRequest a = request(), b = request();
        enqueue(a);
        duringRun = () -> {
            coordinator.setExecutionEnabled(false);
            coordinator.setExecutionEnabled(true);
            coordinator.enqueue(b);
            serial.drain();
            assertEquals(PAUSING, task(a).state);
            assertEquals(QUEUED, task(b).state);
            assertTrue(workers.pending.isEmpty());
        };
        finish();
        assertEquals(PAUSED, task(a).state);
        assertEquals(DOWNLOADING, task(b).state);
    }

    @Test public void stoppingServiceDoesNotStartQueuedTasks() {
        DownloadRequest a = request(), b = request();
        enqueue(a, b);
        coordinator.setExecutionEnabled(false);
        serial.drain();
        finish();
        assertEquals(PAUSED, task(a).state);
        assertEquals(QUEUED, task(b).state);
        assertTrue(workers.pending.isEmpty());
    }

    @Test public void publishedListCannotBeMutatedAndOldSnapshotsDoNotChange() {
        DownloadRequest a = request();
        enqueue(a);
        List<TaskSnapshot> original = latest;
        assertThrows(UnsupportedOperationException.class, original::clear);
        finish();
        assertEquals(DOWNLOADING, original.get(0).state);
        assertEquals(DONE, latest.get(0).state);
    }

    @Test public void cancelOverridesPauseButResumeCannotInterruptStopping() {
        DownloadRequest a = request();
        enqueue(a);
        coordinator.pause(a.id);
        coordinator.resume(a.id);
        coordinator.cancel(a.id);
        coordinator.pause(a.id);
        serial.drain();
        assertEquals(CANCELLING, task(a).state);
        finish();
        assertEquals(CANCELLED, task(a).state);
        assertTrue(workers.pending.isEmpty());
    }
}
