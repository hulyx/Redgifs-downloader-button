package com.redgifs.downloader;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
    private String detectedVideoId;
    private WebAppInterface webAppInterface;
    private MenuItem downloadNavItem;

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

        bottomNav.getMenu().getItem(0).setChecked(true);

        if (savedInstanceState == null) {
            loadFragment(new BrowserFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_browser) {
                fragment = new BrowserFragment();
            } else if (id == R.id.nav_download) {
                triggerDownload();
                bottomNav.getMenu().getItem(0).setChecked(true);
                return true;
            } else if (id == R.id.nav_history) {
                fragment = new HistoryFragment();
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

        if (downloadNavItem != null) {
            downloadNavItem.setIcon(R.drawable.ic_download_active);
        }

        SharedPreferences prefs = getSharedPreferences("redgifs_prefs", 0);
        boolean fabEnabled = prefs.getBoolean("show_fab", false);

        if (fabEnabled) {
            downloadButtonContainer.setVisibility(View.VISIBLE);
            downloadButton.setAlpha(1.0f);
            GradientDrawable bg = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.bg_download_fab_active);
            downloadButton.setBackground(bg);

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

        if (downloadNavItem != null) {
            downloadNavItem.setIcon(R.drawable.ic_download_white);
        }

        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
        }
        downloadButton.setAlpha(0.4f);
        GradientDrawable bg = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.bg_download_fab);
        downloadButton.setBackground(bg);
        downloadButtonContainer.setVisibility(View.GONE);
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
