/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.TriConsumer;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.search.SearchModule;

public class HttpConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    TriConsumer<String, String, ActionListener<String>> encryptFunction;
    TriConsumer<String, String, ActionListener<String>> decryptFunction;

    String TEST_CONNECTOR_JSON_STRING = "{\"name\":\"test_connector_name\",\"version\":\"1\","
        + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
        + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
        + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
        + "\"headers\":{\"api_key\":\"${credential.key}\"},"
        + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
        + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
        + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
        + "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\","
        + "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000,\"read_timeout\":30000,"
        + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}}";

    @Before
    public void setUp() {
        encryptFunction = (s, v, l) -> l.onResponse("encrypted: " + s.toLowerCase(Locale.ROOT));
        decryptFunction = (s, v, l) -> l.onResponse("decrypted: " + s.toUpperCase(Locale.ROOT));
    }

    @Test
    public void constructor_InvalidProtocol() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported connector protocol. Please use one of [aws_sigv4, http, mcp_sse, mcp_streamable_http]");

        HttpConnector.builder().protocol("wrong protocol").build();
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
        connector.decrypt(PREDICT.name(), decryptFunction, null);
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
    public void decrypt_Throws_Exception() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Exception during decrypting credentials");
        TriConsumer<String, String, ActionListener<String>> decryptErrorFunction = (s, v, l) -> l
            .onFailure(new RuntimeException("Exception during decrypting credentials"));
        HttpConnector connector = createHttpConnector();
        connector.decrypt(PREDICT.name(), decryptErrorFunction, null);
    }

    @Test
    public void encrypted() {
        HttpConnector connector = createHttpConnector();
        connector.encrypt(encryptFunction, null);
        Map<String, String> credential = connector.getCredential();
        Assert.assertEquals(1, credential.size());
        Assert.assertEquals("encrypted: test_key_value", credential.get("key"));

        connector.removeCredential();
        Assert.assertNull(connector.getCredential());
        Assert.assertNull(connector.getDecryptedCredential());
        Assert.assertNull(connector.getDecryptedHeaders());
    }

    @Test
    public void encrypt_Throws_Exception() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Exception during encrypting credentials");
        TriConsumer<String, String, ActionListener<String>> encryptErrorFunction = (s, v, l) -> l
            .onFailure(new RuntimeException("Exception during encrypting credentials"));
        HttpConnector connector = createHttpConnector();
        connector.encrypt(encryptErrorFunction, null);
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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Some parameter placeholder not filled in payload: input");
        HttpConnector connector = createHttpConnector();
        String predictPayload = connector.createPayload(PREDICT.name(), null);
        connector.validatePayload(predictPayload);
    }

    @Test
    public void createPayload_InvalidJson() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Invalid payload: {\"input\": ${parameters.input} }");
        String requestBody = "{\"input\": ${parameters.input} }";
        HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);
        String predictPayload = connector.createPayload(PREDICT.name(), null);
        connector.validatePayload(predictPayload);
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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage(
                "Invalid payload: {\"input\": \"test value\", \"parameters\": {\"sparseEmbeddingFormat\": \"WORD\", \"content_type\": ${parameters.content_type} }}"
            );
        String requestBody =
            "{\"input\": \"${parameters.input}\", \"parameters\": {\"sparseEmbeddingFormat\": \"${parameters.sparseEmbeddingFormat}\", \"content_type\": ${parameters.content_type} }}";
        HttpConnector connector = createHttpConnectorWithRequestBody(requestBody);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test value");
        parameters.put("sparseEmbeddingFormat", "WORD");
        String predictPayload = connector.createPayload(PREDICT.name(), parameters);
        connector.validatePayload(predictPayload);
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
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(
            actionType,
            method,
            url,
            headers,
            requestBody,
            preProcessFunction,
            postProcessFunction
        );

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test input value");

        Map<String, String> credential = new HashMap<>();
        credential.put("key", "test_key_value");

        ConnectorClientConfig httpClientConfig = new ConnectorClientConfig(30, 30000, 30000, 10, 10, -1, RetryBackoffPolicy.CONSTANT);

        HttpConnector connector = HttpConnector
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
        return connector;
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

}
