/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.httpclient;

import static org.junit.Assert.assertNotNull;

import java.net.UnknownHostException;

import org.apache.http.HttpHost;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
    public void test_validateIp_validIp_noException() throws UnknownHostException {
        MLHttpClientFactory.validateIp("api.openai.com");
    }

    @Test
    public void test_validateIp_invalidIp_throwException() throws UnknownHostException {
        expectedException.expect(UnknownHostException.class);
        MLHttpClientFactory.validateIp("www.zaniu.com");
    }

    @Test
    public void test_validateIp_privateIp_throwException() throws UnknownHostException {
        expectedException.expect(IllegalArgumentException.class);
        MLHttpClientFactory.validateIp("localhost");
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws UnknownHostException {
        try {
            MLHttpClientFactory.validateIp("0177.1");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validateIp("172.1048577");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validateIp("2886729729");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validateIp("192.11010049");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validateIp("3232300545");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validateIp("0:0:0:0:0:ffff:127.0.0.1");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateSchemaAndPort_success() {
        HttpHost httpHost = new HttpHost("api.openai.com", 8080, "https");
        MLHttpClientFactory.validateSchemaAndPort(httpHost);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() {
        expectedException.expect(IllegalArgumentException.class);
        HttpHost httpHost = new HttpHost("api.openai.com", 8080, "ftp");
        MLHttpClientFactory.validateSchemaAndPort(httpHost);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange_throwException() {
        expectedException.expect(IllegalArgumentException.class);
        HttpHost httpHost = new HttpHost("api.openai.com:65537", -1, "https");
        MLHttpClientFactory.validateSchemaAndPort(httpHost);
    }
}
