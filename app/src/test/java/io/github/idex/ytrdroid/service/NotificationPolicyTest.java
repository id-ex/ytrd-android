package io.github.idex.ytrdroid.service;

import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationPolicyTest {
    private TaskSnapshot snapshot(TaskSnapshot.State state, UUID taskId, UUID executionId) {
        DownloadRequest request = new DownloadRequest(taskId, "abcdefghijk", "Video", null,
                null, DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 60, "en");
        return new TaskSnapshot(request, executionId, state, -1, 0, 0, 0, null, null, null);
    }

    private TaskSnapshot snapshot(TaskSnapshot.State state) {
        return snapshot(state, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test public void terminalTransitionsAreReportedButTerminalToTerminalIsNot() {
        UUID id = UUID.randomUUID();
        UUID execution = UUID.randomUUID();
        for (TaskSnapshot.State terminal : Arrays.asList(TaskSnapshot.State.DONE, TaskSnapshot.State.ERROR)) {
            assertTrue(NotificationPolicy.isTerminalTransition(
                    snapshot(TaskSnapshot.State.DOWNLOADING, id, execution), snapshot(terminal, id, execution)));
            assertFalse(NotificationPolicy.isTerminalTransition(
                    snapshot(terminal, id, execution), snapshot(terminal, id, execution)));
        }
        assertFalse(NotificationPolicy.isTerminalTransition(snapshot(TaskSnapshot.State.DOWNLOADING, id, execution),
                snapshot(TaskSnapshot.State.CANCELLED, id, execution)));
        assertFalse(NotificationPolicy.isTerminalTransition(null, snapshot(TaskSnapshot.State.DONE)));
        assertFalse(NotificationPolicy.isTerminalTransition(snapshot(TaskSnapshot.State.ERROR, id, execution),
                snapshot(TaskSnapshot.State.QUEUED, id, execution)));
        assertTrue(NotificationPolicy.isTerminalTransition(snapshot(TaskSnapshot.State.QUEUED, id, execution),
                snapshot(TaskSnapshot.State.ERROR, id, execution)));
    }

    @Test public void queuedAndPausedCountAsWork() {
        assertTrue(NotificationPolicy.hasWork(Collections.singletonList(snapshot(TaskSnapshot.State.QUEUED))));
        assertTrue(NotificationPolicy.hasWork(Collections.singletonList(snapshot(TaskSnapshot.State.PAUSED))));
        assertFalse(NotificationPolicy.hasWork(Collections.singletonList(snapshot(TaskSnapshot.State.DONE))));
        assertFalse(NotificationPolicy.hasWork(Collections.emptyList()));
    }

    @Test public void actionMustMatchTaskAndCurrentExecution() {
        UUID taskId = UUID.randomUUID();
        UUID currentExecution = UUID.randomUUID();
        TaskSnapshot downloading = snapshot(TaskSnapshot.State.DOWNLOADING, taskId, currentExecution);
        assertTrue(NotificationPolicy.matchesAction(downloading, taskId.toString(), currentExecution.toString()));
        assertFalse(NotificationPolicy.matchesAction(downloading, taskId.toString(), UUID.randomUUID().toString()));
        assertFalse(NotificationPolicy.matchesAction(downloading, UUID.randomUUID().toString(), currentExecution.toString()));
        TaskSnapshot paused = snapshot(TaskSnapshot.State.PAUSED, taskId, currentExecution);
        assertTrue(NotificationPolicy.matchesAction(paused, taskId.toString(), currentExecution.toString()));
        assertFalse(NotificationPolicy.matchesAction(paused, taskId.toString(), null));
        TaskSnapshot queuedPaused = snapshot(TaskSnapshot.State.PAUSED, taskId, null);
        assertTrue(NotificationPolicy.matchesAction(queuedPaused, taskId.toString(), taskId.toString()));
        assertFalse(NotificationPolicy.matchesAction(snapshot(TaskSnapshot.State.QUEUED, taskId,
                currentExecution), taskId.toString(), null));
    }
}
