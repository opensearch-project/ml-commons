/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_SSE;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ConnectorExecutor(MCP_SSE)
public class McpConnectorExecutor extends AbstractConnectorExecutor {

    @Getter
    private McpConnector connector;
    @Setter
    @Getter
    private TokenBucket rateLimiter;
    @Setter
    @Getter
    private Map<String, TokenBucket> userRateLimiterMap;
    @Setter
    @Getter
    private MLGuard mlGuard;

    public McpConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (McpConnector) connector;

        Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
    }

    public List<MLToolSpec> getMcpToolSpecs() {
        String mcpServerUrl = connector.getUrl();
        List<MLToolSpec> mcpToolSpecs = new ArrayList<>();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {

                // Create a privileged executor service
                ExecutorService executor = Executors.newCachedThreadPool(r -> {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    return thread;
                });

                Map<String, String> credentials = connector.getDecryptedCredential();
                Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
                Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());

                // Create transport
                McpClientTransport transport = HttpClientSseClientTransport.builder(mcpServerUrl).customizeClient(clientBuilder -> {
                    clientBuilder.executor(executor).connectTimeout(connectionTimeout);
                }).customizeRequest(requestBuilder -> { requestBuilder.header("Content-Type", "application/json"); }).build();

                // Create and initialize client
                McpSyncClient client = McpClient
                    .sync(transport)
                    .requestTimeout(readTimeout)
                    .capabilities(McpSchema.ClientCapabilities.builder().roots(false).build())
                    .build();

                client.initialize();
                McpSchema.ListToolsResult tools = client.listTools();

                // Process the results
                Gson gson = new Gson();
                String json = gson.toJson(tools, McpSchema.ListToolsResult.class);
                Map<?, ?> map = gson.fromJson(json, Map.class);

                List<?> mcpTools = (List<?>) map.get("tools");
                Set<String> basicMetaFields = Set.of("name", "description");

                for (Object tool : mcpTools) {
                    Map<String, String> attributes = new HashMap<>();
                    Map<?, ?> toolMap = (Map<?, ?>) tool;

                    for (Object key : toolMap.keySet()) {
                        String keyStr = (String) key;
                        if (!basicMetaFields.contains(keyStr)) {
                            // TODO: change to more flexible way
                            attributes.put("input_schema", StringUtils.toJson(toolMap.get(keyStr)));
                        }
                    }

                    MLToolSpec mlToolSpec = MLToolSpec
                        .builder()
                        .type("McpSseTool")
                        .name(toolMap.get("name").toString())
                        .description(StringUtils.processTextDoc(toolMap.get("description").toString()))
                        .attributes(attributes)
                        .build();
                    mlToolSpec.addRuntimeResource("mcp_client", client);
                    mcpToolSpecs.add(mlToolSpec);
                }
                return null;
            });

            return mcpToolSpecs;
        } catch (Exception e) {
            log.error("Failed to get MCP tools", e);
            return mcpToolSpecs;
        }
    }

    @Override
    public ScriptService getScriptService() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public Client getClient() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public void invokeRemoteService(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        throw new UnsupportedOperationException("Not implemented.");
    }
}
