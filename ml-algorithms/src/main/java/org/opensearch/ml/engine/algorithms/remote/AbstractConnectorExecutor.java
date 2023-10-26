package org.opensearch.ml.engine.algorithms.remote;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor{
    @Setter
    @Getter
    private Integer maxConnections;
    @Setter
    @Getter
    private Integer connectionTimeoutInMillis;
    @Setter
    @Getter
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
