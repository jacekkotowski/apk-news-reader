package pl.stashreader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader so the API parsers can be tested on the PC (org.json exists only on Android).
 * Objects become Map, arrays List, numbers Double, plus String, Boolean and null.
 */
final class Json {
    private final String s;
    private int i;
    private int depth;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text == null ? "" : text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw new IllegalArgumentException("Extra text after the JSON value");
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }
    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : null; }
    static String str(Object o) { return o instanceof String ? (String) o : (o instanceof Double ? trimNumber((Double) o) : ""); }
    static String str(Map<String, Object> m, String key) { return m == null ? "" : str(m.get(key)); }
    static long num(Map<String, Object> m, String key) { Object o = m == null ? null : m.get(key); return o instanceof Double ? ((Double) o).longValue() : 0; }

    private static String trimNumber(double d) { return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d); }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object value() {
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        if (++depth > 50) throw new IllegalArgumentException("JSON is nested too deeply");
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; depth--; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw new IllegalArgumentException("Expected a key");
            String k = string();
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw new IllegalArgumentException("Expected a colon");
            i++; ws();
            m.put(k, value());
            ws();
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(i++);
            if (c == '}') break;
            if (c != ',') throw new IllegalArgumentException("Expected a comma");
        }
        depth--;
        return m;
    }

    private List<Object> array() {
        if (++depth > 50) throw new IllegalArgumentException("JSON is nested too deeply");
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; depth--; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(i++);
            if (c == ']') break;
            if (c != ',') throw new IllegalArgumentException("Expected a comma");
        }
        depth--;
        return l;
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++; // opening quote
        while (true) {
            if (i >= s.length()) throw new IllegalArgumentException("Unterminated string");
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw new IllegalArgumentException("Unterminated escape");
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                    try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); } catch (NumberFormatException x) { throw new IllegalArgumentException("Bad unicode escape"); }
                    i += 4;
                    break;
                default: b.append(e); // \" \\ \/
            }
        }
        return b.toString();
    }

    private Double number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw new IllegalArgumentException("Unexpected character in JSON");
        try { return Double.valueOf(s.substring(st, i)); } catch (NumberFormatException e) { throw new IllegalArgumentException("Bad number"); }
    }
}
