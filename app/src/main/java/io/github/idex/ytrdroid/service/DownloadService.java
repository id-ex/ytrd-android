package io.github.idex.ytrdroid.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.application.DownloadCoordinator;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import io.github.idex.ytrdroid.di.AppContainer;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.LegacyTaskMapper;
import io.github.idex.ytrdroid.ui.MainActivity;

/** Android lifecycle/notification adapter. Queue state belongs to the coordinator. */
public class DownloadService extends Service {
    public static final String ACTION_CANCEL = "io.github.idex.ytrdroid.CANCEL";
    public static final String ACTION_PAUSE = "io.github.idex.ytrdroid.PAUSE";
    public static final String ACTION_RESUME = "io.github.idex.ytrdroid.RESUME";
    private static final String EXTRA_TASK = "taskId";
    private static final String EXTRA_EXECUTION = "executionId";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private Handler main;
    private AppContainer container;
    private DownloadCoordinator coordinator;
    private final Consumer<List<TaskSnapshot>> observer = this::onSnapshots;
    private boolean executionPathStarted;
    private boolean awaitingQueuePublication;
    private long startGeneration;
    private UUID stoppingExecution;
    private boolean listenerPending;
    private List<TaskSnapshot> snapshots = Collections.emptyList();
    private Listener listener;
    private boolean destroyed;
    private boolean foreground;
    private long lastNotificationTime;
    private TaskSnapshot.State lastNotificationState;
    private UUID lastExecution;

    public interface Listener { void onTasksChanged(); }
    public class LocalBinder extends Binder {
        public DownloadService getService() { return DownloadService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        main = new Handler(getMainLooper());
        // A bindService must not create a foreground notification. A foreground-start
        // command promotes synchronously in onStartCommand before observer registration can delay it.
        container = ((App) getApplication()).container();
        coordinator = container.downloads;
        container.observe(observer);
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Satisfy the foreground-start contract before handling any command.
        executionPathStarted = true;
        awaitingQueuePublication = true;
        final long generation = ++startGeneration;
        enterForeground();
        coordinator.afterPendingCommands(() -> main.post(() -> {
            if (destroyed || generation != startGeneration) return;
            awaitingQueuePublication = false;
            if (active() == null && !hasQueued()) leaveForeground(startId, generation);
        }));
        TaskSnapshot active = active();
        if (intent != null && active != null
                && active.request.id.toString().equals(intent.getStringExtra(EXTRA_TASK))) {
            if (ACTION_CANCEL.equals(intent.getAction())) coordinator.cancel(active.request.id);
            else if (ACTION_PAUSE.equals(intent.getAction())) coordinator.pause(active.request.id);
            else if (ACTION_RESUME.equals(intent.getAction())) coordinator.resume(active.request.id);
        }
        return START_NOT_STICKY;
    }

    /** All UI/service calls are on the main thread; worker events are posted there. */
    public static void enqueue(android.content.Context context, DownloadTask legacy) {
        android.content.Context appCtx = context.getApplicationContext();
        AppContainer container = ((App) appCtx).container();
        DownloadRequest request = LegacyTaskMapper.freeze(legacy);
        ContextCompat.startForegroundService(appCtx, new Intent(appCtx, DownloadService.class));
        container.downloads.setExecutionEnabled(true);
        container.downloads.enqueue(request);
    }

    public void enqueue(DownloadTask legacy) {
        enqueue(this, legacy);
    }

    public void retryTask(DownloadTask task) {
        TaskSnapshot stored = find(task);
        if (stored == null || (stored.state != TaskSnapshot.State.ERROR
                && stored.state != TaskSnapshot.State.INTERRUPTED)) return;
        startExecutionPath();
        coordinator.retry(stored.request.id);
    }

    public void resumeTask(DownloadTask task) {
        TaskSnapshot stored = find(task);
        if (stored == null || stored.state != TaskSnapshot.State.PAUSED) return;
        startExecutionPath();
        coordinator.resume(stored.request.id);
    }

    public void pauseCurrentTask() {
        TaskSnapshot active = active();
        if (active != null) coordinator.pause(active.request.id);
    }

    public void cancelTask(DownloadTask task) {
        TaskSnapshot stored = find(task);
        if (stored != null) coordinator.cancel(stored.request.id);
    }

    public void removeTask(DownloadTask task) {
        TaskSnapshot stored = find(task);
        if (stored != null) coordinator.remove(stored.request.id);
    }

