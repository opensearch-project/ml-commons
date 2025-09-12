/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.ID_FIELD;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_PARSE_ERROR;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_SERVER_NOT_READY_ERROR;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;

import java.util.HashMap;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.requests.server.MLMcpServerRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

public class TransportMcpServerActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private McpStatelessServerHolder mcpStatelessServerHolder;

    @Mock
    private OpenSearchMcpStatelessServerTransportProvider transportProvider;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLMcpServerResponse> listener;

    private TransportMcpServerAction action;

    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        action = new TransportMcpServerAction(transportService, actionFilters, mlFeatureEnabledSetting, mcpStatelessServerHolder);
    }

    public void test_doExecute_mcpServerDisabled() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(false);
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\"}");

        action.doExecute(task, request, listener);

        verify(listener).onFailure(any(OpenSearchException.class));
        verify(mcpStatelessServerHolder, never()).getMcpStatelessServerTransportProvider();
    }

    public void test_doExecute_transportProviderNotReady() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenReturn(null);
        MLMcpServerRequest request = new MLMcpServerRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\"}");

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        
        MLMcpServerResponse response = responseCaptor.getValue();
        assertFalse(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNotNull(response.getError());
        assertEquals(JSON_RPC_SERVER_NOT_READY_ERROR, response.getError().get(ERROR_CODE_FIELD));
        assertEquals("MCP handler not ready - server initialization failed", response.getError().get(MESSAGE_FIELD));
    }

    public void test_doExecute_invalidJsonRpcMessage() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenReturn(transportProvider);
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
    }

    public void test_doExecute_jsonRpcNotification() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenReturn(transportProvider);
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}"
        );

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        
        MLMcpServerResponse response = responseCaptor.getValue();
        assertTrue(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNull(response.getError());
        verify(transportProvider, never()).handleRequest(any());
    }

    public void test_doExecute_jsonRpcRequestSuccess() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenReturn(transportProvider);
        
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
        );

        McpSchema.JSONRPCResponse jsonRpcResponse = new McpSchema.JSONRPCResponse("2.0", "1", "success", null);
        
        when(transportProvider.handleRequest(any(McpSchema.JSONRPCMessage.class)))
            .thenReturn(Mono.just(jsonRpcResponse));

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        
        MLMcpServerResponse response = responseCaptor.getValue();
        assertTrue(response.getAcknowledgedResponse());
        assertNotNull(response.getMcpResponse());
        assertTrue(response.getMcpResponse().contains("jsonrpc"));
        assertTrue(response.getMcpResponse().contains("success"));
        assertNull(response.getError());
    }

    public void test_doExecute_responseSerializationError() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenReturn(transportProvider);
        
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
        );

        Map<String, Object> circularData = new HashMap<>();
        circularData.put("jsonrpc", "2.0");
        circularData.put(ID_FIELD, 1);
        circularData.put("result", circularData); // Circular reference
        McpSchema.JSONRPCResponse jsonRpcResponse = new McpSchema.JSONRPCResponse("2.0", "1", circularData, null);
        
        when(transportProvider.handleRequest(any(McpSchema.JSONRPCMessage.class)))
            .thenReturn(Mono.just(jsonRpcResponse));

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        
        MLMcpServerResponse response = responseCaptor.getValue();
        assertFalse(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNotNull(response.getError());
        assertEquals(JSON_RPC_INTERNAL_ERROR, response.getError().get(ERROR_CODE_FIELD));
        assertEquals(1, response.getError().get(ID_FIELD));
        assertTrue(response.getError().get(MESSAGE_FIELD).toString().contains("Response serialization failed"));
    }

    public void test_doExecute_transportError() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenReturn(transportProvider);
        
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
        );

        when(transportProvider.handleRequest(any(McpSchema.JSONRPCMessage.class)))
            .thenReturn(Mono.error(new RuntimeException("Transport error")));

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        
        MLMcpServerResponse response = responseCaptor.getValue();
        assertFalse(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNotNull(response.getError());
        assertEquals(JSON_RPC_INTERNAL_ERROR, response.getError().get(ERROR_CODE_FIELD));
        assertEquals(1, response.getError().get(ID_FIELD));
        assertTrue(response.getError().get(MESSAGE_FIELD).toString().contains("Internal server error"));
    }

    public void test_doExecute_generalException() {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        when(mcpStatelessServerHolder.getMcpStatelessServerTransportProvider()).thenThrow(new RuntimeException("General error"));
        
        MLMcpServerRequest request = new MLMcpServerRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
        );

        action.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpServerResponse> responseCaptor = ArgumentCaptor.forClass(MLMcpServerResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        
        MLMcpServerResponse response = responseCaptor.getValue();
        assertFalse(response.getAcknowledgedResponse());
        assertNull(response.getMcpResponse());
        assertNotNull(response.getError());
        assertEquals(JSON_RPC_INTERNAL_ERROR, response.getError().get(ERROR_CODE_FIELD));
        assertTrue(response.getError().get(MESSAGE_FIELD).toString().contains("Internal server error"));
    }
}
