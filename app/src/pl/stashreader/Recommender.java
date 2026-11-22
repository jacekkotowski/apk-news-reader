package pl.stashreader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * "Because you kept X": papers you kept become seeds. Semantic Scholar recommends papers from them (papers you marked
 * "Less like this" count as negative examples), and a few of the newest papers that cite your latest seeds are added.
 * Two to four requests per refresh, at most every three hours.
 */
final class Recommender {
    private Recommender() {}
    private static final int SEEDS = 5, CITED = 2;

    static FeedParser.Feed fetch(Db db, long now) throws IOException, FeedParser.FeedException {
        List<String[]> pos = db.paperSeeds(Db.KEPT, SEEDS), neg = db.paperSeeds(Db.LESS, SEEDS);
        if (pos.isEmpty()) throw new FeedParser.FeedException("Note: keep a paper from arXiv, Hugging Face daily papers or Semantic Scholar and recommendations will appear here");
        List<String> posIds = new ArrayList<>(), negIds = new ArrayList<>();
        for (String[] p : pos) posIds.add(p[0]);
        for (String[] n : neg) negIds.add(n[0]);

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < pos.size() && i < 2; i++) { if (i > 0) names.append("; "); names.append('“').append(Sanitizer.shorten(pos.get(i)[1], 60)).append('”'); }
        String because = "Recommended from papers you kept: " + names;

        byte[] body = QuerySource.recsBody(posIds, negIds).getBytes(StandardCharsets.UTF_8);
        byte[] answer = Refresher.request(QuerySource.requestUrl(QuerySource.RECS, "", now), body, 4 * 1024 * 1024, "application/json", new String[1]);
        FeedParser.Feed feed = QuerySource.parseRecommendations(answer, because);

        for (int i = 0; i < pos.size() && i < CITED; i++) {
            try {
                Thread.sleep(1000);
                byte[] c = Refresher.fetch(QuerySource.citationsUrl(pos.get(i)[0]), 4 * 1024 * 1024, "application/json");
                FeedParser.Feed cited = QuerySource.parseCitations(c, "Cites “" + Sanitizer.shorten(pos.get(i)[1], 60) + "”, which you kept", now);
                for (FeedParser.Item it : cited.items) FeedParser.add(feed, it, "query:recs:");
            } catch (Refresher.RateLimited e) {
                break; // keep what we have; the back-off is recorded on the next failure
            } catch (IOException | FeedParser.FeedException | InterruptedException e) {
                // the recommendations are the main result; a missing citations list is not an error
            }
        }
        FeedParser.finish(feed);
        return feed;
    }
}
