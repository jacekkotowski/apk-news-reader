package pl.stashreader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Learn fast: reads the learning path Gemini suggests and keeps only links that were checked, so nothing made up reaches the reader.
 * Videos are checked with YouTube's oEmbed answer (real title and channel), pages by loading them and reading their title.
 * Gemini without Google Search makes up most video addresses, so a step without a checked video gets one from the search page of
 * a channel that was checked (YouTube's robots.txt allows a channel's search page, not the site search).
 * No Android imports; the network is behind {@link Web}, so the tests use saved answers.
 */
final class PathFinder {
    private PathFinder() {}

    static final int MAX_STEPS = 8, MAX_PER_STEP = 3, MAX_CHANNELS = 4, MAX_CHANNEL_SEARCHES = 10, MAX_CHANNELS_PER_STEP = 3, MAX_CHECKS = 45;

    /** The network, as the checks need it. get throws an IOException for a page that does not answer with 2xx. */
    interface Web {
        String get(String url) throws IOException;
        /** Where a redirect address points, or "" when it does not redirect. */
        String location(String url) throws IOException;
    }

    static final class Res {
        String title = "", url = "", kind = "", channel = "", channelUrl = "";
        int minutes;
        long itemId; // set once it is stored
        Res() {}
        Res(String title, String url, String kind, int minutes) { this.title = title; this.url = url; this.kind = kind; this.minutes = minutes; }
    }
    static final class Step { String name = "", why = "", search = ""; final List<Res> res = new ArrayList<>(); }
    static final class Channel { String name = "", url = "", feed = ""; Channel() {} Channel(String n, String u) { name = n; url = u; } }
    static final class Plan {
        final List<Step> steps = new ArrayList<>();
        final List<Channel> channels = new ArrayList<>();
        /** Links Gemini gave, and how many of them were kept after the checks. */
        int suggested, kept;
        boolean searched;
    }

    // ---- reading Gemini's answer ----

    /** The plan from Gemini's text: plain JSON, JSON in a code fence, or JSON with a sentence around it. */
    static Plan parse(String text) throws Gemini.GeminiException {
        String t = Gemini.stripFences(text == null ? "" : text);
        Map<String, Object> o = null;
        try { o = Json.obj(Json.parse(t)); } catch (RuntimeException e) { /* look for the object inside */ }
        if (o == null) {
            int a = t.indexOf('{'), b = t.lastIndexOf('}');
            if (a >= 0 && b > a) { try { o = Json.obj(Json.parse(t.substring(a, b + 1))); } catch (RuntimeException ignored) { } }
        }
        if (o == null) throw new Gemini.GeminiException("Gemini's learning path could not be read");
        Plan p = new Plan();
        List<Object> steps = Json.list(o.get("steps"));
        if (steps != null) for (Object so : steps) {
            Map<String, Object> s = Json.obj(so);
            if (s == null || p.steps.size() >= MAX_STEPS) continue;
            Step st = new Step();
            st.name = oneLine(Json.str(s, "step"), 120); st.why = oneLine(Json.str(s, "why"), 300); st.search = oneLine(Json.str(s, "search"), 80);
            if (st.name.isEmpty()) continue;
            List<Object> rs = Json.list(s.get("resources"));
            if (rs != null) for (Object ro : rs) {
                Map<String, Object> r = Json.obj(ro);
                if (r == null) continue;
                String url = Json.str(r, "url").trim();
                if (!url.regionMatches(true, 0, "https://", 0, 8) && !url.regionMatches(true, 0, "http://", 0, 7)) continue;
                st.res.add(new Res(oneLine(Json.str(r, "title"), 200), url, Json.str(r, "kind").trim().toLowerCase(Locale.ROOT), (int) clampMinutes(Json.num(r, "minutes"))));
                p.suggested++;
            }
            p.steps.add(st);
        }
        List<Object> chs = Json.list(o.get("channels"));
        if (chs != null) for (Object co : chs) {
            Map<String, Object> c = Json.obj(co);
            String u = channelUrl(Json.str(c, "url"));
            if (u.isEmpty() || p.channels.size() >= MAX_CHANNELS) continue;
            boolean dup = false;
            for (Channel x : p.channels) if (x.url.equalsIgnoreCase(u)) dup = true;
            if (!dup) p.channels.add(new Channel(oneLine(Json.str(c, "name"), 80), u));
        }
        if (p.steps.isEmpty()) throw new Gemini.GeminiException("Gemini returned an empty learning path");
        return p;
    }

