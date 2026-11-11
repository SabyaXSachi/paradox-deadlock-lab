package com.paradox.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser tests, including a realistic snapshot document roundtrip. */
class JsonParserTest {

    @Test
    void parsesScalars() {
        assertEquals(42.0, (Double) JsonParser.parse("42"));
        assertEquals(-3.5, (Double) JsonParser.parse("-3.5"));
        assertEquals(Boolean.TRUE, JsonParser.parse("true"));
        assertEquals(null, JsonParser.parse("null"));
        assertEquals("hello", JsonParser.parse("\"hello\""));
        assertEquals("quote \" and backslash \\", JsonParser.parse("\"quote \\\" and backslash \\\\\""));
        assertEquals("\u0001", JsonParser.parse("\"\\u0001\""));
    }

    @Test
    void parsesArraysAndObjects() {
        List<?> array = (List<?>) JsonParser.parse("[1, 2, 3]");
        assertEquals(3, array.size());
        assertEquals(2.0, array.get(1));

        Map<?, ?> object = (Map<?, ?>) JsonParser.parse("{\"a\": 1, \"b\": [true, null]}");
        assertEquals(1.0, object.get("a"));
        List<?> inner = (List<?>) object.get("b");
        assertEquals(Boolean.TRUE, inner.get(0));
        assertEquals(null, inner.get(1));
    }

    @Test
    void parsesSnapshotDocument() {
        String snapshot = "{\"step\":7,"
                + "\"processes\":[{\"name\":\"P1\",\"state\":\"WAITING\",\"arrival\":0,\"priority\":0}],"
                + "\"resources\":[{\"name\":\"R1\",\"total\":1,\"available\":0,\"allocated\":1}],"
                + "\"allocations\":[[\"P1\",\"R1\",1]],"
                + "\"pendingRequests\":[[\"P1\",\"R2\",1]],"
                + "\"pendingCount\":1}";

        Map<?, ?> document = (Map<?, ?>) JsonParser.parse(snapshot);
        assertEquals(7.0, document.get("step"));
        List<?> processes = (List<?>) document.get("processes");
        Map<?, ?> process = (Map<?, ?>) processes.get(0);
        assertEquals("P1", process.get("name"));
        assertEquals("WAITING", process.get("state"));
        List<?> allocations = (List<?>) document.get("allocations");
        List<?> allocation = (List<?>) allocations.get(0);
        assertEquals("P1", allocation.get(0));
        assertEquals("R1", allocation.get(1));
        assertEquals(1.0, allocation.get(2));
    }

    @Test
    void rejectsMalformedDocuments() {
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("[1, 2"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{\"a\" 1}"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{} trailing"));
    }

    @Test
    void writerAndParserRoundtrip() {
        String written = JsonUtil.stringArray(List.of("P1", "P\"2", "line\nbreak"));
        List<?> parsed = (List<?>) JsonParser.parse(written);
        assertEquals(3, parsed.size());
        assertEquals("P1", parsed.get(0));
        assertTrue(parsed.get(1).equals("P\"2"));
        assertTrue(parsed.get(2).equals("line\nbreak"));
    }
}
