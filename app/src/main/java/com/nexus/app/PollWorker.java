package com.nexus.app;

import android.app.*;
import android.content.*;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class PollWorker extends Worker {

    private static final String CHANNEL = "nexus_messages";
    private static final String PREFS   = "nexus_prefs";
    private static final String API     = "http://186.246.46.119/api/chats?limit=100";

    public PollWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null || token.isEmpty()) return Result.success();

        try {
            HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return Result.retry();

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
            if (total > prev && total > 0) {
                showNotif(ctx, total, sender);
            }
            prefs.edit().putInt("last_unread", total).apply();

        } catch (Exception e) {
            return Result.retry();
        }
        return Result.success();
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
