/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodeRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodeResponse;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.transport.TransportService;

@RunWith(MockitoJUnitRunner.class)
public class UndeployControllerTransportActionTests {

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

    private UndeployControllerTransportAction action;

    private DiscoveryNode localNode;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setUp() throws Exception {
        action = new UndeployControllerTransportAction(
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
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("Local Cluster"));
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("successful");
            return null;
        }).when(mlModelManager).undeployController(any(), any());
    }

    @Test
    public void testNewResponses() {
        final MLUndeployControllerNodesRequest nodesRequest = new MLUndeployControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        Map<String, String> modelUndeployControllerStatusMap = new HashMap<>();
        modelUndeployControllerStatusMap.put("modelName:version", "response");
        MLUndeployControllerNodeResponse response = new MLUndeployControllerNodeResponse(localNode, modelUndeployControllerStatusMap);
        final List<MLUndeployControllerNodeResponse> responses = List.of(response);
        final List<FailedNodeException> failures = new ArrayList<>();
        MLUndeployControllerNodesResponse response1 = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response1);
    }

    @Test
    public void testNewNodeRequest() {
        final MLUndeployControllerNodesRequest request = new MLUndeployControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        final MLUndeployControllerNodeRequest undeployControllerNodeRequest = action.newNodeRequest(request);
        assertNotNull(undeployControllerNodeRequest);
    }

    @Test
    public void testNewNodeStreamRequest() throws IOException {
        Map<String, String> undeployControllerStatus = new HashMap<>();
        undeployControllerStatus.put("modelId1", "response");
        MLUndeployControllerNodeResponse response = new MLUndeployControllerNodeResponse(localNode, undeployControllerStatus);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLUndeployControllerNodeResponse undeployControllerNodeResponse = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(undeployControllerNodeResponse);
    }

    @Test
    public void testNodeOperation() {
        final MLUndeployControllerNodesRequest request = new MLUndeployControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        final MLUndeployControllerNodeResponse response = action.nodeOperation(new MLUndeployControllerNodeRequest(request));
        assertNotNull(response);
    }

    @Test
    public void testNodeOperationException() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(mlModelManager).undeployController(any(), any());
        final MLUndeployControllerNodesRequest request = new MLUndeployControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        final MLUndeployControllerNodeResponse response = action.nodeOperation(new MLUndeployControllerNodeRequest(request));
        assertNotNull(response);
    }
}
