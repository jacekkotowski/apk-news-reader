package pl.stashreader;

/** The review schedule for recall cards. No Android imports. */
final class Recall {
    private Recall() {}

    static final long DAY = 86400000L;
    /** Days to wait after the 1st, 2nd, 3rd ... successful review; the last value repeats. */
    static final int[] DAYS = {1, 3, 7, 21, 60};
    /** Cards asked per day, so a session has an end. */
    static final int DAILY_CAP = 5;

    /** New recall cards from notes that may become due on one day; the rest wait for a later day. */
    static final int NEW_PER_DAY = 5;

    static final class Next { int step; long due; }

    /**
     * Due times for n new cards from notes: the first day is tomorrow, and no local day gets more than NEW_PER_DAY of them,
     * counting the cards from notes that already wait (their due times in waiting).
     */
    static long[] spread(java.util.List<Long> waiting, long now, int n, java.util.TimeZone tz) {
        java.util.Map<Long, Integer> perDay = new java.util.HashMap<>();
        for (long d : waiting) { long k = dayKey(d, tz); perDay.put(k, (perDay.containsKey(k) ? perDay.get(k) : 0) + 1); }
        long[] out = new long[Math.max(0, n)];
        int k = 1;
        for (int i = 0; i < out.length; i++) {
            while (true) {
                long due = now + k * DAY;
                Integer c = perDay.get(dayKey(due, tz));
                if (c == null || c < NEW_PER_DAY) { out[i] = due; perDay.put(dayKey(due, tz), c == null ? 1 : c + 1); break; }
                k++;
            }
        }
        return out;
    }

    /** The local day of a moment, as a day number. */
    static long dayKey(long ms, java.util.TimeZone tz) { return Math.floorDiv(ms + tz.getOffset(ms), DAY); }

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
