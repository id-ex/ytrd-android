package io.github.idex.ytrdroid.ui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.util.CookieNetscapeWriter;

/**
 * Full-screen WebView activity for signing in to Google / YouTube.
 * <p>
 * Google blocks login from stock Android WebViews by detecting:
 * <ol>
 *   <li>{@code ; wv} token and {@code Version/X.X} in the User-Agent</li>
 *   <li>{@code X-Requested-With} header set to the app package name</li>
 * </ol>
 * We counter both: the UA is replaced with a genuine Chrome Mobile string,
 * and the {@code X-Requested-With} header is suppressed via AndroidX WebKit.
 */
public class YouTubeLoginActivity extends AppCompatActivity {

    private static final String LOGIN_URL =
            "https://accounts.google.com/ServiceLogin"
                    + "?service=youtube"
                    + "&continue=https%3A%2F%2Fm.youtube.com%2F";

    /**
     * A real Chrome-on-Android User-Agent.  Using a hardcoded string is more
     * reliable than patching the WebView default, because OEMs sometimes add
     * extra tokens that still betray an embedded context.
     */
    private static final String CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/AP3A.241105.008) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.6778.135 Mobile Safari/537.36";

    private WebView webView;
    private ProgressBar progressBar;
    private boolean loginSaved = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_login);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
            toolbar.inflateMenu(R.menu.menu_youtube_login);
            toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        }

        progressBar = findViewById(R.id.login_progress);
        webView = findViewById(R.id.login_webview);

        // ── Cookie setup ──
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // ── WebSettings ──
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // 1) Replace the entire User-Agent with a real Chrome string
        //    (removes "; wv", "Version/4.0", and any OEM-specific tokens)
        settings.setUserAgentString(CHROME_UA);

        // 2) Suppress the X-Requested-With header that WebView adds automatically.
        //    Without this, Google sees "X-Requested-With: com.idex.ytrd" and blocks login.
        //    Setting an empty allow-list means the header is never sent.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(
                    settings,
                    java.util.Collections.emptySet());
        }

        // ── Chrome client for progress bar ──
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                }
            }
        });

        // ── Page client — detect successful login ──
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Stay inside WebView for all Google/YouTube URLs
                String host = request.getUrl().getHost();
                if (host != null && (host.endsWith("google.com") || host.endsWith("youtube.com")
                        || host.endsWith("gstatic.com") || host.endsWith("googleapis.com"))) {
                    return false;           // let WebView handle it
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                checkAndSaveLogin(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                checkAndSaveLogin(url);
            }
        });

        webView.loadUrl(LOGIN_URL);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_save_cookies) {
            saveCookiesAndFinish(true);
            return true;
        }
        return false;
    }

    /**
     * Auto-detect: if we've landed on youtube.com and cookies contain login tokens,
     * save them and close the activity.
     */
    private void checkAndSaveLogin(String currentUrl) {
        if (loginSaved || currentUrl == null) return;

        if (currentUrl.contains("youtube.com") && !currentUrl.contains("accounts.google.com")) {
            CookieManager cm = CookieManager.getInstance();
            String cookies = cm.getCookie("https://m.youtube.com");
            if (cookies != null && (cookies.contains("LOGIN_INFO") || cookies.contains("SID")
                    || cookies.contains("SAPISID") || cookies.contains("SSID"))) {
                loginSaved = true;
                saveCookiesAndFinish(false);
            }
        }
    }

    private void saveCookiesAndFinish(boolean manual) {
        File dest = new File(getFilesDir(), "cookies.txt");
        boolean ok = CookieNetscapeWriter.saveYouTubeCookies(dest);
        if (ok) {
            Toast.makeText(this, "Вход выполнен! Cookies сохранены", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else if (manual) {
            Toast.makeText(this, "Cookies не найдены. Войдите в аккаунт", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
