/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for SummarizationManager.
 */
public class SummarizationManagerTest {

    @Mock
    private Client client;

    private SummarizationManager manager;
    private ContextManagerContext context;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new SummarizationManager(client);
        context = ContextManagerContext.builder().toolInteractions(new ArrayList<>()).parameters(new HashMap<>()).build();
    }

    @Test
    public void testGetType() {
        Assert.assertEquals("SummarizationManager", manager.getType());
    }

    @Test
    public void testInitializeWithDefaults() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        Assert.assertEquals(0.3, manager.summaryRatio, 0.001);
        Assert.assertEquals(10, manager.preserveRecentMessages);
    }

    @Test
    public void testInitializeWithCustomConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", 0.5);
        config.put("preserve_recent_messages", 5);
        config.put("summarization_model_id", "test-model");
        config.put("summarization_system_prompt", "Custom prompt");

        manager.initialize(config);

        Assert.assertEquals(0.5, manager.summaryRatio, 0.001);
        Assert.assertEquals(5, manager.preserveRecentMessages);
        Assert.assertEquals("test-model", manager.summarizationModelId);
        Assert.assertEquals("Custom prompt", manager.summarizationSystemPrompt);
    }

    @Test
    public void testInitializeWithInvalidSummaryRatio() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", 0.9); // Invalid - too high

        manager.initialize(config);

        // Should use default value
        Assert.assertEquals(0.3, manager.summaryRatio, 0.001);
    }

    @Test
    public void testShouldActivateWithNoRules() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        Assert.assertTrue(manager.shouldActivate(context));
    }

    @Test
    public void testExecuteWithEmptyToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        manager.execute(context);

        Assert.assertTrue(context.getToolInteractions().isEmpty());
    }

    @Test
    public void testExecuteWithInsufficientMessages() {
        Map<String, Object> config = new HashMap<>();
        config.put("preserve_recent_messages", 10);
        manager.initialize(config);

        // Add only 5 interactions - not enough to summarize
        addToolInteractionsToContext(5);

        manager.execute(context);

        // Should remain unchanged
        Assert.assertEquals(5, context.getToolInteractions().size());
    }

    @Test
    public void testExecuteWithNoModelId() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        addToolInteractionsToContext(20);

        manager.execute(context);

        // Should remain unchanged due to missing model ID
        Assert.assertEquals(20, context.getToolInteractions().size());
    }

    @Test
    public void testExecuteWithNonStringOutputs() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Add tool interactions as strings
        context.getToolInteractions().add("123"); // Integer as string
        context.getToolInteractions().add("String output"); // String output

        manager.execute(context);

        // Should handle gracefully - only 2 string interactions, not enough to summarize
        Assert.assertEquals(2, context.getToolInteractions().size());
    }

    @Test
    public void testProcessSummarizationResult() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        addToolInteractionsToContext(10);
        List<String> remainingMessages = List.of("Message 6", "Message 7", "Message 8", "Message 9", "Message 10");

        manager.processSummarizationResult(context, "Test summary", 5, remainingMessages, context.getToolInteractions());

        // Should have 1 summary + 5 remaining = 6 total
        Assert.assertEquals(6, context.getToolInteractions().size());

        // First should be summary
        String firstOutput = context.getToolInteractions().get(0);
        Assert.assertTrue(firstOutput.contains("Test summary"));
    }

    @Test
    public void testExtractSummaryFromResponseWithLLMResponseFilter() throws Exception {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Set up context with LLM_RESPONSE_FILTER
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$.choices[0].message.content");
        context.setParameters(parameters);

        // Create mock response with OpenAI-style structure
        Map<String, Object> responseData = new HashMap<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "This is the extracted summary content");
        choice.put("message", message);
        responseData.put("choices", List.of(choice));

        MLTaskResponse mockResponse = createMockMLTaskResponse(responseData);

        // Use reflection to access the private method
        java.lang.reflect.Method extractMethod = SummarizationManager.class
            .getDeclaredMethod("extractSummaryFromResponse", MLTaskResponse.class, ContextManagerContext.class);
        extractMethod.setAccessible(true);

        String result = (String) extractMethod.invoke(manager, mockResponse, context);

        Assert.assertEquals("This is the extracted summary content", result);
    }

    @Test
    public void testExtractSummaryFromResponseWithBedrockResponseFilter() throws Exception {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Set up context with Bedrock-style LLM_RESPONSE_FILTER
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");
        context.setParameters(parameters);

        // Create mock response with Bedrock-style structure
        Map<String, Object> responseData = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        content.put("text", "Bedrock extracted summary");
        message.put("content", List.of(content));
        output.put("message", message);
        responseData.put("output", output);

        MLTaskResponse mockResponse = createMockMLTaskResponse(responseData);

        // Use reflection to access the private method
        java.lang.reflect.Method extractMethod = SummarizationManager.class
            .getDeclaredMethod("extractSummaryFromResponse", MLTaskResponse.class, ContextManagerContext.class);
        extractMethod.setAccessible(true);

        String result = (String) extractMethod.invoke(manager, mockResponse, context);

        Assert.assertEquals("Bedrock extracted summary", result);
    }

    @Test
    public void testExtractSummaryFromResponseWithInvalidFilter() throws Exception {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Set up context with invalid LLM_RESPONSE_FILTER path
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$.invalid.path");
        context.setParameters(parameters);

        // Create mock response with simple structure
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("response", "Fallback summary content");

        MLTaskResponse mockResponse = createMockMLTaskResponse(responseData);

        // Use reflection to access the private method
        java.lang.reflect.Method extractMethod = SummarizationManager.class
            .getDeclaredMethod("extractSummaryFromResponse", MLTaskResponse.class, ContextManagerContext.class);
        extractMethod.setAccessible(true);

        String result = (String) extractMethod.invoke(manager, mockResponse, context);

        // Should fall back to default parsing
        Assert.assertEquals("Fallback summary content", result);
    }

    @Test
    public void testExtractSummaryFromResponseWithoutFilter() throws Exception {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Context without LLM_RESPONSE_FILTER
        Map<String, String> parameters = new HashMap<>();
        context.setParameters(parameters);

        // Create mock response with simple structure
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("response", "Default parsed summary");

        MLTaskResponse mockResponse = createMockMLTaskResponse(responseData);

        // Use reflection to access the private method
        java.lang.reflect.Method extractMethod = SummarizationManager.class
            .getDeclaredMethod("extractSummaryFromResponse", MLTaskResponse.class, ContextManagerContext.class);
        extractMethod.setAccessible(true);

        String result = (String) extractMethod.invoke(manager, mockResponse, context);

        Assert.assertEquals("Default parsed summary", result);
    }

    @Test
    public void testExtractSummaryFromResponseWithEmptyFilter() throws Exception {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Set up context with empty LLM_RESPONSE_FILTER
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "");
        context.setParameters(parameters);

        // Create mock response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("response", "Empty filter fallback");

        MLTaskResponse mockResponse = createMockMLTaskResponse(responseData);

        // Use reflection to access the private method
        java.lang.reflect.Method extractMethod = SummarizationManager.class
            .getDeclaredMethod("extractSummaryFromResponse", MLTaskResponse.class, ContextManagerContext.class);
        extractMethod.setAccessible(true);

        String result = (String) extractMethod.invoke(manager, mockResponse, context);

        Assert.assertEquals("Empty filter fallback", result);
    }

    @Test
    public void testExecuteWithSummarizationFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("summarization_model_id", "test-model");
        manager.initialize(config);

        // Add enough interactions to trigger summarization
        addToolInteractionsToContext(20);
        int originalSize = context.getToolInteractions().size();

        // Execute - this will fail because we don't have a real client
        // but it should gracefully skip summarization
        manager.execute(context);

        // Should keep original interactions unchanged when summarization fails
        Assert.assertEquals(originalSize, context.getToolInteractions().size());

        // Verify original interactions are preserved
        for (int i = 0; i < originalSize; i++) {
            Assert.assertEquals("Tool output " + (i + 1), context.getToolInteractions().get(i));
        }
    }

    @Test
    public void testExecuteWithEmptyStructuredChatHistory() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(new ArrayList<>())
            .build();

        manager.execute(context);

        Assert.assertTrue(context.getStructuredChatHistory().isEmpty());
    }

    @Test
    public void testExecuteWithNullStructuredChatHistory() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(null)
            .build();

        manager.execute(context);

        Assert.assertNull(context.getStructuredChatHistory());
    }

    @Test
    public void testExecuteWithInsufficientStructuredMessages() {
        Map<String, Object> config = new HashMap<>();
        config.put("preserve_recent_messages", 10);
        manager.initialize(config);

        // Add only 5 structured messages - will try to summarize with effective preserve
        // but fail because no model ID is available
        List<Message> messages = createStructuredMessages(5);
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(messages)
            .build();

        manager.execute(context);

        // Should remain unchanged (no model ID available)
        Assert.assertEquals(5, context.getStructuredChatHistory().size());
    }

    @Test
    public void testExecuteWithSingleStructuredMessage() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Add only 1 structured message - need at least 2 to summarize
        List<Message> messages = createStructuredMessages(1);
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(messages)
            .build();

        manager.execute(context);

        // Should remain unchanged - need at least 2 messages
        Assert.assertEquals(1, context.getStructuredChatHistory().size());
    }

    @Test
    public void testStructuredMessagesEffectivePreserveWhenCountEqualsPreserve() {
        // This tests the scenario where totalMessages == preserveRecentMessages.
        // Old logic: min(count, 10-10) = 0 -> returns early (no summarization).
        // New logic: effectivePreserve = min(10, 9) = 9, messagesToSummarize = min(3, 10-9) = 1
        // -> at least 1 message gets summarized (if model ID is available).
        Map<String, Object> config = new HashMap<>();
        config.put("preserve_recent_messages", 10);
        manager.initialize(config);

        List<Message> messages = createStructuredMessages(10);
        Map<String, String> params = new HashMap<>();
        // No model ID -> summarization fails gracefully
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(params)
            .structuredChatHistory(messages)
            .build();

        manager.execute(context);

        // Remains unchanged because no model ID is available,
        // but the method proceeds past the message count check (not an early return)
        Assert.assertEquals(10, context.getStructuredChatHistory().size());
    }

    @Test
    public void testExecuteWithNoModelIdStructuredMessages() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Add enough structured messages to trigger summarization
        List<Message> messages = createStructuredMessages(20);
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(messages)
            .build();

        manager.execute(context);

        // Should remain unchanged due to missing model ID
        Assert.assertEquals(20, context.getStructuredChatHistory().size());
    }

    @Test
    public void testProcessStructuredSummarizationResult() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        List<Message> remainingMessages = createStructuredMessages(5);
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(new ArrayList<>())
            .build();

        manager.processStructuredSummarizationResult(context, "Test structured summary", remainingMessages);

        // Should have 1 summary + 5 remaining = 6 total
        Assert.assertEquals(6, context.getStructuredChatHistory().size());

        // First should be summary message with assistant role
        Message summaryMsg = context.getStructuredChatHistory().get(0);
        Assert.assertEquals("assistant", summaryMsg.getRole());
        Assert.assertNotNull(summaryMsg.getContent());
        Assert.assertEquals(1, summaryMsg.getContent().size());
        Assert.assertTrue(summaryMsg.getContent().get(0).getText().contains("Test structured summary"));
    }

    @Test
    public void testShouldActivateWithRulesNotSatisfied() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> activation = new HashMap<>();
        activation.put("tokens_exceed", 99999); // Very high threshold - won't be satisfied
        config.put("activation", activation);
        manager.initialize(config);

        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .userPrompt("short prompt")
            .build();

        Assert.assertFalse(manager.shouldActivate(context));
    }

    @Test
    public void testShouldActivateWithRulesSatisfied() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> activation = new HashMap<>();
        activation.put("tokens_exceed", 1); // Very low threshold - always satisfied
        config.put("activation", activation);
        manager.initialize(config);

        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .userPrompt("This is a prompt with enough tokens")
            .build();

        Assert.assertTrue(manager.shouldActivate(context));
    }

    @Test
    public void testExecuteStructuredMessagesWithModelIdParam() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Add enough structured messages to pass early returns
        List<Message> messages = createStructuredMessages(5);
        Map<String, String> params = new HashMap<>();
        params.put("_llm_model_id", "test-model-from-params");
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(params)
            .structuredChatHistory(messages)
            .build();

        // Execute - will proceed past model ID check but fail on client call
        manager.execute(context);

        // Should remain unchanged since client mock isn't set up for LLM call
        Assert.assertEquals(5, context.getStructuredChatHistory().size());
    }

    @Test
    public void testExecuteStructuredMessagesWithNoTextContent() {
        Map<String, Object> config = new HashMap<>();
        config.put("summarization_model_id", "test-model");
        manager.initialize(config);

        // Create messages with null content
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", null));
        messages.add(new Message("assistant", null));
        messages.add(new Message("user", null));

        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .structuredChatHistory(messages)
            .build();

        manager.execute(context);

        // Should remain unchanged since no text content to summarize
        Assert.assertEquals(3, context.getStructuredChatHistory().size());
    }

    @Test
    public void testInitializeWithStringConfigs() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", "0.4");
        config.put("preserve_recent_messages", "8");
        manager.initialize(config);

        Assert.assertEquals(0.4, manager.summaryRatio, 0.001);
        Assert.assertEquals(8, manager.preserveRecentMessages);
    }

    @Test
    public void testInitializeWithInvalidStringConfigs() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", "not-a-number");
        config.put("preserve_recent_messages", "not-a-number");
        manager.initialize(config);

        // Should fall back to defaults
        Assert.assertEquals(0.3, manager.summaryRatio, 0.001);
        Assert.assertEquals(10, manager.preserveRecentMessages);
    }

    @Test
    public void testInitializeWithLowSummaryRatio() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", 0.05); // Below 0.1 minimum
        manager.initialize(config);

        Assert.assertEquals(0.3, manager.summaryRatio, 0.001);
    }

    @Test
    public void testResolveModelIdFromConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("summarization_model_id", "config-model-id");
        manager.initialize(config);

        addToolInteractionsToContext(20);

        // Execute - config model ID should be used (will fail on client call but that's OK)
        manager.execute(context);

        // Interactions unchanged because client call fails, but model ID resolution worked
        Assert.assertEquals(20, context.getToolInteractions().size());
    }

    @Test
    public void testExecuteSummarizationPropagatesTenantId() {
        Map<String, Object> config = new HashMap<>();
        config.put("summarization_model_id", "test-model");
        manager.initialize(config);

        // Set up client mock to capture the prediction request
        ThreadPool threadPool = mock(ThreadPool.class);
        ExecutorService executorService = mock(ExecutorService.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.generic()).thenReturn(executorService);

        // Make the executor run the submitted task immediately so client.execute() is called
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // Set up tenant_id in context parameters
        String expectedTenantId = "test-tenant-123";
        Map<String, String> params = new HashMap<>();
        params.put(TENANT_ID_FIELD, expectedTenantId);
        params.put("_llm_model_id", "test-model");

        context = ContextManagerContext.builder().toolInteractions(new ArrayList<>()).parameters(params).build();

        // Add enough interactions to trigger summarization
        addToolInteractionsToContext(20);

        manager.execute(context);

        // Capture the request passed to client.execute()
        ArgumentCaptor<MLPredictionTaskRequest> requestCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(ActionType.class), requestCaptor.capture(), any(ActionListener.class));

        MLPredictionTaskRequest capturedRequest = requestCaptor.getValue();
        Assert.assertEquals(expectedTenantId, capturedRequest.getTenantId());
    }

    @Test
    public void testExecuteSummarizationPropagatesNullTenantIdWhenAbsent() {
        Map<String, Object> config = new HashMap<>();
        config.put("summarization_model_id", "test-model");
        manager.initialize(config);

        // Set up client mock
        ThreadPool threadPool = mock(ThreadPool.class);
        ExecutorService executorService = mock(ExecutorService.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.generic()).thenReturn(executorService);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // No tenant_id in context parameters
        Map<String, String> params = new HashMap<>();
        params.put("_llm_model_id", "test-model");
        context = ContextManagerContext.builder().toolInteractions(new ArrayList<>()).parameters(params).build();

        addToolInteractionsToContext(20);

        manager.execute(context);

        ArgumentCaptor<MLPredictionTaskRequest> requestCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(ActionType.class), requestCaptor.capture(), any(ActionListener.class));

        MLPredictionTaskRequest capturedRequest = requestCaptor.getValue();
        Assert.assertNull(capturedRequest.getTenantId());
    }

    @Test
    public void testExecuteStructuredSummarizationPropagatesTenantId() {
        Map<String, Object> config = new HashMap<>();
        config.put("summarization_model_id", "test-model");
        manager.initialize(config);

        // Set up client mock
        ThreadPool threadPool = mock(ThreadPool.class);
        ExecutorService executorService = mock(ExecutorService.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.generic()).thenReturn(executorService);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        // Set up tenant_id in context parameters
        String expectedTenantId = "structured-tenant-456";
        Map<String, String> params = new HashMap<>();
        params.put(TENANT_ID_FIELD, expectedTenantId);
        params.put("_llm_model_id", "test-model");

        List<Message> messages = createStructuredMessages(20);
        context = ContextManagerContext
            .builder()
            .toolInteractions(new ArrayList<>())
            .parameters(params)
            .structuredChatHistory(messages)
            .build();

        manager.execute(context);

        // Capture the request passed to client.execute()
        ArgumentCaptor<MLPredictionTaskRequest> requestCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(ActionType.class), requestCaptor.capture(), any(ActionListener.class));

        MLPredictionTaskRequest capturedRequest = requestCaptor.getValue();
        Assert.assertEquals(expectedTenantId, capturedRequest.getTenantId());
    }

    /**
     * Helper method to create structured messages for testing.
     */
    private List<Message> createStructuredMessages(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ContentBlock block = new ContentBlock();
            block.setType(ContentType.TEXT);
            block.setText("Message content " + i);
            String role = (i % 2 == 1) ? "user" : "assistant";
            messages.add(new Message(role, List.of(block)));
        }
        return messages;
    }

    /**
     * Helper method to create a mock MLTaskResponse with the given data.
     */
    private MLTaskResponse createMockMLTaskResponse(Map<String, Object> responseData) {
        ModelTensor tensor = ModelTensor.builder().dataAsMap(responseData).build();

        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();

        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        return MLTaskResponse.builder().output(output).build();
    }

    /**
     * Helper method to add tool interactions to the context.
     */
    private void addToolInteractionsToContext(int count) {
        for (int i = 1; i <= count; i++) {
            context.getToolInteractions().add("Tool output " + i);
        }
    }
}
