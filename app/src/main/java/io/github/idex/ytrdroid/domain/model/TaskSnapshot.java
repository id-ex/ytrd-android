package io.github.idex.ytrdroid.domain.model;

import java.util.UUID;

/** A detached, immutable observation. It cannot be used to mutate a running task. */
public final class TaskSnapshot {
    public enum State {
        QUEUED, PAUSING, PAUSED, ANALYZING, TRANSLATING, DOWNLOADING, PROCESSING,
        CANCELLING, DONE, ERROR, CANCELLED, INTERRUPTED
    }
    public final DownloadRequest request;
    public final UUID executionId;
    public final State state;
    /** Stage progress, not progress of the entire pipeline; -1 means unknown. */
    public final float progress;
    public final long downloadedBytes;
    public final long totalBytes;
    public final float speed;
    public final String stageText;
    public final String resultReference;
    public final DownloadError error;

    public TaskSnapshot(DownloadRequest request, UUID executionId, State state,
            float progress, long downloadedBytes, long totalBytes, float speed,
            String stageText, String resultReference, DownloadError error) {
        if (request == null || state == null) throw new IllegalArgumentException("Missing task identity/state");
        this.request = request;
        this.executionId = executionId;
        this.state = state;
        this.progress = Float.isFinite(progress) ? Math.max(-1, Math.min(100, progress)) : -1;
        this.downloadedBytes = Math.max(0, downloadedBytes);
        this.totalBytes = Math.max(0, totalBytes);
        this.speed = Float.isFinite(speed) ? Math.max(0, speed) : 0;
        this.stageText = stageText;
        this.resultReference = resultReference;
        this.error = error;
    }
}
