/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;
import org.opensearch.core.action.ActionListener;

/**
 * Unit tests for ChatMemoryAdapter interface default methods.
 */
public class ChatMemoryAdapterTest {

    /**
     * Test implementation of ChatMemoryAdapter for testing default methods
     */
    private static class TestChatMemoryAdapter implements ChatMemoryAdapter {
        @Override
        public void getMessages(ActionListener<List<ChatMessage>> listener) {
            // Test implementation - not used in these tests
        }

        @Override
        public String getConversationId() {
            return "test-conversation-id";
        }

        @Override
        public String getMemoryContainerId() {
            return "test-memory-container-id";
        }
    }

    @Test
    public void testSaveInteractionDefaultImplementation() {
        TestChatMemoryAdapter adapter = new TestChatMemoryAdapter();
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        // Test that default implementation throws UnsupportedOperationException
        adapter.saveInteraction("question", "response", "parentId", 1, "action", listener);

        // Verify that onFailure was called with UnsupportedOperationException
        org.mockito.Mockito
            .verify(listener)
            .onFailure(
                org.mockito.ArgumentMatchers
                    .argThat(
                        exception -> exception instanceof UnsupportedOperationException
                            && "Save not implemented".equals(exception.getMessage())
                    )
            );
    }

    @Test
    public void testUpdateInteractionDefaultImplementation() {
        TestChatMemoryAdapter adapter = new TestChatMemoryAdapter();
        @SuppressWarnings("unchecked")
        ActionListener<Void> listener = mock(ActionListener.class);

        // Test that default implementation throws UnsupportedOperationException
        adapter.updateInteraction("interactionId", java.util.Map.of("key", "value"), listener);

        // Verify that onFailure was called with UnsupportedOperationException
        org.mockito.Mockito
            .verify(listener)
            .onFailure(
                org.mockito.ArgumentMatchers
                    .argThat(
                        exception -> exception instanceof UnsupportedOperationException
                            && "Update interaction not implemented".equals(exception.getMessage())
                    )
            );
    }

    @Test
    public void testSaveTraceDataDefaultImplementation() {
        TestChatMemoryAdapter adapter = new TestChatMemoryAdapter();
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        // Test that default implementation throws UnsupportedOperationException
        adapter.saveTraceData("toolName", "input", "output", "parentId", 1, "action", listener);

        // Verify that onFailure was called with UnsupportedOperationException
        org.mockito.Mockito
            .verify(listener)
            .onFailure(
                org.mockito.ArgumentMatchers
                    .argThat(
                        exception -> exception instanceof UnsupportedOperationException
                            && "Save trace data not implemented".equals(exception.getMessage())
                    )
            );
    }

    @Test
    public void testGetConversationId() {
        TestChatMemoryAdapter adapter = new TestChatMemoryAdapter();
        assertEquals("test-conversation-id", adapter.getConversationId());
    }

    @Test
    public void testGetMemoryContainerId() {
        TestChatMemoryAdapter adapter = new TestChatMemoryAdapter();
        assertEquals("test-memory-container-id", adapter.getMemoryContainerId());
    }
}
