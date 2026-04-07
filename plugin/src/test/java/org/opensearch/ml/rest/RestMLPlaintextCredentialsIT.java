/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

/**
 * Integration tests for the plaintext credentials feature.
 * Tests the cluster setting 'plugins.ml_commons.allow_plaintext_credentials'.
 */
public class RestMLPlaintextCredentialsIT extends MLCommonsRestTestCase {

    private static final String PLAINTEXT_CREDENTIALS_SETTING = "plugins.ml_commons.allow_plaintext_credentials";

    // Connector with encrypted credentials (default behavior)
    private final String encryptedCredentialsConnector = "{\n"
        + "  \"name\": \"Test Connector with Encrypted Credentials\",\n"
        + "  \"description\": \"Connector for testing encrypted credentials\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"http\",\n"
        + "  \"parameters\": {\n"
        + "    \"endpoint\": \"api.openai.com\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"api_key\": \"test_api_key\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "    {\n"
        + "      \"action_type\": \"predict\",\n"
        + "      \"method\": \"POST\",\n"
        + "      \"url\": \"https://api.openai.com/v1/completions\",\n"
        + "      \"headers\": {\n"
        + "        \"Authorization\": \"Bearer ${credential.api_key}\"\n"
        + "      },\n"
        + "      \"request_body\": \"{\\\"model\\\": \\\"gpt-3.5-turbo\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\"}\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    // Connector with plaintext credentials (encrypted: false)
    private final String plaintextCredentialsConnector = "{\n"
        + "  \"name\": \"Test Connector with Plaintext Credentials\",\n"
        + "  \"description\": \"Connector for testing plaintext credentials\",\n"
        + "  \"version\": 1,\n"
        + "  \"protocol\": \"http\",\n"
        + "  \"parameters\": {\n"
        + "    \"endpoint\": \"api.openai.com\"\n"
        + "  },\n"
        + "  \"credential\": {\n"
        + "    \"api_key\": \"test_api_key\",\n"
        + "    \"encrypted\": \"false\"\n"
        + "  },\n"
        + "  \"actions\": [\n"
        + "    {\n"
        + "      \"action_type\": \"predict\",\n"
        + "      \"method\": \"POST\",\n"
        + "      \"url\": \"https://api.openai.com/v1/completions\",\n"
        + "      \"headers\": {\n"
        + "        \"Authorization\": \"Bearer ${credential.api_key}\"\n"
        + "      },\n"
        + "      \"request_body\": \"{\\\"model\\\": \\\"gpt-3.5-turbo\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\"}\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Ensure the setting is disabled (default) before each test
        updateClusterSettings(PLAINTEXT_CREDENTIALS_SETTING, false);
    }

    @After
    public void tearDown() throws Exception {
        // Reset to default after each test
        updateClusterSettings(PLAINTEXT_CREDENTIALS_SETTING, false);
        super.tearDown();
    }

    @Test
    public void testCreateConnectorWithEncryptedCredentials_Success() throws Exception {
        // Creating a connector with encrypted credentials (default) should always succeed
        Response response = createConnector(encryptedCredentialsConnector);
        Map<String, Object> responseMap = parseResponseToMap(response);

        assertNotNull("Response should contain connector_id", responseMap.get("connector_id"));
        String connectorId = (String) responseMap.get("connector_id");
        assertFalse("Connector ID should not be empty", connectorId.isEmpty());

        // Clean up
        deleteConnector(connectorId);
    }

    @Test
    public void testCreateConnectorWithPlaintextCredentials_BlockedByDefault() throws Exception {
        // Creating a connector with plaintext credentials should fail when setting is disabled
        try {
            createConnector(plaintextCredentialsConnector);
            fail("Expected exception when creating connector with plaintext credentials while setting is disabled");
        } catch (ResponseException e) {
            String responseBody = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(
                "Error message should mention plaintext credentials not allowed",
                responseBody.contains("Plaintext credentials are not allowed")
            );
        }
    }

    @Test
    public void testCreateConnectorWithPlaintextCredentials_AllowedWhenEnabled() throws Exception {
        // Enable plaintext credentials
        updateClusterSettings(PLAINTEXT_CREDENTIALS_SETTING, true);

        // Creating a connector with plaintext credentials should succeed when setting is enabled
        Response response = createConnector(plaintextCredentialsConnector);
        Map<String, Object> responseMap = parseResponseToMap(response);

        assertNotNull("Response should contain connector_id", responseMap.get("connector_id"));
        String connectorId = (String) responseMap.get("connector_id");
        assertFalse("Connector ID should not be empty", connectorId.isEmpty());

        // Verify the connector was created by getting it
        Response getResponse = getConnector(connectorId);
        Map<String, Object> connectorMap = parseResponseToMap(getResponse);
        assertEquals("Test Connector with Plaintext Credentials", connectorMap.get("name"));

        // Clean up
        deleteConnector(connectorId);
    }

