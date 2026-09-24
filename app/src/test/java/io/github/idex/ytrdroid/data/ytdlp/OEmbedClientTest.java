package io.github.idex.ytrdroid.data.ytdlp;

import org.junit.Test;

import static org.junit.Assert.*;

public class OEmbedClientTest {

    @Test
    public void parsesCompleteOEmbedResponse() {
        String json = "{\n" +
                "  \"title\": \"Sample Video Title\",\n" +
                "  \"author_name\": \"Sample Author\",\n" +
                "  \"author_url\": \"https://www.youtube.com/@SampleAuthor\",\n" +
                "  \"type\": \"video\",\n" +
                "  \"height\": 113,\n" +
                "  \"width\": 200,\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"provider_name\": \"YouTube\",\n" +
                "  \"thumbnail_url\": \"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg\"\n" +
                "}";

        OEmbedClient.PreviewInfo info = OEmbedClient.parseJson(json, "dQw4w9WgXcQ");

        assertNotNull(info);
        assertEquals("dQw4w9WgXcQ", info.videoId);
        assertEquals("Sample Video Title", info.title);
        assertEquals("Sample Author", info.author);
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", info.thumbnailUrl);
    }

    @Test
    public void parsesOEmbedResponseWithoutAuthor() {
        String json = "{\n" +
                "  \"title\": \"No Author Video\",\n" +
                "  \"thumbnail_url\": \"https://custom.thumb/pic.jpg\"\n" +
                "}";

        OEmbedClient.PreviewInfo info = OEmbedClient.parseJson(json, "xyz123");

        assertNotNull(info);
        assertEquals("xyz123", info.videoId);
        assertEquals("No Author Video", info.title);
        assertNull(info.author);
        assertEquals("https://custom.thumb/pic.jpg", info.thumbnailUrl);
    }

    @Test
    public void fallsBackToDefaultThumbnailWhenMissing() {
        String json = "{\n" +
                "  \"title\": \"Missing Thumbnail Video\",\n" +
                "  \"author_name\": \"Channel One\"\n" +
                "}";

        OEmbedClient.PreviewInfo info = OEmbedClient.parseJson(json, "abc789");

        assertNotNull(info);
        assertEquals("abc789", info.videoId);
        assertEquals("Missing Thumbnail Video", info.title);
        assertEquals("Channel One", info.author);
        assertEquals("https://i.ytimg.com/vi/abc789/hqdefault.jpg", info.thumbnailUrl);
    }

    @Test
    public void fallsBackToDefaultThumbnailWhenEmptyOrNull() {
        String jsonNull = "{\"title\": \"Null Thumb\", \"thumbnail_url\": null}";
        OEmbedClient.PreviewInfo infoNull = OEmbedClient.parseJson(jsonNull, "vid1");
        assertNotNull(infoNull);
        assertEquals("https://i.ytimg.com/vi/vid1/hqdefault.jpg", infoNull.thumbnailUrl);

        String jsonEmpty = "{\"title\": \"Empty Thumb\", \"thumbnail_url\": \"   \"}";
        OEmbedClient.PreviewInfo infoEmpty = OEmbedClient.parseJson(jsonEmpty, "vid2");
        assertNotNull(infoEmpty);
        assertEquals("https://i.ytimg.com/vi/vid2/hqdefault.jpg", infoEmpty.thumbnailUrl);
    }

    @Test
    public void returnsNullOnInvalidOrIncompleteJson() {
        assertNull(OEmbedClient.parseJson(null, "vid"));
        assertNull(OEmbedClient.parseJson("", "vid"));
        assertNull(OEmbedClient.parseJson("   ", "vid"));
        assertNull(OEmbedClient.parseJson("not a json", "vid"));
        assertNull(OEmbedClient.parseJson("{}", "vid")); // No title
        assertNull(OEmbedClient.parseJson("{\"author_name\": \"Author\"}", "vid")); // No title
        assertNull(OEmbedClient.parseJson("[]", "vid")); // Array instead of object
    }

    @Test
    public void fetchReturnsNullOnInvalidVideoIdWithoutCrashing() {
        assertNull(OEmbedClient.fetch(null));
        assertNull(OEmbedClient.fetch(""));
        assertNull(OEmbedClient.fetch("   "));
    }
}
