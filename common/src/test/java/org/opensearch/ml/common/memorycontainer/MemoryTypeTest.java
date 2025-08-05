/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MemoryTypeTest {

    @Test
    public void testEnumValues() {
        // Test all enum values exist
        assertEquals(2, MemoryType.values().length);
        assertEquals(MemoryType.RAW_MESSAGE, MemoryType.valueOf("RAW_MESSAGE"));
        assertEquals(MemoryType.FACT, MemoryType.valueOf("FACT"));
    }

    @Test
    public void testGetValue() {
        assertEquals("RAW_MESSAGE", MemoryType.RAW_MESSAGE.getValue());
        assertEquals("FACT", MemoryType.FACT.getValue());
    }

    @Test
    public void testToString() {
        assertEquals("RAW_MESSAGE", MemoryType.RAW_MESSAGE.toString());
        assertEquals("FACT", MemoryType.FACT.toString());
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryType.RAW_MESSAGE, MemoryType.fromString("RAW_MESSAGE"));
        assertEquals(MemoryType.FACT, MemoryType.fromString("FACT"));

        // Test case insensitive
        assertEquals(MemoryType.RAW_MESSAGE, MemoryType.fromString("raw_message"));
        assertEquals(MemoryType.FACT, MemoryType.fromString("FaCt"));
        assertEquals(MemoryType.RAW_MESSAGE, MemoryType.fromString("Raw_Message"));
    }

    @Test
    public void testFromString_Null() {
        assertNull(MemoryType.fromString(null));
    }

    @Test
    public void testFromString_InvalidValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryType.fromString("INVALID_TYPE"));
        assertEquals("Invalid memory type: INVALID_TYPE. Must be either RAW_MESSAGE or FACT", exception.getMessage());
    }

    @Test
    public void testFromString_EmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryType.fromString(""));
        assertEquals("Invalid memory type: . Must be either RAW_MESSAGE or FACT", exception.getMessage());
    }

    @Test
    public void testFromString_Whitespace() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryType.fromString("   "));
        assertEquals("Invalid memory type:    . Must be either RAW_MESSAGE or FACT", exception.getMessage());
    }

    @Test
    public void testEnumConsistency() {
        // Verify each enum's getValue() returns its name
        for (MemoryType type : MemoryType.values()) {
            assertNotNull(type.getValue());
            assertEquals(type.getValue(), type.toString());
            assertEquals(type, MemoryType.fromString(type.getValue()));
        }
    }
}
