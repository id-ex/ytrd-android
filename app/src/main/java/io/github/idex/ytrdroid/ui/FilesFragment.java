package io.github.idex.ytrdroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import io.github.idex.ytrdroid.App;
import io.github.idex.ytrdroid.R;
import io.github.idex.ytrdroid.data.persistence.ArtifactEntity;

public class FilesFragment extends Fragment {
    private FileAdapter adapter;
    private TextView emptyText;

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
            });
        });
    }

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

    private void showCardMenu(View anchor, ArtifactEntity item) {
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

    private void repeatDownload(ArtifactEntity item) {
        MainActivity act = (MainActivity) getActivity();
        if (act == null) return;

        String query = item.url != null && !item.url.isEmpty() ? item.url : item.title;
        ParamsFragment.newInstance(query).show(act.getSupportFragmentManager(), "download-sheet");
    }

    private void confirmDelete(ArtifactEntity item) {
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

        // "С файлом": удаляем и файл, и запись из базы данных
        btnWithFile.setOnClickListener(v -> {
            try {
                if (item.uri != null && item.uri.startsWith("content://")) {
                    if (!android.provider.DocumentsContract.deleteDocument(ctx.getContentResolver(), Uri.parse(item.uri)))
                        throw new java.io.IOException("Провайдер не удалил файл");
                } else if (item.uri != null) {
                    File f = new File(item.uri);
                    if (f.exists() && !f.delete()) throw new java.io.IOException("Нет доступа к файлу");
                }
            } catch (Exception e) {
                Toast.makeText(ctx, "Не удалось удалить файл: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (item.thumbUri != null) {
                File t = new File(item.thumbUri);
                if (t.exists()) t.delete();
            }
            App app = (App) ctx.getApplicationContext();
            app.container().artifacts.delete(item.id, () -> {
                if (getActivity() != null) getActivity().runOnUiThread(this::loadFiles);
            });
            dialog.dismiss();
            Toast.makeText(ctx, "Файл удалён", Toast.LENGTH_SHORT).show();
        });

        // "ОК": скрываем запись из истории (файл на диске остаётся)
        btnOk.setOnClickListener(v -> {
            App app = (App) ctx.getApplicationContext();
            app.container().artifacts.hide(item.id, () -> {
                if (getActivity() != null) getActivity().runOnUiThread(this::loadFiles);
            });
            dialog.dismiss();
        });

        // "Отмена"
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ── Adapter ──

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private List<ArtifactEntity> items = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

        void setItems(List<ArtifactEntity> list) {
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

                itemView.setOnClickListener(v -> openWithSystemApp(item));
                itemView.setOnLongClickListener(v -> {
                    showCardMenu(v, item);
                    return true;
                });
            }
        }
    }
}
