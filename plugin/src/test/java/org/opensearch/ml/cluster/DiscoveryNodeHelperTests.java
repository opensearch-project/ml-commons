/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.BUILT_IN_ROLES;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_EXCLUDE_NODE_NAMES;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
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
    private final String dataNode1Name = "dataNodeName1";
    private final String dataNode2Id = "dataNode2";
    private final String dataNode2Name = "dataNodeName2";
    private final String warmDataNode1Id = "warmDataNode1";
    private final String mlNode1Id = "mlNode1";
    private final String mlNode1Name = "mlNodeName1";
    private final String mlNode2Id = "mlNode2";
    private final String mlNode2Name = "mlNodeName2";
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
    private String nonExistingNodeName;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        nonExistingNodeName = randomAlphaOfLength(5);
        mockSettings(true, nonExistingNodeName);

        clusterManagerNode = new DiscoveryNode(
            clusterManagerNodeId,
            buildNewFakeTransportAddress(),
            emptyMap(),
            emptySet(),
            Version.CURRENT
        );

        dataNode1 = new DiscoveryNode(
            dataNode1Name,
            dataNode1Id,
            buildNewFakeTransportAddress(),
            emptyMap(),
            BUILT_IN_ROLES,
            Version.CURRENT
        );
        dataNode2 = new DiscoveryNode(
            dataNode1Name,
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

        mlNode1 = new DiscoveryNode(
            mlNode1Name,
            mlNode1Id,
            buildNewFakeTransportAddress(),
            emptyMap(),
            ImmutableSet.of(ML_ROLE),
            Version.CURRENT
        );
        mlNode2 = new DiscoveryNode(
            mlNode2Name,
            mlNode2Id,
            buildNewFakeTransportAddress(),
            emptyMap(),
            ImmutableSet.of(ML_ROLE),
            Version.CURRENT
        );

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

    private void mockSettings(boolean onlyRunOnMLNode, String excludedNodeName) {
        settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), onlyRunOnMLNode)
            .put(ML_COMMONS_EXCLUDE_NODE_NAMES.getKey(), excludedNodeName)
            .build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ONLY_RUN_ON_ML_NODE, ML_COMMONS_EXCLUDE_NODE_NAMES);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
    }

    public void testGetEligibleNodes_MLNode() {
        DiscoveryNode[] eligibleNodes = discoveryNodeHelper.getEligibleNodes();
        assertEquals(2, eligibleNodes.length);
        Set<String> nodeIds = new HashSet<>();
        nodeIds.addAll(Arrays.asList(eligibleNodes).stream().map(n -> n.getId()).collect(Collectors.toList()));
        assertTrue(nodeIds.contains(mlNode1.getId()));
        assertTrue(nodeIds.contains(mlNode2.getId()));
    }

    public void testGetEligibleNodes_DataNode() {
        mockSettings(false, nonExistingNodeName);
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(clusterManagerNode).add(dataNode1).add(dataNode2).add(warmDataNode1).build();
        clusterState = new ClusterState(new ClusterName(clusterName), 123l, "111111", null, null, nodes, null, null, 0, false);
        when(clusterService.state()).thenReturn(clusterState);

        DiscoveryNode[] eligibleNodes = discoveryNodeHelper.getEligibleNodes();
        assertEquals(2, eligibleNodes.length);
        assertEquals(dataNode1.getName(), eligibleNodes[0].getName());
        assertEquals(dataNode2.getName(), eligibleNodes[1].getName());
    }

    public void testGetEligibleNodes_MLNode_Excluded() {
        mockSettings(false, mlNode1.getName() + "," + mlNode2.getName());
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        DiscoveryNode[] eligibleNodes = discoveryNodeHelper.getEligibleNodes();
        assertEquals(2, eligibleNodes.length);
        assertEquals(dataNode1.getName(), eligibleNodes[0].getName());
        assertEquals(dataNode1.getName(), eligibleNodes[1].getName());
    }

    public void testFilterEligibleNodes_Null() {
        mockSettings(false, mlNode1.getName() + "," + mlNode2.getName());
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        String[] eligibleNodes = discoveryNodeHelper.filterEligibleNodes(null);
        assertNull(eligibleNodes);
    }

    public void testFilterEligibleNodes_Empty() {
        mockSettings(false, mlNode1.getName() + "," + mlNode2.getName());
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        String[] eligibleNodes = discoveryNodeHelper.filterEligibleNodes(new String[] {});
        assertEquals(0, eligibleNodes.length);
    }

    public void testFilterEligibleNodes() {
        mockSettings(true, mlNode1.getName());
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        String[] eligibleNodes = discoveryNodeHelper.filterEligibleNodes(new String[] { mlNode1Id, mlNode2Id, dataNode1Id });
        assertEquals(1, eligibleNodes.length);
        assertEquals(mlNode2Id, eligibleNodes[0]);
    }

    public void testFilterEligibleNodes_BothMLAndDataNodes() {
        mockSettings(false, mlNode1.getName());
        DiscoveryNodeHelper discoveryNodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        String[] eligibleNodes = discoveryNodeHelper.filterEligibleNodes(new String[] { mlNode1Id, mlNode2Id, dataNode1Id });
        assertEquals(2, eligibleNodes.length);
        Set<String> nodeIds = new HashSet<>();
        nodeIds.addAll(Arrays.asList(eligibleNodes));
        assertTrue(nodeIds.contains(dataNode1Id));
        assertTrue(nodeIds.contains(mlNode2Id));
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
