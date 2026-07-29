package com.redgifs.downloader;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements WebAppInterface.VideoDetectionListener {

    private BottomNavigationView bottomNav;
    private View downloadButtonContainer;
    private ImageView downloadButton;
    private ObjectAnimator pulseAnimator;
    private ObjectAnimator navPulseAnimator;
    private String detectedVideoId;
    private WebAppInterface webAppInterface;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

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
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                fragment = new HistoryFragment();
            } else if (id == R.id.nav_download) {
                triggerDownload();
                return true;
            } else if (id == R.id.nav_settings) {
                fragment = new SettingsFragment();
            }
            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });

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
        } else {
            Toast.makeText(this, getString(R.string.download_failed_generic), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onVideoDetected(String videoId) {
        detectedVideoId = videoId;

        setNavDownloadActive(true);

        SharedPreferences prefs = getSharedPreferences("redgifs_prefs", 0);
        boolean fabEnabled = prefs.getBoolean("show_fab", false);

        if (fabEnabled) {
            downloadButtonContainer.setVisibility(View.VISIBLE);
            downloadButton.setAlpha(1.0f);
            downloadButton.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_download_fab_active));

            if (pulseAnimator == null || !pulseAnimator.isRunning()) {
                pulseAnimator = ObjectAnimator.ofFloat(downloadButton, "alpha", 1.0f, 0.5f, 1.0f);
                pulseAnimator.setDuration(1200);
                pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                pulseAnimator.setInterpolator(new LinearInterpolator());
                pulseAnimator.start();
            }
        }
    }

    @Override
    public void onVideoLost() {
        detectedVideoId = null;

        setNavDownloadActive(false);

        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
        }
        downloadButton.setAlpha(0.4f);
        downloadButton.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_download_fab));
        downloadButtonContainer.setVisibility(View.GONE);
    }

    private void setNavDownloadActive(boolean active) {
        if (bottomNav == null) return;

        View downloadView = bottomNav.findViewById(R.id.nav_download);
        if (downloadView == null) return;

        if (navPulseAnimator != null && navPulseAnimator.isRunning()) {
            navPulseAnimator.cancel();
        }

        if (active) {
            bottomNav.getMenu().findItem(R.id.nav_download)
                    .setIcon(R.drawable.ic_download_active);

            downloadView.animate().cancel();
            downloadView.setScaleX(1.0f);
            downloadView.setScaleY(1.0f);

            navPulseAnimator = ObjectAnimator.ofFloat(downloadView, "scaleX", 1.0f, 1.25f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(downloadView, "scaleY", 1.0f, 1.25f, 1.0f);
            navPulseAnimator.setDuration(800);
            navPulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            navPulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleY.setDuration(800);
            scaleY.setRepeatCount(ObjectAnimator.INFINITE);
            scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            navPulseAnimator.start();
            scaleY.start();
        } else {
            bottomNav.getMenu().findItem(R.id.nav_download)
                    .setIcon(R.drawable.ic_download_white);

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
