package io.github.idex.ytrdroid.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlUtil {
    private static final Pattern YT_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?.*v=|shorts/|embed/|live/)|youtu\\.be/)([a-zA-Z0-9_-]{11})");

    private UrlUtil() {}

    public static String extractVideoId(String text) {
        if (text == null) return null;
        Matcher m = YT_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    public static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = YT_PATTERN.matcher(text);
        return m.find() ? m.group(0) : null;
    }

    public static boolean isYouTubeUrl(String text) {
        return extractVideoId(text) != null;
    }

    public static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
