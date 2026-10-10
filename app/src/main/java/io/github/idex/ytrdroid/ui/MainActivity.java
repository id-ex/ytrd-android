package io.github.idex.ytrdroid.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.data.updater.AppUpdateChecker;
import io.github.idex.ytrdroid.service.DownloadService;
import io.github.idex.ytrdroid.util.UrlUtil;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_SCREEN = "extra_screen";
    public static final String SCREEN_DOWNLOADS = "downloads";
    public static final String SCREEN_FILES = "files";

    private DownloadService downloadService;
    private boolean bound;
    private String pendingUrl;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabSelectAll;
    private FloatingActionButton fabClearSelection;
    private Runnable currentFabAction;
    private Runnable currentSelectAllAction;
    private Runnable currentClearSelectionAction;
    private boolean isFabInSelectionMode = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName c, IBinder b) {
            downloadService = ((DownloadService.LocalBinder) b).getService();
            bound = true;
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

        fabAdd = findViewById(R.id.add_download);
        fabSelectAll = findViewById(R.id.fab_select_all);
        fabClearSelection = findViewById(R.id.fab_clear_selection);

        fabAdd.setOnClickListener(v -> {
            if (currentFabAction != null) {
                currentFabAction.run();
            } else {
                openSheet(null);
            }
        });

        fabSelectAll.setOnClickListener(v -> {
            if (currentSelectAllAction != null) {
                currentSelectAllAction.run();
            }
        });

        fabClearSelection.setOnClickListener(v -> {
            if (currentClearSelectionAction != null) {
                currentClearSelectionAction.run();
            }
        });

        findViewById(R.id.open_files).setOnClickListener(v -> {
            setSelectionModeActive(false, null, null, null);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new FilesFragment())
                    .commit();
        });
        findViewById(R.id.open_settings).setOnClickListener(v -> {
            setSelectionModeActive(false, null, null, null);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        findViewById(R.id.open_downloads).setOnClickListener(v -> {
            setSelectionModeActive(false, null, null, null);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DownloadsFragment())
                    .addToBackStack(null).commit();
        });
        if (savedInstanceState == null) {
            String targetScreen = getIntent() != null ? getIntent().getStringExtra(EXTRA_SCREEN) : null;
            if (SCREEN_DOWNLOADS.equals(targetScreen)) {
                showFragment(new FilesFragment());
                openDownloads();
            } else {
                showFragment(new FilesFragment());
            }
            handleIncomingIntent(getIntent());

            // Check for app update in background on launch
            AppUpdateChecker.check(new AppUpdateChecker.Callback() {
                @Override
                public void onUpdateAvailable(AppUpdateChecker.UpdateInfo info) {
                    if (!isFinishing() && !isDestroyed()) {
                        AppUpdateDialog.show(MainActivity.this, info);
                    }
                }

                @Override
                public void onNoUpdateAvailable() {}

                @Override
                public void onError(Throwable error) {}
            });
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

    public void setSelectionModeActive(boolean active,
                                       Runnable onDelete,
                                       Runnable onSelectAll,
                                       Runnable onClearSelection) {
        if (fabAdd == null) fabAdd = findViewById(R.id.add_download);
        if (fabSelectAll == null) fabSelectAll = findViewById(R.id.fab_select_all);
        if (fabClearSelection == null) fabClearSelection = findViewById(R.id.fab_clear_selection);
        if (fabAdd == null || fabSelectAll == null || fabClearSelection == null) return;

        if (active) {
            currentFabAction = onDelete;
            currentSelectAllAction = onSelectAll;
            currentClearSelectionAction = onClearSelection;

            if (!isFabInSelectionMode) {
                isFabInSelectionMode = true;

                fabAdd.animate().scaleX(0.7f).scaleY(0.7f).setDuration(120).withEndAction(() -> {
                    fabAdd.setImageResource(R.drawable.ic_delete);
                    fabAdd.setContentDescription("Удалить выбранные");
                    fabAdd.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                            .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                }).start();

                float dy = 24 * getResources().getDisplayMetrics().density;

                fabSelectAll.setVisibility(View.VISIBLE);
                fabSelectAll.setAlpha(0f);
                fabSelectAll.setScaleX(0.2f);
                fabSelectAll.setScaleY(0.2f);
                fabSelectAll.setTranslationY(dy);
                fabSelectAll.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                        .start();

                fabClearSelection.setVisibility(View.VISIBLE);
                fabClearSelection.setAlpha(0f);
                fabClearSelection.setScaleX(0.2f);
                fabClearSelection.setScaleY(0.2f);
                fabClearSelection.setTranslationY(dy * 1.5f);
                fabClearSelection.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setStartDelay(45)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                        .start();
            } else {
                fabAdd.setImageResource(R.drawable.ic_delete);
                fabSelectAll.setVisibility(View.VISIBLE);
                fabClearSelection.setVisibility(View.VISIBLE);
            }
        } else {
            currentFabAction = null;
            currentSelectAllAction = null;
            currentClearSelectionAction = null;

            if (isFabInSelectionMode) {
                isFabInSelectionMode = false;

                float dy = 20 * getResources().getDisplayMetrics().density;

                fabSelectAll.animate()
                        .alpha(0f)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .translationY(dy)
                        .setDuration(160)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> fabSelectAll.setVisibility(View.GONE))
                        .start();

                fabClearSelection.animate()
                        .alpha(0f)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .translationY(dy)
                        .setDuration(160)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> fabClearSelection.setVisibility(View.GONE))
                        .start();

                fabAdd.animate().scaleX(0.7f).scaleY(0.7f).setDuration(120).withEndAction(() -> {
                    fabAdd.setImageResource(R.drawable.ic_add);
                    fabAdd.setContentDescription("Добавить ссылку");
                    fabAdd.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                            .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                }).start();
            } else {
                fabClearSelection.setVisibility(View.GONE);
                fabSelectAll.setVisibility(View.GONE);
                fabAdd.setImageResource(R.drawable.ic_add);
                fabAdd.setContentDescription("Добавить ссылку");
            }
        }
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
        if (intent.hasExtra(EXTRA_SCREEN)) {
            String screen = intent.getStringExtra(EXTRA_SCREEN);
            setSelectionModeActive(false, null, null, null);
            if (SCREEN_DOWNLOADS.equals(screen)) {
                openDownloads();
            } else if (SCREEN_FILES.equals(screen)) {
                getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                showFragment(new FilesFragment());
            }
        }
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
