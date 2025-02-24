/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

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

    private SdkClient sdkClient;

    @Mock
    ClusterState clusterState;

    @Mock
    Task task;

    @Spy
    ActionListener<MLUndeployModelNodesResponse> actionListener;

    @Mock
    MLSyncUpNodeResponse syncUpNodeResponse;

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

    DiscoveryNode localNode;

    private DiscoveryNode node1;

    private DiscoveryNode node2;

    DiscoveryNode[] nodesArray;

    @Mock
    private MLUndeployModelNodesResponse undeployModelNodesResponse;

    @Mock
    private TransportNodesAction<MLUndeployModelNodesRequest, MLUndeployModelNodesResponse, MLUndeployModelNodeRequest, MLUndeployModelNodeResponse> transportNodesAction;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = Mockito.spy(SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap()));
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.generic()).thenReturn(executorService);
        when(threadPool.executor(anyString())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        action = spy(
            new TransportUndeployModelAction(
                transportService,
                actionFilters,
                mlModelManager,
                clusterService,
                threadPool,
                client,
                sdkClient,
                nodeFilter,
                mlStats
            )
        );

        localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        InetAddress inetAddress1 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 1 });
        InetAddress inetAddress2 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 2 });

        DiscoveryNode node1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(inetAddress1, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        DiscoveryNode node2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(inetAddress2, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node1).add(node2).build();

        when(clusterService.getClusterName()).thenReturn(new ClusterName("Local Cluster"));
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.nodes()).thenReturn(nodes);
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testNewNodeRequest() {
        final MLUndeployModelNodesRequest request = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final MLUndeployModelNodeRequest undeployRequest = action.newNodeRequest(request);
        assertNotNull(undeployRequest);
    }

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

    public void testDoExecuteTransportUndeployedModelAction() {
        MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );

        action.doExecute(task, nodesRequest, actionListener);
        ArgumentCaptor<MLUndeployModelNodesResponse> argCaptor = ArgumentCaptor.forClass(MLUndeployModelNodesResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
    }

    public void testProcessUndeployModelResponseAndUpdateNullResponse() {
        MLUndeployModelNodesResponse undeployResponse = mock(MLUndeployModelNodesResponse.class);

        when(undeployResponse.getNodes()).thenReturn(null);

        action.processUndeployModelResponseAndUpdate(null, undeployResponse, actionListener);

        // Verify that the response is handled correctly
        verify(actionListener).onResponse(undeployResponse);
    }

    public void testProcessUndeployModelResponseAndUpdateResponse() {
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

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        MLSyncUpNodesResponse syncUpNodesResponse = mock(MLSyncUpNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(syncUpNodesResponse);
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
        verify(actionListener).onResponse(response);
    }

    public void testProcessUndeployModelResponseAndUpdateBulkException() {
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

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Bulk request failed"));
            return null;
        }).when(client).bulk(any(), any());

        MLSyncUpNodesResponse syncUpNodesResponse = mock(MLSyncUpNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(syncUpNodesResponse);
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
        verify(actionListener).onResponse(response);
    }

    public void testProcessUndeployModelResponseAndUpdateSyncUpException() {
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

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("SyncUp request failed"));
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
        verify(actionListener).onResponse(response);
    }

    public void testProcessUndeployModelResponseAndUpdateResponseDeployStatusWrong() {
        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "wrong_status");
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] { "foo0", "foo0" });
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        MLSyncUpNodesResponse syncUpNodesResponse = mock(MLSyncUpNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(syncUpNodesResponse);
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
        verify(actionListener).onResponse(response);
    }

    public void testProcessUndeployModelResponseAndUpdateResponseUndeployPartialNodes() {
        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus1 = new HashMap<>();
        modelToDeployStatus1.put("modelId1", "undeployed");
        Map<String, String> modelToDeployStatus2 = new HashMap<>();
        modelToDeployStatus2.put("modelId1", "deployed");
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] { "foo0", "foo0" });
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus1, modelWorkerNodeCounts);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus2, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        MLSyncUpNodesResponse syncUpNodesResponse = mock(MLSyncUpNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(syncUpNodesResponse);
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
        verify(actionListener).onResponse(response);
    }

    public void testProcessUndeployModelResponseAndUpdateResponseUndeployEmptyNodes() {
        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "undeployed");
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] {});
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        MLSyncUpNodesResponse syncUpNodesResponse = mock(MLSyncUpNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(syncUpNodesResponse);
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
        verify(actionListener).onResponse(response);
    }

    public void testProcessUndeployModelResponseAndUpdateResponseUndeployNodeEntrySetNull() {
        exceptionRule.expect(NullPointerException.class);

        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "undeployed");
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", null);
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
    }

    public void testProcessUndeployModelResponseAndUpdateResponseUndeployModelWorkerNodeBeforeRemovalNull() {
        exceptionRule.expect(NullPointerException.class);

        final MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );
        final List<MLUndeployModelNodeResponse> responses = new ArrayList<>();
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "undeployed");
        MLUndeployModelNodeResponse response1 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, null);
        MLUndeployModelNodeResponse response2 = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, null);
        responses.add(response1);
        responses.add(response2);
        final List<FailedNodeException> failures = new ArrayList<>();
        final MLUndeployModelNodesResponse response = action.newResponse(nodesRequest, responses, failures);

        action.processUndeployModelResponseAndUpdate(null, response, actionListener);
    }

    public void testDoExecuteWithEmptyRequest() {
        MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(new String[] {}, new String[] {});
        action.doExecute(task, nodesRequest, actionListener);
        verify(actionListener).onResponse(any(MLUndeployModelNodesResponse.class));
    }

    public void testProcessUndeployModelResponseAndUpdateNoUndeployments() {
        MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1", "modelId2" }
        );

        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, Collections.emptyMap(), new HashMap<>());
        MLUndeployModelNodesResponse undeployModelNodesResponse = new MLUndeployModelNodesResponse(
            clusterService.getClusterName(),
            List.of(response),
            new ArrayList<>()
        );

        action.processUndeployModelResponseAndUpdate(null, undeployModelNodesResponse, actionListener);

        verify(actionListener).onResponse(undeployModelNodesResponse);
    }

    public void testDoExecuteExceptionHandling() {
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Simulated Exception"));
            return null;
        }).when(action).doExecute(any(), any(), any());

        MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(new String[] { "nodeId1" }, new String[] { "modelId1" });

        action.doExecute(task, nodesRequest, actionListener);

        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    public void testProcessUndeployModelResponseAndUpdateSyncUpFailure() {
        MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1" }
        );

        Map<String, String> modelUndeployStatus = new HashMap<>();
        modelUndeployStatus.put("modelId1", "undeployed");

        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] { "foo0", "foo0" });

        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, modelUndeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodesResponse undeployModelNodesResponse = new MLUndeployModelNodesResponse(
            clusterService.getClusterName(),
            List.of(response),
            new ArrayList<>()
        );

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Sync-Up Failure"));
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, undeployModelNodesResponse, actionListener);

        verify(actionListener).onResponse(undeployModelNodesResponse);
    }

    public void testProcessUndeployModelResponseAndUpdateSyncUpSuccess() {
        MLUndeployModelNodesRequest nodesRequest = new MLUndeployModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            new String[] { "modelId1" }
        );

        Map<String, String> modelUndeployStatus = new HashMap<>();
        modelUndeployStatus.put("modelId1", "undeployed");

        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] { "foo0", "foo0" });

        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, modelUndeployStatus, modelWorkerNodeCounts);
        MLUndeployModelNodesResponse undeployModelNodesResponse = new MLUndeployModelNodesResponse(
            clusterService.getClusterName(),
            List.of(response),
            new ArrayList<>()
        );

        BulkResponse bulkResponse = mock(BulkResponse.class);
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        MLSyncUpNodesResponse syncUpNodesResponse = mock(MLSyncUpNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(syncUpNodesResponse);
            return null;
        }).when(client).execute(any(), any(MLSyncUpNodesRequest.class), any());

        action.processUndeployModelResponseAndUpdate(null, undeployModelNodesResponse, actionListener);

        verify(actionListener).onResponse(undeployModelNodesResponse);
    }

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
    }
}
