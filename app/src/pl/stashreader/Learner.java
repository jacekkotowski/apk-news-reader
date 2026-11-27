package pl.stashreader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Learns from what you keep (swipe right) and what you swipe left. No Android imports.
 *
 * Every item has features: its words (title words count fully, words only in the summary count half), its source,
 * the topics it matches, its kind (article, video, podcast), its author when the feed names one, and for an article
 * its length (short under 4 minutes, medium 4 to 12, long over 12). Author and length can tell apart two items on the
 * same subject from the same source. For each feature the learner compares how often it
 * appears in kept items, and in items swiped left, with how often it appears in the items waiting in the Feed:
 *   lift(kept) = log(rate in kept / rate in the Feed), only when above 0
 *   lift(less) = log(rate in less / rate in the Feed), only when above 0
 *   weight     = lift(kept) - lift(less)
 * So a word that is common everywhere ("data") weighs little, and a word as common in kept as in less items weighs 0.
 * Each label counts 0.5^(age / 60 days), so old interests fade. Each feature and each group of features is capped,
 * so what you learned never outweighs your own topics by much.
 *
 * Quiet signals (v1.16) are labels with a small weight: a card you looked at in the Feed and never opened is a weak
 * "less" for its source only (SEEN_WEIGHT); an article you read for at least half a minute without keeping it is a
 * weak "kept" (READ_WEIGHT). Because every rate is compared with the Feed, a source that fills half the Feed and
 * half of what you scroll past loses nothing.
 */
final class Learner {
    private Learner() {}

    static final double HALF_LIFE_DAYS = 60, PRIOR = 2;
    static final double WORD_CAP = 1.5, WORDS_CAP = 2.5, SOURCE_CAP = 1.5, TOPIC_CAP = 0.75, KIND_CAP = 0.5, TOTAL_CAP = 3.5;
    static final double AUTHOR_CAP = 1.0, LENGTH_CAP = 0.5;
    /** Reading minutes below SHORT_BELOW are short, above LONG_ABOVE long. */
    static final int SHORT_BELOW = 4, LONG_ABOVE = 12;
    /** A reason is shown when its part of the score is at least this large. */
    static final double SHOW = 0.75, SHOW_SOURCE = 0.5;
    static final double SEEN_WEIGHT = 0.15, READ_WEIGHT = 0.3;

    /** One thing you told the app: an item kept or swiped left. weight below 1 and sourceOnly are for quiet signals. */
    static final class Label {
        final String title, text, kind; final long sourceId, at; final boolean kept, sourceOnly; final double weight;
        String author = ""; int mins; // 0 = length not known
        Label(String title, String text, long sourceId, String kind, long at, boolean kept) { this(title, text, sourceId, kind, at, kept, 1, false); }
        Label(String title, String text, long sourceId, String kind, long at, boolean kept, double weight, boolean sourceOnly) {
            this.title = title == null ? "" : title; this.text = text == null ? "" : text; this.kind = kind == null ? "" : kind;
            this.sourceId = sourceId; this.at = at; this.kept = kept; this.weight = weight; this.sourceOnly = sourceOnly;
        }
        Label by(String author, int mins) { this.author = author == null ? "" : author; this.mins = mins; return this; }
    }

    /** What the learner adds to one item's score, and the reasons worth showing (empty when none). */
    static final class Result { double score; String up = "", down = ""; }

    /** The features of an item, each with how much it counts (1, or 0.5 for a word found only in the summary). */
    static Map<String, Double> features(String title, String text, long sourceId, String kind, List<Ranker.Topic> topics) {
        return features(title, text, sourceId, kind, topics, "", 0);
    }

    static Map<String, Double> features(String title, String text, long sourceId, String kind, List<Ranker.Topic> topics, String author, int mins) {
        Map<String, Double> f = new HashMap<>();
        String who = Sanitizer.collapse(author == null ? "" : author).toLowerCase(Locale.ROOT);
        if (!who.isEmpty()) f.put("a:" + who, 1.0);
        // a video or a podcast has a fixed stand-in for minutes, and its kind already says that
        if (mins > 0 && (kind == null || kind.isEmpty())) f.put("len:" + length(Ranker.minutes("", mins)), 1.0);
        for (String t : Ranker.tokens(text)) f.put("w:" + t, 0.5);
        for (String t : Ranker.tokens(title)) f.put("w:" + t, 1.0);
        if (sourceId > 0) f.put("s:" + sourceId, 1.0);
        if (kind != null && !kind.isEmpty()) f.put("k:" + kind, 1.0);
        for (String t : Ranker.matchedTopics(title + " " + text, topics)) f.put("t:" + t.toLowerCase(Locale.ROOT), 1.0);
        return f;
    }

    static String length(int mins) { return mins < SHORT_BELOW ? "short" : mins > LONG_ABOVE ? "long" : "medium"; }

    static final class Model {
        private final Map<String, Double> kept = new HashMap<>(), less = new HashMap<>();   // decayed counts
        private final Map<String, Double> keptN = new HashMap<>(), lessN = new HashMap<>(); // counts without decay
        private double keptAll, lessAll, keptSrc, lessSrc; // decayed totals: all full labels, and labels that teach sources
        private int keptLabels, lessLabels;
        private final Map<String, Integer> df = new HashMap<>();
        private int pool;

