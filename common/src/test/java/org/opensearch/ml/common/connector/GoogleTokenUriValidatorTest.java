/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class GoogleTokenUriValidatorTest {

    private static Map<String, String> credential(String tokenUri) {
        Map<String, String> credential = new HashMap<>();
        credential.put("client_email", "sa@example.iam.gserviceaccount.com");
        credential.put("private_key", "k");
        if (tokenUri != null) {
            credential.put("token_uri", tokenUri);
        }
        return credential;
    }

    private static void assertRejected(String expectedMessage, Runnable check) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, check::run);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void validateTokenUri_acceptsOauth2Endpoint() {
        assertEquals(
            URI.create("https://oauth2.googleapis.com/token"),
            GoogleTokenUriValidator.validateTokenUri("https://oauth2.googleapis.com/token")
        );
    }

    @Test
    public void validateTokenUri_rejectsMalformedUri() {
        assertRejected("Invalid token_uri: https://oauth2.googleapis.com/to ken", () -> {
            GoogleTokenUriValidator.validateTokenUri("https://oauth2.googleapis.com/to ken");
        });
    }

    @Test
    public void validateCredential_rejectsEachDisallowedTokenUri() {
        String protocol = ConnectorProtocols.GOOGLE_CLOUD;
        assertRejected(
            "token_uri must be an https Google endpoint, got: http://mock:8080/token",
            () -> GoogleTokenUriValidator.validateCredential(protocol, Map.of(), credential("http://mock:8080/token"))
        );
        assertRejected(
            "token_uri host must be oauth2.googleapis.com, got: example.com",
            () -> GoogleTokenUriValidator.validateCredential(protocol, null, credential("https://example.com/token"))
        );
        assertRejected(
            "token_uri must use the default https port, got port: 8443",
            () -> GoogleTokenUriValidator.validateCredential(protocol, Map.of(), credential("https://oauth2.googleapis.com:8443/token"))
        );
    }

    @Test
    public void validateCredential_acceptsAllowedOrMissingTokenUri() {
        GoogleTokenUriValidator
            .validateCredential(ConnectorProtocols.GOOGLE_CLOUD, Map.of(), credential("https://oauth2.googleapis.com/token"));
        GoogleTokenUriValidator.validateCredential(ConnectorProtocols.GOOGLE_CLOUD, Map.of(), credential(null));
        GoogleTokenUriValidator.validateCredential(ConnectorProtocols.GOOGLE_CLOUD, Map.of(), null);
    }

    @Test
    public void validateCredential_ignoresOtherProtocolsAndAdcMode() {
        // Only the google_cloud service-account path sends a request to token_uri.
        GoogleTokenUriValidator.validateCredential(ConnectorProtocols.HTTP, Map.of(), credential("https://example.com/token"));
        GoogleTokenUriValidator.validateCredential(null, Map.of(), credential("https://example.com/token"));
        GoogleTokenUriValidator
            .validateCredential(ConnectorProtocols.GOOGLE_CLOUD, Map.of("auth_mode", "ADC"), credential("https://example.com/token"));
    }

    @Test
    public void validateCredentialAfterUpdate_checksOnlySuppliedCredential() {
        // No credential in the update: the stored one is encrypted and unchanged, so there is nothing to check.
        GoogleTokenUriValidator.validateCredentialAfterUpdate(ConnectorProtocols.GOOGLE_CLOUD, Map.of(), null, null, null);
        GoogleTokenUriValidator.validateCredentialAfterUpdate(ConnectorProtocols.GOOGLE_CLOUD, Map.of(), null, null, Map.of());
        assertRejected(
            "token_uri host must be oauth2.googleapis.com, got: example.com",
            () -> GoogleTokenUriValidator
                .validateCredentialAfterUpdate(
                    ConnectorProtocols.GOOGLE_CLOUD,
                    Map.of(),
                    null,
                    null,
                    credential("https://example.com/token")
                )
        );
    }

    @Test
    public void validateCredentialAfterUpdate_usesResultingProtocolAndParameters() {
        // Switching to google_cloud applies the rule; switching away from it does not.
        assertRejected(
            "token_uri host must be oauth2.googleapis.com, got: example.com",
            () -> GoogleTokenUriValidator
                .validateCredentialAfterUpdate(
                    ConnectorProtocols.HTTP,
                    Map.of(),
                    ConnectorProtocols.GOOGLE_CLOUD,
                    null,
                    credential("https://example.com/token")
                )
        );
        GoogleTokenUriValidator
            .validateCredentialAfterUpdate(
                ConnectorProtocols.GOOGLE_CLOUD,
                Map.of(),
                ConnectorProtocols.HTTP,
                null,
                credential("https://example.com/token")
            );
        // Parameters are merged, so a stored auth_mode=adc still applies when the update does not change it.
        GoogleTokenUriValidator
            .validateCredentialAfterUpdate(
                ConnectorProtocols.GOOGLE_CLOUD,
                Map.of("auth_mode", "adc"),
                null,
                Map.of("model", "m"),
                credential("https://example.com/token")
            );
        assertRejected(
            "token_uri host must be oauth2.googleapis.com, got: example.com",
            () -> GoogleTokenUriValidator
                .validateCredentialAfterUpdate(
                    ConnectorProtocols.GOOGLE_CLOUD,
                    Map.of("auth_mode", "adc"),
                    null,
                    Map.of("auth_mode", "service_account"),
                    credential("https://example.com/token")
                )
        );
    }
}
