/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.modelcontextprotocol.util.Assert;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

/**
 * Simple transport provider that delegates everything to the MCP framework.
 */
@Log4j2
public class OpenSearchMcpStatelessServerTransportProvider implements McpStatelessServerTransport {

    private final ObjectMapper objectMapper;
    private McpStatelessServerHandler mcpHandler;

    public OpenSearchMcpStatelessServerTransportProvider(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
        log.info("MCP handler set for stateless transport provider: {}", mcpHandler != null ? "SUCCESS" : "FAILED");
    }

    @Override
    public Mono<Void> closeGracefully() {
        log.debug("Closing stateless MCP transport provider gracefully");
        return Mono.empty();
    }

    /**
     * Simple request handler - let the MCP framework do all the work
     */
    public Mono<McpSchema.JSONRPCMessage> handleRequest(McpSchema.JSONRPCMessage message) {
        try {
            if (mcpHandler == null) {
                log.error("MCP handler is null - server may not be properly initialized");
                return Mono.error(new RuntimeException("MCP handler not initialized"));
            }
            // Let MCP framework handle everything else!
            if (message instanceof McpSchema.JSONRPCRequest request) {
                log.debug("Handling JSON-RPC request: {}", request.method());
                return mcpHandler.handleRequest(McpTransportContext.EMPTY, request).map(response -> (McpSchema.JSONRPCMessage) response);
            } else {
                log.error("Unknown message type: {}", message.getClass().getSimpleName());
                return Mono.error(new RuntimeException("Unknown message type"));
            }

        } catch (Exception e) {
            log.error("Failed to handle MCP request: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
}
