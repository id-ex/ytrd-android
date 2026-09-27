package io.github.idex.ytrdroid.model;

import org.junit.Test;

import java.util.UUID;

import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import static org.junit.Assert.assertEquals;

public class LegacyTaskMapperAnalyzingTest {
    @Test
    public void analyzingStateRoundTripsWithRequestAndExecutionIdentity() {
        UUID requestId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        DownloadRequest request = new DownloadRequest(requestId, "abcdefghijk", null, null,
                null, DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4,
                false, DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
        TaskSnapshot snapshot = new TaskSnapshot(request, executionId, TaskSnapshot.State.ANALYZING,
                -1, 0, 0, 0, null, null, null);

        DownloadTask task = LegacyTaskMapper.fromSnapshot(snapshot);

        assertEquals(DownloadTask.State.ANALYZING, task.state);
        assertEquals(requestId, task.getRequest().id);
        assertEquals(executionId, task.executionId);

        TaskSnapshot roundTrip = LegacyTaskMapper.snapshot(task);
        assertEquals(snapshot.state, roundTrip.state);
        assertEquals(request.id, roundTrip.request.id);
        assertEquals(executionId, roundTrip.executionId);
        assertEquals(request.videoId, roundTrip.request.videoId);
        assertEquals(request.title, roundTrip.request.title);
        assertEquals(request.duration, roundTrip.request.duration, 0);
        assertEquals(request.resultType, roundTrip.request.resultType);
        assertEquals(request.container, roundTrip.request.container);
        assertEquals(request.audioMode, roundTrip.request.audioMode);
    }
}
