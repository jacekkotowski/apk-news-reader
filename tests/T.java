package pl.stashreader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny test runner, no libraries. A failed check prints one line; any failure exits non-zero. */
public final class T {
    static int fails = 0, runs = 0;

    static void eq(String name, Object want, Object got) {
        runs++;
        boolean ok = want == null ? got == null : want.equals(got);
        if (!ok) { fails++; System.out.println("FAIL " + name + ": wanted <" + want + "> got <" + got + ">"); }
    }
    static void yes(String name, boolean cond) { eq(name, true, cond); }
    static byte[] file(String p) throws Exception { return Files.readAllBytes(Paths.get(p)); }

    public static void main(String[] a) throws Exception {
        sanitizer();
        feeds();
        starter();
        System.out.println(runs + " checks, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    static void sanitizer() {
        String h = Sanitizer.clean("<p onclick=\"x()\">Hi <b>there</b><script>alert(1)</script></p><iframe src=\"https://e\"></iframe><style>p{}</style>");
        eq("clean strips scripts, styles, iframes and attributes", "<p>Hi <b>there</b></p>", h);
        eq("link keeps https href only", "<a href=\"https://x.org/a?b=1&amp;c=2\">t</a>", Sanitizer.clean("<a href=\"https://x.org/a?b=1&amp;c=2\" onclick=\"z\">t</a>"));
        eq("javascript link loses its href", "<a>t</a>", Sanitizer.clean("<a href=\"javascript:alert(1)\">t</a>"));
        eq("entity-obfuscated javascript link loses its href", "<a>t</a>", Sanitizer.clean("<a href=\"java&#115;cript:alert(1)\">t</a>"));
        eq("data link loses its href", "<a>t</a>", Sanitizer.clean("<a href='data:text/html,x'>t</a>"));
        eq("unclosed tags are closed", "<p>one <em>two</em></p>", Sanitizer.clean("<p>one <em>two"));
        eq("stray closing tag is dropped", "a", Sanitizer.clean("a</b>"));
        eq("img and unknown tags vanish, text stays", "x y", Sanitizer.clean("<img src=\"https://t/p.png\" onerror=\"e()\">x <blink>y</blink>"));
        eq("comments vanish", "ab", Sanitizer.clean("a<!-- <script>x</script> -->b"));
        eq("a lone < is escaped", "1 &lt; 2", Sanitizer.clean("1 < 2"));
        eq("script with upper case tag", "ok", Sanitizer.clean("<SCRIPT>bad()</SCRIPT>ok"));
        eq("unterminated script drops the rest", "ok", Sanitizer.clean("ok<script>bad()"));
        yes("no 'on' attribute survives", !Sanitizer.clean("<p onmouseover=x>a</p><a href=\"https://e.org\" onfocus=y>b</a>").contains("onmouse") && !Sanitizer.clean("<a href=\"https://e.org\" onfocus=y>b</a>").contains("onfocus"));
        eq("plain text", "Hello & goodbye — it's 5 > 3", Sanitizer.text("<p>Hello &amp; goodbye &mdash; it&#39;s 5 &gt; 3</p><script>x</script>"));
        eq("plain text keeps paragraph breaks as spaces", "One Two", Sanitizer.text("<p>One</p><p>Two</p>"));
        eq("numeric hex entity", "A", Sanitizer.decode("&#x41;"));
        eq("shorten at word", "alpha beta…", Sanitizer.shorten("alpha beta gamma delta", 12));
        eq("word count", 3, Sanitizer.words("one two  three"));
        eq("attr unquoted", "https://a.b", Sanitizer.attr(" href=https://a.b class=x", "href"));
        eq("attr not fooled by data-href", null, Sanitizer.attr(" data-href=\"https://a.b\"", "href"));
    }

    static void feeds() throws Exception {
        FeedParser.Feed f = FeedParser.parse(file("tests/fixtures/rss-sample.xml"), "https://example.com/feed");
        eq("rss title decoded", "Sample & Blog", f.title);
        eq("rss: duplicate link and untitled item dropped", 2, f.items.size());
        FeedParser.Item first = f.items.get(1), second = f.items.get(0);
        eq("rss: newest first", "Second post", second.title);
        eq("rss: first title", "First post about tidymodels", first.title);
        eq("rss: uid is the normalised link", "l:example.com/p/first", first.uid);
        yes("rss: content:encoded wins over description", first.body.contains("Full text") && first.fromContent);
        yes("rss: description-only item is not full content", !second.fromContent);
        eq("rss: pubDate with offset", 1794303000000L, first.published);
        eq("rss: pubDate with GMT", 1794391200000L, second.published);
        String clean = Sanitizer.clean(first.body);
        yes("rss: sanitised body keeps text and good link", clean.contains("Full text") && clean.contains("href=\"https://example.com/x\"") && clean.contains("Second paragraph."));
        yes("rss: sanitised body has no script, iframe, style or javascript", !clean.contains("iframe") && !clean.contains("style") && !clean.contains("javascript") && !clean.contains("onclick"));

        FeedParser.Feed at = FeedParser.parse(file("tests/fixtures/atom-sample.xml"), "https://example.com/atom");
        eq("atom title", "Atom Sample", at.title);
        eq("atom entries", 2, at.items.size());
        eq("atom: newest first (offset date)", "Atom entry two", at.items.get(0).title);
        eq("atom: alternate link chosen, not self", "https://example.com/a/one", at.items.get(1).link);
        eq("atom: link without rel", "https://example.com/a/two", at.items.get(0).link);
        eq("atom: published with microseconds", 1794471330123L, at.items.get(1).published);
        yes("atom: content is the body and counts as content", at.items.get(0).fromContent && at.items.get(0).body.contains("<em>content</em>"));
        eq("atom: updated with +01:00", 1794646800000L, at.items.get(0).published);

        FeedParser.Feed st = FeedParser.parse(file("tests/fixtures/stray-cdata-end.xml"), "https://example.com/s");
        eq("stray ]]> in text is repaired", 1, st.items.size());
        yes("repaired feed keeps the real CDATA section", st.items.get(0).body.contains("Fine section") && st.items.get(0).body.contains("]]>"));
        try { FeedParser.parse(file("tests/fixtures/doctype.xml"), "x"); yes("doctype refused", false); }
        catch (FeedParser.FeedException e) { yes("doctype refused", e.getMessage().startsWith("Refused")); }
        try { FeedParser.parse("<html><body>no</body></html>".getBytes(StandardCharsets.UTF_8), "x"); yes("html refused", false); }
        catch (FeedParser.FeedException e) { yes("html refused", true); }
        try { FeedParser.parse("<!DOCTYPE html><html></html>".getBytes(StandardCharsets.UTF_8), "x"); yes("html doctype refused", false); }
        catch (FeedParser.FeedException e) { eq("html doctype message", "This is a web page, not a feed", e.getMessage()); }
        try { FeedParser.parse("<a><b></a>".getBytes(StandardCharsets.UTF_8), "x"); yes("broken xml refused", false); }
        catch (FeedParser.FeedException e) { eq("broken xml message", "The feed is not valid XML", e.getMessage()); }

        StringBuilder big = new StringBuilder("<rss><channel><title>Big</title>");
        for (int i = 0; i < 100; i++) big.append("<item><title>T").append(i).append("</title><link>https://e.org/").append(i).append("</link><pubDate>Mon, 10 Nov 2026 09:").append(i % 60 < 10 ? "0" : "").append(i % 60).append(":00 +0000</pubDate></item>");
        big.append("</channel></rss>");
        eq("items are capped per feed", FeedParser.MAX_ITEMS, FeedParser.parse(big.toString().getBytes(StandardCharsets.UTF_8), "x").items.size());

        eq("link normalising", "example.com/a/?x=1", FeedParser.normalizeLink("https://www.example.com/a/?x=1&utm_source=rss#frag"));
        eq("https is required for sources", "The address must start with https://", msg("http://example.com/feed"));
        eq("host is required", "The address has no valid host name", msg("https://localhost/feed"));
    }

    static String msg(String u) {
        try { FeedParser.validateUrl(u); return ""; } catch (FeedParser.FeedException e) { return e.getMessage(); }
    }

    /** starter.json is read by the app with org.json; here it is checked as text. */
    static void starter() throws Exception {
        String s = new String(file("app/assets/starter.json"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
        java.util.Set<String> seen = new java.util.HashSet<>();
        int n = 0;
        while (m.find()) {
            n++;
            yes("starter url is https: " + m.group(1), m.group(1).startsWith("https://"));
            yes("starter url is unique: " + m.group(1), seen.add(m.group(1)));
        }
        yes("starter has about 15 to 25 feeds", n >= 15 && n <= 25);
        yes("starter includes prokulski", s.contains("https://blog.prokulski.science/index.xml"));
        Matcher t = Pattern.compile("\"name\"\\s*:\\s*\"[^\"]+\"").matcher(s);
        int names = 0; while (t.find()) names++;
        eq("every starter feed has a name", n, names);
    }
}
