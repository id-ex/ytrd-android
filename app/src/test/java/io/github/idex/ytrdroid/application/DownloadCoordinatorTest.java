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
    final List<DownloadRequest> executedRequests = new ArrayList<>();
    Consumer<TaskSnapshot> oldCallback;
    TaskSnapshot oldEvent;
    Runnable duringRun = () -> {};
    boolean fail;
    final DownloadCoordinator coordinator = new DownloadCoordinator(serial, workers,
            (request, execution, cancellation, progress) -> {
                executions.add(execution);
                executedRequests.add(request);
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

    @Test public void earlyRequestStartsAnalyzingAndAcceptsAnalyzingProgress() {
        DownloadRequest early = new DownloadRequest(UUID.randomUUID(), "abcdefghijk", null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
        enqueue(early);
        assertEquals(ANALYZING, task(early).state);
        duringRun = () -> {
            TaskSnapshot analyzing = snapshot(early, task(early).executionId, ANALYZING);
            oldCallback.accept(analyzing);
            serial.drain();
            assertSame(analyzing, task(early));
        };
        finish();
        assertEquals(DONE, task(early).state);
    }

    @Test public void pauseDuringAnalyzingWaitsForWorkerBeforeStartingNext() {
        DownloadRequest a = new DownloadRequest(UUID.randomUUID(), "abcdefghijk", null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
        DownloadRequest b = request();
        enqueue(a, b);
        duringRun = () -> {
            oldCallback.accept(snapshot(a, task(a).executionId, ANALYZING));
            coordinator.pause(a.id);
            serial.drain();
            assertEquals(PAUSING, task(a).state);
            assertEquals(QUEUED, task(b).state);
            assertTrue(workers.pending.isEmpty());
            duringRun = () -> {};
        };
        finish();
        assertEquals(PAUSED, task(a).state);
        assertEquals(DOWNLOADING, task(b).state);
        finish();
        assertEquals(2, executions.size());
    }

    @Test public void barrierRunsFifoAfterEarlierCommandHasPublishedSnapshots() {
        DownloadRequest a = request();
        List<String> order = new ArrayList<>();
        coordinator.enqueue(a);
        coordinator.afterPendingCommands(() -> order.add("barrier:" + task(a).state));
        assertTrue(order.isEmpty());
        serial.drain();
        assertEquals(java.util.Arrays.asList("barrier:DOWNLOADING"), order);
        assertEquals(DOWNLOADING, task(a).state);
    }

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

    @Test public void lateAnalyzingProgressAfterCancellationCannotOverwriteCancelled() {
        DownloadRequest a = request();
        enqueue(a);
        duringRun = () -> {
            coordinator.cancel(a.id);
            serial.drain();
            assertEquals(CANCELLING, task(a).state);
        };
        finish();
        Consumer<TaskSnapshot> callback = oldCallback;
        UUID execution = task(a).executionId;
        assertEquals(CANCELLED, task(a).state);
        DownloadRequest analyzed = new DownloadRequest(a.id, a.videoId, "Analyzed", a.thumbnail, 1080,
                a.resultType, a.container, a.translate, a.voice, a.audioMode, a.subtitles,
                a.destination, a.duration, a.language);
        callback.accept(snapshot(analyzed, execution, ANALYZING));
        serial.drain();
        assertEquals(CANCELLED, task(a).state);
        assertSame(a, task(a).request);
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

    @Test public void retryUsesRequestEnrichedBySuccessfulAnalysisProgress() {
        DownloadRequest early = new DownloadRequest(UUID.randomUUID(), "abcdefghijk", null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
        enqueue(early);
        DownloadRequest analyzed = new DownloadRequest(early.id, early.videoId, "Analyzed title", early.thumbnail, 1080,
                early.resultType, early.container, early.translate, early.voice, early.audioMode,
                early.subtitles, early.destination, 300, early.language);
        duringRun = () -> {
            oldCallback.accept(snapshot(analyzed, task(early).executionId, ANALYZING));
            serial.drain();
            duringRun = () -> {};
        };
        fail = true;
        finish();
        assertEquals(ERROR, task(early).state);
        assertEquals("Analyzed title", task(early).request.title);
        fail = false;
        coordinator.retry(early.id);
        serial.drain();
        assertSame(analyzed, task(early).request);
        finish();
        assertSame(analyzed, executedRequests.get(1));
        assertEquals(Integer.valueOf(1080), executedRequests.get(1).height);
        assertEquals(DONE, task(early).state);
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
