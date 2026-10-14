package io.github.idex.ytrdroid.di;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.application.DownloadCoordinator;
import io.github.idex.ytrdroid.data.network.NetworkPolicy;
import io.github.idex.ytrdroid.data.persistence.AppDatabase;
import io.github.idex.ytrdroid.data.persistence.ArtifactRepository;
import io.github.idex.ytrdroid.data.persistence.LegacyHistoryMigrator;
import io.github.idex.ytrdroid.data.persistence.TaskRepository;
import io.github.idex.ytrdroid.data.settings.SettingsRepository;
import io.github.idex.ytrdroid.data.storage.WorkspaceManager;
import io.github.idex.ytrdroid.data.ytdlp.RuntimeManager;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import io.github.idex.ytrdroid.engine.LegacyDownloadPipeline;

/** Process scope: service recreation cannot create a second scheduler or worker. */
public final class AppContainer {
    public final DownloadCoordinator downloads;
    public final RuntimeManager runtime;
    public final AppDatabase database;
    public final ArtifactRepository artifacts;
    public final TaskRepository tasks;
    public final SettingsRepository settings;
    public final NetworkPolicy network;
    public final ExecutorService backgroundExecutor;
    private final List<Consumer<List<TaskSnapshot>>> observers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private List<TaskSnapshot> latest = Collections.emptyList();

    public AppContainer(Context context) {
        Handler main = new Handler(Looper.getMainLooper());
        ThreadPoolExecutor workers = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> new Thread(runnable, "download-worker"));
        backgroundExecutor = Executors.newFixedThreadPool(4, runnable -> new Thread(runnable, "ytrd-bg"));
        runtime = new RuntimeManager(context);
        settings = new SettingsRepository(context);
        network = new NetworkPolicy(context);

        database = AppDatabase.getInstance(context);
        artifacts = new ArtifactRepository(database.artifactDao());
        tasks = new TaskRepository(database.taskDao());

        downloads = new DownloadCoordinator(command -> main.post(command), workers,
                new LegacyDownloadPipeline(context, settings, network), snapshots -> {
                    latest = snapshots;
                    // Persist snapshots to database
                    for (TaskSnapshot s : snapshots) {
                        tasks.save(s, null);
                    }
                    for (Consumer<List<TaskSnapshot>> obs : observers) {
                        obs.accept(snapshots);
                    }
                }, taskId -> {
                    tasks.delete(taskId, null);
                    WorkspaceManager.cleanupTask(context.getFilesDir(), taskId);
                });

        // Startup recovery: mark any unfinished tasks from previous process as INTERRUPTED,
        // then restore them into DownloadCoordinator.
        tasks.markActiveAsInterrupted(() -> {
            tasks.getAllSnapshots(restored -> {
                if (restored != null && !restored.isEmpty()) {
                    downloads.restore(restored);
                }
            });
        });

        // One-time idempotent legacy history migration in background
        backgroundExecutor.execute(() -> {
            File legacyHistory = new File(context.getFilesDir(), "history.json");
            new LegacyHistoryMigrator().migrate(legacyHistory, database.artifactDao());
        });
    }

    /** Thread-safe observer registration; immediately notifies with latest snapshots. */
    public void observe(Consumer<List<TaskSnapshot>> observer) {
        if (observer == null) return;
        observers.add(observer);
        observer.accept(latest);
    }

    public void detach(Consumer<List<TaskSnapshot>> observer) {
        observers.remove(observer);
    }
}
