/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.Mockito.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.UpdateMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;
import org.opensearch.ml.engine.tools.SearchIndexTool;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportMcpToolsUpdateOnNodesActionTests extends OpenSearchTestCase {

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

    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("SearchIndexTool", SearchIndexTool.Factory.getInstance());

    private McpToolsHelper mcpToolsHelper;

    private TransportMcpToolsUpdateOnNodesAction action;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.clear();
        mcpToolsHelper = new McpToolsHelper(client, threadPool, toolFactoryWrapper);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("test-cluster"));
        when(clusterService.localNode().getId()).thenReturn("local-node");
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        action = new TransportMcpToolsUpdateOnNodesAction(
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
        MLMcpToolsUpdateNodesRequest nodesRequest = new MLMcpToolsUpdateNodesRequest(
                new String[]{"node1", "node2"},
                List.of(createTestTool())
        );

        DiscoveryNode node1 = createDiscoveryNode("node1");

        List<MLMcpToolsUpdateNodeResponse> responses = List.of(
                new MLMcpToolsUpdateNodeResponse(node1, true)
        );
        List<FailedNodeException> failures = List.of(
                new FailedNodeException("node2", "Update failed", new Exception())
        );

        MLMcpToolsUpdateNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertEquals(1, response.getNodes().size());
        assertEquals(1, response.failures().size());
        assertTrue(response.getNodes().get(0).getUpdated());
    }

    @Test
    public void testNewNodeRequest() {
        MLMcpToolsUpdateNodesRequest nodesRequest = new MLMcpToolsUpdateNodesRequest(
                new String[]{"node1"},
                List.of(createTestTool())
        );

        MLMcpToolsUpdateNodeRequest nodeRequest = action.newNodeRequest(nodesRequest);
        assertEquals(1, nodeRequest.getMcpTools().size());
        assertEquals("SearchIndexTool", nodeRequest.getMcpTools().get(0).getType());
    }

    @Test
    public void testNewNodeResponse() throws IOException {
        DiscoveryNode node = createDiscoveryNode("test-node");
        MLMcpToolsUpdateNodeResponse response = new MLMcpToolsUpdateNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);

        MLMcpToolsUpdateNodeResponse deserialized = action.newNodeResponse(output.bytes().streamInput());
        assertEquals("test-node", deserialized.getNode().getId());
        assertTrue(deserialized.getUpdated());
    }

    @Test
    public void testNodeOperationSuccess() {
        McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put("SearchIndexTool", 1L);

        MLMcpToolsUpdateNodeRequest request = new MLMcpToolsUpdateNodeRequest(
                List.of(createTestTool(2L))
        );

        MLMcpToolsUpdateNodeResponse response = action.nodeOperation(request);
        assertTrue(response.getUpdated());
        assertEquals(2L, (long) McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.get("SearchIndexTool"));
    }

    @Test
    public void testNodeOperationException() {
        McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put("IndexMappingTool", 1L);
        UpdateMcpTool updateMcpTool = new UpdateMcpTool(
                "IndexMappingTool",
                "Updated index mapping tool",
                Map.of("parameters", "{}"),
                Map.of("attributes", "{}"),
                null, null
        );
        updateMcpTool.setType("IndexMappingTool");
        updateMcpTool.setVersion(2L);
        MLMcpToolsUpdateNodeRequest request = new MLMcpToolsUpdateNodeRequest(
                List.of(updateMcpTool)
        );
        exceptionRule.expect(FailedNodeException.class);
        exceptionRule.expectMessage("Failed to find tool factory for tool type: IndexMappingTool");
        action.nodeOperation(request);
    }

    private UpdateMcpTool createTestTool() {
        return createTestTool(2L);
    }

    private UpdateMcpTool createTestTool(long version) {
        UpdateMcpTool updateMcpTool = new UpdateMcpTool(
                "SearchIndexTool",
                "Updated search tool",
                Map.of("parameters", "{}"),
                Map.of("attributes", "{}"),
               null, null
        );
        updateMcpTool.setType("SearchIndexTool");
        updateMcpTool.setVersion(version);
        return updateMcpTool;
    }

    private DiscoveryNode createDiscoveryNode(String nodeId) {
        return new DiscoveryNode(
                nodeId,
                nodeId,
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
    }
}
