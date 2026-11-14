/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

public class MLValidatableAsyncHttpClientTests {
    private static final boolean PRIVATE_IP_DISABLED = false;

    private final SdkAsyncHttpClient delegate = mock(SdkAsyncHttpClient.class);
    private final MLValidatableAsyncHttpClient client = new MLValidatableAsyncHttpClient(
        delegate,
        PRIVATE_IP_DISABLED,
        Collections.emptyList(),
        Collections.emptyList()
    );

    @Test
    public void execute_passesAnAllowedRequestToTheDelegate() {
        CompletableFuture<Void> response = new CompletableFuture<>();
        when(delegate.execute(any())).thenReturn(response);
        AsyncExecuteRequest request = request("https://203.0.113.1/v1/predict");

        assertSame(response, client.execute(request));
        verify(delegate).execute(request);
    }

    @Test
    public void execute_refusesBeforeCallingTheDelegate() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> client.execute(request("http://10.0.0.1/latest/meta-data/"))
        );
        assertEquals("Remote inference host name has private ip address: 10.0.0.1", e.getMessage());
        verify(delegate, never()).execute(any());
    }

    @Test
    public void close_closesTheDelegate() {
        client.close();
        verify(delegate).close();
    }

    private static AsyncExecuteRequest request(String url) {
        SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).uri(URI.create(url)).build();
        return AsyncExecuteRequest.builder().request(httpRequest).responseHandler(mock(SdkAsyncHttpResponseHandler.class)).build();
    }
}
