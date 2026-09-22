package io.github.idex.ytrdroid.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference;

import io.github.idex.ytrdroid.R;
import android.widget.Toast;
import com.yausername.youtubedl_android.YoutubeDL;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Version
            Preference version = findPreference("version");
            if (version != null) {
                try {
                    String v = requireContext().getPackageManager()
                            .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                    version.setSummary("ytrd " + v);
                } catch (Exception ignored) {}
            }

            // yt-dlp version & manual updater
            Preference ytdlp = findPreference("ytdlp_version");
            if (ytdlp != null) {
                try {
                    String v = YoutubeDL.getInstance().version(requireContext());
                    ytdlp.setSummary(v != null ? v : "Нажмите для обновления");
                } catch (Exception ignored) {
                    ytdlp.setSummary("Нажмите для обновления");
                }
                ytdlp.setOnPreferenceClickListener(pref -> {
                    pref.setSummary("Обновление...");
                    Toast.makeText(requireContext(), "Обновление yt-dlp...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        String newVer;
                        String toastMsg;
                        try {
                            YoutubeDL.UpdateStatus s = YoutubeDL.getInstance().updateYoutubeDL(
                                    requireContext().getApplicationContext(),
                                    YoutubeDL.UpdateChannel._NIGHTLY);
                            newVer = YoutubeDL.getInstance().version(requireContext());
                            toastMsg = (s == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
                                    ? "Уже последняя версия: " + newVer
                                    : "Обновлено до: " + newVer;
                        } catch (Exception e) {
                            newVer = "Ошибка: " + e.getMessage();
                            toastMsg = newVer;
                        }
                        if (getActivity() != null) {
                            String finalVer = newVer;
                            String finalToast = toastMsg;
                            getActivity().runOnUiThread(() -> {
                                pref.setSummary(finalVer);
                                Toast.makeText(requireContext(), finalToast, Toast.LENGTH_LONG).show();
                            });
                        }
                    }, "ytdlp-manual-update").start();
                    return true;
                });
            }

            // Clear cache
            Preference clearCache = findPreference("clear_cache");
            if (clearCache != null) {
                clearCache.setOnPreferenceClickListener(pref -> {
                    java.io.File cacheDir = requireContext().getCacheDir();
                    if (cacheDir.isDirectory()) {
                        for (java.io.File f : cacheDir.listFiles()) {
                            f.delete();
                        }
                    }
                    pref.setSummary("Очищено");
                    return true;
                });
            }
        }
    }
}
