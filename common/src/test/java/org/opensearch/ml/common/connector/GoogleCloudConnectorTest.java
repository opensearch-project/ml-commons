/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.TriConsumer;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class GoogleCloudConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    TriConsumer<List<String>, String, ActionListener<List<String>>> encryptFunction = (s, v, t) -> t
        .onResponse(List.of(s.stream().map(x -> "encrypted: " + x.toLowerCase(Locale.ROOT)).toArray(String[]::new)));
    TriConsumer<List<String>, String, ActionListener<List<String>>> decryptFunction = (s, v, t) -> t
        .onResponse(List.of(s.stream().map(x -> "decrypted: " + x.toUpperCase(Locale.ROOT)).toArray(String[]::new)));

    @Test
    public void constructor_SaKey_MissingPrivateKey() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        GoogleCloudConnector.googleCloudConnectorBuilder().protocol(ConnectorProtocols.GOOGLE_CLOUD).credential(credential).build();
    }

    @Test
    public void constructor_SaKey_Valid() {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "test_private_key");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(credential)
            .build();
        Assert.assertNotNull(connector);

        TestHelper.endecryptCredentials(connector, encryptFunction, true);
        TestHelper.endecryptCredentials(connector, decryptFunction, false);
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_PRIVATE_KEY", connector.getPrivateKey());
        Assert.assertEquals("decrypted: ENCRYPTED: SA@PROJECT.IAM.GSERVICEACCOUNT.COM", connector.getClientEmail());
    }

    @Test
    public void constructor_Adc_Valid_NoCredential() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .build();
        Assert.assertNotNull(connector);
        Assert.assertTrue(connector.useAdc());
    }

    @Test
    public void constructor_Adc_RejectsMixWithSaCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("auth_mode=adc must not include service-account credentials");

        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "should_not_be_here");
        GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .credential(credential)
            .build();
    }

    @Test
    public void getScopes_DefaultsWhenAbsent() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .build();
        Assert.assertEquals(GoogleCloudConnector.DEFAULT_SCOPE, connector.getScopes());
    }

    @Test
    public void getScopes_HonorsOverride() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        parameters.put(GoogleCloudConnector.SCOPES_FIELD, "https://www.googleapis.com/auth/custom-scope");
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .build();
        Assert.assertEquals("https://www.googleapis.com/auth/custom-scope", connector.getScopes());
    }

    @Test
    public void decrypt_AdcMode_LeavesNonNullDecryptedCredential() {
        // ADC mode has no credential; decrypt() must still leave a non-null (empty) decryptedCredential
        // so the SA-key accessors read an empty map (return null) instead of throwing NPE.
        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .build();

        TestHelper.endecryptCredentials(connector, decryptFunction, false);

        Assert.assertNull(connector.getPrivateKey());
        Assert.assertNull(connector.getClientEmail());
        Assert.assertEquals(GoogleCloudConnector.DEFAULT_TOKEN_URI, connector.getTokenUri());
    }

    private static final String VERSIONLESS_BATCH_URL =
        "https://us-central1-aiplatform.googleapis.com/projects/p/locations/l/batchPredictionJobs";
    private static final List<String> TRUSTED_VERTEX_REGEX = List.of("^https://([a-z0-9][a-z0-9-]*-)?aiplatform\\.googleapis\\.com/.*$");

    private static GoogleCloudConnector batchConnector(String batchUrl, Map<String, String> parameters) {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "pk");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        ConnectorAction batchAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
            .method("POST")
            .url(batchUrl)
            .requestBody("{}")
            .build();
        return GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .credential(credential)
            .actions(List.of(batchAction))
            .build();
    }

    @Test
    public void storedConnector_versionlessBatchUrl_stillParses() throws IOException {
        // A connector persisted before create-time batch URL validation existed must stay readable after
        // upgrade: stored connectors are re-parsed through the XContent constructor on every GET, update,
        // deploy and predict, so rejecting it there would make it unloadable and unfixable.
        String jsonStr = "{\"name\":\"gcp\",\"version\":\"1\",\"protocol\":\"google_cloud\","
            + "\"credential\":{\"private_key\":\"pk\",\"client_email\":\"sa@project.iam.gserviceaccount.com\"},"
            + "\"actions\":[{\"action_type\":\"BATCH_PREDICT\",\"method\":\"POST\",\"url\":\""
            + VERSIONLESS_BATCH_URL
            + "\",\"request_body\":\"{}\"}]}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        Connector connector = Connector.createConnector(parser);

        Assert.assertTrue(connector instanceof GoogleCloudConnector);
        Assert.assertEquals(VERSIONLESS_BATCH_URL, connector.getActions().get(0).getUrl());
    }

    @Test
    public void storedConnector_versionlessBatchUrl_survivesStreamRoundTrip() throws IOException {
        GoogleCloudConnector connector = batchConnector(VERSIONLESS_BATCH_URL, null);
        BytesStreamOutput output = new BytesStreamOutput();
        connector.writeTo(output);

        GoogleCloudConnector copy = new GoogleCloudConnector(output.bytes().streamInput());

        Assert.assertEquals(VERSIONLESS_BATCH_URL, copy.getActions().get(0).getUrl());
    }

    @Test
    public void validateConnectorURL_batchPredictUrlMissingVersionSegment_throws() {
        GoogleCloudConnector connector = batchConnector(VERSIONLESS_BATCH_URL, null);

        IllegalArgumentException e = Assert
            .assertThrows(IllegalArgumentException.class, () -> connector.validateConnectorURL(TRUSTED_VERTEX_REGEX));

        Assert
            .assertEquals(
                "Vertex AI batch_predict endpoint must contain a '/v1/' or '/v1beta<N>/' path segment, got: " + VERSIONLESS_BATCH_URL,
                e.getMessage()
            );
    }

    @Test
    public void validateConnectorURL_untrustedBatchUrl_reportsTrustFailureFirst() {
        GoogleCloudConnector connector = batchConnector("https://evil.example.com/projects/p/batchPredictionJobs", null);

        IllegalArgumentException e = Assert
            .assertThrows(IllegalArgumentException.class, () -> connector.validateConnectorURL(TRUSTED_VERTEX_REGEX));

        Assert.assertEquals("Connector URL is not matching the trusted connector endpoint regex", e.getMessage());
    }

    @Test
    public void validateConnectorURL_batchPredictUrlWithV1_ok() {
        batchConnector("https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/l/batchPredictionJobs", null)
            .validateConnectorURL(TRUSTED_VERTEX_REGEX);
    }

    @Test
    public void validateConnectorURL_batchPredictUrlWithV1beta1_ok() {
        batchConnector("https://us-central1-aiplatform.googleapis.com/v1beta1/projects/p/locations/l/batchPredictionJobs", null)
            .validateConnectorURL(TRUSTED_VERTEX_REGEX);
    }

    @Test
    public void validateConnectorURL_batchPredictVersionFromParameters_ok() {
        // The runtime check resolves ${parameters.*} before matching, so the create-time check must too.
        batchConnector(
            "https://${parameters.region}-aiplatform.googleapis.com/${parameters.api_version}/projects/p/locations/l/batchPredictionJobs",
            Map.of("region", "us-central1", "api_version", "v1")
        ).validateConnectorURL(TRUSTED_VERTEX_REGEX);
    }

    @Test
    public void validateConnectorURL_noBatchPredictAction_ok() {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "pk");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        // A PREDICT action whose URL has no version segment must NOT trigger the batch_predict check.
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://us-central1-aiplatform.googleapis.com/projects/p/locations/l/x:generateContent")
            .requestBody("{}")
            .build();
        GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(credential)
            .actions(List.of(predictAction))
            .build()
            .validateConnectorURL(TRUSTED_VERTEX_REGEX);
    }

    @Test
    public void streamInput_RoundTrip() throws IOException {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "pk");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        GoogleCloudConnector original = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp-connector")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(credential)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        GoogleCloudConnector restored = new GoogleCloudConnector(in);

        Assert.assertEquals(original.getName(), restored.getName());
        Assert.assertEquals(original.getProtocol(), restored.getProtocol());
        Assert.assertEquals(original, restored);
    }

    @Test
    public void cloneConnector_ReturnsEquivalent() {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "pk");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        GoogleCloudConnector original = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp-connector")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(credential)
            .build();
        Connector clone = original.cloneConnector();
        Assert.assertTrue(clone instanceof GoogleCloudConnector);
        Assert.assertEquals(original.getName(), clone.getName());
        Assert.assertEquals(original, clone);
    }

    @Test
    public void createConnector_ResolvesGoogleCloudClass() throws IOException {
        Map<String, String> credential = new HashMap<>();
        credential.put(GoogleCloudConnector.PRIVATE_KEY_FIELD, "pk");
        credential.put(GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@project.iam.gserviceaccount.com");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://us-central1-aiplatform.googleapis.com/v1/x:generateContent")
            .requestBody("{}")
            .build();
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(credential)
            .actions(List.of(predictAction))
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        Connector resolved = Connector.createConnector(parser);
        Assert.assertTrue(resolved instanceof GoogleCloudConnector);
        Assert.assertEquals(ConnectorProtocols.GOOGLE_CLOUD, resolved.getProtocol());
    }
}
