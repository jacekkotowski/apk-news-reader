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
        shareAndRecall();
        gemini();
        feedFilter();
        starter();
        discovery();
        pageText();
        ranking();
        fitting();
        clusters();
        passages();
        listening();
        s2key();
        markdown();
        json();
        querySources();
        watching();
        recommendations();
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

    static String text(String p) throws Exception { return new String(file(p), java.nio.charset.StandardCharsets.UTF_8); }

    static String thrown(java.util.concurrent.Callable<Object> c) {
        try { c.call(); return ""; } catch (Exception e) { return String.valueOf(e.getMessage()); }
    }

    static void gemini() throws Exception {
        eq("gemini esc: quotes, newline, backslash", "a\\\"b\\nc\\\\d", Gemini.esc("a\"b\nc\\d"));
        eq("gemini esc: control character", "\\u0001", Gemini.esc("\u0001"));

        // requests must be valid JSON of the right shape
        Object vid = Json.parse(Gemini.notesRequest("tidymodels, Power BI", "A talk \"quoted\"", "https://www.youtube.com/watch?v=9hE5-98ZeCg", ""));
        String vj = Gemini.notesRequest("tidymodels", "T", "https://www.youtube.com/watch?v=9hE5-98ZeCg", "");
        yes("gemini: video request is JSON", Json.obj(vid) != null);
        yes("gemini: video request carries the YouTube address as fileData", vj.contains("\"fileData\":{\"fileUri\":\"https://www.youtube.com/watch?v=9hE5-98ZeCg\"}"));
        String aj = Gemini.notesRequest("tidymodels", "T", "", "Line one\nLine \"two\"");
        yes("gemini: article request is JSON", Json.obj(Json.parse(aj)) != null);
        yes("gemini: article request has no video part and sends the text", !aj.contains("fileData") && aj.contains("Line one\\nLine \\\"two\\\""));
        yes("gemini: structured output is asked for", aj.contains("\"responseMimeType\":\"application/json\"") && aj.contains("\"responseSchema\""));
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 50000; i++) big.append('x');
        yes("gemini: article text is capped", Gemini.notesRequest("", "T", "", big.toString()).length() < Gemini.MAX_TEXT + 3000);
        String pj = Gemini.pathRequest("tidymodels", "Power BI", "- A (S)\n  highlight: \"x\"\n");
        yes("gemini: path request is JSON and never mentions notes", Json.obj(Json.parse(pj)) != null && !pj.toLowerCase().contains("my note"));
        yes("gemini: ping request is JSON", Json.obj(Json.parse(Gemini.pingRequest())) != null);

        // real answers recorded from the live API
        eq("gemini: ping answer", "OK", Gemini.replyText(200, text("tests/fixtures/gemini-ping.json")).trim());
        Gemini.Notes nv = Gemini.parseNotes(Gemini.replyText(200, text("tests/fixtures/gemini-video.json")));
        yes("gemini: video notes have the sections", nv.md.contains("## Key ideas") && nv.md.contains("## Quotes") && nv.md.contains("## Try next"));
        yes("gemini: video notes have questions with answers", nv.questions.size() == 3 && !nv.questions.get(0)[0].isEmpty() && !nv.questions.get(0)[1].isEmpty());
        Gemini.Notes na = Gemini.parseNotes(Gemini.replyText(200, text("tests/fixtures/gemini-article.json")));
        yes("gemini: article notes read", na.md.contains("## Key ideas") && na.questions.size() == 3);
        Gemini.Path gp = Gemini.parsePath(Gemini.replyText(200, text("tests/fixtures/gemini-path.json")));
        yes("gemini: path has order and gaps", gp.md.contains("## Order to learn") && gp.md.contains("## Gaps"));
        yes("gemini: path searches are arxiv or hn", gp.searches.size() == 3 && gp.searches.get(0)[0].equals("arxiv") && gp.searches.get(1)[0].equals("hn"));

        // errors: the wrong-key answer was recorded; the others are written by hand
        String bad = thrown(() -> Gemini.replyText(400, text("tests/fixtures/gemini-badkey.json")));
        yes("gemini: wrong key says so", bad.startsWith("Gemini did not accept the key") && bad.contains("API key not valid"));
        String quota = thrown(() -> Gemini.replyText(429, "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\",\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        yes("gemini: quota error is a calm sentence", quota.contains("free limit"));
        yes("gemini: unknown model", thrown(() -> Gemini.replyText(404, "{\"error\":{\"code\":404,\"message\":\"not found\"}}")).contains("needs an update"));
        yes("gemini: private video", thrown(() -> Gemini.replyText(400, "{\"error\":{\"code\":400,\"message\":\"Failed to process YouTube video\"}}")).contains("must be public"));
        yes("gemini: html error page", thrown(() -> Gemini.replyText(502, "<html>Bad gateway</html>")).startsWith("Gemini answered HTTP 502"));
        yes("gemini: blocked prompt", thrown(() -> Gemini.replyText(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).contains("SAFETY"));
        yes("gemini: no candidates", thrown(() -> Gemini.replyText(200, "{\"candidates\":[]}")).contains("no text"));

        // tolerant parsing
        eq("gemini: code fences around the JSON are stripped", "x", Gemini.parseNotes("```json\n{\"notes_md\":\"x\",\"questions\":[]}\n```").md);
        yes("gemini: empty notes are refused", thrown(() -> Gemini.parseNotes("{\"notes_md\":\"\",\"questions\":[]}")).contains("empty"));
        yes("gemini: not JSON is refused", thrown(() -> Gemini.parseNotes("Sure! Here are your notes")).contains("could not be read"));
        Gemini.Path odd = Gemini.parsePath("{\"path_md\":\"## Order to learn\",\"searches\":[{\"kind\":\"google\",\"query\":\"x\"},{\"kind\":\"HN\",\"query\":\"duckdb\"},{\"kind\":\"arxiv\",\"query\":\"\"}]}");
        yes("gemini: unknown kinds and empty queries are dropped, kind is lower-cased", odd.searches.size() == 1 && odd.searches.get(0)[0].equals("hn") && odd.searches.get(0)[1].equals("duckdb"));

        // export
        MarkdownExport.Note n = new MarkdownExport.Note();
        n.title = "T"; n.source = "S"; n.url = "https://example.com/t"; n.ai = "*Based on the video.*\n\n## Key ideas\n- one";
        String md = MarkdownExport.note(n, java.util.TimeZone.getTimeZone("UTC"));
        yes("export: AI notes are labelled and their headings demoted", md.contains("## AI notes (Gemini, not the article)") && md.contains("### Key ideas") && !md.contains("\n## Key ideas"));
        MarkdownExport.Note plain = new MarkdownExport.Note(); plain.title = "T"; plain.source = "S";
        yes("export: no AI section when there are no AI notes", !MarkdownExport.note(plain, java.util.TimeZone.getTimeZone("UTC")).contains("AI notes"));
        String pn = MarkdownExport.pathNote("tidymodels", "## Order to learn\n1. A", 1794303000000L, java.util.TimeZone.getTimeZone("UTC"));
        yes("export: path note has front matter and the path", pn.startsWith("---\ntitle: ") && pn.contains("# Learning path: tidymodels") && pn.contains("## Order to learn"));
        eq("export: path file name", "Path - Power BI DAX.md", MarkdownExport.pathFileName("Power BI / DAX"));
        eq("export: path file name for an empty topic", "Path - topic.md", MarkdownExport.pathFileName("///"));
    }

    static void feedFilter() {
        FeedFilter.Filters none = new FeedFilter.Filters();
        yes("filter: nothing on accepts everything", FeedFilter.accept(none, "", false, 5, "Any title", "any text") && none.count() == 0);
        FeedFilter.Filters t = new FeedFilter.Filters(); t.type = "video";
        yes("filter: video type", FeedFilter.accept(t, "video", false, 1, "a", "b") && !FeedFilter.accept(t, "", false, 1, "a", "b") && !FeedFilter.accept(t, "audio", false, 1, "a", "b"));
        t.type = "audio";
        yes("filter: podcast type", FeedFilter.accept(t, "audio", false, 1, "a", "b") && !FeedFilter.accept(t, "video", false, 1, "a", "b"));
        t.type = "article";
        yes("filter: article type is everything that is not a video or podcast", FeedFilter.accept(t, "", false, 1, "a", "b") && !FeedFilter.accept(t, "video", false, 1, "a", "b") && !FeedFilter.accept(t, "audio", false, 1, "a", "b"));
        FeedFilter.Filters tp = new FeedFilter.Filters(); tp.topic = "tidymodels";
        yes("filter: topic", FeedFilter.accept(tp, "", true, 1, "a", "b") && !FeedFilter.accept(tp, "", false, 1, "a", "b"));
        FeedFilter.Filters src = new FeedFilter.Filters(); src.source = 7;
        yes("filter: source", FeedFilter.accept(src, "", false, 7, "a", "b") && !FeedFilter.accept(src, "", false, 8, "a", "b"));
        FeedFilter.Filters q = new FeedFilter.Filters(); q.query = "  Random FOREST ";
        yes("filter: search needs every word, in any case and order", FeedFilter.accept(q, "", false, 1, "Tuning a random forest", "x") && FeedFilter.accept(q, "", false, 1, "Forest", "a random thing") && !FeedFilter.accept(q, "", false, 1, "Tuning a random tree", "x"));
        yes("filter: search also reads the text", FeedFilter.accept(q, "", false, 1, "Title", "about a random forest model"));
        yes("filter: blank search is no filter", FeedFilter.accept(new FeedFilter.Filters(), "", false, 1, "a", "b") && new FeedFilter.Filters().count() == 0);
        FeedFilter.Filters all = new FeedFilter.Filters(); all.type = "video"; all.topic = "x"; all.source = 3; all.query = "dax"; all.newest = true;
        eq("filter: count of filters on", 5, all.count());
        yes("filter: all together", FeedFilter.accept(all, "video", true, 3, "DAX tips", "") && !FeedFilter.accept(all, "video", true, 4, "DAX tips", "") && !FeedFilter.accept(all, "video", true, 3, "Power BI", ""));
        yes("filter: null filters accept", FeedFilter.accept(null, "", false, 1, "a", "b"));
        long now = 1800000000000L;
        eq("expiry: cutoff is 30 days back", now - 30L * 86400000L, FeedFilter.cutoff(now, FeedFilter.EXPIRY_DAYS));
        eq("expiry: the period is 30 days", 30, FeedFilter.EXPIRY_DAYS);
    }

    static void shareAndRecall() {
        eq("share: url from youtube text", "https://youtu.be/abc123?si=x", Share.extractUrl("Great video https://youtu.be/abc123?si=x"));
        eq("share: trailing punctuation is dropped", "https://example.com/a", Share.extractUrl("See (https://example.com/a)."));
        eq("share: no address", "", Share.extractUrl("just words"));
        eq("share: null text", "", Share.extractUrl(null));
        eq("share: title from subject", "A good post", Share.titleFor("A good post", "https://example.com/p", "https://example.com/p"));
        eq("share: subject that is only an address is ignored", "Hello there", Share.titleFor("https://example.com/p", "Hello there https://example.com/p", "https://example.com/p"));
        eq("share: title from text around the address", "Great video", Share.titleFor(null, "Great video https://youtu.be/abc", "https://youtu.be/abc"));
        eq("share: quotes and dashes around the title are removed", "Great video", Share.titleFor(null, "\u201cGreat video\u201d - https://youtu.be/abc", "https://youtu.be/abc"));
        eq("share: nothing useful gives empty title", "", Share.titleFor(null, "https://example.com/p", "https://example.com/p"));
        eq("share: host without www", "example.com", Share.hostOf("https://www.example.com/p?q=1"));
        long now = 1000000000000L;
        Recall.Next a = Recall.next(0, true, now);
        eq("recall: first success moves to step 1", 1, a.step);
        eq("recall: first success asks again in 3 days", now + 3 * Recall.DAY, a.due);
        eq("recall: second success in 7 days", now + 7 * Recall.DAY, Recall.next(1, true, now).due);
        eq("recall: third success in 21 days", now + 21 * Recall.DAY, Recall.next(2, true, now).due);
        eq("recall: fourth success in 60 days", now + 60 * Recall.DAY, Recall.next(3, true, now).due);
        eq("recall: later successes stay at 60 days", now + 60 * Recall.DAY, Recall.next(9, true, now).due);
        Recall.Next r = Recall.next(5, false, now);
        eq("recall: Again resets the step", 0, r.step);
        eq("recall: Again asks tomorrow", now + Recall.DAY, r.due);
        eq("recall: cap leaves what is not yet answered", 3, Recall.toShow(2, 10));
        eq("recall: fewer due than the cap", 2, Recall.toShow(0, 2));
        eq("recall: nothing left after the cap", 0, Recall.toShow(5, 10));
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

        FeedParser.Feed yt = FeedParser.parse(file("tests/fixtures/youtube-sample.xml"), "https://www.youtube.com/feeds/videos.xml?channel_id=x");
        eq("youtube: channel title", "StatQuest with Josh Starmer", yt.title);
        eq("youtube: two videos", 2, yt.items.size());
        eq("youtube: video link", "https://www.youtube.com/watch?v=euwIxfkAJMU", yt.items.get(0).link);
        yes("youtube: description is read from media:group", yt.items.get(0).body.contains("Concepts and Code") || yt.items.get(0).body.length() > 50);
        eq("youtube: kind is video", "video", FeedParser.kindOf(yt.items.get(0).link, yt.items.get(0).audio));
        eq("youtube: no audio", "", yt.items.get(0).audio);
        eq("kind: short link", "video", FeedParser.kindOf("https://youtu.be/abc", ""));
        eq("kind: ordinary page", "", FeedParser.kindOf("https://example.com/watch?v=1", ""));
        FeedParser.Feed pod = FeedParser.parse(file("tests/fixtures/podcast-sample.xml"), "https://dataskeptic.libsyn.com/rss");
        eq("podcast: two episodes", 2, pod.items.size());
        yes("podcast: audio address is https and ends in mp3 before the query", pod.items.get(0).audio.startsWith("https://") && pod.items.get(0).audio.contains(".mp3"));
        eq("podcast: kind is audio", "audio", FeedParser.kindOf(pod.items.get(0).link, pod.items.get(0).audio));
        FeedParser.Item ins = new FeedParser.Item(); ins.title = "Clear text"; ins.link = "https://example.com/e"; ins.audio = "http://example.com/a.mp3";
        FeedParser.Feed fx = new FeedParser.Feed(); FeedParser.add(fx, ins, "https://example.com/f");
        eq("audio: http is upgraded to https", "https://example.com/a.mp3", fx.items.get(0).audio);
        FeedParser.Item bad = new FeedParser.Item(); bad.title = "Bad audio"; bad.link = "https://example.com/b"; bad.audio = "javascript:alert(1)";
        FeedParser.add(fx, bad, "https://example.com/f");
        eq("audio: other schemes are dropped", "", fx.items.get(1).audio);

        StringBuilder pad = new StringBuilder("<html><head><script>");
        for (int i = 0; i < 283000; i++) pad.append('x');
        pad.append("</script><link rel=\"alternate\" type=\"application/rss+xml\" title=\"RSS\" href=\"https://www.youtube.com/feeds/videos.xml?channel_id=UCcc21gBGNJwZM_eDEByeN-Q\"></head></html>");
        java.util.List<String> far = FeedDiscovery.alternates(pad.toString(), "https://m.youtube.com/@sqlbi?si=GbjplyTSPoH1WfLZ&cbrd=1&ucbcb=1");
        yes("discovery: a feed link far into a big page (YouTube) is found", far.size() == 1 && far.get(0).equals("https://www.youtube.com/feeds/videos.xml?channel_id=UCcc21gBGNJwZM_eDEByeN-Q"));
        eq("discovery: a YouTube address with a share tag is kept as typed", "https://youtube.com/@sqlbi?si=GbjplyTSPoH1WfLZ", FeedDiscovery.normalizeInput("https://youtube.com/@sqlbi?si=GbjplyTSPoH1WfLZ"));
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



    static void clusters() throws Exception {
        long now = 1794700000000L;
        java.util.List<Clusters.Item> items = new java.util.ArrayList<>();
        for (Object o : Json.list(Json.parse(text("tests/fixtures/clusters.json")))) {
            java.util.Map<String, Object> m = Json.obj(o);
            long age = Json.num(m, "ageHours");
            items.add(new Clusters.Item(Json.num(m, "id"), Json.str(m, "title"), Json.str(m, "link"), Json.str(m, "source"), age < 0 ? 0 : now - age * 3600000L));
        }
        java.util.List<java.util.List<Clusters.Item>> g = Clusters.group(items, now);
        java.util.List<java.util.List<Long>> ids = new java.util.ArrayList<>();
        for (java.util.List<Clusters.Item> c : g) { java.util.List<Long> x = new java.util.ArrayList<>(); for (Clusters.Item i : c) x.add(i.id); ids.add(x); }
        eq("clusters: the GPT-5 story has three sources, in input order", java.util.Arrays.asList(1L, 3L, 8L), ids.get(0));
        eq("clusters: the same link (tracking, www, slash, scheme ignored) joins two items", java.util.Arrays.asList(5L, 7L), ids.get(1));
        eq("clusters: exactly two groups", 2, ids.size());
        boolean joined = false;
        for (java.util.List<Long> x : ids) if (x.contains(4L) || x.contains(6L)) joined = true;
        yes("clusters: two Power BI posts with only two shared words stay apart", !joined);
        for (java.util.List<Long> x : ids) yes("clusters: an item older than 3 days is left out", !x.contains(9L));
        for (java.util.List<Long> x : ids) yes("clusters: an undated item is left out", !x.contains(13L));
        for (java.util.List<Long> x : ids) yes("clusters: two bare home pages are not one story", !x.contains(10L) && !x.contains(11L));
        eq("link key: tracking, www, slash", "essay.example.com/notebooks", Clusters.linkKey("http://www.Essay.example.com/notebooks/?utm_source=a&ref=hn#top"));
        eq("link key: a real parameter stays", "x.org/watch?v=1", Clusters.linkKey("https://x.org/watch?v=1&utm_campaign=z"));
        eq("link key: bare host is empty", "", Clusters.linkKey("https://blog.example.org/"));
        eq("link key: not a web address", "", Clusters.linkKey("mailto:a@b.c"));
        eq("clusters: no items, no groups", 0, Clusters.group(new java.util.ArrayList<Clusters.Item>(), now).size());
    }


    static void passages() throws Exception {
        // chunking
        StringBuilder long1 = new StringBuilder();
        for (int i = 0; i < 40; i++) long1.append("Sentence number ").append(i).append(" says something about columns and files. ");
        java.util.List<String> ch = Passages.chunk(long1.toString());
        yes("chunk: several pieces", ch.size() >= 4);
        boolean small = true, ends = true;
        for (String c : ch) { if (c.length() > Passages.HARD_MAX) small = false; if (!c.endsWith(".")) ends = false; }
        yes("chunk: no piece over the hard limit", small);
        yes("chunk: pieces end at a sentence end", ends);
        eq("chunk: empty text", 0, Passages.chunk("  ").size());
        StringBuilder nospace = new StringBuilder();
        for (int i = 0; i < 2500; i++) nospace.append('x');
        for (String c : Passages.chunk(nospace.toString())) yes("chunk: a huge word is still cut", c.length() <= Passages.HARD_MAX);
        eq("chunk: a short text is one piece", 1, Passages.chunk("One. Two. Three.").size());

        // questions
        eq("match query: key words as prefixes", "parquet* OR column* OR store*", Passages.matchQuery("How does Parquet store the columns?"));
        eq("match query: only stop words", "", Passages.matchQuery("What is the?"));
        yes("match query: quotes and operators cannot get through", !Passages.matchQuery("\"x\" AND NEAR(a b) -c OR d").contains("\"") && !Passages.matchQuery("\"x\" AND NEAR(a b) -c").contains("("));
        eq("terms: at most eight", 8, Passages.terms("alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo").size());

        // ranking
        java.util.List<Passages.Passage> c = new java.util.ArrayList<>();
        c.add(new Passages.Passage(1, "text", "Parquet stores data by column so queries read fewer bytes."));
        c.add(new Passages.Passage(1, "text", "The columns of a Parquet file are compressed one by one."));
        c.add(new Passages.Passage(1, "text", "Parquet column chunks carry statistics for skipping."));
        c.add(new Passages.Passage(2, "hl", "Columnar storage makes analytics fast."));
        c.add(new Passages.Passage(3, "text", "Gardening in spring needs patience."));
        java.util.List<Passages.Passage> r = Passages.rank("How does Parquet store columns?", c, 8);
        yes("rank: an unrelated passage is dropped", r.size() == 3 && !containsText(r, "Gardening"));
        int fromOne = 0;
        for (Passages.Passage p : r) if (p.itemId == 1) fromOne++;
        eq("rank: at most two passages from one item", 2, fromOne);
        eq("rank: no match, no passages", 0, Passages.rank("kubernetes", c, 8).size());
        eq("rank: the limit is respected", 1, Passages.rank("Parquet columns", c, 1).size());

        // answers
        String reply = Gemini.replyText(200, text("tests/fixtures/gemini-answer.json"));
        yes("answer: text is read from the reply", reply.startsWith("Parquet stores data by column"));
        String checked = Passages.checkCitations(reply, 2);
        yes("answer: a citation to a passage that was not sent is removed", !checked.contains("[7]") && checked.contains("[1]") && checked.contains("[2]"));
        eq("answer: two real citations counted", 2, Passages.citations(reply, 2));
        eq("answer: none counted when no passages", 0, Passages.citations(reply, 0));
        java.util.List<String[]> ps = new java.util.ArrayList<>();
        ps.add(new String[]{"Parquet basics", "Parquet stores data by column."});
        String req = Gemini.answerRequest("What is \"Parquet\"?", ps);
        yes("answer request: valid JSON", Json.obj(Json.parse(req)) != null);
        yes("answer request: numbered passage, question and the no-answer rule", req.contains("[1] (Parquet basics) Parquet stores data by column.") && req.contains("QUESTION: What is \\\"Parquet\\\"?") && req.contains(Passages.NO_ANSWER));
        yes("answer request: no JSON schema is asked for", !req.contains("responseSchema"));
    }
    static boolean containsText(java.util.List<Passages.Passage> l, String s) { for (Passages.Passage p : l) if (p.text.contains(s)) return true; return false; }


    static void s2key() {
        yes("s2 key: a normal key is valid", QuerySource.validS2Key("aB3dEfGh1jKlMnOpQrStUvWxYz0123456789AbCd"));
        yes("s2 key: with spaces is not", !QuerySource.validS2Key("aB3dEfGh1jKlMnOpQ rStUvWxYz"));
        yes("s2 key: too short is not", !QuerySource.validS2Key("abc"));
        yes("s2 key: empty and null are not", !QuerySource.validS2Key("") && !QuerySource.validS2Key(null));
        yes("s2 host: the API address", QuerySource.isS2Host(QuerySource.requestUrl("s2", "causal inference", 1794700000000L)));
        yes("s2 host: recommendations and citations too", QuerySource.isS2Host(QuerySource.requestUrl("recs", "", 1794700000000L)) && QuerySource.isS2Host(QuerySource.citationsUrl("0123456789012345678901234567890123456789")));
        yes("s2 host: the key never goes to another host", !QuerySource.isS2Host("https://evil.example/https://api.semanticscholar.org/") && !QuerySource.isS2Host("https://api.semanticscholar.org.evil.example/x") && !QuerySource.isS2Host("http://api.semanticscholar.org/x") && !QuerySource.isS2Host("https://www.reddit.com/r/x"));
    }

    static void listening() {
        yes("isArticle: a plain page is an article", FeedParser.isArticle("https://example.org/post", ""));
        yes("isArticle: a video is not", !FeedParser.isArticle("https://www.youtube.com/watch?v=1", ""));
        yes("isArticle: a podcast episode is not", !FeedParser.isArticle("https://example.org/ep1", "https://cdn.example.org/a.mp3"));
        java.util.List<String> s = Listen.sentences("A talk about Parquet", true, "Parquet stores data by column. It is compressed! Is it fast? Yes, very. See Dr. Smith.");
        eq("listen: the title comes first", "A talk about Parquet.", s.get(0));
        eq("listen: sentences follow", "Parquet stores data by column.", s.get(1));
        yes("listen: short pieces are joined to the next sentence", s.contains("Is it fast? Yes, very.") || s.contains("It is compressed! Is it fast?"));
        yes("listen: no warning for a full article", !s.get(1).startsWith("This is only"));
        java.util.List<String> summary = Listen.sentences("T", false, "Some text here that is long enough to be a sentence.");
        eq("listen: a summary is announced as a summary", "This is only the summary from the feed, not the full article.", summary.get(1));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 120; i++) big.append("word").append(i).append(i % 9 == 0 ? ", " : " ");
        boolean cut = true;
        for (String x : Listen.sentences("", true, big.toString())) if (x.length() > Listen.MAX_SENTENCE + 1) cut = false;
        yes("listen: a very long sentence is cut", cut);
        eq("listen: no text, only the title", 1, Listen.sentences("Only a title", true, "").size());
        eq("listen: nothing at all", 0, Listen.sentences("", true, "  ").size());
        eq("language: English", "", Listen.language("This is an ordinary English text about data and models and nothing else at all."));
        eq("language: Polish", "pl", Listen.language("Zażółć gęślą jaźń: to jest zwykły tekst po polsku o danych, modelach i łączeniu źródeł."));
        eq("language: too short to tell", "", Listen.language("Zażółć"));
        eq("rate: stays on a choice", 120, Listen.rate(120));
        eq("rate: snaps to the nearest choice", 150, Listen.rate(140));
        eq("rate: nonsense becomes normal speed or the nearest", 180, Listen.rate(999));
        eq("start: saved sentence", 7, Listen.start(7, 20, false));
        eq("start: past the end starts again", 0, Listen.start(25, 20, false));
        eq("start: heard to the end starts again", 0, Listen.start(7, 20, true));
        eq("start: negative", 0, Listen.start(-1, 20, false));
    }

    static Ranker.Scored sc(long id, double score, boolean topic, String why) {
        Ranker.Scored s = new Ranker.Scored(); s.id = id; s.score = score; s.topicHit = topic; s.why = why; return s;
    }

    static void fitting() {
        java.util.List<Ranker.Scored> ranked = new java.util.ArrayList<>();
        java.util.Map<Long, Ranker.Cand> info = new java.util.HashMap<>();
        // 12 topic items, then 6 items without a topic: three good ones, an old one, a long one, one from a source turned down
        for (long i = 1; i <= 12; i++) { ranked.add(sc(i, 10 - i * 0.3, true, "Matches your topics: x")); info.put(i, new Ranker.Cand(3, 1, "Blog A")); }
        ranked.add(sc(101, 1.4, false, "Recent")); info.put(101L, new Ranker.Cand(4, 7, "Blog B"));
        ranked.add(sc(102, 1.2, false, "Like items you kept (parquet)")); info.put(102L, new Ranker.Cand(5, 8, "Blog C"));
        ranked.add(sc(103, 0.1, false, "No topic matched; shown by date")); info.put(103L, new Ranker.Cand(5, 9, "Blog D"));
        ranked.add(sc(104, 1.3, false, "Recent")); info.put(104L, new Ranker.Cand(30, 9, "Blog D"));
        ranked.add(sc(105, 1.35, false, "Recent")); info.put(105L, new Ranker.Cand(4, 10, "Blog E"));
        ranked.add(sc(106, 1.1, false, "Recent")); info.put(106L, new Ranker.Cand(4, 11, "Blog F"));
        java.util.Map<Long, Integer> pen = new java.util.HashMap<>();
        pen.put(10L, 3);

        java.util.List<Ranker.Pick> m = Ranker.mix(ranked, info, pen);
        eq("mix: every item appears once", ranked.size(), m.size());
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Ranker.Pick k : m) yes("mix: no repeated item", seen.add(k.id));
        eq("mix: the first four are the best ranked", java.util.Arrays.asList(1L, 2L, 3L, 4L), java.util.Arrays.asList(m.get(0).id, m.get(1).id, m.get(2).id, m.get(3).id));
        yes("mix: the fifth place is a surprise", m.get(4).surprise && m.get(4).id > 100);
        yes("mix: the tenth place is a surprise", m.get(9).surprise);
        int sur = 0;
        for (Ranker.Pick k : m) if (k.surprise) { sur++; yes("mix: a surprise matches no topic", k.id > 100); yes("mix: a surprise says why", k.why.startsWith("Surprise: matches none of your topics")); }
        eq("mix: three surprises fit the list (18 items / 5)", 3, sur);
        yes("mix: the best candidate comes first", m.get(4).id == 101L || m.get(4).id == 105L);
        yes("mix: an old no-topic item is no surprise", !byPick(m, 103).surprise);
        yes("mix: a long item is no surprise", !byPick(m, 104).surprise);
        yes("mix: a source turned down three times gives none", !byPick(m, 105).surprise && !byPick(m, 105).why.startsWith("Surprise"));
        yes("mix: a kept-like item says why", byPick(m, 102).why.contains("like items you kept") || !byPick(m, 102).surprise);
        yes("mix: regular items keep their own why", byPick(m, 3).why.equals("Matches your topics: x"));

        java.util.Map<Long, Integer> pen1 = new java.util.HashMap<>();
        pen1.put(7L, 2);
        boolean b101 = false;
        for (Ranker.Pick k : Ranker.mix(ranked, info, pen1)) if (k.id == 101 && k.surprise) b101 = true;
        yes("mix: two turn-downs lower a source but 1.4 - 1.6 is under the bar", !b101);

        eq("mix: a short list has no surprise slot", 0, countSurprises(Ranker.mix(ranked.subList(0, 4), info, pen)));
        eq("mix: empty input", 0, Ranker.mix(new java.util.ArrayList<Ranker.Scored>(), new java.util.HashMap<Long, Ranker.Cand>(), new java.util.HashMap<Long, Integer>()).size());
        eq("minutes: article is capped", 40, Ranker.minutes("article", 90));
        eq("minutes: article is at least one", 1, Ranker.minutes("article", 0));
        eq("minutes: video", 10, Ranker.minutes("video", 1));
        eq("minutes: podcast", 20, Ranker.minutes("audio", 1));
        FeedFilter.Filters f = new FeedFilter.Filters();
        yes("filters: none on is the plain Feed", f.plain());
        f.maxMins = 15;
        yes("filters: the time limit alone keeps surprises", f.plain());
        f.query = "x";
        yes("filters: a search is not the plain Feed", !f.plain());
    }
    static Ranker.Pick byPick(java.util.List<Ranker.Pick> l, long id) { for (Ranker.Pick k : l) if (k.id == id) return k; return null; }
    static int countSurprises(java.util.List<Ranker.Pick> l) { int n = 0; for (Ranker.Pick k : l) if (k.surprise) n++; return n; }

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

    static void json() {
        Object o = Json.parse(" {\"a\": [1, 2.5, -3e2, true, false, null, \"x\\ny\\u00e9\\\"\"], \"b\": {\"c\": {}}, \"d\": []} ");
        java.util.Map<String, Object> m = Json.obj(o);
        java.util.List<Object> a = Json.list(m.get("a"));
        eq("json: number", 1.0, a.get(0));
        eq("json: decimal", 2.5, a.get(1));
        eq("json: exponent", -300.0, a.get(2));
        eq("json: literals", java.util.Arrays.asList(true, false, null), a.subList(3, 6));
        eq("json: string escapes", "x\ny\u00e9\"", a.get(6));
        eq("json: nested empty object", 0, Json.obj(Json.obj(m.get("b")).get("c")).size());
        eq("json: empty array", 0, Json.list(m.get("d")).size());
        eq("json: whole numbers print without .0", "2026", Json.str(Json.obj(Json.parse("{\"y\": 2026}")), "y"));
        String[] bad = {"", "{", "[1,]", "{\"a\" 1}", "[1] x", "\"open", "{\"a\": tru}", "[\"\\u12\"]"};
        for (String b : bad) {
            try { Json.parse(b); yes("json: rejects <" + b + ">", false); } catch (IllegalArgumentException e) { yes("json: rejects <" + b + ">", true); }
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 80; i++) deep.append('[');
        try { Json.parse(deep.toString()); yes("json: deep nesting refused", false); } catch (IllegalArgumentException e) { yes("json: deep nesting refused", true); }
    }

    static void querySources() throws Exception {
        long now = 1794700000000L; // 2026-11-14
        String q = "claude code";
        eq("key: lower case and trimmed", "query:hn:claude code", QuerySource.key("hn", "  Claude   Code "));
        yes("kinds: all six are valid", QuerySource.valid("arxiv") && QuerySource.valid("hn") && QuerySource.valid("hf") && QuerySource.valid("reddit") && QuerySource.valid("github") && QuerySource.valid("s2") && !QuerySource.valid("rss"));
        yes("url arxiv", QuerySource.requestUrl("arxiv", "causal inference", now).startsWith("https://export.arxiv.org/api/query?search_query=all%3A%22causal+inference%22&sortBy=submittedDate"));
        yes("url hn", QuerySource.requestUrl("hn", q, now).startsWith("https://hn.algolia.com/api/v1/search_by_date?query=claude+code&tags=story&numericFilters=points%3E%3D20"));
        eq("url hf", "https://huggingface.co/api/daily_papers?limit=50", QuerySource.requestUrl("hf", "", now));
        eq("url reddit subreddit", "https://www.reddit.com/r/rstats/new.rss?limit=30", QuerySource.requestUrl("reddit", "r/rstats", now));
        yes("url reddit search", QuerySource.requestUrl("reddit", "power bi", now).startsWith("https://www.reddit.com/search.rss?q=power+bi&sort=new"));
        yes("url github: only repos created in the last 90 days", QuerySource.requestUrl("github", "tidymodels", now).contains("q=tidymodels+created%3A%3E2026-08-16&sort=stars"));
        yes("url s2: this and last year", QuerySource.requestUrl("s2", "causal inference", now).contains("&year=2025-2026&fields="));

        FeedParser.Feed ax = QuerySource.parse("arxiv", "causal inference", file("tests/fixtures/arxiv.xml"), now);
        eq("arxiv: three papers", 3, ax.items.size());
        eq("arxiv: feed title", "arXiv: causal inference", ax.title);
        yes("arxiv: https link and a summary", ax.items.get(0).link.startsWith("https://arxiv.org/abs/") && ax.items.get(0).body.length() > 50);
        yes("arxiv: newest first", ax.items.get(0).published >= ax.items.get(2).published);

        FeedParser.Feed hn = QuerySource.parse("hn", q, file("tests/fixtures/hn.json"), now);
        eq("hn: four stories", 4, hn.items.size());
        FeedParser.Item noUrl = null;
        for (FeedParser.Item i : hn.items) if (i.title.startsWith("Show HN: Mastra Factory")) noUrl = i;
        yes("hn: a story without a url links to its discussion", noUrl != null && noUrl.link.startsWith("https://news.ycombinator.com/item?id=") && noUrl.body.contains("points"));
        yes("hn: published time is in milliseconds", hn.items.get(0).published > 1700000000000L);

        FeedParser.Feed hf = QuerySource.parse("hf", "", file("tests/fixtures/hf.json"), now);
        eq("hf: three papers", 3, hf.items.size());
        yes("hf: paper link", hf.items.get(0).link.startsWith("https://huggingface.co/papers/2611."));
        FeedParser.Feed hfq = QuerySource.parse("hf", "policy", file("tests/fixtures/hf.json"), now);
        eq("hf: a query keeps only matching papers", 2, hfq.items.size());

        FeedParser.Feed gh = QuerySource.parse("github", "tidymodels", file("tests/fixtures/github.json"), now);
        eq("github: three repositories", 3, gh.items.size());
        eq("github: newest repository first", "ielbadisy/agentic-compactness-r-ai-2026", gh.items.get(0).title.split(":")[0]);
        yes("github: link is the repository page", gh.items.get(0).link.equals("https://github.com/ielbadisy/agentic-compactness-r-ai-2026"));
        yes("github: body carries the stars", gh.items.get(1).body.contains("1 stars") || gh.items.get(2).body.contains("7 stars"));

        FeedParser.Feed s2 = QuerySource.parse("s2", "causal machine learning", file("tests/fixtures/s2.json"), now);
        eq("s2: two papers", 2, s2.items.size());
        eq("s2: title is plain text", "Causal machine learning for policy evaluation", s2.items.get(0).title);
        yes("s2: abstract is escaped for HTML", s2.items.get(0).body.contains("how &amp; when"));
        yes("s2: missing abstract is said so", s2.items.get(1).body.contains("(no abstract available)"));
        eq("s2: the year stands in for a missing date", FeedParser.date("2025-01-01"), s2.items.get(1).published);

        FeedParser.Feed rd = QuerySource.parse("reddit", "r/rstats", file("tests/fixtures/reddit.xml"), now);
        eq("reddit: one post", 1, rd.items.size());
        yes("reddit: link and date", rd.items.get(0).link.endsWith("/tidymodels_churn/") && rd.items.get(0).published > 0);

        try { QuerySource.parse("github", "x", "{\"message\":\"API rate limit exceeded\"}".getBytes(StandardCharsets.UTF_8), now); yes("github: rate limit message", false); }
        catch (FeedParser.FeedException e) { eq("github: rate limit message", "GitHub says: API rate limit exceeded", e.getMessage()); }
        try { QuerySource.parse("s2", "x", "{\"message\": \"Too Many Requests.\", \"code\": \"429\"}".getBytes(StandardCharsets.UTF_8), now); yes("s2: message", false); }
        catch (FeedParser.FeedException e) { eq("s2: message", "Semantic Scholar says: Too Many Requests.", e.getMessage()); }
        try { QuerySource.parse("hn", "x", "<html>".getBytes(StandardCharsets.UTF_8), now); yes("hn: not json", false); }
        catch (FeedParser.FeedException e) { eq("hn: not json", "The answer was not valid JSON", e.getMessage()); }
        eq("s2: no matches is an empty list, not an error", 0, QuerySource.parse("s2", "x", "{\"total\": 0, \"offset\": 0}".getBytes(StandardCharsets.UTF_8), now).items.size());
    }

    static void watching() throws Exception {
        String robots = new String(file("tests/fixtures/robots.txt"), StandardCharsets.UTF_8);
        yes("robots: StashReader has its own group, so the * rules do not apply", Robots.allowed(robots, "StashReader/0.6", "/private/x"));
        yes("robots: its own Disallow applies", !Robots.allowed(robots, "StashReader/0.6", "/members/area"));
        yes("robots: a shared group reaches both agents", !Robots.allowed(robots, "OtherReader", "/members/"));
        yes("robots: an agent with no group uses *", !Robots.allowed(robots, "Somebody", "/private/x"));
        yes("robots: longest rule wins, Allow beats a shorter Disallow", Robots.allowed(robots, "Somebody", "/private/public-note"));
        yes("robots: prefix match", !Robots.allowed(robots, "Somebody", "/search?q=x"));
        yes("robots: wildcard with end anchor", !Robots.allowed(robots, "Somebody", "/data/a.json") && Robots.allowed(robots, "Somebody", "/data/a.json5"));
        yes("robots: a blocked bot does not block others", Robots.allowed(robots, "Somebody", "/blog/post"));
        yes("robots: the blocked agent is blocked everywhere", !Robots.allowed(robots, "BadBot/1", "/blog/post"));
        yes("robots: empty file allows everything", Robots.allowed("", "x", "/a") && Robots.allowed(null, "x", "/a"));
        yes("robots: empty Disallow allows", Robots.allowed("User-agent: *\nDisallow:\n", "x", "/a"));
        yes("robots: comments and spacing", !Robots.allowed("User-agent: *  # all\n  Disallow : /x # no\n", "x", "/x/y"));

        String listing = new String(file("tests/fixtures/listing.html"), StandardCharsets.UTF_8);
        java.util.List<PageWatch.Link> ls = PageWatch.links(listing, "https://example.org/blog/");
        eq("links: article links only", 3, ls.size());
        eq("links: first is the new method post", "https://example.org/posts/2026/new-method-for-uplift/", ls.get(0).url);
        eq("links: title", "A new method for uplift modelling", ls.get(0).title);
        eq("links: www host is the same site, fragment cut, query kept", "https://www.example.org/posts/2026/older-post-on-trees?utm_source=x", ls.get(1).url);
        yes("links: markup in a title is stripped", ls.get(2).title.equals("Third post, with markup in its title"));
        eq("links: nothing on a page with no anchors", 0, PageWatch.links("<p>none</p>", "https://example.org/").size());

        PageWatch.Meta mt = PageWatch.meta(new String(file("tests/fixtures/article-page.html"), StandardCharsets.UTF_8));
        eq("meta: Open Graph title wins", "A new method for uplift & response modelling", mt.title);
        eq("meta: Open Graph description wins", "We compare two uplift learners on a retail campaign.", mt.description);
        eq("meta: published time", FeedParser.date("2026-11-12T08:30:00+00:00"), mt.published);
        PageWatch.Meta plain = PageWatch.meta("<html><head><title>Only a title</title><meta name=\"description\" content=\"A plain one\"></head><time datetime=\"2026-01-05\"></time></html>");
        eq("meta: fallback title", "Only a title", plain.title);
        eq("meta: fallback description", "A plain one", plain.description);
        eq("meta: time element date", FeedParser.date("2026-01-05"), plain.published);

        java.util.List<PageWatch.Link> sm = PageWatch.sitemap(file("tests/fixtures/sitemap.xml"));
        eq("sitemap: pages that look like articles", 3, sm.size());
        eq("sitemap: newest first, http upgraded", "https://example.org/blog/newest-post-on-causal-forests", sm.get(0).url);
        yes("sitemap: dates read", sm.get(0).date > sm.get(1).date && sm.get(1).date > sm.get(2).date);
        try { PageWatch.sitemap(file("tests/fixtures/sitemap-index.xml")); yes("sitemap index refused", false); }
        catch (FeedParser.FeedException e) { yes("sitemap index refused with advice", e.getMessage().startsWith("This is a sitemap index")); }
        try { PageWatch.sitemap(file("tests/fixtures/doctype.xml")); yes("sitemap doctype refused", false); }
        catch (FeedParser.FeedException e) { yes("sitemap doctype refused", e.getMessage().startsWith("Refused")); }
        eq("title from url", "newest post on causal forests", PageWatch.titleFromUrl("https://example.org/blog/newest-post-on-causal-forests/"));
        eq("title from url with extension", "middle post", PageWatch.titleFromUrl("https://example.org/blog/middle-post.html"));
    }

    static void recommendations() throws Exception {
        long now = 1794700000000L;
        eq("paper id: arxiv abs", "ARXIV:2611.12345", QuerySource.paperId("https://arxiv.org/abs/2611.12345v2"));
        eq("paper id: arxiv pdf", "ARXIV:2611.1234", QuerySource.paperId("https://arxiv.org/pdf/2611.1234.pdf"));
        eq("paper id: old arxiv id", "ARXIV:stat.ML/0601001", QuerySource.paperId("https://arxiv.org/abs/stat.ML/0601001"));
        eq("paper id: hugging face papers", "ARXIV:2611.26232", QuerySource.paperId("https://huggingface.co/papers/2611.26232"));
        eq("paper id: semantic scholar", "0123456789abcdef0123456789abcdef01234567", QuerySource.paperId("https://www.semanticscholar.org/paper/Some-Title/0123456789ABCDEF0123456789ABCDEF01234567"));
        eq("paper id: doi", "DOI:10.1000/xyz123", QuerySource.paperId("https://doi.org/10.1000/xyz123"));
        eq("paper id: an ordinary blog is not a paper", "", QuerySource.paperId("https://juliasilge.com/blog/tune/"));
        eq("paper id: empty", "", QuerySource.paperId(null));
        String body = QuerySource.recsBody(java.util.Arrays.asList("ARXIV:2611.1", "abc"), java.util.Arrays.asList("DOI:10.1/a\"b"));
        java.util.Map<String, Object> parsed = Json.obj(Json.parse(body));
        eq("recs body: positives", java.util.Arrays.asList("ARXIV:2611.1", "abc"), Json.list(parsed.get("positivePaperIds")));
        eq("recs body: negatives escaped", java.util.Arrays.asList("DOI:10.1/a\"b"), Json.list(parsed.get("negativePaperIds")));
        eq("recs url", "https://api.semanticscholar.org/recommendations/v1/papers/?limit=30&fields=title,abstract,year,url,publicationDate,citationCount", QuerySource.requestUrl("recs", "", now));
        eq("citations url", "https://api.semanticscholar.org/graph/v1/paper/ARXIV%3A2611.1/citations?limit=30&fields=title,abstract,year,url,publicationDate,citationCount", QuerySource.citationsUrl("ARXIV:2611.1"));
        eq("watch url is the page itself", "https://example.org/blog/", QuerySource.requestUrl("watch", " https://example.org/blog/ ", now));
        eq("intervals", 24 * 3600000L, QuerySource.minInterval("sitemap"));
        eq("intervals: searches", 3 * 3600000L, QuerySource.minInterval("hn"));
        yes("recs needs no query, watch does", !QuerySource.needsQuery("recs") && QuerySource.needsQuery("watch"));

        FeedParser.Feed r = QuerySource.parseRecommendations(file("tests/fixtures/recs.json"), "Recommended from papers you kept: A, B");
        eq("recs: two papers, the one without an id skipped", 2, r.items.size());
        yes("recs: because is stored on each item", r.items.get(0).because.startsWith("Recommended from papers you kept"));
        eq("recs: title", "Conformal prediction for treatment effects", r.items.get(0).title);
        FeedParser.Feed c = QuerySource.parseCitations(file("tests/fixtures/citations.json"), "Cites \"Seed\"", now);
        eq("citations: only this and last year", 2, c.items.size());
        yes("citations: reason", c.items.get(0).because.equals("Cites \"Seed\""));
        try { QuerySource.parseRecommendations("{\"message\":\"Too Many Requests\"}".getBytes(StandardCharsets.UTF_8), "x"); yes("recs rate limit", false); }
        catch (FeedParser.FeedException e) { eq("recs rate limit", "Semantic Scholar says: Too Many Requests", e.getMessage()); }

        long day = 86400000L;
        java.util.List<Ranker.Doc> docs = new java.util.ArrayList<>();
        docs.add(new Ranker.Doc(1, "Plain item", "nothing special", now - day));
        docs.add(new Ranker.Doc(2, "Recommended paper", "nothing special", now - 5 * day, "Recommended from papers you kept: Seed"));
        java.util.List<Ranker.Scored> rs = Ranker.rank(docs, new java.util.ArrayList<Ranker.Topic>(), new java.util.ArrayList<String>(), new java.util.ArrayList<String>(), now);
        eq("because: a recommendation beats a slightly fresher plain item", 2L, rs.get(0).id);
        yes("because: the reason leads the why line", byId(rs, 2).why.startsWith("Recommended from papers you kept: Seed"));
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
        yes("starter has about 15 to 35 feeds", n >= 15 && n <= 35);
        yes("starter includes prokulski", s.contains("https://blog.prokulski.science/index.xml"));
        Matcher t = Pattern.compile("\"name\"\\s*:\\s*\"[^\"]+\"").matcher(s);
        int names = 0; while (t.find()) names++;
        eq("every starter feed has a name", n, names);
    }
}
