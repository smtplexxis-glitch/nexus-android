package com.nexus.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationWorker extends Worker {
    private static final String CHANNEL_ID = "nexus_messages";
    private static final String PREFS      = "nexus_prefs";
    private static final String BASE       = "http://186.246.46.119";

    public NotificationWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c,p); }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null) return Result.success();

        // Fallback polling (WorkManager runs every 15 min as backup)
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/api/chats?limit=100").openConnection();
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return Result.success();
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close(); c.disconnect();

            org.json.JSONArray chats = new org.json.JSONArray(sb.toString());
            int prev = prefs.getInt("last_unread", 0), total = 0;
            String sender = "";
            for (int i = 0; i < chats.length(); i++) {
                JSONObject ch = chats.getJSONObject(i);
                int u = ch.optInt("unread_count", 0);
                if (u > 0) { total += u; if (sender.isEmpty()) sender = ch.optString("contact_name", ch.optString("name","")); }
            }
            if (total > prev && total > 0) {
                createChannel(ctx);
                notify(ctx, total == 1 ? "Новое сообщение" : "Сообщений: " + total, sender);
            }
            prefs.edit().putInt("last_unread", total).apply();
        } catch (Exception ignored) {}
        return Result.success();
    }

    void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    void notify(Context ctx, String title, String text) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
            .notify(1001, new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title).setContentText(text.isEmpty() ? "Откройте NEXUS" : text)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build());
    }
}
