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
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
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
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodeRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodeResponse;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

@Ignore
public class UndeployModelControllerTransportActionTests extends OpenSearchTestCase {

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

    private UndeployModelControllerTransportAction action;

    private DiscoveryNode localNode;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setUp() throws Exception {
        action = new UndeployModelControllerTransportAction(
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
        }).when(mlModelManager).undeployModelController(any(), any());
    }

    @Test
    public void testNewResponses() {
        final MLUndeployModelControllerNodesRequest nodesRequest = new MLUndeployModelControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        Map<String, String> modelUndeployModelControllerStatusMap = new HashMap<>();
        modelUndeployModelControllerStatusMap.put("modelName:version", "response");
        MLUndeployModelControllerNodeResponse response = new MLUndeployModelControllerNodeResponse(
            localNode,
            modelUndeployModelControllerStatusMap
        );
        final List<MLUndeployModelControllerNodeResponse> responses = List.of(response);
        final List<FailedNodeException> failures = new ArrayList<>();
        MLUndeployModelControllerNodesResponse response1 = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response1);
    }

    @Test
    public void testNewNodeRequest() {
        final MLUndeployModelControllerNodesRequest request = new MLUndeployModelControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        final MLUndeployModelControllerNodeRequest undeployModelControllerNodeRequest = action.newNodeRequest(request);
        assertNotNull(undeployModelControllerNodeRequest);
    }

    @Test
    public void testNewNodeStreamRequest() throws IOException {
        Map<String, String> undeployModelControllerStatus = new HashMap<>();
        undeployModelControllerStatus.put("modelId1", "response");
        MLUndeployModelControllerNodeResponse response = new MLUndeployModelControllerNodeResponse(
            localNode,
            undeployModelControllerStatus
        );
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLUndeployModelControllerNodeResponse undeployModelControllerNodeResponse = action
            .newNodeResponse(output.bytes().streamInput());
        assertNotNull(undeployModelControllerNodeResponse);
    }

    @Test
    public void testNodeOperation() {
        final MLUndeployModelControllerNodesRequest request = new MLUndeployModelControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        final MLUndeployModelControllerNodeResponse response = action.nodeOperation(new MLUndeployModelControllerNodeRequest(request));
        assertNotNull(response);
    }

    @Test
    public void testNodeOperationException() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(mlModelManager).undeployModelController(any(), any());
        final MLUndeployModelControllerNodesRequest request = new MLUndeployModelControllerNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId"
        );
        final MLUndeployModelControllerNodeResponse response = action.nodeOperation(new MLUndeployModelControllerNodeRequest(request));
        assertNotNull(response);
    }
}
