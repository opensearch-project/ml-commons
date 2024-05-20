package org.opensearch.ml.engine.algorithms.remote;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorRetryOption {
    private boolean retryEnabled = false;
    private Integer retryBackoffMillis;
    private Integer retryTimeoutSeconds;
    private String retyExecutor;
}
