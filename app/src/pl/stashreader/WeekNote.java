package pl.stashreader;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/** The automatic week note: what you kept last week, as one Markdown note. Local text only, no Gemini. No Android imports. */
final class WeekNote {
    private WeekNote() {}

    static final class Item {
        long id, keptAt; String title, link, source, topic, note;
        final List<String> highlights = new ArrayList<>();
        /** Highlight ids in the same order as highlights (may be empty in tests). */
        final List<Long> highlightIds = new ArrayList<>();
        /** The item's own note file name ("" when unknown), the topics it matches, and its text for a quoted sentence. */
        String file = "", text = "";
        final List<String> topics = new ArrayList<>();
        Item(long id, String title, String link, String source, String topic, long keptAt, String note) {
            this.id = id; this.title = title == null ? "" : title; this.link = link == null ? "" : link; this.source = source == null ? "" : source;
            this.topic = topic == null ? "" : topic; this.keptAt = keptAt; this.note = note == null ? "" : note;
        }
    }

    /** A recall card answered "Again": what was asked and which item it came from. */
    static final class Again {
        final String text, title;
        Again(String text, String title) { this.text = text == null ? "" : text; this.title = title == null ? "" : title; }
    }

    static final String OTHER = "Other";
    static final String MY_WORDS = "## In my words";
    static final int REMEMBER = 3;

    /** What the note says besides the kept items: your own line, and how much you looked at. seen below 0 means unknown. */
    static final class Extra {
        String answer = "";
        int seen = -1;
        final List<String> quiet = new ArrayList<>();
    }

    /** A highlight chosen for "To remember": the item and the place of the highlight in its list. */
    static final class Pick {
        final Item item; final int index;
        Pick(Item item, int index) { this.item = item; this.index = index; }
        String text() { return item.highlights.get(index); }
    }

    /** Monday 00:00 (in tz) of the week that holds this moment. */
    static long weekStart(long when, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(when);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        int back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday 0 ... Sunday 6
        c.add(Calendar.DAY_OF_MONTH, -back);
        return c.getTimeInMillis();
    }

    /** Monday 00:00 of the week before the one that starts at start (a clock change does not matter). */
    static long previousWeek(long start, TimeZone tz) { return shift(start, tz, -7); }

    /** Monday 00:00 of the next week, which is the end of this one (not included). */
    static long nextWeek(long start, TimeZone tz) { return shift(start, tz, 7); }

    /** The Sunday of the week, the last whole day. */
    static long lastDay(long start, TimeZone tz) { return shift(start, tz, 6); }

    private static long shift(long start, TimeZone tz, int days) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(start);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    static String fileName(long start, TimeZone tz) { return MarkdownExport.day(start, tz) + " week.md"; }

    static String title(long start, TimeZone tz) { return "Week " + MarkdownExport.day(start, tz) + " to " + MarkdownExport.day(lastDay(start, tz), tz); }

    /** True when the moment falls in the week that starts at start. */
    static boolean inWeek(long when, long start, TimeZone tz) { return when >= start && when < nextWeek(start, tz); }

    /**
     * Which week to write on this start: the week before the current one, unless a note for it exists already.
     * Returns the Monday of that week, or -1 when there is nothing to do. A written week is never written again.
     */
    static long due(long now, TimeZone tz, boolean alreadyWritten) {
        long prev = previousWeek(weekStart(now, tz), tz);
        return alreadyWritten ? -1 : prev;
    }

    /** The note for the week that starts at start, or "" when nothing was kept in it. Pairs are {id, id} in any order:
     * only pairs where both items were kept this week are shown, and the later one "builds on" the earlier one.
     * The same input always gives the same text.
     */
    static String build(long start, TimeZone tz, List<Item> kept, List<long[]> pairs, List<Again> again) { return build(start, tz, kept, pairs, again, new Extra()); }

