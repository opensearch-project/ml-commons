/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;

/**
 * Unit test for BedrockAgentCoreAdapter format conversions.
 */
public class BedrockAgentCoreAdapterTest {

    private BedrockAgentCoreAdapter adapter;

    @Before
    public void setUp() {
        adapter = new BedrockAgentCoreAdapter();
    }

    @Test
    public void testConversationIndexMessageToBedrockRecord() {
        ConversationIndexMessage message = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-type")
            .sessionId("session-123")
            .question("What is the weather?")
            .response("It's sunny today")
            .finalAnswer(true)
            .build();

        BedrockAgentCoreMemoryRecord record = adapter.convertToBedrockRecord(message);

        assertNotNull(record);
        assertEquals("test-type", record.getType());
        assertEquals("session-123", record.getSessionId());
        assertEquals("What is the weather?", record.getContent());
        assertEquals("It's sunny today", record.getResponse());
        assertEquals(true, record.getMetadata().get("finalAnswer"));
    }

    @Test
    public void testBedrockRecordToConversationIndexMessage() {
        BedrockAgentCoreMemoryRecord record = BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .type("test-type")
            .sessionId("session-123")
            .content("What is the weather?")
            .response("It's sunny today")
            .metadata(Map.of("finalAnswer", true))
            .build();

        ConversationIndexMessage message = adapter.convertFromBedrockRecord(record);

        assertNotNull(message);
        assertEquals("test-type", message.getType());
        assertEquals("session-123", message.getSessionId());
        assertEquals("What is the weather?", message.getQuestion());
        assertEquals("It's sunny today", message.getResponse());
        assertEquals(true, message.getFinalAnswer());
    }

    @Test
    public void testBedrockRecordsToInteractions() {
        BedrockAgentCoreMemoryRecord record1 = BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .sessionId("session-123")
            .content("Question 1")
            .response("Answer 1")
            .timestamp(Instant.now())
            .build();

        BedrockAgentCoreMemoryRecord record2 = BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .sessionId("session-123")
            .content("Question 2")
            .response("Answer 2")
            .timestamp(Instant.now())
            .build();

        List<Interaction> interactions = adapter.convertToInteractions(List.of(record1, record2));

        assertEquals(2, interactions.size());
        assertEquals("session-123", interactions.get(0).getConversationId());
        assertEquals("Question 1", interactions.get(0).getInput());
        assertEquals("Answer 1", interactions.get(0).getResponse());
        assertEquals("bedrock-agentcore", interactions.get(0).getOrigin());
    }

    @Test
    public void testInteractionToBedrockRecord() {
        Interaction interaction = Interaction
            .builder()
            .conversationId("session-123")
            .input("Test question")
            .response("Test answer")
            .createTime(Instant.now())
            .build();

        BedrockAgentCoreMemoryRecord record = adapter.convertFromInteraction(interaction);

        assertNotNull(record);
        assertEquals("session-123", record.getSessionId());
        assertEquals("Test question", record.getContent());
        assertEquals("Test answer", record.getResponse());
        assertEquals("interaction", record.getMetadata().get("source"));
    }

    @Test
    public void testNullInputHandling() {
        assertNull(adapter.convertToBedrockRecord(null));
        assertNull(adapter.convertFromBedrockRecord(null));
        assertNull(adapter.convertFromInteraction(null));

        List<Interaction> emptyInteractions = adapter.convertToInteractions(null);
        assertTrue(emptyInteractions.isEmpty());
    }

    @Test
    public void testEventDataConversion() {
        BedrockAgentCoreMemoryRecord record = BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .sessionId("session-123")
            .content("Test content")
            .response("Test response")
            .timestamp(Instant.now())
            .metadata(Map.of("key", "value"))
            .build();

        Map<String, Object> eventData = adapter.convertToEventData(record);

        assertEquals("Test content", eventData.get("content"));
        assertEquals("Test response", eventData.get("response"));
        assertEquals("session-123", eventData.get("sessionId"));
        assertNotNull(eventData.get("timestamp"));
        assertEquals(Map.of("key", "value"), eventData.get("metadata"));
    }
}
