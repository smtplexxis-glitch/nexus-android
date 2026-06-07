package com.nexus.app;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL  = "http://186.246.46.119/app/";
    private static final String API_BASE = "http://186.246.46.119";
    private static final String PREFS    = "nexus_prefs";
    private static final int    REQ      = 42;

    private WebView     webView;
    private ProgressBar progressBar;
    private boolean     asked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        createChannel();
        setupWebView(savedInstanceState);
        refreshFcmToken();
    }

    // Получаем FCM токен при каждом запуске и регистрируем если есть auth токен
    private void refreshFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                android.util.Log.e("FCM", "getToken failed: " + (task.getException() != null ? task.getException().getMessage() : "null"));
                return;
            }
            String fcmToken = task.getResult();
            android.util.Log.d("FCM", "Token: " + fcmToken.substring(0, Math.min(20, fcmToken.length())));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("fcm_token", fcmToken).apply();
            // Если уже есть auth токен — регистрируем FCM сразу
            String authToken = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", "");
            if (!authToken.isEmpty()) {
                registerFcm(authToken, fcmToken);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (!asked) {
            asked = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        // После выдачи разрешения — обновляем токен
        refreshFcmToken();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel("nexus_messages", "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(Bundle savedState) {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new Object() {
            // JS вызывает это после логина через страницу
            @JavascriptInterface
            public void setToken(String authToken) {
                if (authToken == null || authToken.isEmpty()) return;
                android.util.Log.d("FCM", "setToken from JS: " + authToken.substring(0, Math.min(15, authToken.length())));
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("token", authToken).apply();
                String fcmToken = getSharedPreferences(PREFS, MODE_PRIVATE).getString("fcm_token", "");
                if (!fcmToken.isEmpty()) {
                    registerFcm(authToken, fcmToken);
                }
            }
            @JavascriptInterface
            public String getFcmToken() {
                return getSharedPreferences(PREFS, MODE_PRIVATE).getString("fcm_token", "");
            }
            @JavascriptInterface
            public void show(String title, String body) {
                runOnUiThread(() -> showNotif(title, body));
            }
        }, "AndroidBridge");

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void show(String title, String body) {
                runOnUiThread(() -> showNotif(title, body));
            }
        }, "AndroidNotify");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                // Читаем nx_token из localStorage и передаём в AndroidBridge
                v.evaluateJavascript(
                    "(function(){var t=localStorage.getItem('nx_token');" +
                    "if(t&&t!='null'&&t!='undefined'&&t!=''){AndroidBridge.setToken(t);return 'ok:'+t.substr(0,10);}" +
                    "return 'no_token';})()",
                    val -> android.util.Log.d("FCM", "localStorage eval: " + val));
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) { h.proceed(); }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("http://186.246.46.119")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }
        });

        if (savedState != null) webView.restoreState(savedState);
        else webView.loadUrl(APP_URL);
    }

    // Регистрируем FCM токен на сервере
    public void registerFcm(String authToken, String fcmToken) {
        new Thread(() -> {
            try {
                android.util.Log.d("FCM", "Registering token on server...");
                HttpURLConnection c = (HttpURLConnection) new URL(API_BASE + "/api/fcm-token").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Authorization", "Bearer " + authToken);
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                byte[] body = ("{\"token\":\"" + fcmToken + "\"}").getBytes(StandardCharsets.UTF_8);
                c.getOutputStream().write(body);
                int code = c.getResponseCode();
                android.util.Log.d("FCM", "Server register: " + code);
                c.disconnect();
            } catch (Exception e) {
                android.util.Log.e("FCM", "Register error: " + e.getMessage());
            }
        }).start();
    }

    private void showNotif(String title, String body) {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify((int)(System.currentTimeMillis() % 10000),
                new NotificationCompat.Builder(this, "nexus_messages")
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(title.isEmpty() ? "NEXUS" : title)
                    .setContentText(body.isEmpty() ? "Новое сообщение" : body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).setContentIntent(pi).build());
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); webView.saveState(out); }
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(k, e);
    }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() { if (webView != null) webView.destroy(); super.onDestroy(); }
}
