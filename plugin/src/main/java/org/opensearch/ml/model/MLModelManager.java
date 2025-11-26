/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.XContentType.JSON;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.NOT_FOUND;
import static org.opensearch.ml.common.CommonValue.UNDEPLOYED;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.MLTask.ERROR_FIELD;
import static org.opensearch.ml.common.MLTask.FUNCTION_NAME_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_BATCH_INFERENCE_TASKS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_BATCH_INGESTION_TASKS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
import static org.opensearch.ml.engine.ModelHelper.CHUNK_FILES;
import static org.opensearch.ml.engine.ModelHelper.CHUNK_SIZE;
import static org.opensearch.ml.engine.ModelHelper.MODEL_FILE_HASH;
import static org.opensearch.ml.engine.ModelHelper.MODEL_SIZE_IN_BYTES;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.CLIENT;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.CLUSTER_SERVICE;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.CONNECTOR_PRIVATE_IP_ENABLED;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.GUARDRAILS;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.RATE_LIMITER;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SCRIPT_SERVICE;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SDK_CLIENT;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SETTINGS;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.USER_RATE_LIMITER_MAP;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.XCONTENT_REGISTRY;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.ML_ENGINE;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.MODEL_HELPER;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel.MODEL_ZIP_FILE;
import static org.opensearch.ml.engine.utils.FileUtils.calculateFileHash;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.plugin.MachineLearningPlugin.DEPLOY_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.REGISTER_THREAD_POOL;
import static org.opensearch.ml.stats.ActionName.REGISTER;
import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_REQUEST_COUNT;
import static org.opensearch.ml.utils.MLExceptionUtils.CONTROLLER_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;
import static org.opensearch.ml.utils.MLNodeUtils.checkOpenCircuitBreaker;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.io.File;
import java.nio.file.Path;
import java.security.PrivilegedActionException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.model.Guardrails;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.MLExecutable;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.utils.FileUtils;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLExceptionUtils;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectResponse;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.script.ScriptService;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import lombok.extern.log4j.Log4j2;

/**
 * Manager class for ML models. It contains ML model related operations like
 * register, deploy model etc.
 */
@Log4j2
public class MLModelManager {

    public static final int TIMEOUT_IN_MILLIS = 5000;
    public static final long MODEL_FILE_SIZE_LIMIT = 4L * 1024 * 1024 * 1024;// 4GB

    private final Client client;
    private final SdkClient sdkClient;
    private final ClusterService clusterService;
    private final ScriptService scriptService;
    private final ThreadPool threadPool;
    private final NamedXContentRegistry xContentRegistry;
    private final ModelHelper modelHelper;
    private Settings settings;

    private final MLModelCacheHelper modelCacheHelper;
    private final MLStats mlStats;
    private final MLCircuitBreakerService mlCircuitBreakerService;
    private final MLIndicesHandler mlIndicesHandler;
    private final MLTaskManager mlTaskManager;
    private final MLEngine mlEngine;
    private final DiscoveryNodeHelper nodeHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private volatile Integer maxModelPerNode;
    private volatile Integer maxRegisterTasksPerNode;
    private volatile Integer maxDeployTasksPerNode;
    private volatile Integer maxBatchInferenceTasks;
    private volatile Integer maxBatchIngestionTasks;

    public static final ImmutableSet<MLModelState> MODEL_DONE_STATES = ImmutableSet
        .of(
            MLModelState.TRAINED,
            MLModelState.REGISTERED,
            MLModelState.DEPLOYED,
            MLModelState.PARTIALLY_DEPLOYED,
            MLModelState.DEPLOY_FAILED,
            MLModelState.UNDEPLOYED
        );

    public MLModelManager(
        ClusterService clusterService,
        ScriptService scriptService,
        Client client,
        SdkClient sdkClient,
        ThreadPool threadPool,
        NamedXContentRegistry xContentRegistry,
        ModelHelper modelHelper,
        Settings settings,
        MLStats mlStats,
        MLCircuitBreakerService mlCircuitBreakerService,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager,
        MLModelCacheHelper modelCacheHelper,
        MLEngine mlEngine,
        DiscoveryNodeHelper nodeHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.threadPool = threadPool;
        this.xContentRegistry = xContentRegistry;
        this.modelHelper = modelHelper;
        this.settings = settings;
        this.clusterService = clusterService;
        this.scriptService = scriptService;
        this.modelCacheHelper = modelCacheHelper;
        this.mlStats = mlStats;
        this.mlCircuitBreakerService = mlCircuitBreakerService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;
        this.mlEngine = mlEngine;
        this.nodeHelper = nodeHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;

        this.maxModelPerNode = ML_COMMONS_MAX_MODELS_PER_NODE.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MAX_MODELS_PER_NODE, it -> maxModelPerNode = it);

