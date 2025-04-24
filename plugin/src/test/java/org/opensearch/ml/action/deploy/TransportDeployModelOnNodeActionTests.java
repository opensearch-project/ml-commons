/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.deploy;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Ignore;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.transport.deploy.MLDeployModelInput;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodeRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodeResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesResponse;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;

public class TransportDeployModelOnNodeActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ModelHelper modelHelper;

    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MLCircuitBreakerService mlCircuitBreakerService;

    @Mock
    private MLStats mlStats;

    @Mock
    private ExecutorService executorService;

    @Mock
    private ActionFilters actionFilters;

    private TransportDeployModelOnNodeAction action;

    private ThreadContext threadContext;

    private Settings settings;

    private DiscoveryNode localNode;
    private DiscoveryNode localNode1;
    private DiscoveryNode localNode2;
    private DiscoveryNode localNode3;
    private DiscoveryNode clusterManagerNode;
    private final String clusterManagerNodeId = "clusterManagerNode";

    private MLTask mlTask;

    @Before
    @Ignore
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE.getKey(), 1).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        action = new TransportDeployModelOnNodeAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            mlModelManager,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            mlCircuitBreakerService,
            mlStats
        );

        clusterManagerNode = new DiscoveryNode(
            clusterManagerNodeId,
            buildNewFakeTransportAddress(),
            emptyMap(),
            emptySet(),
            Version.CURRENT
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
        InetAddress inetAddress3 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 3 });
        localNode1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(inetAddress1, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        localNode2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(inetAddress2, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        localNode3 = new DiscoveryNode(
            "foo3",
            "foo3",
            new TransportAddress(inetAddress3, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        when(clusterService.getClusterName()).thenReturn(new ClusterName("Local Cluster"));
        when(clusterService.localNode()).thenReturn(localNode);

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(5);
            listener.onResponse("successful");
            return null;
        }).when(mlModelManager).deployModel(any(), any(), any(), any(Boolean.class), any(), any(), any());
        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());

        DiscoveryNodes nodes = DiscoveryNodes
            .builder()
            .add(clusterManagerNode)
            .add(localNode)
            .add(localNode1)
            .add(localNode2)
            .add(localNode3)
            .build();
        ClusterState clusterState = new ClusterState(
            new ClusterName("Local Cluster"),
            123l,
            "111111",
            null,
            null,
            nodes,
            null,
            null,
            0,
            false
        );
        when(clusterService.state()).thenReturn(clusterState);

        Instant time = Instant.now();
        mlTask = MLTask
            .builder()
            .taskId("mlTaskTaskId")
            .modelId("mlTaskModelId")
            .taskType(MLTaskType.PREDICTION)
            .functionName(FunctionName.LINEAR_REGRESSION)
            .state(MLTaskState.RUNNING)
            .inputType(MLInputDataType.DATA_FRAME)
            .workerNodes(Arrays.asList("node1"))
            .progress(0.0f)
            .outputIndex("test_index")
            .error("test_error")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .lastUpdateTime(time)
            .build();

    }

    @Ignore
    public void testConstructor() {
        assertNotNull(action);
    }

    @Ignore
    public void testNewResponses() {
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelName:version", "response");
        MLDeployModelNodeResponse response = new MLDeployModelNodeResponse(localNode, modelToDeployStatus);
        final List<MLDeployModelNodeResponse> responses = Arrays.asList(new MLDeployModelNodeResponse[] { response });
        final List<FailedNodeException> failures = new ArrayList<FailedNodeException>();
        MLDeployModelNodesResponse response1 = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response1);
    }

    @Ignore
    public void testNewRequest() {
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        assertNotNull(request);
    }

    @Ignore
    public void testNewNodeResponse() throws IOException {
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelName:version", "response");
        MLDeployModelNodeResponse response = new MLDeployModelNodeResponse(localNode, modelToDeployStatus);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLDeployModelNodeResponse response1 = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(response1);
    }

    @Ignore
    public void testNodeOperation_Success() {
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    public void testNodeOperation_Success_DifferentCoordinatingNode() {
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode1.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    public void testNodeOperation_FailToSendForwardRequest() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(4);
            listener.onResponse("ok");
            return null;
        }).when(mlModelManager).deployModel(any(), any(), any(), any(Boolean.class), any(), any(), any());
        doAnswer(invocation -> {
            TransportResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleException(new TransportException("error"));
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    public void testNodeOperation_Exception() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(4);
            listener.onFailure(new RuntimeException("Something went wrong"));
            return null;
        }).when(mlModelManager).deployModel(any(), any(), any(), any(Boolean.class), any(), any(), any());
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    public void testNodeOperation_DeployModelRuntimeException() {
        doThrow(new RuntimeException("error"))
            .when(mlModelManager)
            .deployModel(any(), any(), any(), any(Boolean.class), any(), any(), any());
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    public void testNodeOperation_MLLimitExceededException() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(4);
            listener.onFailure(new MLLimitExceededException("Limit exceeded exception"));
            return null;
        }).when(mlModelManager).deployModel(any(), any(), any(), any(Boolean.class), any(), any(), any());
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    public void testNodeOperation_ErrorMessageNotNull() {
        doThrow(new MLLimitExceededException("exceed max running task limit")).when(mlModelManager).checkAndAddRunningTask(any(), any());
        final MLDeployModelNodesRequest nodesRequest = prepareRequest(localNode.getId());
        final MLDeployModelNodeRequest request = action.newNodeRequest(nodesRequest);
        final MLDeployModelNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
    }

    @Ignore
    private MLDeployModelNodesRequest prepareRequest(String coordinatingNodeId) {
        DiscoveryNode[] nodeIds = { localNode1, localNode2, localNode3 };
        MLDeployModelInput deployModelInput = new MLDeployModelInput(
            "modelId",
            "taskId",
            "modelContentHash",
            3,
            coordinatingNodeId,
            true,
            mlTask,
            null
        );
        return new MLDeployModelNodesRequest(nodeIds, deployModelInput);
    }
}
