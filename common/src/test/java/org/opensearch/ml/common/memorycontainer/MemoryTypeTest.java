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
        assertEquals(1, MemoryType.values().length);
        assertEquals(MemoryType.SEMANTIC, MemoryType.valueOf("SEMANTIC"));
    }

    @Test
    public void testGetValue() {
        assertEquals("SEMANTIC", MemoryType.SEMANTIC.getValue());
    }

    @Test
    public void testToString() {
        assertEquals("SEMANTIC", MemoryType.SEMANTIC.toString());
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryType.SEMANTIC, MemoryType.fromString("SEMANTIC"));

        // Test case insensitive
        assertEquals(MemoryType.SEMANTIC, MemoryType.fromString("semantic"));
        assertEquals(MemoryType.SEMANTIC, MemoryType.fromString("SeMANtIC"));
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
