package com.tan.tantube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Hẹn giờ tắt app. */
final class SleepTimer {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable fireTask = SleepTimer::fire;

    static long endAt = 0;            // thời điểm tắt (ms), 0 = không hẹn
    static boolean endOfVideo = false; // tắt khi hết video đang xem

    private SleepTimer() {}

    static void start(Context c, int minutes) {
        clear();
        endAt = System.currentTimeMillis() + minutes * 60_000L;
        handler.postDelayed(fireTask, minutes * 60_000L);
        changed(c);
    }

    static void startEndOfVideo(Context c) {
        clear();
        endOfVideo = true;
        changed(c);
    }

    static void cancel(Context c) {
        clear();
        changed(c);
    }

    static boolean active() {
        return endAt > 0 || endOfVideo;
    }

    /** Chữ hiển thị trên nút / thông báo, null nếu chưa hẹn. */
    static String label() {
        if (endOfVideo) return "hết video";
        if (endAt > 0) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(endAt));
        return null;
    }

    /** Kiểm tra lại khi app quay lại (phòng khi máy ngủ làm trễ hẹn giờ). */
    static void checkOverdue() {
        if (endAt > 0 && System.currentTimeMillis() >= endAt) fire();
    }

    static void fire() {
        clear();
        MainActivity a = MainActivity.current.get();
        if (a != null) a.closeApp();
    }

    static void clear() {
        handler.removeCallbacks(fireTask);
        endAt = 0;
        endOfVideo = false;
    }

    private static void changed(Context c) {
        MainActivity a = MainActivity.current.get();
        if (a != null) a.refreshTimerButton();
        PlaybackService.refresh(c);
    }
}
