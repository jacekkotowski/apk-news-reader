package pl.stashreader;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Learn fast: asks Gemini for a learning path on one topic, checks every link (on this background thread, with the app's
 * User-Agent and timeouts), and stores the path as a source of kind "path" whose items are the steps.
 * Google Search is tried first; a free key gets HTTP 429 for it (Gemini 3 models have no Search in the free tier, checked
 * 2026-11-27), so the app then asks without it for a week and fills in videos from checked channels.
 */
final class PathMaker {
    private PathMaker() {}

    private static final long NO_SEARCH_MS = 7L * 86400000L;

    /** The real network, polite: one request at a time, the app's User-Agent, the usual timeouts. */
    static final PathFinder.Web WEB = new PathFinder.Web() {
        @Override public String get(String url) throws IOException { return Refresher.fetchPage(url); }
        @Override public String location(String url) throws IOException { return Refresher.location(url); }
    };

    /** Makes the path and returns a sentence for the reader. */
    static String make(Context c, String topicIn) throws Exception {
        String topic = Sanitizer.collapse(topicIn == null ? "" : topicIn);
        if (topic.isEmpty() || topic.length() > 60) throw new Gemini.GeminiException("Choose a topic first.");
        SharedPreferences prefs = NoteWorker.prefs(c);
        String key = prefs.getString("geminiKey", "");
        if (key.isEmpty()) throw new Gemini.GeminiException("Add your Gemini key in Sources first.");
        Db db = Db.get(c);
        Set<String> keptLinks = new HashSet<>();
        String material = db.pathMaterial(topic, keptLinks);
        String topics = db.topicNames();
        long now = System.currentTimeMillis();
        PathFinder.Plan plan = null;
        if (now >= prefs.getLong("searchOffUntil", 0)) {
            try {
                String[] a = Gemini.askFull(key, Gemini.pathRequest(topic, topics, material, true), Refresher.AGENT);
                NoteWorker.counted(c, true);
                plan = PathFinder.parse(a[0]);
                plan.searched = !PathFinder.groundingSources(a[1]).isEmpty();
            } catch (Gemini.Limit e) {
                // Google Search is not in this key's tier (or used up): ask without it, and do not try it again for a week
                prefs.edit().putLong("searchOffUntil", now + NO_SEARCH_MS).apply();
            } catch (Gemini.GeminiException e) {
                plan = null; // an answer that could not be read: try once without Search
            }
        }
        if (plan == null) {
            String answer;
            try { answer = Gemini.ask(key, Gemini.pathRequest(topic, topics, material, false), Refresher.AGENT); }
            catch (Gemini.Limit e) { NoteWorker.limitReached(c); throw e; }
            NoteWorker.counted(c, true);
            plan = PathFinder.parse(answer);
        }
        PathFinder.check(plan, topic, WEB, keptLinks);
        if (plan.kept == 0) throw new Gemini.GeminiException("None of the " + plan.suggested + " links Gemini gave could be checked, so no path was saved. Try again later, or with a shorter topic.");
        String md = PathFinder.markdown(topic, plan);
        db.savePath(topic, plan, md, now);
        String e = Exporter.enabled(c) ? Exporter.writeText(c, MarkdownExport.pathFileName(topic, now, java.util.TimeZone.getDefault()), MarkdownExport.pathNote(topic, md, now, java.util.TimeZone.getDefault())) : "";
        int dropped = Math.max(0, plan.suggested - plan.kept);
        String msg = "Learning path saved: " + plan.kept + (plan.kept == 1 ? " checked link" : " checked links") + " in " + plan.steps.size() + " steps"
                + (dropped > 0 ? "; links that did not check out were left out" : "") + ".";
        return e.isEmpty() ? msg : msg + " " + e;
    }
}
