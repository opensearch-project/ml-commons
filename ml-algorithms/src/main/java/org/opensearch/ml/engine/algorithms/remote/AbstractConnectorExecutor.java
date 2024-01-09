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
    private ConnectorHttpClientConfig httpClientConfig;

    public void initialize(Connector connector) {
        if (connector.getHttpClientConfig() != null) {
            httpClientConfig = connector.getHttpClientConfig();
        } else {
            httpClientConfig = new ConnectorHttpClientConfig();
        }
    }
}
