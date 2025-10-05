/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MemoryTypeTest {

    @Test
    public void testEnumValues() {
        // Test all enum values exist
        assertEquals(4, MemoryType.values().length);
        assertEquals(MemoryType.SESSIONS, MemoryType.valueOf("SESSIONS"));
        assertEquals(MemoryType.WORKING, MemoryType.valueOf("WORKING"));
        assertEquals(MemoryType.LONG_TERM, MemoryType.valueOf("LONG_TERM"));
        assertEquals(MemoryType.HISTORY, MemoryType.valueOf("HISTORY"));
    }

    @Test
    public void testGetValue() {
        assertEquals("sessions", MemoryType.SESSIONS.getValue());
        assertEquals("working", MemoryType.WORKING.getValue());
        assertEquals("long-term", MemoryType.LONG_TERM.getValue());
        assertEquals("history", MemoryType.HISTORY.getValue());
    }

    @Test
    public void testGetIndexSuffix() {
        assertEquals("sessions", MemoryType.SESSIONS.getIndexSuffix());
        assertEquals("working", MemoryType.WORKING.getIndexSuffix());
        assertEquals("long-term", MemoryType.LONG_TERM.getIndexSuffix());
        assertEquals("history", MemoryType.HISTORY.getIndexSuffix());
    }

    @Test
    public void testIsDisableable() {
        assertTrue(MemoryType.SESSIONS.isDisableable());
        assertFalse(MemoryType.WORKING.isDisableable());
        assertFalse(MemoryType.LONG_TERM.isDisableable());
        assertTrue(MemoryType.HISTORY.isDisableable());
    }

    @Test
    public void testToIndexName() {
        String prefix = ".plugins-ml-am-";
        assertEquals(".plugins-ml-am-sessions", MemoryType.SESSIONS.toIndexName(prefix));
        assertEquals(".plugins-ml-am-working", MemoryType.WORKING.toIndexName(prefix));
        assertEquals(".plugins-ml-am-long-term", MemoryType.LONG_TERM.toIndexName(prefix));
        assertEquals(".plugins-ml-am-history", MemoryType.HISTORY.toIndexName(prefix));
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryType.SESSIONS, MemoryType.fromString("sessions"));
        assertEquals(MemoryType.WORKING, MemoryType.fromString("working"));
        assertEquals(MemoryType.LONG_TERM, MemoryType.fromString("long-term"));
        assertEquals(MemoryType.HISTORY, MemoryType.fromString("history"));

        // Test case insensitive
        assertEquals(MemoryType.SESSIONS, MemoryType.fromString("SESSIONS"));
        assertEquals(MemoryType.SESSIONS, MemoryType.fromString("Sessions"));
        assertEquals(MemoryType.WORKING, MemoryType.fromString("WORKING"));
        assertEquals(MemoryType.LONG_TERM, MemoryType.fromString("LONG-TERM"));
        assertEquals(MemoryType.HISTORY, MemoryType.fromString("HISTORY"));
    }

    @Test
    public void testFromString_Null() {
        assertNull(MemoryType.fromString(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromString_InvalidValue() {
        MemoryType.fromString("INVALID_TYPE");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromString_EmptyString() {
        MemoryType.fromString("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromString_WhitespaceString() {
        MemoryType.fromString("   ");
    }

    @Test
    public void testIsValid() {
        assertTrue(MemoryType.isValid("sessions"));
        assertTrue(MemoryType.isValid("working"));
        assertTrue(MemoryType.isValid("long-term"));
        assertTrue(MemoryType.isValid("history"));

        assertTrue(MemoryType.isValid("SESSIONS"));
        assertTrue(MemoryType.isValid("WORKING"));

        assertFalse(MemoryType.isValid("invalid"));
        assertFalse(MemoryType.isValid(null));
        assertFalse(MemoryType.isValid(""));
    }

    @Test
    public void testGetAllValues() {
        List<String> allValues = MemoryType.getAllValues();
        assertEquals(4, allValues.size());
        assertTrue(allValues.contains("sessions"));
        assertTrue(allValues.contains("working"));
        assertTrue(allValues.contains("long-term"));
        assertTrue(allValues.contains("history"));
    }

    @Test
    public void testGetAllValuesAsString() {
        String allValuesStr = MemoryType.getAllValuesAsString();
        assertNotNull(allValuesStr);
        assertTrue(allValuesStr.contains("sessions"));
        assertTrue(allValuesStr.contains("working"));
        assertTrue(allValuesStr.contains("long-term"));
        assertTrue(allValuesStr.contains("history"));
        assertTrue(allValuesStr.contains(", "));
    }

    @Test
    public void testToString() {
        assertEquals("sessions", MemoryType.SESSIONS.toString());
        assertEquals("working", MemoryType.WORKING.toString());
        assertEquals("long-term", MemoryType.LONG_TERM.toString());
        assertEquals("history", MemoryType.HISTORY.toString());
    }

    @Test
    public void testEnumConsistency() {
        // Verify each enum's getValue() returns expected value
        for (MemoryType type : MemoryType.values()) {
            assertNotNull(type.getValue());
            assertNotNull(type.getIndexSuffix());
            assertEquals(type, MemoryType.fromString(type.getValue()));
            assertEquals(type.getValue(), type.toString());
        }
    }
}
