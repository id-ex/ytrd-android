package io.github.idex.ytrdroid.service;

import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import java.util.List;
import java.util.Objects;

/** Pure-Java policy for task notifications and notification actions. */
public final class NotificationPolicy {
    private NotificationPolicy() { }

    /** Null previous means there was no observed event (for example, service restoration). */
    public static boolean isTerminalTransition(TaskSnapshot previous, TaskSnapshot current) {
        return previous != null && current != null
                && previous.request.id.equals(current.request.id)
                && !isTerminal(previous.state)
                && (current.state == TaskSnapshot.State.DONE || current.state == TaskSnapshot.State.ERROR);
    }

    public static boolean hasWork(List<TaskSnapshot> snapshots) {
        if (snapshots == null) return false;
        for (TaskSnapshot snapshot : snapshots) {
            if (snapshot != null && (isRunning(snapshot.state)
                    || snapshot.state == TaskSnapshot.State.PAUSED
                    || snapshot.state == TaskSnapshot.State.QUEUED)) {
                return true;
            }
        }
        return false;
    }

    /** PAUSED actions may omit executionId and are then matched against the task id. */
    public static boolean matchesAction(TaskSnapshot current, String taskId, String executionId) {
        if (current == null || taskId == null || !current.request.id.toString().equals(taskId)) {
            return false;
        }
        if (current.state == TaskSnapshot.State.PAUSED && current.executionId == null) {
            return current.request.id.toString().equals(executionId);
        }
        return executionId != null && current.executionId != null
                && Objects.equals(current.executionId.toString(), executionId);
    }

    private static boolean isRunning(TaskSnapshot.State state) {
        return state == TaskSnapshot.State.PAUSING || state == TaskSnapshot.State.ANALYZING
                || state == TaskSnapshot.State.TRANSLATING || state == TaskSnapshot.State.DOWNLOADING
                || state == TaskSnapshot.State.PROCESSING || state == TaskSnapshot.State.CANCELLING;
    }

    private static boolean isTerminal(TaskSnapshot.State state) {
        return state == TaskSnapshot.State.DONE || state == TaskSnapshot.State.ERROR
                || state == TaskSnapshot.State.CANCELLED;
    }
}
