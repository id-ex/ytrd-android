package io.github.idex.ytrdroid.data.ytdlp;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.idex.ytrdroid.model.VideoInfo;

import static org.junit.Assert.*;

public class VideoInfoCacheTest {

    @Before
    public void setUp() {
        VideoInfoCache.clear();
    }

    private VideoInfo createVideoInfo(String url, String title) {
        VideoInfo info = new VideoInfo();
        info.url = url;
        info.title = title;
        return info;
    }

    @Test
    public void putsAndGetsVideoInfo() {
        VideoInfo info = createVideoInfo("https://youtu.be/123", "Title 123");
        VideoInfoCache.put("123", info);

        VideoInfo retrieved = VideoInfoCache.get("123");
        assertNotNull(retrieved);
        assertEquals("Title 123", retrieved.title);
        assertEquals("https://youtu.be/123", retrieved.url);
    }

    @Test
    public void handlesNullKeysAndValuesSafely() {
        assertNull(VideoInfoCache.get(null));
        VideoInfoCache.put(null, createVideoInfo("url", "title"));
        VideoInfoCache.put("key", null);
        assertNull(VideoInfoCache.get("key"));
        assertEquals(0, VideoInfoCache.size());
    }

    @Test
    public void clearRemovesAllEntries() {
        VideoInfoCache.put("k1", createVideoInfo("u1", "t1"));
        VideoInfoCache.put("k2", createVideoInfo("u2", "t2"));
        assertEquals(2, VideoInfoCache.size());

        VideoInfoCache.clear();
        assertEquals(0, VideoInfoCache.size());
        assertNull(VideoInfoCache.get("k1"));
        assertNull(VideoInfoCache.get("k2"));
    }

    @Test
    public void maintainsLruOrderAndEvictsLeastRecentlyUsedAtLimit30() {
        // Fill cache with 30 items: k0 .. k29
        for (int i = 0; i < 30; i++) {
            VideoInfoCache.put("k" + i, createVideoInfo("u" + i, "title " + i));
        }
        assertEquals(30, VideoInfoCache.size());

        // Access k0 so that k0 becomes most recently used
        assertNotNull(VideoInfoCache.get("k0"));

        // Put 31st item: k30. The eldest item was k1 (since k0 was accessed).
        VideoInfoCache.put("k30", createVideoInfo("u30", "title 30"));

        assertEquals(30, VideoInfoCache.size());
        assertNotNull(VideoInfoCache.get("k0")); // Still present because accessed
        assertNull(VideoInfoCache.get("k1"));    // Evicted as LRU
        assertNotNull(VideoInfoCache.get("k2")); // Still present
        assertNotNull(VideoInfoCache.get("k30"));// New item present
    }

    @Test
    public void isThreadSafeUnderConcurrentAccess() throws InterruptedException {
        int threads = 8;
        int operationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "thread-" + (threadId % 4) + "-item-" + (i % 20);
                        VideoInfo info = createVideoInfo("https://youtu.be/" + key, "Title " + key);
                        VideoInfoCache.put(key, info);
                        VideoInfo cached = VideoInfoCache.get(key);
                        if (cached != null && !key.equals(cached.url.replace("https://youtu.be/", ""))) {
                            failed.set(true);
                        }
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertFalse(failed.get());
        assertTrue(VideoInfoCache.size() <= 30);
    }
}
