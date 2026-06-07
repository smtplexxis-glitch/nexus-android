package com.nexus.app;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MyFirebaseService extends FirebaseMessagingService {
    private static final String CHANNEL = "nexus_messages";
    private static final String PREFS   = "nexus_prefs";
    private static final String API     = "http://186.246.46.119";

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        android.util.Log.d("FCM", "Message received!");
        String title = "Новое сообщение";
        String body  = "Откройте NEXUS";
        // Notification payload (приоритет — показывает даже при убитом приложении)
        if (msg.getNotification() != null) {
            if (msg.getNotification().getTitle() != null) title = msg.getNotification().getTitle();
            if (msg.getNotification().getBody()  != null) body  = msg.getNotification().getBody();
        }
        // Data payload (переопределяет если есть)
        Map<String, String> data = msg.getData();
        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body"))  body  = data.get("body");
        showNotif(title, body);
    }

    @Override
    public void onNewToken(String token) {
        android.util.Log.d("FCM", "New token: " + token.substring(0, 20));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        String authToken = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", "");
        if (!authToken.isEmpty()) sendToken(authToken, token);
    }

    public static void sendToken(String authToken, String fcmToken) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API + "/api/fcm-token").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Authorization", "Bearer " + authToken);
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                c.getOutputStream().write(("{\"token\":\"" + fcmToken + "\"}").getBytes(StandardCharsets.UTF_8));
                android.util.Log.d("FCM", "sendToken: " + c.getResponseCode());
                c.disconnect();
            } catch (Exception e) { android.util.Log.e("FCM", "sendToken error: " + e.getMessage()); }
        }).start();
    }

    private void showNotif(String title, String body) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true); ch.setBypassDnd(true);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(ch);
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify((int)(System.currentTimeMillis() % 10000),
            new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true).setContentIntent(pi).build());
    }
}
