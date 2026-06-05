package com.nexus.app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "http://186.246.46.119/app/";
    private static final String PREFS   = "nexus_prefs";

    private WebView webView;
    private ProgressBar progressBar;
    private ActivityResultLauncher<String> permLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

        // Создаём канал уведомлений
        NotificationChannel ch = new NotificationChannel(
            "nexus_messages", "Сообщения NEXUS",
            NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);

        // Регистрируем запрос разрешения
        permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> { if (granted) launchSseService(); }
        );

        setupWebView(savedInstanceState);
    }

    private void launchSseService() {
        try {
            Intent i = new Intent(this, SseService.class);
            startForegroundService(i);
        } catch (Exception ignored) {}
    }

    // Вызывается после того как страница загрузилась и токен извлечён
    private void onTokenReady() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            int granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS);
            if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchSseService();
            } else {
                permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            launchSseService();
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
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString("token", token).apply();
                runOnUiThread(() -> onTokenReady());
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                progressBar.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                // Извлекаем токен из localStorage
                v.evaluateJavascript(
                    "(function(){" +
                    "  var t=localStorage.getItem('token');" +
                    "  if(t && t!='null') AndroidBridge.setToken(t);" +
                    "})()", null);
            }
            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.proceed();
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("http://186.246.46.119")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }
        });

        if (savedState != null) webView.restoreState(savedState);
        else webView.loadUrl(APP_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(k, e);
    }

    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onPause()   { super.onPause();   webView.onPause(); }
    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
