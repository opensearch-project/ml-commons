/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MLCreateConnectorInputTests {
    private MLCreateConnectorInput mlCreateConnectorInput;
    private MLCreateConnectorInput mlCreateDryRunConnectorInput;

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();
    private final String expectedInputStr = "{\"name\":\"test_connector_name\"," +
            "\"description\":\"this is a test connector\",\"version\":\"1\",\"protocol\":\"http\"," +
            "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"}," +
            "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\"," +
            "\"headers\":{\"api_key\":\"${credential.key}\"}," +
            "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"," +
            "\"pre_process_function\":\"connector.pre_process.openai.embedding\"," +
            "\"post_process_function\":\"connector.post_process.openai.embedding\"}]," +
            "\"backend_roles\":[\"role1\",\"role2\"],\"add_all_backend_roles\":false," +
            "\"access_mode\":\"PUBLIC\",\"client_config\":{\"max_connection\":20," +
            "\"connection_timeout\":10000,\"read_timeout\":10000," +
            "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}}";

    @Before
    public void setUp(){
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String mlCreateConnectorRequestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;
        ConnectorAction action = new ConnectorAction(actionType, method, url, headers, mlCreateConnectorRequestBody, preProcessFunction, postProcessFunction);
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(20, 10000, 10000, 10, 10, -1, RetryBackoffPolicy.CONSTANT);

        mlCreateConnectorInput = MLCreateConnectorInput.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version("1")
                .protocol("http")
                .parameters(Map.of("input", "test input value"))
                .credential(Map.of("key", "test_key_value"))
                .actions(List.of(action))
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList("role1", "role2"))
                .addAllBackendRoles(false)
                .connectorClientConfig(connectorClientConfig)
                .build();

        mlCreateDryRunConnectorInput = MLCreateConnectorInput.builder()
                .dryRun(true)
                .build();
    }

    @Test
    public void constructorMLCreateConnectorInput_NullName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector name is null");
        MLCreateConnectorInput.builder()
                .name(null)
                .description("this is a test connector")
                .version("1")
                .protocol("http")
                .parameters(Map.of("input", "test input value"))
                .credential(Map.of("key", "test_key_value"))
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList("role1", "role2"))
                .addAllBackendRoles(false)
                .build();
    }

    @Test
    public void constructorMLCreateConnectorInput_NullVersion() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector version is null");
        MLCreateConnectorInput.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version(null)
                .protocol("http")
                .parameters(Map.of("input", "test input value"))
                .credential(Map.of("key", "test_key_value"))
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList("role1", "role2"))
                .addAllBackendRoles(false)
                .build();
    }

    @Test
    public void constructorMLCreateConnectorInput_NullProtocol() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector protocol is null");
        MLCreateConnectorInput.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version("1")
                .protocol(null)
                .parameters(Map.of("input", "test input value"))
                .credential(Map.of("key", "test_key_value"))
                .actions(List.of())
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList("role1", "role2"))
                .addAllBackendRoles(false)
                .build();
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
            assertEquals("test_connector_name", parsedInput.getName());
            assertEquals(20, parsedInput.getConnectorClientConfig().getMaxConnections().intValue());
            assertEquals(10000, parsedInput.getConnectorClientConfig().getReadTimeout().intValue());
            assertEquals(10000, parsedInput.getConnectorClientConfig().getConnectionTimeout().intValue());
        });
    }

    @Test
    public void testParse_ArrayParameter() throws Exception {
        String expectedInputStr = "{\"name\":\"test_connector_name\"," +
                                  "\"description\":\"this is a test connector\",\"version\":\"1\",\"protocol\":\"http\"," +
                                  "\"parameters\":{\"input\":[\"test input value\"]},\"credential\":{\"key\":\"test_key_value\"}," +
                                  "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\"," +
                                  "\"headers\":{\"api_key\":\"${credential.key}\"}," +
                                  "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"," +
                                  "\"pre_process_function\":\"connector.pre_process.openai.embedding\"," +
                                  "\"post_process_function\":\"connector.post_process.openai.embedding\"}]," +
                                  "\"backend_roles\":[\"role1\",\"role2\"],\"add_all_backend_roles\":false," +
                                  "\"access_mode\":\"PUBLIC\"}";
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertEquals("test_connector_name", parsedInput.getName());
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
        MLCreateConnectorInput mlCreateMinimalConnectorInput = MLCreateConnectorInput.builder()
                .name("test_connector_name")
                .version("1")
                .protocol("http")
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
        MLCreateConnectorInput input = MLCreateConnectorInput.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version("1")
                .protocol("http")
                .parameters(Map.of("input", "test input value"))
                .credential(Map.of("key", "test_key_value"))
                .actions(null) // Setting actions to null
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList("role1", "role2"))
                .addAllBackendRoles(false)
                .build();

        assertNull(input.getActions());
    }

    @Test
    public void testParse_MissingNameField_ShouldThrowException() throws IOException {
        String jsonMissingName = "{\"description\":\"this is a test connector\",\"version\":\"1\",\"protocol\":\"http\"}";
        XContentParser parser = createParser(jsonMissingName);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector name is null");

        MLCreateConnectorInput.parse(parser);
    }

    @Test
    public void testWriteToVersionCompatibility() throws IOException {
        MLCreateConnectorInput input = mlCreateConnectorInput; // Assuming mlCreateConnectorInput is already initialized

        // Simulate an older version of OpenSearch that does not support connectorClientConfig
        Version oldVersion = Version.fromString("2.12.0"); // Change this as per your old version
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
        MLCreateConnectorInput input = MLCreateConnectorInput.builder()
                .dryRun(true) // Set dryRun to true
                .build();

        // No exception for missing mandatory fields when dryRun is true
        assertTrue(input.isDryRun());
        assertNull(input.getName()); // Name is not set, but no exception due to dryRun
    }

    // Helper method to create XContentParser from a JSON string
    private XContentParser createParser(String jsonString) throws IOException {
        XContentParser parser = XContentType.JSON.xContent().createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonString);
        parser.nextToken(); // Move to the first token
        return parser;
    }

    private void testParseFromJsonString(String expectedInputString, Consumer<MLCreateConnectorInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputString);
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
