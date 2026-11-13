/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.Collections;

import org.junit.Test;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

/**
 * IPv6 metadata addresses refused on the {@code execute()} path that connector requests take; the classification cases
 * are in {@link ConnectorAddressValidatorTests}. Cases contributed by Tony Piazza (@tonypiazza).
 */
public class MLValidatableAsyncHttpClientIpv6Tests {
    private static final boolean PRIVATE_IP_DISABLED = false;

    private static MLValidatableAsyncHttpClient client(SdkAsyncHttpClient delegate) {
        return new MLValidatableAsyncHttpClient(delegate, PRIVATE_IP_DISABLED, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void execute_blocksIpv6MetadataRequest() {
        SdkAsyncHttpClient delegate = mock(SdkAsyncHttpClient.class);
        assertThrows(IllegalArgumentException.class, () -> client(delegate).execute(request("http://[fd00:ec2::254]/latest/meta-data/")));
        verify(delegate, never()).execute(any());
    }

    @Test
    public void execute_blocksIpv4MetadataRequest() {
        SdkAsyncHttpClient delegate = mock(SdkAsyncHttpClient.class);
        assertThrows(IllegalArgumentException.class, () -> client(delegate).execute(request("http://169.254.169.254/latest/meta-data/")));
        verify(delegate, never()).execute(any());
    }

    private static AsyncExecuteRequest request(String url) {
        SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).uri(URI.create(url)).build();
        return AsyncExecuteRequest.builder().request(httpRequest).responseHandler(mock(SdkAsyncHttpResponseHandler.class)).build();
    }
}
