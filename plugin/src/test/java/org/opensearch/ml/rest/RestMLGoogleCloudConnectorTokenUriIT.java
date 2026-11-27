/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.TestHelper;

/**
 * The google_cloud {@code token_uri} rule is enforced when a connector is created or changed, not only when a token
 * is first requested. None of these requests reaches Google: a rejected one fails before anything is stored, and an
 * accepted one is never used for predict.
 */
public class RestMLGoogleCloudConnectorTokenUriIT extends MLCommonsRestTestCase {

    private static final String VERTEXAI_SETTING = "plugins.ml_commons.connector.vertexai_enabled";
    private static final String ALLOWED_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final Map<String, String> DISALLOWED_TOKEN_URIS = Map
        .of(
            "http://mock:8080/token",
            "token_uri must be an https Google endpoint, got: http://mock:8080/token",
            "https://example.com/token",
            "token_uri host must be oauth2.googleapis.com, got: example.com",
            "https://oauth2.googleapis.com:8443/token",
            "token_uri must use the default https port, got port: 8443"
        );

    @Before
    public void enableVertexAi() throws IOException {
        updateClusterSettings(VERTEXAI_SETTING, true);
    }

    @After
    public void resetVertexAi() throws IOException {
        updateClusterSettings(VERTEXAI_SETTING, null);
    }

    private static Map<String, Object> credential(String tokenUri) {
        return Map.of("client_email", "sa@example.iam.gserviceaccount.com", "private_key", "not-used", "token_uri", tokenUri);
    }

    private static Map<String, Object> connector(String tokenUri) {
        return Map
            .of(
                "name",
                "google-token-uri",
                "description",
                "t",
                "version",
                "1",
                "protocol",
                "google_cloud",
                "parameters",
                Map.of("model", "gemini"),
                "credential",
                credential(tokenUri),
                "actions",
                List
                    .of(
                        Map
                            .of(
                                "action_type",
                                "predict",
                                "method",
                                "POST",
                                "url",
                                "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1/publishers/google/models/${parameters.model}:generateContent",
                                "request_body",
                                "{\"contents\": ${parameters.messages}}"
                            )
                    )
            );
    }

    private static void assertBadRequest(String expectedMessage, String method, String path, Object body) {
        ResponseException e = expectThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), method, path, null, StringUtils.gson.toJson(body), null)
        );
        assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        assertTrue(e.getMessage(), e.getMessage().contains(expectedMessage));
    }

    public void testCreateConnectorRejectsDisallowedTokenUri() {
        DISALLOWED_TOKEN_URIS
            .forEach((tokenUri, message) -> assertBadRequest(message, "POST", "/_plugins/_ml/connectors/_create", connector(tokenUri)));
    }

    public void testUpdateConnectorRejectsDisallowedTokenUri() throws Exception {
        String connectorId = registerConnector(StringUtils.gson.toJson(connector(ALLOWED_TOKEN_URI)));
        assertNotNull(connectorId);
        assertBadRequest(
            DISALLOWED_TOKEN_URIS.get("https://example.com/token"),
            "PUT",
            "/_plugins/_ml/connectors/" + connectorId,
            Map.of("credential", credential("https://example.com/token"))
        );
        // An update without a credential leaves the stored one alone and is not checked.
        Response response = TestHelper
            .makeRequest(client(), "PUT", "/_plugins/_ml/connectors/" + connectorId, null, "{\"description\":\"updated\"}", null);
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    public void testRegisterAndUpdateModelWithInlineConnectorRejectDisallowedTokenUri() throws Exception {
        assertBadRequest(
            DISALLOWED_TOKEN_URIS.get("http://mock:8080/token"),
            "POST",
            "/_plugins/_ml/models/_register",
            Map.of("name", "google-inline", "function_name", "remote", "connector", connector("http://mock:8080/token"))
        );

        // Retries until the encryption master key exists, so the inline register below does not hit that race.
        assertNotNull(registerConnector(StringUtils.gson.toJson(connector(ALLOWED_TOKEN_URI))));
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/models/_register",
                null,
                StringUtils.gson
                    .toJson(Map.of("name", "google-inline", "function_name", "remote", "connector", connector(ALLOWED_TOKEN_URI))),
                null
            );
        String modelId = (String) parseResponseToMap(response).get("model_id");
        assertNotNull(modelId);
        assertBadRequest(
            DISALLOWED_TOKEN_URIS.get("https://oauth2.googleapis.com:8443/token"),
            "PUT",
            "/_plugins/_ml/models/" + modelId,
            Map.of("connector", Map.of("credential", credential("https://oauth2.googleapis.com:8443/token")))
        );
    }
}
