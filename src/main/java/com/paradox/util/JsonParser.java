package com.paradox.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for the Paradox snapshot documents.
 *
 * The project keeps its dependency list to the strict stack, so the few
 * documents we need to READ (snapshots for replay) are parsed here. Supports
 * objects, arrays, strings with escapes, numbers, true, false and null.
 * Values map to LinkedHashMap / ArrayList / String / Double / Boolean / null.
 */
public final class JsonParser {

    private final String text;
    private int position;

    private JsonParser(String text) {
        this.text = text;
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON text is empty");
        }
        JsonParser parser = new JsonParser(json.trim());
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.position != parser.text.length()) {
            throw parser.error("unexpected trailing characters");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (isAtEnd()) {
            throw error("unexpected end of input");
        }
        return switch (currentChar()) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseKeyword("true", Boolean.TRUE);
            case 'f' -> parseKeyword("false", Boolean.FALSE);
            case 'n' -> parseKeyword("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (isAtEnd()) {
            throw error("unterminated object");
        }
        if (currentChar() == '}') {
            position++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (isAtEnd()) {
                throw error("unterminated object");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            object.put(key, parseValue());
            skipWhitespace();
            if (isAtEnd()) {
                throw error("unterminated object");
            }
            if (currentChar() == ',') {
                position++;
            } else if (currentChar() == '}') {
                position++;
                return object;
            } else {
                throw error("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (isAtEnd()) {
            throw error("unterminated array");
        }
        if (currentChar() == ']') {
            position++;
            return array;
        }
        while (true) {
            array.add(parseValue());
            skipWhitespace();
            if (isAtEnd()) {
                throw error("unterminated array");
            }
            if (currentChar() == ',') {
                position++;
            } else if (currentChar() == ']') {
                position++;
                return array;
            } else {
                throw error("expected ',' or ']' in array");
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (isAtEnd()) {
                throw error("unterminated string");
            }
            char c = text.charAt(position++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                sb.append(parseEscape());
            } else {
                sb.append(c);
            }
        }
    }

    private char parseEscape() {
        if (isAtEnd()) {
            throw error("unterminated escape sequence");
        }
        char c = text.charAt(position++);
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> {
                if (position + 4 > text.length()) {
                    throw error("incomplete unicode escape");
                }
                char decoded = (char) Integer.parseInt(text.substring(position, position + 4), 16);
                position += 4;
                yield decoded;
            }
            default -> throw error("unknown escape character \\" + c);
        };
    }

    private Object parseKeyword(String keyword, Object value) {
        if (text.startsWith(keyword, position)) {
            position += keyword.length();
            return value;
        }
        throw error("invalid keyword");
    }

    private Object parseNumber() {
        int start = position;
        if (currentChar() == '-') {
            position++;
        }
        while (!isAtEnd() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        if (!isAtEnd() && text.charAt(position) == '.') {
            position++;
            while (!isAtEnd() && Character.isDigit(text.charAt(position))) {
                position++;
            }
        }
        if (position == start || (position == start + 1 && text.charAt(start) == '-')) {
            throw error("invalid number");
        }
        return Double.parseDouble(text.substring(start, position));
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private char currentChar() {
        return text.charAt(position);
    }

    private boolean isAtEnd() {
        return position >= text.length();
    }

    private void expect(char expected) {
        if (isAtEnd() || currentChar() != expected) {
            throw error("expected '" + expected + "'");
        }
        position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(
                "Invalid JSON at position " + position + ": " + message);
    }
}
