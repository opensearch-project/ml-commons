/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.AuthenticationException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;
import software.amazon.awssdk.services.s3.model.InvalidRequestException;

public class BedrockInvokeModelStreamingHandlerTest {

    private BedrockInvokeModelStreamingHandler handler;
    private SdkAsyncHttpClient mockHttpClient;
    private AwsConnector mockConnector;

    @Before
    public void setUp() {
        mockHttpClient = mock(SdkAsyncHttpClient.class);
        mockConnector = mock(AwsConnector.class);

        when(mockConnector.getAccessKey()).thenReturn("test-access-key");
        when(mockConnector.getSecretKey()).thenReturn("test-secret-key");
        when(mockConnector.getRegion()).thenReturn("us-east-1");

        handler = new BedrockInvokeModelStreamingHandler(mockHttpClient, mockConnector);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildInvokeModelRequest_passesPayloadAsRawBytes() {
        String payload =
            "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":1024,\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-3-5-sonnet-20241022-v2:0");

        InvokeModelWithResponseStreamRequest request = handler.buildInvokeModelRequest(payload, parameters);

        assertNotNull(request);
        assertEquals("anthropic.claude-3-5-sonnet-20241022-v2:0", request.modelId());
        assertEquals("application/json", request.contentType());
        assertEquals("application/json", request.accept());
        assertEquals(payload, request.body().asUtf8String());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildInvokeModelRequest_withCompactionPayload() {
        String payload = "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":1024,"
            + "\"anthropic_beta\":[\"interleaved-thinking-2025-05-14\",\"context-management-2025-05-14\"],"
            + "\"context_management\":{\"enabled\":true,\"pause_after_compaction\":true},"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-sonnet-4-20250514-v1:0");

        InvokeModelWithResponseStreamRequest request = handler.buildInvokeModelRequest(payload, parameters);

        assertNotNull(request);
        assertEquals("anthropic.claude-sonnet-4-20250514-v1:0", request.modelId());
        // Payload is passed through as-is, preserving all model-specific parameters
        assertEquals(payload, request.body().asUtf8String());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildInvokeModelRequest_withExtendedThinking() {
        String payload = "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":16000,"
            + "\"thinking\":{\"type\":\"enabled\",\"budget_tokens\":10000},"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Think about this\"}]}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-opus-4-20250514-v1:0");

        InvokeModelWithResponseStreamRequest request = handler.buildInvokeModelRequest(payload, parameters);

        assertNotNull(request);
        assertEquals(payload, request.body().asUtf8String());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildInvokeModelRequest_withToolsPayload() {
        String payload = "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":1024,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather?\"}],"
            + "\"tools\":[{\"name\":\"get_weather\",\"description\":\"Get weather\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}}]}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-3-5-sonnet-20241022-v2:0");

        InvokeModelWithResponseStreamRequest request = handler.buildInvokeModelRequest(payload, parameters);

        assertNotNull(request);
        assertEquals(payload, request.body().asUtf8String());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildInvokeModelRequest_withEmptyPayload() {
        String payload = "{}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-3-5-sonnet-20241022-v2:0");

        InvokeModelWithResponseStreamRequest request = handler.buildInvokeModelRequest(payload, parameters);

        assertNotNull(request);
        assertEquals(payload, request.body().asUtf8String());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConstructorWithCustomParser() {
        InvokeModelEventParser customParser = mock(InvokeModelEventParser.class);
        BedrockInvokeModelStreamingHandler customHandler = new BedrockInvokeModelStreamingHandler(
            mockHttpClient,
            mockConnector,
            null,
            customParser
        );

        assertNotNull(customHandler);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConstructorWithParameters() {
        Map<String, String> params = new HashMap<>();
        params.put("model", "test-model");
        BedrockInvokeModelStreamingHandler paramHandler = new BedrockInvokeModelStreamingHandler(mockHttpClient, mockConnector, params);

        assertNotNull(paramHandler);
    }

    // ==================== Tests for contentBlockToMap ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testContentBlockToMap_TextBlock() throws Exception {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello world");

        Map<String, String> result = invokeContentBlockToMap(textBlock);

        assertNotNull(result);
        assertEquals("text", result.get("type"));
        assertEquals("Hello world", result.get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContentBlockToMap_CompactionBlock() throws Exception {
        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Summary of earlier conversation");

        Map<String, String> result = invokeContentBlockToMap(compactionBlock);

        assertNotNull(result);
        assertEquals("compaction", result.get("type"));
        assertEquals("Summary of earlier conversation", result.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContentBlockToMap_TextBlockWithNull() throws Exception {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText(null);

        Map<String, String> result = invokeContentBlockToMap(textBlock);

        assertNotNull(result);
        assertEquals("text", result.get("type"));
        assertEquals("", result.get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContentBlockToMap_CompactionBlockWithNull() throws Exception {
        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent(null);

        Map<String, String> result = invokeContentBlockToMap(compactionBlock);

        assertNotNull(result);
        assertEquals("compaction", result.get("type"));
        assertEquals("", result.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContentBlockToMap_ImageBlock() throws Exception {
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);

        Map<String, String> result = invokeContentBlockToMap(imageBlock);

        assertNotNull(result);
        assertEquals("image", result.get("type"));
    }

    // ==================== Tests for createFinalAnswerResponse ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_TextOnly() throws Exception {
        List<ContentBlock> contentBlocks = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Final response");
        contentBlocks.add(textBlock);

        MLTaskResponse response = invokeCreateFinalAnswerResponse(contentBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        assertNotNull(output);
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        assertTrue(dataAsMap.containsKey("output"));
        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        assertEquals("assistant", message.get("role"));

        List<Map<String, String>> content = (List<Map<String, String>>) message.get("content");
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("Final response", content.get(0).get("text"));

        assertEquals("end_turn", dataAsMap.get("stopReason"));
        assertEquals("end_turn", dataAsMap.get("stop_reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_CompactionOnly() throws Exception {
        List<ContentBlock> contentBlocks = new ArrayList<>();
        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Summary text");
        contentBlocks.add(compactionBlock);

        MLTaskResponse response = invokeCreateFinalAnswerResponse(contentBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, String>> content = (List<Map<String, String>>) message.get("content");

        assertEquals(1, content.size());
        assertEquals("compaction", content.get(0).get("type"));
        assertEquals("Summary text", content.get(0).get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_CompactionAndText() throws Exception {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Earlier conversation summary");
        contentBlocks.add(compactionBlock);

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Current response");
        contentBlocks.add(textBlock);

        MLTaskResponse response = invokeCreateFinalAnswerResponse(contentBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, String>> content = (List<Map<String, String>>) message.get("content");

        assertEquals(2, content.size());
        assertEquals("compaction", content.get(0).get("type"));
        assertEquals("Earlier conversation summary", content.get(0).get("content"));
        assertEquals("text", content.get(1).get("type"));
        assertEquals("Current response", content.get(1).get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_EmptyContentBlocks() throws Exception {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        MLTaskResponse response = invokeCreateFinalAnswerResponse(contentBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, String>> content = (List<Map<String, String>>) message.get("content");

        // Should have empty text block as fallback
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("", content.get(0).get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_MultipleBlocks() throws Exception {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Summary");
        contentBlocks.add(compactionBlock);

        ContentBlock textBlock1 = new ContentBlock();
        textBlock1.setType(ContentType.TEXT);
        textBlock1.setText("First response");
        contentBlocks.add(textBlock1);

        ContentBlock textBlock2 = new ContentBlock();
        textBlock2.setType(ContentType.TEXT);
        textBlock2.setText("Second response");
        contentBlocks.add(textBlock2);

        MLTaskResponse response = invokeCreateFinalAnswerResponse(contentBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, String>> content = (List<Map<String, String>>) message.get("content");

        assertEquals(3, content.size());
        assertEquals("compaction", content.get(0).get("type"));
        assertEquals("text", content.get(1).get("type"));
        assertEquals("text", content.get(2).get("type"));
    }

    // ==================== Tests for createToolUseResponse ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateToolUseResponse_Basic() throws Exception {
        AtomicReference<String> toolName = new AtomicReference<>("get_weather");
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>(Map.of("location", "NYC"));
        AtomicReference<String> toolUseId = new AtomicReference<>("tool_123");
        List<ContentBlock> textBlocks = new ArrayList<>();

        MLTaskResponse response = invokeCreateToolUseResponse(toolName, toolInput, toolUseId, textBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        assertEquals("tool_use", dataAsMap.get("stopReason"));
        assertEquals("tool_use", dataAsMap.get("stop_reason"));

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        assertEquals("assistant", message.get("role"));

        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertEquals(1, content.size());

        Map<String, Object> toolUse = content.get(0);
        assertEquals("tool_use", toolUse.get("type"));
        assertEquals("tool_123", toolUse.get("id"));
        assertEquals("get_weather", toolUse.get("name"));
        assertNotNull(toolUse.get("input"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateToolUseResponse_WithTextAndCompactionBeforeToolCall() throws Exception {
        AtomicReference<String> toolName = new AtomicReference<>("search");
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>(Map.of("query", "test"));
        AtomicReference<String> toolUseId = new AtomicReference<>("tool_456");

        List<ContentBlock> textBlocks = new ArrayList<>();
        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Previous context");
        textBlocks.add(compactionBlock);

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Let me search for that");
        textBlocks.add(textBlock);

        MLTaskResponse response = invokeCreateToolUseResponse(toolName, toolInput, toolUseId, textBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");

        // Should have compaction + text + tool_use
        assertEquals(3, content.size());
        assertEquals("compaction", content.get(0).get("type"));
        assertEquals("Previous context", content.get(0).get("content"));
        assertEquals("text", content.get(1).get("type"));
        assertEquals("Let me search for that", content.get(1).get("text"));
        assertEquals("tool_use", content.get(2).get("type"));
        assertEquals("tool_456", content.get(2).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateToolUseResponse_WithEmptyToolInput() throws Exception {
        AtomicReference<String> toolName = new AtomicReference<>("no_args_tool");
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>(Map.of());
        AtomicReference<String> toolUseId = new AtomicReference<>("tool_789");
        List<ContentBlock> textBlocks = new ArrayList<>();

        MLTaskResponse response = invokeCreateToolUseResponse(toolName, toolInput, toolUseId, textBlocks);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, Object> dataAsMap = (Map<String, Object>) (Map<String, ?>) output
            .getMlModelOutputs()
            .get(0)
            .getMlModelTensors()
            .get(0)
            .getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");

        Map<String, Object> toolUse = content.get(0);
        assertEquals("tool_use", toolUse.get("type"));
        Map<String, Object> input = (Map<String, Object>) toolUse.get("input");
        assertTrue(input.isEmpty());
    }

    // ==================== Tests for error detection methods ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testIsThrottlingError_WithThrottlingMessage() throws Exception {
        Throwable error = new RuntimeException("Request throttling occurred");
        boolean result = invokeIsThrottlingError(error);
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIsThrottlingError_WithTooManyRequestsException() throws Exception {
        Throwable error = new RuntimeException("TooManyRequestsException");
        boolean result = invokeIsThrottlingError(error);
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIsThrottlingError_WithRateExceeded() throws Exception {
        Throwable error = new RuntimeException("Rate exceeded for API");
        boolean result = invokeIsThrottlingError(error);
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIsClientError_WithValidationException() throws Exception {
        Throwable error = ValidationException.builder().message("Invalid input").build();
        boolean result = invokeIsClientError(error);
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIsClientError_WithInvalidRequestException() throws Exception {
        Throwable error = InvalidRequestException.builder().message("Bad request").build();
        boolean result = invokeIsClientError(error);
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIsClientError_WithAuthenticationException() throws Exception {
        Throwable error = new AuthenticationException("Auth failed");
        boolean result = invokeIsClientError(error);
        assertTrue(result);
    }

    // ==================== Helper methods to invoke private methods via reflection ====================

    private Map<String, String> invokeContentBlockToMap(ContentBlock block) throws Exception {
        Method method = BedrockInvokeModelStreamingHandler.class.getDeclaredMethod("contentBlockToMap", ContentBlock.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(handler, block);
    }

    private MLTaskResponse invokeCreateFinalAnswerResponse(List<ContentBlock> contentBlocks) throws Exception {
        Method method = BedrockInvokeModelStreamingHandler.class.getDeclaredMethod("createFinalAnswerResponse", List.class, Map.class);
        method.setAccessible(true);
        return (MLTaskResponse) method.invoke(handler, contentBlocks, null);
    }

    private MLTaskResponse invokeCreateToolUseResponse(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        List<ContentBlock> textBlocks
    ) throws Exception {
        Method method = BedrockInvokeModelStreamingHandler.class
            .getDeclaredMethod(
                "createToolUseResponse",
                AtomicReference.class,
                AtomicReference.class,
                AtomicReference.class,
                List.class,
                Map.class
            );
        method.setAccessible(true);
        return (MLTaskResponse) method.invoke(handler, toolName, toolInput, toolUseId, textBlocks, null);
    }

    private boolean invokeIsThrottlingError(Throwable error) throws Exception {
        Method method = BedrockInvokeModelStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(handler, error);
    }

    private boolean invokeIsClientError(Throwable error) throws Exception {
        Method method = BedrockInvokeModelStreamingHandler.class.getDeclaredMethod("isClientError", Throwable.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(handler, error);
    }
}
