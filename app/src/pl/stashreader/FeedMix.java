package pl.stashreader;

/** Where due recall cards go inside the Feed. No Android imports. */
final class FeedMix {
    private FeedMix() {}

    /** One recall card after about every this many articles. */
    static final int EVERY = 4;

    /**
     * For each recall card to show, the number of articles that come before it.
     * Card k goes after (k+1)*EVERY articles; when the list is shorter, the card goes at the end.
     * Never more than the daily cap, and no cards means an empty answer (the feed is unchanged).
     */
    static int[] slots(int articles, int cards) {
        int n = Math.max(0, Math.min(cards, Recall.DAILY_CAP));
        int[] s = new int[n];
        for (int k = 0; k < n; k++) s[k] = Math.max(0, Math.min(articles, (k + 1) * EVERY));
        return s;
    }
}
