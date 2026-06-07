package com.nexus.app;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.io.*;
import java.net.*;
import java.util.Map;

public class MyFirebaseService extends FirebaseMessagingService {
    private static final String CHANNEL = "nexus_messages";
    private static final String PREFS   = "nexus_prefs";
    private static final String API     = "http://186.246.46.119";

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> data = msg.getData();
        String title = data.containsKey("title") ? data.get("title") : "Новое сообщение";
        String body  = data.containsKey("body")  ? data.get("body")  : "Откройте NEXUS";
        if (msg.getNotification() != null) {
            if (msg.getNotification().getTitle() != null) title = msg.getNotification().getTitle();
            if (msg.getNotification().getBody()  != null) body  = msg.getNotification().getBody();
        }
        showNotif(title, body);
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        String auth = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
        if (auth != null && !auth.isEmpty()) sendTokenToServer(auth, token);
    }

    public static void sendTokenToServer(String auth, String fcm) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API + "/api/fcm-token").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Authorization", "Bearer " + auth);
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                c.getOutputStream().write(("{\"token\":\"" + fcm + "\"}").getBytes("UTF-8"));
                android.util.Log.d("FCM", "register: " + c.getResponseCode());
                c.disconnect();
            } catch (Exception e) { android.util.Log.e("FCM", e.getMessage()); }
        }).start();
    }

    private void showNotif(String title, String body) {
        createChannel();
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify((int)(System.currentTimeMillis() % 10000),
                new NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(title).setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true).setContentIntent(pi).build());
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true); ch.setBypassDnd(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
}
