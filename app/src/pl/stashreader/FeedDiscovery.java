package pl.stashreader;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the feed behind a page address. Pure logic, no network and no Android imports. */
final class FeedDiscovery {
    private FeedDiscovery() {}

    static final int SCAN_CHARS = 2000000;
    static final String[] COMMON_PATHS = {"/feed", "/rss", "/index.xml", "/atom.xml", "/feed.xml"};

    /** "example.com/blog" becomes "https://example.com/blog". Returns "" when nothing usable is left. */
    static String normalizeInput(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty() || s.indexOf(' ') >= 0) return "";
        if (s.regionMatches(true, 0, "http://", 0, 7)) s = "https://" + s.substring(7);
        else if (!s.regionMatches(true, 0, "https://", 0, 8)) {
            if (s.contains("://")) return "";
            s = "https://" + s;
        }
        try {
            URI u = new URI(s);
            if (u.getHost() == null || u.getHost().indexOf('.') < 0) return "";
        } catch (Exception e) { return ""; }
        return s;
    }

    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    /** Feed addresses announced by {@code <link rel="alternate" type="application/rss+xml" href="...">}, in page order. */
    static List<String> alternates(String html, String pageUrl) {
        List<String> out = new ArrayList<>();
        if (html == null) return out;
        // YouTube puts its feed link after about 280,000 characters of inline script, so look far into the page
        String head = html.length() > SCAN_CHARS ? html.substring(0, SCAN_CHARS) : html;
        Matcher m = LINK.matcher(head);
        while (m.find()) {
            String tag = m.group();
            String rel = Sanitizer.attr(tag, "rel"), type = Sanitizer.attr(tag, "type"), href = Sanitizer.attr(tag, "href");
            if (rel == null || href == null || type == null) continue;
            if (!(" " + rel.toLowerCase(Locale.ROOT) + " ").contains(" alternate ")) continue;
            String t = type.toLowerCase(Locale.ROOT);
            if (!(t.contains("rss") || t.contains("atom") || t.equals("text/xml") || t.equals("application/xml"))) continue;
            String abs = resolve(pageUrl, Sanitizer.decode(href.trim()));
            if (!abs.isEmpty() && !out.contains(abs)) out.add(abs);
        }
        return out;
    }

    /** Guesses to try when the page announces no feed: the page's own folder first, then the site root. */
    static List<String> commonGuesses(String pageUrl) {
        List<String> out = new ArrayList<>();
        try {
            URI u = new URI(pageUrl);
            String origin = "https://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
            String path = u.getPath() == null ? "" : u.getPath();
            if (path.length() > 1) {
                String dir = path.endsWith("/") ? path : path.substring(0, path.lastIndexOf('/') + 1);
                if (dir.length() > 1) for (String p : COMMON_PATHS) out.add(origin + dir.substring(0, dir.length() - 1) + p);
            }
            for (String p : COMMON_PATHS) { String g = origin + p; if (!out.contains(g)) out.add(g); }
        } catch (Exception ignored) { }
        return out;
    }

    static String resolve(String base, String href) {
        try {
            String abs = new URI(base).resolve(new URI(href.replace(" ", "%20"))).toString();
            if (abs.regionMatches(true, 0, "http://", 0, 7)) abs = "https://" + abs.substring(7);
            return abs.regionMatches(true, 0, "https://", 0, 8) ? abs : "";
        } catch (Exception e) { return ""; }
    }
}
