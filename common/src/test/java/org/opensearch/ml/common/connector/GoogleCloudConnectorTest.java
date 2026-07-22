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
