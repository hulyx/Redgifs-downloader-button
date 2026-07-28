package com.redgifs.downloader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.redgifs.downloader.db.DownloadDatabase;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebAppInterface {

    private static final String TAG = "WebAppInterface";
    private final Context context;
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String cachedToken = null;
    private long tokenExpiry = 0;

    private VideoDetectionListener videoDetectionListener;

    public interface VideoDetectionListener {
        void onVideoDetected(String videoId);
        void onVideoLost();
    }

    public WebAppInterface(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    public void setVideoDetectionListener(VideoDetectionListener listener) {
        this.videoDetectionListener = listener;
    }

    @JavascriptInterface
    public boolean isHdOnly() {
        SharedPreferences prefs = context.getSharedPreferences("redgifs_prefs", 0);
        return prefs.getBoolean("hd_only", true);
    }

    @JavascriptInterface
    public void onVideoDetected(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        Log.d(TAG, "Video detected: " + videoId);
        if (videoDetectionListener != null) {
            activity.runOnUiThread(() -> videoDetectionListener.onVideoDetected(videoId));
        }
    }

    @JavascriptInterface
    public void onVideoLost() {
        if (videoDetectionListener != null) {
            activity.runOnUiThread(() -> videoDetectionListener.onVideoLost());
        }
    }

    @JavascriptInterface
    public void downloadVideo(String videoId, String title) {
        if (videoId == null || videoId.isEmpty()) return;

        executor.execute(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("redgifs_prefs", 0);
                boolean hdOnly = prefs.getBoolean("hd_only", true);

                String directUrl = getDirectVideoUrl(videoId, hdOnly);
                if (directUrl == null) {
                    showToast("Could not resolve video URL");
                    return;
                }

                String safeTitle = (title != null && !title.isEmpty())
                        ? title.replaceAll("[^a-zA-Z0-9_\\-]", "_")
                        : "redgifs_" + videoId;
                String filename = safeTitle + ".mp4";

                if (!Environment.isExternalStorageManager()) {
                    showToast("Storage permission required");
                    return;
                }

                android.content.Intent intent = new android.content.Intent(context, DownloadService.class);
                intent.putExtra(DownloadService.EXTRA_URL, directUrl);
                intent.putExtra(DownloadService.EXTRA_FILENAME, filename);
                intent.putExtra(DownloadService.EXTRA_VIDEO_ID, videoId);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }

                showToast("Download started: " + filename);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage());
                showToast("Download failed: " + e.getMessage());
            }
        });
    }

    private String getDirectVideoUrl(String videoId, boolean hdOnly) {
        try {
            String token = getToken();
            if (token != null) {
                URL apiUrl = new URL("https://api.redgifs.com/v2/gifs/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONObject gif = json.getJSONObject("gif");
                    JSONObject urls = gif.getJSONObject("urls");

                    if (!hdOnly && urls.has("sd")) return urls.getString("sd");
                    if (urls.has("hd")) return urls.getString("hd");
                    if (urls.has("sd")) return urls.getString("sd");
                }
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "API v2 failed: " + e.getMessage());
        }

        try {
            String m3u8Url = "https://api.redgifs.com/v2/gifs/" + videoId + "/hd.m3u8";
            String manifest = fetchUrl(m3u8Url);
            if (manifest != null && manifest.contains("#EXTM3U")) {
                String m4sUrl = extractM4sFromManifest(manifest);
                if (m4sUrl != null) return m4sUrl;
            }
        } catch (Exception e) {
            Log.e(TAG, "m3u8 failed: " + e.getMessage());
        }

        try {
            String capitalized = videoId.substring(0, 1).toUpperCase() + videoId.substring(1);
            return "https://media.redgifs.com/" + capitalized + ".m4s";
        } catch (Exception e) {
            return null;
        }
    }

    private String extractM4sFromManifest(String manifest) {
        String[] lines = manifest.split("\n");
        for (String line : lines) {
            if (line.contains(".m4s") || line.contains(".mp4")) {
                if (line.contains("EXT-X-MAP:URI=")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("URI=\"([^\"]+)\"")
                            .matcher(line);
                    if (m.find()) return m.group(1);
                }
                if (line.startsWith("http") || line.startsWith("/")) {
                    return line.trim();
                }
            }
        }
        return null;
    }

    private String fetchUrl(String urlStr) {
        try {
            String token = getToken();
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                return sb.toString();
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "fetchUrl failed: " + e.getMessage());
        }
        return null;
    }

    private synchronized String getToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }

        try {
            URL url = new URL("https://api.redgifs.com/v2/auth/temporary");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                cachedToken = json.getString("token");
                tokenExpiry = System.currentTimeMillis() + 23 * 60 * 60 * 1000;
                return cachedToken;
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Token fetch failed: " + e.getMessage());
        }
        return null;
    }

    private void showToast(String message) {
        activity.runOnUiThread(() ->
                android.widget.Toast.makeText(context, message,
                        android.widget.Toast.LENGTH_SHORT).show()
        );
    }
}
