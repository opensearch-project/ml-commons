/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import java.lang.reflect.Method;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.MLTaskRequest;
import org.opensearch.ml.grpc.interfaces.MLTaskRunner;

import lombok.extern.log4j.Log4j2;

/**
 * Adapter that wraps ML task runners to implement the gRPC interface.
 * This breaks the circular dependency between grpc and plugin modules.
 *
 * Note: Still uses reflection internally because checkCBAndExecute is a protected method
 * in the base class. To eliminate this, the method would need to be made public.
 */
@Log4j2
public class TaskRunnerAdapter implements MLTaskRunner {

    private final Object delegate; // Can be MLPredictTaskRunner or MLExecuteTaskRunner

    public TaskRunnerAdapter(Object delegate) {
        this.delegate = delegate;
    }

    @Override
    public void checkCBAndExecute(FunctionName functionName, MLTaskRequest request, ActionListener<?> listener) {
        try {
            // Both MLPredictTaskRunner and MLExecuteTaskRunner extend MLTaskRunner
            // which has protected checkCBAndExecute method in base class
            Method method = delegate
                .getClass()
                .getSuperclass()
                .getDeclaredMethod("checkCBAndExecute", FunctionName.class, MLTaskRequest.class, ActionListener.class);
            method.setAccessible(true);
            method.invoke(delegate, functionName, request, listener);
        } catch (Exception e) {
            log.error("Failed to execute task via reflection", e);
            // Unwrap InvocationTargetException if present
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Failed to execute task: " + cause.getMessage(), cause);
        }
    }
}
