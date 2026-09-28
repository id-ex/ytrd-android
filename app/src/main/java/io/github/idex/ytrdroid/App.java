package io.github.idex.ytrdroid;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

public class App extends Application {
    private static final String TAG = "YtrdApp";
    public static final String CHANNEL_DOWNLOADS = "downloads";
    public static final String CHANNEL_RESULTS = "download_results";
    private io.github.idex.ytrdroid.di.AppContainer container;

    public io.github.idex.ytrdroid.di.AppContainer container() { return container; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        container = new io.github.idex.ytrdroid.di.AppContainer(this);
        container.runtime.init();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    getString(R.string.download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Прогресс загрузок");

            NotificationChannel resultsChannel = new NotificationChannel(
                    CHANNEL_RESULTS,
                    getString(R.string.download_results_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            resultsChannel.setDescription("Результаты загрузок и ошибки");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(ch);
            notificationManager.createNotificationChannel(resultsChannel);
        }
    }
}
