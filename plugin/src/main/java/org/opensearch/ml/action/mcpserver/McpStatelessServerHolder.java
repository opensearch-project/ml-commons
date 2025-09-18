/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.plugin.MachineLearningPlugin.MCP_TOOLS_SYNC_THREAD_POOL;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.common.collect.Tuple;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Log4j2
public class McpStatelessServerHolder {

    private final McpToolsHelper mcpToolsHelper;
    private final Client client;
    private final ThreadPool threadPool;
    private static final int SYNC_MCP_TOOLS_JOB_INTERVAL = 10;
    public static Map<String, Long> IN_MEMORY_MCP_TOOLS = new ConcurrentHashMap<>();
    private static volatile McpStatelessAsyncServer mcpStatelessAsyncServer;
    private static volatile OpenSearchMcpStatelessServerTransportProvider mcpStatelessServerTransportProvider;
    private static volatile Boolean initialized = false;

    public McpStatelessServerHolder(McpToolsHelper mcpToolsHelper, Client client, ThreadPool threadPool) {
        this.mcpToolsHelper = mcpToolsHelper;
        this.client = client;
        this.threadPool = threadPool;
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        synchronized (McpStatelessServerHolder.class) {
            if (initialized) {
                return;
            }
            try {
                mcpStatelessServerTransportProvider = new OpenSearchMcpStatelessServerTransportProvider(new ObjectMapper());

                McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities
                    .builder()
                    .tools(true)
                    .logging()
                    .resources(false, false)  // We don't support resources
                    .prompts(false)           // We don't support prompts
                    .build();

                log.info("Building MCP server without pre-loaded tools (dynamic loading)...");
                mcpStatelessAsyncServer = McpServer
                    .async(mcpStatelessServerTransportProvider)
                    .serverInfo("OpenSearch-MCP-Stateless-Server", "0.1.0")
                    .capabilities(serverCapabilities)
                    .instructions("OpenSearch MCP Stateless Server - provides access to ML tools without sessions")
                    .build();

                log.info("Stateless MCP server created successfully");

                autoLoadAllMcpTools(
                    ActionListener
                        .wrap(
                            success -> log.info("Initial tool loading completed successfully"),
                            error -> log.error("Initial tool loading failed", error)
                        )
                );
                initialized = true;

            } catch (Exception e) {
                log.error("Failed to create stateless MCP server", e);
                throw new RuntimeException("Failed to create stateless MCP server", e);
            }
        }

    }

    public OpenSearchMcpStatelessServerTransportProvider getMcpStatelessServerTransportProvider() {
        if (initialized) {
            return mcpStatelessServerTransportProvider;
        }

        initialize();
        return mcpStatelessServerTransportProvider;
    }

    public McpStatelessAsyncServer getMcpStatelessAsyncServerInstance() {
        if (initialized) {
            return mcpStatelessAsyncServer;
        }

        initialize();
        return mcpStatelessAsyncServer;
    }

    public void autoLoadAllMcpTools(ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> searchListener = ActionListener.wrap(r -> {
                r.forEach((key, value) -> {
                    // Use putIfAbsent to make check-and-act atomic
                    Long previousVersion = IN_MEMORY_MCP_TOOLS.putIfAbsent(key, value.v2());
                    if (previousVersion == null) {
                        // We successfully added the key, now add the tool
                        getMcpStatelessAsyncServerInstance()
                            .addTool(mcpToolsHelper.createToolSpecification(value.v1()))
                            .doOnError(x -> {
                                // If tool addition fails, remove from memory cache
                                IN_MEMORY_MCP_TOOLS.remove(key);
                                log.error("Failed to auto load tool: {}", value.v1().getName(), x);
                            })
                            .subscribe();
                    } else if (previousVersion < value.v2()) {
                        // Chain the operations to avoid race conditions
                        getMcpStatelessAsyncServerInstance().removeTool(key).onErrorResume(e -> {
                            log.warn("Failed to remove old tool version: {}", key, e);
                            return Mono.empty();
                        })
                            .then(getMcpStatelessAsyncServerInstance().addTool(mcpToolsHelper.createToolSpecification(value.v1())))
                            .doOnSuccess(x -> {
                                IN_MEMORY_MCP_TOOLS.put(key, value.v2());
                                log.info("Successfully updated tool: {} to version: {}", key, value.v2());
                            })
                            .doOnError(x -> log.error("Failed to update tool: {} to version: {}", value.v1().getName(), value.v2(), x))
                            .subscribe();
                    }
                });
                startSyncMcpToolsJob();
                restoreListener.onResponse(true);
            }, e -> {
                log.error("Failed to auto load all MCP tools to MCP server", e);
                restoreListener.onFailure(e);
            });
            mcpToolsHelper.searchAllToolsWithVersion(searchListener);
        } catch (Exception e) {
            log.error("Failed to auto load all MCP tools to MCP server", e);
            listener.onFailure(e);
        }
    }

    /**
     * Start the sync job for auto-reloading MCP tools
     */
    public void startSyncMcpToolsJob() {
        ActionListener<Boolean> listener = ActionListener
            .wrap(r -> { log.debug("Auto reload mcp tools schedule job run successfully!"); }, e -> {
                log.error(e.getMessage(), e);
            });
        threadPool
            .schedule(
                () -> autoLoadAllMcpTools(listener),
                TimeValue.timeValueSeconds(SYNC_MCP_TOOLS_JOB_INTERVAL),
                MCP_TOOLS_SYNC_THREAD_POOL
            );
    }

}
