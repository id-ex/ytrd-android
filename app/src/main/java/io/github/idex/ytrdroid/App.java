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

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initYtDlp();
    }

    private void initYtDlp() {
        new Thread(() -> {
            try {
                YoutubeDL.getInstance().init(this);
                FFmpeg.getInstance().init(this);
                Log.i(TAG, "yt-dlp and FFmpeg initialized. Version: " + YoutubeDL.getInstance().version(this));

                try {
                    YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._NIGHTLY);
                    Log.i(TAG, "yt-dlp update status: " + status + ", new version: " + YoutubeDL.getInstance().version(this));
                } catch (Exception e) {
                    Log.w(TAG, "Nightly update failed, trying stable: " + e.getMessage());
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._STABLE);
                        Log.i(TAG, "yt-dlp stable version: " + YoutubeDL.getInstance().version(this));
                    } catch (Exception ex) {
                        Log.w(TAG, "Stable update failed: " + ex.getMessage());
                    }
                }
            } catch (YoutubeDLException e) {
                Log.e(TAG, "Failed to init yt-dlp", e);
            }
        }, "ytdlp-init").start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    getString(R.string.download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Download progress");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}
