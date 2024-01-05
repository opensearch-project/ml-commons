/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorHttpClientConfig;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor {
    private Integer maxConnections;
    private Integer connectionTimeoutInMillis;
    private Integer readTimeoutInMillis;

    public void validate(Connector connector) {
        ConnectorHttpClientConfig httpClientConfig = connector.getHttpClientConfig();
        if (httpClientConfig != null) {
            if (httpClientConfig.getMaxConnections() != null) {
                maxConnections = httpClientConfig.getMaxConnections();
            } else {
                maxConnections = ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE;
            }
            if (httpClientConfig.getConnectionTimeout() != null) {
                connectionTimeoutInMillis = httpClientConfig.getConnectionTimeout();
            } else {
                connectionTimeoutInMillis = ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE;
            }
            if (httpClientConfig.getReadTimeout() != null) {
                readTimeoutInMillis = httpClientConfig.getReadTimeout();
            } else {
                readTimeoutInMillis = ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE;
            }

        } else {
            connectionTimeoutInMillis = ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE;
            readTimeoutInMillis = ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE;
            maxConnections = ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE;
        }
    }
}
