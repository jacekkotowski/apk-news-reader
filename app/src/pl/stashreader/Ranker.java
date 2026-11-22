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
 * Explainable ranking, no Android imports and no learning model:
 * score = topic match (BM25 on title and summary, times the topic weight)
 *       + a boost for words that appear in items you kept
 *       - a penalty for words that appear in items marked "Less like this"
 *       + a small freshness term.
 * Every item comes with a short sentence saying why it is where it is.
 */
final class Ranker {
    private Ranker() {}

    static final double K1 = 1.2, B = 0.75, TITLE_TIMES = 3, PHRASE_BONUS = 0.75;
    static final double KEPT_WEIGHT = 3.0, LESS_WEIGHT = 4.0, FRESH_WEIGHT = 1.5, FRESH_DAYS = 3.0;
    static final int PROFILE_TERMS = 40;

    static final class Topic { final String phrase; final double weight; Topic(String p, double w) { phrase = p; weight = w; } }
    static final class Doc {
        final long id; final String title, text; final long pub;
        Doc(long id, String title, String text, long pub) { this.id = id; this.title = title; this.text = text; this.pub = pub; }
    }
    static final class Scored { long id; double score; String why; }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(("a an and are as at be been but by can for from had has have how i if in into is it its of on or our "
        + "so than that the their then there these they this to up was we were what when which who will with you your not no more most new one also about after all any "
        + "can could do does get got just may might out over some such use used using via why would").split(" ")));

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

    /** Ranks the documents, best first. Equal scores keep the newer item first. */
    static List<Scored> rank(List<Doc> docs, List<Topic> topics, List<String> keptTexts, List<String> lessTexts, long now) {
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
        Map<String, Double> kept = profile(keptTexts), less = profile(lessTexts);

        List<Scored> out = new ArrayList<>();
        for (Prepared p : ps) {
            Scored sc = new Scored();
            sc.id = p.doc.id;
            double score = 0;
            List<String> why = new ArrayList<>();

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

            List<String> keptWords = overlap(kept, p);
            double ks = similarity(kept, p);
            if (ks > 0) {
                score += KEPT_WEIGHT * ks;
                if (ks >= 0.25 && !keptWords.isEmpty()) why.add("Like items you kept (" + join(keptWords, 3) + ")");
            }
            double ls = similarity(less, p);
            if (ls > 0) {
                score -= LESS_WEIGHT * ls;
                if (ls >= 0.25) why.add("Lowered: similar to items you marked Less like this (" + join(overlap(less, p), 3) + ")");
            }

            double ageDays = p.doc.pub > 0 ? Math.max(0, (now - p.doc.pub) / 86400000.0) : 30;
            double fresh = FRESH_WEIGHT / (1 + ageDays / FRESH_DAYS);
            score += fresh;
            if (ageDays < 1) why.add("New today");
            else if (ageDays < 3 && why.isEmpty()) why.add("Recent");

            sc.score = score;
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

    /** The most characteristic words of a set of texts, each with a weight between 0 and 1. */
    static Map<String, Double> profile(List<String> texts) {
        Map<String, Integer> count = new HashMap<>();
        for (String x : texts) for (String t : new HashSet<>(tokens(x))) count.merge(t, 1, Integer::sum);
        List<Map.Entry<String, Integer>> es = new ArrayList<>(count.entrySet());
        Collections.sort(es, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                int c = Integer.compare(b.getValue(), a.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        Map<String, Double> out = new HashMap<>();
        if (es.isEmpty()) return out;
        double top = es.get(0).getValue();
        // a word seen in one text only is too weak to steer anything once there are several texts
        int min = texts.size() >= 3 ? 2 : 1;
        for (int i = 0; i < es.size() && out.size() < PROFILE_TERMS; i++) {
            if (es.get(i).getValue() < min) break;
            out.put(es.get(i).getKey(), es.get(i).getValue() / top);
        }
        return out;
    }

    private static double similarity(Map<String, Double> profile, Prepared p) {
        if (profile.isEmpty()) return 0;
        double s = 0;
        for (Map.Entry<String, Double> e : profile.entrySet()) if (p.tf.containsKey(e.getKey())) s += e.getValue();
        return Math.min(1, s / 4.0);
    }

    private static List<String> overlap(Map<String, Double> profile, Prepared p) {
        List<Map.Entry<String, Double>> hit = new ArrayList<>();
        for (Map.Entry<String, Double> e : profile.entrySet()) if (p.tf.containsKey(e.getKey())) hit.add(e);
        Collections.sort(hit, new Comparator<Map.Entry<String, Double>>() {
            @Override public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                int c = Double.compare(b.getValue(), a.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : hit) out.add(e.getKey());
        return out;
    }

    private static String join(List<String> l, int max) { return join(l, max, ", "); }
    private static String join(List<String> l, int max, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size() && i < max; i++) { if (i > 0) b.append(sep); b.append(l.get(i)); }
        return b.toString();
    }
}
