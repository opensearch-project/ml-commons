/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.NOT_FOUND;
import static org.opensearch.ml.common.CommonValue.UNLOADED;
import static org.opensearch.ml.engine.MLEngine.getLoadModelChunkPath;
import static org.opensearch.ml.engine.MLEngine.getLoadModelZipPath;
import static org.opensearch.ml.engine.MLEngine.getUploadModelPath;
import static org.opensearch.ml.engine.ModelHelper.CHUNK_FILES;
import static org.opensearch.ml.engine.ModelHelper.MODEL_FILE_HASH;
import static org.opensearch.ml.engine.ModelHelper.MODEL_SIZE_IN_BYTES;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.MODEL_HELPER;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.MODEL_ZIP_FILE;
import static org.opensearch.ml.engine.utils.FileUtils.calculateFileHash;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.utils.FileUtils;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

/**
 * Manager class for ML models. It contains ML model related operations like upload, load model etc.
 */
@Log4j2
public class MLModelManager {

    public static final int TIMEOUT_IN_MILLIS = 5000;

    private final Client client;
    private ThreadPool threadPool;
    private NamedXContentRegistry xContentRegistry;
    private ModelHelper modelHelper;

    private final MLModelCache modelCache;
    private final MLStats mlStats;
    private final MLCircuitBreakerService mlCircuitBreakerService;
    private final MLIndicesHandler mlIndicesHandler;
    private final MLTaskManager mlTaskManager;

    private volatile Integer maxModelPerNode;
    private volatile Integer maxUploadTasksPerNode;

    public MLModelManager(
        ClusterService clusterService,
        Client client,
        ThreadPool threadPool,
        NamedXContentRegistry xContentRegistry,
        ModelHelper modelHelper,
        Settings settings,
        MLStats mlStats,
        MLCircuitBreakerService mlCircuitBreakerService,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager
    ) {
        this.client = client;
        this.threadPool = threadPool;
        this.xContentRegistry = xContentRegistry;
        this.modelHelper = modelHelper;
        this.modelCache = new MLModelCache(clusterService, settings);
        this.mlStats = mlStats;
        this.mlCircuitBreakerService = mlCircuitBreakerService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;

        this.maxModelPerNode = ML_COMMONS_MAX_MODELS_PER_NODE.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MAX_MODELS_PER_NODE, it -> maxModelPerNode = it);

