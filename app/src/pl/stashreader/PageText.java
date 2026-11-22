package pl.stashreader;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns downloaded page bytes into text. Pure logic, no Android imports. */
final class PageText {
    private PageText() {}

    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_.:-]+)", Pattern.CASE_INSENSITIVE);

    /** Character set from the Content-Type header, else from a meta tag near the top, else UTF-8. */
    static String decode(byte[] bytes, String contentType) {
        Charset cs = null;
        if (contentType != null) cs = lookup(contentType);
        if (cs == null) {
            String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1);
            int meta = head.toLowerCase(Locale.ROOT).indexOf("<meta");
            if (meta >= 0) cs = lookup(head.substring(meta));
        }
        return new String(bytes, cs == null ? StandardCharsets.UTF_8 : cs);
    }

    private static Charset lookup(String text) {
        Matcher m = CHARSET.matcher(text);
        if (!m.find()) return null;
        try { return Charset.forName(m.group(1)); } catch (RuntimeException e) { return null; }
    }
}
