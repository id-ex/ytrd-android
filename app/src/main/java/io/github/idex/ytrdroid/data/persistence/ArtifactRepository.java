package io.github.idex.ytrdroid.data.persistence;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ArtifactRepository {
    private final ArtifactDao dao;
    private final Executor executor;

    public ArtifactRepository(ArtifactDao dao) {
        this(dao, Executors.newSingleThreadExecutor(r -> new Thread(r, "artifact-db")));
    }

    public ArtifactRepository(ArtifactDao dao, Executor executor) {
        this.dao = dao;
        this.executor = executor;
    }

    public void getAllVisible(Consumer<List<ArtifactEntity>> callback) {
        executor.execute(() -> {
            List<ArtifactEntity> items = dao.getAllVisible();
            callback.accept(items);
        });
    }

    public void save(ArtifactEntity entity, Runnable onComplete) {
        executor.execute(() -> {
            dao.upsert(entity);
            if (onComplete != null) onComplete.run();
        });
    }

    public void hide(String id, Runnable onComplete) {
        executor.execute(() -> {
            dao.hide(id);
            if (onComplete != null) onComplete.run();
        });
    }

    public void delete(String id, Runnable onComplete) {
        executor.execute(() -> {
            dao.delete(id);
            if (onComplete != null) onComplete.run();
        });
    }
}
