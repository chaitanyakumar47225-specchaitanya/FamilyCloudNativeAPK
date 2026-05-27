package com.familycloud.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    public static final String PREFS = "family_cloud_prefs";
    public static final String KEY_BASE_URL = "base_url";
    public static final String KEY_LOCAL_URL = "local_url";
    public static final String KEY_MODE = "mode";
    public static final String KEY_COOKIE = "cookie";

    public static final String ONLINE_URL = "http://100.109.57.8:3000";
    public static final String DEFAULT_LOCAL_URL = "http://192.168.1.50:3000";

    private SharedPreferences prefs;
    private WebView webView;
    private ProgressBar progressBar;
    private View topNavView;
    private ValueCallback<Uri[]> filePathCallback;
    private String currentBaseUrl = ONLINE_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showStartupModeScreen();
    }

    private String cleanUrl(String url) {
        if (url == null || url.trim().isEmpty()) return ONLINE_URL;
        url = url.trim();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(105, 35, 10));
        b.setPadding(14, 10, 14, 10);
        return b;
    }

    private TextView makeText(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 40, 28, 42);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 5, 5));
        return root;
    }

    private void showStartupModeScreen() {
        LinearLayout root = baseRoot();

        TextView title = makeText("Family Cloud", 30, Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = makeText("Choose connection mode.", 15, Color.rgb(255, 210, 122));
        subtitle.setGravity(Gravity.CENTER);

        Button online = makeButton("Online Mode");
        Button offline = makeButton("Offline / Local Mode");

        TextView onlineInfo = makeText("Online fixed URL: " + ONLINE_URL, 13, Color.rgb(255, 210, 122));

        online.setOnClickListener(v -> {
            currentBaseUrl = ONLINE_URL;
            prefs.edit()
                    .putString(KEY_MODE, "online")
                    .putString(KEY_BASE_URL, ONLINE_URL)
                    .apply();

            showWebView();
            fcRestoreCookieForBase(ONLINE_URL);
                webView.loadUrl(ONLINE_URL + "/dashboard");
        });

        offline.setOnClickListener(v -> showOfflineChoiceScreen());

        root.addView(title);
        root.addView(subtitle);
        root.addView(onlineInfo);
        root.addView(online);
        root.addView(offline);

        setContentView(root);
    }

    private void showOfflineChoiceScreen() {
        LinearLayout root = baseRoot();
        String savedLocal = prefs.getString(KEY_LOCAL_URL, DEFAULT_LOCAL_URL);

        TextView title = makeText("Offline / Local Mode", 28, Color.WHITE);
        TextView info = makeText("Phone and laptop must be on same Wi-Fi/hotspot/LAN.", 15, Color.rgb(255, 210, 122));
        TextView saved = makeText("Saved local URL: " + savedLocal, 14, Color.rgb(255, 210, 122));

        Button continueSaved = makeButton("Continue With Saved URL");
        Button editUrl = makeButton("Edit Local URL");
        Button back = makeButton("Back");

        continueSaved.setOnClickListener(v -> {
            String url = cleanUrl(prefs.getString(KEY_LOCAL_URL, DEFAULT_LOCAL_URL));
            currentBaseUrl = url;

            prefs.edit()
                    .putString(KEY_MODE, "local")
                    .putString(KEY_BASE_URL, url)
                    .putString(KEY_LOCAL_URL, url)
                    .apply();

            showWebView();
            fcRestoreCookies(url);
                    webView.loadUrl(url + "/dashboard");
        });

        editUrl.setOnClickListener(v -> showLocalUrlEditScreen());
        back.setOnClickListener(v -> showStartupModeScreen());

        root.addView(title);
        root.addView(info);
        root.addView(saved);
        root.addView(continueSaved);
        root.addView(editUrl);
        root.addView(back);

        setContentView(root);
    }

    private void showLocalUrlEditScreen() {
        LinearLayout root = baseRoot();

        TextView title = makeText("Edit Local URL", 28, Color.WHITE);
        TextView info = makeText("Example: http://192.168.1.45:3000", 15, Color.rgb(255, 210, 122));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setText(prefs.getString(KEY_LOCAL_URL, DEFAULT_LOCAL_URL));
        input.setHint("http://local-ip:3000");
        input.setBackgroundColor(Color.rgb(40, 12, 12));
        input.setPadding(18, 18, 18, 18);

        Button save = makeButton("Save And Continue");
        Button back = makeButton("Back");

        save.setOnClickListener(v -> {
            String url = cleanUrl(input.getText().toString());
            currentBaseUrl = url;

            prefs.edit()
                    .putString(KEY_MODE, "local")
                    .putString(KEY_BASE_URL, url)
                    .putString(KEY_LOCAL_URL, url)
                    .apply();

            showWebView();
            fcRestoreCookies(url);
                    webView.loadUrl(url + "/dashboard");
        });

        back.setOnClickListener(v -> showOfflineChoiceScreen());

        root.addView(title);
        root.addView(info);
        root.addView(input);
        root.addView(save);
        root.addView(back);

        setContentView(root);
    }

    private void saveCookieForBase(String baseUrl) {
        try {
            String cookie = CookieManager.getInstance().getCookie(baseUrl);
            if (cookie != null && !cookie.trim().isEmpty()) {
                prefs.edit().putString(KEY_COOKIE, cookie).apply();
            }
        } catch (Exception ignored) {}
    }

    private void showWebView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 5, 5));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(6, 4, 6, 4);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.rgb(18, 5, 5));

        Button back = makeButton("Back");
        Button home = makeButton("Home");
        Button gallery = makeButton("Gallery");
        Button upload = makeButton("Upload");
        Button account = makeButton("Account");
        Button autoSync = makeButton("Auto Sync");
        Button mode = makeButton("Mode");

        topBar.addView(back);
        topBar.addView(home);
        topBar.addView(gallery);
        topBar.addView(upload);
        topBar.addView(account);
        topBar.addView(autoSync);
        topBar.addView(mode);

        HorizontalScrollView topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);
        topScroll.addView(topBar);

        topNavView = topScroll;
        topNavView.setVisibility(View.GONE);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        webView = new WebView(this);

        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );

        root.addView(topScroll);
        root.addView(progressBar);
        root.addView(webView, webParams);

        setContentView(root);
        setupWebView();

        back.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        home.setOnClickListener(v -> webView.loadUrl(currentBaseUrl + "/dashboard"));
        gallery.setOnClickListener(v -> webView.loadUrl(currentBaseUrl + "/gallery"));
        upload.setOnClickListener(v -> webView.loadUrl(currentBaseUrl + "/dashboard#upload"));
        account.setOnClickListener(v -> webView.loadUrl(currentBaseUrl + "/account"));

        autoSync.setOnClickListener(v -> {
            saveCookieForBase(currentBaseUrl);
                    fcSaveCurrentCookies();
                    fcSaveCookieForBase(currentBaseUrl);
                    android.webkit.CookieManager.getInstance().flush();
            Intent intent = new Intent(this, BackupActivity.class);
            startActivity(intent);
        });

        mode.setOnClickListener(v -> showStartupModeScreen());
    }

    private void updateNativeNavVisibility(String url) {
        if (topNavView == null || url == null) return;

        String lower = url.toLowerCase();

        if (lower.contains("/login") || lower.endsWith(":3000/") || lower.endsWith(":3000")) {
            topNavView.setVisibility(View.GONE);
        } else {
            topNavView.setVisibility(View.VISIBLE);
        }
    }

    private void showServerErrorScreen(String failedUrl) {
        LinearLayout root = baseRoot();

        TextView title = makeText("Server Not Reachable", 28, Color.WHITE);
        TextView info = makeText(
                "Could not open:\n" + failedUrl + "\n\nCheck server, Wi-Fi, hotspot, Tailscale, or local URL.",
                15,
                Color.rgb(255, 210, 122)
        );

        Button retry = makeButton("Retry");
        Button switchMode = makeButton("Switch Mode");
        Button autoSync = makeButton("Auto Sync");
        Button close = makeButton("Close App");

        retry.setOnClickListener(v -> {
            showWebView();
            fcRestoreCookieForBase(currentBaseUrl);
        webView.loadUrl(currentBaseUrl + "/dashboard");
        });

        switchMode.setOnClickListener(v -> showStartupModeScreen());
        autoSync.setOnClickListener(v -> startActivity(new Intent(this, BackupActivity.class)));
        close.setOnClickListener(v -> finish());

        root.addView(title);
        root.addView(info);
        root.addView(retry);
        root.addView(switchMode);
        root.addView(autoSync);
        root.addView(close);

        setContentView(root);
    }

    private void setupWebView() {
        // FC_PERSISTENT_WEBVIEW_DATA_V1
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        webView.addJavascriptInterface(new FamilyCloudDeviceBridge(), "FamilyCloudDevice");

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        android.webkit.CookieManager.getInstance().flush();


        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateNativeNavVisibility(url);
                injectFamilyCloudDeviceButtons();

                if (url != null && url.toLowerCase().contains("/dashboard")) {
                    saveCookieForBase(currentBaseUrl);
                    fcSaveCurrentCookies();
                    fcSaveCookieForBase(currentBaseUrl);
                    android.webkit.CookieManager.getInstance().flush();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);

                if (android.os.Build.VERSION.SDK_INT >= 21 && request != null && request.isForMainFrame()) {
                    showServerErrorScreen(request.getUrl().toString());
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                try {
                    startActivityForResult(Intent.createChooser(intent, "Select files"), 101);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }

                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);

                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);

                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading from Family Cloud");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType)
                );

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            showStartupModeScreen();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 101) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, intent);
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK && intent != null) {
                ClipData clipData = intent.getClipData();

                if (clipData != null && clipData.getItemCount() > 0) {
                    results = new Uri[clipData.getItemCount()];

                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        results[i] = clipData.getItemAt(i).getUri();
                    }
                } else if (intent.getData() != null) {
                    results = new Uri[]{intent.getData()};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void fcFlushWebViewData() {
        try {
            android.webkit.CookieManager.getInstance().flush();
        } catch (Exception ignored) {}
    }
private void injectFamilyCloudDeviceButtons() {
        String js =
                "(function() {" +
                "  if (window.__fcApkDeviceButtonsInjected) return;" +
                "  window.__fcApkDeviceButtonsInjected = true;" +
                "  function ready() {" +
                "    if (!window.FamilyCloudDevice) return;" +
                "    var path = String(location.pathname || '').toLowerCase();" +
                "    if (!(path.indexOf('/dashboard') >= 0 || path.indexOf('/upload') >= 0)) return;" +
                "    if (document.getElementById('fcApkDeviceTools')) return;" +
                "    var box = document.createElement('div');" +
                "    box.id = 'fcApkDeviceTools';" +
                "    box.style.cssText = 'margin:14px;padding:14px;border-radius:18px;background:linear-gradient(135deg,#421f14,#160a08);border:1px solid rgba(245,158,11,.55);box-shadow:0 8px 20px rgba(0,0,0,.25);';" +
                "    box.innerHTML = '' +" +
                "      '<h3 style=\"margin:0 0 10px;color:#fbbf24;font-size:20px;\">APK Device Tools</h3>' +" +
                "      '<button id=\"fcApkBackupBtn\" type=\"button\" style=\"width:100%;margin:6px 0;border:0;border-radius:14px;padding:13px;font-weight:900;color:white;background:linear-gradient(90deg,#dc2626,#f59e0b);\">Background Backup</button>' +" +
                "      '<button id=\"fcApkFreeSpaceBtn\" type=\"button\" style=\"width:100%;margin:6px 0;border:0;border-radius:14px;padding:13px;font-weight:900;color:white;background:linear-gradient(90deg,#7f1d1d,#ef4444);\">Free Up Gallery Space</button>'; " +
                "    var target = document.querySelector('form[action=\"/upload\"], form, main, .container, body');" +
                "    if (target && target.parentNode && target !== document.body) {" +
                "      target.parentNode.insertBefore(box, target);" +
                "    } else {" +
                "      document.body.insertBefore(box, document.body.firstChild);" +
                "    }" +
                "    document.getElementById('fcApkBackupBtn').onclick = function() {" +
                "      window.FamilyCloudDevice.enableBackgroundSync(location.origin);" +
                "    };" +
                "    document.getElementById('fcApkFreeSpaceBtn').onclick = function() {" +
                "      window.FamilyCloudDevice.freeSpaceOnDevice();" +
                "    };" +
                "  }" +
                "  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready);" +
                "  else ready();" +
                "})();";

        webView.evaluateJavascript(js, null);
    }


    private class FamilyCloudDeviceBridge {
        @JavascriptInterface
        public void enableBackgroundSync(String serverUrl) {
            runOnUiThread(() -> {
                try {
                    prefs.edit()
                            .putString(KEY_BASE_URL, cleanUrl(serverUrl))
                            .apply();
                } catch (Exception ignored) {}

                startActivity(new Intent(MainActivity.this, BackupActivity.class));
            });
        }

        @JavascriptInterface
        public void runSyncNow(String serverUrl) {
            runOnUiThread(() -> {
                try {
                    prefs.edit()
                            .putString(KEY_BASE_URL, cleanUrl(serverUrl))
                            .apply();
                } catch (Exception ignored) {}

                startActivity(new Intent(MainActivity.this, BackupActivity.class));
            });
        }

        @JavascriptInterface
        public void freeSpaceOnDevice() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, BackupActivity.class)));
        }

        @JavascriptInterface
        public void openBackupSettings() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, BackupActivity.class)));
        }
    }


    private void fcRestoreCookieForBase(String baseUrl) {
        try {
            String cookie = prefs.getString(KEY_COOKIE, "");
            if (cookie != null && !cookie.trim().isEmpty() && baseUrl != null && !baseUrl.trim().isEmpty()) {
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setCookie(baseUrl, cookie);
                CookieManager.getInstance().flush();
            }
        } catch (Exception ignored) {}
    }

    private void fcSaveCookieForBase(String baseUrl) {
        try {
            if (baseUrl == null || baseUrl.trim().isEmpty()) return;

            String cookie = CookieManager.getInstance().getCookie(baseUrl);

            if (cookie != null && !cookie.trim().isEmpty()) {
                prefs.edit()
                        .putString(KEY_COOKIE, cookie)
                        .putString(KEY_BASE_URL, cleanUrl(baseUrl))
                        .apply();

                CookieManager.getInstance().flush();
            }
        } catch (Exception ignored) {}
    }

    private void fcFlushWebViewDataStrong() {
        try {
            String base = prefs.getString(KEY_BASE_URL, currentBaseUrl);
            fcSaveCookieForBase(base);
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}
    }

    private void fcSaveCurrentCookies() {
        try {
            String base = currentBaseUrl;

            if (base == null || base.trim().isEmpty()) {
                base = prefs.getString(KEY_BASE_URL, "");
            }

            if (base != null && !base.trim().isEmpty()) {
                String cookie = CookieManager.getInstance().getCookie(base);

                if (cookie != null && !cookie.trim().isEmpty()) {
                    prefs.edit()
                            .putString(KEY_COOKIE, cookie)
                            .putString(KEY_BASE_URL, cleanUrl(base))
                            .apply();
                }
            }

            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}
    }

    private void fcRestoreCookies(String baseUrl) {
        try {
            if (baseUrl == null || baseUrl.trim().isEmpty()) return;

            String cookie = prefs.getString(KEY_COOKIE, "");

            if (cookie != null && !cookie.trim().isEmpty()) {
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setCookie(baseUrl, cookie);
                CookieManager.getInstance().flush();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        fcSaveCurrentCookies();
        super.onPause();
    }

    @Override
    protected void onStop() {
        fcSaveCurrentCookies();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        fcSaveCurrentCookies();
        super.onDestroy();
    }

}
