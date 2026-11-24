package pl.stashreader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Builds on": which items you kept are related to this one. Local only, no Gemini.
 * It takes the rarest key words of an item, finds kept passages that hold them, and links only when
 * at least two rare words are shared and their combined rarity is high enough. No Android imports.
 */
final class Related {
    private Related() {}

    static final int MAX_WORDS = 8, MIN_SHARED = 2, MIN_WORD_LEN = 4;
    static final double MIN_SCORE = 1.2;
    /** A word is rare when at most this share of the kept items hold it (and never fewer than 2 items). */
    static final double RARE_SHARE = 0.3;

    static final class Hit {
        long id; double score; int shared;
        Hit(long id, double score, int shared) { this.id = id; this.score = score; this.shared = shared; }
    }

    /** Candidate key words of an item, in order of first use: title first, then highlights, then the start of the text. Distinct, no stop words. */
    static List<String> candidates(String title, String highlights, String text) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        String[] parts = {title, highlights, text == null ? "" : (text.length() > 1500 ? text.substring(0, 1500) : text)};
        for (String p : parts) {
            for (String t : Ranker.tokens(p)) {
                if (t.length() < MIN_WORD_LEN || !t.matches("[\\p{L}\\p{N}]+")) continue;
                seen.put(t, Boolean.TRUE);
            }
        }
        return new ArrayList<>(seen.keySet());
    }

    /** Is a word with this document frequency rare among n kept items? A word nobody holds cannot link anything. */
    static boolean rare(int df, int n) {
        return df >= 1 && df <= Math.max(2, (int) Math.floor(n * RARE_SHARE));
    }

    /** The up to MAX_WORDS rarest of the candidates (lowest document frequency first, longer first on a tie). df maps a word to the number of kept items holding it. */
    static List<String> rarest(List<String> cands, final Map<String, Integer> df, int n) {
        List<String> ok = new ArrayList<>();
        for (String w : cands) { Integer d = df.get(w); if (d != null && rare(d, n)) ok.add(w); }
        Collections.sort(ok, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int c = Integer.compare(df.get(a), df.get(b));
                return c != 0 ? c : Integer.compare(b.length(), a.length());
            }
        });
        return ok.size() > MAX_WORDS ? new ArrayList<>(ok.subList(0, MAX_WORDS)) : ok;
    }

    /** The FTS MATCH expression for these words: every word as a prefix, joined by OR. Empty when there are none. */
    static String matchQuery(List<String> words) {
        StringBuilder b = new StringBuilder();
        for (String w : words) {
            if (!w.matches("[\\p{L}\\p{N}]+")) continue;
            if (b.length() > 0) b.append(" OR ");
            b.append(w).append('*');
        }
        return b.toString();
    }

    /**
     * Scores items from the passages that matched. An item needs at least MIN_SHARED different words and a score of MIN_SCORE;
     * each shared word adds its rarity. The item itself is never returned. Best first, at most limit.
     */
    static List<Hit> rank(long self, List<String> words, List<Passages.Passage> passages, Map<String, Integer> df, int n, int limit) {
        Map<Long, Set<String>> shared = new HashMap<>();
        for (Passages.Passage p : passages) {
            if (p.itemId == self) continue;
            Set<String> toks = new HashSet<>(Ranker.tokens(p.text));
            for (String w : words) {
                for (String t : toks) {
                    if (t.startsWith(w)) {
                        Set<String> s = shared.get(p.itemId);
                        if (s == null) { s = new HashSet<>(); shared.put(p.itemId, s); }
                        s.add(w);
                        break;
                    }
                }
            }
        }
        List<Hit> out = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> e : shared.entrySet()) {
            if (e.getValue().size() < MIN_SHARED) continue;
            double s = 0;
            for (String w : e.getValue()) { Integer d = df.get(w); s += Math.log(1 + (double) Math.max(1, n) / (1 + (d == null ? 0 : d))); }
            if (s >= MIN_SCORE) out.add(new Hit(e.getKey(), s, e.getValue().size()));
        }
        Collections.sort(out, new Comparator<Hit>() {
            @Override public int compare(Hit a, Hit b) {
                int c = Double.compare(b.score, a.score);
                return c != 0 ? c : Long.compare(a.id, b.id);
            }
        });
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
