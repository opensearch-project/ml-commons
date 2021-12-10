/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.task;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * MLExecuteTaskRunner is responsible for running execute tasks.
 */
@Log4j2
public class MLExecuteTaskRunner extends MLTaskRunner<MLExecuteTaskRequest, MLExecuteTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLInputDatasetHandler mlInputDatasetHandler;

    public MLExecuteTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
    }

    /**
     * Execute algorithm and return result.
     * TODO: 1. support backend task run; 2. support dispatch task to remote node
     * @param request MLExecuteTaskRequest
     * @param transportService transport service
     * @param listener Action listener
     */
    public void execute(MLExecuteTaskRequest request, TransportService transportService, ActionListener<MLExecuteTaskResponse> listener) {
        Input input = request.getInput();
        Output output = MLEngine.execute(input);
        listener.onResponse(MLExecuteTaskResponse.builder().output(output).build());
    }

    @Override
    public void run(MLExecuteTaskRequest request, TransportService transportService, ActionListener<MLExecuteTaskResponse> listener) {
        Input input = request.getInput();
        Output output = MLEngine.execute(input);
        MLExecuteTaskResponse response = MLExecuteTaskResponse.builder().output(output).build();
        listener.onResponse(response);
    }

}
