package pl.stashreader;

import java.util.ArrayList;
import java.util.List;

/** Listen to Anything: how a text becomes sentences to speak, and which language to speak it in. No Android imports. */
final class Listen {
    private Listen() {}

    /** A sentence longer than this is cut at a comma or a space, so the speech engine never gets a huge piece. */
    static final int MAX_SENTENCE = 320, MIN_SENTENCE = 25;
    static final int[] RATES = {80, 100, 120, 150, 180};

    /** The title, a warning when only the feed summary is available, then the text in sentences. Index = the place that is remembered. */
    static List<String> sentences(String title, boolean fullText, String text) {
        List<String> out = new ArrayList<>();
        String t = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (!t.isEmpty()) out.add(t + ".");
        if (!fullText) out.add("This is only the summary from the feed, not the full article.");
        String body = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (body.isEmpty()) return out;
        StringBuilder carry = new StringBuilder();
        for (String s : body.split("(?<=[.!?…])\\s+")) {
            if (carry.length() > 0) { carry.append(' '); }
            carry.append(s);
            if (carry.length() < MIN_SENTENCE) continue; // a lone "1." or "Dr." is joined to the next sentence
            addCut(out, carry.toString());
            carry.setLength(0);
        }
        if (carry.length() > 0) addCut(out, carry.toString());
        return out;
    }

    private static void addCut(List<String> out, String s) {
        while (s.length() > MAX_SENTENCE) {
            int cut = s.lastIndexOf(", ", MAX_SENTENCE);
            if (cut < MAX_SENTENCE / 3) cut = s.lastIndexOf(' ', MAX_SENTENCE);
            if (cut < MAX_SENTENCE / 3) cut = MAX_SENTENCE;
            out.add(s.substring(0, cut + 1).trim());
            s = s.substring(cut + 1).trim();
        }
        if (!s.isEmpty()) out.add(s);
    }

    /** "pl" when the text looks Polish (Polish letters make up at least 1.5% of its letters), else "" for the phone's own language. */
    static String language(String text) {
        if (text == null) return "";
        int letters = 0, polish = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if ("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ".indexOf(c) >= 0) polish++;
        }
        return letters >= 40 && polish * 1000 >= letters * 15 ? "pl" : "";
    }

    /** The speed in percent, kept to the choices in the settings. */
    static int rate(int pct) {
        int best = RATES[1];
        for (int r : RATES) if (Math.abs(r - pct) < Math.abs(best - pct)) best = r;
        return best;
    }

    /** Where to start: the saved sentence, but never past the end; an item that was heard to the end starts again from the top. */
    static int start(int saved, int total, boolean done) {
        if (done || saved < 0 || saved >= total) return 0;
        return saved;
    }
}
