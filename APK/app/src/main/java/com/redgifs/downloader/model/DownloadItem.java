package com.redgifs.downloader.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "downloaditem")
public class DownloadItem {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String videoId;
    private String filename;
    private String url;
    private String localFilePath;
    private long timestamp;
    private long fileSize;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLocalFilePath() { return localFilePath; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",
                java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    public String getFormattedSize() {
        if (fileSize <= 0) return "Unknown size";
        double size = fileSize;
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex]);
    }
}
