/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

public class MLUndeployModelsResponseTest {

    MLUndeployModelNodesResponse undeployModelNodesResponse;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        ClusterName clusterName = new ClusterName("clusterName");
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "response");
        DiscoveryNode localNode = new DiscoveryNode(
                "test_node_name",
                "test_node_id",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        Map<String, String[]> modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[]{"node"});
        MLUndeployModelNodeResponse nodeResponse = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        List<MLUndeployModelNodeResponse> nodes = Arrays.asList(nodeResponse);

        List<FailedNodeException> failures = Arrays.asList();
        undeployModelNodesResponse = new MLUndeployModelNodesResponse(clusterName, nodes, failures);

    }

    @Test
    public void writeTo_Success() throws IOException {
        MLUndeployModelsResponse undeployModelsResponse = new MLUndeployModelsResponse(undeployModelNodesResponse);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        undeployModelsResponse.writeTo(bytesStreamOutput);
        MLUndeployModelsResponse parsedResponse = new MLUndeployModelsResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(1, parsedResponse.getResponse().getNodes().size());
        assertEquals("test_node_id", parsedResponse.getResponse().getNodes().get(0).getNode().getId());
    }

    @Test
    public void toXContent() throws IOException {
        MLUndeployModelsResponse undeployModelsResponse = new MLUndeployModelsResponse(undeployModelNodesResponse);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        undeployModelsResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"test_node_id\":{\"stats\":{\"modelId1\":\"response\"}}}", jsonStr);
    }

    @Test
    public void fromActionResponse_Success() {
        MLUndeployModelsResponse undeployModelsResponse = new MLUndeployModelsResponse(undeployModelNodesResponse);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                undeployModelsResponse.writeTo(out);
            }
        };
        MLUndeployModelsResponse parsedResponse = MLUndeployModelsResponse.fromActionResponse(actionResponse);
        assertNotSame(undeployModelsResponse, parsedResponse);
        assertEquals(1, parsedResponse.getResponse().getNodes().size());
        assertEquals("test_node_id", parsedResponse.getResponse().getNodes().get(0).getNode().getId());
    }

    @Test
    public void fromActionResponse_Success_MLUndeployModelsResponse() {
        MLUndeployModelsResponse undeployModelsResponse = new MLUndeployModelsResponse(undeployModelNodesResponse);
        MLUndeployModelsResponse parsedResponse = MLUndeployModelsResponse.fromActionResponse(undeployModelsResponse);
        assertSame(undeployModelsResponse, parsedResponse);
    }

    @Test
    public void fromActionResponse_Exception() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionResponse into MLUndeployModelsResponse");
        MLUndeployModelsResponse undeployModelsResponse = new MLUndeployModelsResponse(undeployModelNodesResponse);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLUndeployModelsResponse.fromActionResponse(actionResponse);
    }
}
