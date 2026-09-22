package io.github.idex.ytrdroid.engine;

import android.content.Context;

import java.util.UUID;
import java.util.function.Consumer;

import io.github.idex.ytrdroid.application.CancellationToken;
import io.github.idex.ytrdroid.application.DownloadCoordinator;
import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.LegacyTaskMapper;

/** Each execution owns its engine, mutable DTO and resource handles. */
public final class LegacyDownloadPipeline implements DownloadCoordinator.Pipeline {
    private final Context context;

    public LegacyDownloadPipeline(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public TaskSnapshot run(DownloadRequest request, UUID executionId, CancellationToken token,
                            Consumer<TaskSnapshot> progress) {
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
