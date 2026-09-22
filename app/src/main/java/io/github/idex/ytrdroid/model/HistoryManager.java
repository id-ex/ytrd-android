package io.github.idex.ytrdroid.model;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class HistoryManager {
    private static final String TAG = "HistoryManager";
    private static final String FILE_NAME = "history.json";
    private static HistoryManager instance;

    private final Context context;
    private final Gson gson = new Gson();
    private final List<HistoryItem> items = new ArrayList<>();

    private HistoryManager(Context context) {
        this.context = context.getApplicationContext();
        load();
        importExistingDiskFiles();
    }

    public static synchronized HistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryManager(context);
        }
        return instance;
    }

    private synchronized void load() {
        items.clear();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<HistoryItem>>() {}.getType();
            List<HistoryItem> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                items.addAll(loaded);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load history", e);
        }
    }

    private synchronized void save() {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(items, writer);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save history", e);
        }
    }

    public synchronized List<HistoryItem> getAll() {
        // Return sorted by timestamp descending
        List<HistoryItem> copy = new ArrayList<>(items);
        Collections.sort(copy, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        return copy;
    }

    public synchronized void addOrUpdate(HistoryItem item) {
        if (item == null) return;
        // Remove existing item with same id or filePath
        items.removeIf(i -> (item.id != null && item.id.equals(i.id))
                || (item.filePath != null && item.filePath.equals(i.filePath)));
        items.add(0, item);
        save();
    }

    /** Remove record from history only (file remains on disk). */
    public synchronized void remove(String id) {
        if (id == null) return;
        items.removeIf(i -> id.equals(i.id));
        save();
    }

    /** Remove record from history AND delete file from disk. */
    public synchronized void deleteWithFile(HistoryItem item) {
        if (item == null) return;
        if (item.filePath != null) {
            File f = new File(item.filePath);
            if (f.exists()) f.delete();
        }
        if (item.thumbPath != null) {
            File t = new File(item.thumbPath);
            if (t.exists()) t.delete();
        }
        remove(item.id);
    }

    /** Import any existing files from Download/ytrd that are not yet in history. */
    private void importExistingDiskFiles() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "ytrd");
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] diskFiles = dir.listFiles();
        if (diskFiles == null) return;

        boolean changed = false;
        for (File f : diskFiles) {
            if (!f.isFile() || f.getName().startsWith(".")) continue;

            String path = f.getAbsolutePath();
            boolean exists = false;
            for (HistoryItem i : items) {
                if (path.equals(i.filePath)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                HistoryItem item = new HistoryItem();
                item.id = String.valueOf(f.getName().hashCode());
                item.filePath = path;
                item.fileSize = f.length();
                item.timestamp = f.lastModified();

                String name = f.getName();
                int dot = name.lastIndexOf('.');
                String clean = dot > 0 ? name.substring(0, dot) : name;
                item.isTranslated = clean.contains("(RU)");
                item.title = clean.replace(" (RU)", "").replaceAll("_u[0-9a-fA-F]{4}", "…");
                item.quality = dot > 0 ? name.substring(dot + 1).toUpperCase() : "";

                File thumb = new File(dir, "." + name + ".thumb.jpg");
                if (thumb.exists()) item.thumbPath = thumb.getAbsolutePath();

                items.add(item);
                changed = true;
            }
        }
        if (changed) save();
    }
}
