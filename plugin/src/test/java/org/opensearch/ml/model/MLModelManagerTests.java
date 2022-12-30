/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.ModelHelper.CHUNK_FILES;
import static org.opensearch.ml.engine.ModelHelper.MODEL_FILE_HASH;
import static org.opensearch.ml.engine.ModelHelper.MODEL_SIZE_IN_BYTES;
import static org.opensearch.ml.plugin.MachineLearningPlugin.LOAD_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.UPLOAD_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.utils.MockHelper.mock_MLIndicesHandler_initModelIndex;
import static org.opensearch.ml.utils.MockHelper.mock_MLIndicesHandler_initModelIndex_failure;
import static org.opensearch.ml.utils.MockHelper.mock_client_ThreadContext;
import static org.opensearch.ml.utils.MockHelper.mock_client_ThreadContext_Exception;
import static org.opensearch.ml.utils.MockHelper.mock_client_get_NotExist;
import static org.opensearch.ml.utils.MockHelper.mock_client_get_NullResponse;
import static org.opensearch.ml.utils.MockHelper.mock_client_get_failure;
import static org.opensearch.ml.utils.MockHelper.mock_client_index;
import static org.opensearch.ml.utils.MockHelper.mock_client_index_failure;
import static org.opensearch.ml.utils.MockHelper.mock_client_update;
import static org.opensearch.ml.utils.MockHelper.mock_client_update_failure;
import static org.opensearch.ml.utils.MockHelper.mock_threadpool;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;
import static org.opensearch.ml.utils.TestHelper.copyFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MLModelManagerTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ClusterService clusterService;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private ModelHelper modelHelper;
    private Settings settings;
    private MLStats mlStats;
    @Mock
    private MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private MLTaskManager mlTaskManager;

    private MLModelManager modelManager;

    private String modelName;
    private String version;
    private MLUploadInput uploadInput;
    private MLTask mlTask;
    @Mock
    private ExecutorService taskExecutorService;
    private ThreadContext threadContext;
    private String modelId;
    private String modelContentHashValue;
    private String url;
    private String chunk0;
    private String chunk1;
    private MLModel model;
    private MLModel modelChunk0;
    private MLModel modelChunk1;
    private Long modelContentSize;
    @Mock
    private MLModelCacheHelper modelCacheHelper;
    private MLEngine mlEngine;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)));
        settings = Settings.builder().put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE.getKey(), 10).build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MAX_MODELS_PER_NODE,
            ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE,
            ML_COMMONS_MONITORING_REQUEST_COUNT,
            ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE
        );
        clusterService = spy(new ClusterService(settings, clusterSettings, null));
        xContentRegistry = NamedXContentRegistry.EMPTY;

        modelName = "model_name1";
        modelId = randomAlphaOfLength(10);
        modelContentHashValue = "c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8";
        version = "1.0.0";
        url = "http://testurl";
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(384)
            .build();
        uploadInput = MLUploadInput
            .builder()
            .modelName(modelName)
            .version(version)
            .functionName(FunctionName.TEXT_EMBEDDING)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(modelConfig)
            .url(url)
            .build();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        this.mlStats = spy(new MLStats(stats));

        mlTask = MLTask
            .builder()
            .taskId("taskId1")
            .modelId("modelId1")
            .taskType(MLTaskType.UPLOAD_MODEL)
            .functionName(FunctionName.TEXT_EMBEDDING)
            .state(MLTaskState.CREATED)
            .inputType(MLInputDataType.TEXT_DOCS)
            .build();

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutorService).execute(any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        modelManager = spy(
            new MLModelManager(
                clusterService,
                client,
                threadPool,
                xContentRegistry,
                modelHelper,
                settings,
                mlStats,
                mlCircuitBreakerService,
                mlIndicesHandler,
                mlTaskManager,
                modelCacheHelper,
                mlEngine
            )
        );

        chunk0 = getClass().getResource("chunk/0").toURI().getPath();
        chunk1 = getClass().getResource("chunk/1").toURI().getPath();

        modelContentSize = 1000L;
        model = MLModel
            .builder()
            .modelId(modelId)
            .modelState(MLModelState.UPLOADED)
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .name(modelName)
            .version(version)
            .totalChunks(2)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(modelConfig)
            .modelContentHash(modelContentHashValue)
            .modelContentSizeInBytes(modelContentSize)
            .build();
        modelChunk0 = model.toBuilder().content(Base64.getEncoder().encodeToString("test chunk1".getBytes(StandardCharsets.UTF_8))).build();
        modelChunk1 = model.toBuilder().content(Base64.getEncoder().encodeToString("test chunk2".getBytes(StandardCharsets.UTF_8))).build();
    }

    public void testUploadMLModel_ExceedMaxRunningTask() {
        String error = "exceed max running task limit";
        doThrow(new MLLimitExceededException(error)).when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        expectedEx.expect(MLException.class);
        expectedEx.expectMessage(error);
        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
    }

    public void testUploadMLModel_CircuitBreakerOpen() {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn("Disk Circuit Breaker");
        expectedEx.expect(MLException.class);
        expectedEx.expectMessage("Disk Circuit Breaker is open, please check your resources!");
        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
    }

    public void testUploadMLModel_InitModelIndexFailure() {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_MLIndicesHandler_initModelIndex_failure(mlIndicesHandler);

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
        verify(modelHelper, never()).downloadAndSplit(any(), any(), any(), any(), any());
        verify(client, never()).index(any(), any());
    }

    public void testUploadMLModel_IndexModelMetaFailure() {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_client_ThreadContext(client, threadPool, threadContext);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index_failure(client);

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client).index(any(), any());
        verify(modelHelper, never()).downloadAndSplit(any(), any(), any(), any(), any());
    }

    public void testUploadMLModel_IndexModelChunkFailure() throws IOException {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_client_ThreadContext(client, threadPool, threadContext);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index_ModelChunkFailure(client, modelId);
        setUpMock_DownloadModelFile(createTempChunkFiles(), 1000L);

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client, times(2)).index(any(), any());
        verify(modelHelper).downloadAndSplit(any(), any(), any(), any(), any());
    }

    public void testUploadMLModel_DownloadModelFileFailure() {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index(client, modelId);
        setUpMock_DownloadModelFileFailure();

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client).index(any(), any());
        verify(modelHelper).downloadAndSplit(eq(modelId), eq(modelName), eq(version), eq(url), any());
    }

    public void testUploadMLModel_DownloadModelFile() throws IOException {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index(client, modelId);
        String[] newChunks = createTempChunkFiles();
        setUpMock_DownloadModelFile(newChunks, 1000L);

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client, times(3)).index(any(), any());
        verify(modelHelper).downloadAndSplit(eq(modelId), eq(modelName), eq(version), eq(url), any());
    }

    public void testUploadMLModel_LoadModel() throws IOException {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index(client, modelId);
        String[] newChunks = createTempChunkFiles();
        setUpMock_DownloadModelFile(newChunks, 1000L);
        mock_client_update(client);

        MLUploadInput mlUploadInput = uploadInput.toBuilder().loadModel(true).build();
        modelManager.uploadMLModel(mlUploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client, times(3)).index(any(), any());
        verify(modelHelper).downloadAndSplit(eq(modelId), eq(modelName), eq(version), eq(url), any());
        verify(client).execute(eq(MLLoadModelAction.INSTANCE), any(), any());
    }

    public void testUploadMLModel_LoadModel_failure() throws IOException {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index(client, modelId);
        String[] newChunks = createTempChunkFiles();
        setUpMock_DownloadModelFile(newChunks, 1000L);
        mock_client_update_failure(client);

        MLUploadInput mlUploadInput = uploadInput.toBuilder().loadModel(true).build();
        modelManager.uploadMLModel(mlUploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client, times(3)).index(any(), any());
        verify(modelHelper).downloadAndSplit(eq(modelId), eq(modelName), eq(version), eq(url), any());
        verify(client, never()).execute(eq(MLLoadModelAction.INSTANCE), any(), any());
    }

    public void testUploadMLModel_DownloadModelFile_ModelFileSizeExceedLimit() throws IOException {
        doNothing().when(mlTaskManager).checkLimitAndAddRunningTask(any(), any());
        when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
        mock_client_index(client, modelId);
        String[] newChunks = createTempChunkFiles();
        setUpMock_DownloadModelFile(newChunks, 10 * 1024 * 1024 * 1024L);

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client, times(1)).index(any(), any());
        verify(modelHelper).downloadAndSplit(eq(modelId), eq(modelName), eq(version), eq(url), any());
    }

    public void testUploadModel_ClientFailedToGetThreadPool() {
        mock_client_ThreadContext_Exception(client, threadPool, threadContext);
        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlIndicesHandler, never()).initModelIndexIfAbsent(any());
    }

    public void testLoadModel_FailedToGetModel() {
        ActionListener<String> listener = mock(ActionListener.class);
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        mock_threadpool(threadPool, taskExecutorService);
        mock_client_get_failure(client);
        mock_client_ThreadContext(client, threadPool, threadContext);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        assertFalse(modelManager.isModelRunningOnNode(modelId));
        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exception.capture());
        assertEquals("get doc failure", exception.getValue().getMessage());
        verify(mlStats)
            .createCounterStatIfAbsent(eq(FunctionName.TEXT_EMBEDDING), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    public void testLoadModel_NullGetModelResponse() {
        ActionListener<String> listener = mock(ActionListener.class);
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        mock_threadpool(threadPool, taskExecutorService);
        mock_client_get_NullResponse(client);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        assertFalse(modelManager.isModelRunningOnNode(modelId));
        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exception.capture());
        assertEquals("Fail to find model", exception.getValue().getMessage());
        verify(mlStats)
            .createCounterStatIfAbsent(eq(FunctionName.TEXT_EMBEDDING), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    public void testLoadModel_GetModelResponse_NotExist() {
        ActionListener<String> listener = mock(ActionListener.class);
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        mock_threadpool(threadPool, taskExecutorService);
        mock_client_get_NotExist(client);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        assertFalse(modelManager.isModelRunningOnNode(modelId));
        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exception.capture());
        assertEquals("Fail to find model", exception.getValue().getMessage());
        verify(mlStats)
            .createCounterStatIfAbsent(eq(FunctionName.TEXT_EMBEDDING), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    public void testLoadModel_GetModelResponse_wrong_hash_value() {
        ActionListener<String> listener = mock(ActionListener.class);
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        mock_client_ThreadContext(client, threadPool, threadContext);
        mock_threadpool(threadPool, taskExecutorService);
        setUpMock_GetModel(model);
        setUpMock_GetModel(modelChunk0);
        setUpMock_GetModel(modelChunk0);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        assertFalse(modelManager.isModelRunningOnNode(modelId));
        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exception.capture());
        assertEquals("model content changed", exception.getValue().getMessage());
        verify(mlStats)
            .createCounterStatIfAbsent(eq(FunctionName.TEXT_EMBEDDING), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));
        verify(mlStats, never())
            .createCounterStatIfAbsent(eq(FunctionName.TEXT_EMBEDDING), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    public void testLoadModel_GetModelResponse_FailedToLoad() {
        ActionListener<String> listener = mock(ActionListener.class);
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        mock_client_ThreadContext(client, threadPool, threadContext);
        mock_threadpool(threadPool, taskExecutorService);
        setUpMock_GetModelChunks(model);
        // setUpMock_GetModel(modelChunk0);
        // setUpMock_GetModel(modelChunk1);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        assertFalse(modelManager.isModelRunningOnNode(modelId));
        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exception.capture());
        assertEquals("Failed to load model " + modelId, exception.getValue().getMessage());
        verify(mlStats)
            .createCounterStatIfAbsent(eq(FunctionName.TEXT_EMBEDDING), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    public void testLoadModel_ModelAlreadyLoaded() {
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(true);
        ActionListener<String> listener = mock(ActionListener.class);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(response.capture());
        assertEquals("successful", response.getValue());
    }

    public void testLoadModel_ExceedMaxLoadedModel() {
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        String[] models = new String[100];
        for (int i = 0; i < 100; i++) {
            models[i] = "model" + i;
        }
        when(modelCacheHelper.getLoadedModels()).thenReturn(models);
        ActionListener<String> listener = mock(ActionListener.class);
        modelManager.loadModel(modelId, modelContentHashValue, FunctionName.TEXT_EMBEDDING, mlTask, listener);
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(failure.capture());
        assertEquals("Exceed max model per node limit", failure.getValue().getMessage());
    }

    public void testLoadModel_ThreadPoolException() {
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        mock_client_ThreadContext_Exception(client, threadPool, threadContext);
        ActionListener<String> listener = mock(ActionListener.class);
        FunctionName functionName = FunctionName.TEXT_EMBEDDING;

        modelManager.loadModel(modelId, modelContentHashValue, functionName, mlTask, listener);
        verify(modelCacheHelper).removeModel(eq(modelId));
        verify(mlStats).createCounterStatIfAbsent(eq(functionName), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    public void testLoadModel_FailedToRetrieveFirstModelChunks() {
        testLoadModel_FailedToRetrieveModelChunks(false);
    }

    public void testLoadModel_FailedToRetrieveLastModelChunks() {
        testLoadModel_FailedToRetrieveModelChunks(true);
    }

    public void testUnloadModel_NullModelIds_NoLoadedModel() {
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        Map<String, String> unloadModelStatus = modelManager.unloadModel(null);
        assertEquals(0, unloadModelStatus.size());
    }

    public void testUnloadModel_EmptyModelIds_LoadedModel() {
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] { modelId });
        when(modelCacheHelper.getFunctionName(modelId)).thenReturn(FunctionName.TEXT_EMBEDDING);
        Map<String, String> unloadModelStatus = modelManager.unloadModel(new String[] {});
        assertEquals(1, unloadModelStatus.size());
        assertTrue(unloadModelStatus.containsKey(modelId));
        assertEquals("unloaded", unloadModelStatus.get(modelId));
    }

    public void testUpdateModel_NullUpdatedFields() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        modelManager.updateModel(modelId, null, listener);
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(failure.capture());
        assertEquals("Updated fields is null or empty", failure.getValue().getMessage());
    }

    public void testUpdateModel_EmptyUpdatedFields() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        modelManager.updateModel(modelId, ImmutableMap.of(), listener);
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(failure.capture());
        assertEquals("Updated fields is null or empty", failure.getValue().getMessage());
    }

    public void testUpdateModel_ThreadPoolException() {
        mock_client_ThreadContext_Exception(client, threadPool, threadContext);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        modelManager.updateModel(modelId, ImmutableMap.of(MLModel.MODEL_STATE_FIELD, MLModelState.LOADED), listener);
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(failure.capture());
        assertEquals("failed to stashContext", failure.getValue().getMessage());
    }

    public void testSyncModelWorkerNodes() {
        Map<String, Set<String>> modelWorkerNodes = ImmutableMap.of(modelId, ImmutableSet.of("node1"));
        modelManager.syncModelWorkerNodes(modelWorkerNodes);
        verify(modelCacheHelper).syncWorkerNodes(eq(modelWorkerNodes));
    }

    public void testClearRoutingTable() {
        modelManager.clearRoutingTable();
        verify(modelCacheHelper).clearWorkerNodes();
    }

    private void testLoadModel_FailedToRetrieveModelChunks(boolean lastChunk) {
        when(modelCacheHelper.isModelLoaded(modelId)).thenReturn(false);
        when(modelCacheHelper.getLoadedModels()).thenReturn(new String[] {});
        when(threadPool.executor(LOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        mock_client_ThreadContext(client, threadPool, threadContext);
        if (lastChunk) {
            setUpMock_GetModelMeta_FailedToGetLastChunk(model);
        } else {
            setUpMock_GetModelMeta_FailedToGetFirstChunk(model);
        }

        ActionListener<String> listener = mock(ActionListener.class);
        FunctionName functionName = FunctionName.TEXT_EMBEDDING;

        modelManager.loadModel(modelId, modelContentHashValue, functionName, mlTask, listener);
        verify(modelCacheHelper).removeModel(eq(modelId));
        verify(mlStats).createCounterStatIfAbsent(eq(functionName), eq(ActionName.LOAD), eq(MLActionLevelStat.ML_ACTION_FAILURE_COUNT));
    }

    private void mock_client_index_ModelChunkFailure(Client client, String modelId) {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn(modelId);
            listener.onResponse(indexResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("index failure"));
            return null;
        }).when(client).index(any(), any());
    }

    private void setUpMock_GetModel(MLModel model) {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(model);
            return null;
        }).when(modelManager).getModel(any(), any());
    }

    private void setUpMock_GetModelChunks(MLModel model) {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(model);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(modelChunk0);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(modelChunk1);
            return null;
        }).when(modelManager).getModel(any(), any());
    }

    private void setUpMock_GetModelMeta_FailedToGetFirstChunk(MLModel model) {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(model);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to get model"));
            return null;
        }).when(modelManager).getModel(any(), any());
    }

    private void setUpMock_GetModelMeta_FailedToGetLastChunk(MLModel model) {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(model);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(modelChunk0);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to get model"));
            return null;
        }).when(modelManager).getModel(any(), any());
    }

    private void setUpMock_DownloadModelFileFailure() {
        doAnswer(invocation -> {
            ActionListener<Map<String, Object>> listener = invocation.getArgument(4);
            listener.onFailure(new RuntimeException("downloadAndSplit failure"));
            return null;
        }).when(modelHelper).downloadAndSplit(any(), any(), any(), any(), any());
    }

    private void setUpMock_DownloadModelFile(String[] chunks, Long modelContentSize) {
        doAnswer(invocation -> {
            ActionListener<Map<String, Object>> listener = invocation.getArgument(4);
            Map<String, Object> result = new HashMap<>();
            result.put(MODEL_SIZE_IN_BYTES, modelContentSize);
            result.put(CHUNK_FILES, Arrays.asList(chunks[0], chunks[1]));
            result.put(MODEL_FILE_HASH, randomAlphaOfLength(10));
            listener.onResponse(result);
            return null;
        }).when(modelHelper).downloadAndSplit(any(), any(), any(), any(), any());
    }

    private String[] createTempChunkFiles() throws IOException {
        String tmpFolder = randomAlphaOfLength(10);
        String newChunk0 = chunk0.substring(0, chunk0.length() - 2) + "/" + tmpFolder + "/0";
        String newChunk1 = chunk1.substring(0, chunk1.length() - 2) + "/" + tmpFolder + "/1";
        copyFile(chunk0, newChunk0);
        copyFile(chunk1, newChunk1);
        return new String[] { newChunk0, newChunk1 };
    }
}
