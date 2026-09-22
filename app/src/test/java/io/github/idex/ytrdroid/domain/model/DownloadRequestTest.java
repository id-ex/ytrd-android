package io.github.idex.ytrdroid.domain.model;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class DownloadRequestTest {
    private static final String VIDEO_ID = "abcdefghijk";
    private DownloadRequest video(boolean translate) {
        return new DownloadRequest(UUID.randomUUID(), VIDEO_ID, "Title", null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, translate,
                translate ? DownloadRequest.Voice.LIVE : DownloadRequest.Voice.STANDARD,
                translate ? DownloadRequest.AudioMode.MIX : DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 120.0, "en");
    }

    @Test public void validVideoRequestCreated() {
        DownloadRequest r = video(false);
        assertEquals(VIDEO_ID, r.videoId);
        assertEquals("https://www.youtube.com/watch?v=" + VIDEO_ID, r.url);
        assertEquals(Integer.valueOf(720), r.height);
    }
    @Test public void validTranslatedAudioRequestCreated() {
        DownloadRequest r = new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, null,
                DownloadRequest.ResultType.AUDIO, DownloadRequest.Container.MP3, true,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.TRANSLATION_ONLY,
                DownloadRequest.Subtitles.NONE, null, 0, null);
        assertNull(r.height);
        assertTrue(r.translate);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsAudioWithHeight() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, 720,
                DownloadRequest.ResultType.AUDIO, DownloadRequest.Container.MP3, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsVideoInMp3() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP3, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsLiveVoiceWithoutTranslation() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.LIVE, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsNegativeHeight() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, -1,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsBadVideoId() {
        new DownloadRequest(UUID.randomUUID(), "short", null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsNegativeDuration() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, -1.0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsAudioWithSubtitles() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, null,
                DownloadRequest.ResultType.AUDIO, DownloadRequest.Container.MP3, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.RU_THEN_EN, null, 0, null);
    }
    @Test(expected = DownloadRequest.ValidationError.class)
    public void rejectsTranslatedVideoWithOriginalMode() {
        new DownloadRequest(UUID.randomUUID(), VIDEO_ID, null, null, 720,
                DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, true,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 0, null);
    }
    @Test public void snapshotClampsOutOfRangeProgress() {
        TaskSnapshot snapshot = new TaskSnapshot(video(false), null, TaskSnapshot.State.QUEUED,
                200f, -5, -10, Float.NaN, null, null, null);
        assertEquals(100f, snapshot.progress, 0f);
        assertEquals(0, snapshot.downloadedBytes);
        assertEquals(0, snapshot.totalBytes);
        assertEquals(0f, snapshot.speed, 0f);
    }
}
