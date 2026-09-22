package io.github.idex.ytrdroid.data.ytdlp;

import android.content.Context;
import android.util.Log;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages yt-dlp and FFmpeg native runtime readiness and explicit updates.
 * Prevents concurrent updates during execution and eliminates unconditional
 * nightly updates on startup or random errors.
 */
public final class RuntimeManager {
    private static final String TAG = "RuntimeManager";

    public enum Status { UNINITIALIZED, INITIALIZING, READY, FAILED }

    private final Context context;
    private final Executor executor;
    private volatile Status status = Status.UNINITIALIZED;
    private volatile String version;
    private volatile String initError;
    private final CompletableFuture<Boolean> readyFuture = new CompletableFuture<>();
    private final AtomicBoolean updating = new AtomicBoolean(false);

    public RuntimeManager(Context context) {
        this(context, Executors.newSingleThreadExecutor(r -> new Thread(r, "runtime-init")));
    }

    public RuntimeManager(Context context, Executor executor) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.executor = executor;
    }

    public Status getStatus() { return status; }
    public boolean isReady() { return status == Status.READY; }
    public String getVersion() { return version; }
    public String getInitError() { return initError; }
    public CompletableFuture<Boolean> getReadyFuture() { return readyFuture; }

    /** Initialize runtime once. Safe to call multiple times. */
    public void init() {
        if (status != Status.UNINITIALIZED) return;
        status = Status.INITIALIZING;
        executor.execute(() -> {
            try {
                YoutubeDL.getInstance().init(context);
                FFmpeg.getInstance().init(context);
                version = YoutubeDL.getInstance().version(context);
                status = Status.READY;
                Log.i(TAG, "Runtime initialized successfully. yt-dlp version: " + version);
                readyFuture.complete(true);
            } catch (YoutubeDLException | RuntimeException e) {
                initError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                status = Status.FAILED;
                Log.e(TAG, "Failed to initialize runtime: " + initError, e);
                readyFuture.complete(false);
            }
        });
    }

    /**
     * Explicit update. Single-flight, returns future with result status.
     * Fails if runtime is not ready or another update is already in progress.
     */
    public CompletableFuture<String> update(YoutubeDL.UpdateChannel channel) {
        CompletableFuture<String> result = new CompletableFuture<>();
        if (!isReady()) {
            result.completeExceptionally(new IllegalStateException("Runtime is not initialized"));
            return result;
        }
        if (!updating.compareAndSet(false, true)) {
            result.completeExceptionally(new IllegalStateException("Update already in progress"));
            return result;
        }
        executor.execute(() -> {
            try {
                Log.i(TAG, "Updating yt-dlp via channel: " + channel);
                YoutubeDL.UpdateStatus updateStatus = YoutubeDL.getInstance().updateYoutubeDL(context, channel);
                version = YoutubeDL.getInstance().version(context);
                String msg = "Update status: " + updateStatus + ", version: " + version;
                Log.i(TAG, msg);
                result.complete(msg);
            } catch (Exception e) {
                Log.w(TAG, "yt-dlp update failed: " + e.getMessage());
                result.completeExceptionally(e);
            } finally {
                updating.set(false);
            }
        });
        return result;
    }
}
