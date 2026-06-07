package com.nexus.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseService extends FirebaseMessagingService {

    private static final String CHANNEL = "nexus_messages";

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        super.onMessageReceived(msg);

        // Читаем из data (data-only push работает в фоне)
        Map<String, String> data = msg.getData();
        String title = data.containsKey("title") ? data.get("title") : "Новое сообщение";
        String body  = data.containsKey("body")  ? data.get("body")  : "Откройте NEXUS";

        // Fallback на notification payload если есть
        if (msg.getNotification() != null) {
            if (msg.getNotification().getTitle() != null) title = msg.getNotification().getTitle();
            if (msg.getNotification().getBody()  != null) body  = msg.getNotification().getBody();
        }

        showNotif(title, body);
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("nexus_prefs", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply();
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
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build());
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
