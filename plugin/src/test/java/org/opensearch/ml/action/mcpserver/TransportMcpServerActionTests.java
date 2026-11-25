/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_PARSE_ERROR;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.transport.mcpserver.requests.server.MLMcpServerRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

public class TransportMcpServerActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private McpToolsHelper mcpToolsHelper;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLMcpServerResponse> listener;

    private TransportMcpServerAction action;

    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        action = new TransportMcpServerAction(transportService, actionFilters, mlFeatureEnabledSetting, mcpToolsHelper);
    }

    private void mockTools(List<McpToolRegisterInput> tools) {
        doAnswer(invocation -> {
            ActionListener<List<McpToolRegisterInput>> l = invocation.getArgument(0);
            l.onResponse(tools);
            return null;
        }).when(mcpToolsHelper).searchAllTools(any());
    }

    public void test_doExecute_mcpServerDisabled() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(false);
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\"}");

        action.doExecute(task, request, listener);

        verify(listener).onFailure(any(OpenSearchException.class));
        verify(mcpToolsHelper, never()).searchAllTools(any());
    }

    public void test_doExecute_invalidJsonRpcMessage() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        MLMcpServerRequest request = new MLMcpServerRequest("invalid json");

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLMcpServerResponse response = responseCaptor.getValue();
        assertFalse(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNotNull(response.getError());
        assertEquals(JSON_RPC_PARSE_ERROR, response.getError().get(ERROR_CODE_FIELD));
        assertTrue(response.getError().get(MESSAGE_FIELD).toString().contains("Parse error"));
        verify(mcpToolsHelper, never()).searchAllTools(any());
    }

    public void test_doExecute_jsonRpcNotification() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLMcpServerResponse response = responseCaptor.getValue();
        assertTrue(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNull(response.getError());
        verify(mcpToolsHelper, never()).searchAllTools(any());
    }

    public void test_doExecute_toolsListSuccess() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        McpToolRegisterInput toolA = new McpToolRegisterInput("ToolA", "ListIndexTool", "desc", Map.of(), Map.of(), null, null);
        McpToolRegisterInput toolB = new McpToolRegisterInput("ToolB", "ListIndexTool", "desc", Map.of(), Map.of(), null, null);
        mockTools(List.of(toolA, toolB));
        // Echo each loaded tool's name so the test pins that the response maps over the loaded list.
        when(mcpToolsHelper.createToolSpecification(any()))
            .thenAnswer(inv -> toolSpec(((McpToolRegisterInput) inv.getArgument(0)).getName()));
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLMcpServerResponse response = responseCaptor.getValue();
        assertTrue(response.getAcknowledgedResponse());
        assertNotNull(response.getMcpResponse());
        assertTrue(response.getMcpResponse().contains("ToolA"));
        assertTrue(response.getMcpResponse().contains("ToolB"));
        assertNull(response.getError());
    }

    public void test_doExecute_unbuildableToolSkipped() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        McpToolRegisterInput good = new McpToolRegisterInput("GoodTool", "ListIndexTool", "desc", Map.of(), Map.of(), null, null);
        McpToolRegisterInput bad = new McpToolRegisterInput("BadTool", "GoneType", "desc", Map.of(), Map.of(), null, null);
        mockTools(List.of(good, bad));
        when(mcpToolsHelper.createToolSpecification(good)).thenReturn(toolSpec("GoodTool"));
        when(mcpToolsHelper.createToolSpecification(bad)).thenThrow(new RuntimeException("no factory for GoneType"));
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLMcpServerResponse response = responseCaptor.getValue();
        assertTrue(response.getAcknowledgedResponse());
        assertNotNull(response.getMcpResponse());
        assertTrue(response.getMcpResponse().contains("GoodTool"));
        assertFalse(response.getMcpResponse().contains("BadTool"));
        assertNull(response.getError());
    }

    public void test_doExecute_toolsCallFailure_returnsIsErrorResult() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        McpToolRegisterInput failing = new McpToolRegisterInput("FailingTool", "ListIndexTool", "desc", Map.of(), Map.of(), null, null);
        mockTools(List.of(failing));
        // Build the spec with the real helper so the test covers how a tool failure is turned into a result.
        McpStatelessServerFeatures.AsyncToolSpecification spec = realSpecForFailingTool(failing, "no permissions for [x]");
        when(mcpToolsHelper.createToolSpecification(failing)).thenReturn(spec);
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"FailingTool\",\"arguments\":{}}}"
        );

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        MLMcpServerResponse response = responseCaptor.getValue();
        assertNull(response.getError());
        String body = response.getMcpResponse();
        assertTrue(body, body.contains("\"isError\":true"));
        assertTrue(body, body.contains("no permissions for [x]"));
        assertFalse(body, body.contains("\"error\""));
    }

    @SuppressWarnings("unchecked")
    public void test_doExecute_toolsCallUnknownTool_returnsJsonRpcError() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        McpToolRegisterInput known = new McpToolRegisterInput("KnownTool", "ListIndexTool", "desc", Map.of(), Map.of(), null, null);
        mockTools(List.of(known));
        when(mcpToolsHelper.createToolSpecification(known)).thenReturn(toolSpec("KnownTool"));
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"MissingTool\",\"arguments\":{}}}"
        );

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        MLMcpServerResponse response = responseCaptor.getValue();
        String body = response.getMcpResponse();
        assertNull(response.getError());
        assertNotNull(body);
        Map<String, Object> json = gson.fromJson(body, Map.class);
        assertFalse(body, json.containsKey("result"));
        Map<String, Object> error = (Map<String, Object>) json.get("error");
        assertNotNull(body, error);
        assertNotNull(body, error.get("code"));
    }

    @SuppressWarnings("unchecked")
    private McpStatelessServerFeatures.AsyncToolSpecification realSpecForFailingTool(McpToolRegisterInput input, String message) {
        Tool failingTool = mock(Tool.class);
        doAnswer(inv -> {
            ActionListener<Object> l = inv.getArgument(1);
            l.onFailure(new RuntimeException(message));
            return null;
        }).when(failingTool).run(any(), any());
        Tool.Factory<Tool> factory = mock(Tool.Factory.class);
        when(factory.create(any())).thenReturn(failingTool);
        ToolFactoryWrapper wrapper = mock(ToolFactoryWrapper.class);
        when(wrapper.getToolsFactories()).thenReturn(Map.of(input.getType(), factory));
        return new McpToolsHelper(mock(Client.class), wrapper).createToolSpecification(input);
    }

    private McpStatelessServerFeatures.AsyncToolSpecification toolSpec(String name) {
        return new McpStatelessServerFeatures.AsyncToolSpecification(
            McpSchema.Tool.builder().name(name).description("desc").inputSchema(JSON_MAPPER, "{}").build(),
            (ctx, req) -> Mono.just(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("ok")), false, null, Map.of()))
        );
    }

    private static final JacksonMcpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(JsonMapper.shared());

    public void test_doExecute_toolLoadError() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<List<McpToolRegisterInput>> l = invocation.getArgument(0);
            l.onFailure(new RuntimeException("search failed"));
            return null;
        }).when(mcpToolsHelper).searchAllTools(any());
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLMcpServerResponse response = responseCaptor.getValue();
        assertFalse(response.getAcknowledgedResponse());
        assertNotNull(response.getError());
        assertEquals(JSON_RPC_INTERNAL_ERROR, response.getError().get(ERROR_CODE_FIELD));
    }
}