        maxRegisterTasksPerNode = ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE, it -> maxRegisterTasksPerNode = it);

        maxDeployTasksPerNode = ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE, it -> maxDeployTasksPerNode = it);

        maxBatchInferenceTasks = ML_COMMONS_MAX_BATCH_INFERENCE_TASKS.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MAX_BATCH_INFERENCE_TASKS, it -> maxBatchInferenceTasks = it);

        maxBatchIngestionTasks = ML_COMMONS_MAX_BATCH_INGESTION_TASKS.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MAX_BATCH_INGESTION_TASKS, it -> maxBatchIngestionTasks = it);
    }

    public void registerModelMeta(MLRegisterModelMetaInput mlRegisterModelMetaInput, ActionListener<String> listener) {
        try {
            FunctionName functionName = mlRegisterModelMetaInput.getFunctionName();
            mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
            mlStats.createCounterStatIfAbsent(functionName, REGISTER, ML_ACTION_REQUEST_COUNT).increment();
            String modelGroupId = mlRegisterModelMetaInput.getModelGroupId();
            if (Strings.isBlank(modelGroupId)) {
                uploadMLModelMeta(mlRegisterModelMetaInput, "1", listener);
            } else {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
                    GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);
                    client.get(getModelGroupRequest, ActionListener.wrap(modelGroup -> {
                        if (modelGroup.isExists()) {
                            Map<String, Object> modelGroupSource = modelGroup.getSourceAsMap();
                            int updatedVersion = incrementLatestVersion(modelGroupSource);
                            UpdateRequest updateModelGroupRequest = createUpdateModelGroupRequest(
                                modelGroupSource,
                                modelGroupId,
                                modelGroup.getSeqNo(),
                                modelGroup.getPrimaryTerm(),
                                updatedVersion
                            );

                            client.update(updateModelGroupRequest, ActionListener.wrap(r -> {
                                uploadMLModelMeta(mlRegisterModelMetaInput, updatedVersion + "", wrappedListener);
                            }, e -> {
                                log.error("Failed to update model group", e);
                                wrappedListener.onFailure(e);
                            }));
                        } else {
                            log.error("Model group not found");
                            wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                        }
                    }, e -> {
                        if (e instanceof IndexNotFoundException) {
                            wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                        } else {
                            log.error("Failed to get model group", e);
                            wrappedListener.onFailure(new MLValidationException("Failed to get model group"));
                        }
                    }));
                } catch (Exception e) {
                    log.error("Failed to register model", e);
                    listener.onFailure(e);
                }
            }
        } catch (final Exception e) {
            log.error("Failed to init model index", e);
            listener.onFailure(e);
        }
    }

    private void uploadMLModelMeta(MLRegisterModelMetaInput mlRegisterModelMetaInput, String version, ActionListener<String> listener) {
        FunctionName functionName = mlRegisterModelMetaInput.getFunctionName();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
            String modelName = mlRegisterModelMetaInput.getName();
            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                if (!res) {
                    wrappedListener.onFailure(new RuntimeException("No response to create ML Model index"));
                    return;
                }
                Instant now = Instant.now();
                MLModel mlModelMeta = MLModel
                    .builder()
                    .name(modelName)
                    .algorithm(functionName)
                    .version(version)
                    .modelGroupId(mlRegisterModelMetaInput.getModelGroupId())
                    .description(mlRegisterModelMetaInput.getDescription())
                    .isEnabled(mlRegisterModelMetaInput.getIsEnabled())
                    .rateLimiter(mlRegisterModelMetaInput.getRateLimiter())
                    .modelFormat(mlRegisterModelMetaInput.getModelFormat())
                    .modelState(MLModelState.REGISTERING)
                    .modelConfig(mlRegisterModelMetaInput.getModelConfig())
                    .deploySetting(mlRegisterModelMetaInput.getDeploySetting())
                    .totalChunks(mlRegisterModelMetaInput.getTotalChunks())
                    .modelContentHash(mlRegisterModelMetaInput.getModelContentHashValue())
                    .modelContentSizeInBytes(mlRegisterModelMetaInput.getModelContentSizeInBytes())
                    .isHidden(mlRegisterModelMetaInput.getIsHidden())
                    .modelInterface(mlRegisterModelMetaInput.getModelInterface())
                    .createdTime(now)
                    .lastUpdateTime(now)
                    .build();
                IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);

                if (mlRegisterModelMetaInput.getIsHidden() != null && mlRegisterModelMetaInput.getIsHidden()) {
                    indexRequest.id(modelName);
                }
                indexRequest.source(mlModelMeta.toXContent(XContentBuilder.builder(JSON.xContent()), EMPTY_PARAMS));
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                client.index(indexRequest, ActionListener.wrap(response -> {
                    log.debug("Index model meta doc successfully {}", modelName);
                    wrappedListener.onResponse(response.getId());
                }, e -> {
                    deleteOrUpdateModelGroup(
                        mlRegisterModelMetaInput.getModelGroupId(),
                        mlRegisterModelMetaInput.getDoesVersionCreateModelGroup(),
                        version
                    );
                    log.error("Failed to index model meta doc", e);
                    wrappedListener.onFailure(e);
                }));
            }, ex -> {
                log.error("Failed to init model index", ex);
                wrappedListener.onFailure(ex);
            }));
        } catch (Exception e) {
            log.error("Failed to register model", e);
            listener.onFailure(e);
        }
    }

    /**
     * @param sdkClient            metadata client
     * @param mlRegisterModelInput register model input for remote models
     * @param mlTask               ML task
     * @param listener             action listener
     */
    public void registerMLRemoteModel(
        SdkClient sdkClient,
        MLRegisterModelInput mlRegisterModelInput,
        MLTask mlTask,
        ActionListener<MLRegisterModelResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            checkAndAddRunningTask(mlTask, maxRegisterTasksPerNode);
            mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
            mlStats.createCounterStatIfAbsent(mlTask.getFunctionName(), REGISTER, ML_ACTION_REQUEST_COUNT).increment();
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();

            String modelGroupId = mlRegisterModelInput.getModelGroupId();
            GetDataObjectRequest getModelGroupRequest = GetDataObjectRequest
                .builder()
                .index(ML_MODEL_GROUP_INDEX)
                .tenantId(mlRegisterModelInput.getTenantId())
                .id(modelGroupId)
                .build();

            sdkClient.getDataObjectAsync(getModelGroupRequest).whenComplete((r, throwable) -> {
                if (throwable == null) {
                    try {
                        GetResponse getModelGroupResponse = r.getResponse();
                        if (getModelGroupResponse.isExists()) {
                            Map<String, Object> modelGroupSourceMap = getModelGroupResponse.getSourceAsMap();
                            int updatedVersion = incrementLatestVersion(modelGroupSourceMap);
                            modelGroupSourceMap.put(MLModelGroup.LATEST_VERSION_FIELD, updatedVersion);
                            modelGroupSourceMap.put(MLModelGroup.LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
                            UpdateDataObjectRequest updateDataObjectRequest = UpdateDataObjectRequest
                                .builder()
                                .index(ML_MODEL_GROUP_INDEX)
                                .id(modelGroupId)
                                .tenantId(mlRegisterModelInput.getTenantId())
                                .ifSeqNo(getModelGroupResponse.getSeqNo())
                                .ifPrimaryTerm(getModelGroupResponse.getPrimaryTerm())
                                .dataObject(modelGroupSourceMap)
                                .build();
                            try (ThreadContext.StoredContext innerContext = client.threadPool().getThreadContext().stashContext()) {

                                sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((ur, ut) -> {
                                    if (ut == null) {
                                        indexRemoteModel(sdkClient, mlRegisterModelInput, mlTask, updatedVersion + "", listener);
                                    } else {
                                        Exception e = SdkClientUtils.unwrapAndConvertToException(ut);
                                        log.error("Failed to update model group {}", modelGroupId, e);
                                        handleException(
                                            mlRegisterModelInput.getFunctionName(),
                                            mlTask.getTaskId(),
                                            mlRegisterModelInput.getTenantId(),
                                            e
                                        );
                                        listener.onFailure(e);
                                    }
                                });
                            }
                        } else {
                            log.error("Model group response is empty");
                            handleException(
                                mlRegisterModelInput.getFunctionName(),
                                mlTask.getTaskId(),
                                mlRegisterModelInput.getTenantId(),
                                new MLValidationException("Model group not found")
                            );
                            listener.onFailure(new MLResourceNotFoundException("Model Group Response is empty for " + modelGroupId));
                        }
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                } else {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                        log.error("Model group Index is missing");
                        handleException(
                            mlRegisterModelInput.getFunctionName(),
                            mlTask.getTaskId(),
                            mlRegisterModelInput.getTenantId(),
                            new MLResourceNotFoundException("Failed to get model group due to index missing")
                        );
                        listener.onFailure(new OpenSearchStatusException("Model group not found", RestStatus.NOT_FOUND));
                    } else {
                        log.error("Failed to get model group", e);
                        handleException(mlRegisterModelInput.getFunctionName(), mlTask.getTaskId(), mlRegisterModelInput.getTenantId(), e);
                        listener.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to register remote model", e);
            handleException(mlRegisterModelInput.getFunctionName(), mlTask.getTaskId(), mlRegisterModelInput.getTenantId(), e);
            listener.onFailure(e);
        } finally {
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
        }
    }

    /**
     * Register model. Basically download model file, split into chunks and save
     * into model index.
     *
     * @param registerModelInput register model input
     * @param mlTask             ML task
     */
    public void registerMLModel(MLRegisterModelInput registerModelInput, MLTask mlTask) {

        checkAndAddRunningTask(mlTask, maxRegisterTasksPerNode);
        try {
            mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
            mlStats.createCounterStatIfAbsent(mlTask.getFunctionName(), REGISTER, ML_ACTION_REQUEST_COUNT).increment();
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();

            String modelGroupId = registerModelInput.getModelGroupId();
            GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.get(getModelGroupRequest, ActionListener.runBefore(ActionListener.wrap(modelGroup -> {
                    if (modelGroup.isExists()) {
                        Map<String, Object> modelGroupSourceMap = modelGroup.getSourceAsMap();
                        int updatedVersion = incrementLatestVersion(modelGroupSourceMap);
                        UpdateRequest updateModelGroupRequest = createUpdateModelGroupRequest(
                            modelGroupSourceMap,
                            modelGroupId,
                            modelGroup.getSeqNo(),
                            modelGroup.getPrimaryTerm(),
                            updatedVersion
                        );
                        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                            client
                                .update(
                                    updateModelGroupRequest,
                                    ActionListener.wrap(r -> { uploadModel(registerModelInput, mlTask, updatedVersion + ""); }, e -> {
                                        log.error("Failed to update model group", e);
                                        handleException(
                                            registerModelInput.getFunctionName(),
                                            mlTask.getTaskId(),
                                            registerModelInput.getTenantId(),
                                            e
                                        );
                                    })
                                );
                        }
                    } else {
                        log.error("Model group not found");
                        handleException(
                            registerModelInput.getFunctionName(),
                            mlTask.getTaskId(),
                            registerModelInput.getTenantId(),
                            new MLValidationException("Model group not found")
                        );
                    }
                }, e -> {
                    if (e instanceof IndexNotFoundException) {
                        handleException(
                            registerModelInput.getFunctionName(),
                            mlTask.getTaskId(),
                            registerModelInput.getTenantId(),
                            new MLResourceNotFoundException("Failed to get model group")
                        );
                    } else {
                        log.error("Failed to get model group", e);
                        handleException(registerModelInput.getFunctionName(), mlTask.getTaskId(), registerModelInput.getTenantId(), e);
                    }
                }), context::restore));
            } catch (Exception e) {
                log.error("Failed to register model", e);
                handleException(registerModelInput.getFunctionName(), mlTask.getTaskId(), registerModelInput.getTenantId(), e);
            }
        } catch (Exception e) {
            handleException(registerModelInput.getFunctionName(), mlTask.getTaskId(), registerModelInput.getTenantId(), e);
        } finally {
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
        }
    }

    private UpdateRequest createUpdateModelGroupRequest(
        Map<String, Object> modelGroupSourceMap,
        String modelGroupId,
        long seqNo,
        long primaryTerm,
        int updatedVersion
    ) {
        modelGroupSourceMap.put(MLModelGroup.LATEST_VERSION_FIELD, updatedVersion);
        modelGroupSourceMap.put(MLModelGroup.LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
        UpdateRequest updateModelGroupRequest = new UpdateRequest();

        updateModelGroupRequest
            .index(ML_MODEL_GROUP_INDEX)
            .id(modelGroupId)
            .setIfSeqNo(seqNo)
            .setIfPrimaryTerm(primaryTerm)
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .doc(modelGroupSourceMap);

        return updateModelGroupRequest;
    }

    private int incrementLatestVersion(Map<String, Object> modelGroupSourceMap) {
        return Integer.parseInt(modelGroupSourceMap.get(MLModelGroup.LATEST_VERSION_FIELD).toString()) + 1;
    }

    private void indexRemoteModel(
        SdkClient sdkClient,
        MLRegisterModelInput registerModelInput,
        MLTask mlTask,
        String modelVersion,
        ActionListener<MLRegisterModelResponse> listener
    ) {

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            if (registerModelInput.getConnector() != null) {
                ActionListener<Boolean> encryptSuccessfulListener = ActionListener.wrap(r -> {
                    innerIndexRemoteModel(sdkClient, registerModelInput, mlTask, modelVersion, listener);
                }, e -> {
                    log.error("Failed to encrypt credentials in connector", e);
                    listener.onFailure(e);
                });
                registerModelInput.getConnector().encrypt(mlEngine::encrypt, registerModelInput.getTenantId(), encryptSuccessfulListener);
            } else {
                innerIndexRemoteModel(sdkClient, registerModelInput, mlTask, modelVersion, listener);
            }
        }
    }

    private void innerIndexRemoteModel(
        SdkClient sdkClient,
        MLRegisterModelInput registerModelInput,
        MLTask mlTask,
        String modelVersion,
        ActionListener<MLRegisterModelResponse> listener
    ) {
        {
            String taskId = mlTask.getTaskId();
            FunctionName functionName = mlTask.getFunctionName();
            String modelName = registerModelInput.getModelName();
            String version = modelVersion == null ? registerModelInput.getVersion() : modelVersion;
            Instant now = Instant.now();
            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(boolResponse -> {
                if (!boolResponse) {
                    listener.onFailure(new RuntimeException("No response to create ML Model index"));
                    return;
                }
                MLModel mlModelMeta = MLModel
                    .builder()
                    .name(modelName)
                    .algorithm(functionName)
                    .modelGroupId(registerModelInput.getModelGroupId())
                    .version(version)
                    .description(registerModelInput.getDescription())
                    .rateLimiter(registerModelInput.getRateLimiter())
                    .isEnabled(registerModelInput.getIsEnabled())
                    .modelFormat(registerModelInput.getModelFormat())
                    .modelState(MLModelState.REGISTERED)
                    .connector(registerModelInput.getConnector())
                    .connectorId(registerModelInput.getConnectorId())
                    .modelConfig(registerModelInput.getModelConfig())
                    .deploySetting(registerModelInput.getDeploySetting())
                    .createdTime(now)
                    .lastUpdateTime(now)
                    .isHidden(registerModelInput.getIsHidden())
                    .guardrails(registerModelInput.getGuardrails())
                    .modelInterface(registerModelInput.getModelInterface())
                    .tenantId(registerModelInput.getTenantId())
                    .build();

                PutDataObjectRequest putModelMetaRequest = PutDataObjectRequest
                    .builder()
                    .index(ML_MODEL_INDEX)
                    .id(Boolean.TRUE.equals(registerModelInput.getIsHidden()) ? modelName : null)
                    .tenantId(registerModelInput.getTenantId())
                    .dataObject(mlModelMeta)
                    .build();

                // index remote model doc
                ActionListener<IndexResponse> indexListener = ActionListener.wrap(modelMetaRes -> {
                    String modelId = modelMetaRes.getId();
                    mlTask.setModelId(modelId);
                    log.info("create new model meta doc {} for upload task {}", modelId, taskId);
                    mlTaskManager
                        .updateMLTask(
                            taskId,
                            registerModelInput.getTenantId(),
                            Map.of(MODEL_ID_FIELD, modelId, STATE_FIELD, COMPLETED),
                            5000,
                            true
                        );
                    if (registerModelInput.isDeployModel()) {
                        deployModelAfterRegistering(registerModelInput, modelId);
                    }
                    listener.onResponse(new MLRegisterModelResponse(taskId, MLTaskState.CREATED.name(), modelId));
                }, e -> {
                    log.error("Failed to index model meta doc", e);
                    handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                    listener.onFailure(e);
                });

                ThreadedActionListener<IndexResponse> putListener = threadedActionListener(REGISTER_THREAD_POOL, indexListener);
                sdkClient.putDataObjectAsync(putModelMetaRequest).whenComplete(SdkClientUtils.wrapPutCompletion(putListener));
            }, error -> {
                // failed to initialize the model index
                log.error("Failed to init model index", error);
                handleException(functionName, taskId, registerModelInput.getTenantId(), error);
                listener.onFailure(error);
            }));
        }
    }

    @VisibleForTesting
    void indexRemoteModel(MLRegisterModelInput registerModelInput, MLTask mlTask, String modelVersion) {
        String taskId = mlTask.getTaskId();
        FunctionName functionName = mlTask.getFunctionName();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {

            String modelName = registerModelInput.getModelName();
            String version = modelVersion == null ? registerModelInput.getVersion() : modelVersion;
            Instant now = Instant.now();
            if (registerModelInput.getConnector() != null) {
                ActionListener<Boolean> encryptSuccessfulListener = ActionListener.wrap(r -> {
                    mlIndicesHandler.initModelIndexIfAbsent(ActionListener.runBefore(ActionListener.wrap(res -> {
                        if (!res) {
                            handleException(
                                functionName,
                                taskId,
                                registerModelInput.getTenantId(),
                                new RuntimeException("No response to create ML Model index")
                            );
                            return;
                        }
                        MLModel mlModelMeta = MLModel
                            .builder()
                            .name(modelName)
                            .algorithm(functionName)
                            .modelGroupId(registerModelInput.getModelGroupId())
                            .version(version)
                            .description(registerModelInput.getDescription())
                            .rateLimiter(registerModelInput.getRateLimiter())
                            .isEnabled(registerModelInput.getIsEnabled())
                            .modelFormat(registerModelInput.getModelFormat())
                            .modelState(MLModelState.REGISTERED)
                            .connector(registerModelInput.getConnector())
                            .connectorId(registerModelInput.getConnectorId())
                            .modelConfig(registerModelInput.getModelConfig())
                            .deploySetting(registerModelInput.getDeploySetting())
                            .createdTime(now)
                            .lastUpdateTime(now)
                            .isHidden(registerModelInput.getIsHidden())
                            .guardrails(registerModelInput.getGuardrails())
                            .modelInterface(registerModelInput.getModelInterface())
                            .tenantId(registerModelInput.getTenantId())
                            .build();

                        PutDataObjectRequest putModelMetaRequest = PutDataObjectRequest
                            .builder()
                            .index(ML_MODEL_INDEX)
                            .id(Boolean.TRUE.equals(registerModelInput.getIsHidden()) ? modelName : null)
                            .tenantId(registerModelInput.getTenantId())
                            .dataObject(mlModelMeta)
                            .build();

                        IndexRequest indexModelMetaRequest = new IndexRequest(ML_MODEL_INDEX);
                        if (registerModelInput.getIsHidden() != null && registerModelInput.getIsHidden()) {
                            indexModelMetaRequest.id(modelName);
                        }
                        indexModelMetaRequest.source(mlModelMeta.toXContent(XContentBuilder.builder(JSON.xContent()), EMPTY_PARAMS));
                        indexModelMetaRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                        // create model meta doc
                        ActionListener<IndexResponse> indexListener = ActionListener.wrap(modelMetaRes -> {
                            String modelId = modelMetaRes.getId();
                            mlTask.setModelId(modelId);
                            log.info("create new model meta doc {} for upload task {}", modelId, taskId);
                            mlTaskManager
                                .updateMLTask(
                                    taskId,
                                    registerModelInput.getTenantId(),
                                    Map.of(MODEL_ID_FIELD, modelId, STATE_FIELD, COMPLETED),
                                    5000,
                                    true
                                );
                            if (registerModelInput.isDeployModel()) {
                                deployModelAfterRegistering(registerModelInput, modelId);
                            }
                        }, e -> {
                            log.error("Failed to index model meta doc", e);
                            handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                        });

                        ThreadedActionListener<IndexResponse> putListener = threadedActionListener(REGISTER_THREAD_POOL, indexListener);
                        sdkClient.putDataObjectAsync(putModelMetaRequest).whenComplete(SdkClientUtils.wrapPutCompletion(putListener));

                    }, e -> {
                        log.error("Failed to init model index", e);
                        handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                    }), context::restore));
                }, e -> {
                    log.error("Failed to encrypt credentials in connector", e);
                    handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                });
                registerModelInput.getConnector().encrypt(mlEngine::encrypt, registerModelInput.getTenantId(), encryptSuccessfulListener);
            }
        } catch (Exception e) {
            logException("Failed to upload model", e, log);
            handleException(functionName, taskId, registerModelInput.getTenantId(), e);
        }
    }

    private void uploadModel(MLRegisterModelInput registerModelInput, MLTask mlTask, String modelVersion) throws PrivilegedActionException {
        if (registerModelInput.getUrl() != null) {
            registerModelFromUrl(registerModelInput, mlTask, modelVersion);
        } else if (registerModelInput.getFunctionName() == FunctionName.REMOTE || registerModelInput.getConnectorId() != null) {
            indexRemoteModel(registerModelInput, mlTask, modelVersion);
        } else {
            registerPrebuiltModel(registerModelInput, mlTask, modelVersion);
        }
    }

    private void registerModelFromUrl(MLRegisterModelInput registerModelInput, MLTask mlTask, String modelVersion) {
        String taskId = mlTask.getTaskId();
        FunctionName functionName = mlTask.getFunctionName();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            String modelName = registerModelInput.getModelName();
            String version = modelVersion == null ? registerModelInput.getVersion() : modelVersion;
            String modelGroupId = registerModelInput.getModelGroupId();
            Instant now = Instant.now();
            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.runBefore(ActionListener.wrap(res -> {
                if (!res) {
                    handleException(
                        functionName,
                        taskId,
                        registerModelInput.getTenantId(),
                        new RuntimeException("No response to create ML Model index")
                    );
                    return;
                }
                MLModel mlModelMeta = MLModel
                    .builder()
                    .name(modelName)
                    .modelGroupId(modelGroupId)
                    .algorithm(functionName)
                    .version(version)
                    .description(registerModelInput.getDescription())
                    .rateLimiter(registerModelInput.getRateLimiter())
                    .isEnabled(registerModelInput.getIsEnabled())
                    .modelFormat(registerModelInput.getModelFormat())
                    .modelState(MLModelState.REGISTERING)
                    .modelConfig(registerModelInput.getModelConfig())
                    .deploySetting(registerModelInput.getDeploySetting())
                    .createdTime(now)
                    .lastUpdateTime(now)
                    .isHidden(registerModelInput.getIsHidden())
                    .guardrails(registerModelInput.getGuardrails())
                    .modelInterface(registerModelInput.getModelInterface())
                    .build();
                IndexRequest indexModelMetaRequest = new IndexRequest(ML_MODEL_INDEX);
                if (functionName == FunctionName.METRICS_CORRELATION) {
                    indexModelMetaRequest.id(functionName.name());
                }
                if (registerModelInput.getIsHidden() != null && registerModelInput.getIsHidden()) {
                    indexModelMetaRequest.id(modelName);
                }
                indexModelMetaRequest.source(mlModelMeta.toXContent(XContentBuilder.builder(JSON.xContent()), EMPTY_PARAMS));
                indexModelMetaRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                // create model meta doc
                ActionListener<IndexResponse> listener = ActionListener.wrap(modelMetaRes -> {
                    String modelId = modelMetaRes.getId();
                    mlTask.setModelId(modelId);
                    log.info("create new model meta doc {} for register model task {}", modelId, taskId);
                    // model group id is not present in request body.
                    registerModel(registerModelInput, taskId, functionName, modelName, version, modelId);
                }, e -> {
                    log.error("Failed to index model meta doc", e);
                    handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                });
                client.index(indexModelMetaRequest, threadedActionListener(REGISTER_THREAD_POOL, listener));
            }, e -> {
                log.error("Failed to init model index", e);
                handleException(functionName, taskId, registerModelInput.getTenantId(), e);
            }), context::restore));
        } catch (Exception e) {
            logException("Failed to register model", e, log);
            handleException(functionName, taskId, registerModelInput.getTenantId(), e);
        }
    }

    private void registerModel(
        MLRegisterModelInput registerModelInput,
        String taskId,
        FunctionName functionName,
        String modelName,
        String version,
        String modelId
    ) {
        modelHelper
            .downloadAndSplit(
                registerModelInput.getModelFormat(),
                modelId,
                modelName,
                version,
                registerModelInput.getUrl(),
                registerModelInput.getHashValue(),
                functionName,
                ActionListener.wrap(result -> {
                    Long modelSizeInBytes = (Long) result.get(MODEL_SIZE_IN_BYTES);
                    if (modelSizeInBytes >= MODEL_FILE_SIZE_LIMIT) {
                        throw new MLException("Model file size exceeds the limit of 4GB: " + modelSizeInBytes);
                    }
                    List<String> chunkFiles = (List<String>) result.get(CHUNK_FILES);
                    String hashValue = (String) result.get(MODEL_FILE_HASH);
                    Semaphore semaphore = new Semaphore(1);
                    AtomicInteger uploaded = new AtomicInteger(0);
                    AtomicBoolean failedToUploadChunk = new AtomicBoolean(false);
                    // upload chunks
                    for (String name : chunkFiles) {
                        semaphore.tryAcquire(10, TimeUnit.SECONDS);
                        if (failedToUploadChunk.get()) {
                            throw new MLException("Failed to save model chunk");
                        }
                        File file = new File(name);
                        byte[] bytes = Files.toByteArray(file);
                        int chunkNum = Integer.parseInt(file.getName());
                        Instant now = Instant.now();
                        MLModel mlModel = MLModel
                            .builder()
                            .modelId(modelId)
                            .name(modelName)
                            .algorithm(functionName)
                            .version(version)
                            .modelFormat(registerModelInput.getModelFormat())
                            .rateLimiter(registerModelInput.getRateLimiter())
                            .isEnabled(registerModelInput.getIsEnabled())
                            .chunkNumber(chunkNum)
                            .totalChunks(chunkFiles.size())
                            .content(Base64.getEncoder().encodeToString(bytes))
                            .createdTime(now)
                            .lastUpdateTime(now)
                            .isHidden(registerModelInput.getIsHidden())
                            .guardrails(registerModelInput.getGuardrails())
                            .modelInterface(registerModelInput.getModelInterface())
                            .build();
                        IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
                        if (registerModelInput.getIsHidden() != null && registerModelInput.getIsHidden()) {
                            indexRequest.id(modelName);
                        }
                        String chunkId = getModelChunkId(modelId, chunkNum);
                        indexRequest.id(chunkId);
                        indexRequest.source(mlModel.toXContent(XContentBuilder.builder(JSON.xContent()), EMPTY_PARAMS));
                        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                        client.index(indexRequest, ActionListener.wrap(r -> {
                            uploaded.getAndIncrement();
                            if (uploaded.get() == chunkFiles.size()) {
                                updateModelRegisterStateAsDone(
                                    registerModelInput,
                                    taskId,
                                    modelId,
                                    modelSizeInBytes,
                                    chunkFiles,
                                    hashValue,
                                    version
                                );
                            } else {
                                deleteFileQuietly(file);
                            }
                            semaphore.release();
                        }, e -> {
                            log.error("Failed to index model chunk {}", chunkId, e);
                            failedToUploadChunk.set(true);
                            handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                            deleteFileQuietly(file);
                            // remove model doc as failed to upload model
                            deleteModel(modelId, registerModelInput, version);
                            semaphore.release();
                            deleteFileQuietly(mlEngine.getRegisterModelPath(modelId));
                        }));
                    }
                }, e -> {
                    log.error("Failed to index chunk file", e);
                    deleteFileQuietly(mlEngine.getRegisterModelPath(modelId));
                    deleteModel(modelId, registerModelInput, version);
                    handleException(functionName, taskId, registerModelInput.getTenantId(), e);
                })
            );
    }

    private void registerPrebuiltModel(MLRegisterModelInput registerModelInput, MLTask mlTask, String modelVersion)
        throws PrivilegedActionException {
        String taskId = mlTask.getTaskId();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        if (!modelHelper.isModelAllowed(registerModelInput, modelMetaList)) {
            throw new IllegalArgumentException("This model is not in the pre-trained model list, please check your parameters.");
        }
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, ActionListener.wrap(mlRegisterModelInput -> {
            mlTask.setFunctionName(mlRegisterModelInput.getFunctionName());
            mlTaskManager
                .updateMLTask(
                    taskId,
                    registerModelInput.getTenantId(),
                    Map.of(FUNCTION_NAME_FIELD, mlRegisterModelInput.getFunctionName()),
                    TIMEOUT_IN_MILLIS,
                    false
                );
            registerModelFromUrl(mlRegisterModelInput, mlTask, modelVersion);
        }, e -> {
            log.error("Failed to register prebuilt model", e);
            handleException(registerModelInput.getFunctionName(), taskId, registerModelInput.getTenantId(), e);
        }));
    }

    private <T> ThreadedActionListener<T> threadedActionListener(String threadPoolName, ActionListener<T> listener) {
        return new ThreadedActionListener<>(log, threadPool, threadPoolName, listener, false);
    }

    /**
     * Check if exceed running task limit and if circuit breaker is open.
     *
     * @param mlTask           ML task
     * @param runningTaskLimit limit
     */
    public void checkAndAddRunningTask(MLTask mlTask, Integer runningTaskLimit) {

        // for agent and remote model prediction we don't need to check circuit breaker
        if (Objects.nonNull(mlTask) && mlTask.getFunctionName() != FunctionName.REMOTE && mlTask.getFunctionName() != FunctionName.AGENT) {
            checkOpenCircuitBreaker(mlCircuitBreakerService, mlStats);
        }
        mlTaskManager.checkLimitAndAddRunningTask(mlTask, runningTaskLimit);
    }

    /**
     * Check if exceed batch job task limit
     *
     * @param mlTask ML task
     * @param listener ActionListener if the limit is exceeded
     */
    public void checkMaxBatchJobTask(MLTask mlTask, ActionListener<Boolean> listener) {
        MLTaskType taskType = mlTask.getTaskType();
        int maxLimit = taskType.equals(MLTaskType.BATCH_PREDICTION) ? maxBatchInferenceTasks : maxBatchIngestionTasks;
        mlTaskManager.checkMaxBatchJobTask(taskType, maxLimit, listener);
    }

    /**
     * Update model register state as done. This is only for local model. Not for remote model.
     * @param registerModelInput model input for local model registration
     * @param taskId id of the task
     * @param modelId id of the model
     * @param modelSizeInBytes size of the model in bytes
     * @param chunkFiles list of chunk files
     * @param hashValue model hash value
     * @param version model version
     */
    private void updateModelRegisterStateAsDone(
        MLRegisterModelInput registerModelInput,
        String taskId,
        String modelId,
        Long modelSizeInBytes,
        List<String> chunkFiles,
        String hashValue,
        String version
    ) {
        FunctionName functionName = registerModelInput.getFunctionName();
        deleteFileQuietly(mlEngine.getRegisterModelPath(modelId));
        Map<String, Object> updatedFields = Map
            .of(
                MLModel.MODEL_STATE_FIELD,
                MLModelState.REGISTERED,
                MLModel.LAST_REGISTERED_TIME_FIELD,
                Instant.now().toEpochMilli(),
                MLModel.TOTAL_CHUNKS_FIELD,
                chunkFiles.size(),
                MLModel.MODEL_CONTENT_HASH_VALUE_FIELD,
                hashValue,
                MLModel.MODEL_CONTENT_SIZE_IN_BYTES_FIELD,
                modelSizeInBytes
            );
        log.info("Model registered successfully, model id: {}, task id: {}", modelId, taskId);

        // For local model we don't support multi-tenancy. So we are providing tenant Id null by default.
        updateModel(modelId, null, updatedFields, ActionListener.wrap(updateResponse -> {
            mlTaskManager
                .updateMLTask(
                    taskId,
                    registerModelInput.getTenantId(),
                    Map.of(STATE_FIELD, COMPLETED, MODEL_ID_FIELD, modelId),
                    TIMEOUT_IN_MILLIS,
                    true
                );
            if (registerModelInput.isDeployModel()) {
                deployModelAfterRegistering(registerModelInput, modelId);
            }
        }, e -> {
            log.error("Failed to update model", e);
            handleException(functionName, taskId, registerModelInput.getTenantId(), e);
            deleteModel(modelId, registerModelInput, version);
        }));
    }

    @VisibleForTesting
    void deployModelAfterRegistering(MLRegisterModelInput registerModelInput, String modelId) {
        String[] modelNodeIds = registerModelInput.getModelNodeIds();
        log.debug("start deploying model after registering, modelId: {} on nodes: {}", modelId, Arrays.toString(modelNodeIds));
        MLDeployModelRequest request = new MLDeployModelRequest(modelId, registerModelInput.getTenantId(), modelNodeIds, false, true, true);
        ActionListener<MLDeployModelResponse> listener = ActionListener
            .wrap(r -> log.debug("model deployed, response {}", r), e -> log.error("Failed to deploy model", e));
        client.execute(MLDeployModelAction.INSTANCE, request, listener);
    }

    private void deleteModel(String modelId, MLRegisterModelInput registerModelInput, String modelVersion) {
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index(ML_MODEL_INDEX).id(modelId).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.delete(deleteRequest);
        DeleteByQueryRequest deleteChunksRequest = new DeleteByQueryRequest(ML_MODEL_INDEX)
            .setQuery(new TermQueryBuilder(MLModel.MODEL_ID_FIELD, modelId))
            .setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN)
            .setAbortOnVersionConflict(false);
        client.execute(DeleteByQueryAction.INSTANCE, deleteChunksRequest);
        deleteOrUpdateModelGroup(registerModelInput.getModelGroupId(), registerModelInput.getDoesVersionCreateModelGroup(), modelVersion);
    }

    private void deleteOrUpdateModelGroup(String modelGroupID, Boolean doesVersionCreateModelGroup, String modelVersion) {
        // This checks if model group is created when registering the version. If yes,
        // model group is deleted since the version registration
        // had failed. Else model group latest version is decremented by 1
        if (Boolean.TRUE.equals(doesVersionCreateModelGroup)) {
            DeleteRequest deleteModelGroupRequest = new DeleteRequest();
            deleteModelGroupRequest.index(ML_MODEL_GROUP_INDEX).id(modelGroupID).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.delete(deleteModelGroupRequest);
        } else {
            updateLatestVersionInModelGroup(
                modelGroupID,
                Integer.parseInt(modelVersion) - 1,
                ActionListener
                    .wrap(r -> log.debug("model group updated, response {}", r), e -> log.error("Failed to update model group", e))
            );
        }
    }

    private void updateLatestVersionInModelGroup(String modelGroupID, Integer latestVersion, ActionListener<UpdateResponse> listener) {
        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put(MLModelGroup.LATEST_VERSION_FIELD, latestVersion);
        updatedFields.put(MLModelGroup.LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
        UpdateRequest updateRequest = new UpdateRequest(ML_MODEL_GROUP_INDEX, modelGroupID);
        updateRequest.doc(updatedFields);
        updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    // I will raise another PR where tenantId will be provided to the task manager. That time
    // I will refactor updateMLTask method.
    private void handleException(FunctionName functionName, String taskId, String tenantId, Exception e) {
        if (!(e instanceof MLLimitExceededException)
            && !(e instanceof MLResourceNotFoundException)
            && !(e instanceof IllegalArgumentException)) {
            mlStats.createCounterStatIfAbsent(functionName, REGISTER, MLActionLevelStat.ML_ACTION_FAILURE_COUNT).increment();
            mlStats.getStat(MLNodeLevelStat.ML_FAILURE_COUNT).increment();
        }
        Map<String, Object> updated = Map.of(ERROR_FIELD, MLExceptionUtils.getRootCauseMessage(e), STATE_FIELD, FAILED);
        mlTaskManager.updateMLTask(taskId, tenantId, updated, TIMEOUT_IN_MILLIS, true);
    }

    /**
     * Get connector credential by connector id.
     * This code is developed only for batch. And currently multi-tenancy isn't implemented in batch
     * so by default, tenantId is provided null when we invoke getConnector method
     * @param connectorId connector id
     * @param connectorCredentialListener  listener
     */
    public void getConnectorCredential(String connectorId, ActionListener<Map<String, String>> connectorCredentialListener) {
        getConnector(connectorId, null, ActionListener.wrap(connector -> {
            mlEngine.getConnectorCredential(connector, connectorCredentialListener);
            log.info("Completed loading credential in the connector {}", connectorId);
        }, connectorCredentialListener::onFailure));
    }

    /**
     * Read model chunks from model index. Concat chunks into a whole model file,
     * then load
     * into memory.
     *
     * @param modelId          model id
     * @param tenantId         tenant id
     * @param modelContentHash model content hash value
     * @param functionName     function name
     * @param mlTask           ML task
     * @param listener         action listener
     */
    public void deployModel(
        String modelId,
        String tenantId,
        String modelContentHash,
        FunctionName functionName,
        boolean deployToAllNodes,
        boolean autoDeployModel,
        MLTask mlTask,
        ActionListener<String> listener
    ) {
        mlStats.createCounterStatIfAbsent(functionName, ActionName.DEPLOY, ML_ACTION_REQUEST_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
        mlStats.createModelCounterStatIfAbsent(modelId, ActionName.DEPLOY, ML_ACTION_REQUEST_COUNT).increment();
        List<String> workerNodes = mlTask.getWorkerNodes();
        if (modelCacheHelper.isModelDeployed(modelId)) {
            if (!autoDeployModel && workerNodes != null && !workerNodes.isEmpty()) {
                log.info("Set new target node ids {} for model {}", Arrays.toString(workerNodes.toArray(new String[0])), modelId);
                modelCacheHelper.setDeployToAllNodes(modelId, deployToAllNodes);
                modelCacheHelper.setTargetWorkerNodes(modelId, workerNodes);
                modelCacheHelper.refreshLastAccessTime(modelId);
            }
            listener.onResponse("successful");
            return;
        }
        if (functionName != FunctionName.REMOTE && modelCacheHelper.getLocalDeployedModels().length >= maxModelPerNode) {
            listener.onFailure(new IllegalArgumentException("Exceed max local model per node limit"));
            return;
        }
        int eligibleNodeCount = workerNodes.size();
        if (!autoDeployModel) {
            modelCacheHelper.initModelState(modelId, MLModelState.DEPLOYING, functionName, workerNodes, deployToAllNodes);
        } else {
            modelCacheHelper.initModelStateAutoDeploy(modelId, MLModelState.DEPLOYING, functionName, workerNodes);
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<String> wrappedListener = ActionListener.runBefore(listener, () -> {
                context.restore();
                modelCacheHelper.removeAutoDeployModel(modelId);
                modelCacheHelper.setIsAutoDeploying(modelId, false);
            });
            if (!autoDeployModel) {
                checkAndAddRunningTask(mlTask, maxDeployTasksPerNode);
            }
            this.getModel(modelId, tenantId, threadedActionListener(DEPLOY_THREAD_POOL, ActionListener.wrap(mlModel -> {
                modelCacheHelper.setIsModelEnabled(modelId, mlModel.getIsEnabled());
                modelCacheHelper.setModelInfo(modelId, mlModel);
                if (FunctionName.REMOTE == mlModel.getAlgorithm()
                    || (!FunctionName.isDLModel(mlModel.getAlgorithm()) && mlModel.getAlgorithm() != FunctionName.METRICS_CORRELATION)) {
                    // deploy remote model or model trained by built-in algorithm like kmeans
                    // deploy remote model with internal connector or model trained by built-in
                    // algorithm like kmeans
                    if (BooleanUtils.isTrue(mlModel.getIsControllerEnabled())) {
                        getController(modelId, ActionListener.wrap(controller -> {
                            setupUserRateLimiterMap(modelId, eligibleNodeCount, controller.getUserRateLimiter());
                            log.info("Successfully redeployed model controller for model " + modelId);
                            log.info("Trying to deploy remote model with model controller configured.");
                            deployRemoteOrBuiltInModel(mlModel, eligibleNodeCount, wrappedListener);
                        }, e -> {
                            log
                                .error(
                                    "Trying to deploy remote model with exceptions in re-deploying its model controller. Model ID: {}",
                                    modelId,
                                    e
                                );
                            deployRemoteOrBuiltInModel(mlModel, eligibleNodeCount, wrappedListener);
                        }));
                        return;
                    } else {
                        log.info("Trying to deploy remote or built-in model without model controller configured.");
                        deployRemoteOrBuiltInModel(mlModel, eligibleNodeCount, wrappedListener);
                    }
                    return;
                }

                setupRateLimiter(modelId, eligibleNodeCount, mlModel.getRateLimiter());
                setupMLGuard(modelId, tenantId, mlModel.getGuardrails());
                setupModelInterface(modelId, mlModel.getModelInterface());
                deployControllerWithDeployingModel(mlModel, eligibleNodeCount);
                // check circuit breaker before deploying custom model chunks
                checkOpenCircuitBreaker(mlCircuitBreakerService, mlStats);
                retrieveModelChunks(mlModel, ActionListener.wrap(modelZipFile -> {// read model chunks
                    String hash = calculateFileHash(modelZipFile);
                    if (modelContentHash != null && !modelContentHash.equals(hash)) {
                        log.error("Model content hash can't match original hash value");
                        removeModel(modelId);
                        wrappedListener.onFailure(new IllegalArgumentException("model content changed"));
                        return;
                    }
                    log.debug("Model content matches original hash value, continue deploying");
                    Map<String, Object> params = Map.of(MODEL_ZIP_FILE, modelZipFile, MODEL_HELPER, modelHelper, ML_ENGINE, mlEngine);
                    if (FunctionName.METRICS_CORRELATION.equals(mlModel.getAlgorithm())) {
                        MLExecutable mlExecutable = mlEngine.deployExecute(mlModel, params);
                        try {
                            modelCacheHelper.setMLExecutor(modelId, mlExecutable);
                            mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).increment();
                            modelCacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
                            modelCacheHelper.refreshLastAccessTime(modelId);
                            wrappedListener.onResponse("successful");
                        } catch (Exception e) {
                            log.error("Failed to add predictor to cache", e);
                            mlExecutable.close();
                            wrappedListener.onFailure(e);
                        }
                    } else {
                        Predictable predictable = mlEngine.deploy(mlModel, params);
                        try {
                            modelCacheHelper.setPredictor(modelId, predictable);
                            mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).increment();
                            modelCacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
                            modelCacheHelper.refreshLastAccessTime(modelId);
                            Long modelContentSizeInBytes = mlModel.getModelContentSizeInBytes();
                            long contentSize = modelContentSizeInBytes == null
                                ? mlModel.getTotalChunks() * CHUNK_SIZE
                                : modelContentSizeInBytes;
                            modelCacheHelper.setMemSizeEstimation(modelId, mlModel.getModelFormat(), contentSize);
                            wrappedListener.onResponse("successful");
                        } catch (Exception e) {
                            log.error("Failed to add predictor to cache", e);
                            predictable.close();
                            wrappedListener.onFailure(e);
                        }
                    }
                }, e -> {
                    log.error("Failed to retrieve model {}", modelId, e);
                    handleDeployModelException(modelId, functionName, wrappedListener, e);
                }));
            }, e -> {
                log.error("Failed to deploy model {}", modelId, e);
                handleDeployModelException(modelId, functionName, wrappedListener, e);
            })));
        } catch (Exception e) {
            handleDeployModelException(modelId, functionName, listener, e);
        } finally {
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
        }
    }

    /**
     * Read model chunks from model index. Concat chunks into a whole model file,
     * then load
     * into memory.
     *
     * TODO: I'll remove this method later. Currently this method is being used in multiple classes.
     *
     * @param modelId          model id
     * @param modelContentHash model content hash value
     * @param functionName     function name
     * @param mlTask           ML task
     * @param listener         action listener
     */
    public void deployModel(
        String modelId,
        String modelContentHash,
        FunctionName functionName,
        boolean deployToAllNodes,
        boolean autoDeployModel,
        MLTask mlTask,
        ActionListener<String> listener
    ) {
        mlStats.createCounterStatIfAbsent(functionName, ActionName.DEPLOY, ML_ACTION_REQUEST_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
        mlStats.createModelCounterStatIfAbsent(modelId, ActionName.DEPLOY, ML_ACTION_REQUEST_COUNT).increment();
        List<String> workerNodes = mlTask.getWorkerNodes();
        if (modelCacheHelper.isModelDeployed(modelId)) {
            if (!autoDeployModel && workerNodes != null && !workerNodes.isEmpty()) {
                log.info("Set new target node ids {} for model {}", Arrays.toString(workerNodes.toArray(new String[0])), modelId);
                modelCacheHelper.setDeployToAllNodes(modelId, deployToAllNodes);
                modelCacheHelper.setTargetWorkerNodes(modelId, workerNodes);
                modelCacheHelper.refreshLastAccessTime(modelId);
            }
            listener.onResponse("successful");
            return;
        }
        if (functionName != FunctionName.REMOTE && modelCacheHelper.getLocalDeployedModels().length >= maxModelPerNode) {
            listener.onFailure(new IllegalArgumentException("Exceed max local model per node limit"));
            return;
        }
        int eligibleNodeCount = workerNodes.size();
        if (!autoDeployModel) {
            modelCacheHelper.initModelState(modelId, MLModelState.DEPLOYING, functionName, workerNodes, deployToAllNodes);
        } else {
            modelCacheHelper.initModelStateAutoDeploy(modelId, MLModelState.DEPLOYING, functionName, workerNodes);
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<String> wrappedListener = ActionListener.runBefore(listener, () -> {
                context.restore();
                modelCacheHelper.removeAutoDeployModel(modelId);
                modelCacheHelper.setIsAutoDeploying(modelId, false);
            });
            if (!autoDeployModel) {
                checkAndAddRunningTask(mlTask, maxDeployTasksPerNode);
            }
            this.getModel(modelId, threadedActionListener(DEPLOY_THREAD_POOL, ActionListener.wrap(mlModel -> {
                modelCacheHelper.setIsModelEnabled(modelId, mlModel.getIsEnabled());
                modelCacheHelper.setModelInfo(modelId, mlModel);
                if (FunctionName.REMOTE == mlModel.getAlgorithm()
                    || (!FunctionName.isDLModel(mlModel.getAlgorithm()) && mlModel.getAlgorithm() != FunctionName.METRICS_CORRELATION)) {
                    // deploy remote model or model trained by built-in algorithm like kmeans
                    // deploy remote model with internal connector or model trained by built-in
                    // algorithm like kmeans
                    if (BooleanUtils.isTrue(mlModel.getIsControllerEnabled())) {
                        getController(modelId, ActionListener.wrap(controller -> {
                            setupUserRateLimiterMap(modelId, eligibleNodeCount, controller.getUserRateLimiter());
                            log.info("Successfully redeployed model controller for model " + modelId);
                            log.info("Trying to deploy remote model with model controller configured.");
                            deployRemoteOrBuiltInModel(mlModel, eligibleNodeCount, wrappedListener);
                        }, e -> {
                            log
                                .error(
                                    "Trying to deploy remote model with exceptions in re-deploying its model controller. Model ID: "
                                        + modelId,
                                    e
                                );
                            deployRemoteOrBuiltInModel(mlModel, eligibleNodeCount, wrappedListener);
                        }));
                        return;
                    } else {
                        log.info("Trying to deploy remote or built-in model without model controller configured.");
                        deployRemoteOrBuiltInModel(mlModel, eligibleNodeCount, wrappedListener);
                    }
                    return;
                }

                setupRateLimiter(modelId, eligibleNodeCount, mlModel.getRateLimiter());
                setupMLGuard(modelId, mlModel.getTenantId(), mlModel.getGuardrails());
                setupModelInterface(modelId, mlModel.getModelInterface());
                deployControllerWithDeployingModel(mlModel, eligibleNodeCount);
                // check circuit breaker before deploying custom model chunks
                checkOpenCircuitBreaker(mlCircuitBreakerService, mlStats);
                retrieveModelChunks(mlModel, ActionListener.wrap(modelZipFile -> {// read model chunks
                    String hash = calculateFileHash(modelZipFile);
                    if (modelContentHash != null && !modelContentHash.equals(hash)) {
                        log.error("Model content hash can't match original hash value");
                        removeModel(modelId);
                        wrappedListener.onFailure(new IllegalArgumentException("model content changed"));
                        return;
                    }
                    log.debug("Model content matches original hash value, continue deploying");
                    Map<String, Object> params = Map.of(MODEL_ZIP_FILE, modelZipFile, MODEL_HELPER, modelHelper, ML_ENGINE, mlEngine);
                    if (FunctionName.METRICS_CORRELATION.equals(mlModel.getAlgorithm())) {
                        MLExecutable mlExecutable = mlEngine.deployExecute(mlModel, params);
                        try {
                            modelCacheHelper.setMLExecutor(modelId, mlExecutable);
                            mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).increment();
                            modelCacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
                            modelCacheHelper.refreshLastAccessTime(modelId);
                            wrappedListener.onResponse("successful");
                        } catch (Exception e) {
                            log.error("Failed to add predictor to cache", e);
                            mlExecutable.close();
                            wrappedListener.onFailure(e);
                        }
                    } else {
                        Predictable predictable = mlEngine.deploy(mlModel, params);
                        try {
                            modelCacheHelper.setPredictor(modelId, predictable);
                            mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).increment();
                            modelCacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
                            modelCacheHelper.refreshLastAccessTime(modelId);
                            Long modelContentSizeInBytes = mlModel.getModelContentSizeInBytes();
                            long contentSize = modelContentSizeInBytes == null
                                ? mlModel.getTotalChunks() * CHUNK_SIZE
                                : modelContentSizeInBytes;
                            modelCacheHelper.setMemSizeEstimation(modelId, mlModel.getModelFormat(), contentSize);
                            wrappedListener.onResponse("successful");
                        } catch (Exception e) {
                            log.error("Failed to add predictor to cache", e);
                            predictable.close();
                            wrappedListener.onFailure(e);
                        }
                    }
                }, e -> {
                    log.error("Failed to retrieve model " + modelId, e);
                    handleDeployModelException(modelId, functionName, wrappedListener, e);
                }));
            }, e -> {
                log.error("Failed to deploy model " + modelId, e);
                handleDeployModelException(modelId, functionName, wrappedListener, e);
            })));
        } catch (Exception e) {
            handleDeployModelException(modelId, functionName, listener, e);
        } finally {
            mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
        }
    }

    private void deployRemoteOrBuiltInModel(MLModel mlModel, Integer eligibleNodeCount, ActionListener<String> wrappedListener) {
        String modelId = mlModel.getModelId();
        setupRateLimiter(modelId, eligibleNodeCount, mlModel.getRateLimiter());
        setupMLGuard(modelId, mlModel.getTenantId(), mlModel.getGuardrails());
        setupModelInterface(modelId, mlModel.getModelInterface());
        ActionListener<String> initModelActionListener = ActionListener.wrap(r -> {
            mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).increment();
            modelCacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
            modelCacheHelper.refreshLastAccessTime(modelId);
            wrappedListener.onResponse(r);
        }, e -> {
            log.error("Failed to deploy model, model id: {}", modelId, e);
            wrappedListener.onFailure(e);
        });
        if (mlModel.getConnector() != null || FunctionName.REMOTE != mlModel.getAlgorithm()) {
            setupParamsAndPredictable(modelId, mlModel, initModelActionListener);
            return;
        }
        log.info("Set connector {} for the model: {}", mlModel.getConnectorId(), modelId);
        getConnector(mlModel.getConnectorId(), mlModel.getTenantId(), ActionListener.wrap(connector -> {
            mlModel.setConnector(connector);
            setupParamsAndPredictable(modelId, mlModel, initModelActionListener);
            log.info("Completed setting connector {} in the model {}", mlModel.getConnectorId(), modelId);
        }, wrappedListener::onFailure));
    }

    private void setupParamsAndPredictable(String modelId, MLModel mlModel, ActionListener<String> listener) {
        Map<String, Object> params = setUpParameterMap(modelId, mlModel.getTenantId());
        ActionListener<Predictable> wrappedListener = ActionListener.wrap(r -> {
            modelCacheHelper.setPredictor(modelId, r);
            listener.onResponse("successful");
        }, e -> {
            log.error("Failed to deploy model", e);
            listener.onFailure(e);
        });
        mlEngine.deploy(mlModel, params, wrappedListener);
    }

    private Map<String, Object> setUpParameterMap(String modelId, String tenantId) {
        TokenBucket rateLimiter = getRateLimiter(modelId);
        Map<String, TokenBucket> userRateLimiterMap = getUserRateLimiterMap(modelId);
        MLGuard mlGuard = getMLGuard(modelId);

        Map<String, Object> params = new HashMap<>();
        params.put(ML_ENGINE, mlEngine);
        params.put(SCRIPT_SERVICE, scriptService);
        params.put(CLIENT, client);
        params.put(XCONTENT_REGISTRY, xContentRegistry);
        params.put(CLUSTER_SERVICE, clusterService);

        if (rateLimiter == null && userRateLimiterMap == null) {
            log.info("Setting up basic ML predictor parameters.");
        } else if (rateLimiter != null && userRateLimiterMap == null) {
            params.put(RATE_LIMITER, rateLimiter);
            log.info("Setting up basic ML predictor parameters with model level throttling.");
        } else if (rateLimiter == null) {
            params.put(USER_RATE_LIMITER_MAP, userRateLimiterMap);
            log.info("Setting up basic ML predictor parameters with user level throttling.");
        } else {
            params.put(RATE_LIMITER, rateLimiter);
            params.put(USER_RATE_LIMITER_MAP, userRateLimiterMap);
            log.info("Setting up basic ML predictor parameters with both model and user level throttling.");
        }

        if (mlGuard != null) {
            params.put(GUARDRAILS, mlGuard);
            log.info("Setting up ML guard parameter for ML predictor.");
        }
        params.put(CONNECTOR_PRIVATE_IP_ENABLED, mlFeatureEnabledSetting.isConnectorPrivateIpEnabled());
        params.put(SDK_CLIENT, sdkClient);
        params.put(SETTINGS, settings);
        return Collections.unmodifiableMap(params);
    }

    private void handleDeployModelException(String modelId, FunctionName functionName, ActionListener<String> listener, Exception e) {

        if (!(e instanceof MLLimitExceededException)
            && !(e instanceof MLResourceNotFoundException)
            && !(e instanceof IllegalArgumentException)) {
            mlStats.createCounterStatIfAbsent(functionName, ActionName.DEPLOY, MLActionLevelStat.ML_ACTION_FAILURE_COUNT).increment();
            mlStats.getStat(MLNodeLevelStat.ML_FAILURE_COUNT).increment();
        }
        removeModel(modelId);
        listener.onFailure(e);
    }

    public synchronized void updateModelCache(String modelId, ActionListener<String> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
            getModel(modelId, ActionListener.wrap(mlModel -> {
                int eligibleNodeCount = getWorkerNodes(modelId, mlModel.getAlgorithm()).length;
                modelCacheHelper.setIsModelEnabled(modelId, mlModel.getIsEnabled());
                setupRateLimiter(modelId, eligibleNodeCount, mlModel.getRateLimiter());
                setupMLGuard(modelId, mlModel.getTenantId(), mlModel.getGuardrails());
                setupModelInterface(modelId, mlModel.getModelInterface());
                if (mlModel.getAlgorithm() == FunctionName.REMOTE) {
                    String completeModelCacheUpdateMessage = String
                        .format(Locale.ROOT, "Completed the model cache update for the remote model %s", modelId);
                    if (mlModel.getConnector() != null) {
                        setupParamsAndPredictable(modelId, mlModel, wrappedListener);
                        log.info(completeModelCacheUpdateMessage);
                    } else {
                        getConnector(mlModel.getConnectorId(), mlModel.getTenantId(), ActionListener.wrap(connector -> {
                            mlModel.setConnector(connector);
                            setupParamsAndPredictable(modelId, mlModel, wrappedListener);
                            log.info(completeModelCacheUpdateMessage);
                        }, wrappedListener::onFailure));
                    }
                }
                wrappedListener.onResponse("Successfully updated model cache for the model " + modelId);
                log.info("Completed the model cache update for the model {}", modelId);
            }, wrappedListener::onFailure));
        } catch (Exception e) {
            log.error("Failed to updated model cache for the model {}", modelId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Deploy the model controller with a model id. This method should be called
     * AFTER a model is deployed.
     * If you want to implement similar behavior during model deploy,
     * deployControllerWithDeployingModel is the one supposed be called.
     *
     * @param modelId  ml model ID
     * @param listener action listener
     */
    public synchronized void deployControllerWithDeployedModel(String modelId, ActionListener<String> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            if (!mlFeatureEnabledSetting.isControllerEnabled()) {
                throw new IllegalStateException(CONTROLLER_DISABLED_ERR_MSG);
            }
            if (!modelCacheHelper.isModelDeployed(modelId)) {
                throw new OpenSearchStatusException(
                    "The model of this model controller has not deployed yet, please deploy the model first.",
                    RestStatus.CONFLICT
                );
            }
            ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
            getModel(modelId, ActionListener.wrap(mlModel -> {
                getController(modelId, ActionListener.wrap(controller -> {
                    int eligibleNodeCount = getWorkerNodes(modelId, mlModel.getAlgorithm()).length;
                    setupUserRateLimiterMap(modelId, eligibleNodeCount, controller.getUserRateLimiter());
                    if (mlModel.getAlgorithm() == FunctionName.REMOTE) {
                        String deployModelControllerCompleteMessage = String
                            .format(Locale.ROOT, "Deployed model controller for the remote model %s", modelId);
                        if (mlModel.getConnector() != null) {
                            setupParamsAndPredictable(modelId, mlModel, wrappedListener);
                            log.info(deployModelControllerCompleteMessage);
                        } else {
                            getConnector(mlModel.getConnectorId(), mlModel.getTenantId(), ActionListener.wrap(connector -> {
                                mlModel.setConnector(connector);
                                setupParamsAndPredictable(modelId, mlModel, wrappedListener);
                                log.info(deployModelControllerCompleteMessage);
                            }, wrappedListener::onFailure));
                        }
                        return;
                    }
                    wrappedListener.onResponse("Successfully deployed model controller for the model " + modelId);
                    log.info("Deployed model controller for the model {}", modelId);
                }, wrappedListener::onFailure));
            }, wrappedListener::onFailure));
        } catch (Exception e) {
            log.error("Failed to deploy model controller for the model {}", modelId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Undploy the model controller for a model.
     * Usually this method is called during deleting the model controller.
     *
     * @param modelId  ml model ID
     * @param listener action listener
     */
    public synchronized void undeployController(String modelId, ActionListener<String> listener) {
        if (modelCacheHelper.isModelDeployed(modelId)) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
                getModel(modelId, ActionListener.wrap(mlModel -> {
                    removeUserRateLimiterMap(modelId);
                    if (mlModel.getAlgorithm() == FunctionName.REMOTE) {
                        String undeployModelControllerCompleteMessage = String
                            .format(Locale.ROOT, "Undeployed model controller for the remote model %s", modelId);
                        if (mlModel.getConnector() != null) {
                            setupParamsAndPredictable(modelId, mlModel, wrappedListener);
                            log.info(undeployModelControllerCompleteMessage);
                        } else {
                            getConnector(mlModel.getConnectorId(), mlModel.getTenantId(), ActionListener.wrap(connector -> {
                                mlModel.setConnector(connector);
                                setupParamsAndPredictable(modelId, mlModel, wrappedListener);
                                log.info(undeployModelControllerCompleteMessage);
                            }, wrappedListener::onFailure));
                        }
                        return;
                    }
                    wrappedListener.onResponse("Successfully undeployed model controller for the model " + modelId);
                    log.info("Undeployed model controller for the model {}", modelId);
                }, wrappedListener::onFailure));
            } catch (Exception e) {
                log.error("Failed to undeploy model controller for the model {}", modelId, e);
                listener.onFailure(e);
            }
        } else if (isModelRunningOnNode(modelId)) {
            log
                .error(
                    "Failed to undeploy model controller because model is in ML cache but with a state other than deployed. Please check model: {}",
                    modelId,
                    new RuntimeException()
                );
            listener
                .onFailure(
                    new RuntimeException(
                        "Failed to undeploy model controller because model is in ML cache but with a state other than deployed. Please check model: "
                            + modelId
                    )
                );
        } else {
            log.info("Successfully deployed model controller from cache due to model not exist in cache. Model ID: {}", modelId);
            listener.onResponse("Successfully deployed model controller from cache due to model not exist in cache. Model ID: " + modelId);
        }
    }

    /**
     * Deploy the model controller for a model during model is deploying.
     *
     * @param mlModel  ml model
     * @param listener action listener
     */
    private synchronized void deployControllerWithDeployingModel(
        MLModel mlModel,
        Integer eligibleNodeCount,
        ActionListener<String> listener
    ) {
        String modelId = mlModel.getModelId();
        FetchSourceContext fetchContext = new FetchSourceContext(true);
        GetRequest getRequest = new GetRequest(ML_CONTROLLER_INDEX).id(modelId).fetchSourceContext(fetchContext);
        client.get(getRequest, ActionListener.wrap(r -> {
            if (r != null && r.isExists()) {
                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLController controller = MLController.parse(parser);
                    setupUserRateLimiterMap(modelId, eligibleNodeCount, controller.getUserRateLimiter());
                    log.info("Successfully redeployed model controller for model " + modelId);
                    listener.onResponse("Successfully redeployed model controller for model " + modelId);
                } catch (Exception e) {
                    log.error("Failed to parse ml task{}", r.getId(), e);
                    listener.onFailure(e);
                }
            } else if (!BooleanUtils.isTrue(mlModel.getIsControllerEnabled())) {
                // Not going to respond the failure here due to the model deploy can still work
                // well
                listener
                    .onResponse(
                        "No controller is deployed because the model "
                            + modelId
                            + " is expected not having an enabled model controller. Please use the create controller api to create one if this is unexpected."
                    );
                log
                    .debug(
                        "No controller is deployed because the model " + modelId + " is expected not having an enabled model controller."
                    );
            } else {
                listener.onFailure(new OpenSearchStatusException("Failed to find model controller", RestStatus.NOT_FOUND));
            }
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                if (!BooleanUtils.isTrue(mlModel.getIsControllerEnabled())) {
                    // Not going to respond the failure here due to the model deploy can still work
                    // well
                    listener
                        .onResponse(
                            "No controller is deployed because the model "
                                + modelId
                                + " is expected not having an enabled model controller. Please use the create model controller api to create one if this is unexpected."
                        );
                    log
                        .debug(
                            "No controller is deployed because the model "
                                + modelId
                                + " is expected not having an enabled model controller."
                        );
                } else {
                    listener.onFailure(new OpenSearchStatusException("Failed to find model controller", RestStatus.NOT_FOUND));
                }
            } else {
                log.error("Failed to re-deploy the model controller for model: {}", modelId, e);
                listener.onFailure(e);
            }
        }));
    }

    /**
     * Deploy the model controller for a model during model is deploying with
     * build-in listener.
     * Usually this method is called when re-deploying a previous un-deployed model
     * with the model controller.
     *
     * @param mlModel ml model
     */
    public void deployControllerWithDeployingModel(MLModel mlModel, Integer eligibleNodeCount) {
        if (!mlFeatureEnabledSetting.isControllerEnabled()) {
            throw new IllegalStateException(CONTROLLER_DISABLED_ERR_MSG);
        }
        if (mlModel.getModelState() != MLModelState.DEPLOYING) {
            throw new OpenSearchStatusException(
                "This method should only be called when model is in DEPLOYING state, but the model is in state: " + mlModel.getModelState(),
                RestStatus.CONFLICT
            );
        }
        deployControllerWithDeployingModel(mlModel, eligibleNodeCount, ActionListener.wrap(response -> {
            if (response.startsWith("Successfully")) {
                log.debug(response, mlModel.getModelId());
            } else if (response
                .endsWith(
                    "is expected not having a model controller. Please use the create model controller api to create one if this is unexpected."
                )) {
                log.warn(response);
            } else {
                log.error(response);
            }
        }, e -> log.error("Failed to re-deploy the model controller for model: {}", mlModel.getModelId(), e)));
    }

    private void setupRateLimiter(String modelId, Integer eligibleNodeCount, MLRateLimiter rateLimiter) {
        if (rateLimiter != null) {
            modelCacheHelper.setRateLimiter(modelId, createTokenBucket(eligibleNodeCount, rateLimiter));
        } else {
            modelCacheHelper.removeRateLimiter(modelId);
        }
    }

    private void setupUserRateLimiterMap(String modelId, Integer eligibleNodeCount, Map<String, MLRateLimiter> userRateLimiter) {
        if (userRateLimiter != null && !userRateLimiter.isEmpty()) {
            Map<String, TokenBucket> userRateLimiterMap = new HashMap<>();
            userRateLimiter.forEach((user, rateLimiter) -> userRateLimiterMap.put(user, createTokenBucket(eligibleNodeCount, rateLimiter)));
            modelCacheHelper.setUserRateLimiterMap(modelId, userRateLimiterMap);
        } else {
            modelCacheHelper.removeUserRateLimiterMap(modelId);
        }
    }

    private void removeUserRateLimiterMap(String modelId) {
        modelCacheHelper.removeUserRateLimiterMap(modelId);
    }

    /**
     * Construct a TokenBucket object from its rate limiter config.
     *
     * @param eligibleNodeCount eligible node count
     * @param rateLimiter       model rate limiter config
     * @return a TokenBucket object to enable throttling
     */
    private TokenBucket createTokenBucket(Integer eligibleNodeCount, MLRateLimiter rateLimiter) {
        if (rateLimiter.isValid()) {
            double limit = Double.parseDouble(rateLimiter.getLimit());
            TimeUnit unit = rateLimiter.getUnit();
            log
                .info(
                    "Initializing the rate limiter with setting {} per {} (TPS limit {}), evenly distributed on {} nodes",
                    limit,
                    unit,
                    limit / unit.toSeconds(1),
                    eligibleNodeCount
                );
            // Burst token must be greater than 1 to accept request
            return new TokenBucket(
                System::nanoTime,
                limit / unit.toNanos(1) / eligibleNodeCount,
                Math.max(limit / eligibleNodeCount, 1),
                Math.max(limit / eligibleNodeCount, 1)
            );
        }
        return null;
    }

    /**
     * Get model-level rate limiter with model id.
     *
     * @param modelId model id
     * @return a TokenBucket object to enable model-level throttling
     */
    public TokenBucket getRateLimiter(String modelId) {
        return modelCacheHelper.getRateLimiter(modelId);
    }

    /**
     * Get model-level rate limiter with model id.
     *
     * @param modelId model id
     * @return a map with user's name and its corresponding rate limiter object to
     *         track user-level throttling
     */
    public Map<String, TokenBucket> getUserRateLimiterMap(String modelId) {
        return modelCacheHelper.getUserRateLimiterMap(modelId);
    }

    /**
     * Set up model interface with model id.
     */
    private void setupModelInterface(String modelId, Map<String, String> modelInterface) {
        log.debug("Model interface for model: {} loaded into cache.", modelId);
        if (modelInterface != null) {
            modelCacheHelper.setModelInterface(modelId, modelInterface);
        } else {
            modelCacheHelper.removeModelInterface(modelId);
        }
    }

    /**
     * Get model interface with model id.
     *
     * @param modelId model id
     * @return a Map containing the model interface
     */
    public Map<String, String> getModelInterface(String modelId) {
        return modelCacheHelper.getModelInterface(modelId);
    }

    /**
     * Set up ML guard with model id.
     *
     * @param modelId model id
     * @param guardrails guardrail for the model
     */

    private void setupMLGuard(String modelId, String tenantId, Guardrails guardrails) {
        if (guardrails != null) {
            modelCacheHelper.setMLGuard(modelId, createMLGuard(guardrails, xContentRegistry, client, sdkClient, tenantId));
        } else {
            modelCacheHelper.removeMLGuard(modelId);
        }
    }

    private MLGuard createMLGuard(
        Guardrails guardrails,
        NamedXContentRegistry xContentRegistry,
        Client client,
        SdkClient sdkClient,
        String tenantId
    ) {
        return new MLGuard(guardrails, xContentRegistry, client, sdkClient, tenantId);
    }

    /**
     * Get ML guard with model id.
     *
     * @param modelId model id
     *
     * @return a ML guard
     */
    public MLGuard getMLGuard(String modelId) {
        return modelCacheHelper.getMLGuard(modelId);
    }

    /**
     * Get model from model index.
     *
     * @param modelId  model id
     * @param listener action listener
     */
    public void getModel(String modelId, ActionListener<MLModel> listener) {
        getModel(modelId, null, listener);
    }

    /**
     * Get model from model index.
     *
     * @param modelId  model id
     * @param tenantId  tenant id
     * @param listener action listener
     */
    public void getModel(String modelId, String tenantId, ActionListener<MLModel> listener) {
        getModel(modelId, tenantId, null, null, listener);
    }

    /**
     * Get model from model index with includes/excludes filter.
     *
     * TODO: I will remove this method later. Currently other classes are invoking this method.
     * @param modelId  model id
     * @param includes fields included
     * @param excludes fields excluded
     * @param listener action listener
     */
    public void getModel(String modelId, String[] includes, String[] excludes, ActionListener<MLModel> listener) {
        GetDataObjectRequest getRequest = GetDataObjectRequest
            .builder()
            .index(ML_MODEL_INDEX)
            .id(modelId)
            .fetchSourceContext(new FetchSourceContext(true, includes, excludes))
            .build();
        sdkClient.getDataObjectAsync(getRequest).whenComplete((r, throwable) -> {
            if (throwable == null) {
                try {
                    GetResponse gr = r.getResponse();
                    if (gr != null && gr.isExists()) {
                        try (
                            XContentParser parser = jsonXContent
                                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                        ) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            String algorithmName = r.source().get(ALGORITHM_FIELD).toString();

                            MLModel mlModel = MLModel.parse(parser, algorithmName);
                            mlModel.setModelId(modelId);
                            listener.onResponse(mlModel);
                        } catch (Exception e) {
                            log.error("Failed to parse ml task{}", r.id(), e);
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new OpenSearchStatusException("Failed to find model", RestStatus.NOT_FOUND));
                    }
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            } else {
                Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
                listener.onFailure(e);
            }
        });
    }

    /**
     * Get model from model index with includes/excludes filter.
     *
     * @param modelId  model id
     * @param tenantId  tenant id
     * @param includes fields included
     * @param excludes fields excluded
     * @param listener action listener
     */
    public void getModel(String modelId, String tenantId, String[] includes, String[] excludes, ActionListener<MLModel> listener) {
        GetDataObjectRequest getRequest = buildGetRequest(modelId, tenantId, includes, excludes);

        sdkClient.getDataObjectAsync(getRequest).whenComplete((response, throwable) -> {
            if (throwable != null) {
                handleError(throwable, listener);
                return;
            }

            processGetResponse(response, modelId, listener);
        });
    }

    private GetDataObjectRequest buildGetRequest(String modelId, String tenantId, String[] includes, String[] excludes) {
        return GetDataObjectRequest
            .builder()
            .index(ML_MODEL_INDEX)
            .id(modelId)
            .tenantId(tenantId)
            .fetchSourceContext(new FetchSourceContext(true, includes, excludes))
            .build();
    }

    private void handleError(Throwable throwable, ActionListener<MLModel> listener) {
        Exception exception = SdkClientUtils.unwrapAndConvertToException(throwable);
        listener.onFailure(exception);
    }

    private void processGetResponse(GetDataObjectResponse response, String modelId, ActionListener<MLModel> listener) {
        try {
            GetResponse getResponse = response.getResponse();
            if (getResponse == null || !getResponse.isExists()) {
                listener.onFailure(new OpenSearchStatusException("Failed to find model", RestStatus.NOT_FOUND));
                return;
            }

            parseAndReturnModel(
                getResponse,
                Optional.ofNullable(response.source().get(FUNCTION_NAME_FIELD)).orElse(response.source().get(ALGORITHM_FIELD)).toString(),
                modelId,
                listener
            );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void parseAndReturnModel(GetResponse getResponse, String algorithmName, String modelId, ActionListener<MLModel> listener) {
        try (
            XContentParser parser = jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            MLModel mlModel = MLModel.parse(parser, algorithmName);
            mlModel.setModelId(modelId);
            listener.onResponse(mlModel);
        } catch (Exception e) {
            log.error("Failed to parse ml model {}", modelId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Get model controller from model controller index.
     *
     * @param modelId  model id
     * @param listener action listener
     */
    public void getController(String modelId, ActionListener<MLController> listener) {
        FetchSourceContext fetchContext = new FetchSourceContext(true);
        GetRequest getRequest = new GetRequest(ML_CONTROLLER_INDEX).id(modelId).fetchSourceContext(fetchContext);
        client.get(getRequest, ActionListener.wrap(r -> {
            if (r != null && r.isExists()) {
                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLController controller = MLController.parse(parser);
                    listener.onResponse(controller);
                } catch (Exception e) {
                    log.error("Failed to parse ml task{}", r.getId(), e);
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new OpenSearchStatusException("Failed to find model controller", RestStatus.NOT_FOUND));
            }
        }, listener::onFailure));
    }

    /**
     * Get connector from connector index.
     *
     * @param connectorId connector id
     * @param tenantId tenant id
     * @param listener    action listener
     */
    public void getConnector(String connectorId, String tenantId, ActionListener<Connector> listener) {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_CONNECTOR_INDEX)
            .id(connectorId)
            .tenantId(tenantId)
            .build();

        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                log.debug("Completed Get Connector Request, id:{}", connectorId);
                ctx.restore();
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        log.error("Failed to get connector index", cause);
                        listener.onFailure(new OpenSearchStatusException("Failed to find connector", RestStatus.NOT_FOUND));
                    } else {
                        log.error("Failed to get ML connector {}", connectorId, cause);
                        listener.onFailure(cause);
                    }
                } else {
                    try {
                        GetResponse gr = r.getResponse();
                        if (gr != null && gr.isExists()) {
                            try (
                                XContentParser parser = MLNodeUtils
                                    .createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, gr.getSourceAsBytesRef())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                Connector connector = Connector.createConnector(parser);
                                listener.onResponse(connector);
                            } catch (Exception e) {
                                log.error("Failed to parse connector:{}", connectorId);
                                listener.onFailure(e);
                            }
                        } else {
                            listener
                                .onFailure(new OpenSearchStatusException("Failed to find connector:" + connectorId, RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                }
            });
        }
    }

    /**
     * Retreive a model's all chunks.
     *
     * @param mlModelMeta model meta
     * @param listener    action listener
     */
    private void retrieveModelChunks(MLModel mlModelMeta, ActionListener<File> listener) throws InterruptedException {
        String modelId = mlModelMeta.getModelId();
        String modelName = mlModelMeta.getName();
        Integer totalChunks = mlModelMeta.getTotalChunks();
        GetRequest getRequest = new GetRequest();
        getRequest.index(ML_MODEL_INDEX);
        getRequest.id();
        Semaphore semaphore = new Semaphore(1);
        AtomicBoolean stopNow = new AtomicBoolean(false);
        String modelZip = mlEngine.getDeployModelZipPath(modelId, modelName);
        ConcurrentLinkedDeque<File> chunkFiles = new ConcurrentLinkedDeque();
        AtomicInteger retrievedChunks = new AtomicInteger(0);
        for (int i = 0; i < totalChunks; i++) {
            semaphore.tryAcquire(10, TimeUnit.SECONDS);
            if (stopNow.get()) {
                throw new MLException("Failed to deploy model");
            }
            String modelChunkId = this.getModelChunkId(modelId, i);
            int currentChunk = i;
            this.getModel(modelChunkId, threadedActionListener(DEPLOY_THREAD_POOL, ActionListener.wrap(model -> {
                Path chunkPath = mlEngine.getDeployModelChunkPath(modelId, currentChunk);
                FileUtils.write(Base64.getDecoder().decode(model.getContent()), chunkPath.toString());
                chunkFiles.add(new File(chunkPath.toUri()));
                retrievedChunks.getAndIncrement();
                if (retrievedChunks.get() == totalChunks) {
                    File modelZipFile = new File(modelZip);
                    FileUtils.mergeFiles(chunkFiles, modelZipFile);
                    listener.onResponse(modelZipFile);
                }
                semaphore.release();
            }, e -> {
                stopNow.set(true);
                semaphore.release();
                log.error("Failed to retrieve model chunk {}", modelChunkId, e);
                if (retrievedChunks.get() == totalChunks - 1) {
                    listener.onFailure(new MLResourceNotFoundException("Fail to find model chunk " + modelChunkId));
                }
            })));
        }
    }

    /**
     * Update model with build-in listener.
     *
     * @param modelId       model id
     * @param tenantId      tenant id
     * @param updatedFields updated fields
     */
    public void updateModel(String modelId, String tenantId, Boolean isHidden, Map<String, Object> updatedFields) {
        updateModel(modelId, tenantId, updatedFields, ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug(getErrorMessage("Updated ML model successfully: {}", modelId, isHidden), response.status());
            } else {
                log.error(getErrorMessage("Failed to update provided ML model, status: {}", modelId, isHidden), response.status());
            }
        }, e -> { log.error(getErrorMessage("Failed to update the provided ML model", modelId, isHidden), e); }));
    }

    /**
     * Update model.
     *
     * @param modelId       model id
     * @param tenantId      tenant id
     * @param updatedFields updated fields
     * @param listener      action listener
     */
    public void updateModel(String modelId, String tenantId, Map<String, Object> updatedFields, ActionListener<UpdateResponse> listener) {
        if (updatedFields == null || updatedFields.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
            return;
        }
        Map<String, Object> newUpdatedFields = new HashMap<>();
        newUpdatedFields.putAll(updatedFields);
        newUpdatedFields.put(MLModel.LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());

        UpdateDataObjectRequest.Builder requestBuilder = UpdateDataObjectRequest
            .builder()
            .index(ML_MODEL_INDEX)
            .id(modelId)
            .tenantId(tenantId)
            .dataObject(newUpdatedFields);

        // Conditionally add retryOnConflict based on the provided condition
        if (updatedFields.containsKey(MLModel.MODEL_STATE_FIELD)
            && MODEL_DONE_STATES.contains(newUpdatedFields.get(MLModel.MODEL_STATE_FIELD))) {
            requestBuilder.retryOnConflict(3);
        }

        // Build the request
        UpdateDataObjectRequest updateDataObjectRequest = requestBuilder.build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {

            sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore(); // Restore the context once the operation is done
                handleUpdateDataObjectCompletionStage(r, throwable, getUpdateResponseListener(modelId, listener));
            });

        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Update model with build-in listener.
     *
     * TODO: Will remove this method later. Currently it's being used by multiple classes.
     *
     * @param modelId       model id
     * @param updatedFields updated fields
     */
    public void updateModel(String modelId, Boolean isHidden, Map<String, Object> updatedFields) {
        updateModel(modelId, updatedFields, ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug(getErrorMessage("Updated ML model successfully: {}", modelId, isHidden), response.status());
            } else {
                log.error(getErrorMessage("Failed to update provided ML model, status: {}", modelId, isHidden), response.status());
            }
        }, e -> { log.error(getErrorMessage("Failed to update the provided ML model", modelId, isHidden), e); }));
    }

    /**
     * Update model.
     *
     * * TODO: Will remove this method later. Currently it's being used by multiple classes.
     *
     * @param modelId       model id
     * @param updatedFields updated fields
     * @param listener      action listener
     */
    public void updateModel(String modelId, Map<String, Object> updatedFields, ActionListener<UpdateResponse> listener) {
        if (updatedFields == null || updatedFields.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
            return;
        }
        Map<String, Object> newUpdatedFields = new HashMap<>();
        newUpdatedFields.putAll(updatedFields);
        newUpdatedFields.put(MLModel.LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());

        UpdateDataObjectRequest.Builder requestBuilder = UpdateDataObjectRequest
            .builder()
            .index(ML_MODEL_INDEX)
            .id(modelId)
            .dataObject(newUpdatedFields);

        // Conditionally add retryOnConflict based on the provided condition
        if (updatedFields.containsKey(MLModel.MODEL_STATE_FIELD)
            && MODEL_DONE_STATES.contains(newUpdatedFields.get(MLModel.MODEL_STATE_FIELD))) {
            requestBuilder.retryOnConflict(3);
        }

        // Build the request
        UpdateDataObjectRequest updateDataObjectRequest = requestBuilder.build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {

            sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore(); // Restore the context once the operation is done
                handleUpdateDataObjectCompletionStage(r, throwable, getUpdateResponseListener(modelId, listener));
            });

        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void handleUpdateDataObjectCompletionStage(
        UpdateDataObjectResponse r,
        Throwable throwable,
        ActionListener<UpdateResponse> updateListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            updateListener.onFailure(cause);
        } else {
            try {
                updateListener.onResponse(r.updateResponse());
            } catch (Exception e) {
                updateListener.onFailure(e);
            }
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(String modelId, ActionListener<UpdateResponse> actionListener) {
        return ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.error("Failed to update the model with ID: {}", modelId);
                actionListener.onResponse(updateResponse);
                return;
            }
            log.info("Successfully updated the model with ID: {}", modelId);
            actionListener.onResponse(updateResponse);
        }, exception -> {
            log.error("Failed to update ML model with ID {}. Details: {}", modelId, exception);
            actionListener.onFailure(exception);
        });
    }

    /**
     * Get model chunk id.
     *
     * @param modelId     model id
     * @param chunkNumber model chunk number
     * @return model chunk id
     */
    public String getModelChunkId(String modelId, Integer chunkNumber) {
        return modelId + "_" + chunkNumber;
    }

    /**
     * Add model worker node to cache.
     *
     * @param modelId model id
     * @param nodeIds node ids
     */
    public void addModelWorkerNode(String modelId, String... nodeIds) {
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                modelCacheHelper.addWorkerNode(modelId, nodeId);
            }
        }
    }

    public void addModelWorkerNodes(List<String> nodeIds) {
        if (nodeIds != null) {
            String[] modelIds = getAllModelIds();
            for (String nodeId : nodeIds) {
                Arrays.stream(modelIds).forEach(x -> modelCacheHelper.addWorkerNode(x, nodeId));
            }
        }
    }

    /**
     * Remove model from worker node cache.
     *
     * @param modelId model id
     * @param nodeIds node ids
     */
    public void removeModelWorkerNode(String modelId, boolean isFromUndeploy, String... nodeIds) {
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                modelCacheHelper.removeWorkerNode(modelId, nodeId, isFromUndeploy);
            }
        }
    }

    /**
     * Remove a set of worker nodes from cache.
     *
     * @param removedNodes removed node ids
     */
    public void removeWorkerNodes(Set<String> removedNodes, boolean isFromUndeploy) {
        modelCacheHelper.removeWorkerNodes(removedNodes, isFromUndeploy);
    }

    /**
     * Undeploy model from memory.
     *
     * @param modelIds model ids
     * @return model undeploy status
     */
    public synchronized Map<String, String> undeployModel(String[] modelIds) {
        Map<String, String> modelUndeployStatus = new HashMap<>();
        if (modelIds != null && modelIds.length > 0) {
            log.debug("undeploy models {}", Arrays.toString(modelIds));
            for (String modelId : modelIds) {
                if (modelCacheHelper.isModelDeployed(modelId)) {
                    modelUndeployStatus.put(modelId, UNDEPLOYED);
                    mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).decrement();
                    mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
                    mlStats
                        .createCounterStatIfAbsent(getModelFunctionName(modelId), ActionName.UNDEPLOY, ML_ACTION_REQUEST_COUNT)
                        .increment();
                    mlStats.createModelCounterStatIfAbsent(modelId, ActionName.UNDEPLOY, ML_ACTION_REQUEST_COUNT).increment();
                } else {
                    modelUndeployStatus.put(modelId, NOT_FOUND);
                }
                removeModel(modelId);
            }
        } else {
            log.debug("undeploy all models {}", Arrays.toString(getLocalDeployedModels()));
            for (String modelId : getLocalDeployedModels()) {
                modelUndeployStatus.put(modelId, UNDEPLOYED);
                mlStats.getStat(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT).decrement();
                mlStats.createCounterStatIfAbsent(getModelFunctionName(modelId), ActionName.UNDEPLOY, ML_ACTION_REQUEST_COUNT).increment();
                mlStats.createModelCounterStatIfAbsent(modelId, ActionName.UNDEPLOY, ML_ACTION_REQUEST_COUNT).increment();
                removeModel(modelId);
            }
        }
        return modelUndeployStatus;
    }

    private void removeModel(String modelId) {
        modelCacheHelper.removeModel(modelId);
        modelHelper.deleteFileCache(modelId);
    }

    /**
     * Get worker nodes of specific model.
     *
     * @param modelId          model id
     * @param functionName     function name
     * @param onlyEligibleNode return only eligible node
     * @return list of worker node ids
     */
    public String[] getWorkerNodes(String modelId, FunctionName functionName, boolean onlyEligibleNode) {
        String[] workerNodeIds = modelCacheHelper.getWorkerNodes(modelId);
        if (!onlyEligibleNode) {
            return workerNodeIds;
        }
        if (workerNodeIds == null || workerNodeIds.length == 0) {
            return workerNodeIds;
        }

        String[] eligibleNodeIds = nodeHelper.filterEligibleNodes(functionName, workerNodeIds);
        if (eligibleNodeIds == null || eligibleNodeIds.length == 0) {
            throw new IllegalArgumentException("No eligible worker node found");
        }
        return eligibleNodeIds;
    }

    public int getWorkerNodesSize(String modelId, FunctionName functionName, boolean onlyEligibleNode) {
        return getWorkerNodes(modelId, functionName, onlyEligibleNode).length;
    }

    /**
     * Get worker node of specific model without filtering eligible node.
     *
     * @param modelId      model id
     * @param functionName function name
     * @return list of worker node ids
     */
    public String[] getWorkerNodes(String modelId, FunctionName functionName) {
        return getWorkerNodes(modelId, functionName, false);
    }

    public int getWorkerNodesSize(String modelId, FunctionName functionName) {
        return getWorkerNodes(modelId, functionName, false).length;
    }

    /**
     * Get target/planning worker node of a specific model.
     *
     * @param modelId      model id
     * @return list of planning worker node ids
     */
    public String[] getTargetWorkerNodes(String modelId) {
        return modelCacheHelper.getTargetWorkerNodes(modelId);
    }

    /**
     * Get predictable instance with model id.
     *
     * @param modelId model id
     * @return predictable instance
     */
    public Predictable getPredictor(String modelId) {
        return modelCacheHelper.getPredictor(modelId);
    }

    /**
     * Get all model ids in cache, both local model id and remote model in routing
     * table.
     *
     * @return array of model ids
     */
    public String[] getAllModelIds() {
        return modelCacheHelper.getAllModels();
    }

    /**
     * Get all local model ids.
     *
     * @return array of local deployed models
     */
    public String[] getLocalDeployedModels() {
        return modelCacheHelper.getDeployedModels();
    }

    public String[] getExpiredModels() {
        return modelCacheHelper.getExpiredModels();
    }

    /**
     * Sync model routing table.
     *
     * @param modelWorkerNodes model worker nodes
     */
    public synchronized void syncModelWorkerNodes(Map<String, Set<String>> modelWorkerNodes) {
        modelCacheHelper.syncWorkerNodes(modelWorkerNodes);

        syncModelPlanningWorkerNodes(modelWorkerNodes);
    }

    public synchronized void syncModelPlanningWorkerNodes(Map<String, Set<String>> modelWorkerNodes) {
        Map<String, Set<String>> modelPlanningWorkerNodes = new HashMap<>();
        modelWorkerNodes.keySet().forEach(modelId -> {
            FunctionName functionName = modelCacheHelper.getFunctionName(modelId);
            boolean isDeployToAll = modelCacheHelper.getDeployToAllNodes(modelId);
            if (!isDeployToAll) {
                return;
            }
            DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(functionName);
            Set<String> eligibleNodeIds = Arrays.stream(eligibleNodes).map(DiscoveryNode::getId).collect(Collectors.toSet());
            modelPlanningWorkerNodes.put(modelId, eligibleNodeIds);
        });
        modelCacheHelper.syncPlanningWorkerNodes(modelPlanningWorkerNodes);
    }

    /**
     * Clear all model worker nodes from cache.
     */
    public void clearRoutingTable() {
        modelCacheHelper.clearWorkerNodes();
    }

    public MLModelProfile getModelProfile(String modelId) {
        return modelCacheHelper.getModelProfile(modelId);
    }

    public <T> T trackPredictDuration(String modelId, Supplier<T> supplier) {
        long start = System.nanoTime();
        T t = supplier.get();
        long end = System.nanoTime();
        double durationInMs = (end - start) / 1e6;
        modelCacheHelper.addModelInferenceDuration(modelId, durationInMs);
        return t;
    }

    public void trackPredictDuration(String modelId, long startTime) {
        long end = System.nanoTime();
        double durationInMs = (end - startTime) / 1e6;
        modelCacheHelper.addModelInferenceDuration(modelId, durationInMs);
    }

    public FunctionName getModelFunctionName(String modelId) {
        return modelCacheHelper.getFunctionName(modelId);
    }

    public Optional<FunctionName> getOptionalModelFunctionName(String modelId) {
        return modelCacheHelper.getOptionalFunctionName(modelId);
    }

    public boolean isModelRunningOnNode(String modelId) {
        return modelCacheHelper.isModelRunningOnNode(modelId);
    }

    public boolean isModelDeployed(String modelId) {
        return modelCacheHelper.isModelDeployed(modelId);
    }

    public boolean isNodeEligible(String nodeId, FunctionName functionName) {
        Set<String> allEligibleNodeIds = Arrays
            .stream(nodeHelper.getEligibleNodes(functionName))
            .map(DiscoveryNode::getId)
            .collect(Collectors.toSet());
        return allEligibleNodeIds.contains(nodeId);
    }

    public MLModel addModelToAutoDeployCache(String modelId, MLModel model) {
        return modelCacheHelper.addModelToAutoDeployCache(modelId, model);
    }

    public void removeAutoDeployModel(String modelId) {
        modelCacheHelper.removeAutoDeployModel(modelId);
    }
}
