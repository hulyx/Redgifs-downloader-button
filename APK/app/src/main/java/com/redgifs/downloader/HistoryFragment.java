package com.redgifs.downloader;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.redgifs.downloader.adapter.HistoryAdapter;
import com.redgifs.downloader.db.DownloadDatabase;
import com.redgifs.downloader.model.DownloadItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment implements HistoryAdapter.OnItemActionListener {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private TextView emptyView;
    private MaterialButton clearAllBtn;
    private DownloadDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = DownloadDatabase.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.recycler_history);
        emptyView = view.findViewById(R.id.empty_view);
        clearAllBtn = view.findViewById(R.id.btn_clear_all);

        adapter = new HistoryAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        clearAllBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clear_history)
                    .setMessage(R.string.clear_history_confirm)
                    .setPositiveButton(R.string.yes, (d, w) -> {
                        executor.execute(() -> {
                            db.downloadDao().deleteAll();
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    adapter.setItems(new ArrayList<>());
                                    updateEmptyView();
                                });
                            }
                        });
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        });

        loadHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (db != null) loadHistory();
    }

    private void loadHistory() {
        executor.execute(() -> {
            List<DownloadItem> items = db.downloadDao().getAll();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.setItems(items);
                    updateEmptyView();
                });
            }
        });
    }

    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            clearAllBtn.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            clearAllBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPlay(DownloadItem item) {
        String localPath = item.getLocalFilePath();
        if (localPath != null && !localPath.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri;
                if (localPath.startsWith("/")) {
                    uri = Uri.parse("file://" + localPath);
                } else {
                    uri = Uri.parse("content://com.android.providers.downloads.documents/document/primary:Download/Redgifs/" + item.getFilename());
                }
                intent.setDataAndType(uri, "video/mp4");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.cannot_play, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), R.string.cannot_play, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(DownloadItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    executor.execute(() -> {
                        db.downloadDao().delete(item);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                adapter.removeItem(item);
                                updateEmptyView();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
