package pl.stashreader;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads what another app shares to Stash Reader: a web address and, when there is one, a title. No Android imports. */
final class Share {
    private Share() {}

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    /** The first web address in the shared text, without trailing punctuation; "" when there is none. */
    static String extractUrl(String text) {
        if (text == null) return "";
        Matcher m = URL.matcher(text);
        if (!m.find()) return "";
        String u = m.group();
        while (u.length() > 0 && ".,;:!?)]}’”".indexOf(u.charAt(u.length() - 1)) >= 0) u = u.substring(0, u.length() - 1);
        return Sanitizer.isHttp(u) ? u : "";
    }

    /** A title from the share subject, else from the text around the address; "" when neither says anything useful. */
    static String titleFor(String subject, String text, String url) {
        String s = clean(subject);
        if (!s.isEmpty() && !s.toLowerCase(Locale.ROOT).startsWith("http")) return cap(s);
        String t = text == null ? "" : text;
        if (url != null && !url.isEmpty()) t = t.replace(url, " ");
        t = clean(t);
        return t.isEmpty() ? "" : cap(t);
    }

    /** The host name without "www.", used when nothing better is known. */
    static String hostOf(String url) {
        try {
            String h = new URI(url).getHost();
            if (h != null && !h.isEmpty()) return h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception ignored) { }
        return url == null ? "" : url;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        // strip quote marks and separators a share sheet leaves around the title
        s = s.replaceAll("^[\\s\"'“”‘’|:\\-–—]+", "").replaceAll("[\\s\"'“”‘’|:\\-–—]+$", "");
        return s;
    }

    private static String cap(String s) { return s.length() > 200 ? s.substring(0, 200) : s; }
}
