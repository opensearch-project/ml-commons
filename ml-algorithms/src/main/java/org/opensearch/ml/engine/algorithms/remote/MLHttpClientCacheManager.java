/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

/**
 * HTTP client cache manager to isolate cache invalidation logic
 * from the core getHttpClient() method.
 * This handles client lifecycle management including cache key generation,
 * invalidation detection, and deferred cleanup of old clients.
 */
@Log4j2
final class MLHttpClientCacheManager {

    private final AtomicReference<String> httpClientCacheKey = new AtomicReference<>();
    private final AtomicReference<SdkAsyncHttpClient> httpClientRef = new AtomicReference<>();

    /**
     * Gets or creates an HTTP client with cache invalidation support.
     * 
     * @param connector The HTTP connector
     * @param config The connector client configuration
     * @param client The OpenSearch client for scheduling cleanup tasks
     * @param clientFactory Function to create a new HTTP client
     * @return The cached or newly created HTTP client
     */
    public SdkAsyncHttpClient getOrCreateHttpClient(
        HttpConnector connector,
        ConnectorClientConfig config,
        Client client,
        java.util.function.Supplier<SdkAsyncHttpClient> clientFactory
    ) {
        String currentCacheKey = generateHttpClientCacheKey(connector, config);

        if (httpClientRef.get() == null || !Objects.equals(httpClientCacheKey.get(), currentCacheKey)) {
            synchronized (this) {
                if (httpClientRef.get() == null || !Objects.equals(httpClientCacheKey.get(), currentCacheKey)) {
                    SdkAsyncHttpClient existingClient = httpClientRef.get();

                    // Create new client BEFORE scheduling old one for close to prevent race condition
                    SdkAsyncHttpClient newClient;
                    try {
                        newClient = clientFactory.get();
                    } catch (Exception e) {
                        log.error("Failed to create new HTTP client, keeping existing client: {}", e.getMessage());
                        throw e; // Re-throw to maintain existing error handling behavior
                    }

                    // Only after successful creation, update references and schedule old client cleanup
                    httpClientRef.set(newClient);
                    httpClientCacheKey.set(currentCacheKey);

                    if (existingClient != null) {
                        // Schedule deferred close to avoid tearing down connection pool while in-flight requests are active
                        client.threadPool().schedule(() -> {
                            try {
                                existingClient.close();
                                log.debug("Closed existing HTTP client after grace period due to configuration change");
                            } catch (Exception e) {
                                log.warn("Failed to close existing HTTP client: {}", e.getMessage());
                            }
                        }, TimeValue.timeValueSeconds(30), "generic");
                        log.debug("Scheduled deferred close of existing HTTP client due to configuration change");
                    }

                    log.debug("Created new HTTP client with cache key: {}", currentCacheKey);
                }
            }
        }
        return httpClientRef.get();
    }

    /**
     * Generate a cache key that includes all configuration parameters that affect HTTP client creation.
     * This ensures the client is recreated when credentials or SSL configuration changes.
     */
    @com.google.common.annotations.VisibleForTesting
    String generateHttpClientCacheKey(HttpConnector connector, ConnectorClientConfig config) {
        StringBuilder keyBuilder = new StringBuilder();

        keyBuilder.append("conn:").append(config.getConnectionTimeout());
        keyBuilder.append(",read:").append(config.getReadTimeout());
        keyBuilder.append(",max:").append(config.getMaxConnections());

        Boolean skipSslVerification = config.getSkipSslVerification();
        Boolean mutualTlsEnabled = config.getMutualTlsEnabled();
        keyBuilder.append(",skipSsl:").append(skipSslVerification != null ? skipSslVerification : false);
        keyBuilder.append(",mtls:").append(mutualTlsEnabled != null ? mutualTlsEnabled : false);

        if (mutualTlsEnabled != null && mutualTlsEnabled && connector.getDecryptedCredential() != null) {
            int credentialHash = Objects
                .hash(
                    connector.getDecryptedCredential().get(CertificateProcessor.CLIENT_CERT_PEM_FIELD),
                    connector.getDecryptedCredential().get(CertificateProcessor.CLIENT_KEY_PEM_FIELD),
                    connector.getDecryptedCredential().get(CertificateProcessor.CLIENT_CERT_PKCS12_FIELD),
                    connector.getDecryptedCredential().get(CertificateProcessor.CA_CERT_PEM_FIELD),
                    connector.getDecryptedCredential().get(CertificateProcessor.KEYSTORE_PASSWORD_FIELD)
                );
            keyBuilder.append(",creds:").append(credentialHash);
        }

        return keyBuilder.toString();
    }

