package com.redgifs.downloader;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements WebAppInterface.VideoDetectionListener {

    private BottomNavigationView bottomNav;
    private View downloadButtonContainer;
    private ImageView downloadButton;
    private String detectedVideoId;
    private WebAppInterface webAppInterface;
    private BrowserFragment browserFragment;

    private static final String TAG_BROWSER = "tag_browser";
    private static final String TAG_HISTORY = "tag_history";
    private static final String TAG_SETTINGS = "tag_settings";

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

        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            browserFragment = new BrowserFragment();
            fm.beginTransaction()
                    .add(R.id.fragment_container, browserFragment, TAG_BROWSER)
                    .commit();
        } else {
            browserFragment = (BrowserFragment) fm.findFragmentByTag(TAG_BROWSER);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                switchFragment(new HistoryFragment(), TAG_HISTORY);
                updateCenterButton(false);
                return true;
            } else if (id == R.id.nav_download) {
                handleCenterButtonClick();
                return true;
            } else if (id == R.id.nav_settings) {
                switchFragment(new SettingsFragment(), TAG_SETTINGS);
                updateCenterButton(false);
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                Fragment current = fm.findFragmentById(R.id.fragment_container);

                if (current instanceof BrowserFragment) {
                    BrowserFragment browser = (BrowserFragment) current;
                    if (browser.canGoBack()) {
                        browser.goBack();
                        return;
                    }
                } else {
                    showBrowserFragment();
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        checkPermissions();
    }

    private void switchFragment(Fragment newFragment, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        Fragment current = fm.findFragmentById(R.id.fragment_container);
        if (current != null) {
            ft.hide(current);
        }

        Fragment existing = fm.findFragmentByTag(tag);
        if (existing != null) {
            ft.show(existing);
        } else {
            ft.add(R.id.fragment_container, newFragment, tag);
        }

        ft.commit();
    }

    private void showBrowserFragment() {
        if (browserFragment == null) return;

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        Fragment current = fm.findFragmentById(R.id.fragment_container);
        if (current != null && current != browserFragment) {
            ft.hide(current);
        }

        ft.show(browserFragment);
        ft.commit();

        bottomNav.getMenu().findItem(R.id.nav_download).setChecked(true);
        updateCenterButton(true);
    }

    private void handleCenterButtonClick() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_container);

        if (current instanceof BrowserFragment) {
            triggerDownload();
        } else {
            showBrowserFragment();
        }
    }

    private void updateCenterButton(boolean onBrowser) {
        if (bottomNav == null) return;

        if (onBrowser) {
            bottomNav.getMenu().findItem(R.id.nav_download)
                    .setIcon(R.drawable.ic_download_white);
            bottomNav.getMenu().findItem(R.id.nav_download).setTitle(R.string.nav_download);
            if (detectedVideoId != null && !detectedVideoId.isEmpty()) {
                setNavDownloadActive(true);
            }
        } else {
            cancelNavPulse();
            bottomNav.getMenu().findItem(R.id.nav_download)
                    .setIcon(R.drawable.ic_browser);
            bottomNav.getMenu().findItem(R.id.nav_download).setTitle(R.string.nav_browser);
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
            SharedPreferences prefs = getSharedPreferences("redgifs_prefs", 0);
            if (prefs.getBoolean("show_fab", false)) {
                playDownloadGlow();
            }
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

        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_container);
        if (current instanceof BrowserFragment) {
            setNavDownloadActive(true);
        }
    }

    @Override
    public void onVideoLost() {
        detectedVideoId = null;
        setNavDownloadActive(false);
    }

    private void setNavDownloadActive(boolean active) {
        if (bottomNav == null) return;

        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_container);
        if (!(current instanceof BrowserFragment)) return;

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
