package dev.streamflix.desktop;

import java.util.*;

/** Minimal dependency-free JSON parser/writer for provider APIs. */
final class Json {
    private Json() {}

    static Object parse(String input) {
        Parser p = new Parser(input);
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.eof()) throw new IllegalArgumentException("Trailing JSON at offset " + p.pos);
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (value instanceof List<?> list) return (List<Object>) list;
        return List.of();
    }

    static String string(Object value) {
        if (value == null) return "";
        return String.valueOf(value);
    }

    static Integer integer(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.valueOf(string(value)); } catch (Exception ignored) { return null; }
    }

    static Double decimal(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.valueOf(string(value)); } catch (Exception ignored) { return null; }
    }

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { writeString(s, out); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                first = false;
                write(item, out);
            }
            out.append(']');
            return;
        }
        writeString(String.valueOf(value), out);
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = Objects.requireNonNull(s); }
        boolean eof() { return pos >= s.length(); }
        void skipWhitespace() { while (!eof() && Character.isWhitespace(s.charAt(pos))) pos++; }

        Object parseValue() {
            skipWhitespace();
            if (eof()) throw error("Unexpected end");
            return switch (s.charAt(pos)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                case 'n' -> { expect("null"); yield null; }
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            pos++; skipWhitespace();
            if (peek('}')) { pos++; return map; }
            while (true) {
                skipWhitespace();
                if (!peek('"')) throw error("Expected string key");
                String key = parseString();
                skipWhitespace(); require(':');
                map.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) { pos++; return map; }
                require(',');
            }
        }

        List<Object> parseArray() {
            ArrayList<Object> list = new ArrayList<>();
            pos++; skipWhitespace();
            if (peek(']')) { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(']')) { pos++; return list; }
                require(',');
            }
        }

        String parseString() {
            require('"');
            StringBuilder out = new StringBuilder();
            while (!eof()) {
                char c = s.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (eof()) throw error("Bad escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("Bad unicode escape");
                        int cp = Integer.parseInt(s.substring(pos, pos + 4), 16);
                        out.append((char) cp);
                        pos += 4;
                    }
                    default -> throw error("Unknown escape " + e);
                }
            }
            throw error("Unterminated string");
        }

        Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (!eof() && Character.isDigit(s.charAt(pos))) pos++;
            boolean floating = false;
            if (!eof() && s.charAt(pos) == '.') {
                floating = true; pos++;
                while (!eof() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (!eof() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                floating = true; pos++;
                if (!eof() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (!eof() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (start == pos) throw error("Expected value");
            String n = s.substring(start, pos);
            try { return floating ? Double.valueOf(n) : Long.valueOf(n); }
            catch (NumberFormatException ex) { throw error("Bad number: " + n); }
        }

        void expect(String literal) {
            if (!s.startsWith(literal, pos)) throw error("Expected " + literal);
            pos += literal.length();
        }
        void require(char c) {
            skipWhitespace();
            if (eof() || s.charAt(pos) != c) throw error("Expected '" + c + "'");
            pos++;
        }
        boolean peek(char c) { return !eof() && s.charAt(pos) == c; }
        IllegalArgumentException error(String msg) { return new IllegalArgumentException(msg + " at offset " + pos); }
    }
}
