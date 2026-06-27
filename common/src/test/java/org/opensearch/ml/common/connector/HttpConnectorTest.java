/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.MockitoTestHelper.mockActionListener;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.TriConsumer;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.search.SearchModule;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class HttpConnectorTest {
    TriConsumer<List<String>, String, ActionListener<List<String>>> encryptFunction = (s, v, t) -> t
        .onResponse(List.of(s.stream().map(x -> "encrypted: " + x.toLowerCase(Locale.ROOT)).toArray(String[]::new)));
    TriConsumer<List<String>, String, ActionListener<List<String>>> decryptFunction = (s, v, t) -> t
        .onResponse(List.of(s.stream().map(x -> "decrypted: " + x.toUpperCase(Locale.ROOT)).toArray(String[]::new)));

    ActionListener<Boolean> listener = mockActionListener();

    String TEST_CONNECTOR_JSON_STRING = "{\"name\":\"test_connector_name\",\"version\":\"1\","
        + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
        + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
        + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
        + "\"headers\":{\"api_key\":\"${credential.key}\"},"
        + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
        + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
        + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
        + "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\","
        + "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30,\"read_timeout\":30,"
        + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}}";

    @Test
    public void constructor_InvalidProtocol() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            HttpConnector.builder().protocol("wrong protocol").build();
        });
        assertEquals(
            "Unsupported connector protocol. Please use one of [aws_sigv4, http, mcp_sse, mcp_streamable_http]",
            exception.getMessage()
        );
    }

    @Test
    public void writeTo() throws IOException {
        HttpConnector connector = createHttpConnector();

        BytesStreamOutput output = new BytesStreamOutput();
        connector.writeTo(output);

        HttpConnector connector2 = new HttpConnector(output.bytes().streamInput());
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void toXContent() throws IOException {
        HttpConnector connector = createHttpConnector();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals(TEST_CONNECTOR_JSON_STRING, content);
    }

    @Test
    public void constructor_Parser() throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                TEST_CONNECTOR_JSON_STRING
            );
        parser.nextToken();

        HttpConnector connector = new HttpConnector("http", parser);
        Assert.assertEquals("test_connector_name", connector.getName());
        Assert.assertEquals("1", connector.getVersion());
        Assert.assertEquals("this is a test connector", connector.getDescription());
        Assert.assertEquals("http", connector.getProtocol());
        Assert.assertEquals(AccessMode.PUBLIC, connector.getAccess());
        Assert.assertEquals(1, connector.getParameters().size());
        Assert.assertEquals("test input value", connector.getParameters().get("input"));
        Assert.assertEquals(1, connector.getActions().size());
        Assert.assertEquals(ConnectorAction.ActionType.PREDICT, connector.getActions().get(0).getActionType());
        Assert.assertEquals("POST", connector.getActions().get(0).getMethod());
        Assert.assertEquals("https://test.com", connector.getActions().get(0).getUrl());
    }

    @Test
    public void cloneConnector() {
        HttpConnector connector = createHttpConnector();
        Connector connector2 = connector.cloneConnector();
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void decrypt() {
        HttpConnector connector = createHttpConnector();
        TestHelper.endecryptCredentials(connector, decryptFunction, false);
        Map<String, String> decryptedCredential = connector.getDecryptedCredential();
        Assert.assertEquals(1, decryptedCredential.size());
        Assert.assertEquals("decrypted: TEST_KEY_VALUE", decryptedCredential.get("key"));
        Assert.assertNotNull(connector.getDecryptedHeaders());
        Assert.assertEquals(1, connector.getDecryptedHeaders().size());
        Assert.assertEquals("decrypted: TEST_KEY_VALUE", connector.getDecryptedHeaders().get("api_key"));

        connector.removeCredential();
        Assert.assertNull(connector.getCredential());
        Assert.assertNull(connector.getDecryptedCredential());
        Assert.assertNull(connector.getDecryptedHeaders());
    }

    @Test
    public void encrypted() {
        HttpConnector connector = createHttpConnector();
        TestHelper.endecryptCredentials(connector, encryptFunction, true);
        Map<String, String> credential = connector.getCredential();
        Assert.assertEquals(1, credential.size());
        Assert.assertEquals("encrypted: test_key_value", credential.get("key"));

        connector.removeCredential();
        Assert.assertNull(connector.getCredential());
        Assert.assertNull(connector.getDecryptedCredential());
        Assert.assertNull(connector.getDecryptedHeaders());
    }

    @Test
    public void getActionEndpoint() {
        HttpConnector connector = createHttpConnector();
        Assert.assertEquals("https://test.com", connector.getActionEndpoint(PREDICT.name(), null));
    }

    @Test
    public void getActionHttpMethod() {
        HttpConnector connector = createHttpConnector();
        Assert.assertEquals("POST", connector.getActionHttpMethod(PREDICT.name()));
    }

    @Test
    public void createPayload_Invalid() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpConnector connector = createHttpConnector();
            String predictPayload = connector.createPayload(PREDICT.name(), null);
            connector.validatePayload(predictPayload);
        });
        assertEquals("Some parameter placeholder not filled in payload: input", exception.getMessage());
    }

    @Test
    public void createPayload_InvalidJson() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            String requestBody = "{\"input\": ${parameters.input} }";
            HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);
            String predictPayload = connector.createPayload(PREDICT.name(), null);
            connector.validatePayload(predictPayload);
        });
        assertEquals("Invalid payload: {\"input\": ${parameters.input} }", exception.getMessage());
    }

    @Test
    public void createPayload() {
        HttpConnector connector = createHttpConnector();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test input value");
        String predictPayload = connector.createPayload(PREDICT.name(), parameters);
        connector.validatePayload(predictPayload);
        Assert.assertEquals("{\"input\": \"test input value\"}", predictPayload);
    }

    @Test
    public void createPayload_ExtraParams() {

        String requestBody =
            "{\"input\": \"${parameters.input}\", \"parameters\": {\"sparseEmbeddingFormat\": \"${parameters.sparseEmbeddingFormat}\", \"content_type\": \"${parameters.content_type}\" }}";
        String expected =
            "{\"input\": \"test value\", \"parameters\": {\"sparseEmbeddingFormat\": \"WORD\", \"content_type\": \"query\" }}";

        HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test value");
        parameters.put("sparseEmbeddingFormat", "WORD");
        parameters.put("content_type", "query");
        String predictPayload = connector.createPayload(PREDICT.name(), parameters);
        connector.validatePayload(predictPayload);
        Assert.assertEquals(expected, predictPayload);
    }

    @Test
    public void createPayload_MissingParamsInvalidJson() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            String requestBody =
                "{\"input\": \"${parameters.input}\", \"parameters\": {\"sparseEmbeddingFormat\": \"${parameters.sparseEmbeddingFormat}\", \"content_type\": ${parameters.content_type} }}";
            HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);
            Map<String, String> parameters = new HashMap<>();
            parameters.put("input", "test value");
            parameters.put("sparseEmbeddingFormat", "WORD");
            String predictPayload = connector.createPayload(PREDICT.name(), parameters);
            connector.validatePayload(predictPayload);
        });
        assertEquals(
            "Invalid payload: {\"input\": \"test value\", \"parameters\": {\"sparseEmbeddingFormat\": \"WORD\", \"content_type\": ${parameters.content_type} }}",
            exception.getMessage()
        );
    }

    @Test
    public void createPayload_WithStreamParameter_OpenAI() {
        String requestBody = "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "Hello world");
        parameters.put("stream", "true");
        parameters.put("_llm_interface", "openai/v1/chat/completions");

        String payload = connector.createPayload(PREDICT.name(), parameters);
        Assert
            .assertEquals(
                "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello world\"}],\"stream\":true}",
                payload
            );
    }

    @Test
    public void createPayload_WithoutStreamParameter() {
        String requestBody = "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "Hello world");
        parameters.put("_llm_interface", "openai/v1/chat/completions");

        String payload = connector.createPayload(PREDICT.name(), parameters);
        Assert.assertEquals("{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": \"Hello world\"}]}", payload);
    }

    @Test
    public void createPayload_WithStreamParameter_UnsupportedInterface() {
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "Hello world");
        parameters.put("stream", "true");
        parameters.put("_llm_interface", "invalid/interface");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        Assert.assertEquals("{\"input\": \"Hello world\"}", payload);
    }

    @Test
    public void parseResponse_modelTensorJson() throws IOException {
        HttpConnector connector = createHttpConnector();

        Map<String, String> dataAsMap = new HashMap<>();
        dataAsMap.put("key1", "test value1");
        dataAsMap.put("key2", "test value2");
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        tensor.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String responseContent = TestHelper.xContentBuilderToString(builder);

        List<ModelTensor> modelTensors = new ArrayList<>();
        connector.parseResponse(responseContent, modelTensors, true);
        Assert.assertEquals("response", modelTensors.get(0).getName());
        Assert.assertEquals(2, modelTensors.get(0).getDataAsMap().size());
        Assert.assertEquals("test value1", modelTensors.get(0).getDataAsMap().get("key1"));
        Assert.assertEquals("test value2", modelTensors.get(0).getDataAsMap().get("key2"));
    }

    @Test
    public void parseResponse_modelTensorArrayJson() throws IOException {
        HttpConnector connector = createHttpConnector();

        Map<String, String> dataAsMap1 = new HashMap<>();
        dataAsMap1.put("key1", "test value1");
        ModelTensor tensor1 = ModelTensor.builder().name("response1").dataAsMap(dataAsMap1).build();
        Map<String, String> dataAsMap2 = new HashMap<>();
        dataAsMap2.put("key2", "test value2");
        ModelTensor tensor2 = ModelTensor.builder().name("response2").dataAsMap(dataAsMap2).build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startArray();
        tensor1.toXContent(builder, ToXContent.EMPTY_PARAMS);
        tensor2.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endArray();
        String responseContent = TestHelper.xContentBuilderToString(builder);

        List<ModelTensor> modelTensors = new ArrayList<>();
        connector.parseResponse(responseContent, modelTensors, true);
        Assert.assertEquals(2, modelTensors.size());

        Assert.assertEquals("response1", modelTensors.get(0).getName());
        Assert.assertEquals(1, modelTensors.get(0).getDataAsMap().size());
        Assert.assertEquals("test value1", modelTensors.get(0).getDataAsMap().get("key1"));

        Assert.assertEquals("response2", modelTensors.get(1).getName());
        Assert.assertEquals(1, modelTensors.get(1).getDataAsMap().size());
        Assert.assertEquals("test value2", modelTensors.get(1).getDataAsMap().get("key2"));
    }

    @Test
    public void parseResponse_JsonString() throws IOException {
        HttpConnector connector = createHttpConnector();
        String jsonStr = "{\"output\": \"test output\"}";
        List<ModelTensor> modelTensors = new ArrayList<>();

        connector.parseResponse(jsonStr, modelTensors, false);
        Assert.assertEquals(1, modelTensors.size());
        Assert.assertEquals(1, modelTensors.get(0).getDataAsMap().size());
        Assert.assertEquals("test output", modelTensors.get(0).getDataAsMap().get("output"));
    }

    @Test
    public void parseResponse_NonJsonString() throws IOException {
        HttpConnector connector = createHttpConnector();
        String jsonStr = "test output";
        List<ModelTensor> modelTensors = new ArrayList<>();

        connector.parseResponse(jsonStr, modelTensors, false);
        Assert.assertEquals(1, modelTensors.size());
        Assert.assertEquals(1, modelTensors.get(0).getDataAsMap().size());
        Assert.assertEquals("test output", modelTensors.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void parseResponse_MapResponse() throws IOException {
        HttpConnector connector = createHttpConnector();
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("key1", "value1");
        responseMap.put("key2", "value2");
        List<ModelTensor> modelTensors = new ArrayList<>();

        connector.parseResponse(responseMap, modelTensors, false);
        Assert.assertEquals(1, modelTensors.size());
        Assert.assertEquals("response", modelTensors.get(0).getName());
        Assert.assertEquals(responseMap, modelTensors.get(0).getDataAsMap());
    }

    @Test
    public void fillNullParameters() {
        HttpConnector connector = createHttpConnector();
        Map<String, String> parameters = new HashMap<>();
        String output = connector.fillNullParameters(parameters, "{\"input1\": \"${parameters.input1:-null}\"}");
        Assert.assertEquals("{\"input1\": null}", output);
    }

    public static HttpConnector createHttpConnector() {
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        return createHttpConnectorWithRequestBody(requestBody);
    }

    public static HttpConnector createHttpConnectorWithRequestBody(String requestBody) {
        ConnectorAction action = new ConnectorAction(
            ConnectorAction.ActionType.PREDICT,
            null,
            "POST",
            "https://test.com",
            Map.of("api_key", "${credential.key}"),
            requestBody,
            MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT,
            MLPostProcessFunction.OPENAI_EMBEDDING
        );
        return buildConnector(action);
    }

    /** Creates a connector whose PREDICT action has {@code supports_structured_output: true}. */
    public static HttpConnector createHttpConnectorWithStructuredOutputEnabled(String requestBody) {
        ConnectorAction action = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://test.com")
            .headers(Map.of("api_key", "${credential.key}"))
            .requestBody(requestBody)
            .supportsStructuredOutput(true)
            .build();
        return buildConnector(action);
    }

    private static HttpConnector buildConnector(ConnectorAction action) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test input value");

        Map<String, String> credential = new HashMap<>();
        credential.put("key", "test_key_value");

        ConnectorClientConfig httpClientConfig = new ConnectorClientConfig(30, 30, 30, 10, 10, -1, RetryBackoffPolicy.CONSTANT, null);

        return HttpConnector
            .builder()
            .name("test_connector_name")
            .description("this is a test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(action))
            .backendRoles(Arrays.asList("role1", "role2"))
            .accessMode(AccessMode.PUBLIC)
            .connectorClientConfig(httpClientConfig)
            .build();
    }

    @Test
    public void writeToAndReadFrom_WithTenantId() throws IOException {
        HttpConnector originalConnector = HttpConnector
            .builder()
            .name("test_connector_name")
            .description("this is a test connector")
            .protocol("http")
            .tenantId("test_tenant")
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        originalConnector.writeTo(output);

        HttpConnector deserializedConnector = new HttpConnector(output.bytes().streamInput());
        Assert.assertEquals("test_tenant", deserializedConnector.getTenantId());
        Assert.assertEquals(originalConnector, deserializedConnector);
    }

    @Test
    public void writeToAndReadFrom_WithoutTenantId() throws IOException {
        HttpConnector originalConnector = HttpConnector
            .builder()
            .name("test_connector_name")
            .description("this is a test connector")
            .protocol("http")
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        originalConnector.writeTo(output);

        HttpConnector deserializedConnector = new HttpConnector(output.bytes().streamInput());
        Assert.assertNull(deserializedConnector.getTenantId());
        Assert.assertEquals(originalConnector, deserializedConnector);
    }

    @Test
    public void toXContent_WithTenantId() throws IOException {
        HttpConnector connector = HttpConnector
            .builder()
            .name("test_connector_name")
            .description("this is a test connector")
            .protocol("http")
            .tenantId("test_tenant")
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertTrue(content.contains("\"tenant_id\":\"test_tenant\""));
    }

    @Test
    public void constructor_WithTenantId() {
        HttpConnector connector = HttpConnector
            .builder()
            .name("test_connector_name")
            .description("this is a test connector")
            .protocol("http")
            .tenantId("test_tenant")
            .build();

        Assert.assertEquals("test_tenant", connector.getTenantId());
    }

    @Test
    public void parse_WithTenantId() throws IOException {
        String jsonStr = "{\"name\":\"test_connector_name\",\"protocol\":\"http\",\"tenant_id\":\"test_tenant\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        HttpConnector connector = new HttpConnector("http", parser);
        Assert.assertEquals("test_tenant", connector.getTenantId());
    }

    @Test
    public void testParseResponse_MapResponse() throws IOException {
        HttpConnector connector = createHttpConnector();

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("result", "success");
        responseMap.put("data", Arrays.asList("item1", "item2"));

        List<ModelTensor> modelTensors = new ArrayList<>();
        connector.parseResponse(responseMap, modelTensors, false);

        Assert.assertEquals(1, modelTensors.size());
        Assert.assertEquals("response", modelTensors.get(0).getName());
        Assert.assertEquals(responseMap, modelTensors.get(0).getDataAsMap());
    }

    @Test
    public void testParseResponse_NonStringNonMapResponse() throws IOException {
        HttpConnector connector = createHttpConnector();

        Integer numericResponse = 42;
        List<ModelTensor> modelTensors = new ArrayList<>();
        connector.parseResponse(numericResponse, modelTensors, false);

        Assert.assertEquals(1, modelTensors.size());
        Assert.assertEquals("response", modelTensors.get(0).getName());
        Assert.assertEquals(42, modelTensors.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testFindAction_WithValidActionType() {
        HttpConnector connector = createHttpConnector();
        Optional<ConnectorAction> action = connector.findAction("PREDICT");
        Assert.assertTrue(action.isPresent());
        Assert.assertEquals(PREDICT, action.get().getActionType());
    }

    @Test
    public void testFindAction_WithValidActionTypeCaseInsensitive() {
        HttpConnector connector = createHttpConnector();
        Optional<ConnectorAction> action = connector.findAction("predict");
        Assert.assertTrue(action.isPresent());
        Assert.assertEquals(PREDICT, action.get().getActionType());
    }

    @Test
    public void testFindAction_WithCustomActionName() {
        String customActionName = "custom_action";
        ConnectorAction customAction = new ConnectorAction(
            PREDICT,
            customActionName,
            "POST",
            "https://custom.com",
            null,
            "{\"input\": \"test\"}",
            null,
            null
        );

        HttpConnector connector = HttpConnector
            .builder()
            .name("test_connector")
            .protocol("http")
            .actions(Arrays.asList(customAction))
            .build();

        Optional<ConnectorAction> action = connector.findAction(customActionName);
        Assert.assertTrue(action.isPresent());
        Assert.assertEquals(customActionName, action.get().getName());
        Assert.assertEquals(PREDICT, action.get().getActionType());
    }

    @Test
    public void testFindAction_WithNullAction() {
        HttpConnector connector = createHttpConnector();
        Optional<ConnectorAction> action = connector.findAction(null);
        Assert.assertFalse(action.isPresent());
    }

    @Test
    public void testFindAction_WithInvalidActionType() {
        HttpConnector connector = createHttpConnector();
        Optional<ConnectorAction> action = connector.findAction("INVALID_ACTION");
        Assert.assertFalse(action.isPresent());
    }

    @Test
    public void testFindAction_WithNullActions() {
        HttpConnector connector = HttpConnector.builder().name("test_connector").protocol("http").actions(null).build();
        Optional<ConnectorAction> action = connector.findAction("PREDICT");
        Assert.assertFalse(action.isPresent());
    }

    @Test
    public void testFindAction_CustomNameTakesPrecedenceOverActionType() {
        String customActionName = "my_predict";
        ConnectorAction action1 = new ConnectorAction(
            PREDICT,
            null,
            "POST",
            "https://test1.com",
            null,
            "{\"input\": \"test1\"}",
            null,
            null
        );
        ConnectorAction action2 = new ConnectorAction(
            ConnectorAction.ActionType.EXECUTE,
            customActionName,
            "POST",
            "https://test2.com",
            null,
            "{\"input\": \"test2\"}",
            null,
            null
        );

        HttpConnector connector = HttpConnector
            .builder()
            .name("test_connector")
            .protocol("http")
            .actions(Arrays.asList(action1, action2))
            .build();

        // When searching by valid action type, should find by action type first
        Optional<ConnectorAction> foundByType = connector.findAction("PREDICT");
        Assert.assertTrue(foundByType.isPresent());
        Assert.assertEquals(PREDICT, foundByType.get().getActionType());
        Assert.assertEquals("https://test1.com", foundByType.get().getUrl());

        // When searching by custom name, should find by name
        Optional<ConnectorAction> foundByName = connector.findAction(customActionName);
        Assert.assertTrue(foundByName.isPresent());
        Assert.assertEquals(customActionName, foundByName.get().getName());
        Assert.assertEquals("https://test2.com", foundByName.get().getUrl());
    }

    @Test
    public void createPayload_WithResponseFormatJson_MergesIntoPayload() {
        String requestBody = "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "Hello");
        parameters
            .put(
                "_response_format_json",
                "{\"type\":\"json_schema\",\"json_schema\":{\"name\":\"result\","
                    + "\"schema\":{\"type\":\"object\",\"properties\":{\"facts\":{\"type\":\"array\","
                    + "\"items\":{\"type\":\"string\"}}},\"required\":[\"facts\"]}}}"
            );

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertTrue("response_format should be present", json.has("response_format"));
        Assert.assertEquals("json_schema", json.getAsJsonObject("response_format").get("type").getAsString());
        Assert.assertEquals("Hello", json.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void createPayload_WithoutResponseFormatJson_NoResponseFormatField() {
        String requestBody = "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "Hello");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertFalse("response_format should NOT be present when parameter is absent", json.has("response_format"));
    }

    @Test
    public void createPayload_ResponseFormatJson_IsJsonObjectNotString() {
        String requestBody = "{\"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters.put("_response_format_json", "{\"type\":\"json_object\"}");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertTrue("response_format must be a JSON object, not a string", json.get("response_format").isJsonObject());
        Assert.assertEquals("json_object", json.getAsJsonObject("response_format").get("type").getAsString());
    }

    @Test
    public void createPayload_ResponseFormatJson_InvalidJson_IgnoresParameter() {
        String requestBody = "{\"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters.put("_response_format_json", "not-valid-json");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertFalse("response_format must be absent when _response_format_json is not valid JSON", json.has("response_format"));
    }

    @Test
    public void createPayload_ResponseFormatJson_JsonArray_IgnoresParameter() {
        String requestBody = "{\"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters.put("_response_format_json", "[\"not\", \"an\", \"object\"]");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert
            .assertFalse(
                "response_format must be absent when _response_format_json is a JSON array, not an object",
                json.has("response_format")
            );
    }

    @Test
    public void createPayload_WithDisallowedField_IsNotInjected() {
        String requestBody = "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        // "model" is not in STRUCTURED_OUTPUT_ALLOWED_FIELDS — must be silently ignored
        parameters.put("_model_json", "{\"id\":\"attacker-model\"}");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertEquals("model field must not be overridden by disallowed injection", "gpt-4", json.get("model").getAsString());
    }

    @Test
    public void createPayload_WithAllowedCamelCaseField_IsInjected() {
        String requestBody = "{\"contents\": [{\"role\": \"user\", \"parts\": [{\"text\": \"${parameters.input}\"}]}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        // "generationConfig" is in STRUCTURED_OUTPUT_ALLOWED_FIELDS — camelCase field must be injected
        parameters.put("_generationConfig_json", "{\"responseMimeType\":\"application/json\"}");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertTrue("generationConfig should be present", json.has("generationConfig"));
        Assert.assertTrue("generationConfig must be a JSON object", json.get("generationConfig").isJsonObject());
    }

    @Test
    public void createPayload_WithGenerationConfigAdditionsJson_MergesIntoExistingField() {
        String requestBody = "{\"generationConfig\":{\"maxOutputTokens\":1024},\"contents\":[{\"role\":\"user\","
            + "\"parts\":[{\"text\":\"${parameters.input}\"}]}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters
            .put(
                "_generationConfig_additions_json",
                "{\"responseMimeType\":\"application/json\",\"responseSchema\":{\"type\":\"OBJECT\"}}"
            );

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertTrue("generationConfig should be present", json.has("generationConfig"));
        JsonObject genConfig = json.getAsJsonObject("generationConfig");
        Assert.assertEquals("maxOutputTokens must be preserved", 1024, genConfig.get("maxOutputTokens").getAsInt());
        Assert.assertEquals("responseMimeType must be merged in", "application/json", genConfig.get("responseMimeType").getAsString());
        Assert.assertTrue("responseSchema must be merged in", genConfig.has("responseSchema"));
    }

    @Test
    public void createPayload_WithToolConfigJson_IsInjected() {
        // "toolConfig" is in STRUCTURED_OUTPUT_ALLOWED_FIELDS for Bedrock Converse tool-use structured output
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\"," + "\"text\":\"${parameters.input}\"}]}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters
            .put(
                "_toolConfig_json",
                "{\"tools\":[{\"toolSpec\":{\"name\":\"extract_facts\"}}],\"toolChoice\":{\"tool\":{\"name\":\"extract_facts\"}}}"
            );

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertTrue("toolConfig must be injected for Bedrock Converse tool-use structured output", json.has("toolConfig"));
        Assert.assertTrue("toolConfig must be a JSON object", json.get("toolConfig").isJsonObject());
    }

    @Test
    public void createPayload_WithGenerationConfigAdditionsJson_CreatesFieldIfAbsent() {
        String requestBody = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.input}\"}]}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters.put("_generationConfig_additions_json", "{\"responseMimeType\":\"application/json\"}");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertTrue("generationConfig should be created when absent", json.has("generationConfig"));
        Assert
            .assertEquals(
                "responseMimeType should be set",
                "application/json",
                json.getAsJsonObject("generationConfig").get("responseMimeType").getAsString()
            );
    }

    @Test
    public void createPayload_ArrayPayload_StructuredOutputNotInjected() {
        // Payload is a JSON array, not an object — structured output injection must be skipped
        // without throwing IllegalStateException from getAsJsonObject().
        String requestBody = "[{\"role\":\"user\",\"content\":\"hello\"}]";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("_response_format_json", "{\"type\":\"json_object\"}");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        Assert.assertTrue("Payload must remain a JSON array", payload.startsWith("["));
        Assert.assertFalse("response_format must not be injected into an array payload", payload.contains("response_format"));
    }

    @Test
    public void createPayload_EmptyFieldNameKey_DoesNotThrow() {
        // Keys exactly "_additions_json" or "_json" produce an empty field name after slicing —
        // must be silently ignored, not throw StringIndexOutOfBoundsException.
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters.put("_additions_json", "{\"type\":\"json_object\"}");
        parameters.put("_json", "{\"type\":\"json_object\"}");

        // Must not throw; payload must be returned unchanged since field names are empty
        String payload = connector.createPayload(PREDICT.name(), parameters);
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        Assert.assertFalse("Empty-field-name keys must be ignored", json.has(""));
    }

    @Test
    public void createPayload_BothReplaceAndMergeForSameField_ReplaceTakesPrecedence() {
        // When _response_format_json (replace) and _response_format_additions_json (merge) are both
        // present, the replace pass runs first; the merge then merges into the replaced value.
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"${parameters.input}\"}]}";
        HttpConnector connector = createHttpConnectorWithStructuredOutputEnabled(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test");
        parameters.put("_response_format_json", "{\"type\":\"json_schema\"}");
        parameters.put("_response_format_additions_json", "{\"extra\":\"field\"}");

        String payload = connector.createPayload(PREDICT.name(), parameters);

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        JsonObject rf = json.getAsJsonObject("response_format");
        Assert.assertNotNull("response_format must be present", rf);
        Assert.assertEquals("type from replace pass must be preserved", "json_schema", rf.get("type").getAsString());
        Assert.assertEquals("extra field from merge pass must be added", "field", rf.get("extra").getAsString());
    }

    @Test
    public void testFindAction_MultipleActionsWithSameType() {
        ConnectorAction action1 = new ConnectorAction(
            PREDICT,
            "predict_action_1",
            "POST",
            "https://test1.com",
            null,
            "{\"input\": \"test1\"}",
            null,
            null
        );
        ConnectorAction action2 = new ConnectorAction(
            PREDICT,
            "predict_action_2",
            "POST",
            "https://test2.com",
            null,
            "{\"input\": \"test2\"}",
            null,
            null
        );

        HttpConnector connector = HttpConnector
            .builder()
            .name("test_connector")
            .protocol("http")
            .actions(Arrays.asList(action1, action2))
            .build();

        // Should return the first matching action when searching by type
        Optional<ConnectorAction> foundByType = connector.findAction("PREDICT");
        Assert.assertTrue(foundByType.isPresent());
        Assert.assertEquals("predict_action_1", foundByType.get().getName());

        // Should find specific action by custom name
        Optional<ConnectorAction> foundByName = connector.findAction("predict_action_2");
        Assert.assertTrue(foundByName.isPresent());
        Assert.assertEquals("predict_action_2", foundByName.get().getName());
        Assert.assertEquals("https://test2.com", foundByName.get().getUrl());
    }

    // ---- provisioned_by tests ----

    @Test
    public void builder_WithProvisionedBy() {
        HttpConnector connector = HttpConnector.builder().name("test").protocol("http").provisionedBy("flow-framework").build();
        Assert.assertEquals("flow-framework", connector.getProvisionedBy());
    }

    @Test
    public void builder_WithoutProvisionedBy_DefaultsToNull() {
        HttpConnector connector = HttpConnector.builder().name("test").protocol("http").build();
        Assert.assertNull(connector.getProvisionedBy());
    }

    @Test
    public void toXContent_WithProvisionedBy() throws IOException {
        HttpConnector connector = HttpConnector.builder().name("test").protocol("http").provisionedBy("flow-framework").build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        Assert.assertTrue(TestHelper.xContentBuilderToString(builder).contains("\"provisioned_by\":\"flow-framework\""));
    }

    @Test
    public void toXContent_WithoutProvisionedBy_OmitsField() throws IOException {
        HttpConnector connector = HttpConnector.builder().name("test").protocol("http").build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        Assert.assertFalse(TestHelper.xContentBuilderToString(builder).contains("provisioned_by"));
    }

    @Test
    public void parse_WithProvisionedBy() throws IOException {
        String jsonStr = "{\"name\":\"test\",\"protocol\":\"http\",\"provisioned_by\":\"flow-framework\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        HttpConnector connector = new HttpConnector("http", parser);
        Assert.assertEquals("flow-framework", connector.getProvisionedBy());
    }

    @Test
    public void writeTo_ReadFrom_WithProvisionedBy() throws IOException {
        HttpConnector connector = HttpConnector.builder().name("test").protocol("http").provisionedBy("flow-framework").build();
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_7_0);
        connector.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(CommonValue.VERSION_3_7_0);
        HttpConnector deserialized = new HttpConnector(streamInput);
        Assert.assertEquals("flow-framework", deserialized.getProvisionedBy());
    }

    @Test
    public void writeTo_ReadFrom_ProvisionedBy_OldVersion_IsNull() throws IOException {
        HttpConnector connector = HttpConnector.builder().name("test").protocol("http").provisionedBy("flow-framework").build();
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_5_0);
        connector.writeTo(output);
        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(CommonValue.VERSION_3_5_0);
        HttpConnector deserialized = new HttpConnector(streamInput);
        Assert.assertNull(deserialized.getProvisionedBy());
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_WithRuntimeValue() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-ID", "${parameters.request_id}");
        headers.put("Content-Type", "application/json");

        Map<String, String> runtimeParameters = new HashMap<>();
        runtimeParameters.put("request_id", "req-12345");

        Map<String, String> result = connector.substituteHeadersWithRuntimeParameters(headers, runtimeParameters);

        Assert.assertEquals("req-12345", result.get("X-Request-ID"));
        Assert.assertEquals("application/json", result.get("Content-Type"));
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_NoPlaceholder() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer static-token");

        Map<String, String> runtimeParameters = new HashMap<>();

        Map<String, String> result = connector.substituteHeadersWithRuntimeParameters(headers, runtimeParameters);

        Assert.assertEquals("application/json", result.get("Content-Type"));
        Assert.assertEquals("Bearer static-token", result.get("Authorization"));
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_MultipleHeaders() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-ID", "${parameters.request_id}");
        headers.put("X-User-ID", "${parameters.user_id}");
        headers.put("X-Trace-ID", "${parameters.trace_id}");

        Map<String, String> runtimeParameters = new HashMap<>();
        runtimeParameters.put("request_id", "req-123");
        runtimeParameters.put("user_id", "user-456");
        runtimeParameters.put("trace_id", "trace-789");

        Map<String, String> result = connector.substituteHeadersWithRuntimeParameters(headers, runtimeParameters);

        Assert.assertEquals("req-123", result.get("X-Request-ID"));
        Assert.assertEquals("user-456", result.get("X-User-ID"));
        Assert.assertEquals("trace-789", result.get("X-Trace-ID"));
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_NullHeaders() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> result = connector.substituteHeadersWithRuntimeParameters(null, new HashMap<>());

        Assert.assertNull(result);
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_EmptyHeaders() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> headers = new HashMap<>();
        Map<String, String> result = connector.substituteHeadersWithRuntimeParameters(headers, new HashMap<>());

        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_UnresolvedPlaceholder() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-ID", "${parameters.request_id}");

        Map<String, String> runtimeParameters = new HashMap<>();
        // No request_id provided

        IllegalArgumentException exception = Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> connector.substituteHeadersWithRuntimeParameters(headers, runtimeParameters)
            );

        Assert.assertTrue(exception.getMessage().contains("X-Request-ID"));
        Assert.assertTrue(exception.getMessage().contains("unresolved placeholder"));
    }

    @Test
    public void testSubstituteHeadersWithRuntimeParameters_MixedStaticAndDynamic() {
        HttpConnector connector = createHttpConnector();

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer ${credential.token}");
        headers.put("X-Request-ID", "${parameters.request_id}");
        headers.put("Content-Type", "application/json");

        Map<String, String> runtimeParameters = new HashMap<>();
        runtimeParameters.put("request_id", "req-999");

        Map<String, String> result = connector.substituteHeadersWithRuntimeParameters(headers, runtimeParameters);

        Assert.assertEquals("Bearer ${credential.token}", result.get("Authorization"));
        Assert.assertEquals("req-999", result.get("X-Request-ID"));
        Assert.assertEquals("application/json", result.get("Content-Type"));
    }

    @Test
    public void testValidateConnectorHeaders_ValidHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Request-ID", "${parameters.request_id}");

        AbstractConnector.validateConnectorHeaders(headers, "http");
    }

    @Test
    public void testValidateConnectorHeaders_NullHeaders() {
        AbstractConnector.validateConnectorHeaders(null, "http");
    }

    @Test
    public void testValidateConnectorHeaders_EmptyHeaders() {
        AbstractConnector.validateConnectorHeaders(new HashMap<>(), "http");
    }

    @Test
    public void testValidateConnectorHeaders_AuthorizationWithParameters() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer ${parameters.token}");

        IllegalArgumentException exception = Assert
            .assertThrows(IllegalArgumentException.class, () -> AbstractConnector.validateConnectorHeaders(headers, "http"));
        Assert.assertTrue(exception.getMessage().contains("Authorization"));
    }

    @Test
    public void testValidateConnectorHeaders_AuthorizationWithCredential() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer ${credential.secretArn.token}");

        AbstractConnector.validateConnectorHeaders(headers, "http");
    }

    @Test
    public void testValidateConnectorHeaders_CookieWithParameters() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "session=${parameters.session_id}");

        IllegalArgumentException exception = Assert
            .assertThrows(IllegalArgumentException.class, () -> AbstractConnector.validateConnectorHeaders(headers, "http"));
        Assert.assertTrue(exception.getMessage().contains("Cookie"));
    }

    @Test
    public void testValidateConnectorHeaders_HostWithParameters() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "${parameters.host}");

        IllegalArgumentException exception = Assert
            .assertThrows(IllegalArgumentException.class, () -> AbstractConnector.validateConnectorHeaders(headers, "http"));
        Assert.assertTrue(exception.getMessage().contains("Host"));
    }

    @Test
    public void testValidateConnectorHeaders_CaseInsensitive() {
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer ${parameters.token}");

        IllegalArgumentException exception = Assert
            .assertThrows(IllegalArgumentException.class, () -> AbstractConnector.validateConnectorHeaders(headers, "http"));
        Assert.assertTrue(exception.getMessage().contains("cannot use ${parameters.*} placeholders"));
    }

    @Test
    public void testValidateConnectorHeaders_CustomTracingHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-Id", "${parameters.request_id}");
        headers.put("intuit_tid", "${parameters.intuit_tid}");

        AbstractConnector.validateConnectorHeaders(headers, "http");
    }

    @Test
    public void testValidateConnectorHeaders_McpSseWithParameters() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Trace-Id", "${parameters.trace_id}");

        IllegalArgumentException exception = Assert
            .assertThrows(IllegalArgumentException.class, () -> AbstractConnector.validateConnectorHeaders(headers, "mcp_sse"));
        Assert.assertTrue(exception.getMessage().contains("MCP connectors"));
    }

    @Test
    public void testValidateConnectorHeaders_McpWithStaticHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Trace-Id", "static-trace-id");
        headers.put("Authorization", "Bearer ${credential.secretArn.token}");

        AbstractConnector.validateConnectorHeaders(headers, "mcp_sse");
    }
}
