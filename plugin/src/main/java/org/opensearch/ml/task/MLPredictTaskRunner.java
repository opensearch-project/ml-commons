/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
import static org.opensearch.ml.permission.AccessController.checkUserPermissions;
import static org.opensearch.ml.permission.AccessController.getUserContext;
import static org.opensearch.ml.plugin.MachineLearningPlugin.PREDICT_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.REMOTE_PREDICT_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
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
import org.opensearch.ml.stats.otel.counters.MLOperationalMetricsCounter;
import org.opensearch.ml.stats.otel.metrics.OperationalMetric;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportResponseHandler;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportRequestOptions;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.stream.StreamTransportResponse;

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

    public static final String BUCKET_FIELD = "bucket";
    public static final String REGION_FIELD = "region";

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
    protected String getTransportStreamActionName() {
        return MLPredictionStreamTaskAction.NAME;
    }

    @Override
    protected TransportResponseHandler<MLTaskResponse> getResponseHandler(ActionListener<MLTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLTaskResponse::new);
    }

    @Override
    protected TransportResponseHandler<MLTaskResponse> getResponseStreamHandler(MLPredictionTaskRequest request) {
        TransportChannel channel = request.getStreamingChannel();
        return new StreamTransportResponseHandler<MLTaskResponse>() {
            @Override
            public void handleStreamResponse(StreamTransportResponse<MLTaskResponse> streamResponse) {
                try {
                    MLTaskResponse response;
                    while ((response = streamResponse.nextResponse()) != null) {
                        channel.sendResponseBatch(response);
                    }
                    channel.completeStream();
                    streamResponse.close();
                } catch (Exception e) {
                    streamResponse.cancel("Stream error", e);
                }
            }

            @Override
            public void handleException(TransportException exp) {
                try {
                    channel.sendResponse(exp);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String executor() {
                return ThreadPool.Names.SAME;
            }

            @Override
            public MLTaskResponse read(StreamInput in) throws IOException {
                return new MLTaskResponse(in);
            }
        };
    }

    @Override
    public void dispatchTask(
        FunctionName functionName,
        MLPredictionTaskRequest request,
        TransportService transportService,
        ActionListener<MLTaskResponse> listener
    ) {
        String modelId = request.getModelId();
        Map<String, String> dlq;
        String bucketName, region;
        if (request.getMlInput().getInputDataset() instanceof RemoteInferenceInputDataSet) {
            RemoteInferenceInputDataSet inputDataset = (RemoteInferenceInputDataSet) request.getMlInput().getInputDataset();
            dlq = inputDataset.getDlq();
            if (dlq != null) {
                bucketName = dlq.get(BUCKET_FIELD);
                region = dlq.get(REGION_FIELD);

                if (bucketName == null || region == null) {
                    throw new IllegalArgumentException("DLQ bucketName or region cannot be null");
                }
                // TODO: check if we are able to input an object into the s3 bucket.
                // Or check permissions to DLQ write access
            }
        }

        try {
            ActionListener<DiscoveryNode> actionListener = ActionListener.wrap(node -> {
                if (clusterService.localNode().getId().equals(node.getId())) {
                    log.debug("Execute ML predict request {} locally on node {}", request.getRequestID(), node.getId());
                    request.setDispatchTask(false);
                    checkCBAndExecute(functionName, request, listener);
                } else {
                    log.debug("Execute ML predict request {} remotely on node {}", request.getRequestID(), node.getId());
                    request.setDispatchTask(false);
                    // Check if this is a streaming request
                    if (isStreamingRequest(request)) {
                        log.debug("Using streaming transport for request {}", request.getRequestID());
                        transportService
                            .sendRequest(
                                node,
                                getTransportStreamActionName(),
                                request,
                                TransportRequestOptions.builder().withType(TransportRequestOptions.Type.STREAM).build(),
                                getResponseStreamHandler(request)
                            );
                    } else {
                        transportService.sendRequest(node, getTransportActionName(), request, getResponseHandler(listener));
                    }
                }
            }, listener::onFailure);
            String[] workerNodes = mlModelManager.getWorkerNodes(modelId, functionName, true);
            String[] targetWorkerNodes = mlModelManager.getTargetWorkerNodes(modelId);

            if (requiresAutoDeployment(workerNodes, targetWorkerNodes)) {
                if (FunctionName.isAutoDeployEnabled(autoDeploymentEnabled, functionName)) {
                    try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                        mlModelManager.getModel(modelId, request.getTenantId(), ActionListener.runBefore(ActionListener.wrap(model -> {
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
                            boolean deployToAllNodes = model.isDeployToAllNodes();
                            if (deployToAllNodes) {
                                planningWorkerNodes = null;
                            }
                            MLModel modelBeingAutoDeployed = mlModelManager.addModelToAutoDeployCache(modelId, model);
                            if (modelBeingAutoDeployed == model) {
                                log.info(getErrorMessage("Automatically deploy model", modelId, isHidden));

                                MLDeployModelRequest deployModelRequest = new MLDeployModelRequest(
                                    modelId,
                                    request.getTenantId(),
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
        TransportChannel channel = request.getStreamingChannel();
        final String tenantId = request.getTenantId();
        MLInputDataType inputDataType = request.getMlInput().getInputDataset().getInputDataType();
        Instant now = Instant.now();
        String modelId = request.getModelId();
        FunctionName functionName = request.getMlInput().getFunctionName();

        MLInput mlInput = request.getMlInput();
        ActionType actionType = null;
        if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            actionType = ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getActionType();
        }
        actionType = actionType == null ? ActionType.PREDICT : actionType;
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .modelId(modelId)
            .taskType(actionType.equals(ActionType.BATCH_PREDICT) ? MLTaskType.BATCH_PREDICTION : MLTaskType.PREDICTION)
            .inputType(inputDataType)
            .functionName(functionName)
            .state(MLTaskState.CREATED)
            .workerNodes(ImmutableList.of(clusterService.localNode().getId()))
            .createTime(now)
            .lastUpdateTime(now)
            .async(false)
            .tenantId(tenantId)
            .build();
        if (actionType.equals(ActionType.BATCH_PREDICT)) {
            mlModelManager.checkMaxBatchJobTask(mlTask, ActionListener.wrap(exceedLimits -> {
                if (exceedLimits) {
                    String error =
                        "Exceeded maximum limit for BATCH_PREDICTION tasks. To increase the limit, update the plugins.ml_commons.max_batch_inference_tasks setting.";
                    log.warn(error + " in task " + mlTask.getTaskId());
                    listener.onFailure(new OpenSearchStatusException(error, RestStatus.TOO_MANY_REQUESTS));
                } else {
                    executePredictionByInputDataType(inputDataType, modelId, mlInput, mlTask, functionName, tenantId, listener, channel);
                }
            }, exception -> {
                log.error("Failed to check the maximum BATCH_PREDICTION Task limits", exception);
                listener.onFailure(exception);
            }));
            return;
        }
        executePredictionByInputDataType(inputDataType, modelId, mlInput, mlTask, functionName, tenantId, listener, channel);
    }

    @Override
    protected boolean isStreamingRequest(MLPredictionTaskRequest request) {
        return request.getStreamingChannel() != null;
    }

    private void executePredictionByInputDataType(
        MLInputDataType inputDataType,
        String modelId,
        MLInput mlInput,
        MLTask mlTask,
        FunctionName functionName,
        String tenantId,
        ActionListener<MLTaskResponse> listener,
        TransportChannel channel
    ) {
        switch (inputDataType) {
            case SEARCH_QUERY:
                ActionListener<MLInputDataset> dataFrameActionListener = ActionListener.wrap(dataSet -> {
                    MLInput newInput = mlInput.toBuilder().inputDataset(dataSet).build();
                    predict(modelId, tenantId, mlTask, newInput, listener, channel);
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
                threadPool.executor(threadPoolName).execute(() -> { predict(modelId, tenantId, mlTask, mlInput, listener, channel); });
                break;
        }
    }

    private boolean checkModelAutoDeployEnabled(MLModel mlModel) {
        if (mlModel.getDeploySetting() == null || mlModel.getDeploySetting().getIsAutoDeployEnabled() == null) {
            return true;
        }
        return mlModel.getDeploySetting().getIsAutoDeployEnabled();
    }

    private String getPredictThreadPool(FunctionName functionName) {
        return functionName == FunctionName.REMOTE ? REMOTE_PREDICT_THREAD_POOL : PREDICT_THREAD_POOL;
    }

    private void predict(
        String modelId,
        String tenantId,
        MLTask mlTask,
        MLInput mlInput,
        ActionListener<MLTaskResponse> listener,
        TransportChannel channel
    ) {
        ActionListener<MLTaskResponse> internalListener = wrappedCleanupListener(listener, mlTask.getTaskId());
        // track ML task count and add ML task into cache
        ActionName actionName = getActionNameFromInput(mlInput);
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
        mlStats.createCounterStatIfAbsent(mlTask.getFunctionName(), actionName, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        if (modelId != null) {
            mlStats.createModelCounterStatIfAbsent(modelId, actionName, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
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
                .tenantId(tenantId)
                .build();
            mlModelManager.deployModel(modelId, tenantId, null, functionName, false, true, mlDeployTask, ActionListener.wrap(s -> {
                runPredict(modelId, tenantId, mlTask, mlInput, functionName, actionName, internalListener, channel);
            }, e -> {
                log.error("Failed to auto deploy model {}", modelId, e);
                internalListener.onFailure(e);
            }));
            return;
        }
        runPredict(modelId, tenantId, mlTask, mlInput, functionName, actionName, internalListener, channel);
    }

    // todo: add setting to control this as it can impact predict latency
    private void recordPredictMetrics(
        String modelId,
        double durationInMs,
        MLTaskResponse output,
        ActionListener<MLTaskResponse> internalListener
    ) {
        // todo: store tags in cache and fetch from cache
        mlModelManager.getModel(modelId, ActionListener.wrap(model -> {
            if (model != null) {
                if (model.getConnector() == null && model.getConnectorId() != null) {
                    mlModelManager.getConnector(model.getConnectorId(), model.getTenantId(), ActionListener.wrap(connector -> {
                        MLOperationalMetricsCounter
                            .getInstance()
                            .incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT, model.getTags(connector));

                        MLOperationalMetricsCounter
                            .getInstance()
                            .recordHistogram(OperationalMetric.MODEL_PREDICT_LATENCY, durationInMs, model.getTags(connector));

                        internalListener.onResponse(output);
                    }, e -> {
                        log.error("Failed to get connector for latency metrics", e);
                        internalListener.onResponse(output);
                    }));
                    return;
                }

                MLOperationalMetricsCounter.getInstance().incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT, model.getTags());
                MLOperationalMetricsCounter
                    .getInstance()
                    .recordHistogram(OperationalMetric.MODEL_PREDICT_LATENCY, durationInMs, model.getTags());

                internalListener.onResponse(output);
            } else {
                internalListener.onResponse(output);
            }
        }, e -> {
            log.error("Failed to get model for latency metrics", e);
            internalListener.onResponse(output);
        }));
    }

    private void runPredict(
        String modelId,
        String tenantId,
        MLTask mlTask,
        MLInput mlInput,
        FunctionName algorithm,
        ActionName actionName,
        ActionListener<MLTaskResponse> internalListener,
        TransportChannel channel
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
                            if (output.getOutput() instanceof ModelTensorOutput) {
                                validateOutputSchema(modelId, (ModelTensorOutput) output.getOutput());
                            }
                            if (mlTask.getTaskType().equals(MLTaskType.BATCH_PREDICTION)) {
                                Map<String, Object> remoteJob = new HashMap<>();
                                ModelTensorOutput tensorOutput = (ModelTensorOutput) output.getOutput();
                                if (tensorOutput != null
                                    && tensorOutput.getMlModelOutputs() != null
                                    && !tensorOutput.getMlModelOutputs().isEmpty()) {
                                    ModelTensors modelOutput = tensorOutput.getMlModelOutputs().get(0);
                                    Integer statusCode = modelOutput.getStatusCode();
                                    if (modelOutput.getMlModelTensors() != null && !modelOutput.getMlModelTensors().isEmpty()) {
                                        Map<String, Object> dataAsMap = (Map<String, Object>) modelOutput
                                            .getMlModelTensors()
                                            .get(0)
                                            .getDataAsMap();
                                        if (dataAsMap != null && statusCode != null && statusCode >= 200 && statusCode < 300) {
                                            remoteJob.putAll(dataAsMap);
                                            // put dlq info in remote job
                                            remoteJob.put("dlq", ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getDlq());
                                            mlTask.setRemoteJob(remoteJob);
                                            mlTask.setTaskId(null);
                                            mlTaskManager.createMLTask(mlTask, ActionListener.wrap(response -> {
                                                String taskId = response.getId();
                                                mlTask.setTaskId(taskId);
                                                MLPredictionOutput outputBuilder = new MLPredictionOutput(
                                                    taskId,
                                                    MLTaskState.CREATED.name(),
                                                    remoteJob
                                                );

                                                mlTaskManager.startTaskPollingJob();

                                                MLTaskResponse predictOutput = MLTaskResponse.builder().output(outputBuilder).build();
                                                internalListener.onResponse(predictOutput);
                                            }, e -> {
                                                logException("Failed to create task for batch predict model", e, log);
                                                internalListener.onFailure(e);
                                            }));
                                        } else {
                                            log.debug("Batch transform job output from remote model did not return the job ID");
                                            internalListener
                                                .onFailure(new ResourceNotFoundException("Unable to create batch transform job"));
                                        }
                                    } else {
                                        log.debug("ML Model Tensors are null or empty.");
                                        internalListener.onFailure(new ResourceNotFoundException("Unable to create batch transform job"));
                                    }
                                } else {
                                    log.debug("ML Model Outputs are null or empty.");
                                    internalListener.onFailure(new ResourceNotFoundException("Unable to create batch transform job"));
                                }
                            } else {
                                handleAsyncMLTaskComplete(mlTask);
                                mlModelManager.trackPredictDuration(modelId, startTime);
                                internalListener.onResponse(output);
                                // double durationInMs = (System.nanoTime() - startTime) / 1_000_000.0;
                                // recordPredictMetrics(modelId, durationInMs, output, internalListener);
                            }
                        }, e -> handlePredictFailure(mlTask, internalListener, e, shouldTrackRemoteFailure(e), modelId, actionName));
                        predictor.asyncPredict(mlInput, trackPredictDurationListener, channel); // with listener
                    } else {
                        // long startTime = System.nanoTime();
                        MLOutput output = mlModelManager.trackPredictDuration(modelId, () -> predictor.predict(mlInput)); // without
                                                                                                                          // listener
                        if (output instanceof MLPredictionOutput) {
                            ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
                        }
                        if (output instanceof ModelTensorOutput) {
                            validateOutputSchema(modelId, (ModelTensorOutput) output);
                        }
                        // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                        handleAsyncMLTaskComplete(mlTask);
                        internalListener.onResponse(new MLTaskResponse(output));
                        // double durationInMs = (System.nanoTime() - startTime) / 1_000_000.0;
                        // recordPredictMetrics(modelId, durationInMs, new MLTaskResponse(output), internalListener);
                    }
                    return;
                } catch (Exception e) {
                    log.error("Failed to predict model " + modelId, e);
                    handlePredictFailure(mlTask, internalListener, e, shouldTrackRemoteFailure(e), modelId, actionName);
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
                            handlePredictFailure(mlTask, internalListener, e, false, modelId, actionName);
                            return;
                        }
                        // run predict
                        if (mlTaskManager.contains(mlTask.getTaskId())) {
                            mlTaskManager.updateTaskStateAsRunning(mlTask.getTaskId(), tenantId, mlTask.isAsync());
                        }
                        MLOutput output = mlEngine.predict(mlInput, mlModel);
                        if (output instanceof MLPredictionOutput) {
                            ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
                        }
                        if (output instanceof ModelTensorOutput) {
                            validateOutputSchema(modelId, (ModelTensorOutput) output);
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
                    handlePredictFailure(mlTask, internalListener, e, true, modelId, actionName);
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
                handlePredictFailure(mlTask, internalListener, e, true, modelId, actionName);
            }
        } else {
            IllegalArgumentException e = new IllegalArgumentException("ModelId is invalid");
            log.error("ModelId is invalid", e);
            handlePredictFailure(mlTask, internalListener, e, false, modelId, actionName);
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
        String modelId,
        ActionName actionName
    ) {
        if (trackFailure) {
            mlStats.createCounterStatIfAbsent(mlTask.getFunctionName(), actionName, MLActionLevelStat.ML_ACTION_FAILURE_COUNT).increment();
            mlStats.createModelCounterStatIfAbsent(modelId, actionName, MLActionLevelStat.ML_ACTION_FAILURE_COUNT);
            mlStats.getStat(MLNodeLevelStat.ML_FAILURE_COUNT).increment();
        }
        handleAsyncMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }

    private ActionName getActionNameFromInput(MLInput mlInput) {
        ConnectorAction.ActionType actionType = null;
        if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            actionType = ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getActionType();
        }
        return (actionType == null) ? ActionName.PREDICT : ActionName.from(actionType.toString());
    }

    public void validateOutputSchema(String modelId, ModelTensorOutput output) {
        if (mlModelManager.getModelInterface(modelId) != null && mlModelManager.getModelInterface(modelId).get("output") != null) {
            String outputSchemaString = mlModelManager.getModelInterface(modelId).get("output");
            try {
                MLNodeUtils
                    .validateSchema(
                        outputSchemaString,
                        output.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS).toString()
                    );
            } catch (Exception e) {
                throw new OpenSearchStatusException(
                    "Error validating output schema, if you think this is expected, please update your 'output' field in the 'interface' field for this model: "
                        + e.getMessage(),
                    RestStatus.BAD_REQUEST
                );
            }
        }
    }

    boolean shouldTrackRemoteFailure(Exception e) {
        // Don't track failures for user configuration issues
        if (e instanceof IllegalArgumentException
            || e instanceof OpenSearchStatusException && ((OpenSearchStatusException) e).status() == RestStatus.BAD_REQUEST) {
            return false;
        }

        // Track failures for infrastructure/service issues
        return true;
    }

    private boolean requiresAutoDeployment(String[] workerNodes, String[] targetWorkerNodes) {
        return workerNodes == null
            || workerNodes.length == 0
            || (targetWorkerNodes != null && workerNodes.length < targetWorkerNodes.length);
    }
}
