/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.transport.client.Client;

public class MemoryProcessingServiceTests {

    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ActionListener<List<String>> factsListener;

    @Mock
    private ActionListener<List<MemoryDecision>> decisionsListener;

    private MemoryProcessingService memoryProcessingService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryProcessingService = new MemoryProcessingService(client, xContentRegistry);
    }

    @Test
    public void testExtractFactsFromConversation_NoStorageConfig() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());

        memoryProcessingService.extractFactsFromConversation(messages, null, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_NoLLMModel() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.getLlmModelId()).thenReturn(null);

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithLLMModel() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("My name is John").role("user").build());

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.getLlmModelId()).thenReturn("llm-model-123");

        // Mock successful LLM response
        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList());

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testMakeMemoryDecisions_NoStorageConfig() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, decisionsListener);

        verify(decisionsListener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testMakeMemoryDecisions_NoLLMModel() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.getLlmModelId()).thenReturn(null);

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testMakeMemoryDecisions_WithLLMModel() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList(new FactSearchResult("fact-1", "User name is Jane", 0.8f));

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.getLlmModelId()).thenReturn("llm-model-123");

        // Mock successful LLM response
        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList());

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }
}
