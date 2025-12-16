/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.time.Duration;
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

    private volatile AtomicReference<SdkAsyncHttpClient> httpClientRef = new AtomicReference<>();

    private ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();

    public void initialize(Connector connector) {
        if (connector.getConnectorClientConfig() != null) {
            connectorClientConfig = connector.getConnectorClientConfig();
        }
    }

    protected SdkAsyncHttpClient getHttpClient() {
        if (httpClientRef.get() == null) {
            Duration connectionTimeout = Duration.ofMillis(connectorClientConfig.getConnectionTimeoutMillis());
            Duration readTimeout = Duration.ofSeconds(connectorClientConfig.getReadTimeoutSeconds());
            Integer maxConnection = connectorClientConfig.getMaxConnections();
            this.httpClientRef
                .compareAndSet(
                    null,
                    MLHttpClientFactory.getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection, connectorPrivateIpEnabled)
                );
        }
        return httpClientRef.get();
    }

    public void close() {
        SdkAsyncHttpClient httpClient = httpClientRef.get();
        if (httpClient != null) {
            httpClient.close();
        }
    }
}
