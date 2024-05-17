package org.opensearch.ml.engine.algorithms.remote;

public interface ConnectorRetryOption {
    default boolean getRetryEnabled() {
        return false;
    }

    default Integer getRetryBackoffMillis() {
        throw new UnsupportedOperationException();
    };

    default Integer getRetryTimeoutSeconds() {
        throw new UnsupportedOperationException();
    };

    default String getRetryExecutor() {
        throw new UnsupportedOperationException();
    };
}
