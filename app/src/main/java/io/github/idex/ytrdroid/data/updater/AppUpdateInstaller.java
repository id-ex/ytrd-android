package io.github.idex.ytrdroid.data.updater;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AppUpdateInstaller {

    public interface ProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onComplete(File apkFile);
        void onError(Throwable error);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private static volatile File pendingInstallApk;

    private AppUpdateInstaller() {}

    public static void downloadAndInstall(Context context, AppUpdateChecker.UpdateInfo info, ProgressCallback callback) {
        executor.execute(() -> {
            File destDir = new File(context.getCacheDir(), "updates");

            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File apkFile = new File(destDir, info.apkName != null ? info.apkName : "update.apk");
            if (apkFile.exists()) {
                apkFile.delete();
            }

            try {
                Request request = new Request.Builder()
                        .url(info.downloadUrl)
                        .header("User-Agent", "ytrd-android")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        dispatchError(callback, new java.io.IOException("HTTP download error: " + response.code()));
                        return;
                    }

                    ResponseBody body = response.body();
                    long totalBytes = body.contentLength() > 0 ? body.contentLength() : info.apkSize;

                    try (InputStream in = body.byteStream();
                         FileOutputStream out = new FileOutputStream(apkFile)) {

                        byte[] buffer = new byte[16384];
                        long downloaded = 0;
                        int read;
                        long lastProgressUpdate = 0;

                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            downloaded += read;

                            long now = System.currentTimeMillis();
                            if (now - lastProgressUpdate > 100 || downloaded == totalBytes) {
                                lastProgressUpdate = now;
                                int percent = totalBytes > 0 ? (int) (downloaded * 100 / totalBytes) : 0;
                                long finalDownloaded = downloaded;
                                mainHandler.post(() -> callback.onProgress(percent, finalDownloaded, totalBytes));
                            }
                        }
                        out.flush();

                        if (totalBytes > 0 && downloaded < totalBytes) {
                            apkFile.delete();
                            throw new java.io.IOException("Загрузка не завершена (" + downloaded + " из " + totalBytes + " байт)");
                        }
                    }

                    mainHandler.post(() -> {
                        callback.onComplete(apkFile);
                        installApk(context, apkFile);
                    });
                }
            } catch (Exception e) {
                dispatchError(callback, e);
            }
        });
    }

    @android.annotation.SuppressLint("QueryPermissionsNeeded")
    public static void installApk(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) return;

        if (!context.getPackageManager().canRequestPackageInstalls()) {
            pendingInstallApk = apkFile;
            Toast.makeText(context, "Разрешите установку из этого источника для обновления", Toast.LENGTH_LONG).show();
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            return;
        }
        pendingInstallApk = null;

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            List<ResolveInfo> resolveInfoList = context.getPackageManager().queryIntentActivities(
                    installIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resolveInfoList) {
                context.grantUriPermission(resolveInfo.activityInfo.packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            context.startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка запуска установки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void resumePendingInstall(Context context) {
        if (context == null) return;
        File pending = pendingInstallApk;
        if (pending != null && pending.exists()) {
            if (context.getPackageManager().canRequestPackageInstalls()) {
                pendingInstallApk = null;
                installApk(context, pending);
            }
        }
    }

    private static void dispatchError(ProgressCallback callback, Throwable error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
