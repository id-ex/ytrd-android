package io.github.idex.ytrdroid.data.network;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;

/**
 * DNS resolver that falls back to DNS-over-HTTPS (1.1.1.1 / 8.8.8.8) or known static IPs
 * if the device's system DNS fails (e.g. when using a foreign VPN that blocks or fails .ru domains).
 */
public final class SafeDns implements Dns {
    private static final String TAG = "SafeDns";
    public static final SafeDns INSTANCE = new SafeDns();

    // Known static IP fallback for api.browser.yandex.ru
    private static final byte[] YANDEX_API_IP = new byte[]{(byte) 213, (byte) 180, (byte) 193, (byte) 234};

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        try {
            return Dns.SYSTEM.lookup(hostname);
        } catch (UnknownHostException originalException) {
            Log.w(TAG, "System DNS failed for " + hostname + ", trying fallback DoH...");
            List<InetAddress> fallback = resolveViaDoH(hostname);
            if (!fallback.isEmpty()) {
                Log.i(TAG, "Resolved " + hostname + " via DoH: " + fallback);
                return fallback;
            }

            if ("api.browser.yandex.ru".equalsIgnoreCase(hostname)) {
                Log.i(TAG, "Using static IP fallback for " + hostname);
                return Collections.singletonList(InetAddress.getByAddress(hostname, YANDEX_API_IP));
            }
            throw originalException;
        }
    }

    private List<InetAddress> resolveViaDoH(String hostname) {
        List<InetAddress> result = new ArrayList<>();
        // 1. Try Cloudflare DoH (via direct IP 1.1.1.1 - no DNS needed)
        try {
            List<InetAddress> addresses = queryDnsJson("https://1.1.1.1/dns-query?name=" + hostname + "&type=A", hostname);
            if (!addresses.isEmpty()) return addresses;
        } catch (Exception ignored) {}

        // 2. Try Google DoH (via direct IP 8.8.8.8 - no DNS needed)
        try {
            List<InetAddress> addresses = queryDnsJson("https://8.8.8.8/resolve?name=" + hostname + "&type=A", hostname);
            if (!addresses.isEmpty()) return addresses;
        } catch (Exception ignored) {}

        return result;
    }

    private List<InetAddress> queryDnsJson(String urlStr, String hostname) throws Exception {
        List<InetAddress> list = new ArrayList<>();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestProperty("Accept", "application/dns-json");
        try (InputStream in = conn.getInputStream()) {
            byte[] bytes = readFully(in);
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (json.has("Answer")) {
                JSONArray answers = json.getJSONArray("Answer");
                for (int i = 0; i < answers.length(); i++) {
                    JSONObject ans = answers.getJSONObject(i);
                    // type 1 is A record
                    if (ans.optInt("type") == 1 && ans.has("data")) {
                        String ipStr = ans.getString("data").trim();
                        try {
                            byte[] ipBytes = parseIpv4(ipStr);
                            if (ipBytes != null) {
                                list.add(InetAddress.getByAddress(hostname, ipBytes));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        return list;
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) return null;
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int val = Integer.parseInt(parts[i]);
            if (val < 0 || val > 255) return null;
            bytes[i] = (byte) val;
        }
        return bytes;
    }
}
