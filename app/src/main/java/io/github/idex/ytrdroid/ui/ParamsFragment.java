package io.github.idex.ytrdroid.ui;

import android.content.Context;
import android.content.DialogInterface;
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
import io.github.idex.ytrdroid.data.ytdlp.OEmbedClient;
import io.github.idex.ytrdroid.data.ytdlp.VideoInfoCache;
import io.github.idex.ytrdroid.domain.quality.QualitySelector;
import io.github.idex.ytrdroid.engine.DownloadEngine;
import io.github.idex.ytrdroid.engine.VotClient;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.VideoInfo;
import io.github.idex.ytrdroid.service.DownloadService;
import io.github.idex.ytrdroid.util.ThumbnailLoader;
import io.github.idex.ytrdroid.util.UrlUtil;

public class ParamsFragment extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {
    private int analysisGeneration;
    private volatile DownloadEngine analysisEngine;
    private volatile String analysisProcessId;
    private android.widget.EditText urlField;
    private View options;
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
    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> folderPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    currentFolder = uri.toString();
                    ((io.github.idex.ytrdroid.App) requireContext().getApplicationContext())
                            .container().settings.setDownloadFolder(currentFolder);
                    if (txtFolder != null) txtFolder.setText(formatFolderPath(currentFolder));
                } catch (SecurityException e) {
                    Toast.makeText(requireContext(), "Не удалось получить доступ к папке", Toast.LENGTH_LONG).show();
                }
            });
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
        View btnClearUrl = view.findViewById(R.id.btn_clear_url);
        View btnPasteUrl = view.findViewById(R.id.btn_paste_url);
        options.setVisibility(View.GONE);
        btnDownload.setEnabled(false);
        btnDownload.setVisibility(View.GONE);
        view.findViewById(R.id.img_thumb).setVisibility(View.GONE);
        txtTitle.setText("Вставьте ссылку на видео");
        txtUploader.setText("Параметры появятся после анализа ссылки");

        btnClearUrl.setOnClickListener(v -> urlField.setText(""));
        btnPasteUrl.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                android.content.ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
                    if (text != null) urlField.setText(text.toString().trim());
                }
            }
        });
        urlField.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearUrl.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                analysisGeneration++;
                cancelAnalysis();
                info = null;
                loading = true;
                options.setVisibility(View.GONE);
                boolean validUrl = UrlUtil.extractVideoId(s == null ? "" : s.toString()) != null;
                btnDownload.setEnabled(validUrl);
                btnDownload.setVisibility(validUrl ? View.VISIBLE : View.GONE);
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
        groupSubtitles.setVisibility(initialTranslateVis);
        spinnerVoice.setSelection("live".equals(app.container().settings.getDefaultVoice()) ? 1 : 0);
        radioAudioMode.check("dual".equals(app.container().settings.getDefaultAudioMode()) ? R.id.radio_dual : R.id.radio_mix);

        // Translation switch
        switchTranslate.setOnCheckedChangeListener((v, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            groupVoice.setVisibility(vis);
            groupAudioMode.setVisibility(vis);
            groupSubtitles.setVisibility(vis);
        });

        // Video/Audio toggle
        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                boolean isVideo = checkedId == R.id.btn_video;
                spinnerQuality.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                view.findViewById(R.id.label_quality).setVisibility(isVideo ? View.VISIBLE : View.GONE);
                groupSubtitles.setVisibility(isVideo && switchTranslate.isChecked() ? View.VISIBLE : View.GONE);
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
        cancelAnalysis();
        String id = UrlUtil.extractVideoId(urlField.getText().toString());
        if (id == null) {
            urlField.setError(getString(R.string.error_invalid_url));
            return;
        }

        VideoInfo cached = VideoInfoCache.get(id);
        if (cached != null) {
            info = cached;
            options.setVisibility(View.VISIBLE);
            btnDownload.setEnabled(true);
            btnDownload.setVisibility(View.VISIBLE);
            bindInfo();
            return;
        }

        String url = "https://www.youtube.com/watch?v=" + id;
        final int generation = ++analysisGeneration;
        loading = true;
        btnDownload.setEnabled(true);
        btnDownload.setVisibility(View.VISIBLE);
        options.setVisibility(View.GONE);
        txtTitle.setText(R.string.analyzing);
        txtUploader.setText("Получение информации о видео…");

        // Этап 1 (Мгновенное превью oEmbed)
        new Thread(() -> {
            OEmbedClient.PreviewInfo preview = OEmbedClient.fetch(id);
            if (preview != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (getView() == null || generation != analysisGeneration || info != null) return;
                    txtTitle.setText(preview.title);
                    txtUploader.setText(preview.author != null
                            ? getString(R.string.analysis_formats_author, preview.author)
                            : getString(R.string.analysis_formats));
                    ImageView imgThumb = getView().findViewById(R.id.img_thumb);
                    if (imgThumb != null) {
                        imgThumb.setVisibility(View.VISIBLE);
                        ThumbnailLoader.getInstance().load(preview.thumbnailUrl, imgThumb);
                    }
                });
            }
        }, "oembed-preview").start();

        // Этап 2 (Фоновый yt-dlp с оптимизированными флагами)
        Context ctx = requireContext().getApplicationContext();
        DownloadEngine engine = new DownloadEngine();
        String processId = java.util.UUID.randomUUID().toString();
        analysisEngine = engine;
        analysisProcessId = processId;
        new Thread(() -> {
            try {
                VideoInfo result = engine.fetchInfo(ctx, url, processId);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getView() == null || generation != analysisGeneration) return;
                        VideoInfoCache.put(id, result);
                        info = result;
                        options.setVisibility(View.VISIBLE);
                        btnDownload.setEnabled(true);
                        btnDownload.setVisibility(View.VISIBLE);
                        bindInfo();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getView() == null) return;
                        if (generation == analysisGeneration && info == null) {
                            txtTitle.setText(R.string.error);
                            txtUploader.setText(e.getMessage());
                            boolean validUrl = UrlUtil.extractVideoId(urlField.getText().toString()) != null;
                            btnDownload.setEnabled(validUrl);
                            btnDownload.setVisibility(validUrl ? View.VISIBLE : View.GONE);
                            options.setVisibility(View.GONE);
                        }
                    });
                }
            } finally {
                if (engine == analysisEngine && processId.equals(analysisProcessId)) {
                    analysisEngine = null;
                    analysisProcessId = null;
                }
            }
        }, "fetch-info").start();
    }

    private void cancelAnalysis() {
        DownloadEngine engine = analysisEngine;
        if (engine == null) return;
        analysisEngine = null;
        analysisProcessId = null;
        engine.cancel(null);
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
        String selectedQuality = QualitySelector.select(defaultQ, info.qualities);
        if (info.qualities != null) {
            for (int i = 0; i < info.qualities.size(); i++) {
                if (selectedQuality.equalsIgnoreCase(info.qualities.get(i))) {
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
                ThumbnailLoader.getInstance().load(info.thumbnail, imgThumb);
            }
        }

        // Translation availability
        txtLangStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
        if (VotClient.isRussian(info.language)) {
            switchTranslate.setChecked(false);
            switchTranslate.setEnabled(false);
            txtLangStatus.setText(R.string.translation_not_needed);
            txtLangStatus.setVisibility(View.VISIBLE);
        } else {
            boolean defTranslate = appInstance.container().settings.isTranslateDefault();
            switchTranslate.setChecked(defTranslate);
            switchTranslate.setEnabled(true);
            txtLangStatus.setText("Проверка наличия перевода…");
            txtLangStatus.setVisibility(View.VISIBLE);

            spinnerVoice.setSelection("live".equals(appInstance.container().settings.getDefaultVoice()) ? 1 : 0);
            radioAudioMode.check("dual".equals(appInstance.container().settings.getDefaultAudioMode()) ? R.id.radio_dual : R.id.radio_mix);

            final int currentGen = analysisGeneration;
            new Thread(() -> {
                VotClient vot = new VotClient();
                String lang = VotClient.normalizeLang(info.language);
                double dur = info.duration > 0 ? info.duration : 341.0;
                boolean isLive = spinnerVoice != null && spinnerVoice.getSelectedItemPosition() == 1;
                VotClient.TranslationResult tr = vot.checkStatus(info.url, dur, isLive, lang);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getView() == null || currentGen != analysisGeneration) return;
                        txtLangStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
                        if (tr != null && tr.success) {
                            if ("Ready".equals(tr.status)) {
                                txtLangStatus.setText("Перевод готов");
                            } else if ("Waiting".equals(tr.status)) {
                                txtLangStatus.setText("Перевод готовится (попробуйте позже)");
                            } else {
                                String msg = tr.message != null && !tr.message.isEmpty() ? tr.message : "недоступен";
                                txtLangStatus.setText(getString(R.string.translation_status_message, msg));
                            }
                        } else {
                            String msg = tr != null && tr.message != null && !tr.message.isEmpty() ? tr.message : "недоступен";
                            txtLangStatus.setText(getString(R.string.translation_unavailable_message, msg));
                        }
                        txtLangStatus.setVisibility(View.VISIBLE);
                    });
                }
            }, "vot-status-check").start();
        }

        int translateVis = switchTranslate.isChecked() ? View.VISIBLE : View.GONE;
        groupVoice.setVisibility(translateVis);
        groupAudioMode.setVisibility(translateVis);
        groupSubtitles.setVisibility(translateVis);
    }

    private void startDownload() {
        cancelAnalysis();
        String videoId = UrlUtil.extractVideoId(urlField.getText().toString());
        if (videoId == null) {
            btnDownload.setEnabled(false);
            btnDownload.setVisibility(View.GONE);
            urlField.setError(getString(R.string.error_invalid_url));
            return;
        }
        btnDownload.setEnabled(false);
        io.github.idex.ytrdroid.data.settings.SettingsRepository settings =
                ((io.github.idex.ytrdroid.App) requireContext().getApplicationContext()).container().settings;
        DownloadTask task = new DownloadTask();
        if (info == null) {
            task.url = "https://www.youtube.com/watch?v=" + videoId;
            task.title = null;
            task.duration = 0;
            task.quality = settings.getDefaultQuality();
            if (task.quality == null || task.quality.isEmpty()) task.quality = "auto";
            task.translate = settings.isTranslateDefault();
            task.liveVoice = "live".equals(settings.getDefaultVoice());
            task.audioMode = "dual".equals(settings.getDefaultAudioMode()) ? "dual" : "mix";
            task.subtitles = false;
            task.language = null;
            int desiredHeight = 0;
            try { desiredHeight = Integer.parseInt(task.quality); } catch (NumberFormatException ignored) {}
            task.ext = desiredHeight > 1080 ? "mkv" : "mp4";
        } else {
            task.url = info.url;
            task.title = info.title;
            task.thumbnail = info.thumbnail;

            boolean isVideo = toggleType.getCheckedButtonId() == R.id.btn_video;
            if (isVideo) {
                int pos = spinnerQuality.getSelectedItemPosition();
                task.quality = (info.qualities != null && pos < info.qualities.size())
                        ? info.qualities.get(pos) : "auto";
                task.ext = "mp4";
                try {
                    if (Integer.parseInt(task.quality) > 1080) task.ext = "mkv";
                } catch (NumberFormatException ignored) { /* auto */ }
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
        }
        task.destinationPath = currentFolder;

        try {
            DownloadService.enqueue(requireContext(), task);
        } catch (io.github.idex.ytrdroid.domain.model.DownloadRequest.ValidationError e) {
            btnDownload.setEnabled(true);
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), R.string.downloading, Toast.LENGTH_SHORT).show();
        dismiss();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDownloads();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        cancelAnalysis();
        if (getActivity() instanceof ShareActivity) {
            getActivity().finish();
        }
    }

    private String formatFolderPath(String path) {
        if (path == null) return "Download/ytrd";
        if (path.startsWith("content://")) {
            String documentId = android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(path));
            int separator = documentId.indexOf(':');
            return separator >= 0 && separator + 1 < documentId.length()
                    ? documentId.substring(separator + 1) : documentId;
        }
        if (path.contains("Download")) {
            int idx = path.indexOf("Download");
            return path.substring(idx);
        }
        return path;
    }

    private void showFolderPicker() {
        folderPicker.launch(currentFolder != null && currentFolder.startsWith("content://")
                ? android.net.Uri.parse(currentFolder) : null);
    }
}
