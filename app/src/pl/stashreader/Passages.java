package pl.stashreader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ask My Stash: cutting kept text into passages, turning a question into a full-text query, and ranking what comes back. No Android imports. */
final class Passages {
    private Passages() {}

    static final int TARGET = 500, HARD_MAX = 900, MAX_TERMS = 8, LIMIT = 8, PER_ITEM = 2;
    static final String NO_ANSWER = "Nothing in your Stash answers this, so there is no answer to give.";

    static final class Passage {
        long itemId; String kind = "text", text = "", title = "", link = "", source = "";
        double score;
        Passage() {}
        Passage(long itemId, String kind, String text) { this.itemId = itemId; this.kind = kind; this.text = text; }
    }

    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");

    /** Plain text cut at sentence ends into pieces of about TARGET characters (never more than HARD_MAX). */
    static List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        for (String s : SENTENCE.split(t)) {
            while (s.length() > HARD_MAX) { // a very long "sentence": cut at a space
                int cut = s.lastIndexOf(' ', HARD_MAX);
                if (cut < HARD_MAX / 2) cut = HARD_MAX;
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(s.substring(0, cut).trim());
                s = s.substring(cut).trim();
            }
            if (cur.length() > 0 && cur.length() + 1 + s.length() > HARD_MAX) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(s);
            if (cur.length() >= TARGET) { out.add(cur.toString()); cur.setLength(0); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** The key words of a question: no stop words, longest first, at most MAX_TERMS. */
    static List<String> terms(String question) {
        LinkedHashSet<String> seen = new LinkedHashSet<>(Ranker.tokens(question));
        List<String> l = new ArrayList<>(seen);
        Collections.sort(l, new Comparator<String>() {
            @Override public int compare(String a, String b) { return Integer.compare(b.length(), a.length()); }
        });
        return l.size() > MAX_TERMS ? new ArrayList<>(l.subList(0, MAX_TERMS)) : l;
    }

    /** An SQLite FTS4 MATCH expression: every key word as a prefix (so "model" finds "models"), joined by OR. Empty when there is none. */
    static String matchQuery(String question) {
        StringBuilder b = new StringBuilder();
        for (String t : terms(question)) {
            if (!t.matches("[\\p{L}\\p{N}]+")) continue;
            if (b.length() > 0) b.append(" OR ");
            b.append(t).append('*');
        }
        return b.toString();
    }

    /** Orders candidate passages by how many rare question words they hold, highlights and your notes first; keeps at most PER_ITEM from one item. */
    static List<Passage> rank(String question, List<Passage> cands, int limit) {
        List<String> qs = terms(question);
        List<Set<String>> sets = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (Passage p : cands) {
            Set<String> w = new HashSet<>(Ranker.tokens(p.text));
            sets.add(w);
            for (String q : qs) if (startsWithAny(w, q)) df.merge(q, 1, Integer::sum);
        }
        int n = Math.max(1, cands.size());
        for (int i = 0; i < cands.size(); i++) {
            Passage p = cands.get(i);
            double s = 0;
            for (String q : qs) {
                if (!startsWithAny(sets.get(i), q)) continue;
                int d = df.containsKey(q) ? df.get(q) : 0;
                s += Math.log(1 + (double) n / (1 + d)) + 0.5;
            }
            if (p.kind.equals("hl") || p.kind.equals("note")) s *= 1.3;
            p.score = s;
        }
        List<Passage> sorted = new ArrayList<>(cands);
        Collections.sort(sorted, new Comparator<Passage>() {
            @Override public int compare(Passage a, Passage b) { return Double.compare(b.score, a.score); }
        });
        List<Passage> out = new ArrayList<>();
        Map<Long, Integer> per = new HashMap<>();
        for (Passage p : sorted) {
            if (p.score <= 0 || out.size() >= limit) continue;
            int c = per.containsKey(p.itemId) ? per.get(p.itemId) : 0;
            if (c >= PER_ITEM) continue;
            per.put(p.itemId, c + 1);
            out.add(p);
        }
        return out;
    }

    private static boolean startsWithAny(Set<String> words, String prefix) {
        for (String w : words) if (w.startsWith(prefix)) return true;
        return false;
    }

    private static final Pattern CITE = Pattern.compile("\\[(\\d{1,3})\\]");

    /** The answer with every citation [k] outside 1..count removed, so nothing points at a passage that was not sent. */
    static String checkCitations(String answer, int count) {
        Matcher m = CITE.matcher(answer == null ? "" : answer);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            int k = Integer.parseInt(m.group(1));
            m.appendReplacement(b, k >= 1 && k <= count ? Matcher.quoteReplacement(m.group()) : "");
        }
        m.appendTail(b);
        return b.toString().replaceAll(" {2,}", " ").trim();
    }

    /** How many different citations in range the answer has. */
    static int citations(String answer, int count) {
        Set<Integer> seen = new HashSet<>();
        Matcher m = CITE.matcher(answer == null ? "" : answer);
        while (m.find()) { int k = Integer.parseInt(m.group(1)); if (k >= 1 && k <= count) seen.add(k); }
        return seen.size();
    }
}
