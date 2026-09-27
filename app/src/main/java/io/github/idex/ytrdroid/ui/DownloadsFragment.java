package io.github.idex.ytrdroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.domain.model.TaskSnapshot;
import io.github.idex.ytrdroid.model.DownloadTask;
import io.github.idex.ytrdroid.model.LegacyTaskMapper;
import io.github.idex.ytrdroid.service.DownloadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadsFragment extends Fragment {
    private final java.util.function.Consumer<List<TaskSnapshot>> snapshotObserver = snapshots -> {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> updateUi(snapshots));
        }
    };

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
    }

    @Override
    public void onResume() {
        super.onResume();
        App app = (App) requireContext().getApplicationContext();
        app.container().observe(snapshotObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        App app = (App) requireContext().getApplicationContext();
        app.container().detach(snapshotObserver);
    }

    private void updateUi(List<TaskSnapshot> snapshots) {
        if (getView() == null || snapshots == null) return;

        List<DownloadTask> all = new ArrayList<>();
        for (TaskSnapshot s : snapshots) {
            all.add(LegacyTaskMapper.fromSnapshot(s));
        }
        Collections.reverse(all);

        DownloadTask active = null;
        List<DownloadTask> queued = new ArrayList<>();
        List<DownloadTask> errors = new ArrayList<>();

        for (DownloadTask t : all) {
            if (active == null && (t.state == DownloadTask.State.ANALYZING
                    || t.state == DownloadTask.State.DOWNLOADING
                    || t.state == DownloadTask.State.TRANSLATING
                    || t.state == DownloadTask.State.PROCESSING
                    || t.state == DownloadTask.State.PAUSING
                    || t.state == DownloadTask.State.CANCELLING)) {
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
            boolean allPaused = true;
            for (DownloadTask q : queued) {
                if (q.state != DownloadTask.State.PAUSED) {
                    allPaused = false;
                    break;
                }
            }
            if (allPaused) {
                labelQueued.setText(getString(R.string.status_paused_count, queued.size()));
            } else {
                labelQueued.setText(getString(R.string.status_queued_count, queued.size()));
            }
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

        boolean isPaused = task.state == DownloadTask.State.PAUSED;
        boolean stopping = task.state == DownloadTask.State.PAUSING
                || task.state == DownloadTask.State.CANCELLING;

        // Size and speed
        if (isPaused || task.state == DownloadTask.State.ANALYZING
                || task.state == DownloadTask.State.PROCESSING || task.state == DownloadTask.State.TRANSLATING) {
            activeSize.setText("");
            activeSpeed.setText("");
        } else {
            activeSize.setText(task.formatSize());
            activeSpeed.setText(task.formatSpeed());
        }

        // Progress bar and percentage: hidden when paused as requested
        if (isPaused) {
            activeProgressBar.setVisibility(View.GONE);
            activePercent.setVisibility(View.GONE);
        } else {
            activeProgressBar.setVisibility(View.VISIBLE);
            activePercent.setVisibility(View.VISIBLE);
            if (task.state == DownloadTask.State.ANALYZING || task.progress < 0) {
                activePercent.setText("");
                activeProgressBar.setIndeterminate(true);
            } else {
                activePercent.setText(task.formatProgress());
                activeProgressBar.setIndeterminate(false);
                activeProgressBar.setProgress((int) task.progress);
            }
        }

        // Stage text under progress bar
        if (isPaused) {
            activeStageText.setText("На паузе");
        } else if (task.state == DownloadTask.State.ANALYZING) {
            activeStageText.setText("Анализ видео");
        } else if (task.stageText != null && !task.stageText.isEmpty()) {
            activeStageText.setText(task.stageText);
        } else {
            activeStageText.setText(task.state == DownloadTask.State.TRANSLATING ? "Перевод…" : "Скачивание…");
        }

        // Thumbnail loading
        loadActiveThumbnail(task);

        btnPauseActive.setEnabled(!stopping);
        btnCancelActive.setEnabled(task.state != DownloadTask.State.CANCELLING);

        if (isPaused) {
            btnPauseActive.setText("Продолжить");
            btnPauseActive.setOnClickListener(v -> {
                Context ctx = requireContext();
                androidx.core.content.ContextCompat.startForegroundService(
                        ctx, new Intent(ctx, DownloadService.class));
                App app = (App) ctx.getApplicationContext();
                app.container().downloads.resume(task.getRequest().id);
            });
        } else {
            btnPauseActive.setText("Пауза");
            btnPauseActive.setOnClickListener(v -> {
                App app = (App) requireContext().getApplicationContext();
                app.container().downloads.pause(task.getRequest().id);
            });
        }

        // Cancel button with confirmation dialog
        btnCancelActive.setOnClickListener(v -> showCancelConfirmation(task));
    }

    private void loadActiveThumbnail(DownloadTask task) {
        io.github.idex.ytrdroid.util.ThumbnailLoader.getInstance().load(task.thumbnail, activeThumb);
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
            App app = (App) requireContext().getApplicationContext();
            app.container().downloads.cancel(task.getRequest().id);
            dialog.dismiss();
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
                Context ctx = requireContext();
                androidx.core.content.ContextCompat.startForegroundService(
                        ctx, new Intent(ctx, DownloadService.class));
                App app = (App) ctx.getApplicationContext();
                app.container().downloads.retry(task.getRequest().id);
                return true;
            } else if (item.getItemId() == 2) {
                App app = (App) requireContext().getApplicationContext();
                app.container().downloads.remove(task.getRequest().id);
                return true;
            }
            return false;
        });
        popup.show();
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
            String stage = isPaused ? " · На паузе" : (t.stageText != null ? " · " + t.stageText : "");
            holder.info.setText((t.quality != null ? t.quality + "p" : "") + (t.translate ? " · RU" : "") + stage);
            holder.info.setTextColor(0xFFB0B0B0);

            holder.action.setVisibility(View.VISIBLE);
            holder.action.setOnClickListener(v -> {
                App app = (App) holder.itemView.getContext().getApplicationContext();
                app.container().downloads.cancel(t.getRequest().id);
            });

            // Клик по задаче на паузе возобновляет её
            holder.itemView.setOnClickListener(v -> {
                if (t.state == DownloadTask.State.PAUSED) {
                    Context ctx = holder.itemView.getContext();
                    androidx.core.content.ContextCompat.startForegroundService(
                            ctx, new Intent(ctx, DownloadService.class));
                    App app = (App) ctx.getApplicationContext();
                    app.container().downloads.resume(t.getRequest().id);
                }
            });

            io.github.idex.ytrdroid.util.ThumbnailLoader.getInstance().load(t.thumbnail, holder.thumb);
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

            io.github.idex.ytrdroid.util.ThumbnailLoader.getInstance().load(t.thumbnail, holder.thumb);

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
