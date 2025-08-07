/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MemoryEventTest {

    @Test
    public void testEnumValues() {
        // Test all enum values exist
        assertEquals(4, MemoryEvent.values().length);
        assertEquals(MemoryEvent.ADD, MemoryEvent.valueOf("ADD"));
        assertEquals(MemoryEvent.UPDATE, MemoryEvent.valueOf("UPDATE"));
        assertEquals(MemoryEvent.DELETE, MemoryEvent.valueOf("DELETE"));
        assertEquals(MemoryEvent.NONE, MemoryEvent.valueOf("NONE"));
    }

    @Test
    public void testGetValue() {
        assertEquals("ADD", MemoryEvent.ADD.getValue());
        assertEquals("UPDATE", MemoryEvent.UPDATE.getValue());
        assertEquals("DELETE", MemoryEvent.DELETE.getValue());
        assertEquals("NONE", MemoryEvent.NONE.getValue());
    }

    @Test
    public void testToString() {
        assertEquals("ADD", MemoryEvent.ADD.toString());
        assertEquals("UPDATE", MemoryEvent.UPDATE.toString());
        assertEquals("DELETE", MemoryEvent.DELETE.toString());
        assertEquals("NONE", MemoryEvent.NONE.toString());
    }

    @Test
    public void testFromString_ValidValues() {
        // Test exact match
        assertEquals(MemoryEvent.ADD, MemoryEvent.fromString("ADD"));
        assertEquals(MemoryEvent.UPDATE, MemoryEvent.fromString("UPDATE"));
        assertEquals(MemoryEvent.DELETE, MemoryEvent.fromString("DELETE"));
        assertEquals(MemoryEvent.NONE, MemoryEvent.fromString("NONE"));

        // Test case insensitive
        assertEquals(MemoryEvent.ADD, MemoryEvent.fromString("add"));
        assertEquals(MemoryEvent.UPDATE, MemoryEvent.fromString("Update"));
        assertEquals(MemoryEvent.DELETE, MemoryEvent.fromString("dElEtE"));
        assertEquals(MemoryEvent.NONE, MemoryEvent.fromString("none"));
    }

    @Test
    public void testFromString_Null() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryEvent.fromString(null));
        assertEquals("Memory event value cannot be null", exception.getMessage());
    }

    @Test
    public void testFromString_InvalidValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryEvent.fromString("INVALID_EVENT"));
        assertEquals("Unknown memory event: INVALID_EVENT", exception.getMessage());
    }

    @Test
    public void testFromString_EmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryEvent.fromString(""));
        assertEquals("Unknown memory event: ", exception.getMessage());
    }

    @Test
    public void testFromString_Whitespace() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MemoryEvent.fromString("   "));
        assertEquals("Unknown memory event:    ", exception.getMessage());
    }

    @Test
    public void testEnumConsistency() {
        // Verify each enum's getValue() returns its name
        for (MemoryEvent event : MemoryEvent.values()) {
            assertNotNull(event.getValue());
            assertEquals(event.getValue(), event.toString());
            assertEquals(event, MemoryEvent.fromString(event.getValue()));
        }
    }

    @Test
    public void testAllEventsHandled() {
        // Ensure all events are properly handled in fromString
        String[] expectedEvents = { "ADD", "UPDATE", "DELETE", "NONE" };
        for (String eventStr : expectedEvents) {
            MemoryEvent event = MemoryEvent.fromString(eventStr);
            assertNotNull("Event should not be null for: " + eventStr, event);
            assertEquals(eventStr, event.getValue());
        }
    }
}
