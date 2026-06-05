package com.nexus.app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG    = "NexusMain";
    private static final String URL    = "http://186.246.46.119/app/";
    private static final String PREFS  = "nexus_prefs";

    private WebView webView;
    private ProgressBar progressBar;
    private ActivityResultLauncher<String> permLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

        // Канал уведомлений
        NotificationChannel ch = new NotificationChannel(
            "nexus_messages", "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);

        permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                Log.d(TAG, "Permission result: " + granted);
                startSse();
            }
        );

        setupWebView(savedInstanceState);

        // Запрашиваем разрешение через 1 сек после старта
        webView.postDelayed(() -> {
            Log.d(TAG, "Requesting notification permission");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission already granted");
                    startSse();
                } else {
                    permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                }
            } else {
                startSse();
            }
        }, 1000);
    }

    private void startSse() {
        String token = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", null);
        Log.d(TAG, "startSse token=" + (token != null ? "present(len="+token.length()+")" : "null"));
        try {
            Intent i = new Intent(this, SseService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            Log.d(TAG, "SseService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SseService: " + e);
        }
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
            @JavascriptInterface
            public void setToken(String token) {
                Log.d(TAG, "setToken called, len=" + (token != null ? token.length() : 0));
                if (token == null || token.isEmpty()) return;
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString("token", token).apply();
                // Перезапускаем сервис с новым токеном
                runOnUiThread(() -> startSse());
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                progressBar.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v, String u) {
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "Page loaded: " + u);
                v.evaluateJavascript(
                    "(function(){" +
                    "var t=localStorage.getItem(\"token\");" +
                    "if(t&&t!==\"null\"&&t!==\"undefined\")AndroidBridge.setToken(t);" +
                    "return t;" +
                    "})()", val -> Log.d(TAG, "localStorage token: " + val));
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.proceed();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
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
        else webView.loadUrl(URL);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(k, e);
    }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() { if (webView != null) webView.destroy(); super.onDestroy(); }
}
