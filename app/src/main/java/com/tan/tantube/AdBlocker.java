package com.tan.tantube;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;

/** Chặn các yêu cầu mạng tới máy chủ quảng cáo / theo dõi. */
final class AdBlocker {

    private static final String[] HOSTS = {
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "googletagservices.com",
            "googletagmanager.com",
            "adservice.google.com",
            "imasdk.googleapis.com",
            "pagead2.googlesyndication.com",
    };

    private static final String[] PATHS = {
            "/pagead/",
            "/api/stats/ads",
            "/get_midroll_info",
            "/ptracking",
            "/youtubei/v1/log_event",
            "/api/stats/qoe?adformat",
    };

    private AdBlocker() {}

    static boolean isAd(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        for (String h : HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        String s = uri.toString();
        for (String p : PATHS) {
            if (s.contains(p)) return true;
        }
        return false;
    }

    static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }
}
