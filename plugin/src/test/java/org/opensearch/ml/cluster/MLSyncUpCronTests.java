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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;
import static org.opensearch.ml.utils.TestHelper.setupTestClusterState;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MLSyncUpCronTests extends OpenSearchTestCase {
    @Mock
    private Client client;
    private SdkClient sdkClient;
    @Mock
    private ClusterService clusterService;
    @Mock
    private DiscoveryNodeHelper nodeHelper;
    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private DiscoveryNode mlNode1;
    private DiscoveryNode mlNode2;
    private MLSyncUpCron syncUpCron;

    private final String mlNode1Id = "mlNode1";
    private final String mlNode2Id = "mlNode2";

    private ClusterState testState;
    private Encryptor encryptor;

    @Mock
    ThreadPool threadPool;
    ThreadContext threadContext;
    final String USER_STRING = "myuser|role1,role2|myTenant";

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlNode1 = new DiscoveryNode(mlNode1Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(ML_ROLE), Version.CURRENT);
        mlNode2 = new DiscoveryNode(mlNode2Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(ML_ROLE), Version.CURRENT);
        encryptor = spy(new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w="));

        testState = setupTestClusterState("node");
        when(clusterService.state()).thenReturn(testState);

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        Settings settings = Settings.builder().build();
        sdkClient = Mockito.spy(SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap()));
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        syncUpCron = new MLSyncUpCron(client, sdkClient, clusterService, nodeHelper, mlIndicesHandler, encryptor, mlFeatureEnabledSetting);
    }

    public void testInitMlConfig_MasterKeyNotExist() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            IndexResponse indexResponse = mock(IndexResponse.class);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        syncUpCron.initMLConfig();
        syncUpCron.initMLConfig();
        verify(encryptor, times(1)).setMasterKey(any(), any());
    }

    public void testInitMlConfig_MasterKeyExists() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(true);
            String masterKey = encryptor.generateMasterKey();
            when(response.getSourceAsMap())
                .thenReturn(ImmutableMap.of(MASTER_KEY, masterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli()));
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        syncUpCron.initMLConfig();
        syncUpCron.initMLConfig();
        verify(encryptor, times(1)).setMasterKey(any(), any());
    }

    public void testRun_NoMLModelIndex() {
        Metadata metadata = new Metadata.Builder().indices(Map.of()).build();
        DiscoveryNode node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, new AtomicInteger().incrementAndGet()),
            new HashMap<>(),
            ImmutableSet.of(DiscoveryNodeRole.DATA_ROLE),
            Version.CURRENT
        );
        ClusterState state = new ClusterState(
            new ClusterName("test cluster"),
            123l,
            "111111",
            metadata,
            null,
            DiscoveryNodes.builder().add(node).build(),
            null,
            Map.of(),
            0,
            false
        );
        ;
        when(clusterService.state()).thenReturn(state);

        syncUpCron.run();
        verify(client, never()).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRun() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks();

        syncUpCron.run();
        verify(client, times(2)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRun_NoDeployedModel() {
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

    public void testRefreshModelState_ResetAsDeployFailed() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        Map<String, Set<String>> deployingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(createSearchModelResponse("modelId", "tenantId", MLModelState.DEPLOYED, 2, null, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, deployingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"DEPLOY_FAILED\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":0"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_ResetAsPartiallyDeployed() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put("modelId", ImmutableSet.of("node1"));
        Map<String, Set<String>> deployingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(createSearchModelResponse("modelId", "tenantId", MLModelState.DEPLOYED, 2, 0, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, deployingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"PARTIALLY_DEPLOYED\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":1"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_ResetCurrentWorkerNodeCountForPartiallyDeployed() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put("modelId", ImmutableSet.of("node1"));
        Map<String, Set<String>> deployingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(createSearchModelResponse("modelId", "tenantId", MLModelState.DEPLOYED, 2, 0, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, deployingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"PARTIALLY_DEPLOYED\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":1"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_ResetAsDeploying() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put("modelId", ImmutableSet.of("node1"));
        Map<String, Set<String>> deployingModels = new HashMap<>();
        deployingModels.put("modelId", ImmutableSet.of("node2"));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(createSearchModelResponse("modelId", "tenantId", MLModelState.DEPLOYED, 2, 0, Instant.now().toEpochMilli()));
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, deployingModels);
        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkRequestCaptor.capture(), any());
        BulkRequest bulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, bulkRequest.numberOfActions());
        assertEquals(1, bulkRequest.requests().size());
        UpdateRequest updateRequest = (UpdateRequest) bulkRequest.requests().get(0);
        String updateContent = updateRequest.toString();
        assertTrue(updateContent.contains("\"model_state\":\"DEPLOYING\""));
        assertTrue(updateContent.contains("\"current_worker_node_count\":1"));
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
    }

    public void testRefreshModelState_NotResetState_DeployingModelTaskRunning() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        Map<String, Set<String>> deployingModels = new HashMap<>();
        deployingModels.put("modelId", ImmutableSet.of("node2"));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(
                    createSearchModelResponse("modelId", "tenantId", MLModelState.DEPLOYING, 2, null, Instant.now().toEpochMilli())
                );
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, deployingModels);
        verify(client, times(1)).search(any(), any());
        verify(client, never()).bulk(any(), any());
    }

    public void testRefreshModelState_NotResetState_DeployingInGraceTime() {
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        Map<String, Set<String>> deployingModels = new HashMap<>();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(
                    createSearchModelResponse("modelId", "tenantId", MLModelState.DEPLOYING, 2, null, Instant.now().toEpochMilli())
                );
            return null;
        }).when(client).search(any(), any());
        syncUpCron.refreshModelState(modelWorkerNodes, deployingModels);
        verify(client, times(1)).search(any(), any());
        verify(client, never()).bulk(any(), any());
    }

    private void mockSyncUp_GatherRunningTasks() {
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            List<MLSyncUpNodeResponse> nodeResponses = new ArrayList<>();
            String[] deployedModelIds = new String[] { randomAlphaOfLength(10) };
            String[] runningDeployModelIds = new String[] { randomAlphaOfLength(10) };
            String[] runningDeployModelTaskIds = new String[] { randomAlphaOfLength(10) };
            String[] expiredModelIds = new String[] { randomAlphaOfLength(10) };
            nodeResponses
                .add(
                    new MLSyncUpNodeResponse(
                        mlNode1,
                        "ok",
                        deployedModelIds,
                        runningDeployModelIds,
                        runningDeployModelTaskIds,
                        expiredModelIds
                    )
                );
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
        String tenantId,
        MLModelState state,
        Integer planningWorkerNodeCount,
        Integer currentWorkerNodeCount,
        Long lastUpdateTime
    ) throws IOException {
        XContentBuilder content = TestHelper.builder();
        content.startObject();
        content.field(CommonValue.TENANT_ID_FIELD, tenantId);
        content.field(MLModel.MODEL_STATE_FIELD, state);
        content.field(MLModel.ALGORITHM_FIELD, FunctionName.KMEANS);
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
