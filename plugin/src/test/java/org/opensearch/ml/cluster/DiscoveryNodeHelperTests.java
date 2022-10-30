/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.BUILT_IN_ROLES;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.Set;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class DiscoveryNodeHelperTests extends OpenSearchTestCase {
    private final String clusterManagerNodeId = "clusterManagerNode";
    private final String dataNode1Id = "dataNode1";
    private final String dataNode2Id = "dataNode2";
    private final String warmDataNode1Id = "warmDataNode1";
    private final String mlNode1Id = "mlNode1";
    private final String mlNode2Id = "mlNode2";
    private final String clusterName = "multi-node-cluster";

    @Mock
    private ClusterService clusterService;
    private Settings settings;
    private DiscoveryNodeHelper discoveryNodeHelper;

    private DiscoveryNode clusterManagerNode;
    private DiscoveryNode dataNode1;
    private DiscoveryNode dataNode2;
    private DiscoveryNode warmDataNode1;
    private DiscoveryNode mlNode1;
    private DiscoveryNode mlNode2;
    private ClusterState clusterState;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mockSettings(true);

        clusterManagerNode = new DiscoveryNode(
            clusterManagerNodeId,
            buildNewFakeTransportAddress(),
            emptyMap(),
            emptySet(),
            Version.CURRENT
        );

        dataNode1 = new DiscoveryNode(dataNode1Id, buildNewFakeTransportAddress(), emptyMap(), BUILT_IN_ROLES, Version.CURRENT);
        dataNode2 = new DiscoveryNode(
            dataNode2Id,
            buildNewFakeTransportAddress(),
            ImmutableMap.of(CommonValue.BOX_TYPE_KEY, CommonValue.HOT_BOX_TYPE),
            BUILT_IN_ROLES,
            Version.CURRENT
        );
        warmDataNode1 = new DiscoveryNode(
            warmDataNode1Id,
            buildNewFakeTransportAddress(),
            ImmutableMap.of(CommonValue.BOX_TYPE_KEY, CommonValue.WARM_BOX_TYPE),
            BUILT_IN_ROLES,
            Version.CURRENT
        );

        DiscoveryNodeRole mlRole = new MLDiscoveryNodeRole();
        mlNode1 = new DiscoveryNode(mlNode1Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(mlRole), Version.CURRENT);
        mlNode2 = new DiscoveryNode(mlNode2Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(mlRole), Version.CURRENT);

        DiscoveryNodes nodes = DiscoveryNodes
            .builder()
            .add(clusterManagerNode)
            .add(dataNode1)
            .add(dataNode2)
            .add(warmDataNode1)
            .add(mlNode1)
            .add(mlNode2)
            .build();
        clusterState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, null, 0, false);

        when(clusterService.state()).thenReturn(clusterState);
        discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
    }

    private void mockSettings(boolean onlyRunOnMLNode) {
        settings = Settings.builder().put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), onlyRunOnMLNode).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ONLY_RUN_ON_ML_NODE);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
    }

    public void testGetEligibleNodes_MLNode() {
        DiscoveryNode[] eligibleNodes = discoveryNodeHelper.getEligibleNodes();
        assertEquals(2, eligibleNodes.length);
        assertEquals(mlNode1.getName(), eligibleNodes[0].getName());
        assertEquals(mlNode2.getName(), eligibleNodes[1].getName());
    }

    public void testGetEligibleNodes_DataNode() {
        mockSettings(false);
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(clusterManagerNode).add(dataNode1).add(dataNode2).add(warmDataNode1).build();
        clusterState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, null, 0, false);
        when(clusterService.state()).thenReturn(clusterState);

        DiscoveryNode[] eligibleNodes = discoveryNodeHelper.getEligibleNodes();
        assertEquals(2, eligibleNodes.length);
        assertEquals(mlNode1.getName(), eligibleNodes[0].getName());
        assertEquals(mlNode2.getName(), eligibleNodes[1].getName());
    }

    public void testGetAllNodeIds() {
        String[] allNodeIds = discoveryNodeHelper.getAllNodeIds();
        assertEquals(6, allNodeIds.length);
    }

    public void testGetNodes() {
        DiscoveryNode[] nodes = discoveryNodeHelper.getNodes(new String[] { mlNode1Id, mlNode2Id });
        assertEquals(2, nodes.length);
        Set<String> nodeIds = ImmutableSet.of(nodes[0].getId(), nodes[1].getId());
        assertTrue(nodeIds.contains(mlNode1Id));
        assertTrue(nodeIds.contains(mlNode2Id));
    }

    public void testGetNodeIds() {
        String[] nodeIds = discoveryNodeHelper.getNodeIds(new DiscoveryNode[] { mlNode1, mlNode2 });
        assertArrayEquals(new String[] { mlNode1Id, mlNode2Id }, nodeIds);
    }

    public void testGetNode() {
        DiscoveryNode node = discoveryNodeHelper.getNode(mlNode1Id);
        assertEquals(mlNode1, node);

        node = discoveryNodeHelper.getNode(randomAlphaOfLength(10));
        assertNull(node);
    }
}
