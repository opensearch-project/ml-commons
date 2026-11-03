/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.ml.common.exception.MLValidationException;

import software.amazon.awssdk.http.TlsKeyManagersProvider;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

/**
 * Verifies that the TLS managers actually reach the HTTP client builder. The builder is mocked so
 * these assertions cover the provider wiring itself rather than only that a client was returned.
 */
public class MLTlsClientConfigurerTests {

    @Test
    public void test_build_keyManagersPresent_configuresKeyManagersProvider() {
        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
        KeyManager[] keyManagers = new KeyManager[] { mock(KeyManager.class) };

        MLTlsClientConfigurer.build(builder, keyManagers, null, false);

        ArgumentCaptor<TlsKeyManagersProvider> captor = ArgumentCaptor.forClass(TlsKeyManagersProvider.class);
        verify(builder).tlsKeyManagersProvider(captor.capture());
        assertArrayEquals("Provider must expose the supplied key managers", keyManagers, captor.getValue().keyManagers());
        verify(builder, never()).tlsTrustManagersProvider(any());
        verify(builder).build();
    }

    @Test
    public void test_build_trustManagersPresent_configuresTrustManagersProvider() {
        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
        TrustManager[] trustManagers = new TrustManager[] { mock(TrustManager.class) };

        MLTlsClientConfigurer.build(builder, null, trustManagers, false);

        ArgumentCaptor<TlsTrustManagersProvider> captor = ArgumentCaptor.forClass(TlsTrustManagersProvider.class);
        verify(builder).tlsTrustManagersProvider(captor.capture());
        assertArrayEquals("Provider must expose the supplied trust managers", trustManagers, captor.getValue().trustManagers());
        verify(builder, never()).tlsKeyManagersProvider(any());
    }

    @Test
    public void test_build_bothManagersPresent_configuresBothProviders() {
        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
        KeyManager[] keyManagers = new KeyManager[] { mock(KeyManager.class) };
        TrustManager[] trustManagers = new TrustManager[] { mock(TrustManager.class) };

        MLTlsClientConfigurer.build(builder, keyManagers, trustManagers, false);

        verify(builder).tlsKeyManagersProvider(any());
        verify(builder).tlsTrustManagersProvider(any());
    }

    @Test
    public void test_build_trustManagersWithSkipSslVerification_skipsTrustManagersProvider() {
        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
        TrustManager[] trustManagers = new TrustManager[] { mock(TrustManager.class) };

        // skipSslVerification delegates trust to TRUST_ALL_CERTIFICATES, so the provider must not be set
        MLTlsClientConfigurer.build(builder, null, trustManagers, true);

        verify(builder, never()).tlsTrustManagersProvider(any());
        verify(builder).buildWithDefaults(any());
        verify(builder, never()).build();
    }

    @Test
    public void test_build_emptyManagerArrays_configuresNoProviders() {
        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);

        MLTlsClientConfigurer.build(builder, new KeyManager[0], new TrustManager[0], false);

        verify(builder, never()).tlsKeyManagersProvider(any());
        verify(builder, never()).tlsTrustManagersProvider(any());
    }

    @Test
    public void test_build_keyManagersWithSkipSslVerification_throwsWithoutMutatingBuilder() {
        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
        KeyManager[] keyManagers = new KeyManager[] { mock(KeyManager.class) };

        assertThrows(MLValidationException.class, () -> MLTlsClientConfigurer.build(builder, keyManagers, null, true));

        // A rejected configuration must never leave the shared builder partially configured
        verifyNoInteractions(builder);
    }
}
