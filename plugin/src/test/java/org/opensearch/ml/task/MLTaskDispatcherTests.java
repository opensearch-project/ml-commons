/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.breaker.MemoryCircuitBreaker.DEFAULT_JVM_HEAP_USAGE_THRESHOLD;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_ROLE_NAME;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.action.stats.MLStatsNodesResponse;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.test.OpenSearchTestCase;

public class MLTaskDispatcherTests extends OpenSearchTestCase {

    @Mock
    ClusterService clusterService;

    @Mock
    Client client;

    @Mock
    ActionListener<DiscoveryNode> listener;
    @Mock
    DiscoveryNodeHelper nodeHelper;

    MLTaskDispatcher taskDispatcher;
    ClusterState testState;
    DiscoveryNode dataNode1;
    DiscoveryNode dataNode2;
    DiscoveryNode mlNode;
    MLStatsNodesResponse mlStatsNodesResponse;
    String clusterName = "test cluster";
    Settings settings;

    @Before
    public void setup() {
        settings = Settings.builder().put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), false).build();
        MockitoAnnotations.openMocks(this);

        taskDispatcher = spy(new MLTaskDispatcher(clusterService, client, settings, nodeHelper));
        nodeHelper = spy(new DiscoveryNodeHelper(clusterService, settings));

        Set<DiscoveryNodeRole> dataRoleSet = Set.of(DiscoveryNodeRole.DATA_ROLE);
        dataNode1 = new DiscoveryNode("node1", buildNewFakeTransportAddress(), new HashMap<>(), dataRoleSet, Version.CURRENT);
        dataNode2 = new DiscoveryNode("node2", buildNewFakeTransportAddress(), new HashMap<>(), dataRoleSet, Version.CURRENT);
        Set<DiscoveryNodeRole> mlRoleSet = Set.of(ML_ROLE);
        mlNode = new DiscoveryNode("mlNode", buildNewFakeTransportAddress(), new HashMap<>(), mlRoleSet, Version.CURRENT);
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(dataNode1).add(dataNode2).build();
        testState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, Map.of(), 0, false);
        when(clusterService.state()).thenReturn(testState);

        doAnswer(invocation -> {
            ActionListener<MLStatsNodesResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mlStatsNodesResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        mlStatsNodesResponse = getMlStatsNodesResponse();
    }

    @Ignore
    public void testDispatchTask_Success() {
        taskDispatcher.dispatch(FunctionName.REMOTE, listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        verify(listener).onResponse(any());
    }

    @Ignore
    public void testDispatchTask_NullPointerException() {
        mlStatsNodesResponse = getNodesResponse_NoTaskCounts();
        taskDispatcher.dispatch(FunctionName.REMOTE, listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        verify(listener).onFailure(any(NullPointerException.class));
    }

    @Ignore
    public void testDispatchTask_MemoryExceedLimit() {
        mlStatsNodesResponse = getNodesResponse_MemoryExceedLimits();
        taskDispatcher.dispatch(FunctionName.REMOTE, listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        String errorMessage = "All nodes' memory usage exceeds limitation "
            + DEFAULT_JVM_HEAP_USAGE_THRESHOLD
            + ". No eligible node available to run ml jobs ";
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void testDispatchTask_TaskCountExceedLimit() {
        mlStatsNodesResponse = getNodesResponse_TaskCountExceedLimits();
        taskDispatcher.dispatch(FunctionName.REMOTE, listener);
        verify(client).execute(any(MLStatsNodesAction.class), any(MLStatsNodesRequest.class), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        String errorMessage = "All nodes' executing ML task count reach limitation.";
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    @Ignore
    public void testGetEligibleNodes_DataNodeOnly() {
        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(FunctionName.REMOTE);
        assertEquals(2, eligibleNodes.length);
        for (DiscoveryNode node : eligibleNodes) {
            assertTrue(node.isDataNode());
        }
    }

    @Ignore
    public void testGetEligibleNodes_MlAndDataNodes() {
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(dataNode1).add(dataNode2).add(mlNode).build();
        testState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, Map.of(), 0, false);
        when(clusterService.state()).thenReturn(testState);

        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(FunctionName.REMOTE);
        assertEquals(1, eligibleNodes.length);
        for (DiscoveryNode node : eligibleNodes) {
            assertFalse(node.isDataNode());
            DiscoveryNodeRole[] discoveryNodeRoles = node.getRoles().toArray(new DiscoveryNodeRole[0]);
            assertEquals(ML_ROLE_NAME, discoveryNodeRoles[0].roleName());
        }
    }

    private MLStatsNodesResponse getMlStatsNodesResponse() {
        Map<MLNodeLevelStat, Object> nodeStats = new HashMap<>();
        nodeStats.put(MLNodeLevelStat.ML_JVM_HEAP_USAGE, 50l);
        nodeStats.put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, 5l);
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
        nodeStats.put(MLNodeLevelStat.ML_JVM_HEAP_USAGE, 50l);
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
        nodeStats.put(MLNodeLevelStat.ML_JVM_HEAP_USAGE, 90l);
        nodeStats.put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, 5l);
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
        nodeStats.put(MLNodeLevelStat.ML_JVM_HEAP_USAGE, 50l);
        nodeStats.put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, 15l);
        MLStatsNodeResponse mlStatsNodeResponse1 = new MLStatsNodeResponse(dataNode1, nodeStats);
        MLStatsNodeResponse mlStatsNodeResponse2 = new MLStatsNodeResponse(dataNode1, nodeStats);
        return new MLStatsNodesResponse(
            new ClusterName(clusterName),
            Arrays.asList(mlStatsNodeResponse1, mlStatsNodeResponse2),
            new ArrayList<>()
        );
    }
}
