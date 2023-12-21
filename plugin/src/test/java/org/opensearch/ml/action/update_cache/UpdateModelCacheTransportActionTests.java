/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.update_cache;

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
import org.opensearch.ml.common.transport.update_cache.MLUpdateModelCacheNodeRequest;
import org.opensearch.ml.common.transport.update_cache.MLUpdateModelCacheNodeResponse;
import org.opensearch.ml.common.transport.update_cache.MLUpdateModelCacheNodesRequest;
import org.opensearch.ml.common.transport.update_cache.MLUpdateModelCacheNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.transport.TransportService;

@RunWith(MockitoJUnitRunner.class)
public class UpdateModelCacheTransportActionTests {

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

    private UpdateModelCacheTransportAction action;

    private DiscoveryNode localNode;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setUp() throws Exception {
        action = new UpdateModelCacheTransportAction(
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
        }).when(mlModelManager).updateModelCache(any(), any(Boolean.class), any());
    }

    @Test
    public void testNewResponses() {
        final MLUpdateModelCacheNodesRequest nodesRequest = new MLUpdateModelCacheNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        Map<String, String> modelUpdateModelCacheStatusMap = new HashMap<>();
        modelUpdateModelCacheStatusMap.put("modelName:version", "response");
        MLUpdateModelCacheNodeResponse response = new MLUpdateModelCacheNodeResponse(localNode, modelUpdateModelCacheStatusMap);
        final List<MLUpdateModelCacheNodeResponse> responses = List.of(response);
        final List<FailedNodeException> failures = new ArrayList<>();
        MLUpdateModelCacheNodesResponse response1 = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response1);
    }

    @Test
    public void testNewNodeRequest() {
        final MLUpdateModelCacheNodesRequest request = new MLUpdateModelCacheNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        final MLUpdateModelCacheNodeRequest updateModelCacheNodeRequest = action.newNodeRequest(request);
        assertNotNull(updateModelCacheNodeRequest);
    }

    @Test
    public void testNewNodeStreamRequest() throws IOException {
        Map<String, String> updateModelCacheStatus = new HashMap<>();
        updateModelCacheStatus.put("modelId1", "response");
        MLUpdateModelCacheNodeResponse response = new MLUpdateModelCacheNodeResponse(localNode, updateModelCacheStatus);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLUpdateModelCacheNodeResponse updateModelCacheNodeResponse = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(updateModelCacheNodeResponse);
    }

    @Test
    public void testNodeOperation() {
        final MLUpdateModelCacheNodesRequest request = new MLUpdateModelCacheNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        final MLUpdateModelCacheNodeResponse response = action.nodeOperation(new MLUpdateModelCacheNodeRequest(request));
        assertNotNull(response);
    }

    @Test
    public void testNodeOperationException() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(mlModelManager).updateModelCache(any(), any(Boolean.class), any());
        final MLUpdateModelCacheNodesRequest request = new MLUpdateModelCacheNodesRequest(
            new String[] { "nodeId1", "nodeId2" },
            "testModelId",
            true
        );
        final MLUpdateModelCacheNodeResponse response = action.nodeOperation(new MLUpdateModelCacheNodeRequest(request));
        assertNotNull(response);
    }

}
