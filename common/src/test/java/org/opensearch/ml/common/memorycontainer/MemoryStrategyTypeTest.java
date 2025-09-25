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

public class MemoryStrategyTypeTest {

    @Test
    public void testEnumValues() {
        // Test all enum values exist
        assertEquals(1, MemoryStrategyType.values().length);
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.valueOf("SEMANTIC"));
    }

    @Test
    public void testGetValue() {
        assertEquals("SEMANTIC", MemoryStrategyType.SEMANTIC.getValue());
    }

    @Test
    public void testToString() {
        assertEquals("SEMANTIC", MemoryStrategyType.SEMANTIC.toString());
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.fromString("SEMANTIC"));

        // Test case insensitive
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.fromString("semantic"));
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.fromString("SeMaNtIc"));
    }

    @Test
    public void testFromString_Null() {
        assertNull(MemoryStrategyType.fromString(null));
    }

    @Test
    public void testFromString_InvalidValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryStrategyType.fromString("INVALID_TYPE"));
        assertEquals("Invalid memory strategy type: INVALID_TYPE. Must be: SEMANTIC", exception.getMessage());
    }

    @Test
    public void testFromString_EmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryStrategyType.fromString(""));
        assertEquals("Invalid memory strategy type: . Must be: SEMANTIC", exception.getMessage());
    }

    @Test
    public void testFromString_Whitespace() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryStrategyType.fromString("   "));
        assertEquals("Invalid memory strategy type:    . Must be: SEMANTIC", exception.getMessage());
    }

    @Test
    public void testEnumConsistency() {
        // Verify each enum's getValue() returns its name
        for (MemoryStrategyType type : MemoryStrategyType.values()) {
            assertNotNull(type.getValue());
            assertEquals(type.getValue(), type.toString());
            assertEquals(type, MemoryStrategyType.fromString(type.getValue()));
        }
    }
}