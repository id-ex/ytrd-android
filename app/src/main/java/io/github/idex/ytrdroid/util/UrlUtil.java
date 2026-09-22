package io.github.idex.ytrdroid.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UrlUtil {
    private static final Pattern VIDEO_ID = Pattern.compile("[a-zA-Z0-9_-]{11}");

    private UrlUtil() {}

    public static String extractVideoId(String text) {
        if (text == null) return null;
        for (String token : text.split("\\s+")) {
            // Shared text may surround a URL with punctuation. Never search inside
            // another URL: its host, user info or query may contain a YouTube URL.
            String candidate = token.replaceAll("^[\\(\\[<\"']+|[\\)\\]>\"'.,!;]+$", "");
            String id = parseVideoId(candidate);
            if (id != null) return id;
        }
        return null;
    }

    private static String parseVideoId(String candidate) {
        try {
            URI uri = new URI(candidate.contains("://") ? candidate : "https://" + candidate);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            String id = null;
            if (host.equals("youtu.be")) {
                if (path != null && path.startsWith("/")) id = path.substring(1);
            } else if (host.equals("youtube.com") || host.equals("www.youtube.com")
                    || host.equals("m.youtube.com")) {
                if ("/watch".equals(path)) {
                    String query = uri.getRawQuery();
                    if (query == null) return null;
                    for (String parameter : query.split("&")) {
                        if (parameter.startsWith("v=")) {
                            if (id != null) return null; // Ambiguous duplicate identity.
                            id = parameter.substring(2);
                        }
                    }
                } else if (path != null) {
                    for (String prefix : new String[]{"/shorts/", "/embed/", "/live/"}) {
                        if (path.startsWith(prefix)) id = path.substring(prefix.length());
                    }
                }
            }
            return id != null && VIDEO_ID.matcher(id).matches() ? id : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Return a canonical URL without tracking or unrelated query parameters. */
    public static String extractUrl(String text) {
        String id = extractVideoId(text);
        return id == null ? null : "https://www.youtube.com/watch?v=" + id;
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
