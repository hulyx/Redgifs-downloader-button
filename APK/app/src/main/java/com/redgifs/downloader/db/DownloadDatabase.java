package com.redgifs.downloader.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.redgifs.downloader.model.DownloadItem;

@Database(entities = {DownloadItem.class}, version = 2, exportSchema = false)
public abstract class DownloadDatabase extends RoomDatabase {

    private static volatile DownloadDatabase INSTANCE;

    public abstract DownloadDao downloadDao();

    public static DownloadDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DownloadDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            DownloadDatabase.class,
                            "redgifs_downloads.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
