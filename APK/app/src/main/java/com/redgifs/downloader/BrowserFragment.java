package com.redgifs.downloader;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private WebAppInterface webAppInterface;
    private static final String REDGIFS_URL = "https://www.redgifs.com";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_browser, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        webView = view.findViewById(R.id.webview);
        progressBar = view.findViewById(R.id.progress_bar);

        setupWebView();

        webAppInterface = new WebAppInterface(requireContext(), requireActivity());
        webView.addJavascriptInterface(webAppInterface, "Android");

        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            webAppInterface.setVideoDetectionListener(mainActivity);
            mainActivity.setWebAppInterface(webAppInterface);
        }

        DownloadService.setSharedWebView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(REDGIFS_URL);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (webView == null) return;
        if (hidden) {
            webView.onPause();
        } else {
            webView.onResume();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString()
                .replace("wv", "") + " RedgifsDownloader/1.9");

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectDownloadScript();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String id = extractVideoIdFromUrl(url);
                if (id != null && !id.isEmpty()) {
                    String finalId = id;
                    view.post(() -> {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).onVideoDetected(finalId);
                        }
                    });
                }
                return null;
            }
        });
    }

    private void injectDownloadScript() {
        try {
            InputStream is = requireContext().getAssets().open("inject.js");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            is.close();

            String script = sb.toString();
            webView.post(() -> webView.evaluateJavascript(script, null));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String extractVideoIdFromUrl(String url) {
        // media.redgifs.com/VIDEOID.hd.mp4 or .sd.mp4
        Matcher m = Pattern.compile("/([A-Za-z][A-Za-z0-9]{5,})\\.(?:hd|sd|mobile)\\.(?:mp4|m4s)").matcher(url);
        if (m.find()) return m.group(1);

        // media.redgifs.com/VIDEOID.mp4 or .m4s
        m = Pattern.compile("/([A-Za-z][A-Za-z0-9]{5,})\\.(?:mp4|m4s)").matcher(url);
        if (m.find()) return m.group(1);

        // api.redgifs.com/v2/gifs/VIDEOID (not search/trending/tags)
        m = Pattern.compile("/v2/gifs/([A-Za-z][A-Za-z0-9]{5,})").matcher(url);
        if (m.find()) {
            String id = m.group(1);
            if (!id.equals("search") && !id.equals("trending") && !id.equals("tags")) {
                return id;
            }
        }

        return null;
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null && !isHidden()) {
            webView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (webView != null && !isHidden()) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroyView();
    }
}
