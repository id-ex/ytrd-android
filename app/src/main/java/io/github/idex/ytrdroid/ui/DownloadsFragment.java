package io.github.idex.ytrdroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.service.DownloadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadsFragment extends Fragment implements DownloadService.Listener {
    private DownloadService service;

    // Active Card views
    private View activeCard;
    private TextView labelActive, txtNoDownloads, labelQueued, labelErrors;
    private ImageView activeThumb;
    private TextView activeTitle, activeInfo, activeStageText, activePercent, activeSize, activeSpeed;
    private ProgressBar activeProgressBar;
    private Button btnPauseActive, btnCancelActive;

    // Queued List
    private RecyclerView queuedList;
    private QueuedAdapter queuedAdapter;

    // Errors List
    private RecyclerView errorsList;
    private ErrorsAdapter errorsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getFragmentManager() != null) getFragmentManager().popBackStack();
        });

        activeCard = view.findViewById(R.id.active_card);
        labelActive = view.findViewById(R.id.label_active);
        txtNoDownloads = view.findViewById(R.id.txt_no_downloads);
        labelQueued = view.findViewById(R.id.label_queued);
        labelErrors = view.findViewById(R.id.label_errors);

        activeThumb = view.findViewById(R.id.active_thumb);
        activeTitle = view.findViewById(R.id.active_title);
        activeInfo = view.findViewById(R.id.active_info);
        activeStageText = view.findViewById(R.id.active_stage_text);
        activePercent = view.findViewById(R.id.active_percent);
        activeSize = view.findViewById(R.id.active_size);
        activeSpeed = view.findViewById(R.id.active_speed);
        activeProgressBar = view.findViewById(R.id.active_progress_bar);
        btnPauseActive = view.findViewById(R.id.btn_pause_active);
        btnCancelActive = view.findViewById(R.id.btn_cancel_active);

        queuedList = view.findViewById(R.id.queued_list);
        queuedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        queuedAdapter = new QueuedAdapter();
        queuedList.setAdapter(queuedAdapter);

        errorsList = view.findViewById(R.id.errors_list);
        errorsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        errorsAdapter = new ErrorsAdapter();
        errorsList.setAdapter(errorsAdapter);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null && activity.getDownloadService() != null) {
            onServiceConnected(activity.getDownloadService());
        }
    }

    public void onServiceConnected(DownloadService svc) {
        service = svc;
        service.setListener(this);
        updateUi();
    }

    @Override
    public void onTasksChanged() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateUi);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (service != null) service.setListener(null);
    }

    private void updateUi() {
        if (service == null || getView() == null) return;

        List<DownloadTask> all = service.getTasks();
        DownloadTask active = null;
        List<DownloadTask> queued = new ArrayList<>();
        List<DownloadTask> errors = new ArrayList<>();

        for (DownloadTask t : all) {
            if (active == null && (t.state == DownloadTask.State.DOWNLOADING
                    || t.state == DownloadTask.State.TRANSLATING
                    || t.state == DownloadTask.State.PROCESSING)) {
                active = t;
            } else if (t.state == DownloadTask.State.QUEUED || t.state == DownloadTask.State.PAUSED) {
                queued.add(t);
            } else if (t.state == DownloadTask.State.ERROR) {
                errors.add(t);
            }
        }

        // Active section
        if (active == null) {
            activeCard.setVisibility(View.GONE);
            labelActive.setVisibility(View.GONE);
            if (queued.isEmpty() && errors.isEmpty()) {
                txtNoDownloads.setVisibility(View.VISIBLE);
            } else {
                txtNoDownloads.setVisibility(View.GONE);
            }
        } else {
            txtNoDownloads.setVisibility(View.GONE);
            activeCard.setVisibility(View.VISIBLE);
            labelActive.setVisibility(View.VISIBLE);
            bindActiveTask(active);
        }

        // Queued section
        if (queued.isEmpty()) {
            labelQueued.setVisibility(View.GONE);
            queuedList.setVisibility(View.GONE);
        } else {
            labelQueued.setVisibility(View.VISIBLE);
            queuedList.setVisibility(View.VISIBLE);
            labelQueued.setText("В очереди · " + queued.size());
            queuedAdapter.setItems(queued);
        }

        // Errors section
        if (errors.isEmpty()) {
            labelErrors.setVisibility(View.GONE);
            errorsList.setVisibility(View.GONE);
        } else {
            labelErrors.setVisibility(View.VISIBLE);
            errorsList.setVisibility(View.VISIBLE);
            labelErrors.setText("Ошибки загрузки · " + errors.size());
            errorsAdapter.setItems(errors);
        }
    }

    private void bindActiveTask(DownloadTask task) {
        activeTitle.setText(task.title != null ? task.title : task.url);
        String info = (task.quality != null ? task.quality + "p" : "") +
                (task.translate ? " · RU" : " · Оригинал");
        activeInfo.setText(info);

        // Size and speed: only display during active downloading
        if (task.state == DownloadTask.State.PROCESSING || task.state == DownloadTask.State.TRANSLATING) {
            activeSize.setText("");
            activeSpeed.setText("");
        } else {
            activeSize.setText(task.formatSize());
            activeSpeed.setText(task.formatSpeed());
        }

        // Stage text under progress bar (red accent color)
        if (task.stageText != null && !task.stageText.isEmpty()) {
            activeStageText.setText(task.stageText);
        } else {
            activeStageText.setText(task.state == DownloadTask.State.TRANSLATING ? "Перевод…" : "Скачивание…");
        }

        // Progress bar and percentage
        if (task.progress < 0) {
            activePercent.setText("0%");
            activeProgressBar.setIndeterminate(true);
        } else {
            activePercent.setText(task.formatProgress());
            activeProgressBar.setIndeterminate(false);
            activeProgressBar.setProgress((int) task.progress);
        }

        // Thumbnail loading
        loadActiveThumbnail(task);

        // Pause button: moves active task to the end of the queue
        btnPauseActive.setText("Пауза");
        btnPauseActive.setOnClickListener(v -> {
            if (service != null) {
                service.pauseCurrentTask();
                updateUi();
            }
        });

        // Cancel button with confirmation dialog
        btnCancelActive.setOnClickListener(v -> showCancelConfirmation(task));
    }

    private void loadActiveThumbnail(DownloadTask task) {
        if (task.thumbnail == null || task.thumbnail.isEmpty()) return;

        new Thread(() -> {
            try {
                Request req = new Request.Builder().url(task.thumbnail).build();
                try (Response resp = new OkHttpClient().newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        Bitmap bmp = BitmapFactory.decodeStream(resp.body().byteStream());
                        if (getActivity() != null && bmp != null) {
                            getActivity().runOnUiThread(() -> {
                                if (activeThumb != null) {
                                    activeThumb.setImageBitmap(bmp);
                                }
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        }, "active-thumb").start();
    }

    private void showCancelConfirmation(DownloadTask task) {
        Context ctx = requireContext();
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_cancel_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            if (service != null) service.cancelTask(task);
            dialog.dismiss();
            updateUi();
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showErrorCardMenu(View anchor, DownloadTask task) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Повторить загрузку");
        popup.getMenu().add(0, 2, 1, "Удалить");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (service != null) service.retryTask(task);
                return true;
            } else if (item.getItemId() == 2) {
                confirmDeleteErrorTask(task);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDeleteErrorTask(DownloadTask task) {
        Context ctx = requireContext();
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_delete_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnWithFile = dialogView.findViewById(R.id.btn_delete_with_file);
        Button btnOk = dialogView.findViewById(R.id.btn_ok);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        btnWithFile.setOnClickListener(v -> {
            if (task.outputPath != null) {
                File f = new File(task.outputPath);
                if (f.exists()) f.delete();
            }
            if (service != null) service.removeTask(task);
            dialog.dismiss();
            updateUi();
            Toast.makeText(ctx, "Файл удалён", Toast.LENGTH_SHORT).show();
        });

        btnOk.setOnClickListener(v -> {
            if (service != null) service.removeTask(task);
            dialog.dismiss();
            updateUi();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ── Adapter for queued tasks ──

    class QueuedAdapter extends RecyclerView.Adapter<QueuedAdapter.VH> {
        private List<DownloadTask> items = new ArrayList<>();

        void setItems(List<DownloadTask> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DownloadTask t = items.get(position);
            holder.title.setText(t.title != null ? t.title : t.url);
            boolean isPaused = t.state == DownloadTask.State.PAUSED;
            String stage = isPaused ? " · ⏸ На паузе" : (t.stageText != null ? " · " + t.stageText : "");
            holder.info.setText((t.quality != null ? t.quality + "p" : "") + (t.translate ? " · RU" : "") + stage);
            if (isPaused) {
                holder.info.setTextColor(0xFFFFB300);
            } else {
                holder.info.setTextColor(0xFFB0B0B0);
            }

            holder.action.setVisibility(View.VISIBLE);
            holder.action.setOnClickListener(v -> {
                if (service != null) service.cancelTask(t);
            });

            // Клик по задаче на паузе возобновляет её
            holder.itemView.setOnClickListener(v -> {
                if (t.state == DownloadTask.State.PAUSED && service != null) {
                    service.resumeTask(t);
                    updateUi();
                }
            });

            holder.action.setVisibility(View.VISIBLE);
            holder.action.setOnClickListener(v -> {
                if (service != null) service.cancelTask(t);
            });

            if (t.thumbnail != null && !t.thumbnail.isEmpty()) {
                new Thread(() -> {
                    try {
                        Request req = new Request.Builder().url(t.thumbnail).build();
                        try (Response resp = new OkHttpClient().newCall(req).execute()) {
                            if (resp.isSuccessful() && resp.body() != null) {
                                Bitmap bmp = BitmapFactory.decodeStream(resp.body().byteStream());
                                if (getActivity() != null && bmp != null) {
                                    getActivity().runOnUiThread(() -> holder.thumb.setImageBitmap(bmp));
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }, "queued-thumb").start();
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView title, info;
            ImageView thumb;
            View action;

            VH(View v) {
                super(v);
                title = v.findViewById(R.id.item_title);
                info = v.findViewById(R.id.item_info);
                thumb = v.findViewById(R.id.item_thumb);
                action = v.findViewById(R.id.item_action);
            }
        }
    }

    // ── Adapter for error tasks ──

    class ErrorsAdapter extends RecyclerView.Adapter<ErrorsAdapter.VH> {
        private List<DownloadTask> items = new ArrayList<>();

        void setItems(List<DownloadTask> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DownloadTask t = items.get(position);
            holder.title.setText(t.title != null ? t.title : t.url);

            String err = t.errorMessage != null ? t.errorMessage : "Ошибка загрузки";
            holder.info.setText("❌ " + err);
            holder.info.setTextColor(0xFFE53935);

            if (t.thumbnail != null && !t.thumbnail.isEmpty()) {
                new Thread(() -> {
                    try {
                        Request req = new Request.Builder().url(t.thumbnail).build();
                        try (Response resp = new OkHttpClient().newCall(req).execute()) {
                            if (resp.isSuccessful() && resp.body() != null) {
                                Bitmap bmp = BitmapFactory.decodeStream(resp.body().byteStream());
                                if (getActivity() != null && bmp != null) {
                                    getActivity().runOnUiThread(() -> holder.thumb.setImageBitmap(bmp));
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }, "error-thumb").start();
            }

            // Долгий тап — всплывающее меню: Повторить загрузку / Удалить
            holder.itemView.setOnLongClickListener(v -> {
                showErrorCardMenu(v, t);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView title, info;
            ImageView thumb;

            VH(View v) {
                super(v);
                title = v.findViewById(R.id.item_title);
                info = v.findViewById(R.id.item_info);
                thumb = v.findViewById(R.id.item_thumb);
            }
        }
    }
}
