/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.httpclient;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.engine.algorithms.remote.ConnectorUtils;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLHttpClientFactoryTests {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_getSdkAsyncHttpClient_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory.getAsyncHttpClient();
        assertNotNull(client);
    }

    @Test
    public void test_validateIp_validIp_noException() throws Exception {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://api.openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        SdkHttpFullRequest request = ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        assertNotNull(request);
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws Exception {
        try {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://0254.020.00.01/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://172.1048577/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://2886729729/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://192.11010049/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://3232300545/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://0:0:0:0:0:ffff:127.0.0.1/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateSchemaAndPort_success() throws Exception {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://api.openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        SdkHttpFullRequest request = ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        assertNotNull(request);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Protocol is not http or https: ftp");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("ftp://api.openai.com:8080/v1/completions")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        SdkHttpFullRequest request = ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
        assertNull(request);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: 65537");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://api.openai.com:65537/v1/completions")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
    }

    @Test
    public void test_validateSchemaAndPort_portNotANumber_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port is not a valid number: abc");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://api.openai.com:abc/v1/completions")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        ConnectorUtils.buildSdkRequest(connector, Map.of(), "hello world", SdkHttpMethod.POST);
    }
}
