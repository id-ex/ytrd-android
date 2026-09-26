package io.github.idex.ytrdroid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.util.UrlUtil;

public class ShareActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            showErrorAndFinish();
            return;
        }

        CharSequence extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String text = extraText != null ? extraText.toString() : null;
        String url = text != null ? UrlUtil.extractUrl(text) : null;

        if (url == null || url.trim().isEmpty()) {
            showErrorAndFinish();
            return;
        }

        Fragment existing = getSupportFragmentManager().findFragmentByTag("download-sheet");
        if (existing instanceof ParamsFragment) {
            ((ParamsFragment) existing).dismiss();
        }
        ParamsFragment.newInstance(url).show(getSupportFragmentManager(), "download-sheet");
    }

    private void showErrorAndFinish() {
        Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_SHORT).show();
        finish();
    }
}
