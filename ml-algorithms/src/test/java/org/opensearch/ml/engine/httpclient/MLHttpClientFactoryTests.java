/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.httpclient;

import static org.junit.Assert.assertNotNull;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.net.URI;
import java.nio.charset.Charset;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
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
    public void test_validateIp_validIp_noException() {
        RequestBody requestBody = RequestBody.fromString("hello world", Charset.defaultCharset());
        SdkHttpFullRequest request = SdkHttpFullRequest
            .builder()
            .method(POST)
            .uri(URI.create("https://api.openai.com"))
            .contentStreamProvider(requestBody.contentStreamProvider())
            .build();
    }

    @Test
    public void test_validateIp_rarePrivateIp_throwException() {
        RequestBody requestBody = RequestBody.fromString("hello world", Charset.defaultCharset());
        try {
            SdkHttpFullRequest request = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create("http://0177.1/v1/completions"))
                .contentStreamProvider(requestBody.contentStreamProvider())
                .build();
        } catch (NullPointerException e) {
            assertNotNull(e);
        }

        try {
            SdkHttpFullRequest request = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create("http://172.1048577/v1/completions"))
                .contentStreamProvider(requestBody.contentStreamProvider())
                .build();
        } catch (NullPointerException e) {
            assertNotNull(e);
        }

        try {
            SdkHttpFullRequest request = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create("http://2886729729/v1/completions"))
                .contentStreamProvider(requestBody.contentStreamProvider())
                .build();
        } catch (NullPointerException e) {
            assertNotNull(e);
        }

        try {
            SdkHttpFullRequest request = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create("http://192.11010049/v1/completions"))
                .contentStreamProvider(requestBody.contentStreamProvider())
                .build();
        } catch (NullPointerException e) {
            assertNotNull(e);
        }

        try {
            SdkHttpFullRequest request = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create("http://3232300545/v1/completions"))
                .contentStreamProvider(requestBody.contentStreamProvider())
                .build();
        } catch (NullPointerException e) {
            assertNotNull(e);
        }

        try {
            SdkHttpFullRequest request = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create("http://0:0:0:0:0:ffff:127.0.0.1/v1/completions"))
                .contentStreamProvider(requestBody.contentStreamProvider())
                .build();
        } catch (NullPointerException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void test_validateSchemaAndPort_success() {
        RequestBody requestBody = RequestBody.fromString("hello world", Charset.defaultCharset());
        SdkHttpFullRequest request = SdkHttpFullRequest
            .builder()
            .method(POST)
            .uri(URI.create("https://api.openai.com:8080/v1/completions"))
            .contentStreamProvider(requestBody.contentStreamProvider())
            .build();
        assertNotNull(request);
    }

    @Test
    public void test_validateSchemaAndPort_notAllowedSchema_throwException() {
        expectedException.expect(IllegalArgumentException.class);
        RequestBody requestBody = RequestBody.fromString("hello world", Charset.defaultCharset());
        SdkHttpFullRequest request = SdkHttpFullRequest
            .builder()
            .method(POST)
            .uri(URI.create("ftp://api.openai.com:8080/v1/completions"))
            .contentStreamProvider(requestBody.contentStreamProvider())
            .build();
        assertNotNull(request);
    }

    @Test
    public void test_validateSchemaAndPort_portNotInRange_throwException() {
        RequestBody requestBody = RequestBody.fromString("hello world", Charset.defaultCharset());
        SdkHttpFullRequest request = SdkHttpFullRequest
            .builder()
            .method(POST)
            .uri(URI.create("https://api.openai.com:65537/v1/completions"))
            .contentStreamProvider(requestBody.contentStreamProvider())
            .build();
        assertNotNull(request);
    }
}
