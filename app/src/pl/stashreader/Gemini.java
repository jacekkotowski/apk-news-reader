package pl.stashreader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Study notes and learning paths from Google's Gemini API (free tier, the user's own key). No Android imports.
 * Only the model, the address and the header name below would change if Google changes the API.
 */
final class Gemini {
    private Gemini() {}

    static final String MODEL = "gemini-3.5-flash-lite";
    static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    static final String KEY_HEADER = "x-goog-api-key";
    /** Characters of article text sent at most. */
    static final int MAX_TEXT = 30000;

    static class GeminiException extends Exception { GeminiException(String m) { super(m); } }
    /** HTTP 429 or RESOURCE_EXHAUSTED: the free limit is used up for now. */
    static final class Limit extends GeminiException { Limit(String m) { super(m); } }

    static final class Notes { String md = ""; final List<String[]> questions = new ArrayList<>(); }

    private static final String NOTES_SCHEMA = "{\"type\":\"OBJECT\",\"properties\":{\"notes_md\":{\"type\":\"STRING\"},\"questions\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{\"q\":{\"type\":\"STRING\"},\"a\":{\"type\":\"STRING\"}},\"required\":[\"q\",\"a\"]}}},\"required\":[\"notes_md\",\"questions\"]}";

    /** JSON string contents: quotes, backslashes and control characters escaped. */
    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.toString();
    }

    private static String request(String partsJson, String schema) { return request(partsJson, schema, ""); }

    /** extraConfig: more members for generationConfig, each starting with a comma; only used together with a schema. */
    private static String request(String partsJson, String schema, String extraConfig) {
        String cfg = schema == null ? "" : ",\"generationConfig\":{\"responseMimeType\":\"application/json\",\"responseSchema\":" + schema + extraConfig + "}";
        return "{\"contents\":[{\"parts\":[" + partsJson + "]}]" + cfg + "}";
    }

    private static String textPart(String t) { return "{\"text\":\"" + esc(t) + "\"}"; }

    /** A tiny request to check that the key is accepted. */
    static String pingRequest() { return request(textPart("Reply with the single word OK."), null); }

    /** Study notes with 3 recall questions. */
    static String notesRequest(String topics, String title, String videoUrl, String articleText) { return notesRequest(topics, title, videoUrl, articleText, 3); }

    /**
     * Study notes. For a video pass its YouTube address and an empty text; for an article pass the text and an empty address.
     * questions: how many recall questions to ask for (notes made on their own, after a keep, ask for 1).
     */
    static String notesRequest(String topics, String title, String videoUrl, String articleText, int questions) {
        int nq = Math.max(1, Math.min(5, questions));
        boolean video = videoUrl != null && !videoUrl.isEmpty();
        String what = video ? "video" : "article";
        String p = "You are helping me study. I am learning: " + (topics.isEmpty() ? "data science" : topics) + ". "
                + "Write study notes from the " + what + " below" + (title == null || title.isEmpty() ? "" : " (title: " + title + ")") + ". "
                + "Return JSON with notes_md and questions. notes_md is Markdown with exactly these sections: "
                + "'## Key ideas' (5 short bullets), '## Quotes' (up to 3 short quotes" + (video ? ", each with an mm:ss timestamp" : "") + "), "
                + "'## Why it matters for me' (2 sentences; tie it to my topics only if it really fits), '## Try next' (2 bullets). "
                + "questions holds " + (nq == 1 ? "1 recall question" : nq + " recall questions") + ", each with a short answer. "
                + "Use only what the " + what + " says; do not invent anything."
                + (video ? " Take the notes mainly from what is said in the video." : "");
        // low resolution: the notes come from the speech, and a video then costs far fewer tokens
        if (video) return request("{\"fileData\":{\"fileUri\":\"" + esc(videoUrl) + "\"}}," + textPart(p), NOTES_SCHEMA, ",\"mediaResolution\":\"MEDIA_RESOLUTION_LOW\"");
        String t = articleText == null ? "" : articleText;
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT);
        return request(textPart(p + "\n\n--- ARTICLE ---\n" + t), NOTES_SCHEMA);
    }

    private static final String PLAN_SCHEMA = "{\"type\":\"OBJECT\",\"properties\":{\"steps\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{"
            + "\"step\":{\"type\":\"STRING\"},\"why\":{\"type\":\"STRING\"},\"search\":{\"type\":\"STRING\"},\"resources\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{"
            + "\"title\":{\"type\":\"STRING\"},\"url\":{\"type\":\"STRING\"},\"kind\":{\"type\":\"STRING\"},\"minutes\":{\"type\":\"INTEGER\"}},\"required\":[\"title\",\"url\",\"kind\"]}}},"
            + "\"required\":[\"step\",\"why\",\"search\",\"resources\"]}}},\"required\":[\"steps\"]}";

    /**
     * A learning path from zero to useful on one topic. Gemini plans the steps, gives a YouTube search phrase for each and suggests
     * free courses and official documentation; it gives no video links (the app finds videos with a real YouTube search).
     * material: titles, links and highlights kept on the topic (never private notes); may be empty.
     */
    static String pathRequest(String topic, String topics, String material) {
        String m = material == null ? "" : material.trim();
        if (m.length() > 15000) m = m.substring(0, 15000);
        String p = "I want to learn " + topic + " fast, from zero to useful." + (topics.isEmpty() ? "" : " My other topics: " + topics + ".") + " "
                + (m.isEmpty() ? "I have not kept anything on it yet. " : "Below is what I already kept on it: titles, links and passages I highlighted. Reuse an item where it fits, with its link. ")
                + "Make a learning path of 5 to 8 steps, in order. For each step give: step (a short name), why (one sentence), "
                + "search (the words I would type into YouTube to find a good video for this step, 2 to 5 words, including the topic), "
                + "and resources: 0 to 2 free resources that are not videos: free courses or MOOCs first, then official documentation. "
                + "kind is one of course, docs, article. minutes is your estimate of the time it takes. "
                + "Give only pages you are sure exist, with their exact full address, and no YouTube links. Leave resources empty when you are not sure.";
        return request(textPart(m.isEmpty() ? p : p + "\n\n--- WHAT I KEPT ---\n" + m), PLAN_SCHEMA);
    }

    /** A question answered from numbered passages only. passages: {title, text}. The answer is plain text with [1], [2] citations. */
    static String answerRequest(String question, List<String[]> passages) {
        StringBuilder b = new StringBuilder();
        b.append("Answer the question using ONLY the numbered passages below, which come from my own saved reading. ")
         .append("Cite the passage after every claim, like [1] or [2]; never cite a number that is not listed. ")
         .append("If the passages do not contain the answer, reply exactly: ").append(Passages.NO_ANSWER).append(" ")
         .append("Do not use outside knowledge. Keep the answer under 150 words.\n\nQUESTION: ").append(question).append("\n\nPASSAGES:\n");
        for (int i = 0; i < passages.size(); i++) {
            String t = passages.get(i)[1];
            if (t.length() > 900) t = t.substring(0, 900);
            b.append('[').append(i + 1).append("] (").append(passages.get(i)[0]).append(") ").append(t).append('\n');
        }
        return request(textPart(b.toString()), null);
    }

    /** The model's text from a generateContent answer, or a GeminiException with a short message. */
    static String replyText(int code, String body) throws GeminiException {
        Object root = parseOrNull(body == null ? "" : body);
        Map<String, Object> o = Json.obj(root);
        if (code != 200 || (o != null && o.get("error") != null)) {
            Map<String, Object> e = o == null ? null : Json.obj(o.get("error"));
            if (code == 429 || Json.str(e, "status").equals("RESOURCE_EXHAUSTED")) throw new Limit(errorMessage(code, body));
            throw new GeminiException(errorMessage(code, body));
        }
        if (o == null) throw new GeminiException("Gemini's answer could not be read");
        List<Object> cands = Json.list(o.get("candidates"));
        StringBuilder out = new StringBuilder();
        if (cands != null && !cands.isEmpty()) {
            Map<String, Object> c0 = Json.obj(cands.get(0));
            Map<String, Object> content = c0 == null ? null : Json.obj(c0.get("content"));
            List<Object> parts = content == null ? null : Json.list(content.get("parts"));
            if (parts != null) for (Object p : parts) { String t = Json.str(Json.obj(p), "text"); if (!t.isEmpty()) out.append(t); }
        }
        if (out.length() == 0) {
            Map<String, Object> fb = Json.obj(o.get("promptFeedback"));
            String block = Json.str(fb, "blockReason");
            throw new GeminiException(block.isEmpty() ? "Gemini returned no text" : "Gemini declined to read this (" + block + ")");
        }
        return out.toString();
    }

    private static Object parseOrNull(String t) {
        try { return Json.parse(t); } catch (RuntimeException e) { return null; }
    }

    static String stripFences(String t) {
        String s = t.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            s = nl < 0 ? "" : s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    static Notes parseNotes(String text) throws GeminiException {
        Map<String, Object> o = Json.obj(parseOrNull(stripFences(text)));
        if (o == null) throw new GeminiException("Gemini's notes could not be read");
        Notes n = new Notes();
        n.md = Json.str(o, "notes_md").trim();
        if (n.md.isEmpty()) throw new GeminiException("Gemini returned empty notes");
        List<Object> qs = Json.list(o.get("questions"));
        if (qs != null) for (Object q : qs) {
            Map<String, Object> m = Json.obj(q);
            String a = Json.str(m, "q").trim(), b = Json.str(m, "a").trim();
            if (!a.isEmpty() && !b.isEmpty() && n.questions.size() < 5) n.questions.add(new String[]{a, b});
        }
        return n;
    }

    /** A short sentence for a failed call. */
    static String errorMessage(int code, String body) {
        String msg = "", status = "";
        Map<String, Object> o = Json.obj(parseOrNull(body == null ? "" : body));
        Map<String, Object> e = o == null ? null : Json.obj(o.get("error"));
        if (e != null) { msg = Json.str(e, "message"); status = Json.str(e, "status"); }
        if (msg.length() > 200) msg = msg.substring(0, 200);
        if (code == 429 || status.equals("RESOURCE_EXHAUSTED")) return "Gemini's free limit is reached for now. Try again later.";
        if (code == 401 || code == 403 || msg.toLowerCase(java.util.Locale.ROOT).contains("api key")) return "Gemini did not accept the key" + (msg.isEmpty() ? "" : ": " + msg);
        if (code == 404) return "Gemini does not know the model " + MODEL + "; the app needs an update";
        if (code == 400 && msg.toLowerCase(java.util.Locale.ROOT).contains("youtube")) return "Gemini could not read that video (it must be public)";
        return "Gemini answered HTTP " + code + (msg.isEmpty() ? "" : ": " + msg);
    }

    /** Sends one request and returns the model's text. The key goes in a header, never in the address. */
    static String ask(String key, String requestJson, String agent) throws IOException, GeminiException {
        HttpURLConnection h = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            h.setConnectTimeout(15000); h.setReadTimeout(120000);
            h.setRequestMethod("POST"); h.setDoOutput(true);
            h.setRequestProperty("User-Agent", agent);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty(KEY_HEADER, key);
            try (OutputStream out = h.getOutputStream()) { out.write(requestJson.getBytes(StandardCharsets.UTF_8)); }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                try (InputStream i = in) {
                    byte[] b = new byte[8192];
                    for (int n; (n = i.read(b)) > 0; ) { buf.write(b, 0, n); if (buf.size() > 2000000) throw new IOException("Gemini's answer is too large"); }
                }
            }
            return replyText(code, new String(buf.toByteArray(), StandardCharsets.UTF_8));
        } finally { h.disconnect(); }
    }
}
