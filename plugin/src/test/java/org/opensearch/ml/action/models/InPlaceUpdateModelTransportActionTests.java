/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

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
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodeRequest;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodeResponse;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodesRequest;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.transport.TransportService;

@RunWith(MockitoJUnitRunner.class)
public class InPlaceUpdateModelTransportActionTests {

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

    private InPlaceUpdateModelTransportAction action;

    private DiscoveryNode localNode;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setUp() throws Exception {
        action = new InPlaceUpdateModelTransportAction(
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
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onResponse("successful");
            return null;
        }).when(mlModelManager).inplaceUpdateModel(any(), any(Boolean.class), any());
    }

    @Test
    public void testNewResponses() {
        final MLInPlaceUpdateModelNodesRequest nodesRequest = new MLInPlaceUpdateModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        Map<String, String> modelInPlaceUpdateStatusMap = new HashMap<>();
        modelInPlaceUpdateStatusMap.put("modelName:version", "response");
        MLInPlaceUpdateModelNodeResponse response = new MLInPlaceUpdateModelNodeResponse(localNode, modelInPlaceUpdateStatusMap);
        final List<MLInPlaceUpdateModelNodeResponse> responses = List.of(response);
        final List<FailedNodeException> failures = new ArrayList<>();
        MLInPlaceUpdateModelNodesResponse response1 = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response1);
    }

    @Test
    public void testNewNodeRequest() {
        final MLInPlaceUpdateModelNodesRequest request = new MLInPlaceUpdateModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        final MLInPlaceUpdateModelNodeRequest inPlaceUpdateModelNodeRequest = action.newNodeRequest(request);
        assertNotNull(inPlaceUpdateModelNodeRequest);
    }

    @Test
    public void testNewNodeStreamRequest() throws IOException {
        Map<String, String> inPlaceUpdateModelStatus = new HashMap<>();
        inPlaceUpdateModelStatus.put("modelId1", "response");
        MLInPlaceUpdateModelNodeResponse response = new MLInPlaceUpdateModelNodeResponse(localNode, inPlaceUpdateModelStatus);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLInPlaceUpdateModelNodeResponse inPlaceUpdateModelNodeResponse = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(inPlaceUpdateModelNodeResponse);
    }

    @Test
    public void testNodeOperation() {
        final MLInPlaceUpdateModelNodesRequest request = new MLInPlaceUpdateModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        final MLInPlaceUpdateModelNodeResponse response = action.nodeOperation(new MLInPlaceUpdateModelNodeRequest(request));
        assertNotNull(response);
    }

    @Test
    public void testNodeOperationException() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(mlModelManager).inplaceUpdateModel(any(), any(Boolean.class), any());
        final MLInPlaceUpdateModelNodesRequest request = new MLInPlaceUpdateModelNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        final MLInPlaceUpdateModelNodeResponse response = action.nodeOperation(new MLInPlaceUpdateModelNodeRequest(request));
        assertNotNull(response);
    }

}
