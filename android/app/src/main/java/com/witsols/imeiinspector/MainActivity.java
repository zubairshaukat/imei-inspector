package com.witsols.imeiinspector;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * Hosts the bundled web app (assets/www) in a WebView served from
 * https://appassets.androidplatform.net, so fetch() of the bundled database works offline.
 * Adds: a JS bridge (window.IMEIApp) for the phone self-check, camera access for the
 * barcode scanner, and a file chooser for "scan from a photo".
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_CAMERA = 1;
    private static final int REQ_FILE = 2;

    private WebView web;
    private PermissionRequest pendingCamera;
    private ValueCallback<Uri[]> pendingFile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new DeviceBridge(), "IMEIApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsCamera = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                }
                if (!wantsCamera || !HOST.equals(request.getOrigin().getHost())) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingCamera = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFile != null) pendingFile.onReceiveValue(null);
                pendingFile = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose a photo of the barcode"), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingFile = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(START);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQ_CAMERA || pendingCamera == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pendingCamera.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            pendingCamera.deny();
        }
        pendingCamera = null;
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_FILE || pendingFile == null) return;
        Uri[] uris = null;
        if (result == RESULT_OK && data != null && data.getData() != null) uris = new Uri[]{data.getData()};
        pendingFile.onReceiveValue(uris);
        pendingFile = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Read-only facts about this phone, used by the authenticity self-check. */
    private class DeviceBridge {
        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public String getDeviceInfo() {
            JSONObject o = new JSONObject();
            try {
                o.put("manufacturer", Build.MANUFACTURER);
                o.put("brand", Build.BRAND);
                o.put("model", Build.MODEL);
                o.put("device", Build.DEVICE);
                o.put("product", Build.PRODUCT);
                o.put("hardware", Build.HARDWARE);
                o.put("board", Build.BOARD);
                o.put("release", Build.VERSION.RELEASE);
                o.put("sdk", Build.VERSION.SDK_INT);
                o.put("fingerprint", Build.FINGERPRINT);
                o.put("tags", Build.TAGS);
                o.put("type", Build.TYPE);
                o.put("abis", new JSONArray(Build.SUPPORTED_ABIS));
                o.put("cores", Runtime.getRuntime().availableProcessors());

                String name = null;
                if (Build.VERSION.SDK_INT >= 25) name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
                o.put("deviceName", name == null ? "" : name);

                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                o.put("ramBytes", mi.totalMem);

                StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
                o.put("storageBytes", fs.getTotalBytes());

                DisplayMetrics dm = new DisplayMetrics();
                ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
                o.put("screenW", Math.min(dm.widthPixels, dm.heightPixels));
                o.put("screenH", Math.max(dm.widthPixels, dm.heightPixels));
                o.put("densityDpi", dm.densityDpi);

                o.put("emulator", isEmulator());
                o.put("rooted", hasRootSigns());
                o.put("appVersion", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            } catch (Exception ignored) { }
            return o.toString();
        }
    }

    private static boolean isEmulator() {
        String fp = Build.FINGERPRINT, model = Build.MODEL, hw = Build.HARDWARE;
        return fp.startsWith("generic") || fp.startsWith("unknown") || fp.contains("emulator")
                || model.contains("google_sdk") || model.contains("Emulator") || model.contains("Android SDK built for")
                || Build.MANUFACTURER.contains("Genymotion")
                || hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox86")
                || Build.PRODUCT.contains("sdk_gphone");
    }

    private static boolean hasRootSigns() {
        if (Build.TAGS != null && Build.TAGS.contains("test-keys")) return true;
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk",
                "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/data/adb/magisk"};
        for (String p : paths) {
            if (new File(p).exists()) return true;
        }
        return false;
    }
}
