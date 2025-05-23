/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor {
    private ConnectorClientConfig connectorClientConfig;
    protected ClusterService clusterService;
    protected final AtomicBoolean connectorPrivateIpEnabled = new AtomicBoolean(false);

    public void initialize(Connector connector) {
        if (connector.getConnectorClientConfig() != null) {
            connectorClientConfig = connector.getConnectorClientConfig();
        } else {
            connectorClientConfig = new ConnectorClientConfig();
        }
    }

    public boolean isConnectorPrivateIpEnabled() {
        if (clusterService != null) {
            connectorPrivateIpEnabled.set(clusterService.getClusterSettings().get(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED));
            clusterService
                .getClusterSettings()
                .addSettingsUpdateConsumer(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED, it -> connectorPrivateIpEnabled.set(it));
            return connectorPrivateIpEnabled.get();
        }
        return false;
    }
}
