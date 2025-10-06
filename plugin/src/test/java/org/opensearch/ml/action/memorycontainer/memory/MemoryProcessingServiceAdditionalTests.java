/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.createTestContent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.transport.client.Client;

public class MemoryProcessingServiceAdditionalTests {

    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ActionListener<List<String>> factsListener;

    @Mock
    private ActionListener<List<MemoryDecision>> decisionsListener;

    private MemoryStrategy memoryStrategy;

    private MemoryProcessingService memoryProcessingService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryStrategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        memoryProcessingService = new MemoryProcessingService(client, xContentRegistry);
    }

    @Test
    public void testExtractFactsFromConversation_NonModelTensorOutput() {
        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("My name is John")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        when(mockResponse.getOutput()).thenReturn(mock(org.opensearch.ml.common.output.MLOutput.class));

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testMakeMemoryDecisions_NoResponseContent() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));

        Map<String, Object> dataMap = new HashMap<>();
        ModelTensor mockTensor = mock(ModelTensor.class);
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
        verify(decisionsListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testMakeMemoryDecisions_WithStrategyLlmIdOverride() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("default-llm-123");

        MemoryStrategy strategy = mock(MemoryStrategy.class);
        when(strategy.getStrategyConfig()).thenReturn(Map.of("llm_id", "override-llm-456"));

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList());

        doAnswer(invocation -> {
            MLPredictionTaskRequest request = invocation.getArgument(1);
            assertEquals("override-llm-456", request.getModelId());
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, strategy, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_WithStrategyLlmIdOverride() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("default-llm-123");

        MemoryStrategy strategy = mock(MemoryStrategy.class);
        when(strategy.getType()).thenReturn(MemoryStrategyType.SEMANTIC);
        when(strategy.getStrategyConfig()).thenReturn(Map.of("llm_id", "override-llm-789"));

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList());

        doAnswer(invocation -> {
            MLPredictionTaskRequest request = invocation.getArgument(1);
            assertEquals("override-llm-789", request.getModelId());
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }
}
