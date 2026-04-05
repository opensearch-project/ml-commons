/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.connector.McpStreamableHttpConnector;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.McpConnectorExecutor;
import org.opensearch.ml.engine.algorithms.remote.McpStreamableHttpConnectorExecutor;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.tools.McpSseTool;
import org.opensearch.ml.engine.tools.McpStreamableHttpTool;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLConversationalFlowAgentRunnerTest extends MLStaticMockBase {

    private static final String FIRST_TOOL = "firstTool";
    private static final String SECOND_TOOL = "secondTool";

    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private ActionListener<Object> agentActionListener;
    @Mock
    private Tool.Factory firstToolFactory;
    @Mock
    private Tool.Factory secondToolFactory;
    @Mock
    private Tool firstTool;
    @Mock
    private Tool secondTool;
    @Mock
    private SdkClient sdkClient;
    @Mock
    private Encryptor encryptor;
    @Mock
    private ThreadPool threadPool;

    private MLConversationalFlowAgentRunner runner;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        runner = new MLConversationalFlowAgentRunner(
            client,
            Settings.builder().build(),
            clusterService,
            xContentRegistry,
            Map.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory),
            Map.of(),
            sdkClient,
            encryptor
        );
        when(firstToolFactory.create(anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(anyMap())).thenReturn(secondTool);
        when(firstTool.getDescription()).thenReturn("first tool description");
        when(secondTool.getDescription()).thenReturn("second tool description");
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse("tool-output");
            return null;
        }).when(firstTool).run(anyMap(), any(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse("tool-output");
            return null;
        }).when(secondTool).run(anyMap(), any(ActionListener.class));
    }

    @Test
    public void testRun_WithMcpStreamableHttp_Tools_Success() throws Exception {
        runMixedOpenSearchAndMcpToolsSuccessTest(McpStreamableHttpTool.TYPE, McpStreamableHttpTool.TYPE);
    }

    @Test
    public void testRun_WithMcpSse_Tools_Success() throws Exception {
        runMixedOpenSearchAndMcpToolsSuccessTest(McpSseTool.TYPE, McpSseTool.TYPE);
    }

    private void runMixedOpenSearchAndMcpToolsSuccessTest(String mcpToolType, String mcpProtocol) throws Exception {
        final Map<String, String> params = new HashMap<>();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .tenantId("tenant")
            .parameters(Map.of("mcp_connectors", "[{\"mcp_connector_id\":\"connector_1\"}]"))
            .build();
        final MLToolSpec osTool1 = MLToolSpec.builder().name("os_tool_1").type(FIRST_TOOL).includeOutputInAgentResponse(true).build();
        final MLToolSpec mcpTool1 = MLToolSpec.builder().name("mcp_tool_1").type(mcpToolType).includeOutputInAgentResponse(true).build();
        final MLToolSpec osTool2 = MLToolSpec.builder().name("os_tool_2").type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        final MLToolSpec mcpTool2 = MLToolSpec.builder().name("mcp_tool_2").type(mcpToolType).includeOutputInAgentResponse(true).build();

        Map<String, Tool.Factory> extendedFactories = new HashMap<>(Map.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory));
        extendedFactories.put(mcpToolType, firstToolFactory);
        MLConversationalFlowAgentRunner runnerWithMcp = new MLConversationalFlowAgentRunner(
            client,
            Settings.builder().build(),
            clusterService,
            xContentRegistry,
            extendedFactories,
            Map.of(),
            sdkClient,
            encryptor
        );

        ThreadContext threadContext = new ThreadContext(Settings.builder().build());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenAnswer(inv -> {
            String json = "{\"_index\":\"i\",\"_id\":\"j\",\"found\":true,\"_source\":{}}";
            org.opensearch.core.xcontent.XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(NamedXContentRegistry.EMPTY, null, json);
            GetDataObjectResponse resp = mock(GetDataObjectResponse.class);
            when(resp.parser()).thenReturn(parser);
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(resp, null);
                return stage;
            });
            return stage;
        });

        try (
            MockedStatic<Connector> connStatic = mockStatic(Connector.class);
            MockedStatic<MLEngineClassLoader> loadStatic = mockStatic(MLEngineClassLoader.class)
        ) {
            if ("mcp_sse".equals(mcpProtocol)) {
                McpConnector mockConnector = mock(McpConnector.class);
                when(mockConnector.getProtocol()).thenReturn("mcp_sse");
                doAnswer(invocation -> {
                    ActionListener<Boolean> listener = invocation.getArgument(3);
                    listener.onResponse(true);
                    return null;
                }).when(mockConnector).decrypt(anyString(), any(), anyString(), any(ActionListener.class));
                connStatic
                    .when(() -> Connector.createConnector(any(org.opensearch.core.xcontent.XContentParser.class)))
                    .thenReturn(mockConnector);
                McpConnectorExecutor exec = mock(McpConnectorExecutor.class);
                when(exec.getMcpToolSpecs())
                    .thenReturn(
                        List
                            .of(
                                MLToolSpec.builder().name("mcp_tool_1").type(mcpToolType).runtimeResources(Map.of()).build(),
                                MLToolSpec.builder().name("mcp_tool_2").type(mcpToolType).runtimeResources(Map.of()).build()
                            )
                    );
                loadStatic.when(() -> MLEngineClassLoader.initInstance(anyString(), any(), any())).thenReturn(exec);
            } else {
                McpStreamableHttpConnector mockConnector = mock(McpStreamableHttpConnector.class);
                when(mockConnector.getProtocol()).thenReturn("mcp_streamable_http");
                doAnswer(invocation -> {
                    ActionListener<Boolean> listener = invocation.getArgument(3);
                    listener.onResponse(true);
                    return null;
                }).when(mockConnector).decrypt(anyString(), any(), anyString(), any(ActionListener.class));
                connStatic
                    .when(() -> Connector.createConnector(any(org.opensearch.core.xcontent.XContentParser.class)))
                    .thenReturn(mockConnector);
                McpStreamableHttpConnectorExecutor exec = mock(McpStreamableHttpConnectorExecutor.class);
                when(exec.getMcpToolSpecs())
                    .thenReturn(
                        List
                            .of(
                                MLToolSpec.builder().name("mcp_tool_1").type(mcpToolType).runtimeResources(Map.of()).build(),
                                MLToolSpec.builder().name("mcp_tool_2").type(mcpToolType).runtimeResources(Map.of()).build()
                            )
                    );
                loadStatic.when(() -> MLEngineClassLoader.initInstance(anyString(), any(), any())).thenReturn(exec);
            }

            MLAgent runnableAgent = mlAgent.toBuilder().tools(List.of(osTool1, mcpTool1, osTool2, mcpTool2)).build();
            runnerWithMcp.run(runnableAgent, params, agentActionListener, null);

            ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
            verify(agentActionListener).onResponse(responseCaptor.capture());
            @SuppressWarnings("unchecked")
            List<ModelTensor> output = (List<ModelTensor>) responseCaptor.getValue();
            assertEquals(4, output.size());
            assertEquals("os_tool_1", output.get(0).getName());
            assertEquals("mcp_tool_1", output.get(1).getName());
            assertEquals("os_tool_2", output.get(2).getName());
            assertEquals("mcp_tool_2", output.get(3).getName());
        }
    }

    @Test
    public void testRun_WhenNoToolConfigured_ShouldFail() {
        MLAgent mlAgent = MLAgent.builder().name("TestAgent").type(MLAgentType.CONVERSATIONAL_FLOW.name()).tools(List.of()).build();
        runner.run(mlAgent, new HashMap<>(), agentActionListener, null);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("no tool configured"));
    }

    @Test
    public void testRun_WhenMcpToolConfiguredButNoMcpConnector_ShouldFail() {
        MLToolSpec mcpTool = MLToolSpec.builder().name("missing_mcp_tool").type(McpStreamableHttpTool.TYPE).build();
        MLAgent mlAgent = MLAgent.builder().name("TestAgent").type(MLAgentType.CONVERSATIONAL_FLOW.name()).tools(List.of(mcpTool)).build();
        runner.run(mlAgent, new HashMap<>(), agentActionListener, null);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("missing_mcp_tool"));
        assertTrue(exceptionCaptor.getValue().getMessage().contains("not available"));
    }
}
