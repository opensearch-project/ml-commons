/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.unload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.unload.UnloadModelNodeRequest;
import org.opensearch.ml.common.transport.unload.UnloadModelNodeResponse;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesRequest;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportUnloadModelActionTests extends OpenSearchTestCase {

    @Mock
    private ThreadPool threadPool;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private ClusterService clusterService;

    @Mock
    private Client client;

    @Mock
    private DiscoveryNodeHelper nodeFilter;

    @Mock
    private MLStats mlStats;

    private ThreadContext threadContext;

    @Mock
    private ExecutorService executorService;

    private TransportUnloadModelAction action;

    private DiscoveryNode localNode;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        action = new TransportUnloadModelAction(
            transportService,
            actionFilters,
            mlModelManager,
            clusterService,
            null,
            client,
            nodeFilter,
            mlStats
        );
        localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.getClusterName()).thenReturn(new ClusterName("Local Cluster"));
        when(clusterService.localNode()).thenReturn(localNode);
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testNewNodeRequest() {
        final UnloadModelNodesRequest request = new UnloadModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final UnloadModelNodeRequest unLoadrequest = action.newNodeRequest(request);
        assertNotNull(unLoadrequest);
    }

    public void testNewNodeStreamRequest() throws IOException {
        Map<String, String> modelToLoadStatus = new HashMap<>();
        Map<String, Integer> modelWorkerNodeCounts = new HashMap<>();
        modelToLoadStatus.put("modelId1", "response");
        modelWorkerNodeCounts.put("modelId1", 1);
        UnloadModelNodeResponse response = new UnloadModelNodeResponse(localNode, modelToLoadStatus, modelWorkerNodeCounts);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final UnloadModelNodeResponse unLoadResponse = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(unLoadResponse);
    }

    public void testNodeOperation() {
        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(any())).thenReturn(mlStat);
        final UnloadModelNodesRequest request = new UnloadModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final UnloadModelNodeResponse response = action.nodeOperation(new UnloadModelNodeRequest(request));
        assertNotNull(response);
    }

    public void testNewResponseWithUnloadedModelStatus() {
        final UnloadModelNodesRequest nodesRequest = new UnloadModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<UnloadModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToLoadStatus = new HashMap<>();
        modelToLoadStatus.put("modelId1", "unloaded");
        Map<String, Integer> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", 1);
        UnloadModelNodeResponse response1 = new UnloadModelNodeResponse(localNode, modelToLoadStatus, modelWorkerNodeCounts);
        UnloadModelNodeResponse response2 = new UnloadModelNodeResponse(localNode, modelToLoadStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final UnloadModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response);
        ArgumentCaptor<BulkRequest> argumentCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(argumentCaptor.capture(), any());
        UpdateRequest updateRequest = (UpdateRequest) argumentCaptor.getValue().requests().get(0);
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
        Map<String, Object> updateContent = updateRequest.doc().sourceAsMap();
        assertEquals(MLModelState.UNLOADED.name(), updateContent.get(MLModel.MODEL_STATE_FIELD));
    }

    public void testNewResponseWithNotFoundModelStatus() {
        final UnloadModelNodesRequest nodesRequest = new UnloadModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<UnloadModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToLoadStatus = new HashMap<>();
        Map<String, Integer> modelWorkerNodeCounts = new HashMap<>();
        modelToLoadStatus.put("modelId1", "not_found");
        modelWorkerNodeCounts.put("modelId1", 2);
        UnloadModelNodeResponse response1 = new UnloadModelNodeResponse(localNode, modelToLoadStatus, modelWorkerNodeCounts);
        UnloadModelNodeResponse response2 = new UnloadModelNodeResponse(localNode, modelToLoadStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final UnloadModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response);
        ArgumentCaptor<BulkRequest> argumentCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(argumentCaptor.capture(), any());
        UpdateRequest updateRequest = (UpdateRequest) argumentCaptor.getValue().requests().get(0);
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
        Map<String, Object> updateContent = updateRequest.doc().sourceAsMap();
        assertEquals(MLModelState.PARTIALLY_LOADED.name(), updateContent.get(MLModel.MODEL_STATE_FIELD));
    }
}
