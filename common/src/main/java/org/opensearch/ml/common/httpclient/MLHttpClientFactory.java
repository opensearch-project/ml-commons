/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.opensearch.secure_sm.AccessController.doPrivileged;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import io.netty.handler.ssl.SslContext;
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

            if (sslContext != null || keyManagers != null || trustManagers != null) {
                // Configure mutual TLS using AWS SDK v2 proper approach
                log.info("Configuring HTTP client with mutual TLS authentication using AWS SDK TLS providers");

                try {
                    // Use provided managers or extract from SSL context
                    final KeyManager[] effectiveKeyManagers;
                    final TrustManager[] effectiveTrustManagers;

                    if (keyManagers != null) {
                        effectiveKeyManagers = keyManagers;
                    } else if (sslContext != null) {
                        effectiveKeyManagers = extractKeyManagers(sslContext);
                    } else {
                        effectiveKeyManagers = null;
                    }

                    if (trustManagers != null) {
                        effectiveTrustManagers = trustManagers;
                    } else if (sslContext != null) {
                        effectiveTrustManagers = extractTrustManagers(sslContext);
                    } else {
                        effectiveTrustManagers = null;
                    }

                    // Configure the client builder with TLS providers
                    if (effectiveKeyManagers != null && effectiveKeyManagers.length > 0) {
                        // Create TLS key managers provider
                        TlsKeyManagersProvider keyManagersProvider = () -> effectiveKeyManagers;
                        clientBuilder.tlsKeyManagersProvider(keyManagersProvider);
                        log.info("Configured TLS key managers provider for client certificate authentication");
                    }

                    if (effectiveTrustManagers != null && effectiveTrustManagers.length > 0 && !skipSslVerification) {
                        // Create TLS trust managers provider
                        TlsTrustManagersProvider trustManagersProvider = () -> effectiveTrustManagers;
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

    /**
     * Extract key managers from SSL context for client certificate authentication
     */
    private static KeyManager[] extractKeyManagers(SSLContext sslContext) {
        try {
            // Use reflection to access the SSL context's key managers
            java.lang.reflect.Field contextSpiField = sslContext.getClass().getDeclaredField("contextSpi");
            contextSpiField.setAccessible(true);
            Object contextSpi = contextSpiField.get(sslContext);

            java.lang.reflect.Field keyManagerField = contextSpi.getClass().getDeclaredField("keyManager");
            keyManagerField.setAccessible(true);
            Object keyManagerObj = keyManagerField.get(contextSpi);

            if (keyManagerObj instanceof KeyManager[]) {
                return (KeyManager[]) keyManagerObj;
            } else if (keyManagerObj instanceof KeyManager) {
                return new KeyManager[] { (KeyManager) keyManagerObj };
            }

            log.warn("Could not extract key managers from SSL context");
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract key managers from SSL context using reflection", e);
            return null;
        }
    }

    /**
     * Extract trust managers from SSL context for server certificate validation
     */
    private static TrustManager[] extractTrustManagers(SSLContext sslContext) {
        try {
            // Use reflection to access the SSL context's trust managers
            java.lang.reflect.Field contextSpiField = sslContext.getClass().getDeclaredField("contextSpi");
            contextSpiField.setAccessible(true);
            Object contextSpi = contextSpiField.get(sslContext);

            java.lang.reflect.Field trustManagerField = contextSpi.getClass().getDeclaredField("trustManager");
            trustManagerField.setAccessible(true);
            Object trustManagerObj = trustManagerField.get(contextSpi);

            if (trustManagerObj instanceof TrustManager[]) {
                return (TrustManager[]) trustManagerObj;
            } else if (trustManagerObj instanceof TrustManager) {
                return new TrustManager[] { (TrustManager) trustManagerObj };
            }

            log.warn("Could not extract trust managers from SSL context");
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract trust managers from SSL context using reflection", e);
            return null;
        }
    }

    /**
     * Creates a NettyNioAsyncHttpClient with custom SSL context using reflection
     */
    private static SdkAsyncHttpClient createNettyHttpClientWithSslContext(
        NettyNioAsyncHttpClient.Builder clientBuilder,
        SslContext sslContext,
        boolean skipSslVerification
    ) {
        try {
            // Try to use reflection to set the SSL context on the Netty client builder
            log.info("Attempting to configure Netty SSL context using reflection");

            // Build the client first
            SdkAsyncHttpClient client = clientBuilder.build();

            // Try to access the internal Netty bootstrap and configure SSL
            if (client instanceof NettyNioAsyncHttpClient) {
                NettyNioAsyncHttpClient nettyClient = (NettyNioAsyncHttpClient) client;
                Field bootstrapField = nettyClient.getClass().getDeclaredField("bootstrap");
                if (bootstrapField != null) {
                    bootstrapField.setAccessible(true);
                    Object bootstrap = bootstrapField.get(nettyClient);

                    // Try to configure SSL handler on the bootstrap
                    Method handlerMethod = bootstrap.getClass().getMethod("handler", io.netty.channel.ChannelHandler.class);
                    if (handlerMethod != null) {
                        // Create SSL handler with our context
                        io.netty.handler.ssl.SslHandler sslHandler = sslContext.newHandler(io.netty.buffer.ByteBufAllocator.DEFAULT);
                        handlerMethod.invoke(bootstrap, sslHandler);
                        log.info("Successfully configured Netty SSL context via reflection");
                        return client;
                    }
                }
            }

            log.warn("Could not configure Netty SSL context via reflection, using standard approach");
            return client;

        } catch (Exception e) {
            log.warn("Failed to configure Netty SSL context via reflection: {}", e.getMessage());

            // Fallback to standard client configuration
            if (skipSslVerification) {
                return clientBuilder
                    .buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true).build());
            } else {
                return clientBuilder.build();
            }
        }
    }
}
