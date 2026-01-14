/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor {

    @Setter
    private volatile boolean connectorPrivateIpEnabled;

    private final AtomicReference<SdkAsyncHttpClient> httpClientRef = new AtomicReference<>();

    private ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();

    public void initialize(Connector connector) {
        if (connector.getConnectorClientConfig() != null) {
            connectorClientConfig = connector.getConnectorClientConfig();
        }
    }

    protected SdkAsyncHttpClient getHttpClient() {
        // This block for high performance retrieval after http client is created.
        SdkAsyncHttpClient existingClient = httpClientRef.get();
        if (existingClient != null) {
            return existingClient;
        }
        // This block handles concurrent http client creation.
        synchronized (this) {
            existingClient = httpClientRef.get();
            if (existingClient != null) {
                return existingClient;
            }
            Duration connectionTimeout = Duration
                .ofMillis(
                    Optional
                        .ofNullable(connectorClientConfig.getConnectionTimeoutMillis())
                        .orElse(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE)
                );
            Duration readTimeout = Duration
                .ofMillis(
                    Optional
                        .ofNullable(connectorClientConfig.getReadTimeoutMillis())
                        .orElse(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE)
                );
            int maxConnection = Optional
                .ofNullable(connectorClientConfig.getMaxConnections())
                .orElse(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE);
            SdkAsyncHttpClient newClient = MLHttpClientFactory
                .getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection, connectorPrivateIpEnabled);
            httpClientRef.set(newClient);
            return newClient;
        }
    }

    public void close() {
        SdkAsyncHttpClient httpClient = httpClientRef.getAndSet(null);
        if (httpClient != null) {
            httpClient.close();
        }
    }
}