    /** The sources Google Search gave ({address, title}); the addresses are redirects through vertexaisearch.cloud.google.com. */
    static List<String[]> groundingSources(String body) {
        List<String[]> out = new ArrayList<>();
        Map<String, Object> o;
        try { o = Json.obj(Json.parse(body == null ? "" : body)); } catch (RuntimeException e) { return out; }
        List<Object> cands = o == null ? null : Json.list(o.get("candidates"));
        Map<String, Object> c0 = cands == null || cands.isEmpty() ? null : Json.obj(cands.get(0));
        Map<String, Object> gm = c0 == null ? null : Json.obj(c0.get("groundingMetadata"));
        List<Object> chunks = gm == null ? null : Json.list(gm.get("groundingChunks"));
        if (chunks != null) for (Object ch : chunks) {
            Map<String, Object> web = Json.obj(Json.obj(ch) == null ? null : Json.obj(ch).get("web"));
            String uri = Json.str(web, "uri");
            if (!uri.isEmpty()) out.add(new String[]{uri, Json.str(web, "title")});
        }
        return out;
    }

    static boolean isRedirect(String url) {
        String h = PageWatch.host(url);
        return h.equals("vertexaisearch.cloud.google.com") || h.endsWith(".vertexaisearch.cloud.google.com");
    }

    // ---- YouTube ----

    private static final Pattern YT_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    /** The 11-character video id of a YouTube address (watch, youtu.be, shorts, embed, live), or "". */
    static String videoId(String url) {
        if (url == null) return "";
        String h = PageWatch.host(url);
        String id = "";
        if (h.equals("youtu.be")) id = firstSegment(pathOf(url));
        else if (h.equals("youtube.com") || h.equals("m.youtube.com") || h.equals("music.youtube.com")) {
            String p = pathOf(url);
            if (p.equals("/watch")) id = query(url, "v");
            else for (String pre : new String[]{"/shorts/", "/embed/", "/live/", "/v/"}) if (p.startsWith(pre)) id = firstSegment(p.substring(pre.length() - 1));
        }
        return YT_ID.matcher(id).matches() ? id : "";
    }

    static boolean isYouTube(String url) {
        String h = PageWatch.host(url);
        return h.equals("youtube.com") || h.equals("m.youtube.com") || h.equals("youtu.be");
    }

    static String watchUrl(String id) { return "https://www.youtube.com/watch?v=" + id; }

    static String oembedUrl(String videoUrl) { return "https://www.youtube.com/oembed?format=json&url=" + enc(videoUrl); }

    /** {title, channel name, channel address} from a YouTube oEmbed answer, or null when it is not a usable answer. */
    static String[] oembed(String json) {
        Map<String, Object> o;
        try { o = Json.obj(Json.parse(json == null ? "" : json)); } catch (RuntimeException e) { return null; }
        if (o == null) return null;
        String title = oneLine(Json.str(o, "title"), 200);
        if (title.isEmpty()) return null;
        return new String[]{title, oneLine(Json.str(o, "author_name"), 80), channelUrl(Json.str(o, "author_url"))};
    }

