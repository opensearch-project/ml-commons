/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.jwtBearer;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parser;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.toJson;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.JwtSpec;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;

/**
 * The {@code auth_config} of a {@code google_cloud} connector: an explicit block is stored and used as on http; a legacy
 * connector stores none and its executor uses the configuration derived from the stored fields.
 */
public class GoogleCloudConnectorAuthTests {

    private static final String CLIENT_EMAIL = "svc@project.iam.gserviceaccount.com";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String ACTION_URL =
        "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1/x:generateContent";

    private static Map<String, String> serviceAccount(String tokenUri) {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "-----BEGIN PRIVATE KEY-----\nnever-read-here\n-----END PRIVATE KEY-----\n");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, CLIENT_EMAIL);
        if (tokenUri != null) {
            credential.put(GoogleCloudConnector.TOKEN_URI_FIELD, tokenUri);
        }
        return credential;
    }

    private static GoogleCloudConnector connector(
        Map<String, String> parameters,
        Map<String, String> credential,
        OAuth2AuthConfig authConfig
    ) {
        return GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .credential(credential)
            .authConfig(authConfig)
            .actions(
                List
                    .of(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url(ACTION_URL)
                            .requestBody("{}")
                            .build()
                    )
            )
            .build();
    }

    private static GoogleCloudConnector serviceAccountConnector() {
        return connector(null, serviceAccount(null), null);
    }

    private static GoogleCloudConnector adcConnector() {
        return connector(Map.of(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC), null, null);
    }

    /** The configuration a legacy service account connector stands for, with {@code tokenUrl}, {@code audience} and the {@code scope} claim. */
    private static OAuth2AuthConfig derived(String tokenUrl, String audience, String scope) {
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(tokenUrl)
            .grantType(OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE)
            .jwt(
                JwtSpec
                    .builder()
                    .issuer("${credential.client_email}")
                    .audience(audience)
                    .privateKey("${credential.private_key}")
                    .signingAlg("RS256")
                    .lifetimeSeconds(3600)
                    .additionalClaims(Map.of("scope", scope))
                    .build()
            )
            .build();
    }

    @Test
    public void aServiceAccountConnector_derivesAJwtBearerConfigurationFromItsStoredFields() {
        OAuth2AuthConfig config = serviceAccountConnector().getEffectiveAuthConfig();

        assertEquals(derived(TOKEN_URI, null, GoogleCloudConnector.DEFAULT_SCOPE), config);
        // The members the Google auth library sends, and nothing it does not: no subject, no client, no form scope, and no
        // audience, since the signer addresses the assertion to the token URL, the Google endpoint.
        assertEquals(TOKEN_URI, config.getTokenUrl());
        assertEquals(3600, config.getJwt().getLifetimeSeconds());
        assertEquals("RS256", config.getJwt().getSigningAlg());
        assertEquals("none", config.getClientAuthMethod());
        assertNull(config.getJwt().getSubject());
        assertNull(config.getJwt().getAudience());
        assertNull(config.getClientId());
        assertNull(config.getScope());
        assertEquals(List.of("client_email", "private_key"), List.copyOf(config.referencedCredentialKeys()));
    }

    @Test
    public void storedScopesAndTokenUri_enterTheDerivedConfigurationAsWritten() {
        String scopes = "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/other";
        GoogleCloudConnector connector = connector(
            Map.of(GoogleCloudConnector.SCOPES_FIELD, scopes),
            serviceAccount("https://oauth2.example.com/token"),
            null
        );

        OAuth2AuthConfig config = connector.getEffectiveAuthConfig();

        // The token URI is referenced, never copied, the scopes stay one claim value, and the assertion stays addressed to
        // the Google endpoint, as the library sends it whatever token server URI it is configured with.
        assertEquals(derived("${credential.token_uri}", TOKEN_URI, scopes), config);
        assertEquals(TOKEN_URI, config.getJwt().getAudience());
        assertEquals(List.of("client_email", "private_key", "token_uri"), List.copyOf(config.referencedCredentialKeys()));
    }

    @Test
    public void anAdcConnector_derivesTheInternalGoogleAdcType() {
        OAuth2AuthConfig config = adcConnector().getEffectiveAuthConfig();

        assertEquals(OAuth2AuthConfig.builder().type(ConnectorAuthType.GOOGLE_ADC).build(), config);
        assertTrue(config.getType().isActive());
        assertFalse(config.getType().isUserWritable());
    }

    @Test
    public void anExplicitBlock_isTheEffectiveConfiguration_evenWhenItIsNone() {
        OAuth2AuthConfig explicit = jwtBearer();
        GoogleCloudConnector withBlock = connector(null, serviceAccount(null), explicit);
        assertSame(explicit, withBlock.getAuthConfig());
        assertSame(explicit, withBlock.getEffectiveAuthConfig());

        // none over a stored service account: the legacy identity is not consulted.
        GoogleCloudConnector none = connector(null, serviceAccount(null), OAuth2AuthConfig.NONE);
        assertSame(OAuth2AuthConfig.NONE, none.getEffectiveAuthConfig());
        assertFalse(none.getEffectiveAuthConfig().getType().isActive());
    }

    @Test
    public void theLegacyIdentityIsRequiredOnlyWithoutAnExplicitBlock() {
        Map<String, String> keyOnly = Map.of(GoogleCloudConnector.PRIVATE_KEY_FIELD, "k");

        connector(null, keyOnly, jwtBearer());
        connector(null, Map.of(), OAuth2AuthConfig.NONE);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> connector(null, keyOnly, null));
        assertEquals("Missing credential", e.getMessage());
    }

    @Test
    public void anExplicitBlock_survivesTheStoredAndTheStreamRoundTrips() throws IOException {
        GoogleCloudConnector original = connector(null, serviceAccount(null), jwtBearer());

        Connector stored = Connector.createConnector(parser(toJson(original)));
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        GoogleCloudConnector streamed = new GoogleCloudConnector(out.bytes().streamInput());

        assertTrue(stored instanceof GoogleCloudConnector);
        assertEquals(jwtBearer(), stored.getAuthConfig());
        assertEquals(jwtBearer(), streamed.getAuthConfig());
        assertEquals(original, streamed);
    }

    @Test
    public void aLegacyConnector_storesAndShowsNoAuthConfig() throws IOException {
        GoogleCloudConnector connector = serviceAccountConnector();

        assertNull(connector.getAuthConfig());
        assertFalse(toJson(connector).contains("auth_config"));
        assertNull(Connector.createConnector(parser(toJson(connector))).getAuthConfig());
    }
}
