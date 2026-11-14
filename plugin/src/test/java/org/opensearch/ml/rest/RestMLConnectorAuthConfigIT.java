/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.utils.ConnectorProtocolValidator;
import org.opensearch.ml.utils.TestHelper;

/**
 * The connector {@code auth_config} object over REST: stored, returned and searched; the rejected writes with their
 * status and message; and updates that leave the stored credential alone unless they supply one.
 */
public class RestMLConnectorAuthConfigIT extends MLCommonsRestTestCase {

    private static final String CONNECTORS = "/_plugins/_ml/connectors/";
    private static final String MODELS = "/_plugins/_ml/models/";
    private static final String CONNECTOR_INDEX = ".plugins-ml-connector";
    private static final String AUTH_ENABLED = "plugins.ml_commons.connector.auth_enabled";
    private static final String MCP_CONNECTOR_ENABLED = "plugins.ml_commons.mcp_connector_enabled";

    // Rejection messages; the text comes from the request parsers and the validators.
    private static final String NOT_AN_OBJECT = OAuth2AuthConfig.NOT_AN_OBJECT_MESSAGE;
    private static final String TYPE_REQUIRED = OAuth2AuthConfig.TYPE_REQUIRED_MESSAGE;
    private static final String SCOPE_NOT_A_STRING = "auth_config.scope must be a string";
    private static final String AUTH_DISABLED = MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
    private static final String OAUTH2_NOT_SUPPORTED = ConnectorProtocolValidator.typeNotSupportedMessage(ConnectorAuthType.OAUTH2);
    private static final String NONE_WITH_FIELDS = OAuth2AuthConfig.NONE_WITH_FIELDS_MESSAGE;

    private static final String PARAMETERS = "{\"endpoint\": \"api.openai.com\", \"model\": \"gpt-4o-mini\"}";
    /** Parameters with an {@code auth} key, which is an ordinary parameter whatever it holds. */
    private static final String API_KEY_PARAMETERS =
        "{\"endpoint\": \"api.openai.com\", \"model\": \"gpt-4o-mini\", \"auth\": \"API_Key\"}";
    private static final String ACTIONS =
        """
            [
              {
                "action_type": "predict",
                "method": "POST",
                "url": "https://${parameters.endpoint}/v1/chat/completions",
                "request_body": "{ \\"model\\": \\"${parameters.model}\\", \\"messages\\": [{\\"role\\": \\"user\\", \\"content\\": \\"${parameters.prompt}\\"}] }"
              }
            ]""";
    private static final String AWS_PARAMETERS =
        "{\"region\": \"us-east-1\", \"service_name\": \"bedrock\", \"model\": \"anthropic.claude-v2\"}";
    private static final String AWS_CREDENTIAL = "{\"access_key\": \"test-access-key\", \"secret_key\": \"test-secret-key\"}";
    private static final String AWS_ACTIONS = """
        [
          {
            "action_type": "predict",
            "method": "POST",
            "url": "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke",
            "headers": {
              "content-type": "application/json"
            },
            "request_body": "{ \\"prompt\\": \\"${parameters.prompt}\\" }"
          }
        ]""";
    private static final String MCP_SSE_WITH_AUTH_CONFIG = """
        {
          "name": "auth config mcp connector",
          "description": "MCP connector for the auth_config REST tests",
          "version": 1,
          "protocol": "mcp_sse",
          "url": "https://api.openai.com/v1/mcp",
          "auth_config": {"type": "none"}
        }""";
    private static final String NONE = "{\"type\": \"none\"}";
    private static final String OAUTH2 = """
        {
          "type": "oauth2",
          "token_url": "https://login.microsoftonline.com/my-tenant/oauth2/v2.0/token",
          "client_auth_method": "client_secret_post",
          "client_id": "my-client",
          "client_secret": "${credential.client_secret}",
          "scope": "api://my-api/.default"
        }""";
    private static final String TERM_TYPE_NONE = "{\"query\": {\"term\": {\"auth_config.type\": \"none\"}}}";

    @Before
    public void setupConnectorAccessControl() throws IOException {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
    }

