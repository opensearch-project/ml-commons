/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.createTestContent;

import java.util.ArrayList;
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
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
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
    private MemoryStrategy memoryStrategy;

    private MemoryProcessingService memoryProcessingService;

    private List<Map<String, Object>> testContent;

    @Mock
    private MemoryConfiguration memoryConfig;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryStrategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        memoryStrategy.getStrategyConfig().put("llm_result_path", "$");
        memoryProcessingService = new MemoryProcessingService(client, xContentRegistry);
        testContent = createTestContent("Hello");
        when(memoryConfig.getParameters()).thenReturn(new HashMap<>());
    }

    @Test
    public void testExtractFactsFromConversation_NoStorageConfig() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, null, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_NoLLMModel() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn(null);

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithLLMModel() {
        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("My name is John")).role("user").build());

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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testMakeMemoryDecisions_NoStorageConfig() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, null, decisionsListener);

        verify(decisionsListener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testMakeMemoryDecisions_NoLLMModel() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays.asList();

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn(null);

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_WithValidLLMCall() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_LLMFailure() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("LLM error"));
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(Exception.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyMessages() {
        List<MessageInput> messages = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testMakeMemoryDecisions_EmptyFacts() {
        List<String> facts = Arrays.asList();
        List<FactSearchResult> searchResults = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testMakeMemoryDecisions_WithSearchResults() {
        List<String> facts = Arrays.asList("User name is John");
        List<FactSearchResult> searchResults = Arrays
            .asList(new FactSearchResult("fact-1", "User name is Jane", 0.8f), new FactSearchResult("fact-2", "User age is 25", 0.7f));
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_ParseException() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        MemoryStrategy memoryStrategy = new MemoryStrategy(
            "id",
            true,
            MemoryStrategyType.SEMANTIC,
            Arrays.asList("user_id"),
            new HashMap<>()
        );
        memoryStrategy.getStrategyConfig().put("llm_result_path", "$.content[0].text");

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testExtractFactsFromConversation_NonModelTensorOutput() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyModelOutputs() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyModelTensors() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_NoContentKey() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyContentList() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_NoTextKey() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

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

        memoryProcessingService.makeMemoryDecisions(facts, searchResults, null, storageConfig, decisionsListener);

        verify(decisionsListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithOtherFields() {
        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("My name is John")).role("user").build());
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

        memoryProcessingService.extractFactsFromConversation(messages, memoryStrategy, storageConfig, factsListener);

        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_UserPreferenceStrategy() {
        // Test with user_preference strategy type
        MemoryStrategy userPreferenceStrategy = new MemoryStrategy(
            "id",
            true,
            MemoryStrategyType.USER_PREFERENCE,
            Arrays.asList("user_id"),
            new HashMap<>()
        );

        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("I prefer dark mode for the UI")).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, userPreferenceStrategy, storageConfig, factsListener);

        // Verify that the LLM is called (which means the strategy was accepted)
        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testRunMemoryStrategy_UserPreferenceStrategy() {
        // Test that user_preference strategy is properly routed
        MemoryStrategy userPreferenceStrategy = new MemoryStrategy(
            "id",
            true,
            MemoryStrategyType.USER_PREFERENCE,
            Arrays.asList("user_id"),
            new HashMap<>()
        );

        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("I like weekly summary emails")).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.runMemoryStrategy(userPreferenceStrategy, messages, storageConfig, factsListener);

        // Verify that the LLM is called (strategy was accepted and processed)
        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testRunMemoryStrategy_SemanticStrategy() {
        // Test that semantic strategy still works
        MemoryStrategy semanticStrategy = new MemoryStrategy(
            "id",
            true,
            MemoryStrategyType.SEMANTIC,
            Arrays.asList("user_id"),
            new HashMap<>()
        );

        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("My name is John")).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.runMemoryStrategy(semanticStrategy, messages, storageConfig, factsListener);

        // Verify that the LLM is called (strategy was accepted and processed)
        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testRunMemoryStrategy_SummaryStrategy() {
        // Test that summary strategy is properly routed
        MemoryStrategy summaryStrategy = new MemoryStrategy(
            "id",
            true,
            MemoryStrategyType.SUMMARY,
            Arrays.asList("user_id"),
            new HashMap<>()
        );

        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("This is a document to summarize")).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.runMemoryStrategy(summaryStrategy, messages, storageConfig, factsListener);

        // Verify that the LLM is called (strategy was accepted and processed)
        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_SummaryStrategy() {
        // Test with summary strategy type
        MemoryStrategy summaryStrategy = new MemoryStrategy(
            "id",
            true,
            MemoryStrategyType.SUMMARY,
            Arrays.asList("user_id"),
            new HashMap<>()
        );

        List<MessageInput> messages = Arrays
            .asList(MessageInput.builder().content(createTestContent("Document content to summarize")).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, summaryStrategy, storageConfig, factsListener);

        // Verify that the LLM is called (which means the strategy was accepted)
        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testRunMemoryStrategy_InvalidStrategy() {
        // Test that invalid strategy type is rejected
        MemoryStrategy invalidStrategy = new MemoryStrategy("id", true, null, Arrays.asList("user_id"), new HashMap<>());

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.runMemoryStrategy(invalidStrategy, messages, storageConfig, factsListener);

        // Verify that the listener receives a failure for unsupported strategy type
        verify(factsListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testExtractFactsFromConversation_InvalidCustomPrompt() {
        // Test with custom prompt that doesn't specify JSON format
        Map<String, Object> strategyConfig = new HashMap<>();
        strategyConfig.put("system_prompt", "Extract information from this conversation");
        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        // Verify that the listener receives a failure for invalid prompt format
        verify(factsListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testExtractFactsFromConversation_EmptyCustomPrompt() {
        // Test with empty custom prompt (should fallback to default)
        Map<String, Object> strategyConfig = new HashMap<>();
        strategyConfig.put("prompt", "");
        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        // Should execute with default prompt
        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_WithSystemPromptMessage() {
        // Test with system_prompt_message in strategy config
        Map<String, Object> strategyConfig = new HashMap<>();
        Map<String, Object> systemPromptMsg = new HashMap<>();
        systemPromptMsg.put("role", "system");
        systemPromptMsg.put("content", Arrays.asList(Map.of("text", "You are a helpful assistant", "type", "text")));
        strategyConfig.put("system_prompt_message", systemPromptMsg);
        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_WithUserPromptMessage() {
        // Test with user_prompt_message in strategy config
        Map<String, Object> strategyConfig = new HashMap<>();
        Map<String, Object> userPromptMsg = new HashMap<>();
        userPromptMsg.put("role", "user");
        userPromptMsg.put("content", Arrays.asList(Map.of("text", "Extract all facts from above", "type", "text")));
        strategyConfig.put("user_prompt_message", userPromptMsg);
        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_WithBothPromptMessages() {
        // Test with both system_prompt_message and user_prompt_message
        Map<String, Object> strategyConfig = new HashMap<>();
        Map<String, Object> systemPromptMsg = new HashMap<>();
        systemPromptMsg.put("role", "system");
        systemPromptMsg.put("content", Arrays.asList(Map.of("text", "You are a helpful assistant", "type", "text")));
        strategyConfig.put("system_prompt_message", systemPromptMsg);

        Map<String, Object> userPromptMsg = new HashMap<>();
        userPromptMsg.put("role", "user");
        userPromptMsg.put("content", Arrays.asList(Map.of("text", "Extract all facts", "type", "text")));
        strategyConfig.put("user_prompt_message", userPromptMsg);

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testSummarizeMessages_NullMessages() {
        ActionListener<String> stringListener = mock(ActionListener.class);
        memoryProcessingService.summarizeMessages(memoryConfig, null, stringListener);
        verify(stringListener).onResponse("");
    }

    @Test
    public void testSummarizeMessages_EmptyMessages() {
        ActionListener<String> stringListener = mock(ActionListener.class);
        memoryProcessingService.summarizeMessages(memoryConfig, Arrays.asList(), stringListener);
        verify(stringListener).onResponse("");
    }

    @Test
    public void testSummarizeMessages_WithMessages() {
        ActionListener<String> stringListener = mock(ActionListener.class);
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            List<ModelTensors> mlModelOutputs = new ArrayList<>();
            List<ModelTensor> tensors = new ArrayList<>();
            Map<String, Object> contents = new HashMap<>();
            contents.put("content", List.of(Map.of("text", "test summary")));
            tensors.add(ModelTensor.builder().name("response").dataAsMap(contents).build());
            mlModelOutputs.add(ModelTensors.builder().mlModelTensors(tensors).build());
            MLTaskResponse output = MLTaskResponse
                .builder()
                .output(ModelTensorOutput.builder().mlModelOutputs(mlModelOutputs).build())
                .build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());
        memoryProcessingService.summarizeMessages(memoryConfig, messages, stringListener);
        verify(stringListener).onResponse(any(String.class));
    }

    @Test
    public void testExtractFactsFromConversation_ValidCustomPrompt() {
        // Test with valid custom prompt that has JSON and "facts" keywords (with quotes as required by validation)
        Map<String, Object> strategyConfig = new HashMap<>();
        strategyConfig.put("prompt", "Extract information and return JSON response format with \"facts\" array containing key points");
        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }

    @Test
    public void testExtractFactsFromConversation_JsonEnforcementMessageAppended() {
        // Test that JSON enforcement message is always appended to fact extraction requests
        Map<String, Object> strategyConfig = new HashMap<>();
        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);

        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(testContent).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        // Capture the request to verify JSON enforcement message is included
        doAnswer(invocation -> {
            MLPredictionTaskRequest request = invocation.getArgument(1);
            RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) request.getMlInput().getInputDataset();
            Map<String, String> parameters = dataset.getParameters();
            String messagesJson = parameters.get("messages");

            // Verify that the JSON enforcement message is included in the messages
            assertTrue(
                "JSON enforcement message should be included",
                messagesJson.contains("Respond NOW with ONE LINE of valid JSON ONLY")
            );

            // Mock successful response
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            List<ModelTensors> mlModelOutputs = new ArrayList<>();
            List<ModelTensor> tensors = new ArrayList<>();
            Map<String, Object> contents = new HashMap<>();
            contents.put("content", List.of(Map.of("text", "{\"facts\":[\"Test fact\"]}")));
            tensors.add(ModelTensor.builder().name("response").dataAsMap(contents).build());
            mlModelOutputs.add(ModelTensors.builder().mlModelTensors(tensors).build());
            MLTaskResponse output = MLTaskResponse
                .builder()
                .output(ModelTensorOutput.builder().mlModelOutputs(mlModelOutputs).build())
                .build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
    }
}
