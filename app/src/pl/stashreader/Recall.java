package pl.stashreader;

/** The review schedule for recall cards. No Android imports. */
final class Recall {
    private Recall() {}

    static final long DAY = 86400000L;
    /** Days to wait after the 1st, 2nd, 3rd ... successful review; the last value repeats. */
    static final int[] DAYS = {1, 3, 7, 21, 60};
    /** Cards asked per day, so a session has an end. */
    static final int DAILY_CAP = 5;

    static final class Next { int step; long due; }

    /** After an answer: "Got it" moves one step on, "Again" starts over and asks again tomorrow. */
    static Next next(int step, boolean ok, long now) {
        Next n = new Next();
        if (!ok) { n.step = 0; n.due = now + DAY; return n; }
        n.step = step + 1;
        n.due = now + DAYS[Math.min(n.step, DAYS.length - 1)] * DAY;
        return n;
    }

    /** How many cards to show now: the due ones, but never more than what is left of today's cap. */
    static int toShow(int answeredToday, int due) {
        return Math.max(0, Math.min(DAILY_CAP - answeredToday, due));
    }
}
