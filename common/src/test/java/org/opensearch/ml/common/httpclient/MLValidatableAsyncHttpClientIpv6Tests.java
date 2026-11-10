/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertEquals;
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
 * IPv6 classification of the connector private-IP check, through both {@code validate()} and the {@code execute()}
 * path that connector requests take. Cases contributed by Tony Piazza (@tonypiazza).
 */
public class MLValidatableAsyncHttpClientIpv6Tests {
    private static final boolean PRIVATE_IP_DISABLED = false;

    private static MLValidatableAsyncHttpClient client(SdkAsyncHttpClient delegate) {
        return new MLValidatableAsyncHttpClient(delegate, PRIVATE_IP_DISABLED, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void validate_rejectsIpv4LoopbackPrivateAndMetadata() {
        MLValidatableAsyncHttpClient c = client(mock(SdkAsyncHttpClient.class));
        for (String ip : new String[] { "127.0.0.1", "10.0.0.1" }) {
            IllegalArgumentException e = assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> c.validate("http://" + ip + "/", "http", ip, 80, PRIVATE_IP_DISABLED)
            );
            assertEquals("Remote inference host name has private ip address: " + ip, e.getMessage());
        }
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> c.validate("http://169.254.169.254/", "http", "169.254.169.254", 80, PRIVATE_IP_DISABLED)
        );
        assertEquals("Remote inference host name has restricted ip address: 169.254.169.254", e.getMessage());
    }

    @Test
    public void validate_rejectsIpv6LoopbackAndDeprecatedSiteLocal() {
        MLValidatableAsyncHttpClient c = client(mock(SdkAsyncHttpClient.class));
        for (String ip : new String[] { "::1", "fec0::1" }) {
            assertThrows(ip, IllegalArgumentException.class, () -> c.validate("http://[" + ip + "]/", "http", ip, 80, PRIVATE_IP_DISABLED));
        }
    }

    @Test
    public void validate_rejectsIpv6UniqueLocalLinkLocalAndMetadata() {
        MLValidatableAsyncHttpClient c = client(mock(SdkAsyncHttpClient.class));
        for (String ip : new String[] { "fd00::1", "fc00::1", "fe80::1", "fd00:ec2::254" }) {
            assertThrows(ip, IllegalArgumentException.class, () -> c.validate("http://[" + ip + "]/", "http", ip, 80, PRIVATE_IP_DISABLED));
            assertThrows(
                ip,
                IllegalArgumentException.class,
                () -> c.validate("http://[" + ip + "]/", "http", "[" + ip + "]", 80, PRIVATE_IP_DISABLED)
            );
        }
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
