/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.MLStaticMockBase;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

public class GoogleCredentialProviderTest extends MLStaticMockBase {

    @Test
    public void getAccessToken_ReturnsTokenValue() {
        // Real credentials backed by a non-expiring static token: refreshIfExpired() is a
        // no-op (no network), and getAccessToken() (a final method, so not mockable) returns
        // the token value.
        AccessToken token = new AccessToken("ya29.test-token", new Date(Long.MAX_VALUE));
        GoogleCredentials credentials = GoogleCredentials.create(token);

        GoogleCredentialProvider provider = new GoogleCredentialProvider(credentials);

        assertEquals("ya29.test-token", provider.getAccessToken());
    }

    @Test(expected = MLException.class)
    public void getAccessToken_RefreshFailure_WrapsInMLException() throws IOException {
        // refreshIfExpired() is non-final, so it can be stubbed to throw. The failure occurs
        // before the final getAccessToken() accessor is reached.
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        doThrow(new IOException("boom")).when(credentials).refreshIfExpired();

        GoogleCredentialProvider provider = new GoogleCredentialProvider(credentials);
        provider.getAccessToken();
    }

    @Test
    public void validateTokenUri_acceptsOauth2Endpoint() {
        assertEquals(
            URI.create("https://oauth2.googleapis.com/token"),
            GoogleCredentialProvider.validateTokenUri("https://oauth2.googleapis.com/token")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsOtherGoogleApisHost() {
        // token_uri is the OAuth2 token-mint endpoint; only oauth2.googleapis.com is a valid host.
        // Other *.googleapis.com surfaces (e.g. aiplatform) must not receive the signed JWT.
        GoogleCredentialProvider.validateTokenUri("https://us-central1-aiplatform.googleapis.com/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsBareGoogleApisHost() {
        GoogleCredentialProvider.validateTokenUri("https://googleapis.com/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsNonGoogleHost() {
        GoogleCredentialProvider.validateTokenUri("https://evil.example.com/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsInternalMetadataAddress() {
        GoogleCredentialProvider.validateTokenUri("http://169.254.169.254/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsNonHttpsScheme() {
        GoogleCredentialProvider.validateTokenUri("http://oauth2.googleapis.com/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsHostSuffixSpoofing() {
        // Ensure endsWith(".googleapis.com") cannot be bypassed by a lookalike domain.
        GoogleCredentialProvider.validateTokenUri("https://googleapis.com.evil.example/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsNonDefaultPortOnValidHost() {
        // Pinning the host alone still allows an arbitrary port on it, which would send the signed JWT
        // somewhere other than the real token endpoint.
        GoogleCredentialProvider.validateTokenUri("https://oauth2.googleapis.com:8443/token");
    }

    @Test
    public void validateTokenUri_acceptsExplicitDefaultPort() {
        assertEquals(
            URI.create("https://oauth2.googleapis.com:443/token"),
            GoogleCredentialProvider.validateTokenUri("https://oauth2.googleapis.com:443/token")
        );
    }

    @Test
    public void getAccessToken_MissingGrpcContextClass_WrapsInMLExceptionNamingTheModule() throws IOException {
        // The OAuth2 refresh path reaches google-http-client's OpenCensus instrumentation, which
        // requires io.grpc.Context. That class ships in the mandatory transport-grpc module, so this
        // only occurs on a distribution with modules/ stripped -- an unsupported configuration whose
        // raw NoClassDefFoundError names neither google_cloud nor the missing module.
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        doThrow(new NoClassDefFoundError("io/grpc/Context")).when(credentials).refreshIfExpired();

        GoogleCredentialProvider provider = new GoogleCredentialProvider(credentials);

        MLException e = assertThrows(MLException.class, provider::getAccessToken);
        assertTrue(e.getMessage().contains("transport-grpc"));
    }

    /** A throwaway RSA key generated per run, so no key material is committed. */
    private static String generatePkcs8PrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String der = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + der + "\n-----END PRIVATE KEY-----\n";
    }

    private static ConnectorAction predictAction() {
        return ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://us-central1-aiplatform.googleapis.com/v1/x:generateContent")
            .requestBody("{\"contents\":[]}")
            .build();
    }

    /** fromConnector reads the decrypted credential map; the identity function stands in for real decryption. */
    private static GoogleCloudConnector decrypted(GoogleCloudConnector connector) {
        connector
            .decrypt(PREDICT.name(), (keys, tenantId, listener) -> listener.onResponse(keys), null, ActionListener.wrap(r -> {}, e -> {}));
        return connector;
    }

    private static GoogleCloudConnector serviceAccountConnector(String privateKey, String tokenUri) {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, privateKey);
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "svc@my-project.iam.gserviceaccount.com");
        if (tokenUri != null) {
            credential.put(GoogleCloudConnector.TOKEN_URI_FIELD, tokenUri);
        }
        return decrypted(
            GoogleCloudConnector
                .googleCloudConnectorBuilder()
                .name("gcp")
                .version("1")
                .protocol(ConnectorProtocols.GOOGLE_CLOUD)
                .credential(credential)
                .actions(List.of(predictAction()))
                .build()
        );
    }

    private static GoogleCloudConnector adcConnector() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        return decrypted(
            GoogleCloudConnector
                .googleCloudConnectorBuilder()
                .name("gcp")
                .version("1")
                .protocol(ConnectorProtocols.GOOGLE_CLOUD)
                .parameters(parameters)
                .actions(List.of(predictAction()))
                .build()
        );
    }

    @Test
    public void fromConnector_ServiceAccountKey_BuildsProvider() throws Exception {
        assertNotNull(GoogleCredentialProvider.fromConnector(serviceAccountConnector(generatePkcs8PrivateKey(), null)));
    }

    @Test
    public void fromConnector_ServiceAccountKey_MalformedPrivateKey_WrapsInMLException() {
        GoogleCloudConnector connector = serviceAccountConnector("not-a-private-key", null);
        MLException e = assertThrows(MLException.class, () -> GoogleCredentialProvider.fromConnector(connector));
        assertTrue(e.getMessage().contains("service-account key"));
    }

    @Test
    public void fromConnector_AdcMode_UsesApplicationDefaultCredentials() {
        // Built before the static mock opens: mocking GoogleCredentials' statics also stubs create().
        GoogleCredentials adc = GoogleCredentials.create(new AccessToken("ya29.adc-token", new Date(Long.MAX_VALUE)));
        try (MockedStatic<GoogleCredentials> mocked = mockStatic(GoogleCredentials.class)) {
            mocked.when(GoogleCredentials::getApplicationDefault).thenReturn(adc);
            assertEquals("ya29.adc-token", GoogleCredentialProvider.fromConnector(adcConnector()).getAccessToken());
        }
    }

    @Test
    public void fromConnector_AdcMode_Unavailable_WrapsInMLException() {
        try (MockedStatic<GoogleCredentials> mocked = mockStatic(GoogleCredentials.class)) {
            mocked.when(GoogleCredentials::getApplicationDefault).thenThrow(new IOException("no ADC available"));
            MLException e = assertThrows(MLException.class, () -> GoogleCredentialProvider.fromConnector(adcConnector()));
            assertTrue(e.getMessage().contains("ADC/Workload Identity"));
        }
    }

    @Test
    public void fromConnector_InvalidTokenUri_ThrowsIllegalArgumentExceptionUnwrapped() throws Exception {
        // Documents current behavior: validateTokenUri throws IllegalArgumentException, which the
        // IOException-only catch in fromConnector does not wrap. Pinned so that changing the surfaced
        // type is a deliberate decision rather than an incidental one.
        GoogleCloudConnector connector = serviceAccountConnector(generatePkcs8PrivateKey(), "https://evil.example.com/token");
        assertThrows(IllegalArgumentException.class, () -> GoogleCredentialProvider.fromConnector(connector));
    }
}
