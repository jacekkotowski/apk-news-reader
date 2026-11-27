package pl.stashreader;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI notes: the one function Make notes and the notes made on their own share, and the background worker for the second.
 * The worker makes notes one item at a time for what was kept (after Undo has passed), at most one request a minute and
 * 30 notes a day; when Gemini says its free limit is reached it stops until the next day. The rules are in {@link AutoNotes}.
 */
final class NoteWorker {
    private NoteWorker() {}

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Object LOCK = new Object();

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("stash", Context.MODE_PRIVATE); }

    static AutoNotes.Budget budget(Context c) {
        SharedPreferences p = prefs(c);
        AutoNotes.Budget b = new AutoNotes.Budget();
        b.day = p.getString("gemDay", ""); b.stop = p.getString("gemStop", "");
        b.notes = p.getInt("gemNotes", 0); b.paths = p.getInt("gemPaths", 0); b.last = p.getLong("gemLast", 0);
        return AutoNotes.roll(b, today());
    }

    private static void save(Context c, AutoNotes.Budget b) {
        prefs(c).edit().putString("gemDay", b.day).putString("gemStop", b.stop).putInt("gemNotes", b.notes).putInt("gemPaths", b.paths).putLong("gemLast", b.last).apply();
    }

    static String today() { return AutoNotes.day(System.currentTimeMillis(), TimeZone.getDefault()); }

    /** Counts one Gemini request of today: notes, or a learning path. */
    static void counted(Context c, boolean path) {
        synchronized (LOCK) {
            AutoNotes.Budget b = budget(c);
            if (path) b.paths++; else b.notes++;
            save(c, b);
        }
    }

    /** Gemini said its free limit is reached: no notes on their own until tomorrow. */
    static void limitReached(Context c) {
        synchronized (LOCK) { AutoNotes.Budget b = budget(c); b.stop = today(); save(c, b); }
    }

    static String todayLine(Context c) {
        synchronized (LOCK) { return AutoNotes.line(budget(c), today()); }
    }

    /**
     * Makes and saves notes for one item and writes its note file. A short article is loaded from its page first.
     * questions: 3 for Make notes, 1 for notes made on their own. Returns "Notes saved."
     */
    static String make(Context c, long item, int questions) throws Exception {
        Db db = Db.get(c);
        String key = prefs(c).getString("geminiKey", "");
        if (key.isEmpty()) throw new Gemini.GeminiException("Add your Gemini key in Sources first.");
        String[] in = db.notesInput(item);
        if (in == null) throw new Gemini.GeminiException("That item is gone.");
        String kind = FeedParser.kindOf(in[1], in[4]);
        if (kind.equals("audio")) throw new Gemini.GeminiException("Notes are not made for podcast episodes.");
        String topics = db.topicNames(), request, basis;
        if (kind.equals("video")) {
            request = Gemini.notesRequest(topics, in[0], in[1], "", questions);
            basis = "the video";
        } else {
            String text = Sanitizer.text(in[2]);
            basis = "the saved article";
            if (text.length() < 400 && !in[1].isEmpty()) {
                try { text = Sanitizer.text(Sanitizer.clean(Refresher.fetchPage(in[1]), 400000)); basis = "the page text"; } catch (Exception e) { text = ""; }
            }
            if (text.length() < 400) { text = Sanitizer.text(in[3]); basis = "the feed excerpt only"; }
            if (text.length() < 200) throw new Gemini.GeminiException("There is not enough text to make notes from. Try Read full article first.");
            request = Gemini.notesRequest(topics, in[0], "", text, questions);
        }
        String answer;
        try { answer = Gemini.ask(key, request, Refresher.AGENT); }
        catch (Gemini.Limit e) { limitReached(c); throw e; }
        counted(c, false);
        Gemini.Notes n = Gemini.parseNotes(answer);
        db.saveAi(item, "*Based on " + basis + ".*\n\n" + n.md, n.questions);
        if (Exporter.enabled(c)) Exporter.write(c, db, item);
        return "Notes saved.";
    }

    /** After a keep: marks the item and starts the worker. */
    static void kept(Context c, long item) {
        Db.get(c).wantNotes(item);
        kick(c);
    }

    /** Starts the worker if it is not running; it ends when nothing is waiting, the day's budget is used, or there is no key. */
    static void kick(final Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (prefs(c).getString("geminiKey", "").isEmpty()) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        new Thread(() -> {
            try { loop(c); } catch (Throwable ignored) { } finally { RUNNING.set(false); }
        }, "auto-notes").start();
    }

    private static void loop(Context c) throws InterruptedException {
        Db db = Db.get(c);
        for (int rounds = 0; rounds < 200; rounds++) {
            long id = db.nextWanted();
            if (id < 0) return;
            if (prefs(c).getString("geminiKey", "").isEmpty()) return;
            long wait;
            synchronized (LOCK) { wait = AutoNotes.waitMs(budget(c), today(), System.currentTimeMillis()); }
            if (wait < 0) return;
            if (wait > 0) { Thread.sleep(Math.min(wait, AutoNotes.GAP_MS)); continue; }
            String[] in = db.notesInput(id);
            if (in == null) { db.setWanted(id, 0); continue; }
            boolean hasNotes = db.hasAi(id);
            if (AutoNotes.skip(FeedParser.kindOf(in[1], in[4]), hasNotes)) { db.setWanted(id, hasNotes ? 0 : 2); continue; }
            synchronized (LOCK) { AutoNotes.Budget b = budget(c); b.last = System.currentTimeMillis(); save(c, b); }
            try { make(c, id, 1); }
            catch (Gemini.Limit e) { return; } // tomorrow
            catch (Gemini.GeminiException e) { db.setWanted(id, 2); } // no text, a private video ...: Make notes can still be pressed
            catch (java.io.IOException e) { return; } // no network: the next start tries again
            catch (Exception e) { db.setWanted(id, 2); }
        }
    }
}
