/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;
import static org.opensearch.ml.common.connector.RetryBackoffPolicy.CONSTANT;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        encryptFunction = (s, v) -> "encrypted: " + s.toLowerCase(Locale.ROOT);
        decryptFunction = (s, v) -> "decrypted: " + s.toUpperCase(Locale.ROOT);
    }

    @Test
    public void constructor_InvalidProtocol() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported connector protocol. Please use one of [aws_sigv4, http, mcp_sse, mcp_streamable_http]");

        McpStreamableHttpConnector.builder().protocol("wrong protocol").build();
    }

    @Test
    public void writeTo() throws IOException {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();

        BytesStreamOutput output = new BytesStreamOutput();
        connector.writeTo(output);

        McpStreamableHttpConnector connector2 = new McpStreamableHttpConnector(output.bytes().streamInput());
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void toXContent() throws IOException {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();

        XContentBuilder builder = XContentFactory.jsonBuilder();
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

        McpStreamableHttpConnector connector = new McpStreamableHttpConnector("mcp_streamable_http", parser);
        Assert.assertEquals("test_mcp_streamable_http_connector_name", connector.getName());
        Assert.assertEquals("1", connector.getVersion());
        Assert.assertEquals("this is a test mcp streamable http connector", connector.getDescription());
        Assert.assertEquals("mcp_streamable_http", connector.getProtocol());
        Assert.assertEquals(AccessMode.PUBLIC, connector.getAccess());
        Assert.assertEquals("https://test.com", connector.getUrl());
        Assert.assertEquals("/custom/endpoint", connector.getParameters().get("endpoint"));
        connector.decrypt(PREDICT.name(), decryptFunction, null);
        Map<String, String> decryptedCredential = connector.getDecryptedCredential();
        Assert.assertEquals(1, decryptedCredential.size());
        Assert.assertEquals("decrypted: TEST_KEY_VALUE", decryptedCredential.get("key"));
        Assert.assertNotNull(connector.getDecryptedHeaders());
        Assert.assertEquals(1, connector.getDecryptedHeaders().size());
        Assert.assertEquals("decrypted: TEST_KEY_VALUE", connector.getDecryptedHeaders().get("api_key"));
    }

    @Test
    public void cloneConnector() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        Connector connector2 = connector.cloneConnector();
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void decrypt() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        connector.decrypt("", decryptFunction, null);
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
    public void encrypt() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
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
    public void validateConnectorURL_Invalid() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector URL is not matching the trusted connector endpoint regex");
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        connector
            .validateConnectorURL(
                Arrays
                    .asList(
                        "^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                        "^https://api\\.openai\\.com/.*$",
                        "^https://api\\.cohere\\.ai/.*$",
                        "^https://bedrock-agent-runtime\\\\..*[a-z0-9-]\\\\.amazonaws\\\\.com/.*$"
                    )
            );
    }

    @Test
    public void validateConnectorURL() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        connector
            .validateConnectorURL(
                Arrays
                    .asList(
                        "^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                        "^https://api\\.openai\\.com/.*$",
                        "^https://bedrock-agent-runtime\\\\..*[a-z0-9-]\\\\.amazonaws\\\\.com/.*$",
                        "^" + connector.getUrl()
                    )
            );
    }

    @Test
    public void testUpdate() {
        McpStreamableHttpConnector connector = createMcpStreamableHttpConnector();
        Map<String, String> initialCredential = new HashMap<>(connector.getCredential());

        // Create update content
        String updatedName = "updated_name";
        String updatedDescription = "updated description";
        String updatedVersion = "2";
        Map<String, String> updatedCredential = new HashMap<>();
        updatedCredential.put("new_key", "new_value");
        List<String> updatedBackendRoles = List.of("role3", "role4");
        AccessMode updatedAccessMode = AccessMode.PRIVATE;
        ConnectorClientConfig updatedClientConfig = new ConnectorClientConfig(40, 40000, 40000, 20, 20, 5, CONSTANT);
        String updatedUrl = "https://updated.test.com";
        Map<String, String> updatedHeaders = new HashMap<>();
        updatedHeaders.put("new_header", "new_header_value");
        updatedHeaders.put("updated_api_key", "${credential.new_key}"); // Referencing new credential key
        Map<String, String> updatedParameters = new HashMap<>();
        updatedParameters.put("endpoint", "/updated/endpoint");

        MLCreateConnectorInput updateInput = MLCreateConnectorInput
            .builder()
            .name(updatedName)
            .description(updatedDescription)
            .version(updatedVersion)
            .credential(updatedCredential)
            .backendRoles(updatedBackendRoles)
            .access(updatedAccessMode)
            .connectorClientConfig(updatedClientConfig)
            .url(updatedUrl)
            .headers(updatedHeaders)
            .parameters(updatedParameters)
            .protocol(MCP_STREAMABLE_HTTP)
            .build();

        // Call the update method
        connector.update(updateInput, encryptFunction);

        // Assertions
        Assert.assertEquals(updatedName, connector.getName());
        Assert.assertEquals(updatedDescription, connector.getDescription());
        Assert.assertEquals(updatedVersion, connector.getVersion());
        Assert.assertEquals(MCP_STREAMABLE_HTTP, connector.getProtocol()); // Should not change if not provided
        Assert.assertEquals(updatedParameters, connector.getParameters());
        Assert.assertEquals(updatedBackendRoles, connector.getBackendRoles());
        Assert.assertEquals(updatedAccessMode, connector.getAccess());
        Assert.assertEquals(updatedClientConfig, connector.getConnectorClientConfig());
        Assert.assertEquals(updatedUrl, connector.getUrl());
        Assert.assertEquals(updatedHeaders, connector.getHeaders());

        // Check encrypted credentials
        Map<String, String> currentCredential = connector.getCredential();
        Assert.assertNotNull(currentCredential);
        Assert.assertEquals(1, currentCredential.size()); // Should replace old credentials
        Assert.assertEquals("encrypted: new_value", currentCredential.get("new_key"));
        Assert.assertNotEquals(initialCredential, currentCredential);

        // Check decrypted credentials and headers (need to explicitly decrypt after update)
        connector.decrypt("", decryptFunction, null); // Use decrypt function from setUp
        Map<String, String> decryptedCredential = connector.getDecryptedCredential();
        Assert.assertNotNull(decryptedCredential);
        Assert.assertEquals(1, decryptedCredential.size());
        Assert.assertEquals("decrypted: ENCRYPTED: NEW_VALUE", decryptedCredential.get("new_key")); // Uses the decrypt function logic

        Map<String, String> decryptedHeaders = connector.getDecryptedHeaders();
        Assert.assertNotNull(decryptedHeaders);
        Assert.assertEquals(2, decryptedHeaders.size());
        Assert.assertEquals("new_header_value", decryptedHeaders.get("new_header"));
        Assert.assertEquals("decrypted: ENCRYPTED: NEW_VALUE", decryptedHeaders.get("updated_api_key")); // Check header substitution
    }

    public static McpStreamableHttpConnector createMcpStreamableHttpConnector() {
        Map<String, String> credential = new HashMap<>();
        credential.put("key", "test_key_value");

        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("endpoint", "/custom/endpoint");

        ConnectorClientConfig clientConfig = new ConnectorClientConfig(30, 30000, 30000, 10, 10, -1, RetryBackoffPolicy.CONSTANT);

        return McpStreamableHttpConnector
            .builder()
            .name("test_mcp_streamable_http_connector_name")
            .version("1")
            .description("this is a test mcp streamable http connector")
            .protocol(MCP_STREAMABLE_HTTP)
            .credential(credential)
            .backendRoles(List.of("role1", "role2"))
            .accessMode(AccessMode.PUBLIC)
            .connectorClientConfig(clientConfig)
            .url("https://test.com")
            .headers(headers)
            .parameters(parameters)
            .build();
    }
}
