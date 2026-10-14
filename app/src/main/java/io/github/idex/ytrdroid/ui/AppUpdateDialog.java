package io.github.idex.ytrdroid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.Html;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.Locale;

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
        MaterialButton btnOpenGithub = view.findViewById(R.id.btn_open_github);
        TextView btnToggleChangelog = view.findViewById(R.id.btn_toggle_changelog);
        View scrollChangelog = view.findViewById(R.id.scroll_changelog);

        // Constrain changelog height so action buttons are ALWAYS visible
        int maxChangelogHeight = (int) (activity.getResources().getDisplayMetrics().density * 200);
        scrollChangelog.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                scrollChangelog.getViewTreeObserver().removeOnPreDrawListener(this);
                if (scrollChangelog.getHeight() > maxChangelogHeight) {
                    android.view.ViewGroup.LayoutParams lp = scrollChangelog.getLayoutParams();
                    lp.height = maxChangelogHeight;
                    scrollChangelog.setLayoutParams(lp);
                }
                return true;
            }
        });

        txtCurrent.setText("v" + info.currentVersion);
        txtNew.setText("v" + info.newVersion);
        if (info.apkSize > 0) {
            txtSize.setText(Formatter.formatFileSize(activity, info.apkSize));
            txtSize.setVisibility(View.VISIBLE);
        } else {
            txtSize.setVisibility(View.GONE);
        }

        final String fullRaw = info.changelog != null ? info.changelog.trim() : "";
        final String shortRaw = extractShortChangelog(fullRaw);
        final boolean canExpand = !shortRaw.equals(fullRaw) && fullRaw.length() > shortRaw.length();
        final boolean[] isExpanded = { false };

        txtChangelog.setText(renderMarkdown(shortRaw.isEmpty() ? "Релиз " + info.releaseTitle : shortRaw));
        txtChangelog.setMovementMethod(LinkMovementMethod.getInstance());

        if (canExpand && btnToggleChangelog != null) {
            btnToggleChangelog.setVisibility(View.VISIBLE);
            btnToggleChangelog.setOnClickListener(v -> {
                isExpanded[0] = !isExpanded[0];
                txtChangelog.setText(renderMarkdown(isExpanded[0] ? fullRaw : shortRaw));
                btnToggleChangelog.setText(isExpanded[0] ? "Свернуть ▴" : "Полный список изменений ▾");
            });
        } else if (btnToggleChangelog != null) {
            btnToggleChangelog.setVisibility(View.GONE);
        }

        if (btnOpenGithub != null) {
            if (info.releaseUrl != null && !info.releaseUrl.isEmpty()) {
                btnOpenGithub.setVisibility(View.VISIBLE);
                btnOpenGithub.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl));
                        activity.startActivity(intent);
                    } catch (Exception ignored) {}
                });
            } else {
                btnOpenGithub.setVisibility(View.GONE);
            }
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

    public static CharSequence renderMarkdown(String md) {
        if (md == null || md.trim().isEmpty()) return "";
        String text = md.trim();

        // Strip HTML comments <!-- ... -->
        text = text.replaceAll("(?s)<!--.*?-->", "");

        // Convert links: [text](url) -> <a href="url">text</a>
        text = text.replaceAll("\\[([^\\]]+)\\]\\(([^\\)]+)\\)", "<a href=\"$2\">$1</a>");

        // Convert bold: **text** -> <b>text</b>
        text = text.replaceAll("\\*\\*([^\\*]+)\\*\\*", "<b>$1</b>");

        // Convert inline code: `code` -> <font color="#FF5252">code</font>
        text = text.replaceAll("`([^`]+)`", "<font color=\"#FF7597\">$1</font>");

        // Convert headers
        text = text.replaceAll("(?m)^###?\\s+(.*)$", "<br><b>$1</b>");
        text = text.replaceAll("(?m)^#\\s+(.*)$", "<b><big>$1</big></b>");

        // Convert bullet points: - item or * item -> • item
        text = text.replaceAll("(?m)^\\s*[-*]\\s+", "• ");

        // Convert newlines to <br>
        text = text.replace("\r\n", "\n").replace("\n", "<br>");

        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
    }

    public static String extractShortChangelog(String fullMarkdown) {
        if (fullMarkdown == null || fullMarkdown.trim().isEmpty()) return "";

        // 1. Check for explicit <!-- app --> ... <!-- /app --> block
        int startApp = fullMarkdown.indexOf("<!-- app -->");
        int endApp = fullMarkdown.indexOf("<!-- /app -->");
        if (startApp != -1 && endApp != -1 && endApp > startApp) {
            return fullMarkdown.substring(startApp + 12, endApp).trim();
        }

        // 2. Check for "## Что нового" section
        String lower = fullMarkdown.toLowerCase(Locale.ROOT);
        int whatsNewIdx = lower.indexOf("что нового");
        String candidate = fullMarkdown;
        if (whatsNewIdx != -1) {
            candidate = fullMarkdown.substring(whatsNewIdx);
            int colonIdx = candidate.indexOf(":\n");
            if (colonIdx != -1) candidate = candidate.substring(colonIdx + 2);
        }

        String[] lines = candidate.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("##") || trimmed.startsWith("#")) {
                if (count > 0) break;
                continue;
            }
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
                count++;
                if (count >= 5) break;
            }
        }

        if (sb.length() > 0) return sb.toString();

        if (fullMarkdown.length() > 250) {
            int lastSpace = fullMarkdown.lastIndexOf(" ", 250);
            return fullMarkdown.substring(0, lastSpace > 0 ? lastSpace : 250) + "…";
        }
        return fullMarkdown;
    }
}
