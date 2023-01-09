/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.TransportAddress;
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
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 3)
public class MLModelAutoReLoaderITTests extends MLCommonsIntegTestCase {
    private final Instant time = Instant.now();

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

        MockitoAnnotations.openMocks(this);

        taskId = "taskId1";
        modelId = "modelId1";

        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true).build();

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

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client().threadPool(), client(), xContentRegistry(), nodeHelper, settings)
        );
        modelManager = mock(MLModelManager.class);

        when(nodeHelper.getEligibleNodes()).thenReturn(new DiscoveryNode[] { node });
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
    }

    public void testAutoReLoadModel_setting_false() {
        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), false).build();

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client().threadPool(), client(), xContentRegistry(), nodeHelper, settings)
        );

        mlModelAutoReLoader.autoReLoadModel();
    }

    public void testAutoReLoadModel_Is_Not_ML_Node() {
        mlModelAutoReLoader.autoReLoadModel();
    }

    public void testAutoReLoadModelByNodeId() throws IOException, ExecutionException, InterruptedException {
        createIndex(ML_MODEL_RELOAD_INDEX);
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));
    }

    public void testAutoReLoadModelByNodeId_ReTry() throws IOException, ExecutionException, InterruptedException {
        createIndex(ML_MODEL_RELOAD_INDEX);
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));
    }

    public void testAutoReLoadModelByNodeId_Max_ReTryTimes() throws IOException, ExecutionException, InterruptedException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        final CountDownLatch inProgressLatch = new CountDownLatch(1);
        StepListener<IndexResponse> saveLatestReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader
            .saveLatestReTryTimes(
                localNodeId,
                3,
                ActionListener.wrap(saveLatestReTryTimesStep::onResponse, saveLatestReTryTimesStep::onFailure)
            );

        saveLatestReTryTimesStep.whenComplete(response -> {
            inProgressLatch.countDown();
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status().getStatus(), anyOf(is(201), is(200)));
        }, exception -> fail(exception.getMessage()));

        inProgressLatch.await();

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModelByNodeId_IndexNotFound() throws ExecutionException, InterruptedException {
        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModelByNodeId_EmptyHits() throws ExecutionException, InterruptedException {
        createIndex();

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModelByNodeAndModelId() throws IOException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId);
        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testAutoReLoadModelByNodeAndModelId_Exception() {
        Throwable exception = Assert
            .assertThrows(RuntimeException.class, () -> mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId));

        org.hamcrest.MatcherAssert
            .assertThat(
                exception.getMessage(),
                containsString("fail to reload model " + modelId + " under the node " + localNodeId + "\nthe reason is: ")
            );
    }

    public void testQueryTask() throws IOException {
        StepListener<SearchResponse> queryTaskStep = queryTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));
        }, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, modelId, MLTaskType.UPLOAD_MODEL, MLTaskState.COMPLETED);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(0));
        }, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.RUNNING);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(0));
        }, exception -> fail(exception.getMessage()));
    }

    public void testQueryTask_MultiDataInTaskIndex() throws IOException {
        StepListener<SearchResponse> queryTaskStep = queryTask(localNodeId, "modelId1", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        queryTaskStep.whenComplete(response -> {}, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, "modelId2", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        queryTaskStep.whenComplete(response -> {}, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, "modelId3", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));

            Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
            org.hamcrest.MatcherAssert.assertThat(source.get("model_id"), is("modelId3"));
        }, exception -> fail(exception.getMessage()));

    }

    public void testQueryTask_IndexNotExisted() {
        StepListener<SearchResponse> queryTaskStep = new StepListener<>();

        Throwable exception = Assert
            .assertThrows(
                IndexNotFoundException.class,
                () -> mlModelAutoReLoader.queryTask(localNodeId, ActionListener.wrap(queryTaskStep::onResponse, queryTaskStep::onFailure))
            );
        org.hamcrest.MatcherAssert
            .assertThat(exception.getMessage(), containsString("no such index [index " + ML_TASK_INDEX + " not found]"));
    }

    public void testGetReTryTimes() throws InterruptedException {
        assertLatestReTryTimes(1);
        assertLatestReTryTimes(3);
    }

    public void testGetReTryTimes_IndexNotExisted() {
        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();

        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));
    }

    public void testGetReTryTimes_EmptyHits() {
        createIndex(ML_MODEL_RELOAD_INDEX);

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));

        getReTryTimesStep.whenComplete(response -> {}, exception -> {
            org.hamcrest.MatcherAssert.assertThat(exception.getClass(), is(RuntimeException.class));
            org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), containsString("can't get reTryTimes from node"));
        });
    }

    public void testIsExistedIndex_False() {
        StepListener<IndicesExistsResponse> indicesExistsResponseStep = new StepListener<>();
        mlModelAutoReLoader
            .isExistedIndex(
                ML_MODEL_RELOAD_INDEX,
                ActionListener.wrap(indicesExistsResponseStep::onResponse, indicesExistsResponseStep::onFailure)
            );

        indicesExistsResponseStep.whenComplete(response -> assertFalse(response.isExists()), exception -> fail(exception.getMessage()));
    }

    public void testIsExistedIndex_True() {
        createIndex(ML_MODEL_RELOAD_INDEX);

        StepListener<IndicesExistsResponse> indicesExistsResponseStep = new StepListener<>();
        mlModelAutoReLoader
            .isExistedIndex(
                ML_MODEL_RELOAD_INDEX,
                ActionListener.wrap(indicesExistsResponseStep::onResponse, indicesExistsResponseStep::onFailure)
            );

        indicesExistsResponseStep.whenComplete(response -> assertTrue(response.isExists()), exception -> fail(exception.getMessage()));
    }

    public void testSaveLatestReTryTimes() throws InterruptedException {
        assertLatestReTryTimes(0);
        assertLatestReTryTimes(1);
        assertLatestReTryTimes(3);
    }

    private void createIndex() {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(ML_TASK_INDEX);
        createIndexRequest.mapping(ML_TASK_INDEX_MAPPING);

        client().execute(CreateIndexAction.INSTANCE, createIndexRequest).actionGet(5000);
    }

    private void initDataOfMlTask(String nodeId, String modelId, MLTaskType mlTaskType, MLTaskState mlTaskState) throws IOException {
        MLTask mlTask = MLTask
            .builder()
            .taskId(taskId)
            .modelId(modelId)
            .taskType(mlTaskType)
            .state(mlTaskState)
            .workerNodes(List.of(nodeId))
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

    private void assertLatestReTryTimes(int reTryTimes) throws InterruptedException {
        final CountDownLatch inProgressLatch = new CountDownLatch(1);
        StepListener<IndexResponse> saveLatestReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader
            .saveLatestReTryTimes(
                localNodeId,
                reTryTimes,
                ActionListener.wrap(saveLatestReTryTimesStep::onResponse, saveLatestReTryTimesStep::onFailure)
            );

        saveLatestReTryTimesStep.whenComplete(response -> {
            inProgressLatch.countDown();
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status().getStatus(), anyOf(is(201), is(200)));
        }, exception -> fail(exception.getMessage()));

        inProgressLatch.await();

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));

        getReTryTimesStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));

            Map<String, Object> sourceAsMap = response.getHits().getHits()[0].getSourceAsMap();
            int result = (Integer) sourceAsMap.get(MODEL_LOAD_RETRY_TIMES_FIELD);
            org.hamcrest.MatcherAssert.assertThat(result, is(reTryTimes));
        }, exception -> fail(exception.getMessage()));
    }

    private StepListener<SearchResponse> queryTask(String localNodeId, String modelId, MLTaskType mlTaskType, MLTaskState mlTaskState)
        throws IOException {
        initDataOfMlTask(localNodeId, modelId, mlTaskType, mlTaskState);

        StepListener<SearchResponse> queryTaskStep = new StepListener<>();
        mlModelAutoReLoader.queryTask(localNodeId, ActionListener.wrap(queryTaskStep::onResponse, queryTaskStep::onFailure));

        return queryTaskStep;
    }

    private void initDataOfMlModel(String modelId) throws IOException {
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
            .modelState(MLModelState.LOADED)
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
}
