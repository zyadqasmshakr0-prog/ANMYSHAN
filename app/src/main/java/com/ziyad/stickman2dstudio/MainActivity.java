package com.ziyad.stickman2dstudio;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
        private File pendingTempFile;
        private BufferedOutputStream pendingOutStream;
        private String pendingFileName;
        private String pendingMimeType;

        @JavascriptInterface
        public synchronized boolean startVideoSave(String fileName, String mimeType) {
            try {
                closePendingStreamQuietly();
                pendingFileName = (fileName != null && !fileName.isEmpty()) ? fileName : ("Stickman_Studio_" + System.currentTimeMillis() + ".mp4");
                pendingMimeType = (mimeType != null && !mimeType.isEmpty()) ? mimeType : (pendingFileName.endsWith(".mp4") ? "video/mp4" : "video/webm");
                pendingTempFile = new File(getCacheDir(), "export_tmp_" + System.currentTimeMillis() + "_" + pendingFileName);
                pendingOutStream = new BufferedOutputStream(new FileOutputStream(pendingTempFile), 262144);
                return true;
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ تعذر بدء ملف الفيديو: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean appendVideoChunk(String base64Chunk) {
            try {
                if (pendingOutStream == null || base64Chunk == null || base64Chunk.isEmpty()) return false;
                byte[] chunkBytes = Base64.decode(base64Chunk, Base64.DEFAULT);
                pendingOutStream.write(chunkBytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public synchronized void finishVideoSave() {
            try {
                if (pendingOutStream != null) {
                    pendingOutStream.flush();
                    pendingOutStream.close();
                    pendingOutStream = null;
                }
                if (pendingTempFile == null || !pendingTempFile.exists() || pendingTempFile.length() == 0) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ ملف الفيديو فارغ!", Toast.LENGTH_LONG).show());
                    return;
                }

                final long totalBytes = pendingTempFile.length();
                final String finalName = pendingFileName;
                final String finalMime = pendingMimeType;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Video.Media.DISPLAY_NAME, finalName);
                    values.put(MediaStore.Video.Media.MIME_TYPE, finalMime);
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StickmanStudio");
                    values.put(MediaStore.Video.Media.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream os = getContentResolver().openOutputStream(uri);
                             FileInputStream fis = new FileInputStream(pendingTempFile)) {
                            if (os != null) {
                                byte[] buf = new byte[65536];
                                int r;
                                while ((r = fis.read(buf)) != -1) {
                                    os.write(buf, 0, r);
                                }
                                os.flush();
                            }
                        }
                        ContentValues doneValues = new ContentValues();
                        doneValues.put(MediaStore.Video.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, doneValues, null, null);
                    }
                } else {
                    File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "StickmanStudio");
                    if (!moviesDir.exists()) moviesDir.mkdirs();
                    File outFile = new File(moviesDir, finalName);
                    try (FileOutputStream fos = new FileOutputStream(outFile);
                         FileInputStream fis = new FileInputStream(pendingTempFile)) {
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = fis.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                        }
                        fos.flush();
                    }
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{outFile.getAbsolutePath()}, new String[]{finalMime}, null);
                }

                try { pendingTempFile.delete(); } catch (Exception ignored) {}
                final String mbStr = String.format(java.util.Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0));
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ تم حفظ الفيديو فائق الدقة (" + mbStr + ") في الاستوديو (Movies/StickmanStudio)!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ خطأ أثناء حفظ الفيديو: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                closePendingStreamQuietly();
            }
        }

        private void closePendingStreamQuietly() {
            try {
                if (pendingOutStream != null) {
                    pendingOutStream.close();
                }
            } catch (Exception ignored) {}
            pendingOutStream = null;
        }

        @JavascriptInterface
        public void saveVideoToGallery(String base64Data, String fileName) {
            if (startVideoSave(fileName, fileName.endsWith(".mp4") ? "video/mp4" : "video/webm")) {
                appendVideoChunk(base64Data);
                finishVideoSave();
            }
        }
    }
}
