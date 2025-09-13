/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpToolsRemoveNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpToolsRemoveNodesResponse;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import reactor.core.publisher.Mono;

public class TransportMcpToolsRemoveOnNodesActionTests extends OpenSearchTestCase {
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

    private List<String> toRemoveTools = List.of("ListIndexTool");

    private TransportMcpToolsRemoveOnNodesAction action;

    @Mock
    private McpStatelessServerHolder mcpStatelessServerHolder;

    @Mock
    private McpStatelessAsyncServer mcpStatelessAsyncServer;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("clusterName"));
        when(clusterService.localNode().getId()).thenReturn("localNodeId");
        when(mcpStatelessServerHolder.getMcpStatelessAsyncServerInstance()).thenReturn(mcpStatelessAsyncServer);

        // Mock McpStatelessAsyncServer methods
        when(mcpStatelessAsyncServer.removeTool(anyString())).thenReturn(Mono.empty());
        when(mcpStatelessAsyncServer.addTool(any())).thenReturn(Mono.empty());
        action = new TransportMcpToolsRemoveOnNodesAction(
            transportService,
            mock(ActionFilters.class),
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            mcpStatelessServerHolder
        );
    }

    @Test
    public void testNewResponse() {
        MLMcpToolsRemoveNodesRequest nodesRequest = new MLMcpToolsRemoveNodesRequest(new String[] { "node1", "node2" }, toRemoveTools);
        DiscoveryNode discoveryNode1 = mock(DiscoveryNode.class);
        when(discoveryNode1.getId()).thenReturn("node1");

        DiscoveryNode discoveryNode2 = mock(DiscoveryNode.class);
        when(discoveryNode2.getId()).thenReturn("node2");

        List<MLMcpToolsRemoveNodeResponse> responses = List.of(new MLMcpToolsRemoveNodeResponse(discoveryNode1, true));
        List<FailedNodeException> failures = List.of(new FailedNodeException("node2", "failed", new Exception("failed")));
        MLMcpToolsRemoveNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertEquals(1, response.getNodes().size());
        assertEquals(1, response.failures().size());
    }

    @Test
    public void testNewNodeRequest() {
        MLMcpToolsRemoveNodesRequest nodesRequest = new MLMcpToolsRemoveNodesRequest(new String[] { "node1", "node2" }, toRemoveTools);
        MLMcpToolsRemoveNodeRequest nodeRequest = action.newNodeRequest(nodesRequest);
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
        MLMcpToolsRemoveNodeResponse response = new MLMcpToolsRemoveNodeResponse(node, true);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLMcpToolsRemoveNodeResponse newNodeResponse = action.newNodeResponse(output.bytes().streamInput());
        assertEquals("node1", newNodeResponse.getNode().getId());
    }

    @Test
    public void testNodeOperation() {
        MLMcpToolsRemoveNodeRequest request = new MLMcpToolsRemoveNodeRequest(toRemoveTools);
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L);
        // Mock the server instance call
        when(mcpStatelessServerHolder.getMcpStatelessAsyncServerInstance())
            .thenReturn(mock(io.modelcontextprotocol.server.McpStatelessAsyncServer.class));
        MLMcpToolsRemoveNodeResponse response = action.nodeOperation(request);
        assertEquals(true, response.getDeleted());
    }

    @Test
    public void testNodeOperation_OnError() {
        exceptionRule.expect(FailedNodeException.class);
        exceptionRule.expectMessage("[ListIndexTool] not found on node: localNodeId");

        // override the default success stub for this test
        when(mcpStatelessAsyncServer.removeTool(anyString())).thenReturn(Mono.error(new RuntimeException("not found")));

        MLMcpToolsRemoveNodeRequest request = new MLMcpToolsRemoveNodeRequest(toRemoveTools);
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L);

        action.nodeOperation(request);
    }

    @Test
    public void testNodeOperation_Success_RemovesFromMap() {
        MLMcpToolsRemoveNodeRequest request = new MLMcpToolsRemoveNodeRequest(toRemoveTools);
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("ListIndexTool", 1L);

        // Create a new mock server for this test
        McpStatelessAsyncServer testServer = mock(McpStatelessAsyncServer.class);
        when(mcpStatelessServerHolder.getMcpStatelessAsyncServerInstance()).thenReturn(testServer);

        when(testServer.removeTool(anyString())).thenReturn(Mono.empty());

        // Wait a bit for the tool to be added
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now try to remove - this should succeed and cover the doOnSuccess path
        MLMcpToolsRemoveNodeResponse response = action.nodeOperation(request);
        assertEquals(true, response.getDeleted());

        // Verify the tool was removed from IN_MEMORY_MCP_TOOLS (doOnSuccess coverage)
        assertFalse(McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.containsKey("ListIndexTool"));
    }

    @Test
    public void testNodeOperation_ErrorPath() {
        // Test the error path by creating a scenario where removeTool would fail
        // We'll use a tool name that doesn't exist in the server but exists in IN_MEMORY_MCP_TOOLS
        List<String> errorTools = List.of("NonExistentTool");
        MLMcpToolsRemoveNodeRequest request = new MLMcpToolsRemoveNodeRequest(errorTools);
        McpStatelessServerHolder.IN_MEMORY_MCP_TOOLS.put("NonExistentTool", 1L);

        // Create a new mock server for this test
        McpStatelessAsyncServer testServer = mock(McpStatelessAsyncServer.class);
        when(mcpStatelessServerHolder.getMcpStatelessAsyncServerInstance()).thenReturn(testServer);

        // Mock the removeTool method to return a Mono that errors
        when(testServer.removeTool(anyString())).thenReturn(Mono.error(new RuntimeException("Tool not found")));

        // This should trigger the onErrorResume block since the tool doesn't exist in the server
        // and eventually throw FailedNodeException
        exceptionRule.expect(FailedNodeException.class);
        action.nodeOperation(request);
    }
}
