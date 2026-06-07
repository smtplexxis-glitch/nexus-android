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
        super.onMessageReceived(msg);
        Map<String, String> data = msg.getData();
        String title = data.containsKey("title") ? data.get("title") : "Новое сообщение";
        String body  = data.containsKey("body")  ? data.get("body")  : "Откройте NEXUS";
        if (msg.getNotification() != null) {
            if (msg.getNotification().getTitle() != null) title = msg.getNotification().getTitle();
            if (msg.getNotification().getBody()  != null) body  = msg.getNotification().getBody();
        }
        android.util.Log.d("FCM", "Message received: " + title);
        showNotif(title, body);
    }

    @Override
    public void onNewToken(String fcmToken) {
        android.util.Log.d("FCM", "New FCM token: " + fcmToken.substring(0, 20));
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putString("fcm_token", fcmToken).apply();
        String authToken = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
        if (authToken != null && !authToken.isEmpty()) {
            sendTokenToServer(authToken, fcmToken);
        }
    }

    public static void sendTokenToServer(String authToken, String fcmToken) {
        new Thread(() -> {
            try {
                android.util.Log.d("FCM", "Sending token to server...");
                HttpURLConnection c = (HttpURLConnection)
                    new URL(API + "/api/fcm-token").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Authorization", "Bearer " + authToken);
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.getOutputStream().write(
                    ("{\"token\":\"" + fcmToken + "\"}").getBytes("UTF-8"));
                int code = c.getResponseCode();
                android.util.Log.d("FCM", "Server response: " + code);
                c.disconnect();
            } catch (Exception e) {
                android.util.Log.e("FCM", "Error: " + e.getMessage());
            }
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
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true).setContentIntent(pi).build());
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setBypassDnd(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(ch);
    }
}
