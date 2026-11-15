package com.tan.tantube;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
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
    private TextView timerBtn;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        injectJs = readAsset("inject.js");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F0F0F"));
        web = new BgWebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        addTimerButton();

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
    protected void onResume() {
        super.onResume();
        SleepTimer.checkOverdue();
        refreshTimerButton();
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) SleepTimer.clear();
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

    /* ----- Hẹn giờ tắt app ----- */
    void closeApp() {
        runOnUiThread(() -> {
            web.evaluateJavascript("window.__ttPause&&__ttPause()", null);
            PlaybackService.stop(this);
            Toast.makeText(this, "Hết giờ hẹn – đã tắt TanTube", Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addTimerButton() {
        timerBtn = new TextView(this);
        timerBtn.setTextColor(Color.WHITE);
        timerBtn.setTextSize(15);
        timerBtn.setGravity(Gravity.CENTER);
        timerBtn.setMinWidth(dp(44));
        timerBtn.setMinHeight(dp(44));
        timerBtn.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(Color.parseColor("#99000000"));
        bg.setStroke(dp(1), Color.parseColor("#55FFFFFF"));
        timerBtn.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.END | Gravity.BOTTOM);
        lp.setMargins(0, 0, dp(10), dp(150));
        root.addView(timerBtn, lp);

        // Kéo lên/xuống để đổi vị trí, chạm nhẹ để mở menu
        timerBtn.setOnTouchListener(new View.OnTouchListener() {
            float downY, startTy;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        startTy = v.getTranslationY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dy) > dp(8)) dragging = true;
                        if (dragging) {
                            float max = root.getHeight() - dp(150) - v.getHeight();
                            v.setTranslationY(Math.max(-max, Math.min(dp(140), startTy + dy)));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) showTimerMenu();
                        return true;
                }
                return false;
            }
        });
        refreshTimerButton();
    }

    void refreshTimerButton() {
        if (timerBtn == null) return;
        String l = SleepTimer.label();
        timerBtn.setText(l == null ? "⏱" : "⏱ " + l);
        timerBtn.setAlpha(l == null ? 0.6f : 1f);
    }

    boolean isAutoNext() {
        return getSharedPreferences("tantube", MODE_PRIVATE).getBoolean("autoNext", true);
    }

    private void setAutoNext(boolean on) {
        getSharedPreferences("tantube", MODE_PRIVATE).edit().putBoolean("autoNext", on).apply();
        Toast.makeText(this, on ? "Đã BẬT tự phát video tiếp" : "Đã TẮT tự phát video tiếp",
                Toast.LENGTH_SHORT).show();
    }

    private void showTimerMenu() {
        final int[] mins = {15, 30, 45, 60, 90};
        java.util.List<String> items = new java.util.ArrayList<>();
        for (int m : mins) items.add(m + " phút");
        items.add("Khi hết video đang xem");
        items.add("Tự nhập số phút…");
        final boolean auto = isAutoNext();
        items.add("▶ Tự phát video tiếp theo: " + (auto ? "ĐANG BẬT" : "ĐANG TẮT"));
        final int autoIdx = items.size() - 1;
        if (SleepTimer.active()) items.add("Hủy hẹn giờ (đang hẹn: " + SleepTimer.label() + ")");

        new AlertDialog.Builder(this)
                .setTitle("Hẹn giờ tắt app")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < mins.length) {
                        SleepTimer.start(this, mins[which]);
                        Toast.makeText(this, "Sẽ tắt lúc " + SleepTimer.label(), Toast.LENGTH_SHORT).show();
                    } else if (which == mins.length) {
                        SleepTimer.startEndOfVideo(this);
                        Toast.makeText(this, "Sẽ tắt khi hết video này", Toast.LENGTH_SHORT).show();
                    } else if (which == mins.length + 1) {
                        askCustomMinutes();
                    } else if (which == autoIdx) {
                        setAutoNext(!auto);
                    } else {
                        SleepTimer.cancel(this);
                        Toast.makeText(this, "Đã hủy hẹn giờ", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void askCustomMinutes() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Ví dụ: 20");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Tắt app sau bao nhiêu phút?")
                .setView(box)
                .setPositiveButton("Hẹn giờ", (d, w) -> {
                    try {
                        int m = Integer.parseInt(input.getText().toString().trim());
                        if (m > 0 && m <= 1440) {
                            SleepTimer.start(this, m);
                            Toast.makeText(this, "Sẽ tắt lúc " + SleepTimer.label(), Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /* ----- Cầu nối JS -> Java ----- */
    private class Bridge {
        @JavascriptInterface
        public void onState(int playing, String title) {
            runOnUiThread(() -> PlaybackService.update(MainActivity.this, playing == 1, title));
        }

        /** Trả về true nếu app sắp tắt (hẹn giờ "hết video"). */
        @JavascriptInterface
        public boolean onEnded() {
            boolean closing = SleepTimer.endOfVideo;
            if (closing) runOnUiThread(SleepTimer::fire);
            return closing;
        }

        @JavascriptInterface
        public boolean autoNext() {
            return isAutoNext();
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
