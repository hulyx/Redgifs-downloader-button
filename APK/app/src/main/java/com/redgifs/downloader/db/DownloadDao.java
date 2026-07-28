package com.redgifs.downloader.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.redgifs.downloader.model.DownloadItem;

import java.util.List;

@Dao
public interface DownloadDao {

    @Query("SELECT * FROM downloaditem ORDER BY timestamp DESC")
    List<DownloadItem> getAll();

    @Query("SELECT * FROM downloaditem WHERE videoId = :videoId LIMIT 1")
    DownloadItem getByVideoId(String videoId);

    @Query("SELECT COUNT(*) FROM downloaditem")
    int getCount();

    @Insert
    void insert(DownloadItem item);

    @Delete
    void delete(DownloadItem item);

    @Query("DELETE FROM downloaditem")
    void deleteAll();
}
