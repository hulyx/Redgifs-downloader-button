package com.redgifs.downloader;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements WebAppInterface.VideoDetectionListener {

    private BottomNavigationView bottomNav;
    private View downloadButtonContainer;
    private ImageView downloadButton;
    private String detectedVideoId;
    private WebAppInterface webAppInterface;
    private boolean isOnBrowserTab = true;
    private String currentCenterIcon = "download";

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Toast.makeText(this, getString(R.string.permission_granted),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.permission_required),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (result.containsValue(Boolean.TRUE)) {
                    Toast.makeText(this, getString(R.string.permission_granted),
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        downloadButtonContainer = findViewById(R.id.download_button_container);
        downloadButton = findViewById(R.id.download_button);

        downloadButton.setOnClickListener(v -> triggerDownload());

        if (savedInstanceState == null) {
            loadFragment(new BrowserFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                isOnBrowserTab = false;
                updateCenterButton();
                loadFragment(new HistoryFragment());
                return true;
            } else if (id == R.id.nav_download) {
                handleCenterButtonClick();
                return true;
            } else if (id == R.id.nav_settings) {
                isOnBrowserTab = false;
                updateCenterButton();
                loadFragment(new SettingsFragment());
                return true;
            }
            return false;
        });

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentResumed(FragmentManager fm, Fragment f) {
                        if (f instanceof BrowserFragment) {
                            isOnBrowserTab = true;
                        } else {
                            isOnBrowserTab = false;
                        }
                        updateCenterButton();
                    }
                }, true);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (currentFragment instanceof BrowserFragment) {
                    BrowserFragment browser = (BrowserFragment) currentFragment;
                    if (browser.canGoBack()) {
                        browser.goBack();
                        return;
                    }
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        checkPermissions();
    }

    private void handleCenterButtonClick() {
        if (isOnBrowserTab) {
            triggerDownload();
        } else {
            isOnBrowserTab = true;
            bottomNav.getMenu().findItem(R.id.nav_download).setChecked(true);
            loadFragment(new BrowserFragment());
        }
    }

    private void updateCenterButton() {
        if (bottomNav == null) return;

        if (isOnBrowserTab) {
            if (!currentCenterIcon.equals("download")) {
                bottomNav.getMenu().findItem(R.id.nav_download)
                        .setIcon(R.drawable.ic_download_white);
                currentCenterIcon = "download";
                bottomNav.getMenu().findItem(R.id.nav_download).setTitle(R.string.nav_download);
                if (detectedVideoId != null && !detectedVideoId.isEmpty()) {
                    setNavDownloadActive(true);
                }
            }
        } else {
            if (!currentCenterIcon.equals("browser")) {
                cancelNavPulse();
                bottomNav.getMenu().findItem(R.id.nav_download)
                        .setIcon(R.drawable.ic_browser);
                currentCenterIcon = "browser";
                bottomNav.getMenu().findItem(R.id.nav_download).setTitle(R.string.nav_browser);
            }
        }
    }

    public void setWebAppInterface(WebAppInterface iface) {
        this.webAppInterface = iface;
    }

    public void triggerDownload() {
        if (detectedVideoId == null || detectedVideoId.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_video_detected), Toast.LENGTH_SHORT).show();
            return;
        }

        if (webAppInterface != null) {
            String title = "redgifs_" + detectedVideoId;
            webAppInterface.downloadVideo(detectedVideoId, title);
            playDownloadGlow();
        } else {
            Toast.makeText(this, getString(R.string.download_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    private void playDownloadGlow() {
        downloadButtonContainer.setVisibility(View.VISIBLE);

        downloadButton.setScaleX(0.6f);
        downloadButton.setScaleY(0.6f);
        downloadButton.setAlpha(0.0f);
        downloadButton.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_download_fab_active));

        downloadButton.animate()
                .scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> downloadButton.animate()
                        .alpha(0.0f)
                        .setStartDelay(600)
                        .setDuration(400)
                        .withEndAction(() -> downloadButtonContainer.setVisibility(View.GONE))
                        .start())
                .start();
    }

    @Override
    public void onVideoDetected(String videoId) {
        detectedVideoId = videoId;
        if (isOnBrowserTab) {
            setNavDownloadActive(true);
        }
    }

    @Override
    public void onVideoLost() {
        detectedVideoId = null;
        setNavDownloadActive(false);
    }

    private void setNavDownloadActive(boolean active) {
        if (bottomNav == null || !isOnBrowserTab) return;

        View downloadView = bottomNav.findViewById(R.id.nav_download);
        if (downloadView == null) return;

        cancelNavPulse();

        if (active) {
            bottomNav.getMenu().findItem(R.id.nav_download)
                    .setIcon(R.drawable.ic_download_active);

            downloadView.setScaleX(1.0f);
            downloadView.setScaleY(1.0f);

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(downloadView, "scaleX", 1.0f, 1.2f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(downloadView, "scaleY", 1.0f, 1.2f, 1.0f);
            scaleX.setDuration(500);
            scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleY.setDuration(500);
            scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleX.start();
            scaleY.start();
        } else {
            bottomNav.getMenu().findItem(R.id.nav_download)
                    .setIcon(R.drawable.ic_download_white);

            downloadView.setScaleX(1.0f);
            downloadView.setScaleY(1.0f);
        }
    }

    private void cancelNavPulse() {
        View downloadView = bottomNav.findViewById(R.id.nav_download);
        if (downloadView != null) {
            downloadView.animate().cancel();
            downloadView.setScaleX(1.0f);
            downloadView.setScaleY(1.0f);
        }
    }

    public void refreshFabVisibility() {
        if (detectedVideoId != null && !detectedVideoId.isEmpty()) {
            onVideoDetected(detectedVideoId);
        } else {
            onVideoLost();
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, getString(R.string.permission_storage_message),
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{
                        "android.permission.READ_EXTERNAL_STORAGE",
                        "android.permission.WRITE_EXTERNAL_STORAGE"
                });
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{"android.permission.POST_NOTIFICATIONS"});
            }
        }
    }
}
