/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.utils.ConnectorProtocolValidator;
import org.opensearch.ml.utils.TestHelper;

/**
 * The connector {@code auth_config} object over REST: stored, returned and searched; the rejected writes with their
 * status and message; updates that leave the stored credential alone unless they supply one; and turning the feature
 * off, which stops the requests of a connector whose configuration is oauth2.
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
    private static final String NONE_WITH_FIELDS = OAuth2AuthConfig.NONE_WITH_FIELDS_MESSAGE;
    private static final String TOKEN_URL_NOT_ALLOWED = TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE;
    private static final String CLIENT_SECRET_KEY_MISSING = AuthConfigValidator.missingCredentialKeyMessage("client_secret");

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
    private static final String BEARER_ACTIONS =
        """
            [
              {
                "action_type": "predict",
                "method": "POST",
                "url": "https://${parameters.endpoint}/v1/chat/completions",
                "headers": {
                  "Authorization": "Bearer ${credential.api_key}"
                },
                "request_body": "{ \\"model\\": \\"${parameters.model}\\", \\"messages\\": [{\\"role\\": \\"user\\", \\"content\\": \\"${parameters.prompt}\\"}] }"
              }
            ]""";
    private static final String PREDICT_AND_EXECUTE_ACTIONS =
        """
            [
              {
                "action_type": "predict",
                "method": "POST",
                "url": "https://${parameters.endpoint}/v1/chat/completions",
                "request_body": "{ \\"model\\": \\"${parameters.model}\\", \\"messages\\": [{\\"role\\": \\"user\\", \\"content\\": \\"${parameters.prompt}\\"}] }"
              },
              {
                "action_type": "execute",
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
    private static final String ENTRA_TOKEN_URL = "https://login.microsoftonline.com/my-tenant/oauth2/v2.0/token";
    /** The members, in canonical order, of an oauth2 object that passes every write check with the default token endpoints. */
    private static final String OAUTH2_MEMBERS = "\"type\": \"oauth2\", \"token_url\": \""
        + ENTRA_TOKEN_URL
        + "\", \"client_auth_method\": \"client_secret_post\", \"client_id\": \"my-client\", "
        + "\"client_secret\": \"${credential.client_secret}\"";
    private static final String SCOPE = "api://my-api/.default";
    private static final String OAUTH2 = oauth2(", \"scope\": \"" + SCOPE + "\"");
    /** {@link #OAUTH2} with a quoted integer, which the parse accepts as the integer. */
    private static final String OAUTH2_QUOTED_TTL = oauth2(
        ", \"scope\": \"" + SCOPE + "\", \"token_response\": {\"default_ttl_seconds\": \"600\"}"
    );
    /** The credential that holds the key {@link #OAUTH2} references. */
    private static final String CLIENT_SECRET = "{\"client_secret\": \"s\"}";
    private static final String TERM_TYPE_NONE = "{\"query\": {\"term\": {\"auth_config.type\": \"none\"}}}";
    private static final String TERM_TYPE_OAUTH2 = "{\"query\": {\"term\": {\"auth_config.type\": \"oauth2\"}}}";
    private static final String EXISTS_SCOPE = "{\"query\": {\"exists\": {\"field\": \"auth_config.scope\"}}}";

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

    public void testInlineModelConnector() throws Exception {
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            String modelId = registerModelWithInlineConnector("auth config model", httpConnector(PARAMETERS, OAUTH2, CLIENT_SECRET));
            Map<String, Object> inline = connectorOf(getModel(modelId));
            assertEquals(mapOf(OAUTH2), inline.get("auth_config"));
            assertFalse(parametersOf(inline).containsKey("auth"));

            // Dropping scope changes a protected field, so it needs credential.
            String dropScope = "{\"connector\": {\"auth_config\": " + oauth2("") + "}}";
            assertRejected(
                RestStatus.BAD_REQUEST,
                ConnectorProtocolValidator.credentialRequiredMessage("scope"),
                () -> updateModel(modelId, dropScope)
            );
            assertEquals(SCOPE, authConfigOf(connectorOf(getModel(modelId))).get("scope"));
            updateModel(modelId, "{\"connector\": {\"auth_config\": " + oauth2("") + ", \"credential\": " + CLIENT_SECRET + "}}");
            assertEquals(mapOf(oauth2("")), authConfigOf(connectorOf(getModel(modelId))));

            // {"type": "none"} removes the stored members, and a none object holds only type.
            updateModel(modelId, "{\"connector\": {\"auth_config\": " + NONE + "}}");
            assertEquals(Map.of("type", "none"), authConfigOf(connectorOf(getModel(modelId))));
            assertRejected(
                RestStatus.BAD_REQUEST,
                NONE_WITH_FIELDS,
                () -> updateModel(modelId, "{\"connector\": {\"auth_config\": {\"type\": \"none\", \"scope\": \"a\"}}}")
            );
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }
    }

    public void testUpdatesKeepTheStoredCredential() throws Exception {
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            String c3 = registerConnector(
                httpConnector(PARAMETERS, oauth2(", \"token_response\": {\"default_ttl_seconds\": 600}"), "{\"client_secret\": \"v\"}")
            );
            Map<String, Object> c0 = storedCredential(c3);
            assertEquals(Set.of("client_secret"), c0.keySet());
            assertNotEquals("the credential is stored encrypted", "v", c0.get("client_secret"));

            // (a) A description-only update.
            updateConnector(c3, "{\"description\": \"description only\"}");
            assertEquals(c0, storedCredential(c3));

            // (b) A token_response-only update without credential: token_response is not a protected field.
            updateConnector(c3, "{\"auth_config\": " + oauth2(", \"token_response\": {\"default_ttl_seconds\": 900}") + "}");
            assertEquals(tokenResponse(900), authConfigOf(getConnector(c3)).get("token_response"));
            assertEquals(c0, storedCredential(c3));

            // (c) A credential update replaces the value under the same key; a later description-only update keeps it.
            updateConnector(c3, "{\"credential\": {\"client_secret\": \"w\"}}");
            Map<String, Object> replaced = storedCredential(c3);
            assertEquals(Set.of("client_secret"), replaced.keySet());
            assertNotEquals(c0.get("client_secret"), replaced.get("client_secret"));
            updateConnector(c3, "{\"description\": \"description after the credential update\"}");
            assertEquals(replaced, storedCredential(c3));
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }

        // (d) A plain API-key connector without auth_config.
        String plain = registerConnector(connectorBody("http", PARAMETERS, null, "{\"api_key\": \"p\"}", BEARER_ACTIONS));
        Map<String, Object> plainCredential = storedCredential(plain);
        updateConnector(plain, "{\"description\": \"plain connector, description only\"}");
        assertEquals(plainCredential, storedCredential(plain));
    }

    public void testProtectedFieldsNeedCredential() throws Exception {
        String c4 = registerConnector(httpConnector(PARAMETERS, NONE, CLIENT_SECRET));

        String oauth2WithoutCredential = "{\"auth_config\": " + OAUTH2 + "}";
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> updateConnector(c4, oauth2WithoutCredential));
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            assertRejected(
                RestStatus.BAD_REQUEST,
                ConnectorProtocolValidator.credentialRequiredMessage("token_url"),
                () -> updateConnector(c4, oauth2WithoutCredential)
            );
            updateConnector(c4, "{\"auth_config\": " + OAUTH2 + ", \"credential\": {\"client_secret\": \"t\"}}");
            assertTrue(searchConnectorIds(EXISTS_SCOPE).contains(c4));

            assertRejected(
                RestStatus.BAD_REQUEST,
                ConnectorProtocolValidator.credentialRequiredMessage("scope"),
                () -> updateConnector(c4, "{\"auth_config\": " + oauth2(", \"scope\": \"s2\"") + "}")
            );

            // Dropping scope with credential: the stored document marks the dropped member with [], which GET never
            // shows and which the index does not hold as a value.
            updateConnector(c4, "{\"auth_config\": " + oauth2("") + ", \"credential\": {\"client_secret\": \"u\"}}");
            assertFalse(authConfigOf(getConnector(c4)).containsKey("scope"));
            assertEquals(List.of(), authConfigOf(searchHitSource(c4)).get("scope"));
            assertFalse(searchConnectorIds(EXISTS_SCOPE).contains(c4));
            assertTrue(searchConnectorIds(TERM_TYPE_OAUTH2).contains(c4));

            String otherTenant = oauth2("").replace("my-tenant", "other-tenant");
            updateConnector(c4, "{\"auth_config\": " + otherTenant + ", \"credential\": {\"client_secret\": \"v\"}}");
            assertEquals(ENTRA_TOKEN_URL.replace("my-tenant", "other-tenant"), authConfigOf(getConnector(c4)).get("token_url"));
            // The credential replaces the stored one, so an unrelated key leaves the reference without its key.
            assertRejected(
                RestStatus.BAD_REQUEST,
                CLIENT_SECRET_KEY_MISSING,
                () -> updateConnector(c4, "{\"auth_config\": " + otherTenant + ", \"credential\": {\"other\": \"x\"}}")
            );
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }
    }

    /**
     * An oauth2 object needs the flag. With it on, create, register and update store it; GET returns it in canonical
     * order with its integers as integers, and a token URL must match the trusted token endpoints.
     */
    @SuppressWarnings("unchecked")
    public void testOauth2WritePathsAndRefusals() throws Exception {
        String target = registerConnector(httpConnector(PARAMETERS, null, "{\"api_key\": \"k\"}"));
        String create = httpConnector(PARAMETERS, OAUTH2, CLIENT_SECRET);
        String createWithQuotedTtl = httpConnector(PARAMETERS, OAUTH2_QUOTED_TTL, CLIENT_SECRET);
        String update = "{\"auth_config\": " + OAUTH2 + ", \"credential\": " + CLIENT_SECRET + "}";
        String register = registerModelBody("oauth2 model", create);

        // The parse accepts the quoted integer: the write gets as far as the flag, not a parse error.
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> createConnector(create));
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> createConnector(createWithQuotedTtl));
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> request("POST", MODELS + "_register", register));
        assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> updateConnector(target, update));
        assertNull(getConnector(target).get("auth_config"));
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            // Sent out of canonical order: GET returns the members in canonical order and the integer as an integer.
            String outOfOrder = "{\"token_response\": {\"default_ttl_seconds\": 1800}, \"scope\": \""
                + SCOPE
                + "\", "
                + OAUTH2_MEMBERS
                + "}";
            String c1 = registerConnector(httpConnector(PARAMETERS, outOfOrder, CLIENT_SECRET));
            Map<String, Object> created = (Map<String, Object>) orderedMap(request("GET", CONNECTORS + c1, "")).get("auth_config");
            assertEquals(
                List.of("type", "token_url", "grant_type", "client_auth_method", "client_id", "client_secret", "scope", "token_response"),
                new ArrayList<>(created.keySet())
            );
            assertEquals(tokenResponse(1800), created.get("token_response"));
            String quoted = registerConnector(createWithQuotedTtl);
            assertEquals(tokenResponse(600), authConfigOf(getConnector(quoted)).get("token_response"));

            // The GET body sent back without credential is written.
            String getBody = EntityUtils.toString(request("GET", CONNECTORS + c1, "").getEntity(), StandardCharsets.UTF_8);
            updateConnector(c1, getBody);
            assertEquals(created, authConfigOf(getConnector(c1)));

            String modelId = registerModelWithInlineConnector("oauth2 model", create);
            assertEquals(mapOf(OAUTH2), authConfigOf(connectorOf(getModel(modelId))));
            updateConnector(target, update);
            assertEquals(mapOf(OAUTH2), authConfigOf(getConnector(target)));

            String otherHost = OAUTH2.replace("login.microsoftonline.com", "login.example.com");
            assertRejected(
                RestStatus.BAD_REQUEST,
                TOKEN_URL_NOT_ALLOWED,
                () -> createConnector(httpConnector(PARAMETERS, otherHost, CLIENT_SECRET))
            );
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }
    }

    /**
     * Turning the flag off stops a deployed model and a connector run through an agent before any token request is
     * made: both answer 403 naming the setting.
     */
    @SuppressWarnings("unchecked")
    public void testTurningTheFlagOffStopsPredictAndConnectorExecute() throws Exception {
        updateClusterSettings(AUTH_ENABLED, true);
        try {
            String connectorId = registerConnector(connectorBody("http", PARAMETERS, OAUTH2, CLIENT_SECRET, PREDICT_AND_EXECUTE_ACTIONS));
            String modelId = (String) entityAsMap(RestMLRemoteInferenceIT.registerRemoteModel("oauth2 model", connectorId)).get("model_id");
            String taskId = (String) entityAsMap(RestMLRemoteInferenceIT.deployRemoteModel(modelId)).get("task_id");
            waitForTask(taskId, MLTaskState.COMPLETED);
            String agent = "{\"name\": \"oauth2 connector agent\", \"type\": \"flow\", \"tools\": [{\"type\": \"ConnectorTool\", "
                + "\"parameters\": {\"connector_id\": \""
                + connectorId
                + "\", \"connector_action\": \"execute\"}}]}";
            String agentId = (String) entityAsMap(request("POST", "/_plugins/_ml/agents/_register", agent)).get("agent_id");

            updateClusterSettings(AUTH_ENABLED, false);
            String input = "{\"parameters\": {\"prompt\": \"hello\"}}";
            assertRejected(RestStatus.FORBIDDEN, AUTH_DISABLED, () -> request("POST", MODELS + modelId + "/_predict", input));
            // The agent API reports the message as the error details.
            ResponseException e = expectThrows(
                ResponseException.class,
                () -> request("POST", "/_plugins/_ml/agents/" + agentId + "/_execute", input)
            );
            assertEquals(RestStatus.FORBIDDEN.getStatus(), e.getResponse().getStatusLine().getStatusCode());
            assertEquals(AUTH_DISABLED, ((Map<String, Object>) entityAsMap(e.getResponse()).get("error")).get("details"));
        } finally {
            updateClusterSettings(AUTH_ENABLED, (Object) null);
        }
    }

    /** An oauth2 object of {@link #OAUTH2_MEMBERS} followed by {@code moreMembers}. */
    private static String oauth2(String moreMembers) {
        return "{" + OAUTH2_MEMBERS + moreMembers + "}";
    }

    /** The token_response GET returns for a request that set only {@code default_ttl_seconds}: the full typed form. */
    private static Map<String, Object> tokenResponse(int defaultTtlSeconds) {
        return Map.of("access_token_field", "access_token", "expires_in_field", "expires_in", "default_ttl_seconds", defaultTtlSeconds);
    }

    /** The object {@code json} denotes, as GET returns it: the {@code grant_type} default is emitted for oauth2. */
    private static Map<String, Object> mapOf(String json) {
        Map<String, Object> object = XContentHelper.convertToMap(JsonXContent.jsonXContent, json, false);
        if ("oauth2".equals(object.get("type"))) {
            object.putIfAbsent("grant_type", "client_credentials");
        }
        return object;
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

    /** The hits of a connector search, after refreshing the connector index. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> searchConnectors(String query) throws IOException {
        adminClient().performRequest(new Request("POST", "/" + CONNECTOR_INDEX + "/_refresh"));
        Map<String, Object> response = entityAsMap(request("POST", CONNECTORS + "_search", query));
        return (List<Map<String, Object>>) ((Map<String, Object>) response.get("hits")).get("hits");
    }

    private static List<String> searchConnectorIds(String query) throws IOException {
        return searchConnectors(query).stream().map(hit -> (String) hit.get("_id")).toList();
    }

    /** The {@code _source} of a connector's search hit: the stored document, without its credential. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> searchHitSource(String connectorId) throws IOException {
        List<Map<String, Object>> hits = searchConnectors("{\"query\": {\"ids\": {\"values\": [\"" + connectorId + "\"]}}}");
        assertEquals(1, hits.size());
        return (Map<String, Object>) hits.get(0).get("_source");
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

    /** The response body as a map that keeps member order. */
    private static Map<String, Object> orderedMap(Response response) throws Exception {
        return XContentHelper
            .convertToMap(JsonXContent.jsonXContent, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> authConfigOf(Map<String, Object> connector) {
        Map<String, Object> authConfig = (Map<String, Object>) connector.get("auth_config");
        assertNotNull(authConfig);
        return authConfig;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parametersOf(Map<String, Object> connector) {
        Map<String, Object> parameters = (Map<String, Object>) connector.get("parameters");
        assertNotNull(parameters);
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> connectorOf(Map<String, Object> model) {
        Map<String, Object> connector = (Map<String, Object>) model.get("connector");
        assertNotNull(connector);
        return connector;
    }
}
