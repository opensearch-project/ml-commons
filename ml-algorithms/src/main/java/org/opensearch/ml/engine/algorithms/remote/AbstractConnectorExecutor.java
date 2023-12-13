/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

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
            connectionTimeoutInMillis = MLCreateConnectorInput.CONNECTION_TIMEOUT_DEFAULT_VALUE;
        }
        if (readTimeoutInMillis == null) {
            readTimeoutInMillis = MLCreateConnectorInput.READ_TIMEOUT_DEFAULT_VALUE;
        }
        if (maxConnections == null) {
            maxConnections = MLCreateConnectorInput.MAX_CONNECTION_DEFAULT_VALUE;
        }
    }
}
