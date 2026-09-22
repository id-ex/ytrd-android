package io.github.idex.ytrdroid.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlUtilTest {
    private static final String ID = "abcdefghijk";

    @Test public void supportedForms() {
        for (String url : new String[]{
                "https://www.youtube.com/watch?v=" + ID,
                "http://m.youtube.com/watch?feature=share&v=" + ID + "&t=12",
                "youtube.com/watch?v=" + ID,
                "https://youtu.be/" + ID + "?si=tracking",
                "https://youtube.com/shorts/" + ID,
                "https://youtube.com/embed/" + ID,
                "https://youtube.com/live/" + ID,
                "HTTPS://WWW.YOUTUBE.COM/watch?v=" + ID}) {
            assertEquals(url, ID, UrlUtil.extractVideoId(url));
        }
    }

    @Test public void rejectsForeignHostsAndNestedUrls() {
        for (String url : new String[]{
                "https://notyoutube.com/watch?v=" + ID,
                "https://youtube.com.evil.org/watch?v=" + ID,
                "https://evil.org/youtube.com/watch?v=" + ID,
                "https://evil.org/?next=https://youtu.be/" + ID,
                "https://youtube.com@evil.org/watch?v=" + ID,
                "https://evil.org@youtube.com/watch?v=" + ID,
                "ftp://youtube.com/watch?v=" + ID,
                "https://youtube.com:444/watch?v=" + ID}) {
            assertNull(url, UrlUtil.extractVideoId(url));
        }
    }

    @Test public void requiresExactlyOneCompleteId() {
        for (String url : new String[]{
                "https://youtube.com/watch?v=" + ID + "MORE",
                "https://youtu.be/" + ID + "MORE",
                "https://youtube.com/shorts/" + ID + "/extra",
                "https://youtube.com/watch?v=short",
                "https://youtube.com/watch?notv=" + ID,
                "https://youtube.com/watch?v=" + ID + "&v=ABCDEFGHIJK",
                "https://youtube.com/watch?v=",
                "https://youtube.com/watch?v=abc%20defghi"}) {
            assertNull(url, UrlUtil.extractVideoId(url));
        }
    }

    @Test public void nestedQueryDoesNotReplaceOriginalIdentity() {
        assertEquals(ID, UrlUtil.extractVideoId("https://youtube.com/watch?v="
                + ID + "&next=https://youtu.be/ABCDEFGHIJK"));
    }

    @Test public void extractsCanonicalUrlFromSharedText() {
        assertEquals("https://www.youtube.com/watch?v=" + ID,
                UrlUtil.extractUrl("Посмотри (https://youtu.be/" + ID + "?t=5)."));
    }

    @Test public void missingOrMalformedInputIsNotAUrl() {
        for (String text : new String[]{null, "", "   ", "просто текст", "https://[invalid"}) {
            assertNull(UrlUtil.extractUrl(text));
            assertFalse(UrlUtil.isYouTubeUrl(text));
        }
    }
}
