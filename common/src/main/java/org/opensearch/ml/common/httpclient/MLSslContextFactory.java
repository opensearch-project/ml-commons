/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.opensearch.secure_sm.AccessController.doPrivileged;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLValidationException;

import lombok.extern.log4j.Log4j2;

/**
 * Builds the {@link SSLContext} used by the MCP connector transports, which reach remote services
 * through the JDK's {@code java.net.http.HttpClient}. {@link MLHttpClientFactory} takes
 * {@code KeyManager[]}/{@code TrustManager[]} directly through the AWS SDK's provider hooks; the JDK
 * client only accepts an assembled {@code SSLContext}.
 */
@Log4j2
public final class MLSslContextFactory {

    private MLSslContextFactory() {}

    /**
     * Builds the SSLContext implied by the connector client configuration, or {@code null} when no
     * TLS customization is requested so the caller keeps the JDK client's default context.
     *
     * @throws MLValidationException if the mutual TLS configuration is invalid
     */
    public static SSLContext create(
        ConnectorClientConfig config,
        Map<String, String> decryptedCredentials,
        CertificateProcessor certificateProcessor
    ) {
        if (config == null) {
            return null;
        }

        CertificateProcessor.MtlsManagers mtlsManagers = certificateProcessor.resolveMtls(config, decryptedCredentials);

        if (mtlsManagers != null) {
            return buildContext(mtlsManagers.getKeyManagers(), mtlsManagers.getTrustManagers(), "mutual TLS");
        }

        if (Boolean.TRUE.equals(config.getSkipSslVerification())) {
            log
                .warn(
                    "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                        + " attacks. Only use this setting in trusted environments."
                );
            return buildContext(null, new TrustManager[] { new TrustAllX509TrustManager() }, "trust-all");
        }

        return null;
    }

    private static SSLContext buildContext(KeyManager[] keyManagers, TrustManager[] trustManagers, String description) {
        return doPrivileged(() -> {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagers, trustManagers, null);
                return sslContext;
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                log.error("Failed to build {} SSLContext: {}", description, e.getMessage());
                throw new MLException("Failed to build " + description + " SSLContext: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Extends {@link X509ExtendedTrustManager} rather than implementing {@code X509TrustManager} on
     * purpose: the JDK performs hostname verification inside the trust manager, so a plain
     * implementation would be wrapped in a delegate that still enforces it, leaving
     * {@code skip_ssl_verification} only half applied.
     */
    private static final class TrustAllX509TrustManager extends X509ExtendedTrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
