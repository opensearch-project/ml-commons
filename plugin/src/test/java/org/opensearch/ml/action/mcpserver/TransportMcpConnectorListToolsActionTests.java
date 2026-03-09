/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpConnectorListToolsRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpConnectorListToolsResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.list.McpToolInfo;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

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
            MLFeatureEnabledSetting mlFeatureEnabledSetting
        ) {
            super(transportService, actionFilters, client, sdkClient, encryptor, mlFeatureEnabledSetting);
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
        transportAction = new TestableTransportMcpConnectorListToolsAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            encryptor,
            mlFeatureEnabledSetting
        );
    }

    @Test
    public void testDoExecute_Success() {
        String inputSchema =
            "{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\"},\"max_tokens\":{\"type\":\"integer\"}}}";
        Map<String, String> attributes = Collections.singletonMap("input_schema", inputSchema);
        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("TestTool").description("Desc").attributes(attributes).build();
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
        assertNotNull(info.getArguments());
        assertTrue(info.getArguments().containsKey("prompt"));
        assertTrue(info.getArguments().containsKey("max_tokens"));
    }

    @Test
    public void testDoExecute_EmptyTools_Fails() {
        transportAction.setToolSpecsToReturn(Collections.emptyList());

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId("conn-1")
            .tenantId("tenant-1")
            .build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof MLException);
        assertTrue(captor.getValue().getMessage().contains("No tools defined for connector"));
    }

    @Test
    public void testDoExecute_NullTools_Fails() {
        transportAction.setToolSpecsToReturn(null);

        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId("conn-1")
            .tenantId("tenant-1")
            .build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof MLException);
        assertTrue(captor.getValue().getMessage().contains("No tools defined for connector"));
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
            mlFeatureEnabledSetting
        );
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("conn-1").tenantId(null).build();
        ActionListener<MLMcpConnectorListToolsResponse> listener = mock(ActionListener.class);

        actionWithMultiTenancy.doExecute(task, request, listener);

        verify(listener).onFailure(any(Exception.class));
    }

    @Test
    public void testDoExecute_WithMultiTenancyEnabled_ValidTenantId_Success() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        String inputSchema = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}}";
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
        assertNotNull(info.getArguments());
        assertTrue(info.getArguments().containsKey("query"));
        assertTrue(info.getArguments().containsKey("limit"));
        assertEquals("tenant-abc", request.getTenantId());
    }
}
