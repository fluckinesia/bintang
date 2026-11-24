package id.test.fixedbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

/** Minimal fixed-location browser shell for one default website. */
public class MainActivity extends Activity {
    private static final double FIXED_LATITUDE = -6.2212607;
    private static final double FIXED_LONGITUDE = 106.8321831;
    /* Google Maps 153m denotes map view height; GPS altitude is unspecified. */
    private static final int FIXED_ACCURACY_METERS = 15;
    private static final String DEFAULT_URL = "https://star-asn.kemenimipas.go.id/authentication/login";
    private static final int REQ_CAMERA = 101;
    private static final int REQ_FILE = 102;

    private WebView browser;
    private TextView chip;
    private TextView subtitle;
    private TextView coords;
    private TextView status;
    private ImageButton refreshBtn;

    private ScriptHandler activeScript;
    private String mockOrigin = null;
    private PermissionRequest pendingCamera;
    private ValueCallback<Uri[]> fileCallback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        setContentView(R.layout.activity_main);

        browser = findViewById(R.id.webview);
        chip = findViewById(R.id.chip);
        subtitle = findViewById(R.id.subtitle);
        coords = findViewById(R.id.coords);
        status = findViewById(R.id.status);
        refreshBtn = findViewById(R.id.refresh);

        coords.setText(String.format(Locale.US, "GPS terkunci • %.7f, %.7f", FIXED_LATITUDE, FIXED_LONGITUDE));
        setStatus("Memuat halaman...");

        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " STARASNBrowser/1.2");
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!request.isForMainFrame()) return false;
                if (u == null) return true;
                String scheme = u.getScheme();
                if ("https".equalsIgnoreCase(scheme)) return false;
                if ("http".equalsIgnoreCase(scheme) && isLocalHost(u.getHost())) return false;
                setStatus("Navigasi dibatasi ke HTTPS.");
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                updateForUrl(url);
                setStatus("Memuat halaman...");
            }

            @Override public void onPageFinished(WebView view, String url) {
                updateForUrl(url);
                setStatus("");
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    setStatus("Gagal memuat halaman.");
                }
            }
        });

        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> requestCameraPermission(request));
            }

            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingCamera == request) pendingCamera = null;
            }

            @Override public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }

            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    setStatus("Pemilih berkas tidak tersedia.");
                    return true;
                }
            }
        });

        refreshBtn.setOnClickListener(v -> browser.reload());
        navigate(DEFAULT_URL);
    }

    private static boolean isLocalHost(String host) {
        return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("10.0.2.2") || host.equals("[::1]"));
    }

    private String normalizeUrl(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("URL kosong");
        if (!raw.matches("(?i)^https?://.*")) raw = "https://" + raw;
        Uri u = Uri.parse(raw);
        if (u.getHost() == null || u.getHost().isEmpty()) throw new IllegalArgumentException("Format URL tidak valid");
        String scheme = u.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !("http".equalsIgnoreCase(scheme) && isLocalHost(u.getHost()))) {
            throw new IllegalArgumentException("Gunakan HTTPS atau HTTP localhost saja");
        }
        return u.toString();
    }

    private String originOf(String url) {
        try {
            Uri u = Uri.parse(url);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null) return null;
            int port = u.getPort();
            return scheme.toLowerCase(Locale.US) + "://" + host.toLowerCase(Locale.US) + (port < 0 ? "" : ":" + port);
        } catch (Exception e) {
            return null;
        }
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void resetScript() {
        if (activeScript != null) {
            activeScript.remove();
            activeScript = null;
        }
        mockOrigin = null;
    }

    private boolean installMockFor(String url) {
        resetScript();
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            setStatus("WebView terlalu lama. Mohon update Android System WebView.");
            return false;
        }
        try {
            String origin = originOf(url);
            if (origin == null) throw new IllegalArgumentException("Origin website tidak valid");
            String js = readAsset("geo_mock.js")
                    .replace("__LAT__", Double.toString(FIXED_LATITUDE))
                    .replace("__LON__", Double.toString(FIXED_LONGITUDE))
                    .replace("__ALT__", "null")
                    .replace("__ACCURACY__", Integer.toString(FIXED_ACCURACY_METERS));
            activeScript = WebViewCompat.addDocumentStartJavaScript(browser, js, Collections.singleton(origin));
            mockOrigin = origin;
            return true;
        } catch (Exception e) {
            setStatus("Pengaturan GPS gagal.");
            return false;
        }
    }

    private void navigate(String input) {
        try {
            String url = normalizeUrl(input);
            if (!installMockFor(url)) return;
            browser.loadUrl(url);
        } catch (Exception e) {
            setStatus(e.getMessage());
        }
    }

    private void updateForUrl(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        subtitle.setText(host == null ? "Default page" : host);
        boolean active = mockOrigin != null && mockOrigin.equals(originOf(url));
        chip.setText(active ? "GPS Locked" : "Loading");
    }

    private void setStatus(String s) {
        if (s == null || s.trim().isEmpty()) {
            status.setVisibility(View.GONE);
            status.setText("");
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(s);
        }
    }

    private void requestCameraPermission(PermissionRequest req) {
        boolean wantsCamera = false;
        for (String resource : req.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) wantsCamera = true;
        }
        String pageOrigin = originOf(browser.getUrl());
        String requestOrigin = originOf(req.getOrigin().toString());
        if (!wantsCamera || pageOrigin == null || !pageOrigin.equals(requestOrigin)) {
            req.deny();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Akses kamera")
                .setMessage("Izinkan website ini menggunakan kamera perangkat?")
                .setNegativeButton("Tolak", (d, which) -> req.deny())
                .setPositiveButton("Izinkan", (d, which) -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingCamera = req;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                }).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && pendingCamera != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingCamera.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                pendingCamera.deny();
            }
            pendingCamera = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (browser.canGoBack()) browser.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        resetScript();
        if (pendingCamera != null) pendingCamera.deny();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        browser.destroy();
        super.onDestroy();
    }
}
