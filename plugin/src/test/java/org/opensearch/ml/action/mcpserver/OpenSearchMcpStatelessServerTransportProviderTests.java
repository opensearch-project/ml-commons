/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.test.OpenSearchTestCase;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class OpenSearchMcpStatelessServerTransportProviderTests extends OpenSearchTestCase {

    private OpenSearchMcpStatelessServerTransportProvider provider;

    @Mock
    private McpStatelessServerHandler mcpHandler;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        provider = new OpenSearchMcpStatelessServerTransportProvider(new ObjectMapper());
        provider.setMcpHandler(mcpHandler);
    }

    @Test
    public void test_handleRequest_successful() throws Exception {
        when(mcpHandler.handleRequest(any(), any())).thenReturn(Mono.just(new McpSchema.JSONRPCResponse("2.0", "1", "success", null)));
        
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "100",
              "method": "tools/list"
            }
            """;
        McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(new ObjectMapper(), requestBody);
        StepVerifier.create(provider.handleRequest(message))
            .expectNextMatches(response -> response instanceof McpSchema.JSONRPCResponse)
            .verifyComplete();
    }

    @Test
    public void test_handleRequest_invalidJson() throws Exception {
        String requestBody = """
            {,
              "jsonrpc": "2.0",
              "method": "tools/list"
            }
            """;

        McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(new ObjectMapper(), requestBody);
        StepVerifier.create(provider.handleRequest(message)).expectErrorMatches(e -> e instanceof Exception).verify();
    }

    @Test
    public void test_handleRequest_handlerNotSet() throws Exception {
        OpenSearchMcpStatelessServerTransportProvider providerWithoutHandler = new OpenSearchMcpStatelessServerTransportProvider(
            new ObjectMapper()
        );

        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "100",
              "method": "tools/list"
            }
            """;
        McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(new ObjectMapper(), requestBody);
        StepVerifier
            .create(providerWithoutHandler.handleRequest(message))
            .expectErrorMatches(e -> e.getMessage().contains("MCP handler not initialized"))
            .verify();
    }

    @Test
    public void test_handleRequest_handlerThrowsException() throws Exception {
        when(mcpHandler.handleRequest(any(), any())).thenReturn(Mono.error(new RuntimeException("Handler error")));
        
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "100",
              "method": "tools/list"
            }
            """;
        McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(new ObjectMapper(), requestBody);
        StepVerifier.create(provider.handleRequest(message))
            .expectErrorMatches(e -> e.getMessage().contains("Handler error"))
            .verify();
    }

    @Test
    public void test_closeGracefully() {
        StepVerifier.create(provider.closeGracefully()).verifyComplete();
    }

}
