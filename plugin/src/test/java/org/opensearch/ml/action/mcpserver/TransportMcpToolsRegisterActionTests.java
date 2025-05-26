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
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodesResponse;
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

public class TransportMcpToolsRegisterActionTests extends OpenSearchTestCase {

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
    private ActionListener<MLMcpToolsRegisterNodesResponse> listener;

    private McpToolsHelper mcpToolsHelper = spy(new McpToolsHelper(client, threadPool, toolFactoryWrapper));

    private TransportMcpToolsRegisterAction transportMcpToolsRegisterAction;

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
            ActionListener<Boolean> actionListener = invocationOnMock.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLMcpToolsIndex(isA(ActionListener.class));
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onResponse(List.of());
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
            ActionListener<MLMcpToolsRegisterNodesResponse> actionListener = invocationOnMock.getArgument(2);
            List<MLMcpToolsRegisterNodeResponse> nodes = List
                .of(
                    new MLMcpToolsRegisterNodeResponse(
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
            MLMcpToolsRegisterNodesResponse response = new MLMcpToolsRegisterNodesResponse(ClusterName.DEFAULT, nodes, ImmutableList.of());
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        transportMcpToolsRegisterAction = new TransportMcpToolsRegisterAction(
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            nodeFilter,
            mlIndicesHandler,
            mcpToolsHelper
        );
    }

    public void test_doExecute_success() {
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = List.of(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<MLMcpToolsRegisterNodesResponse> argumentCaptor = ArgumentCaptor.forClass(MLMcpToolsRegisterNodesResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(mcpTools.size(), argumentCaptor.getValue().getNodes().size());
    }

    public void test_doExecute_featureFlagDisabled() {
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), false).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TransportMcpToolsRegisterAction transportMcpToolsRegisterAction = new TransportMcpToolsRegisterAction(
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            nodeFilter,
            mlIndicesHandler,
            mcpToolsHelper
        );
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = List.of(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The MCP server is not enabled. To enable, please update the setting plugins.ml_commons.mcp_server_enabled",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_clientThreadContextException() {
        when(client.threadPool()).thenThrow(new RuntimeException("unexpected error"));
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = List.of(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("unexpected error", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_initToolIndexException() {
        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> actionListener = invocationOnMock.getArgument(0);
            actionListener.onFailure(new RuntimeException("Network issue"));
            return null;
        }).when(mlIndicesHandler).initMLMcpToolsIndex(isA(ActionListener.class));
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = List.of(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_failedToSearchIndex() {
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onFailure(new RuntimeException("Network issue"));
            return null;
        }).when(mcpToolsHelper).searchToolsWithVersion(isA(List.class), isA(ActionListener.class));
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = List.of(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Network issue", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_toolExists() {
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(1);
            actionListener.onResponse(List.of(getRegisterMcpTool()));
            return null;
        }).when(mcpToolsHelper).searchToolsWithVersion(isA(List.class), isA(ActionListener.class));
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = List.of(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Unable to register tools: [ListIndexTool] as they already exist", argumentCaptor.getValue().getMessage());
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
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = new ArrayList<>();
        mcpTools.add(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to persist mcp tool: ListIndexTool into system index with error: java.lang.RuntimeException: Network issue",
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
                "ListIndexTool",
                new RuntimeException("Network issue")
            );
            when(failedItem.getFailure()).thenReturn(failure);
            when(failedItem.getId()).thenReturn("ListIndexTool");

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
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = new ArrayList<>();
        mcpTools.add(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to persist mcp tool: ListIndexTool into system index with error: java.lang.RuntimeException: Network issue",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_registerOnNodeHasFailure() {
        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsRegisterNodesResponse> actionListener = invocationOnMock.getArgument(2);
            List<FailedNodeException> failures = List
                .of(new FailedNodeException("mockNodeId", "Network issue", new RuntimeException("Network issue")));
            MLMcpToolsRegisterNodesResponse response = new MLMcpToolsRegisterNodesResponse(
                ClusterName.DEFAULT,
                ImmutableList.of(),
                failures
            );
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = new ArrayList<>();
        mcpTools.add(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Tools: [ListIndexTool] are persisted successfully but failed to register to mcp server memory with error: Network issue",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_registerOnNodeException() {
        doAnswer(invocationOnMock -> {
            ActionListener<MLMcpToolsRegisterNodesResponse> actionListener = invocationOnMock.getArgument(2);
            actionListener.onFailure(new RuntimeException("Serialization failure"));
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLMcpToolsRegisterNodesRequest nodesRequest = mock(MLMcpToolsRegisterNodesRequest.class);
        List<RegisterMcpTool> mcpTools = new ArrayList<>();
        mcpTools.add(getRegisterMcpTool());
        when(nodesRequest.getMcpTools()).thenReturn(mcpTools);
        transportMcpToolsRegisterAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Tools are persisted successfully but failed to register to mcp server memory with error: Serialization failure",
            argumentCaptor.getValue().getMessage()
        );
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
                ),
            null,
            null
        );
        registerMcpTool.setVersion(1L);
        return registerMcpTool;
    }
}
