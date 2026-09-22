package io.github.idex.ytrdroid.data.storage;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Manages isolated per-task and per-execution workspaces under filesDir/work/<taskId>/.
 * Persistent across pause/resume cycles so yt-dlp and HTTP downloads can resume from the exact byte.
 */
public final class WorkspaceManager {
    private final File baseDir;
    private final UUID taskId;
    private final UUID executionId;
    private final File executionDir;

    public WorkspaceManager(File filesDir, UUID taskId, UUID executionId) {
        if (filesDir == null || taskId == null || executionId == null) {
            throw new IllegalArgumentException("filesDir, taskId, and executionId must not be null");
        }
        this.baseDir = new File(filesDir, "work/" + taskId);
        this.taskId = taskId;
        this.executionId = executionId;
        this.executionDir = new File(baseDir, executionId.toString());
    }

    public File getTaskDir() {
        return baseDir;
    }

    public File ensureTaskDir() {
        if (!baseDir.exists()) baseDir.mkdirs();
        return baseDir;
    }

    public File getExecutionDir() {
        return executionDir;
    }

    public File ensureExecutionDir() {
        if (!executionDir.exists()) executionDir.mkdirs();
        return executionDir;
    }

    public File getTempVideoFile() {
        ensureTaskDir();
        return new File(baseDir, "temp_video.part");
    }

    public File getTempAudioFile() {
        ensureTaskDir();
        return new File(baseDir, "vot_audio.part");
    }

    /** Persistent across pause/resume of the same task. */
    public File getCompleteAudioFile() {
        ensureTaskDir();
        return new File(baseDir, "vot_audio.mp3");
    }

    /** Persistent across pause/resume of the same task. yt-dlp uses .part alongside it. */
    public File getCompleteVideoFile() {
        ensureTaskDir();
        return new File(baseDir, "temp_video.mp4");
    }

    public File getSubtitleFile(String lang) {
        ensureTaskDir();
        return new File(baseDir, "subtitles." + (lang != null ? lang : "und") + ".srt");
    }

    public boolean isAudioComplete() {
        File f = getCompleteAudioFile();
        File marker = new File(baseDir, ".audio_complete");
        return f.exists() && f.length() > 0 && marker.exists();
    }

    public void markAudioComplete() throws IOException {
        ensureTaskDir();
        File marker = new File(baseDir, ".audio_complete");
        if (!marker.exists()) marker.createNewFile();
    }

    public boolean isVideoComplete() {
        File f = getCompleteVideoFile();
        File marker = new File(baseDir, ".video_complete");
        return f.exists() && f.length() > 0 && marker.exists();
    }

    public void markVideoComplete() throws IOException {
        ensureTaskDir();
        File marker = new File(baseDir, ".video_complete");
        if (!marker.exists()) marker.createNewFile();
    }

    /** Clean up current execution directory. */
    public void cleanupExecution() {
        deleteRecursively(executionDir);
    }

    /** Clean up entire task workspace including all historical executions and partial files. */
    public void cleanupTask() {
        deleteRecursively(baseDir);
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}
