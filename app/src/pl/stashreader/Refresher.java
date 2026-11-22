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
    static final String VERSION = "0.4";
    static final String AGENT = "StashReader/" + VERSION;
    private static final int CONNECT_MS = 15000, READ_MS = 20000, MAX_BYTES = 16 * 1024 * 1024, PAGE_BYTES = 4 * 1024 * 1024, POOL = 4;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    static boolean busy() { return BUSY.get(); }

    static void all(Context c) {
        if (!BUSY.compareAndSet(false, true)) return;
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
        Db db = Db.get(c);
        one(db, id, true);
    }

    private static void one(Db db, long id, boolean force) {
        try {
            String url = db.sourceUrl(id);
            if (url == null) return;
            long now = System.currentTimeMillis();
            if (!force && db.retryAfter(id) > now) return; // told to back off (HTTP 429)
            try {
                db.store(id, FeedParser.parse(fetch(url), url), now);
            } catch (FeedParser.FeedException e) { db.sourceFailed(id, e.getMessage(), 0); }
            catch (RateLimited e) { db.sourceFailed(id, "The server asked us to slow down (HTTP 429); trying again later", now + e.waitMs); }
            catch (UnknownHostException e) { db.sourceFailed(id, "No network, or the server name was not found", 0); }
            catch (SocketTimeoutException e) { db.sourceFailed(id, "The server took too long to answer", 0); }
            catch (javax.net.ssl.SSLException e) { db.sourceFailed(id, "The server's secure connection could not be verified", 0); }
            catch (IOException e) { db.sourceFailed(id, String.valueOf(e.getMessage()), 0); }
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

    static byte[] fetch(String address, int max, String accept, String[] contentType) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(address).openConnection();
        try {
            h.setConnectTimeout(CONNECT_MS); h.setReadTimeout(READ_MS);
            h.setRequestProperty("User-Agent", AGENT + " (personal reader; Android)");
            h.setRequestProperty("Accept", accept);
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
