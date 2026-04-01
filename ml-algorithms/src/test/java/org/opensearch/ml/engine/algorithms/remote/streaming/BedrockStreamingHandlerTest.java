package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
        MLTaskResponse response = handler.createFinalAnswerResponse("Final answer text", null);

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
        MLTaskResponse response = handler.createFinalAnswerResponse(null, null);

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
        MLTaskResponse response = handler.createFinalAnswerResponse("", null);

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
    public void testCreateFinalAnswerResponse_WithTokenUsage() {
        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("inputTokens", 100);
        tokenUsage.put("outputTokens", 50);
        tokenUsage.put("totalTokens", 150);

        MLTaskResponse response = handler.createFinalAnswerResponse("answer", tokenUsage);

        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        assertEquals("end_turn", dataMap.get("stopReason"));

        Map<String, Object> usage = (Map<String, Object>) dataMap.get("usage");
        assertNotNull(usage);
        assertEquals(100, usage.get("inputTokens"));
        assertEquals(50, usage.get("outputTokens"));
        assertEquals(150, usage.get("totalTokens"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateFinalAnswerResponse_WithTokenUsageAndTextContent() {
        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("inputTokens", 500);
        tokenUsage.put("outputTokens", 200);
        tokenUsage.put("totalTokens", 700);

        MLTaskResponse response = handler.createFinalAnswerResponse("Here is the answer", tokenUsage);

        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();

        // Verify response structure
        assertEquals("end_turn", dataMap.get("stopReason"));

        // Verify text content
        Map<String, Object> outputMap = (Map<String, Object>) dataMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertEquals("Here is the answer", content.get(0).get("text"));

        // Verify token usage
        Map<String, Object> usage = (Map<String, Object>) dataMap.get("usage");
        assertNotNull(usage);
        assertEquals(500, usage.get("inputTokens"));
        assertEquals(200, usage.get("outputTokens"));
        assertEquals(700, usage.get("totalTokens"));
    }

    // ==================== Tests for buildConverseStreamRequest with messages ====================

    @Test
    public void testBuildConverseStreamRequest_withBasicTextMessages() throws Exception {
        String payloadJson = """
            {
              "messages": [
                {"role": "user", "content": [{"text": "Hello"}]},
                {"role": "assistant", "content": [{"text": "Hi there"}]},
                {"role": "user", "content": [{"text": "How are you?"  }]}
              ]
            }
            """;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertEquals(3, request.messages().size());
        assertEquals("user", request.messages().get(0).role().toString());
        assertEquals("Hello", request.messages().get(0).content().get(0).text());
        assertEquals("assistant", request.messages().get(1).role().toString());
    }

    @Test
    public void testBuildConverseStreamRequest_withSystemMessages() throws Exception {
        String payloadJson = """
            {
              "system": [{"text": "You are a helpful assistant"}],
              "messages": [
                {"role": "user", "content": [{"text": "Hello"}]}
              ]
            }
            """;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.system());
        assertEquals(1, request.system().size());
        assertEquals("You are a helpful assistant", request.system().get(0).text());
    }

    @Test
    public void testBuildConverseStreamRequest_withToolConfig() throws Exception {
        String payloadJson = """
            {
              "messages": [
                {"role": "user", "content": [{"text": "What is the weather?"}]}
              ],
              "toolConfig": {
                "tools": [
                  {
                    "toolSpec": {
                      "name": "get_weather",
                      "description": "Gets the weather",
                      "inputSchema": {
                        "json": {
                          "type": "object",
                          "properties": {
                            "location": {"type": "string"}
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
            """;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertNotNull(request.toolConfig());
        assertEquals(1, request.toolConfig().tools().size());
        assertEquals("get_weather", request.toolConfig().tools().get(0).toolSpec().name());
        assertEquals("Gets the weather", request.toolConfig().tools().get(0).toolSpec().description());
    }

    @Test
    public void testBuildConverseStreamRequest_withToolResultContent() throws Exception {
        String payloadJson = """
            {
              "messages": [
                {"role": "user", "content": [{"text": "What is the weather?"}]},
                {"role": "assistant", "content": [
                  {"toolUse": {"toolUseId": "tool_1", "name": "get_weather", "input": {"location": "Seattle"}}}
                ]},
                {"role": "user", "content": [
                  {"toolResult": {"toolUseId": "tool_1", "content": [{"text": "72F and sunny"}]}}
                ]}
              ]
            }
            """;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "anthropic.claude-v2");

        ConverseStreamRequest request = handler.buildConverseStreamRequest(payloadJson, parameters);

        assertNotNull(request);
        assertEquals(3, request.messages().size());
        // Third message should have a tool result content block
        assertNotNull(request.messages().get(2).content().get(0).toolResult());
        assertEquals("tool_1", request.messages().get(2).content().get(0).toolResult().toolUseId());
    }

    // ==================== Tests for createToolUseResponse ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateToolUseResponse_withToolUseOnly() throws Exception {
        AtomicReference<String> toolName = new AtomicReference<>("get_weather");
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>(Map.of("location", "Seattle"));
        AtomicReference<String> toolUseId = new AtomicReference<>("tool_123");

        MLTaskResponse response = invokeCreateToolUseResponse(toolName, toolInput, toolUseId, new StringBuilder(), null);

        assertNotNull(response);
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        assertEquals("tool_use", dataMap.get("stopReason"));
        assertNull(dataMap.get("usage"));

        Map<String, Object> outputMap = (Map<String, Object>) dataMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertEquals(1, content.size());
        Map<String, Object> toolUseBlock = (Map<String, Object>) content.get(0).get("toolUse");
        assertEquals("get_weather", toolUseBlock.get("name"));
        assertEquals("tool_123", toolUseBlock.get("toolUseId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateToolUseResponse_withTokenUsage() throws Exception {
        AtomicReference<String> toolName = new AtomicReference<>("search");
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>(Map.of("query", "test"));
        AtomicReference<String> toolUseId = new AtomicReference<>("tool_456");

        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("inputTokens", 200);
        tokenUsage.put("outputTokens", 100);
        tokenUsage.put("totalTokens", 300);

        MLTaskResponse response = invokeCreateToolUseResponse(toolName, toolInput, toolUseId, new StringBuilder(), tokenUsage);

        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        assertEquals("tool_use", dataMap.get("stopReason"));

        Map<String, Object> usage = (Map<String, Object>) dataMap.get("usage");
        assertNotNull(usage);
        assertEquals(200, usage.get("inputTokens"));
        assertEquals(100, usage.get("outputTokens"));
        assertEquals(300, usage.get("totalTokens"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateToolUseResponse_withAccumulatedTextContent() throws Exception {
        AtomicReference<String> toolName = new AtomicReference<>("calculator");
        AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>(Map.of("expression", "2+2"));
        AtomicReference<String> toolUseId = new AtomicReference<>("tool_789");
        StringBuilder accumulatedContent = new StringBuilder("Let me calculate that for you.");

        MLTaskResponse response = invokeCreateToolUseResponse(toolName, toolInput, toolUseId, accumulatedContent, null);

        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        Map<String, ?> dataMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();

        Map<String, Object> outputMap = (Map<String, Object>) dataMap.get("output");
        Map<String, Object> message = (Map<String, Object>) outputMap.get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        // Should have text block + toolUse block
        assertEquals(2, content.size());
        assertEquals("Let me calculate that for you.", content.get(0).get("text"));
        assertNotNull(content.get(1).get("toolUse"));
    }

    @Test(expected = InvocationTargetException.class)
    public void testCreateToolUseResponse_nullToolName() throws Exception {
        invokeCreateToolUseResponse(null, new AtomicReference<>(Map.of()), new AtomicReference<>("id"), new StringBuilder(), null);
    }

    @Test(expected = InvocationTargetException.class)
    public void testCreateToolUseResponse_nullToolInput() throws Exception {
        invokeCreateToolUseResponse(new AtomicReference<>("name"), null, new AtomicReference<>("id"), new StringBuilder(), null);
    }

    @Test(expected = InvocationTargetException.class)
    public void testCreateToolUseResponse_nullToolUseId() throws Exception {
        invokeCreateToolUseResponse(new AtomicReference<>("name"), new AtomicReference<>(Map.of()), null, new StringBuilder(), null);
    }

    // ===== Reflection helper for testing private methods =====

    private MLTaskResponse invokeCreateToolUseResponse(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder accumulatedContent,
        Map<String, Object> tokenUsage
    ) throws Exception {
        Method method = BedrockStreamingHandler.class
            .getDeclaredMethod(
                "createToolUseResponse",
                AtomicReference.class,
                AtomicReference.class,
                AtomicReference.class,
                StringBuilder.class,
                Map.class
            );
        method.setAccessible(true);
        return (MLTaskResponse) method.invoke(handler, toolName, toolInput, toolUseId, accumulatedContent, tokenUsage);
    }
}
