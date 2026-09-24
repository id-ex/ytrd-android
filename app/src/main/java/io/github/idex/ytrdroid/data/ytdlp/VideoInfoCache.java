package io.github.idex.ytrdroid.data.ytdlp;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.idex.ytrdroid.model.VideoInfo;

/**
 * Thread-safe LRU cache for {@link VideoInfo} metadata entries, holding up to 30 elements.
 * Built on {@link LinkedHashMap} for pure-JVM testing without Android dependencies or mocks.
 */
public final class VideoInfoCache {
    private static final int MAX_ENTRIES = 30;

    private static final Map<String, VideoInfo> CACHE = new LinkedHashMap<String, VideoInfo>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, VideoInfo> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private VideoInfoCache() {}

    /**
     * Retrieves the cached {@link VideoInfo} for the given key, updating access order.
     */
    public static synchronized VideoInfo get(String key) {
        if (key == null) {
            return null;
        }
        return CACHE.get(key);
    }

    /**
     * Stores a {@link VideoInfo} entry in the cache.
     * If the cache exceeds capacity, the least recently accessed item is evicted.
     */
    public static synchronized void put(String key, VideoInfo info) {
        if (key == null || info == null) {
            return;
        }
        CACHE.put(key, info);
    }

    /**
     * Clears all entries from the cache.
     */
    public static synchronized void clear() {
        CACHE.clear();
    }

    /**
     * Returns current number of entries in the cache (primarily for verification and testing).
     */
    public static synchronized int size() {
        return CACHE.size();
    }
}
