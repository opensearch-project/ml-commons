/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.load;

import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.transport.model.load.LoadModelInput;
import org.opensearch.ml.common.transport.model.load.LoadModelNodeResponse;
import org.opensearch.ml.common.transport.model.load.LoadModelNodesRequest;
import org.opensearch.ml.common.transport.model.load.LoadModelNodesResponse;
import org.opensearch.ml.common.transport.model.load.LoadModelResponse;
import org.opensearch.ml.common.transport.model.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.model.load.MLLoadModelOnNodeAction;
import org.opensearch.ml.common.transport.model.load.MLLoadModelRequest;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

@Log4j2
public class TransportLoadModelAction extends HandledTransportAction<ActionRequest, LoadModelResponse> {
    TransportService transportService;
    CustomModelManager customModelManager;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    MLTaskDispatcher mlTaskDispatcher;

    @Inject
    public TransportLoadModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLTaskDispatcher mlTaskDispatcher
    ) {
        super(MLLoadModelAction.NAME, transportService, actionFilters, MLLoadModelRequest::new);
        this.transportService = transportService;
        this.customModelManager = customModelManager;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlTaskDispatcher = mlTaskDispatcher;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<LoadModelResponse> listener) {
        MLLoadModelRequest deployModelRequest = MLLoadModelRequest.fromActionRequest(request);
        String modelId = deployModelRequest.getModelId();
        try {

            DiscoveryNode[] eligibleNodes = mlTaskDispatcher.getEligibleNodes();
            List<String> nodeIds = Arrays.stream(eligibleNodes).map(n -> n.getId()).collect(Collectors.toList());
            String workerNodes = String.join(",", nodeIds);
            log.warn("Will load model on these nodes: {}", workerNodes);
            MLTask mlTask = MLTask
                .builder()
                .async(true)
                .taskType(MLTaskType.LOAD_MODEL)
                .functionName(FunctionName.CUSTOM)
                .inputType(MLInputDataType.SEARCH_QUERY)
                .createTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .state(MLTaskState.CREATED)
                .workerNode(workerNodes)
                .build();
            mlTaskManager.createMLTask(mlTask, ActionListener.wrap(response -> {
                String taskId = response.getId();
                mlTask.setTaskId(taskId);
                mlTaskManager.add(mlTask);
                mlTaskManager.addTaskWorkerNodes(taskId, nodeIds);
                listener.onResponse(new LoadModelResponse(taskId, MLTaskState.CREATED.name()));

                threadPool.executor(TASK_THREAD_POOL).execute(() -> {

                    LoadModelInput loadModelInput = new LoadModelInput(
                        modelId,
                        taskId,
                        eligibleNodes.length,
                        clusterService.localNode().getId(),
                        mlTask
                    );
                    LoadModelNodesRequest loadModelRequest = new LoadModelNodesRequest(eligibleNodes, loadModelInput);
                    ActionListener<LoadModelNodesResponse> actionListener = ActionListener.wrap(r -> {
                        List<LoadModelNodeResponse> nodes = r.getNodes();
                        mlTaskManager.updateMLTask(taskId, ImmutableMap.of(MLTask.STATE_FIELD, MLTaskState.RUNNING), 5000);
                    }, e -> {
                        log.error("Failed to load model " + modelId, e);
                        mlTaskManager
                            .updateMLTask(
                                taskId,
                                ImmutableMap
                                    .of(MLTask.ERROR_FIELD, ExceptionUtils.getStackTrace(e), MLTask.STATE_FIELD, MLTaskState.FAILED),
                                5000
                            );
                        mlTaskManager.remove(taskId);
                    });
                    client.execute(MLLoadModelOnNodeAction.INSTANCE, loadModelRequest, actionListener);
                });
            }, exception -> {
                log.error("Failed to create upload model task for " + modelId, exception);
                listener.onFailure(exception);
            }));
        } catch (Exception e) {
            log.error("Failed to download custom model " + modelId, e);
            listener.onFailure(e);
        }
    }

}
