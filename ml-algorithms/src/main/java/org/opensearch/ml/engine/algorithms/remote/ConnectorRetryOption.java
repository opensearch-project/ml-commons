/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

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
    private Integer maxRetryTimes;
    private String retryExecutor;
}
