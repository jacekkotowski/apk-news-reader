package pl.stashreader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Page watch and sitemap watch: new links on one page you chose become items.
 * Polite: robots.txt is read first and honoured, one listing page per refresh (the refresh runs at most daily),
 * at most {@link #MAX_NEW} new pages read per refresh, one second between pages.
 */
final class Watcher {
    private Watcher() {}
    static final int MAX_NEW = 8;
    private static final long PAUSE_MS = 1000;

    static FeedParser.Feed fetch(Db db, Db.Src src, long now) throws IOException, FeedParser.FeedException {
        String url = FeedDiscovery.normalizeInput(src.query);
        if (url.isEmpty()) throw new FeedParser.FeedException("That is not a web address");
        String robots = robots(url);
        if (!Robots.allowed(robots, "StashReader", PageWatch.pathAndQuery(url))) throw new FeedParser.FeedException("robots.txt asks robots not to read this page, so it is not watched");

        List<PageWatch.Link> candidates;
        if (src.kind.equals(QuerySource.SITEMAP)) {
            candidates = PageWatch.sitemap(Refresher.fetch(url, 8 * 1024 * 1024, "application/xml, text/xml, */*;q=0.5"));
            if (candidates.isEmpty()) throw new FeedParser.FeedException("The sitemap lists no pages that look like articles");
        } else {
            candidates = PageWatch.links(Refresher.fetchPage(url), url);
            if (candidates.isEmpty()) throw new FeedParser.FeedException("No article links found. The page may need JavaScript to show them, which this app does not run");
        }

        FeedParser.Feed feed = new FeedParser.Feed();
        feed.title = "Watching " + PageWatch.host(url);
        int read = 0;
        for (PageWatch.Link c : candidates) {
            if (read >= MAX_NEW) break;
            FeedParser.Item probe = new FeedParser.Item();
            probe.link = c.url; probe.title = "x";
            if (db.hasKey(FeedParser.uid(probe, url))) continue; // already known: not new
            read++;
            FeedParser.Item i = new FeedParser.Item();
            i.link = c.url;
            i.title = c.title.isEmpty() ? PageWatch.titleFromUrl(c.url) : c.title;
            i.published = c.date;
            i.body = "";
            if (Robots.allowed(robots, "StashReader", PageWatch.pathAndQuery(c.url))) {
                try {
                    PageWatch.Meta m = PageWatch.meta(Refresher.fetchPage(c.url));
                    if (!m.title.isEmpty()) i.title = m.title;
                    if (!m.description.isEmpty()) i.body = "<p>" + m.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>";
                    if (m.published > 0) i.published = m.published;
                } catch (Refresher.RateLimited e) {
                    throw e;
                } catch (IOException e) { /* keep the link and its title; the page may be fetched again when it is opened */ }
                try { Thread.sleep(PAUSE_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (i.title.isEmpty()) i.title = PageWatch.titleFromUrl(c.url);
            if (i.published == 0) i.published = now;
            FeedParser.add(feed, i, url);
        }
        return feed;
    }

    /** The site's robots.txt, or "" when it cannot be read (then everything is allowed, as the standard says for a missing file). */
    private static String robots(String pageUrl) {
        try {
            java.net.URI u = new java.net.URI(pageUrl);
            return Refresher.fetchPage("https://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + "/robots.txt");
        } catch (Exception e) { return ""; }
    }
}
