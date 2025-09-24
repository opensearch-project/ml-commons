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
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
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

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn(null);

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithLLMModel() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("My name is John").role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

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

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn(null);

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testMakeMemoryDecisions_WithLLMModel() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList(new FactSearchResult("fact-1", "User name is Jane", 0.8f));

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

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

    @Test
    public void testExtractFactsFromConversation_WithValidLLMCall() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_LLMFailure() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("LLM error"));
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onFailure(any(Exception.class));
    }

    @Test
    public void testMakeMemoryDecisions_LLMFailure() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("LLM error"));
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(Exception.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyMessages() {
        List<MessageInput> messages = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_MessageWithNullRole() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role(null).build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testMakeMemoryDecisions_EmptyFacts() {
        List<String> facts = Arrays.asList();
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testMakeMemoryDecisions_WithSearchResults() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays
            .asList(new FactSearchResult("fact-1", "User name is Jane", 0.8f), new FactSearchResult("fact-2", "User age is 25", 0.7f));
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_ParseException() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("text", "invalid json");
        dataMap.put("content", Arrays.asList(contentItem));

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testExtractFactsFromConversation_NonModelTensorOutput() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        MLOutput mockOutput = mock(MLOutput.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyModelOutputs() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

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

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyModelTensors() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

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

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_NoContentKey() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("other", "value");

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyContentList() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("content", Arrays.asList());

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_NoTextKey() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("Hello").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("other", "value");
        dataMap.put("content", Arrays.asList(contentItem));

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testMakeMemoryDecisions_NonModelTensorOutput() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        MLOutput mockOutput = mock(MLOutput.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testMakeMemoryDecisions_EmptyTensors() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList());

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testMakeMemoryDecisions_ContentFormat() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("text", "{\"memory_decisions\": []}");
        dataMap.put("content", Arrays.asList(contentItem));

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onResponse(any(List.class));
    }

    @Test
    public void testMakeMemoryDecisions_JsonCodeBlock() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("response", "```json\n{\"memory_decisions\": []}\n```");

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onResponse(any(List.class));
    }

    @Test
    public void testMakeMemoryDecisions_PlainCodeBlock() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("response", "```\n{\"memory_decisions\": []}\n```");

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onResponse(any(List.class));
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
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("other", "value");

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithOtherFields() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content("My name is John").role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("text", "{\"facts\": [\"User name is John\"], \"other_field\": \"value\", \"metadata\": {\"key\": \"value\"}}");
        dataMap.put("content", Arrays.asList(contentItem));

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor));
        when(mockTensor.getDataAsMap()).thenReturn((Map) dataMap);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }
}
