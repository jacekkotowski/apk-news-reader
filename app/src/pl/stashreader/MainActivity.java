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
    private android.content.SharedPreferences prefs;
    private static final int DEFAULT_HOURS = 6;
    private volatile String addState = "";
    private final java.util.concurrent.ConcurrentHashMap<String, String> pages = new java.util.concurrent.ConcurrentHashMap<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = Db.get(this);
        prefs = getSharedPreferences("stash", MODE_PRIVATE);
        RefreshJob.schedule(this, prefs.getInt("refreshHours", DEFAULT_HOURS));
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
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

    private static final int PICK_FOLDER = 41;

    /** Rewrites the item's note file when an export folder is set. */
    private void sync(final long id) {
        if (id < 0 || Exporter.folder(this) == null) return;
        new Thread(() -> {
            String e = Exporter.write(MainActivity.this, db, id);
            if (!e.isEmpty()) runOnUiThread(() -> web.evaluateJavascript("window.exportMsg&&exportMsg(" + JSONObject.quote(e) + ")", null));
        }, "export-one").start();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK_FOLDER || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs.edit().putString(Exporter.PREF, tree.toString()).apply();
            web.evaluateJavascript("window.folderChosen&&folderChosen()", null);
        } catch (RuntimeException e) {
            web.evaluateJavascript("window.exportMsg&&exportMsg('The folder could not be kept: ' + " + JSONObject.quote(String.valueOf(e.getMessage())) + ")", null);
        }
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
        @JavascriptInterface public void keep(String id) { db.setState(Long.parseLong(id), Db.KEPT); sync(Long.parseLong(id)); }
        @JavascriptInterface public void done(String id) { db.setState(Long.parseLong(id), Db.DONE); }
        @JavascriptInterface public String highlights(String id) { try { return db.highlightsJson(Long.parseLong(id)); } catch (Exception e) { return "[]"; } }
        /** Adds a highlight (this keeps the item) and returns true when something was stored. */
        @JavascriptInterface public boolean addHighlight(String id, String text) { boolean ok = db.addHighlight(Long.parseLong(id), text); if (ok) sync(Long.parseLong(id)); return ok; }
        @JavascriptInterface public void setHighlightNote(String hid, String note) { long h = Long.parseLong(hid); db.setHighlightNote(h, note); sync(db.highlightItem(h)); }
        @JavascriptInterface public void removeHighlight(String hid) { long h = Long.parseLong(hid); long item = db.highlightItem(h); db.removeHighlight(h); sync(item); }
        @JavascriptInterface public void setNote(String id, String note) { db.setNote(Long.parseLong(id), note); sync(Long.parseLong(id)); }
        @JavascriptInterface public void share(String id) {
            final String md = Exporter.markdown(db, Long.parseLong(id));
            if (md == null) return;
            final MarkdownExport.Note n = db.noteFor(Long.parseLong(id));
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_TEXT, md).putExtra(Intent.EXTRA_SUBJECT, n.title).putExtra(Intent.EXTRA_TITLE, n.title);
                startActivity(Intent.createChooser(i, "Share note"));
            });
        }
        @JavascriptInterface public String exportFolder() { return Exporter.folderName(MainActivity.this); }
        @JavascriptInterface public void chooseFolder() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try { startActivityForResult(i, PICK_FOLDER); } catch (RuntimeException e) { web.evaluateJavascript("window.exportMsg&&exportMsg('This phone has no folder picker.')", null); }
            });
        }
        @JavascriptInterface public void clearFolder() { prefs.edit().remove(Exporter.PREF).apply(); }
        /** Writes every kept item to the folder; the answer comes back through exportMsg(). */
        @JavascriptInterface public void exportAll() {
            new Thread(() -> {
                String m = Exporter.writeAll(MainActivity.this, db);
                runOnUiThread(() -> web.evaluateJavascript("window.exportMsg&&exportMsg(" + JSONObject.quote(m) + ")", null));
            }, "export-all").start();
        }
        @JavascriptInterface public void less(String id) { db.setState(Long.parseLong(id), Db.LESS); }
        @JavascriptInterface public String topics() { try { return db.topicsJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public boolean addTopic(String phrase) { return db.addTopic(phrase); }
        @JavascriptInterface public void setTopicWeight(String id, int w) { db.setTopicWeight(Long.parseLong(id), w); }
        @JavascriptInterface public void removeTopic(String id) { db.removeTopic(Long.parseLong(id)); }
        @JavascriptInterface public int refreshHours() { return prefs.getInt("refreshHours", DEFAULT_HOURS); }
        @JavascriptInterface public void setRefreshHours(int h) {
            prefs.edit().putInt("refreshHours", h).apply();
            RefreshJob.schedule(MainActivity.this, h);
        }
        @JavascriptInterface public void unkeep(String id) { db.setState(Long.parseLong(id), Db.NEW); }
        @JavascriptInterface public String sources() { try { return db.sourcesJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public void refresh() { refreshInBackground(); }
        @JavascriptInterface public String addSource(String input) {
            if ("pending".equals(addState)) return "Still checking the last address";
            addState = "pending";
            new Thread(() -> {
                String m;
                try { m = Discoverer.add(MainActivity.this, input); } catch (Throwable t) { m = "Unexpected problem: " + t; }
                addState = "done:" + m;
            }, "add-source").start();
            return "";
        }
        /** "" when idle, "pending" while working, "done:" plus a message (empty on success) once, then idle again. */
        @JavascriptInterface public String addStatus() {
            String v = addState;
            if (v.startsWith("done:")) addState = "";
            return v;
        }
        /** Adds a search on an open API (kind: arxiv, hn, hf, reddit, github, s2). Returns "" or a message. */
        @JavascriptInterface public String addQuerySource(String kind, String query) {
            if (!QuerySource.valid(kind)) return "Choose where to search";
            String q = QuerySource.clean(query);
            if (q.isEmpty() && QuerySource.needsQuery(kind)) return "Type what to search for";
            if (QuerySource.watches(kind)) {
                q = FeedDiscovery.normalizeInput(q);
                if (q.isEmpty()) return "That is not a web address. Try something like example.org/blog";
            }
            if (q.length() > 200) return "Keep it under 200 characters";
            final long id = db.addQuerySource(kind, q, "");
            if (id == -1) return "That search is already in the list";
            new Thread(() -> Refresher.single(MainActivity.this, id), "add-search").start();
            return "";
        }
        @JavascriptInterface public String queryKinds() {
            StringBuilder b = new StringBuilder("[");
            boolean first = true;
            for (int i = 0; i < QuerySource.KINDS.length; i++) {
                String k = QuerySource.KINDS[i];
                if (k.equals(QuerySource.RECS)) continue; // added by the app, not by hand
                if (!first) b.append(',');
                first = false;
                b.append("{\"kind\":\"").append(k).append("\",\"label\":\"").append(QuerySource.label(k)).append("\",\"needsQuery\":").append(QuerySource.needsQuery(k)).append('}');
            }
            return b.append(']').toString();
        }
        @JavascriptInterface public void fetchPage(String id) {
            final String key = id;
            pages.remove(key);
            new Thread(() -> {
                try { pages.put(key, "ok:" + Refresher.fetchPage(db.itemLink(Long.parseLong(key)))); }
                catch (Throwable t) { pages.put(key, "err:" + (t instanceof java.io.IOException ? t.getMessage() : "Unexpected problem: " + t)); }
            }, "fetch-page").start();
        }
        /** "" while loading, else "ok:" plus the page HTML or "err:" plus a message. */
        @JavascriptInterface public String pageResult(String id) { String v = pages.remove(id); return v == null ? "" : v; }
        @JavascriptInterface public void saveArticle(String id, String html) { db.saveArticle(Long.parseLong(id), html); }
        @JavascriptInterface public void setSourceEnabled(String id, boolean on) { db.setEnabled(Long.parseLong(id), on); }
        @JavascriptInterface public void removeSource(String id) { db.removeSource(Long.parseLong(id)); }
        @JavascriptInterface public void openUrl(String url) {
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        }
    }

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
