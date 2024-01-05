/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.ml.common.connector.ConnectorHttpClientConfig;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor {
    private Integer maxConnections;
    private Integer connectionTimeoutInMillis;
    private Integer readTimeoutInMillis;

    public void validate() {
        if (connectionTimeoutInMillis == null) {
            connectionTimeoutInMillis = ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE;
        }
        if (readTimeoutInMillis == null) {
            readTimeoutInMillis = ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE;
        }
        if (maxConnections == null) {
            maxConnections = ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE;
        }
    }
}
