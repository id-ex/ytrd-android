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
                io.github.idex.ytrdroid.App app = (io.github.idex.ytrdroid.App) requireContext().getApplicationContext();
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
