/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.util.Map;

import org.opensearch.ml.common.transport.MLTaskResponse;

/**
 * Streaming handler interface.
 */
public interface StreamingHandler {
    void startStream(
        String action,
        Map<String, String> parameters,
        String payload,
        StreamPredictActionListener<MLTaskResponse, ?> listener
    );

    void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener);
}
