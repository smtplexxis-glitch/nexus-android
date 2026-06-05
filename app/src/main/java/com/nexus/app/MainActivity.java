package com.nexus.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String APP_URL  = "http://186.246.46.119/app/";
    private static final String PREFS    = "nexus_prefs";
    private static final String WORK_TAG = "nexus_notif";

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ActivityResultLauncher<String> notifLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView            = findViewById(R.id.webview);
        progressBar        = findViewById(R.id.progress_bar);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);

        createNotificationChannel();

        notifLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> { if (granted) startServices(); }
        );

        setupWebView(savedInstanceState);

        // Запрашиваем разрешение через 1.5 сек
        webView.postDelayed(this::requestNotifPermission, 1500);
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startServices();
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(this)
                    .setTitle("Уведомления NEXUS")
                    .setMessage("Разрешите уведомления чтобы получать сообщения от клиентов мгновенно.")
                    .setPositiveButton("Разрешить", (d, w) -> notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton("Не сейчас", null).show();
            } else {
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            startServices();
        }
    }

    private void startServices() {
        // 1. SSE Foreground Service — мгновенные уведомления
        Intent sseIntent = new Intent(this, SseService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sseIntent);
        } else {
            startService(sseIntent);
        }
        // 2. WorkManager — резервный опрос каждые 15 мин
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_TAG, ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG).build());
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            "nexus_messages", "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(Bundle savedState) {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " NexusApp/1.0");

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setToken(String token) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("token", token).apply();
                // Перезапускаем SSE сервис с новым токеном
                startService(new Intent(MainActivity.this, SseService.class));
            }
            @JavascriptInterface
            public void clearToken() {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").remove("last_unread").apply();
                stopService(new Intent(MainActivity.this, SseService.class));
                WorkManager.getInstance(MainActivity.this).cancelAllWorkByTag(WORK_TAG);
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) { progressBar.setVisibility(View.VISIBLE); }
            @Override public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                v.evaluateJavascript("(function(){var t=localStorage.getItem('token');if(t&&t!='null')AndroidBridge.setToken(t);})()", null);
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) { h.proceed(); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("http://186.246.46.119")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p); if (p == 100) progressBar.setVisibility(View.GONE);
            }
        });
        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
        if (savedState != null) webView.restoreState(savedState); else webView.loadUrl(APP_URL);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); webView.saveState(out); }
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(k, e);
    }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() { if (webView != null) webView.destroy(); super.onDestroy(); }
}
