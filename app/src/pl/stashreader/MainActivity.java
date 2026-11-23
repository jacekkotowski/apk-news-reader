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
    private final java.util.concurrent.ExecutorService gemExec = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ConcurrentHashMap<String, String> gemResults = new java.util.concurrent.ConcurrentHashMap<>();

    private interface Job { String run() throws Exception; }

    /** Runs one Gemini job at a time in the background; the result is read later with geminiResult(). */
    private void gem(final String key, final Job job) {
        gemResults.remove(key);
        gemExec.execute(() -> {
            String out;
            try { out = "ok:" + job.run(); }
            catch (Gemini.GeminiException e) { out = "err:" + e.getMessage(); }
            catch (java.net.UnknownHostException e) { out = "err:No network, so Gemini could not be reached."; }
            catch (java.net.SocketTimeoutException e) { out = "err:Gemini took too long to answer."; }
            catch (Exception e) { out = "err:" + e.getMessage(); }
            gemResults.put(key, out);
        });
    }

    private String apiKey() throws Gemini.GeminiException {
        String k = prefs.getString("geminiKey", "");
        if (k.isEmpty()) throw new Gemini.GeminiException("Add your Gemini key in Sources first.");
        return k;
    }
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
        handleShare(getIntent());
        // the first start with the automatic folder writes the notes of everything already kept
        if (Exporter.auto(this) && !prefs.getBoolean("autoExported", false)) {
            prefs.edit().putBoolean("autoExported", true).apply();
            new Thread(() -> Exporter.writeAll(MainActivity.this, db), "export-first").start();
        }
    }

    private volatile boolean backOpen = false;
    /** The page over the reader, or an open note, takes the Back button first. */
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (backOpen) web.evaluateJavascript("window.pageBack&&pageBack()", null);
        else super.onBackPressed();
    }

    private static final String PLAN = "plan";
    private static String today() { return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()); }

    /** Makes a new plan for the chosen minutes and stores it (ids, why lines, surprises) for the rest of the day. */
    private synchronized JSONObject makePlan(int mins) {
        try {
            Ranker.Plan pl = db.plan(mins);
            JSONObject p = new JSONObject();
            org.json.JSONArray ids = new org.json.JSONArray(), sur = new org.json.JSONArray(); JSONObject why = new JSONObject();
            for (Ranker.Pick k : pl.picks) { ids.put(k.id); if (k.surprise) sur.put(k.id); why.put("" + k.id, k.why == null ? "" : k.why); }
            p.put("day", today()); p.put("mins", mins); p.put("prov", Refresher.busy()); p.put("ids", ids); p.put("sur", sur); p.put("why", why);
            prefs.edit().putString(PLAN, p.toString()).apply();
            return p;
        } catch (Exception e) { return null; }
    }
    private synchronized JSONObject currentPlan() throws Exception {
        String s = prefs.getString(PLAN, "");
        if (!s.isEmpty()) {
            JSONObject p = new JSONObject(s);
            // a plan made while the sources were still loading, or one with nothing in it, is made again once they are loaded
            boolean stale = p.optJSONArray("ids") == null || p.getJSONArray("ids").length() == 0 || (p.optBoolean("prov") && !Refresher.busy());
            if (today().equals(p.optString("day")) && !stale) return p;
        }
        JSONObject p = makePlan(prefs.getInt("planMins", 15));
        if (p == null) throw new Exception("no plan");
        return p;
    }
    private java.util.Set<Long> planSurprises() {
        java.util.Set<Long> out = new java.util.HashSet<>();
        try {
            org.json.JSONArray a = new JSONObject(prefs.getString(PLAN, "{}")).optJSONArray("sur");
            if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.getLong(i));
        } catch (Exception ignored) { }
        return out;
    }

    @Override protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        handleShare(in);
    }

    /** A link shared from another app goes straight into the Stash. The title comes from the share, else from the page. */
    private void handleShare(Intent in) {
        if (in == null || !Intent.ACTION_SEND.equals(in.getAction()) || in.getBooleanExtra("handled", false)) return;
        in.putExtra("handled", true);
        final String text = in.getStringExtra(Intent.EXTRA_TEXT);
        final String url = Share.extractUrl(text);
        if (url.isEmpty()) { android.widget.Toast.makeText(this, "No web address found in what you shared", android.widget.Toast.LENGTH_LONG).show(); return; }
        final String given = Share.titleFor(in.getStringExtra(Intent.EXTRA_SUBJECT), text, url);
        new Thread(() -> {
            String title = given;
            if (title.isEmpty()) {
                try { title = PageWatch.meta(Refresher.fetchPage(url)).title; } catch (Exception ignored) { }
                if (title == null || title.isEmpty()) title = Share.hostOf(url);
            }
            final long id = db.addShared(url, title);
            runOnUiThread(() -> {
                android.widget.Toast.makeText(MainActivity.this, id < 0 ? "That link could not be saved" : "Saved to Stash", android.widget.Toast.LENGTH_SHORT).show();
                if (id >= 0) web.evaluateJavascript("window.sharedSaved&&sharedSaved()", null);
            });
            if (id >= 0) sync(id);
        }, "share").start();
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
        if (id < 0 || !Exporter.enabled(this)) return;
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
            prefs.edit().putString(Exporter.PREF, tree.toString()).putBoolean(Exporter.OFF, false).apply();
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
        /** The Feed with the reader's filters: type "", article, video or audio; sort "newest" or anything else for best match. */
        @JavascriptInterface public String feedFiltered(String type, String topic, String source, String query, String sort) {
            try {
                FeedFilter.Filters f = new FeedFilter.Filters();
                f.type = type == null ? "" : type; f.topic = topic == null ? "" : topic; f.query = query == null ? "" : query;
                try { f.source = Long.parseLong(source); } catch (RuntimeException e) { f.source = 0; }
                f.newest = "newest".equals(sort);
                return db.feedJson(f);
            } catch (Exception e) { return "[]"; }
        }
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
        @JavascriptInterface public void clearFolder() { prefs.edit().remove(Exporter.PREF).putBoolean(Exporter.OFF, true).apply(); }
        @JavascriptInterface public boolean autoFolderAvailable() { return Exporter.autoAvailable(); }
        @JavascriptInterface public void useAutoFolder() { prefs.edit().remove(Exporter.PREF).putBoolean(Exporter.OFF, false).apply(); }
        /** Writes every kept item to the folder; the answer comes back through exportMsg(). */
        @JavascriptInterface public void exportAll() {
            new Thread(() -> {
                String m = Exporter.writeAll(MainActivity.this, db);
                runOnUiThread(() -> web.evaluateJavascript("window.exportMsg&&exportMsg(" + JSONObject.quote(m) + ")", null));
            }, "export-all").start();
        }
        @JavascriptInterface public void less(String id) {
            long item = Long.parseLong(id);
            if (planSurprises().contains(item)) db.surpriseLess(item);
            db.setState(item, Db.LESS);
        }
        /** Today's Fit My Time list: {mins, used, total, items}. Made on the first call of the day, and again when a time is chosen. */
        @JavascriptInterface public String feedPlan() {
            try {
                JSONObject p = currentPlan();
                java.util.List<Long> ids = new java.util.ArrayList<>(); java.util.Map<Long, String> why = new java.util.HashMap<>();
                org.json.JSONArray ia = p.getJSONArray("ids"); JSONObject wj = p.getJSONObject("why");
                for (int i = 0; i < ia.length(); i++) { long id = ia.getLong(i); ids.add(id); why.put(id, wj.optString("" + id, "")); }
                org.json.JSONArray items = db.planCards(ids, why, planSurprises());
                JSONObject o = new JSONObject();
                o.put("mins", p.getInt("mins")); o.put("total", ia.length()); o.put("items", items);
                int used = 0;
                for (int i = 0; i < items.length(); i++) used += Ranker.minutes(items.getJSONObject(i).getString("kind"), items.getJSONObject(i).getInt("mins"));
                o.put("used", used);
                return o.toString();
            } catch (Exception e) { return "{\"mins\":15,\"total\":0,\"used\":0,\"items\":[]}"; }
        }
        @JavascriptInterface public void setPlanMins(int mins) {
            int m = mins == 5 || mins == 30 ? mins : 15;
            prefs.edit().putInt("planMins", m).apply();
            makePlan(m);
        }
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
        /** Loads any web address for the page view; the answer is read with pageResult(address). */
        @JavascriptInterface public void fetchUrl(final String url) {
            if (url == null) return;
            pages.remove(url);
            new Thread(() -> {
                if (!Sanitizer.isHttp(url)) { pages.put(url, "err:That is not a web address"); return; }
                try { pages.put(url, "ok:" + Refresher.fetchPage(url)); }
                catch (Throwable t) { pages.put(url, "err:" + (t instanceof java.io.IOException ? t.getMessage() : "Unexpected problem: " + t)); }
            }, "fetch-url").start();
        }
        /** Cleans HTML from the page view before the page shows it. */
        @JavascriptInterface public String cleanHtml(String html) { return Sanitizer.clean(html == null ? "" : html, 400000); }
        /** Keeps a page opened inside Stash as an item with its reader text. Returns the item id, or "" when it could not be saved. */
        @JavascriptInterface public String keepPage(String url, String title, String html) {
            if (url == null || !Sanitizer.isHttp(url)) return "";
            String t = Sanitizer.collapse(title == null ? "" : title);
            if (t.isEmpty()) t = Share.hostOf(url);
            if (t.length() > 200) t = t.substring(0, 200);
            long id = db.addShared(url, t);
            if (id < 0) return "";
            db.saveArticle(id, html == null ? "" : html);
            sync(id);
            return "" + id;
        }
        /** Passages from the Stash that match a question, each with its article link: [{n, id, kind, text, title, link, source}]. */
        @JavascriptInterface public String searchStash(String question) { return db.passagesJson(question == null ? "" : question); }
        /** "Answer with Gemini": answers only from those passages, with [n] citations. The result is read with geminiResult("ask"). */
        @JavascriptInterface public void askStash(final String question) {
            gem("ask", () -> {
                java.util.List<Passages.Passage> ps = db.searchPassages(question == null ? "" : question);
                if (ps.isEmpty()) return Passages.NO_ANSWER;
                java.util.List<String[]> in = new java.util.ArrayList<>();
                for (Passages.Passage p : ps) in.add(new String[]{p.title, p.text});
                String answer = Gemini.ask(apiKey(), Gemini.answerRequest(question, in), Refresher.AGENT).trim();
                if (answer.contains(Passages.NO_ANSWER)) return Passages.NO_ANSWER;
                answer = Passages.checkCitations(answer, ps.size());
                if (Passages.citations(answer, ps.size()) == 0) throw new Gemini.GeminiException("Gemini did not cite your passages, so its answer is not shown. Read the passages above instead.");
                return answer;
            });
        }
        @JavascriptInterface public void setBack(boolean open) { backOpen = open; }
        @JavascriptInterface public String notesJson() { try { return db.notesJson(); } catch (Exception e) { return "{\"items\":[],\"paths\":[]}"; } }
        @JavascriptInterface public void saveArticle(String id, String html) { db.saveArticle(Long.parseLong(id), html); }
        @JavascriptInterface public void setSourceEnabled(String id, boolean on) { db.setEnabled(Long.parseLong(id), on); }
        @JavascriptInterface public void removeSource(String id) { db.removeSource(Long.parseLong(id)); }
        /** Opens the episode in the phone's own audio player; the address comes from the database, never from the page. */
        @JavascriptInterface public void play(String id) {
            String u;
            try { u = db.itemAudio(Long.parseLong(id)); } catch (Exception e) { return; }
            if (u == null || !u.startsWith("https://")) return;
            final String url = u;
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "audio/*")); }
                catch (Exception e) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e2) { /* no app can play it */ } }
            });
        }
        @JavascriptInterface public String recall() { try { return db.recallJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public void recallAnswer(String id, boolean ok) { db.recallAnswer(Long.parseLong(id), ok); }
        @JavascriptInterface public boolean geminiKeySet() { return !prefs.getString("geminiKey", "").isEmpty(); }
        @JavascriptInterface public String setGeminiKey(String k) {
            String v = k == null ? "" : k.trim();
            if (!v.isEmpty() && (v.length() < 20 || v.length() > 200 || v.matches(".*\\s.*"))) return "That does not look like an API key.";
            prefs.edit().putString("geminiKey", v).apply();
            return "";
        }
        @JavascriptInterface public void testGemini() {
            gem("test", () -> "Gemini accepted the key (" + Gemini.MODEL + " answered: " + Gemini.ask(apiKey(), Gemini.pingRequest(), Refresher.AGENT).trim() + ").");
        }
        /** "" while the job runs, then "ok:..." or "err:..." once. */
        @JavascriptInterface public String geminiResult(String key) { String v = gemResults.remove(key); return v == null ? "" : v; }
        @JavascriptInterface public String pathJson(String topic) { return db.pathJson(topic); }
        @JavascriptInterface public void makeNotes(final String id) {
            gem("notes:" + id, () -> {
                long item = Long.parseLong(id);
                String[] in = db.notesInput(item);
                if (in == null) throw new Gemini.GeminiException("That item is gone.");
                String kind = FeedParser.kindOf(in[1], in[4]);
                if (kind.equals("audio")) throw new Gemini.GeminiException("Notes are not made for podcast episodes.");
                String topics = db.topicNames(), request, basis;
                if (kind.equals("video")) {
                    request = Gemini.notesRequest(topics, in[0], in[1], "");
                    basis = "the video";
                } else {
                    String text = Sanitizer.text(in[2]);
                    basis = "the saved article";
                    if (text.length() < 400 && !in[1].isEmpty()) {
                        try { text = Sanitizer.text(Sanitizer.clean(Refresher.fetchPage(in[1]), 400000)); basis = "the page text"; } catch (Exception e) { text = ""; }
                    }
                    if (text.length() < 400) { text = Sanitizer.text(in[3]); basis = "the feed excerpt only"; }
                    if (text.length() < 200) throw new Gemini.GeminiException("There is not enough text to make notes from. Try Read full article first.");
                    request = Gemini.notesRequest(topics, in[0], "", text);
                }
                Gemini.Notes n = Gemini.parseNotes(Gemini.ask(apiKey(), request, Refresher.AGENT));
                db.saveAi(item, "*Based on " + basis + ".*\n\n" + n.md, n.questions);
                sync(item);
                return "Notes saved.";
            });
        }
        @JavascriptInterface public void makePath(final String topic) {
            gem("path:" + topic, () -> {
                String material = db.pathMaterial(topic);
                if (material.isEmpty()) throw new Gemini.GeminiException("Nothing is kept under this topic yet.");
                Gemini.Path p = Gemini.parsePath(Gemini.ask(apiKey(), Gemini.pathRequest(topic, db.topicNames(), material), Refresher.AGENT));
                db.savePath(topic, p.md, p.searches);
                String e = Exporter.writeText(MainActivity.this, MarkdownExport.pathFileName(topic), MarkdownExport.pathNote(topic, p.md, System.currentTimeMillis(), java.util.TimeZone.getDefault()));
                return e.isEmpty() ? "Learning path saved." : "Learning path saved. " + e;
            });
        }
        @JavascriptInterface public void openUrl(String url) {
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        }
    }

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
