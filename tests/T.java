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
        discovery();
        pageText();
        ranking();
        markdown();
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

    static void discovery() throws Exception {
        String page = new String(file("tests/fixtures/page-with-feeds.html"), StandardCharsets.UTF_8);
        java.util.List<String> alt = FeedDiscovery.alternates(page, "https://blog.example.org/posts/hello/");
        eq("alternates: three distinct feeds", 3, alt.size());
        eq("alternates: relative href resolved", "https://blog.example.org/index.xml", alt.get(0));
        eq("alternates: absolute href with entities", "https://blog.example.org/comments.atom?a=1&b=2", alt.get(1));
        eq("alternates: http upgraded to https, upper case tag read", "https://old.example.org/feed", alt.get(2));
        eq("alternates: none in a page without links", 0, FeedDiscovery.alternates("<p>hi</p>", "https://a.org/").size());
        java.util.List<String> g = FeedDiscovery.commonGuesses("https://blog.example.org/posts/hello/");
        eq("guesses: folder first", "https://blog.example.org/posts/hello/feed", g.get(0));
        yes("guesses: all five common paths at the root", g.contains("https://blog.example.org/feed") && g.contains("https://blog.example.org/rss") && g.contains("https://blog.example.org/index.xml") && g.contains("https://blog.example.org/atom.xml") && g.contains("https://blog.example.org/feed.xml"));
        eq("guesses: root page gives five", 5, FeedDiscovery.commonGuesses("https://example.com/").size());
        eq("input: bare host gets https", "https://example.com/blog", FeedDiscovery.normalizeInput("  example.com/blog "));
        eq("input: http becomes https", "https://example.com/x", FeedDiscovery.normalizeInput("http://example.com/x"));
        eq("input: spaces refused", "", FeedDiscovery.normalizeInput("not a url"));
        eq("input: other scheme refused", "", FeedDiscovery.normalizeInput("ftp://example.com/"));
        eq("input: no dot refused", "", FeedDiscovery.normalizeInput("localhost"));
    }

    static void pageText() throws Exception {
        byte[] latin = "<p>zażółć</p>".getBytes("UTF-8");
        eq("page: utf-8 by default", "<p>zażółć</p>", PageText.decode(latin, null));
        byte[] iso = "café".getBytes("ISO-8859-1");
        eq("page: charset from header", "café", PageText.decode(iso, "text/html; charset=ISO-8859-1"));
        eq("page: charset from meta tag", "café", PageText.decode("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">café".getBytes("ISO-8859-1"), null).substring(PageText.decode("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">café".getBytes("ISO-8859-1"), null).length() - 4));
        eq("page: unknown charset falls back to utf-8", "ok", PageText.decode("ok".getBytes("UTF-8"), "text/html; charset=nonsense-9"));
    }

    static java.util.List<Ranker.Doc> rankDocs(long now) {
        long day = 86400000L;
        java.util.List<Ranker.Doc> d = new java.util.ArrayList<>();
        d.add(new Ranker.Doc(1, "Tuning tidymodels workflows", "How to tune a workflow with tidymodels and cross validation", now - day / 2));
        d.add(new Ranker.Doc(2, "Power BI DAX measures explained", "Measures in Power BI use DAX and filter context", now - day / 2));
        d.add(new Ranker.Doc(3, "Cooking pasta at home", "Boil water, add salt and pasta", now - day / 2));
        d.add(new Ranker.Doc(4, "Older tidymodels post", "An old note about tidymodels recipes", now - 60 * day));
        d.add(new Ranker.Doc(5, "DuckDB and Parquet files", "Fast analytics with duckdb on parquet files", now - 20 * day));
        return d;
    }
    static java.util.List<Long> ids(java.util.List<Ranker.Scored> l) { java.util.List<Long> o = new java.util.ArrayList<>(); for (Ranker.Scored s : l) o.add(s.id); return o; }
    static Ranker.Scored byId(java.util.List<Ranker.Scored> l, long id) { for (Ranker.Scored s : l) if (s.id == id) return s; return null; }

    static void ranking() {
        long now = 1794700000000L;
        java.util.List<String> none = new java.util.ArrayList<>();
        eq("tokens: plural, stop words and case", java.util.Arrays.asList("model", "tidymodel", "workflow"), Ranker.tokens("The Models, TIDYMODELS and a workflows!"));
        eq("tokens: short words that matter stay", java.util.Arrays.asList("power", "bi"), Ranker.tokens("Power BI"));

        java.util.List<Ranker.Topic> t1 = java.util.Arrays.asList(new Ranker.Topic("tidymodels", 3), new Ranker.Topic("Power BI", 1));
        java.util.List<Ranker.Scored> r1 = Ranker.rank(rankDocs(now), t1, none, none, now);
        eq("topics: the tidymodels post comes first", 1L, r1.get(0).id);
        eq("topics: the unrelated post is last among fresh ones, the 60-day-old match still beats it", true, ids(r1).indexOf(4L) < ids(r1).indexOf(3L));
        yes("why: names the matched topic", byId(r1, 1).why.contains("Matches your topics: tidymodels"));
        yes("why: says when nothing matched", byId(r1, 3).why.startsWith("No topic matched") || byId(r1, 3).why.contains("New today"));

        java.util.List<Ranker.Topic> t2 = java.util.Arrays.asList(new Ranker.Topic("tidymodels", 1), new Ranker.Topic("Power BI", 5));
        eq("weights: raising Power BI puts that post first", 2L, Ranker.rank(rankDocs(now), t2, none, none, now).get(0).id);

        java.util.List<Ranker.Topic> t0 = java.util.Arrays.asList(new Ranker.Topic("tidymodels", 0));
        yes("weight 0 switches a topic off", !byId(Ranker.rank(rankDocs(now), t0, none, none, now), 1).why.contains("Matches"));

        java.util.List<Ranker.Scored> fresh = Ranker.rank(rankDocs(now), new java.util.ArrayList<Ranker.Topic>(), none, none, now);
        eq("freshness alone: newest first, ties by date", 5L, fresh.get(fresh.size() - 2).id);
        eq("freshness alone: the oldest item is last", 4L, fresh.get(fresh.size() - 1).id);

        java.util.List<String> kept = java.util.Arrays.asList("Parquet and duckdb make analytics fast");
        java.util.List<Ranker.Scored> rk = Ranker.rank(rankDocs(now), new java.util.ArrayList<Ranker.Topic>(), kept, none, now);
        eq("kept: a similar older item rises above fresher ones", 5L, rk.get(0).id);
        yes("kept: the reason names the shared words", byId(rk, 5).why.contains("Like items you kept") && byId(rk, 5).why.contains("duckdb"));

        java.util.List<String> less = java.util.Arrays.asList("Cooking pasta with salt", "Pasta recipes at home");
        java.util.List<Ranker.Scored> rl = Ranker.rank(rankDocs(now), new java.util.ArrayList<Ranker.Topic>(), none, less, now);
        eq("less like this: the pasta post sinks to the bottom", 3L, rl.get(rl.size() - 1).id);
        yes("less like this: the reason says so", byId(rl, 3).why.contains("Less like this"));

        java.util.List<Ranker.Scored> ph = Ranker.rank(java.util.Arrays.asList(
            new Ranker.Doc(10, "Business intelligence", "power plants and a bi weekly meeting", now),
            new Ranker.Doc(11, "Dashboards", "learning Power BI step by step", now)), java.util.Arrays.asList(new Ranker.Topic("Power BI", 1)), none, none, now);
        eq("phrase: the exact phrase beats two scattered words", 11L, ph.get(0).id);
        java.util.List<Ranker.Scored> sc = Ranker.rank(java.util.Arrays.asList(new Ranker.Doc(20, "Claude is an assistant", "nothing about the other word", now)), java.util.Arrays.asList(new Ranker.Topic("Claude Code", 3)), none, none, now);
        yes("phrase: a topic needs all of its words", !sc.get(0).why.contains("Matches"));
        eq("empty input gives an empty list", 0, Ranker.rank(new java.util.ArrayList<Ranker.Doc>(), t1, none, none, now).size());
    }

    static void markdown() {
        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
        MarkdownExport.Note n = new MarkdownExport.Note();
        n.title = "Tuning \"tidymodels\": a guide / part 1?";
        n.source = "Julia Silge"; n.url = "https://juliasilge.com/blog/tune/"; n.published = 1794303000000L; n.keptAt = 1794700000000L;
        n.topics.add("tidymodels"); n.topics.add("Power BI");
        n.highlights.add(new MarkdownExport.Highlight("First line of a quote\nsecond line\n\nafter a blank line", "why it matters"));
        n.highlights.add(new MarkdownExport.Highlight("Plain one", ""));
        n.note = "Try this on the churn data.";
        String md = MarkdownExport.note(n, utc);
        yes("md: starts with front matter", md.startsWith("---\ntitle: \"Tuning \\\"tidymodels\\\": a guide / part 1?\"\nsource: \"Julia Silge\"\nurl: \"https://juliasilge.com/blog/tune/\"\ndate: 2026-11-10\nkept: 2026-11-14\ntopics: [\"tidymodels\", \"Power BI\"]\n---\n"));
        yes("md: heading and source link", md.contains("\n# Tuning \"tidymodels\": a guide / part 1?\n\n[Julia Silge](https://juliasilge.com/blog/tune/)\n"));
        yes("md: highlight as block quote with blank line kept inside", md.contains("> First line of a quote\n> second line\n>\n> after a blank line\n\nNote: why it matters\n"));
        yes("md: highlight without note", md.contains("> Plain one\n\n"));
        yes("md: my note section", md.endsWith("## My note\n\nTry this on the churn data.\n"));
        eq("md: file name", "2026-11-14 Tuning tidymodels a guide part 1.md", MarkdownExport.fileName(n, utc));
        eq("md: file name uses the local day", "2026-11-15 Tuning tidymodels a guide part 1.md", MarkdownExport.fileName(n, java.util.TimeZone.getTimeZone("Pacific/Auckland")));
        MarkdownExport.Note e = new MarkdownExport.Note();
        e.keptAt = 1794700000000L;
        eq("md: untitled file name", "2026-11-14 Untitled.md", MarkdownExport.fileName(e, utc));
        String empty = MarkdownExport.note(e, utc);
        yes("md: empty note has no sections", !empty.contains("## ") && empty.contains("topics: []"));
        e.title = "A very long title ".repeat(10);
        yes("md: long title is cut", MarkdownExport.fileName(e, utc).length() <= 10 + 1 + 80 + 3);
        eq("topics: all words must be present", java.util.Arrays.asList("tidymodels"), Ranker.matchedTopics("A post on tidymodels", java.util.Arrays.asList(new Ranker.Topic("tidymodels", 3), new Ranker.Topic("Power BI", 3))));
        eq("topics: multi-word match", java.util.Arrays.asList("Power BI"), Ranker.matchedTopics("BI and power users", java.util.Arrays.asList(new Ranker.Topic("Power BI", 1))));
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
