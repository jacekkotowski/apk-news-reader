package pl.stashreader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sources, topics and cached items. Items: state 0 new, 1 kept, 2 done, 3 "less like this". */
final class Db extends SQLiteOpenHelper {
    static final int NEW = 0, KEPT = 1, DONE = 2, LESS = 3;
    static final int MAX_ITEMS = 2000, FEED_LIMIT = 300, RANK_POOL = 1500;
    /** Starter searches: {kind, query, topic}. They bring items from places nobody added by hand. */
    private static final String[][] DEFAULT_QUERIES = {
        {QuerySource.ARXIV, "causal inference", "causal inference"}, {QuerySource.HN, "claude code", "Claude Code"},
        {QuerySource.HN, "duckdb", "duckdb"}, {QuerySource.HF, "", "AI for data science"},
        {QuerySource.RECS, "", "AI for data science"},
        {QuerySource.REDDIT, "r/datascience", "data science"}, {QuerySource.GITHUB, "tidymodels", "tidymodels"},
        {QuerySource.S2, "causal machine learning", "causal inference"}};
    static final String CURATOR_URL = "https://raw.githubusercontent.com/jacekkotowski/apk-news-reader/curator-data/curator.xml";
    private static final String[][] DEFAULT_TOPICS = {{"tidymodels", "3"}, {"Power BI", "3"}, {"DAX", "2"}, {"Claude Code", "3"}, {"machine learning", "2"}, {"LLM", "2"}, {"data science", "2"}, {"causal inference", "2"}, {"duckdb", "2"}};
    private static final String WHEN = "CASE WHEN i.pub>0 THEN i.pub ELSE i.fetched END";
    private static Db instance;
    private final Context ctx;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }
    private Db(Context c) { super(c, "stash.db", null, 10); ctx = c; }

    @Override public void onConfigure(SQLiteDatabase d) { d.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE sources(id INTEGER PRIMARY KEY, url TEXT UNIQUE NOT NULL, name TEXT NOT NULL DEFAULT '', topic TEXT NOT NULL DEFAULT '', custom INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, last_ok INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '', retry_after INTEGER NOT NULL DEFAULT 0, kind TEXT NOT NULL DEFAULT 'rss', query TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY, source_id INTEGER NOT NULL REFERENCES sources(id) ON DELETE CASCADE, key TEXT UNIQUE NOT NULL, title TEXT NOT NULL, excerpt TEXT NOT NULL, html TEXT NOT NULL, full INTEGER NOT NULL DEFAULT 0, link TEXT NOT NULL, pub INTEGER NOT NULL, fetched INTEGER NOT NULL, mins INTEGER NOT NULL DEFAULT 1, state INTEGER NOT NULL DEFAULT 0, article TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', kept_at INTEGER NOT NULL DEFAULT 0, because TEXT NOT NULL DEFAULT '', audio TEXT NOT NULL DEFAULT '', ai TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE INDEX items_state ON items(state)");
        createTopics(d);
        createHighlights(d);
        createCards(d);
        createPaths(d);
        try {
            JSONArray a = new JSONObject(readAsset("starter.json")).getJSONArray("feeds");
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                ContentValues v = new ContentValues();
                v.put("url", o.getString("url")); v.put("name", o.getString("name")); v.put("topic", o.optString("topic", ""));
                d.insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
        } catch (Exception e) { /* an empty list is better than a crash; the Sources screen can add feeds */ }
        seedQueries(d);
    }

    private void seedQueries(SQLiteDatabase d) {
        for (String[] q : DEFAULT_QUERIES) {
            ContentValues v = new ContentValues();
            v.put("url", QuerySource.key(q[0], q[1])); v.put("kind", q[0]); v.put("query", q[1]); v.put("topic", q[2]);
            d.insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) d.execSQL("ALTER TABLE items ADD COLUMN article TEXT NOT NULL DEFAULT ''");
        if (o < 3) createTopics(d);
        if (o < 4) {
            d.execSQL("ALTER TABLE items ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            d.execSQL("ALTER TABLE items ADD COLUMN kept_at INTEGER NOT NULL DEFAULT 0");
            d.execSQL("UPDATE items SET kept_at=fetched WHERE state=" + KEPT);
            createHighlights(d);
        }
        if (o < 5) {
            d.execSQL("ALTER TABLE sources ADD COLUMN kind TEXT NOT NULL DEFAULT 'rss'");
            d.execSQL("ALTER TABLE sources ADD COLUMN query TEXT NOT NULL DEFAULT ''");
            seedQueries(d);
        }
        if (o < 6) {
            d.execSQL("ALTER TABLE items ADD COLUMN because TEXT NOT NULL DEFAULT ''");
            ContentValues v = new ContentValues();
            v.put("url", QuerySource.key(QuerySource.RECS, "")); v.put("kind", QuerySource.RECS); v.put("query", ""); v.put("topic", "AI for data science");
            d.insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
        if (o < 7) { // the daily curator feed, published by the curator workflow
            ContentValues v = new ContentValues();
            v.put("url", CURATOR_URL); v.put("name", "Curator (daily, from the cloud)");
            d.insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
        if (o < 8) { // podcast audio, and the new starter channels and podcasts
            d.execSQL("ALTER TABLE items ADD COLUMN audio TEXT NOT NULL DEFAULT ''");
            try {
                JSONArray a = new JSONObject(readAsset("starter.json")).getJSONArray("feeds");
                for (int k = 0; k < a.length(); k++) {
                    JSONObject x = a.getJSONObject(k);
                    ContentValues v = new ContentValues();
                    v.put("url", x.getString("url")); v.put("name", x.getString("name")); v.put("topic", x.optString("topic", ""));
                    d.insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                }
            } catch (Exception e) { /* the Sources screen can still add them */ }
        }
        if (o < 9) { // recall cards, one for every highlight you already have
            createCards(d);
            d.execSQL("INSERT INTO cards(item_id, hl_id, due) SELECT item_id, id, created + " + Recall.DAY + " FROM highlights");
        }
        if (o < 10) { // AI notes on items, and learning paths per topic
            d.execSQL("ALTER TABLE items ADD COLUMN ai TEXT NOT NULL DEFAULT ''");
            createPaths(d);
        }
    }

    private void createPaths(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE paths(topic TEXT PRIMARY KEY, md TEXT NOT NULL, searches TEXT NOT NULL DEFAULT '[]', created INTEGER NOT NULL)");
    }

    private void createCards(SQLiteDatabase d) {
        // hl_id set: the card asks about a highlight (text and note are read from it); otherwise front and back hold a question and answer
        d.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY, item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE, hl_id INTEGER REFERENCES highlights(id) ON DELETE CASCADE, front TEXT NOT NULL DEFAULT '', back TEXT NOT NULL DEFAULT '', due INTEGER NOT NULL, step INTEGER NOT NULL DEFAULT 0, last INTEGER NOT NULL DEFAULT 0)");
    }

    private void createHighlights(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE highlights(id INTEGER PRIMARY KEY, item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE, text TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)");
    }

    private void createTopics(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE topics(id INTEGER PRIMARY KEY, phrase TEXT UNIQUE NOT NULL, weight INTEGER NOT NULL DEFAULT 3)");
        for (String[] t : DEFAULT_TOPICS) {
            ContentValues v = new ContentValues(); v.put("phrase", t[0]); v.put("weight", Integer.parseInt(t[1]));
            d.insertWithOnConflict("topics", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }

    long addSource(String url, String name, String topic) {
        ContentValues v = new ContentValues();
        v.put("url", url); v.put("name", name); v.put("topic", topic); v.put("custom", 1);
        return getWritableDatabase().insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    /** A search on an open API. The address column holds a stable key; the real request address is built when fetching. */
    long addQuerySource(String kind, String query, String topic) {
        ContentValues v = new ContentValues();
        v.put("url", QuerySource.key(kind, query)); v.put("kind", kind); v.put("query", QuerySource.clean(query)); v.put("topic", topic); v.put("custom", 1);
        return getWritableDatabase().insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    static final class Src { String url, kind, query; long lastOk, retryAfter; }
    Src source(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT url, kind, query, last_ok, retry_after FROM sources WHERE id=?", new String[]{"" + id})) {
            if (!c.moveToFirst()) return null;
            Src s = new Src();
            s.url = c.getString(0); s.kind = c.getString(1); s.query = c.getString(2); s.lastOk = c.getLong(3); s.retryAfter = c.getLong(4);
            return s;
        }
    }

    boolean hasKey(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM items WHERE key=?", new String[]{key})) { return c.moveToFirst(); }
    }

    /** Papers among the items in this state: {Semantic Scholar id, title}, newest first. */
    List<String[]> paperSeeds(int state, int limit) {
        List<String[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT link, title FROM items WHERE state=" + state + " AND link<>'' ORDER BY kept_at DESC, id DESC LIMIT 200", null)) {
            while (c.moveToNext() && out.size() < limit) {
                String id = QuerySource.paperId(c.getString(0));
                if (!id.isEmpty()) out.add(new String[]{id, c.getString(1)});
            }
        }
        return out;
    }

    void setEnabled(long id, boolean on) {
        ContentValues v = new ContentValues(); v.put("enabled", on ? 1 : 0);
        getWritableDatabase().update("sources", v, "id=?", new String[]{"" + id});
    }
    void removeSource(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.delete("items", "source_id=? AND state<>" + KEPT, new String[]{"" + id});
        // kept items stay in the Stash; the source row stays hidden-less, so only custom sources without kept items are removed
        d.delete("sources", "id=? AND NOT EXISTS (SELECT 1 FROM items WHERE source_id=sources.id)", new String[]{"" + id});
        ContentValues v = new ContentValues(); v.put("enabled", 0);
        d.update("sources", v, "id=?", new String[]{"" + id});
    }

    long[] sourceIds(boolean enabledOnly) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM sources" + (enabledOnly ? " WHERE enabled=1" : "") + " ORDER BY id", null)) {
            long[] out = new long[c.getCount()];
            for (int i = 0; c.moveToNext(); i++) out[i] = c.getLong(0);
            return out;
        }
    }
    String sourceUrl(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT url FROM sources WHERE id=?", new String[]{"" + id})) { return c.moveToFirst() ? c.getString(0) : null; }
    }
    long retryAfter(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT retry_after FROM sources WHERE id=?", new String[]{"" + id})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    void sourceFailed(long id, String message, long retryAfter) {
        ContentValues v = new ContentValues(); v.put("last_error", message); v.put("retry_after", retryAfter);
        getWritableDatabase().update("sources", v, "id=?", new String[]{"" + id});
    }

    /** Stores new items. Items already known (same link) are left alone, so Keep and Done survive a refresh. */
    int store(long sourceId, FeedParser.Feed f, long now) {
        SQLiteDatabase d = getWritableDatabase();
        int added = 0;
        d.beginTransaction();
        try {
            ContentValues u = new ContentValues();
            u.put("last_ok", now); u.put("last_error", ""); u.put("retry_after", 0);
            d.update("sources", u, "id=?", new String[]{"" + sourceId});
            if (!f.title.isEmpty()) {
                ContentValues nm = new ContentValues(); nm.put("name", f.title);
                d.update("sources", nm, "id=? AND name=''", new String[]{"" + sourceId});
            }
            for (FeedParser.Item i : f.items) {
                String html = Sanitizer.clean(i.body);
                String plain = Sanitizer.text(html);
                ContentValues v = new ContentValues();
                v.put("source_id", sourceId); v.put("key", i.uid); v.put("title", i.title);
                v.put("excerpt", Sanitizer.shorten(plain, 300)); v.put("html", html);
                v.put("because", i.because); v.put("audio", i.audio == null ? "" : i.audio);
                v.put("full", i.fromContent && Sanitizer.words(plain) >= 250 ? 1 : 0);
                v.put("link", i.link); v.put("pub", i.published); v.put("fetched", now);
                v.put("mins", Math.max(1, Math.round(Sanitizer.words(plain) / 220f)));
                if (d.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) added++;
                else if (i.audio != null && !i.audio.isEmpty()) d.execSQL("UPDATE items SET audio=? WHERE key=? AND audio=''", new Object[]{i.audio, i.uid});
            }
            // never drop kept items; drop the oldest of the rest beyond the cap
            d.execSQL("DELETE FROM items WHERE state<>" + KEPT + " AND id NOT IN (SELECT id FROM items i WHERE state<>" + KEPT + " ORDER BY " + WHEN + " DESC LIMIT " + MAX_ITEMS + ")");
            // an unkept card disappears 30 days after it arrived; it is marked done, so it is not offered again
            d.execSQL("UPDATE items SET state=" + DONE + ", html='', article='' WHERE state=" + NEW + " AND fetched < " + FeedFilter.cutoff(now, FeedFilter.EXPIRY_DAYS));
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
        return added;
    }

    /** Stores the reader-mode text (sanitised here) and corrects the reading time. */
    void saveArticle(long id, String html) {
        String clean = Sanitizer.clean(html, 400000);
        int words = Sanitizer.words(Sanitizer.text(clean));
        ContentValues v = new ContentValues();
        v.put("article", clean); v.put("mins", Math.max(1, Math.round(words / 220f)));
        getWritableDatabase().update("items", v, "id=?", new String[]{"" + id});
    }
    String itemLink(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT link FROM items WHERE id=?", new String[]{"" + id})) { return c.moveToFirst() ? c.getString(0) : ""; }
    }

    void setState(long id, int state) {
        ContentValues v = new ContentValues(); v.put("state", state);
        // a finished item no longer needs its text; the row stays so the item is not offered again
        if (state == KEPT) v.put("kept_at", System.currentTimeMillis());
        if (state == DONE || state == LESS) { v.put("html", ""); v.put("article", ""); }
        SQLiteDatabase d = getWritableDatabase();
        if (state == KEPT) { // keep the first kept date when an item is kept again
            try (Cursor c = d.rawQuery("SELECT kept_at, state FROM items WHERE id=?", new String[]{"" + id})) {
                if (c.moveToFirst() && c.getLong(0) > 0 && c.getInt(1) == KEPT) v.remove("kept_at");
            }
        }
        d.update("items", v, "id=?", new String[]{"" + id});
    }

    private static JSONObject card(Cursor c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", "" + c.getLong(0)); o.put("title", c.getString(1)); o.put("excerpt", c.getString(2)); o.put("pub", c.getLong(3));
        o.put("mins", c.getInt(4)); o.put("source", c.getString(5)); o.put("topic", c.getString(6)); o.put("state", c.getInt(7));
        o.put("kind", FeedParser.kindOf(c.getString(9), c.getString(10)));
        return o;
    }
    private static final String CARD = "SELECT i.id, i.title, i.excerpt, " + WHEN + ", i.mins, CASE WHEN s.name='' THEN s.url ELSE s.name END, s.topic, i.state, i.because, i.link, i.audio, i.source_id FROM items i JOIN sources s ON s.id=i.source_id";

    /** The unread items, ordered by the explainable score. Each card carries a "why" sentence. */
    String feedJson() throws Exception { return feedJson(new FeedFilter.Filters()); }

    /** Same, but only the cards that pass the reader's filters; "newest" skips the score and orders by date. */
    String feedJson(FeedFilter.Filters f) throws Exception {
        List<Ranker.Topic> topics = topicList();
        String want = f.topic.toLowerCase(java.util.Locale.ROOT);
        List<Ranker.Doc> docs = new ArrayList<>();
        Map<Long, JSONObject> cards = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(CARD + " WHERE i.state=" + NEW + " AND s.enabled=1 ORDER BY " + WHEN + " DESC, i.id DESC LIMIT " + RANK_POOL, null)) {
            while (c.moveToNext()) {
                JSONObject o = card(c);
                boolean topicOk = false;
                if (!want.isEmpty()) {
                    topicOk = c.getString(6).toLowerCase(java.util.Locale.ROOT).equals(want);
                    if (!topicOk) for (String t : Ranker.matchedTopics(c.getString(1) + " " + c.getString(2), topics)) if (t.toLowerCase(java.util.Locale.ROOT).equals(want)) { topicOk = true; break; }
                }
                if (!FeedFilter.accept(f, o.getString("kind"), topicOk, c.getLong(11), c.getString(1), c.getString(2))) continue;
                long id = c.getLong(0);
                cards.put(id, o);
                docs.add(new Ranker.Doc(id, c.getString(1), c.getString(2), c.getLong(3), c.getString(8)));
            }
        }
        JSONArray a = new JSONArray();
        if (f.newest) {
            for (Ranker.Doc d : docs) { if (a.length() >= FEED_LIMIT) break; a.put(cards.get(d.id)); }
            return a.toString();
        }
        List<Ranker.Scored> ranked = Ranker.rank(docs, topics, texts(KEPT), texts(LESS), System.currentTimeMillis());
        for (Ranker.Scored r : ranked) {
            if (a.length() >= FEED_LIMIT) break;
            JSONObject o = cards.get(r.id);
            o.put("why", r.why);
            a.put(o);
        }
        return a.toString();
    }

    private List<String> texts(int state) {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT title || ' ' || excerpt FROM items WHERE state=" + state + " ORDER BY id DESC LIMIT 200", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    private List<Ranker.Topic> topicList() {
        List<Ranker.Topic> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT phrase, weight FROM topics", null)) {
            while (c.moveToNext()) out.add(new Ranker.Topic(c.getString(0), c.getInt(1)));
        }
        return out;
    }

    String topicsJson() throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, phrase, weight FROM topics ORDER BY weight DESC, phrase COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("phrase", c.getString(1)); o.put("weight", c.getInt(2));
                a.put(o);
            }
        }
        return a.toString();
    }
    boolean addTopic(String phrase) {
        String p = Sanitizer.collapse(phrase == null ? "" : phrase);
        if (p.isEmpty() || p.length() > 60) return false;
        ContentValues v = new ContentValues(); v.put("phrase", p); v.put("weight", 3);
        return getWritableDatabase().insertWithOnConflict("topics", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }
    void setTopicWeight(long id, int weight) {
        ContentValues v = new ContentValues(); v.put("weight", Math.max(0, Math.min(5, weight)));
        getWritableDatabase().update("topics", v, "id=?", new String[]{"" + id});
    }
    void removeTopic(long id) { getWritableDatabase().delete("topics", "id=?", new String[]{"" + id}); }

    String keptJson() throws Exception {
        JSONArray a = new JSONArray();
        List<Ranker.Topic> topics = topicList();
        try (Cursor c = getReadableDatabase().rawQuery(CARD + " WHERE i.state=" + KEPT + " ORDER BY i.kept_at DESC, i.id DESC", null)) {
            while (c.moveToNext()) {
                JSONObject o = card(c);
                JSONArray ts = new JSONArray();
                java.util.Set<String> seen = new java.util.HashSet<>();
                String own = c.getString(6);
                if (!own.isEmpty() && seen.add(own.toLowerCase(java.util.Locale.ROOT))) ts.put(own);
                for (String t : Ranker.matchedTopics(c.getString(1) + " " + c.getString(2), topics)) if (seen.add(t.toLowerCase(java.util.Locale.ROOT))) ts.put(t);
                o.put("topics", ts);
                o.put("hl", count("SELECT COUNT(*) FROM highlights WHERE item_id=" + c.getLong(0)));
                a.put(o);
            }
        }
        return a.toString();
    }

    String highlightsJson(long item) throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, text, note FROM highlights WHERE item_id=? ORDER BY id", new String[]{"" + item})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("text", c.getString(1)); o.put("note", c.getString(2));
                a.put(o);
            }
        }
        return a.toString();
    }
    /** Adds a highlight; the item is kept at the same time, because only kept items are exported. Returns false for empty text. */
    boolean addHighlight(long item, String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return false;
        if (t.length() > 3000) t = t.substring(0, 3000);
        SQLiteDatabase d = getWritableDatabase();
        try (Cursor c = d.rawQuery("SELECT 1 FROM highlights WHERE item_id=? AND text=?", new String[]{"" + item, t})) { if (c.moveToFirst()) return true; }
        ContentValues v = new ContentValues();
        v.put("item_id", item); v.put("text", t); v.put("created", System.currentTimeMillis());
        long hid = d.insert("highlights", null, v);
        if (hid > 0) {
            ContentValues cv = new ContentValues();
            cv.put("item_id", item); cv.put("hl_id", hid); cv.put("due", System.currentTimeMillis() + Recall.DAY);
            d.insert("cards", null, cv);
        }
        setState(item, KEPT);
        return true;
    }
    void setHighlightNote(long id, String note) {
        ContentValues v = new ContentValues(); v.put("note", note == null ? "" : note.trim());
        getWritableDatabase().update("highlights", v, "id=?", new String[]{"" + id});
    }
    long highlightItem(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT item_id FROM highlights WHERE id=?", new String[]{"" + id})) { return c.moveToFirst() ? c.getLong(0) : -1; }
    }
    void removeHighlight(long id) { getWritableDatabase().delete("highlights", "id=?", new String[]{"" + id}); }
    /** Saves a link shared from another app straight into the Stash. Returns the item id, or -1 when the link is not usable. */
    long addShared(String url, String title) {
        SQLiteDatabase d = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues sv = new ContentValues();
        sv.put("url", "stash:shared"); sv.put("name", "Shared with Stash Reader"); sv.put("kind", "shared"); sv.put("custom", 1);
        d.insertWithOnConflict("sources", null, sv, SQLiteDatabase.CONFLICT_IGNORE);
        long sid;
        try (Cursor c = d.rawQuery("SELECT id FROM sources WHERE url='stash:shared'", null)) { if (!c.moveToFirst()) return -1; sid = c.getLong(0); }
        FeedParser.Item i = new FeedParser.Item();
        i.title = title; i.link = url; i.body = ""; i.published = now;
        FeedParser.Feed f = new FeedParser.Feed();
        FeedParser.add(f, i, "stash:shared");
        if (f.items.isEmpty() || f.items.get(0).link.isEmpty()) return -1;
        i = f.items.get(0);
        ContentValues v = new ContentValues();
        v.put("source_id", sid); v.put("key", i.uid); v.put("title", i.title); v.put("excerpt", "Saved from a share."); v.put("html", "");
        v.put("link", i.link); v.put("pub", now); v.put("fetched", now); v.put("state", KEPT); v.put("kept_at", now);
        long id = d.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id == -1) { // the same link is already known, perhaps from a feed: keep that one
            try (Cursor c = d.rawQuery("SELECT id FROM items WHERE key=?", new String[]{i.uid})) { if (!c.moveToFirst()) return -1; id = c.getLong(0); }
            setState(id, KEPT);
        }
        return id;
    }

    private static long dayStart(long now) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** The recall cards to ask now: due, on a kept item, and no more than what is left of today's cap. */
    String recallJson() throws Exception {
        long now = System.currentTimeMillis();
        JSONArray a = new JSONArray();
        int answered = count("SELECT COUNT(*) FROM cards WHERE last>=" + dayStart(now));
        int due = count("SELECT COUNT(*) FROM cards c JOIN items i ON i.id=c.item_id WHERE i.state=" + KEPT + " AND c.due<=" + now);
        int show = Recall.toShow(answered, due);
        if (show == 0) return a.toString();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT c.id, i.title, CASE WHEN s.name='' THEN s.url ELSE s.name END, c.hl_id, c.front, c.back, h.text, h.note FROM cards c JOIN items i ON i.id=c.item_id AND i.state=" + KEPT + " JOIN sources s ON s.id=i.source_id LEFT JOIN highlights h ON h.id=c.hl_id WHERE c.due<=" + now + " ORDER BY c.due LIMIT " + show, null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("title", c.getString(1)); o.put("source", c.getString(2));
                if (c.isNull(3)) { o.put("front", c.getString(4)); o.put("back", c.getString(5)); }
                else {
                    String note = c.getString(7);
                    o.put("front", "What do you remember about this?");
                    o.put("back", c.getString(6) + (note == null || note.isEmpty() ? "" : "\n\nMy note: " + note));
                }
                a.put(o);
            }
        }
        return a.toString();
    }

    void recallAnswer(long id, boolean ok) {
        SQLiteDatabase d = getWritableDatabase();
        int step;
        try (Cursor c = d.rawQuery("SELECT step FROM cards WHERE id=?", new String[]{"" + id})) { if (!c.moveToFirst()) return; step = c.getInt(0); }
        long now = System.currentTimeMillis();
        Recall.Next n = Recall.next(step, ok, now);
        ContentValues v = new ContentValues();
        v.put("step", n.step); v.put("due", n.due); v.put("last", now);
        d.update("cards", v, "id=?", new String[]{"" + id});
    }

    void setNote(long item, String note) {
        ContentValues v = new ContentValues(); v.put("note", note == null ? "" : note.trim());
        getWritableDatabase().update("items", v, "id=?", new String[]{"" + item});
    }

    /** Everything the Markdown note needs, or null when the item is gone or not kept. */
    MarkdownExport.Note noteFor(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT i.title, CASE WHEN s.name='' THEN s.url ELSE s.name END, i.link, i.pub, i.kept_at, i.note, s.topic, i.excerpt, i.state, i.ai FROM items i JOIN sources s ON s.id=i.source_id WHERE i.id=?", new String[]{"" + id})) {
            if (!c.moveToFirst() || c.getInt(8) != KEPT) return null;
            MarkdownExport.Note n = new MarkdownExport.Note();
            n.title = c.getString(0); n.source = c.getString(1); n.url = c.getString(2); n.published = c.getLong(3); n.keptAt = c.getLong(4); n.note = c.getString(5); n.ai = c.getString(9);
            java.util.Set<String> seen = new java.util.HashSet<>();
            if (!c.getString(6).isEmpty() && seen.add(c.getString(6).toLowerCase(java.util.Locale.ROOT))) n.topics.add(c.getString(6));
            for (String t : Ranker.matchedTopics(n.title + " " + c.getString(7), topicList())) if (seen.add(t.toLowerCase(java.util.Locale.ROOT))) n.topics.add(t);
            try (Cursor h = getReadableDatabase().rawQuery("SELECT text, note FROM highlights WHERE item_id=? ORDER BY id", new String[]{"" + id})) {
                while (h.moveToNext()) n.highlights.add(new MarkdownExport.Highlight(h.getString(0), h.getString(1)));
            }
            return n;
        }
    }
    long[] keptIds() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM items WHERE state=" + KEPT + " ORDER BY id", null)) {
            long[] out = new long[c.getCount()];
            for (int i = 0; c.moveToNext(); i++) out[i] = c.getLong(0);
            return out;
        }
    }

    private static String oneLine(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }

    /** What the notes request needs for one item: title, link, saved article (HTML), feed text (HTML), audio address; null when gone. */
    String[] notesInput(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT title, link, article, html, audio FROM items WHERE id=?", new String[]{"" + id})) {
            if (!c.moveToFirst()) return null;
            return new String[]{c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)};
        }
    }

    /** The topics you follow, strongest first, as one line for a prompt. */
    String topicNames() {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Ranker.Topic t : topicList()) {
            if (t.weight <= 0 || n >= 12) continue;
            if (b.length() > 0) b.append(", ");
            b.append(t.phrase); n++;
        }
        return b.toString();
    }

    /** Saves the AI notes and turns the questions into recall cards (the earlier ones for this item are replaced). */
    void saveAi(long id, String md, List<String[]> questions) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("ai", md);
            d.update("items", v, "id=?", new String[]{"" + id});
            d.delete("cards", "item_id=? AND hl_id IS NULL", new String[]{"" + id});
            long due = System.currentTimeMillis() + Recall.DAY;
            for (String[] q : questions) {
                ContentValues cv = new ContentValues();
                cv.put("item_id", id); cv.put("front", q[0]); cv.put("back", q[1]); cv.put("due", due);
                d.insert("cards", null, cv);
            }
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    /** Titles, sources and highlighted passages of the kept items on one topic, for the learning path. Never your own notes. */
    String pathMaterial(String topic) {
        StringBuilder b = new StringBuilder();
        String want = topic.toLowerCase(java.util.Locale.ROOT);
        List<Ranker.Topic> topics = topicList();
        SQLiteDatabase d = getReadableDatabase();
        int n = 0;
        try (Cursor c = d.rawQuery("SELECT i.id, i.title, CASE WHEN s.name='' THEN s.url ELSE s.name END, i.excerpt, s.topic FROM items i JOIN sources s ON s.id=i.source_id WHERE i.state=" + KEPT + " ORDER BY i.kept_at DESC, i.id DESC LIMIT 300", null)) {
            while (c.moveToNext() && n < 40 && b.length() < 15000) {
                boolean hit = c.getString(4).toLowerCase(java.util.Locale.ROOT).equals(want);
                if (!hit) for (String t : Ranker.matchedTopics(c.getString(1) + " " + c.getString(3), topics)) if (t.toLowerCase(java.util.Locale.ROOT).equals(want)) { hit = true; break; }
                if (!hit) continue;
                n++;
                b.append("- ").append(oneLine(c.getString(1))).append(" (").append(oneLine(c.getString(2))).append(")\n");
                try (Cursor h = d.rawQuery("SELECT text FROM highlights WHERE item_id=? ORDER BY id LIMIT 3", new String[]{"" + c.getLong(0)})) {
                    while (h.moveToNext()) {
                        String t = oneLine(h.getString(0));
                        if (t.length() > 300) t = t.substring(0, 300);
                        b.append("  highlight: \"").append(t).append("\"\n");
                    }
                }
            }
        }
        return b.toString();
    }

    void savePath(String topic, String md, List<String[]> searches) {
        JSONArray a = new JSONArray();
        try { for (String[] s : searches) { JSONObject o = new JSONObject(); o.put("kind", s[0]); o.put("query", s[1]); a.put(o); } } catch (Exception ignored) { }
        ContentValues v = new ContentValues();
        v.put("topic", topic); v.put("md", md); v.put("searches", a.toString()); v.put("created", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("paths", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    String pathJson(String topic) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT md, searches, created FROM paths WHERE topic=?", new String[]{topic})) {
            JSONObject o = new JSONObject();
            if (c.moveToFirst()) { o.put("md", c.getString(0)); o.put("searches", new JSONArray(c.getString(1))); o.put("created", c.getLong(2)); }
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    String itemAudio(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT audio FROM items WHERE id=?", new String[]{"" + id})) { return c.moveToFirst() ? c.getString(0) : ""; }
    }

    String itemJson(long id) throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT html, full, link, excerpt, article, note, state, audio, ai FROM items WHERE id=?", new String[]{"" + id})) {
            JSONObject o = new JSONObject();
            if (c.moveToFirst()) { o.put("html", c.getString(0)); o.put("full", c.getInt(1) != 0); o.put("link", c.getString(2)); o.put("excerpt", c.getString(3)); o.put("article", c.getString(4)); o.put("note", c.getString(5)); o.put("state", c.getInt(6)); o.put("kind", FeedParser.kindOf(c.getString(2), c.getString(7))); o.put("ai", c.getString(8)); }
            return o.toString();
        }
    }

    private int count(String sql) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) { return c.moveToFirst() ? c.getInt(0) : 0; }
    }
    JSONObject counts() throws Exception {
        JSONObject o = new JSONObject();
        o.put("fresh", count("SELECT COUNT(*) FROM items i JOIN sources s ON s.id=i.source_id WHERE i.state=" + NEW + " AND s.enabled=1"));
        o.put("kept", count("SELECT COUNT(*) FROM items WHERE state=" + KEPT));
        o.put("sources", count("SELECT COUNT(*) FROM sources WHERE enabled=1"));
        o.put("failed", count("SELECT COUNT(*) FROM sources WHERE enabled=1 AND last_error<>'' AND last_error NOT LIKE 'Note:%'"));
        return o;
    }

    String sourcesJson() throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT s.id, s.url, s.name, s.topic, s.custom, s.enabled, s.last_ok, s.last_error, (SELECT COUNT(*) FROM items x WHERE x.source_id=s.id), s.kind, s.query FROM sources s ORDER BY s.id", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("url", c.getString(1)); o.put("name", c.getString(2)); o.put("topic", c.getString(3));
                o.put("custom", c.getInt(4) != 0); o.put("enabled", c.getInt(5) != 0); o.put("lastOk", c.getLong(6)); o.put("error", c.getString(7)); o.put("count", c.getInt(8)); o.put("kind", c.getString(9)); o.put("query", c.getString(10));
                a.put(o);
            }
        }
        return a.toString();
    }
}
