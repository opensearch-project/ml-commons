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
        assertEquals(3, MemoryType.values().length);
        assertEquals(MemoryType.SEMANTIC, MemoryType.valueOf("SEMANTIC"));
        assertEquals(MemoryType.USER_PREFERENCE, MemoryType.valueOf("USER_PREFERENCE"));
        assertEquals(MemoryType.SUMMARY, MemoryType.valueOf("SUMMARY"));
    }

    @Test
    public void testGetValue() {
        assertEquals("SEMANTIC", MemoryType.SEMANTIC.getValue());
        assertEquals("USER_PREFERENCE", MemoryType.USER_PREFERENCE.getValue());
        assertEquals("SUMMARY", MemoryType.SUMMARY.getValue());
    }

    @Test
    public void testToString() {
        assertEquals("SEMANTIC", MemoryType.SEMANTIC.toString());
        assertEquals("USER_PREFERENCE", MemoryType.USER_PREFERENCE.toString());
        assertEquals("SUMMARY", MemoryType.SUMMARY.toString());
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryType.SEMANTIC, MemoryType.fromString("SEMANTIC"));
        assertEquals(MemoryType.USER_PREFERENCE, MemoryType.fromString("USER_PREFERENCE"));
        assertEquals(MemoryType.SUMMARY, MemoryType.fromString("SUMMARY"));

        // Test case insensitive
        assertEquals(MemoryType.SEMANTIC, MemoryType.fromString("semantic"));
        assertEquals(MemoryType.SEMANTIC, MemoryType.fromString("SeMANtIC"));
        assertEquals(MemoryType.USER_PREFERENCE, MemoryType.fromString("user_preference"));
        assertEquals(MemoryType.USER_PREFERENCE, MemoryType.fromString("User_Preference"));
        assertEquals(MemoryType.SUMMARY, MemoryType.fromString("summary"));
        assertEquals(MemoryType.SUMMARY, MemoryType.fromString("SuMmArY"));
    }

    @Test
    public void testFromString_Null() {
        assertNull(MemoryType.fromString(null));
    }

    @Test
    public void testFromString_InvalidValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryType.fromString("INVALID_TYPE"));
        assertEquals("Invalid memory type: INVALID_TYPE. Must be SEMANTIC, USER_PREFERENCE, or SUMMARY", exception.getMessage());
    }

    @Test
    public void testFromString_EmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryType.fromString(""));
        assertEquals("Invalid memory type: . Must be SEMANTIC, USER_PREFERENCE, or SUMMARY", exception.getMessage());
    }

    @Test
    public void testFromString_Whitespace() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryType.fromString("   "));
        assertEquals("Invalid memory type:    . Must be SEMANTIC, USER_PREFERENCE, or SUMMARY", exception.getMessage());
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