    public void testSettingDefaultsAndCreateStoresTheObject() throws Exception {
        Map<String, Object> settings = entityAsMap(
            TestHelper
                .makeRequest(client(), "GET", "/_cluster/settings", Map.of("include_defaults", "true", "flat_settings", "true"), "", null)
        );
        assertEquals("false", defaultClusterSetting(settings, AUTH_ENABLED));

        String c1 = registerConnector(httpConnector(PARAMETERS, NONE, "{\"api_key\": \"k1\"}"));
        Map<String, Object> created = getConnector(c1);
        assertEquals(Map.of("type", "none"), created.get("auth_config"));
        assertEquals(Map.of("endpoint", "api.openai.com", "model", "gpt-4o-mini"), created.get("parameters"));
        assertTrue(searchConnectorIds(TERM_TYPE_NONE).contains(c1));
    }

    public void testUpdatesOfAStoredConfiguration() throws Exception {
        String c1 = registerConnector(httpConnector(PARAMETERS, NONE, "{\"api_key\": \"k1\"}"));

        // type is required on every write, auth_config must be an object, members have their types, and a none object
        // holds only type.
        Map<String, String> invalidObjects = new LinkedHashMap<>();
        invalidObjects.put("{\"scope\": \"a\"}", TYPE_REQUIRED);
        invalidObjects.put("{}", TYPE_REQUIRED);
        invalidObjects.put("[]", NOT_AN_OBJECT);
        invalidObjects.put("{\"type\": \"oauth2\", \"scope\": []}", SCOPE_NOT_A_STRING);
        invalidObjects.put("{\"type\": \"none\", \"scope\": \"a\"}", NONE_WITH_FIELDS);
        for (Map.Entry<String, String> invalid : invalidObjects.entrySet()) {
            assertRejected(
                RestStatus.BAD_REQUEST,
                invalid.getValue(),
                () -> updateConnector(c1, "{\"auth_config\": " + invalid.getKey() + "}")
            );
        }

        // The GET body is a valid update body, and writing it back changes nothing but last_updated_time.
        String getBody = EntityUtils.toString(request("GET", CONNECTORS + c1, "").getEntity(), StandardCharsets.UTF_8);
        Map<String, Object> beforeRoundTrip = XContentHelper.convertToMap(JsonXContent.jsonXContent, getBody, false);
        updateConnector(c1, getBody);
        Map<String, Object> afterRoundTrip = getConnector(c1);
        assertEquals(Map.of("type", "none"), afterRoundTrip.get("auth_config"));
        beforeRoundTrip.remove("last_updated_time");
        afterRoundTrip.remove("last_updated_time");
        assertEquals(beforeRoundTrip, afterRoundTrip);
    }

    @SuppressWarnings("unchecked")
    public void testTheAuthParameterIsOrdinaryAndProtocolsWithoutAuthConfigRejectIt() throws Exception {
        // Stored, returned and kept beside an auth_config.
        String c2 = registerConnector(httpConnector(API_KEY_PARAMETERS, null, "{\"api_key\": \"k1\"}"));
        Map<String, Object> withoutAuthConfig = getConnector(c2);
        assertEquals("API_Key", ((Map<String, Object>) withoutAuthConfig.get("parameters")).get("auth"));
        assertNull(withoutAuthConfig.get("auth_config"));
        updateConnector(c2, "{\"auth_config\": " + NONE + "}");
        Map<String, Object> configured = getConnector(c2);
        assertEquals(Map.of("type", "none"), configured.get("auth_config"));
        assertEquals("API_Key", ((Map<String, Object>) configured.get("parameters")).get("auth"));

        // Protocols that do not take auth_config.
        assertRejected(
            RestStatus.BAD_REQUEST,
            ConnectorProtocolValidator.protocolNotSupportedMessage("aws_sigv4"),
            () -> createConnector(connectorBody("aws_sigv4", AWS_PARAMETERS, NONE, AWS_CREDENTIAL, AWS_ACTIONS))
        );
        updateClusterSettings(MCP_CONNECTOR_ENABLED, true);
        try {
            assertRejected(
                RestStatus.BAD_REQUEST,
                ConnectorProtocolValidator.protocolNotSupportedMessage("mcp_sse"),
                () -> createConnector(MCP_SSE_WITH_AUTH_CONFIG)
            );
        } finally {
            updateClusterSettings(MCP_CONNECTOR_ENABLED, (Object) null);
        }
    }

