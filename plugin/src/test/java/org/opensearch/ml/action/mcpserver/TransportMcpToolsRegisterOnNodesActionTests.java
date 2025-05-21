/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodesResponse;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

public class TransportMcpToolsRegisterOnNodesActionTests extends OpenSearchTestCase {
    @Mock
    private TransportService transportService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ToolFactoryWrapper toolFactoryWrapper;

    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("ListIndexTool", ListIndexTool.Factory.getInstance());

    private TransportMcpToolsRegisterOnNodesAction action;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        McpToolsHelper mcpToolsHelper = new McpToolsHelper(client, threadPool, clusterService, toolFactoryWrapper);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("clusterName"));
        when(clusterService.localNode().getId()).thenReturn("localNodeId");
        action = new TransportMcpToolsRegisterOnNodesAction(
            transportService,
            mock(ActionFilters.class),
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            toolFactoryWrapper,
            mcpToolsHelper
        );
    }

    @Test
    public void testNewResponse() {
        List<RegisterMcpTool> mcpTools = List
            .of(new RegisterMcpTool("ListIndexTool", "ListIndexTool", "ListIndexTool", Map.of(), Map.of(), null, null));
        MLMcpToolsRegisterNodesRequest nodesRequest = new MLMcpToolsRegisterNodesRequest(new String[] { "node1", "node2" }, mcpTools);
        DiscoveryNode discoveryNode1 = mock(DiscoveryNode.class);
        when(discoveryNode1.getId()).thenReturn("node1");

        DiscoveryNode discoveryNode2 = mock(DiscoveryNode.class);
        when(discoveryNode2.getId()).thenReturn("node2");

        List<MLMcpToolsRegisterNodeResponse> responses = List.of(new MLMcpToolsRegisterNodeResponse(discoveryNode1, true));
        List<FailedNodeException> failures = List.of(new FailedNodeException("node2", "failed", new Exception("failed")));
        MLMcpToolsRegisterNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertEquals(1, response.getNodes().size());
        assertEquals(1, response.failures().size());
    }

    @Test
    public void testNewNodeRequest() {
        List<RegisterMcpTool> mcpTools = List
            .of(new RegisterMcpTool("ListIndexTool", "ListIndexTool", "ListIndexTool", Map.of(), Map.of(), null, null));
        MLMcpToolsRegisterNodesRequest nodesRequest = new MLMcpToolsRegisterNodesRequest(new String[] { "node1", "node2" }, mcpTools);
        MLMcpToolsRegisterNodeRequest nodeRequest = action.newNodeRequest(nodesRequest);
        assertEquals(nodesRequest.getMcpTools(), nodeRequest.getMcpTools());
    }

    @Test
    public void testNewNodeResponse() throws IOException {
        DiscoveryNode node = new DiscoveryNode(
            "node1",
            "node1",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        MLMcpToolsRegisterNodeResponse response = new MLMcpToolsRegisterNodeResponse(node, true);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLMcpToolsRegisterNodeResponse newNodeResponse = action.newNodeResponse(output.bytes().streamInput());
        assertEquals("node1", newNodeResponse.getNode().getId());
    }

    @Test
    public void testNodeOperation() {
        List<RegisterMcpTool> mcpTools = List
            .of(
                new RegisterMcpTool(
                    null,
                    "ListIndexTool",
                    "OpenSearch index name list, separated by comma. for example: [\\\"index1\\\", \\\"index2\\\"], use empty array [] to list all indices in the cluster",
                    Map.of(),
                    Map
                        .of(
                            "type",
                            "object",
                            "properties",
                            Map.of("indices", Map.of("type", "array", "items", Map.of("type", "string"))),
                            "additionalProperties",
                            false
                        ), null, null
                )
            );

        MLMcpToolsRegisterNodeRequest request = new MLMcpToolsRegisterNodeRequest(mcpTools);
        MLMcpToolsRegisterNodeResponse response = action.nodeOperation(request);
        assertEquals(true, response.getCreated());
    }
}
