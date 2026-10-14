package io.github.idex.ytrdroid.data.updater;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.idex.ytrdroid.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class AppUpdateChecker {

    public static final String GITHUB_REPO = "id-ex/ytrd-android";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases?per_page=1";

    public static class UpdateInfo {
        public final String currentVersion;
        public final String newVersion;
        public final String releaseTitle;
        public final String changelog;
        public final String downloadUrl;
        public final String apkName;
        public final long apkSize;
        public final String releaseUrl;

        public UpdateInfo(String currentVersion, String newVersion, String releaseTitle,
                          String changelog, String downloadUrl, String apkName, long apkSize) {
            this(currentVersion, newVersion, releaseTitle, changelog, downloadUrl, apkName, apkSize, null);
        }

        public UpdateInfo(String currentVersion, String newVersion, String releaseTitle,
                          String changelog, String downloadUrl, String apkName, long apkSize,
                          String releaseUrl) {
            this.currentVersion = currentVersion;
            this.newVersion = newVersion;
            this.releaseTitle = releaseTitle;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.apkName = apkName;
            this.apkSize = apkSize;
            this.releaseUrl = releaseUrl;
        }
    }

    public interface Callback {
        void onUpdateAvailable(UpdateInfo info);
        void onNoUpdateAvailable();
        void onError(Throwable error);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private AppUpdateChecker() {}

    public static void check(Callback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(API_URL)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "ytrd-android")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        dispatchError(callback, new IOException("HTTP error: " + response.code()));
                        return;
                    }

                    String json = response.body().string();
                    UpdateInfo info = parseReleaseJson(json, BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS);
                    if (info != null) {
                        mainHandler.post(() -> callback.onUpdateAvailable(info));
                    } else {
                        mainHandler.post(callback::onNoUpdateAvailable);
                    }
                }
            } catch (Exception e) {
                dispatchError(callback, e);
            }
        });
    }

    private static void dispatchError(Callback callback, Throwable e) {
        mainHandler.post(() -> callback.onError(e));
    }

    /**
     * Parses the GitHub release JSON and returns UpdateInfo if the release is newer,
     * or null if no update is available.
     */
    public static UpdateInfo parseReleaseJson(String json, String currentVersionName, String[] supportedAbis) {
        if (json == null || json.trim().isEmpty()) return null;
        JsonElement rootElement = JsonParser.parseString(json);
        JsonObject root;
        if (rootElement.isJsonArray()) {
            JsonArray array = rootElement.getAsJsonArray();
            if (array.isEmpty()) return null;
            root = array.get(0).getAsJsonObject();
        } else if (rootElement.isJsonObject()) {
            root = rootElement.getAsJsonObject();
        } else {
            return null;
        }

        String tagName = getString(root, "tag_name");
        if (tagName == null || tagName.isEmpty()) return null;

        String releaseTitle = getString(root, "name");
        String changelog = getString(root, "body");

        if (!isNewer(tagName, currentVersionName)) {
            return null;
        }

        // Find best matching APK asset
        JsonArray assets = root.has("assets") && root.get("assets").isJsonArray()
                ? root.getAsJsonArray("assets") : null;

        BestAsset bestAsset = selectBestApkAsset(assets, supportedAbis);
        if (bestAsset == null) {
            return null;
        }

        String normalizedNewVersion = tagName.startsWith("v") || tagName.startsWith("V")
                ? tagName.substring(1) : tagName;
        String htmlUrl = getString(root, "html_url");

        return new UpdateInfo(
                currentVersionName,
                normalizedNewVersion,
                releaseTitle != null && !releaseTitle.isEmpty() ? releaseTitle : "Версия " + normalizedNewVersion,
                changelog != null ? changelog.trim() : "",
                bestAsset.downloadUrl,
                bestAsset.name,
                bestAsset.size,
                htmlUrl
        );
    }

    public static class BestAsset {
        public final String name;
        public final String downloadUrl;
        public final long size;

        public BestAsset(String name, String downloadUrl, long size) {
            this.name = name;
            this.downloadUrl = downloadUrl;
            this.size = size;
        }
    }

    public static BestAsset selectBestApkAsset(JsonArray assets, String[] supportedAbis) {
        if (assets == null || assets.isEmpty()) return null;

        BestAsset primaryAbiMatch = null;
        BestAsset anyAbiMatch = null;
        BestAsset universalMatch = null;
        BestAsset anyApkMatch = null;

        String primaryAbi = (supportedAbis != null && supportedAbis.length > 0) ? supportedAbis[0] : "";

        for (JsonElement element : assets) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String name = getString(obj, "name");
            String downloadUrl = getString(obj, "browser_download_url");
            long size = obj.has("size") && !obj.get("size").isJsonNull() ? obj.get("size").getAsLong() : 0;

            if (name == null || downloadUrl == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                continue;
            }

            BestAsset current = new BestAsset(name, downloadUrl, size);
            String lowerName = name.toLowerCase(java.util.Locale.ROOT);

            if (!primaryAbi.isEmpty() && lowerName.contains(primaryAbi.toLowerCase(java.util.Locale.ROOT))) {
                primaryAbiMatch = current;
                break; // Highest priority match
            }

            if (supportedAbis != null) {
                for (String abi : supportedAbis) {
                    if (lowerName.contains(abi.toLowerCase(java.util.Locale.ROOT))) {
                        anyAbiMatch = current;
                        break;
                    }
                }
            }

            if (lowerName.contains("universal")) {
                universalMatch = current;
            }

            if (anyApkMatch == null) {
                anyApkMatch = current;
            }
        }

        if (primaryAbiMatch != null) return primaryAbiMatch;
        if (anyAbiMatch != null) return anyAbiMatch;
        if (universalMatch != null) return universalMatch;
        return anyApkMatch;
    }

    /**
     * Compares remote version (e.g. "v0.4.1" or "0.4.1") with current version (e.g. "0.4.0" or "0.4.0-alpha").
     * Returns true if remote is newer.
     */
    public static boolean isNewer(String remoteTag, String currentVersion) {
        if (remoteTag == null || currentVersion == null) return false;

        String rClean = remoteTag.trim();
        if (rClean.startsWith("v") || rClean.startsWith("V")) {
            rClean = rClean.substring(1);
        }

        String cClean = currentVersion.trim();
        if (cClean.startsWith("v") || cClean.startsWith("V")) {
            cClean = cClean.substring(1);
        }

        String[] rParts = rClean.split("-")[0].split("\\.");
        String[] cParts = cClean.split("-")[0].split("\\.");

        int rMajor = rParts.length > 0 ? parseSafe(rParts[0]) : 0;
        int rMinor = rParts.length > 1 ? parseSafe(rParts[1]) : 0;
        int rPatch = rParts.length > 2 ? parseSafe(rParts[2]) : 0;

        int cMajor = cParts.length > 0 ? parseSafe(cParts[0]) : 0;
        int cMinor = cParts.length > 1 ? parseSafe(cParts[1]) : 0;
        int cPatch = cParts.length > 2 ? parseSafe(cParts[2]) : 0;

        if (rMajor != cMajor) return rMajor > cMajor;
        if (rMinor != cMinor) return rMinor > cMinor;
        if (rPatch != cPatch) return rPatch > cPatch;

        // If numeric SemVer is identical: release is newer than pre-release (-alpha)
        boolean cHasPrerelease = cClean.contains("-");
        boolean rHasPrerelease = rClean.contains("-");
        if (cHasPrerelease && !rHasPrerelease) {
            return true;
        }

        return false;
    }

    private static int parseSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
