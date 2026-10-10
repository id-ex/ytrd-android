package io.github.idex.ytrdroid.data.ytdlp;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

public class RuntimeManagerTest {
    @Test
    public void uninitializedByDefault() {
        RuntimeManager rm = new RuntimeManager(null, Runnable::run);
        assertEquals(RuntimeManager.Status.UNINITIALIZED, rm.getStatus());
        assertFalse(rm.isReady());
        assertNull(rm.getVersion());
        assertNull(rm.getInitError());
        assertFalse(rm.getReadyFuture().isDone());
    }

    @Test
    public void updateFailsWhenNotReady() {
        RuntimeManager rm = new RuntimeManager(null, Runnable::run);
        CompletableFuture<String> future = rm.update(null);
        assertTrue(future.isCompletedExceptionally());
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void formatsCleanVersionCorrectly() {
        assertEquals("2026.09.27 (nightly)",
                RuntimeManager.formatCleanVersion("nightly@2026.09.27.232945 from yt-dlp/yt-dlp-nightly-builds [51bab8a01] (zip)"));
        assertEquals("2026.09.27",
                RuntimeManager.formatCleanVersion("2026.09.27"));
        assertEquals("2026.09.27",
                RuntimeManager.formatCleanVersion("2026.09.27 from yt-dlp/yt-dlp"));
        assertEquals("не определена",
                RuntimeManager.formatCleanVersion(null));
        assertEquals("не определена",
                RuntimeManager.formatCleanVersion("   "));
    }
}
