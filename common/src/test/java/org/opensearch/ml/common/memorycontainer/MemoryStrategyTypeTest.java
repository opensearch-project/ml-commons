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
        assertEquals(3, MemoryStrategyType.values().length);
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.valueOf("SEMANTIC"));
        assertEquals(MemoryStrategyType.USER_PREFERENCE, MemoryStrategyType.valueOf("USER_PREFERENCE"));
        assertEquals(MemoryStrategyType.SUMMARY, MemoryStrategyType.valueOf("SUMMARY"));
    }

    @Test
    public void testGetValue() {
        assertEquals("SEMANTIC", MemoryStrategyType.SEMANTIC.getValue());
        assertEquals("USER_PREFERENCE", MemoryStrategyType.USER_PREFERENCE.getValue());
        assertEquals("SUMMARY", MemoryStrategyType.SUMMARY.getValue());
    }

    @Test
    public void testToString() {
        assertEquals("SEMANTIC", MemoryStrategyType.SEMANTIC.toString());
        assertEquals("USER_PREFERENCE", MemoryStrategyType.USER_PREFERENCE.toString());
        assertEquals("SUMMARY", MemoryStrategyType.SUMMARY.toString());
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.fromString("SEMANTIC"));
        assertEquals(MemoryStrategyType.USER_PREFERENCE, MemoryStrategyType.fromString("USER_PREFERENCE"));
        assertEquals(MemoryStrategyType.SUMMARY, MemoryStrategyType.fromString("SUMMARY"));

        // Test case insensitive
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.fromString("semantic"));
        assertEquals(MemoryStrategyType.SEMANTIC, MemoryStrategyType.fromString("SeMANtIC"));
        assertEquals(MemoryStrategyType.USER_PREFERENCE, MemoryStrategyType.fromString("user_preference"));
        assertEquals(MemoryStrategyType.USER_PREFERENCE, MemoryStrategyType.fromString("User_Preference"));
        assertEquals(MemoryStrategyType.SUMMARY, MemoryStrategyType.fromString("summary"));
        assertEquals(MemoryStrategyType.SUMMARY, MemoryStrategyType.fromString("SuMmArY"));
    }

    @Test
    public void testFromString_Null() {
        assertNull(MemoryStrategyType.fromString(null));
    }

    @Test
    public void testFromString_InvalidValue() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MemoryStrategyType.fromString("INVALID_TYPE")
        );
        assertEquals("Invalid memory type: INVALID_TYPE. Must be SEMANTIC, USER_PREFERENCE, or SUMMARY", exception.getMessage());
    }

    @Test
    public void testFromString_EmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryStrategyType.fromString(""));
        assertEquals("Invalid memory type: . Must be SEMANTIC, USER_PREFERENCE, or SUMMARY", exception.getMessage());
    }

    @Test
    public void testFromString_Whitespace() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryStrategyType.fromString("   "));
        assertEquals("Invalid memory type:    . Must be SEMANTIC, USER_PREFERENCE, or SUMMARY", exception.getMessage());
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
