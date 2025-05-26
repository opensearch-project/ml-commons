package org.opensearch.ml.action.mcpserver;

import com.google.common.collect.ImmutableMap;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpToolsListResponse;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransportMcpToolsListActionTests extends OpenSearchTestCase {

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

    private Map<String, Tool.Factory> toolFactories = ImmutableMap.of("ListIndexTool", ListIndexTool.Factory.getInstance());
    @Mock
    private ToolFactoryWrapper toolFactoryWrapper;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLMcpToolsListResponse> listener;

    private McpToolsHelper mcpToolsHelper = spy(new McpToolsHelper(client, threadPool, toolFactoryWrapper));

    private TransportMcpToolsListAction transportMcpToolsListAction;

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
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(0);
            actionListener.onResponse(getRegisterMcpTools());
            return null;
        }).when(mcpToolsHelper).searchAllTools(isA(ActionListener.class));
        transportMcpToolsListAction = new TransportMcpToolsListAction(transportService, clusterService, actionFilters, xContentRegistry, nodeFilter, mcpToolsHelper);
    }

    public void test_doExecute_success() {
        ActionRequest nodesRequest = mock(ActionRequest.class);
        transportMcpToolsListAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<MLMcpToolsListResponse> argumentCaptor = ArgumentCaptor.forClass(MLMcpToolsListResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().getMcpTools().size());
    }

    public void test_doExecute_featureFlagDisabled() {
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), false).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
                .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        TransportMcpToolsListAction mcpToolsRemoveAction = new TransportMcpToolsListAction(transportService, clusterService, actionFilters, xContentRegistry, nodeFilter, mcpToolsHelper);
        ActionRequest nodesRequest = mock(ActionRequest.class);
        mcpToolsRemoveAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("The MCP server is not enabled. To enable, please update the setting plugins.ml_commons.mcp_server_enabled", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_exception() {
        doAnswer(invocationOnMock -> {
            ActionListener<List<RegisterMcpTool>> actionListener = invocationOnMock.getArgument(0);
            actionListener.onFailure(new RuntimeException("Network issue"));
            return null;
        }).when(mcpToolsHelper).searchAllTools(isA(ActionListener.class));
        ActionRequest nodesRequest = mock(ActionRequest.class);
        transportMcpToolsListAction.doExecute(task, nodesRequest, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Network issue", argumentCaptor.getValue().getMessage());
    }

    private List<RegisterMcpTool> getRegisterMcpTools() {
        RegisterMcpTool listIndexTool = new RegisterMcpTool(
                "ListIndexTool",
                "ListIndexTool",
                "",
                Map.of(),
                Map.of(), null, null
        );
        listIndexTool.setVersion(1L);
        return List.of(listIndexTool);
    }
}
