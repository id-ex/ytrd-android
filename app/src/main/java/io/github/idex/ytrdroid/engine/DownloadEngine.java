package io.github.idex.ytrdroid.engine;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

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
    private volatile boolean cancelled;
    private volatile Thread currentThread;

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Execution cancelled");
        }
    }
    private volatile okhttp3.Call currentHttpCall;
    private volatile Process currentProcess;
    private volatile String currentProcessId;

    public interface Listener {
        void onProgress(DownloadTask task);
        void onStateChanged(DownloadTask task);
    }

    /** Fetch video metadata via yt-dlp --dump-json. */
    public VideoInfo fetchInfo(Context context, String url) throws Exception {
        return doFetchInfo(url);
    }

    private VideoInfo doFetchInfo(String url) throws Exception {
        YoutubeDLRequest req = new YoutubeDLRequest(url);
        req.addOption("--dump-json");
        req.addOption("--no-download");
        req.addOption("-f", "bv+ba/b");

        com.yausername.youtubedl_android.YoutubeDLResponse resp =
                YoutubeDL.getInstance().execute(req, null, null);

        return io.github.idex.ytrdroid.data.ytdlp.YtDlpMetadataParser.parse(resp.getOut(), url);
    }

    /** Run the full download pipeline for a task. */
    public void execute(Context context, DownloadTask task, Listener listener) {
        currentThread = Thread.currentThread();
        try {
            checkCancelled();
            io.github.idex.ytrdroid.data.storage.WorkspaceManager workspace =
                    new io.github.idex.ytrdroid.data.storage.WorkspaceManager(
                            context.getFilesDir(), task.getRequest().id,
                            task.executionId != null ? task.executionId : java.util.UUID.randomUUID());

            // 1. Translation
            File translationAudio = workspace.getCompleteAudioFile();
            boolean alreadyHasAudio = workspace.isAudioComplete();

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

            String destination = task.destinationPath;
            boolean documentTree = destination != null && destination.startsWith("content://");
            if (documentTree) {
                boolean granted = false;
                for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().toString().equals(destination) && permission.isWritePermission()) {
                        granted = true;
                        break;
                    }
                }
                if (!granted) throw new java.io.IOException("Доступ к папке отозван. Выберите папку заново.");
            }
            File outDir = documentTree ? new File(workspace.ensureTaskDir(), "publish")
                    : destination != null && !destination.isEmpty() ? new File(destination)
                    : new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "ytrd");
            if (!outDir.isDirectory() && !outDir.mkdirs()) {
                throw new java.io.IOException("Нет доступа к папке сохранения. Выберите папку заново.");
            }

            String baseName = io.github.idex.ytrdroid.data.storage.DestinationWriter.sanitize(
                    task.title != null ? task.title : (isAudio ? "audio" : "video"));
            if (task.translate) baseName += " (RU)";

            String processId = "ytrd-" + task.executionId;

            // Audio-only with translation: download directly
            if (isAudio && task.translate && task.translationAudioUrl != null) {
                task.stageText = "Скачивание перевода…";
                if (listener != null) listener.onStateChanged(task);
                File finalFile = io.github.idex.ytrdroid.data.storage.DestinationWriter.resolveUniqueFile(
                        outDir, baseName, "mp3");
                downloadFileWithProgress(task.translationAudioUrl, finalFile, task, listener);
                checkCancelled();
                task.outputPath = documentTree
                        ? io.github.idex.ytrdroid.data.storage.TreePublisher.publish(context, destination, finalFile, this::checkCancelled)
                        : finalFile.getAbsolutePath();
                if (documentTree) finalFile.delete();
                task.progress = 100;
                task.state = DownloadTask.State.DONE;
                task.stageText = "Готово";

                saveToHistory(context, task);
                if (listener != null) listener.onStateChanged(task);
                workspace.cleanupExecution();
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
                req.addOption("--write-auto-subs");
                req.addOption("--sub-langs", "ru,en");
                req.addOption("--embed-subs");
                // --write-subs retains sidecar files for the subsequent translation merge.
            }

            // Download thumbnail for video if available
            File thumbDir = new File(context.getFilesDir(), "thumbnails");
            if (!thumbDir.isDirectory()) thumbDir.mkdirs();
            File thumbDest = new File(thumbDir, task.getRequest().id + ".jpg");
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
                workspace.markAudioComplete();
            }

            // Reset bytes for video download
            task.totalBytes = 0;
            task.downloadedBytes = 0;
            task.speed = 0;

            // If we have translation audio, we download video to temp file, then merge with ffmpeg
            if (translationAudio.exists() && translationAudio.length() > 0) {
                task.stageText = "Скачивание видео…";
                if (listener != null) listener.onStateChanged(task);

                File tempVideo = workspace.getCompleteVideoFile();
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

                File finalFile = io.github.idex.ytrdroid.data.storage.DestinationWriter.resolveUniqueFile(
                        outDir, baseName, task.ext != null ? task.ext : "mp4");

                File subFile = null;
                if (task.subtitles) {
                    subFile = findSubtitleFile(workspace.getTaskDir(), "temp_video");
                }

                List<String> ffArgs;
                if ("dual".equals(task.audioMode)) {
                    ffArgs = io.github.idex.ytrdroid.data.ffmpeg.FfmpegCommandBuilder.buildDual(
                            tempVideo, translationAudio, subFile, task.language, task.ext, finalFile);
                } else {
                    ffArgs = io.github.idex.ytrdroid.data.ffmpeg.FfmpegCommandBuilder.buildMix(
                            tempVideo, translationAudio, subFile, task.language, task.ext, finalFile);
                }

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
                checkCancelled();
                currentProcess = pb.start();
                checkCancelled();
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

                workspace.cleanupExecution();
                task.outputPath = finalFile.getAbsolutePath();
            } else {
                // Audio extraction produces an mp3, not the original mp4 template.
                task.stageText = isAudio ? "Скачивание аудио…" : "Скачивание видео…";
                File tempVideo = isAudio ? new File(workspace.ensureTaskDir(), "original.mp3")
                        : workspace.getCompleteVideoFile();
                req.addOption("-o", tempVideo.getAbsolutePath());

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

                File finalFile = io.github.idex.ytrdroid.data.storage.DestinationWriter.resolveUniqueFile(
                        outDir, baseName, isAudio ? "mp3" : (task.ext != null ? task.ext : "mp4"));
                io.github.idex.ytrdroid.data.storage.DestinationWriter.copyAndVerify(tempVideo, finalFile);
                workspace.cleanupExecution();
                task.outputPath = finalFile.getAbsolutePath();
            }

            checkCancelled();
            if (documentTree) {
                task.stageText = "Сохранение в выбранную папку…";
                if (listener != null) listener.onStateChanged(task);
                File localResult = new File(task.outputPath);
                task.outputPath = io.github.idex.ytrdroid.data.storage.TreePublisher.publish(
                        context, destination, localResult, this::checkCancelled);
                localResult.delete();
            }
            task.progress = 100;
            task.state = DownloadTask.State.DONE;
            task.stageText = "Готово";
            saveToHistory(context, task);
            if (listener != null) listener.onStateChanged(task);

        } catch (Exception e) {
            if (cancelled || e instanceof java.util.concurrent.CancellationException) {
                Log.i(TAG, "Task was paused or cancelled, ignoring exception: " + e.getMessage());
                return;
            }
            Log.e(TAG, "Download failed", e);
            task.state = DownloadTask.State.ERROR;
            task.errorMessage = e.getMessage();
            if (listener != null) listener.onStateChanged(task);
        } finally {
            currentThread = null;
            currentHttpCall = null;
            currentProcessId = null;
            Process process = currentProcess;
            if (process != null) {
                process.destroyForcibly();
                // Acknowledge completion only after the native process terminates.
                boolean interrupted = Thread.interrupted();
                while (true) {
                    try { process.waitFor(); break; }
                    catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            currentProcess = null;
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
        cancelled = true;
        vot.cancel();
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
        checkCancelled();
        try {
            io.github.idex.ytrdroid.data.persistence.ArtifactEntity entity =
                    new io.github.idex.ytrdroid.data.persistence.ArtifactEntity();
            entity.id = java.util.UUID.randomUUID().toString();
            entity.taskId = task.executionId != null ? task.executionId.toString() : null;
            entity.url = task.url != null ? task.url : "";
            entity.title = task.title != null ? task.title : "Видео";
            entity.uri = task.outputPath != null ? task.outputPath : "";
            if (task.outputPath != null && task.outputPath.startsWith("content://")) {
                try (android.database.Cursor cursor = context.getContentResolver().query(
                        android.net.Uri.parse(task.outputPath),
                        new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) entity.fileSize = cursor.getLong(0);
                }
                entity.thumbUri = task.thumbnail;
            } else if (task.outputPath != null) {
                File f = new File(task.outputPath);
                entity.fileSize = f.length();
                File thumb = new File(f.getParentFile(), "." + f.getName() + ".thumb.jpg");
                if (thumb.exists()) entity.thumbUri = thumb.getAbsolutePath();
            }
            File savedThumb = new File(context.getFilesDir(), "thumbnails/" + task.getRequest().id + ".jpg");
            if (savedThumb.isFile() && savedThumb.length() > 0) entity.thumbUri = savedThumb.getAbsolutePath();
            else if (task.thumbnail != null && !task.thumbnail.isEmpty()) entity.thumbUri = task.thumbnail;
            entity.timestamp = System.currentTimeMillis();
            entity.quality = task.quality;
            entity.isTranslated = task.translate;
            entity.hidden = false;
            io.github.idex.ytrdroid.data.persistence.AppDatabase.getInstance(context)
                    .artifactDao().upsert(entity);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save to database: " + e.getMessage());
        }
    }

    // ── helpers ──

    private void downloadFileWithProgress(String url, File dest, DownloadTask task, Listener listener) throws Exception {
        OkHttpClient http = new OkHttpClient();
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File partFile = new File(parent, dest.getName() + ".part");
        long existing = partFile.exists() ? partFile.length() : 0;

        Request.Builder reqBuilder = new Request.Builder().url(url);
        if (existing > 0) {
            reqBuilder.header("Range", "bytes=" + existing + "-");
        }
        checkCancelled();
        currentHttpCall = http.newCall(reqBuilder.build());
        if (cancelled) currentHttpCall.cancel();
        try (Response resp = currentHttpCall.execute()) {
            int code = resp.code();
            boolean append = false;
            long total;
            if (code == 206) {
                append = true;
                long len = resp.body() != null ? resp.body().contentLength() : -1;
                total = len > 0 ? existing + len : -1;
            } else if (resp.isSuccessful()) {
                append = false;
                existing = 0;
                total = resp.body() != null ? resp.body().contentLength() : -1;
            } else {
                throw new Exception("HTTP " + code);
            }

            task.totalBytes = total;
            task.downloadedBytes = existing;
            long startTime = System.currentTimeMillis();

            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(partFile, append)) {
                byte[] buf = new byte[16384];
                int n;
                long lastUpdate = 0;
                while ((n = in.read(buf)) != -1) {
                    checkCancelled();
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
                            task.speed = ((task.downloadedBytes - existing) * 1000.0f) / elapsed;
                        }
                        if (listener != null) listener.onProgress(task);
                    }
                }
                out.flush();
                checkCancelled();
            }

            if (dest.exists()) dest.delete();
            if (!partFile.renameTo(dest)) {
                throw new java.io.IOException("Failed to rename partial file to " + dest.getAbsolutePath());
            }
            task.progress = 100;
            if (listener != null) listener.onProgress(task);
        } finally {
            currentHttpCall = null;
        }
    }

    private void downloadFile(String url, File dest) throws Exception {
        OkHttpClient http = new OkHttpClient();
        Request req = new Request.Builder().url(url).build();
        checkCancelled();
        currentHttpCall = http.newCall(req);
        if (cancelled) currentHttpCall.cancel();
        try (Response resp = currentHttpCall.execute()) {
            if (!resp.isSuccessful()) throw new Exception("HTTP " + resp.code());
            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    checkCancelled();
                    out.write(buf, 0, n);
                }
            }
            checkCancelled();
        } finally {
            currentHttpCall = null;
        }
    }
}
