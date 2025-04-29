/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * This class holds the singleton instance of the MCP server.
 * It provides access to the server instance and its configuration.
 */
public class McpAsyncServerHolder {

    public static OpenSearchMcpServerTransportProvider mcpServerTransportProvider = new OpenSearchMcpServerTransportProvider(
        new ObjectMapper()
    );

    public static final McpAsyncServer asyncServer = McpServer
        .async(mcpServerTransportProvider)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
        .serverInfo("OpenSearch-MCP-Server", "1.0.0")
        .build();
}
