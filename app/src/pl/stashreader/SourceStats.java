package pl.stashreader;

/** Quiet feeds: how much of a source you keep, and when to suggest muting it. No Android imports. */
final class SourceStats {
    private SourceStats() {}

    static final long WINDOW_MS = 30L * 86400000L;
    /** A source needs at least this many items in the window before a Mute suggestion. */
    static final int MIN_ITEMS = 20;

    /** Items fetched in the window start at this time. */
    static long since(long now) { return now - WINDOW_MS; }

    /** "kept 3 of 41 in 30 days"; empty when the source has no items in the window. */
    static String line(int kept, int seen) {
        if (seen <= 0) return "";
        return "kept " + Math.max(0, Math.min(kept, seen)) + " of " + seen + " in 30 days";
    }

    /** Suggest muting a source that is on, gave at least MIN_ITEMS items and none of them was kept. */
    static boolean suggestMute(int kept, int seen, boolean enabled) {
        return enabled && seen >= MIN_ITEMS && kept <= 0;
    }
}
