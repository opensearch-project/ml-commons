/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableSet;

public class MLSyncUpCronTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;
    @Mock
    private DiscoveryNodeHelper nodeHelper;

    private DiscoveryNode mlNode1;
    private DiscoveryNode mlNode2;
    private MLSyncUpCron syncUpCron;

    private final String mlNode1Id = "mlNode1";
    private final String mlNode2Id = "mlNode2";

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlNode1 = new DiscoveryNode(mlNode1Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(ML_ROLE), Version.CURRENT);
        mlNode2 = new DiscoveryNode(mlNode2Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(ML_ROLE), Version.CURRENT);
        syncUpCron = new MLSyncUpCron(client, clusterService, nodeHelper, null);
    }

    public void testRun() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks();

        syncUpCron.run();
        verify(client, times(2)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRun_NoLoadedModel() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks();

        syncUpCron.run();
        verify(client, times(2)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRun_Failure() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks_Failure();

        syncUpCron.run();
        verify(client, times(1)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRefreshModelState_NoSemaphore() throws InterruptedException {
        syncUpCron.updateModelStateSemaphore.acquire();
        syncUpCron.refreshModelState(null, null);
        verify(client, never()).search(any(), any());
        syncUpCron.updateModelStateSemaphore.release();
    }

    public void testRefreshModelState_SearchException() {
        doThrow(new RuntimeException("test exception")).when(client).search(any(), any());
        syncUpCron.refreshModelState(null, null);
        verify(client, times(1)).search(any(), any());
        assertTrue(syncUpCron.updateModelStateSemaphore.tryAcquire());
        syncUpCron.updateModelStateSemaphore.release();
    }

    public void testRefreshModelState_SearchFailed() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("search error"));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(null, null);
        verify(client, times(1)).search(any(), any());
        assertTrue(syncUpCron.updateModelStateSemaphore.tryAcquire());
        syncUpCron.updateModelStateSemaphore.release();
    }

    public void testRefreshModelState_EmptySearchResponse() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            SearchHits hits = new SearchHits(new SearchHit[0], null, Float.NaN);
            SearchResponseSections searchSections = new SearchResponseSections(
                hits,
                InternalAggregations.EMPTY,
                null,
                true,
                false,
                null,
                1
            );
            SearchResponse searchResponse = new SearchResponse(
                searchSections,
                null,
                1,
                1,
                0,
                11,
                ShardSearchFailure.EMPTY_ARRAY,
                SearchResponse.Clusters.EMPTY
            );
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(new HashMap<>(), new HashMap<>());
        verify(client, times(1)).search(any(), any());
        verify(client, never()).bulk(any(), any());
        assertTrue(syncUpCron.updateModelStateSemaphore.tryAcquire());
        syncUpCron.updateModelStateSemaphore.release();
    }

    public void testRefreshModelState_ResetAsLoadFailed() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        Map<String, Set<String>> loadingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(createSearchModelResponse("modelId", MLModelState.LOADED, 2, null, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, loadingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"LOAD_FAILED\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":0"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_ResetAsPartiallyLoaded() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put("modelId", ImmutableSet.of("node1"));
        Map<String, Set<String>> loadingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(createSearchModelResponse("modelId", MLModelState.LOADED, 2, 0, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, loadingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"PARTIALLY_LOADED\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":1"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_ResetCurrentWorkerNodeCountForPartiallyLoaded() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put("modelId", ImmutableSet.of("node1"));
        Map<String, Set<String>> loadingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(createSearchModelResponse("modelId", MLModelState.PARTIALLY_LOADED, 3, 2, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, loadingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"PARTIALLY_LOADED\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":1"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_ResetAsLoading() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put("modelId", ImmutableSet.of("node1"));
        Map<String, Set<String>> loadingModels = new HashMap<>();
        loadingModels.put("modelId", ImmutableSet.of("node2"));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(createSearchModelResponse("modelId", MLModelState.LOAD_FAILED, 2, 0, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, loadingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"LOADING\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":1"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_NotResetState_LoadingModelTaskRunning() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        Map<String, Set<String>> loadingModels = new HashMap<>();
        loadingModels.put("modelId", ImmutableSet.of("node2"));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(createSearchModelResponse("modelId", MLModelState.LOADING, 2, null, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, loadingModels);
        verify(client, times(1)).search(any(), any());
        verify(client, never()).bulk(any(), any());
    }

    public void testRefreshModelState_NotResetState_LoadingInGraceTime() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        Map<String, Set<String>> loadingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(createSearchModelResponse("modelId", MLModelState.LOADING, 2, null, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, loadingModels);
        verify(client, times(1)).search(any(), any());
        verify(client, never()).bulk(any(), any());
    }

    private void mockSyncUp_GatherRunningTasks() {
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            List<MLSyncUpNodeResponse> nodeResponses = new ArrayList<>();
            String[] loadedModelIds = new String[] { randomAlphaOfLength(10) };
            String[] runningLoadModelIds = new String[] { randomAlphaOfLength(10) };
            String[] runningLoadModelTaskIds = new String[] { randomAlphaOfLength(10) };
            nodeResponses.add(new MLSyncUpNodeResponse(mlNode1, "ok", loadedModelIds, runningLoadModelIds, runningLoadModelTaskIds));
            MLSyncUpNodesResponse response = new MLSyncUpNodesResponse(ClusterName.DEFAULT, nodeResponses, Arrays.asList());
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    private void mockSyncUp_GatherRunningTasks_Failure() {
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("failed to get running tasks"));
            return null;
        }).when(client).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    private SearchResponse createSearchModelResponse(
        String modelId,
        MLModelState state,
        Integer planningWorkerNodeCount,
        Integer currentWorkerNodeCount,
        Long lastUpdateTime
    ) throws IOException {
        XContentBuilder content = TestHelper.builder();
        content.startObject();
        content.field(MLModel.MODEL_STATE_FIELD, state);
        content.field(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, planningWorkerNodeCount);
        if (currentWorkerNodeCount != null) {
            content.field(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, currentWorkerNodeCount);
        }
        content.field(MLModel.LAST_UPDATED_TIME_FIELD, lastUpdateTime);
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, modelId, null, null).sourceRef(BytesReference.bytes(content));

        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            5,
            5,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }
}
