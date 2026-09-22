package io.github.idex.ytrdroid.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Thread-safe, bounded thumbnail loader with in-memory LRU cache and downsampling.
 * Guards against bitmap reuse race conditions in RecyclerViews.
 */
public final class ThumbnailLoader {
    private static final int MAX_CACHE_BYTES = 8 * 1024 * 1024; // 8 MB
    private static final int TARGET_WIDTH = 256;
    private static final int TARGET_HEIGHT = 144;

    private static volatile ThumbnailLoader instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;

    public static ThumbnailLoader getInstance() {
        if (instance == null) {
            synchronized (ThumbnailLoader.class) {
                if (instance == null) {
                    instance = new ThumbnailLoader();
                }
            }
        }
        return instance;
    }

    private ThumbnailLoader() {
        memoryCache = new LruCache<>(MAX_CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        executor = Executors.newFixedThreadPool(2, r -> new Thread(r, "thumb-loader"));
        mainHandler = new Handler(Looper.getMainLooper());
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public void load(String uriOrPath, ImageView target) {
        if (target == null) return;
        if (uriOrPath == null || uriOrPath.trim().isEmpty()) {
            target.setImageDrawable(null);
            target.setTag(null);
            return;
        }

        String key = uriOrPath.trim();
        target.setTag(key);

        Bitmap cached = memoryCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(null);

        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                if (key.startsWith("http://") || key.startsWith("https://")) {
                    bmp = loadNetwork(key);
                } else {
                    bmp = loadLocal(key);
                }
            } catch (Exception ignored) {}

            if (bmp != null) {
                memoryCache.put(key, bmp);
                Bitmap finalBmp = bmp;
                mainHandler.post(() -> {
                    // Guard against RecyclerView view recycling
                    if (key.equals(target.getTag())) {
                        target.setImageBitmap(finalBmp);
                    }
                });
            }
        });
    }

    private Bitmap loadNetwork(String url) throws Exception {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            byte[] bytes = response.body().bytes();
            return decodeSampledBitmap(bytes, TARGET_WIDTH, TARGET_HEIGHT);
        }
    }

    private Bitmap loadLocal(String path) {
        File f = new File(path);
        if (!f.exists()) return null;

        String lower = f.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
            return decodeSampledBitmap(path, TARGET_WIDTH, TARGET_HEIGHT);
        }

        // For video files: extract thumbnail
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return ThumbnailUtils.createVideoThumbnail(f, new Size(TARGET_WIDTH, TARGET_HEIGHT), null);
                } catch (Exception ignored) {}
            }
            return ThumbnailUtils.createVideoThumbnail(path, android.provider.MediaStore.Video.Thumbnails.MINI_KIND);
        }
        return null;
    }

    public static Bitmap decodeSampledBitmap(byte[] data, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    public static Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    public void clearMemory() {
        memoryCache.evictAll();
    }
}
