package pl.stashreader;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String VERSION = Refresher.VERSION;
    private static final int DARK = Color.rgb(27, 42, 65), LIGHT = Color.rgb(246, 244, 239);
    private WebView web;
    private FrameLayout root;
    private Db db;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = Db.get(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        // A WebView ignores its own padding, so the system-bar insets go on a parent layout.
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((View v, WindowInsets i) -> {
                android.graphics.Insets p = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(p.left, p.top, p.right, p.bottom);
                return WindowInsets.CONSUMED;
            });
        } else {
            root.setFitsSystemWindows(true);
        }
        setContentView(root);
        applyTheme();
        web.loadUrl("file:///android_asset/index.html");
        refreshInBackground();
    }

    private boolean dark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("deprecation")
    private void applyTheme() {
        int bg = dark() ? DARK : LIGHT;
        root.setBackgroundColor(bg);
        web.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(dark() ? 0 : light, light);
        } else {
            int f = dark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyTheme();
        web.evaluateJavascript("window.setTheme&&setTheme(" + dark() + ")", null);
    }

    private void refreshInBackground() {
        new Thread(() -> {
            Refresher.all(MainActivity.this);
            // when everything failed (no network yet at start-up), try once more after a pause
            try {
                JSONObject c = db.counts();
                if (c.optInt("sources") > 0 && c.optInt("failed") >= c.optInt("sources")) {
                    Thread.sleep(10000);
                    Refresher.all(MainActivity.this);
                }
            } catch (Exception ignored) { }
        }, "refresh").start();
    }

    class Bridge {
        @JavascriptInterface public String version() { return VERSION; }
        @JavascriptInterface public boolean dark() { return MainActivity.this.dark(); }
        @JavascriptInterface public String status() {
            try {
                JSONObject o = db.counts();
                o.put("refreshing", Refresher.busy());
                return o.toString();
            } catch (Exception e) { return "{\"refreshing\":false}"; }
        }
        @JavascriptInterface public String feed() { try { return db.feedJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public String kept() { try { return db.keptJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public String item(String id) { try { return db.itemJson(Long.parseLong(id)); } catch (Exception e) { return "{}"; } }
        @JavascriptInterface public void keep(String id) { db.setState(Long.parseLong(id), Db.KEPT); }
        @JavascriptInterface public void done(String id) { db.setState(Long.parseLong(id), Db.DONE); }
        @JavascriptInterface public void unkeep(String id) { db.setState(Long.parseLong(id), Db.NEW); }
        @JavascriptInterface public String sources() { try { return db.sourcesJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public void refresh() { refreshInBackground(); }
        @JavascriptInterface public String addSource(String url) {
            try {
                String u = FeedParser.validateUrl(url);
                final long id = db.addSource(u, "", "");
                if (id == -1) return "That source is already in the list";
                new Thread(() -> Refresher.single(MainActivity.this, id), "add-source").start();
                return "";
            } catch (FeedParser.FeedException e) { return e.getMessage(); }
        }
        @JavascriptInterface public void setSourceEnabled(String id, boolean on) { db.setEnabled(Long.parseLong(id), on); }
        @JavascriptInterface public void removeSource(String id) { db.removeSource(Long.parseLong(id)); }
        @JavascriptInterface public void openUrl(String url) {
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        }
    }

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
