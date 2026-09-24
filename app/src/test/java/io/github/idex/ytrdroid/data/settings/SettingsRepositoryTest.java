package io.github.idex.ytrdroid.data.settings;

import android.content.SharedPreferences;

import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class SettingsRepositoryTest {
    private static class FakeSharedPreferences implements SharedPreferences {
        final Map<String, Object> map = new HashMap<>();

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String defValue) {
            Object v = map.get(key); return v instanceof String ? (String) v : defValue;
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = map.get(key); return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return new FakeEditor(this); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }

    private static class FakeEditor implements SharedPreferences.Editor {
        final FakeSharedPreferences sp;
        final Map<String, Object> temp = new HashMap<>();

        FakeEditor(FakeSharedPreferences sp) { this.sp = sp; }
        @Override public SharedPreferences.Editor putString(String key, String value) { temp.put(key, value); return this; }
        @Override public SharedPreferences.Editor putStringSet(String key, Set<String> values) { return this; }
        @Override public SharedPreferences.Editor putInt(String key, int value) { return this; }
        @Override public SharedPreferences.Editor putLong(String key, long value) { return this; }
        @Override public SharedPreferences.Editor putFloat(String key, float value) { return this; }
        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) { temp.put(key, value); return this; }
        @Override public SharedPreferences.Editor remove(String key) { temp.remove(key); sp.map.remove(key); return this; }
        @Override public SharedPreferences.Editor clear() { temp.clear(); sp.map.clear(); return this; }
        @Override public boolean commit() { sp.map.putAll(temp); return true; }
        @Override public void apply() { commit(); }
    }

    @Test
    public void returnsDefaultValuesWhenEmpty() {
        SettingsRepository repo = new SettingsRepository(new FakeSharedPreferences());
        assertEquals("1080", repo.getDefaultQuality());
        assertFalse(repo.isWifiOnly());
        assertFalse(repo.isTranslateDefault());
        assertEquals("standard", repo.getDefaultVoice());
        assertEquals("mix", repo.getDefaultAudioMode());
        assertEquals("/default", repo.getDownloadFolder(new File("/default")));
    }

    @Test
    public void readsAndWritesValuesCorrectly() {
        FakeSharedPreferences sp = new FakeSharedPreferences();
        SettingsRepository repo = new SettingsRepository(sp);

        sp.map.put(SettingsRepository.KEY_DEFAULT_QUALITY, "720");
        sp.map.put(SettingsRepository.KEY_WIFI_ONLY, true);
        sp.map.put(SettingsRepository.KEY_TRANSLATE_DEFAULT, true);
        sp.map.put(SettingsRepository.KEY_VOICE_DEFAULT, "live");
        sp.map.put(SettingsRepository.KEY_AUDIO_MODE_DEFAULT, "dual");

        assertEquals("720", repo.getDefaultQuality());
        assertTrue(repo.isWifiOnly());
        assertTrue(repo.isTranslateDefault());
        assertEquals("live", repo.getDefaultVoice());
        assertEquals("dual", repo.getDefaultAudioMode());

        repo.setDownloadFolder("/custom/path");
        assertEquals("/custom/path", repo.getDownloadFolder(null));
    }
}
