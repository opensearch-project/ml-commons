/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.MLTaskRequest;

/**
 * Interface for ML task execution operations needed by gRPC services.
 * This abstracts task runners to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLTaskRunner {

    /**
     * Checks circuit breaker and executes the ML task.
     * This method handles streaming responses via the ActionListener and TransportChannel.
     *
     * @param functionName the ML function to execute (e.g., REMOTE, AGENT)
     * @param request the ML task request containing input and streaming channel
     * @param listener the action listener for async results
     */
    void checkCBAndExecute(FunctionName functionName, MLTaskRequest request, ActionListener<?> listener);
}
