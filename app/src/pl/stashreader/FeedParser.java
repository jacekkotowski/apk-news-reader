package pl.stashreader;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Parses RSS 2.0, RSS 1.0 and Atom. Documents with a DOCTYPE are refused, which blocks external-entity attacks. No Android imports. */
final class FeedParser {
    private FeedParser() {}

    /** Newest items kept per source and refresh. */
    static final int MAX_ITEMS = 40;

    static final class Item {
        String uid, title, link;
        /** Feed text as sent: HTML, not yet sanitised. */
        String body;
        /** True when the body came from content:encoded or Atom content, which is usually the whole article. */
        boolean fromContent;
        long published;
    }
    static final class Feed { String title = ""; final List<Item> items = new ArrayList<>(); }
    static final class FeedException extends Exception { FeedException(String m) { super(m); } }

    static String validateUrl(String raw) throws FeedException {
        String s = raw == null ? "" : raw.trim();
        URI u;
        try { u = new URI(s); } catch (Exception e) { throw new FeedException("That is not a valid address"); }
        if (!"https".equalsIgnoreCase(u.getScheme())) throw new FeedException("The address must start with https://");
        if (u.getHost() == null || u.getHost().indexOf('.') < 0) throw new FeedException("The address has no valid host name");
        return s;
    }

    static Feed parse(byte[] xml, String feedUrl) throws FeedException {
        String probe = new String(xml, StandardCharsets.ISO_8859_1).replace("\0", "").toUpperCase(Locale.ROOT);
        if (probe.contains("<!DOCTYPE") || probe.contains("<!ENTITY")) {
            if (probe.contains("<!DOCTYPE HTML") || probe.contains("<HTML")) throw new FeedException("This is a web page, not a feed");
            throw new FeedException("Refused: the feed contains a DOCTYPE");
        }
        Document doc;
        try {
            doc = read(xml);
        } catch (Exception e) {
            try { doc = read(escapeStrayCdataEnds(xml)); }
            catch (Exception e2) { throw new FeedException("The feed is not valid XML"); }
        }
        return build(doc, feedUrl);
    }

