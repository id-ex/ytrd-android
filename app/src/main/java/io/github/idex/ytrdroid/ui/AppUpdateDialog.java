package io.github.idex.ytrdroid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.io.File;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.data.updater.AppUpdateChecker;
import io.github.idex.ytrdroid.data.updater.AppUpdateInstaller;

public final class AppUpdateDialog {

    private AppUpdateDialog() {}

    @android.annotation.SuppressLint({"InflateParams", "SetTextI18n"})
    public static void show(Activity activity, AppUpdateChecker.UpdateInfo info) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90);
            window.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView txtCurrent = view.findViewById(R.id.txt_current_version);
        TextView txtNew = view.findViewById(R.id.txt_new_version);
        TextView txtSize = view.findViewById(R.id.txt_apk_size);
        TextView txtChangelog = view.findViewById(R.id.txt_update_changelog);

        View layoutProgress = view.findViewById(R.id.layout_update_progress);
        ProgressBar progressBar = view.findViewById(R.id.progress_update_download);
        TextView txtProgress = view.findViewById(R.id.txt_update_progress);

        MaterialButton btnLater = view.findViewById(R.id.btn_update_later);
        MaterialButton btnUpdate = view.findViewById(R.id.btn_update_now);

        txtCurrent.setText("v" + info.currentVersion);
        txtNew.setText("v" + info.newVersion);
        if (info.apkSize > 0) {
            txtSize.setText(Formatter.formatFileSize(activity, info.apkSize));
            txtSize.setVisibility(View.VISIBLE);
        } else {
            txtSize.setVisibility(View.GONE);
        }

        if (info.changelog != null && !info.changelog.isEmpty()) {
            txtChangelog.setText(info.changelog);
        } else {
            txtChangelog.setText("Релиз " + info.releaseTitle);
        }

        btnLater.setOnClickListener(v -> dialog.dismiss());

        btnUpdate.setOnClickListener(v -> {
            btnUpdate.setEnabled(false);
            btnLater.setEnabled(false);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            layoutProgress.setVisibility(View.VISIBLE);
            txtProgress.setText("Подключение к серверу…");

            AppUpdateInstaller.downloadAndInstall(activity.getApplicationContext(), info, new AppUpdateInstaller.ProgressCallback() {
                @Override
                public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    progressBar.setProgress(percent);
                    String text = totalBytes > 0
                            ? String.format(java.util.Locale.getDefault(), "Скачивание: %d%% (%s / %s)",
                            percent,
                            Formatter.formatFileSize(activity, downloadedBytes),
                            Formatter.formatFileSize(activity, totalBytes))
                            : String.format(java.util.Locale.getDefault(), "Скачивание: %s", Formatter.formatFileSize(activity, downloadedBytes));
                    txtProgress.setText(text);
                }

                @Override
                public void onComplete(File apkFile) {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    btnUpdate.setEnabled(true);
                    btnLater.setEnabled(true);
                    dialog.setCancelable(true);
                    txtProgress.setText("Ошибка загрузки: " + error.getMessage());
                    Toast.makeText(activity, "Не удалось скачать обновление", Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }
}
