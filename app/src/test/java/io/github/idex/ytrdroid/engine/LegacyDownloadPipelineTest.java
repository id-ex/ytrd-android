package io.github.idex.ytrdroid.engine;

import android.content.SharedPreferences;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import io.github.idex.ytrdroid.application.CancellationToken;
import io.github.idex.ytrdroid.application.DownloadCoordinator;
import io.github.idex.ytrdroid.data.network.NetworkPolicy;
import io.github.idex.ytrdroid.data.settings.SettingsRepository;
import io.github.idex.ytrdroid.domain.model.DownloadError;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import static org.junit.Assert.*;

public class LegacyDownloadPipelineTest {

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

    private static class FakeNetworkPolicy extends NetworkPolicy {
        boolean available = true;
        boolean wifi = true;

        FakeNetworkPolicy(boolean available, boolean wifi) {
            super(null);
            this.available = available;
            this.wifi = wifi;
        }

        @Override
        public boolean isNetworkAvailable() {
            return available;
        }

        @Override
        public boolean isWifiConnected() {
            return wifi;
        }
    }

    private static DownloadRequest createRequest() {
        return new DownloadRequest(UUID.randomUUID(), "dQw4w9WgXcQ", "Test Video", null,
                720, DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4,
                false, DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 120, "en");
    }

    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> pending = new ArrayDeque<>();
        @Override public void execute(Runnable work) { pending.add(work); }
        void next() { pending.remove().run(); }
        void drain() { while (!pending.isEmpty()) next(); }
    }

    @Test
    public void throwsWhenNetworkUnavailable() {
        FakeNetworkPolicy network = new FakeNetworkPolicy(false, false);
        SettingsRepository settings = new SettingsRepository(new FakeSharedPreferences());
        LegacyDownloadPipeline pipeline = new LegacyDownloadPipeline(null, settings, network);

        DownloadRequest req = createRequest();
        CancellationToken token = new CancellationToken();

        try {
            pipeline.run(req, UUID.randomUUID(), token, snapshot -> {});
            fail("Expected IOException when network is unavailable");
        } catch (IOException e) {
            assertEquals("Отсутствует интернет-соединение", e.getMessage());
        } catch (Exception e) {
            fail("Expected IOException, but caught: " + e);
        }
    }

    @Test
    public void throwsWhenWifiOnlyEnabledAndWifiDisconnected() {
        FakeNetworkPolicy network = new FakeNetworkPolicy(true, false);
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.map.put(SettingsRepository.KEY_WIFI_ONLY, true);
        SettingsRepository settings = new SettingsRepository(prefs);

        LegacyDownloadPipeline pipeline = new LegacyDownloadPipeline(null, settings, network);

        DownloadRequest req = createRequest();
        CancellationToken token = new CancellationToken();

        try {
            pipeline.run(req, UUID.randomUUID(), token, snapshot -> {});
            fail("Expected IOException when wifi-only constraint is not met");
        } catch (IOException e) {
            assertEquals("Загрузка приостановлена: требуется подключение к Wi-Fi (включено ограничение в настройках)", e.getMessage());
        } catch (Exception e) {
            fail("Expected IOException, but caught: " + e);
        }
    }

    @Test
    public void coordinatorTransitionsToErrorStateOnNetworkFailureWithoutCrashing() {
        FakeNetworkPolicy network = new FakeNetworkPolicy(false, false);
        SettingsRepository settings = new SettingsRepository(new FakeSharedPreferences());
        LegacyDownloadPipeline pipeline = new LegacyDownloadPipeline(null, settings, network);

        ManualExecutor serial = new ManualExecutor();
        ManualExecutor workers = new ManualExecutor();
        List<TaskSnapshot> snapshots = new ArrayList<>();

        DownloadCoordinator coordinator = new DownloadCoordinator(
                serial, workers, pipeline, list -> {
                    snapshots.clear();
                    snapshots.addAll(list);
                });

        DownloadRequest req = createRequest();
        coordinator.enqueue(req);
        serial.drain();

        // Worker executes and fails with IOException
        assertEquals(1, workers.pending.size());
        workers.next();
        serial.drain();

        assertEquals(1, snapshots.size());
        TaskSnapshot task = snapshots.get(0);
        assertEquals(TaskSnapshot.State.ERROR, task.state);
        assertNotNull(task.error);
        assertEquals("Отсутствует интернет-соединение", task.error.message);
        assertEquals(DownloadError.Category.NETWORK, task.error.category);
    }

    @Test
    public void coordinatorTransitionsToErrorStateOnWifiOnlyFailureWithoutCrashing() {
        FakeNetworkPolicy network = new FakeNetworkPolicy(true, false);
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.map.put(SettingsRepository.KEY_WIFI_ONLY, true);
        SettingsRepository settings = new SettingsRepository(prefs);
        LegacyDownloadPipeline pipeline = new LegacyDownloadPipeline(null, settings, network);

        ManualExecutor serial = new ManualExecutor();
        ManualExecutor workers = new ManualExecutor();
        List<TaskSnapshot> snapshots = new ArrayList<>();

        DownloadCoordinator coordinator = new DownloadCoordinator(
                serial, workers, pipeline, list -> {
                    snapshots.clear();
                    snapshots.addAll(list);
                });

        DownloadRequest req = createRequest();
        coordinator.enqueue(req);
        serial.drain();

        assertEquals(1, workers.pending.size());
        workers.next();
        serial.drain();

        assertEquals(1, snapshots.size());
        TaskSnapshot task = snapshots.get(0);
        assertEquals(TaskSnapshot.State.ERROR, task.state);
        assertNotNull(task.error);
        assertEquals("Загрузка приостановлена: требуется подключение к Wi-Fi (включено ограничение в настройках)", task.error.message);
        assertEquals(DownloadError.Category.NETWORK, task.error.category);
    }
}
