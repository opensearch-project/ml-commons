/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.CommonValue.ENDPOINT_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_DEFAULT_STREAMABLE_HTTP_ENDPOINT;
import static org.opensearch.ml.common.CommonValue.MCP_SYNC_CLIENT;
import static org.opensearch.ml.common.CommonValue.MCP_TOOLS_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_TOOL_DESCRIPTION_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_TOOL_NAME_FIELD;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.McpStreamableHttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.tools.McpStreamableHttpTool;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ConnectorExecutor(MCP_STREAMABLE_HTTP)
public class McpStreamableHttpConnectorExecutor extends AbstractConnectorExecutor {

    @Getter
    private McpStreamableHttpConnector connector;

    public McpStreamableHttpConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (McpStreamableHttpConnector) connector;
    }

    public List<MLToolSpec> getMcpToolSpecs() {
        String mcpServerUrl = connector.getUrl();
        String endpoint = Optional
            .ofNullable(connector.getParameters())
            .map(params -> params.get(ENDPOINT_FIELD))
            .orElse(MCP_DEFAULT_STREAMABLE_HTTP_ENDPOINT);
        List<MLToolSpec> mcpToolSpecs = new ArrayList<>();
        try {
            Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
            Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());

            Consumer<HttpRequest.Builder> headerConfig = builder -> {
                if (connector.getDecryptedHeaders() != null) {
                    for (Map.Entry<String, String> entry : connector.getDecryptedHeaders().entrySet()) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }
            };

            // Create streamable HTTP transport
            McpClientTransport transport = HttpClientStreamableHttpTransport
                .builder(mcpServerUrl)
                .endpoint(endpoint)
                .customizeClient(clientBuilder -> {
                    clientBuilder.connectTimeout(connectionTimeout);
                    clientBuilder.followRedirects(HttpClient.Redirect.NORMAL);
                })
                .customizeRequest(headerConfig)
                .build();

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
            @SuppressWarnings("unchecked")
            Map<String, Object> map = gson.fromJson(json, Map.class);

            @SuppressWarnings("unchecked")
            List<Object> mcpTools = (List<Object>) map.get(MCP_TOOLS_FIELD);

            for (Object tool : mcpTools) {
                @SuppressWarnings("unchecked")
                Map<String, Object> toolMap = (Map<String, Object>) tool;
                Map<String, String> attributes = new HashMap<>();
                attributes.put(TOOL_INPUT_SCHEMA_FIELD, StringUtils.toJson(toolMap.get(MCP_TOOL_INPUT_SCHEMA_FIELD)));

                String description = (toolMap.containsKey(MCP_TOOL_DESCRIPTION_FIELD))
                    ? StringUtils.processTextDoc(toolMap.get(MCP_TOOL_DESCRIPTION_FIELD).toString())
                    : McpStreamableHttpTool.DEFAULT_DESCRIPTION;
                MLToolSpec mlToolSpec = MLToolSpec
                    .builder()
                    .type(McpStreamableHttpTool.TYPE)
                    .name(toolMap.get(MCP_TOOL_NAME_FIELD).toString())
                    .description(description)
                    .attributes(attributes)
                    .build();
                mlToolSpec.addRuntimeResource(MCP_SYNC_CLIENT, client);
                mcpToolSpecs.add(mlToolSpec);
            }

            return mcpToolSpecs;
        } catch (Exception e) {
            throw new MLException("Unexpected error while getting MCP tools", e);
        }
    }

    @Override
    public ScriptService getScriptService() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public TokenBucket getRateLimiter() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public Map<String, TokenBucket> getUserRateLimiterMap() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public MLGuard getMlGuard() {
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

    @Override
    public void invokeRemoteServiceStream(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        throw new UnsupportedOperationException("Streaming is not supported for MCP connector.");
    }
}
