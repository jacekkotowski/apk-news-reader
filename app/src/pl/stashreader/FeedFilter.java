package pl.stashreader;

import java.util.Locale;

/** Which Feed cards pass the filters the reader chose, and when an unkept card expires. No Android imports. */
final class FeedFilter {
    private FeedFilter() {}

    /** Unkept cards leave the Feed this many days after they first arrived. */
    static final int EXPIRY_DAYS = 30;
    static final long DAY = 86400000L;

    static final class Filters {
        /** "", "article", "video" or "audio". */
        String type = "";
        String topic = "";
        /** 0 = every source. */
        long source;
        String query = "";
        boolean newest;
        /** 0 = any length; else only items of at most this many minutes (a video counts 10, a podcast 20). */
        int maxMins;

        /** No filter and the default order: the plain Feed, where surprises are mixed in. */
        boolean plain() { return count() == 0; }

        /** How many filters are on, for the number shown on the filter button. Sorting counts as one. */
        int count() {
            return (type.isEmpty() ? 0 : 1) + (topic.isEmpty() ? 0 : 1) + (source == 0 ? 0 : 1) + (query.trim().isEmpty() ? 0 : 1) + (newest ? 1 : 0);
        }
    }

    /** kind is "video", "audio" or "" (an article), as FeedParser.kindOf says. Every typed word must occur in the title or text. */
    static boolean accept(Filters f, String kind, boolean topicMatches, long sourceId, String title, String text) {
        if (f == null) return true;
        if (!f.type.isEmpty()) {
            if (f.type.equals("article") && !kind.isEmpty()) return false;
            if (f.type.equals("video") && !kind.equals("video")) return false;
            if (f.type.equals("audio") && !kind.equals("audio")) return false;
        }
        if (!f.topic.isEmpty() && !topicMatches) return false;
        if (f.source != 0 && f.source != sourceId) return false;
        String q = f.query == null ? "" : f.query.trim();
        if (!q.isEmpty()) {
            String hay = ((title == null ? "" : title) + " " + (text == null ? "" : text)).toLowerCase(Locale.ROOT);
            for (String w : q.toLowerCase(Locale.ROOT).split("\\s+")) if (!w.isEmpty() && !hay.contains(w)) return false;
        }
        return true;
    }

    /** Cards that arrived before this moment, and are still unkept, are expired. */
    static long cutoff(long now, int days) { return now - days * DAY; }
}
