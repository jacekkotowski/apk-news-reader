package pl.stashreader;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches enabled sources on a small thread pool. A failure is stored on its source and never stops the others. */
final class Refresher {
    private Refresher() {}
    static final String VERSION = "1.8.2";
    static final String AGENT = "StashReader/" + VERSION;
    private static final int CONNECT_MS = 15000, READ_MS = 20000, MAX_BYTES = 16 * 1024 * 1024, PAGE_BYTES = 4 * 1024 * 1024, POOL = 4;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    /** The reader's optional Semantic Scholar key; sent only to Semantic Scholar, as the header x-api-key. */
    private static volatile String s2Key = "";

    static void loadKeys(Context c) { s2Key = c.getSharedPreferences("stash", Context.MODE_PRIVATE).getString("s2Key", ""); }

    static boolean busy() { return BUSY.get(); }

    static void all(Context c) {
        if (!BUSY.compareAndSet(false, true)) return;
        loadKeys(c);
        try {
            final Db db = Db.get(c);
            ExecutorService pool = Executors.newFixedThreadPool(POOL);
            for (final long id : db.sourceIds(true)) {
                pool.execute(new Runnable() { @Override public void run() { one(db, id, false); } });
            }
            pool.shutdown();
            try { pool.awaitTermination(5, TimeUnit.MINUTES); } catch (InterruptedException e) { pool.shutdownNow(); }
        } finally { BUSY.set(false); }
    }

    /** Refreshes one source right away (used after adding it), ignoring a back-off. */
    static void single(Context c, long id) {
        loadKeys(c);
        Db db = Db.get(c);
        one(db, id, true);
    }

    private static void one(Db db, long id, boolean force) {
        try {
            Db.Src src = db.source(id);
            if (src == null) return;
            if (src.kind.equals("shared")) return; // links shared from other apps: nothing to fetch
            boolean query = !src.kind.equals("rss");
            long now = System.currentTimeMillis();
            if (!force && src.retryAfter > now) return; // told to wait (HTTP 429), or a failed search that is not retried yet
            // the stored items are the cache: a search source is not asked again within three hours
            if (!force && query && src.lastOk > 0 && now - src.lastOk < QuerySource.minInterval(src.kind)) return;
            long failRetry = query ? now + QuerySource.MIN_INTERVAL_MS : 0;
            try {
                FeedParser.Feed f;
                if (QuerySource.watches(src.kind)) {
                    f = Watcher.fetch(db, src, now);
                } else if (src.kind.equals(QuerySource.RECS)) {
                    f = Recommender.fetch(db, now);
                } else if (query) {
                    byte[] body = fetch(QuerySource.requestUrl(src.kind, src.query, now), MAX_BYTES, QuerySource.accept(src.kind), new String[1]);
                    f = QuerySource.parse(src.kind, src.query, body, now);
                } else {
                    f = FeedParser.parse(fetch(src.url), src.url);
                }
                db.store(id, f, now);
            } catch (FeedParser.FeedException e) { db.sourceFailed(id, e.getMessage(), failRetry); }
            catch (RateLimited e) { db.sourceFailed(id, "The server asked us to slow down (HTTP 429); trying again later", now + e.waitMs); }
            catch (UnknownHostException e) { db.sourceFailed(id, "No network, or the server name was not found", 0); }
            catch (SocketTimeoutException e) { db.sourceFailed(id, "The server took too long to answer", failRetry); }
            catch (javax.net.ssl.SSLException e) { db.sourceFailed(id, "The server's secure connection could not be verified", failRetry); }
            catch (IOException e) { db.sourceFailed(id, String.valueOf(e.getMessage()), failRetry); }
        } catch (Throwable t) {
            try { db.sourceFailed(id, "Unexpected problem: " + t, 0); } catch (Throwable ignored) { }
        }
    }

    static final class RateLimited extends IOException { final long waitMs; RateLimited(long w) { super("HTTP 429"); waitMs = w; } }

    static byte[] fetch(String address) throws IOException { return fetch(address, MAX_BYTES, "application/rss+xml, application/atom+xml, application/xml, text/xml, */*;q=0.5"); }

    /** A web page as text. The character set comes from the HTTP header, then from a meta tag, then UTF-8. */
    static String fetchPage(String address) throws IOException {
        String[] type = new String[1];
        byte[] b = fetch(address, PAGE_BYTES, "text/html, application/xhtml+xml;q=0.9, */*;q=0.5", type);
        return PageText.decode(b, type[0]);
    }

    static byte[] fetch(String address, int max, String accept) throws IOException { return fetch(address, max, accept, new String[1]); }

    static byte[] fetch(String address, int max, String accept, String[] contentType) throws IOException { return request(address, null, max, accept, contentType); }

    /** One HTTP request. With a body it is a POST of JSON; without, a GET. HTTP 429 becomes RateLimited. */
    static byte[] request(String address, byte[] body, int max, String accept, String[] contentType) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(address).openConnection();
        try {
            h.setConnectTimeout(CONNECT_MS); h.setReadTimeout(READ_MS);
            h.setRequestProperty("User-Agent", AGENT + " (personal reader; Android)");
            h.setRequestProperty("Accept", accept);
            if (!s2Key.isEmpty() && QuerySource.isS2Host(address)) h.setRequestProperty("x-api-key", s2Key);
            if (body != null) {
                h.setRequestMethod("POST");
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream o = h.getOutputStream()) { o.write(body); }
            }
            int code = h.getResponseCode();
            if (code == 429) {
                long wait = 3600000L;
                try { String ra = h.getHeaderField("Retry-After"); if (ra != null) wait = Math.max(60000L, Math.min(86400000L, Long.parseLong(ra.trim()) * 1000L)); } catch (RuntimeException ignored) { }
                throw new RateLimited(wait);
            }
            if (code != 200) throw new IOException("The server answered HTTP " + code);
            contentType[0] = h.getContentType();
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0; ) {
                    out.write(b, 0, n);
                    if (out.size() > max) throw new IOException("The page is larger than " + (max / 1048576) + " MB");
                }
                return out.toByteArray();
            }
        } finally { h.disconnect(); }
    }
}
