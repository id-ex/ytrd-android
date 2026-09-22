package io.github.idex.ytrdroid.model;

public class DownloadTask {
    public enum State { QUEUED, PAUSED, TRANSLATING, DOWNLOADING, PROCESSING, DONE, ERROR, CANCELLED }

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
