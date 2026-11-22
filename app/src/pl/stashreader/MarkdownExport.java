package pl.stashreader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Builds one Markdown note per kept item. Pure logic, no Android imports. */
final class MarkdownExport {
    private MarkdownExport() {}

    static final class Highlight { final String text, note; Highlight(String t, String n) { text = t; note = n == null ? "" : n; } }
    static final class Note {
        String title = "", source = "", url = "", note = "";
        long published, keptAt;
        final List<String> topics = new ArrayList<>();
        final List<Highlight> highlights = new ArrayList<>();
    }

    /** The whole note: YAML front matter, then the highlights as block quotes, then your own note. */
    static String note(Note n, TimeZone tz) {
        StringBuilder b = new StringBuilder();
        b.append("---\n");
        b.append("title: ").append(yaml(n.title)).append('\n');
        b.append("source: ").append(yaml(n.source)).append('\n');
        if (!n.url.isEmpty()) b.append("url: ").append(yaml(n.url)).append('\n');
        if (n.published > 0) b.append("date: ").append(day(n.published, tz)).append('\n');
        if (n.keptAt > 0) b.append("kept: ").append(day(n.keptAt, tz)).append('\n');
        b.append("topics: [");
        for (int i = 0; i < n.topics.size(); i++) { if (i > 0) b.append(", "); b.append(yaml(n.topics.get(i))); }
        b.append("]\n---\n\n");
        b.append("# ").append(oneLine(n.title)).append("\n\n");
        if (!n.url.isEmpty()) b.append('[').append(oneLine(n.source.isEmpty() ? n.url : n.source)).append("](").append(n.url.replace(")", "%29").replace(" ", "%20")).append(")\n\n");
        if (!n.highlights.isEmpty()) {
            b.append("## Highlights\n\n");
            for (Highlight h : n.highlights) {
                b.append(quote(h.text)).append('\n');
                if (!h.note.trim().isEmpty()) b.append("\nNote: ").append(oneLine(h.note)).append('\n');
                b.append('\n');
            }
        }
        if (!n.note.trim().isEmpty()) {
            b.append("## My note\n\n").append(n.note.trim()).append("\n");
        }
        String out = b.toString();
        return out.endsWith("\n\n") ? out.substring(0, out.length() - 1) : out;
    }

    /** "YYYY-MM-DD title.md": the day the item was kept, then a file-name-safe title. */
    static String fileName(Note n, TimeZone tz) {
        String t = n.title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ");
        t = Sanitizer.collapse(t);
        if (t.length() > 80) t = t.substring(0, 80).trim();
        while (t.endsWith(".") || t.endsWith(" ")) t = t.substring(0, t.length() - 1);
        if (t.isEmpty()) t = "Untitled";
        long when = n.keptAt > 0 ? n.keptAt : (n.published > 0 ? n.published : System.currentTimeMillis());
        return day(when, tz) + " " + t + ".md";
    }

    static String day(long ms, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(tz);
        return f.format(new Date(ms));
    }

    /** A double-quoted YAML string on one line. */
    static String yaml(String s) {
        String v = oneLine(s).replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + v + "\"";
    }

    static String oneLine(String s) {
        return s == null ? "" : Sanitizer.collapse(s.replace('\r', ' ').replace('\n', ' '));
    }

    /** Every line of the text becomes a block-quote line; blank lines stay inside the same quote. */
    static String quote(String text) {
        String[] lines = (text == null ? "" : text.trim()).split("\\r?\\n", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) b.append('\n');
            b.append(lines[i].trim().isEmpty() ? ">" : "> " + lines[i].trim());
        }
        return b.toString();
    }
}
