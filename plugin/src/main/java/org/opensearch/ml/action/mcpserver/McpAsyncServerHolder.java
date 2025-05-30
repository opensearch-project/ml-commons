/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.rest.StreamingRestChannel;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * This class holds the singleton instance of the MCP server.
 * It provides access to the server instance and its configuration.
 */
public class McpAsyncServerHolder {

    public static Map<String, StreamingRestChannel> CHANNELS = new ConcurrentHashMap<>();
    public static Map<String, Long> IN_MEMORY_MCP_TOOLS = new ConcurrentHashMap<>();
    private static volatile MLIndicesHandler mlIndicesHandler;
    private static volatile McpToolsHelper mcpToolsHelper;

    public static void init(MLIndicesHandler mlIndicesHandler, McpToolsHelper mcpToolsHelper) {
        McpAsyncServerHolder.mlIndicesHandler = mlIndicesHandler;
        McpAsyncServerHolder.mcpToolsHelper = mcpToolsHelper;
    }

    private static volatile OpenSearchMcpServerTransportProvider mcpServerTransportProvider;
    private static volatile McpAsyncServer asyncServer;

    public static OpenSearchMcpServerTransportProvider getMcpServerTransportProviderInstance() {
        if (mcpServerTransportProvider != null) {
            return mcpServerTransportProvider;
        }
        synchronized (McpAsyncServerHolder.class) {
            if (mcpServerTransportProvider != null) {
                return mcpServerTransportProvider;
            }
            mcpServerTransportProvider = new OpenSearchMcpServerTransportProvider(
                McpAsyncServerHolder.mlIndicesHandler,
                McpAsyncServerHolder.mcpToolsHelper,
                new ObjectMapper()
            );
            if (asyncServer == null) {
                asyncServer = McpServer
                    .async(mcpServerTransportProvider)
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
                    .serverInfo("OpenSearch-MCP-Server", "0.1.0")
                    .build();
            }
            return mcpServerTransportProvider;
        }
    }

    public static McpAsyncServer getMcpAsyncServerInstance() {
        if (asyncServer == null) {
            OpenSearchMcpServerTransportProvider provider = getMcpServerTransportProviderInstance();
            synchronized (McpAsyncServerHolder.class) {
                if (asyncServer == null) {
                    asyncServer = McpServer
                        .async(provider)
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
                        .serverInfo("OpenSearch-MCP-Server", "0.1.0")
                        .build();
                }
            }
        }
        return asyncServer;
    }

}
