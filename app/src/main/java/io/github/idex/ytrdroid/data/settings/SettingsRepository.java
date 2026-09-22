package io.github.idex.ytrdroid.data.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.File;

/**
 * Single source of truth for app preferences with safe defaults.
 * Bridges SettingsActivity and download configuration.
 */
public final class SettingsRepository {
    public static final String KEY_DEFAULT_QUALITY = "default_quality";
    public static final String KEY_WIFI_ONLY = "wifi_only";
    public static final String KEY_TRANSLATE_DEFAULT = "translate_default";
    public static final String KEY_VOICE_DEFAULT = "voice_default";
    public static final String KEY_AUDIO_MODE_DEFAULT = "audio_mode_default";
    public static final String KEY_THEME = "theme";
    public static final String KEY_FOLDER = "last_download_folder";

    private final SharedPreferences prefs;

    public SettingsRepository(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        migrateLegacyPrefs(context);
    }

    public SettingsRepository(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public String getDefaultQuality() {
        return prefs.getString(KEY_DEFAULT_QUALITY, "1080");
    }

    public boolean isWifiOnly() {
        return prefs.getBoolean(KEY_WIFI_ONLY, false);
    }

    public boolean isTranslateDefault() {
        return prefs.getBoolean(KEY_TRANSLATE_DEFAULT, false);
    }

    public String getDefaultVoice() {
        return prefs.getString(KEY_VOICE_DEFAULT, "standard");
    }

    public String getDefaultAudioMode() {
        return prefs.getString(KEY_AUDIO_MODE_DEFAULT, "mix");
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, "dark");
    }

    public String getDownloadFolder(File fallbackDir) {
        String def = fallbackDir != null ? fallbackDir.getAbsolutePath() : "";
        return prefs.getString(KEY_FOLDER, def);
    }

    public void setDownloadFolder(String folder) {
        prefs.edit().putString(KEY_FOLDER, folder).apply();
    }

    /** Migrates old "ytrd_prefs" private file to standard default preferences. */
    private void migrateLegacyPrefs(Context context) {
        SharedPreferences old = context.getSharedPreferences("ytrd_prefs", Context.MODE_PRIVATE);
        if (old.contains(KEY_FOLDER) && !prefs.contains(KEY_FOLDER)) {
            prefs.edit().putString(KEY_FOLDER, old.getString(KEY_FOLDER, "")).apply();
            old.edit().remove(KEY_FOLDER).apply();
        }
    }
}
