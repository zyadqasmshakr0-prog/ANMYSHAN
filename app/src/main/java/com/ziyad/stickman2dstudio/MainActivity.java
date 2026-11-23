package com.ziyad.stickman2dstudio;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestNeededPermissions();

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF060913);
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(false);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new AndroidStudioBridge(), "AndroidStudioBridge");
        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView vw, ValueCallback<Uri[]> filePathCb, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = filePathCb;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/stickman_2d_studio.html");
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
            requestPermissions(perms, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // No SYSTEM_UI_FLAG_HIDE_NAVIGATION so Android never intercepts or swallows the user's first touch tap!

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                "(function(){ if(document.body.classList.contains('cinema-mode-active')){ window.setCinemaFullscreenMode(false); return 'EXITED'; } return 'NONE'; })()",
                val -> {
                    if (val != null && val.contains("EXITED")) return;
                    if (webView.canGoBack()) webView.goBack();
                    else MainActivity.super.onBackPressed();
                }
            );
        } else {
            super.onBackPressed();
        }
    }

    public class AndroidStudioBridge {
        @JavascriptInterface
        public void saveVideoToGallery(String base64Data, String fileName) {
            try {
                byte[] videoBytes = Base64.decode(base64Data, Base64.DEFAULT);
                String mimeType = fileName.endsWith(".mp4") ? "video/mp4" : "video/webm";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StickmanStudio");
                    Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            os.write(videoBytes);
                            os.flush();
                            os.close();
                        }
                    }
                } else {
                    File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "StickmanStudio");
                    if (!moviesDir.exists()) moviesDir.mkdirs();
                    File outFile = new File(moviesDir, fileName);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(videoBytes);
                    fos.flush();
                    fos.close();
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{outFile.getAbsolutePath()}, new String[]{mimeType}, null);
                }

                runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ تم حفظ الفيديو في معرض الهاتف (Movies/StickmanStudio)!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ خطأ أثناء حفظ الفيديو: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
