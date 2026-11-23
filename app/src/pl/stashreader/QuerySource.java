package pl.stashreader;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Sources that are a search on an open API instead of a feed address: arXiv, Hacker News (Algolia), Hugging Face daily papers,
 * Reddit, GitHub and Semantic Scholar. Pure logic (build the request address, read the answer), no network and no Android imports.
 */
final class QuerySource {
    private QuerySource() {}

    static final String ARXIV = "arxiv", HN = "hn", HF = "hf", REDDIT = "reddit", GITHUB = "github", S2 = "s2";
    /** Page watch, sitemap watch and the recommendations source are handled by their own classes. */
    static final String WATCH = "watch", SITEMAP = "sitemap", RECS = "recs";
    static final String[] KINDS = {ARXIV, HN, HF, REDDIT, GITHUB, S2, WATCH, SITEMAP, RECS};
    /** Query sources are fetched at most this often; the stored items are the cache in between. */
    static final long MIN_INTERVAL_MS = 3 * 3600000L, WATCH_INTERVAL_MS = 24 * 3600000L;

    /** How often a source of this kind may be asked: a watched page at most daily, searches every three hours. */
    static long minInterval(String kind) { return kind.equals(WATCH) || kind.equals(SITEMAP) ? WATCH_INTERVAL_MS : MIN_INTERVAL_MS; }
    static boolean watches(String kind) { return kind.equals(WATCH) || kind.equals(SITEMAP); }

    static boolean valid(String kind) { for (String k : KINDS) if (k.equals(kind)) return true; return false; }

    static String label(String kind) {
        switch (kind) {
            case ARXIV: return "arXiv";
            case HN: return "Hacker News";
            case HF: return "Hugging Face daily papers";
            case REDDIT: return "Reddit";
            case GITHUB: return "GitHub";
            case S2: return "Semantic Scholar";
            case WATCH: return "Watch a page (new links)";
            case SITEMAP: return "Watch a sitemap";
            case RECS: return "Recommendations from papers you kept";
            default: return kind;
        }
    }

    /** Hugging Face daily papers is a list, so it works with an empty query; every other kind needs one. */
    static boolean needsQuery(String kind) { return !HF.equals(kind) && !RECS.equals(kind); }

    /** A stable text used as the unique address of the source in the database. */
    static String key(String kind, String query) { return "query:" + kind + ":" + Sanitizer.collapse(query == null ? "" : query).toLowerCase(Locale.ROOT); }

