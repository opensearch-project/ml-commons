/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.Version;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.UpdateMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportMcpToolsUpdateActionTests extends OpenSearchTestCase {

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
    private DiscoveryNodeHelper nodeFilter;
    @Mock
    private McpToolsHelper mcpToolsHelper;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLMcpToolsUpdateNodesResponse> listener;

    private TransportMcpToolsUpdateAction action;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder()
                .put("plugins.ml_commons.mcp_server_enabled", true)
                .build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings())
                .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TestHelper.mockClientStashContext(client, settings);
        when(clusterService.state().metadata().hasIndex(MLIndex.MCP_TOOLS.getIndexName())).thenReturn(true);

        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsUpdateNodesResponse> actionListener = invocationOnMock.getArgument(2);
            List<MLMcpToolsUpdateNodeResponse> nodes = List.of(new MLMcpToolsUpdateNodeResponse(new DiscoveryNode(
                    "foo0",
                    "foo0",
                    new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                    Collections.emptyMap(),
                    Collections.singleton(CLUSTER_MANAGER_ROLE),
                    Version.CURRENT
            ), true));
            MLMcpToolsUpdateNodesResponse response = new MLMcpToolsUpdateNodesResponse(ClusterName.DEFAULT, nodes, ImmutableList.of());
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        action = new TransportMcpToolsUpdateAction(
                transportService,
                mock(ActionFilters.class),
                clusterService,
                threadPool,
                client,
                xContentRegistry,
                nodeFilter,
                mcpToolsHelper
        );
    }

    @Test
    public void testUpdateSuccess() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(true, true);
        mockBulkResponse(false);

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpToolsUpdateNodesResponse> captor = ArgumentCaptor.forClass(MLMcpToolsUpdateNodesResponse.class);
        verify(listener).onResponse(captor.capture());
        assertEquals(1, captor.getValue().getNodes().size());
    }

    @Test
    public void testFeatureDisabled() {
        Settings disabledSettings = Settings.builder()
                .put("plugins.ml_commons.mcp_server_enabled", false)
                .build();
        when(clusterService.getSettings()).thenReturn(disabledSettings);
        TransportMcpToolsUpdateAction action = new TransportMcpToolsUpdateAction(
                transportService,
                mock(ActionFilters.class),
                clusterService,
                threadPool,
                client,
                xContentRegistry,
                nodeFilter,
                mcpToolsHelper
        );
        action.doExecute(task, mock(MLMcpToolsUpdateNodesRequest.class), listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("The MCP server is not enabled. To enable, please update the setting plugins.ml_commons.mcp_server_enabled", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_IndexNotExist() {
        when(clusterService.state().metadata().hasIndex(MLIndex.MCP_TOOLS.getIndexName())).thenReturn(false);

        action.doExecute(task, mock(MLMcpToolsUpdateNodesRequest.class), listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue().getMessage().contains("MCP tools index doesn't exist"));
    }

    @Test
    public void test_doExecute_IndexSearchException() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(false, false);

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Network issue", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_allToolsNotExist() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(true, false);

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Failed to update tools as none of them is found in index", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_partialToolsNotExist() throws IOException {
        UpdateMcpTool tool1 = new UpdateMcpTool(
                "ListIndexTool",
                "Updated tool",
                Map.of("threshold", "80%"),
                Map.of("type", "object"),
                null, null
        );
        UpdateMcpTool tool2 = new UpdateMcpTool(
                "SearchIndexTool",
                "Updated tool",
                Map.of("threshold", "80%"),
                Map.of("type", "object"),
                null, null
        );
        MLMcpToolsUpdateNodesRequest request = new MLMcpToolsUpdateNodesRequest(new String[]{"mockNodeId"}, List.of(tool1, tool2));
        mockSearchResponse(true, true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Failed to find tools: [SearchIndexTool] in system index", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_bulkUpdateFailure() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(true, true);
        mockBulkResponse(true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Failed to update mcp tool: ListIndexTool in system index with error: java.lang.RuntimeException: Network issue", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_bulkUpdatePartialFailure() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(true, true);
        mockPartialBulkResponse();

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Failed to update mcp tool: ListIndexTool in system index with error: java.lang.RuntimeException: Network issue", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_allNodeUpdateFailure() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(true, true);
        mockBulkResponse(false);
        mockNodeUpdateAllFailure();

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Tools are updated successfully but failed to update to mcp server memory with error: Node update failed", captor.getValue().getMessage());
    }

    @Test
    public void test_doExecute_partialNodeUpdateFailure() throws IOException {
        MLMcpToolsUpdateNodesRequest request = createTestRequest();
        mockSearchResponse(true, true);
        mockBulkResponse(false);
        mockNodeUpdatePartialFailure();

        action.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchException> captor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Tools: [ListIndexTool] are updated successfully but failed to update to mcp server memory with error: Node update failed", captor.getValue().getMessage());
    }

    private MLMcpToolsUpdateNodesRequest createTestRequest() {
        UpdateMcpTool tool = new UpdateMcpTool(
                "ListIndexTool",
                null, null, null,
                null, null
        );
        List<UpdateMcpTool> tools = new ArrayList<>();
        tools.add(tool);
        return new MLMcpToolsUpdateNodesRequest(new String[]{"mockNodeId"}, tools);
    }

    private void mockSearchResponse(boolean success, boolean hasResult) throws IOException {
        SearchResponse searchResponse = TestHelper.createSearchResponse(getRegisterMcpTool(), 1);
        doAnswer(inv -> {
            ActionListener<SearchResponse> listener = inv.getArgument(1);
            if(success) {
                if (hasResult) {
                    listener.onResponse(searchResponse);
                } else {
                    listener.onResponse(TestHelper.createSearchResponse(null, 0));
                }
            } else {
                listener.onFailure(new OpenSearchException("Network issue"));
            }
            return null;
        }).when(mcpToolsHelper).searchToolsWithPrimaryTermAndSeqNo(any(), any());
    }

    private RegisterMcpTool getRegisterMcpTool() {
        RegisterMcpTool registerMcpTool = new RegisterMcpTool(
                "ListIndexTool",
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
        );
        registerMcpTool.setVersion(1L);
        return registerMcpTool;
    }

    private void mockPartialBulkResponse() {
        doAnswer(inv -> {
            ActionListener<BulkResponse> listener = inv.getArgument(1);
            BulkResponse bulkResponse = mock(BulkResponse.class);
            when(bulkResponse.hasFailures()).thenReturn(true);
            BulkItemResponse[] responses = new BulkItemResponse[2];
            BulkItemResponse succeedBulkItemResponse = createSucceedBulkItemResponse();
            BulkItemResponse failedBulkItemResponse = createFailedBulkItemResponse();
            responses[0] = succeedBulkItemResponse;
            responses[1] = failedBulkItemResponse;
            when(bulkResponse.getItems()).thenReturn(responses);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());
    }

    private void mockBulkResponse(boolean hasFailures) {
        doAnswer(inv -> {
            ActionListener<BulkResponse> listener = inv.getArgument(1);
            BulkResponse bulkResponse = mock(BulkResponse.class);
            when(bulkResponse.hasFailures()).thenReturn(hasFailures);
            BulkItemResponse[] responses = new BulkItemResponse[1];
            BulkItemResponse bulkItemResponse = createFailedBulkItemResponse();
            responses[0] = bulkItemResponse;
            when(bulkResponse.getItems()).thenReturn(responses);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());
    }

    private BulkItemResponse createSucceedBulkItemResponse() {
        BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
        when(bulkItemResponse.getId()).thenReturn("ListIndexTool");
        when(bulkItemResponse.isFailed()).thenReturn(false);
        DocWriteResponse docWriteResponse = mock(DocWriteResponse.class);
        when(docWriteResponse.getVersion()).thenReturn(2L);
        when(bulkItemResponse.getResponse()).thenReturn(docWriteResponse);
        return bulkItemResponse;
    }

    private BulkItemResponse createFailedBulkItemResponse() {
        BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
        when(bulkItemResponse.getId()).thenReturn("ListIndexTool");
        when(bulkItemResponse.isFailed()).thenReturn(true);
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure("mock_index", "ListIndexTool", new RuntimeException("Network issue"));
        when(bulkItemResponse.getFailure()).thenReturn(failure);
        return bulkItemResponse;
    }

    private void mockNodeUpdateAllFailure() {
        doAnswer(inv -> {
            ActionListener<MLMcpToolsUpdateNodesResponse> listener = inv.getArgument(2);
            listener.onFailure(new OpenSearchException("Node update failed"));
            return null;
        }).when(client).execute(any(), any(), any());
    }

    private void mockNodeUpdatePartialFailure() {
        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsUpdateNodesResponse> actionListener = invocationOnMock.getArgument(2);
            List<FailedNodeException> failures = List.of(new FailedNodeException("mockNodeId", "Node update failed", new RuntimeException("Node update failed")));
            MLMcpToolsUpdateNodesResponse response = new MLMcpToolsUpdateNodesResponse(ClusterName.DEFAULT, ImmutableList.of(), failures);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
    }
}
