package io.github.idex.ytrdroid.model;

public class DownloadTask {
    public enum State { QUEUED, PAUSING, PAUSED, ANALYZING, TRANSLATING, DOWNLOADING, PROCESSING, CANCELLING, DONE, ERROR, CANCELLED, INTERRUPTED }

    private io.github.idex.ytrdroid.domain.model.DownloadRequest request;
    public java.util.UUID executionId;
    public String destinationPath;

    public void bindRequest(io.github.idex.ytrdroid.domain.model.DownloadRequest value) {
        if (value == null || request != null) throw new IllegalStateException("Request already bound or missing");
        request = value;
    }

    public io.github.idex.ytrdroid.domain.model.DownloadRequest getRequest() {
        if (request == null) throw new IllegalStateException("Task has not been enqueued");
        return request;
    }

    public long id;
    public String url;
    public String title;
    public String thumbnail;
    public String quality;       // "1080", "720", "audio"
    public String ext;           // "mp4", "mkv", "mp3"
    public boolean translate;
    public boolean liveVoice;
    public String audioMode;     // "mix", "dual"
    public boolean subtitles;
    public double duration;
    public String language;
    public String outputPath;

    public State state = State.QUEUED;
    public float progress;       // 0..100
    public long downloadedBytes;
    public long totalBytes;
    public float speed;          // bytes/sec
    public String stageText;
    public String errorMessage;
    public String translationAudioUrl;

    public String formatProgress() {
        if (progress < 0) return "0%";
        return String.format("%.0f%%", progress);
    }

    public String formatSize() {
        if (totalBytes <= 0) return "";
        return formatMB(downloadedBytes) + " / " + formatMB(totalBytes) + " МБ";
    }

    public String formatSpeed() {
        if (speed <= 0) return "";
        return String.format("%.1f МБ/с", speed / (1024 * 1024));
    }

    private String formatMB(long bytes) {
        return String.format("%.0f", bytes / (1024.0 * 1024.0));
    }
}
