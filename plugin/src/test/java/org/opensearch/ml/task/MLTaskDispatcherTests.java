/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.breaker.MemoryCircuitBreaker.DEFAULT_JVM_HEAP_USAGE_THRESHOLD;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_ROLE_NAME;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.action.stats.MLStatsNodesResponse;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableSet;

public class MLTaskDispatcherTests extends OpenSearchTestCase {

    @Mock
    ClusterService clusterService;

    @Mock
    Client client;

    @Mock
    ActionListener<DiscoveryNode> listener;

    MLTaskDispatcher taskDispatcher;
    ClusterState testState;
    DiscoveryNode dataNode1;
    DiscoveryNode dataNode2;
    DiscoveryNode mlNode;
    MLStatsNodesResponse mlStatsNodesResponse;
    String clusterName = "test cluster";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        taskDispatcher = spy(new MLTaskDispatcher(clusterService, client));

        Set<DiscoveryNodeRole> dataRoleSet = ImmutableSet.of(DiscoveryNodeRole.DATA_ROLE);
        dataNode1 = new DiscoveryNode("node1", buildNewFakeTransportAddress(), new HashMap<>(), dataRoleSet, Version.CURRENT);
        dataNode2 = new DiscoveryNode("node2", buildNewFakeTransportAddress(), new HashMap<>(), dataRoleSet, Version.CURRENT);
        Set<DiscoveryNodeRole> mlRoleSet = ImmutableSet.of(ML_ROLE);
        mlNode = new DiscoveryNode("mlNode", buildNewFakeTransportAddress(), new HashMap<>(), mlRoleSet, Version.CURRENT);
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(dataNode1).add(dataNode2).build();
        testState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, null, 0, false);
        when(clusterService.state()).thenReturn(testState);

        doAnswer(invocation -> {
            ActionListener<MLStatsNodesResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mlStatsNodesResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        mlStatsNodesResponse = getMlStatsNodesResponse();
    }

    public void testDispatchTask_Success() {
        taskDispatcher.dispatchTask(listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        verify(listener).onResponse(any());
    }

    public void testDispatchTask_NullPointerException() {
        mlStatsNodesResponse = getNodesResponse_NoTaskCounts();
        taskDispatcher.dispatchTask(listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        verify(listener).onFailure(any(NullPointerException.class));
    }

    public void testDispatchTask_MemoryExceedLimit() {
        mlStatsNodesResponse = getNodesResponse_MemoryExceedLimits();
        taskDispatcher.dispatchTask(listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        String errorMessage = "All nodes' memory usage exceeds limitation "
            + DEFAULT_JVM_HEAP_USAGE_THRESHOLD
            + ". No eligible node available to run ml jobs ";
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testDispatchTask_TaskCountExceedLimit() {
        mlStatsNodesResponse = getNodesResponse_TaskCountExceedLimits();
        taskDispatcher.dispatchTask(listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        String errorMessage = "All nodes' executing ML task count reach limitation.";
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testGetEligibleNodes_DataNodeOnly() {
        DiscoveryNode[] eligibleNodes = taskDispatcher.getEligibleNodes();
        assertEquals(2, eligibleNodes.length);
        for (DiscoveryNode node : eligibleNodes) {
            assertTrue(node.isDataNode());
        }
    }

    public void testGetEligibleNodes_MlAndDataNodes() {
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(dataNode1).add(dataNode2).add(mlNode).build();
        testState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, null, 0, false);
        when(clusterService.state()).thenReturn(testState);

        DiscoveryNode[] eligibleNodes = taskDispatcher.getEligibleNodes();
        assertEquals(1, eligibleNodes.length);
        for (DiscoveryNode node : eligibleNodes) {
            assertFalse(node.isDataNode());
            DiscoveryNodeRole[] discoveryNodeRoles = node.getRoles().toArray(new DiscoveryNodeRole[0]);
            assertEquals(ML_ROLE_NAME, discoveryNodeRoles[0].roleName());
        }
    }

    private MLStatsNodesResponse getMlStatsNodesResponse() {
        Map<MLNodeLevelStat, Object> nodeStats = new HashMap<>();
        nodeStats.put(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE, 50l);
        nodeStats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, 5l);
        MLStatsNodeResponse mlStatsNodeResponse1 = new MLStatsNodeResponse(dataNode1, nodeStats);
        MLStatsNodeResponse mlStatsNodeResponse2 = new MLStatsNodeResponse(dataNode1, nodeStats);
        return new MLStatsNodesResponse(
            new ClusterName(clusterName),
            Arrays.asList(mlStatsNodeResponse1, mlStatsNodeResponse2),
            new ArrayList<>()
        );
    }

    private MLStatsNodesResponse getNodesResponse_NoTaskCounts() {
        Map<MLNodeLevelStat, Object> nodeStats = new HashMap<>();
        nodeStats.put(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE, 50l);
        MLStatsNodeResponse mlStatsNodeResponse1 = new MLStatsNodeResponse(dataNode1, nodeStats);
        MLStatsNodeResponse mlStatsNodeResponse2 = new MLStatsNodeResponse(dataNode1, nodeStats);
        return new MLStatsNodesResponse(
            new ClusterName(clusterName),
            Arrays.asList(mlStatsNodeResponse1, mlStatsNodeResponse2),
            new ArrayList<>()
        );
    }

    private MLStatsNodesResponse getNodesResponse_MemoryExceedLimits() {
        Map<MLNodeLevelStat, Object> nodeStats = new HashMap<>();
        nodeStats.put(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE, 90l);
        nodeStats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, 5l);
        MLStatsNodeResponse mlStatsNodeResponse1 = new MLStatsNodeResponse(dataNode1, nodeStats);
        MLStatsNodeResponse mlStatsNodeResponse2 = new MLStatsNodeResponse(dataNode1, nodeStats);
        return new MLStatsNodesResponse(
            new ClusterName(clusterName),
            Arrays.asList(mlStatsNodeResponse1, mlStatsNodeResponse2),
            new ArrayList<>()
        );
    }

    private MLStatsNodesResponse getNodesResponse_TaskCountExceedLimits() {
        Map<MLNodeLevelStat, Object> nodeStats = new HashMap<>();
        nodeStats.put(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE, 50l);
        nodeStats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, 15l);
        MLStatsNodeResponse mlStatsNodeResponse1 = new MLStatsNodeResponse(dataNode1, nodeStats);
        MLStatsNodeResponse mlStatsNodeResponse2 = new MLStatsNodeResponse(dataNode1, nodeStats);
        return new MLStatsNodesResponse(
            new ClusterName(clusterName),
            Arrays.asList(mlStatsNodeResponse1, mlStatsNodeResponse2),
            new ArrayList<>()
        );
    }
}
