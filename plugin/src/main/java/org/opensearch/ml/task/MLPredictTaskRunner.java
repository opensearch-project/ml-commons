/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
import static org.opensearch.ml.permission.AccessController.checkUserPermissions;
import static org.opensearch.ml.permission.AccessController.getUserContext;
import static org.opensearch.ml.plugin.MachineLearningPlugin.PREDICT_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.REMOTE_PREDICT_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.opensearch.OpenSearchException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

/**
 * MLPredictTaskRunner is responsible for running predict tasks.
 */
@Log4j2
public class MLPredictTaskRunner extends MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLInputDatasetHandler mlInputDatasetHandler;
    private final NamedXContentRegistry xContentRegistry;
    private final MLModelManager mlModelManager;
    private final DiscoveryNodeHelper nodeHelper;
    private final MLEngine mlEngine;
    private volatile boolean autoDeploymentEnabled;

    public MLPredictTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService,
        NamedXContentRegistry xContentRegistry,
        MLModelManager mlModelManager,
        DiscoveryNodeHelper nodeHelper,
        MLEngine mlEngine,
        Settings settings
    ) {
        super(mlTaskManager, mlStats, nodeHelper, mlTaskDispatcher, mlCircuitBreakerService, clusterService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
        this.xContentRegistry = xContentRegistry;
        this.mlModelManager = mlModelManager;
        this.nodeHelper = nodeHelper;
        this.mlEngine = mlEngine;
        autoDeploymentEnabled = ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE, it -> autoDeploymentEnabled = it);
    }

    @Override
    protected String getTransportActionName() {
        return MLPredictionTaskAction.NAME;
    }

    @Override
    protected TransportResponseHandler<MLTaskResponse> getResponseHandler(ActionListener<MLTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLTaskResponse::new);
    }

    @Override
    public void dispatchTask(
        FunctionName functionName,
        MLPredictionTaskRequest request,
        TransportService transportService,
        ActionListener<MLTaskResponse> listener
    ) {
        String modelId = request.getModelId();
        try {
            ActionListener<DiscoveryNode> actionListener = ActionListener.wrap(node -> {
                if (clusterService.localNode().getId().equals(node.getId())) {
                    log.debug("Execute ML predict request {} locally on node {}", request.getRequestID(), node.getId());
                    request.setDispatchTask(false);
                    executeTask(request, listener);
                } else {
                    log.debug("Execute ML predict request {} remotely on node {}", request.getRequestID(), node.getId());
                    request.setDispatchTask(false);
                    transportService.sendRequest(node, getTransportActionName(), request, getResponseHandler(listener));
                }
            }, listener::onFailure);
            String[] workerNodes = mlModelManager.getWorkerNodes(modelId, functionName, true);
            if (workerNodes == null || workerNodes.length == 0) {
                if (FunctionName.isAutoDeployEnabled(autoDeploymentEnabled, functionName)) {
                    try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                        mlModelManager.getModel(modelId, ActionListener.runBefore(ActionListener.wrap(model -> {
                            Boolean isHidden = model.getIsHidden();
                            if (!checkModelAutoDeployEnabled(model)) {
                                final String errorMsg = getErrorMessage(
                                    "Auto deployment disabled for this model, please deploy model first",
                                    modelId,
                                    isHidden
                                );
                                log.info(errorMsg);
                                listener.onFailure(new IllegalArgumentException(errorMsg));
                                return;
                            }
                            String[] planningWorkerNodes = model.getPlanningWorkerNodes();
                            MLModel modelBeingAutoDeployed = mlModelManager.addModelToAutoDeployCache(modelId, model);
                            if (modelBeingAutoDeployed == model) {
                                log.info(getErrorMessage("Automatically deploy model", modelId, isHidden));

                                MLDeployModelRequest deployModelRequest = new MLDeployModelRequest(
                                    modelId,
                                    planningWorkerNodes,
                                    false,
                                    true,
                                    false
                                );
                                client.execute(MLDeployModelAction.INSTANCE, deployModelRequest, ActionListener.wrap(r -> {
                                    log.info(getErrorMessage("Auto deployment action triggered for the model", modelId, isHidden));
                                },
                                    e -> log
                                        .info(getErrorMessage("Auto deployment action failed for the given model {}", modelId, isHidden), e)
                                ));
                            }
                            if (planningWorkerNodes == null || planningWorkerNodes.length == 0) {
                                planningWorkerNodes = nodeHelper.getEligibleNodeIds(functionName);
                            }
                            mlTaskDispatcher.dispatchPredictTask(planningWorkerNodes, actionListener);
                        }, e -> {
                            log.error("Failed to get model " + modelId, e);
                            listener.onFailure(e);
                        }), context::restore));
                    }
                    return;
                } else if (FunctionName.needDeployFirst(functionName)) {
                    listener.onFailure(new IllegalArgumentException("Model not ready yet. Please deploy the model first."));
                    return;
                } else {
                    workerNodes = nodeHelper.getEligibleNodeIds(functionName);
                }
            } else {
                mlModelManager.removeAutoDeployModel(modelId);
            }
            mlTaskDispatcher.dispatchPredictTask(workerNodes, actionListener);
        } catch (Exception e) {
            log.error("Failed to predict model " + modelId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Start prediction task
     * @param request MLPredictionTaskRequest
     * @param listener Action listener
     */
    @Override
    protected void executeTask(MLPredictionTaskRequest request, ActionListener<MLTaskResponse> listener) {
        MLInputDataType inputDataType = request.getMlInput().getInputDataset().getInputDataType();
        Instant now = Instant.now();
        String modelId = request.getModelId();
        FunctionName functionName = request.getMlInput().getFunctionName();
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .modelId(modelId)
            .taskType(MLTaskType.PREDICTION)
            .inputType(inputDataType)
            .functionName(functionName)
            .state(MLTaskState.CREATED)
            .workerNodes(ImmutableList.of(clusterService.localNode().getId()))
            .createTime(now)
            .lastUpdateTime(now)
            .async(false)
            .build();
        MLInput mlInput = request.getMlInput();
        switch (inputDataType) {
            case SEARCH_QUERY:
                ActionListener<MLInputDataset> dataFrameActionListener = ActionListener.wrap(dataSet -> {
                    MLInput newInput = mlInput.toBuilder().inputDataset(dataSet).build();
                    predict(modelId, mlTask, newInput, listener);
                }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    handleAsyncMLTaskFailure(mlTask, e);
                    listener.onFailure(e);
                });
                mlInputDatasetHandler
                    .parseSearchQueryInput(mlInput.getInputDataset(), threadedActionListener(functionName, dataFrameActionListener));
                break;
            case DATA_FRAME:
            case TEXT_DOCS:
            default:
                String threadPoolName = getPredictThreadPool(functionName);
                threadPool.executor(threadPoolName).execute(() -> { predict(modelId, mlTask, mlInput, listener); });
                break;
        }
    }

    private boolean checkModelAutoDeployEnabled(MLModel mlModel) {
        if (mlModel.getDeploySetting() == null) {
            return true;
        }
        return mlModel.getDeploySetting().getIsAutoDeployEnabled();
    }

    private String getPredictThreadPool(FunctionName functionName) {
        return functionName == FunctionName.REMOTE ? REMOTE_PREDICT_THREAD_POOL : PREDICT_THREAD_POOL;
    }

    private void predict(String modelId, MLTask mlTask, MLInput mlInput, ActionListener<MLTaskResponse> listener) {
        ActionListener<MLTaskResponse> internalListener = wrappedCleanupListener(listener, mlTask.getTaskId());
        // track ML task count and add ML task into cache
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
        mlStats
            .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
            .increment();
        if (modelId != null) {
            mlStats.createModelCounterStatIfAbsent(modelId, ActionName.PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        }
        mlTask.setState(MLTaskState.RUNNING);
        mlTaskManager.add(mlTask);

        FunctionName functionName = mlInput.getFunctionName();
        Predictable predictor = mlModelManager.getPredictor(modelId);
        boolean modelReady = predictor != null && predictor.isModelReady();
        if (!modelReady && FunctionName.isAutoDeployEnabled(autoDeploymentEnabled, functionName)) {
            log.info("Auto deploy model {} to local node", modelId);
            Instant now = Instant.now();
            MLTask mlDeployTask = MLTask
                .builder()
                .taskId(UUID.randomUUID().toString())
                .functionName(functionName)
                .async(false)
                .taskType(MLTaskType.DEPLOY_MODEL)
                .createTime(now)
                .lastUpdateTime(now)
                .state(MLTaskState.RUNNING)
                .workerNodes(Arrays.asList(clusterService.localNode().getId()))
                .build();
            mlModelManager.deployModel(modelId, null, functionName, false, true, mlDeployTask, ActionListener.wrap(s -> {
                runPredict(modelId, mlTask, mlInput, functionName, internalListener);
            }, e -> {
                log.error("Failed to auto deploy model " + modelId, e);
                internalListener.onFailure(e);
            }));
            return;
        }

        runPredict(modelId, mlTask, mlInput, functionName, internalListener);
    }

    private void runPredict(
        String modelId,
        MLTask mlTask,
        MLInput mlInput,
        FunctionName algorithm,
        ActionListener<MLTaskResponse> internalListener
    ) {
        // run predict
        if (modelId != null) {
            Predictable predictor = mlModelManager.getPredictor(modelId);
            if (predictor != null) {
                try {
                    if (!predictor.isModelReady()) {
                        throw new IllegalArgumentException("Model not ready: " + modelId);
                    }
                    if (mlInput.getAlgorithm() == FunctionName.REMOTE) {
                        long startTime = System.nanoTime();
                        ActionListener<MLTaskResponse> trackPredictDurationListener = ActionListener.wrap(output -> {
                            handleAsyncMLTaskComplete(mlTask);
                            mlModelManager.trackPredictDuration(modelId, startTime);
                            internalListener.onResponse(output);
                        }, e -> handlePredictFailure(mlTask, internalListener, e, false, modelId));
                        predictor.asyncPredict(mlInput, trackPredictDurationListener);
                    } else {
                        MLOutput output = mlModelManager.trackPredictDuration(modelId, () -> predictor.predict(mlInput));
                        if (output instanceof MLPredictionOutput) {
                            ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
                        }
                        // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                        handleAsyncMLTaskComplete(mlTask);
                        internalListener.onResponse(new MLTaskResponse(output));
                    }
                    return;
                } catch (Exception e) {
                    log.error("Failed to predict model " + modelId, e);
                    handlePredictFailure(mlTask, internalListener, e, false, modelId);
                    return;
                }
            } else if (FunctionName.needDeployFirst(algorithm)) {
                throw new IllegalArgumentException("Model not ready to be used: " + modelId);
            }

            // search model by model id.
            try (ThreadContext.StoredContext context = threadPool.getThreadContext().stashContext()) {
                ActionListener<GetResponse> getModelListener = ActionListener.wrap(r -> {
                    if (r == null || !r.isExists()) {
                        internalListener.onFailure(new ResourceNotFoundException("No model found, please check the modelId."));
                        return;
                    }
                    try (
                        XContentParser xContentParser = XContentType.JSON
                            .xContent()
                            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getSourceAsString())
                    ) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, xContentParser.nextToken(), xContentParser);
                        GetResponse getResponse = r;
                        String algorithmName = getResponse.getSource().get(ALGORITHM_FIELD).toString();
                        MLModel mlModel = MLModel.parse(xContentParser, algorithmName);
                        mlModel.setModelId(modelId);
                        User resourceUser = mlModel.getUser();
                        User requestUser = getUserContext(client);
                        if (!checkUserPermissions(requestUser, resourceUser, modelId)) {
                            // The backend roles of request user and resource user doesn't have intersection
                            OpenSearchException e = new OpenSearchException(
                                "User: " + requestUser.getName() + " does not have permissions to run predict by model: " + modelId
                            );
                            handlePredictFailure(mlTask, internalListener, e, false, modelId);
                            return;
                        }
                        // run predict
                        if (mlTaskManager.contains(mlTask.getTaskId())) {
                            mlTaskManager.updateTaskStateAsRunning(mlTask.getTaskId(), mlTask.isAsync());
                        }
                        MLOutput output = mlEngine.predict(mlInput, mlModel);
                        if (output instanceof MLPredictionOutput) {
                            ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
                        }

                        // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                        handleAsyncMLTaskComplete(mlTask);
                        MLTaskResponse response = MLTaskResponse.builder().output(output).build();
                        internalListener.onResponse(response);
                    } catch (Exception e) {
                        log.error("Failed to predict model " + modelId, e);
                        internalListener.onFailure(e);
                    }

                }, e -> {
                    log.error("Failed to predict " + mlInput.getAlgorithm() + ", modelId: " + mlTask.getModelId(), e);
                    handlePredictFailure(mlTask, internalListener, e, true, modelId);
                });
                GetRequest getRequest = new GetRequest(ML_MODEL_INDEX, mlTask.getModelId());
                client
                    .get(
                        getRequest,
                        threadedActionListener(
                            mlTask.getFunctionName(),
                            ActionListener.runBefore(getModelListener, () -> context.restore())
                        )
                    );
            } catch (Exception e) {
                log.error("Failed to get model " + mlTask.getModelId(), e);
                handlePredictFailure(mlTask, internalListener, e, true, modelId);
            }
        } else {
            IllegalArgumentException e = new IllegalArgumentException("ModelId is invalid");
            log.error("ModelId is invalid", e);
            handlePredictFailure(mlTask, internalListener, e, false, modelId);
        }
    }

    private <T> ThreadedActionListener<T> threadedActionListener(FunctionName functionName, ActionListener<T> listener) {
        String threadPoolName = getPredictThreadPool(functionName);
        return new ThreadedActionListener<>(log, threadPool, threadPoolName, listener, false);
    }

    private void handlePredictFailure(
        MLTask mlTask,
        ActionListener<MLTaskResponse> listener,
        Exception e,
        boolean trackFailure,
        String modelId
    ) {
        if (trackFailure) {
            mlStats
                .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.PREDICT, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                .increment();
            mlStats.createModelCounterStatIfAbsent(modelId, ActionName.PREDICT, MLActionLevelStat.ML_ACTION_FAILURE_COUNT);
            mlStats.getStat(MLNodeLevelStat.ML_FAILURE_COUNT).increment();
        }
        handleAsyncMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
