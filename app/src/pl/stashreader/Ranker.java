package pl.stashreader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explainable ranking, no Android imports:
 * score = topic match (BM25 on title and summary, times the topic weight)
 *       + what the Learner learned from items you kept and swiped left (words, source, topic, kind; capped)
 *       + a small freshness term.
 * Every item comes with a short sentence saying why it is where it is.
 */
final class Ranker {
    private Ranker() {}

    static final double K1 = 1.2, B = 0.75, TITLE_TIMES = 3, PHRASE_BONUS = 0.75;
    static final double FRESH_WEIGHT = 1.5, FRESH_DAYS = 3.0;
    /** Items recommended from papers you kept get this much extra, and their reason comes first. */
    static final double BECAUSE_BONUS = 1.5;

    static final class Topic { final String phrase; final double weight; Topic(String p, double w) { phrase = p; weight = w; } }
    static final class Doc {
        final long id; final String title, text, because; final long pub;
        final long sourceId; final String source, kind;
        Doc(long id, String title, String text, long pub) { this(id, title, text, pub, ""); }
        Doc(long id, String title, String text, long pub, String because) { this(id, title, text, pub, because, 0, "", ""); }
        Doc(long id, String title, String text, long pub, String because, long sourceId, String source, String kind) {
            this.id = id; this.title = title; this.text = text; this.pub = pub; this.because = because == null ? "" : because;
            this.sourceId = sourceId; this.source = source == null ? "" : source; this.kind = kind == null ? "" : kind;
        }
    }
    static final class Scored { long id; double score; String why; boolean topicHit; }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(("a an and are as at be been but by can for from had has have how i if in into is it its of on or our "
        + "so than that the their then there these they this to up was we were what when which who will with you your not no more most new one also about after all any "
        + "can could do does doe did ha wa thi get got just may might out over some such use used using via why would").split(" ")));

    /** Lower case words without stop words; a trailing plural "s" is removed so "models" and "model" match. */
    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder w = new StringBuilder();
        String s = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c)) { w.append(c); continue; }
            if (w.length() > 0) {
                String t = stem(w.toString());
                w.setLength(0);
                if (t.length() >= 2 && !STOP.contains(t)) out.add(t);
            }
        }
        return out;
    }

    static String stem(String t) {
        if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss")) return t.substring(0, t.length() - 1);
        return t;
    }

    private static final class Prepared {
        final Doc doc; final Map<String, Integer> tf = new HashMap<>(); int len; final String lower;
        Prepared(Doc d) {
            doc = d;
            List<String> title = tokens(d.title), body = tokens(d.text);
            for (String t : title) { tf.merge(t, (int) TITLE_TIMES, Integer::sum); len += (int) TITLE_TIMES; }
            for (String t : body) { tf.merge(t, 1, Integer::sum); len++; }
            lower = (d.title + " " + d.text).toLowerCase(Locale.ROOT);
        }
    }

    /** Ranks with kept and less texts only (no source, no dates): every text counts as labelled now. */
    static List<Scored> rank(List<Doc> docs, List<Topic> topics, List<String> keptTexts, List<String> lessTexts, long now) {
        List<Learner.Label> labels = new ArrayList<>();
        for (String t : keptTexts) labels.add(new Learner.Label("", t, 0, "", now, true));
        for (String t : lessTexts) labels.add(new Learner.Label("", t, 0, "", now, false));
        return rank(docs, topics, labels, now);
    }

    /** Ranks the documents, best first. Equal scores keep the newer item first. */
    static List<Scored> rank(List<Doc> docs, List<Topic> topics, List<Learner.Label> labels, long now) {
        List<Prepared> ps = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        double totalLen = 0;
        for (Doc d : docs) {
            Prepared p = new Prepared(d);
            ps.add(p);
            totalLen += p.len;
            for (String t : p.tf.keySet()) df.merge(t, 1, Integer::sum);
        }
        double avg = ps.isEmpty() ? 1 : Math.max(1, totalLen / ps.size());
        int n = ps.size();
        List<Map<String, Double>> feats = new ArrayList<>();
        for (Doc d : docs) feats.add(Learner.features(d.title, d.text, d.sourceId, d.kind, topics));
        Learner.Model model = Learner.build(labels, topics, feats, now);
        int at = 0;

        List<Scored> out = new ArrayList<>();
        for (Prepared p : ps) {
            Scored sc = new Scored();
            sc.id = p.doc.id;
            double score = 0;
            List<String> why = new ArrayList<>();

            if (!p.doc.because.isEmpty()) { score += BECAUSE_BONUS; why.add(p.doc.because); }
            List<Object[]> hits = new ArrayList<>(); // {score, phrase}
            for (Topic t : topics) {
                if (t.weight <= 0) continue;
                double s = topicScore(t, p, df, n, avg);
                if (s > 0) { hits.add(new Object[]{s, t.phrase}); score += s; }
            }
            Collections.sort(hits, new Comparator<Object[]>() {
                @Override public int compare(Object[] a, Object[] b) { return Double.compare((Double) b[0], (Double) a[0]); }
            });
            if (!hits.isEmpty()) {
                StringBuilder b = new StringBuilder("Matches your topics: ");
                for (int i = 0; i < hits.size() && i < 3; i++) { if (i > 0) b.append(", "); b.append((String) hits.get(i)[1]); }
                why.add(b.toString());
            }

            Learner.Result learned = model.score(feats.get(at++), p.doc.source);
            score += learned.score;
            if (!learned.up.isEmpty()) why.add(learned.up);
            if (!learned.down.isEmpty()) why.add(learned.down);

            double ageDays = p.doc.pub > 0 ? Math.max(0, (now - p.doc.pub) / 86400000.0) : 30;
            double fresh = FRESH_WEIGHT / (1 + ageDays / FRESH_DAYS);
            score += fresh;
            if (ageDays < 1) why.add("New today");
            else if (ageDays < 3 && why.isEmpty()) why.add("Recent");

            sc.score = score;
            sc.topicHit = !hits.isEmpty() || !p.doc.because.isEmpty();
            sc.why = why.isEmpty() ? "No topic matched; shown by date" : join(why, 3, "; ");
            out.add(sc);
        }
        final Map<Long, Long> pubs = new HashMap<>();
        for (Doc d : docs) pubs.put(d.id, d.pub);
        Collections.sort(out, new Comparator<Scored>() {
            @Override public int compare(Scored a, Scored b) {
                int c = Double.compare(b.score, a.score);
                if (c != 0) return c;
                return Long.compare(pubs.get(b.id), pubs.get(a.id));
            }
        });
        return out;
    }

    /** The scored items put back into the order of the documents (the Newest sort: the caller passes them by date). */
    static List<Scored> inOrder(List<Scored> scored, List<Doc> docs) {
        Map<Long, Scored> by = new HashMap<>();
        for (Scored s : scored) by.put(s.id, s);
        List<Scored> out = new ArrayList<>();
        for (Doc d : docs) if (by.containsKey(d.id)) out.add(by.get(d.id));
        return out;
    }

    /** Topics (any weight) whose words all appear in the text, for tagging an exported note. */
    static List<String> matchedTopics(String text, List<Topic> topics) {
        Set<String> have = new HashSet<>(tokens(text));
        List<String> out = new ArrayList<>();
        for (Topic t : topics) {
            List<String> terms = tokens(t.phrase);
            if (!terms.isEmpty() && have.containsAll(terms)) out.add(t.phrase);
        }
        return out;
    }

    private static double topicScore(Topic t, Prepared p, Map<String, Integer> df, int n, double avg) {
        List<String> terms = tokens(t.phrase);
        if (terms.isEmpty()) return 0;
        double sum = 0; int matched = 0;
        for (String term : terms) {
            Integer f = p.tf.get(term);
            if (f == null) continue;
            matched++;
            double d = df.containsKey(term) ? df.get(term) : 0;
            double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
            sum += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * p.len / avg));
        }
        if (matched < terms.size()) return 0; // every word of a multi-word topic must be present
        double s = sum / terms.size();
        if (terms.size() > 1 && p.lower.contains(t.phrase.toLowerCase(Locale.ROOT).trim())) s += PHRASE_BONUS;
        return s * t.weight;
    }

    private static String join(List<String> l, int max) { return join(l, max, ", "); }
    private static String join(List<String> l, int max, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size() && i < max; i++) { if (i > 0) b.append(sep); b.append(l.get(i)); }
        return b.toString();
    }

    // ---- Surprise Me: about every fifth card of the Feed is a surprise ----

    static final double SURPRISE_MIN = 0.4, SURPRISE_PENALTY = 0.8;
    static final int BLOCK_AFTER = 3, SURPRISE_EVERY = 5, SURPRISE_MAX_MINS = 12;

    /** What the Feed needs to know about one item besides its score. */
    static final class Cand {
        final int mins; final long sourceId; final String source;
        Cand(int mins, long sourceId, String source) { this.mins = mins; this.sourceId = sourceId; this.source = source; }
    }
    static final class Pick { long id; boolean surprise; String why; }

    /** Reading time of a card: the estimate for articles (at least 1, at most 40); a video counts 10 minutes, a podcast episode 20. */
    static int minutes(String kind, int mins) {
        if ("video".equals(kind)) return 10;
        if ("audio".equals(kind)) return 20;
        return Math.max(1, Math.min(40, mins));
    }

    /**
     * The whole Feed in order: the ranked items, with a surprise at every fifth place (the fifth, tenth ...) while there are any.
     * A surprise matches none of your topics, is fresh or like what you kept (score plus a little for length, at least SURPRISE_MIN),
     * is short (12 minutes or less), and comes from a source you have not turned down three times.
     */
    static List<Pick> mix(List<Scored> ranked, Map<Long, Cand> info, Map<Long, Integer> penalty) {
        final Map<Long, Double> quality = new HashMap<>();
        List<Scored> cands = new ArrayList<>();
        for (Scored s : ranked) {
            Cand c = info.get(s.id);
            if (c == null || s.topicHit || c.mins > SURPRISE_MAX_MINS) continue;
            int pen = penalty.containsKey(c.sourceId) ? penalty.get(c.sourceId) : 0;
            double q = s.score + 0.3 * Math.min(c.mins, 10) / 10.0 - SURPRISE_PENALTY * pen;
            if (pen >= BLOCK_AFTER || q < SURPRISE_MIN) continue;
            quality.put(s.id, q);
            cands.add(s);
        }
        Collections.sort(cands, new Comparator<Scored>() {
            @Override public int compare(Scored a, Scored b) { return Double.compare(quality.get(b.id), quality.get(a.id)); }
        });
        int max = ranked.size() / SURPRISE_EVERY;
        List<Scored> sur = cands.size() > max ? new ArrayList<>(cands.subList(0, max)) : cands;
        Set<Long> surIds = new HashSet<>();
        for (Scored s : sur) surIds.add(s.id);
        List<Scored> reg = new ArrayList<>();
        for (Scored s : ranked) if (!surIds.contains(s.id)) reg.add(s);
        List<Pick> out = new ArrayList<>();
        int ri = 0, si = 0;
        while (ri < reg.size() || si < sur.size()) {
            boolean slot = out.size() % SURPRISE_EVERY == SURPRISE_EVERY - 1;
            if ((slot && si < sur.size()) || ri >= reg.size()) { out.add(pick(sur.get(si), info.get(sur.get(si).id), true)); si++; }
            else { out.add(pick(reg.get(ri), info.get(reg.get(ri).id), false)); ri++; }
        }
        return out;
    }

    private static Pick pick(Scored s, Cand c, boolean surprise) {
        Pick p = new Pick();
        p.id = s.id; p.surprise = surprise;
        if (!surprise) p.why = s.why;
        else if (s.why.startsWith("Like items you kept")) p.why = "Surprise: matches none of your topics; " + s.why.substring(0, 1).toLowerCase(Locale.ROOT) + s.why.substring(1);
        else p.why = "Surprise: matches none of your topics, but it is recent and from " + c.source;
        return p;
    }
}
