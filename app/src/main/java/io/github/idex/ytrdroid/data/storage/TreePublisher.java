package io.github.idex.ytrdroid.data.storage;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** Publishes completed local files without passing content URIs to native tools. */
public final class TreePublisher {
    private TreePublisher() {}

    public static String publish(Context context, String tree, File source, Runnable checkCancelled)
            throws IOException {
        Uri root = Uri.parse(tree);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(root,
                DocumentsContract.getTreeDocumentId(root));
        String name = source.getName().toLowerCase(Locale.ROOT);
        String mime = name.endsWith(".mp3") ? "audio/mpeg"
                : name.endsWith(".mkv") ? "video/x-matroska" : "video/mp4";
        checkCancelled.run();
        Uri created = DocumentsContract.createDocument(context.getContentResolver(), parent,
                mime, source.getName());
        if (created == null) throw new IOException("Не удалось создать файл в выбранной папке");
        boolean complete = false;
        try {
            long copied = 0;
            try (FileInputStream input = new FileInputStream(source);
                 OutputStream output = context.getContentResolver().openOutputStream(created, "w")) {
                if (output == null) throw new IOException("Нет доступа для записи в выбранную папку");
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled.run();
                    output.write(buffer, 0, count);
                    copied += count;
                }
                output.flush();
            }
            if (copied == 0 || copied != source.length()) throw new IOException("Файл скопирован не полностью");
            checkCancelled.run();
            complete = true;
            return created.toString();
        } finally {
            if (!complete) {
                try { DocumentsContract.deleteDocument(context.getContentResolver(), created); }
                catch (Exception ignored) { /* Preserve original failure. */ }
            }
        }
    }
}
