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
 * Learn fast: Gemini plans the steps and suggests free courses and documentation; videos come only from a real YouTube search
 * (the YouTube Data API, with the reader's own free key). Every page Gemini names is loaded and its title compared with the
 * step, so nothing made up reaches the reader. Without a YouTube key a step gets no video, only a "Search YouTube" link.
 * No paid service is used. No Android imports; the network is behind {@link Web}, so the tests use saved answers.
 */
final class PathFinder {
    private PathFinder() {}

    static final int MAX_STEPS = 8, MAX_PER_STEP = 3, MAX_CHECKS = 30, SEARCH_RESULTS = 5;
    static final String YT_API = "https://www.googleapis.com/youtube/v3/";

    /** The network, as the checks need it. get throws an IOException for a page that does not answer with 2xx. */
    interface Web { String get(String url) throws IOException; }

    static final class Res {
        String title = "", url = "", kind = "", channel = "", channelUrl = "";
        int minutes;
        long itemId; // set once it is stored
        Res() {}
        Res(String title, String url, String kind, int minutes) { this.title = title; this.url = url; this.kind = kind; this.minutes = minutes; }
    }
    static final class Step { String name = "", why = "", search = ""; final List<Res> res = new ArrayList<>(); }
    static final class Plan {
        final List<Step> steps = new ArrayList<>();
        /** Pages Gemini gave, and how many links the path has after the checks. */
        int suggested, kept;
        /** YouTube was searched (a key is set); youtubeNote says why it stopped early, or is "". */
        boolean youtube;
        String youtubeNote = "";
    }

    // ---- reading Gemini's answer ----

    /** The plan from Gemini's text: plain JSON, JSON in a code fence, or JSON with a sentence around it. Video links are not taken. */
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
                if (isYouTube(url)) continue; // videos come from the YouTube search, never from Gemini's memory
                st.res.add(new Res(oneLine(Json.str(r, "title"), 200), url, Json.str(r, "kind").trim().toLowerCase(Locale.ROOT), (int) clampMinutes(Json.num(r, "minutes"))));
                p.suggested++;
            }
            p.steps.add(st);
        }
        if (p.steps.isEmpty()) throw new Gemini.GeminiException("Gemini returned an empty learning path");
        return p;
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
        return h.equals("youtube.com") || h.equals("m.youtube.com") || h.equals("youtu.be") || h.equals("music.youtube.com");
    }

    static String watchUrl(String id) { return "https://www.youtube.com/watch?v=" + id; }

    /** A YouTube API key looks like AIza followed by 35 letters, digits, - or _. */
    static boolean validKey(String k) { return k != null && k.matches("AIza[0-9A-Za-z_-]{35}"); }

    /** The search request: videos only, English, 5 results. The key is not in the address; the app sends it as a header. */
    static String searchUrl(String query) {
        return YT_API + "search?part=snippet&type=video&maxResults=" + SEARCH_RESULTS + "&relevanceLanguage=en&safeSearch=moderate&q=" + enc(query);
    }

    /** The request for the lengths of these videos (one request for all). */
    static String videosUrl(List<String> ids) { return YT_API + "videos?part=contentDetails&id=" + enc(String.join(",", ids)); }

    /** Videos in a YouTube search answer, in YouTube's order: title, address, channel name and channel address. */
    static List<Res> parseSearch(String json) {
        List<Res> out = new ArrayList<>();
        Map<String, Object> o;
        try { o = Json.obj(Json.parse(json == null ? "" : json)); } catch (RuntimeException e) { return out; }
        List<Object> items = o == null ? null : Json.list(o.get("items"));
        if (items != null) for (Object io : items) {
            Map<String, Object> it = Json.obj(io);
            Map<String, Object> id = it == null ? null : Json.obj(it.get("id"));
            Map<String, Object> sn = it == null ? null : Json.obj(it.get("snippet"));
            String vid = Json.str(id, "videoId");
            if (!YT_ID.matcher(vid).matches() || sn == null) continue;
            if (Json.str(sn, "liveBroadcastContent").equals("upcoming")) continue;
            String title = oneLine(Sanitizer.decode(Json.str(sn, "title")), 200);
            if (title.isEmpty()) continue;
            Res r = new Res(title, watchUrl(vid), "video", 0);
            r.channel = oneLine(Sanitizer.decode(Json.str(sn, "channelTitle")), 80);
            String ch = Json.str(sn, "channelId");
            if (ch.matches("UC[A-Za-z0-9_-]{20,24}")) r.channelUrl = "https://www.youtube.com/channel/" + ch;
            out.add(r);
        }
        return out;
    }

    /** Video id to minutes, from a videos answer with contentDetails. */
    static Map<String, Integer> parseLengths(String json) {
        Map<String, Integer> out = new HashMap<>();
        Map<String, Object> o;
        try { o = Json.obj(Json.parse(json == null ? "" : json)); } catch (RuntimeException e) { return out; }
        List<Object> items = o == null ? null : Json.list(o.get("items"));
        if (items != null) for (Object io : items) {
            Map<String, Object> it = Json.obj(io);
            Map<String, Object> cd = it == null ? null : Json.obj(it.get("contentDetails"));
            int m = isoMinutes(Json.str(cd, "duration"));
            if (m > 0) out.put(Json.str(it, "id"), m);
        }
        return out;
    }

    private static final Pattern ISO = Pattern.compile("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$");

    /** "PT1H2M3S" as whole minutes, rounded up; 0 when unknown. */
    static int isoMinutes(String d) {
        if (d == null || d.isEmpty()) return 0;
        Matcher m = ISO.matcher(d);
        if (!m.matches()) return 0;
        long s = 0;
        int[] mult = {86400, 3600, 60, 1};
        for (int i = 0; i < 4; i++) if (m.group(i + 1) != null) s += Long.parseLong(m.group(i + 1)) * mult[i];
        return s <= 0 ? 0 : (int) Math.min(6000, (s + 59) / 60);
    }

    /** The search words for a step: Gemini's phrase, with the topic in front when the phrase does not name it. */
    static String query(Step st, String topic) {
        String q = st.search.isEmpty() ? st.name : st.search;
        for (String w : words(topic)) if (words(q).contains(w)) return q;
        return topic + " " + q;
    }

    // ---- does a title fit what was asked for ----

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
     * How well a video found by the YouTube search fits a step; 0 when it does not. The title or the channel name must have a
     * topic word, and when the step has words of its own, the title must share one of them. Score: shared step words plus 0.5.
     */
    static double videoScore(String title, String channel, Step st, String topic) {
        Set<String> real = words(title), top = words(topic), chan = words(channel), own = words(st.search + " " + st.name);
        own.removeAll(top);
        boolean topicOk = false;
        for (String w : top) if (real.contains(w) || chan.contains(w)) topicOk = true;
        if (!topicOk) return 0;
        int shared = 0;
        for (String w : own) if (real.contains(w)) shared++;
        return !own.isEmpty() && shared == 0 ? 0 : shared + 0.5;
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

    /** Checks one page Gemini named: it must load and its title (with the site's name) must fit. Returns the checked copy, or null. */
    static Res checkPage(Res r, Step st, String topic, Web web, Budget b) {
        String url = r.url;
        try {
            if (url.regionMatches(true, 0, "http://", 0, 7)) url = "https://" + url.substring(7);
            if (!url.regionMatches(true, 0, "https://", 0, 8) || !b.take()) return null;
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
     * Checks every page of the plan, then, when youtube is true, searches YouTube once per step and puts the best fitting video
     * first. kept: links of items already kept on the topic, which count as checked. Returns the same plan, changed.
     */
    static Plan check(Plan p, String topic, Web web, Set<String> kept, boolean youtube) {
        Budget b = new Budget();
        Set<String> used = new HashSet<>();
        for (Step st : p.steps) {
            List<Res> ok = new ArrayList<>();
            for (Res r : st.res) {
                if (ok.size() >= MAX_PER_STEP) break;
                Res c;
                if (kept != null && kept.contains(FeedParser.normalizeLink(r.url))) { c = r; if (c.kind.isEmpty()) c.kind = "article"; }
                else c = checkPage(r, st, topic, web, b);
                if (c != null && used.add(dedupKey(c.url))) ok.add(c);
            }
            st.res.clear(); st.res.addAll(ok);
        }
        p.youtube = youtube;
        if (youtube) {
            List<Res> found = new ArrayList<>();
            for (Step st : p.steps) {
                List<Res> vids;
                try { vids = parseSearch(web.get(searchUrl(query(st, topic)))); }
                catch (IOException e) {
                    p.youtubeNote = "YouTube did not answer (" + e.getMessage() + "): the key may be wrong, or today's 100 searches are used.";
                    break;
                }
                Res best = null; double bestScore = 0;
                for (Res v : vids) {
                    if (used.contains(dedupKey(v.url))) continue;
                    double s = videoScore(v.title, v.channel, st, topic);
                    if (s > bestScore) { best = v; bestScore = s; } // equal scores keep YouTube's order
                }
                if (best == null) continue;
                used.add(dedupKey(best.url));
                st.res.add(0, best);
                while (st.res.size() > MAX_PER_STEP) st.res.remove(st.res.size() - 1);
                found.add(best);
            }
            if (!found.isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (Res v : found) ids.add(videoId(v.url));
                try {
                    Map<String, Integer> len = parseLengths(web.get(videosUrl(ids)));
                    for (Res v : found) { Integer m = len.get(videoId(v.url)); if (m != null) v.minutes = m; }
                } catch (IOException ignored) { } // lengths are a nicety
            }
        }
        int n = 0;
        for (Step st : p.steps) {
            sortRes(st.res);
            for (Res r : st.res) { if (r.minutes <= 0) r.minutes = r.kind.equals("video") ? 10 : 8; n++; }
        }
        p.kept = n;
        return p;
    }

    static boolean hasVideo(Step st) { for (Res r : st.res) if (r.kind.equals("video")) return true; return false; }

    private static final List<String> ORDER = Arrays.asList("video", "course", "docs", "article");

    /** Videos first, then free courses, then documentation, then other pages; otherwise in the given order. */
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

    /** YouTube channels that two or more videos of the path come from: {name, channel address}, most used first. */
    static List<String[]> offerChannels(Plan p) {
        Map<String, Integer> n = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Step st : p.steps) for (Res r : st.res) {
            if (r.channelUrl.isEmpty()) continue;
            n.put(r.channelUrl, n.containsKey(r.channelUrl) ? n.get(r.channelUrl) + 1 : 1);
            if (!names.containsKey(r.channelUrl)) names.put(r.channelUrl, r.channel.isEmpty() ? r.channelUrl : r.channel);
        }
        List<String[]> out = new ArrayList<>();
        for (int want = 20; want >= 2; want--) for (Map.Entry<String, Integer> e : n.entrySet()) if (e.getValue() == want) out.add(new String[]{names.get(e.getKey()), e.getKey()});
        return out;
    }

    /** The path as a Markdown note: one section per step, a checkbox per resource, a search line for a step without a video. */
    static String markdown(String topic, Plan p) {
        StringBuilder b = new StringBuilder();
        b.append("Free material, every link checked when the path was made").append(p.youtube ? "; videos from a YouTube search" : "").append(".\n");
        for (int i = 0; i < p.steps.size(); i++) {
            Step st = p.steps.get(i);
            b.append("\n## ").append(i + 1).append(". ").append(st.name).append("\n\n");
            if (!st.why.isEmpty()) b.append(st.why).append("\n\n");
            for (Res r : st.res) {
                b.append("- [ ] [").append(r.title.replace("[", "(").replace("]", ")")).append("](").append(r.url.replace(")", "%29").replace(" ", "%20")).append(") (")
                 .append(r.kind).append(", ").append(r.minutes).append(" min").append(r.channel.isEmpty() ? "" : ", " + r.channel).append(")\n");
            }
            if (!hasVideo(st)) b.append("- Search YouTube for: ").append(query(st, topic)).append('\n');
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
}
