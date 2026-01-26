/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.transport.client.Client;

public class AgenticConversationMemoryTest {

    @Mock
    private Client client;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private MLMemoryManager memoryManager;

    private AgenticConversationMemory agenticMemory;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        agenticMemory = new AgenticConversationMemory(client, "test_conversation_id", "test_memory_container_id");
    }

    @Test
    public void testGetType() {
        assert agenticMemory.getType().equals("AGENTIC_MEMORY");
    }

    @Test
    public void testSaveMessage() {
        ConversationIndexMessage message = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .sessionId("test_session")
            .question("What is AI?")
            .response("AI is artificial intelligence")
            .finalAnswer(true)
            .build();

        // Mock memory container save (primary path)
        doAnswer(invocation -> {
            ActionListener<MLAddMemoriesResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLAddMemoriesResponse.builder().workingMemoryId("working_mem_123").build());
            return null;
        }).when(client).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());

        ActionListener<CreateInteractionResponse> testListener = ActionListener.wrap(response -> {
            // Response should contain the working memory ID
            assert response.getId().equals("working_mem_123");
        }, e -> { throw new RuntimeException("Should not fail", e); });

        agenticMemory.save(message, null, null, "test_action", testListener);

        // Verify only memory container save was called (not conversation index)
        verify(client, times(1)).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());
        verify(memoryManager, never()).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testFactoryCreate() {
        AgenticConversationMemory.Factory factory = new AgenticConversationMemory.Factory();
        factory.init(client);

        Map<String, Object> params = new HashMap<>();
        params.put("memory_id", "test_memory_id");
        params.put("memory_name", "Test Memory");
        params.put("app_type", "conversational");
        params.put("memory_container_id", "test_container_id");

        ActionListener<AgenticConversationMemory> listener = ActionListener
            .wrap(memory -> { assert memory.getId().equals("test_memory_id"); }, e -> {
                throw new RuntimeException("Should not fail", e);
            });

        factory.create(params, listener);
    }

    @Test
    public void testFactoryCreateWithNewSession() {
        AgenticConversationMemory.Factory factory = new AgenticConversationMemory.Factory();
        factory.init(client);

        // Mock session creation
        doAnswer(invocation -> {
            ActionListener<MLCreateSessionResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLCreateSessionResponse.builder().sessionId("new_session_123").status("created").build());
            return null;
        }).when(client).execute(eq(MLCreateSessionAction.INSTANCE), any(), any());

        Map<String, Object> params = new HashMap<>();
        params.put("memory_name", "New Session");
        params.put("app_type", "conversational");
        params.put("memory_container_id", "test_container_id");

        ActionListener<AgenticConversationMemory> listener = ActionListener
            .wrap(memory -> { assert memory.getId().equals("new_session_123"); }, e -> {
                throw new RuntimeException("Should not fail", e);
            });

        factory.create(params, listener);

        // Verify session creation was called
        verify(client, times(1)).execute(eq(MLCreateSessionAction.INSTANCE), any(), any());
    }

    @Test
    public void testSaveWithoutMemoryContainerId() {
        AgenticConversationMemory memoryWithoutContainer = new AgenticConversationMemory(
            client,
            "test_conversation_id",
            null  // No memory container ID = should fail
        );

        ConversationIndexMessage message = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .sessionId("test_session")
            .question("What is AI?")
            .response("AI is artificial intelligence")
            .build();

        ActionListener<CreateInteractionResponse> testListener = ActionListener.wrap(response -> {
            throw new RuntimeException("Should have failed without memory container ID");
        }, e -> {
            // Expected to fail
            assert e instanceof IllegalStateException;
            assert e.getMessage().contains("Memory container ID is not configured");
        });

        memoryWithoutContainer.save(message, null, null, "test_action", testListener);

        // Verify no API calls were made
        verify(memoryManager, never()).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(client, never()).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());
    }
}
