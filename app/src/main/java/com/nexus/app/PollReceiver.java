package com.nexus.app;

import android.app.*;
import android.content.*;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class PollReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "nexus_messages";
    private static final String PREFS   = "nexus_prefs";
    private static final String API     = "http://186.246.46.119/api/chats?limit=100";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Держим wakelock пока делаем запрос
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "nexus:poll");
        wl.acquire(15000);

        try {
            String token = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("token", null);
            if (token == null || token.isEmpty()) return;

            HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return;

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close(); c.disconnect();

            JSONArray arr = new JSONArray(sb.toString());
            int total = 0;
            for (int i = 0; i < arr.length(); i++)
                total += arr.getJSONObject(i).optInt("unread_count", 0);

            int prev = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("last_unread", 0);

            if (total > prev && total > 0) {
                showNotification(ctx, total);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt("last_unread", total).apply();

        } catch (Exception ignored) {
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }

    private void showNotification(Context ctx, int count) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = count == 1 ? "Новое сообщение" : "Новых сообщений: " + count;
        NotificationManager nm = (NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1001, new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText("Откройте NEXUS")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build());
    }
}
