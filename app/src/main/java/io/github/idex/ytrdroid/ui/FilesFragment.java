package io.github.idex.ytrdroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.model.HistoryItem;
import io.github.idex.ytrdroid.model.HistoryManager;

public class FilesFragment extends Fragment {
    private FileAdapter adapter;
    private TextView emptyText;
    private static final LruCache<String, Bitmap> thumbCache = new LruCache<>(50);

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
        adapter = new FileAdapter();
        rv.setAdapter(adapter);

        loadFiles();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFiles();
    }

    public void loadFiles() {
        if (getContext() == null) return;
        List<HistoryItem> items = HistoryManager.getInstance(requireContext()).getAll();
        if (emptyText != null) {
            emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (adapter != null) {
            adapter.setItems(items);
        }
    }

    private void openWithSystemApp(HistoryItem item) {
        if (item.filePath == null) return;
        File file = new File(item.filePath);
        if (!file.exists()) {
            Toast.makeText(getContext(), "Файл не найден на диске", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Context ctx = requireContext();
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String name = file.getName().toLowerCase();
            String mime = (name.endsWith(".mp3") || name.endsWith(".m4a")) ? "audio/*" : "video/*";
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Открыть через"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Не удалось открыть: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showCardMenu(View anchor, HistoryItem item) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Повторить загрузку");
        popup.getMenu().add(0, 2, 1, "Удалить");

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 1) {
                repeatDownload(item);
                return true;
            } else if (menuItem.getItemId() == 2) {
                confirmDelete(item);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void repeatDownload(HistoryItem item) {
        MainActivity act = (MainActivity) getActivity();
        if (act == null) return;

        // Вставляем реальную ссылку на видео, если есть, иначе название
        String query = item.url != null && !item.url.isEmpty() ? item.url : item.title;
        ParamsFragment.newInstance(query).show(act.getSupportFragmentManager(), "download-sheet");
    }

    private void confirmDelete(HistoryItem item) {
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

        // "С файлом": удаляем и файл, и запись из истории
        btnWithFile.setOnClickListener(v -> {
            HistoryManager.getInstance(ctx).deleteWithFile(item);
            loadFiles();
            dialog.dismiss();
            Toast.makeText(ctx, "Файл удалён", Toast.LENGTH_SHORT).show();
        });

        // "ОК": удаляем ТОЛЬКО запись из истории (файл на диске остаётся)
        btnOk.setOnClickListener(v -> {
            HistoryManager.getInstance(ctx).remove(item.id);
            loadFiles();
            dialog.dismiss();
        });

        // "Отмена"
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ── Adapter ──

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private List<HistoryItem> items = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

        void setItems(List<HistoryItem> list) {
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

            void bind(HistoryItem item) {
                title.setText(item.title != null ? item.title : "Видео");

                long mb = item.fileSize / (1024 * 1024);
                String dateStr = dateFormat.format(new Date(item.timestamp > 0 ? item.timestamp : System.currentTimeMillis()));
                String q = item.quality != null ? item.quality : "MP4";
                String isRu = item.isTranslated ? " · RU" : "";
                info.setText(q + " · " + mb + " МБ · " + dateStr + isRu);

                loadThumbnail(item, thumb);

                itemView.setOnClickListener(v -> openWithSystemApp(item));
                itemView.setOnLongClickListener(v -> {
                    showCardMenu(v, item);
                    return true;
                });
            }

            private void loadThumbnail(HistoryItem item, ImageView target) {
                if (item.filePath == null) return;
                String path = item.filePath;
                Bitmap cached = thumbCache.get(path);
                if (cached != null) {
                    target.setImageBitmap(cached);
                    return;
                }

                // 1. Проверяем наличие файла превью рядом
                if (item.thumbPath != null && new File(item.thumbPath).exists()) {
                    Bitmap bmp = android.graphics.BitmapFactory.decodeFile(item.thumbPath);
                    if (bmp != null) {
                        thumbCache.put(path, bmp);
                        target.setImageBitmap(bmp);
                        return;
                    }
                }

                File f = new File(path);
                File thumbFile = new File(f.getParentFile(), "." + f.getName() + ".thumb.jpg");
                if (thumbFile.exists()) {
                    Bitmap bmp = android.graphics.BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                    if (bmp != null) {
                        thumbCache.put(path, bmp);
                        target.setImageBitmap(bmp);
                        return;
                    }
                }

                target.setImageDrawable(null);

                // 2. Системная генерация миниатюры
                new Thread(() -> {
                    Bitmap bmp = null;
                    String lower = f.getName().toLowerCase();
                    if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")) {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                bmp = android.media.ThumbnailUtils.createVideoThumbnail(
                                        f,
                                        new android.util.Size(192, 108),
                                        null
                                );
                            }
                        } catch (Exception ignored) {}

                        if (bmp == null) {
                            try {
                                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                                mmr.setDataSource(path);
                                bmp = mmr.getFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                                if (bmp == null) bmp = mmr.getFrameAtTime(0);
                                mmr.release();
                            } catch (Exception ignored) {}
                        }
                    }

                    if (bmp != null) {
                        thumbCache.put(path, bmp);
                        Bitmap finalBmp = bmp;
                        target.post(() -> target.setImageBitmap(finalBmp));
                    }
                }, "thumb-extractor").start();
            }
        }
    }
}
