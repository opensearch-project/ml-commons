/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.MCP_SYNC_CLIENT;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpConnectorListToolsRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpConnectorListToolsResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.list.McpToolInfo;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import io.modelcontextprotocol.client.McpSyncClient;

public class TransportMcpConnectorListToolsActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private Client client;
    @Mock
    private SdkClient sdkClient;
    @Mock
    private EncryptorImpl encryptor;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private Task task;

    /** Test-only subclass that overrides fetch to avoid static mocking. */
    private static class TestableTransportMcpConnectorListToolsAction extends TransportMcpConnectorListToolsAction {
        private List<MLToolSpec> toolSpecsToReturn = Collections.emptyList();
        private Exception failureToReturn = null;

        TestableTransportMcpConnectorListToolsAction(
            TransportService transportService,
            ActionFilters actionFilters,
            Client client,
            SdkClient sdkClient,
            EncryptorImpl encryptor,
            MLFeatureEnabledSetting mlFeatureEnabledSetting,
            ConnectorAccessControlHelper connectorAccessControlHelper
        ) {
            super(transportService, actionFilters, client, sdkClient, encryptor, mlFeatureEnabledSetting, connectorAccessControlHelper);
        }

        void setToolSpecsToReturn(List<MLToolSpec> toolSpecsToReturn) {
            this.toolSpecsToReturn = toolSpecsToReturn;
            this.failureToReturn = null;
        }

        void setFailureToReturn(Exception failureToReturn) {
            this.failureToReturn = failureToReturn;
            this.toolSpecsToReturn = null;
        }

        @Override
        protected void fetchToolSpecsFromConnector(
            String connectorId,
            String tenantId,
            ActionListener<List<MLToolSpec>> toolSpecsListener
        ) {
            if (failureToReturn != null) {
                toolSpecsListener.onFailure(failureToReturn);
            } else {
                toolSpecsListener.onResponse(toolSpecsToReturn);
            }
        }
    }

    private TestableTransportMcpConnectorListToolsAction transportAction;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Boolean> accessListener = invocation.getArgument(5);
            accessListener.onResponse(true);
            return null;
        })
            .when(connectorAccessControlHelper)
            .validateConnectorAccess(eq(sdkClient), eq(client), any(), any(), eq(mlFeatureEnabledSetting), any());
        transportAction = new TestableTransportMcpConnectorListToolsAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            encryptor,
            mlFeatureEnabledSetting,
            connectorAccessControlHelper
        );
    }

    @Test
    public void testDoExecute_McpConnectorDisabled_CallsListenerOnFailure() {
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(false);
        TestableTransportMcpConnectorListToolsAction action = new TestableTransportMcpConnectorListToolsAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            encryptor,
            mlFeatureEnabledSetting,
            connectorAccessControlHelper
        );
        action.setToolSpecsToReturn(List.of(MLToolSpec.builder().type("test_tool").name("TestTool").description("Desc").build()));

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        verifyNoMoreInteractions(listener);
        Exception e = captor.getValue();
        assertTrue(e instanceof OpenSearchException);
        assertEquals(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE, e.getMessage());
    }

    @Test
    public void testDoExecute_Success() {
        String inputSchema = "\"input_schema\":\"{\"type\":\"object\",\"properties\":{\"query\":"
            + "{\"type\":\"string\",\"description\":\"The query to search for.\"},\"language\":{\"type\":\"string\""
            + ",\"description\":\"The language for the SDK to search for.\",\"detail\":{\"type\":\"string\","
            + "\"description\":\"The amount of detail to return.\"}},\"required\":[\"query\",\"language\"]";
        Map<String, String> attributes = Collections.singletonMap("input_schema", inputSchema);
        McpSyncClient mcpSyncClient = mock(McpSyncClient.class);
        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("TestTool").description("Desc").attributes(attributes).build();
        toolSpec.addRuntimeResource(MCP_SYNC_CLIENT, mcpSyncClient);
        transportAction.setToolSpecsToReturn(List.of(toolSpec));

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpConnectorListToolsResponse> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsResponse.class);
        verify(listener).onResponse(captor.capture());
        MLMcpConnectorListToolsResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(1, response.getTools().size());
        McpToolInfo info = response.getTools().get(0);
        assertEquals("TestTool", info.getName());
        assertEquals("test_tool", info.getType());
        assertEquals("Desc", info.getDescription());
        assertEquals(inputSchema, info.getInputSchema());
        verify(mcpSyncClient, times(1)).closeGracefully();
    }

    @Test
    public void testDoExecute_EmptyTools_ReturnsEmptyList() {
        transportAction.setToolSpecsToReturn(Collections.emptyList());

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId("conn-1")
            .tenantId("tenant-1")
            .build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpConnectorListToolsResponse> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsResponse.class);
        verify(listener).onResponse(captor.capture());
        assertNotNull(captor.getValue());
        assertTrue(captor.getValue().getTools().isEmpty());
    }

    @Test
    public void testDoExecute_GetToolsFailure() {
        RuntimeException failure = new RuntimeException("connector error");
        transportAction.setFailureToReturn(failure);

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId("conn-1")
            .tenantId("tenant-1")
            .build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(failure);
    }

    @Test
    public void testDoExecute_InvalidTenantId_CallsListenerOnFailure() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        TransportMcpConnectorListToolsAction actionWithMultiTenancy = new TransportMcpConnectorListToolsAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            encryptor,
            mlFeatureEnabledSetting,
            connectorAccessControlHelper
        );
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").tenantId(null).build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        actionWithMultiTenancy.doExecute(task, request, listener);

        verify(listener).onFailure(any(Exception.class));
    }

    @Test
    public void testDoExecute_ConnectorAccessDenied_Forbidden() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Boolean> accessListener = invocation.getArgument(5);
            accessListener.onResponse(false);
            return null;
        })
            .when(connectorAccessControlHelper)
            .validateConnectorAccess(eq(sdkClient), eq(client), any(), any(), eq(mlFeatureEnabledSetting), any());

        transportAction.setToolSpecsToReturn(List.of(MLToolSpec.builder().type("test_tool").name("TestTool").description("Desc").build()));

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        verifyNoMoreInteractions(listener);
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) captor.getValue()).status());
        assertEquals("You don't have permission to access this connector", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_WithMultiTenancyEnabled_ValidTenantId_Success() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        String inputSchema = "\"input_schema\":\"{\"type\":\"object\",\"properties\":{\"query\":"
                + "{\"type\":\"string\",\"description\":\"The query to search for.\"},\"language\":{\"type\":\"string\""
                + ",\"description\":\"The language for the SDK to search for.\",\"detail\":{\"type\":\"string\","
                + "\"description\":\"The amount of detail to return.\"}},\"required\":[\"query\",\"language\"]";
        Map<String, String> attributes = Collections.singletonMap("input_schema", inputSchema);
        MLToolSpec toolSpec = MLToolSpec
            .builder()
            .type("mcp_tool")
            .name("McpTool")
            .description("MCP tool for tenant")
            .attributes(attributes)
            .build();
        transportAction.setToolSpecsToReturn(List.of(toolSpec));

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder()
            .connectorId("conn-multi")
            .tenantId("tenant-abc")
            .build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpConnectorListToolsResponse> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsResponse.class);
        verify(listener).onResponse(captor.capture());
        MLMcpConnectorListToolsResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(1, response.getTools().size());
        McpToolInfo info = response.getTools().get(0);
        assertEquals("McpTool", info.getName());
        assertEquals("mcp_tool", info.getType());
        assertEquals("MCP tool for tenant", info.getDescription());
        assertEquals(inputSchema, info.getInputSchema());
        assertEquals("tenant-abc", request.getTenantId());
    }

    @Test
    public void testDoExecute_InputSchemaAttributesNull_ReturnsNullInputSchema() {
        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("ToolNullAttrs").description("Desc").attributes(null).build();
        transportAction.setToolSpecsToReturn(List.of(toolSpec));

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpConnectorListToolsResponse> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsResponse.class);
        verify(listener).onResponse(captor.capture());
        McpToolInfo info = captor.getValue().getTools().get(0);
        assertNull(info.getInputSchema());
    }

    @Test
    public void testDoExecute_InputSchemaAttributeValueNull_ReturnsNullInputSchema() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, null);
        MLToolSpec toolSpec = MLToolSpec
            .builder()
            .type("test_tool")
            .name("ToolInputSchemaValueNull")
            .description("Desc")
            .attributes(attributes)
            .build();
        transportAction.setToolSpecsToReturn(List.of(toolSpec));

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLMcpConnectorListToolsResponse> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsResponse.class);
        verify(listener).onResponse(captor.capture());
        McpToolInfo info = captor.getValue().getTools().get(0);
        assertNull(info.getInputSchema());
    }
}
