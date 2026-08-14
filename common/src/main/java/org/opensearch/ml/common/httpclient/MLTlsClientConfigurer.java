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
        if (skipSslVerification) {
            // mTLS + skipSslVerification defeats server certificate validation while still presenting a
            // client certificate - reject outright rather than merely warning, consistent with the upstream
            // validation in CertificateProcessor#validateCertificateConfig. Checked before any builder
            // mutation so a rejected configuration never leaves the shared builder partially configured.
            if (hasManagers(keyManagers)) {
                throw new MLValidationException(
                    "skip_ssl_verification cannot be enabled together with mutual_tls_enabled. Disabling server "
                        + "certificate validation while presenting a client certificate defeats the purpose of "
                        + "mutual TLS and allows man-in-the-middle attacks."
                );
            }
            // Trust is delegated to TRUST_ALL_CERTIFICATES, so no trust managers provider is configured.
            return builder.buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true).build());
        }

        // Configure client certificate authentication if key managers are provided
        if (hasManagers(keyManagers)) {
            builder.tlsKeyManagersProvider(() -> keyManagers);
            log.debug("Configured TLS key managers provider for client certificate authentication");
        }

        // Configure server certificate validation if trust managers are provided
        if (hasManagers(trustManagers)) {
            builder.tlsTrustManagersProvider(() -> trustManagers);
            log.debug("Configured TLS trust managers provider for server certificate validation");
        }

        return builder.build();
    }

    private static boolean hasManagers(Object[] managers) {
        return managers != null && managers.length > 0;
    }
}
