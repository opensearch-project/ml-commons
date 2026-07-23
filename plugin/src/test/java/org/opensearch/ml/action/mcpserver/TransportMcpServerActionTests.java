/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_PARSE_ERROR;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.transport.mcpserver.requests.server.MLMcpServerRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

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
