package io.github.idex.ytrdroid.data.persistence;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Migrates legacy `history.json` into Room `artifacts` table.
 * Idempotent, non-destructive (renames to .bak rather than deleting).
 */
public final class LegacyHistoryMigrator {
    private static final String TAG = "LegacyHistoryMigrator";
    private final Gson gson = new Gson();

    static class LegacyHistoryItem {
        public String id;
        public String url;
        public String title;
        public String uploader;
        public String filePath;
        public String thumbPath;
        public long duration;
        public long timestamp;
        public long fileSize;
        public String quality;
        public boolean isTranslated;
    }

    public boolean migrate(File historyFile, ArtifactDao dao) {
        if (historyFile == null || !historyFile.exists() || !historyFile.isFile()) {
            return false;
        }

        List<LegacyHistoryItem> items;
        try (FileReader reader = new FileReader(historyFile)) {
            Type listType = new TypeToken<List<LegacyHistoryItem>>() {}.getType();
            items = gson.fromJson(reader, listType);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read legacy history file: " + e.getMessage());
            return false;
        }

        if (items == null || items.isEmpty()) {
            backupFile(historyFile);
            return true;
        }

        List<ArtifactEntity> entities = new ArrayList<>();
        for (LegacyHistoryItem item : items) {
            if (item == null) continue;
            // A valid legacy entry has at least an ID, URL or filePath
            if ((item.url == null || item.url.trim().isEmpty()) &&
                (item.filePath == null || item.filePath.trim().isEmpty())) {
                continue;
            }

            ArtifactEntity entity = new ArtifactEntity();
            // Generate a fresh UUID for the artifact to avoid collision with old hashCodes
            entity.id = UUID.randomUUID().toString();
            entity.url = item.url != null ? item.url : "";
            entity.title = item.title != null ? item.title : "Видео";
            entity.uploader = item.uploader;
            entity.uri = item.filePath != null ? item.filePath : "";
            entity.thumbUri = item.thumbPath;
            entity.duration = item.duration;
            entity.fileSize = item.fileSize;
            entity.quality = item.quality;
            entity.isTranslated = item.isTranslated;
            entity.timestamp = item.timestamp > 0 ? item.timestamp : System.currentTimeMillis();
            entity.hidden = false;

            entities.add(entity);
        }

        try {
            dao.upsertAll(entities);
            backupFile(historyFile);
            Log.i(TAG, "Migrated " + entities.size() + " legacy history items to Room database");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write legacy items to database: " + e.getMessage(), e);
            return false;
        }
    }

    private void backupFile(File file) {
        File bak = new File(file.getParentFile(), file.getName() + ".bak");
        if (bak.exists()) bak.delete();
        boolean renamed = file.renameTo(bak);
        if (!renamed) {
            Log.w(TAG, "Could not rename " + file.getName() + " to " + bak.getName());
        }
    }
}
