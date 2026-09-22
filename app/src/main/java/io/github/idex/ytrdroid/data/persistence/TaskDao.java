package io.github.idex.ytrdroid.data.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TaskEntity task);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    TaskEntity get(String id);

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    List<TaskEntity> getAll();

    @Query("DELETE FROM tasks WHERE id = :id")
    void delete(String id);

    @Query("UPDATE tasks SET state = 'INTERRUPTED', stageText = 'Прервано остановкой приложения' " +
           "WHERE state IN ('TRANSLATING', 'DOWNLOADING', 'PROCESSING', 'PAUSING', 'CANCELLING')")
    int markActiveAsInterrupted();
}
