package io.github.idex.ytrdroid.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Per-execution cancellation; late handle registration is cancelled immediately. */
public final class CancellationToken {
    private boolean cancelled;
    private final List<Runnable> actions = new ArrayList<>();

    public synchronized boolean isCancelled() { return cancelled; }

    public void throwIfCancelled() {
        if (isCancelled()) throw new CancellationException("Execution cancelled");
    }

    public void onCancel(Runnable action) {
        synchronized (this) {
            if (!cancelled) {
                actions.add(action);
                return;
            }
        }
        runSafely(action);
    }

    public void cancel() {
        List<Runnable> pending;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            pending = new ArrayList<>(actions);
            actions.clear();
        }
        for (Runnable action : pending) runSafely(action);
    }

    private static void runSafely(Runnable action) {
        try { action.run(); } catch (RuntimeException ignored) {
            // One failed resource cancellation must not prevent the remaining hooks.
        }
    }
}
