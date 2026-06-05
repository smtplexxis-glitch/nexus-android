package com.nexus.app;

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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationWorker extends Worker {

    private static final String CHANNEL_ID = "nexus_messages";
    private static final String API_URL    = "http://186.246.46.119/api/chats?limit=100";
    private static final String PREFS      = "nexus_prefs";

    public NotificationWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null || token.isEmpty()) return Result.success();

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != 200) return Result.success();

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            conn.disconnect();

            JSONArray chats = new JSONArray(sb.toString());
            int prevTotal = prefs.getInt("last_unread", 0);
            int newTotal  = 0;
            String sender = "";

            for (int i = 0; i < chats.length(); i++) {
                JSONObject ch = chats.getJSONObject(i);
                int u = ch.optInt("unread_count", 0);
                if (u > 0) {
                    newTotal += u;
                    if (sender.isEmpty())
                        sender = ch.optString("contact_name",
                                 ch.optString("name", ""));
                }
            }

            if (newTotal > prevTotal) {
                createChannel(ctx);
                showNotification(ctx, newTotal, sender);
            }

            prefs.edit().putInt("last_unread", newTotal).apply();

        } catch (Exception e) {
            return Result.retry();
        }
        return Result.success();
    }

    private void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
            .createNotificationChannel(ch);
    }

    private void showNotification(Context ctx, int count, String sender) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = count == 1 ? "Новое сообщение" : "Новых сообщений: " + count;
        String body  = sender.isEmpty() ? "Откройте NEXUS" : sender;

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi);

        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE))
            .notify(1001, b.build());
    }
}
