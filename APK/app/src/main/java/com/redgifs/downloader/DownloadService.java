package com.redgifs.downloader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.redgifs.downloader.db.DownloadDatabase;
import com.redgifs.downloader.model.DownloadItem;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "redgifs_download_channel";
    private static final int NOTIFICATION_ID_BASE = 2000;
    private static final int MAX_RETRIES = 3;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int activeDownloads = 0;
    private NotificationManager notificationManager;
    private DownloadDatabase db;

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_FILENAME = "extra_filename";
    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_LOCAL_PATH = "extra_local_path";

    private static WebView sharedWebView;

    public static void setSharedWebView(WebView webView) {
        sharedWebView = webView;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        db = DownloadDatabase.getInstance(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        String filename = intent.getStringExtra(EXTRA_FILENAME);
        String videoId = intent.getStringExtra(EXTRA_VIDEO_ID);

        if (url == null || filename == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        activeDownloads++;
        startForeground(NOTIFICATION_ID_BASE, buildNotification(0, filename));

        executor.execute(() -> {
            String localPath = downloadFile(url, filename, videoId);
            activeDownloads--;

            if (localPath != null) {
                notifyJsComplete(videoId, filename, localPath);
                showCompletionNotification(filename);
            } else {
                notifyJsError(videoId, "Download failed");
                showFailureNotification(filename);
            }

            if (activeDownloads == 0) {
                stopForeground(true);
                stopSelf();
            } else {
                updateNotification(activeDownloads);
            }
        });

        return START_NOT_STICKY;
    }

    private String downloadFile(String urlStr, String filename, String videoId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                connection.setRequestProperty("Referer", "https://www.redgifs.com/");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != 200 && responseCode != 206) {
                    Log.w(TAG, "HTTP " + responseCode + " for " + urlStr);
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(1000L * attempt);
                        continue;
                    }
                    return null;
                }

                inputStream = connection.getInputStream();
                long fileSize = connection.getContentLength();
                String savedPath = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "video/mp4");
                    values.put(MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/Redgifs");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return null;

                    outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream == null) {
                        getContentResolver().delete(uri, null, null);
                        return null;
                    }

                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (fileSize > 0) {
                            notifyJsProgress(videoId, (int) (totalRead * 100 / fileSize));
                        }
                    }
                    outputStream.flush();
                    outputStream.close();
                    outputStream = null;

                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);

                    savedPath = Environment.DIRECTORY_DOWNLOADS + "/Redgifs/" + filename;
                } else {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File redgifsDir = new File(downloadsDir, "Redgifs");
                    if (!redgifsDir.exists()) redgifsDir.mkdirs();

                    File outputFile = new File(redgifsDir, filename);
                    outputStream = new java.io.FileOutputStream(outputFile);

                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (fileSize > 0) {
                            notifyJsProgress(videoId, (int) (totalRead * 100 / fileSize));
                        }
                    }
                    outputStream.flush();
                    outputStream.close();
                    outputStream = null;

                    savedPath = outputFile.getAbsolutePath();
                }

                DownloadItem item = new DownloadItem();
                item.setVideoId(videoId != null ? videoId : filename);
                item.setFilename(filename);
                item.setUrl(urlStr);
                item.setLocalFilePath(savedPath);
                item.setTimestamp(System.currentTimeMillis());
                item.setFileSize(fileSize);
                db.downloadDao().insert(item);

                return savedPath;

            } catch (Exception e) {
                Log.e(TAG, "Download failed (attempt " + attempt + "): " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ignored) {}
                }
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                } catch (Exception ignored) {}
                try {
                    if (outputStream != null) outputStream.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }
        return null;
    }

    private void notifyJsProgress(String videoId, int percent) {
        String js = String.format(
                "javascript:if(window.__onDownloadProgress)window.__onDownloadProgress('%s',%d)",
                escapeJs(videoId), percent);
        postToWebView(js);
    }

    private void notifyJsComplete(String videoId, String filename, String localPath) {
        String js = String.format(
                "javascript:if(window.__onDownloadComplete)window.__onDownloadComplete('%s','%s')",
                escapeJs(videoId), escapeJs(filename));
        postToWebView(js);
    }

    private void notifyJsError(String videoId, String error) {
        String js = String.format(
                "javascript:if(window.__onDownloadError)window.__onDownloadError('%s','%s')",
                escapeJs(videoId), escapeJs(error));
        postToWebView(js);
    }

    private void postToWebView(String js) {
        if (sharedWebView == null) return;
        mainHandler.post(() -> {
            try {
                sharedWebView.evaluateJavascript(js, null);
            } catch (Exception ignored) {}
        });
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            notificationManager.createNotificationChannel(channel);
        }
    }

    private android.app.Notification buildNotification(int progress, String filename) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.downloading))
                .setContentText(filename)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setProgress(100, progress, progress == 0)
                .build();
    }

    private void updateNotification(int activeCount) {
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(activeCount + " " + getString(R.string.downloads_active))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build();
        notificationManager.notify(NOTIFICATION_ID_BASE, notification);
    }

    private void showCompletionNotification(String filename) {
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.download_complete))
                .setContentText(filename)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(NOTIFICATION_ID_BASE + (int) (System.currentTimeMillis() % 10000),
                notification);
    }

    private void showFailureNotification(String filename) {
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.download_failed))
                .setContentText(filename)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(NOTIFICATION_ID_BASE + (int) (System.currentTimeMillis() % 10000),
                notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
