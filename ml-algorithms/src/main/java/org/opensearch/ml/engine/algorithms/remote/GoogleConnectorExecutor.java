/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.GOOGLE_CLOUD;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;

/**
 * The executor of {@code google_cloud} connectors: an http executor, whose token comes from the connector's effective
 * {@code auth_config} through the node's authorization provider factory.
 */
@ConnectorExecutor(GOOGLE_CLOUD)
public class GoogleConnectorExecutor extends HttpJsonConnectorExecutor {

    public GoogleConnectorExecutor(Connector connector) {
        super(connector);
    }
}
