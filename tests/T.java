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
        authors();
        shareAndRecall();
        gemini();
        feedFilter();
        swipes();
        starter();
        discovery();
        pageText();
        ranking();
        learner();
        fitting();
        clusters();
        passages();
        feedMix();
        learnFast();
        autoNotes();
        related();
        quietFeeds();
        weekNote();
        listening();
        s2key();
        openAlex();
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
        java.util.Map<String, Object> vcfg = Json.obj(Json.obj(vid).get("generationConfig"));
        eq("gemini: video is read at low resolution", "MEDIA_RESOLUTION_LOW", Json.str(vcfg, "mediaResolution"));
        yes("gemini: video request keeps the structured output", Json.str(vcfg, "responseMimeType").equals("application/json") && Json.obj(vcfg.get("responseSchema")) != null);
        yes("gemini: video notes come mainly from the speech", vj.contains("Take the notes mainly from what is said in the video."));
        yes("gemini: an article asks for neither", !aj.contains("mediaResolution") && !aj.contains("said in the video"));
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 50000; i++) big.append('x');
        yes("gemini: article text is capped", Gemini.notesRequest("", "T", "", big.toString()).length() < Gemini.MAX_TEXT + 3000);
        String pj = Gemini.pathRequest("tidymodels", "Power BI", "- A (S)\n  highlight: \"x\"\n");
        yes("gemini: path request is JSON and never mentions notes", Json.obj(Json.parse(pj)) != null && !pj.toLowerCase().contains("my note"));
        yes("gemini: path asks for the schema and uses no paid tool", pj.contains("\"responseSchema\"") && !pj.contains("google_search") && !pj.contains("\"tools\"") && pj.contains("WHAT I KEPT"));
        String ps = Gemini.pathRequest("Meshtastic", "", "");
        yes("gemini: a topic with nothing kept says so", ps.contains("I have not kept anything on it yet") && !ps.contains("WHAT I KEPT"));
        yes("gemini: path asks for a YouTube search phrase and no YouTube links", ps.contains("type into YouTube") && ps.contains("no YouTube links"));
        String one = Gemini.notesRequest("t", "T", "", "Some text", 1);
        yes("gemini: notes on their own ask for 1 question", one.contains("questions holds 1 recall question,") && !one.contains("3 recall questions"));
        yes("gemini: Make notes still asks for 3", aj.contains("questions holds 3 recall questions"));
        yes("gemini: ping request is JSON", Json.obj(Json.parse(Gemini.pingRequest())) != null);

        // real answers recorded from the live API
        eq("gemini: ping answer", "OK", Gemini.replyText(200, text("tests/fixtures/gemini-ping.json")).trim());
        Gemini.Notes nv = Gemini.parseNotes(Gemini.replyText(200, text("tests/fixtures/gemini-video.json")));
        yes("gemini: video notes have the sections", nv.md.contains("## Key ideas") && nv.md.contains("## Quotes") && nv.md.contains("## Try next"));
        yes("gemini: video notes have questions with answers", nv.questions.size() == 3 && !nv.questions.get(0)[0].isEmpty() && !nv.questions.get(0)[1].isEmpty());
        Gemini.Notes na = Gemini.parseNotes(Gemini.replyText(200, text("tests/fixtures/gemini-article.json")));
        yes("gemini: article notes read", na.md.contains("## Key ideas") && na.questions.size() == 3);
        Gemini.Notes n1 = Gemini.parseNotes(Gemini.replyText(200, text("tests/fixtures/gemini-video-1q.json")));
        yes("gemini: video at low resolution with 1 question (live answer)", n1.md.contains("## Key ideas") && n1.questions.size() == 1);

        // errors: the wrong-key answer was recorded; the others are written by hand
        String bad = thrown(() -> Gemini.replyText(400, text("tests/fixtures/gemini-badkey.json")));
        yes("gemini: wrong key says so", bad.startsWith("Gemini did not accept the key") && bad.contains("API key not valid"));
        String quota = thrown(() -> Gemini.replyText(429, "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\",\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        yes("gemini: quota error is a calm sentence", quota.contains("free limit"));
        boolean limit = false;
        try { Gemini.replyText(429, text("tests/fixtures/gemini-ground-429.json")); } catch (Gemini.Limit e) { limit = true; } catch (Gemini.GeminiException e) { limit = false; }
        yes("gemini: a real HTTP 429 answer is a Limit", limit);
        yes("gemini: unknown model", thrown(() -> Gemini.replyText(404, "{\"error\":{\"code\":404,\"message\":\"not found\"}}")).contains("needs an update"));
        yes("gemini: private video", thrown(() -> Gemini.replyText(400, "{\"error\":{\"code\":400,\"message\":\"Failed to process YouTube video\"}}")).contains("must be public"));
        yes("gemini: html error page", thrown(() -> Gemini.replyText(502, "<html>Bad gateway</html>")).startsWith("Gemini answered HTTP 502"));
        yes("gemini: blocked prompt", thrown(() -> Gemini.replyText(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).contains("SAFETY"));
        yes("gemini: no candidates", thrown(() -> Gemini.replyText(200, "{\"candidates\":[]}")).contains("no text"));

        // tolerant parsing
        eq("gemini: code fences around the JSON are stripped", "x", Gemini.parseNotes("```json\n{\"notes_md\":\"x\",\"questions\":[]}\n```").md);
        yes("gemini: empty notes are refused", thrown(() -> Gemini.parseNotes("{\"notes_md\":\"\",\"questions\":[]}")).contains("empty"));
        yes("gemini: not JSON is refused", thrown(() -> Gemini.parseNotes("Sure! Here are your notes")).contains("could not be read"));

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

    static void swipes() {
        final java.util.List<String> log = new java.util.ArrayList<>();
        final java.util.Set<Long> read = new java.util.HashSet<>(); // ids that are no longer unread
        Swipes s = new Swipes(new Swipes.Store() {
            @Override public java.util.List<Long> mark(java.util.List<Long> ids, boolean keep) {
                java.util.List<Long> out = new java.util.ArrayList<>();
                for (long id : ids) if (read.add(id)) { out.add(id); log.add((keep ? "keep " : "less ") + id); }
                return out;
            }
            @Override public void restore(long id) { read.remove(id); log.add("restore " + id); }
            @Override public void export(long id) { log.add("export " + id); }
            @Override public void dropText(long id) { log.add("drop " + id); }
            @Override public void surpriseLess(long id) { log.add("surprise " + id); }
        });
        eq("swipe ids: list, spaces, junk and repeats", java.util.Arrays.asList(3L, 4L), Swipes.parse(" 3,x, 4 ,3,-2,0,"));
        yes("swipe ids: empty or null", Swipes.parse("").isEmpty() && Swipes.parse(null).isEmpty());

        s.swipe(Swipes.parse("1,2"), true, 0);
        eq("swipe right: marked at once, no file yet", "[keep 1, keep 2]", log.toString());
        log.clear(); s.undo();
        eq("swipe right undone: restored, no file", "[restore 1, restore 2]", log.toString());
        log.clear(); s.commit(); s.undo();
        eq("swipe: nothing left after undo", "[]", log.toString());

        s.swipe(Swipes.parse("1,2"), true, 0); log.clear(); s.commit();
        eq("swipe right final: every copy gets its file", "[export 1, export 2]", log.toString());
        log.clear(); s.commit(); s.undo();
        eq("swipe: a final swipe cannot be undone or repeated", "[]", log.toString());

        s.swipe(Swipes.parse("5"), false, 5);
        eq("swipe left: marked at once, nothing erased or counted yet", "[less 5]", log.toString());
        log.clear(); s.undo();
        eq("swipe left on a surprise undone: not counted", "[restore 5]", log.toString());
        s.swipe(Swipes.parse("5,6"), false, 5); log.clear();
        s.swipe(Swipes.parse("7"), true, 0);
        eq("swipe: the next swipe makes the one before final", "[drop 5, drop 6, surprise 5, keep 7]", log.toString());
        log.clear(); s.undo();
        eq("swipe: only the last swipe is undone", "[restore 7]", log.toString());

        log.clear(); s.swipe(Swipes.parse("1,8"), false, 1); s.commit();
        eq("swipe: an item no longer unread is left alone", "[less 8, drop 8]", log.toString());
        log.clear(); s.swipe(Swipes.parse("1"), true, 0); s.commit();
        eq("swipe: nothing changed, nothing to finish", "[]", log.toString());
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
        eq("filter: count of filters on, the sort not counted", 4, all.count());
        FeedFilter.Filters newest = new FeedFilter.Filters(); newest.newest = true;
        yes("filter: the Newest sort alone is no filter and keeps the plain Feed", newest.count() == 0 && newest.plain() && FeedFilter.accept(newest, "", false, 1, "a", "b"));
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
        java.util.List<Clusters.Item> big = new java.util.ArrayList<>();
        big.add(new Clusters.Item(101, "AI model helps data teams", "https://a.example/1", "A", now - 3600000L));
        big.add(new Clusters.Item(102, "AI tools for data teams", "https://b.example/2", "B", now - 3600000L));
        for (int i = 0; i < 60; i++) big.add(new Clusters.Item(200 + i, "AI and data teams, story " + (1000 + i), "https://c.example/" + i, "C", now - 7200000L));
        boolean apart = true;
        for (java.util.List<Clusters.Item> c : Clusters.group(big, now)) { java.util.Set<Long> x = new java.util.HashSet<>(); for (Clusters.Item i : c) x.add(i.id); if (x.contains(101L) && x.contains(102L)) apart = false; }
        yes("clusters: titles that share only common words are different stories", apart);
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

    static void openAlex() throws Exception {
        long now = 1794700000000L; // 2026-11-14
        eq("oa: label", "OpenAlex", QuerySource.label(QuerySource.OA));
        String u = QuerySource.requestUrl("openalex", " causal  inference ", now);
        eq("oa url: the last 365 days up to today, newest first", "https://api.openalex.org/works?search=causal+inference&filter=from_publication_date:2025-11-14,to_publication_date:2026-11-14"
            + "&sort=publication_date:desc&per_page=25&select=id,doi,title,publication_date,cited_by_count,abstract_inverted_index,primary_location", u);
        yes("oa url: no mailto", !u.contains("mailto"));
        eq("oa: asks for JSON", "application/json", QuerySource.accept("openalex"));
        eq("oa: searched at most every three hours", 3 * 3600000L, QuerySource.minInterval("openalex"));
        yes("oa: needs a query", QuerySource.needsQuery("openalex"));

        String key = "aB3dEfGh1jKlMnOpQrStUv"; // made up
        yes("oa key: letters and digits are valid", QuerySource.validOaKey(key) && QuerySource.validOaKey("abcdefghij"));
        StringBuilder longKey = new StringBuilder(); for (int i = 0; i < 101; i++) longKey.append('a');
        yes("oa key: too short, too long, spaces and signs are not", !QuerySource.validOaKey("abcdefghi") && !QuerySource.validOaKey(longKey.toString())
            && !QuerySource.validOaKey("abcde fghijk") && !QuerySource.validOaKey("abcdefghij&x=1") && !QuerySource.validOaKey("") && !QuerySource.validOaKey(null));
        yes("oa host: the API address", QuerySource.isOaHost(u));
        yes("oa host: the key never goes to another host", !QuerySource.isOaHost("https://evil.example/https://api.openalex.org/") && !QuerySource.isOaHost("https://api.openalex.org.evil.example/works")
            && !QuerySource.isOaHost("http://api.openalex.org/works") && !QuerySource.isOaHost("https://openalex.org/W1") && !QuerySource.isOaHost(null));
        eq("oa key: added to the OpenAlex address", u + "&api_key=" + key, QuerySource.withOaKey(u, key));
        eq("oa key: starts the query when there is none", "https://api.openalex.org/works/W1?api_key=" + key, QuerySource.withOaKey("https://api.openalex.org/works/W1", key));
        eq("oa key: not added elsewhere", "https://api.semanticscholar.org/x?a=1", QuerySource.withOaKey("https://api.semanticscholar.org/x?a=1", key));
        eq("oa key: no key, address unchanged", u, QuerySource.withOaKey(u, ""));
        String shown = QuerySource.redact("failed to connect to " + u + "&api_key=" + key + " after 15000ms");
        yes("oa key: blanked in error messages", !shown.contains(key) && shown.contains("api_key=... after 15000ms"));
        eq("oa redact: other messages stay", "The server answered HTTP 403", QuerySource.redact("The server answered HTTP 403"));

        java.util.Map<String, Object> idx = Json.obj(Json.parse("{\"the\": [0, 4], \"cat\": [1], \"sat\": [2], \"on\": [3], \"mat\": [5], \"odd\": \"x\"}"));
        eq("oa abstract: words go back to their places", "the cat sat on the mat", QuerySource.abstractText(idx));
        eq("oa abstract: none", "", QuerySource.abstractText(null));

        // a saved real answer, cut to three works
        FeedParser.Feed f = QuerySource.parse("openalex", "causal machine learning", file("tests/fixtures/openalex.json"), now);
        eq("oa: three works", 3, f.items.size());
        eq("oa: feed title", "OpenAlex: causal machine learning", f.title);
        FeedParser.Item doi = null, noDoi = null, noAbstract = null;
        for (FeedParser.Item i : f.items) {
            if (i.title.startsWith("Machine Learning for Causal Inference")) doi = i;
            if (i.title.startsWith("Exploration ")) noDoi = i;
            if (i.title.startsWith("Learning by Imagining")) noAbstract = i;
        }
        yes("oa: all three are read", doi != null && noDoi != null && noAbstract != null);
        eq("oa: the DOI is the link", "https://doi.org/10.25949/35134390", doi.link);
        yes("oa: the abstract is rebuilt, with the citations", doi.body.startsWith("<p>Estimates of treatment effects and preferences inform decisions about healthcare") && doi.body.endsWith("</p><p>0 citations.</p>"));
        eq("oa: publication date", FeedParser.date("2026-11-27"), doi.published);
        eq("oa: without a DOI the landing page is the link", "https://hal.science/tel-05482693", noDoi.link);
        yes("oa: a missing abstract is said so", noAbstract.body.contains("(no abstract available)"));
        eq("oa: a DOI link can seed recommendations", "DOI:10.25949/35134390", QuerySource.paperId(doi.link));

        FeedParser.Feed bare = QuerySource.parse("openalex", "x", "{\"results\":[{\"id\":\"https://openalex.org/W42\",\"doi\":null,\"title\":\"A <i>bare</i> work\",\"publication_date\":\"2026-01-02\",\"cited_by_count\":7,\"abstract_inverted_index\":{\"a<b\":[0]},\"primary_location\":null},{\"id\":\"https://openalex.org/W43\",\"title\":null}]}".getBytes(StandardCharsets.UTF_8), now);
        eq("oa: a work without a title is skipped", 1, bare.items.size());
        eq("oa: the OpenAlex page is the last resort", "https://openalex.org/W42", bare.items.get(0).link);
        eq("oa: title is plain text", "A bare work", bare.items.get(0).title);
        eq("oa: abstract is escaped for HTML", "<p>a&lt;b</p><p>7 citations.</p>", bare.items.get(0).body);
        eq("oa: no matches is an empty list, not an error", 0, QuerySource.parse("openalex", "x", "{\"meta\":{\"count\":0},\"results\":[]}".getBytes(StandardCharsets.UTF_8), now).items.size());
        try { QuerySource.parse("openalex", "x", "{\"error\":\"Invalid query\",\"message\":\"Bad filter.\"}".getBytes(StandardCharsets.UTF_8), now); yes("oa: message", false); }
        catch (FeedParser.FeedException e) { eq("oa: message", "OpenAlex says: Bad filter.", e.getMessage()); }
        try { QuerySource.parse("openalex", "x", "[]".getBytes(StandardCharsets.UTF_8), now); yes("oa: odd shape", false); }
        catch (FeedParser.FeedException e) { eq("oa: odd shape", "OpenAlex answered in an unexpected shape", e.getMessage()); }
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
        eq("language: English", "en", Listen.language("This is an ordinary English text about data and models and nothing else at all."));
        eq("language: Polish", "pl", Listen.language("Zażółć gęślą jaźń: to jest zwykły tekst po polsku o danych, modelach i łączeniu źródeł."));
        eq("language: too short to tell, so English", "en", Listen.language("Zażółć"));
        eq("code blocks are dropped", " After. ", Listen.dropCode("<pre><code>x = 1\nprint(x)</code></pre> After. ").replaceAll("^ +", " ").replaceAll("^ ", " "));
        yes("code: text around a code block stays", Listen.dropCode("<p>Before</p><PRE class=\"a\">code\nmore</PRE><p>After</p>").contains("Before") && Listen.dropCode("<p>Before</p><PRE class=\"a\">code\nmore</PRE><p>After</p>").contains("After") && !Listen.dropCode("<p>Before</p><PRE class=\"a\">code\nmore</PRE><p>After</p>").contains("code"));
        yes("links are read as the word link", Listen.sentences("", true, "See https://example.org/a?b=1 for details about it all.").get(0).equals("See link for details about it all."));
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

        // the Newest sort: the scored items go back into date order (here: highest id is newest) and surprises still mix in
        java.util.List<Ranker.Doc> byDate = new java.util.ArrayList<>();
        for (int i = ranked.size() - 1; i >= 0; i--) byDate.add(new Ranker.Doc(ranked.get(i).id, "t", "x", ranked.get(i).id));
        java.util.List<Ranker.Pick> nm = Ranker.mix(Ranker.inOrder(ranked, byDate), info, pen);
        eq("newest mix: every item appears once", ranked.size(), nm.size());
        yes("newest mix: the fifth place is still a surprise", nm.get(4).surprise);
        java.util.List<Long> regular = new java.util.ArrayList<>(), sorted = new java.util.ArrayList<>();
        for (Ranker.Pick k : nm) if (!k.surprise) regular.add(k.id);
        sorted.addAll(regular);
        java.util.Collections.sort(sorted, java.util.Collections.reverseOrder());
        eq("newest mix: the other cards stay newest first", sorted, regular);

        java.util.Map<Long, Integer> pen1 = new java.util.HashMap<>();
        pen1.put(7L, 2);
        boolean b101 = false;
        for (Ranker.Pick k : Ranker.mix(ranked, info, pen1)) if (k.id == 101 && k.surprise) b101 = true;
        yes("mix: two turn-downs lower a source but 1.4 - 1.6 is under the bar", !b101);

        java.util.List<Ranker.Scored> ex = new java.util.ArrayList<>();
        java.util.Map<Long, Ranker.Cand> exi = new java.util.HashMap<>();
        for (long i = 1; i <= 4; i++) { ex.add(sc(i, 9, true, "Matches your topics: x")); exi.put(i, new Ranker.Cand(3, 1, "Blog A")); }
        ex.add(sc(201, 1.4, false, "Recent")); exi.put(201L, new Ranker.Cand(4, 7, "Known Blog"));
        Ranker.Scored nw = sc(202, 1.1, false, "Recent"); nw.unknown = true; ex.add(nw); exi.put(202L, new Ranker.Cand(4, 8, "New Blog"));
        java.util.List<Ranker.Pick> em = Ranker.mix(ex, exi, pen);
        eq("explore: the surprise slot goes to a source the app knows little about", 202L, em.get(4).id);
        eq("explore: and says so", "Surprise: matches none of your topics, and the app knows little about New Blog yet", em.get(4).why);
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
        yes("less like this: the reason says so", byId(rl, 3).why.contains("swiped left"));

        java.util.List<Ranker.Scored> ph = Ranker.rank(java.util.Arrays.asList(
            new Ranker.Doc(10, "Business intelligence", "power plants and a bi weekly meeting", now),
            new Ranker.Doc(11, "Dashboards", "learning Power BI step by step", now)), java.util.Arrays.asList(new Ranker.Topic("Power BI", 1)), none, none, now);
        eq("phrase: the exact phrase beats two scattered words", 11L, ph.get(0).id);
        java.util.List<Ranker.Scored> sc = Ranker.rank(java.util.Arrays.asList(new Ranker.Doc(20, "Claude is an assistant", "nothing about the other word", now)), java.util.Arrays.asList(new Ranker.Topic("Claude Code", 3)), none, none, now);
        yes("phrase: a topic needs all of its words", !sc.get(0).why.contains("Matches"));
        eq("empty input gives an empty list", 0, Ranker.rank(new java.util.ArrayList<Ranker.Doc>(), t1, none, none, now).size());
    }

    static void learner() {
        long now = 1794700000000L, day = 86400000L;
        java.util.List<Ranker.Topic> none = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Double>> pool = new java.util.ArrayList<>();
        String[] feed = {"Weekly data roundup", "New data on rainfall", "Football scores tonight", "Cheap flights to Rome", "Data and duckdb tricks", "Garden tools on sale"};
        for (int i = 0; i < feed.length; i++) pool.add(Learner.features(feed[i], "", 10 + i, "", none));

        // decay: the same kept item counts less when it was kept half a year ago
        java.util.List<Learner.Label> fresh = new java.util.ArrayList<>(), old = new java.util.ArrayList<>();
        for (java.util.List<Learner.Label> l : java.util.Arrays.asList(fresh, old)) {
            l.add(new Learner.Label("Pasta at home", "", 1, "", now, true));
            l.add(new Learner.Label("Rome by train", "", 2, "", now, true));
            l.add(new Learner.Label("Birds in winter", "", 3, "", now, true));
        }
        fresh.add(new Learner.Label("Duckdb tricks", "", 4, "", now, true));
        fresh.add(new Learner.Label("More duckdb", "", 4, "", now, true));
        old.add(new Learner.Label("Duckdb tricks", "", 4, "", now - 180 * day, true));
        old.add(new Learner.Label("More duckdb", "", 4, "", now - 180 * day, true));
        double wf = Learner.build(fresh, none, pool, now).weight("w:duckdb"), wo = Learner.build(old, none, pool, now).weight("w:duckdb");
        yes("learner: a kept word raises items", wf > 0);
        yes("learner: an interest from half a year ago weighs less", wo < wf);

        // a word as common in kept items as in items swiped left weighs about nothing
        java.util.List<Learner.Label> both = new java.util.ArrayList<>();
        String[] k = {"data tools for teams", "data in schools", "data and rivers"}, l = {"data about celebrities", "data on lotteries", "data of horoscopes"};
        for (String x : k) both.add(new Learner.Label(x, "", 1, "", now, true));
        for (String x : l) both.add(new Learner.Label(x, "", 2, "", now, false));
        yes("learner: a word in both kept and less items weighs about 0", Math.abs(Learner.build(both, none, pool, now).weight("w:data")) < 0.05);
        yes("learner: a word seen once among several labels does not steer", Learner.build(both, none, pool, now).weight("w:river") == 0);

        // a source you keep swiping left sinks, even with words it has never used before
        java.util.List<Learner.Label> src = new java.util.ArrayList<>();
        String[] gone = {"Celebrity news", "Royal wedding gossip", "Reality show finale"};
        for (String x : gone) src.add(new Learner.Label(x, "", 12, "", now - day, false));
        src.add(new Learner.Label("Rivers of Europe", "", 20, "", now, true));
        src.add(new Learner.Label("Mountain trails", "", 21, "", now, true));
        src.add(new Learner.Label("Lakes in autumn", "", 22, "", now, true));
        Learner.Model ms = Learner.build(src, none, pool, now);
        Learner.Result r = ms.score(Learner.features("Something completely different", "", 12, "", none), "Gossip Daily");
        yes("learner: a source swiped left lowers its new items", r.score < -0.5);
        eq("learner: the reason names the source", "Lowered: you often swipe away items from Gossip Daily", r.down);

        // caps: however much matches, the learned part stays within bounds
        java.util.List<Learner.Label> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(new Learner.Label("alpha beta gamma delta epsilon zeta eta theta", "", 30, "video", now, true));
        Learner.Result big = Learner.build(many, none, pool, now).score(Learner.features("alpha beta gamma delta epsilon zeta eta theta", "", 30, "video", none), "X");
        yes("learner: the total is capped", big.score <= Learner.TOTAL_CAP + 1e-9);
        yes("learner: many words together are capped", big.score <= Learner.WORDS_CAP + Learner.SOURCE_CAP + Learner.KIND_CAP + 1e-9);
        eq("learner: when the words weigh more than the source, the words are named", "Like items you kept (alpha, beta, delta)", big.up);
        eq("learner: nothing learned adds nothing", 0.0, Learner.build(new java.util.ArrayList<Learner.Label>(), none, pool, now).score(pool.get(0), "X").score);

        // quiet signals: cards you scroll past teach only about their source, and only relative to how much of the Feed it fills
        java.util.List<java.util.Map<String, Double>> qp = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) qp.add(Learner.features("Item " + i, "", i < 5 ? 50 : 51 + i, "", none)); // source 50 fills half the Feed
        java.util.List<Learner.Label> q1 = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) q1.add(new Learner.Label("Skipped thing " + i, "", 60, "", now, false, Learner.SEEN_WEIGHT, true));
        for (int i = 0; i < 3; i++) q1.add(new Learner.Label("Kept " + i, "", 70 + i, "", now, true));
        Learner.Model qm = Learner.build(q1, none, qp, now);
        yes("quiet: a source you always scroll past sinks", qm.weight("s:60") < -0.5);
        yes("quiet: scrolled-past cards teach no words", qm.weight("w:skipped") == 0);
        java.util.List<Learner.Label> q2 = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) q2.add(new Learner.Label("Seen " + i, "", i < 5 ? 50 : 51 + i, "", now, false, Learner.SEEN_WEIGHT, true));
        yes("quiet: a source skipped as often as it appears loses little", Learner.build(q2, none, qp, now).weight("s:50") > -0.3);
        yes("quiet: one look is not enough to act on", Learner.build(q2, none, qp, now).weight("s:52") == 0);
        java.util.List<Learner.Label> q3 = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) q3.add(new Learner.Label("Long read about soil " + i, "", 80, "", now, true, Learner.READ_WEIGHT, false));
        Learner.Model rm = Learner.build(q3, none, qp, now);
        yes("quiet: articles you read for a while raise their source and words", rm.weight("s:80") > 0 && rm.weight("w:soil") > 0);
        yes("quiet: heard counts quiet signals", Math.abs(rm.heard(80) - 8 * Learner.READ_WEIGHT) < 1e-9 && rm.heard(99) == 0);

        // in the ranking: an item from a source you keep rises above an equally fresh one
        java.util.List<Ranker.Doc> docs = new java.util.ArrayList<>();
        docs.add(new Ranker.Doc(1, "Notes on soil", "x", now, "", 40, "Blog A", ""));
        docs.add(new Ranker.Doc(2, "Notes on clay", "x", now, "", 41, "Blog B", ""));
        for (int i = 3; i <= 6; i++) docs.add(new Ranker.Doc(i, "Other note " + i, "x", now - day, "", 40 + i, "Blog " + i, ""));
        java.util.List<Learner.Label> kb = new java.util.ArrayList<>();
        kb.add(new Learner.Label("Hedges", "", 41, "", now, true)); kb.add(new Learner.Label("Ponds", "", 41, "", now, true));
        java.util.List<Ranker.Scored> rs = Ranker.rank(docs, none, kb, now);
        eq("learner: the kept source comes first", 2L, rs.get(0).id);
        yes("learner: and says why", rs.get(0).why.contains("You keep a lot from Blog B"));

        // a good and a bad article on the same subject from the same source: author and length decide
        java.util.List<java.util.Map<String, Double>> tp = new java.util.ArrayList<>(pool);
        tp.add(Learner.features("A tidyverse article", "", 7, "", none, "Ann Good", 15));
        tp.add(Learner.features("A tidyverse article", "", 7, "", none, "Bob Thin", 2));
        java.util.List<Learner.Label> tv = new java.util.ArrayList<>();
        tv.add(new Learner.Label("A tidyverse article", "", 7, "", now, true).by("Ann Good", 15));
        tv.add(new Learner.Label("A tidyverse article", "", 7, "", now, false).by("Bob Thin", 2));
        Learner.Model tm = Learner.build(tv, none, tp, now);
        yes("learner: the shared source weighs about 0", Math.abs(tm.weight("s:7")) < 1e-9);
        yes("learner: the shared word weighs about 0", Math.abs(tm.weight("w:tidyverse")) < 1e-9);
        yes("learner: the kept author is up, the other down", tm.weight("a:ann good") > 0 && tm.weight("a:bob thin") < 0);
        yes("learner: the kept length is up, the other down", tm.weight("len:long") > 0 && tm.weight("len:short") < 0);
        Learner.Result good = tm.score(Learner.features("A tidyverse article", "", 7, "", none, "Ann Good", 20), "Blog", "Ann Good");
        Learner.Result bad = tm.score(Learner.features("A tidyverse article", "", 7, "", none, "Bob Thin", 1), "Blog", "Bob Thin");
        yes("learner: so one rises and the other sinks", good.score > 0.5 && bad.score < -0.5);
        eq("learner: the reason names the author", "You keep a lot by Ann Good", good.up);
        eq("learner: and for the other one", "Lowered: you often swipe away items by Bob Thin", bad.down);
        double authorOnly = tm.score(Learner.features("Unrelated", "", 999, "", none, "Ann Good", 0), "X", "Ann Good").score;
        double lengthOnly = tm.score(Learner.features("Unrelated", "", 999, "", none, "", 30), "X", "").score;
        yes("learner: the author is capped", authorOnly > 0 && authorOnly <= Learner.AUTHOR_CAP + 1e-9);
        yes("learner: the length is capped", lengthOnly > 0 && lengthOnly <= Learner.LENGTH_CAP + 1e-9);
        eq("learner: length bands", "short medium medium long", Learner.length(3) + " " + Learner.length(4) + " " + Learner.length(12) + " " + Learner.length(13));
        yes("learner: a video has no length feature", !Learner.features("T", "", 1, "video", none, "Ann", 3).containsKey("len:short"));
        yes("learner: unknown author and length add no features", !Learner.features("T", "", 1, "", none, "  ", 0).toString().matches(".*(a:|len:).*"));
        yes("learner: the author is lower-cased", Learner.features("T", "", 1, "", none, "Ann  Good", 5).containsKey("a:ann good"));
        java.util.List<Ranker.Doc> ad = new java.util.ArrayList<>();
        ad.add(new Ranker.Doc(1, "A tidyverse article", "", now, "", 7, "Blog", "").by("Bob Thin", 2));
        ad.add(new Ranker.Doc(2, "A tidyverse article", "", now, "", 7, "Blog", "").by("Ann Good", 15));
        for (int i = 3; i <= 8; i++) ad.add(new Ranker.Doc(i, "Other note " + i, "", now, "", 40 + i, "Blog " + i, "")); // a writer is only notable among others
        java.util.List<Ranker.Scored> ar = Ranker.rank(ad, none, tv, now);
        yes("learner: in the ranking the kept author comes first and is named", ar.get(0).id == 2L && ar.get(0).why.contains("You keep a lot by Ann Good"));
    }

    static void authors() throws Exception {
        String rss = "<rss xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><channel><title>T</title>"
            + "<item><title>One</title><link>https://e.org/1</link><dc:creator><![CDATA[Julia  Silge]]></dc:creator><author>x@e.org</author></item>"
            + "<item><title>Two</title><link>https://e.org/2</link><author>jane@e.org (Jane Doe)</author></item>"
            + "<item><title>Three</title><link>https://e.org/3</link><author>jane@e.org</author></item>"
            + "<item><title>Four</title><link>https://e.org/4</link></item></channel></rss>";
        java.util.Map<String, String> by = new java.util.HashMap<>();
        for (FeedParser.Item i : FeedParser.parse(rss.getBytes(StandardCharsets.UTF_8), "https://e.org/feed").items) by.put(i.title, i.author);
        eq("author: dc:creator", "Julia Silge", by.get("One"));
        eq("author: the name beside an e-mail address", "Jane Doe", by.get("Two"));
        eq("author: a bare e-mail address is not a name", "", by.get("Three"));
        eq("author: none", "", by.get("Four"));
        String atom = "<feed><title>A</title><author><name>Feed Owner</name></author><entry><title>E</title><id>1</id><author><name>Ann One</name><email>a@e.org</email></author><author><name>Ben Two</name></author></entry></feed>";
        eq("author: Atom author/name, the first one", "Ann One", FeedParser.parse(atom.getBytes(StandardCharsets.UTF_8), "https://e.org/a").items.get(0).author);
        eq("author: a stand-in is no author", "", FeedParser.author("Admin"));
        eq("author: very long names are cut", 80, FeedParser.author(new String(new char[200]).replace('\0', 'n')).length());
        yes("author: arXiv papers have one", !QuerySource.parse("arxiv", "x", file("tests/fixtures/arxiv.xml"), 1794700000000L).items.get(0).author.isEmpty());
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
        yes("kinds: all seven are valid", QuerySource.valid("arxiv") && QuerySource.valid("hn") && QuerySource.valid("hf") && QuerySource.valid("reddit") && QuerySource.valid("github") && QuerySource.valid("s2") && QuerySource.valid("openalex") && !QuerySource.valid("rss"));
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

    /** Stands in for the database: kept items from related.txt, document frequency by prefix, passages = every item holding a word. */
    static java.util.Map<Long, String> relDocs() throws Exception {
        java.util.Map<Long, String> m = new java.util.LinkedHashMap<>();
        for (String line : text("tests/fixtures/related.txt").split("\n")) { int i = line.indexOf('|'); if (i > 0) m.put(Long.parseLong(line.substring(0, i)), line.substring(i + 1)); }
        return m;
    }
    static java.util.List<Long> relFind(java.util.Map<Long, String> docs, long self, String title, String hl, String body, int limit) {
        java.util.Map<String, Integer> df = new java.util.HashMap<>();
        java.util.List<String> cands = Related.candidates(title, hl, body);
        for (String w : cands) { int d = 0; for (java.util.Map.Entry<Long, String> de : docs.entrySet()) { if (de.getKey() == self) continue; String t = de.getValue(); for (String x : new java.util.HashSet<>(Ranker.tokens(t))) if (x.startsWith(w)) { d++; break; } } df.put(w, d); }
        java.util.List<String> words = Related.rarest(cands, df, docs.size());
        java.util.List<Passages.Passage> ps = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, String> e : docs.entrySet()) for (String x : Ranker.tokens(e.getValue())) { boolean hit = false; for (String w : words) if (x.startsWith(w)) hit = true; if (hit) { ps.add(new Passages.Passage(e.getKey(), "text", e.getValue())); break; } }
        java.util.List<Long> out = new java.util.ArrayList<>();
        for (Related.Hit h : Related.rank(self, words, ps, df, docs.size(), limit)) out.add(h.id);
        return out;
    }
    static void related() throws Exception {
        java.util.Map<Long, String> docs = relDocs();
        eq("related: fixture has 8 kept items", 8, docs.size());
        // a new article about Parquet row groups builds on the two kept Parquet items
        java.util.List<Long> r = relFind(docs, 0, "Parquet column chunks and statistics", "", "How a reader skips row groups using statistics.", 3);
        yes("related: a related pair is found (item 1)", r.contains(1L));
        yes("related: and the second one (item 2)", r.contains(2L));
        yes("related: sourdough is not related", !r.contains(5L));
        // an unrelated article finds nothing
        eq("related: unrelated article finds nothing", 0, relFind(docs, 0, "Mountain hiking in the Tatras", "", "Trails, huts and weather in summer.", 3).size());
        // no self match: item 1 asked about itself never returns itself
        java.util.List<Long> self = relFind(docs, 1, "Parquet stores columns", "", docs.get(1L), 3);
        yes("related: never itself", !self.contains(1L));
        yes("related: item 1 still finds item 2", self.contains(2L));
        // an empty Stash returns nothing
        eq("related: empty Stash", 0, relFind(new java.util.LinkedHashMap<Long, String>(), 0, "Parquet column chunks", "", "row groups", 3).size());
        // generic words alone do not link: model and data are in half of the items
        eq("related: generic words alone do not link", 0, relFind(docs, 0, "A model and its data", "", "The model needs data and more data.", 3).size());
        // one shared rare word is not enough
        eq("related: one rare word is not enough", 0, relFind(docs, 0, "Kubernetes in the cloud", "", "Plain hosting of services.", 3).size());
        // two rare words link
        yes("related: two rare words link", relFind(docs, 0, "Kubernetes operators", "", "A controller loop.", 3).contains(6L));
        // Polish text
        yes("related: Polish words match", relFind(docs, 0, "Rust: pożyczanie referencji", "", "Własność i czas życia w kompilatorze.", 3).contains(7L));
        eq("related: match query", "parquet* OR column*", Related.matchQuery(java.util.Arrays.asList("parquet", "column")));
        eq("related: no words, no query", "", Related.matchQuery(new java.util.ArrayList<String>()));
        eq("related: limit is respected", 1, relFind(docs, 0, "Parquet column chunks and statistics", "", "row groups", 1).size());
    }

    static void weekNote() {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Europe/Warsaw");
        java.util.Calendar c = java.util.Calendar.getInstance(tz);
        c.clear(); c.set(2026, java.util.Calendar.NOVEMBER, 16, 0, 0, 0); // a Monday
        long mon = c.getTimeInMillis();
        long hour = 3600000L, day = 24 * hour;
        // week bounds
        eq("week: a Wednesday belongs to its Monday", mon, WeekNote.weekStart(mon + 2 * day + 5 * hour, tz));
        eq("week: Monday 00:00 starts the week", mon, WeekNote.weekStart(mon, tz));
        eq("week: Sunday 23:59 is still the same week", mon, WeekNote.weekStart(mon + 7 * day - 60000, tz));
        eq("week: next Monday starts the next week", mon + 7 * day, WeekNote.weekStart(mon + 7 * day, tz));
        eq("week: previous week", mon - 7 * day, WeekNote.previousWeek(mon, tz));
        eq("week: title", "Week 2026-11-16 to 2026-11-22", WeekNote.title(mon, tz));
        eq("week: file name", "2026-11-16 week.md", WeekNote.fileName(mon, tz));
        // a week over the clock change in March keeps Monday dates
        c.clear(); c.set(2026, java.util.Calendar.MARCH, 23, 0, 0, 0);
        long marMon = c.getTimeInMillis();
        eq("week: over the clock change the title still ends on Sunday", "Week 2026-03-23 to 2026-03-29", WeekNote.title(marMon, tz));
        eq("week: previous week across the clock change is a Monday", "2026-03-16", MarkdownExport.day(WeekNote.previousWeek(marMon, tz), tz));

        java.util.List<WeekNote.Item> kept = new java.util.ArrayList<>();
        WeekNote.Item a = new WeekNote.Item(1, "Parquet in practice", "https://e.org/a", "Blog A", "data", mon + hour, "");
        a.highlights.add("Row groups hold column chunks.");
        WeekNote.Item b = new WeekNote.Item(2, "Parquet row groups", "https://e.org/b", "Blog B", "data", mon + day, "worth a re-read");
        WeekNote.Item d = new WeekNote.Item(3, "Zarządzanie pamięcią w Rust: pożyczanie", "https://e.org/c", "Blog C", "rust", mon + 2 * day, "");
        d.highlights.add("Żaden zły pożyczający\nnie przeżyje.");
        WeekNote.Item e = new WeekNote.Item(4, "Old news", "https://e.org/old", "Blog D", "data", mon - day, "");
        WeekNote.Item f = new WeekNote.Item(5, "Loose item", "", "Blog E", "", mon + 3 * day, "");
        kept.add(a); kept.add(b); kept.add(d); kept.add(e); kept.add(f);
        java.util.List<long[]> pairs = new java.util.ArrayList<>();
        pairs.add(new long[]{1, 2}); pairs.add(new long[]{2, 1}); pairs.add(new long[]{2, 4}); pairs.add(new long[]{3, 3});
        java.util.List<WeekNote.Again> again = new java.util.ArrayList<>();
        again.add(new WeekNote.Again("What do you remember about this?", "Parquet in practice"));
        String md = WeekNote.build(mon, tz, kept, pairs, again);
        yes("week note: heading", md.contains("\n# Week 2026-11-16 to 2026-11-22\n"));
        yes("week note: counts only the items kept that week", md.contains("You kept 4 items this week."));
        yes("week note: an item kept the week before is left out", !md.contains("Old news"));
        yes("week note: highlights are quotes", md.contains("> Row groups hold column chunks."));
        yes("week note: a multi-line highlight stays quoted", md.contains("> Żaden zły pożyczający\n> nie przeżyje."));
        yes("week note: your note", md.contains("My note: worth a re-read"));
        yes("week note: Polish text is kept", md.contains("Zarządzanie pamięcią w Rust"));
        yes("week note: the topic comes before its items", md.indexOf("## data") < md.indexOf("Parquet in practice") && md.indexOf("## rust") > md.indexOf("## data"));
        yes("week note: no topic goes last under Other", md.indexOf("## Other") > md.indexOf("## rust"));
        yes("week note: item with no link is a plain heading", md.contains("### Loose item\n"));
        yes("week note: one builds-on line, later builds on earlier", md.contains("## Builds on\n\n- Parquet row groups builds on Parquet in practice\n"));
        eq("week note: a pair with an item from another week or itself is dropped, and a repeated pair shows once", 1, md.split("builds on", -1).length - 1);
        yes("week note: asked-again card", md.contains("## Asked again") && md.contains("- What do you remember about this? (Parquet in practice)"));
        eq("week note: the same input gives the same text", md, WeekNote.build(mon, tz, kept, pairs, again));
        eq("week note: the order of the input does not matter", md, WeekNote.build(mon, tz, new java.util.ArrayList<>(java.util.Arrays.asList(f, e, d, b, a)), pairs, again));
        yes("week note: no time of writing inside", !md.contains("2026-11-24"));
        // v1.17: threads, links to the notes, tags, to remember, a sentence, your own words, the closing line
        yes("week note: items that build on each other form a thread named by shared rare words", md.contains("## Thread: parquet, group\n"));
        yes("week note: a threaded item is not repeated under its topic", md.split("### \\[Parquet in practice\\]", -1).length == 2 && !md.contains("## data\n"));
        yes("week note: topics become tags", md.contains("tags:\n  - data\n  - rust\n  - stash/week\n"));
        java.util.List<WeekNote.Item> wk = new java.util.ArrayList<>();
        WeekNote.Item w1 = new WeekNote.Item(21, "Rivers in spring", "https://e.org/r", "Blog A", "nature", mon + hour, "");
        w1.file = "2026-11-16 Rivers in spring.md"; w1.topics.add("Open Data");
        w1.highlights.add("Short one."); w1.highlights.add("A river carries more sediment in spring than in any other season.");
        WeekNote.Item w2 = new WeekNote.Item(22, "Lakes", "https://e.org/l", "Blog B", "nature", mon + 2 * hour, "");
        w2.text = "Lakes are fed by rivers and by rain, and they store heat for weeks after summer ends. Other things happen too, of course, in many places.";
        WeekNote.Item w3 = new WeekNote.Item(23, "Odd #name", "", "Blog C", "", mon + 3 * hour, "");
        w3.file = "2026-11-16 Odd #name.md";
        wk.add(w1); wk.add(w2); wk.add(w3);
        WeekNote.Extra ex = new WeekNote.Extra();
        ex.seen = 40; ex.quiet.add("Gossip Daily"); ex.quiet.add("Ads Weekly"); ex.answer = "Rivers move\nmore in spring.";
        String m2 = WeekNote.build(mon, tz, wk, null, null, ex);
        yes("week note: a link to the item's own note", m2.contains("Note: [[2026-11-16 Rivers in spring]]\n"));
        yes("week note: no link when the file name would break it", !m2.contains("[[2026-11-16 Odd"));
        yes("week note: the first highlight and how many more", m2.contains("> Short one.\n\n(1 more highlight in the note)"));
        yes("week note: with no note or highlight, a sentence from the article, said so", m2.contains("Sentence from the article: “Lakes are fed by rivers and by rain, and they store heat for weeks after summer ends.”"));
        yes("week note: to remember picks a highlight of the right length", m2.contains("## To remember") && m2.contains("- A river carries more sediment in spring than in any other season. (Rivers in spring)") && !m2.contains("- Short one."));
        yes("week note: a topic with a space becomes a tag with a dash", m2.contains("  - open-data\n"));
        yes("week note: the closing line", m2.contains("You kept 3 and looked at 40 other cards in the Feed. Quietest sources: Gossip Daily, Ads Weekly."));
        yes("week note: your own words come right under the heading", m2.contains("# Week 2026-11-16 to 2026-11-22\n\n## In my words\n\nRivers move more in spring.\n\nYou kept 3 items"));
        String noAns = WeekNote.build(mon, tz, wk, null, null, new WeekNote.Extra());
        eq("week note: an answer added later gives the same note", m2.replace("\n\nYou kept 3 and looked at 40 other cards in the Feed. Quietest sources: Gossip Daily, Ads Weekly.", ""), WeekNote.withAnswer(noAns, "Rivers move more in spring."));
        eq("week note: a second answer replaces the first", WeekNote.withAnswer(noAns, "B"), WeekNote.withAnswer(WeekNote.withAnswer(noAns, "A"), "B"));
        eq("week note: an empty answer removes it", noAns, WeekNote.withAnswer(WeekNote.withAnswer(noAns, "A"), ""));
        eq("week note: tag of a number alone", "", WeekNote.tag("2026"));
        eq("week note: three to remember at most, one per item", 1, WeekNote.toRemember(WeekNote.inWeek(mon, tz, wk), null).size());
        // which week is due on a start
        eq("week: on a Thursday the week before is due", mon - 7 * day, WeekNote.due(mon + 3 * day, tz, false));
        eq("week: on the Monday itself too", mon - 7 * day, WeekNote.due(mon, tz, false));
        eq("week: a written week is never due again", -1L, WeekNote.due(mon + 3 * day, tz, true));
        // an empty week writes no note
        eq("week note: an empty week gives no note", "", WeekNote.build(mon + 14 * day, tz, kept, pairs, again));
        eq("week note: no items at all", "", WeekNote.build(mon, tz, new java.util.ArrayList<WeekNote.Item>(), null, null));
        // without pairs and cards there are no such sections
        String plain = WeekNote.build(mon, tz, kept, null, null);
        yes("week note: no builds-on section without pairs", !plain.contains("## Builds on") && !plain.contains("## Asked again"));
        // duplicate titles are told apart
        java.util.List<WeekNote.Item> dup = new java.util.ArrayList<>();
        dup.add(new WeekNote.Item(10, "Same title", "https://e.org/1", "Blog A", "data", mon + hour, ""));
        dup.add(new WeekNote.Item(11, "Same title", "https://e.org/2", "Blog B", "data", mon + 2 * hour, ""));
        dup.add(new WeekNote.Item(12, "Same title", "https://e.org/3", "Blog B", "data", mon + 3 * hour, ""));
        java.util.List<long[]> dp = new java.util.ArrayList<>();
        dp.add(new long[]{11, 10});
        String dm = WeekNote.build(mon, tz, dup, dp, null);
        yes("week note: duplicate titles carry their source", dm.contains("[Same title (Blog A)](https://e.org/1)") && dm.contains("[Same title (Blog B)](https://e.org/2)"));
        yes("week note: the same title and source get a number", dm.contains("[Same title (Blog B) #2](https://e.org/3)"));
        yes("week note: the builds-on line uses the same names", dm.contains("- Same title (Blog B) builds on Same title (Blog A)"));
    }

    static void quietFeeds() {
        eq("quiet: line", "kept 3 of 41 in 30 days", SourceStats.line(3, 41));
        eq("quiet: no items, no line", "", SourceStats.line(0, 0));
        eq("quiet: kept cannot exceed seen", "kept 5 of 5 in 30 days", SourceStats.line(9, 5));
        yes("quiet: 0 of 20 is suggested", SourceStats.suggestMute(0, 20, true));
        yes("quiet: 0 of 19 is not", !SourceStats.suggestMute(0, 19, true));
        yes("quiet: 1 of 50 is not", !SourceStats.suggestMute(1, 50, true));
        yes("quiet: a muted source is not suggested again", !SourceStats.suggestMute(0, 40, false));
        eq("quiet: window starts 30 days back", 1000000000L - 30L * 86400000L, SourceStats.since(1000000000L));
    }

    static void feedMix() {
        eq("feed mix: no cards leaves the feed alone", 0, FeedMix.slots(10, 0).length);
        eq("feed mix: one card after four articles", "[4]", java.util.Arrays.toString(FeedMix.slots(10, 1)));
        eq("feed mix: three cards", "[4, 8, 10]", java.util.Arrays.toString(FeedMix.slots(10, 3)));
        eq("feed mix: short feed puts the card at the end", "[2]", java.util.Arrays.toString(FeedMix.slots(2, 1)));
        eq("feed mix: empty feed still has its card", "[0, 0]", java.util.Arrays.toString(FeedMix.slots(0, 2)));
        eq("feed mix: never above the daily cap", Recall.DAILY_CAP, FeedMix.slots(100, 50).length);
        eq("feed mix: negative count is nothing", 0, FeedMix.slots(5, -3).length);
        eq("feed mix: path step after six articles", 6, FeedMix.pathSlot(40));
        eq("feed mix: path step at the end of a short feed", 3, FeedMix.pathSlot(3));
        eq("feed mix: path step in an empty feed", 0, FeedMix.pathSlot(0));
    }

    /** Saved answers for the link checks: address -> page; a missing address answers like HTTP 404. */
    static final class FakeWeb implements PathFinder.Web {
        final java.util.Map<String, String> pages = new java.util.HashMap<>();
        final java.util.List<String> asked = new java.util.ArrayList<>();
        public String get(String url) throws java.io.IOException {
            asked.add(url);
            String p = pages.get(url);
            if (p == null) throw new java.io.IOException("The server answered HTTP 404");
            return p;
        }
    }

    static void learnFast() throws Exception {
        // the real answer of gemini-3.5-flash-lite to the v1.20 request (2026-11-27): steps, search phrases, docs, no videos
        PathFinder.Plan p = PathFinder.parse(Gemini.replyText(200, text("tests/fixtures/gemini-learn.json")));
        eq("learn: five steps", 5, p.steps.size());
        eq("learn: four pages suggested", 4, p.suggested);
        yes("learn: every step has a search phrase", !p.steps.get(0).search.isEmpty() && !p.steps.get(4).search.isEmpty());
        eq("learn: a step may come without pages", 0, p.steps.get(3).res.size());
        // other shapes
        PathFinder.Plan f = PathFinder.parse("Here it is:\n```json\n{\"steps\":[{\"step\":\"A\",\"why\":\"w\",\"resources\":[{\"title\":\"t\",\"url\":\"ftp://x\",\"kind\":\"docs\"},{\"title\":\"v\",\"url\":\"https://www.youtube.com/watch?v=AKix55rQEUU\",\"kind\":\"video\"},{\"title\":\"t\",\"url\":\"https://x.org/a\",\"kind\":\"Docs\",\"minutes\":9999}]}]}\n```");
        eq("learn: JSON inside a sentence and a fence is read", 1, f.steps.size());
        eq("learn: only web pages are taken, never a video link from Gemini", 1, f.steps.get(0).res.size());
        eq("learn: kind is lower-cased", "docs", f.steps.get(0).res.get(0).kind);
        eq("learn: minutes are capped", 600, f.steps.get(0).res.get(0).minutes);
        yes("learn: no steps is refused", thrown(() -> PathFinder.parse("{\"steps\":[]}")).contains("empty"));
        yes("learn: not JSON is refused", thrown(() -> PathFinder.parse("Sorry, I cannot")).contains("could not be read"));

        // YouTube addresses and keys
        eq("learn: watch id", "-Ykyy58lFOI", PathFinder.videoId("https://www.youtube.com/watch?v=-Ykyy58lFOI&t=30s"));
        eq("learn: youtu.be id", "-Ykyy58lFOI", PathFinder.videoId("https://youtu.be/-Ykyy58lFOI?si=x"));
        eq("learn: shorts id", "-Ykyy58lFOI", PathFinder.videoId("https://www.youtube.com/shorts/-Ykyy58lFOI"));
        eq("learn: a channel is not a video", "", PathFinder.videoId("https://www.youtube.com/@KM4ACK"));
        eq("learn: a short id is not a video", "", PathFinder.videoId("https://www.youtube.com/watch?v=abc"));
        yes("learn: a YouTube key looks like AIza plus 35", PathFinder.validKey("AIzaSyA1234567890abcdefghijklmnopqrstuv") && !PathFinder.validKey("AIza short") && !PathFinder.validKey("sk-1234567890abcdefghijklmnopqrstuvwxyzABC"));
        String su = PathFinder.searchUrl("duckdb python pandas");
        yes("learn: the search asks for 5 English videos", su.startsWith("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=5&relevanceLanguage=en") && su.endsWith("q=duckdb+python+pandas"));
        yes("learn: the key is never in the address", !su.contains("key="));
        eq("learn: lengths in one request", "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=a%2Cb", PathFinder.videosUrl(java.util.Arrays.asList("a", "b")));
        java.util.List<PathFinder.Res> sr = PathFinder.parseSearch(text("tests/fixtures/yt-search.json"));
        eq("learn: videos in a search answer, without channels and upcoming streams", 3, sr.size());
        yes("learn: title, address, channel and its address", sr.get(1).title.equals("Introducing DuckLake") && sr.get(1).url.equals("https://www.youtube.com/watch?v=zeonmOO9jm4")
            && sr.get(1).channel.equals("DuckDB") && sr.get(1).channelUrl.equals("https://www.youtube.com/channel/UCQli51lMp-oW0gUHrcIj0jA"));
        eq("learn: entities in titles are decoded", "DuckDB & Python: querying Pandas DataFrames with SQL", sr.get(2).title);
        eq("learn: an error answer has no videos", 0, PathFinder.parseSearch("{\"error\":{\"code\":403,\"message\":\"quotaExceeded\"}}").size());
        // real answers recorded with Jacek's key on 2026-11-27 (search "duckdb python pandas", and the lengths of a live path's videos)
        java.util.List<PathFinder.Res> live = PathFinder.parseSearch(text("tests/fixtures/yt-search-live.json"));
        yes("learn: a real search answer gives 5 videos with channels", live.size() == 5 && live.get(0).url.endsWith("8SYQtpSk_OI") && live.get(0).channel.equals("ArjanCodes") && live.get(0).channelUrl.startsWith("https://www.youtube.com/channel/UC"));
        PathFinder.Step lpy = new PathFinder.Step(); lpy.name = "Python Integration"; lpy.search = "duckdb python pandas";
        int fitting = 0; for (PathFinder.Res v : live) if (PathFinder.videoScore(v.title, v.channel, lpy, "duckdb") > 0) fitting++;
        yes("learn: most real results fit the Python step", fitting >= 3);
        java.util.Map<String, Integer> real = PathFinder.parseLengths(text("tests/fixtures/yt-videos-live.json"));
        yes("learn: real lengths", real.size() == 4 && real.get("uHm6FEb2Re4") == 3 && real.get("HJGiMTLcpDs") == 25);
        java.util.Map<String, Integer> lens = PathFinder.parseLengths(text("tests/fixtures/yt-videos.json"));
        yes("learn: lengths from the videos answer", lens.get("zeonmOO9jm4") == 85 && lens.get("AKix55rQEUU") == 10 && lens.get("T23Bs75F7ZQ") == 230);
        eq("learn: PT9M43S", 10, PathFinder.isoMinutes("PT9M43S"));
        eq("learn: PT45S", 1, PathFinder.isoMinutes("PT45S"));
        eq("learn: P1DT1M", 1441, PathFinder.isoMinutes("P1DT1M"));
        eq("learn: a live stream has no length", 0, PathFinder.isoMinutes("P0D"));
        PathFinder.Step qs = new PathFinder.Step(); qs.name = "Python Integration"; qs.search = "python pandas";
        eq("learn: the topic is added to a search that lacks it", "duckdb python pandas", PathFinder.query(qs, "duckdb"));
        qs.search = "duckdb python pandas";
        eq("learn: a search that names the topic is kept", "duckdb python pandas", PathFinder.query(qs, "duckdb"));

        // which video fits a step
        yes("learn: a page title that shares a word fits", PathFinder.fits("CSV Import – DuckDB", "Querying CSV files", "", "duckdb"));
        yes("learn: only generic words do not fit", !PathFinder.fits("A beginner tutorial for everyone", "DuckDB tutorial for beginners", "Introduction", "duckdb"));
        PathFinder.Step py = new PathFinder.Step(); py.name = "Python Integration"; py.search = "duckdb python pandas";
        eq("learn: step words and the topic fit", 2.5, PathFinder.videoScore("DuckDB & Python: querying Pandas DataFrames with SQL", "freeCodeCamp.org", py, "duckdb"));
        eq("learn: a step word without the topic is not enough", 0.0, PathFinder.videoScore("Data Engineering with Python", "freeCodeCamp.org", py, "duckdb"));
        eq("learn: the topic without a step word is not enough", 0.0, PathFinder.videoScore("Introducing DuckLake", "DuckDB", py, "duckdb"));
        yes("learn: the channel name can carry the topic", PathFinder.videoScore("Python client deep dive", "DuckDB", py, "duckdb") >= 1.5);
        PathFinder.Step intro = new PathFinder.Step(); intro.name = "Introduction to DuckDB"; intro.search = "duckdb overview introduction";
        eq("learn: a step of topic words takes a topic match", 0.5, PathFinder.videoScore("Introducing DuckLake", "DuckDB", intro, "duckdb"));
        eq("learn: instant meta refresh is followed", "https://duckdb.org/docs/2.0/data/csv/overview.html", PathFinder.metaRefresh(text("tests/fixtures/docs-redirect.html"), "https://duckdb.org/docs/data/csv"));
        eq("learn: a page without one", "", PathFinder.metaRefresh("<html><title>x</title></html>", "https://a.org/"));

        // the whole check with saved answers: dead pages go, a redirect is followed, a video per step comes from the search
        FakeWeb w = new FakeWeb();
        w.pages.put("https://duckdb.org/docs/", "<html><head><title>Documentation</title></head></html>");
        w.pages.put("https://duckdb.org/docs/sql/introduction", text("tests/fixtures/docs-redirect.html"));
        w.pages.put("https://duckdb.org/docs/2.0/data/csv/overview.html", text("tests/fixtures/docs-page.html"));
        String search = text("tests/fixtures/yt-search.json");
        for (PathFinder.Step st : p.steps) w.pages.put(PathFinder.searchUrl(PathFinder.query(st, "duckdb")), search);
        w.pages.put(PathFinder.searchUrl(PathFinder.query(p.steps.get(1), "duckdb")), "{\"items\":[]}"); // the SQL step finds nothing
        w.pages.put(PathFinder.videosUrl(java.util.Arrays.asList("AKix55rQEUU", "T23Bs75F7ZQ")), text("tests/fixtures/yt-videos.json"));
        PathFinder.check(p, "duckdb", w, new java.util.HashSet<String>(), true);
        PathFinder.Step s0 = p.steps.get(0), s2 = p.steps.get(2);
        yes("learn: a page titled only Documentation fits by its site name", s0.res.size() == 2 && s0.res.get(1).url.equals("https://duckdb.org/docs/"));
        yes("learn: a step of topic words gets YouTube's first fitting video", s0.res.get(0).url.endsWith("AKix55rQEUU") && s0.res.get(0).minutes == 10);
        yes("learn: the Python step gets the Python video, first, with its length", s2.res.get(0).url.endsWith("T23Bs75F7ZQ") && s2.res.get(0).minutes == 230 && s2.res.get(0).channel.equals("freeCodeCamp.org"));
        yes("learn: a dead page (404) is dropped", s2.res.size() == 1);
        yes("learn: a docs page that redirects is kept at its real address", p.steps.get(1).res.size() == 1 || p.steps.get(1).res.get(p.steps.get(1).res.size() - 1).url.contains("overview"));
        int t23 = 0;
        for (PathFinder.Step x : p.steps) for (PathFinder.Res r : x.res) if (r.url.endsWith("T23Bs75F7ZQ") || r.url.endsWith("AKix55rQEUU")) t23++;
        eq("learn: a video is used once in a path", 2, t23);
        yes("learn: YouTube was searched", p.youtube && p.youtubeNote.isEmpty());
        yes("learn: nothing but the YouTube API and the pages Gemini named was asked", String.valueOf(w.asked).replaceAll("https://(www\\.googleapis\\.com/youtube/v3/|duckdb\\.org/docs/)[^,\\]]*", "").replaceAll("[\\[\\], ]", "").isEmpty());
        String md = PathFinder.markdown("duckdb", p);
        yes("learn: the note has numbered steps with checkboxes", md.contains("## 1. Introduction to DuckDB") && md.contains("- [ ] [Documentation](https://duckdb.org/docs/)"));
        yes("learn: a step without a video has a search line", md.contains("- Search YouTube for: duckdb power bi integration"));
        yes("learn: the note says where videos come from", md.startsWith("Free material, every link checked when the path was made; videos from a YouTube search."));
        // without a key: no request to YouTube, every step gets the search line
        FakeWeb w2 = new FakeWeb();
        PathFinder.Plan nk = PathFinder.parse(Gemini.replyText(200, text("tests/fixtures/gemini-learn.json")));
        PathFinder.check(nk, "duckdb", w2, null, false);
        yes("learn: without a key YouTube is not asked", !String.valueOf(w2.asked).contains("googleapis"));
        String md2 = PathFinder.markdown("duckdb", nk);
        int lines = md2.split("- Search YouTube for:", -1).length - 1;
        eq("learn: without a key every step has a search line", 5, lines);
        // a refused key or a used-up day: the path is still made, and says why there are no videos
        FakeWeb w3 = new FakeWeb();
        PathFinder.Plan rk = PathFinder.parse(Gemini.replyText(200, text("tests/fixtures/gemini-learn.json")));
        PathFinder.check(rk, "duckdb", w3, null, true);
        yes("learn: a failed search stops after one request and says why", rk.youtubeNote.contains("100 searches") && String.valueOf(w3.asked).split("googleapis", -1).length == 2);
        // channels that appear twice can be added
        PathFinder.Plan two = new PathFinder.Plan();
        for (int i = 0; i < 2; i++) { PathFinder.Step x = new PathFinder.Step(); x.name = "s" + i; PathFinder.Res v = new PathFinder.Res("v" + i, PathFinder.watchUrl(i == 0 ? "AKix55rQEUU" : "zeonmOO9jm4"), "video", 5); v.channel = "DuckDB"; v.channelUrl = "https://www.youtube.com/channel/UCQli51lMp-oW0gUHrcIj0jA"; x.res.add(v); two.steps.add(x); }
        java.util.List<String[]> offer = PathFinder.offerChannels(two);
        yes("learn: a channel with two videos is offered", offer.size() == 1 && offer.get(0)[0].equals("DuckDB") && offer.get(0)[1].endsWith("UCQli51lMp-oW0gUHrcIj0jA"));
        two.steps.get(1).res.get(0).channelUrl = "https://www.youtube.com/channel/UC8butISFwT-Wl7EV0hUK0BQ";
        eq("learn: one video is not enough", 0, PathFinder.offerChannels(two).size());
        // an item already kept counts as checked
        FakeWeb w4 = new FakeWeb();
        PathFinder.Plan k = PathFinder.parse("{\"steps\":[{\"step\":\"Basics\",\"why\":\"w\",\"search\":\"x basics\",\"resources\":[{\"title\":\"Mine\",\"url\":\"https://blog.example.org/post\",\"kind\":\"article\"}]}]}");
        java.util.Set<String> keptLinks = new java.util.HashSet<>(); keptLinks.add(FeedParser.normalizeLink("https://blog.example.org/post"));
        PathFinder.check(k, "x", w4, keptLinks, false);
        yes("learn: a kept item is reused without a request", k.steps.get(0).res.size() == 1 && w4.asked.isEmpty());
        // order inside a step, the next step
        java.util.List<PathFinder.Res> mix = new java.util.ArrayList<>();
        mix.add(new PathFinder.Res("d", "https://a.org/d", "docs", 1)); mix.add(new PathFinder.Res("v", "https://youtu.be/-Ykyy58lFOI", "video", 1)); mix.add(new PathFinder.Res("c", "https://a.org/c", "course", 1));
        PathFinder.sortRes(mix);
        eq("learn: videos, then courses, then docs", "v c d", mix.get(0).title + " " + mix.get(1).title + " " + mix.get(2).title);
        java.util.Map<Long, Integer> states = new java.util.HashMap<>();
        long idn = 1;
        for (PathFinder.Step x : p.steps) for (PathFinder.Res r : x.res) { r.itemId = idn; states.put(idn, 0); idn++; }
        states.put(1L, 1);
        int[] nx = PathFinder.next(p, states);
        yes("learn: the next step skips what is kept", nx != null && p.steps.get(nx[0]).res.get(nx[1]).itemId == 2);
        for (long i = 1; i < idn; i++) states.put(i, i % 2 == 0 ? 3 : 1);
        yes("learn: kept and skipped means the path is done", PathFinder.next(p, states) == null);
        eq("learn: the .md of a path made again has its date", "Path - duckdb 2026-11-27.md", MarkdownExport.pathFileName("duckdb", 1795776000000L, java.util.TimeZone.getTimeZone("UTC")));
    }

    static void autoNotes() {
        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
        long now = 1795776000000L; // 2026-11-27 10:40 UTC
        String today = AutoNotes.day(now, utc);
        eq("auto notes: local day", "2026-11-27", today);
        AutoNotes.Budget b = new AutoNotes.Budget();
        eq("auto notes: first one goes at once", 0L, AutoNotes.waitMs(b, today, now));
        b.last = now - 20000;
        eq("auto notes: one a minute", 40000L, AutoNotes.waitMs(b, today, now));
        b.last = now - 61000; b.notes = 29;
        eq("auto notes: the 30th may go", 0L, AutoNotes.waitMs(b, today, now));
        b.notes = 30;
        eq("auto notes: no more than 30 a day", -1L, AutoNotes.waitMs(b, today, now));
        eq("auto notes: the next day starts again", 0L, AutoNotes.waitMs(b, "2026-11-28", now + Recall.DAY));
        eq("auto notes: counts reset at midnight", 0, b.notes);
        b.stop = "2026-11-28";
        eq("auto notes: after Gemini's limit, nothing more that day", -1L, AutoNotes.waitMs(b, "2026-11-28", now + Recall.DAY));
        eq("auto notes: and again the day after", 0L, AutoNotes.waitMs(b, "2026-11-29", now + 2 * Recall.DAY));
        yes("auto notes: podcasts are skipped", AutoNotes.skip("audio", false));
        yes("auto notes: items with notes are skipped", AutoNotes.skip("", true));
        yes("auto notes: videos and articles are not", !AutoNotes.skip("video", false) && !AutoNotes.skip("", false));
        AutoNotes.Budget c = new AutoNotes.Budget(); c.day = today; c.notes = 1; c.paths = 2;
        eq("auto notes: the line in Sources", "Gemini today: 1 note, 2 path searches", AutoNotes.line(c, today));
        c.stop = today;
        yes("auto notes: the line says when the limit was reached", AutoNotes.line(c, today).contains("limit was reached"));
        eq("auto notes: a new day shows zero", "Gemini today: 0 notes, 0 path searches", AutoNotes.line(new AutoNotes.Budget(), today));
        // recall cards from notes: at most 5 become due a day
        long[] d = Recall.spread(new java.util.ArrayList<Long>(), now, 12, utc);
        int[] per = new int[5];
        for (long x : d) per[(int) (Recall.dayKey(x, utc) - Recall.dayKey(now, utc))]++;
        eq("recall spread: 5 tomorrow, 5 the day after, 2 the third day", "[0, 5, 5, 2, 0]", java.util.Arrays.toString(per));
        eq("recall spread: the first is due a day later", now + Recall.DAY, d[0]);
        java.util.List<Long> waiting = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) waiting.add(now + Recall.DAY + i);
        long[] e = Recall.spread(waiting, now, 2, utc);
        yes("recall spread: cards already waiting count", Recall.dayKey(e[0], utc) == Recall.dayKey(now + Recall.DAY, utc) && Recall.dayKey(e[1], utc) == Recall.dayKey(now + 2 * Recall.DAY, utc));
        eq("recall spread: nothing asked, nothing given", 0, Recall.spread(waiting, now, 0, utc).length);
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
