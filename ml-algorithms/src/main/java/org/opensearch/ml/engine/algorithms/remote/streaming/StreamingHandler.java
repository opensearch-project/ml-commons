/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.net.URI;
import java.util.Map;

import org.opensearch.ml.common.transport.MLTaskResponse;

/**
 * Streaming handler interface.
 */
public interface StreamingHandler {
    /**
     * endpoint is the action URL that passed the connector's checks and headers the request headers; a handler that sends
     * through a vendor SDK client, which picks the endpoint from the connector's region, uses neither.
     */
    void startStream(
        URI endpoint,
        Map<String, String> headers,
        Map<String, String> parameters,
        String payload,
        StreamPredictActionListener<MLTaskResponse, ?> listener
    );

    void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener);
}
