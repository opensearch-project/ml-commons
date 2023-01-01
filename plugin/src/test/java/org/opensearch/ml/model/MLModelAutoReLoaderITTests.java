/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX_MAPPING;
import static org.opensearch.ml.common.CommonValue.MODEL_LOAD_RETRY_TIMES_FIELD;
import static org.opensearch.ml.common.CommonValue.NODE_ID_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.delete.DeleteAction;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.common.util.CollectionUtils;
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
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class MLModelAutoReLoaderITTests extends MLCommonsIntegTestCase {
    private final Instant time = Instant.now();
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private DiscoveryNodeHelper nodeHelper;
    private Settings settings;
    private MLModelAutoReLoader mlModelAutoReLoader;
    private String taskId;
    private String modelId;
    private String localNodeId;
    @Mock
    private MLModelManager modelManager;
    private MLModel modelChunk0;
    private MLModel modelChunk1;

    @Before
    public void setup() throws Exception {
        super.setUp();

        settings = Settings.builder().put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true).build();

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));

        taskId = "taskId1";
        modelId = "modelId1";
        localNodeId = clusterService().localNode().getId();

        AtomicInteger portGenerator = new AtomicInteger();
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(TestHelper.ML_ROLE);
        DiscoveryNode node = new DiscoveryNode(
            localNodeId,
            new TransportAddress(TransportAddress.META_ADDRESS, portGenerator.incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client().threadPool(), client(), xContentRegistry(), nodeHelper, settings)
        );

        modelManager = mock(MLModelManager.class);

        when(clusterService().localNode()).thenReturn(node);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        settings = null;
        nodeHelper = null;
        mlModelAutoReLoader = null;
        modelId = null;
        localNodeId = null;
        modelManager = null;
    }

    public void testAutoReLoadModel() {
        mlModelAutoReLoader.autoReLoadModel();

        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModel_setting_false() {
        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), false).build();

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client().threadPool(), client(), xContentRegistry(), nodeHelper, settings)
        );

        mlModelAutoReLoader.autoReLoadModel();

        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModel_Is_Not_ML_Node() {
        mlModelAutoReLoader.autoReLoadModel();

        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    @Ignore
    public void testAutoReLoadModelByNodeId() throws IOException {
        if (indexExists(ML_TASK_INDEX)) {
            clearDataOfMlTask();
        }

        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL);
        initDataOfMlModel(modelId, MLModelState.LOADED);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        int retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        SearchResponse searchResponse = mlModelAutoReLoader.queryTask(localNodeId);
        SearchHit[] hits = searchResponse.getHits().getHits();
        if (!CollectionUtils.isEmpty(hits)) {
            for (SearchHit searchHit : hits) {
                logger.info("searchHit is:" + searchHit.getSourceAsString());
            }
        }

    }

    public void testAutoReLoadModelByNodeId_Max_ReTryTimes() throws IOException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 3);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModelByNodeId_ReTry() throws IOException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        int retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(1));
    }

    public void testAutoReLoadModelByNodeId_IndexNotFound() {
        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModelByNodeId_EmptyHits() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));

        createIndex();

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_INDEX));
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testAutoReLoadModelByNodeAndModelId_Exception() {
        exceptionRule.expect(RuntimeException.class);
        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testQueryTask() throws IOException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        SearchResponse response = mlModelAutoReLoader.queryTask(localNodeId);
        SearchHit[] hits = response.getHits().getHits();
        assertThat(hits.length, is(1));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));

        initDataOfMlTask(localNodeId, modelId, MLTaskType.UPLOAD_MODEL, MLTaskState.COMPLETED);
        response = mlModelAutoReLoader.queryTask(localNodeId);
        hits = response.getHits().getHits();
        assertThat(hits.length, is(0));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));

        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.RUNNING);
        response = mlModelAutoReLoader.queryTask(localNodeId);
        hits = response.getHits().getHits();
        assertThat(hits.length, is(0));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
    }

    public void testQueryTask_MultiDataInTaskIndex() throws IOException {
        initDataOfMlTask(localNodeId, "modelId1", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlTask(localNodeId, "modelId2", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlTask(localNodeId, "modelId3", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        SearchResponse response = mlModelAutoReLoader.queryTask(localNodeId);

        SearchHit[] hits = response.getHits().getHits();
        assertThat(hits.length, is(1));

        Map<String, Object> source = hits[0].getSourceAsMap();
        assertThat(source.get("model_id"), equalTo("modelId3"));

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
    }

    public void testQueryTask_IndexNotExisted() {
        exceptionRule.expect(IndexNotFoundException.class);

        mlModelAutoReLoader.queryTask(localNodeId);
    }

    public void testGetReTryTimes() {
        int retryTimes;

        initDataOfModelReload(localNodeId, 0);
        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        initDataOfModelReload(localNodeId, 1);
        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(1));
    }

    public void testGetReTryTimes_IndexNotExisted() {
        int retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));
    }

    public void testGetReTryTimes_EmptyHits() {
        createIndex(ML_MODEL_RELOAD_INDEX);

        int retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));
    }

    public void testIsExistedIndex_False() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testIsExistedIndex_True() {
        createIndex(ML_MODEL_RELOAD_INDEX);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testSaveLatestReTryTimes() {
        int retryTimes;
        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 0);

        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 1);

        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(1));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 3);

        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(3));
    }

    private void createIndex() {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(ML_TASK_INDEX);
        createIndexRequest.mapping(ML_TASK_INDEX_MAPPING);

        client().execute(CreateIndexAction.INSTANCE, createIndexRequest).actionGet(5000);
    }

    private void initDataOfModelReload(String nodeId, int reTryTimes) {
        Map<String, Object> content = new HashMap<>(2);
        content.put(NODE_ID_FIELD, nodeId);
        content.put(MODEL_LOAD_RETRY_TIMES_FIELD, reTryTimes);

        IndexRequest indexRequest = new IndexRequest(ML_MODEL_RELOAD_INDEX);
        indexRequest.id(nodeId);
        indexRequest.version();
        indexRequest.source(content);
        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client().execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);
    }

    private void initDataOfMlTask(String nodeId, String modelId, MLTaskType mlTaskType, MLTaskState mlTaskState) throws IOException {
        MLTask mlTask = MLTask
            .builder()
            .taskId(taskId)
            .modelId(modelId)
            .taskType(mlTaskType)
            .state(mlTaskState)
            .workerNode(nodeId)
            .progress(0.0f)
            .outputIndex("test_index")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .autoReload(true)
            .async(true)
            .lastUpdateTime(time)
            .build();

        IndexRequest indexRequest = new IndexRequest(ML_TASK_INDEX);
        indexRequest.id(taskId);
        indexRequest.version();
        indexRequest.source(mlTask.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client().execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);
    }

    private void initDataOfMlTask(String nodeId, String modelId, MLTaskType mlTaskType) throws IOException {
        MLTask mlTask = MLTask
            .builder()
            .taskId(taskId)
            .modelId(modelId)
            .taskType(mlTaskType)
            .workerNode(nodeId)
            .progress(0.0f)
            .outputIndex("test_index")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .state(MLTaskState.COMPLETED)
            .autoReload(true)
            .async(true)
            .lastUpdateTime(time)
            .build();

        IndexRequest indexRequest = new IndexRequest(ML_TASK_INDEX);
        indexRequest.id(taskId);
        indexRequest.version();
        indexRequest.source(mlTask.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client().execute(IndexAction.INSTANCE, indexRequest).actionGet(5000);
    }

    private void initDataOfMlModel(String modelId, MLModelState modelState) throws IOException {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(384)
            .build();

        MLModel mlModel = MLModel
            .builder()
            .modelId(modelId)
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
        indexRequest.version();
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

    private void clearDataOfMlTask() {
        DeleteRequest deleteRequest = new DeleteRequest(ML_TASK_INDEX);
        deleteRequest.id("taskId2");
        client().execute(DeleteAction.INSTANCE, deleteRequest).actionGet(5000);
    }
}
