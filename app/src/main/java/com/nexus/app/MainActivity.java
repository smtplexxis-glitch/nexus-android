package com.nexus.app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    private static final String APP_URL = "http://186.246.46.119/app/";
    private static final String API_BASE = "http://186.246.46.119/api";
    private static final String CHANNEL_ID = "nexus_messages";
    private static final String PREFS = "nexus_prefs";

    private Handler notifHandler;
    private Runnable notifRunnable;
    private String authToken = null;
    private boolean appInForeground = true;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);

        createNotificationChannel();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " NexusApp/1.0");

        // JavaScript интерфейс для получения токена из веб-приложения
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void setToken(String token) {
                authToken = token;
                SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                ed.putString("token", token);
                ed.apply();
                startNotificationPolling();
            }

            @android.webkit.JavascriptInterface
            public void clearToken() {
                authToken = null;
                stopNotificationPolling();
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                // Извлекаем токен из localStorage после загрузки
                view.evaluateJavascript(
                    "(function(){ var t=localStorage.getItem(\'token\'); if(t) AndroidBridge.setToken(t); return t; })()",
                    null
                );
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://186.246.46.119")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());

        // Восстанавливаем токен
        authToken = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(APP_URL);
        }

        if (authToken != null) startNotificationPolling();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о новых сообщениях");
            channel.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private int lastUnreadCount = 0;

    private void startNotificationPolling() {
        stopNotificationPolling();
        notifHandler = new Handler(Looper.getMainLooper());
        notifRunnable = new Runnable() {
            @Override
            public void run() {
                if (authToken != null && !appInForeground) {
                    checkUnreadMessages();
                }
                notifHandler.postDelayed(this, 15000); // каждые 15 сек
            }
        };
        notifHandler.postDelayed(notifRunnable, 15000);
    }

    private void stopNotificationPolling() {
        if (notifHandler != null && notifRunnable != null) {
            notifHandler.removeCallbacks(notifRunnable);
        }
    }

    private void checkUnreadMessages() {
        new Thread(() -> {
            try {
                URL url = new URL(API_BASE + "/chats?limit=50");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONArray chats = new JSONArray(sb.toString());
                    int totalUnread = 0;
                    String senderName = "";

                    for (int i = 0; i < chats.length(); i++) {
                        JSONObject chat = chats.getJSONObject(i);
                        int unread = chat.optInt("unread_count", 0);
                        if (unread > 0) {
                            totalUnread += unread;
                            if (senderName.isEmpty()) {
                                senderName = chat.optString("contact_name",
                                    chat.optString("name", "Новое сообщение"));
                            }
                        }
                    }

                    if (totalUnread > lastUnreadCount && totalUnread > 0) {
                        showNotification(totalUnread, senderName);
                    }
                    lastUnreadCount = totalUnread;
                }
                conn.disconnect();
            } catch (Exception e) {
                // игнорируем ошибки сети
            }
        }).start();
    }

    private void showNotification(int count, String sender) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = count == 1 ? "Новое сообщение" : "Новых сообщений: " + count;
        String text = sender.isEmpty() ? "Откройте NEXUS чтобы прочитать" : "От: " + sender;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1001, builder.build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        appInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        appInForeground = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        stopNotificationPolling();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
