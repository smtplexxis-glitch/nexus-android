package com.nexus.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

public class PollService extends Service {

    private static final String CHANNEL_SVC  = "nexus_service";
    private static final String CHANNEL_MSG  = "nexus_messages";
    private static final String PREFS        = "nexus_prefs";
    private static final String API          = "http://186.246.46.119";
    private static final int    NOTIF_SVC_ID = 9001;

    private Thread pollThread;
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Foreground notification — HiOS не убивает
        Notification n = new NotificationCompat.Builder(this, CHANNEL_SVC)
            .setContentTitle("NEXUS")
            .setContentText("Ожидание сообщений...")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
        startForeground(NOTIF_SVC_ID, n);

        if (!running) {
            running = true;
            pollThread = new Thread(this::pollLoop);
            pollThread.setDaemon(true);
            pollThread.start();
        }
        return START_STICKY;
    }

    private void pollLoop() {
        int lastUnread = 0;
        while (running) {
            try {
                String token = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString("token", null);
                if (token != null && !token.isEmpty()) {
                    HttpURLConnection c = (HttpURLConnection)
                        new URL(API + "/api/chats?limit=100").openConnection();
                    c.setRequestProperty("Authorization", "Bearer " + token);
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    if (c.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(c.getInputStream()));
                        StringBuilder sb = new StringBuilder();
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
                        if (total > lastUnread && total > 0) {
                            showMsgNotif(total, sender);
                        }
                        lastUnread = total;
                    } else {
                        c.disconnect();
                    }
                }
            } catch (Exception ignored) {}

            try { Thread.sleep(30000); } catch (InterruptedException e) { break; }
        }
    }

    private void showMsgNotif(int count, String sender) {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = count == 1 ? "Новое сообщение" : "Сообщений: " + count;
        String body  = sender.isEmpty() ? "Откройте NEXUS" : sender;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify((int)(System.currentTimeMillis() % 10000),
                new NotificationCompat.Builder(this, CHANNEL_MSG)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build());
    }

    private void createChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL_SVC, "NEXUS Сервис", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel msg = new NotificationChannel(
            CHANNEL_MSG, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        msg.enableVibration(true);
        nm.createNotificationChannel(msg);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
