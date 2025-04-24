/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.EXECUTE_THREAD_POOL;

import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportResponseHandler;

import lombok.extern.log4j.Log4j2;

/**
 * MLExecuteTaskRunner is responsible for running execute tasks.
 */
@Log4j2
public class MLExecuteTaskRunner extends MLTaskRunner<MLExecuteTaskRequest, MLExecuteTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLInputDatasetHandler mlInputDatasetHandler;
    protected final DiscoveryNodeHelper nodeHelper;
    private final MLEngine mlEngine;
    private volatile Boolean isPythonModelEnabled;

    public MLExecuteTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService,
        DiscoveryNodeHelper nodeHelper,
        MLEngine mlEngine
    ) {
        super(mlTaskManager, mlStats, nodeHelper, mlTaskDispatcher, mlCircuitBreakerService, clusterService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
        this.nodeHelper = nodeHelper;
        this.mlEngine = mlEngine;
        isPythonModelEnabled = ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL.get(this.clusterService.getSettings());
        this.clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL, it -> isPythonModelEnabled = it);
    }

    @Override
    protected String getTransportActionName() {
        return MLExecuteTaskAction.NAME;
    }

    @Override
    protected TransportResponseHandler<MLExecuteTaskResponse> getResponseHandler(ActionListener<MLExecuteTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLExecuteTaskResponse::new);
    }

    /**
     * Execute algorithm and return result.
     * @param request MLExecuteTaskRequest
     * @param listener Action listener
     */
    @Override
    protected void executeTask(MLExecuteTaskRequest request, ActionListener<MLExecuteTaskResponse> listener) {
        threadPool.executor(EXECUTE_THREAD_POOL).execute(() -> {
            try {
                mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
                mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
                mlStats
                    .createCounterStatIfAbsent(request.getFunctionName(), ActionName.EXECUTE, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
                    .increment();

                // ActionListener<MLExecuteTaskResponse> wrappedListener = ActionListener.runBefore(listener, )
                Input input = request.getInput();
                FunctionName functionName = request.getFunctionName();
                if (FunctionName.METRICS_CORRELATION.equals(functionName)) {
                    if (!isPythonModelEnabled) {
                        Exception exception = new IllegalArgumentException("This algorithm is not enabled from settings");
                        listener.onFailure(exception);
                        return;
                    }
                }
                mlEngine.execute(input, ActionListener.wrap(output -> {
                    MLExecuteTaskResponse response = new MLExecuteTaskResponse(functionName, output);
                    listener.onResponse(response);
                }, e -> { listener.onFailure(e); }));
            } catch (Exception e) {
                mlStats
                    .createCounterStatIfAbsent(request.getFunctionName(), ActionName.EXECUTE, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                    .increment();
                listener.onFailure(e);
            } finally {
                mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
            }
        });
    }

}
