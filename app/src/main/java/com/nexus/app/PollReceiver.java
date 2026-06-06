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
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "nexus:poll");
        wl.acquire(20_000);
        try {
            check(ctx);
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }

    private void check(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null || token.isEmpty()) return;

        try {
            HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            if (c.getResponseCode() != 200) return;

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            c.disconnect();

            JSONArray arr = new JSONArray(sb.toString());
            int total = 0;
            String sender = "";
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ch = arr.getJSONObject(i);
                int u = ch.optInt("unread_count", 0);
                if (u > 0) {
                    total += u;
                    if (sender.isEmpty())
                        sender = ch.optString("contact_name",
                                 ch.optString("name", ""));
                }
            }

            int prev = prefs.getInt("last_unread", 0);
            if (total > prev) {
                showNotif(ctx, total, sender);
            }
            prefs.edit().putInt("last_unread", total).apply();

        } catch (Exception ignored) {}
    }

    private void showNotif(Context ctx, int count, String sender) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = count == 1 ? "Новое сообщение" : "Сообщений: " + count;
        String body  = sender.isEmpty() ? "Откройте NEXUS" : sender;

        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1001,
            new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
    }
}