        /** How much one feature raises (above 0) or lowers (below 0) an item, before the caps. */
        double weight(String f) {
            double q = ((df.containsKey(f) ? df.get(f) : 0) + 0.5) / (pool + 1.0);
            boolean src = f.startsWith("s:");
            return lift(f, kept, keptN, src ? keptSrc : keptAll, keptLabels, q) - lift(f, less, lessN, src ? lessSrc : lessAll, lessLabels, q);
        }

        private static double lift(String f, Map<String, Double> c, Map<String, Double> n, double total, int labels, double q) {
            double raw = n.containsKey(f) ? n.get(f) : 0;
            // a feature seen in one item only is too weak to steer anything once there are several labels
            if (raw <= 0 || raw < (labels >= 3 ? 2 : 1) - 1e-9) return 0;
            double rate = ((c.containsKey(f) ? c.get(f) : 0) + PRIOR * q) / (total + PRIOR);
            return Math.max(0, Math.log(rate / q));
        }

        /** How much the app has heard about a source: kept, less and quiet signals together, without fading. */
        double heard(long sourceId) {
            String f = "s:" + sourceId;
            return (keptN.containsKey(f) ? keptN.get(f) : 0) + (lessN.containsKey(f) ? lessN.get(f) : 0);
        }

        /** The learned part of an item's score. sourceName and authorName are used only in the reason. */
        Result score(Map<String, Double> feats, String sourceName) { return score(feats, sourceName, ""); }

        Result score(Map<String, Double> feats, String sourceName, String authorName) {
            final Map<String, Double> part = new HashMap<>();
            double up = 0, down = 0, src = 0, topic = 0, kind = 0, author = 0, length = 0;
            for (Map.Entry<String, Double> e : feats.entrySet()) {
                String f = e.getKey();
                double w = weight(f);
                if (w == 0) continue;
                if (f.startsWith("w:")) {
                    double v = clamp(w, WORD_CAP) * e.getValue();
                    part.put(f.substring(2), v);
                    if (v > 0) up += v; else down += v;
                } else if (f.startsWith("s:")) src += clamp(w, SOURCE_CAP);
                else if (f.startsWith("t:")) topic += w * 0.5;
                else if (f.startsWith("k:")) kind += w * 0.5;
                else if (f.startsWith("a:")) author += clamp(w, AUTHOR_CAP);
                else if (f.startsWith("len:")) length += w;
            }
            double words = clamp(up + down, WORDS_CAP);
            Result r = new Result();
            r.score = clamp(words + clamp(src, SOURCE_CAP) + clamp(topic, TOPIC_CAP) + clamp(kind, KIND_CAP)
                    + clamp(author, AUTHOR_CAP) + clamp(length, LENGTH_CAP), TOTAL_CAP);
            String name = sourceName == null || sourceName.isEmpty() ? "this source" : sourceName;
            boolean named = authorName != null && !authorName.isEmpty();
            if (src >= SHOW_SOURCE && src >= up && src >= author) r.up = "You keep a lot from " + name;
            else if (named && author >= SHOW_SOURCE && author >= up) r.up = "You keep a lot by " + authorName;
            else if (up >= SHOW) r.up = "Like items you kept (" + top(part, true) + ")";
            if (src <= -SHOW_SOURCE && src <= down && src <= author) r.down = "Lowered: you often swipe away items from " + name;
            else if (named && author <= -SHOW_SOURCE && author <= down) r.down = "Lowered: you often swipe away items by " + authorName;
            else if (down <= -SHOW) r.down = "Lowered: similar to items you swiped left (" + top(part, false) + ")";
            return r;
        }
    }

    /**
     * Builds the model. pool holds the features of the items waiting in the Feed: they are the base rate a kept or
     * less word is compared with.
     */
    static Model build(List<Label> labels, List<Ranker.Topic> topics, List<Map<String, Double>> pool, long now) {
        Model m = new Model();
        m.pool = pool.size();
        for (Map<String, Double> p : pool) for (String f : p.keySet()) m.df.merge(f, 1, Integer::sum);
        for (Label l : labels) {
            double ageDays = Math.max(0, (now - l.at) / 86400000.0);
            double w = l.weight * Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
            Map<String, Double> c = l.kept ? m.kept : m.less, n = l.kept ? m.keptN : m.lessN;
            if (l.kept) m.keptSrc += w; else m.lessSrc += w;
            if (l.sourceOnly) {
                if (l.sourceId > 0) { c.merge("s:" + l.sourceId, w, Double::sum); n.merge("s:" + l.sourceId, l.weight, Double::sum); }
                continue;
            }
            if (l.kept) m.keptAll += w; else m.lessAll += w;
            if (l.weight >= 1) { if (l.kept) m.keptLabels++; else m.lessLabels++; } // a quiet signal is not a whole label
            for (String f : features(l.title, l.text, l.sourceId, l.kind, topics, l.author, l.mins).keySet()) {
                c.merge(f, w, Double::sum);
                n.merge(f, l.weight, Double::sum);
            }
        }
        return m;
    }

    private static double clamp(double v, double cap) { return Math.max(-cap, Math.min(cap, v)); }

    /** The three words that moved the score most in one direction. */
    private static String top(Map<String, Double> part, final boolean positive) {
        List<Map.Entry<String, Double>> es = new ArrayList<>();
        for (Map.Entry<String, Double> e : part.entrySet()) if (positive ? e.getValue() > 0 : e.getValue() < 0) es.add(e);
        Collections.sort(es, new Comparator<Map.Entry<String, Double>>() {
            @Override public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                int c = Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue()));
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < es.size() && i < 3; i++) { if (i > 0) b.append(", "); b.append(es.get(i).getKey()); }
        return b.toString();
    }
}
