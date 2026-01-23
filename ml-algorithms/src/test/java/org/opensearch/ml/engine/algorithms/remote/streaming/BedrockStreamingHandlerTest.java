package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.AwsConnector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailStreamConfiguration;

public class BedrockStreamingHandlerTest {

    private BedrockStreamingHandler handler;
    private SdkAsyncHttpClient mockHttpClient;
    private AwsConnector mockConnector;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        mockHttpClient = mock(SdkAsyncHttpClient.class);
        mockConnector = mock(AwsConnector.class);

        when(mockConnector.getAccessKey()).thenReturn("test-access-key");
        when(mockConnector.getSecretKey()).thenReturn("test-secret-key");
        when(mockConnector.getRegion()).thenReturn("us-east-1");

        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector);
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testBuildConverseStreamRequest_withGuardrailConfig() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> guardrailConfig = new HashMap<>();
        guardrailConfig.put("guardrailIdentifier", "test-guardrail-id");
        guardrailConfig.put("guardrailVersion", "1");
        guardrailConfig.put("trace", "enabled");
        payload.put("guardrailConfig", guardrailConfig);

        String payloadJson = objectMapper.writeValueAsString(payload);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.guardrailConfig());
        assertEquals("test-guardrail-id", request.guardrailConfig().guardrailIdentifier());
        assertEquals("1", request.guardrailConfig().guardrailVersion());
        assertEquals("enabled", request.guardrailConfig().traceAsString());
        assertEquals("async", request.guardrailConfig().streamProcessingModeAsString());
    }

    @Test
    public void testBuildConverseStreamRequest_withPartialGuardrailConfig() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> guardrailConfig = new HashMap<>();
        guardrailConfig.put("guardrailIdentifier", "test-guardrail-id");
        payload.put("guardrailConfig", guardrailConfig);

        String payloadJson = objectMapper.writeValueAsString(payload);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.guardrailConfig());
        assertEquals("test-guardrail-id", request.guardrailConfig().guardrailIdentifier());
        assertNull(request.guardrailConfig().guardrailVersion());
        assertEquals("async", request.guardrailConfig().streamProcessingModeAsString());
    }

    @Test
    public void testBuildConverseStreamRequest_withoutGuardrailConfig() throws Exception {
        Map<String, Object> payload = new HashMap<>();

        String payloadJson = objectMapper.writeValueAsString(payload);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNull(request.guardrailConfig());
    }

    @Test
    public void testBuildConverseStreamRequest_withEmptyGuardrailConfig() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> guardrailConfig = new HashMap<>();
        payload.put("guardrailConfig", guardrailConfig);

        String payloadJson = objectMapper.writeValueAsString(payload);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.guardrailConfig());
        assertEquals("async", request.guardrailConfig().streamProcessingModeAsString());
    }

    @Test
    public void testParseGuardrailConfig_withAllFields() throws Exception {
        Map<String, Object> guardrailConfig = new HashMap<>();
        guardrailConfig.put("guardrailIdentifier", "my-guardrail");
        guardrailConfig.put("guardrailVersion", "2");
        guardrailConfig.put("trace", "disabled");

        JsonNode guardrailNode = objectMapper.valueToTree(guardrailConfig);
        GuardrailStreamConfiguration result = handler.parseGuardrailConfig(guardrailNode);

        assertNotNull(result);
        assertEquals("my-guardrail", result.guardrailIdentifier());
        assertEquals("2", result.guardrailVersion());
        assertEquals("disabled", result.traceAsString());
        assertEquals("async", result.streamProcessingModeAsString());
    }

    @Test
    public void testParseGuardrailConfig_withOnlyIdentifier() throws Exception {
        Map<String, Object> guardrailConfig = new HashMap<>();
        guardrailConfig.put("guardrailIdentifier", "simple-guardrail");

        JsonNode guardrailNode = objectMapper.valueToTree(guardrailConfig);
        GuardrailStreamConfiguration result = handler.parseGuardrailConfig(guardrailNode);

        assertNotNull(result);
        assertEquals("simple-guardrail", result.guardrailIdentifier());
        assertNull(result.guardrailVersion());
        assertNull(result.trace());
        assertEquals("async", result.streamProcessingModeAsString());
    }

    @Test
    public void testParseGuardrailConfig_withEmptyConfig() throws Exception {
        Map<String, Object> guardrailConfig = new HashMap<>();

        JsonNode guardrailNode = objectMapper.valueToTree(guardrailConfig);
        GuardrailStreamConfiguration result = handler.parseGuardrailConfig(guardrailNode);

        assertNotNull(result);
        assertNull(result.guardrailIdentifier());
        assertNull(result.guardrailVersion());
        assertNull(result.trace());
        assertEquals("async", result.streamProcessingModeAsString());
    }
}
