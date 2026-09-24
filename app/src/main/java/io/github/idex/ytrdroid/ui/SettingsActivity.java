package io.github.idex.ytrdroid.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.format.Formatter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yausername.youtubedl_android.YoutubeDL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.R;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private final ActivityResultLauncher<Uri> folderPicker =
                registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri == null) return;
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    String folder = uri.toString();
                    App app = (App) requireContext().getApplicationContext();
                    app.container().settings.setDownloadFolder(folder);
                    updateFolderSummary();
                });

        private final ActivityResultLauncher<String[]> cookiesPicker =
                registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null) return;
                    try {
                        File dest = new File(requireContext().getFilesDir(), "cookies.txt");
                        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                             FileOutputStream out = new FileOutputStream(dest)) {
                            if (in == null) throw new IOException("Не удалось прочитать файл");
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                            out.flush();
                        }
                        updateCookiesSummary();
                        Toast.makeText(requireContext(), "Файл cookies сохранён", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Ошибка импорта cookies: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

        private void updateFolderSummary() {
            Preference folderPref = findPreference("default_folder");
            if (folderPref != null) {
                App app = (App) requireContext().getApplicationContext();
                File defaultDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "ytrd");
                String currentFolder = app.container().settings.getDownloadFolder(defaultDir);
                folderPref.setSummary(formatFolderPath(currentFolder));
            }
        }

        private String formatFolderPath(String path) {
            if (path == null || path.isEmpty()) return "/storage/emulated/0/Download/ytrd";
            if (path.startsWith("content://")) {
                try {
                    String documentId = DocumentsContract.getTreeDocumentId(Uri.parse(path));
                    int separator = documentId.indexOf(':');
                    return separator >= 0 && separator + 1 < documentId.length()
                            ? documentId.substring(separator + 1) : documentId;
                } catch (Exception e) {
                    return path;
                }
            }
            return path;
        }

        private void updateCookiesSummary() {
            Preference p = findPreference("cookies_file");
            if (p != null) {
                File f = new File(requireContext().getFilesDir(), "cookies.txt");
                if (f.isFile() && f.length() > 0) {
                    p.setSummary("Установлен (" + f.length() + " байт) · Нажмите для замены");
                } else {
                    p.setSummary("Не выбран · Нажмите для выбора cookies.txt");
                }
            }
        }

        private void updateCacheSummary() {
            Preference clearCache = findPreference("clear_cache");
            if (clearCache != null) {
                long size = getDirectorySize(requireContext().getCacheDir());
                if (size <= 0) {
                    clearCache.setSummary("Кэш пуст (0 Б)");
                } else {
                    String formatted = Formatter.formatFileSize(requireContext(), size);
                    clearCache.setSummary("Занято: " + formatted + " · Нажмите для очистки");
                }
            }
        }

        private long getDirectorySize(File dir) {
            if (dir == null || !dir.exists()) return 0;
            if (!dir.isDirectory()) return dir.length();
            long size = 0;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    size += getDirectorySize(f);
                }
            }
            return size;
        }

        private void deleteRecursively(File fileOrDir) {
            if (fileOrDir == null || !fileOrDir.exists()) return;
            if (fileOrDir.isDirectory()) {
                File[] files = fileOrDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        deleteRecursively(f);
                    }
                }
            }
            fileOrDir.delete();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Default folder
            updateFolderSummary();
            Preference folderPref = findPreference("default_folder");
            if (folderPref != null) {
                folderPref.setOnPreferenceClickListener(pref -> {
                    App app = (App) requireContext().getApplicationContext();
                    File defaultDir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "ytrd");
                    String currentFolder = app.container().settings.getDownloadFolder(defaultDir);
                    Uri initialUri = currentFolder != null && currentFolder.startsWith("content://")
                            ? Uri.parse(currentFolder) : null;
                    folderPicker.launch(initialUri);
                    return true;
                });
            }

            // Version
            Preference version = findPreference("version");
            if (version != null) {
                try {
                    String v = requireContext().getPackageManager()
                            .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                    version.setSummary("ytrd " + v);
                } catch (Exception ignored) {}
            }

            // Cookies file picker
            updateCookiesSummary();
            Preference cookiesPref = findPreference("cookies_file");
            if (cookiesPref != null) {
                cookiesPref.setOnPreferenceClickListener(pref -> {
                    File f = new File(requireContext().getFilesDir(), "cookies.txt");
                    if (f.isFile() && f.length() > 0) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.pref_cookies_file)
                                .setItems(new CharSequence[]{"Заменить файл", "Удалить файл cookies", "Отмена"}, (dialog, which) -> {
                                    if (which == 0) {
                                        cookiesPicker.launch(new String[]{"text/plain", "*/*"});
                                    } else if (which == 1) {
                                        if (f.exists()) {
                                            f.delete();
                                        }
                                        updateCookiesSummary();
                                        Toast.makeText(requireContext(), "Файл cookies удален", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .show();
                    } else {
                        cookiesPicker.launch(new String[]{"text/plain", "*/*"});
                    }
                    return true;
                });
            }

            // yt-dlp version & manual updater
            Preference ytdlp = findPreference("ytdlp_version");
            if (ytdlp != null) {
                App app = (App) requireContext().getApplicationContext();
                String v = app.container().runtime.getVersion();
                ytdlp.setSummary(v != null ? v : "Нажмите для обновления");

                ytdlp.setOnPreferenceClickListener(pref -> {
                    pref.setSummary("Обновление...");
                    Toast.makeText(requireContext(), "Обновление yt-dlp...", Toast.LENGTH_SHORT).show();
                    app.container().runtime.update(YoutubeDL.UpdateChannel._NIGHTLY)
                            .thenAccept(msg -> {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        pref.setSummary(app.container().runtime.getVersion());
                                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                                    });
                                }
                            })
                            .exceptionally(ex -> {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        pref.setSummary("Ошибка обновления");
                                        String errMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                                        Toast.makeText(requireContext(), "Ошибка: " + errMsg, Toast.LENGTH_LONG).show();
                                    });
                                }
                                return null;
                            });
                    return true;
                });
            }

            // Clear cache
            updateCacheSummary();
            Preference clearCache = findPreference("clear_cache");
            if (clearCache != null) {
                clearCache.setOnPreferenceClickListener(pref -> {
                    File cacheDir = requireContext().getCacheDir();
                    if (cacheDir != null && cacheDir.isDirectory()) {
                        File[] files = cacheDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                deleteRecursively(f);
                            }
                        }
                    }
                    pref.setSummary("Кэш очищен (0 Б)");
                    Toast.makeText(requireContext(), "Кэш очищен", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }
    }
}
