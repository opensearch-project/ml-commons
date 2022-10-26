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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.UPLOAD_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

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

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10).build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MAX_MODELS_PER_NODE,
            ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE,
            ML_COMMONS_MONITORING_REQUEST_COUNT
        );
        clusterService = spy(new ClusterService(settings, clusterSettings, null));

        modelName = "model_name1";
        version = "1.0.0";
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
            .url("test_url")
            .build();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        this.mlStats = new MLStats(stats);

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

        modelManager = new MLModelManager(
            clusterService,
            client,
            threadPool,
            xContentRegistry,
            modelHelper,
            settings,
            mlStats,
            mlCircuitBreakerService,
            mlIndicesHandler,
            mlTaskManager
        );
    }

    public void testUploadMLModel_ExceedMaxRunningTask() {
        String error = "exceed max running task limit";
        expectedEx.expect(MLLimitExceededException.class);
        expectedEx.expectMessage(error);
        when(mlTaskManager.checkLimitAndAddRunningTask(any(), any())).thenReturn(error);
        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlTaskManager, never()).updateMLTaskDirectly(eq(mlTask.getTaskId()), any());
    }

    public void testUploadMLModel_CircuitBreakerOpen() {
        expectedEx.expect(MLLimitExceededException.class);
        expectedEx.expectMessage("Circuit breaker is open, please check your memory and disk usage!");
        when(mlTaskManager.checkLimitAndAddRunningTask(any(), any())).thenReturn(null);
        when(mlCircuitBreakerService.isOpen()).thenReturn(true);
        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlTaskManager, never()).updateMLTaskDirectly(eq(mlTask.getTaskId()), any());
    }

    public void testUploadMLModel_InitModelIndexFailure() {
        when(mlTaskManager.checkLimitAndAddRunningTask(any(), any())).thenReturn(null);
        when(mlCircuitBreakerService.isOpen()).thenReturn(false);
        when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
        setUpMock_InitModelIndexFailure();

        modelManager.uploadMLModel(uploadInput, mlTask);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
        verify(modelHelper, never()).downloadAndSplit(any(), any(), any(), any(), any());
        verify(client, never()).index(any(), any());
    }

    private void setUpMock_InitModelIndexFailure() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("test failure"));
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(any());
    }

}
