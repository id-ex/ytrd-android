package io.github.idex.ytrdroid.data.persistence;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ArtifactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ArtifactEntity artifact);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ArtifactEntity> artifacts);

    @Query("SELECT * FROM artifacts WHERE id = :id LIMIT 1")
    ArtifactEntity get(String id);

    @Query("SELECT * FROM artifacts WHERE hidden = 0 ORDER BY timestamp DESC")
    List<ArtifactEntity> getAllVisible();

    @Query("UPDATE artifacts SET hidden = 1 WHERE id = :id")
    void hide(String id);

    @Query("UPDATE artifacts SET hidden = 1 WHERE id IN (:ids)")
    void hideAll(List<String> ids);

    @Query("DELETE FROM artifacts WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM artifacts WHERE id IN (:ids)")
    void deleteAll(List<String> ids);

    @Query("SELECT COUNT(*) FROM artifacts")
    int count();
}
