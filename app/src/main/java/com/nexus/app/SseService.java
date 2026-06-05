package com.nexus.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class SseService extends Service {
    private static final String TAG       = "NexusSSE";
    private static final String CHANNEL   = "nexus_messages";
    private static final String PREFS     = "nexus_prefs";
    private static final String SSE_URL   = "http://186.246.46.119/api/sse";
    private Thread thread;
    private volatile boolean running = false;

    @Override public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createChannel();
        startForeground(999, buildFgNotif());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (thread == null || !thread.isAlive()) {
            running = true;
            thread = new Thread(this::connect);
            thread.setDaemon(true);
            thread.start();
        }
        return START_STICKY;
    }

    private void connect() {
        while (running) {
            String token = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
            if (token == null || token.isEmpty()) {
                Log.d(TAG, "No token, waiting...");
                sleep(5000);
                continue;
            }
            Log.d(TAG, "Connecting SSE with token length=" + token.length());
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(SSE_URL).openConnection();
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Accept", "text/event-stream");
                c.setRequestProperty("Cache-Control", "no-cache");
                c.setConnectTimeout(15000);
                c.setReadTimeout(0); // бесконечный таймаут чтения
                int code = c.getResponseCode();
                Log.d(TAG, "SSE response code: " + code);
                if (code != 200) {
                    Log.w(TAG, "Bad response, retry in 5s");
                    sleep(5000);
                    continue;
                }
                Log.d(TAG, "SSE connected, listening...");
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                String line;
                while (running && (line = br.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        Log.d(TAG, "SSE data: " + data);
                        if (!data.equals("connected") && !data.equals("ping") && !data.isEmpty()) {
                            try {
                                JSONObject j = new JSONObject(data);
                                showNotif(j.optString("title","Новое сообщение"),
                                         j.optString("body",""));
                            } catch (Exception e) {
                                Log.e(TAG, "JSON parse error: " + e);
                            }
                        }
                    }
                }
                Log.d(TAG, "SSE stream ended, reconnecting...");
                br.close();
                c.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "SSE error: " + e.getMessage());
                sleep(3000);
            }
        }
    }

    private void showNotif(String title, String body) {
        Log.d(TAG, "Showing notification: " + title);
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify((int)(System.currentTimeMillis() % 10000),
            new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(body.isEmpty() ? "Откройте NEXUS" : body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
    }

    private Notification buildFgNotif() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NEXUS")
            .setContentText("Ожидание сообщений...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(ch);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override public void onDestroy() { running = false; super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent i) { return null; }
}
