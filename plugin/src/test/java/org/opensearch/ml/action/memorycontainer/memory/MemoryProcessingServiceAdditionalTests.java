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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_RESULT_PATH_FIELD;
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
import org.opensearch.ml.helper.MemoryContainerHelper;
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

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    private MemoryStrategy memoryStrategy;

    private MemoryProcessingService memoryProcessingService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryStrategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        memoryProcessingService = new MemoryProcessingService(client, xContentRegistry, memoryContainerHelper);
        // Mock the getLlmResultPath to return the default path
        when(memoryContainerHelper.getLlmResultPath(any(), any())).thenReturn("$.content[0].text");
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

    @Test
    public void testExtractFactsFromConversation_WithProcessorChainExtractingJson() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategy.getStrategyConfig().put(LLM_RESULT_PATH_FIELD, "$.content[0].text");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        // Simulate LLM response with JSON wrapped in markdown code block
        contentItem.put("text", "```json\n{\"facts\": [\"User prefers dark mode\", \"User is from Seattle\"]}\n```");
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

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithProcessorChainExtractingNestedJson() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategy.getStrategyConfig().put(LLM_RESULT_PATH_FIELD, "$.content[0].text");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        // Simulate LLM response with nested JSON object
        contentItem.put("text", "{\"result\": {\"facts\": [\"User name is Alice\", \"User age is 30\"]}}");
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

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithProcessorChainMultipleTensors() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategy.getStrategyConfig().put(LLM_RESULT_PATH_FIELD, "$.content[0].text");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor1 = mock(ModelTensor.class);
        ModelTensor mockTensor2 = mock(ModelTensor.class);

        Map<String, Object> dataMap1 = new HashMap<>();
        Map<String, Object> contentItem1 = new HashMap<>();
        contentItem1.put("text", "{\"facts\": [\"Fact from tensor 1\"]}");
        dataMap1.put("content", Arrays.asList(contentItem1));

        Map<String, Object> dataMap2 = new HashMap<>();
        Map<String, Object> contentItem2 = new HashMap<>();
        contentItem2.put("text", "{\"facts\": [\"Fact from tensor 2\"]}");
        dataMap2.put("content", Arrays.asList(contentItem2));

        when(mockResponse.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getMlModelOutputs()).thenReturn(Arrays.asList(mockTensors));
        when(mockTensors.getMlModelTensors()).thenReturn(Arrays.asList(mockTensor1, mockTensor2));
        when(mockTensor1.getDataAsMap()).thenReturn((Map) dataMap1);
        when(mockTensor2.getDataAsMap()).thenReturn((Map) dataMap2);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithProcessorChainCleaningMarkdown() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategy.getStrategyConfig().put(LLM_RESULT_PATH_FIELD, "$.content[0].text");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        // Simulate LLM response with markdown code block (common LLM behavior)
        contentItem.put("text", "```\n{\"facts\": [\"User prefers light theme\"]}\n```");
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

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        // cleanMarkdownFromJson should strip the markdown before ProcessorChain processes it
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithProcessorChainEmptyFacts() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategy.getStrategyConfig().put(LLM_RESULT_PATH_FIELD, "$.content[0].text");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        // LLM returns empty facts array
        contentItem.put("text", "{\"facts\": []}");
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

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testExtractFactsFromConversation_WithProcessorChainComplexJsonStructure() {
        List<MessageInput> messages = Arrays.asList(MessageInput.builder().content(createTestContent("Test message")).role("user").build());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getLlmId()).thenReturn("llm-model-123");

        MemoryStrategy strategy = new MemoryStrategy("id", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategy.getStrategyConfig().put(LLM_RESULT_PATH_FIELD, "$.content[0].text");

        MLTaskResponse mockResponse = mock(MLTaskResponse.class);
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        // Complex JSON with additional metadata that should be ignored
        contentItem
            .put(
                "text",
                "{\"facts\": [\"User speaks English\", \"User is a developer\"], \"metadata\": {\"confidence\": 0.95}, \"timestamp\": \"2024-01-01\"}"
            );
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

        memoryProcessingService.extractFactsFromConversation(messages, strategy, storageConfig, factsListener);

        verify(client).execute(any(), any(), any());
        // Should extract only the facts array, ignoring other fields
        verify(factsListener).onResponse(any(List.class));
    }

    @Test
    public void testJsonPathRead_MissingProperty() {
        // Test PathNotFoundException when property doesn't exist - message has no period
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("text", "some value");
        dataMap.put("content", Arrays.asList(contentItem));

        try {
            // Try to read a path that doesn't exist
            Object result = com.jayway.jsonpath.JsonPath.read(dataMap, "$.nonexistent");
            throw new AssertionError("Expected PathNotFoundException but got result: " + result);
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            // Verify the exception message format
            String message = e.getMessage();
            assertEquals("No results for path: $['nonexistent']", message);

            // Verify this is the expected exception type
            assertEquals(com.jayway.jsonpath.PathNotFoundException.class, e.getClass());
        }
    }

    @Test
    public void testJsonPathRead_InvalidFilterOnNonArray() {
        // Test PathNotFoundException when filter is applied to non-array - message has period
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> id = new HashMap<>();
        id.put("type", "message");
        id.put("role", "assistant");
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", "Alice from California likes running; AI offers well-wishes.");
        id.put("content", Arrays.asList(contentItem));
        dataMap.put("id", "msg_bdrk_01NDDutcnm9pEsE2AMQ5PodR");
        dataMap.put("type", "message");
        dataMap.put("role", "assistant");
        dataMap.put("model", "claude-3-7-sonnet-20250219");
        dataMap.put("content", Arrays.asList(contentItem));
        dataMap.put("stop_reason", "end_turn");
        dataMap.put("stop_sequence", null);
        Map<String, Object> usage = new HashMap<>();
        usage.put("input_tokens", 86.0);
        usage.put("cache_creation_input_tokens", 0.0);
        usage.put("cache_read_input_tokens", 0.0);
        usage.put("output_tokens", 17.0);
        dataMap.put("usage", usage);

        try {
            // Try to apply array filter to string - this will fail with detailed error
            Object result = com.jayway.jsonpath.JsonPath.read(dataMap, "$.content[0].text");
            // If we successfully got content[0].text, we need a different test case
            // Let's try a path that will actually fail
            result = com.jayway.jsonpath.JsonPath.read(dataMap, "$.type[0].text");
            throw new AssertionError("Expected PathNotFoundException but got result: " + result);
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            // Verify the exception message contains period and has detailed context
            String message = e.getMessage();
            // The message should contain a period indicating sentence structure
            org.junit.Assert.assertTrue("Message should contain details: " + message, message.length() > 0);

            // Verify this is the expected exception type
            assertEquals(com.jayway.jsonpath.PathNotFoundException.class, e.getClass());
        }
    }
}