    static String clean(String query) { return Sanitizer.collapse(query == null ? "" : query); }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    private static String day(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    /** The address to fetch. {@code now} is used for "recent only" limits. */
    static String requestUrl(String kind, String query, long now) {
        String q = clean(query);
        switch (kind) {
            case ARXIV:
                return "https://export.arxiv.org/api/query?search_query=" + enc("all:\"" + q + "\"") + "&sortBy=submittedDate&sortOrder=descending&max_results=30";
            case HN:
                return "https://hn.algolia.com/api/v1/search_by_date?query=" + enc(q) + "&tags=story&numericFilters=" + enc("points>=20") + "&hitsPerPage=30";
            case HF:
                return "https://huggingface.co/api/daily_papers?limit=50";
            case REDDIT:
                if (q.regionMatches(true, 0, "r/", 0, 2) && q.indexOf(' ') < 0) return "https://www.reddit.com/r/" + enc(q.substring(2)) + "/new.rss?limit=30";
                return "https://www.reddit.com/search.rss?q=" + enc(q) + "&sort=new&t=month&limit=30";
            case GITHUB:
                return "https://api.github.com/search/repositories?q=" + enc(q + " created:>" + day(now - 90L * 86400000L)) + "&sort=stars&order=desc&per_page=20";
            case S2: {
                int year = Integer.parseInt(day(now).substring(0, 4));
                return "https://api.semanticscholar.org/graph/v1/paper/search?query=" + enc(q) + "&limit=25&year=" + (year - 1) + "-" + year
                    + "&fields=title,abstract,year,url,publicationDate,citationCount";
            }
            case WATCH: case SITEMAP: return q;
            case RECS: return "https://api.semanticscholar.org/recommendations/v1/papers/?limit=30&fields=" + S2_FIELDS;
            default: throw new IllegalArgumentException("Unknown source kind " + kind);
        }
    }

    /** Semantic Scholar's API host: the only place the optional key is ever sent. */
    static boolean isS2Host(String address) { return address != null && address.startsWith("https://api.semanticscholar.org/"); }

    /** A key as Semantic Scholar issues them: one word of letters, digits, dashes and underscores, 20 to 100 characters. */
    static boolean validS2Key(String k) { return k != null && k.matches("[A-Za-z0-9_\\-]{20,100}"); }

    static final String S2_FIELDS = "title,abstract,year,url,publicationDate,citationCount";

    /** Citations of one paper (newest first is not offered by the API, so the caller keeps recent ones). */
    static String citationsUrl(String paperId) {
        return "https://api.semanticscholar.org/graph/v1/paper/" + enc(paperId) + "/citations?limit=30&fields=" + S2_FIELDS;
    }

    /** JSON body for the recommendations request: papers you kept as positive examples, papers you marked "less" as negative. */
    static String recsBody(List<String> positive, List<String> negative) {
        StringBuilder b = new StringBuilder("{\"positivePaperIds\":[");
        for (int i = 0; i < positive.size(); i++) { if (i > 0) b.append(','); b.append('"').append(jsonEscape(positive.get(i))).append('"'); }
        b.append("],\"negativePaperIds\":[");
        for (int i = 0; i < negative.size(); i++) { if (i > 0) b.append(','); b.append('"').append(jsonEscape(negative.get(i))).append('"'); }
        return b.append("]}").toString();
    }

    static String jsonEscape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " "); }

    private static final java.util.regex.Pattern ARXIV_ID = java.util.regex.Pattern.compile("(?:arxiv\\.org/(?:abs|pdf)/|huggingface\\.co/papers/)((?:\\d{4}\\.\\d{4,5})|(?:[a-z-]+(?:\\.[A-Z]{2})?/\\d{7}))(?:v\\d+)?(?:\\.pdf)?(?:[/?#].*)?$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern S2_ID = java.util.regex.Pattern.compile("semanticscholar\\.org/paper/(?:[^/]+/)?([0-9a-f]{40})(?:[/?#].*)?$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DOI = java.util.regex.Pattern.compile("(?:doi\\.org/)(10\\.\\d{4,9}/[^\\s?#]+)", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Semantic Scholar paper id for a link to a paper (arXiv, Hugging Face papers, Semantic Scholar, DOI), or "" for anything else. */
    static String paperId(String link) {
        if (link == null) return "";
        java.util.regex.Matcher m = ARXIV_ID.matcher(link.trim());
        if (m.find()) return "ARXIV:" + m.group(1);
        m = S2_ID.matcher(link.trim());
        if (m.find()) return m.group(1).toLowerCase(Locale.ROOT);
        m = DOI.matcher(link.trim());
        if (m.find()) return "DOI:" + m.group(1);
        return "";
    }

    static String accept(String kind) {
        switch (kind) {
            case ARXIV: case REDDIT: return "application/atom+xml, application/xml;q=0.9, */*;q=0.5";
            case GITHUB: return "application/vnd.github+json";
            default: return "application/json";
        }
    }

    /** Reads an API answer into the same Feed shape the RSS parser produces. */
    static FeedParser.Feed parse(String kind, String query, byte[] body, long now) throws FeedParser.FeedException {
        String address = key(kind, query);
        if (kind.equals(ARXIV) || kind.equals(REDDIT)) {
            FeedParser.Feed f = FeedParser.parse(body, address);
            f.title = label(kind) + ": " + clean(query);
            return f;
        }
        Object root;
        try { root = Json.parse(new String(body, StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { throw new FeedParser.FeedException("The answer was not valid JSON"); }
        FeedParser.Feed f = new FeedParser.Feed();
        f.title = label(kind) + (clean(query).isEmpty() ? "" : ": " + clean(query));
        switch (kind) {
            case HN: hn(root, f, address); break;
            case HF: hf(root, f, address, query); break;
            case GITHUB: github(root, f, address); break;
            case S2: s2(root, f, address); break;
            default: throw new FeedParser.FeedException("Unknown source kind");
        }
        FeedParser.finish(f);
        return f;
    }

    /** Answer of the recommendations request ({"recommendedPapers": [...]}). Each item says what it was recommended from. */
    static FeedParser.Feed parseRecommendations(byte[] body, String because) throws FeedParser.FeedException {
        Object root;
        try { root = Json.parse(new String(body, StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { throw new FeedParser.FeedException("The answer was not valid JSON"); }
        Map<String, Object> o = Json.obj(root);
        List<Object> l = o == null ? null : Json.list(o.get("recommendedPapers"));
        if (l == null) throw new FeedParser.FeedException(o != null && !Json.str(o, "message").isEmpty() ? "Semantic Scholar says: " + Json.str(o, "message") : "Semantic Scholar answered in an unexpected shape");
        FeedParser.Feed f = new FeedParser.Feed();
        f.title = "Recommendations from papers you kept";
        for (Object x : l) paper(f, Json.obj(x), because, "query:recs:");
        return f;
    }

    /** Papers that cite one of your kept papers, from the last two years. */
    static FeedParser.Feed parseCitations(byte[] body, String because, long now) throws FeedParser.FeedException {
        Object root;
        try { root = Json.parse(new String(body, StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { throw new FeedParser.FeedException("The answer was not valid JSON"); }
        Map<String, Object> o = Json.obj(root);
        List<Object> l = o == null ? null : Json.list(o.get("data"));
        if (l == null) throw new FeedParser.FeedException(o != null && !Json.str(o, "message").isEmpty() ? "Semantic Scholar says: " + Json.str(o, "message") : "Semantic Scholar answered in an unexpected shape");
        int thisYear = Integer.parseInt(day(now).substring(0, 4));
        FeedParser.Feed f = new FeedParser.Feed();
        for (Object x : l) {
            Map<String, Object> m = Json.obj(x);
            Map<String, Object> p = m == null ? null : Json.obj(m.get("citingPaper"));
            if (p == null || Json.num(p, "year") < thisYear - 1) continue;
            paper(f, p, because, "query:recs:");
        }
        return f;
    }

    private static void paper(FeedParser.Feed f, Map<String, Object> m, String because, String address) {
        if (m == null || Json.str(m, "paperId").isEmpty() || Json.str(m, "title").isEmpty()) return;
        String abs = Json.str(m, "abstract");
        FeedParser.Item i = new FeedParser.Item();
        i.uid = "s2:" + Json.str(m, "paperId");
        i.title = Json.str(m, "title");
        i.link = Json.str(m, "url");
        i.published = FeedParser.date(Json.str(m, "publicationDate"));
        if (i.published == 0 && !Json.str(m, "year").isEmpty()) i.published = FeedParser.date(Json.str(m, "year") + "-01-01");
        i.body = "<p>" + html(abs.isEmpty() ? "(no abstract available)" : abs) + "</p><p>" + Json.num(m, "citationCount") + " citations.</p>";
        i.because = because;
        FeedParser.add(f, i, address);
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void hn(Object root, FeedParser.Feed f, String address) throws FeedParser.FeedException {
        Map<String, Object> o = Json.obj(root);
        List<Object> hits = o == null ? null : Json.list(o.get("hits"));
        if (hits == null) throw new FeedParser.FeedException("Hacker News answered in an unexpected shape");
        for (Object h : hits) {
            Map<String, Object> m = Json.obj(h);
            if (m == null) continue;
            String id = Json.str(m, "objectID");
            String discussion = "https://news.ycombinator.com/item?id=" + id;
            String link = Json.str(m, "url");
            if (link.isEmpty()) link = discussion;
            FeedParser.Item i = new FeedParser.Item();
            i.uid = "hn:" + id;
            i.title = Json.str(m, "title");
            i.link = link;
            i.published = Json.num(m, "created_at_i") * 1000L;
            String story = Json.str(m, "story_text");
            i.body = "<p>" + Json.num(m, "points") + " points and " + Json.num(m, "num_comments") + " comments on Hacker News. <a href=\"" + discussion + "\">Discussion</a></p>"
                + (story.isEmpty() ? "" : "<p>" + html(story) + "</p>");
            FeedParser.add(f, i, address);
        }
    }

    private static void hf(Object root, FeedParser.Feed f, String address, String query) throws FeedParser.FeedException {
        List<Object> l = Json.list(root);
        if (l == null) throw new FeedParser.FeedException("Hugging Face answered in an unexpected shape");
        List<String> want = Ranker.tokens(query);
        for (Object x : l) {
            Map<String, Object> top = Json.obj(x);
            Map<String, Object> p = top == null ? null : Json.obj(top.get("paper"));
            if (p == null) p = top;
            if (p == null) continue;
            String id = Json.str(p, "id");
            String title = Json.str(p, "title").isEmpty() ? Json.str(top, "title") : Json.str(p, "title");
            String summary = Json.str(p, "summary").isEmpty() ? Json.str(top, "summary") : Json.str(p, "summary");
            if (!want.isEmpty()) {
                List<String> have = Ranker.tokens(title + " " + summary);
                if (!have.containsAll(want)) continue;
            }
            FeedParser.Item i = new FeedParser.Item();
            i.uid = "hf:" + id;
            i.title = title;
            i.link = id.isEmpty() ? "" : "https://huggingface.co/papers/" + id;
            i.published = FeedParser.date(Json.str(p, "publishedAt").isEmpty() ? Json.str(top, "publishedAt") : Json.str(p, "publishedAt"));
            i.body = "<p>" + html(summary) + "</p><p>" + Json.num(p, "upvotes") + " upvotes on Hugging Face daily papers.</p>";
            FeedParser.add(f, i, address);
        }
    }

    private static void github(Object root, FeedParser.Feed f, String address) throws FeedParser.FeedException {
        Map<String, Object> o = Json.obj(root);
        List<Object> items = o == null ? null : Json.list(o.get("items"));
        if (items == null) throw new FeedParser.FeedException(o != null && !Json.str(o, "message").isEmpty() ? "GitHub says: " + Json.str(o, "message") : "GitHub answered in an unexpected shape");
        for (Object x : items) {
            Map<String, Object> m = Json.obj(x);
            if (m == null) continue;
            String desc = Json.str(m, "description");
            StringBuilder b = new StringBuilder("<p>").append(html(desc.isEmpty() ? "(no description)" : desc)).append("</p><p>");
            b.append(Json.num(m, "stargazers_count")).append(" stars");
            if (!Json.str(m, "language").isEmpty()) b.append(", ").append(html(Json.str(m, "language")));
            List<Object> topics = Json.list(m.get("topics"));
            if (topics != null && !topics.isEmpty()) {
                b.append(". Topics: ");
                for (int k = 0; k < topics.size() && k < 8; k++) { if (k > 0) b.append(", "); b.append(html(Json.str(topics.get(k)))); }
            }
            b.append(".</p>");
            FeedParser.Item i = new FeedParser.Item();
            i.uid = "gh:" + Json.str(m, "id");
            i.title = Json.str(m, "full_name") + (desc.isEmpty() ? "" : ": " + desc);
            if (i.title.length() > 140) i.title = i.title.substring(0, 140);
            i.link = Json.str(m, "html_url");
            i.published = FeedParser.date(Json.str(m, "created_at"));
            i.body = b.toString();
            FeedParser.add(f, i, address);
        }
    }

    private static void s2(Object root, FeedParser.Feed f, String address) throws FeedParser.FeedException {
        Map<String, Object> o = Json.obj(root);
        List<Object> data = o == null ? null : Json.list(o.get("data"));
        if (data == null) {
            if (o != null && o.containsKey("total")) return; // no papers matched
            throw new FeedParser.FeedException(o != null && !Json.str(o, "message").isEmpty() ? "Semantic Scholar says: " + Json.str(o, "message") : "Semantic Scholar answered in an unexpected shape");
        }
        for (Object x : data) {
            Map<String, Object> m = Json.obj(x);
            if (m == null) continue;
            String abs = Json.str(m, "abstract");
            FeedParser.Item i = new FeedParser.Item();
            i.uid = "s2:" + Json.str(m, "paperId");
            i.title = Json.str(m, "title");
            i.link = Json.str(m, "url");
            i.published = FeedParser.date(Json.str(m, "publicationDate"));
            if (i.published == 0 && !Json.str(m, "year").isEmpty()) i.published = FeedParser.date(Json.str(m, "year") + "-01-01");
            i.body = "<p>" + html(abs.isEmpty() ? "(no abstract available)" : abs) + "</p><p>" + Json.num(m, "citationCount") + " citations.</p>";
            FeedParser.add(f, i, address);
        }
    }
}
