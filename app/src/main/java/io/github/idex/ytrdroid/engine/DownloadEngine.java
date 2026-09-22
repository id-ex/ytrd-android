package io.github.idex.ytrdroid.engine;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.VideoInfo;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Wraps youtubedl-android to fetch video info and download.
 */
public class DownloadEngine {
    private static final String TAG = "DownloadEngine";
    private final VotClient vot = new VotClient();
    private volatile Thread currentThread;
    private volatile okhttp3.Call currentHttpCall;
    private volatile Process currentProcess;
    private volatile String currentProcessId;

    public interface Listener {
        void onProgress(DownloadTask task);
        void onStateChanged(DownloadTask task);
    }

    /** Fetch video metadata via yt-dlp --dump-json. */
    public VideoInfo fetchInfo(Context context, String url) throws Exception {
        try {
            return doFetchInfo(url);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            Log.w(TAG, "fetchInfo failed: " + msg + ". Trying to update yt-dlp...");
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context.getApplicationContext(),
                        YoutubeDL.UpdateChannel._NIGHTLY);
                return doFetchInfo(url);
            } catch (Exception updateEx) {
                Log.w(TAG, "Update retry failed: " + updateEx.getMessage());
            }
            throw e;
        }
    }

    private VideoInfo doFetchInfo(String url) throws Exception {
        YoutubeDLRequest req = new YoutubeDLRequest(url);
        req.addOption("--dump-json");
        req.addOption("--no-download");
        req.addOption("-f", "bv+ba/b");

        com.yausername.youtubedl_android.YoutubeDLResponse resp =
                YoutubeDL.getInstance().execute(req, null, null);

        String json = resp.getOut();
        VideoInfo info = new VideoInfo();
        info.url = url;

        // Parse JSON with Gson to properly decode all Unicode characters, quotes, etc.
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            info.title = getJsonString(obj, "title");
            info.uploader = getJsonString(obj, "uploader");
            info.thumbnail = getJsonString(obj, "thumbnail");
            info.language = getJsonString(obj, "language");
            info.ext = getJsonString(obj, "ext");
            if (info.ext == null) info.ext = "mp4";

            if (obj.has("duration") && !obj.get("duration").isJsonNull()) {
                info.duration = obj.get("duration").getAsLong();
            }

            // Parse formats for heights
            info.qualities = new ArrayList<>();
            if (obj.has("formats") && obj.get("formats").isJsonArray()) {
                JsonArray formats = obj.getAsJsonArray("formats");
                java.util.Set<Integer> heights = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
                for (JsonElement fElem : formats) {
                    if (!fElem.isJsonObject()) continue;
                    JsonObject fObj = fElem.getAsJsonObject();
                    if (fObj.has("height") && !fObj.get("height").isJsonNull()) {
                        int h = fObj.get("height").getAsInt();
                        if (h >= 144) heights.add(h);
                    }
                }
                for (int h : heights) info.qualities.add(String.valueOf(h));
            }
        } catch (Exception e) {
            Log.w(TAG, "Gson parse failed, falling back to manual parse: " + e.getMessage());
            info.title = jsonString(json, "title");
            info.uploader = jsonString(json, "uploader");
            info.thumbnail = jsonString(json, "thumbnail");
            info.language = jsonString(json, "language");
            info.ext = jsonString(json, "ext");
            if (info.ext == null) info.ext = "mp4";
            info.qualities = parseQualities(json);
        }

        if (info.qualities == null || info.qualities.isEmpty()) {
            info.qualities = new ArrayList<>();
            info.qualities.add("auto");
        }
        return info;
    }

    private static String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /** Run the full download pipeline for a task. */
    public void execute(Context context, DownloadTask task, Listener listener) {
        currentThread = Thread.currentThread();
        try {
            // 1. Translation
            File translationAudio = new File(context.getCacheDir(), "vot_audio_" + task.id + ".mp3");
            boolean alreadyHasAudio = translationAudio.exists() && translationAudio.length() > 1000;

            if (task.translate && !alreadyHasAudio) {
                task.state = DownloadTask.State.TRANSLATING;
                task.stageText = "Проверка наличия перевода…";
                if (listener != null) listener.onStateChanged(task);

                String lang = VotClient.normalizeLang(task.language);
                double dur = task.duration > 0 ? task.duration : 341.0;
                Log.i(TAG, "Requesting VOT: url=" + task.url + ", duration=" + dur + ", lang=" + lang + ", live=" + task.liveVoice);

                VotClient.TranslationResult tr = vot.getTranslationAudio(
                        task.url, dur, task.liveVoice, lang,
                        (current, total) -> {
                            task.stageText = "Ожидание перевода… (попытка " + current + "/" + total + ")";
                            if (listener != null) listener.onStateChanged(task);
                        });

                if (tr.success && "Ready".equals(tr.status) && tr.audioUrl != null) {
                    task.translationAudioUrl = tr.audioUrl;
                    task.stageText = "Перевод найден";
                    if (listener != null) listener.onStateChanged(task);
                    Log.i(TAG, "VOT ready: " + tr.audioUrl);
                } else {
                    String msg = tr.message != null ? tr.message : "Перевод ещё готовится или недоступен";
                    Log.w(TAG, "VOT failed: " + msg + " (status=" + tr.status + ")");
                    throw new Exception("Перевод недоступен: " + msg);
                }
            }

            boolean isAudio = "audio".equals(task.quality) || "mp3".equals(task.ext);

            // 2. Download
            task.state = DownloadTask.State.DOWNLOADING;
            task.stageText = isAudio ? "Скачивание аудио…" : "Скачивание видео…";
            if (listener != null) listener.onStateChanged(task);

            File outDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "ytrd");
            outDir.mkdirs();

            String processId = "ytrd-" + task.id;

            // Audio-only with translation: download directly
            if (isAudio && task.translate && task.translationAudioUrl != null) {
                task.stageText = "Скачивание перевода…";
                if (listener != null) listener.onStateChanged(task);
                String outName = sanitize(task.title != null ? task.title : "audio") + " (RU).mp3";
                File finalFile = new File(outDir, outName);
                downloadFileWithProgress(task.translationAudioUrl, finalFile, task, listener);
                task.outputPath = finalFile.getAbsolutePath();
                task.progress = 100;
                task.state = DownloadTask.State.DONE;
                task.stageText = "Готово";

                saveToHistory(context, task);
                if (listener != null) listener.onStateChanged(task);
                return;
            }

            YoutubeDLRequest req = new YoutubeDLRequest(task.url);

            if (isAudio) {
                req.addOption("-x");
                req.addOption("--audio-format", "mp3");
            } else {
                if (task.quality != null && !"auto".equals(task.quality)) {
                    String fmt = "bv[height<=" + task.quality + "]+ba/b[height<=" + task.quality + "]/b";
                    req.addOption("-f", fmt);
                } else {
                    req.addOption("-f", "bv+ba/b");
                }
                req.addOption("--merge-output-format", "mp4");
            }

            if (task.subtitles) {
                req.addOption("--write-subs");
                req.addOption("--sub-langs", "ru,en");
                req.addOption("--embed-subs");
            }

            // Download thumbnail for video if available
            String baseName = sanitize(task.title != null ? task.title : "video");
            if (task.translate) baseName += " (RU)";
            File thumbDest = new File(outDir, "." + baseName + ".mp4.thumb.jpg");
            if (task.thumbnail != null && !task.thumbnail.isEmpty()) {
                try {
                    downloadFile(task.thumbnail, thumbDest);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to save thumbnail: " + e.getMessage());
                }
            }

            if (task.translate && !alreadyHasAudio && task.translationAudioUrl != null) {
                task.stageText = "Скачивание перевода…";
                if (listener != null) listener.onStateChanged(task);
                downloadFileWithProgress(task.translationAudioUrl, translationAudio, task, listener);
            }

            // Reset bytes for video download
            task.totalBytes = 0;
            task.downloadedBytes = 0;
            task.speed = 0;

            // If we have translation audio, we download video to temp file, then merge with ffmpeg
            if (translationAudio.exists() && translationAudio.length() > 1000) {
                task.stageText = "Скачивание видео…";
                if (listener != null) listener.onStateChanged(task);

                File tempVideo = new File(context.getCacheDir(), "temp_video_" + task.id + ".mp4");
                req.addOption("-o", tempVideo.getAbsolutePath());

                currentProcessId = processId;
                try {
                    YoutubeDL.getInstance().execute(req, processId,
                            (Function3<Float, Long, String, Unit>) (progress, etaInSeconds, line) -> {
                                task.progress = progress;
                                parseYtDlpLine(line, task);
                                task.stageText = "Скачивание видео…";
                                if (listener != null) listener.onProgress(task);
                                return Unit.INSTANCE;
                            });
                } finally {
                    currentProcessId = null;
                }

                // 3. Process with ffmpeg
                task.state = DownloadTask.State.PROCESSING;
                task.stageText = "Сборка видео…";
                task.progress = 0;
                if (listener != null) listener.onStateChanged(task);

                File finalFile = new File(outDir, baseName + ".mp4");

                File subFile = null;
                if (task.subtitles) {
                    subFile = findSubtitleFile(context.getCacheDir(), "temp_video_" + task.id);
                }

                List<String> ffArgs = new ArrayList<>();
                ffArgs.add("-y");
                ffArgs.add("-i"); ffArgs.add(tempVideo.getAbsolutePath());
                ffArgs.add("-i"); ffArgs.add(translationAudio.getAbsolutePath());
                if (subFile != null) {
                    ffArgs.add("-i"); ffArgs.add(subFile.getAbsolutePath());
                }

                if ("dual".equals(task.audioMode)) {
                    ffArgs.add("-map"); ffArgs.add("0:v");
                    ffArgs.add("-map"); ffArgs.add("0:a");
                    ffArgs.add("-map"); ffArgs.add("1:a");
                    ffArgs.add("-c:v"); ffArgs.add("copy");
                    ffArgs.add("-c:a"); ffArgs.add("copy");
                    ffArgs.add("-metadata:s:a:0"); ffArgs.add("title=Оригинал");
                    ffArgs.add("-metadata:s:a:0"); ffArgs.add("language=eng");
                    ffArgs.add("-metadata:s:a:1"); ffArgs.add("title=Перевод");
                    ffArgs.add("-metadata:s:a:1"); ffArgs.add("language=rus");
                } else {
                    // mix mode
                    ffArgs.add("-filter_complex");
                    ffArgs.add("[0:a]volume=0.2[orig];[1:a]volume=1.2[dub];[orig][dub]amix=inputs=2:duration=shortest[out]");
                    ffArgs.add("-map"); ffArgs.add("0:v");
                    ffArgs.add("-map"); ffArgs.add("[out]");
                    ffArgs.add("-c:v"); ffArgs.add("copy");
                    ffArgs.add("-c:a"); ffArgs.add("aac");
                    ffArgs.add("-b:a"); ffArgs.add("128k");
                }

                if (subFile != null) {
                    ffArgs.add("-c:s"); ffArgs.add("mov_text");
                    ffArgs.add("-metadata:s:s:0");
                    ffArgs.add("language=" + (subFile.getName().contains(".ru") ? "rus" : "eng"));
                }

                ffArgs.add("-progress");
                ffArgs.add("pipe:1");
                ffArgs.add(finalFile.getAbsolutePath());

                File noBackup = context.getNoBackupFilesDir();
                File ffmpegLib = new File(noBackup, "youtubedl-android/packages/ffmpeg/usr/lib");
                File pythonLib = new File(noBackup, "youtubedl-android/packages/python/usr/lib");
                String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
                String ldPath = nativeLibDir + ":" + ffmpegLib.getAbsolutePath() + ":" + pythonLib.getAbsolutePath();

                String ffmpegPath = nativeLibDir + "/libffmpeg.so";
                List<String> cmd = new ArrayList<>();
                cmd.add(ffmpegPath);
                cmd.addAll(ffArgs);

                Log.i(TAG, "Running ffmpeg: " + cmd);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.environment().put("LD_LIBRARY_PATH", ldPath);
                pb.environment().put("PATH", System.getenv("PATH") + ":" + nativeLibDir);
                pb.redirectErrorStream(true);
                currentProcess = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("out_time_us=")) {
                            try {
                                long us = Long.parseLong(line.substring(12).trim());
                                double sec = us / 1000000.0;
                                if (task.duration > 0) {
                                    float p = (float) ((sec / task.duration) * 100.0);
                                    task.progress = Math.min(100f, Math.max(0f, p));
                                    if (listener != null) listener.onProgress(task);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                int exitCode = currentProcess.waitFor();
                currentProcess = null;
                Log.i(TAG, "ffmpeg exit code: " + exitCode);
                if (exitCode != 0) {
                    throw new Exception("FFmpeg failed with exit code " + exitCode);
                }

                tempVideo.delete();
                translationAudio.delete();
                if (subFile != null) subFile.delete();
                task.outputPath = finalFile.getAbsolutePath();
            } else {
                // Simple download without translation
                task.stageText = "Скачивание видео…";
                File finalFile = new File(outDir, baseName + (isAudio ? ".mp3" : ".mp4"));
                req.addOption("-o", finalFile.getAbsolutePath());

                currentProcessId = processId;
                try {
                    YoutubeDL.getInstance().execute(req, processId,
                            (Function3<Float, Long, String, Unit>) (progress, etaInSeconds, line) -> {
                                task.progress = progress;
                                parseYtDlpLine(line, task);
                                if (listener != null) listener.onProgress(task);
                                return Unit.INSTANCE;
                            });
                } finally {
                    currentProcessId = null;
                }
                task.outputPath = finalFile.getAbsolutePath();
            }

            task.progress = 100;
            task.state = DownloadTask.State.DONE;
            task.stageText = "Готово";
            saveToHistory(context, task);
            if (listener != null) listener.onStateChanged(task);

        } catch (Exception e) {
            if (task.state == DownloadTask.State.PAUSED || task.state == DownloadTask.State.CANCELLED) {
                Log.i(TAG, "Task was paused or cancelled, ignoring exception: " + e.getMessage());
                return;
            }
            Log.e(TAG, "Download failed", e);
            task.state = DownloadTask.State.ERROR;
            task.errorMessage = e.getMessage();
            if (listener != null) listener.onStateChanged(task);
        }
    }

    private File findSubtitleFile(File dir, String prefix) {
        if (!dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        File ruSub = null, anySub = null;
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.startsWith(prefix.toLowerCase()) && (name.endsWith(".vtt") || name.endsWith(".srt"))) {
                if (name.contains(".ru.")) return f;
                anySub = f;
            }
        }
        return ruSub != null ? ruSub : anySub;
    }

    private void parseYtDlpLine(String line, DownloadTask task) {
        if (line == null) return;
        try {
            int ofIdx = line.indexOf(" of ");
            if (ofIdx >= 0) {
                int atIdx = line.indexOf(" at ", ofIdx);
                String sizeStr = (atIdx > ofIdx) ? line.substring(ofIdx + 4, atIdx).trim() : line.substring(ofIdx + 4).trim();
                sizeStr = sizeStr.replace("~", "").trim();
                long total = parseBytesString(sizeStr);
                if (total > 0) {
                    task.totalBytes = total;
                    if (task.progress >= 0) {
                        task.downloadedBytes = (long) (total * (task.progress / 100.0f));
                    }
                }
                if (atIdx > 0) {
                    int etaIdx = line.indexOf(" ETA ", atIdx);
                    String speedStr = (etaIdx > atIdx) ? line.substring(atIdx + 4, etaIdx).trim() : line.substring(atIdx + 4).trim();
                    long spd = parseBytesString(speedStr.replace("/s", ""));
                    if (spd > 0) task.speed = spd;
                }
            }
        } catch (Exception ignored) {}
    }

    private long parseBytesString(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            double num = Double.parseDouble(s.replaceAll("[^0-9.]", ""));
            String upper = s.toUpperCase();
            if (upper.contains("GIB") || upper.contains("GB")) return (long) (num * 1024 * 1024 * 1024);
            if (upper.contains("MIB") || upper.contains("MB")) return (long) (num * 1024 * 1024);
            if (upper.contains("KIB") || upper.contains("KB")) return (long) (num * 1024);
            return (long) num;
        } catch (Exception e) {
            return 0;
        }
    }

    public void cancel(DownloadTask task) {
        cancel(task, false);
    }

    public void cancel(DownloadTask task, boolean isPause) {
        task.state = isPause ? DownloadTask.State.PAUSED : DownloadTask.State.CANCELLED;
        if (currentHttpCall != null) {
            try { currentHttpCall.cancel(); } catch (Exception ignored) {}
        }
        if (currentProcess != null) {
            try { currentProcess.destroyForcibly(); } catch (Exception ignored) {}
        }
        if (currentProcessId != null) {
            try { YoutubeDL.getInstance().destroyProcessById(currentProcessId); } catch (Exception ignored) {}
        }
        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    private void saveToHistory(Context context, DownloadTask task) {
        try {
            io.github.idex.ytrdroid.model.HistoryItem item = new io.github.idex.ytrdroid.model.HistoryItem();
            item.id = String.valueOf(task.url.hashCode());
            item.url = task.url;
            item.title = task.title;
            item.filePath = task.outputPath;
            if (task.outputPath != null) {
                File f = new File(task.outputPath);
                item.fileSize = f.length();
                File thumb = new File(f.getParentFile(), "." + f.getName() + ".thumb.jpg");
                if (thumb.exists()) item.thumbPath = thumb.getAbsolutePath();
            }
            item.timestamp = System.currentTimeMillis();
            item.quality = task.quality;
            item.isTranslated = task.translate;
            io.github.idex.ytrdroid.model.HistoryManager.getInstance(context).addOrUpdate(item);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save to history: " + e.getMessage());
        }
    }

    // ── helpers ──

    private void downloadFileWithProgress(String url, File dest, DownloadTask task, Listener listener) throws Exception {
        OkHttpClient http = new OkHttpClient();
        Request req = new Request.Builder().url(url).build();
        currentHttpCall = http.newCall(req);
        try (Response resp = currentHttpCall.execute()) {
            if (!resp.isSuccessful()) throw new Exception("HTTP " + resp.code());
            long total = resp.body() != null ? resp.body().contentLength() : -1;
            task.totalBytes = total;
            task.downloadedBytes = 0;
            long startTime = System.currentTimeMillis();

            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int n;
                long lastUpdate = 0;
                while ((n = in.read(buf)) != -1) {
                    if (task.state == DownloadTask.State.PAUSED || task.state == DownloadTask.State.CANCELLED) {
                        return;
                    }
                    out.write(buf, 0, n);
                    task.downloadedBytes += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 150) {
                        lastUpdate = now;
                        if (total > 0) {
                            task.progress = (task.downloadedBytes * 100.0f) / total;
                        }
                        long elapsed = now - startTime;
                        if (elapsed > 0) {
                            task.speed = (task.downloadedBytes * 1000.0f) / elapsed;
                        }
                        if (listener != null) listener.onProgress(task);
                    }
                }
                task.progress = 100;
                if (listener != null) listener.onProgress(task);
            }
        } finally {
            currentHttpCall = null;
        }
    }

    private void downloadFile(String url, File dest) throws Exception {
        OkHttpClient http = new OkHttpClient();
        Request req = new Request.Builder().url(url).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new Exception("HTTP " + resp.code());
            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String jsonString(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        if (i >= json.length()) return null;
        if (json.charAt(i) == '"') {
            int end = json.indexOf('"', i + 1);
            if (end < 0) return null;
            return json.substring(i + 1, end);
        }
        // number
        int end = i;
        while (end < json.length() && "0123456789.".indexOf(json.charAt(end)) >= 0) end++;
        return json.substring(i, end);
    }

    private List<String> parseQualities(String json) {
        List<String> qualities = new ArrayList<>();
        // Look for height values in formats
        int idx = 0;
        java.util.Set<Integer> seen = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        while ((idx = json.indexOf("\"height\":", idx)) >= 0) {
            idx += 9;
            while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == '\t')) {
                idx++;
            }
            int end = idx;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            if (end > idx) {
                try {
                    int h = Integer.parseInt(json.substring(idx, end));
                    if (h >= 144) seen.add(h);
                } catch (NumberFormatException ignored) {}
            }
        }
        for (int h : seen) qualities.add(String.valueOf(h));
        if (qualities.isEmpty()) qualities.add("auto");
        return qualities;
    }
}
