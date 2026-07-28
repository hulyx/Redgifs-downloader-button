# Redgifs Downloader - Android APK

## Description
Android application that provides a built-in browser for RedGifs with HD video download buttons injected on every video.

## Features
- **Built-in Browser** : Navigate redgifs.com directly in the app
- **Download Buttons** : Automatically injected on every video player
- **HD Downloads** : Fetches the highest quality MP4 via Redgifs API v2
- **Download History** : SQLite-backed history of all downloads
- **Settings** : Configure HD-only mode, auto-inject toggle
- **Android 11+** : Uses MediaStore API for scoped storage compatibility
- **Dark Theme** : Redgifs-inspired dark UI

## Architecture

```
app/src/main/
├── java/com/redgifs/downloader/
│   ├── MainActivity.java          # Main activity with bottom nav
│   ├── BrowserFragment.java       # WebView-based browser
│   ├── HistoryFragment.java       # Download history list
│   ├── SettingsFragment.java      # App settings
│   ├── WebAppInterface.java       # JS bridge (downloadVideo → native)
│   ├── DownloadService.java       # Foreground service for downloads
│   ├── adapter/
│   │   └── HistoryAdapter.java    # RecyclerView adapter
│   ├── db/
│   │   ├── DownloadDatabase.java  # Room database
│   │   └── DownloadDao.java       # Database access
│   └── model/
│       └── DownloadItem.java      # Data model
├── assets/
│   └── inject.js                  # JavaScript injection (buttons + video detection)
└── res/
    ├── layout/                    # UI layouts
    ├── values/                    # Strings, colors, themes
    ├── drawable/                  # Vector icons
    ├── menu/                      # Bottom navigation menu
    └── xml/                       # Network security config
```

## Build Instructions

### Option 1: Android Studio (Recommended)
1. Open Android Studio
2. File → Open → Select the `APK` folder
3. Wait for Gradle sync
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Command Line
Prerequisites:
- JDK 17+
- Android SDK (with build-tools 34.0.0, platform-tools)
- Set `ANDROID_HOME` environment variable

```bash
cd APK
gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Install on Device
1. Enable "Install from unknown sources" in Android Settings > Security
2. Transfer the APK to your device
3. Open the APK file to install

## How It Works

### Video Detection (inject.js)
The JavaScript injection runs on every page load and:
1. Uses a MutationObserver to detect new video containers
2. Targets `.GifPreviewV2`, `.TapTracker`, `.PlayerV2` elements
3. Extracts video IDs from: container IDs, poster URLs, page URLs, meta tags, data attributes, image alt text
4. Injects styled download buttons with state management

### Download Flow
1. User taps download button
2. JavaScript calls `Android.downloadVideo(videoId, title)` via JS interface
3. `WebAppInterface` fetches a temporary auth token from Redgifs API
4. Calls `/v2/gifs/{id}` to get direct HD MP4 URL
5. Starts `DownloadService` with URL, filename, video ID
6. Service downloads via HttpURLConnection and saves to `Downloads/Redgifs/` using MediaStore (Android 11+)
7. Records the download in Room database for history

### Fallback Strategies
If the primary API fails:
1. Tries direct media URL construction (`media.redgifs.com/{Id}.m4s`)
2. Up to 3 retries with exponential backoff

## Permissions
- `INTERNET` - Network access for browsing and downloading
- `MANAGE_EXTERNAL_STORAGE` - Save to Downloads folder (Android 11+)
- `FOREGROUND_SERVICE` - Background download service
- `POST_NOTIFICATIONS` - Download progress notifications (Android 13+)
