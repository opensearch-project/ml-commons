/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor{
    private Integer maxConnections;
    private Integer connectionTimeoutInMillis;
    private Integer readTimeoutInMillis;

    public void validate() {
        if (connectionTimeoutInMillis == null) {
            throw new IllegalArgumentException("connectionTimeoutInMillis must be set to non null value, please check your configuration");
        }
        if (readTimeoutInMillis == null) {
            throw new IllegalArgumentException("readTimeoutInMillis must be set to non null value, please check your configuration");
        }
        if (maxConnections == null) {
            throw new IllegalArgumentException("maxConnections must be set to non null value, please check your configuration");
        }
    }
}
