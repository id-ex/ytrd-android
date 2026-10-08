package io.github.idex.ytrdroid.data.newpipe;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class OkHttpDownloader extends Downloader {
    private final OkHttpClient client;

    public OkHttpDownloader(OkHttpClient client) {
        this.client = client != null ? client : new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        String httpMethod = request.httpMethod();
        String url = request.url();
        Map<String, List<String>> headersMap = request.headers();
        byte[] dataToSend = request.dataToSend();

        Headers.Builder headersBuilder = new Headers.Builder();
        if (headersMap != null) {
            for (Map.Entry<String, List<String>> entry : headersMap.entrySet()) {
                for (String value : entry.getValue()) {
                    headersBuilder.add(entry.getKey(), value);
                }
            }
        }

        RequestBody body = null;
        if (dataToSend != null) {
            body = RequestBody.create(dataToSend, null);
        }

        okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .method(httpMethod, body);

        try (okhttp3.Response response = client.newCall(reqBuilder.build()).execute()) {
            if (response.code() == 429 || (response.code() == 302
                    && response.header("Location", "") != null
                    && response.header("Location", "").contains("google.com/sorry"))) {
                throw new ReCaptchaException("ReCAPTCHA requested", url);
            }

            ResponseBody responseBody = response.body();
            String responseBodyString = responseBody != null ? responseBody.string() : "";
            Map<String, List<String>> responseHeaders = response.headers().toMultimap();

            return new Response(
                    response.code(),
                    response.message(),
                    responseHeaders,
                    responseBodyString,
                    response.request().url().toString()
            );
        }
    }
}
