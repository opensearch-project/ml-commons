/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.opensearch.ml.common.exception.MLValidationException;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.TlsKeyManagersProvider;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Extracts TLS configuration logic from MLHttpClientFactory.
 */
@Log4j2
final class MLTlsClientConfigurer {

    /**
     * Configures and builds an HTTP client with TLS settings.
     * 
     * @param builder The Netty HTTP client builder
     * @param keyManagers Key managers for client certificate authentication
     * @param trustManagers Trust managers for server certificate validation
     * @param skipSslVerification Whether to skip SSL certificate verification
     * @return Configured SdkAsyncHttpClient
     */
    static SdkAsyncHttpClient build(
        NettyNioAsyncHttpClient.Builder builder,
        KeyManager[] keyManagers,
        TrustManager[] trustManagers,
        boolean skipSslVerification
    ) {
        // mTLS + skipSslVerification defeats server certificate validation while still presenting a client
        // certificate - reject outright rather than merely warning, consistent with the upstream validation
        // in CertificateProcessor#validateCertificateConfig. Validated before any builder mutation below so
        // a rejected configuration never leaves the shared builder partially configured.
        if (skipSslVerification && keyManagers != null && keyManagers.length > 0) {
            throw new MLValidationException(
                "skip_ssl_verification cannot be enabled together with mutual_tls_enabled. Disabling server "
                    + "certificate validation while presenting a client certificate defeats the purpose of "
                    + "mutual TLS and allows man-in-the-middle attacks."
            );
        }

        // Configure client certificate authentication if key managers are provided
        if (keyManagers != null && keyManagers.length > 0) {
            TlsKeyManagersProvider keyManagersProvider = () -> keyManagers;
            builder.tlsKeyManagersProvider(keyManagersProvider);
            log.debug("Configured TLS key managers provider for client certificate authentication");
        }

        // Configure server certificate validation if trust managers are provided and SSL verification is enabled
        if (trustManagers != null && trustManagers.length > 0 && !skipSslVerification) {
            TlsTrustManagersProvider trustManagersProvider = () -> trustManagers;
            builder.tlsTrustManagersProvider(trustManagersProvider);
            log.debug("Configured TLS trust managers provider for server certificate validation");
        }

        // Build client with appropriate SSL verification settings
        if (skipSslVerification) {
            return builder.buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true).build());
        } else {
            return builder.build();
        }
    }
}
