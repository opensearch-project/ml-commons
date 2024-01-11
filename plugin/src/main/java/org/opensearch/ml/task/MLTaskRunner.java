/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.utils.MLNodeUtils.checkOpenCircuitBreaker;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.MLTaskRequest;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * MLTaskRunner has common code for dispatching and running predict/training tasks.
 * @param <Request> ML task request
 * @param <Response> ML task request
 */
@Log4j2
public abstract class MLTaskRunner<Request extends MLTaskRequest, Response extends TransportResponse> {
    public static final int TIMEOUT_IN_MILLIS = 2000;
    protected final MLTaskManager mlTaskManager;
    protected final MLStats mlStats;
    protected final DiscoveryNodeHelper nodeHelper;
    protected final MLTaskDispatcher mlTaskDispatcher;
    protected final MLCircuitBreakerService mlCircuitBreakerService;
    private final ClusterService clusterService;

    public MLTaskRunner(
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        DiscoveryNodeHelper nodeHelper,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService,
        ClusterService clusterService
    ) {
        this.mlTaskManager = mlTaskManager;
        this.mlStats = mlStats;
        this.nodeHelper = nodeHelper;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlCircuitBreakerService = mlCircuitBreakerService;
        this.clusterService = clusterService;
    }

    protected void handleAsyncMLTaskFailure(MLTask mlTask, Exception e) {
        // update task state to MLTaskState.FAILED
        // update task error
        if (mlTask.isAsync()) {
            Map<String, Object> updatedFields = Map.of(MLTask.STATE_FIELD, MLTaskState.FAILED.name(), MLTask.ERROR_FIELD, e.getMessage());
            // wait for 2 seconds to make sure failed state persisted
            mlTaskManager.updateMLTask(mlTask.getTaskId(), updatedFields, TIMEOUT_IN_MILLIS, true);
        }
    }

    protected void handleAsyncMLTaskComplete(MLTask mlTask) {
        // update task state to MLTaskState.COMPLETED
        if (mlTask.isAsync()) {
            Map<String, Object> updatedFields = new HashMap<>();
            updatedFields.put(MLTask.STATE_FIELD, MLTaskState.COMPLETED);
            if (mlTask.getModelId() != null) {
                updatedFields.put(MLTask.MODEL_ID_FIELD, mlTask.getModelId());
            }
            // wait for 2 seconds to make sure completed state persisted
            mlTaskManager.updateMLTask(mlTask.getTaskId(), updatedFields, TIMEOUT_IN_MILLIS, true);
        }
    }

    public void run(FunctionName functionName, Request request, TransportService transportService, ActionListener<Response> listener) {
        if (!request.isDispatchTask()) {
            log.debug("Run ML request {} locally", request.getRequestID());
            checkOpenCircuitBreaker(mlCircuitBreakerService, mlStats);
            executeTask(request, listener);
            return;
        }
        dispatchTask(functionName, request, transportService, listener);
    }

    protected ActionListener<MLTaskResponse> wrappedCleanupListener(ActionListener<MLTaskResponse> listener, String taskId) {
        ActionListener<MLTaskResponse> internalListener = ActionListener.runAfter(listener, () -> {
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
            mlTaskManager.remove(taskId);
        });
        return internalListener;
    }

    public void dispatchTask(
        FunctionName functionName,
        Request request,
        TransportService transportService,
        ActionListener<Response> listener
    ) {
        mlTaskDispatcher.dispatch(functionName, ActionListener.wrap(node -> {
            String nodeId = node.getId();
            if (clusterService.localNode().getId().equals(nodeId)) {
                // Execute ML task locally
                log.debug("Execute ML request {} locally on node {}", request.getRequestID(), nodeId);
                checkOpenCircuitBreaker(mlCircuitBreakerService, mlStats);
                executeTask(request, listener);
            } else {
                // Execute ML task remotely
                log.debug("Execute ML request {} remotely on node {}", request.getRequestID(), nodeId);
                request.setDispatchTask(false);
                transportService.sendRequest(node, getTransportActionName(), request, getResponseHandler(listener));
            }
        }, e -> listener.onFailure(e)));
    }

    protected abstract String getTransportActionName();

    protected abstract TransportResponseHandler<Response> getResponseHandler(ActionListener<Response> listener);

    protected abstract void executeTask(Request request, ActionListener<Response> listener);
}
