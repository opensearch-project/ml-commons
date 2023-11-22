/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SESSION_TOKEN_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class AwsConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    Function<String, String> encryptFunction;
    Function<String, String> decryptFunction;

    @Before
    public void setUp() {
        encryptFunction = s -> "encrypted: " + s.toLowerCase(Locale.ROOT);
        decryptFunction = s -> "decrypted: " + s.toUpperCase(Locale.ROOT);
    }

    @Test
    public void constructor_NullCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        AwsConnector.awsConnectorBuilder().protocol(ConnectorProtocols.AWS_SIGV4).build();
    }

    @Test
    public void constructor_NullAccessKey() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        Map<String, String> credential = new HashMap<>();
        AwsConnector.awsConnectorBuilder().protocol(ConnectorProtocols.AWS_SIGV4).credential(credential).build();
    }

    @Test
    public void constructor_NullSecretKey() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        AwsConnector.awsConnectorBuilder().protocol(ConnectorProtocols.AWS_SIGV4).credential(credential).build();
    }

    @Test
    public void constructor_NullServiceName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing service name");

        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        AwsConnector.awsConnectorBuilder().protocol(ConnectorProtocols.AWS_SIGV4).credential(credential).build();
    }

    @Test
    public void constructor_NullRegion() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing region");

        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        Map<String, String> parameters = new HashMap<>();
        parameters.put(SERVICE_NAME_FIELD, "test_service");
        AwsConnector.awsConnectorBuilder().protocol(ConnectorProtocols.AWS_SIGV4).credential(credential).parameters(parameters).build();
    }

    @Test
    public void constructor_NoPredictAction() {
        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        credential.put(REGION_FIELD, "test_region");
        Map<String, String> parameters = new HashMap<>();
        parameters.put(SERVICE_NAME_FIELD, "test_service");
        AwsConnector connector = AwsConnector
            .awsConnectorBuilder()
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .credential(credential)
            .parameters(parameters)
            .build();
        Assert.assertNotNull(connector);

        connector.encrypt(encryptFunction);
        connector.decrypt(decryptFunction);
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_ACCESS_KEY", connector.getAccessKey());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_SECRET_KEY", connector.getSecretKey());
        Assert.assertEquals(null, connector.getSessionToken());
        Assert.assertEquals("test_service", connector.getServiceName());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_REGION", connector.getRegion());
    }

    @Test
    public void constructor_Parser() throws IOException {
        AwsConnector awsConnector = createAwsConnector();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        awsConnector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        AwsConnector connector = new AwsConnector(awsConnector.getProtocol(), parser);
        Assert.assertEquals(awsConnector, connector);
    }

    @Test
    public void constructor() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test input value");
        parameters.put(SERVICE_NAME_FIELD, "test_service");
        parameters.put(REGION_FIELD, "us-west-2");
        parameters.put("endpoint", "test.com");

        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        credential.put(SESSION_TOKEN_FIELD, "test_session_token");
        String url = "https://${parameters.endpoint}/model1";

        AwsConnector connector = createAwsConnector(parameters, credential, url);
        connector.encrypt(encryptFunction);
        connector.decrypt(decryptFunction);
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_ACCESS_KEY", connector.getAccessKey());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_SECRET_KEY", connector.getSecretKey());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_SESSION_TOKEN", connector.getSessionToken());
        Assert.assertEquals("test_service", connector.getServiceName());
        Assert.assertEquals("us-west-2", connector.getRegion());
        Assert.assertEquals("https://test.com/model1", connector.getPredictEndpoint(parameters));
    }

    @Test
    public void constructor_NoParameter() {
        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        credential.put(SESSION_TOKEN_FIELD, "test_session_token");
        credential.put(SERVICE_NAME_FIELD, "test_service");
        credential.put(REGION_FIELD, "us-west-2");

        String url = "https://test.com";
        AwsConnector connector = createAwsConnector(null, credential, url);
        connector.encrypt(encryptFunction);
        connector.decrypt(decryptFunction);
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_ACCESS_KEY", connector.getAccessKey());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_SECRET_KEY", connector.getSecretKey());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_SESSION_TOKEN", connector.getSessionToken());
        Assert.assertEquals("decrypted: ENCRYPTED: TEST_SERVICE", connector.getServiceName());
        Assert.assertEquals("decrypted: ENCRYPTED: US-WEST-2", connector.getRegion());
        Assert.assertEquals("https://test.com", connector.getPredictEndpoint(null));
    }

    @Test
    public void cloneConnector() {
        AwsConnector connector = createAwsConnector();
        Connector connector2 = connector.cloneConnector();
        Assert.assertEquals(connector, connector2);
    }

    private AwsConnector createAwsConnector() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test input value");
        parameters.put(SERVICE_NAME_FIELD, "test_service");
        parameters.put(REGION_FIELD, "us-west-2");

        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        credential.put(SESSION_TOKEN_FIELD, "test_session_token");
        String url = "https://test.com";
        return createAwsConnector(parameters, credential, url);
    }

    private AwsConnector createAwsConnector(Map<String, String> parameters, Map<String, String> credential, String url) {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(
            actionType,
            method,
            url,
            headers,
            requestBody,
            preProcessFunction,
            postProcessFunction
        );

        AwsConnector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test_connector_name")
            .description("this is a test connector")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(action))
            .backendRoles(Arrays.asList("role1", "role2"))
            .accessMode(AccessMode.PUBLIC)
            .build();
        return connector;
    }

}
