/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL_INDEX;
import static org.opensearch.ml.permission.AccessController.checkUserPermissions;
import static org.opensearch.ml.permission.AccessController.getUserContext;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.OpenSearchException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.Model;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportResponseHandler;

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

    public MLPredictTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService,
        NamedXContentRegistry xContentRegistry
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher, mlCircuitBreakerService, clusterService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected String getTransportActionName() {
        return MLPredictionTaskAction.NAME;
    }

    @Override
    protected TransportResponseHandler<MLTaskResponse> getResponseHandler(ActionListener<MLTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLTaskResponse::new);
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
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .modelId(request.getModelId())
            .taskType(MLTaskType.PREDICTION)
            .inputType(inputDataType)
            .functionName(request.getMlInput().getFunctionName())
            .state(MLTaskState.CREATED)
            .workerNode(clusterService.localNode().getId())
            .createTime(now)
            .lastUpdateTime(now)
            .async(false)
            .build();
        MLInput mlInput = request.getMlInput();
        if (mlInput.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<DataFrame> dataFrameActionListener = ActionListener
                .wrap(dataFrame -> { predict(mlTask, dataFrame, request, listener); }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    handleAsyncMLTaskFailure(mlTask, e);
                    listener.onFailure(e);
                });
            mlInputDatasetHandler
                .parseSearchQueryInput(
                    mlInput.getInputDataset(),
                    new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, dataFrameActionListener, false)
                );
        } else {
            DataFrame inputDataFrame = mlInputDatasetHandler.parseDataFrameInput(mlInput.getInputDataset());
            threadPool.executor(TASK_THREAD_POOL).execute(() -> { predict(mlTask, inputDataFrame, request, listener); });
        }
    }

    private void predict(
        MLTask mlTask,
        DataFrame inputDataFrame,
        MLPredictionTaskRequest request,
        ActionListener<MLTaskResponse> listener
    ) {
        ActionListener<MLTaskResponse> internalListener = wrappedCleanupListener(listener, mlTask.getTaskId());
        // track ML task count and add ML task into cache
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT).increment();
        mlStats
            .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
            .increment();
        mlTaskManager.add(mlTask);

        // run predict
        if (request.getModelId() != null) {
            // search model by model id.
            try (ThreadContext.StoredContext context = threadPool.getThreadContext().stashContext()) {
                MLInput mlInput = request.getMlInput();
                ActionListener<GetResponse> getResponseListener = ActionListener.wrap(r -> {
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
                        MLModel mlModel = MLModel.parse(xContentParser);
                        User resourceUser = mlModel.getUser();
                        User requestUser = getUserContext(client);
                        if (!checkUserPermissions(requestUser, resourceUser, request.getModelId())) {
                            // The backend roles of request user and resource user doesn't have intersection
                            OpenSearchException e = new OpenSearchException(
                                "User: "
                                    + requestUser.getName()
                                    + " does not have permissions to run predict by model: "
                                    + request.getModelId()
                            );
                            handlePredictFailure(mlTask, internalListener, e, false);
                            return;
                        }
                        Model model = new Model();
                        model.setName(mlModel.getName());
                        model.setVersion(mlModel.getVersion());
                        byte[] decoded = Base64.getDecoder().decode(mlModel.getContent());
                        model.setContent(decoded);

                        // run predict
                        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING, mlTask.isAsync());
                        MLOutput output = MLEngine
                            .predict(mlInput.toBuilder().inputDataset(new DataFrameInputDataset(inputDataFrame)).build(), model);
                        if (output instanceof MLPredictionOutput) {
                            ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
                        }

                        // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                        handleAsyncMLTaskComplete(mlTask);
                        MLTaskResponse response = MLTaskResponse.builder().output(output).build();
                        internalListener.onResponse(response);
                    } catch (Exception e) {
                        log.error("Failed to predict model " + request.getModelId(), e);
                        internalListener.onFailure(e);
                    }

                }, e -> {
                    log.error("Failed to predict " + mlInput.getAlgorithm() + ", modelId: " + mlTask.getModelId(), e);
                    handlePredictFailure(mlTask, internalListener, e, true);
                });
                GetRequest getRequest = new GetRequest(ML_MODEL_INDEX, mlTask.getModelId());
                client.get(getRequest, ActionListener.runBefore(getResponseListener, () -> context.restore()));
            } catch (Exception e) {
                log.error("Failed to get model " + mlTask.getModelId(), e);
                handlePredictFailure(mlTask, internalListener, e, true);
            }
        } else {
            IllegalArgumentException e = new IllegalArgumentException("ModelId is invalid");
            log.error("ModelId is invalid", e);
            handlePredictFailure(mlTask, internalListener, e, false);
        }
    }

    private void handlePredictFailure(MLTask mlTask, ActionListener<MLTaskResponse> listener, Exception e, boolean trackFailure) {
        if (trackFailure) {
            mlStats
                .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.PREDICT, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                .increment();
            mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT).increment();
        }
        handleAsyncMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
