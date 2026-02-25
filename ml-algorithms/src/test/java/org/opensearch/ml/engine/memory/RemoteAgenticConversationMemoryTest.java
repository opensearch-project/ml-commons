/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.memory.Message;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.client.Client;

public class RemoteAgenticConversationMemoryTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private Client client;

    @Mock
    private ScriptService scriptService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private Connector connector;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup default connector mock behavior
        when(connector.getProtocol()).thenReturn("http");
        when(connector.getParameters()).thenReturn(Map.of("endpoint", "http://localhost:9200"));
        when(connector.getActions()).thenReturn(List.of());
        when(mlFeatureEnabledSetting.isConnectorPrivateIpEnabled()).thenReturn(false);
    }

    @Test
    public void testGetType() {
        assertEquals(MLMemoryType.REMOTE_AGENTIC_MEMORY.name(), RemoteAgenticConversationMemory.TYPE);
    }

    @Test
    public void testClear() {
        RemoteAgenticConversationMemory memory = createTestMemory();
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("clear method is not supported in RemoteAgenticConversationMemory");
        memory.clear();
    }

    @Test
    public void testDeleteInteractionAndTrace() {
        RemoteAgenticConversationMemory memory = createTestMemory();

        ActionListener<Boolean> testListener = ActionListener.wrap(result -> {
            assertFalse(result);  // Should return false as not fully implemented
        }, e -> { throw new RuntimeException("Should not fail", e); });

        memory.deleteInteractionAndTrace("interaction_123", testListener);
    }

    @Test
    public void testSaveWithoutMemoryContainerId() {
        RemoteAgenticConversationMemory memory = createTestMemoryWithoutContainerId();

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

        memory.save(message, null, null, "test_action", testListener);
    }

    @Test
    public void testUpdateWithoutMemoryContainerId() {
        RemoteAgenticConversationMemory memory = createTestMemoryWithoutContainerId();

        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("response", "updated response");

        ActionListener<UpdateResponse> testListener = ActionListener.wrap(response -> {
            throw new RuntimeException("Should have failed without memory container ID");
        }, e -> {
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().contains("Memory container ID is not configured"));
        });

        memory.update("msg_123", updateContent, testListener);
    }

    @Test
    public void testGetMessagesWithoutMemoryContainerId() {
        RemoteAgenticConversationMemory memory = createTestMemoryWithoutContainerId();

        ActionListener<List<Message>> testListener = ActionListener.wrap(messages -> {
            throw new RuntimeException("Should have failed without memory container ID");
        }, e -> {
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().contains("Memory container ID is not configured"));
        });

        memory.getMessages(10, testListener);
    }

    @Test
    public void testGetTracesWithoutMemoryContainerId() {
        RemoteAgenticConversationMemory memory = createTestMemoryWithoutContainerId();

        ActionListener<List<Interaction>> testListener = ActionListener.wrap(traces -> {
            throw new RuntimeException("Should have failed without memory container ID");
        }, e -> {
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().contains("Memory container ID is not configured"));
        });

        memory.getTraces("parent_123", testListener);
    }

    @Test
    public void testExtractDataFromModelTensorOutput() {
        // Test with valid ModelTensorOutput
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("session_id", "test_session_123");
        dataMap.put("working_memory_id", "mem_456");

        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse taskResponse = MLTaskResponse.builder().output(tensorOutput).build();

        Map<String, ?> result = RemoteAgenticConversationMemory.extractDataFromModelTensorOutput(taskResponse);
        assertNotNull(result);
        assertEquals("test_session_123", result.get("session_id"));
        assertEquals("mem_456", result.get("working_memory_id"));
    }

    @Test
    public void testExtractDataFromModelTensorOutputWithNullResponse() {
        Map<String, ?> result = RemoteAgenticConversationMemory.extractDataFromModelTensorOutput(null);
        assertNull(result);
    }

    @Test
    public void testExtractDataFromModelTensorOutputWithNonMLTaskResponse() {
        Map<String, ?> result = RemoteAgenticConversationMemory.extractDataFromModelTensorOutput("not a task response");
        assertNull(result);
    }

    @Test
    public void testExtractDataFromModelTensorOutputWithEmptyOutputs() {
        ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of()).build();
        MLTaskResponse taskResponse = MLTaskResponse.builder().output(tensorOutput).build();

        Map<String, ?> result = RemoteAgenticConversationMemory.extractDataFromModelTensorOutput(taskResponse);
        assertNull(result);
    }

    @Test
    public void testExtractDataFromModelTensorOutputWithEmptyTensors() {
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of()).build();
        ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse taskResponse = MLTaskResponse.builder().output(tensorOutput).build();

        Map<String, ?> result = RemoteAgenticConversationMemory.extractDataFromModelTensorOutput(taskResponse);
        assertNull(result);
    }

    @Test
    public void testExtractDataFromModelTensorOutputWithNullOutputs() {
        ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(null).build();
        MLTaskResponse taskResponse = MLTaskResponse.builder().output(tensorOutput).build();

        Map<String, ?> result = RemoteAgenticConversationMemory.extractDataFromModelTensorOutput(taskResponse);
        assertNull(result);
    }

    // Factory tests
    @Test
    public void testFactoryCreateWithNullMap() {
        RemoteAgenticConversationMemory.Factory factory = new RemoteAgenticConversationMemory.Factory();
        factory.init(scriptService, clusterService, client, xContentRegistry, mlFeatureEnabledSetting);

        ActionListener<RemoteAgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(null, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testFactoryCreateWithEmptyMap() {
        RemoteAgenticConversationMemory.Factory factory = new RemoteAgenticConversationMemory.Factory();
        factory.init(scriptService, clusterService, client, xContentRegistry, mlFeatureEnabledSetting);

        ActionListener<RemoteAgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(new HashMap<>(), listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testFactoryCreateWithoutMemoryContainerId() {
        RemoteAgenticConversationMemory.Factory factory = new RemoteAgenticConversationMemory.Factory();
        factory.init(scriptService, clusterService, client, xContentRegistry, mlFeatureEnabledSetting);

        Map<String, Object> params = new HashMap<>();
        params.put("memory_id", "test_memory_id");
        params.put("memory_name", "Test Memory");
        params.put("endpoint", "https://example.com");
        // No memory_container_id

        ActionListener<RemoteAgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(params, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Memory container ID is required"));
    }

    @Test
    public void testFactoryCreateWithoutEndpoint() {
        RemoteAgenticConversationMemory.Factory factory = new RemoteAgenticConversationMemory.Factory();
        factory.init(scriptService, clusterService, client, xContentRegistry, mlFeatureEnabledSetting);

        Map<String, Object> params = new HashMap<>();
        params.put("memory_id", "test_memory_id");
        params.put("memory_name", "Test Memory");
        params.put("memory_container_id", "test_container_id");
        // No endpoint

        ActionListener<RemoteAgenticConversationMemory> listener = mock(ActionListener.class);
        factory.create(params, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Endpoint is required"));
    }

    @Test
    public void testGetIdAndGetMemoryContainerId() {
        RemoteAgenticConversationMemory memory = createTestMemory();
        assertEquals("test_conversation_id", memory.getId());
        assertEquals("test_memory_container_id", memory.getMemoryContainerId());
    }

    @Test
    public void testGetUserId() {
        RemoteAgenticConversationMemory memory = createTestMemory();
        assertEquals("test_user", memory.getUserId());
    }

    // Helper methods
    private RemoteAgenticConversationMemory createTestMemory() {
        return new RemoteAgenticConversationMemory(
            "test_conversation_id",
            "test_memory_container_id",
            "test_user",
            createMockConnector(),
            scriptService,
            clusterService,
            client,
            xContentRegistry,
            mlFeatureEnabledSetting
        );
    }

    private RemoteAgenticConversationMemory createTestMemoryWithoutContainerId() {
        return new RemoteAgenticConversationMemory(
            "test_conversation_id",
            null,  // No memory container ID
            "test_user",
            createMockConnector(),
            scriptService,
            clusterService,
            client,
            xContentRegistry,
            mlFeatureEnabledSetting
        );
    }

    private Connector createMockConnector() {
        return HttpConnector
            .builder()
            .name("test_connector")
            .protocol("http")
            .parameters(Map.of("endpoint", "http://localhost:9200"))
            .actions(List.of())
            .build();
    }
}
