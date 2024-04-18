/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.deploy;

import static org.opensearch.ml.common.MLTask.ERROR_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
import static org.opensearch.ml.plugin.MachineLearningPlugin.DEPLOY_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelInput;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelOnNodeAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLExceptionUtils;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportDeployModelAction extends HandledTransportAction<ActionRequest, MLDeployModelResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    Settings settings;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelManager mlModelManager;
    MLStats mlStats;

    private volatile boolean allowCustomDeploymentPlan;
    private ModelAccessControlHelper modelAccessControlHelper;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportDeployModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelManager mlModelManager,
        MLStats mlStats,
        Settings settings,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLDeployModelAction.NAME, transportService, actionFilters, MLDeployModelRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelManager = mlModelManager;
        this.mlStats = mlStats;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.settings = settings;
        allowCustomDeploymentPlan = ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN, it -> allowCustomDeploymentPlan = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLDeployModelResponse> listener) {
        MLDeployModelRequest deployModelRequest = MLDeployModelRequest.fromActionRequest(request);
        String modelId = deployModelRequest.getModelId();
        Boolean isUserInitiatedDeployRequest = deployModelRequest.isUserInitiatedDeployRequest();
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLDeployModelResponse> wrappedListener = ActionListener.runBefore(listener, () -> context.restore());
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                FunctionName functionName = mlModel.getAlgorithm();
                Boolean isHidden = mlModel.getIsHidden();
                if (functionName == FunctionName.REMOTE && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
                    throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
                } else if (FunctionName.isDLModel(functionName) && !mlFeatureEnabledSetting.isLocalModelEnabled()) {
                    throw new IllegalStateException(LOCAL_MODEL_DISABLED_ERR_MSG);
                }
                if (!isUserInitiatedDeployRequest) {
                    deployModel(deployModelRequest, mlModel, modelId, wrappedListener, listener);
                } else if (isHidden != null && isHidden) {
                    if (isSuperAdmin) {
                        deployModel(deployModelRequest, mlModel, modelId, wrappedListener, listener);
                    } else {
                        wrappedListener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "User doesn't have privilege to perform this operation on this model",
                                    RestStatus.FORBIDDEN
                                )
                            );
                    }
                } else {
                    modelAccessControlHelper
                        .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                            if (!access) {
                                wrappedListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "User doesn't have privilege to perform this operation on this model",
                                            RestStatus.FORBIDDEN
                                        )
                                    );
                            } else {
                                deployModel(deployModelRequest, mlModel, modelId, wrappedListener, listener);
                            }
                        }, e -> {
                            log.error(getErrorMessage("Failed to Validate Access for the given model", modelId, isHidden), e);
                            wrappedListener.onFailure(e);
                        }));
                }

            }, e -> {
                log.error("Failed to retrieve the ML model with the given ID", e);
                wrappedListener.onFailure(e);
            }));
        } catch (Exception e) {
            log.error("Failed to deploy the ML model", e);
            listener.onFailure(e);
        }

    }

    private void deployModel(
        MLDeployModelRequest deployModelRequest,
        MLModel mlModel,
        String modelId,
        ActionListener<MLDeployModelResponse> wrappedListener,
        ActionListener<MLDeployModelResponse> listener
    ) {
        String[] targetNodeIds = deployModelRequest.getModelNodeIds();
        boolean deployToAllNodes = targetNodeIds == null || targetNodeIds.length == 0;
        if (!allowCustomDeploymentPlan && !deployToAllNodes) {
            throw new IllegalArgumentException("Don't allow custom deployment plan");
        }
        DiscoveryNode[] allEligibleNodes = nodeFilter.getEligibleNodes(mlModel.getAlgorithm());
        Map<String, DiscoveryNode> nodeMapping = new HashMap<>();
        for (DiscoveryNode node : allEligibleNodes) {
            nodeMapping.put(node.getId(), node);
        }

        Set<String> allEligibleNodeIds = Arrays.stream(allEligibleNodes).map(DiscoveryNode::getId).collect(Collectors.toSet());

        List<DiscoveryNode> eligibleNodes = new ArrayList<>();
        List<String> eligibleNodeIds = new ArrayList<>();
        if (!deployToAllNodes) {
            for (String nodeId : targetNodeIds) {
                if (allEligibleNodeIds.contains(nodeId)) {
                    eligibleNodes.add(nodeMapping.get(nodeId));
                    eligibleNodeIds.add(nodeId);
                }
            }
            String[] workerNodes = mlModelManager.getWorkerNodes(modelId, mlModel.getAlgorithm());
            if (workerNodes != null && workerNodes.length > 0) {
                Set<String> difference = new HashSet<String>(Arrays.asList(workerNodes));
                difference.removeAll(Arrays.asList(targetNodeIds));
                if (difference.size() > 0) {
                    wrappedListener
                        .onFailure(
                            new IllegalArgumentException(
                                "Model already deployed to these nodes: "
                                    + Arrays.toString(difference.toArray(new String[0]))
                                    + ", but they are not included in target node ids. Undeploy model from these nodes if don't need them any more."
                                    + "Undeploy from old nodes before try to deploy model on new nodes. Or include all old nodes on your target nodes."

                            )
                        );
                    return;
                }
            }
        } else {
            eligibleNodeIds.addAll(allEligibleNodeIds);
            eligibleNodes.addAll(Arrays.asList(allEligibleNodes));
        }
        if (eligibleNodeIds.size() == 0) {
            wrappedListener.onFailure(new IllegalArgumentException("no eligible node found"));
            return;
        }

        log.info("Will deploy model on these nodes: {}", String.join(",", eligibleNodeIds));
        String localNodeId = clusterService.localNode().getId();

        FunctionName algorithm = mlModel.getAlgorithm();
        // TODO: Track deploy failure
        // mlStats.createCounterStatIfAbsent(algorithm, ActionName.DEPLOY,
        // MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        MLTask mlTask = MLTask
            .builder()
            .async(true)
            .modelId(modelId)
            .taskType(MLTaskType.DEPLOY_MODEL)
            .functionName(algorithm)
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .state(MLTaskState.CREATED)
            .workerNodes(eligibleNodeIds)
            .build();
        mlTaskManager.createMLTask(mlTask, ActionListener.wrap(response -> {
            String taskId = response.getId();
            mlTask.setTaskId(taskId);
            if (algorithm == FunctionName.REMOTE) {
                mlTaskManager.add(mlTask, eligibleNodeIds);
                deployRemoteModel(mlModel, mlTask, localNodeId, eligibleNodes, deployToAllNodes, listener);
                return;
            }
            try {
                mlTaskManager.add(mlTask, eligibleNodeIds);
                wrappedListener.onResponse(new MLDeployModelResponse(taskId, MLTaskType.DEPLOY_MODEL, MLTaskState.CREATED.name()));
                threadPool
                    .executor(DEPLOY_THREAD_POOL)
                    .execute(
                        () -> updateModelDeployStatusAndTriggerOnNodesAction(
                            modelId,
                            taskId,
                            mlModel,
                            localNodeId,
                            mlTask,
                            eligibleNodes,
                            deployToAllNodes
                        )
                    );
            } catch (Exception ex) {
                log.error("Failed to deploy model", ex);
                mlTaskManager
                    .updateMLTask(
                        taskId,
                        Map.of(STATE_FIELD, FAILED, ERROR_FIELD, MLExceptionUtils.getRootCauseMessage(ex)),
                        TASK_SEMAPHORE_TIMEOUT,
                        true
                    );
                wrappedListener.onFailure(ex);
            }
        }, exception -> {
            if (mlModel.getIsHidden()) {
                log.error("Failed to create deploy model task for the provided model", exception);
            } else {
                log.error("Failed to create deploy model task for " + modelId, exception);
            }

            wrappedListener.onFailure(exception);
        }));
    }

    @VisibleForTesting
    void deployRemoteModel(
        MLModel mlModel,
        MLTask mlTask,
        String localNodeId,
        List<DiscoveryNode> eligibleNodes,
        boolean deployToAllNodes,
        ActionListener<MLDeployModelResponse> listener
    ) {
        MLDeployModelInput deployModelInput = new MLDeployModelInput(
            mlModel.getModelId(),
            mlTask.getTaskId(),
            mlModel.getModelContentHash(),
            eligibleNodes.size(),
            localNodeId,
            deployToAllNodes,
            mlTask
        );

        MLDeployModelNodesRequest deployModelRequest = new MLDeployModelNodesRequest(
            eligibleNodes.toArray(new DiscoveryNode[0]),
            deployModelInput
        );

        ActionListener<MLDeployModelNodesResponse> actionListener = deployModelNodesResponseListener(
            mlTask.getTaskId(),
            mlModel.getModelId(),
            mlModel.getIsHidden(),
            listener
        );
        List<String> workerNodes = eligibleNodes.stream().map(n -> n.getId()).collect(Collectors.toList());
        mlModelManager
            .updateModel(
                mlModel.getModelId(),
                Map
                    .of(
                        MLModel.MODEL_STATE_FIELD,
                        MLModelState.DEPLOYING,
                        MLModel.PLANNING_WORKER_NODE_COUNT_FIELD,
                        eligibleNodes.size(),
                        MLModel.PLANNING_WORKER_NODES_FIELD,
                        workerNodes,
                        MLModel.DEPLOY_TO_ALL_NODES_FIELD,
                        deployToAllNodes
                    ),
                ActionListener
                    .wrap(
                        r -> client.execute(MLDeployModelOnNodeAction.INSTANCE, deployModelRequest, actionListener),
                        actionListener::onFailure
                    )
            );
    }

    private ActionListener<MLDeployModelNodesResponse> deployModelNodesResponseListener(
        String taskId,
        String modelId,
        Boolean isHidden,
        ActionListener<MLDeployModelResponse> listener
    ) {
        return ActionListener.wrap(r -> {
            if (mlTaskManager.contains(taskId)) {
                mlTaskManager.updateMLTask(taskId, Map.of(STATE_FIELD, MLTaskState.RUNNING), TASK_SEMAPHORE_TIMEOUT, false);
            }
            listener.onResponse(new MLDeployModelResponse(taskId, MLTaskType.DEPLOY_MODEL, MLTaskState.COMPLETED.name()));
        }, e -> {
            log.error("Failed to deploy model " + modelId, e);
            mlTaskManager
                .updateMLTask(
                    taskId,
                    Map.of(MLTask.ERROR_FIELD, MLExceptionUtils.getRootCauseMessage(e), STATE_FIELD, FAILED),
                    TASK_SEMAPHORE_TIMEOUT,
                    true
                );
            mlModelManager.updateModel(modelId, isHidden, Map.of(MLModel.MODEL_STATE_FIELD, MLModelState.DEPLOY_FAILED));
            listener.onFailure(e);
        });
    }

    @VisibleForTesting
    void updateModelDeployStatusAndTriggerOnNodesAction(
        String modelId,
        String taskId,
        MLModel mlModel,
        String localNodeId,
        MLTask mlTask,
        List<DiscoveryNode> eligibleNodes,
        boolean deployToAllNodes
    ) {
        MLDeployModelInput deployModelInput = new MLDeployModelInput(
            modelId,
            taskId,
            mlModel.getModelContentHash(),
            eligibleNodes.size(),
            localNodeId,
            deployToAllNodes,
            mlTask
        );
        MLDeployModelNodesRequest deployModelRequest = new MLDeployModelNodesRequest(
            eligibleNodes.toArray(new DiscoveryNode[0]),
            deployModelInput
        );
        ActionListener<MLDeployModelNodesResponse> actionListener = ActionListener.wrap(r -> {
            if (mlTaskManager.contains(taskId)) {
                mlTaskManager.updateMLTask(taskId, Map.of(STATE_FIELD, MLTaskState.RUNNING), TASK_SEMAPHORE_TIMEOUT, false);
            }
        }, e -> {
            log.error("Failed to deploy model " + modelId, e);
            mlTaskManager
                .updateMLTask(
                    taskId,
                    Map.of(MLTask.ERROR_FIELD, MLExceptionUtils.getRootCauseMessage(e), STATE_FIELD, FAILED),
                    TASK_SEMAPHORE_TIMEOUT,
                    true
                );
            mlModelManager.updateModel(modelId, mlModel.getIsHidden(), Map.of(MLModel.MODEL_STATE_FIELD, MLModelState.DEPLOY_FAILED));
        });

        List<String> workerNodes = eligibleNodes.stream().map(n -> n.getId()).collect(Collectors.toList());
        mlModelManager
            .updateModel(
                modelId,
                Map
                    .of(
                        MLModel.MODEL_STATE_FIELD,
                        MLModelState.DEPLOYING,
                        MLModel.PLANNING_WORKER_NODE_COUNT_FIELD,
                        eligibleNodes.size(),
                        MLModel.PLANNING_WORKER_NODES_FIELD,
                        workerNodes,
                        MLModel.DEPLOY_TO_ALL_NODES_FIELD,
                        deployToAllNodes
                    ),
                ActionListener
                    .wrap(
                        r -> client.execute(MLDeployModelOnNodeAction.INSTANCE, deployModelRequest, actionListener),
                        actionListener::onFailure
                    )
            );
    }

    // this method is only to stub static method.
    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }

}
