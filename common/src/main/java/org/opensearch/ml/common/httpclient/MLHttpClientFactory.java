/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.opensearch.secure_sm.AccessController.doPrivileged;

import java.time.Duration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.TlsKeyManagersProvider;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.utils.AttributeMap;

@Log4j2
public class MLHttpClientFactory {

    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled
    ) {
        return getAsyncHttpClient(connectionTimeout, readTimeout, maxConnections, connectorPrivateIpEnabled, false);
    }

    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification,
        SSLContext sslContext
    ) {
        return getAsyncHttpClient(
            connectionTimeout,
            readTimeout,
            maxConnections,
            connectorPrivateIpEnabled,
            skipSslVerification,
            sslContext,
            null,
            null,
            null
        );
    }

    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification
    ) {
        return getAsyncHttpClient(
            connectionTimeout,
            readTimeout,
            maxConnections,
            connectorPrivateIpEnabled,
            skipSslVerification,
            null,
            null,
            null,
            null
        );
    }

    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification,
        SSLContext sslContext,
        String clientDescription
    ) {
        return getAsyncHttpClient(
            connectionTimeout,
            readTimeout,
            maxConnections,
            connectorPrivateIpEnabled,
            skipSslVerification,
            sslContext,
            clientDescription,
            null,
            null
        );
    }

    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification,
        SSLContext sslContext,
        String clientDescription,
        KeyManager[] keyManagers,
        TrustManager[] trustManagers
    ) {
        return doPrivileged(() -> {
            if (skipSslVerification) {
                log
                    .warn(
                        "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                            + " attacks. Only use this setting in trusted environments."
                    );
            }

            String description = clientDescription != null ? clientDescription : "standard";
            boolean hasMutualTls = sslContext != null;

            log
                .debug(
                    "Creating MLHttpClient ({}) with connectionTimeout: {}, readTimeout: {}, maxConnections: {}, skipSslVerification: {}, mutualTLS: {}",
                    description,
                    connectionTimeout,
                    readTimeout,
                    maxConnections,
                    skipSslVerification,
                    hasMutualTls
                );

            NettyNioAsyncHttpClient.Builder clientBuilder = NettyNioAsyncHttpClient
                .builder()
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .maxConcurrency(maxConnections);

            SdkAsyncHttpClient delegate;

            if (keyManagers != null || trustManagers != null) {
                // Configure mutual TLS using AWS SDK v2 proper approach
                log.info("Configuring HTTP client with mutual TLS authentication using AWS SDK TLS providers");

                try {
                    // Configure the client builder with TLS providers
                    if (keyManagers != null && keyManagers.length > 0) {
                        // Create TLS key managers provider
                        TlsKeyManagersProvider keyManagersProvider = () -> keyManagers;
                        clientBuilder.tlsKeyManagersProvider(keyManagersProvider);
                        log.info("Configured TLS key managers provider for client certificate authentication");
                    }

                    if (trustManagers != null && trustManagers.length > 0 && !skipSslVerification) {
                        // Create TLS trust managers provider
                        TlsTrustManagersProvider trustManagersProvider = () -> trustManagers;
                        clientBuilder.tlsTrustManagersProvider(trustManagersProvider);
                        log.info("Configured TLS trust managers provider for server certificate validation");
                    }

                    // Create client with SSL verification settings
                    if (skipSslVerification) {
                        delegate = clientBuilder
                            .buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true).build());
                        log.info("Built HTTP client with SSL verification disabled");
                    } else {
                        delegate = clientBuilder.build();
                        log.info("Built HTTP client with custom TLS configuration");
                    }

                    log.info("Successfully configured HTTP client with AWS SDK TLS providers for mutual TLS");

                } catch (Exception e) {
                    log.error("Failed to configure AWS SDK TLS providers, falling back to default", e);
                    delegate = clientBuilder.build();
                }
            } else if (skipSslVerification) {
                // Configure to skip SSL verification
                delegate = clientBuilder
                    .buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true).build());
            } else {
                // Use default SSL configuration
                delegate = clientBuilder.build();
            }
            return new MLValidatableAsyncHttpClient(delegate, connectorPrivateIpEnabled);
        });
    }

}
