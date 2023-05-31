/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Ignore;
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
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportUndeployModelActionTests extends OpenSearchTestCase {

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

    @Mock
    NamedXContentRegistry xContentRegistry;

    private ThreadContext threadContext;

    @Mock
    private ExecutorService executorService;

    private TransportUndeployModelAction action;

    private DiscoveryNode localNode;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    @Ignore
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
        action = new TransportUndeployModelAction(
            transportService,
            actionFilters,
            mlModelManager,
            clusterService,
            null,
            client,
            nodeFilter,
            mlStats,
            xContentRegistry,
            modelAccessControlHelper
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

    @Ignore
    public void testConstructor() {
        assertNotNull(action);
    }

    @Ignore
    public void testNewNodeRequest() {
        final MLUndeployModelNodesRequest request = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final MLUndeployModelNodeRequest undeployRequest = action.newNodeRequest(request);
        assertNotNull(undeployRequest);
    }

    @Ignore
    public void testNewNodeStreamRequest() throws IOException {
        Map<String, String> modelToDeployStatus = new HashMap<>();
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelToDeployStatus.put("modelId1", "response");
        modelWorkerNodeCounts.put("modelId1", new String[] { "node" });
        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLUndeployModelNodeResponse undeployResponse = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(undeployResponse);
    }

    @Ignore
    public void testNodeOperation() {
        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(any())).thenReturn(mlStat);
        final MLUndeployModelNodesRequest request = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final MLUndeployModelNodeResponse response = action.nodeOperation(new MLUndeployModelNodeRequest(request));
        assertNotNull(response);
    }

    @Ignore
    public void testNewResponseWithUndeployedModelStatus() {
        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "undeployed");
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] { "foo0", "foo0" });
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response);
        ArgumentCaptor<BulkRequest> argumentCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(argumentCaptor.capture(), any());
        UpdateRequest updateRequest = (UpdateRequest) argumentCaptor.getValue().requests().get(0);
        assertEquals(ML_MODEL_INDEX, updateRequest.index());
        Map<String, Object> updateContent = updateRequest.doc().sourceAsMap();
        assertEquals(MLModelState.UNDEPLOYED.name(), updateContent.get(MLModel.MODEL_STATE_FIELD));
    }

    @Ignore
    public void testNewResponseWithNotFoundModelStatus() {
        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus = new HashMap<>();
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelToDeployStatus.put("modelId1", "not_found");
        modelWorkerNodeCounts.put("modelId1", new String[] { "node" });
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response);
        // not_found model will not trigger bulk update, this is a bug fix. Only removedWorkNodes is not empty, there'll be bulk update.
        // ArgumentCaptor<BulkRequest> argumentCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        // verify(client, times(1)).bulk(argumentCaptor.capture(), any());
        // UpdateRequest updateRequest = (UpdateRequest) argumentCaptor.getValue().requests().get(0);
        // assertEquals(ML_MODEL_INDEX, updateRequest.index());
        // Map<String, Object> updateContent = updateRequest.doc().sourceAsMap();
        // assertEquals(MLModelState.PARTIALLY_DEPLOYED.name(), updateContent.get(MLModel.MODEL_STATE_FIELD));
    }
}
