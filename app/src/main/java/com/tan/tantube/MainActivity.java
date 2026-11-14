package com.tan.tantube;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String HOME = "https://m.youtube.com/";
    static WeakReference<MainActivity> current = new WeakReference<>(null);

    private FrameLayout root;
    private WebView web;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String injectJs = "";
    private boolean docStartScript = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        injectJs = readAsset("inject.js");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F0F0F"));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Bỏ dấu hiệu "WebView" để YouTube coi như trình duyệt Chrome bình thường
        String ua = s.getUserAgentString()
                .replace("; wv)", ")")
                .replaceAll("Version/\\d+\\.\\d+ ", "");
        s.setUserAgentString(ua);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "TanTube");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Set<String> origins = new HashSet<>();
            origins.add("https://m.youtube.com");
            origins.add("https://www.youtube.com");
            origins.add("https://youtube.com");
            WebViewCompat.addDocumentStartJavaScript(web, injectJs, origins);
            docStartScript = true;
        }

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        requestNotificationPermission();

        String url = urlFromIntent(getIntent());
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(url != null ? url : HOME);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = urlFromIntent(intent);
        if (url != null) web.loadUrl(url);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    // Cố ý KHÔNG gọi web.onPause()/pauseTimers() để video tiếp tục phát nền.

    @Override
    protected void onDestroy() {
        PlaybackService.stop(this);
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            moveTaskToBack(true); // thu nhỏ, nhạc vẫn phát
        }
    }

    /* ----- Lệnh từ thông báo ----- */
    void togglePlay() {
        runOnUiThread(() -> web.evaluateJavascript("window.__ttToggle&&__ttToggle()", null));
    }

    void pausePlay() {
        runOnUiThread(() -> web.evaluateJavascript("window.__ttPause&&__ttPause()", null));
    }

    /* ----- Cầu nối JS -> Java ----- */
    private class Bridge {
        @JavascriptInterface
        public void onState(int playing, String title) {
            runOnUiThread(() -> PlaybackService.update(MainActivity.this, playing == 1, title));
        }
    }

    /* ----- Điều hướng + chặn quảng cáo ----- */
    private class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (AdBlocker.isAd(req.getUrl())) return AdBlocker.empty();
            return super.shouldInterceptRequest(view, req);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme();
            String host = u.getHost() == null ? "" : u.getHost();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return true; // chặn intent:// (YouTube cố mở app chính thức)
            }
            if (host.endsWith("youtube.com") || host.endsWith("youtu.be")
                    || host.endsWith("google.com") || host.endsWith("gstatic.com")
                    || host.endsWith("googleusercontent.com") || host.endsWith("ytimg.com")) {
                if (host.equals("www.youtube.com") || host.equals("youtube.com")) {
                    view.loadUrl(u.buildUpon().authority("m.youtube.com").build().toString());
                    return true;
                }
                if (host.endsWith("youtube.com") && "/redirect".equals(u.getPath())) {
                    String q = u.getQueryParameter("q");
                    if (q != null) openExternal(Uri.parse(q));
                    return true;
                }
                return false;
            }
            openExternal(u);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (!docStartScript) view.evaluateJavascript(injectJs, null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(injectJs, null); // script tự bỏ qua nếu đã chạy
        }
    }

    /* ----- Toàn màn hình khi xem video ----- */
    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            hideSystemBars(true);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        hideSystemBars(false);
        if (customCallback != null) {
            customCallback.onCustomViewHidden();
            customCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars(boolean hide) {
        View d = getWindow().getDecorView();
        if (hide) {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /* ----- Tiện ích ----- */
    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private String urlFromIntent(Intent i) {
        if (i == null) return null;
        if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            return normalize(i.getData().toString());
        }
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                Matcher m = Pattern.compile("https?://\\S+").matcher(text);
                if (m.find()) return normalize(m.group());
            }
        }
        return null;
    }

    private String normalize(String url) {
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost();
        if (host.equals("youtu.be")) {
            String id = u.getPath() == null ? "" : u.getPath().replace("/", "");
            return "https://m.youtube.com/watch?v=" + id;
        }
        if (host.equals("www.youtube.com") || host.equals("youtube.com")) {
            return u.buildUpon().authority("m.youtube.com").build().toString();
        }
        return url;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
