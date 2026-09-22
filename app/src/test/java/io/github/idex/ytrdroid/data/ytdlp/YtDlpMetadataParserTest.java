package io.github.idex.ytrdroid.data.ytdlp;

import org.junit.Test;

import java.util.List;

import io.github.idex.ytrdroid.model.VideoInfo;

import static org.junit.Assert.*;

public class YtDlpMetadataParserTest {
    @Test
    public void parsesValidMetadataWithMultipleFormats() {
        String json = "{\n" +
                "  \"title\": \"Тестовое видео — проверка Unicode 🚀\",\n" +
                "  \"uploader\": \"Канал автора\",\n" +
                "  \"thumbnail\": \"https://i.ytimg.com/vi/test/maxresdefault.jpg\",\n" +
                "  \"duration\": 305,\n" +
                "  \"language\": \"ru\",\n" +
                "  \"ext\": \"mp4\",\n" +
                "  \"formats\": [\n" +
                "    {\"format_id\": \"140\", \"vcodec\": \"none\", \"height\": null},\n" +
                "    {\"format_id\": \"137\", \"vcodec\": \"avc1.640028\", \"height\": 1080},\n" +
                "    {\"format_id\": \"136\", \"vcodec\": \"avc1.4d401f\", \"height\": 720},\n" +
                "    {\"format_id\": \"134\", \"vcodec\": \"avc1.4d401e\", \"height\": 360},\n" +
                "    {\"format_id\": \"160\", \"vcodec\": \"avc1.42c00b\", \"height\": 144}\n" +
                "  ]\n" +
                "}";

        VideoInfo info = YtDlpMetadataParser.parse(json, "https://youtu.be/test");
        assertEquals("Тестовое видео — проверка Unicode 🚀", info.title);
        assertEquals("Канал автора", info.uploader);
        assertEquals(305, info.duration);
        assertEquals("ru", info.language);
        assertEquals("mp4", info.ext);
        assertEquals(List.of("1080", "720", "360", "144"), info.qualities);
    }

    @Test
    public void filtersOutAudioOnlyHeights() {
        String json = "{\n" +
                "  \"title\": \"Audio-only\",\n" +
                "  \"formats\": [\n" +
                "    {\"format_id\": \"audio1\", \"vcodec\": \"none\", \"height\": 720},\n" +
                "    {\"format_id\": \"vid1\", \"vcodec\": \"vp9\", \"height\": 480}\n" +
                "  ]\n" +
                "}";

        VideoInfo info = YtDlpMetadataParser.parse(json, "https://youtu.be/audio");
        assertEquals(List.of("480"), info.qualities);
    }

    @Test
    public void returnsAutoWhenNoValidFormats() {
        String json = "{\"title\": \"No formats\", \"formats\": []}";
        VideoInfo info = YtDlpMetadataParser.parse(json, "https://youtu.be/none");
        assertEquals(List.of("auto"), info.qualities);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyJson() {
        YtDlpMetadataParser.parse("", "https://youtu.be/empty");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedJson() {
        YtDlpMetadataParser.parse("{invalid json", "https://youtu.be/bad");
    }

    @Test
    public void handlesNullFieldsGracefully() {
        String json = "{\"title\": null, \"duration\": null, \"formats\": null}";
        VideoInfo info = YtDlpMetadataParser.parse(json, "https://youtu.be/nulls");
        assertNull(info.title);
        assertEquals(0, info.duration);
        assertEquals("mp4", info.ext);
        assertEquals(List.of("auto"), info.qualities);
    }
}
