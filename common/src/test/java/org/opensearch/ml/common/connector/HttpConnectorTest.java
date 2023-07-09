/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class HttpConnectorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    Function<String, String> encryptFunction;
    Function<String, String> decryptFunction;

    @Before
    public void setUp() {
        encryptFunction = s -> "encrypted: "+s.toLowerCase(Locale.ROOT);
        decryptFunction = s -> "decrypted: "+s.toUpperCase(Locale.ROOT);
    }

    @Test
    public void constructor_InvalidProtocol() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported connector protocol. Please use one of [aws_sigv4, http]");

        HttpConnector.builder().protocol("wrong protocol").build();
    }

    @Test
    public void writeTo() throws IOException {
        HttpConnector connector = createHttpConnector();

        BytesStreamOutput output = new BytesStreamOutput();
        connector.writeTo(output);

        HttpConnector connector2 = new HttpConnector(output.bytes().streamInput());
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void toXContent() throws IOException {
        HttpConnector connector = createHttpConnector();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{\"name\":\"test_connector_name\",\"version\":\"1\",\"description\":\"this is a test connector\"," +
                "\"protocol\":\"http\"," +
                "\"parameters\":{\"input\":\"test input value\"}," +
                "\"credential\":{\"key\":\"test_key_value\"}," +
                "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\"," +
                "\"headers\":{\"api_key\":\"${credential.key}\"},\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"," +
                "\"pre_process_function\":\"connector.pre_process.openai.embedding\"," +
                "\"post_process_function\":\"connector.post_process.openai.embedding\"}]," +
                "\"backend_roles\":[\"role1\",\"role2\"]," +
                "\"access\":\"public\"}", content);
    }


    @Test
    public void constructor_Parser() throws IOException {
        String jsonStr = "{\"name\":\"test_connector_name\",\"version\":\"1\",\"description\":\"this is a test connector\"," +
                "\"protocol\":\"http\"," +
                "\"parameters\":{\"input\":\"test input value\"}," +
                "\"credential\":{\"key\":\"test_key_value\"}," +
                "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\"," +
                "\"headers\":{\"api_key\":\"${credential.key}\"},\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"," +
                "\"pre_process_function\":\"connector.pre_process.openai.embedding\"," +
                "\"post_process_function\":\"connector.post_process.openai.embedding\"}]," +
                "\"backend_roles\":[\"role1\",\"role2\"]," +
                "\"access\":\"public\"}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();

        HttpConnector connector = new HttpConnector("http", parser);
        Assert.assertEquals("test_connector_name", connector.getName());
        Assert.assertEquals("1", connector.getVersion());
        Assert.assertEquals("this is a test connector", connector.getDescription());
        Assert.assertEquals("http", connector.getProtocol());
        Assert.assertEquals(AccessMode.PUBLIC, connector.getAccess());
        Assert.assertEquals(1, connector.getParameters().size());
        Assert.assertEquals("test input value", connector.getParameters().get("input"));
        Assert.assertEquals(1, connector.getActions().size());
        Assert.assertEquals(ConnectorAction.ActionType.PREDICT, connector.getActions().get(0).getActionType());
        Assert.assertEquals("POST", connector.getActions().get(0).getMethod());
        Assert.assertEquals("https://test.com", connector.getActions().get(0).getUrl());
    }

    @Test
    public void cloneConnector() {
        HttpConnector connector = createHttpConnector();
        Connector connector2 = connector.cloneConnector();
        Assert.assertEquals(connector, connector2);
    }

    @Test
    public void decrypt() {
        HttpConnector connector = createHttpConnector();
        connector.decrypt(decryptFunction);
        Map<String, String> decryptedCredential = connector.getDecryptedCredential();
        Assert.assertEquals(1, decryptedCredential.size());
        Assert.assertEquals("decrypted: TEST_KEY_VALUE", decryptedCredential.get("key"));
        Assert.assertNotNull(connector.getDecryptedHeaders());
        Assert.assertEquals(1, connector.getDecryptedHeaders().size());
        Assert.assertEquals("decrypted: TEST_KEY_VALUE", connector.getDecryptedHeaders().get("api_key"));

        connector.removeCredential();
        Assert.assertNull(connector.getCredential());
        Assert.assertNull(connector.getDecryptedCredential());
        Assert.assertNull(connector.getDecryptedHeaders());
    }

    @Test
    public void encrypted() {
        HttpConnector connector = createHttpConnector();
        connector.encrypt(encryptFunction);
        Map<String, String> credential = connector.getCredential();
        Assert.assertEquals(1, credential.size());
        Assert.assertEquals("encrypted: test_key_value", credential.get("key"));

        connector.removeCredential();
        Assert.assertNull(connector.getCredential());
        Assert.assertNull(connector.getDecryptedCredential());
        Assert.assertNull(connector.getDecryptedHeaders());
    }

    @Test
    public void getPredictEndpoint() {
        HttpConnector connector = createHttpConnector();
        Assert.assertEquals("https://test.com", connector.getPredictEndpoint());
    }

    @Test
    public void getPredictHttpMethod() {
        HttpConnector connector = createHttpConnector();
        Assert.assertEquals("POST", connector.getPredictHttpMethod());
    }

    private HttpConnector createHttpConnector() {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(actionType, method, url, headers, requestBody, preProcessFunction, postProcessFunction);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", "test input value");

        Map<String, String> credential = new HashMap<>();
        credential.put("key", "test_key_value");

        HttpConnector connector = HttpConnector.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version("1")
                .protocol("http")
                .parameters(parameters)
                .credential(credential)
                .actions(Arrays.asList(action))
                .backendRoles(Arrays.asList("role1", "role2"))
                .accessMode(AccessMode.PUBLIC)
                .build();
        return connector;
    }

}
