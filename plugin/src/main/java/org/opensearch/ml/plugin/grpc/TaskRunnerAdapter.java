/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.MLTaskRequest;
import org.opensearch.ml.grpc.interfaces.MLTaskRunner;

/**
 * Adapter that wraps ML task runners to implement the gRPC interface.
 * This breaks the circular dependency between grpc and plugin modules.
 */
@SuppressWarnings("rawtypes")
public class TaskRunnerAdapter implements MLTaskRunner {

    private final org.opensearch.ml.task.MLTaskRunner delegate;

    public TaskRunnerAdapter(org.opensearch.ml.task.MLTaskRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public void checkCBAndExecute(FunctionName functionName, MLTaskRequest request, ActionListener<?> listener) {
        delegate.checkCBAndExecute(functionName, request, listener);
    }
}
