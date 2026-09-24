package io.github.idex.ytrdroid.engine;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.github.idex.ytrdroid.engine.VotProtobufCodec.*;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Yandex Voice-Over Translation API client.
 * Port of ytrd/vot.py to Java.
 */
public class VotClient {
    private static final String TAG = "VotClient";
    private static final String API_URL = "https://api.browser.yandex.ru/video-translation/translate";
    private static final byte[] HMAC_KEY = io.github.idex.ytrdroid.BuildConfig.VOT_HMAC_KEY.getBytes(StandardCharsets.UTF_8);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";
    private static final String[] SUPPORTED_LANGS = {"en", "zh", "ko", "ar", "fr", "it", "es", "de", "ja"};
    private static final int MAX_ATTEMPTS = 30;
    private static final int RETRY_SLEEP_MS = 7000;

    private final OkHttpClient client;
    private volatile okhttp3.Call activeCall;
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
        okhttp3.Call call = activeCall;
        if (call != null) call.cancel();
    }

    public VotClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public static boolean isLangSupported(String lang) {
        if (lang == null) return false;
        if (lang.startsWith("ru")) return false; // already Russian
        for (String s : SUPPORTED_LANGS) {
            if (lang.startsWith(s)) return true;
        }
        return false;
    }

    public static boolean isRussian(String lang) {
        return lang != null && lang.startsWith("ru");
    }

    public static String normalizeLang(String lang) {
        if (lang == null || lang.isEmpty()) return "en";
        String l = lang.toLowerCase();
        for (String s : SUPPORTED_LANGS) {
            if (l.startsWith(s)) return s;
        }
        return l.length() >= 2 ? l.substring(0, 2) : l;
    }

    /** Result of a single translation request. */
    public static class TranslationResult {
        public boolean success;
        public String status; // "Ready", "Waiting", "Error"
        public String audioUrl;
        public String message;
    }

    /**
     * Single check of VOT translation status without polling.
     */
    public TranslationResult checkStatus(String url, double duration, boolean liveVoice, String sourceLang) {
        if (HMAC_KEY.length == 0) {
            TranslationResult r = new TranslationResult();
            r.success = false;
            r.status = "Error";
            r.message = "VOT_HMAC_KEY не настроен";
            return r;
        }
        return translateOnce(url, duration, liveVoice, sourceLang);
    }

    /**
     * Poll the VOT API until translation is ready or fails.
     * Calls progressCallback with attempt number.
     */
    public TranslationResult getTranslationAudio(String url, double duration,
                                                   boolean liveVoice, String sourceLang,
                                                   ProgressCallback callback) {
        if (HMAC_KEY.length == 0) {
            TranslationResult result = new TranslationResult();
            result.success = false;
            result.status = "Error";
            result.message = "Перевод недоступен: при сборке не задан VOT_HMAC_KEY";
            return result;
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                TranslationResult r = new TranslationResult();
                r.success = false;
                r.status = "Error";
                r.message = "Отменено";
                return r;
            }
            if (callback != null) callback.onAttempt(attempt + 1, MAX_ATTEMPTS);

            TranslationResult r = translateOnce(url, duration, liveVoice, sourceLang);
            if (!r.success) return r;
            if ("Ready".equals(r.status) && r.audioUrl != null) return r;
            if ("Waiting".equals(r.status)) {
                try { Thread.sleep(RETRY_SLEEP_MS); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    r.success = false;
                    r.status = "Error";
                    r.message = "Отменено";
                    return r;
                }
                continue;
            }
            return r; // unknown status → error
        }
        TranslationResult timeout = new TranslationResult();
        timeout.success = false;
        timeout.status = "Error";
        timeout.message = "Timeout";
        return timeout;
    }

    public interface ProgressCallback {
        void onAttempt(int current, int total);
    }

    // ── protobuf encoding (minimal, matches ytrd/vot.py) ──

    private TranslationResult translateOnce(String url, double duration,
                                            boolean liveVoice, String sourceLang) {
        try {
            byte[] body = buildRequestBody(url, duration, liveVoice, sourceLang);
            String sig = hmacSha256(body);

            Request req = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(body, MediaType.parse("application/x-protobuf")))
                    .header("Accept", "application/x-protobuf")
                    .header("Content-Type", "application/x-protobuf")
                    .header("User-Agent", USER_AGENT)
                    .header("Vtrans-Signature", sig)
                    .header("Sec-Vtrans-Token", UUID.randomUUID().toString().replace("-", ""))
                    .build();

            okhttp3.Call call = client.newCall(req);
            activeCall = call;
            if (cancelled) call.cancel();
            try (Response resp = call.execute()) {
                if (!resp.isSuccessful()) {
                    TranslationResult r = new TranslationResult();
                    r.success = false;
                    r.status = "Error";
                    r.message = "HTTP " + resp.code();
                    return r;
                }
                byte[] data = resp.body().bytes();
                return parseResponse(data);
            }
        } catch (Exception e) {
            Log.e(TAG, "VOT request failed", e);
            TranslationResult r = new TranslationResult();
            r.success = false;
            r.status = "Error";
            r.message = e.getMessage();
            return r;
        } finally {
            activeCall = null;
        }
    }

    private byte[] buildRequestBody(String url, double duration,
                                    boolean liveVoice, String sourceLang) {
        byte[] b = new byte[0];
        b = append(b, encodeString(3, url));
        b = append(b, encodeBool(5, true));
        b = append(b, encodeDouble(6, duration));
        b = append(b, encodeInt(7, 1));
        b = append(b, encodeString(8, sourceLang));
        b = append(b, encodeInt(9, 0));
        b = append(b, encodeInt(10, 0));
        b = append(b, encodeString(14, "ru"));
        b = append(b, encodeInt(15, 0));
        b = append(b, encodeInt(16, 2));
        b = append(b, encodeInt(17, 0));
        b = append(b, encodeBool(18, liveVoice));
        b = append(b, encodeString(19, ""));
        return b;
    }

    private TranslationResult parseResponse(byte[] data) {
        Map<Integer, Object> fields = readProtobuf(data);
        TranslationResult r = new TranslationResult();

        Integer status = getInt(fields, 4);
        String audioUrl = getString(fields, 1);
        String message = getString(fields, 9);

        if (status == null) {
            r.success = false;
            r.status = "Error";
            r.message = "No status in response";
            return r;
        }

        switch (status) {
            case 1:
                r.success = true;
                r.status = "Ready";
                r.audioUrl = audioUrl;
                r.message = message;
                break;
            case 2:
            case 6:
            case 7:
                r.success = true;
                r.status = "Waiting";
                r.message = message;
                break;
            default:
                r.success = false;
                r.status = "Error";
                r.message = message != null ? message : "Code " + status;
                break;
        }
        return r;
    }

    // ── protobuf helpers (delegated to VotProtobufCodec) ──

    private String hmacSha256(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_KEY, "HmacSHA256"));
            byte[] hash = mac.doFinal(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
