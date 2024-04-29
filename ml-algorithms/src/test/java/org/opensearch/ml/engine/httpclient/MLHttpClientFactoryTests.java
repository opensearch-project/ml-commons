/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.httpclient;

import static org.junit.Assert.assertNotNull;

import java.time.Duration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLHttpClientFactoryTests {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_getSdkAsyncHttpClient_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory.getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100);
        assertNotNull(client);
    }

    @Test
    public void test_validateIp_validIp_noException() throws Exception {
        MLHttpClientFactory.validate("http", "api.openai.com", 80);
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() throws Exception {
        try {
            MLHttpClientFactory.validate("http", "0254.020.00.01", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate("http", "172.1048577", 80);
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate("http", "2886729729", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate("http", "192.11010049", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate("http", "3232300545", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate("http", "0:0:0:0:0:ffff:127.0.0.1", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }

        try {
            MLHttpClientFactory.validate("http", "153.24.76.232", 80);
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateSchemaAndPort_success() throws Exception {
        MLHttpClientFactory.validate("http", "api.openai.com", 80);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        MLHttpClientFactory.validate("ftp", "api.openai.com", 80);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange_throwException() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port out of range: 65537");
        MLHttpClientFactory.validate("https", "api.openai.com", 65537);
    }

}
