/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;
import static org.opensearch.ml.common.connector.RetryBackoffPolicy.CONSTANT;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.search.SearchModule;

public class McpStreamableHttpConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    BiFunction<String, String, String> encryptFunction;
    BiFunction<String, String, String> decryptFunction;

    String TEST_CONNECTOR_JSON_STRING =
        "{\"name\":\"test_mcp_streamable_http_connector_name\",\"version\":\"1\",\"description\":\"this is a test mcp streamable http connector\",\"protocol\":\"mcp_streamable_http\",\"credential\":{\"key\":\"test_key_value\"},\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\",\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000,\"read_timeout\":30000,\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"},\"url\":\"https://test.com\",\"headers\":{\"api_key\":\"${credential.key}\"},\"parameters\":{\"endpoint\":\"/custom/endpoint\"}}";

    @Before
    public void setUp() {
        encryptFunction = (credential, tenantId) -> "encrypted_" + credential;
        decryptFunction = (credential, tenantId) -> credential.replace("encrypted_", "");
    }

    @Test
    public void testMcpStreamableHttpConnector_Builder() {
        exceptionRule.expectMessage("Unsupported connector protocol. Please use one of [aws_sigv4, http, mcp_sse, mcp_streamable_http]");
        McpStreamableHttpConnector.builder().protocol("wrong protocol").build();
    }

    @Test
    public void testMcpStreamableHttpConnector_StreamInput() throws IOException {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        BytesStreamOutput output = new BytesStreamOutput();
        connector.writeTo(output);
        McpStreamableHttpConnector connector2 = new McpStreamableHttpConnector(output.bytes().streamInput());
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void testMcpStreamableHttpConnector_ToXContent() throws IOException {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String connectorStr = TestHelper.xContentBuilderToString(builder);
        Assert.assertFalse(connectorStr.contains("encrypted_"));
    }

    @Test
    public void testMcpStreamableHttpConnector_Parse() throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                TEST_CONNECTOR_JSON_STRING
            );
        parser.nextToken();
        
        McpStreamableHttpConnector connector = new McpStreamableHttpConnector("mcp_streamable_http", parser);
        Assert.assertEquals("test_mcp_streamable_http_connector_name", connector.getName());
        Assert.assertEquals("mcp_streamable_http", connector.getProtocol());
    }

    @Test
    public void testMcpStreamableHttpConnector_Encrypt() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        connector.encrypt(encryptFunction, "test_tenant");
        Assert.assertEquals("encrypted_test_key_value", connector.getCredential().get("key"));
    }

    @Test
    public void testMcpStreamableHttpConnector_Decrypt() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        connector.encrypt(encryptFunction, "test_tenant");
        connector.decrypt("", decryptFunction, "test_tenant");
        Assert.assertEquals("test_key_value", connector.getDecryptedCredential().get("key"));
        Assert.assertEquals("test_key_value", connector.getDecryptedHeaders().get("api_key"));
    }

    @Test
    public void testMcpStreamableHttpConnector_CloneConnector() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        Connector clonedConnector = connector.cloneConnector();
        Assert.assertEquals(connector, clonedConnector);
    }

    @Test
    public void testMcpStreamableHttpConnector_RemoveCredential() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        connector.removeCredential();
        Assert.assertNull(connector.getCredential());
        Assert.assertNull(connector.getDecryptedCredential());
        Assert.assertNull(connector.getDecryptedHeaders());
    }

    @Test
    public void testMcpStreamableHttpConnector_Update() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        Map<String, String> updatedCredential = new HashMap<>();
        updatedCredential.put("new_key", "new_value");
        MLCreateConnectorInput updateInput = MLCreateConnectorInput.builder()
            .name("updated_name")
            .description("updated_description")
            .version("2")
            .protocol(MCP_STREAMABLE_HTTP)
            .credential(updatedCredential)
            .build();
        connector.update(updateInput, encryptFunction);
        Assert.assertEquals("updated_name", connector.getName());
        Assert.assertEquals("updated_description", connector.getDescription());
    }

    @Test
    public void testMcpStreamableHttpConnector_ValidateConnectorURL() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        List<String> urlRegexes = Arrays.asList(".*test\\.com.*");
        connector.validateConnectorURL(urlRegexes);
    }

    @Test
    public void testMcpStreamableHttpConnector_ValidateConnectorURL_Invalid() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        List<String> urlRegexes = Arrays.asList(".*invalid\\.com.*");
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        connector.validateConnectorURL(urlRegexes);
    }

    @Test
    public void testMcpStreamableHttpConnector_GetParameters() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        Map<String, String> parameters = connector.getParameters();
        Assert.assertNotNull(parameters);
        Assert.assertEquals("/custom/endpoint", parameters.get("endpoint"));
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.getActions();
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations_AddAction() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.addAction(null);
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations_GetActionEndpoint() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.getActionEndpoint("test", Collections.emptyMap());
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations_GetActionHttpMethod() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.getActionHttpMethod("test");
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations_CreatePayload() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.createPayload("test", Collections.emptyMap());
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations_FindAction() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.findAction("test");
    }

    @Test
    public void testMcpStreamableHttpConnector_UnsupportedOperations_ParseResponse() throws IOException {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        
        exceptionRule.expect(UnsupportedOperationException.class);
        exceptionRule.expectMessage("Not implemented.");
        connector.parseResponse(null, Collections.emptyList(), false);
    }

    public static McpStreamableHttpConnector createMcpStreamableHttpConnector() {
        Map<String, String> credential = new HashMap<>();
        credential.put("key", "test_key_value");

        List<String> backendRoles = Arrays.asList("role1", "role2");

        ConnectorClientConfig clientConfig = ConnectorClientConfig.builder()
            .maxConnections(30)
            .connectionTimeout(30000)
            .readTimeout(30000)
            .retryBackoffMillis(10)
            .retryTimeoutSeconds(10)
            .maxRetryTimes(-1)
            .retryBackoffPolicy(CONSTANT)
            .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("endpoint", "/custom/endpoint");

        return McpStreamableHttpConnector
            .builder()
            .name("test_mcp_streamable_http_connector_name")
            .version("1")
            .description("this is a test mcp streamable http connector")
            .protocol(MCP_STREAMABLE_HTTP)
            .credential(credential)
            .backendRoles(backendRoles)
            .accessMode(AccessMode.PUBLIC)
            .connectorClientConfig(clientConfig)
            .url("https://test.com")
            .headers(headers)
            .parameters(parameters)
            .build();
    }
}