    @SuppressWarnings("unchecked")
    public void testInlineModelConnector() throws Exception {
        String modelId = registerModelWithInlineConnector("auth config model", httpConnector(PARAMETERS, NONE, "{\"api_key\": \"k1\"}"));
        assertEquals(Map.of("type", "none"), ((Map<String, Object>) getModel(modelId).get("connector")).get("auth_config"));
        updateModel(modelId, "{\"connector\": {\"auth_config\": " + NONE + "}}");
        assertEquals(Map.of("type", "none"), ((Map<String, Object>) getModel(modelId).get("connector")).get("auth_config"));
        assertRejected(
            RestStatus.BAD_REQUEST,
            NONE_WITH_FIELDS,
            () -> updateModel(modelId, "{\"connector\": {\"auth_config\": {\"type\": \"none\", \"scope\": \"a\"}}}")
        );
    }

    public void testUpdatesKeepTheStoredCredential() throws Exception {
        String c3 = registerConnector(httpConnector(PARAMETERS, NONE, "{\"api_key\": \"v\"}"));
        Map<String, Object> c0 = storedCredential(c3);
        assertEquals(Set.of("api_key"), c0.keySet());
        assertNotEquals("the credential is stored encrypted", "v", c0.get("api_key"));

        // (a) A description-only update.
        updateConnector(c3, "{\"description\": \"description only\"}");
        assertEquals(c0, storedCredential(c3));

        // (b) An auth_config update without credential.
        updateConnector(c3, "{\"auth_config\": " + NONE + "}");
        assertEquals(Map.of("type", "none"), getConnector(c3).get("auth_config"));
        assertEquals(c0, storedCredential(c3));

        // (c) A credential update replaces the value under the same key; a later description-only update keeps it.
        updateConnector(c3, "{\"credential\": {\"api_key\": \"w\"}}");
        Map<String, Object> replaced = storedCredential(c3);
        assertEquals(Set.of("api_key"), replaced.keySet());
        assertNotEquals(c0.get("api_key"), replaced.get("api_key"));
        updateConnector(c3, "{\"description\": \"description after the credential update\"}");
        assertEquals(replaced, storedCredential(c3));
    }

