package com.paradox.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilTest {

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"\"", JsonUtil.quote(""));
        assertEquals("\"plain\"", JsonUtil.quote("plain"));
        assertEquals("\"say \\\"hi\\\"\"", JsonUtil.quote("say \"hi\""));
        assertEquals("\"back\\\\slash\"", JsonUtil.quote("back\\slash"));
        assertEquals("\"line1\\nline2\"", JsonUtil.quote("line1\nline2"));
        assertEquals("\"tab\\there\"", JsonUtil.quote("tab\there"));
        // Control characters must be unicode-escaped, not raw.
        assertEquals("\"\\u0001\"", JsonUtil.quote("\u0001"));
        assertEquals("null", JsonUtil.quote(null));
    }

    @Test
    void buildsStringArrays() {
        assertEquals("[]", JsonUtil.stringArray(List.of()));
        assertEquals("[\"P1\"]", JsonUtil.stringArray(List.of("P1")));
        assertEquals("[\"P1\",\"P2\",\"P3\"]", JsonUtil.stringArray(List.of("P1", "P2", "P3")));
        // Names containing quotes stay valid JSON.
        assertEquals("[\"P\\\"1\"]", JsonUtil.stringArray(List.of("P\"1")));
    }

    @Test
    void buildsIntArraysAndMatrices() {
        assertEquals("[]", JsonUtil.intArray(new int[0]));
        assertEquals("[0,1,2]", JsonUtil.intArray(new int[]{0, 1, 2}));
        assertEquals("[[1,0],[0,2]]",
                JsonUtil.intMatrix(new int[][]{{1, 0}, {0, 2}}));
        assertEquals("[[]]", JsonUtil.intMatrix(new int[][]{{}}));
    }
}
