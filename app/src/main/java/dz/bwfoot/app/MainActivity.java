package dz.bwfoot.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import android.util.Base64;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String SITE =
            "https://bw-foot-10.hatchable.site/";

    private static final String CHANNEL_ID = "bw_foot_downloads";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                android.graphics.Color.rgb(16, 26, 53));
        getWindow().setNavigationBarColor(
                android.graphics.Color.rgb(16, 26, 53));

        createNotificationChannel();

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(
                webView, true);

        webView.addJavascriptInterface(
                new FileBridge(), "AndroidDownload");

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    android.webkit.WebResourceRequest request) {

                Uri uri = request.getUrl();

                if (uri != null &&
                        ("https".equalsIgnoreCase(uri.getScheme()) ||
                         "http".equalsIgnoreCase(uri.getScheme()))) {

                    String host = uri.getHost();

                    if (host != null &&
                            (host.equals("bw-foot-10.hatchable.site") ||
                             host.endsWith(".hatchable.site"))) {
                        return false;
                    }

                    Toast.makeText(
                            MainActivity.this,
                            "تم منع فتح رابط خارجي",
                            Toast.LENGTH_SHORT).show();

                    return true;
                }

                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (url != null && url.startsWith(SITE)) {
                    installDownloadSupport();
                }
            }
        });

        webView.setDownloadListener(
                (url, userAgent, disposition, mimeType, length) -> {
                    if (url == null) return;

                    if (url.startsWith("http://") ||
                            url.startsWith("https://")) {
                        startDownload(
                                url, userAgent, disposition, mimeType);
                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                "نوع رابط التنزيل غير مدعوم",
                                Toast.LENGTH_LONG).show();
                    }
                });

        setContentView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(SITE);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    }, 100);
        }
    }

    private void startDownload(
            String url,
            String userAgent,
            String disposition,
            String mimeType) {

        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));

            String cookie =
                    CookieManager.getInstance().getCookie(url);

            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }

            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            request.setTitle(
                    URLUtil.guessFileName(url, disposition, mimeType));

            request.setDescription("تحميل ملف من BW Foot");

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, disposition, mimeType));

            DownloadManager manager =
                    (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

            if (manager == null) {
                throw new Exception("مدير التنزيلات غير متاح");
            }

            manager.enqueue(request);

            Toast.makeText(
                    this,
                    "بدأ تحميل الملف. راجع مجلد التنزيلات.",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "تعذر بدء التحميل. تحقق من الرابط والاتصال.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void installDownloadSupport() {
        String script =
                "(function() {" +
                "if(window.bwDownloadInstalled)return;" +
                "window.bwDownloadInstalled=true;" +

                "document.addEventListener('click',function(e){" +
                "var a=e.target.closest('a[download]');" +
                "if(!a)return;" +
                "var u=a.href||'';" +
                "if(!u.startsWith('blob:')&&!u.startsWith('data:'))return;" +
                "e.preventDefault();" +

                "fetch(u).then(function(r){" +
                "if(!r.ok)throw new Error('fetch failed');" +
                "return r.blob();" +
                "}).then(function(b){" +
                "var fr=new FileReader();" +
                "fr.onload=function(){" +
                "var s=String(fr.result).split(',')[1];" +
                "if(s)AndroidDownload.saveFile(" +
                "s,a.download||'BW-Foot-download'," +
                "b.type||'application/octet-stream');" +
                "else AndroidDownload.showError();" +
                "};" +
                "fr.onerror=function(){AndroidDownload.showError();};" +
                "fr.readAsDataURL(b);" +
                "}).catch(function(){AndroidDownload.showError();});" +
                "},true);" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    private class FileBridge {

        @JavascriptInterface
        public void saveFile(
                String base64, String filename, String mimeType) {
            runOnUiThread(() ->
                    saveBase64File(base64, filename, mimeType));
        }

        @JavascriptInterface
        public void showError() {
            runOnUiThread(() ->
                    Toast.makeText(
                            MainActivity.this,
                            "تعذر تجهيز الملف للتنزيل",
                            Toast.LENGTH_LONG).show());
        }
    }

    private void saveBase64File(
            String base64, String filename, String mimeType) {

        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

            if (filename == null || filename.trim().isEmpty()) {
                filename = "BW-Foot-download";
            }

            filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri uri = getContentResolver().insert(collection, values);

            if (uri == null) {
                throw new Exception("تعذر إنشاء الملف");
            }

            try (OutputStream out =
                         getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new Exception("تعذر فتح الملف");
                }
                out.write(bytes);
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);

            Toast.makeText(
                    this,
                    "تم حفظ الملف في مجلد التنزيلات",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "فشل حفظ الملف. حاول مجدداً.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "تنزيلات BW Foot",
                    NotificationManager.IMPORTANCE_DEFAULT);

            channel.setDescription("إشعارات تنزيل الملفات");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidDownload");
            webView.destroy();
        }
        super.onDestroy();
    }
}
