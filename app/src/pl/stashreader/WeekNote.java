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
    static String build(long start, TimeZone tz, List<Item> kept, List<long[]> pairs, List<Again> again) {
        List<Item> items = new ArrayList<>();
        for (Item it : kept) if (inWeek(it.keptAt, start, tz)) items.add(it);
        if (items.isEmpty()) return "";
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                int c = Long.compare(a.keptAt, b.keptAt);
                if (c != 0) return c;
                c = a.title.compareToIgnoreCase(b.title);
                return c != 0 ? c : Long.compare(a.id, b.id);
            }
        });
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
        Map<String, List<Item>> byTopic = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Item it : items) {
            String tp = it.topic.trim().isEmpty() ? OTHER : MarkdownExport.oneLine(it.topic);
            List<Item> l = byTopic.get(tp);
            if (l == null) { l = new ArrayList<>(); byTopic.put(tp, l); }
            l.add(it);
        }
        String a = MarkdownExport.day(start, tz), z = MarkdownExport.day(lastDay(start, tz), tz);
        StringBuilder b = new StringBuilder();
        b.append("---\ntitle: ").append(MarkdownExport.yaml(title(start, tz))).append("\nweek: ").append(a).append("\n---\n\n");
        b.append("# Week ").append(a).append(" to ").append(z).append("\n\n");
        b.append("You kept ").append(items.size()).append(items.size() == 1 ? " item" : " items").append(" this week.\n\n");
        List<String> topics = new ArrayList<>(byTopic.keySet());
        if (topics.remove(OTHER)) topics.add(OTHER);
        for (String tp : topics) {
            b.append("## ").append(tp).append("\n\n");
            for (Item it : byTopic.get(tp)) {
                String lab = label.get(it.id).replace("[", "(").replace("]", ")");
                if (it.link.isEmpty()) b.append("### ").append(lab).append("\n\n");
                else b.append("### [").append(lab).append("](").append(it.link.replace(")", "%29").replace(" ", "%20")).append(")\n\n");
                for (String h : it.highlights) { if (!h.trim().isEmpty()) b.append(MarkdownExport.quote(h)).append("\n\n"); }
                if (!it.note.trim().isEmpty()) b.append("My note: ").append(MarkdownExport.oneLine(it.note)).append("\n\n");
            }
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
        String out = b.toString();
        while (out.endsWith("\n\n")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
