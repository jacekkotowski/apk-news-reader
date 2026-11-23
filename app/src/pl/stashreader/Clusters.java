package pl.stashreader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One Story, Many Sources: groups items from the last few days that are about the same thing. No Android imports.
 * Two items belong together when they have the same link (ignoring "www", tracking parameters and a trailing slash),
 * or when their titles share most of their key words.
 */
final class Clusters {
    private Clusters() {}

    static final long WINDOW_MS = 3L * 24 * 3600 * 1000;
    /** At least this many shared key words, and at least this share of the words of the shorter title. */
    static final int MIN_SHARED = 3;
    static final double MIN_SHARE = 0.55;
    static final int MAX_ITEMS = 700;

    static final class Item {
        final long id; final String title, link, source; final long pub;
        Item(long id, String title, String link, String source, long pub) { this.id = id; this.title = title; this.link = link; this.source = source; this.pub = pub; }
    }

    /** The groups of two or more items, each in input order. Items older than three days (or with no date) are left alone. */
    static List<List<Item>> group(List<Item> items, long now) {
        List<Item> in = new ArrayList<>();
        for (Item it : items) if (it.pub > 0 && now - it.pub <= WINDOW_MS && it.pub <= now + 3600000L && in.size() < MAX_ITEMS) in.add(it);
        int n = in.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        List<Set<String>> words = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Item it : in) { words.add(new HashSet<>(Ranker.tokens(it.title))); keys.add(linkKey(it.link)); }
        Map<String, Integer> firstByLink = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = keys.get(i);
            if (k.isEmpty()) continue;
            Integer j = firstByLink.get(k);
            if (j == null) firstByLink.put(k, i); else union(parent, i, j);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) if (similar(words.get(i), words.get(j))) union(parent, i, j);
        }
        Map<Integer, List<Item>> groups = new HashMap<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int r = find(parent, i);
            if (!groups.containsKey(r)) { groups.put(r, new ArrayList<Item>()); order.add(r); }
            groups.get(r).add(in.get(i));
        }
        List<List<Item>> out = new ArrayList<>();
        for (int r : order) if (groups.get(r).size() > 1) out.add(groups.get(r));
        return out;
    }

    static boolean similar(Set<String> a, Set<String> b) {
        int min = Math.min(a.size(), b.size());
        if (min < MIN_SHARED) return false;
        int shared = 0;
        for (String w : a) if (b.contains(w)) shared++;
        return shared >= MIN_SHARED && shared >= MIN_SHARE * min && shared >= 0.5 * Math.max(a.size(), b.size());
    }

    /** A link without scheme, "www.", fragment, trailing slash and tracking parameters (utm_*, fbclid, gclid, ref), lower case. */
    static String linkKey(String link) {
        if (link == null) return "";
        String s = link.trim().toLowerCase(Locale.ROOT);
        if (!(s.startsWith("http://") || s.startsWith("https://"))) return "";
        s = s.substring(s.indexOf("//") + 2);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        String query = "";
        int q = s.indexOf('?');
        if (q >= 0) { query = s.substring(q + 1); s = s.substring(0, q); }
        if (s.startsWith("www.")) s = s.substring(4);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        StringBuilder keep = new StringBuilder();
        for (String p : query.split("&")) {
            if (p.isEmpty() || p.startsWith("utm_") || p.startsWith("fbclid") || p.startsWith("gclid") || p.startsWith("ref=") || p.startsWith("source=")) continue;
            if (keep.length() > 0) keep.append('&');
            keep.append(p);
        }
        // a bare host is not a story: two items "from example.com" are not the same item
        if (s.indexOf('/') < 0 && keep.length() == 0) return "";
        return keep.length() == 0 ? s : s + "?" + keep;
    }

    private static int find(int[] p, int x) { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; }
    private static void union(int[] p, int a, int b) { int ra = find(p, a), rb = find(p, b); if (ra != rb) p[Math.max(ra, rb)] = Math.min(ra, rb); }
}