        maxUploadTasksPerNode = ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE, it -> maxUploadTasksPerNode = it);
    }

    /**
     * Upload model. Basically download model file, split into chunks and save into model index.
     *
     * @param uploadInput upload input
     * @param mlTask      ML task
     */
    public void uploadMLModel(MLUploadInput uploadInput, MLTask mlTask) {
        mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT).increment();
        String errorMsg = checkAndAddRunningTask(mlTask, maxUploadTasksPerNode);
        if (errorMsg != null) {
            mlTaskManager
                .updateMLTaskDirectly(
                    mlTask.getTaskId(),
                    ImmutableMap.of(MLTask.STATE_FIELD, MLTaskState.FAILED, MLTask.ERROR_FIELD, errorMsg)
                );
            throw new MLLimitExceededException(errorMsg);
        }
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        mlStats
            .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.UPLOAD, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
            .increment();
        try {
            if (uploadInput.getUrl() != null) {
                uploadModel(uploadInput, mlTask);
            } else {
                throw new IllegalArgumentException("wrong model file url");
            }
        } catch (Exception e) {
            mlStats
                .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.UPLOAD, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                .increment();
            throw new MLException("Failed to upload model", e);
        } finally {
            mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        }
    }

    public String checkAndAddRunningTask(MLTask mlTask, Integer limit) {
        String error = mlTaskManager.checkLimitAndAddRunningTask(mlTask, limit);
        if (error != null) {
            return error;
        }
        if (mlCircuitBreakerService.isOpen()) {
            mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT).increment();
            return "Circuit breaker is open, please check your memory and disk usage!";
        }
        return null;
    }

    private void uploadModel(MLUploadInput mlUploadInput, MLTask mlTask) {
        Semaphore semaphore = new Semaphore(1);
        String taskId = mlTask.getTaskId();

        AtomicInteger uploaded = new AtomicInteger(0);
        threadPool.executor(TASK_THREAD_POOL).execute(() -> {
            String modelName = mlUploadInput.getModelName();
            String version = mlUploadInput.getVersion();

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                    MLModel mlModelMeta = MLModel
                        .builder()
                        .name(modelName)
                        .algorithm(mlTask.getFunctionName())
                        .version(version)
                        .modelFormat(mlUploadInput.getModelFormat())
                        .modelState(MLModelState.UPLOADING)
                        .modelConfig(mlUploadInput.getModelConfig())
                        .createdTime(Instant.now())
                        .build();
                    IndexRequest indexModelMetaRequest = new IndexRequest(ML_MODEL_INDEX);
                    indexModelMetaRequest
                        .source(mlModelMeta.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    indexModelMetaRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    client.index(indexModelMetaRequest, ActionListener.wrap(modelMetaRes -> {
                        String modelId = modelMetaRes.getId();
                        mlTask.setModelId(modelId);
                        log.info("create new model meta doc {} for upload task {}", modelId, taskId);
                        modelHelper.downloadAndSplit(modelId, modelName, version, mlUploadInput.getUrl(), ActionListener.wrap(result -> {
                            Long modelSizeInBytes = (Long) result.get(MODEL_SIZE_IN_BYTES);
                            List<String> chunkFiles = (List<String>) result.get(CHUNK_FILES);
                            String hashValue = (String) result.get(MODEL_FILE_HASH);
                            for (String name : chunkFiles) {
                                semaphore.tryAcquire(10, TimeUnit.SECONDS);
                                File file = new File(name);
                                byte[] bytes = Files.toByteArray(file);
                                int chunkNum = Integer.parseInt(file.getName());
                                MLModel mlModel = MLModel
                                    .builder()
                                    .modelId(modelId)
                                    .name(modelName)
                                    .algorithm(mlTask.getFunctionName())
                                    .version(version)
                                    .modelFormat(mlUploadInput.getModelFormat())
                                    .chunkNumber(chunkNum)
                                    .totalChunks(chunkFiles.size())
                                    .content(Base64.getEncoder().encodeToString(bytes))
                                    .createdTime(Instant.now())
                                    .build();
                                IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
                                indexRequest.id(getModelChunkId(modelId, chunkNum));
                                indexRequest
                                    .source(
                                        mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS)
                                    );
                                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                                client.index(indexRequest, ActionListener.wrap(r -> {
                                    uploaded.getAndIncrement();
                                    if (uploaded.get() == chunkFiles.size()) {
                                        deleteFileQuietly(getUploadModelPath(modelId));
                                        updateModel(
                                            modelId,
                                            ImmutableMap
                                                .of(
                                                    MLModel.MODEL_STATE_FIELD,
                                                    MLModelState.UPLOADED,
                                                    MLModel.LAST_UPLOADED_TIME_FIELD,
                                                    Instant.now().toEpochMilli(),
                                                    MLModel.TOTAL_CHUNKS_FIELD,
                                                    chunkFiles.size(),
                                                    MLModel.MODEL_CONTENT_HASH_VALUE_FIELD,
                                                    hashValue,
                                                    MLModel.MODEL_CONTENT_SIZE_IN_BYTES_FIELD,
                                                    modelSizeInBytes
                                                ),
                                            ActionListener.wrap(updateResponse -> {
                                                mlTaskManager
                                                    .updateMLTask(
                                                        taskId,
                                                        ImmutableMap
                                                            .of(MLTask.STATE_FIELD, MLTaskState.COMPLETED, MLTask.MODEL_ID_FIELD, modelId),
                                                        TIMEOUT_IN_MILLIS
                                                    );
                                                mlTaskManager.remove(taskId);
                                                if (mlUploadInput.isLoadModel()) {
                                                    String[] modelNodeIds = mlUploadInput.getModelNodeIds();
                                                    log
                                                        .debug(
                                                            "uploading model done, start loading model {} on nodes: {}",
                                                            modelId,
                                                            Arrays.toString(modelNodeIds)
                                                        );
                                                    MLLoadModelRequest mlLoadModelRequest = new MLLoadModelRequest(
                                                        modelId,
                                                        modelNodeIds,
                                                        false,
                                                        true
                                                    );
                                                    client
                                                        .execute(
                                                            MLLoadModelAction.INSTANCE,
                                                            mlLoadModelRequest,
                                                            ActionListener
                                                                .wrap(
                                                                    response -> { log.info(response); },
                                                                    exc -> { exc.printStackTrace(); }
                                                                )
                                                        );
                                                }
                                            }, e -> {
                                                log.error("Failed to index model chunk", e);
                                                handleException(taskId, e);
                                                deleteModel(modelId);
                                            })
                                        );
                                    } else {
                                        file.delete();
                                    }
                                    semaphore.release();
                                }, e -> {
                                    log.error("Failed to index model chunk", e);
                                    handleException(taskId, e);
                                    file.delete();
                                    // remove model doc as failed to upload model
                                    deleteModel(modelId);
                                    semaphore.release();
                                    deleteFileQuietly(getUploadModelPath(modelId));
                                }));
                            }
                        }, e -> {
                            log.error("Failed to index chunk file", e);
                            deleteFileQuietly(getUploadModelPath(modelId));
                            deleteModel(modelId);
                            handleException(taskId, e);
                        }));
                    }, e -> {
                        log.error("Failed to index model meta doc", e);
                        handleException(taskId, e);
                    }));
                }, e -> {
                    log.error("Failed to init model index", e);
                    handleException(taskId, e);
                }));
            } catch (Exception e) {
                log.error("Failed to upload model", e);
                handleException(taskId, e);
            }
        });
    }

    private void deleteModel(String modelId) {
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index(ML_MODEL_INDEX).id(modelId).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.delete(deleteRequest);
        DeleteByQueryRequest deleteChunksRequest = new DeleteByQueryRequest(ML_MODEL_INDEX)
            .setQuery(new TermQueryBuilder(MLModel.MODEL_ID_FIELD, modelId))
            .setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN)
            .setAbortOnVersionConflict(false);
        client.execute(DeleteByQueryAction.INSTANCE, deleteChunksRequest);
    }

    private void handleException(String taskId, Exception e) {
        mlTaskManager
            .updateMLTask(
                taskId,
                ImmutableMap.of(MLTask.ERROR_FIELD, ExceptionUtils.getStackTrace(e), MLTask.STATE_FIELD, MLTaskState.FAILED),
                ActionListener
                    .runAfter(
                        ActionListener
                            .wrap(
                                r -> { log.debug("updated task successfully {}", taskId); },
                                ex -> { log.error("failed to update ML task " + taskId, ex); }
                            ),
                        () -> mlTaskManager.remove(taskId)
                    ),
                TIMEOUT_IN_MILLIS
            );
    }

    /**
     * Read model chunks from model index. Concat chunks into a whole model file, then load
     * into memory.
     *
     * @param modelId          model id
     * @param modelContentHash model content hash value
     * @param functionName     function name
     * @param listener         action listener
     */
    public void loadModel(String modelId, String modelContentHash, FunctionName functionName, ActionListener<String> listener) {
        mlStats.createCounterStatIfAbsent(functionName, ActionName.LOAD, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        if (modelCache.isModelLoaded(modelId)) {
            listener.onResponse("successful");
            return;
        }
        if (modelCache.modelCount() >= maxModelPerNode) {
            listener.onFailure(new IllegalArgumentException("Exceed max model per node limit"));
            return;
        }
        modelCache.initModelState(modelId, MLModelState.LOADING, functionName);
        try {
            threadPool.executor(TASK_THREAD_POOL).execute(() -> {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    this.getModel(modelId, ActionListener.wrap(mlModel -> {
                        if (mlModel.getAlgorithm() != FunctionName.TEXT_EMBEDDING) {// load model trained by built-in algorithm like kmeans
                            Predictable predictable = MLEngine.load(mlModel, null);
                            modelCache.addPredictable(modelId, predictable);
                            mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT).increment();
                            modelCache.setModelState(modelId, MLModelState.LOADED);
                            listener.onResponse("successful");
                            return;
                        }
                        // check circuit breaker before loading custom model chunks
                        if (mlCircuitBreakerService.isOpen()) {
                            mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT).increment();
                            throw new MLLimitExceededException("Circuit breaker is open, please check your memory and disk usage!");
                        }
                        retrieveModelChunks(mlModel, ActionListener.wrap(modelZipFile -> {// load model trunks
                            String hash = calculateFileHash(modelZipFile);
                            if (modelContentHash != null && !modelContentHash.equals(hash)) {
                                log.error("Model content hash can't match original hash value");
                                modelCache.removeModelState(modelId);
                                modelHelper.deleteFileCache(modelId);
                                listener.onFailure(new IllegalArgumentException("model content changed"));
                                return;
                            }
                            log.error("Model content matches original hash value, continue loading");
                            Predictable predictable = MLEngine
                                .load(mlModel, ImmutableMap.of(MODEL_ZIP_FILE, modelZipFile, MODEL_HELPER, modelHelper));
                            modelCache.addPredictable(modelId, predictable);
                            mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT).increment();
                            modelCache.setModelState(modelId, MLModelState.LOADED);
                            listener.onResponse("successful");
                        }, e -> {
                            mlStats
                                .createCounterStatIfAbsent(functionName, ActionName.LOAD, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                                .increment();
                            log.error("Failed to retrieve model " + modelId, e);
                            modelCache.removeModelState(modelId);
                            listener.onFailure(e);
                        }));
                    }, e -> {
                        mlStats
                            .createCounterStatIfAbsent(functionName, ActionName.LOAD, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                            .increment();
                        modelCache.removeModelState(modelId);
                        listener.onFailure(new MLResourceNotFoundException("ML model not found"));
                    }));
                } catch (Exception e) {
                    mlStats.createCounterStatIfAbsent(functionName, ActionName.LOAD, MLActionLevelStat.ML_ACTION_FAILURE_COUNT).increment();
                    modelCache.removeModelState(modelId);
                    listener.onFailure(e);
                }
            });
        } catch (Exception e) {
            mlStats.createCounterStatIfAbsent(functionName, ActionName.LOAD, MLActionLevelStat.ML_ACTION_FAILURE_COUNT).increment();
            modelCache.removeModelState(modelId);
            listener.onFailure(e);
        }
    }

    /**
     * Get model from model index.
     *
     * @param modelId  model id
     * @param listener action listener
     */
    public void getModel(String modelId, ActionListener<MLModel> listener) {
        getModel(modelId, null, null, listener);
    }

    /**
     * Get model from model index with includes/exludes filter.
     *
     * @param modelId  model id
     * @param includes fields included
     * @param excludes fields excluded
     * @param listener action listener
     */
    public void getModel(String modelId, String[] includes, String[] excludes, ActionListener<MLModel> listener) {
        GetRequest getRequest = new GetRequest();
        FetchSourceContext featchContext = new FetchSourceContext(true, includes, excludes);
        getRequest.index(ML_MODEL_INDEX).id(modelId).fetchSourceContext(featchContext);
        client.get(getRequest, ActionListener.wrap(r -> {
            if (r != null && r.isExists()) {
                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLModel mlModel = MLModel.parse(parser);
                    mlModel.setModelId(modelId);
                    listener.onResponse(mlModel);
                } catch (Exception e) {
                    log.error("Failed to parse ml task" + r.getId(), e);
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new MLResourceNotFoundException("Fail to find model"));
            }
        }, e -> { listener.onFailure(e); }));
    }

    private void retrieveModelChunks(MLModel mlModelMeta, ActionListener<File> listener) throws InterruptedException {
        String modelId = mlModelMeta.getModelId();
        String modelName = mlModelMeta.getName();
        Integer totalChunks = mlModelMeta.getTotalChunks();
        GetRequest getRequest = new GetRequest();
        getRequest.index(ML_MODEL_INDEX);
        getRequest.id();
        Semaphore semaphore = new Semaphore(1);
        AtomicBoolean stopNow = new AtomicBoolean(false);
        String modelZip = getLoadModelZipPath(modelId, modelName);
        File[] chunkFiles = new File[totalChunks];
        AtomicInteger retrievedChunks = new AtomicInteger(0);
        for (int i = 0; i < totalChunks; i++) {
            if (stopNow.get()) {
                listener.onFailure(new MLException("Failed to load model"));
                return;
            }
            semaphore.tryAcquire(10, TimeUnit.SECONDS);

            String modelChunkId = this.getModelChunkId(modelId, i);
            int currentChunk = i;
            this.getModel(modelChunkId, ActionListener.wrap(model -> {
                Path chunkPath = getLoadModelChunkPath(modelId, currentChunk);
                FileUtils.write(Base64.getDecoder().decode(model.getContent()), chunkPath.toString());
                chunkFiles[currentChunk] = new File(chunkPath.toUri());
                semaphore.release();
                retrievedChunks.getAndIncrement();
                if (retrievedChunks.get() == totalChunks) {
                    File modelZipFile = new File(modelZip);
                    FileUtils.mergeFiles(chunkFiles, modelZipFile);
                    listener.onResponse(modelZipFile);
                }
            }, e -> {
                stopNow.set(true);
                semaphore.release();
                listener.onFailure(new MLResourceNotFoundException("Fail to find model chunk " + modelChunkId));
                return;
            }));
        }
    }

    /**
     * Update model with build-in listener.
     *
     * @param modelId       model id
     * @param updatedFields updated fields
     */
    public void updateModel(String modelId, ImmutableMap<String, Object> updatedFields) {
        updateModel(modelId, updatedFields, ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug("Updated ML model successfully: {}, model id: {}", response.status(), modelId);
            } else {
                log.error("Failed to update ML model {}, status: {}", modelId, response.status());
            }
        }, e -> { log.error("Failed to update ML model: " + modelId, e); }));
    }

    /**
     * Update model.
     *
     * @param modelId       model id
     * @param updatedFields updated fields
     * @param listener      action listener
     */
    public void updateModel(String modelId, ImmutableMap<String, Object> updatedFields, ActionListener<UpdateResponse> listener) {
        try {
            if (updatedFields == null || updatedFields.size() == 0) {
                listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                return;
            }
            UpdateRequest updateRequest = new UpdateRequest(ML_MODEL_INDEX, modelId);
            updateRequest.doc(updatedFields);
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.runBefore(listener, () -> context.restore()));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } catch (Exception e) {
            log.error("Failed to update ML model " + modelId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Get model chunk id
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
                modelCache.addNodeToModelRoutingTable(modelId, nodeId);
            }
        }
    }

    /**
     * Remove model from worker node cache.
     *
     * @param modelId model id
     * @param nodeIds node ids
     */
    public void removeModelWorkerNode(String modelId, String... nodeIds) {
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                modelCache.removeNodeFromModelRoutingTable(modelId, nodeId);
            }
        }
    }

    /**
     * Remove a set of worker nodes from cache.
     *
     * @param removedNodes removed node ids
     */
    public void removeWorkerNodes(Set<String> removedNodes) {
        modelCache.removeWorkNodes(removedNodes);
    }

    /**
     * Unload model from memory.
     *
     * @param modelIds model ids
     * @return model unload status
     */
    public synchronized Map<String, String> unloadModel(String[] modelIds) {
        Map<String, String> modelUnloadStatus = new HashMap<>();
        if (modelIds != null && modelIds.length > 0) {
            log.debug("unload models {}", Arrays.toString(modelIds));
            for (String modelId : modelIds) {
                if (modelCache.hasModel(modelId)) {
                    modelUnloadStatus.put(modelId, UNLOADED);
                    mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT).decrement();
                } else {
                    modelUnloadStatus.put(modelId, NOT_FOUND);
                }
                mlStats
                    .createCounterStatIfAbsent(getModelFunctionName(modelId), ActionName.UNLOAD, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
                    .increment();
                modelCache.removeModel(modelId);
            }
        } else {
            log.debug("unload all models {}", Arrays.toString(getLocalLoadedModels()));
            for (String modelId : getLocalLoadedModels()) {
                modelUnloadStatus.put(modelId, UNLOADED);
                mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT).decrement();
                mlStats
                    .createCounterStatIfAbsent(getModelFunctionName(modelId), ActionName.UNLOAD, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
                    .increment();
                modelCache.removeModel(modelId);
            }
        }
        return modelUnloadStatus;
    }

    /**
     * Get worker nodes of specif model.
     *
     * @param modelId model id
     * @return list of worker node ids
     */
    public String[] getWorkerNodes(String modelId) {
        return modelCache.getWorkerNodes(modelId);
    }

    /**
     * Add predictable instance to cache.
     *
     * @param modelId     model id
     * @param predictable predictable instance
     */
    public void addPredictable(String modelId, Predictable predictable) {
        modelCache.addPredictable(modelId, predictable);
    }

    /**
     * Get predictable instance with model id.
     *
     * @param modelId
     * @return
     */
    public Predictable getPredictable(String modelId) {
        return modelCache.getPredictable(modelId);
    }

    /**
     * Get all model ids in cache, both local model id and remote model in routing table.
     *
     * @return
     */
    public String[] getAllModelIds() {
        return modelCache.getAllModelIds();
    }

    /**
     * Get all local model ids.
     *
     * @return
     */
    public String[] getLocalLoadedModels() {
        return modelCache.getLoadedModels();
    }

    /**
     * Sync model routing table.
     *
     * @param modelRoutingTable
     */
    public synchronized void syncModelRouting(Map<String, Set<String>> modelRoutingTable) {
        modelCache.syncModelRouting(modelRoutingTable);
    }

    /**
     *
     */
    public void clearRoutingTable() {
        modelCache.clearRoutingTable();
    }

    public MLModelProfile getModelProfile(String modelId) {
        return modelCache.getModelProfile(modelId);
    }

    public <T> T trackPredictDuration(String modelId, Supplier<T> supplier) {
        long start = System.nanoTime();
        T t = supplier.get();
        long end = System.nanoTime();
        double durationInMs = (end - start) / 1e6;
        modelCache.addInferenceDuration(modelId, durationInMs);
        return t;
    }

    public FunctionName getModelFunctionName(String modelId) {
        return modelCache.getModelFunctionName(modelId);
    }

    public void initModelState(String modelId, MLModelState state, FunctionName functionName) {
        modelCache.initModelState(modelId, state, functionName);
    }

    public boolean containsModel(String modelId) {
        return modelCache.containsModel(modelId);
    }
}
