package pl.stashreader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Notes made on their own after a keep: how many Gemini may be asked today and when the next one may go. No Android imports.
 * At most one request a minute and 30 notes a day; after Gemini says its free limit is reached, nothing more until the next day.
 */
final class AutoNotes {
    private AutoNotes() {}

    static final int PER_DAY = 30;
    static final long GAP_MS = 60000L;

    /** Today's counts, kept in the app's settings. day and stop are local dates, yyyy-MM-dd. */
    static final class Budget {
        String day = "", stop = "";
        int notes, paths;
        long last;
    }

    static String day(long now, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(tz);
        return f.format(new Date(now));
    }

    /** The counts start again at local midnight. */
    static Budget roll(Budget b, String today) {
        if (!today.equals(b.day)) { b.day = today; b.notes = 0; b.paths = 0; }
        return b;
    }

    /** -1: nothing more today (the daily number is reached, or Gemini said its limit is reached today); otherwise the milliseconds to wait, 0 = now. */
    static long waitMs(Budget b, String today, long now) {
        roll(b, today);
        if (today.equals(b.stop) || b.notes >= PER_DAY) return -1;
        long since = now - b.last;
        return since >= GAP_MS || since < 0 ? 0 : GAP_MS - since;
    }

    /** Notes are not made for podcast episodes (Gemini gets no audio from a feed) or twice for one item. */
    static boolean skip(String kind, boolean hasNotes) { return hasNotes || "audio".equals(kind); }

    /** The one line in Sources: "Gemini today: 3 notes, 1 path search". */
    static String line(Budget b, String today) {
        roll(b, today);
        String s = "Gemini today: " + b.notes + (b.notes == 1 ? " note, " : " notes, ") + b.paths + (b.paths == 1 ? " path search" : " path searches");
        if (today.equals(b.stop)) s += ". Gemini's free limit was reached; notes go on tomorrow";
        else if (b.notes >= PER_DAY) s += ". That is today's " + PER_DAY + "; notes go on tomorrow";
        return s;
    }
}
