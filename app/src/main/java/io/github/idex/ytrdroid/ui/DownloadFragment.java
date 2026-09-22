package io.github.idex.ytrdroid.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.util.UrlUtil;

public class DownloadFragment extends Fragment {

    private EditText inputUrl;
    private ImageButton btnClear;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        inputUrl = view.findViewById(R.id.input_url);
        btnClear = view.findViewById(R.id.btn_clear);

        // Clear button
        btnClear.setOnClickListener(v -> inputUrl.setText(""));
        inputUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Paste button
        view.findViewById(R.id.btn_paste).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        inputUrl.setText(text);
                        analyzeUrl(text.toString());
                        return;
                    }
                }
            }
            Toast.makeText(requireContext(), R.string.error_no_url, Toast.LENGTH_SHORT).show();
        });

        // Settings button
        view.findViewById(R.id.btn_settings).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        });

        // URL input submit on action
        inputUrl.setOnEditorActionListener((v, actionId, event) -> {
            analyzeUrl(inputUrl.getText().toString());
            return true;
        });

        // Check for shared URL
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            String pending = activity.consumePendingUrl();
            if (pending != null) {
                inputUrl.setText(pending);
                analyzeUrl(pending);
            }
        }
    }

    private void analyzeUrl(String text) {
        String url = UrlUtil.extractUrl(text);
        if (url == null) {
            Toast.makeText(requireContext(), R.string.error_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }
        // Open params fragment
        ParamsFragment params = ParamsFragment.newInstance(url);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, params)
                .addToBackStack(null)
                .commit();
    }
}
