package io.github.idex.ytrdroid.data.ytdlp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.github.idex.ytrdroid.model.VideoInfo;

/**
 * Parses yt-dlp `--dump-json` output strictly using Gson.
 * Pure Java, no Android dependencies, fully unit-testable.
 */
public final class YtDlpMetadataParser {
    private YtDlpMetadataParser() {}

    public static VideoInfo parse(String json, String originalUrl) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty yt-dlp metadata output");
        }
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Malformed yt-dlp JSON: " + e.getMessage(), e);
        }

        VideoInfo info = new VideoInfo();
        info.url = originalUrl;
        info.title = getString(obj, "title");
        info.uploader = getString(obj, "uploader");
        info.thumbnail = getString(obj, "thumbnail");
        info.language = getString(obj, "language");
        info.ext = getString(obj, "ext");
        if (info.ext == null || info.ext.isEmpty()) info.ext = "mp4";

        if (obj.has("duration") && !obj.get("duration").isJsonNull()) {
            try {
                info.duration = obj.get("duration").getAsLong();
            } catch (Exception ignored) {
                info.duration = 0;
            }
        }

        // Parse formats for video heights (>= 144)
        Set<Integer> heights = new TreeSet<>(Collections.reverseOrder());
        if (obj.has("formats") && obj.get("formats").isJsonArray()) {
            JsonArray formats = obj.getAsJsonArray("formats");
            for (JsonElement elem : formats) {
                if (!elem.isJsonObject()) continue;
                JsonObject f = elem.getAsJsonObject();
                // Filter out audio-only formats where vcodec is "none"
                if (f.has("vcodec") && !f.get("vcodec").isJsonNull()) {
                    String vcodec = f.get("vcodec").getAsString();
                    if ("none".equalsIgnoreCase(vcodec)) continue;
                }
                if (f.has("height") && !f.get("height").isJsonNull()) {
                    try {
                        int h = f.get("height").getAsInt();
                        if (h >= 144) heights.add(h);
                    } catch (Exception ignored) {}
                }
            }
        }

        info.qualities = new ArrayList<>();
        for (int h : heights) {
            info.qualities.add(String.valueOf(h));
        }
        if (info.qualities.isEmpty()) {
            info.qualities.add("auto");
        }

        return info;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
