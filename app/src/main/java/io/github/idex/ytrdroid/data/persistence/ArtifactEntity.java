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
}