    /** A channel address as https://www.youtube.com/@handle (or /channel/UC..., /c/..., /user/...), or "" when it is not one. */
    static String channelUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (!isYouTube(u) || PageWatch.host(u).equals("youtu.be")) return "";
        String p = pathOf(u);
        Matcher m = Pattern.compile("^/(@[A-Za-z0-9._-]{2,60}|channel/UC[A-Za-z0-9_-]{20,24}|c/[A-Za-z0-9._-]{2,60}|user/[A-Za-z0-9._-]{2,60})(/.*)?$").matcher(p);
        return m.matches() ? "https://www.youtube.com/" + m.group(1) : "";
    }

    /**
     * A channel's own search page (allowed by YouTube's robots.txt, unlike /results). hl=en: without it YouTube shows
     * titles machine-translated into the phone's region language, which would not match the English step names.
     */
    static String channelSearchUrl(String channelUrl, String query) { return channelUrl + "/search?query=" + enc(query) + "&hl=en"; }

    /**
     * Videos on a channel's search page, in page order: {id, title, minutes}. The desktop page (videoRenderer) and the mobile
     * page that the app's Android User-Agent gets (compactVideoRenderer, its data inside a JavaScript string) are both read.
     */
    static List<Res> channelVideos(String html) {
        List<Res> out = new ArrayList<>();
        if (html == null) return out;
        if (html.contains("\\x22videoId\\x22")) html = jsUnescape(html);
        Set<String> seen = new HashSet<>();
        Pattern key = Pattern.compile("\"(?:compactVideoRenderer|videoRenderer)\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"");
        Matcher m = key.matcher(html);
        List<int[]> at = new ArrayList<>();
        while (m.find()) at.add(new int[]{m.start(), m.end()});
        for (int k = 0; k < at.size() && out.size() < 30; k++) {
            int e = at.get(k)[1];
            String id = html.substring(e - 12, e - 1);
            if (!seen.add(id)) continue;
            int next = k + 1 < at.size() ? at.get(k + 1)[0] : Math.min(html.length(), e + 20000);
            String part = html.substring(e, Math.max(e, next));
            String title = jsonString(part, "\"title\":{\"runs\":[{\"text\":\"");
            if (title.isEmpty()) title = jsonString(part, "\"title\":{\"simpleText\":\"");
            if (title.isEmpty()) continue;
            int lt = part.indexOf("\"lengthText\":");
            String len = "";
            if (lt >= 0) {
                String l = part.substring(lt, Math.min(part.length(), lt + 400));
                int a = l.indexOf("\"simpleText\":\""), b = l.indexOf("\"text\":\"");
                len = a >= 0 && (b < 0 || a < b) ? jsonString(l, "\"simpleText\":\"") : jsonString(l, "\"text\":\"");
            }
            out.add(new Res(oneLine(title, 200), watchUrl(id), "video", minutes(len)));
        }
        return out;
    }

    /** Undoes JavaScript string escapes (x22 after a backslash ...), as on YouTube's mobile pages; JSON's own u escapes stay for the JSON reader. */
    static String jsUnescape(String s) {
        final char bs = '\\';
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != bs || i + 1 >= s.length()) { b.append(c); continue; }
            char n = s.charAt(++i);
            if (n == 'x' && i + 2 < s.length()) {
                try { b.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16)); i += 2; continue; } catch (NumberFormatException e) { b.append(bs).append(n); continue; }
            }
            if (n == 'u') { b.append(bs).append(n); continue; } // left for the JSON reader
            b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'r' ? '\r' : n); // a doubled backslash becomes one, which the JSON reader then reads
        }
        return b.toString();
    }

    /** "1:02:03" or "12:34" as whole minutes, rounded up; 0 when unknown. */
    static int minutes(String len) {
        if (len == null || !len.matches("\\d{1,3}(:\\d{2}){1,2}")) return 0;
        String[] p = len.split(":");
        int sec = 0;
        for (String x : p) sec = sec * 60 + Integer.parseInt(x);
        return Math.max(1, (sec + 59) / 60);
    }

    // ---- does a checked title fit what was asked for ----

    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(("tutorial tutorials guide beginner beginners introduction intro basics basic course "
        + "official documentation docs doc video videos learn learning how complete full crash part step overview explained explain start started getting "
        + "youtube channel free easy quick fast minute minutes hour hours 2023 2024 2025 2026 vs build building make making first simple "
        + "understand understanding master mastering discover advanced fundamental fundamentals essential essentials best top tip tips").split(" ")));

    static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        for (String t : Ranker.tokens(s)) if (!GENERIC.contains(t)) out.add(t);
        return out;
    }

    /** True when the real title shares a word with what was asked for (the suggested title, the step, the topic). */
    static boolean fits(String realTitle, String... wanted) {
        Set<String> real = words(realTitle);
        if (real.isEmpty()) return false;
        for (String w : wanted) for (String t : words(w)) if (real.contains(t)) return true;
        return false;
    }

    /**
     * How well a video from a channel search fits a step; 0 when it does not fit. The title or the channel name must have a topic
     * word, and the title must have words of the step that are not topic words: one, or two when the step has three or more.
     * A step made only of topic words ("Introduction to DuckDB") gets no video this way; its search link is shown instead.
     * Score: shared step words, plus 0.5 for the topic.
     */
    static double stepScore(String title, String channel, Step st, String topic) {
        Set<String> real = words(title), top = words(topic), chan = words(channel), own = words(st.search + " " + st.name);
        own.removeAll(top);
        if (own.isEmpty()) return 0;
        boolean topicOk = false;
        for (String w : top) if (real.contains(w) || chan.contains(w)) topicOk = true;
        if (!topicOk) return 0;
        int shared = 0;
        for (String w : own) if (real.contains(w)) shared++;
        return shared >= (own.size() >= 3 ? 2 : 1) ? shared + 0.5 : 0;
    }

    // ---- the checks ----

    private static final Pattern REFRESH = Pattern.compile("<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[^>]*>", Pattern.CASE_INSENSITIVE);

    /** The address of an instant meta refresh ("Redirecting..." pages of documentation sites), or "". */
    static String metaRefresh(String html, String base) {
        if (html == null) return "";
        String head = html.length() > 20000 ? html.substring(0, 20000) : html;
        Matcher m = REFRESH.matcher(head);
        if (!m.find()) return "";
        String c = Sanitizer.attr(m.group(), "content");
        if (c == null) return "";
        Matcher u = Pattern.compile("(?i)^\\s*\\d+\\s*;\\s*url\\s*=\\s*['\"]?([^'\"]+)").matcher(Sanitizer.decode(c));
        return u.find() ? FeedDiscovery.resolve(base, u.group(1).trim()) : "";
    }

    static final class Budget { int left = MAX_CHECKS; boolean take() { return left-- > 0; } }

    /** Checks one suggested resource. Returns the checked copy (real title, channel), or null to drop it. */
    static Res checkOne(Res r, Step st, String topic, Web web, Budget b) {
        String url = r.url;
        try {
            if (isRedirect(url)) {
                if (!b.take()) return null;
                url = web.location(url);
                if (url.isEmpty() || isRedirect(url)) return null;
            }
            if (url.regionMatches(true, 0, "http://", 0, 7)) url = "https://" + url.substring(7);
            if (!url.regionMatches(true, 0, "https://", 0, 8)) return null;
            if (isYouTube(url)) {
                String id = videoId(url);
                if (id.isEmpty()) return null; // channels and playlists are not steps
                if (!b.take()) return null;
                String[] o = oembed(web.get(oembedUrl(watchUrl(id))));
                if (o == null || !fits(o[0] + " " + o[1], r.title, st.name, st.search, topic)) return null;
                Res out = new Res(o[0], watchUrl(id), "video", r.minutes);
                out.channel = o[1]; out.channelUrl = o[2];
                return out;
            }
            if (!b.take()) return null;
            String html = web.get(url);
            String to = metaRefresh(html, url);
            if (!to.isEmpty() && !to.equals(url) && b.take()) { url = to; html = web.get(url); }
            String title = PageWatch.meta(html).title;
            // the site's name counts too: duckdb.org titles a page just "Documentation"
            if (title.isEmpty() || !fits(title + " " + PageWatch.host(url).replace('.', ' '), r.title, st.name, st.search, topic)) return null;
            String kind = r.kind.equals("course") || r.kind.equals("docs") ? r.kind : "article";
            return new Res(oneLine(title, 200), url, kind, r.minutes);
        } catch (IOException | RuntimeException e) { return null; }
    }

    /**
     * Checks every link of the plan and fills steps without a checked video from the search pages of checked channels.
     * kept: links of items already kept on the topic, which count as checked. Returns the same plan, changed.
     */
    static Plan check(Plan p, String topic, Web web, Set<String> kept) {
        Budget b = new Budget();
        Set<String> used = new HashSet<>();
        for (Step st : p.steps) {
            List<Res> ok = new ArrayList<>();
            for (Res r : st.res) {
                if (ok.size() >= MAX_PER_STEP) break;
                Res c;
                if (kept != null && kept.contains(FeedParser.normalizeLink(r.url))) { c = r; if (c.kind.isEmpty()) c.kind = isYouTube(r.url) ? "video" : "article"; }
                else c = checkOne(r, st, topic, web, b);
                if (c != null && used.add(dedupKey(c.url))) ok.add(c);
            }
            st.res.clear(); st.res.addAll(ok);
        }
        // the channels to take videos from: the ones Gemini named that answer with a feed, then the channels of checked videos
        List<Channel> chs = new ArrayList<>();
        for (Channel c : p.channels) {
            if (!b.take()) break;
            try {
                String html = web.get(c.url);
                String feed = "";
                for (String a : FeedDiscovery.alternates(html, c.url)) if (a.contains("channel_id=")) { feed = a; break; }
                if (feed.isEmpty()) continue;
                String name = PageWatch.meta(html).title.replaceAll("\\s*-\\s*YouTube$", "").trim();
                c.name = name.isEmpty() ? c.name : oneLine(name, 80);
                c.feed = feed;
                chs.add(c);
            } catch (IOException | RuntimeException ignored) { }
        }
        for (Step st : p.steps) for (Res r : st.res) {
            if (r.channelUrl.isEmpty()) continue;
            boolean known = false;
            for (Channel c : chs) if (c.url.equalsIgnoreCase(r.channelUrl)) known = true;
            if (!known && chs.size() < MAX_CHANNELS + 2) chs.add(new Channel(r.channel, r.channelUrl));
        }
        p.channels.clear(); p.channels.addAll(chs);
        // a step without a checked video: the best fitting video from a checked channel's search page
        int searches = 0, turn = 0;
        Map<String, List<Res>> pages = new HashMap<>();
        Map<String, Integer> hits = new HashMap<>();
        for (Step st : p.steps) {
            if (chs.isEmpty() || hasVideo(st)) continue;
            String q = st.search.isEmpty() ? st.name : st.search;
            Res best = null; double bestScore = 0;
            // channels that already gave a video first, then the others in turn
            List<Channel> order = new ArrayList<>();
            for (int k = 0; k < chs.size(); k++) order.add(chs.get((turn + k) % chs.size()));
            final Map<String, Integer> h = hits;
            java.util.Collections.sort(order, (x, y) -> Integer.compare(h.containsKey(y.url) ? h.get(y.url) : 0, h.containsKey(x.url) ? h.get(x.url) : 0));
            for (int k = 0; k < order.size() && k < MAX_CHANNELS_PER_STEP && best == null; k++) {
                Channel c = order.get(k);
                String key = c.url + "|" + q;
                List<Res> vids = pages.get(key);
                if (vids == null) {
                    if (searches >= MAX_CHANNEL_SEARCHES || !b.take()) break;
                    searches++;
                    try { vids = channelVideos(web.get(channelSearchUrl(c.url, q))); } catch (IOException | RuntimeException e) { vids = new ArrayList<>(); }
                    pages.put(key, vids);
                }
                for (Res v : vids) {
                    if (used.contains(dedupKey(v.url))) continue;
                    double s = stepScore(v.title, c.name, st, topic);
                    if (s > bestScore) { best = v; bestScore = s; v.channelUrl = c.url; }
                }
            }
            turn++;
            if (best == null) continue;
            Res checked = checkOne(best, st, topic, web, b); // the oEmbed answer confirms it is public and gives the channel
            if (checked == null) continue;
            if (checked.minutes == 0) checked.minutes = best.minutes;
            used.add(dedupKey(checked.url));
            hits.put(best.channelUrl, (hits.containsKey(best.channelUrl) ? hits.get(best.channelUrl) : 0) + 1);
            st.res.add(0, checked);
            while (st.res.size() > MAX_PER_STEP) st.res.remove(st.res.size() - 1);
        }
        int kept2 = 0;
        for (Step st : p.steps) {
            sortRes(st.res);
            for (Res r : st.res) { if (r.minutes <= 0) r.minutes = r.kind.equals("video") ? 10 : 8; kept2++; }
        }
        p.kept = kept2;
        return p;
    }

    private static boolean hasVideo(Step st) { for (Res r : st.res) if (r.kind.equals("video")) return true; return false; }

    private static final List<String> ORDER = Arrays.asList("video", "course", "docs", "article");

    /** Videos first, then free courses, then documentation, then other pages; otherwise in Gemini's order. */
    static void sortRes(List<Res> l) {
        List<Res> c = new ArrayList<>(l);
        l.clear();
        for (String k : ORDER) for (Res r : c) if (r.kind.equals(k)) l.add(r);
        for (Res r : c) if (!ORDER.contains(r.kind)) l.add(r);
    }

    static String dedupKey(String url) {
        String id = videoId(url);
        return id.isEmpty() ? FeedParser.normalizeLink(url).toLowerCase(Locale.ROOT) : "yt:" + id;
    }

    /** YouTube channels that two or more resources of the path come from: {name, channel address}, most used first. */
    static List<String[]> offerChannels(Plan p) {
        Map<String, Integer> n = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Step st : p.steps) for (Res r : st.res) {
            if (r.channelUrl.isEmpty()) continue;
            String k = r.channelUrl.toLowerCase(Locale.ROOT);
            n.put(k, n.containsKey(k) ? n.get(k) + 1 : 1);
            if (!names.containsKey(k)) names.put(k, r.channel.isEmpty() ? r.channelUrl : r.channel);
        }
        List<String[]> out = new ArrayList<>();
        for (int want = 20; want >= 2; want--) for (Map.Entry<String, Integer> e : n.entrySet()) {
            if (e.getValue() != want) continue;
            String url = e.getKey();
            for (Step st : p.steps) for (Res r : st.res) if (r.channelUrl.equalsIgnoreCase(url)) { url = r.channelUrl; break; }
            out.add(new String[]{names.get(e.getKey()), url});
        }
        return out;
    }

    /** The path as a Markdown note: one section per step, a checkbox per resource. */
    static String markdown(String topic, Plan p) {
        StringBuilder b = new StringBuilder();
        b.append("Free material, every link checked when the path was made").append(p.searched ? " (found with Google Search)" : "").append(".\n");
        for (int i = 0; i < p.steps.size(); i++) {
            Step st = p.steps.get(i);
            b.append("\n## ").append(i + 1).append(". ").append(st.name).append("\n\n");
            if (!st.why.isEmpty()) b.append(st.why).append("\n\n");
            if (st.res.isEmpty()) b.append("- Nothing checked for this step. Search YouTube for: ").append(st.search.isEmpty() ? st.name : st.search).append('\n');
            for (Res r : st.res) {
                b.append("- [ ] [").append(r.title.replace("[", "(").replace("]", ")")).append("](").append(r.url.replace(")", "%29").replace(" ", "%20")).append(") (")
                 .append(r.kind).append(", ").append(r.minutes).append(" min").append(r.channel.isEmpty() ? "" : ", " + r.channel).append(")\n");
            }
        }
        return b.toString();
    }

    /** The first resource of the first step that is still to do (state 0 = unread), as an index pair {step, resource}, or null. */
    static int[] next(Plan p, Map<Long, Integer> states) {
        for (int i = 0; i < p.steps.size(); i++) {
            List<Res> rs = p.steps.get(i).res;
            for (int j = 0; j < rs.size(); j++) {
                Integer s = states.get(rs.get(j).itemId);
                if (s != null && s == 0) return new int[]{i, j};
            }
        }
        return null;
    }

    // ---- small helpers ----

    static long clampMinutes(long m) { return Math.max(0, Math.min(600, m)); }

    private static String oneLine(String s, int max) {
        String t = Sanitizer.collapse(s == null ? "" : s.replace('\n', ' ').replace('\r', ' '));
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    private static String pathOf(String url) {
        String s = url.replaceFirst("(?i)^https?://[^/?#]+", "");
        int q = s.indexOf('?'); if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#'); if (h >= 0) s = s.substring(0, h);
        return s.isEmpty() ? "/" : s;
    }

    private static String firstSegment(String path) {
        String s = path.startsWith("/") ? path.substring(1) : path;
        int k = s.indexOf('/');
        return k >= 0 ? s.substring(0, k) : s;
    }

    private static String query(String url, String name) {
        int q = url.indexOf('?');
        if (q < 0) return "";
        String qs = url.substring(q + 1);
        int h = qs.indexOf('#'); if (h >= 0) qs = qs.substring(0, h);
        for (String kv : qs.split("&")) if (kv.startsWith(name + "=")) return kv.substring(name.length() + 1);
        return "";
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return ""; }
    }

    /** The JSON string that starts right after the marker, unescaped; "" when the marker is missing. */
    private static String jsonString(String s, String marker) {
        int a = s.indexOf(marker);
        if (a < 0) return "";
        int i = a + marker.length();
        StringBuilder raw = new StringBuilder("\"");
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { raw.append(c).append(s.charAt(++i)); continue; }
            if (c == '"') break;
            raw.append(c);
        }
        raw.append('"');
        try { return Json.str(Json.parse(raw.toString())); } catch (RuntimeException e) { return ""; }
    }
}
