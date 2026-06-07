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

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "http://186.246.46.119/app/";
    private static final String PREFS   = "nexus_prefs";
    private static final int    REQ     = 42;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (!asked) {
            asked = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ);
            } else {
                startPollService();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        startPollService();
    }

    private void startPollService() {
        Intent svc = new Intent(this, PollService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            "nexus_messages", "Сообщения NEXUS", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(ch);
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
                if (token == null || token.isEmpty()) return;
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString("token", token).apply();
                // Перезапускаем сервис с новым токеном
                runOnUiThread(() -> startPollService());
            }
            @JavascriptInterface
            public void show(String title, String body) {
                runOnUiThread(() -> showDirectNotif(title, body));
            }
        }, "AndroidBridge");

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void show(String title, String body) {
                runOnUiThread(() -> showDirectNotif(title, body));
            }
        }, "AndroidNotify");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                progressBar.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                v.evaluateJavascript(
                    "(function(){var t=localStorage.getItem('nx_token');" +
                    "if(t&&t!='null'&&t!='undefined'&&t!='')AndroidBridge.setToken(t);})()",
                    null);
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
        else webView.loadUrl(APP_URL);
    }

    private void showDirectNotif(String title, String body) {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify((int)(System.currentTimeMillis() % 10000),
                new NotificationCompat.Builder(this, "nexus_messages")
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(title.isEmpty() ? "NEXUS" : title)
                    .setContentText(body.isEmpty() ? "Новое сообщение" : body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).setContentIntent(pi).build());
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); webView.saveState(out);
    }
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); return true;
        }
        return super.onKeyDown(k, e);
    }
    @Override protected void onPause()   { super.onPause();   webView.onPause();  }
    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
