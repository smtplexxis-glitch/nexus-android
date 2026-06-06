package com.nexus.app;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationService extends Service {
    private static final String CHANNEL = "nexus_messages";
    private static final String PREFS   = "nexus_prefs";
    private static final String API_URL = "http://186.246.46.119/api/chats?limit=100";
    private Thread thread;
    private volatile boolean running = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread == null || !thread.isAlive()) {
            running = true;
            thread = new Thread(this::pollLoop);
            thread.setDaemon(true);
            thread.start();
        }
        return START_STICKY;
    }

    private void pollLoop() {
        // Первый запрос — запоминаем baseline
        int baseline = fetchUnread();
        while (running) {
            sleep(30000);
            int current = fetchUnread();
            if (current > baseline && current > 0) {
                showNotification(current);
            }
            baseline = current;
        }
    }

    private int fetchUnread() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null || token.isEmpty()) return 0;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return 0;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close(); c.disconnect();
            JSONArray arr = new JSONArray(sb.toString());
            int total = 0;
            for (int i = 0; i < arr.length(); i++)
                total += arr.getJSONObject(i).optInt("unread_count", 0);
            return total;
        } catch (Exception e) { return 0; }
    }

    private void showNotification(int count) {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = count == 1 ? "Новое сообщение" : "Новых сообщений: " + count;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify(1001, new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText("Откройте NEXUS")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
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
