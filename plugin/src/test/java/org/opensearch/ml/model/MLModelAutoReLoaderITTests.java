/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX_MAPPING;
import static org.opensearch.ml.common.CommonValue.MODEL_LOAD_RETRY_TIMES_FIELD;
import static org.opensearch.ml.common.CommonValue.NODE_ID_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.search.SearchHit;
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

    @Before
    public void setup() throws Exception {
        super.setUp();

        MockitoAnnotations.openMocks(this);

        taskId = "taskId1";
        modelId = "modelId1";

        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true).build();

        localNodeId = clusterService().localNode().getId();

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client().threadPool(), client(), xContentRegistry(), nodeHelper, settings)
        );

    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        settings = null;
        nodeHelper = null;
        mlModelAutoReLoader = null;
        modelId = null;
        localNodeId = null;
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
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(1));
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
        Assert.assertThrows(RuntimeException.class, () -> mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId));
    }

    public void testQueryTask() throws IOException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        SearchResponse response = mlModelAutoReLoader.queryTask(localNodeId);
        SearchHit[] hits = response.getHits().getHits();
        org.hamcrest.MatcherAssert.assertThat(hits.length, is(1));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));

        initDataOfMlTask(localNodeId, modelId, MLTaskType.UPLOAD_MODEL, MLTaskState.COMPLETED);
        response = mlModelAutoReLoader.queryTask(localNodeId);
        hits = response.getHits().getHits();
        org.hamcrest.MatcherAssert.assertThat(hits.length, is(0));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));

        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.RUNNING);
        response = mlModelAutoReLoader.queryTask(localNodeId);
        hits = response.getHits().getHits();
        org.hamcrest.MatcherAssert.assertThat(hits.length, is(0));
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
    }

    public void testQueryTask_MultiDataInTaskIndex() throws IOException {
        initDataOfMlTask(localNodeId, "modelId1", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlTask(localNodeId, "modelId2", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlTask(localNodeId, "modelId3", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        SearchResponse response = mlModelAutoReLoader.queryTask(localNodeId);

        SearchHit[] hits = response.getHits().getHits();
        org.hamcrest.MatcherAssert.assertThat(hits.length, is(1));

        Map<String, Object> source = hits[0].getSourceAsMap();
        org.hamcrest.MatcherAssert.assertThat(source.get("model_id"), equalTo("modelId3"));

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_TASK_INDEX));
    }

    public void testQueryTask_IndexNotExisted() {
        Assert.assertThrows(IndexNotFoundException.class, () -> mlModelAutoReLoader.queryTask(localNodeId));
    }

    public void testGetReTryTimes() {
        int retryTimes;

        initDataOfModelReload(localNodeId, 0);
        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(0));

        initDataOfModelReload(localNodeId, 1);
        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(1));
    }

    public void testGetReTryTimesAsync() {
        AtomicInteger reTryTimes = new AtomicInteger(0);
        ActionListener<SearchResponse> actionListener = ActionListener.wrap(searchResponse -> {
            SearchHit[] hits = searchResponse.getHits().getHits();
            if (CollectionUtils.isEmpty(hits)) {
                reTryTimes.set(0);
            } else {
                Map<String, Object> sourceAsMap = hits[0].getSourceAsMap();
                reTryTimes.set((Integer) sourceAsMap.get(MODEL_LOAD_RETRY_TIMES_FIELD));
            }
        }, e -> { throw new RuntimeException("can't get reTryTimes under the node " + localNodeId + " , the reasons is: " + e); });

        doAnswer(invocation -> {
            reTryTimes.set(1);
            return null;
        }).when(mlModelAutoReLoader).getReTryTimesAsync(localNodeId, actionListener);

        mlModelAutoReLoader.getReTryTimesAsync(localNodeId, actionListener);
        verify(mlModelAutoReLoader, times(1)).getReTryTimesAsync(localNodeId, actionListener);
        org.hamcrest.MatcherAssert.assertThat(reTryTimes.get(), is(1));

        doAnswer(invocation -> {
            reTryTimes.set(3);
            return null;
        }).when(mlModelAutoReLoader).getReTryTimesAsync(localNodeId, actionListener);

        mlModelAutoReLoader.getReTryTimesAsync(localNodeId, actionListener);
        verify(mlModelAutoReLoader, times(2)).getReTryTimesAsync(localNodeId, actionListener);
        org.hamcrest.MatcherAssert.assertThat(reTryTimes.get(), is(3));
    }

    public void testGetReTryTimes_IndexNotExisted() {
        int retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(0));
    }

    public void testGetReTryTimes_EmptyHits() {
        createIndex(ML_MODEL_RELOAD_INDEX);

        int retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(0));
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
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(0));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 1);

        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(1));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 3);

        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        org.hamcrest.MatcherAssert.assertThat(retryTimes, is(3));
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

}
