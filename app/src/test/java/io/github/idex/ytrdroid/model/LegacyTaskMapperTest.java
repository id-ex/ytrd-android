package io.github.idex.ytrdroid.model;

import org.junit.Test;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import static org.junit.Assert.*;

public class LegacyTaskMapperTest {
    private DownloadTask input() {
        DownloadTask task = new DownloadTask();
        task.url = "https://youtu.be/abcdefghijk";
        task.quality = "720";
        task.ext = "mp4";
        task.destinationPath = "content://documents/tree/downloads";
        return task;
    }
    @Test public void requestIsFrozenAndDestinationNeverBecomesResult() {
        DownloadTask input = input();
        DownloadRequest request = LegacyTaskMapper.freeze(input);
        input.quality = "2160";
        input.destinationPath = "changed";
        DownloadTask worker = LegacyTaskMapper.fromRequest(request, 1);
        assertEquals("720", worker.quality);
        assertNull(worker.outputPath);
        worker.outputPath = "/published/file.mp4";
        assertEquals("content://documents/tree/downloads", request.destination);
        TaskSnapshot snapshot = LegacyTaskMapper.snapshot(worker);
        worker.outputPath = "changed";
        assertEquals("/published/file.mp4", snapshot.resultReference);
    }
    @Test public void legacyProjectionCannotChangeSnapshot() {
        DownloadTask worker = LegacyTaskMapper.fromRequest(LegacyTaskMapper.freeze(input()), 1);
        TaskSnapshot snapshot = LegacyTaskMapper.snapshot(worker);
        DownloadTask ui = LegacyTaskMapper.fromSnapshot(snapshot);
        ui.quality = "1080";
        ui.state = DownloadTask.State.DONE;
        assertEquals(Integer.valueOf(720), snapshot.request.height);
        assertEquals(TaskSnapshot.State.QUEUED, snapshot.state);
    }
    @Test public void rejectsInvalidIdentityAndQuality() {
        DownloadTask task = input();
        task.url = "https://notyoutube.com/watch?v=abcdefghijk";
        assertThrows(DownloadRequest.ValidationError.class, () -> LegacyTaskMapper.freeze(task));
        task.url = "https://youtu.be/abcdefghijk";
        task.quality = "best";
        assertThrows(DownloadRequest.ValidationError.class, () -> LegacyTaskMapper.freeze(task));
        task.quality = "-1";
        assertThrows(DownloadRequest.ValidationError.class, () -> LegacyTaskMapper.freeze(task));
    }
    @Test public void rejectsAudioWithSubtitles() {
        DownloadTask task = input();
        task.quality = "audio";
        task.ext = "mp3";
        task.subtitles = true;
        assertThrows(DownloadRequest.ValidationError.class, () -> LegacyTaskMapper.freeze(task));
    }
    @Test public void originalDisablesIrrelevantLegacyVoiceAndMixOptions() {
        DownloadTask task = input();
        task.liveVoice = true;
        task.audioMode = "dual";
        DownloadRequest request = LegacyTaskMapper.freeze(task);
        assertEquals(DownloadRequest.Voice.STANDARD, request.voice);
        assertEquals(DownloadRequest.AudioMode.ORIGINAL, request.audioMode);
    }
    @Test public void translationAudioUsesTranslationOnlyMode() {
        DownloadTask task = input();
        task.quality = "audio";
        task.ext = "mp3";
        task.translate = true;
        DownloadRequest request = LegacyTaskMapper.freeze(task);
        assertEquals(DownloadRequest.AudioMode.TRANSLATION_ONLY, request.audioMode);
        assertNull(request.height);
    }
    @Test public void requestCannotBeReplacedAndSnapshotClampsInvalidProgress() {
        DownloadRequest request = LegacyTaskMapper.freeze(input());
        DownloadTask worker = LegacyTaskMapper.fromRequest(request, 1);
        assertThrows(IllegalStateException.class, () -> worker.bindRequest(request));
        TaskSnapshot snapshot = new TaskSnapshot(request, null, TaskSnapshot.State.QUEUED,
                Float.NaN, -1, -1, Float.POSITIVE_INFINITY, null, null, null);
        assertEquals(-1f, snapshot.progress, 0f);
        assertEquals(0, snapshot.downloadedBytes);
        assertEquals(0f, snapshot.speed, 0f);
    }
}