    private static Document read(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new ByteArrayInputStream(xml));
    }

    /** Some feeds contain "]]>" in ordinary text, which XML forbids. Outside real CDATA sections it becomes "]]&gt;". */
    static byte[] escapeStrayCdataEnds(byte[] xml) {
        String s = new String(xml, StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder(s.length() + 16);
        int i = 0, n = s.length();
        while (i < n) {
            if (s.startsWith("<![CDATA[", i)) {
                int e = s.indexOf("]]>", i + 9);
                int stop = e < 0 ? n : e + 3;
                b.append(s, i, stop); i = stop;
            } else if (s.startsWith("]]>", i)) { b.append("]]&gt;"); i += 3; }
            else { b.append(s.charAt(i)); i++; }
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Feed build(Document doc, String feedUrl) throws FeedException {
        Element root = doc.getDocumentElement();
        String kind = local(root).toLowerCase(Locale.ROOT);
        Feed out = new Feed();
        List<Item> all = new ArrayList<>();
        if (kind.equals("feed")) {
            out.title = text(root, "title");
            for (Element e : children(root, "entry")) all.add(atom(e));
        } else if (kind.equals("rss") || kind.equals("rdf")) {
            Element channel = first(root, "channel");
            if (channel == null) throw new FeedException("The RSS feed has no channel");
            out.title = text(channel, "title");
            List<Element> items = children(channel, "item");
            if (items.isEmpty()) items = children(root, "item"); // RSS 1.0 keeps items beside the channel
            for (Element e : items) all.add(rss(e));
        } else {
            throw new FeedException("This is not an RSS or Atom feed");
        }
        out.title = Sanitizer.text(out.title);
        for (Item i : all) add(out, i, feedUrl);
        finish(out);
        return out;
    }

    /** Newest first, then capped, so a feed with years of history does not flood the list. */
    static void finish(Feed f) {
        Collections.sort(f.items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Long.compare(b.published, a.published); }
        });
        while (f.items.size() > MAX_ITEMS) f.items.remove(f.items.size() - 1);
    }

    private static Item rss(Element e) {
        Item i = new Item();
        i.title = text(e, "title");
        i.link = text(e, "link");
        i.uid = text(e, "guid");
        String content = text(e, "encoded");
        i.fromContent = !content.isEmpty();
        i.body = content.isEmpty() ? text(e, "description") : content;
        i.published = date(text(e, "pubDate"));
        if (i.published == 0) i.published = date(text(e, "date"));
        return i;
    }

    private static Item atom(Element e) {
        Item i = new Item();
        i.title = text(e, "title");
        i.uid = text(e, "id");
        String content = text(e, "content");
        i.fromContent = !content.isEmpty();
        i.body = content.isEmpty() ? text(e, "summary") : content;
        i.link = "";
        for (Element l : children(e, "link")) {
            String rel = l.getAttribute("rel");
            if (rel.isEmpty() || rel.equals("alternate")) { i.link = l.getAttribute("href"); break; }
        }
        i.published = date(text(e, "published"));
        if (i.published == 0) i.published = date(text(e, "updated"));
        return i;
    }

    static void add(Feed f, Item i, String feedUrl) {
        i.title = Sanitizer.text(i.title);
        if (i.body == null) i.body = "";
        if (i.title.isEmpty()) return;
        if (i.link == null) i.link = "";
        i.link = i.link.trim();
        if (!Sanitizer.isHttp(i.link)) i.link = "";
        if (i.link.regionMatches(true, 0, "http://", 0, 7)) i.link = "https://" + i.link.substring(7); // the app allows no clear-text traffic
        i.uid = uid(i, feedUrl);
        for (Item o : f.items) if (o.uid.equals(i.uid)) return;
        f.items.add(i);
    }

    /** Normalised link when present (so the same page from two feeds is one item), then GUID, then feed and headline. */
    static String uid(Item i, String feedUrl) {
        String l = normalizeLink(i.link);
        if (!l.isEmpty()) return "l:" + l;
        if (i.uid != null && !i.uid.trim().isEmpty()) return "g:" + i.uid.trim();
        return "t:" + feedUrl + "|" + i.title.toLowerCase(Locale.ROOT);
    }

    static String normalizeLink(String link) {
        if (link == null) return "";
        String s = link.trim();
        if (s.isEmpty()) return "";
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        s = s.replaceAll("(?i)^https?://", "").replaceAll("(?i)^www\\.", "");
        s = s.replaceAll("(?i)([?&])(utm_[a-z]+|fbclid|gclid|ref|source)=[^&]*", "$1").replaceAll("[?&]+$", "").replaceAll("\\?&", "?");
        return s.replaceAll("/+$", "");
    }

    private static final String[] DATES = {"EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss z", "d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm Z", "EEE, d MMM yyyy HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};

    static long date(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        // keep at most three fraction digits: "12:00:00.123456Z" would otherwise be read as 123456 ms
        String v = s.trim().replaceAll("(\\.\\d{3})\\d+", "$1");
        for (String p : DATES) {
            SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition at = new ParsePosition(0);
            try {
                Date d = f.parse(v, at);
                if (d != null && at.getIndex() == v.length()) return d.getTime();
            } catch (RuntimeException ignored) { }
        }
        return 0;
    }

    private static String local(Node n) {
        String s = n.getNodeName();
        int c = s.indexOf(':');
        return c < 0 ? s : s.substring(c + 1);
    }
    private static List<Element> children(Element p, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling())
            if (n.getNodeType() == Node.ELEMENT_NODE && local(n).equalsIgnoreCase(name)) out.add((Element) n);
        return out;
    }
    private static Element first(Element p, String name) {
        List<Element> c = children(p, name);
        return c.isEmpty() ? null : c.get(0);
    }
    private static String text(Element p, String name) {
        Element e = first(p, name);
        if (e == null) return "";
        String s = e.getTextContent();
        return s == null ? "" : s.trim();
    }
}
