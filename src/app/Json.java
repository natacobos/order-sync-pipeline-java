package app;

import java.util.LinkedHashMap;
import java.util.Map;

/** A tiny JSON reader for the flat messages of this project (objects, strings, whole numbers, null). */
public final class Json {
    private final String s;
    private int i = 0;

    private Json(String s) {
        this.s = s;
    }

    public static Map<String, Object> parseObject(String text) {
        Json p = new Json(text);
        Map<String, Object> value = p.object();
        p.space();
        if (p.i != p.s.length()) throw p.error("unexpected text after the object");
        return value;
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> out = new LinkedHashMap<>();
        space();
        if (peek() == '}') { i++; return out; }
        while (true) {
            space();
            String key = string();
            space();
            expect(':');
            out.put(key, value());
            space();
            char c = s.charAt(i++);
            if (c == '}') return out;
            if (c != ',') throw error("expected , or }");
        }
    }

    private Object value() {
        space();
        char c = peek();
        if (c == '{') return object();
        if (c == '"') return string();
        if (s.startsWith("null", i)) { i += 4; return null; }
        int start = i;
        if (c == '-') i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (start == i) throw error("unexpected value");
        return Long.valueOf(s.substring(start, i));
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return out.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                out.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'u' -> { char u = (char) Integer.parseInt(s.substring(i, i + 4), 16); i += 4; yield u; }
                    default -> e;
                });
            } else {
                out.append(c);
            }
        }
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        return s.charAt(i);
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) throw error("expected " + c);
        i++;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("bad JSON at " + i + ": " + what);
    }
}
