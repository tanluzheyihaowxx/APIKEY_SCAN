package cn.apikeyscan.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static Object parse(String text) {
        return new Parser(text).parse();
    }

    public static Object read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void write(Path path, Object value) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, stringify(value, true) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public static String stringify(Object value, boolean pretty) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, pretty, 0);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder out, boolean pretty, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            if (!map.isEmpty()) {
                int i = 0;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (i++ > 0) out.append(',');
                    newline(out, pretty, depth + 1);
                    writeString(String.valueOf(e.getKey()), out);
                    out.append(pretty ? ": " : ":");
                    writeValue(e.getValue(), out, pretty, depth + 1);
                }
                newline(out, pretty, depth);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> list) {
            out.append('[');
            int i = 0;
            for (Object item : list) {
                if (i++ > 0) out.append(',');
                newline(out, pretty, depth + 1);
                writeValue(item, out, pretty, depth + 1);
            }
            if (i > 0) newline(out, pretty, depth);
            out.append(']');
        } else if (value.getClass().isArray()) {
            Object[] items = (Object[]) value;
            List<Object> list = List.of(items);
            writeValue(list, out, pretty, depth);
        } else {
            writeString(String.valueOf(value), out);
        }
    }

    private static void newline(StringBuilder out, boolean pretty, int depth) {
        if (!pretty) return;
        out.append('\n').append("  ".repeat(Math.max(0, depth)));
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
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text == null ? "" : text.replace("\uFEFF", "");
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos != text.length()) throw error("Unexpected trailing content");
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= text.length()) throw error("Unexpected end of JSON");
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                case 'n' -> { expect("null"); yield null; }
                default -> {
                    if (c == '-' || Character.isDigit(c)) yield parseNumber();
                    throw error("Unexpected character: " + c);
                }
            };
        }

        private Map<String, Object> parseObject() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek('}')) { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                require(':');
                map.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) { pos++; return map; }
                require(',');
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek(']')) { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(']')) { pos++; return list; }
                require(',');
            }
        }

        private String parseString() {
            require('"');
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (pos >= text.length()) throw error("Incomplete escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw error("Incomplete unicode escape");
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("Unknown escape: " + e);
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            if (peek('.')) {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (peek('e') || peek('E')) {
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            String n = text.substring(start, pos);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            try { return Integer.parseInt(n); } catch (NumberFormatException ignored) { return Long.parseLong(n); }
        }

        private void expect(String expected) {
            if (!text.startsWith(expected, pos)) throw error("Expected " + expected);
            pos += expected.length();
        }

        private void require(char expected) {
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) != expected) throw error("Expected " + expected);
            pos++;
        }

        private boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}

