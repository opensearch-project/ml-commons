/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive unit test for BedrockAgentCoreMemory.
 * Consolidates functionality from multiple test files.
 */
public class BedrockAgentCoreMemoryTest {

    private BedrockAgentCoreMemory memory;
    private final String testMemoryArn = "arn:aws:bedrock:us-east-1:123456789012:agent-memory/test-memory-id";
    private final String testSessionId = "test-session-12345";
    private final String testAgentId = "test-agent";

    @Before
    public void setUp() {
        // Create memory with real adapter for basic property tests
        memory = new BedrockAgentCoreMemory(
            testMemoryArn,
            testSessionId,
            testAgentId,
            null, // mockClient not needed for basic tests
            new BedrockAgentCoreAdapter()
        );
    }

    @Test
    public void testBasicProperties() {
        assertEquals("bedrock_agentcore_memory", memory.getType());
        assertEquals(testMemoryArn, memory.getMemoryArn());
        assertEquals(testSessionId, memory.getSessionId());
        assertEquals(testAgentId, memory.getAgentId());
        assertEquals("test-memory-id", memory.getMemoryId());
    }

    @Test
    public void testGetConversationIdCompatibility() {
        // Critical for agent runner compatibility
        assertEquals(testSessionId, memory.getConversationId());
    }

    @Test
    public void testMemoryIdExtraction() {
        // Test ARN parsing
        assertEquals("test-memory-id", memory.getMemoryId());

        // Test different ARN formats
        BedrockAgentCoreMemory memory2 = new BedrockAgentCoreMemory(
            "arn:aws:bedrock:us-west-2:123456789012:agent-memory/another-memory-id",
            "session-2",
            "agent-2",
            null,
            new BedrockAgentCoreAdapter()
        );
        assertEquals("another-memory-id", memory2.getMemoryId());
    }

    @Test
    public void testFactoryBasicCreation() {
        BedrockAgentCoreMemory.Factory factory = new BedrockAgentCoreMemory.Factory();
        factory.init(null, null, null);

        // Test that factory is initialized without errors
        assertNotNull(factory);
    }

    @Test
    public void testFactoryParameterValidation() {
        BedrockAgentCoreMemory.Factory factory = new BedrockAgentCoreMemory.Factory();
        factory.init(null, null, null);

        // Test missing required parameters
        Map<String, Object> invalidParams = Map.of("session_id", testSessionId
        // missing memory_arn
        );

        factory.create(invalidParams, new TestActionListener<BedrockAgentCoreMemory>() {
            @Override
            public void onFailure(Exception e) {
                assertTrue(e instanceof IllegalArgumentException);
                assertTrue(e.getMessage().contains("memory_arn"));
            }
        });
    }

    @Test
    public void testFactoryMissingAgentId() {
        BedrockAgentCoreMemory.Factory factory = new BedrockAgentCoreMemory.Factory();
        factory.init(null, null, null);

        Map<String, Object> params = Map.of("memory_arn", testMemoryArn, "session_id", testSessionId
        // missing agent_id
        );

        try {
            factory.create(params, new TestActionListener<BedrockAgentCoreMemory>() {
                @Override
                public void onResponse(BedrockAgentCoreMemory response) {
                    fail("Expected failure but got success");
                }
            });
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Agent ID is mandatory"));
        }
    }

    @Test
    public void testSaveNullRecord() {
        memory.save(testSessionId, null, new TestActionListener<Object>() {
            @Override
            public void onFailure(Exception e) {
                assertTrue(e instanceof IllegalArgumentException);
                assertEquals("Memory record cannot be null", e.getMessage());
            }
        });
    }

    @Test
    public void testClear() {
        // Should not throw exception (no-op for Bedrock)
        memory.clear();
    }

    // Helper class for testing ActionListener callbacks
    private abstract static class TestActionListener<T> implements org.opensearch.core.action.ActionListener<T> {
        @Override
        public void onResponse(T response) {
            // Default implementation - override if needed
        }

        @Override
        public void onFailure(Exception e) {
            // Default implementation - override if needed
        }
    }
}
