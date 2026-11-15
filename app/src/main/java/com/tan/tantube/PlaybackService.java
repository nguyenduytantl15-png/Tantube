package com.tan.tantube;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

/** Dịch vụ chạy nền: giữ app sống khi tắt màn hình và hiện thông báo điều khiển. */
public class PlaybackService extends Service {

    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 7;
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_TOGGLE = "toggle";
    private static final String ACTION_STOP = "stop";
    private static final String ACTION_REFRESH = "refresh";

    static boolean running = false;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private boolean playing = false;
    private String title = "TanTube";

    /** Gọi từ Activity mỗi khi trạng thái phát thay đổi. */
    static void update(Context c, boolean playing, String title) {
        if (!running && !playing) return; // chưa phát gì thì không cần dịch vụ
        Intent i = new Intent(c, PlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra("playing", playing)
                .putExtra("title", title);
        try {
            if (running) c.startService(i);
            else ContextCompat.startForegroundService(c, i);
        } catch (Exception ignored) {
            // Android có thể chặn khởi động dịch vụ khi app đang ở nền; bỏ qua
        }
    }

    /** Cập nhật lại thông báo (ví dụ khi đổi hẹn giờ). */
    static void refresh(Context c) {
        if (!running) return;
        try {
            c.startService(new Intent(c, PlaybackService.class).setAction(ACTION_REFRESH));
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        if (running) c.stopService(new Intent(c, PlaybackService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Đang phát",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TanTube:play");
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TanTube:wifi");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        MainActivity act = MainActivity.current.get();

        if (ACTION_TOGGLE.equals(action)) {
            if (act != null) act.togglePlay();
        } else if (ACTION_STOP.equals(action)) {
            if (act != null) act.pausePlay();
            stopSelf();
            return START_NOT_STICKY;
        } else if (ACTION_UPDATE.equals(action) && intent != null) {
            playing = intent.getBooleanExtra("playing", false);
            String t = intent.getStringExtra("title");
            if (t != null && !t.isEmpty()) title = t;
        }

        int type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type);
        updateLocks();
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags);
        PendingIntent toggle = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP), piFlags);

        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText((playing ? "Đang phát" : "Tạm dừng")
                        + (SleepTimer.endOfVideo ? " · Tắt khi hết video"
                        : SleepTimer.label() != null ? " · Tắt lúc " + SleepTimer.label() : ""))
                .setContentIntent(open)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, playing ? "Tạm dừng" : "Phát", toggle)
                .addAction(0, "Đóng", stop)
                .build();
    }

    @SuppressWarnings("WakelockTimeout")
    private void updateLocks() {
        if (playing) {
            if (!wakeLock.isHeld()) wakeLock.acquire();
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } else {
            if (wakeLock.isHeld()) wakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        playing = false;
        updateLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
