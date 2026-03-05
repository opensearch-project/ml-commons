/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.AwsConnector;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;

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
    public void testBuildInvokeModelRequest_withEmptyPayload() {
        String payload = "{}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-3-5-sonnet-20241022-v2:0");

        InvokeModelWithResponseStreamRequest request = handler.buildInvokeModelRequest(payload, parameters);

        assertNotNull(request);
        assertEquals(payload, request.body().asUtf8String());
    }

    @Test
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
    public void testConstructorWithParameters() {
        Map<String, String> params = new HashMap<>();
        params.put("model", "test-model");
        BedrockInvokeModelStreamingHandler paramHandler = new BedrockInvokeModelStreamingHandler(mockHttpClient, mockConnector, params);

        assertNotNull(paramHandler);
    }
}
