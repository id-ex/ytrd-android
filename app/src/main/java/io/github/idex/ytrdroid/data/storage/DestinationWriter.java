package io.github.idex.ytrdroid.data.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles safe publication of completed media files into destination storage.
 * Guarantees that existing files are never overwritten silently by appending a unique counter suffix.
 */
public final class DestinationWriter {
    private DestinationWriter() {}

    /**
     * Resolves a non-colliding file in the given directory.
     * If baseName.ext exists, tries "baseName (1).ext", "baseName (2).ext", etc.
     */
    public static File resolveUniqueFile(File dir, String baseName, String ext) {
        if (dir == null || baseName == null || ext == null) {
            throw new IllegalArgumentException("Directory, baseName and ext must not be null");
        }
        if (!dir.exists()) dir.mkdirs();

        String cleanExt = ext.startsWith(".") ? ext : "." + ext;
        String sanitized = sanitize(baseName);
        File candidate = new File(dir, sanitized + cleanExt);
        if (!candidate.exists()) {
            return candidate;
        }

        int index = 1;
        while (index < 1000) {
            candidate = new File(dir, sanitized + " (" + index + ")" + cleanExt);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
        // Fallback with timestamp if too many collisions
        return new File(dir, sanitized + "_" + System.currentTimeMillis() + cleanExt);
    }

    /**
     * Atomically copy src to dest (using .part temporary file) and verify size.
     */
    public static void copyAndVerify(File src, File dest) throws IOException {
        if (src == null || !src.exists() || src.length() == 0) {
            throw new IOException("Source file is missing or empty");
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File tempDest = new File(parent, dest.getName() + ".part");
        try {
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(tempDest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }

            if (tempDest.length() != src.length()) {
                throw new IOException("Copied size mismatch: expected " + src.length()
                        + " bytes, got " + tempDest.length());
            }

            if (dest.exists()) dest.delete();
            if (!tempDest.renameTo(dest)) {
                throw new IOException("Failed to rename temporary file to " + dest.getAbsolutePath());
            }
        } finally {
            if (tempDest.exists()) tempDest.delete();
        }
    }

    public static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) return "video";
        String s = name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
        return s.length() > 60 ? s.substring(0, 60).trim() : s;
    }
}
