package com.redgifs.downloader;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
    private SwipeRefreshLayout swipeRefresh;
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
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        adapter = new HistoryAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadHistory);
        swipeRefresh.setColorSchemeColors(0xFFFF5252);

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
                    swipeRefresh.setRefreshing(false);
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
        String filename = item.getFilename();
        if (filename == null || filename.isEmpty()) {
            Toast.makeText(requireContext(), R.string.cannot_play, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri mediaUri = queryMediaStoreUri(filename);
        if (mediaUri != null) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(mediaUri, "video/mp4");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.cannot_play, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), R.string.cannot_play, Toast.LENGTH_SHORT).show();
        }
    }

    private Uri queryMediaStoreUri(String filename) {
        String[] projection = {MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
        String[] selectionArgs = {filename};
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/Redgifs/";

        try (Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection + " AND " + MediaStore.Downloads.RELATIVE_PATH + " = ?",
                new String[]{filename, relativePath},
                null)) {

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            // query failed
        }

        try (Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            // query failed
        }

        return null;
    }

    @Override
    public void onDelete(DownloadItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    executor.execute(() -> {
                        if (item.getLocalFilePath() != null && !item.getLocalFilePath().isEmpty()) {
                            deleteMediaStoreFile(item.getFilename());
                        }
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

    private void deleteMediaStoreFile(String filename) {
        try {
            String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
            String[] selectionArgs = {filename};
            requireContext().getContentResolver().delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection,
                    selectionArgs
            );
        } catch (Exception e) {
            // delete failed
        }
    }
}