    public void testProtectedFieldsNeedCredential() throws Exception {
        String c4 = registerConnector(httpConnector(PARAMETERS, NONE, "{\"client_secret\": \"s\"}"));

        String oauth2Alone = "{\"auth_config\": " + OAUTH2 + "}";
        String oauth2WithCredential = "{\"auth_config\": " + OAUTH2 + ", \"credential\": {\"client_secret\": \"t\"}}";
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> updateConnector(c4, oauth2Alone));
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            assertRejected(
                RestStatus.BAD_REQUEST,
                ConnectorProtocolValidator.credentialRequiredMessage("token_url"),
                () -> updateConnector(c4, oauth2Alone)
            );
            assertRejected(RestStatus.BAD_REQUEST, OAUTH2_NOT_SUPPORTED, () -> updateConnector(c4, oauth2WithCredential));
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }
        assertEquals(Map.of("type", "none"), getConnector(c4).get("auth_config"));
    }

    public void testOauth2NeedsTheFlagAndIsNotSupported() throws Exception {
        String create = httpConnector(PARAMETERS, OAUTH2, "{\"client_secret\": \"s\"}");
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> createConnector(create));
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            assertRejected(
                RestStatus.BAD_REQUEST,
                OAUTH2_NOT_SUPPORTED,
                () -> request("POST", MODELS + "_register", registerModelBody("oauth2 model", create))
            );
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }
    }

    private static String httpConnector(String parameters, String authConfig, String credential) {
        return connectorBody("http", parameters, authConfig, credential, ACTIONS);
    }

    /** A connector create body; a null member is left out. */
    private static String connectorBody(String protocol, String parameters, String authConfig, String credential, String actions) {
        StringBuilder body = new StringBuilder()
            .append("{\"name\": \"auth config connector\", \"description\": \"connector for the auth_config REST tests\", \"version\": 1")
            .append(", \"protocol\": \"")
            .append(protocol)
            .append('"');
        appendMember(body, "parameters", parameters);
        appendMember(body, "auth_config", authConfig);
        appendMember(body, "credential", credential);
        appendMember(body, "actions", actions);
        return body.append('}').toString();
    }

    private static void appendMember(StringBuilder body, String name, String json) {
        if (json != null) {
            body.append(", \"").append(name).append("\": ").append(json);
        }
    }

    private static String registerModelBody(String name, String connector) {
        return "{\"name\": \""
            + name
            + "\", \"function_name\": \"remote\", \"description\": \"model for the auth_config REST tests\", \"connector\": "
            + connector
            + "}";
    }

    private static Response request(String method, String path, String body) throws IOException {
        return TestHelper.makeRequest(client(), method, path, null, body, null);
    }

    private static void createConnector(String body) throws IOException {
        request("POST", CONNECTORS + "_create", body);
    }

    private static void updateConnector(String connectorId, String body) throws IOException {
        assertEquals(RestStatus.OK, TestHelper.restStatus(request("PUT", CONNECTORS + connectorId, body)));
    }

    private static Map<String, Object> getConnector(String connectorId) throws IOException {
        return entityAsMap(request("GET", CONNECTORS + connectorId, ""));
    }

    private static String registerModelWithInlineConnector(String name, String connector) throws IOException {
        Response response = request("POST", MODELS + "_register", registerModelBody(name, connector));
        assertEquals(RestStatus.OK, TestHelper.restStatus(response));
        String modelId = (String) entityAsMap(response).get("model_id");
        assertNotNull(modelId);
        return modelId;
    }

    private static void updateModel(String modelId, String body) throws IOException {
        assertEquals(RestStatus.OK, TestHelper.restStatus(request("PUT", MODELS + modelId, body)));
    }

    private static Map<String, Object> getModel(String modelId) throws IOException {
        return entityAsMap(request("GET", MODELS + modelId, ""));
    }

    /** Runs a request that must fail, and checks its status and the message the response reports. */
    @SuppressWarnings("unchecked")
    private static void assertRejected(RestStatus status, String message, ThrowingRunnable rejected) throws IOException {
        ResponseException e = expectThrows(ResponseException.class, rejected);
        assertEquals(status.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        Map<String, Object> error = (Map<String, Object>) entityAsMap(e.getResponse()).get("error");
        assertEquals(message, error.get("reason"));
    }

    /** Searches through the connector search API, after refreshing the connector index. */
    @SuppressWarnings("unchecked")
    private static List<String> searchConnectorIds(String query) throws IOException {
        adminClient().performRequest(new Request("POST", "/" + CONNECTOR_INDEX + "/_refresh"));
        Map<String, Object> response = entityAsMap(request("POST", CONNECTORS + "_search", query));
        List<Map<String, Object>> hits = (List<Map<String, Object>>) ((Map<String, Object>) response.get("hits")).get("hits");
        return hits.stream().map(hit -> (String) hit.get("_id")).toList();
    }

    /** The stored credential of a connector, read from its document with the admin client. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> storedCredential(String connectorId) throws IOException {
        Map<String, Object> document = entityAsMap(
            adminClient().performRequest(new Request("GET", "/" + CONNECTOR_INDEX + "/_doc/" + connectorId))
        );
        Map<String, Object> credential = (Map<String, Object>) ((Map<String, Object>) document.get("_source")).get("credential");
        assertNotNull(credential);
        return credential;
    }

    /**
     * The value a cluster setting shows in the {@code defaults} section that {@code include_defaults=true} adds. OpenSearch
     * lists a setting there only when neither the persistent nor the transient settings hold it.
     */
    @SuppressWarnings("unchecked")
    private static Object defaultClusterSetting(Map<String, Object> settings, String key) {
        Map<String, Object> defaults = (Map<String, Object>) settings.get("defaults");
        assertTrue(
            "Cluster setting " + key + " is not registered, or the persistent or transient settings hold it",
            defaults != null && defaults.containsKey(key)
        );
        return defaults.get(key);
    }
}
