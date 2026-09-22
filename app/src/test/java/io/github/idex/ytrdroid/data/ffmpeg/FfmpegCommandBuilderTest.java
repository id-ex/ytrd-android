package io.github.idex.ytrdroid.data.ffmpeg;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class FfmpegCommandBuilderTest {
    private final File video = new File("/tmp/video.mp4");
    private final File audio = new File("/tmp/audio.mp3");
    private final File sub = new File("/tmp/sub.ru.srt");
    private final File outMp4 = new File("/tmp/out.mp4");
    private final File outMkv = new File("/tmp/out.mkv");

    @Test
    public void mixCommandContainsExpectedArgs() {
        List<String> cmd = FfmpegCommandBuilder.buildMix(video, audio, null, "en", "mp4", outMp4);
        assertTrue(cmd.contains("-y"));
        assertTrue(cmd.contains(video.getAbsolutePath()));
        assertTrue(cmd.contains(audio.getAbsolutePath()));
        assertTrue(cmd.contains("-filter_complex"));
        // Ensure duration=first is used so original video/audio isn't cut short
        String filter = cmd.get(cmd.indexOf("-filter_complex") + 1);
        assertTrue(filter.contains("duration=first"));
        assertTrue(filter.contains("volume=0.2"));
        assertTrue(filter.contains("volume=1.2"));
        assertTrue(cmd.contains("-c:v"));
        assertEquals("copy", cmd.get(cmd.indexOf("-c:v") + 1));
        assertTrue(cmd.contains("-c:a"));
        assertEquals("aac", cmd.get(cmd.indexOf("-c:a") + 1));
        assertEquals("-progress", cmd.get(cmd.size() - 3));
        assertEquals("pipe:1", cmd.get(cmd.size() - 2));
        assertEquals(outMp4.getAbsolutePath(), cmd.get(cmd.size() - 1));
    }

    @Test
    public void dualCommandContainsAudioMappingsAndMetadata() {
        List<String> cmd = FfmpegCommandBuilder.buildDual(video, audio, null, "de", "mp4", outMp4);
        assertTrue(cmd.contains("-map"));
        assertTrue(cmd.contains("0:v"));
        assertTrue(cmd.contains("0:a"));
        assertTrue(cmd.contains("1:a"));
        assertTrue(cmd.contains("language=deu"));
        assertTrue(cmd.contains("language=rus"));
        assertTrue(cmd.contains("title=Оригинал"));
        assertTrue(cmd.contains("title=Перевод"));
    }

    @Test
    public void mapsExternalSubtitlesInBothModesAndContainers() throws Exception {
        File subtitles = File.createTempFile("captions.ru.", ".srt");
        try {
            for (String container : new String[]{"mp4", "mkv"}) {
                for (List<String> cmd : java.util.Arrays.asList(
                        FfmpegCommandBuilder.buildMix(video, audio, subtitles, "en", container, outMp4),
                        FfmpegCommandBuilder.buildDual(video, audio, subtitles, "en", container, outMkv))) {
                    int mapping = cmd.indexOf("2:s:0");
                    assertTrue(mapping > 0);
                    assertEquals("-map", cmd.get(mapping - 1));
                    assertEquals("mkv".equals(container) ? "srt" : "mov_text", cmd.get(cmd.indexOf("-c:s") + 1));
                    assertTrue(cmd.contains("language=rus"));
                }
            }
        } finally {
            subtitles.delete();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullInputs() {
        FfmpegCommandBuilder.buildMix(null, audio, null, "en", "mp4", outMp4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullOutput() {
        FfmpegCommandBuilder.buildDual(video, audio, null, "en", "mp4", null);
    }
}
