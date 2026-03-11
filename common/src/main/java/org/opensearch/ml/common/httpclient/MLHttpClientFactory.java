/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.opensearch.secure_sm.AccessController.doPrivileged;

import java.time.Duration;

import org.opensearch.ml.common.connector.ConnectorClientConfig;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
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

    /**
     * Legacy overload kept for backward compatibility. Sets connectionAcquisitionTimeout
     * equal to readTimeout to prevent connection pool exhaustion when readTimeout exceeds
     * the SDK default of 10 seconds.
     */
    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification
    ) {
        return buildHttpClient(
            connectionTimeout,
            readTimeout,
            Duration.ofMillis(ConnectorClientConfig.CONNECTION_ACQUISITION_TIMEOUT_DEFAULT_VALUE),
            maxConnections,
            connectorPrivateIpEnabled,
            skipSslVerification
        );
    }

    public static SdkAsyncHttpClient getAsyncHttpClient(ConnectorClientConfig config, boolean connectorPrivateIpEnabled) {
        Duration connectionTimeout = Duration
            .ofMillis(
                config.getConnectionTimeout() != null
                    ? config.getConnectionTimeout()
                    : ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE
            );
        Duration readTimeout = Duration
            .ofMillis(config.getReadTimeout() != null ? config.getReadTimeout() : ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE);
        int maxConnections = config.getMaxConnections() != null
            ? config.getMaxConnections()
            : ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE;
        boolean skipSslVerification = config.getSkipSslVerification() != null ? config.getSkipSslVerification() : false;
        Integer acquisitionTimeoutValue = config.getConnectionAcquisitionTimeout();
        Duration connectionAcquisitionTimeout = Duration
            .ofMillis(
                acquisitionTimeoutValue != null
                    ? acquisitionTimeoutValue
                    : ConnectorClientConfig.CONNECTION_ACQUISITION_TIMEOUT_DEFAULT_VALUE
            );

        return buildHttpClient(
            connectionTimeout,
            readTimeout,
            connectionAcquisitionTimeout,
            maxConnections,
            connectorPrivateIpEnabled,
            skipSslVerification
        );
    }

    private static SdkAsyncHttpClient buildHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        Duration connectionAcquisitionTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification
    ) {
        return doPrivileged(() -> {
            if (skipSslVerification) {
                log
                    .warn(
                        "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                            + " attacks. Only use this setting in trusted environments."
                    );
            }
            log
                .debug(
                    "Creating MLHttpClient with connectionTimeout: {}, readTimeout: {}, connectionAcquisitionTimeout: {},"
                        + " maxConnections: {}, skipSslVerification: {}",
                    connectionTimeout,
                    readTimeout,
                    connectionAcquisitionTimeout,
                    maxConnections,
                    skipSslVerification
                );
            SdkAsyncHttpClient delegate = NettyNioAsyncHttpClient
                .builder()
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .maxConcurrency(maxConnections)
                .connectionAcquisitionTimeout(connectionAcquisitionTimeout)
                .buildWithDefaults(
                    AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, skipSslVerification).build()
                );
            return new MLValidatableAsyncHttpClient(delegate, connectorPrivateIpEnabled);
        });
    }
}
