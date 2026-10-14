package io.github.idex.ytrdroid.data.persistence;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import io.github.idex.ytrdroid.domain.model.DownloadRequest;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;

import static org.junit.Assert.*;

public class TaskRepositoryTest {

    private static class FakeTaskDao implements TaskDao {
        final List<TaskEntity> upserted = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        int markedInterrupted = 0;

        @Override
        public void upsert(TaskEntity task) {
            upserted.add(task);
        }

        @Override
        public TaskEntity get(String id) {
            for (TaskEntity e : upserted) {
                if (id.equals(e.id)) return e;
            }
            return null;
        }

        @Override
        public List<TaskEntity> getAll() {
            return new ArrayList<>(upserted);
        }

        @Override
        public void delete(String id) {
            deleted.add(id);
        }

        @Override
        public int markActiveAsInterrupted() {
            markedInterrupted++;
            return 1;
        }
    }

    private static final Executor DIRECT = Runnable::run;

    private static DownloadRequest request() {
        return new DownloadRequest(UUID.randomUUID(), "abcdefghijk", "Test Title", "http://thumb",
                720, DownloadRequest.ResultType.VIDEO, DownloadRequest.Container.MP4, false,
                DownloadRequest.Voice.STANDARD, DownloadRequest.AudioMode.ORIGINAL,
                DownloadRequest.Subtitles.NONE, null, 120.0, "en");
    }

    @Test
    public void saveThrottlesFrequentProgressUpdates() {
        FakeTaskDao dao = new FakeTaskDao();
        TaskRepository repo = new TaskRepository(dao, DIRECT);
        DownloadRequest req = request();
        UUID executionId = UUID.randomUUID();

        TaskSnapshot s1 = new TaskSnapshot(req, executionId, TaskSnapshot.State.DOWNLOADING,
                10f, 100, 1000, 50, "stage", null, null);
        TaskSnapshot s2 = new TaskSnapshot(req, executionId, TaskSnapshot.State.DOWNLOADING,
                11f, 110, 1000, 50, "stage", null, null);

        repo.save(s1, null);
        assertEquals(1, dao.upserted.size());

        // Immediately saving again in DOWNLOADING state should be throttled
        repo.save(s2, null);
        assertEquals(1, dao.upserted.size());

        // State change to PROCESSING must bypass throttling
        TaskSnapshot s3 = new TaskSnapshot(req, executionId, TaskSnapshot.State.PROCESSING,
                100f, 1000, 1000, 0, "muxing", null, null);
        repo.save(s3, null);
        assertEquals(2, dao.upserted.size());

        // Terminal state DONE must bypass throttling
        TaskSnapshot s4 = new TaskSnapshot(req, executionId, TaskSnapshot.State.DONE,
                100f, 1000, 1000, 0, "done", "/path/out.mp4", null);
        repo.save(s4, null);
        assertEquals(3, dao.upserted.size());
    }

    @Test
    public void deleteRemovesFromDao() {
        FakeTaskDao dao = new FakeTaskDao();
        TaskRepository repo = new TaskRepository(dao, DIRECT);
        UUID id = UUID.randomUUID();

        repo.delete(id, null);
        assertEquals(Collections.singletonList(id.toString()), dao.deleted);
    }

    @Test
    public void entityRoundTripsToSnapshot() {
        DownloadRequest req = request();
        TaskSnapshot s = new TaskSnapshot(req, UUID.randomUUID(), TaskSnapshot.State.INTERRUPTED,
                50f, 500, 1000, 100, "paused", null, null);

        FakeTaskDao dao = new FakeTaskDao();
        TaskRepository repo = new TaskRepository(dao, DIRECT);
        repo.save(s, null);

        assertEquals(1, dao.upserted.size());
        TaskSnapshot restored = TaskRepository.toSnapshot(dao.upserted.get(0));

        assertNotNull(restored);
        assertEquals(req.id, restored.request.id);
        assertEquals(req.title, restored.request.title);
        assertEquals(TaskSnapshot.State.INTERRUPTED, restored.state);
        assertEquals(50f, restored.progress, 0.01f);
    }
}
