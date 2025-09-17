/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertNotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLHttpClientFactoryTests {

    private static final String TEST_HOST = "api.openai.com";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final AtomicBoolean PRIVATE_IP_DISABLED = new AtomicBoolean(false);
    private static final AtomicBoolean PRIVATE_IP_ENABLED = new AtomicBoolean(true);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_getSdkAsyncHttpClient_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory.getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100);
        assertNotNull(client);
    }

    @Test
    public void test_validateIp_validIp_noException() throws Exception {
        MLHttpClientFactory.validate(HTTP, TEST_HOST, 80, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTPS, TEST_HOST, 443, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTP, "127.0.0.1", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTPS, "127.0.0.1", 443, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "177.16.0.1", 80, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTP, "177.0.1.1", 80, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTP, "177.0.0.2", 80, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTP, "::ffff", 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws Exception {
        try {
            MLHttpClientFactory.validate(HTTP, "0254.020.00.01", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "172.1048577", 80, PRIVATE_IP_DISABLED);
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "2886729729", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "192.11010049", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "3232300545", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "153.24.76.232", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "177.0.0.1", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate(HTTP, "12.16.2.3", 80, PRIVATE_IP_DISABLED);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateIp_rarePrivateIp_NotThrowException() throws Exception {
        MLHttpClientFactory.validate(HTTP, "0254.020.00.01", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTPS, "0254.020.00.01", 443, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "172.1048577", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "2886729729", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "192.11010049", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "3232300545", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "0:0:0:0:0:ffff:127.0.0.1", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTPS, "0:0:0:0:0:ffff:127.0.0.1", 443, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "153.24.76.232", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTP, "10.24.76.186", 80, PRIVATE_IP_ENABLED);
        MLHttpClientFactory.validate(HTTPS, "10.24.76.186", 443, PRIVATE_IP_ENABLED);
    }

    @Test
    public void test_validateSchemaAndPort_success() throws Exception {
        MLHttpClientFactory.validate(HTTP, TEST_HOST, 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        MLHttpClientFactory.validate("ftp", TEST_HOST, 80, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange1_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: 65537");
        MLHttpClientFactory.validate(HTTPS, TEST_HOST, 65537, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange2_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: -10");
        MLHttpClientFactory.validate(HTTP, TEST_HOST, -10, PRIVATE_IP_DISABLED);
    }

    @Test
    public void test_validatePort_boundaries_success() throws Exception {
        MLHttpClientFactory.validate(HTTP, TEST_HOST, 65536, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTP, TEST_HOST, 0, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTP, TEST_HOST, -1, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(HTTPS, TEST_HOST, -1, PRIVATE_IP_DISABLED);
        MLHttpClientFactory.validate(null, TEST_HOST, -1, PRIVATE_IP_DISABLED);
    }

}
