package com.redgifs.downloader.adapter;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.redgifs.downloader.R;
import com.redgifs.downloader.model.DownloadItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemActionListener {
        void onPlay(DownloadItem item);
        void onDelete(DownloadItem item);
    }

    private List<DownloadItem> items;
    private final OnItemActionListener listener;
    private final Map<String, Bitmap> thumbnailCache = new HashMap<>();
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public HistoryAdapter(List<DownloadItem> items, OnItemActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<DownloadItem> newItems) {
        this.items = newItems;
        thumbnailCache.clear();
        notifyDataSetChanged();
    }

    public void removeItem(DownloadItem item) {
        int pos = items.indexOf(item);
        if (pos >= 0) {
            items.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadItem item = items.get(position);

        holder.title.setText(item.getFilename());
        holder.details.setText(item.getFormattedDate() + " · " + item.getFormattedSize());

        holder.thumbnail.setImageBitmap(null);
        holder.thumbnail.setBackgroundColor(0xFF1E1E1E);
        holder.playOverlay.setVisibility(View.VISIBLE);

        String cacheKey = item.getFilename();
        if (thumbnailCache.containsKey(cacheKey)) {
            holder.thumbnail.setImageBitmap(thumbnailCache.get(cacheKey));
            holder.playOverlay.setVisibility(View.VISIBLE);
        } else if (item.getFilename() != null && !item.getFilename().isEmpty()) {
            loadThumbnail(holder, item, cacheKey);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPlay(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    private void loadThumbnail(ViewHolder holder, DownloadItem item, String cacheKey) {
        String filename = item.getFilename();
        if (filename == null) return;

        thumbExecutor.execute(() -> {
            Bitmap thumb = null;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                Uri mediaUri = queryMediaStoreUri(holder.itemView.getContext(), filename);
                if (mediaUri != null) {
                    mmr.setDataSource(holder.itemView.getContext(), mediaUri);
                    thumb = mmr.getFrameAtTime(500_000);
                }
            } catch (Exception e) {
                // thumbnail extraction failed
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }

            if (thumb != null) {
                thumbnailCache.put(cacheKey, thumb);
            }

            final Bitmap finalThumb = thumb;
            mainHandler.post(() -> {
                if (finalThumb != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && items.get(pos).getFilename().equals(cacheKey)) {
                        holder.thumbnail.setImageBitmap(finalThumb);
                        holder.playOverlay.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    private Uri queryMediaStoreUri(android.content.Context context, String filename) {
        String[] projection = {MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
        String[] selectionArgs = {filename};
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/Redgifs/";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection + " AND " + MediaStore.Downloads.RELATIVE_PATH + " = ?",
                new String[]{filename, relativePath},
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception ignored) {}

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail, playOverlay;
        TextView title, details;
        ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.item_thumbnail);
            playOverlay = itemView.findViewById(R.id.item_play_overlay);
            title = itemView.findViewById(R.id.item_title);
            details = itemView.findViewById(R.id.item_details);
            btnDelete = itemView.findViewById(R.id.item_btn_delete);
        }
    }
}
