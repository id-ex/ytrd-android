package io.github.idex.ytrdroid.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.engine.DownloadEngine;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.ui.MainActivity;

public class DownloadService extends Service {
    private static long nextId = 1;

    public static final String ACTION_CANCEL = "io.github.idex.ytrdroid.CANCEL";
    public static final String ACTION_PAUSE = "io.github.idex.ytrdroid.PAUSE";
    public static final String ACTION_RESUME = "io.github.idex.ytrdroid.RESUME";

    private boolean isPaused = false;
    private final IBinder binder = new LocalBinder();
    private final DownloadEngine engine = new DownloadEngine();
    private final List<DownloadTask> tasks = new ArrayList<>();
    private final Queue<DownloadTask> queue = new LinkedList<>();
    private DownloadTask current;
    private Listener listener;

    public interface Listener {
        void onTasksChanged();
    }

    public class LocalBinder extends Binder {
        public DownloadService getService() { return DownloadService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_CANCEL.equals(act)) {
                if (current != null) cancelTask(current);
            } else if (ACTION_PAUSE.equals(act)) {
                pauseCurrentTask();
            } else if (ACTION_RESUME.equals(act)) {
                isPaused = false;
                processNext();
            }
        }
        startForeground(1, buildNotification(current != null ? current.title : "ytrd",
                current != null ? stage(current) : "Подготовка…"));
        return START_NOT_STICKY;
    }

    public void enqueue(DownloadTask task) {
        androidx.core.content.ContextCompat.startForegroundService(this,
                new Intent(this, DownloadService.class));
        startForeground(1, buildNotification(task.title, "Подготовка…"));
        task.id = nextId++;
        tasks.add(0, task);
        queue.add(task);
        notifyListener();
        processNext();
    }

    public List<DownloadTask> getTasks() { return tasks; }

    public boolean isPaused() { return isPaused; }

    public void setListener(Listener l) { this.listener = l; }

    public void retryTask(DownloadTask task) {
        if (task == null) return;
        task.state = DownloadTask.State.QUEUED;
        task.errorMessage = null;
        task.progress = 0;
        task.speed = 0;
        task.downloadedBytes = 0;
        task.totalBytes = 0;
        task.stageText = "В очереди";
        if (!queue.contains(task)) {
            queue.add(task);
        }
        notifyListener();
        processNext();
    }

    public void removeTask(DownloadTask task) {
        if (task == null) return;
        queue.remove(task);
        tasks.remove(task);
        if (task == current) {
            engine.cancel(task);
            current = null;
            processNext();
        }
        notifyListener();
    }

    public void pauseCurrentTask() {
        if (current != null) {
            DownloadTask paused = current;
            paused.state = DownloadTask.State.PAUSED;
            paused.stageText = "На паузе";
            engine.cancel(paused, true);
            current = null;
            queue.remove(paused);
            queue.add(paused);
            notifyListener();
            processNext();
        }
    }

    public void resumeTask(DownloadTask task) {
        if (task == null) return;
        task.state = DownloadTask.State.QUEUED;
        task.stageText = "В очереди";
        queue.remove(task);
        ((LinkedList<DownloadTask>) queue).addFirst(task);
        notifyListener();
        processNext();
    }

    public void cancelTask(DownloadTask task) {
        queue.remove(task);
        tasks.remove(task);
        if (task == current) {
            engine.cancel(task, false);
            current = null;
            if (queue.isEmpty()) {
                stopForeground(true);
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(1);
            } else {
                processNext();
            }
        }
        task.state = DownloadTask.State.CANCELLED;
        notifyListener();
    }

    private void processNext() {
        if (current != null) return;
        current = queue.poll();
        if (current == null) {
            stopForeground(true);
            stopSelf();
            return;
        }

        DownloadTask task = current;
        updateNotification(task.title != null ? task.title : "Загрузка...", "0%");

        new Thread(() -> {
            engine.execute(getApplicationContext(), task, new DownloadEngine.Listener() {
                @Override
                public void onProgress(DownloadTask t) {
                    updateNotification(t.title != null ? t.title : "Загрузка...",
                            t.formatProgress());
                    notifyListenerThrottled();
                }

                @Override
                public void onStateChanged(DownloadTask t) {
                    updateNotification(t.title, stage(t));
                    notifyListener();
                }
            });
            current = null;
            processNext();
        }, "download-" + task.id).start();
    }

    private long lastListenerNotifyTime = 0;
    private void notifyListenerThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastListenerNotifyTime > 150) {
            lastListenerNotifyTime = now;
            notifyListener();
        }
    }

    private void notifyListener() {
        if (listener != null) {
            new android.os.Handler(getMainLooper()).post(() -> {
                if (listener != null) listener.onTasksChanged();
            });
        }
    }

    private String stage(DownloadTask task) {
        switch (task.state) {
            case TRANSLATING: return "Ожидание перевода от Яндекса…";
            case PROCESSING: return "Обработка видео…";
            case DOWNLOADING: return task.progress < 0 ? "Подготовка…" : task.formatProgress();
            case DONE: return "Готово";
            case ERROR: return "Ошибка загрузки";
            case CANCELLED: return "Отменено";
            default: return "В очереди";
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent ni = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 1, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pauseIntent = new Intent(this, DownloadService.class).setAction(isPaused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent pausePi = PendingIntent.getService(this, 2, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, App.CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_downloads_nav)
                .setContentTitle(title != null ? title : "ytrd")
                .setContentText(text)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setProgress(100, current == null ? 0 : (int) Math.max(0, current.progress),
                        current == null || current.state != DownloadTask.State.DOWNLOADING || current.progress < 0)
                .addAction(isPaused ? R.drawable.ic_play : R.drawable.ic_pause,
                        isPaused ? "Продолжить" : "Пауза", pausePi)
                .addAction(R.drawable.ic_close, "Отмена", cancelPi)
                .setOngoing(!isPaused);

        return builder.build();
    }

    private long lastNotifTime = 0;

    private void updateNotification(String title, String text) {
        long now = System.currentTimeMillis();
        if (now - lastNotifTime < 400 && current != null && current.state == DownloadTask.State.DOWNLOADING) {
            return;
        }
        lastNotifTime = now;
        Notification n = buildNotification(title, text);
        ((android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(1, n);
    }
}
