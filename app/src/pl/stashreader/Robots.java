package pl.stashreader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** robots.txt rules for one user agent: the most specific group wins, then the longest matching rule, Allow beating Disallow on a tie. No Android imports. */
final class Robots {
    private Robots() {}

    private static final class Rule { boolean allow; String path; }
    private static final class Group { final List<String> agents = new ArrayList<>(); final List<Rule> rules = new ArrayList<>(); }

    /** True when the agent may fetch the path (with query). An empty or unreadable robots.txt allows everything. */
    static boolean allowed(String robotsTxt, String agent, String path) {
        if (robotsTxt == null || robotsTxt.trim().isEmpty()) return true;
        String a = agent.toLowerCase(Locale.ROOT);
        List<Group> groups = parse(robotsTxt);
        Group chosen = null, star = null;
        for (Group g : groups) {
            for (String ua : g.agents) {
                if (ua.equals("*")) { if (star == null) star = g; }
                else if (!ua.isEmpty() && a.contains(ua)) { chosen = g; }
            }
        }
        if (chosen == null) chosen = star;
        if (chosen == null) return true;
        String p = path == null || path.isEmpty() ? "/" : path;
        int bestLen = -1; boolean allow = true;
        for (Rule r : chosen.rules) {
            if (r.path.isEmpty()) continue;
            if (!matches(r.path, p)) continue;
            int len = r.path.length();
            if (len > bestLen || (len == bestLen && r.allow)) { bestLen = len; allow = r.allow; }
        }
        return allow;
    }

    private static List<Group> parse(String text) {
        List<Group> out = new ArrayList<>();
        Group cur = null;
        boolean lastWasAgent = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String val = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!lastWasAgent || cur == null) { cur = new Group(); out.add(cur); }
                cur.agents.add(val.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
            } else if (key.equals("allow") || key.equals("disallow")) {
                lastWasAgent = false;
                if (cur == null) continue;
                Rule r = new Rule();
                r.allow = key.equals("allow"); r.path = val;
                cur.rules.add(r);
            } else {
                lastWasAgent = false;
            }
        }
        return out;
    }

    /** A rule path matches as a prefix; '*' matches any text and a final '$' anchors the end. */
    static boolean matches(String pattern, String path) {
        boolean end = pattern.endsWith("$");
        String pat = end ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder re = new StringBuilder("^");
        String[] parts = pat.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) re.append(".*");
            re.append(Pattern.quote(parts[i]));
        }
        if (end) re.append('$');
        return Pattern.compile(re.toString(), Pattern.DOTALL).matcher(path).find();
    }
}
