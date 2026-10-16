package app.freedev.autoprint;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AutoPrint: ek hi app sabhi shops ke liye.
 * Ye sirf aapki website (customer panel) ko app ke andar kholta hai, aur
 * file upload, camera, UPI apps, call/WhatsApp links ko phone ke saath jodta hai.
 */
public class MainActivity extends AppCompatActivity {

    private static final int NAVY = Color.parseColor("#0b1020");
    private static final String OFFLINE_URL = "file:///android_asset/offline.html";

    private WebView webView;
    private ProgressBar progress;
    private String appHost = "";
    private String lastUrl;

    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraUri;
    private PermissionRequest pendingWebPermission;
    private ActivityResultLauncher<Intent> fileLauncher;
    private ActivityResultLauncher<String> cameraPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String host = Uri.parse(BuildConfig.START_URL).getHost();
        if (host != null) appHost = host.toLowerCase(Locale.ROOT);

        fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            ValueCallback<Uri[]> cb = filePathCallback;
            filePathCallback = null;
            if (cb == null) return;
            Uri[] uris = null;
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data == null || (data.getData() == null && data.getClipData() == null)) {
                    if (cameraUri != null) uris = new Uri[]{cameraUri};
                } else {
                    uris = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), data);
                }
            }
            cb.onReceiveValue(uris);
            cameraUri = null;
        });

        cameraPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            PermissionRequest req = pendingWebPermission;
            pendingWebPermission = null;
            if (req != null) {
                if (granted) req.grant(req.getResources());
                else req.deny();
            }
        });

        buildUi();
        configureWebView();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                String cur = webView.getUrl();
                boolean onOffline = cur != null && cur.startsWith("file:");
                if (webView.canGoBack() && !onOffline) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            // purani state wapas aa gayi
        } else {
            webView.loadUrl(initialUrl(getIntent()));
        }

        // Camera (photo khichne aur QR scan ke liye) ki permission, ek baar
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void buildUi() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(android.view.View.GONE);
        int h = (int) (3 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        pl.gravity = Gravity.TOP;
        root.addView(progress, pl);

        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " AutoPrintApp/" + BuildConfig.VERSION_NAME);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AppBridge(), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url != null && url.startsWith("http")) lastUrl = url;
                progress.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(android.view.View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadUrl(OFFLINE_URL);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                if (newProgress >= 100) progress.setVisibility(android.view.View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                cameraUri = null;

                Intent content = params.createIntent();
                Intent chooser = Intent.createChooser(content, "File chuno");

                if (wantsImages(params)
                        && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    Intent cam = buildCameraIntent();
                    if (cam != null) {
                        List<Intent> extra = new ArrayList<>();
                        extra.add(cam);
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toArray(new Intent[0]));
                    }
                }
                try {
                    fileLauncher.launch(chooser);
                } catch (Exception e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    toast("File chooser nahi khul paya");
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> {
                    String originHost = request.getOrigin() != null ? request.getOrigin().getHost() : null;
                    if (originHost == null || !originHost.toLowerCase(Locale.ROOT).equals(appHost)) {
                        request.deny();
                        return;
                    }
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        if (pendingWebPermission != null) pendingWebPermission.deny();
                        pendingWebPermission = request;
                        cameraPermLauncher.launch(Manifest.permission.CAMERA);
                    }
                });
            }
        });
    }

    private boolean wantsImages(WebChromeClient.FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null) continue;
            String x = t.toLowerCase(Locale.ROOT);
            if (x.isEmpty() || x.contains("image") || x.contains("*/*") || x.contains("jpg") || x.contains("png")) {
                return true;
            }
        }
        return false;
    }

    private Intent buildCameraIntent() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = File.createTempFile("photo_", ".jpg", dir);
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return cam;
        } catch (Exception e) {
            cameraUri = null;
            return null;
        }
    }

    /** Website ke links: apni site webview mein, baaki (UPI, call, WhatsApp...) phone ke apps mein. */
    private boolean handleUrl(String url) {
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);

        if (scheme.equals("http") || scheme.equals("https")) {
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("wa.me") || host.endsWith("whatsapp.com")
                    || host.equals("play.google.com") || host.equals("maps.google.com")) {
                return openExternal(url);
            }
            return false; // payment / bank pages bhi app ke andar hi khulein
        }
        if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")
                || scheme.equals("file") || scheme.equals("javascript")) {
            return false;
        }
        return openExternal(url);
    }

    private boolean openExternal(String url) {
        Intent i = null;
        try {
            if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            String fb = i != null ? i.getStringExtra("browser_fallback_url") : null;
            if (fb != null && fb.startsWith("https://")) {
                webView.loadUrl(fb);
            } else {
                toast("Ye app phone mein nahi mila. Doosra UPI app ya QR try karo.");
            }
        } catch (Exception e) {
            toast("Link nahi khul paya");
        }
        return true;
    }

    private String initialUrl(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri d = intent.getData();
            if ("https".equals(d.getScheme()) && d.getHost() != null
                    && appHost.equals(d.getHost().toLowerCase(Locale.ROOT))) {
                return d.toString();
            }
        }
        return BuildConfig.START_URL;
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) {
            webView.loadUrl(initialUrl(intent));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    /** Offline page ka "Dobara try karo" button isse chalta hai. */
    public class AppBridge {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> webView.loadUrl(lastUrl != null ? lastUrl : BuildConfig.START_URL));
        }
    }
}
