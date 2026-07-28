package com.redgifs.downloader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.redgifs.downloader.R;
import com.redgifs.downloader.model.DownloadItem;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemActionListener {
        void onPlay(DownloadItem item);
        void onDelete(DownloadItem item);
    }

    private List<DownloadItem> items;
    private final OnItemActionListener listener;

    public HistoryAdapter(List<DownloadItem> items, OnItemActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<DownloadItem> newItems) {
        this.items = newItems;
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
                .inflate(R.layout.item_download, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadItem item = items.get(position);

        holder.title.setText(item.getFilename());
        holder.details.setText(item.getFormattedDate() + " - " + item.getFormattedSize());

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onPlay(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, details;
        ImageButton btnPlay, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_title);
            details = itemView.findViewById(R.id.item_details);
            btnPlay = itemView.findViewById(R.id.item_btn_play);
            btnDelete = itemView.findViewById(R.id.item_btn_delete);
        }
    }
}
