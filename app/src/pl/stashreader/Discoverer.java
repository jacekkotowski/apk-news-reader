package pl.stashreader;

import android.content.Context;
import java.io.IOException;
import java.util.List;

/** Adds a source from any address: the feed itself, a page that announces one, or a site with a feed at a common path. */
final class Discoverer {
    private Discoverer() {}
    private static final int MAX_REQUESTS = 9;

    /** Returns "" when a source was added, otherwise a message for the user. */
    static String add(Context c, String input) {
        String url = FeedDiscovery.normalizeInput(input);
        if (url.isEmpty()) return "That is not a web address. Try something like example.com/blog";
        Db db = Db.get(c);
        int requests = 0;
        try {
            byte[] first = Refresher.fetch(url);
            requests++;
            try {
                FeedParser.parse(first, url);
                return save(c, db, url);
            } catch (FeedParser.FeedException notFeed) {
                // not a feed: treat it as a page and look for the feed it announces
            }
            String html = PageText.decode(first, null);
            for (String cand : FeedDiscovery.alternates(html, url)) {
                if (requests++ >= MAX_REQUESTS) break;
                if (isFeed(cand)) return save(c, db, cand);
            }
            List<String> guesses = FeedDiscovery.commonGuesses(url);
            for (String cand : guesses) {
                if (requests++ >= MAX_REQUESTS) break;
                if (isFeed(cand)) return save(c, db, cand);
            }
            return "No feed found on that page. Look for an RSS link on the site and add that address.";
        } catch (IOException e) {
            return "Could not open that address: " + e.getMessage();
        }
    }

    private static boolean isFeed(String url) {
        try { FeedParser.parse(Refresher.fetch(url), url); return true; }
        catch (Exception e) { return false; }
    }

    private static String save(Context c, Db db, String feedUrl) {
        final long id = db.addSource(feedUrl, "", "");
        if (id == -1) return "That source is already in the list";
        Refresher.single(c, id);
        return "";
    }
}
