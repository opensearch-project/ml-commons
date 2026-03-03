package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;

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

    @Test
    public void testBuildConverseStreamRequest_withImageContent() throws Exception {
        String payloadJson = """
            {
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {
                      "text": "Describe this image"
                    },
                    {
                      "image": {
                        "format": "png",
                        "source": {
                          "bytes": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                        }
                      }
                    }
                  ]
                }
              ]
            }
            """;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.messages());
        assertEquals(1, request.messages().size());

        // Verify message has both text and image content
        assertEquals(2, request.messages().get(0).content().size());
        assertNotNull(request.messages().get(0).content().get(0).text());
        assertNotNull(request.messages().get(0).content().get(1).image());
    }

    @Test
    public void testBuildConverseStreamRequest_withImageContentMultipleFormats() throws Exception {
        String payloadJson = """
            {
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {
                      "image": {
                        "format": "jpeg",
                        "source": {
                          "bytes": "base64ImageData"
                        }
                      }
                    }
                  ]
                }
              ]
            }
            """;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.messages());
        assertEquals(1, request.messages().size());
        assertNotNull(request.messages().get(0).content().get(0).image());
        assertEquals("jpeg", request.messages().get(0).content().get(0).image().format().toString());
    }

    // ==================== Tests for createFinalAnswerResponse ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_WithText() {
        MLTaskResponse response = handler.createFinalAnswerResponse("Final answer text");

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        assertNotNull(output);
        assertEquals(1, output.getMlModelOutputs().size());
        assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());

        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        assertNotNull(dataMap);
        assertEquals("end_turn", dataMap.get("stopReason"));

        Map<String, Object> outputMap = (Map<String, Object>) dataMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertEquals(1, content.size());
        assertEquals("Final answer text", content.get(0).get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_NullText() {
        MLTaskResponse response = handler.createFinalAnswerResponse(null);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        Map<String, Object> outputMap = (Map<String, Object>) dataMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertEquals("", content.get(0).get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_EmptyText() {
        MLTaskResponse response = handler.createFinalAnswerResponse("");

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        Map<String, Object> outputMap = (Map<String, Object>) dataMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertEquals("", content.get(0).get("text"));
    }
}
