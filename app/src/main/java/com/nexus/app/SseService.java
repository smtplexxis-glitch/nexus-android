package com.nexus.app;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class SseService extends Service {
    private static final String CHANNEL_ID = "nexus_messages";
    private static final String PREFS      = "nexus_prefs";
    private static final String BASE       = "http://186.246.46.119";
    private Thread thread;
    private volatile boolean running = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(999, buildFgNotification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread == null || !thread.isAlive()) {
            running = true;
            thread = new Thread(this::listenSse);
            thread.start();
        }
        return START_STICKY;
    }

    private void listenSse() {
        while (running) {
            String token = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
            if (token == null) { sleep(5000); continue; }
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/api/sse").openConnection();
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Accept", "text/event-stream");
                c.setConnectTimeout(10000);
                c.setReadTimeout(60000);
                c.connect();
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                String line;
                while (running && (line = br.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (!data.equals("connected") && !data.equals("ping") && !data.isEmpty()) {
                            try {
                                JSONObject j = new JSONObject(data);
                                showNotification(j.optString("title","Новое сообщение"), j.optString("body",""));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                br.close(); c.disconnect();
            } catch (Exception e) {
                sleep(3000); // reconnect
            }
        }
    }

    private void showNotification(String title, String body) {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify((int)(System.currentTimeMillis() % 10000),
            new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title).setContentText(body.isEmpty() ? "Откройте NEXUS" : body)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build());
    }

    private Notification buildFgNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NEXUS").setContentText("Ожидание сообщений...")
            .setPriority(NotificationCompat.PRIORITY_LOW).setContentIntent(pi).build();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    @Override public void onDestroy() { running = false; super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent i) { return null; }
}
