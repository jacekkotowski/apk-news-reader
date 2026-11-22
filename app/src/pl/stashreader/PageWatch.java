package pl.stashreader;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Finds article links on a listing page, reads a page's title and description, and reads sitemaps. Pure logic, no network, no Android imports. */
final class PageWatch {
    private PageWatch() {}

    static final int MAX_LINKS = 30;

    static final class Link { String url, title = ""; long date; }
    static final class Meta { String title = "", description = ""; long published; }

    private static final Pattern ANCHOR = Pattern.compile("<a\\b([^>]*)>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TIME = Pattern.compile("<time\\b[^>]*datetime\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SKIP_SEGMENTS = new HashSet<>(Arrays.asList("tag", "tags", "category", "categories", "author", "authors", "page", "about",
        "contact", "privacy", "login", "signin", "signup", "search", "feed", "rss", "subscribe", "terms", "cookies", "archive", "archives", "topics"));
    private static final String[] SKIP_EXT = {".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".pdf", ".zip", ".css", ".js", ".xml", ".json", ".ico", ".mp3", ".mp4"};

    static String host(String url) {
        try { String h = new URI(url).getHost(); return h == null ? "" : h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""); }
        catch (Exception e) { return ""; }
    }

    static String pathAndQuery(String url) {
        try {
            URI u = new URI(url);
            String p = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            return u.getRawQuery() == null ? p : p + "?" + u.getRawQuery();
        } catch (Exception e) { return "/"; }
    }

    static String withoutFragment(String url) {
        int h = url.indexOf('#');
        return h < 0 ? url : url.substring(0, h);
    }

    /** Links on the same site that look like articles: a title of a few words, no navigation paths, no files. In page order, without duplicates. */
    static List<Link> links(String html, String pageUrl) {
        List<Link> out = new ArrayList<>();
        if (html == null) return out;
        String pageHost = host(pageUrl);
        String self = FeedParser.normalizeLink(pageUrl);
        Set<String> seen = new HashSet<>();
        Matcher m = ANCHOR.matcher(html.length() > 600000 ? html.substring(0, 600000) : html);
        while (m.find() && out.size() < MAX_LINKS) {
            String href = Sanitizer.attr(m.group(1), "href");
            if (href == null || href.trim().isEmpty()) continue;
            String abs = FeedDiscovery.resolve(pageUrl, Sanitizer.decode(href.trim()));
            if (abs.isEmpty()) continue;
            abs = withoutFragment(abs);
            if (!host(abs).equals(pageHost)) continue;
            String key = FeedParser.normalizeLink(abs);
            if (key.equals(self) || !seen.add(key)) continue;
            if (!looksLikeArticle(abs)) continue;
            String title = Sanitizer.text(m.group(2));
            if (title.length() < 20 || Sanitizer.words(title) < 3) continue;
            Link l = new Link();
            l.url = abs; l.title = Sanitizer.shorten(title, 160);
            out.add(l);
        }
        return out;
    }

    static boolean looksLikeArticle(String url) {
        String path = pathAndQuery(url);
        int q = path.indexOf('?');
        String p = (q < 0 ? path : path.substring(0, q)).toLowerCase(Locale.ROOT);
        if (p.length() <= 1) return false;
        for (String e : SKIP_EXT) if (p.endsWith(e)) return false;
        for (String seg : p.split("/")) if (SKIP_SEGMENTS.contains(seg)) return false;
        return true;
    }

    /** Title, description and date of one page, from Open Graph or ordinary tags. */
    static Meta meta(String html) {
        Meta out = new Meta();
        if (html == null) return out;
        String head = html.length() > 300000 ? html.substring(0, 300000) : html;
        String ogTitle = "", name = "", ogDesc = "", published = "";
        Matcher m = META.matcher(head);
        while (m.find()) {
            String tag = m.group();
            String key = lower(Sanitizer.attr(tag, "property")), nm = lower(Sanitizer.attr(tag, "name"));
            String content = Sanitizer.attr(tag, "content");
            if (content == null) continue;
            content = Sanitizer.text(content);
            if (key.equals("og:title") && ogTitle.isEmpty()) ogTitle = content;
            else if (key.equals("og:description") && ogDesc.isEmpty()) ogDesc = content;
            else if (nm.equals("description") && name.isEmpty()) name = content;
            else if ((key.equals("article:published_time") || nm.equals("date") || nm.equals("pubdate") || nm.equals("article:published_time")) && published.isEmpty()) published = content;
        }
        if (published.isEmpty()) { Matcher t = TIME.matcher(head); if (t.find()) published = t.group(1); }
        Matcher t = TITLE.matcher(head);
        String docTitle = t.find() ? Sanitizer.text(t.group(1)) : "";
        out.title = !ogTitle.isEmpty() ? ogTitle : docTitle;
        out.description = !ogDesc.isEmpty() ? ogDesc : name;
        out.published = FeedParser.date(published);
        return out;
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).trim(); }

    /** Entries of a sitemap ({@code <urlset>}). A sitemap index is refused with a message that says what to do. */
    static List<Link> sitemap(byte[] xml) throws FeedParser.FeedException {
        String probe = new String(xml, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        if (probe.contains("<!DOCTYPE") || probe.contains("<!ENTITY")) throw new FeedParser.FeedException("Refused: the sitemap contains a DOCTYPE");
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setExpandEntityReferences(false);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) { throw new FeedParser.FeedException("The sitemap is not valid XML"); }
        Element root = doc.getDocumentElement();
        String kind = root.getNodeName().toLowerCase(Locale.ROOT);
        if (kind.endsWith("sitemapindex")) throw new FeedParser.FeedException("This is a sitemap index. Add one of the sitemaps it lists, for example the one for posts");
        if (!kind.endsWith("urlset")) throw new FeedParser.FeedException("This is not a sitemap");
        List<Link> out = new ArrayList<>();
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE || !n.getNodeName().toLowerCase(Locale.ROOT).endsWith("url")) continue;
            Link l = new Link();
            String loc = "", last = "";
            for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNodeType() != Node.ELEMENT_NODE) continue;
                String nm = c.getNodeName().toLowerCase(Locale.ROOT);
                if (nm.endsWith("loc")) loc = c.getTextContent().trim();
                else if (nm.endsWith("lastmod")) last = c.getTextContent().trim();
            }
            if (!Sanitizer.isHttp(loc)) continue;
            l.url = loc.regionMatches(true, 0, "http://", 0, 7) ? "https://" + loc.substring(7) : loc;
            l.date = FeedParser.date(last);
            if (!looksLikeArticle(l.url)) continue;
            out.add(l);
        }
        java.util.Collections.sort(out, new java.util.Comparator<Link>() {
            @Override public int compare(Link a, Link b) { return Long.compare(b.date, a.date); }
        });
        while (out.size() > 100) out.remove(out.size() - 1);
        return out;
    }

    /** A readable title from a URL when the page itself cannot be read: the last path segment. */
    static String titleFromUrl(String url) {
        String p = pathAndQuery(url);
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        String seg = p.substring(p.lastIndexOf('/') + 1).replaceAll("\\.[a-z0-9]{2,5}$", "").replace('-', ' ').replace('_', ' ');
        return Sanitizer.collapse(seg);
    }
}
