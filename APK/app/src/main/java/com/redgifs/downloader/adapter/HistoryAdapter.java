package com.redgifs.downloader.adapter;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
        holder.playOverlay.setVisibility(View.VISIBLE);

        String cacheKey = item.getFilename();
        if (thumbnailCache.containsKey(cacheKey)) {
            holder.thumbnail.setImageBitmap(thumbnailCache.get(cacheKey));
            holder.playOverlay.setVisibility(View.VISIBLE);
        } else if (item.getLocalFilePath() != null && !item.getLocalFilePath().isEmpty()) {
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
        String path = item.getLocalFilePath();
        if (path == null) return;

        thumbExecutor.execute(() -> {
            Bitmap thumb = null;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                if (path.startsWith("/") || path.startsWith("file://")) {
                    String filePath = path.startsWith("file://") ? Uri.parse(path).getPath() : path;
                    mmr.setDataSource(filePath);
                } else {
                    mmr.setDataSource(holder.itemView.getContext(), Uri.parse(path));
                }
                thumb = mmr.getFrameAtTime(1_000_000);
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
