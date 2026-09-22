package io.github.idex.ytrdroid.ui;

import android.content.Context;
import android.os.Bundle;
import java.io.File;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.engine.DownloadEngine;
import io.github.idex.ytrdroid.engine.VotClient;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.VideoInfo;
import io.github.idex.ytrdroid.service.DownloadService;
import io.github.idex.ytrdroid.util.UrlUtil;

public class ParamsFragment extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {
    private int analysisGeneration;
    private android.widget.EditText urlField;
    private View options;
    private View analyzeButton;
    private static final String ARG_URL = "url";
    private VideoInfo info;
    private boolean loading = true;

    // Views
    private TextView txtTitle, txtUploader, txtLangStatus, txtFolder;
    private Spinner spinnerQuality, spinnerVoice;
    private SwitchMaterial switchTranslate, switchSubtitles;
    private View groupVoice, groupAudioMode, groupSubtitles;
    private RadioGroup radioAudioMode;
    private MaterialButtonToggleGroup toggleType;
    private static final String PREF_KEY_FOLDER = "last_download_folder";
    private String currentFolder;
    private MaterialButton btnDownload;

    public static ParamsFragment newInstance(String url) {
        ParamsFragment f = new ParamsFragment();
        Bundle b = new Bundle();
        b.putString(ARG_URL, url);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_params, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        txtTitle = view.findViewById(R.id.txt_title);
        txtUploader = view.findViewById(R.id.txt_uploader);
        txtLangStatus = view.findViewById(R.id.txt_lang_status);
        txtFolder = view.findViewById(R.id.txt_folder);
        spinnerQuality = view.findViewById(R.id.spinner_quality);
        spinnerVoice = view.findViewById(R.id.spinner_voice);
        switchTranslate = view.findViewById(R.id.switch_translate);
        switchSubtitles = view.findViewById(R.id.switch_subtitles);
        groupVoice = view.findViewById(R.id.group_voice);
        groupAudioMode = view.findViewById(R.id.group_audio_mode);
        groupSubtitles = view.findViewById(R.id.group_subtitles);
        radioAudioMode = view.findViewById(R.id.radio_audio_mode);
        toggleType = view.findViewById(R.id.toggle_type);
        btnDownload = view.findViewById(R.id.btn_download);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> dismiss());
        urlField = view.findViewById(R.id.sheet_url);
        options = view.findViewById(R.id.sheet_options);
        analyzeButton = view.findViewById(R.id.sheet_analyze);
        options.setVisibility(View.GONE);
        btnDownload.setEnabled(false);
        analyzeButton.setVisibility(View.GONE);
        view.findViewById(R.id.sheet_paste).setVisibility(View.GONE);
        btnDownload.setVisibility(View.GONE);
        view.findViewById(R.id.img_thumb).setVisibility(View.GONE);
        txtTitle.setText("Вставьте ссылку на видео");
        txtUploader.setText("Параметры появятся после анализа ссылки");
        analyzeButton.setOnClickListener(v -> analyze());
        view.findViewById(R.id.sheet_paste).setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                android.content.ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0)
                    urlField.setText(clip.getItemAt(0).coerceToText(requireContext()));
            }
        });
        urlField.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                analysisGeneration++;
                info = null;
                loading = true;
                options.setVisibility(View.GONE);
                btnDownload.setEnabled(false);
                analyzeButton.setEnabled(true);
                btnDownload.setVisibility(View.GONE);
                view.findViewById(R.id.img_thumb).setVisibility(View.GONE);
                txtTitle.setText("Вставьте ссылку на видео");
                txtUploader.setText("Параметры появятся после анализа ссылки");
                final int generation = analysisGeneration;
                urlField.postDelayed(() -> {
                    if (getView() != null && generation == analysisGeneration &&
                            UrlUtil.extractVideoId(urlField.getText().toString()) != null) analyze();
                }, 700);
            }
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Setup folder and defaults from SettingsRepository
        io.github.idex.ytrdroid.App app = (io.github.idex.ytrdroid.App) requireContext().getApplicationContext();
        File defaultDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "ytrd");
        currentFolder = app.container().settings.getDownloadFolder(defaultDir);
        txtFolder.setText(formatFolderPath(currentFolder));

        View rowFolder = view.findViewById(R.id.row_folder);
        if (rowFolder != null) {
            rowFolder.setOnClickListener(v -> showFolderPicker());
        }

        // Voice spinner
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.voice_standard), getString(R.string.voice_live)});
        spinnerVoice.setAdapter(voiceAdapter);

        // Apply defaults from settings
        boolean defaultTranslate = app.container().settings.isTranslateDefault();
        switchTranslate.setChecked(defaultTranslate);
        int initialTranslateVis = defaultTranslate ? View.VISIBLE : View.GONE;
        groupVoice.setVisibility(initialTranslateVis);
        groupAudioMode.setVisibility(initialTranslateVis);
        spinnerVoice.setSelection("live".equals(app.container().settings.getDefaultVoice()) ? 1 : 0);
        radioAudioMode.check("dual".equals(app.container().settings.getDefaultAudioMode()) ? R.id.radio_dual : R.id.radio_mix);

        // Translation switch
        switchTranslate.setOnCheckedChangeListener((v, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            groupVoice.setVisibility(vis);
            groupAudioMode.setVisibility(vis);
        });

        // Video/Audio toggle
        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                boolean isVideo = checkedId == R.id.btn_video;
                spinnerQuality.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                view.findViewById(R.id.label_quality).setVisibility(isVideo ? View.VISIBLE : View.GONE);
                groupSubtitles.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                if (!isVideo) {
                    groupAudioMode.setVisibility(View.GONE);
                } else if (switchTranslate.isChecked()) {
                    groupAudioMode.setVisibility(View.VISIBLE);
                }
            }
        });

        btnDownload.setOnClickListener(v -> startDownload());
        String shared = getArguments() == null ? null : getArguments().getString(ARG_URL);
        if (shared != null && !shared.isEmpty()) {
            urlField.setText(shared);
            analyze();
        }
    }

    private void analyze() {
        String id = UrlUtil.extractVideoId(urlField.getText().toString());
        if (id == null) {
            urlField.setError(getString(R.string.error_invalid_url));
            return;
        }
        String url = "https://www.youtube.com/watch?v=" + id;
        final int generation = ++analysisGeneration;
        loading = true;
        btnDownload.setEnabled(false);
        analyzeButton.setEnabled(false);
        txtTitle.setText(R.string.analyzing);
        txtUploader.setText("");
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                DownloadEngine engine = new DownloadEngine();
                VideoInfo result = engine.fetchInfo(ctx, url);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getView() == null || generation != analysisGeneration) return;
                        info = result;
                        analyzeButton.setEnabled(true);
                        options.setVisibility(View.VISIBLE);
                        btnDownload.setEnabled(true);
                        btnDownload.setVisibility(View.VISIBLE);
                        bindInfo();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getView() == null || generation != analysisGeneration) return;
                        analyzeButton.setEnabled(true);
                        txtTitle.setText(R.string.error);
                        txtUploader.setText(e.getMessage());
                    });
                }
            }
        }, "fetch-info").start();


    }

    private void bindInfo() {
        loading = false;
        if (info == null) return;

        txtTitle.setText(info.title != null ? info.title : "");
        String sub = (info.uploader != null ? info.uploader : "") +
                (info.duration > 0 ? " · " + UrlUtil.formatDuration(info.duration) : "");
        txtUploader.setText(sub);

        // Quality spinner
        List<String> qLabels = new ArrayList<>();
        if (info.qualities != null) {
            for (String q : info.qualities) {
                if ("auto".equalsIgnoreCase(q)) {
                    qLabels.add("Auto · MP4");
                } else {
                    try {
                        int h = Integer.parseInt(q);
                        qLabels.add(q + "p · " + (h > 1080 ? "MKV" : "MP4"));
                    } catch (NumberFormatException e) {
                        qLabels.add(q);
                    }
                }
            }
        }
        if (qLabels.isEmpty()) qLabels.add("Auto · MP4");
        spinnerQuality.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, qLabels));

        // Select default quality from settings if available
        io.github.idex.ytrdroid.App appInstance = (io.github.idex.ytrdroid.App) requireContext().getApplicationContext();
        String defaultQ = appInstance.container().settings.getDefaultQuality();
        if (info.qualities != null && defaultQ != null) {
            for (int i = 0; i < info.qualities.size(); i++) {
                if (defaultQ.equals(info.qualities.get(i))) {
                    spinnerQuality.setSelection(i);
                    break;
                }
            }
        }

        // Load thumbnail using ThumbnailLoader
        if (info.thumbnail != null) {
            ImageView imgThumb = getView() != null ? getView().findViewById(R.id.img_thumb) : null;
            if (imgThumb != null) {
                imgThumb.setVisibility(View.VISIBLE);
                io.github.idex.ytrdroid.util.ThumbnailLoader.getInstance().load(info.thumbnail, imgThumb);
            }
        }

        // Translation availability
        if (VotClient.isRussian(info.language)) {
            switchTranslate.setChecked(false);
            switchTranslate.setEnabled(false);
            txtLangStatus.setText(R.string.translation_not_needed);
            txtLangStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
            txtLangStatus.setVisibility(View.VISIBLE);
        } else {
            switchTranslate.setEnabled(true);
            switchTranslate.setChecked(false);
            txtLangStatus.setVisibility(View.GONE);
        }
    }

    private void startDownload() {
        if (loading || info == null) {
            Toast.makeText(requireContext(), R.string.analyzing, Toast.LENGTH_SHORT).show();
            return;
        }

        DownloadTask task = new DownloadTask();
        task.url = info.url;
        task.title = info.title;
        task.thumbnail = info.thumbnail;

        boolean isVideo = toggleType.getCheckedButtonId() == R.id.btn_video;
        if (isVideo) {
            int pos = spinnerQuality.getSelectedItemPosition();
            task.quality = (info.qualities != null && pos < info.qualities.size())
                    ? info.qualities.get(pos) : "auto";
            task.ext = "mp4";
        } else {
            task.quality = "audio";
            task.ext = "mp3";
        }

        task.translate = switchTranslate.isChecked();
        task.liveVoice = spinnerVoice.getSelectedItemPosition() == 1;
        task.audioMode = radioAudioMode.getCheckedRadioButtonId() == R.id.radio_dual
                ? "dual" : "mix";
        task.subtitles = isVideo && switchSubtitles.isChecked();
        task.duration = info.duration > 0 ? info.duration : 341.0;
        task.language = info.language != null ? info.language : "en";

        task.destinationPath = currentFolder;

        // Save last used folder
        ((io.github.idex.ytrdroid.App) requireContext().getApplicationContext())
                .container().settings.setDownloadFolder(currentFolder);

        // Enqueue to service
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null && activity.getDownloadService() != null) {
            try {
                activity.getDownloadService().enqueue(task);
            } catch (io.github.idex.ytrdroid.domain.model.DownloadRequest.ValidationError e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(), R.string.downloading, Toast.LENGTH_SHORT).show();
            dismiss();
            activity.openDownloads();
        }
    }

    private String formatFolderPath(String path) {
        if (path == null) return "Download/ytrd";
        if (path.contains("Download")) {
            int idx = path.indexOf("Download");
            return path.substring(idx);
        }
        return path;
    }

    private void showFolderPicker() {
        Context ctx = requireContext();
        String[] options = {
                "Download/ytrd (по умолчанию)",
                "Download",
                "Movies/ytrd",
                "Music/ytrd"
        };
        File ext = android.os.Environment.getExternalStorageDirectory();
        String[] paths = {
                new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ytrd").getAbsolutePath(),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
                new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "ytrd").getAbsolutePath(),
                new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "ytrd").getAbsolutePath()
        };

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("Папка загрузки")
                .setItems(options, (d, which) -> {
                    currentFolder = paths[which];
                    new File(currentFolder).mkdirs();
                    txtFolder.setText(formatFolderPath(currentFolder));
                    io.github.idex.ytrdroid.App appInstance = (io.github.idex.ytrdroid.App) ctx.getApplicationContext();
                    appInstance.container().settings.setDownloadFolder(currentFolder);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
