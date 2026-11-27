package pl.stashreader;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Learn fast: Gemini plans a learning path for one topic; the app checks every page it names (on this background thread,
 * with the app's User-Agent and timeouts) and, with the reader's free YouTube key, finds each step's video with a real
 * YouTube search. No paid service is used. The path is stored as a source of kind "path" whose items are the steps.
 */
final class PathMaker {
    private PathMaker() {}

    /** The real network, polite: one request at a time, the app's User-Agent, the usual timeouts. */
    static final PathFinder.Web WEB = new PathFinder.Web() {
        @Override public String get(String url) throws IOException { return Refresher.fetchPage(url); }
    };

    /** Makes the path and returns a sentence for the reader. */
    static String make(Context c, String topicIn) throws Exception {
        String topic = Sanitizer.collapse(topicIn == null ? "" : topicIn);
        if (topic.isEmpty() || topic.length() > 60) throw new Gemini.GeminiException("Choose a topic first.");
        SharedPreferences prefs = NoteWorker.prefs(c);
        String key = prefs.getString("geminiKey", "");
        if (key.isEmpty()) throw new Gemini.GeminiException("Add your Gemini key in Sources first.");
        Refresher.loadKeys(c);
        boolean youtube = !prefs.getString("ytKey", "").isEmpty();
        Db db = Db.get(c);
        Set<String> keptLinks = new HashSet<>();
        String material = db.pathMaterial(topic, keptLinks);
        long now = System.currentTimeMillis();
        String answer;
        try { answer = Gemini.ask(key, Gemini.pathRequest(topic, db.topicNames(), material), Refresher.AGENT); }
        catch (Gemini.Limit e) { NoteWorker.limitReached(c); throw e; }
        NoteWorker.counted(c, true);
        PathFinder.Plan plan = PathFinder.parse(answer);
        PathFinder.check(plan, topic, WEB, keptLinks, youtube);
        String md = PathFinder.markdown(topic, plan);
        db.savePath(topic, plan, md, now);
        String e = Exporter.enabled(c) ? Exporter.writeText(c, MarkdownExport.pathFileName(topic, now, java.util.TimeZone.getDefault()), MarkdownExport.pathNote(topic, md, now, java.util.TimeZone.getDefault())) : "";
        int videos = 0;
        for (PathFinder.Step st : plan.steps) if (PathFinder.hasVideo(st)) videos++;
        String msg = "Learning path saved: " + plan.steps.size() + " steps, " + plan.kept + (plan.kept == 1 ? " checked link" : " checked links")
                + (youtube ? ", a video for " + videos + " of them." : ". Add a free YouTube key in Settings to get videos.");
        if (!plan.youtubeNote.isEmpty()) msg += " " + plan.youtubeNote;
        return e.isEmpty() ? msg : msg + " " + e;
    }
}
