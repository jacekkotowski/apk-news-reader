package pl.stashreader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sources and cached items. Items: state 0 new, 1 kept, 2 done. */
final class Db extends SQLiteOpenHelper {
    static final int NEW = 0, KEPT = 1, DONE = 2;
    static final int MAX_ITEMS = 2000, FEED_LIMIT = 300;
    private static final String WHEN = "CASE WHEN i.pub>0 THEN i.pub ELSE i.fetched END";
    private static Db instance;
    private final Context ctx;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }
    private Db(Context c) { super(c, "stash.db", null, 2); ctx = c; }

    @Override public void onConfigure(SQLiteDatabase d) { d.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE sources(id INTEGER PRIMARY KEY, url TEXT UNIQUE NOT NULL, name TEXT NOT NULL DEFAULT '', topic TEXT NOT NULL DEFAULT '', custom INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, last_ok INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '', retry_after INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY, source_id INTEGER NOT NULL REFERENCES sources(id) ON DELETE CASCADE, key TEXT UNIQUE NOT NULL, title TEXT NOT NULL, excerpt TEXT NOT NULL, html TEXT NOT NULL, full INTEGER NOT NULL DEFAULT 0, link TEXT NOT NULL, pub INTEGER NOT NULL, fetched INTEGER NOT NULL, mins INTEGER NOT NULL DEFAULT 1, state INTEGER NOT NULL DEFAULT 0, article TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE INDEX items_state ON items(state)");
        try {
            JSONArray a = new JSONObject(readAsset("starter.json")).getJSONArray("feeds");
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                ContentValues v = new ContentValues();
                v.put("url", o.getString("url")); v.put("name", o.getString("name")); v.put("topic", o.optString("topic", ""));
                d.insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
        } catch (Exception e) { /* an empty list is better than a crash; the Sources screen can add feeds */ }
    }
    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) d.execSQL("ALTER TABLE items ADD COLUMN article TEXT NOT NULL DEFAULT ''");
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
                v.put("full", i.fromContent && Sanitizer.words(plain) >= 250 ? 1 : 0);
                v.put("link", i.link); v.put("pub", i.published); v.put("fetched", now);
                v.put("mins", Math.max(1, Math.round(Sanitizer.words(plain) / 220f)));
                if (d.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) added++;
            }
            // never drop kept items; drop the oldest of the rest beyond the cap
            d.execSQL("DELETE FROM items WHERE state<>" + KEPT + " AND id NOT IN (SELECT id FROM items i WHERE state<>" + KEPT + " ORDER BY " + WHEN + " DESC LIMIT " + MAX_ITEMS + ")");
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
        if (state == DONE) { v.put("html", ""); v.put("article", ""); }
        getWritableDatabase().update("items", v, "id=?", new String[]{"" + id});
    }

    private static JSONObject card(Cursor c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", "" + c.getLong(0)); o.put("title", c.getString(1)); o.put("excerpt", c.getString(2)); o.put("pub", c.getLong(3));
        o.put("mins", c.getInt(4)); o.put("source", c.getString(5)); o.put("topic", c.getString(6)); o.put("state", c.getInt(7));
        return o;
    }
    private static final String CARD = "SELECT i.id, i.title, i.excerpt, " + WHEN + ", i.mins, CASE WHEN s.name='' THEN s.url ELSE s.name END, s.topic, i.state FROM items i JOIN sources s ON s.id=i.source_id";

    String feedJson() throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(CARD + " WHERE i.state=" + NEW + " AND s.enabled=1 ORDER BY " + WHEN + " DESC, i.id DESC LIMIT " + FEED_LIMIT, null)) {
            while (c.moveToNext()) a.put(card(c));
        }
        return a.toString();
    }
    String keptJson() throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(CARD + " WHERE i.state=" + KEPT + " ORDER BY i.id DESC", null)) {
            while (c.moveToNext()) a.put(card(c));
        }
        return a.toString();
    }
    String itemJson(long id) throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT html, full, link, excerpt, article FROM items WHERE id=?", new String[]{"" + id})) {
            JSONObject o = new JSONObject();
            if (c.moveToFirst()) { o.put("html", c.getString(0)); o.put("full", c.getInt(1) != 0); o.put("link", c.getString(2)); o.put("excerpt", c.getString(3)); o.put("article", c.getString(4)); }
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
        o.put("failed", count("SELECT COUNT(*) FROM sources WHERE enabled=1 AND last_error<>''"));
        return o;
    }

    String sourcesJson() throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT s.id, s.url, s.name, s.topic, s.custom, s.enabled, s.last_ok, s.last_error, (SELECT COUNT(*) FROM items x WHERE x.source_id=s.id) FROM sources s ORDER BY s.id", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("url", c.getString(1)); o.put("name", c.getString(2)); o.put("topic", c.getString(3));
                o.put("custom", c.getInt(4) != 0); o.put("enabled", c.getInt(5) != 0); o.put("lastOk", c.getLong(6)); o.put("error", c.getString(7)); o.put("count", c.getInt(8));
                a.put(o);
            }
        }
        return a.toString();
    }
}
