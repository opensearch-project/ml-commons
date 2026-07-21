/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for user-defined document ids on connector, model and agent registration.
 * <p>
 * These tests only exercise the registration path (writing the document to the system index), which does not call any
 * external model service, so a placeholder credential is sufficient and no real API key is required.
 */
public class RestMLCustomIdIT extends MLCommonsRestTestCase {

    // a placeholder credential is enough: registration never calls the external service, so the key is not validated
    private static final String PLACEHOLDER_KEY = "placeholder_api_key";

    @Before
    public void setupCustomIdItSettings() throws IOException {
        // allow the placeholder connector endpoint so connector/model registration is not rejected by the trusted-endpoint check
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
        // the user-defined id feature is disabled by default; enable it so custom ids are accepted
        updateClusterSettings("plugins.ml_commons.user_defined_id_enabled", true);
    }

    private String connectorBody(String customId) {
        String idField = customId == null ? "" : "\"id\": \"" + customId + "\",\n";
        return "{\n"
            + idField
            + "\"name\": \"Custom Id Test Connector\",\n"
            + "\"description\": \"connector for custom id IT\",\n"
            + "\"version\": 1,\n"
            + "\"protocol\": \"http\",\n"
            + "\"parameters\": { \"endpoint\": \"api.test.com\", \"model\": \"test-model\" },\n"
            + "\"credential\": { \"key\": \""
            + PLACEHOLDER_KEY
            + "\" },\n"
            + "\"actions\": [\n"
            + "  {\n"
            + "    \"action_type\": \"predict\",\n"
            + "    \"method\": \"POST\",\n"
            + "    \"url\": \"https://api.test.com/v1/completions\",\n"
            + "    \"request_body\": \"{}\"\n"
            + "  }\n"
            + "]\n"
            + "}";
    }

    private Response createConnector(String body) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, TestHelper.toHttpEntity(body), null);
    }

    private String agentBody(String customId) {
        String idField = customId == null ? "" : "\"id\": \"" + customId + "\",\n";
        return "{\n"
            + idField
            + "\"name\": \"Custom Id Test Agent\",\n"
            + "\"type\": \"flow\",\n"
            + "\"description\": \"agent for custom id IT\",\n"
            + "\"tools\": [ { \"type\": \"ListIndexTool\" } ]\n"
            + "}";
    }

    private Response registerAgent(String body) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(body), null);
    }

    private String createConnectorAndGetId() throws IOException {
        Response response = createConnector(connectorBody(null));
        return (String) parseResponseToMap(response).get("connector_id");
    }

    private String registerModelGroupAndGetId(String name) throws IOException {
        String body = "{ \"name\": \"" + name + "\", \"description\": \"custom id IT model group\" }";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/model_groups/_register", null, TestHelper.toHttpEntity(body), null);
        return (String) parseResponseToMap(response).get("model_group_id");
    }

    private String modelBody(String customId, String modelGroupId, String connectorId) {
        String idField = customId == null ? "" : "\"id\": \"" + customId + "\",\n";
        return "{\n"
            + idField
            + "\"name\": \"Custom Id Test Model\",\n"
            + "\"function_name\": \"remote\",\n"
            + "\"model_group_id\": \""
            + modelGroupId
            + "\",\n"
            + "\"version\": \"1.0.0\",\n"
            + "\"description\": \"model for custom id IT\",\n"
            + "\"connector_id\": \""
            + connectorId
            + "\"\n"
            + "}";
    }

    private Response registerModelRequest(String body) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, TestHelper.toHttpEntity(body), null);
    }

    public void testCreateConnector_withCustomId_usesItAsConnectorId() throws IOException {
        String customId = "my-custom-connector-it";
        Response response = createConnector(connectorBody(customId));
        Map responseMap = parseResponseToMap(response);
        assertEquals(customId, responseMap.get("connector_id"));

        // the document is retrievable under the user-supplied id
        Response getResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/connectors/" + customId, null, "", null);
        Map getMap = parseResponseToMap(getResponse);
        assertEquals("Custom Id Test Connector", getMap.get("name"));
    }

    public void testCreateConnector_withDuplicateCustomId_fails() throws IOException {
        String customId = "duplicate-connector-it";
        createConnector(connectorBody(customId));

        // registering a second connector with the same custom id must fail instead of overwriting the existing one
        ResponseException exception = expectThrows(ResponseException.class, () -> createConnector(connectorBody(customId)));
        assertEquals(409, exception.getResponse().getStatusLine().getStatusCode());
    }

    public void testCreateConnector_withoutCustomId_autoGeneratesId() throws IOException {
        Response response = createConnector(connectorBody(null));
        Map responseMap = parseResponseToMap(response);
        assertNotNull(responseMap.get("connector_id"));
    }

    public void testRegisterAgent_withCustomId_usesItAsAgentId() throws IOException {
        String customId = "my-custom-agent-it";
        Response response = registerAgent(agentBody(customId));
        Map responseMap = parseResponseToMap(response);
        assertEquals(customId, responseMap.get("agent_id"));

        // the document is retrievable under the user-supplied id
        Response getResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + customId, null, "", null);
        Map getMap = parseResponseToMap(getResponse);
        assertEquals("Custom Id Test Agent", getMap.get("name"));
    }

    public void testRegisterAgent_withDuplicateCustomId_fails() throws IOException {
        String customId = "duplicate-agent-it";
        registerAgent(agentBody(customId));

        // registering a second agent with the same custom id must fail instead of overwriting the existing one
        ResponseException exception = expectThrows(ResponseException.class, () -> registerAgent(agentBody(customId)));
        assertEquals(409, exception.getResponse().getStatusLine().getStatusCode());
    }

    public void testRegisterAgent_withoutCustomId_autoGeneratesId() throws IOException {
        Response response = registerAgent(agentBody(null));
        Map responseMap = parseResponseToMap(response);
        assertNotNull(responseMap.get("agent_id"));
    }

    public void testRegisterModel_withCustomId_usesItAsModelId() throws IOException {
        String connectorId = createConnectorAndGetId();
        String modelGroupId = registerModelGroupAndGetId("custom_id_model_group_1");
        String customId = "my-custom-model-it";

        Response response = registerModelRequest(modelBody(customId, modelGroupId, connectorId));
        Map responseMap = parseResponseToMap(response);
        assertEquals(customId, responseMap.get("model_id"));

        // the document is retrievable under the user-supplied id
        Response getResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + customId, null, "", null);
        Map getMap = parseResponseToMap(getResponse);
        assertEquals("Custom Id Test Model", getMap.get("name"));
    }

    public void testRegisterModel_withDuplicateCustomId_fails() throws IOException {
        String connectorId = createConnectorAndGetId();
        String modelGroupId = registerModelGroupAndGetId("custom_id_model_group_2");
        String customId = "duplicate-model-it";

        registerModelRequest(modelBody(customId, modelGroupId, connectorId));

        // registering a second model with the same custom id must fail instead of overwriting the existing one
        ResponseException exception = expectThrows(
            ResponseException.class,
            () -> registerModelRequest(modelBody(customId, modelGroupId, connectorId))
        );
        assertEquals(409, exception.getResponse().getStatusLine().getStatusCode());
    }

    public void testRegisterModel_withoutCustomId_autoGeneratesId() throws IOException {
        String connectorId = createConnectorAndGetId();
        String modelGroupId = registerModelGroupAndGetId("custom_id_model_group_3");

        Response response = registerModelRequest(modelBody(null, modelGroupId, connectorId));
        Map responseMap = parseResponseToMap(response);
        assertNotNull(responseMap.get("model_id"));
    }
}
