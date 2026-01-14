/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_SSE;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.search.SearchModule;

public class MLCreateConnectorInputTests {
    private MLCreateConnectorInput mlCreateConnectorInput;
    private MLCreateConnectorInput mlCreateDryRunConnectorInput;

    private static final String TEST_CONNECTOR_NAME = "test_connector_name";
    private static final String TEST_CONNECTOR_DESCRIPTION = "this is a test connector";
    private static final String TEST_CONNECTOR_VERSION = "1";
    private static final String TEST_CONNECTOR_PROTOCOL = "http";
    private static final String TEST_PARAM_KEY = "input";
    private static final String TEST_PARAM_VALUE = "test input value";
    private static final String TEST_CREDENTIAL_KEY = "key";
    private static final String TEST_CREDENTIAL_VALUE = "test_key_value";
    private static final String TEST_ROLE1 = "role1";
    private static final String TEST_ROLE2 = "role2";
    private final String expectedInputStr = "{\"name\":\"test_connector_name\","
        + "\"description\":\"this is a test connector\",\"version\":\"1\",\"protocol\":\"http\","
        + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
        + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
        + "\"headers\":{\"api_key\":\"${credential.key}\"},"
        + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
        + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
        + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
        + "\"backend_roles\":[\"role1\",\"role2\"],\"add_all_backend_roles\":false,"
        + "\"access_mode\":\"PUBLIC\",\"client_config\":{\"max_connection\":20,"
        + "\"connection_timeout\":10000,\"read_timeout\":10000,"
        + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}}";

    @Before
    public void setUp() {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String mlCreateConnectorRequestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;
        ConnectorAction action = new ConnectorAction(
            actionType,
            method,
            url,
            headers,
            mlCreateConnectorRequestBody,
            preProcessFunction,
            postProcessFunction
        );
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(20, 10000, 10000, 10, 10, -1, RetryBackoffPolicy.CONSTANT);

        mlCreateConnectorInput = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .description(TEST_CONNECTOR_DESCRIPTION)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(TEST_CONNECTOR_PROTOCOL)
            .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
            .credential(Map.of(TEST_CREDENTIAL_KEY, TEST_CREDENTIAL_VALUE))
            .actions(List.of(action))
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
            .addAllBackendRoles(false)
            .connectorClientConfig(connectorClientConfig)
            .build();

        mlCreateDryRunConnectorInput = MLCreateConnectorInput.builder().dryRun(true).build();
    }

    @Test
    public void constructorMLCreateConnectorInput_NullName() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            MLCreateConnectorInput
                .builder()
                .name(null)
                .description(TEST_CONNECTOR_DESCRIPTION)
                .version(TEST_CONNECTOR_VERSION)
                .protocol(TEST_CONNECTOR_PROTOCOL)
                .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
                .credential(Map.of(TEST_CREDENTIAL_KEY, TEST_CREDENTIAL_VALUE))
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
                .addAllBackendRoles(false)
                .build();
        });
        assertEquals("Connector name is null", exception.getMessage());
    }

    @Test
    public void constructorMLCreateConnectorInput_NullVersion() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            MLCreateConnectorInput
                .builder()
                .name(TEST_CONNECTOR_NAME)
                .description(TEST_CONNECTOR_DESCRIPTION)
                .version(null)
                .protocol(TEST_CONNECTOR_PROTOCOL)
                .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
                .credential(Map.of(TEST_CREDENTIAL_KEY, TEST_CREDENTIAL_VALUE))
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
                .addAllBackendRoles(false)
                .build();
        });
        assertEquals("Connector version is null", exception.getMessage());
    }

    @Test
    public void constructorMLCreateConnectorInput_NullProtocol() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            MLCreateConnectorInput
                .builder()
                .name(TEST_CONNECTOR_NAME)
                .description(TEST_CONNECTOR_DESCRIPTION)
                .version(TEST_CONNECTOR_VERSION)
                .protocol(null)
                .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
                .credential(Map.of(TEST_CREDENTIAL_KEY, TEST_CREDENTIAL_VALUE))
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
                .addAllBackendRoles(false)
                .build();
        });
        assertEquals("Connector protocol is null", exception.getMessage());
    }

    @Test
    public void constructorMLCreateConnectorInput_NullCredential() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            MLCreateConnectorInput
                .builder()
                .name(TEST_CONNECTOR_NAME)
                .description(TEST_CONNECTOR_DESCRIPTION)
                .version(TEST_CONNECTOR_VERSION)
                .protocol(TEST_CONNECTOR_PROTOCOL)
                .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
                .credential(null)
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
                .addAllBackendRoles(false)
                .build();
        });
        assertEquals("Connector credential is null or empty list", exception.getMessage());
    }

    @Test
    public void constructorMLCreateConnectorInput_EmptyCredential() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            MLCreateConnectorInput
                .builder()
                .name(TEST_CONNECTOR_NAME)
                .description(TEST_CONNECTOR_DESCRIPTION)
                .version(TEST_CONNECTOR_VERSION)
                .protocol(TEST_CONNECTOR_PROTOCOL)
                .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
                .credential(Map.of())
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
                .addAllBackendRoles(false)
                .build();
        });
        assertEquals("Connector credential is null or empty list", exception.getMessage());
    }

    @Test
    public void constructorMLCreateConnectorInput_McpSseWithNullCredential_ShouldNotThrow() {
        // MCP SSE connectors should be allowed to have null credentials
        MLCreateConnectorInput connector = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .description(TEST_CONNECTOR_DESCRIPTION)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(MCP_SSE)
            .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
            .credential(null)
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
            .addAllBackendRoles(false)
            .url("https://test.com") // Add valid URL for MCP connector
            .build();

        assertNotNull(connector);
        assertEquals(MCP_SSE, connector.getProtocol());
        assertNull(connector.getCredential());
    }

    @Test
    public void constructorMLCreateConnectorInput_McpSseWithEmptyCredential_ShouldNotThrow() {
        // MCP SSE connectors should be allowed to have empty credentials
        MLCreateConnectorInput connector = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .description(TEST_CONNECTOR_DESCRIPTION)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(MCP_SSE)
            .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
            .credential(Map.of())
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
            .addAllBackendRoles(false)
            .url("https://test.com") // Add valid URL for MCP connector
            .build();

        assertNotNull(connector);
        assertEquals(MCP_SSE, connector.getProtocol());
        assertTrue(connector.getCredential().isEmpty());
    }

    @Test
    public void constructorMLCreateConnectorInput_McpStreamableHttpWithNullCredential_ShouldNotThrow() {
        // MCP Streamable HTTP connectors should be allowed to have null credentials
        MLCreateConnectorInput connector = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .description(TEST_CONNECTOR_DESCRIPTION)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(MCP_STREAMABLE_HTTP)
            .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
            .credential(null)
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
            .addAllBackendRoles(false)
            .url("https://test.com") // Add valid URL for MCP connector
            .build();

        assertNotNull(connector);
        assertEquals(MCP_STREAMABLE_HTTP, connector.getProtocol());
        assertNull(connector.getCredential());
    }

    @Test
    public void constructorMLCreateConnectorInput_McpStreamableHttpWithEmptyCredential_ShouldNotThrow() {
        // MCP Streamable HTTP connectors should be allowed to have empty credentials
        MLCreateConnectorInput connector = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .description(TEST_CONNECTOR_DESCRIPTION)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(MCP_STREAMABLE_HTTP)
            .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
            .credential(Map.of())
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
            .addAllBackendRoles(false)
            .url("https://test.com") // Add valid URL for MCP connector
            .build();

        assertNotNull(connector);
        assertEquals(MCP_STREAMABLE_HTTP, connector.getProtocol());
        assertTrue(connector.getCredential().isEmpty());
    }

    @Test
    public void testToXContent_FullFields() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        mlCreateConnectorInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testToXContent_NullFields() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        mlCreateDryRunConnectorInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{}", jsonStr);
    }

    @Test
    public void testParse() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertEquals(TEST_CONNECTOR_NAME, parsedInput.getName());
            assertEquals(20, parsedInput.getConnectorClientConfig().getMaxConnections().intValue());
            assertEquals(10000, parsedInput.getConnectorClientConfig().getReadTimeoutMillis().intValue());
            assertEquals(10000, parsedInput.getConnectorClientConfig().getConnectionTimeoutMillis().intValue());
        });
    }

    @Test
    public void testParse_ArrayParameter() throws Exception {
        String expectedInputStr = "{\"name\":\"test_connector_name\","
            + "\"description\":\"this is a test connector\",\"version\":\"1\",\"protocol\":\"http\","
            + "\"parameters\":{\"input\":[\"test input value\"]},\"credential\":{\"key\":\"test_key_value\"},"
            + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
            + "\"headers\":{\"api_key\":\"${credential.key}\"},"
            + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
            + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
            + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
            + "\"backend_roles\":[\"role1\",\"role2\"],\"add_all_backend_roles\":false,"
            + "\"access_mode\":\"PUBLIC\"}";
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertEquals(TEST_CONNECTOR_NAME, parsedInput.getName());
            assertEquals(1, parsedInput.getParameters().size());
            assertEquals("[\"test input value\"]", parsedInput.getParameters().get("input"));
        });
    }

    @Test
    public void testParseWithDryRun() throws Exception {
        String expectedInputStrWithDryRun = "{\"dry_run\":true}";
        testParseFromJsonString(expectedInputStrWithDryRun, parsedInput -> {
            assertNull(parsedInput.getName());
            assertTrue(parsedInput.isDryRun());
        });
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(mlCreateConnectorInput, parsedInput -> assertEquals(mlCreateConnectorInput.getName(), parsedInput.getName()));
    }

    @Test
    public void readInputStream_SuccessWithNullFields() throws IOException {
        MLCreateConnectorInput mlCreateMinimalConnectorInput = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(TEST_CONNECTOR_PROTOCOL)
            .credential(Map.of(TEST_CREDENTIAL_KEY, TEST_CREDENTIAL_VALUE))
            .build();
        readInputStream(mlCreateMinimalConnectorInput, parsedInput -> {
            assertEquals(mlCreateMinimalConnectorInput.getName(), parsedInput.getName());
            assertNull(parsedInput.getActions());
            assertNull(parsedInput.getConnectorClientConfig());
        });
    }

    @Test
    public void testBuilder_NullActions_ShouldNotThrowException() {
        // Actions can be null for a connector without any specific actions defined.
        MLCreateConnectorInput input = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .description(TEST_CONNECTOR_DESCRIPTION)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(TEST_CONNECTOR_PROTOCOL)
            .parameters(Map.of(TEST_PARAM_KEY, TEST_PARAM_VALUE))
            .credential(Map.of(TEST_CREDENTIAL_KEY, TEST_CREDENTIAL_VALUE))
            .actions(null) // Setting actions to null
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList(TEST_ROLE1, TEST_ROLE2))
            .addAllBackendRoles(false)
            .build();

        assertNull(input.getActions());
    }

    @Test
    public void testParse_MissingNameField_ShouldThrowException() throws IOException {
        String jsonMissingName = "{\"description\":\"this is a test connector\",\"version\":\"1\",\"protocol\":\"http\"}";
        XContentParser parser = createParser(jsonMissingName);

        Throwable exception = assertThrows(IllegalArgumentException.class, () -> { MLCreateConnectorInput.parse(parser); });
        assertEquals("Connector name is null", exception.getMessage());
    }

    @Test
    public void testParseWithTenantId() throws Exception {
        String inputWithTenantId =
            "{\"name\":\"test_connector_name\",\"credential\":{\"key\":\"test_key_value\"},\"version\":\"1\",\"protocol\":\"http\",\"tenant_id\":\"test_tenant\"}";
        testParseFromJsonString(inputWithTenantId, parsedInput -> {
            assertEquals("test_connector_name", parsedInput.getName());
            assertEquals("test_tenant", parsedInput.getTenantId());
        });
    }

    @Test
    public void testParseWithUnknownFields() throws Exception {
        String inputWithUnknownFields =
            "{\"name\":\"test_connector_name\",\"credential\":{\"key\":\"test_key_value\"},\"version\":\"1\",\"protocol\":\"http\",\"unknown_field\":\"unknown_value\"}";
        testParseFromJsonString(inputWithUnknownFields, parsedInput -> {
            assertEquals("test_connector_name", parsedInput.getName());
            assertNull(parsedInput.getTenantId());
        });
    }

    @Test
    public void testParseWithEmptyActions() throws Exception {
        String inputWithEmptyActions =
            "{\"name\":\"test_connector_name\",\"credential\":{\"key\":\"test_key_value\"},\"version\":\"1\",\"protocol\":\"http\",\"actions\":[]}";
        testParseFromJsonString(inputWithEmptyActions, parsedInput -> {
            assertEquals("test_connector_name", parsedInput.getName());
            assertTrue(parsedInput.getActions().isEmpty());
        });
    }

    @Test
    public void testWriteToVersionCompatibility() throws IOException {
        MLCreateConnectorInput input = mlCreateConnectorInput; // Assuming mlCreateConnectorInput is already initialized

        // Simulate an older version of OpenSearch that does not support connectorClientConfig
        Version oldVersion = CommonValue.VERSION_2_12_0; // Change this as per your old version
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(oldVersion);

        input.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        streamInput.setVersion(oldVersion);

        MLCreateConnectorInput deserializedInput = new MLCreateConnectorInput(streamInput);

        // The ConnectorClientConfig should be null as it's not supported in the older version
        assertNull(deserializedInput.getConnectorClientConfig());
    }

    @Test
    public void testDryRunConnectorInput_IgnoreValidation() {
        MLCreateConnectorInput input = MLCreateConnectorInput
            .builder()
            .dryRun(true) // Set dryRun to true
            .build();

        // No exception for missing mandatory fields when dryRun is true
        assertTrue(input.isDryRun());
        assertNull(input.getName()); // Name is not set, but no exception due to dryRun
    }

    @Test
    public void constructorMLCreateConnectorInput_McpSseWithNullUrl_ShouldThrowException() {
        testMcpUrlValidation(MCP_SSE, null);
    }

    @Test
    public void constructorMLCreateConnectorInput_McpSseWithBlankUrl_ShouldThrowException() {
        testMcpUrlValidation(MCP_SSE, "   ");
    }

    @Test
    public void constructorMLCreateConnectorInput_McpStreamableHttpWithNullUrl_ShouldThrowException() {
        testMcpUrlValidation(MCP_STREAMABLE_HTTP, null);
    }

    @Test
    public void constructorMLCreateConnectorInput_McpStreamableHttpWithBlankUrl_ShouldThrowException() {
        testMcpUrlValidation(MCP_STREAMABLE_HTTP, "   ");
    }

    @Test
    public void constructorMLCreateConnectorInput_McpSseWithValidUrl_ShouldNotThrowException() {
        testMcpValidUrl(MCP_SSE);
    }

    @Test
    public void constructorMLCreateConnectorInput_McpStreamableHttpWithValidUrl_ShouldNotThrowException() {
        testMcpValidUrl(MCP_STREAMABLE_HTTP);
    }

    private void testMcpUrlValidation(String protocol, String url) {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            MLCreateConnectorInput
                .builder()
                .name(TEST_CONNECTOR_NAME)
                .version(TEST_CONNECTOR_VERSION)
                .protocol(protocol)
                .credential(null) // MCP connectors allow null credential
                .url(url)
                .build();
        });
        assertEquals("MCP Connector url is null or blank", exception.getMessage());
    }

    private void testMcpValidUrl(String protocol) {
        MLCreateConnectorInput connector = MLCreateConnectorInput
            .builder()
            .name(TEST_CONNECTOR_NAME)
            .version(TEST_CONNECTOR_VERSION)
            .protocol(protocol)
            .credential(null)
            .url("https://test.com")
            .build();

        assertNotNull(connector);
        assertEquals(protocol, connector.getProtocol());
        assertEquals("https://test.com", connector.getUrl());
    }

    // Helper method to create XContentParser from a JSON string
    private XContentParser createParser(String jsonString) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonString
            );
        parser.nextToken(); // Move to the first token
        return parser;
    }

    private void testParseFromJsonString(String expectedInputString, Consumer<MLCreateConnectorInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputString
            );
        parser.nextToken();
        MLCreateConnectorInput parsedInput = MLCreateConnectorInput.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLCreateConnectorInput input, Consumer<MLCreateConnectorInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateConnectorInput parsedInput = new MLCreateConnectorInput(streamInput);
        verify.accept(parsedInput);
    }

}