    /** The items kept in the week, in the order the note uses. */
    static List<Item> inWeek(long start, TimeZone tz, List<Item> kept) {
        List<Item> items = new ArrayList<>();
        for (Item it : kept) if (inWeek(it.keptAt, start, tz)) items.add(it);
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                int c = Long.compare(a.keptAt, b.keptAt);
                if (c != 0) return c;
                c = a.title.compareToIgnoreCase(b.title);
                return c != 0 ? c : Long.compare(a.id, b.id);
            }
        });
        return items;
    }

    /** Groups of two or more items of the week that build on each other, in note order; pairs with other weeks are ignored. */
    static List<List<Item>> threads(List<Item> items, List<long[]> pairs) {
        Map<Long, Integer> at = new HashMap<>();
        for (int i = 0; i < items.size(); i++) at.put(items.get(i).id, i);
        int[] parent = new int[items.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        if (pairs != null) for (long[] p : pairs) {
            Integer x = at.get(p[0]), y = at.get(p[1]);
            if (x == null || y == null || x.equals(y)) continue;
            int rx = root(parent, x), ry = root(parent, y);
            if (rx != ry) parent[Math.max(rx, ry)] = Math.min(rx, ry);
        }
        Map<Integer, List<Item>> g = new TreeMap<>();
        for (int i = 0; i < items.size(); i++) {
            int r = root(parent, i);
            if (!g.containsKey(r)) g.put(r, new ArrayList<Item>());
            g.get(r).add(items.get(i));
        }
        List<List<Item>> out = new ArrayList<>();
        for (List<Item> l : g.values()) if (l.size() > 1) out.add(l);
        return out;
    }

    private static int root(int[] p, int x) { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; }

    /** A thread's name: the two rarest words (among the week's items) that at least two of its items share; longer words win a tie. */
    static String threadName(List<Item> thread, List<Item> items) {
        final Map<String, Integer> df = new HashMap<>();
        for (Item it : items) for (String w : words(it)) df.merge(w, 1, Integer::sum);
        Map<String, Integer> inThread = new HashMap<>();
        for (Item it : thread) for (String w : words(it)) inThread.merge(w, 1, Integer::sum);
        List<String> shared = new ArrayList<>();
        for (Map.Entry<String, Integer> e : inThread.entrySet()) if (e.getValue() >= 2 && e.getKey().length() >= 3) shared.add(e.getKey());
        Collections.sort(shared, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int c = Integer.compare(df.get(a), df.get(b));
                if (c != 0) return c;
                c = Integer.compare(b.length(), a.length());
                return c != 0 ? c : a.compareTo(b);
            }
        });
        if (shared.isEmpty()) return thread.get(0).topic.trim().isEmpty() ? OTHER : MarkdownExport.oneLine(thread.get(0).topic);
        return shared.size() == 1 ? shared.get(0) : shared.get(0) + ", " + shared.get(1);
    }

    private static Set<String> words(Item it) {
        StringBuilder b = new StringBuilder(it.title);
        for (String h : it.highlights) b.append(' ').append(h);
        return new HashSet<>(Ranker.tokens(b.toString()));
    }

    /**
     * Up to three highlights worth seeing again, at most one per item: items in a thread first, then items with your note,
     * then the longer highlight (20 to 300 characters), then the earlier item.
     */
    static List<Pick> toRemember(final List<Item> items, List<long[]> pairs) {
        final Set<Long> threaded = new HashSet<>();
        for (List<Item> t : threads(items, pairs)) for (Item it : t) threaded.add(it.id);
        List<Pick> cands = new ArrayList<>();
        for (Item it : items) {
            int best = -1;
            for (int i = 0; i < it.highlights.size(); i++) {
                int n = it.highlights.get(i).trim().length();
                if (n < 20 || n > 300) continue;
                if (best < 0 || n > it.highlights.get(best).trim().length()) best = i;
            }
            if (best >= 0) cands.add(new Pick(it, best));
        }
        Collections.sort(cands, new Comparator<Pick>() {
            @Override public int compare(Pick a, Pick b) {
                int c = Boolean.compare(threaded.contains(b.item.id), threaded.contains(a.item.id));
                if (c != 0) return c;
                c = Boolean.compare(!b.item.note.trim().isEmpty(), !a.item.note.trim().isEmpty());
                if (c != 0) return c;
                c = Integer.compare(b.text().trim().length(), a.text().trim().length());
                return c != 0 ? c : Integer.compare(items.indexOf(a.item), items.indexOf(b.item));
            }
        });
        return cands.size() > REMEMBER ? new ArrayList<>(cands.subList(0, REMEMBER)) : cands;
    }

    /** One sentence of the article to stand for it: the one (40 to 300 characters) that holds most words of the title, the first on a tie. */
    static String keySentence(String title, String text) {
        if (text == null || text.trim().isEmpty()) return "";
        Set<String> want = new HashSet<>(Ranker.tokens(title));
        String best = ""; int bestHit = -1;
        for (String s : Listen.sentences("", true, text)) {
            String t = s.trim();
            if (t.length() < 40 || t.length() > 300) continue;
            int hit = 0;
            for (String w : new HashSet<>(Ranker.tokens(t))) if (want.contains(w)) hit++;
            if (hit > bestHit) { bestHit = hit; best = t; }
        }
        return best;
    }

    /** An Obsidian tag from a topic: lower case, spaces become "-", only letters, digits, "-", "_" and "/"; a number alone is no tag. */
    static String tag(String topic) {
        String t = topic.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-").replaceAll("[^\\p{L}\\p{N}_/-]", "");
        return t.matches("[0-9]*") ? "" : t;
    }

    /** The note with your own line at the top (under the heading); an earlier answer is replaced. An empty answer removes it. */
    static String withAnswer(String md, String answer) {
        if (md == null) return "";
        String a = answer == null ? "" : answer.trim().replaceAll("\\s*\\n\\s*", " ");
        int h = md.indexOf("\n# ");
        if (h < 0) return md;
        int lineEnd = md.indexOf('\n', h + 1);
        if (lineEnd < 0) return md;
        String head = md.substring(0, lineEnd + 1), rest = md.substring(lineEnd + 1);
        String mine = "\n" + MY_WORDS + "\n\n";
        if (rest.startsWith(mine)) {
            int next = rest.indexOf("\n\n", mine.length());
            rest = next < 0 ? "" : rest.substring(next + 1);
        }
        if (a.isEmpty()) return head + rest;
        return head + mine + a + "\n" + rest;
    }

    static String build(long start, TimeZone tz, List<Item> kept, List<long[]> pairs, List<Again> again, Extra extra) {
        List<Item> items = inWeek(start, tz, kept);
        if (items.isEmpty()) return "";

        Map<String, Integer> titleCount = new HashMap<>();
        for (Item it : items) { String k = MarkdownExport.oneLine(it.title).toLowerCase(Locale.ROOT); Integer n = titleCount.get(k); titleCount.put(k, n == null ? 1 : n + 1); }
        Map<Long, String> label = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (Item it : items) {
            String t = MarkdownExport.oneLine(it.title);
            if (t.isEmpty()) t = "Untitled";
            String l = t;
            if (titleCount.get(t.toLowerCase(Locale.ROOT)) > 1) { // the same title twice: tell them apart
                String s = MarkdownExport.oneLine(it.source);
                l = s.isEmpty() ? t : t + " (" + s + ")";
            }
            String base = l; int n = 2;
            while (!used.add(l)) l = base + " #" + n++;
            label.put(it.id, l);
        }
        List<List<Item>> threads = threads(items, pairs);
        Set<Long> inThread = new HashSet<>();
        for (List<Item> t : threads) for (Item it : t) inThread.add(it.id);
        Map<String, List<Item>> byTopic = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Item it : items) {
            if (inThread.contains(it.id)) continue;
            String tp = it.topic.trim().isEmpty() ? OTHER : MarkdownExport.oneLine(it.topic);
            List<Item> l = byTopic.get(tp);
            if (l == null) { l = new ArrayList<>(); byTopic.put(tp, l); }
            l.add(it);
        }
        String a = MarkdownExport.day(start, tz), z = MarkdownExport.day(lastDay(start, tz), tz);
        StringBuilder b = new StringBuilder();
        java.util.TreeSet<String> tags = new java.util.TreeSet<>();
        tags.add("stash/week");
        for (Item it : items) {
            List<String> ts = new ArrayList<>(it.topics);
            if (!it.topic.trim().isEmpty()) ts.add(it.topic);
            for (String t : ts) { String g = tag(t); if (!g.isEmpty()) tags.add(g); }
        }
        b.append("---\ntitle: ").append(MarkdownExport.yaml(title(start, tz))).append("\nweek: ").append(a).append("\ntags:\n");
        for (String g : tags) b.append("  - ").append(g).append('\n');
        b.append("---\n\n");
        b.append("# Week ").append(a).append(" to ").append(z).append("\n\n");
        b.append("You kept ").append(items.size()).append(items.size() == 1 ? " item" : " items").append(" this week.\n\n");
        for (List<Item> t : threads) {
            b.append("## Thread: ").append(threadName(t, items)).append("\n\n");
            for (Item it : t) item(b, it, label.get(it.id));
        }
        List<String> topics = new ArrayList<>(byTopic.keySet());
        if (topics.remove(OTHER)) topics.add(OTHER);
        for (String tp : topics) {
            b.append("## ").append(tp).append("\n\n");
            for (Item it : byTopic.get(tp)) item(b, it, label.get(it.id));
        }
        List<Pick> rem = toRemember(items, pairs);
        if (!rem.isEmpty()) {
            b.append("## To remember\n\nThese come back as recall cards this week:\n\n");
            for (Pick p : rem) b.append("- ").append(MarkdownExport.oneLine(p.text())).append(" (").append(label.get(p.item.id)).append(")\n");
            b.append('\n');
        }
        Map<Long, Item> byId = new HashMap<>();
        for (Item it : items) byId.put(it.id, it);
        TreeMap<String, String> lines = new TreeMap<>();
        if (pairs != null) for (long[] p : pairs) {
            Item x = byId.get(p[0]), y = byId.get(p[1]);
            if (x == null || y == null || x.id == y.id) continue;
            Item later = (x.keptAt > y.keptAt || (x.keptAt == y.keptAt && x.id > y.id)) ? x : y;
            Item earlier = later == x ? y : x;
            lines.put(String.format(Locale.ROOT, "%019d-%019d", later.id, earlier.id), "- " + label.get(later.id) + " builds on " + label.get(earlier.id));
        }
        if (!lines.isEmpty()) {
            b.append("## Builds on\n\n");
            for (String l : lines.values()) b.append(l).append('\n');
            b.append('\n');
        }
        if (again != null && !again.isEmpty()) {
            List<Again> ag = new ArrayList<>(again);
            Collections.sort(ag, new Comparator<Again>() {
                @Override public int compare(Again p, Again q) { int c = p.title.compareToIgnoreCase(q.title); return c != 0 ? c : p.text.compareTo(q.text); }
            });
            b.append("## Asked again\n\nRecall cards you answered \"Again tomorrow\" this week:\n\n");
            for (Again g : ag) {
                String t = MarkdownExport.oneLine(g.text);
                b.append("- ").append(t.isEmpty() ? "(a highlight)" : Sanitizer.shorten(t, 200));
                if (!g.title.isEmpty()) b.append(" (").append(MarkdownExport.oneLine(g.title)).append(")");
                b.append('\n');
            }
            b.append('\n');
        }
        if (extra != null && extra.seen >= 0) {
            b.append("You kept ").append(items.size()).append(" and looked at ").append(extra.seen).append(extra.seen == 1 ? " other card" : " other cards").append(" in the Feed.");
            if (!extra.quiet.isEmpty()) {
                b.append(" Quietest sources: ");
                for (int i = 0; i < extra.quiet.size() && i < 3; i++) { if (i > 0) b.append(", "); b.append(MarkdownExport.oneLine(extra.quiet.get(i))); }
                b.append('.');
            }
            b.append("\n\n");
        }
        String out = b.toString();
        if (extra != null && !extra.answer.trim().isEmpty()) out = withAnswer(out, extra.answer);
        while (out.endsWith("\n\n")) out = out.substring(0, out.length() - 1);
        return out;
    }

    /** One item: its heading (with the link), the link to its own note, then your note, else its first highlight, else a sentence of the article. */
    private static void item(StringBuilder b, Item it, String label) {
        String lab = label.replace("[", "(").replace("]", ")");
        if (it.link.isEmpty()) b.append("### ").append(lab).append("\n\n");
        else b.append("### [").append(lab).append("](").append(it.link.replace(")", "%29").replace(" ", "%20")).append(")\n\n");
        String f = it.file.endsWith(".md") ? it.file.substring(0, it.file.length() - 3) : it.file;
        if (!f.isEmpty() && !f.matches(".*[\\[\\]#^|].*")) b.append("Note: [[").append(f).append("]]\n\n");
        int count = 0; String hl = "";
        for (String h : it.highlights) if (!h.trim().isEmpty()) { if (count == 0) hl = h; count++; }
        if (!it.note.trim().isEmpty()) b.append("My note: ").append(MarkdownExport.oneLine(it.note)).append("\n\n");
        if (count > 0) {
            b.append(MarkdownExport.quote(hl)).append("\n\n");
            if (count > 1) b.append("(").append(count - 1).append(count == 2 ? " more highlight" : " more highlights").append(" in the note)\n\n");
        } else if (it.note.trim().isEmpty()) {
            String s = keySentence(it.title, it.text);
            if (!s.isEmpty()) b.append("Sentence from the article: “").append(MarkdownExport.oneLine(s)).append("”\n\n");
        }
    }
}