    @Test
    public void testUpdateConnectorWithPlaintextCredentials_BlockedByDefault() throws Exception {
        // First create a connector with encrypted credentials
        Response response = createConnector(encryptedCredentialsConnector);
        Map<String, Object> responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");

        // Try to update the connector with plaintext credentials - should fail
        String updateContent = "{\n"
            + "  \"credential\": {\n"
            + "    \"api_key\": \"new_api_key\",\n"
            + "    \"encrypted\": \"false\"\n"
            + "  }\n"
            + "}";

        try {
            updateConnector(connectorId, updateContent);
            fail("Expected exception when updating connector with plaintext credentials while setting is disabled");
        } catch (ResponseException e) {
            String responseBody = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(
                "Error message should mention plaintext credentials not allowed",
                responseBody.contains("Plaintext credentials are not allowed")
            );
        }

        // Clean up
        deleteConnector(connectorId);
    }

    @Test
    public void testUpdateConnectorWithPlaintextCredentials_AllowedWhenEnabled() throws Exception {
        // Enable plaintext credentials
        updateClusterSettings(PLAINTEXT_CREDENTIALS_SETTING, true);

        // Create a connector with plaintext credentials
        Response response = createConnector(plaintextCredentialsConnector);
        Map<String, Object> responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");

        // Update the connector with new plaintext credentials - should succeed
        String updateContent = "{\n"
            + "  \"description\": \"Updated description\",\n"
            + "  \"credential\": {\n"
            + "    \"api_key\": \"new_api_key\",\n"
            + "    \"encrypted\": \"false\"\n"
            + "  }\n"
            + "}";

        Response updateResponse = updateConnector(connectorId, updateContent);
        assertEquals(200, updateResponse.getStatusLine().getStatusCode());

        // Verify the update
        Response getResponse = getConnector(connectorId);
        Map<String, Object> connectorMap = parseResponseToMap(getResponse);
        assertEquals("Updated description", connectorMap.get("description"));

        // Clean up
        deleteConnector(connectorId);
    }

    @Test
    public void testDynamicSettingChange() throws Exception {
        // Verify setting is initially disabled
        try {
            createConnector(plaintextCredentialsConnector);
            fail("Expected exception when creating connector with plaintext credentials while setting is disabled");
        } catch (ResponseException e) {
            assertTrue(
                "Error should mention plaintext credentials",
                TestHelper.httpEntityToString(e.getResponse().getEntity()).contains("Plaintext credentials are not allowed")
            );
        }

        // Enable the setting dynamically
        updateClusterSettings(PLAINTEXT_CREDENTIALS_SETTING, true);

        // Now it should succeed
        Response response = createConnector(plaintextCredentialsConnector);
        Map<String, Object> responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        assertNotNull("Connector should be created after enabling setting", connectorId);

        // Disable the setting again
        updateClusterSettings(PLAINTEXT_CREDENTIALS_SETTING, false);

        // Creating new connector with plaintext credentials should fail again
        try {
            createConnector(plaintextCredentialsConnector);
            fail("Expected exception after disabling setting again");
        } catch (ResponseException e) {
            assertTrue(
                "Error should mention plaintext credentials",
                TestHelper.httpEntityToString(e.getResponse().getEntity()).contains("Plaintext credentials are not allowed")
            );
        }

        // Clean up
        deleteConnector(connectorId);
    }

    // Helper methods

    private Response createConnector(String input) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/connectors/_create",
                null,
                TestHelper.toHttpEntity(input),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
    }

    private Response getConnector(String connectorId) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "GET",
                "/_plugins/_ml/connectors/" + connectorId,
                null,
                "",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
    }

    private Response updateConnector(String connectorId, String updateContent) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/connectors/" + connectorId,
                null,
                TestHelper.toHttpEntity(updateContent),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
    }

    private Response deleteConnector(String connectorId) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "DELETE",
                "/_plugins/_ml/connectors/" + connectorId,
                null,
                "",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
    }
}
