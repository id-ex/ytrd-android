package io.github.idex.ytrdroid.application;

import org.junit.Test;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CancellationTokenTest {
    @Test public void cancellationIsIdempotentAndCancelsLateHandles() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(calls::incrementAndGet);
        token.cancel();
        token.cancel();
        token.onCancel(calls::incrementAndGet);
        assertEquals(2, calls.get());
        assertTrue(token.isCancelled());
        assertThrows(CancellationException.class, token::throwIfCancelled);
    }
    @Test public void failedHookDoesNotSkipOtherResources() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(() -> { throw new IllegalStateException("fake resource error"); });
        token.onCancel(calls::incrementAndGet);
        token.cancel();
        assertEquals(1, calls.get());
    }
}
