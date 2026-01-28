/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.ml.common.memory.Message;
import org.opensearch.ml.common.memorycontainer.MLWorkingMemory;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.transport.client.Client;

public class AgenticConversationMemoryTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
        assertEquals("agentic_conversation", agenticMemory.getType());
    }

    @Test
    public void testGetId() {
        assertEquals("test_conversation_id", agenticMemory.getId());
    }

    @Test
    public void testGetConversationId() {
        assertEquals("test_conversation_id", agenticMemory.getConversationId());
    }

    @Test
    public void testGetMemoryContainerId() {
        assertEquals("test_memory_container_id", agenticMemory.getMemoryContainerId());
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
            assertEquals("working_mem_123", response.getId());
        }, e -> { throw new RuntimeException("Should not fail", e); });

        agenticMemory.save(message, null, null, "test_action", testListener);

        // Verify only memory container save was called (not conversation index)
        verify(client, times(1)).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());
        verify(memoryManager, never()).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testSaveMessageWithoutListener() {
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

        // Test the save method without explicit listener (uses default)
        agenticMemory.save(message, null, null, "test_action");

        // Verify memory container save was called
        verify(client, times(1)).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());
    }

    @Test
    public void testSaveTrace() {
        ConversationIndexMessage traceMessage = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .sessionId("test_session")
            .question("search_query: weather forecast")
            .response("{\"temperature\": 72, \"condition\": \"sunny\"}")
            .finalAnswer(false)
            .build();

        // Mock memory container save
        doAnswer(invocation -> {
            ActionListener<MLAddMemoriesResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLAddMemoriesResponse.builder().workingMemoryId("trace_mem_456").build());
            return null;
        }).when(client).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());

        ActionListener<CreateInteractionResponse> testListener = ActionListener.wrap(response -> {
            assertEquals("trace_mem_456", response.getId());
        }, e -> { throw new RuntimeException("Should not fail", e); });

        // Save with traceNum to indicate it's a trace
        agenticMemory.save(traceMessage, "parent_msg_123", 1, "SearchTool", testListener);

        // Verify the request was made
        ArgumentCaptor<MLAddMemoriesRequest> captor = ArgumentCaptor.forClass(MLAddMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLAddMemoriesAction.INSTANCE), captor.capture(), any());

        // Verify trace metadata
        MLAddMemoriesRequest request = captor.getValue();
        assertNotNull(request);
    }

    @Test
    public void testSaveFailure() {
        ConversationIndexMessage message = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .sessionId("test_session")
            .question("What is AI?")
            .response("AI is artificial intelligence")
            .build();

        // Mock memory container save failure
        doAnswer(invocation -> {
            ActionListener<MLAddMemoriesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Save failed"));
            return null;
        }).when(client).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());

        ActionListener<CreateInteractionResponse> testListener = ActionListener.wrap(response -> {
            throw new RuntimeException("Should have failed");
        }, e -> {
            assertTrue(e instanceof RuntimeException);
            assertEquals("Save failed", e.getMessage());
        });

        agenticMemory.save(message, null, null, "test_action", testListener);
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
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().contains("Memory container ID is not configured"));
        });

        memoryWithoutContainer.save(message, null, null, "test_action", testListener);

        // Verify no API calls were made
        verify(memoryManager, never()).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(client, never()).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());
    }

    @Test
    public void testSaveWithEmptyMemoryContainerId() {
        AgenticConversationMemory memoryWithEmptyContainer = new AgenticConversationMemory(
            client,
            "test_conversation_id",
            ""  // Empty memory container ID = should fail
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
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().contains("Memory container ID is not configured"));
        });

        memoryWithEmptyContainer.save(message, null, null, "test_action", testListener);
    }

    @Test
    public void testUpdate() {
        Map<String, Object> existingStructuredData = new HashMap<>();
        existingStructuredData.put("input", "original question");
        existingStructuredData.put("response", "original response");

        MLWorkingMemory existingMemory = MLWorkingMemory
            .builder()
            .memoryContainerId("test_memory_container_id")
            .structuredDataBlob(existingStructuredData)
            .build();

        // Mock get memory
        doAnswer(invocation -> {
            ActionListener<MLGetMemoryResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLGetMemoryResponse.builder().workingMemory(existingMemory).build());
            return null;
        }).when(client).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());

        // Mock update memory - MLUpdateMemoryAction returns IndexResponse
        // Create a properly mocked IndexResponse
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getShardInfo()).thenReturn(null);
        when(indexResponse.getShardId()).thenReturn(new org.opensearch.core.index.shard.ShardId("test", "test", 0));
        when(indexResponse.getId()).thenReturn("msg_123");
        when(indexResponse.getSeqNo()).thenReturn(1L);
        when(indexResponse.getPrimaryTerm()).thenReturn(1L);
        when(indexResponse.getVersion()).thenReturn(1L);
        when(indexResponse.getResult()).thenReturn(org.opensearch.action.DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).execute(eq(MLUpdateMemoryAction.INSTANCE), any(), any());

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "updated response");
        updateContent.put("additional_info", "some extra info");

        ActionListener<UpdateResponse> testListener = ActionListener.wrap(response -> {
            // Success
            assertNotNull(response);
        }, e -> { throw new RuntimeException("Should not fail", e); });

        agenticMemory.update("msg_123", updateContent, testListener);

        // Verify both get and update were called
        verify(client, times(1)).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());
        verify(client, times(1)).execute(eq(MLUpdateMemoryAction.INSTANCE), any(), any());
    }

    @Test
    public void testUpdateWithNullStructuredData() {
        MLWorkingMemory existingMemory = MLWorkingMemory
            .builder()
            .memoryContainerId("test_memory_container_id")
            .structuredDataBlob(null)  // null structured data
            .build();

        // Mock get memory
        doAnswer(invocation -> {
            ActionListener<MLGetMemoryResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLGetMemoryResponse.builder().workingMemory(existingMemory).build());
            return null;
        }).when(client).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());

        // Mock update memory - create properly mocked IndexResponse
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getShardInfo()).thenReturn(null);
        when(indexResponse.getShardId()).thenReturn(new org.opensearch.core.index.shard.ShardId("test", "test", 0));
        when(indexResponse.getId()).thenReturn("msg_123");
        when(indexResponse.getSeqNo()).thenReturn(1L);
        when(indexResponse.getPrimaryTerm()).thenReturn(1L);
        when(indexResponse.getVersion()).thenReturn(1L);
        when(indexResponse.getResult()).thenReturn(org.opensearch.action.DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).execute(eq(MLUpdateMemoryAction.INSTANCE), any(), any());

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "new response");

        ActionListener<UpdateResponse> testListener = ActionListener.wrap(response -> { assertNotNull(response); }, e -> {
            throw new RuntimeException("Should not fail", e);
        });

        agenticMemory.update("msg_123", updateContent, testListener);

        verify(client, times(1)).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());
        verify(client, times(1)).execute(eq(MLUpdateMemoryAction.INSTANCE), any(), any());
    }

    @Test
    public void testUpdateWithNullWorkingMemory() {
        // Mock get memory returning null working memory
        doAnswer(invocation -> {
            ActionListener<MLGetMemoryResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLGetMemoryResponse.builder().workingMemory(null).build());
            return null;
        }).when(client).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "new response");

        ActionListener<UpdateResponse> testListener = ActionListener
            .wrap(response -> { throw new RuntimeException("Should have failed"); }, e -> {
                assertTrue(e instanceof IllegalStateException);
                assertTrue(e.getMessage().contains("Working memory not found"));
            });

        agenticMemory.update("msg_123", updateContent, testListener);
    }

    @Test
    public void testUpdateWithoutMemoryContainerId() {
        AgenticConversationMemory memoryWithoutContainer = new AgenticConversationMemory(client, "test_conversation_id", null);

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "updated response");

        ActionListener<UpdateResponse> testListener = ActionListener
            .wrap(response -> { throw new RuntimeException("Should have failed"); }, e -> {
                assertTrue(e instanceof IllegalStateException);
                assertTrue(e.getMessage().contains("Memory container ID is not configured"));
            });

        memoryWithoutContainer.update("msg_123", updateContent, testListener);

        verify(client, never()).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());
    }

    @Test
    public void testUpdateGetFailure() {
        // Mock get memory failure
        doAnswer(invocation -> {
            ActionListener<MLGetMemoryResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Get failed"));
            return null;
        }).when(client).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "updated response");

        ActionListener<UpdateResponse> testListener = ActionListener
            .wrap(response -> { throw new RuntimeException("Should have failed"); }, e -> {
                assertTrue(e instanceof RuntimeException);
                assertEquals("Get failed", e.getMessage());
            });

        agenticMemory.update("msg_123", updateContent, testListener);
    }

    @Test
    public void testUpdateUpdateFailure() {
        Map<String, Object> existingStructuredData = new HashMap<>();
        existingStructuredData.put("input", "original question");

        MLWorkingMemory existingMemory = MLWorkingMemory
            .builder()
            .memoryContainerId("test_memory_container_id")
            .structuredDataBlob(existingStructuredData)
            .build();

        // Mock get memory success
        doAnswer(invocation -> {
            ActionListener<MLGetMemoryResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLGetMemoryResponse.builder().workingMemory(existingMemory).build());
            return null;
        }).when(client).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());

        // Mock update memory failure
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Update failed"));
            return null;
        }).when(client).execute(eq(MLUpdateMemoryAction.INSTANCE), any(), any());

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "updated response");

        ActionListener<UpdateResponse> testListener = ActionListener
            .wrap(response -> { throw new RuntimeException("Should have failed"); }, e -> {
                assertTrue(e instanceof RuntimeException);
                assertEquals("Update failed", e.getMessage());
            });

        agenticMemory.update("msg_123", updateContent, testListener);
    }

    @Test
    public void testGetMessages() {
        // Create mock search response
        SearchHit hit1 = createMockSearchHit("hit_1", createStructuredDataBlob("Question 1", "Response 1"));
        SearchHit hit2 = createMockSearchHit("hit_2", createStructuredDataBlob("Question 2", "Response 2"));
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse searchResponse = new SearchResponse(
            new SearchResponseSections(searchHits, null, null, false, false, null, 0),
            null,
            1,
            1,
            0,
            10,
            new ShardSearchFailure[] {},
            SearchResponse.Clusters.EMPTY
        );

        // Mock search memories
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());

        ActionListener<List<Message>> testListener = ActionListener.wrap(messages -> {
            assertNotNull(messages);
            assertEquals(2, messages.size());
        }, e -> { throw new RuntimeException("Should not fail", e); });

        agenticMemory.getMessages(10, testListener);

        verify(client, times(1)).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());
    }

    @Test
    public void testGetMessagesWithoutMemoryContainerId() {
        AgenticConversationMemory memoryWithoutContainer = new AgenticConversationMemory(client, "test_conversation_id", null);

        ActionListener<List<Message>> testListener = ActionListener
            .wrap(messages -> { throw new RuntimeException("Should have failed"); }, e -> {
                assertTrue(e instanceof IllegalStateException);
                assertTrue(e.getMessage().contains("Memory container ID is not configured"));
            });

        memoryWithoutContainer.getMessages(10, testListener);

        verify(client, never()).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());
    }

    @Test
    public void testGetMessagesFailure() {
        // Mock search memories failure
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Search failed"));
            return null;
        }).when(client).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());

        ActionListener<List<Message>> testListener = ActionListener
            .wrap(messages -> { throw new RuntimeException("Should have failed"); }, e -> {
                assertTrue(e instanceof RuntimeException);
                assertEquals("Search failed", e.getMessage());
            });

        agenticMemory.getMessages(10, testListener);
    }

    @Test
    public void testGetTraces() {
        // Create mock search response with trace data
        SearchHit traceHit = createMockTraceSearchHit("trace_1", "parent_123", 1, "SearchTool");
        SearchHits searchHits = new SearchHits(new SearchHit[] { traceHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse searchResponse = new SearchResponse(
            new SearchResponseSections(searchHits, null, null, false, false, null, 0),
            null,
            1,
            1,
            0,
            10,
            new ShardSearchFailure[] {},
            SearchResponse.Clusters.EMPTY
        );

        // Mock search memories
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());

        ActionListener<List<org.opensearch.ml.common.conversation.Interaction>> testListener = ActionListener.wrap(traces -> {
            assertNotNull(traces);
            assertEquals(1, traces.size());
        }, e -> { throw new RuntimeException("Should not fail", e); });

        agenticMemory.getTraces("parent_123", testListener);

        verify(client, times(1)).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());
    }

    @Test
    public void testGetTracesWithoutMemoryContainerId() {
        AgenticConversationMemory memoryWithoutContainer = new AgenticConversationMemory(client, "test_conversation_id", null);

        ActionListener<List<org.opensearch.ml.common.conversation.Interaction>> testListener = ActionListener.wrap(traces -> {
            throw new RuntimeException("Should have failed");
        }, e -> {
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().contains("Memory container ID is not configured"));
        });

        memoryWithoutContainer.getTraces("parent_123", testListener);

        verify(client, never()).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());
    }

    @Test
    public void testGetTracesFailure() {
        // Mock search memories failure
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Search failed"));
            return null;
        }).when(client).execute(eq(MLSearchMemoriesAction.INSTANCE), any(), any());

        ActionListener<List<org.opensearch.ml.common.conversation.Interaction>> testListener = ActionListener.wrap(traces -> {
            throw new RuntimeException("Should have failed");
        }, e -> {
            assertTrue(e instanceof RuntimeException);
            assertEquals("Search failed", e.getMessage());
        });

        agenticMemory.getTraces("parent_123", testListener);
    }

    @Test
    public void testClear() {
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("clear method is not supported in AgenticConversationMemory");
        agenticMemory.clear();
    }

    @Test
    public void testDeleteInteractionAndTrace() {
        ActionListener<Boolean> testListener = ActionListener.wrap(result -> {
            assertFalse(result);  // Should return false as not fully implemented
        }, e -> { throw new RuntimeException("Should not fail", e); });

        agenticMemory.deleteInteractionAndTrace("interaction_123", testListener);
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
            .wrap(memory -> { assertEquals("test_memory_id", memory.getId()); }, e -> {
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
            .wrap(memory -> { assertEquals("new_session_123", memory.getId()); }, e -> {
                throw new RuntimeException("Should not fail", e);
            });

        factory.create(params, listener);

        // Verify session creation was called
        verify(client, times(1)).execute(eq(MLCreateSessionAction.INSTANCE), any(), any());
    }

    @Test
    public void testFactoryCreateWithEmptyMap() {
        AgenticConversationMemory.Factory factory = new AgenticConversationMemory.Factory();
        factory.init(client);

        ActionListener<AgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(new HashMap<>(), listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testFactoryCreateWithNullMap() {
        AgenticConversationMemory.Factory factory = new AgenticConversationMemory.Factory();
        factory.init(client);

        ActionListener<AgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(null, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testFactoryCreateWithoutMemoryContainerId() {
        AgenticConversationMemory.Factory factory = new AgenticConversationMemory.Factory();
        factory.init(client);

        Map<String, Object> params = new HashMap<>();
        params.put("memory_id", "test_memory_id");
        params.put("memory_name", "Test Memory");
        params.put("app_type", "conversational");
        // No memory_container_id

        ActionListener<AgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(params, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Memory container ID is required"));
    }

    @Test
    public void testFactoryCreateSessionFailure() {
        AgenticConversationMemory.Factory factory = new AgenticConversationMemory.Factory();
        factory.init(client);

        // Mock session creation failure
        doAnswer(invocation -> {
            ActionListener<MLCreateSessionResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Session creation failed"));
            return null;
        }).when(client).execute(eq(MLCreateSessionAction.INSTANCE), any(), any());

        Map<String, Object> params = new HashMap<>();
        params.put("memory_name", "New Session");
        params.put("app_type", "conversational");
        params.put("memory_container_id", "test_container_id");

        ActionListener<AgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(params, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Session creation failed", captor.getValue().getMessage());
    }

    // Helper methods to create mock search hits
    private SearchHit createMockSearchHit(String id, Map<String, Object> structuredDataBlob) {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("structured_data_blob", structuredDataBlob);
        sourceMap.put("created_time", System.currentTimeMillis());
        sourceMap.put("last_updated_time", System.currentTimeMillis());

        String sourceJson = new com.google.gson.Gson().toJson(sourceMap);
        BytesReference source = new BytesArray(sourceJson);
        SearchHit hit = new SearchHit(1, id, null, null);
        hit.sourceRef(source);
        return hit;
    }

    private SearchHit createMockTraceSearchHit(String id, String parentMessageId, int traceNumber, String origin) {
        Map<String, Object> structuredDataBlob = new HashMap<>();
        structuredDataBlob.put("input", "trace input");
        structuredDataBlob.put("response", "trace response");
        structuredDataBlob.put("parent_message_id", parentMessageId);
        structuredDataBlob.put("trace_number", traceNumber);
        structuredDataBlob.put("origin", origin);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "trace");
        metadata.put("parent_message_id", parentMessageId);
        metadata.put("trace_number", String.valueOf(traceNumber));
        metadata.put("origin", origin);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("structured_data_blob", structuredDataBlob);
        sourceMap.put("metadata", metadata);
        sourceMap.put("message_id", traceNumber);
        sourceMap.put("created_time", System.currentTimeMillis());
        sourceMap.put("last_updated_time", System.currentTimeMillis());

        String sourceJson = new com.google.gson.Gson().toJson(sourceMap);
        BytesReference source = new BytesArray(sourceJson);
        SearchHit hit = new SearchHit(1, id, null, null);
        hit.sourceRef(source);
        return hit;
    }

    private Map<String, Object> createStructuredDataBlob(String input, String response) {
        Map<String, Object> structuredDataBlob = new HashMap<>();
        structuredDataBlob.put("input", input);
        structuredDataBlob.put("response", response);
        structuredDataBlob.put("create_time", Instant.now().toString());
        structuredDataBlob.put("updated_time", Instant.now().toString());
        return structuredDataBlob;
    }
}
