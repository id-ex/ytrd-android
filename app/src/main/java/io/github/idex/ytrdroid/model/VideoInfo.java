package io.github.idex.ytrdroid.model;

import java.util.List;

public class VideoInfo {
    public String url;
    public String title;
    public String uploader;
    public String thumbnail;
    public long duration;        // seconds
    public String language;      // original language code
    public List<String> qualities; // e.g. ["2160","1080","720","480","360"]
    public String ext;           // "mp4" or "mkv"
}