    /**
     * Closes the cached HTTP client and clears the cache.
     * This method should be called when the executor is being shut down to prevent resource leaks.
     */
    public void close() {
        SdkAsyncHttpClient client = httpClientRef.getAndSet(null);
        if (client != null) {
            try {
                client.close();
                log.debug("Closed HTTP client from cache manager");
            } catch (Exception e) {
                log.warn("Failed to close HTTP client from cache manager: {}", e.getMessage());
            }
        }
        httpClientCacheKey.set(null);
    }

    /**
     * Gets the currently cached HTTP client without creating a new one.
     * Used for testing and verification purposes.
     *
     * @return The currently cached HTTP client, or null if none exists
     */
    public SdkAsyncHttpClient getCurrentClient() {
        return httpClientRef.get();
    }

    /**
     * Builds a new HTTP client from the connector's current configuration, resolving mTLS
     * key/trust managers via the given certificate processor.
     */
    static SdkAsyncHttpClient createHttpClient(
        HttpConnector connector,
        ConnectorClientConfig config,
        CertificateProcessor certificateProcessor,
        boolean connectorPrivateIpEnabled,
        List<Pattern> connectorTrustedPrivateEndpoints,
        List<Pattern> connectorRestrictedIpPatterns
    ) {
        Duration connectionTimeout = Duration.ofSeconds(config.getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(config.getReadTimeout());
        Integer maxConnection = config.getMaxConnections();
        Boolean skipSslVerification = config.getSkipSslVerification();

        boolean skipSslVerificationValue = skipSslVerification != null ? skipSslVerification : false;

        if (skipSslVerificationValue) {
            log.warn("SSL certificate verification is DISABLED");
        }

        // Use CertificateProcessor to resolve mTLS configuration in a single call
        // This consolidates validateCertificateConfig + validateCertificateOnlyAuthentication + buildSSLContext
        CertificateProcessor.SSLContextWithManagers contextWithManagers = null;
        try {
            contextWithManagers = certificateProcessor.resolveMtls(config, connector.getDecryptedCredential());
        } catch (MLValidationException e) {
            log.error("Certificate validation failed: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.error("Security policy violation during SSL context initialization: {}", e.getMessage());
            throw new MLException("SSL security policy violation: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to configure mutual TLS: {}", e.getMessage());
            throw new MLException("Failed to configure mutual TLS: " + e.getMessage(), e);
        }

        // Extract SSL components and determine client description
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;
        String clientDescription = "standard";
        boolean mutualTlsEnabledValue = false;

        if (contextWithManagers != null) {
            keyManagers = contextWithManagers.getKeyManagers();
            trustManagers = contextWithManagers.getTrustManagers();
            clientDescription = "mutual-TLS";
            mutualTlsEnabledValue = true;

            log.debug("Successfully extracted SSL context and managers");
            log
                .debug(
                    "Key managers: {}, Trust managers: {}",
                    keyManagers != null ? keyManagers.length : 0,
                    trustManagers != null ? trustManagers.length : 0
                );
            log.debug("Successfully configured certificate-only mutual TLS");
        } else {
            log
                .info(
                    "HttpJsonConnectorExecutor creating HTTP client for connector: {} - maxConnections: {}, connectionTimeout: {}s, readTimeout: {}s",
                    connector.getName(),
                    maxConnection,
                    config.getConnectionTimeout(),
                    config.getReadTimeout()
                );
        }

        log
            .info(
                "HTTP client created - type: {}, maxConnections: {}, connectionTimeout: {}s, readTimeout: {}s, mutualTLS: {}",
                clientDescription,
                maxConnection,
                config.getConnectionTimeout(),
                config.getReadTimeout(),
                mutualTlsEnabledValue
            );

        return MLHttpClientFactory
            .getAsyncHttpClient(
                connectionTimeout,
                readTimeout,
                maxConnection,
                connectorPrivateIpEnabled,
                connectorTrustedPrivateEndpoints,
                connectorRestrictedIpPatterns,
                skipSslVerificationValue,
                keyManagers,
                trustManagers
            );
    }
}