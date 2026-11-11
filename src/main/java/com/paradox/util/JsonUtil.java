package com.paradox.util;

import java.util.List;

/**
 * Minimal JSON writer for the Paradox snapshot and event payloads.
 *
 * The project keeps its dependency list to the strict stack (JavaFX, JDBC
 * driver, JUnit), so the few JSON shapes we need are produced here. Only
 * well-defined structures are serialized, and {@link #escape(String)} handles
 * quoting correctly, which is the part that is easy to get wrong by hand.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /** Escapes a string for use inside a JSON document, including the quotes. */
    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** ["P1","P2",...] for a list of strings. */
    public static String stringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(values.get(i)));
        }
        return sb.append(']').toString();
    }

    /** [1,0,2,...] for a vector of ints. */
    public static String intArray(int[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.append(']').toString();
    }

    /** [[1,0],[0,2],...] for a matrix of ints (allocation / request tables). */
    public static String intMatrix(int[][] rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int r = 0; r < rows.length; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append(intArray(rows[r]));
        }
        return sb.append(']').toString();
    }
}
