package io.github.idex.ytrdroid.data.ytdlp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Client for fetching lightweight video preview information via YouTube's public oEmbed endpoint.
 */
public final class OEmbedClient {
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build();

    private OEmbedClient() {}

    /**
     * Preview metadata returned by YouTube oEmbed.
     */
    public static final class PreviewInfo {
        public final String videoId;
        public final String title;
        public final String author;
        public final String thumbnailUrl;

        public PreviewInfo(String videoId, String title, String author, String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.author = author;
            this.thumbnailUrl = thumbnailUrl;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PreviewInfo that = (PreviewInfo) o;
            return Objects.equals(videoId, that.videoId) &&
                    Objects.equals(title, that.title) &&
                    Objects.equals(author, that.author) &&
                    Objects.equals(thumbnailUrl, that.thumbnailUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(videoId, title, author, thumbnailUrl);
        }

        @Override
        public String toString() {
            return "PreviewInfo{" +
                    "videoId='" + videoId + '\'' +
                    ", title='" + title + '\'' +
                    ", author='" + author + '\'' +
                    ", thumbnailUrl='" + thumbnailUrl + '\'' +
                    '}';
        }
    }

    /**
     * Synchronously fetches preview info for the given YouTube videoId.
     * Never throws exceptions; returns null on network or parsing failure.
     */
    public static PreviewInfo fetch(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return null;
        }
        try {
            Request request = new Request.Builder()
                    .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId.trim() + "&format=json")
                    .get()
                    .build();

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                String json = response.body().string();
                return parseJson(json, videoId.trim());
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Parses an oEmbed JSON payload into a PreviewInfo.
     * Never throws; returns null on invalid or incomplete JSON.
     */
    public static PreviewInfo parseJson(String json, String videoId) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("title") || obj.get("title").isJsonNull()) {
                return null;
            }
            String title = obj.get("title").getAsString();
            String author = (obj.has("author_name") && !obj.get("author_name").isJsonNull())
                    ? obj.get("author_name").getAsString()
                    : null;
            String fallbackThumbnail = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
            String thumbnailUrl = (obj.has("thumbnail_url") && !obj.get("thumbnail_url").isJsonNull())
                    ? obj.get("thumbnail_url").getAsString()
                    : fallbackThumbnail;
            if (thumbnailUrl.trim().isEmpty()) {
                thumbnailUrl = fallbackThumbnail;
            }
            return new PreviewInfo(videoId, title, author, thumbnailUrl);
        } catch (Exception ignored) {
            return null;
        }
    }
}
