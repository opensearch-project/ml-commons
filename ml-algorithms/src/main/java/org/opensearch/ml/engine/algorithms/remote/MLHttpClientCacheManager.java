/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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

import com.google.common.annotations.VisibleForTesting;

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

    /**
     * Floor for the grace period before a replaced HTTP client is closed. The actual period is the
     * larger of this and the configured connect + read timeout, so a client is never torn down while
     * a request that the configuration itself permits could still be running.
     */
    @VisibleForTesting
    static final TimeValue MIN_CLIENT_CLOSE_GRACE_PERIOD = TimeValue.timeValueSeconds(30);

    private final AtomicReference<String> httpClientCacheKey = new AtomicReference<>();
    private final AtomicReference<SdkAsyncHttpClient> httpClientRef = new AtomicReference<>();

    /**
     * Grace period to wait before closing a replaced client. Retries re-fetch the client per attempt,
     * so only attempts already in flight need to survive: one connect plus one read. Returns the
     * larger of that budget and {@link #MIN_CLIENT_CLOSE_GRACE_PERIOD}. Best effort - a response that
     * keeps resetting the read timeout can still outlive it.
     */
    @VisibleForTesting
    static TimeValue clientCloseGracePeriod(ConnectorClientConfig config) {
        long inFlightBudgetSeconds = 0;
        if (config != null) {
            if (config.getConnectionTimeout() != null) {
                inFlightBudgetSeconds += config.getConnectionTimeout();
            }
            if (config.getReadTimeout() != null) {
                inFlightBudgetSeconds += config.getReadTimeout();
            }
        }
        return inFlightBudgetSeconds > MIN_CLIENT_CLOSE_GRACE_PERIOD.seconds()
            ? TimeValue.timeValueSeconds(inFlightBudgetSeconds)
            : MIN_CLIENT_CLOSE_GRACE_PERIOD;
    }

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
        Supplier<SdkAsyncHttpClient> clientFactory
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
                        TimeValue gracePeriod = clientCloseGracePeriod(config);
                        client.threadPool().schedule(() -> {
                            try {
                                existingClient.close();
                                log.debug("Closed existing HTTP client after grace period due to configuration change");
                            } catch (Exception e) {
                                log.warn("Failed to close existing HTTP client: {}", e.getMessage());
                            }
                        }, gracePeriod, "generic");
                        log.debug("Scheduled deferred close of existing HTTP client in {} due to configuration change", gracePeriod);
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
    @VisibleForTesting
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
            // The keystore type selects which key managers get built (PEM vs PKCS12), so it has to take
            // part in invalidation. Resolved through KeystoreType.from so that equivalent spellings
            // ("pkcs12", "PKCS12") and the null default (PEM) do not produce spurious cache misses.
            keyBuilder.append(",ksType:").append(CertificateProcessor.KeystoreType.from(config.getKeystoreType()));

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
        // This consolidates validateCertificateConfig + buildMtlsManagers
        CertificateProcessor.MtlsManagers mtlsManagers = null;
        try {
            mtlsManagers = certificateProcessor.resolveMtls(config, connector.getDecryptedCredential());
        } catch (MLValidationException e) {
            log.error("Certificate validation failed: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.error("Security policy violation while building mutual TLS managers: {}", e.getMessage());
            throw new MLException("SSL security policy violation: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to configure mutual TLS: {}", e.getMessage());
            throw new MLException("Failed to configure mutual TLS: " + e.getMessage(), e);
        }

        // Extract the TLS managers and determine client description
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;
        String clientDescription = "standard";
        boolean mutualTlsEnabledValue = false;

        if (mtlsManagers != null) {
            keyManagers = mtlsManagers.getKeyManagers();
            trustManagers = mtlsManagers.getTrustManagers();
            clientDescription = "mutual-TLS";
            mutualTlsEnabledValue = true;

            log.debug("Successfully extracted mutual TLS managers");
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
