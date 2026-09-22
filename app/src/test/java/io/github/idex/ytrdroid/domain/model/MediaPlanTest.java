package io.github.idex.ytrdroid.domain.model;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class MediaPlanTest {
    private DownloadRequest createRequest(DownloadRequest.ResultType resultType,
                                          DownloadRequest.Container container,
                                          DownloadRequest.AudioMode audioMode,
                                          DownloadRequest.Subtitles subtitles) {
        return new DownloadRequest(UUID.randomUUID(), "abcdefghijk", "Title", null,
                resultType == DownloadRequest.ResultType.AUDIO ? null : 1080,
                resultType, container,
                audioMode != DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Voice.STANDARD, audioMode, subtitles, null, 120.0, "en");
    }

    @Test
    public void audioRequestProducesMp3Plan() {
        DownloadRequest r = createRequest(DownloadRequest.ResultType.AUDIO,
                DownloadRequest.Container.MP3, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE);
        MediaPlan plan = MediaPlan.fromRequest(r);

        assertTrue(plan.isAudioOnly);
        assertEquals(MediaPlan.ContainerType.MP3, plan.container);
        assertEquals(MediaPlan.VideoCodec.NONE, plan.videoCodec);
        assertEquals(MediaPlan.AudioCodec.MP3, plan.audioCodec);
        assertEquals(MediaPlan.SubtitleFormat.NONE, plan.subtitleFormat);
    }

    @Test
    public void videoMkvWithSubtitlesProducesSrt() {
        DownloadRequest r = createRequest(DownloadRequest.ResultType.VIDEO,
                DownloadRequest.Container.MKV, DownloadRequest.AudioMode.MIX,
                DownloadRequest.Subtitles.RU_THEN_EN);
        MediaPlan plan = MediaPlan.fromRequest(r);

        assertFalse(plan.isAudioOnly);
        assertEquals(MediaPlan.ContainerType.MKV, plan.container);
        assertEquals(MediaPlan.SubtitleFormat.SRT, plan.subtitleFormat);
        assertEquals(MediaPlan.AudioCodec.AAC, plan.audioCodec);
        assertTrue(plan.isMix);
    }

    @Test
    public void videoMp4WithSubtitlesProducesMovText() {
        DownloadRequest r = createRequest(DownloadRequest.ResultType.VIDEO,
                DownloadRequest.Container.MP4, DownloadRequest.AudioMode.DUAL,
                DownloadRequest.Subtitles.RU_THEN_EN);
        MediaPlan plan = MediaPlan.fromRequest(r);

        assertEquals(MediaPlan.ContainerType.MP4, plan.container);
        assertEquals(MediaPlan.SubtitleFormat.MOV_TEXT, plan.subtitleFormat);
        assertEquals(MediaPlan.AudioCodec.COPY, plan.audioCodec);
        assertTrue(plan.isDual);
    }
}
