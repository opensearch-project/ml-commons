package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
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
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpToolsRemoveNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpToolsRemoveNodesResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TransportMcpToolsRemoveActionTests extends OpenSearchTestCase {

    @Mock
    TransportService transportService;
    @Mock
    ClusterService clusterService;
    @Mock
    ThreadPool threadPool;
    @Mock
    Client client;
    @Mock
    NamedXContentRegistry xContentRegistry;
    @Mock
    ActionFilters actionFilters;
    @Mock
    DiscoveryNodeHelper nodeFilter;
    @Mock
    private MLIndicesHandler mlIndicesHandler;

    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("ListIndexTool", ListIndexTool.Factory.getInstance());
    @Mock
    private ToolFactoryWrapper toolFactoryWrapper;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLMcpToolsRemoveNodesResponse> listener;

    private McpToolsHelper mcpToolsHelper = spy(new McpToolsHelper(client, threadPool, toolFactoryWrapper));

    private TransportMcpToolsRemoveAction transportMcpToolsRemoveAction;

    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TestHelper.mockClientStashContext(client, settings);
        when(toolFactoryWrapper.getToolsFactories()).thenReturn(toolFactories);
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onResponse(getRegisterMcpTools());
            return null;
        }).when(mcpToolsHelper).searchToolsWithVersion(isA(List.class), isA(ActionListener.class));
        doAnswer(invocationOnMock -> {
            ActionListener<BulkResponse> actionListener = invocationOnMock.getArgument(1);
            BulkResponse bulkResponse = mock(BulkResponse.class);
            BulkItemResponse[] responses = new BulkItemResponse[1];
            BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
            when(bulkItemResponse.getId()).thenReturn("ListIndexTool");
            when(bulkItemResponse.isFailed()).thenReturn(false);
            DocWriteResponse docWriteResponse = mock(DocWriteResponse.class);
            when(docWriteResponse.getVersion()).thenReturn(1L);
            when(bulkItemResponse.getResponse()).thenReturn(docWriteResponse);
            responses[0] = bulkItemResponse;
            when(bulkResponse.getItems()).thenReturn(responses);
            actionListener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), isA(ActionListener.class));
        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsRemoveNodesResponse> actionListener = invocationOnMock.getArgument(2);
            List<MLMcpToolsRemoveNodeResponse> nodes = List
                .of(
                    new MLMcpToolsRemoveNodeResponse(
                        new DiscoveryNode(
                            "foo0",
                            "foo0",
                            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                            Collections.emptyMap(),
                            Collections.singleton(CLUSTER_MANAGER_ROLE),
                            Version.CURRENT
                        ),
                        true
                    )
                );
            MLMcpToolsRemoveNodesResponse response = new MLMcpToolsRemoveNodesResponse(ClusterName.DEFAULT, nodes, ImmutableList.of());
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        transportMcpToolsRemoveAction = new TransportMcpToolsRemoveAction(
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            nodeFilter,
            mcpToolsHelper
        );
    }

    public void test_doExecute_success() {
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<MLMcpToolsRemoveNodesResponse> argumentCaptor = ArgumentCaptor.forClass(MLMcpToolsRemoveNodesResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().getNodes().size());
    }

    public void test_doExecute_featureFlagDisabled() {
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), false).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TransportMcpToolsRemoveAction mcpToolsRemoveAction = new TransportMcpToolsRemoveAction(
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            nodeFilter,
            mcpToolsHelper
        );
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        mcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The MCP server is not enabled. To enable, please update the setting plugins.ml_commons.mcp_server_enabled",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_clientThreadContextException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_failedToSearchIndex() {
        doAnswer(invocationOnMock -> {
            ActionListener<List<String>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onFailure(new RuntimeException("Network issue"));
            return null;
        }).when(mcpToolsHelper).searchToolsWithVersion(isA(List.class), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_allToolsNotExists() {
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onResponse(List.of());
            return null;
        }).when(mcpToolsHelper).searchToolsWithVersion(isA(List.class), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Unable to remove tools as no tool in the request found in system index", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_partialToolsNotExists() {
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onResponse(getRegisterMcpTools());
            return null;
        }).when(mcpToolsHelper).searchToolsWithVersion(isA(List.class), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        mcpTools.add("SearchIndexTool");
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Unable to remove tools as these tools: [SearchIndexTool] are not found in system index",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_bulkIndexAllFailed() {
        doAnswer(invocationOnMock -> {
            ActionListener<BulkResponse> actionListener = invocationOnMock.getArgument(1);
            BulkResponse bulkResponse = mock(BulkResponse.class);
            BulkItemResponse[] responses = new BulkItemResponse[1];
            BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
            when(bulkItemResponse.isFailed()).thenReturn(true);
            BulkItemResponse.Failure failure = new BulkItemResponse.Failure(
                "mock_index",
                "ListIndexTool",
                new RuntimeException("Network issue")
            );
            when(bulkItemResponse.getFailure()).thenReturn(failure);
            when(bulkItemResponse.getId()).thenReturn("ListIndexTool");
            responses[0] = bulkItemResponse;
            when(bulkResponse.getItems()).thenReturn(responses);
            when(bulkResponse.hasFailures()).thenReturn(true);
            actionListener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to remove tool: ListIndexTool from index with error: java.lang.RuntimeException: Network issue",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_bulkIndexPartialFailed() {
        doAnswer(invocationOnMock -> {
            ActionListener<BulkResponse> actionListener = invocationOnMock.getArgument(1);
            BulkResponse bulkResponse = mock(BulkResponse.class);
            BulkItemResponse[] responses = new BulkItemResponse[2];

            BulkItemResponse failedItem = mock(BulkItemResponse.class);
            when(failedItem.isFailed()).thenReturn(true);
            BulkItemResponse.Failure failure = new BulkItemResponse.Failure(
                "mock_index",
                "SearchIndexTool",
                new RuntimeException("Network issue")
            );
            when(failedItem.getFailure()).thenReturn(failure);
            when(failedItem.getId()).thenReturn("SearchIndexTool");

            BulkItemResponse succeedItem = mock(BulkItemResponse.class);
            when(succeedItem.isFailed()).thenReturn(false);
            DocWriteResponse docWriteResponse = mock(DocWriteResponse.class);
            when(docWriteResponse.getVersion()).thenReturn(1L);
            when(succeedItem.getResponse()).thenReturn(docWriteResponse);
            when(succeedItem.getId()).thenReturn("ListIndexTool");

            responses[0] = failedItem;
            responses[1] = succeedItem;
            when(bulkResponse.getItems()).thenReturn(responses);
            when(bulkResponse.hasFailures()).thenReturn(true);
            actionListener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to remove tool: SearchIndexTool from index with error: java.lang.RuntimeException: Network issue",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_removeOnNodeHasFailure() {
        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsRemoveNodesResponse> actionListener = invocationOnMock.getArgument(2);
            List<FailedNodeException> failures = List
                .of(new FailedNodeException("mockNodeId", "Network issue", new RuntimeException("Network issue")));
            MLMcpToolsRemoveNodesResponse response = new MLMcpToolsRemoveNodesResponse(ClusterName.DEFAULT, ImmutableList.of(), failures);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Tools: [ListIndexTool] are removed successfully in index but failed to remove from mcp server in memory with error: Network issue",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_removeOnNodeException() {
        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsRemoveNodesResponse> actionListener = invocationOnMock.getArgument(2);
            actionListener.onFailure(new RuntimeException("Serialization failure"));
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLMcpToolsRemoveNodesRequest nodesRequest = mock(MLMcpToolsRemoveNodesRequest.class);
        List<String> mcpTools = getRemoveMcpTool();
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Tools are removed successfully in index but failed to remove from mcp server memory with error: Serialization failure",
            argumentCaptor.getValue().getMessage()
        );
    }

    private List<String> getRemoveMcpTool() {
        List<String> toolNames = new ArrayList<>();
        toolNames.add("ListIndexTool");
        return toolNames;
    }

    private List<RegisterMcpTool> getRegisterMcpTools() {
        RegisterMcpTool listIndexTool = new RegisterMcpTool("ListIndexTool", "ListIndexTool", "", Map.of(), Map.of(), null, null);
        listIndexTool.setVersion(1L);
        return List.of(listIndexTool);
    }
}
