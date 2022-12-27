/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 3)
public class MLModelAutoReLoaderITTests extends MLCommonsIntegTestCase {
    private final Instant time = Instant.now();
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private DiscoveryNodeHelper nodeHelper;
    private Settings settings;
    private MLModelAutoReLoader mlModelAutoReLoader;
    @Mock
    private MLStats mlStats;
    @Mock
    private ModelHelper modelHelper;
    private String modelId;
    private String localNodeId;
    @Mock
    private MLLoadModelRequest mlLoadModelRequest;
    @Mock
    private DiscoveryNodeHelper nodeFilter;
    @Mock
    private MLModelManager modelManager;
    private MLModel modelChunk0;
    private MLModel modelChunk1;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        super.setUp();

        settings = Settings.builder().put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true).build();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        mlStats = spy(new MLStats(stats));
        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));
        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client(), client().threadPool(), xContentRegistry(), nodeHelper, settings, mlStats)
        );
        modelId = "modelId1";
        localNodeId = clusterService().localNode().getId();
        modelManager = mock(MLModelManager.class);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        settings = null;
        mlStats = null;
        nodeHelper = null;
        mlModelAutoReLoader = null;
        modelId = null;
        localNodeId = null;
        modelManager = null;
    }

    public void testIsExistedIndex() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        createIndex(ML_MODEL_RELOAD_INDEX);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testSaveLatestReTryTimes_getReTryTimes() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 0);
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
        Integer retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 1);
        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(1));
    }

    public void testGetReTryTimes_IndexNotFoundException() {
        exceptionRule.expect(IndexNotFoundException.class);
        mlModelAutoReLoader.getReTryTimes(localNodeId);
    }

    public void testGetReTryTimes_EmptyHits() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        createIndex(ML_MODEL_RELOAD_INDEX);

        Integer retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));
    }

    public void testAutoReLoadModelByNodeAndModelId_Exception() throws IOException, URISyntaxException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId, MLModelState.LOADED);

        exceptionRule.expect(IllegalArgumentException.class);
        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testAutoReLoadModelByNodeAndModelId_IndexNotFoundException() throws IOException, URISyntaxException {
        exceptionRule.expect(IndexNotFoundException.class);
        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testAutoReLoadModelByNodeId() throws IOException, URISyntaxException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId, MLModelState.LOADED);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModelByNodeId_IndexNotFound() {
        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModelByNodeId_EmptyHits() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));

        createIndex(ML_TASK_INDEX);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModelByNodeId_FailToAutoReloadModel() throws IOException, URISyntaxException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId, MLModelState.LOADED);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        Integer retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Something went wrong"));
            return null;
        }).when(modelManager).loadModel(any(), any(), any(), any());

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));
    }

    public void testAutoReLoadModel() throws IOException, URISyntaxException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId, MLModelState.LOADED);

        mlModelAutoReLoader.autoReLoadModel();

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    private void initDataOfMlTask(String nodeId, String modelId, MLTaskType mlTaskType, MLTaskState mlTaskState) throws IOException {
        MLTask mlTask = MLTask
            .builder()
            .taskId("taskId1")
            .modelId(modelId)
            .taskType(mlTaskType)
            .workerNode(nodeId)
            .progress(0.0f)
            .outputIndex("test_index")
            .error("test_error")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .lastUpdateTime(time)
            .build();

        IndexRequest indexRequest = new IndexRequest(ML_TASK_INDEX);
        indexRequest.id("taskId1");
        indexRequest.source(mlTask.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client().execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);
    }

    private void initDataOfMlModel(String modelId, MLModelState modelState) throws IOException, URISyntaxException {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(384)
            .build();

        MLModel mlModel = MLModel
            .builder()
            .name("model_name")
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .version("1.0.0")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelState(modelState)
            .modelConfig(modelConfig)
            .totalChunks(2)
            .modelContentHash("c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8")
            .modelContentSizeInBytes(1000L)
            .createdTime(time.minus(1, ChronoUnit.MINUTES))
            .build();
        modelChunk0 = mlModel
            .toBuilder()
            .content(Base64.getEncoder().encodeToString("test chunk1".getBytes(StandardCharsets.UTF_8)))
            .build();
        modelChunk1 = mlModel
            .toBuilder()
            .content(Base64.getEncoder().encodeToString("test chunk2".getBytes(StandardCharsets.UTF_8)))
            .build();

        setUpMock_GetModelChunks(mlModel);

        IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
        indexRequest.id(modelId);
        indexRequest.source(mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client().execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);
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
}
