package io.github.idex.ytrdroid.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.service.DownloadService;
import io.github.idex.ytrdroid.util.UrlUtil;

public class MainActivity extends AppCompatActivity {
    private DownloadService downloadService;
    private boolean bound;
    private String pendingUrl;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName c, IBinder b) {
            downloadService = ((DownloadService.LocalBinder) b).getService();
            bound = true;
            // Notify current fragment
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof DownloadsFragment) {
                ((DownloadsFragment) f).onServiceConnected(downloadService);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName c) {
            bound = false;
            downloadService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        }

        findViewById(R.id.add_download).setOnClickListener(v -> openSheet(null));
        findViewById(R.id.open_files).setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new FilesFragment())
                    .commit();
        });
        findViewById(R.id.open_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.open_downloads).setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DownloadsFragment())
                    .addToBackStack(null).commit();
        });
        if (savedInstanceState == null) {
            showFragment(new FilesFragment());
            handleIncomingIntent(getIntent());
        }

        // Bind to download service
        Intent si = new Intent(this, DownloadService.class);
        bindService(si, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) unbindService(connection);
    }

    public void openDownloads() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DownloadsFragment())
                .addToBackStack(null)
                .commit();
    }

    public DownloadService getDownloadService() { return downloadService; }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                String url = UrlUtil.extractUrl(text.toString());
                if (url != null) {
                    openSheet(url);
                }
            }
        }
    }

    private void openSheet(String url) {
        Fragment existing = getSupportFragmentManager().findFragmentByTag("download-sheet");
        if (existing instanceof ParamsFragment) ((ParamsFragment) existing).dismiss();
        ParamsFragment.newInstance(url).show(getSupportFragmentManager(), "download-sheet");
    }

    public String consumePendingUrl() {
        String url = pendingUrl;
        pendingUrl = null;
        return url;
    }

    private void showFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }
}
