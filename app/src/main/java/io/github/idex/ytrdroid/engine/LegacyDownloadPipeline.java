package io.github.idex.ytrdroid.engine;

import android.content.Context;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.application.CancellationToken;
import io.github.idex.ytrdroid.application.DownloadCoordinator;
import io.github.idex.ytrdroid.data.network.NetworkPolicy;
import io.github.idex.ytrdroid.data.settings.SettingsRepository;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.LegacyTaskMapper;

/** Each execution owns its engine, mutable DTO and resource handles. */
public final class LegacyDownloadPipeline implements DownloadCoordinator.Pipeline {
    private final Context context;
    private final SettingsRepository settings;
    private final NetworkPolicy network;

    public LegacyDownloadPipeline(Context context) {
        this(context, null, null);
    }

    public LegacyDownloadPipeline(Context context, SettingsRepository settings, NetworkPolicy network) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.settings = settings;
        this.network = network;
    }

    private SettingsRepository resolveSettings() {
        if (settings != null) {
            return settings;
        }
        Context appCtx = context != null ? context.getApplicationContext() : null;
        if (appCtx instanceof App) {
            App app = (App) appCtx;
            if (app.container() != null && app.container().settings != null) {
                return app.container().settings;
            }
        }
        return context != null ? new SettingsRepository(context) : null;
    }

    private NetworkPolicy resolveNetwork() {
        if (network != null) {
            return network;
        }
        Context appCtx = context != null ? context.getApplicationContext() : null;
        if (appCtx instanceof App) {
            App app = (App) appCtx;
            if (app.container() != null && app.container().network != null) {
                return app.container().network;
            }
        }
        return context != null ? new NetworkPolicy(context) : null;
    }

    @Override
    public TaskSnapshot run(DownloadRequest request, UUID executionId, CancellationToken token,
                            Consumer<TaskSnapshot> progress) throws Exception {
        token.throwIfCancelled();

        NetworkPolicy net = resolveNetwork();
        if (net != null && !net.isNetworkAvailable()) {
            throw new IOException("Отсутствует интернет-соединение");
        }

        SettingsRepository set = resolveSettings();
        if (set != null && net != null && set.isWifiOnly() && !net.isWifiConnected()) {
            throw new IOException("Загрузка приостановлена: требуется подключение к Wi-Fi (включено ограничение в настройках)");
        }

        DownloadTask workerTask = LegacyTaskMapper.fromRequest(request,
                request.id.getMostSignificantBits() & Long.MAX_VALUE);
        workerTask.executionId = executionId;
        DownloadEngine engine = new DownloadEngine();
        token.onCancel(() -> engine.cancel(workerTask));
        token.throwIfCancelled();
        engine.execute(context, workerTask, new DownloadEngine.Listener() {
            private void emit(DownloadTask task) {
                if (token.isCancelled()) return;
                progress.accept(LegacyTaskMapper.snapshot(task));
            }
            @Override public void onProgress(DownloadTask task) { emit(task); }
            @Override public void onStateChanged(DownloadTask task) { emit(task); }
        });
        return LegacyTaskMapper.snapshot(workerTask);
    }
}
