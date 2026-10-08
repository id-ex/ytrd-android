package io.github.idex.ytrdroid.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CookieNetscapeWriterTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void formatsCookieStringsIntoNetscapeFormat() {
        String yt = "VISITOR_INFO1_LIVE=abc123xyz; YSC=def456";
        String google = "SID=google_sid; HSID=google_hsid";

        String result = CookieNetscapeWriter.formatToNetscape(yt, null, google);

        assertNotNull(result);
        assertTrue(result.startsWith("# Netscape HTTP Cookie File"));
        assertTrue(result.contains(".youtube.com\tTRUE\t/\tTRUE\t"));
        assertTrue(result.contains("VISITOR_INFO1_LIVE\tabc123xyz"));
        assertTrue(result.contains("YSC\tdef456"));
        assertTrue(result.contains(".google.com\tTRUE\t/\tTRUE\t"));
        assertTrue(result.contains("SID\tgoogle_sid"));
    }

    @Test
    public void returnsNullWhenCookiesAreEmpty() {
        assertNull(CookieNetscapeWriter.formatToNetscape(null, "", null));
    }

    @Test
    public void detectsLoginCookiesInFile() throws IOException {
        File file = temp.newFile("cookies.txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("# Netscape\n.youtube.com\tTRUE\t/\tTRUE\t2000000000\tLOGIN_INFO\ttoken123\n");
        }
        assertTrue(CookieNetscapeWriter.hasLoginCookies(file));
    }

    @Test
    public void returnsFalseWhenNoLoginCookiesInFile() throws IOException {
        File file = temp.newFile("guest_cookies.txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("# Netscape\n.youtube.com\tTRUE\t/\tTRUE\t2000000000\tPREF\tf1=50000000\n");
        }
        assertFalse(CookieNetscapeWriter.hasLoginCookies(file));
        assertFalse(CookieNetscapeWriter.hasLoginCookies(null));
    }
}