    /** Compatibility projection only: changing these DTOs cannot change the queue. */
    public List<DownloadTask> getTasks() {
        List<DownloadTask> result = new ArrayList<>();
        for (TaskSnapshot snapshot : snapshots) result.add(LegacyTaskMapper.fromSnapshot(snapshot));
        Collections.reverse(result);
        return result;
    }

    public List<TaskSnapshot> getTaskSnapshots() { return snapshots; }
    public void setListener(Listener value) { listener = value; }

    private TaskSnapshot find(DownloadTask task) {
        if (task == null) return null;
        for (TaskSnapshot snapshot : snapshots) {
            if (snapshot.request.id.equals(task.getRequest().id)) return snapshot;
        }
        return null;
    }

    private void startExecutionPath() {
        ContextCompat.startForegroundService(this, new Intent(this, DownloadService.class));
        executionPathStarted = true;
        enterForeground();
        coordinator.setExecutionEnabled(true);
    }

    private void enterForeground() {
        startForeground(NOTIFICATION_ID, buildNotification(active()));
        foreground = true;
    }

    private void leaveForeground(int startId, long generation) {
        if (generation != startGeneration || active() != null || hasQueued()) return;
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        if (startId == 0) stopSelf(); else stopSelf(startId);
    }

    private void onSnapshots(List<TaskSnapshot> updated) {
        if (destroyed) return;
        snapshots = updated;
        TaskSnapshot active = active();
        if (executionPathStarted) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (active != null && (!foreground || active.state != lastNotificationState
                    || !active.executionId.equals(lastExecution) || now - lastNotificationTime >= 400)) {
                startForeground(NOTIFICATION_ID, buildNotification(active));
                foreground = true;
                lastNotificationTime = now;
                lastNotificationState = active.state;
                lastExecution = active.executionId;
            }
        }
        if (active != null && (active.state == TaskSnapshot.State.PAUSING
                || active.state == TaskSnapshot.State.CANCELLING)
                && !active.executionId.equals(stoppingExecution)) {
            stoppingExecution = active.executionId;
            UUID execution = active.executionId;
            main.postDelayed(() -> coordinator.stoppingDeadline(execution), 10000);
        }
        if (!listenerPending) {
            listenerPending = true;
            main.postDelayed(() -> {
                listenerPending = false;
                if (!destroyed && listener != null) listener.onTasksChanged();
            }, 150);
        }
    }

    private boolean hasQueued() {
        for (TaskSnapshot task : snapshots) {
            if (task.state == TaskSnapshot.State.QUEUED || task.state == TaskSnapshot.State.PAUSED) return true;
        }
        return false;
    }

    private TaskSnapshot active() {
        for (TaskSnapshot task : snapshots) {
            switch (task.state) {
                case TRANSLATING: case DOWNLOADING: case PROCESSING: case PAUSING: case CANCELLING: case PAUSED:
                    return task;
                default: break;
            }
        }
        return null;
    }

    private PendingIntent action(String action, TaskSnapshot task) {
        String executionId = task.executionId != null ? task.executionId.toString() : task.request.id.toString();
        Intent intent = new Intent(this, DownloadService.class).setAction(action)
                .setData(Uri.parse("ytrd://execution/" + executionId + "/" + action))
                .putExtra(EXTRA_TASK, task.request.id.toString())
                .putExtra(EXTRA_EXECUTION, executionId);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(TaskSnapshot task) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean isPaused = task != null && task.state == TaskSnapshot.State.PAUSED;
        String contentText = isPaused ? "На паузе" : (task == null ? "Подготовка…" : task.stageText);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, App.CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_downloads_nav)
                .setContentTitle(task == null || task.request.title == null ? "ytrd" : task.request.title)
                .setContentText(contentText)
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true);

        if (isPaused) {
            builder.setProgress(100, (int) Math.max(0, task.progress), false);
        } else {
            builder.setProgress(100, task == null ? 0 : (int) Math.max(0, task.progress),
                    task == null || task.progress < 0);
        }

        if (task != null) {
            if (isPaused) {
                builder.addAction(R.drawable.ic_play, "Продолжить", action(ACTION_RESUME, task));
            } else if (task.state != TaskSnapshot.State.PAUSING && task.state != TaskSnapshot.State.CANCELLING) {
                builder.addAction(R.drawable.ic_pause, "Пауза", action(ACTION_PAUSE, task));
            }
            builder.addAction(R.drawable.ic_close, "Отмена", action(ACTION_CANCEL, task));
        }
        return builder.build();
    }

    @Override public void onDestroy() {
        destroyed = true;
        listener = null;
        container.detach(observer);
        coordinator.setExecutionEnabled(false);
        super.onDestroy();
    }
}
