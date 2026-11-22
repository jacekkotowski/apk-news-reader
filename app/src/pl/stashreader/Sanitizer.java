package pl.stashreader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Turns feed HTML into safe HTML or plain text. No Android imports.
 * Only a short list of tags survives and they carry no attributes, except links, which keep an http(s) href.
 * Scripts, styles, iframes, forms, svg and the like are dropped together with their content.
 */
final class Sanitizer {
    private Sanitizer() {}

    static final int MAX_IN = 120000;

    private static final String[] ALLOWED = {"p", "br", "a", "ul", "ol", "li", "strong", "b", "em", "i", "code", "pre", "blockquote",
        "h1", "h2", "h3", "h4", "h5", "h6", "hr", "sub", "sup", "table", "thead", "tbody", "tr", "td", "th"};
    private static final String[] DROP_WITH_CONTENT = {"script", "style", "iframe", "object", "embed", "svg", "form", "noscript",
        "template", "textarea", "select", "head", "title", "math", "canvas", "audio", "video", "button", "input"};
    private static final String[] BLOCK = {"p", "br", "li", "ul", "ol", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "tr", "div", "table"};

    private static boolean in(String[] set, String s) { for (String x : set) if (x.equals(s)) return true; return false; }

    /** Safe HTML: allowed tags, no event attributes, no scripts. Tags are balanced. */
    static String clean(String html) {
        if (html == null) return "";
        String s = html.length() > MAX_IN ? html.substring(0, MAX_IN) : html;
        StringBuilder out = new StringBuilder();
        Deque<String> open = new ArrayDeque<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c != '<') { out.append(c); i++; continue; }
            if (s.startsWith("<!--", i)) { int e = s.indexOf("-->", i + 4); i = e < 0 ? n : e + 3; continue; }
            Tag t = readTag(s, i);
            if (t == null) { out.append("&lt;"); i++; continue; }
            i = t.end;
            if (t.name.isEmpty()) continue; // <! ...> or <? ...>
            if (in(DROP_WITH_CONTENT, t.name)) {
                if (!t.closing && !t.selfClosing) i = skipPast(s, i, t.name);
                continue;
            }
            if (!in(ALLOWED, t.name)) continue;
            if (t.closing) {
                if (!open.contains(t.name)) continue;
                while (!open.isEmpty()) {
                    String top = open.pop();
                    out.append("</").append(top).append('>');
                    if (top.equals(t.name)) break;
                }
                continue;
            }
            if (t.name.equals("br") || t.name.equals("hr")) { out.append('<').append(t.name).append('>'); continue; }
            out.append('<').append(t.name);
            if (t.name.equals("a")) {
                String href = attr(t.attrs, "href");
                if (href != null && isHttp(href)) out.append(" href=\"").append(href.replace("\"", "%22").replace("<", "%3C").replace(">", "%3E")).append('"');
            }
            out.append('>');
            open.push(t.name);
        }
        while (!open.isEmpty()) out.append("</").append(open.pop()).append('>');
        return out.toString().trim();
    }

    /** Plain text with entities decoded and white space collapsed. */
    static String text(String html) {
        if (html == null) return "";
        String s = html.length() > MAX_IN ? html.substring(0, MAX_IN) : html;
        StringBuilder out = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c != '<') { out.append(c); i++; continue; }
            if (s.startsWith("<!--", i)) { int e = s.indexOf("-->", i + 4); i = e < 0 ? n : e + 3; continue; }
            Tag t = readTag(s, i);
            if (t == null) { out.append(c); i++; continue; }
            i = t.end;
            if (in(DROP_WITH_CONTENT, t.name) && !t.closing && !t.selfClosing) { i = skipPast(s, i, t.name); continue; }
            if (in(BLOCK, t.name)) out.append(' ');
        }
        return collapse(decode(out.toString()));
    }

    static String collapse(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean space = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ' ' || c == '​') { if (!space) b.append(' '); space = true; }
            else { b.append(c); space = false; }
        }
        int e = b.length();
        if (e > 0 && b.charAt(e - 1) == ' ') b.setLength(e - 1);
        return b.toString();
    }

    /** First {@code max} characters, cut at a word boundary, with an ellipsis when something was cut. */
    static String shorten(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return s.substring(0, cut) + "…";
    }

    static int words(String text) {
        if (text.isEmpty()) return 0;
        int n = 0; boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            boolean w = !Character.isWhitespace(text.charAt(i));
            if (w && !in) n++;
            in = w;
        }
        return n;
    }

    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 10) {
                    String e = s.substring(i + 1, semi);
                    String r = entity(e);
                    if (r != null) { b.append(r); i = semi + 1; continue; }
                }
            }
            b.append(c); i++;
        }
        return b.toString();
    }

    private static String entity(String e) {
        switch (e) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "hellip": return "…";
            case "mdash": return "—";
            case "ndash": return "–";
            case "lsquo": return "‘";
            case "rsquo": return "’";
            case "ldquo": return "“";
            case "rdquo": return "”";
            default: break;
        }
        if (e.length() > 1 && e.charAt(0) == '#') {
            try {
                int cp = e.charAt(1) == 'x' || e.charAt(1) == 'X' ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                if (cp > 0 && cp <= 0x10FFFF) return new String(Character.toChars(cp));
            } catch (RuntimeException ignored) { }
        }
        return null;
    }

    private static final class Tag { String name = ""; String attrs = ""; boolean closing, selfClosing; int end; }

    /** Reads a tag starting at '<'. Returns null when the '<' is plain text. */
    private static Tag readTag(String s, int start) {
        int n = s.length(), i = start + 1;
        if (i >= n) return null;
        Tag t = new Tag();
        char c = s.charAt(i);
        if (c == '/') { t.closing = true; i++; if (i >= n) return null; c = s.charAt(i); }
        if (c == '!' || c == '?') {
            int e = s.indexOf('>', i);
            t.end = e < 0 ? n : e + 1;
            return t;
        }
        if (!Character.isLetter(c)) return null;
        int ns = i;
        while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '-' || s.charAt(i) == ':')) i++;
        t.name = s.substring(ns, i).toLowerCase(Locale.ROOT);
        int as = i; char quote = 0;
        while (i < n) {
            char d = s.charAt(i);
            if (quote != 0) { if (d == quote) quote = 0; }
            else if (d == '"' || d == '\'') quote = d;
            else if (d == '>') break;
            i++;
        }
        t.attrs = s.substring(as, Math.min(i, n));
        t.selfClosing = t.attrs.endsWith("/");
        t.end = i < n ? i + 1 : n;
        return t;
    }

    /** Index just after the closing tag of {@code name}, or the end of the text when there is none. */
    private static int skipPast(String s, int from, String name) {
        String lower = s.toLowerCase(Locale.ROOT);
        int e = lower.indexOf("</" + name, from);
        if (e < 0) return s.length();
        int g = s.indexOf('>', e);
        return g < 0 ? s.length() : g + 1;
    }

    /** Value of an attribute from the text after a tag name, or null. */
    static String attr(String attrs, String name) {
        String lower = attrs.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int p = lower.indexOf(name, from);
            if (p < 0) return null;
            boolean startOk = p == 0 || Character.isWhitespace(lower.charAt(p - 1)) || lower.charAt(p - 1) == '"' || lower.charAt(p - 1) == '\'';
            int q = p + name.length();
            while (q < lower.length() && Character.isWhitespace(lower.charAt(q))) q++;
            if (startOk && q < lower.length() && lower.charAt(q) == '=') {
                q++;
                while (q < attrs.length() && Character.isWhitespace(attrs.charAt(q))) q++;
                if (q >= attrs.length()) return "";
                char d = attrs.charAt(q);
                if (d == '"' || d == '\'') { int e = attrs.indexOf(d, q + 1); return e < 0 ? attrs.substring(q + 1) : attrs.substring(q + 1, e); }
                int e = q;
                while (e < attrs.length() && !Character.isWhitespace(attrs.charAt(e))) e++;
                return attrs.substring(q, e);
            }
            from = p + 1;
        }
    }

    static boolean isHttp(String href) {
        String h = href.trim().toLowerCase(Locale.ROOT);
        return h.startsWith("https://") || h.startsWith("http://");
    }
}
