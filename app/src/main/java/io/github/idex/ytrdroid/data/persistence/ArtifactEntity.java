package io.github.idex.ytrdroid.data.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "artifacts")
public class ArtifactEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String taskId;
    public String url;
    public String title;
    public String uploader;
    public String uri;
    public String thumbUri;
    public long duration;
    public long fileSize;
    public String quality;
    public boolean isTranslated;
    public long timestamp;
    public boolean hidden;

    public ArtifactEntity() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactEntity that = (ArtifactEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
