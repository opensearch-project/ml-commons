/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.node.NodeClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class OpenSearchMcpServerTransportProviderTests extends OpenSearchTestCase {

    @Mock
    StreamingRestChannel channel = mock(StreamingRestChannel.class);

    private OpenSearchMcpServerTransportProvider provider;

    OpenSearchMcpServerTransportProvider.OpenSearchMcpSessionTransport transport;

    @Mock
    private McpServerSession.Factory factory;

    @Mock
    private McpServerSession mcpServerSession;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    McpToolsHelper mcpToolsHelper;

    @Mock
    private NodeClient client;

    private final String nodeId = "nodeId";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        provider = new OpenSearchMcpServerTransportProvider(mlIndicesHandler, mcpToolsHelper, new ObjectMapper());
        transport = provider.new OpenSearchMcpSessionTransport(channel);
        provider.setSessionFactory(factory);
        when(factory.create(any())).thenReturn(mcpServerSession);
        when(mcpServerSession.getId()).thenReturn("mockId");
        when(mcpServerSession.handle(any())).thenReturn(Mono.empty());
        when(mcpServerSession.sendNotification(any(), any())).thenReturn(Mono.empty());
        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> listener = invocationOnMock.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLMcpSessionManagementIndex(isA(ActionListener.class));

        doAnswer(invocationOnMock -> {
            ActionListener<IndexResponse> listener = invocationOnMock.getArgument(1);
            IndexResponse response = mock(IndexResponse.class);
            when(response.getId()).thenReturn(nodeId);
            when(response.status()).thenReturn(RestStatus.CREATED);
            listener.onResponse(response);
            return null;
        }).when(client).index(any(), isA(ActionListener.class));

        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> listener = invocationOnMock.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpToolsHelper).autoLoadAllMcpTools(isA(ActionListener.class));
    }

    @Test
    public void test_handleSseConnection_successful() {
        StepVerifier.create(provider.handleSseConnection(channel, true, nodeId, client)).expectNextMatches(chunk -> {
            String data = chunk.content().utf8ToString();
            return data.contains("endpoint");
        }).verifyComplete();
    }

    @Test
    public void test_handleInitializedMessage_successful() {
        test_handleSseConnection_successful();
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "method": "notifications/initialized"
            }
            """;
        StepVerifier.create(provider.handleMessage("mockId", requestBody)).verifyComplete();
    }

    @Test
    public void test_handleNonInitializedMessage_successful() {
        test_handleSseConnection_successful();
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "100",
              "method": "tools/list"
            }
            """;
        StepVerifier.create(provider.handleMessage("mockId", requestBody)).verifyComplete();
    }

    @Test
    public void test_handleMessage_invalidInput_throwException() {
        test_handleSseConnection_successful();
        String requestBody = """
            {,
              "jsonrpc": "2.0",
              "method": "tools/list"
            }
            """;
        StepVerifier
            .create(provider.handleMessage("mockId", requestBody))
            .expectErrorSatisfies(e -> e.getMessage().contains("Invalid message format"))
            .verify();
    }

    @Test
    public void test_handleMessage_sessionNotExist_throwException() {
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "method": "tools/list"
            }
            """;
        StepVerifier
            .create(provider.handleMessage("mockId", requestBody))
            .expectErrorSatisfies(e -> e.getMessage().contains("Session not found"))
            .verify();
    }

    @Test
    public void test_notifyClients_successful() {
        StepVerifier.create(provider.notifyClients("tools/list", null)).verifyComplete();
        test_handleSseConnection_successful();
        StepVerifier.create(provider.notifyClients("tools/list", null)).verifyComplete();
    }

    @Test
    public void test_closeGracefully_successful() {
        StepVerifier.create(provider.closeGracefully()).verifyComplete();
    }

    @Test
    public void test_sendMessage_successful() {
        String message = """
            {
                "protocolVersion": "2024-11-05",
                "capabilities": { "logging": {}, "tools": { "listChanged": true } },
                "serverInfo": { "name": "OpenSearch-MCP-Server", "version": "1.0.0" }
            }
            """;
        McpSchema.JSONRPCResponse response = new McpSchema.JSONRPCResponse("2.0", "1", message, null);
        StepVerifier.create(transport.sendMessage(response)).verifyComplete();
    }

    @Test
    public void test_unmarshalFrom_successful() {
        String unmarshalled = transport.unmarshalFrom("test", new TypeReference<>() {
        });
        assertEquals("test", unmarshalled);
    }

}
