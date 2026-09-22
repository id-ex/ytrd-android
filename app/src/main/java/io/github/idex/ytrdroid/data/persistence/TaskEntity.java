package io.github.idex.ytrdroid.data.persistence;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String videoId;
    public String url;
    public String title;
    public String thumbnail;
    public Integer height;
    public String resultType;
    public String container;
    public boolean translate;
    public String voice;
    public String audioMode;
    public String subtitles;
    public String destination;
    public double duration;
    public String language;

    public String state;
    public String executionId;
    public float progress;
    public long downloadedBytes;
    public long totalBytes;
    public float speed;
    public String stageText;
    public String resultReference;
    public String errorMessage;

    public long createdAt;
    public long updatedAt;

    public TaskEntity() {}
}
