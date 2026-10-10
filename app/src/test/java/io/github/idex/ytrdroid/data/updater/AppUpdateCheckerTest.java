package io.github.idex.ytrdroid.data.updater;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppUpdateCheckerTest {

    @Test
    public void semVerComparisonDetectsNewerVersions() {
        assertTrue(AppUpdateChecker.isNewer("v0.4.1", "0.4.0"));
        assertTrue(AppUpdateChecker.isNewer("0.4.1", "0.4.0"));
        assertTrue(AppUpdateChecker.isNewer("v0.5.0", "0.4.9"));
        assertTrue(AppUpdateChecker.isNewer("v1.0.0", "0.4.0"));
        assertTrue(AppUpdateChecker.isNewer("v0.4.0", "0.4.0-alpha"));
        assertTrue(AppUpdateChecker.isNewer("0.4.0", "0.4.0-alpha"));

        assertFalse(AppUpdateChecker.isNewer("v0.4.0", "0.4.0"));
        assertFalse(AppUpdateChecker.isNewer("0.4.0", "0.4.0"));
        assertFalse(AppUpdateChecker.isNewer("v0.3.9", "0.4.0"));
        assertFalse(AppUpdateChecker.isNewer("v0.3.0", "0.4.0"));
        assertFalse(AppUpdateChecker.isNewer("v0.4.0-alpha", "0.4.0"));
        assertFalse(AppUpdateChecker.isNewer(null, "0.4.0"));
        assertFalse(AppUpdateChecker.isNewer("v0.4.1", null));
    }

    @Test
    public void parseReleaseJsonPicksBestAssetMatchingAbi() {
        String json = "{\n" +
                "  \"tag_name\": \"v0.4.1\",\n" +
                "  \"name\": \"Релиз v0.4.1\",\n" +
                "  \"body\": \"- Исправление ошибок\\n- Улучшение скорости\",\n" +
                "  \"assets\": [\n" +
                "    {\n" +
                "      \"name\": \"app-universal-release.apk\",\n" +
                "      \"browser_download_url\": \"https://github.com/id-ex/ytrd-android/releases/download/v0.4.1/app-universal-release.apk\",\n" +
                "      \"size\": 104857600\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"app-arm64-v8a-release.apk\",\n" +
                "      \"browser_download_url\": \"https://github.com/id-ex/ytrd-android/releases/download/v0.4.1/app-arm64-v8a-release.apk\",\n" +
                "      \"size\": 61865984\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"app-armeabi-v7a-release.apk\",\n" +
                "      \"browser_download_url\": \"https://github.com/id-ex/ytrd-android/releases/download/v0.4.1/app-armeabi-v7a-release.apk\",\n" +
                "      \"size\": 55574528\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // When arm64-v8a is supported
        AppUpdateChecker.UpdateInfo arm64Info = AppUpdateChecker.parseReleaseJson(
                json, "0.4.0", new String[]{"arm64-v8a", "armeabi-v7a"});

        assertNotNull(arm64Info);
        assertEquals("0.4.0", arm64Info.currentVersion);
        assertEquals("0.4.1", arm64Info.newVersion);
        assertEquals("Релиз v0.4.1", arm64Info.releaseTitle);
        assertEquals("- Исправление ошибок\n- Улучшение скорости", arm64Info.changelog);
        assertEquals("app-arm64-v8a-release.apk", arm64Info.apkName);
        assertEquals(61865984L, arm64Info.apkSize);

        // Works equally well when GitHub wraps response in array (releases?per_page=1)
        String jsonArray = "[" + json + "]";
        AppUpdateChecker.UpdateInfo arrayInfo = AppUpdateChecker.parseReleaseJson(
                jsonArray, "0.4.0", new String[]{"arm64-v8a"});
        assertNotNull(arrayInfo);
        assertEquals("0.4.1", arrayInfo.newVersion);

        // When armeabi-v7a is supported
        AppUpdateChecker.UpdateInfo armV7Info = AppUpdateChecker.parseReleaseJson(
                json, "0.4.0", new String[]{"armeabi-v7a"});

        assertNotNull(armV7Info);
        assertEquals("app-armeabi-v7a-release.apk", armV7Info.apkName);

        // When unknown ABI is supported, falls back to universal
        AppUpdateChecker.UpdateInfo universalInfo = AppUpdateChecker.parseReleaseJson(
                json, "0.4.0", new String[]{"x86_64"});

        assertNotNull(universalInfo);
        assertEquals("app-universal-release.apk", universalInfo.apkName);
    }

    @Test
    public void returnsNullWhenReleaseIsNotNewer() {
        String json = "{\n" +
                "  \"tag_name\": \"v0.4.0\",\n" +
                "  \"name\": \"Релиз v0.4.0\",\n" +
                "  \"body\": \"Changelog\",\n" +
                "  \"assets\": [\n" +
                "    {\n" +
                "      \"name\": \"app-arm64-v8a-release.apk\",\n" +
                "      \"browser_download_url\": \"https://example.com/app.apk\",\n" +
                "      \"size\": 50000000\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        AppUpdateChecker.UpdateInfo info = AppUpdateChecker.parseReleaseJson(
                json, "0.4.0", new String[]{"arm64-v8a"});
        assertNull(info);
    }
}
