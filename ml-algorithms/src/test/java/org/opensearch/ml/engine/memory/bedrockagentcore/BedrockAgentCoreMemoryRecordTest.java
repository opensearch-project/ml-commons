/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.Map;

import org.junit.Test;

/**
 * Unit test for BedrockAgentCoreMemoryRecord to validate data structure.
 */
public class BedrockAgentCoreMemoryRecordTest {

    @Test
    public void testBuilderPattern() {
        Instant now = Instant.now();
        Map<String, Object> metadata = Map.of("key", "value");

        BedrockAgentCoreMemoryRecord record = BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .type("test-type")
            .sessionId("session-123")
            .content("Hello world")
            .response("Hi there")
            .memoryId("memory-456")
            .metadata(metadata)
            .eventId("event-789")
            .traceId("trace-abc")
            .timestamp(now)
            .build();

        assertEquals("test-type", record.getType());
        assertEquals("session-123", record.getSessionId());
        assertEquals("Hello world", record.getContent());
        assertEquals("Hi there", record.getResponse());
        assertEquals("memory-456", record.getMemoryId());
        assertEquals(metadata, record.getMetadata());
        assertEquals("event-789", record.getEventId());
        assertEquals("trace-abc", record.getTraceId());
        assertEquals(now, record.getTimestamp());
    }

    @Test
    public void testDefaultConstructor() {
        BedrockAgentCoreMemoryRecord record = new BedrockAgentCoreMemoryRecord();
        assertNotNull(record);
        // Should not throw exceptions
    }

    @Test
    public void testInheritsFromBaseMessage() {
        BedrockAgentCoreMemoryRecord record = BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .type("test")
            .content("content")
            .build();

        assertTrue(record instanceof org.opensearch.ml.engine.memory.BaseMessage);
        assertTrue(record instanceof org.opensearch.ml.common.spi.memory.Message);
    }
}
