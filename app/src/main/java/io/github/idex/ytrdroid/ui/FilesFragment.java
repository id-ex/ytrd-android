package io.github.idex.ytrdroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.data.persistence.ArtifactEntity;

@android.annotation.SuppressLint({"SetTextI18n", "NotifyDataSetChanged"})
public class FilesFragment extends Fragment {
    private static final String KEY_SELECTED_IDS = "selected_ids";

    private FileAdapter adapter;
    private TextView emptyText;
    private final Set<String> selectedIds = new HashSet<>();
    private OnBackPressedCallback backCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        emptyText = view.findViewById(R.id.empty_text);
        RecyclerView rv = view.findViewById(R.id.files_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setItemAnimator(new SlideRightItemAnimator());
        adapter = new FileAdapter();
        rv.setAdapter(adapter);

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitSelectionMode();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(KEY_SELECTED_IDS);
            if (saved != null && !saved.isEmpty()) {
                selectedIds.addAll(saved);
                backCallback.setEnabled(true);
                updateSelectionUi();
            }
        }

        setupItemTouchHelper(rv);
        loadFiles();
    }

    private void setupItemTouchHelper(RecyclerView rv) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            private final Drawable repeatIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_replay);
            private final Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete);
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rectF = new RectF();

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (isSelectionMode()) {
                    return 0; // Disable swipe during multi-select mode
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || adapter == null) return;
                ArtifactEntity item = adapter.getItem(pos);
                adapter.notifyItemChanged(pos);

                if (direction == ItemTouchHelper.RIGHT) {
                    repeatDownload(item);
                } else if (direction == ItemTouchHelper.LEFT) {
                    confirmDelete(Collections.singletonList(item), false);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    float cornerRadius = getResources().getDimension(R.dimen.card_corner_radius);
                    int margin = getResources().getDimensionPixelSize(R.dimen.swipe_action_icon_margin);
                    int iconSize = getResources().getDimensionPixelSize(R.dimen.swipe_action_icon_size);
                    int itemTop = itemView.getTop();
                    int itemBottom = itemView.getBottom();
                    int itemLeft = itemView.getLeft();
                    int itemRight = itemView.getRight();

                    c.save();
                    if (dX > 0) {
                        // Swipe right -> repeat download
                        c.clipRect(itemLeft, itemTop, itemLeft + (int) dX, itemBottom);
                        paint.setColor(ContextCompat.getColor(requireContext(), R.color.swipe_repeat_bg));
                        rectF.set(itemLeft, itemTop, itemRight, itemBottom);
                        c.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

                        if (repeatIcon != null) {
                            int top = itemTop + (itemBottom - itemTop - iconSize) / 2;
                            int left = itemLeft + margin;
                            repeatIcon.setBounds(left, top, left + iconSize, top + iconSize);
                            repeatIcon.setTint(Color.WHITE);
                            repeatIcon.draw(c);
                        }
                    } else if (dX < 0) {
                        // Swipe left -> delete (red background)
                        c.clipRect(itemRight + (int) dX, itemTop, itemRight, itemBottom);
                        paint.setColor(ContextCompat.getColor(requireContext(), R.color.swipe_delete_bg));
                        rectF.set(itemLeft, itemTop, itemRight, itemBottom);
                        c.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

                        if (deleteIcon != null) {
                            int top = itemTop + (itemBottom - itemTop - iconSize) / 2;
                            int right = itemRight - margin;
                            deleteIcon.setBounds(right - iconSize, top, right, top + iconSize);
                            deleteIcon.setTint(Color.WHITE);
                            deleteIcon.draw(c);
                        }
                    }
                    c.restore();
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(rv);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFiles();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!selectedIds.isEmpty()) {
            outState.putStringArrayList(KEY_SELECTED_IDS, new ArrayList<>(selectedIds));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null && !getActivity().isChangingConfigurations()) {
            exitSelectionMode();
        }
    }

    public void loadFiles() {
        if (getContext() == null) return;
        App app = (App) requireContext().getApplicationContext();
        app.container().artifacts.getAllVisible(entities -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (emptyText != null) {
                    emptyText.setVisibility(entities.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (adapter != null) {
                    adapter.setItems(entities);
                }
                if (isSelectionMode()) {
                    Set<String> existing = new HashSet<>();
                    for (ArtifactEntity e : entities) {
                        existing.add(e.id);
                    }
                    selectedIds.retainAll(existing);
                    if (isSelectionMode()) {
                        updateSelectionUi();
                    } else {
                        exitSelectionMode();
                    }
                }
            });
        });
    }

    // ── Selection mode management ──

    public boolean isSelectionMode() {
        return !selectedIds.isEmpty();
    }

    private void animateCardSelection(View cardView) {
        cardView.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> cardView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                        .start())
                .start();
    }

    private void enterSelectionMode(ArtifactEntity item) {
        selectedIds.add(item.id);
        if (backCallback != null) backCallback.setEnabled(true);
        updateSelectionUi();
    }

    private void toggleSelection(ArtifactEntity item) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id);
            if (selectedIds.isEmpty()) {
                exitSelectionMode();
                return;
            }
        } else {
            selectedIds.add(item.id);
        }
        updateSelectionUi();
    }

    public void exitSelectionMode() {
        exitSelectionMode(true);
    }

    public void exitSelectionMode(boolean notifyAdapter) {
        selectedIds.clear();
        if (backCallback != null) backCallback.setEnabled(false);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSelectionModeActive(false, null, null, null);
        }
        if (notifyAdapter && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateSelectionUi() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSelectionModeActive(
                    isSelectionMode(),
                    this::onDeleteFabClicked,
                    this::onSelectAllClicked,
                    this::exitSelectionMode
            );
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void onSelectAllClicked() {
        selectedIds.clear();
        if (adapter != null) {
            for (ArtifactEntity item : adapter.items) {
                selectedIds.add(item.id);
            }
        }
        updateSelectionUi();
    }

    private void onDeleteFabClicked() {
        List<ArtifactEntity> selected = getSelectedItems();
        if (!selected.isEmpty()) {
            confirmDelete(selected, true);
        }
    }

    private List<ArtifactEntity> getSelectedItems() {
        List<ArtifactEntity> list = new ArrayList<>();
        if (adapter != null) {
            for (ArtifactEntity item : adapter.items) {
                if (selectedIds.contains(item.id)) {
                    list.add(item);
                }
            }
        }
        return list;
    }

    // ── Actions ──

    private void openWithSystemApp(ArtifactEntity item) {
        if (item.uri == null) return;
        File file = new File(item.uri);
        boolean document = item.uri.startsWith("content://");
        if (!document && !file.exists()) {
            Toast.makeText(getContext(), "Файл не найден на диске", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Context ctx = requireContext();
            Uri uri = document ? Uri.parse(item.uri)
                    : FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String name = file.getName().toLowerCase(Locale.ROOT);
            String mime = (name.endsWith(".mp3") || name.endsWith(".m4a")) ? "audio/*" : "video/*";
            if (document) {
                String resolved = ctx.getContentResolver().getType(uri);
                if (resolved != null) mime = resolved;
            }
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Открыть через"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Не удалось открыть: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void repeatDownload(ArtifactEntity item) {
        MainActivity act = (MainActivity) getActivity();
        if (act == null) return;

        String query = item.url != null && !item.url.isEmpty() ? item.url : item.title;
        ParamsFragment.newInstance(query).show(act.getSupportFragmentManager(), "download-sheet");
    }

    private void confirmDelete(List<ArtifactEntity> items, boolean isSelectionMode) {
        if (items.isEmpty() || getContext() == null) return;
        Context ctx = requireContext();
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_delete_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView messageView = dialogView.findViewById(R.id.dialog_message);
        Button btnWithFile = dialogView.findViewById(R.id.btn_delete_with_file);
        Button btnOk = dialogView.findViewById(R.id.btn_ok);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        int count = items.size();
        if (isSelectionMode || count > 1) {
            titleView.setText("Удалить видео (" + count + ")?");
            messageView.setText("Записи будут удалены из списка.");
            btnWithFile.setText(count > 1 ? "С файлами" : "С файлом");
        } else {
            titleView.setText("Удалить видео?");
            messageView.setText("Запись будет удалена из списка.");
            btnWithFile.setText("С файлом");
        }

        btnWithFile.setOnClickListener(v -> {
            dialog.dismiss();
            executeDelete(items, true);
        });

        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            executeDelete(items, false);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void executeDelete(List<ArtifactEntity> items, boolean deletePhysicalFiles) {
        Context ctx = getContext();
        if (ctx == null || items.isEmpty()) return;

        App app = (App) ctx.getApplicationContext();
        List<String> ids = new ArrayList<>(items.size());
        for (ArtifactEntity item : items) {
            ids.add(item.id);
        }

        // Instantly slide deleted cards to the right and collapse empty space
        if (adapter != null) {
            List<ArtifactEntity> remaining = new ArrayList<>(adapter.items);
            remaining.removeAll(items);
            adapter.setItems(remaining);
            if (emptyText != null) {
                emptyText.setVisibility(remaining.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
        exitSelectionMode(false);

        app.container().backgroundExecutor.execute(() -> {
            if (deletePhysicalFiles) {
                for (ArtifactEntity item : items) {
                    try {
                        if (item.uri != null && item.uri.startsWith("content://")) {
                            android.provider.DocumentsContract.deleteDocument(
                                    ctx.getContentResolver(), Uri.parse(item.uri));
                        } else if (item.uri != null) {
                            File f = new File(item.uri);
                            if (f.exists()) f.delete();
                        }
                    } catch (Exception ignored) {}
                    if (item.thumbUri != null) {
                        try {
                            File t = new File(item.thumbUri);
                            if (t.exists()) t.delete();
                        } catch (Exception ignored) {}
                    }
                }
                app.container().artifacts.deleteAll(ids, () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            loadFiles();
                            Toast.makeText(ctx, ids.size() == 1 ? "Файл удалён" : "Файлы удалены",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } else {
                app.container().artifacts.hideAll(ids, () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(this::loadFiles);
                    }
                });
            }
        });
    }

    // ── Adapter ──

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        final List<ArtifactEntity> items = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

        void setItems(List<ArtifactEntity> list) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return items.size();
                }

                @Override
                public int getNewListSize() {
                    return list.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return items.get(oldItemPosition).id.equals(list.get(newItemPosition).id);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    ArtifactEntity a = items.get(oldItemPosition);
                    ArtifactEntity b = list.get(newItemPosition);
                    return (a.title == null ? b.title == null : a.title.equals(b.title))
                            && (a.quality == null ? b.quality == null : a.quality.equals(b.quality))
                            && a.fileSize == b.fileSize
                            && a.timestamp == b.timestamp
                            && a.isTranslated == b.isTranslated;
                }
            });
            items.clear();
            items.addAll(list);
            diff.dispatchUpdatesTo(this);
        }

        ArtifactEntity getItem(int position) {
            return items.get(position);
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
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            private final TextView title, info;
            private final ImageView thumb;

            VH(View v) {
                super(v);
                title = v.findViewById(R.id.item_title);
                info = v.findViewById(R.id.item_info);
                thumb = v.findViewById(R.id.item_thumb);
            }

            void bind(ArtifactEntity item) {
                title.setText(item.title != null ? item.title : "Видео");

                long mb = item.fileSize / (1024 * 1024);
                String dateStr = dateFormat.format(new Date(item.timestamp > 0 ? item.timestamp : System.currentTimeMillis()));
                String q = item.quality != null ? item.quality : "MP4";
                String isRu = item.isTranslated ? " · RU" : "";
                info.setText(q + " · " + mb + " МБ · " + dateStr + isRu);

                String thumbSource = (item.thumbUri != null && (item.thumbUri.startsWith("https://")
                        || item.thumbUri.startsWith("content://") || new File(item.thumbUri).exists()))
                        ? item.thumbUri : item.uri;
                io.github.idex.ytrdroid.util.ThumbnailLoader.getInstance().load(thumbSource, thumb);

                boolean selected = selectedIds.contains(item.id);
                if (itemView instanceof MaterialCardView) {
                    MaterialCardView card = (MaterialCardView) itemView;
                    if (selected) {
                        card.setCardBackgroundColor(ContextCompat.getColor(card.getContext(), R.color.card_selected));
                        card.setStrokeColor(ContextCompat.getColor(card.getContext(), R.color.card_selected_stroke));
                        card.setStrokeWidth(card.getResources().getDimensionPixelSize(R.dimen.card_selected_stroke_width));
                    } else {
                        card.setCardBackgroundColor(ContextCompat.getColor(card.getContext(), R.color.card_dark));
                        card.setStrokeColor(Color.TRANSPARENT);
                        card.setStrokeWidth(0);
                    }
                }

                itemView.setOnClickListener(v -> {
                    if (isSelectionMode()) {
                        animateCardSelection(v);
                        toggleSelection(item);
                    } else {
                        openWithSystemApp(item);
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    animateCardSelection(v);
                    if (!isSelectionMode()) {
                        enterSelectionMode(item);
                    } else {
                        toggleSelection(item);
                    }
                    return true;
                });
            }
        }
    }
}
