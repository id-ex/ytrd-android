package io.github.idex.ytrdroid.service;

import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationSummaryTest {
    private TaskSnapshot snapshot(TaskSnapshot.State state, float progress, long downloaded,
            long total, float speed, String stage, String title) {
        DownloadRequest request = new DownloadRequest(UUID.randomUUID(), "abcdefghijk", title,
                null, null, DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4,
                false, DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 60, "en");
        return new TaskSnapshot(request, UUID.randomUUID(), state, progress, downloaded,
                total, speed, stage, null, null);
    }

    @Test public void analysisDoesNotShowUnknownZeroPercent() {
        TaskSnapshot task = snapshot(TaskSnapshot.State.ANALYZING, -1, 0, 0, 0, null, "Video");
        assertEquals("Анализ видео", NotificationSummary.stage(task));
        assertEquals("", NotificationSummary.metrics(task));
        assertEquals("Анализ видео", NotificationSummary.details(task));
        assertFalse(NotificationSummary.details(task).contains("%"));
    }

    @Test public void downloadingShowsTransferAndKnownPercent() {
        TaskSnapshot task = snapshot(TaskSnapshot.State.DOWNLOADING, 42, 1024 * 1024,
                4 * 1024 * 1024, 512 * 1024, "Downloading", "Video");
        assertEquals("Downloading", NotificationSummary.stage(task));
        assertEquals("1.0/4.0 МБ · 0.5 МБ/с · 42%", NotificationSummary.metrics(task));
        assertEquals("Downloading · 1.0/4.0 МБ · 0.5 МБ/с · 42%", NotificationSummary.details(task));
    }

    @Test public void pausedUsesStageAndDownloadedBytesWithoutSpeed() {
        TaskSnapshot task = snapshot(TaskSnapshot.State.PAUSED, 50, 2 * 1024 * 1024,
                0, 1024 * 1024, null, null);
        assertEquals("На паузе", NotificationSummary.stage(task));
        assertEquals("2.0 МБ · 50%", NotificationSummary.metrics(task));
        assertEquals("На паузе · 2.0 МБ · 50%", NotificationSummary.details(task));
        assertEquals(task.request.url, NotificationSummary.title(task));
    }

    @Test public void processingAndErrorShowStageAndProgress() {
        TaskSnapshot processing = snapshot(TaskSnapshot.State.PROCESSING, 75, 0, 0, 0,
                "Merging audio", "Video");
        TaskSnapshot error = snapshot(TaskSnapshot.State.ERROR, -1, 0, 0, 0, null, "Video");
        assertTrue(NotificationSummary.details(processing).contains("Merging audio"));
        assertTrue(NotificationSummary.details(processing).contains("75%"));
        assertEquals("Ошибка", NotificationSummary.details(error));
        assertEquals("Video", NotificationSummary.title(error));
    }
}
