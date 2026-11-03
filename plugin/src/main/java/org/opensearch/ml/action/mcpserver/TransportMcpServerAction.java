/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.ID_FIELD;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_PARSE_ERROR;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpServerAction;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.transport.mcpserver.requests.server.MLMcpServerRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.log4j.Log4j2;
import tools.jackson.databind.json.JsonMapper;

@Log4j2
public class TransportMcpServerAction extends HandledTransportAction<ActionRequest, MLMcpServerResponse> {

    MLFeatureEnabledSetting mlFeatureEnabledSetting;
    JsonMapper objectMapper;
    McpToolsHelper mcpToolsHelper;

    private static final JacksonMcpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(JsonMapper.shared());
    private static final DefaultJsonSchemaValidator SCHEMA_VALIDATOR = new DefaultJsonSchemaValidator();

    @Inject
    public TransportMcpServerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        McpToolsHelper mcpToolsHelper
    ) {
        super(MLMcpServerAction.NAME, transportService, actionFilters, MLMcpServerRequest::new);
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.objectMapper = JsonMapper.builder().build();
        this.mcpToolsHelper = mcpToolsHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpServerResponse> listener) {
        try {
            if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
                listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
                return;
            }
            MLMcpServerRequest mlMcpServerRequest = MLMcpServerRequest.fromActionRequest(request);

            final McpSchema.JSONRPCMessage message;
            try {
                message = McpSchema.deserializeJsonRpcMessage(new JacksonMcpJsonMapper(objectMapper), mlMcpServerRequest.getRequestBody());
            } catch (Exception e) {
                log.error("Parse error: " + e.getMessage(), e);
                handleError(null, JSON_RPC_PARSE_ERROR, "Parse error: " + e.getMessage(), listener);
                return;
            }

            if (message instanceof McpSchema.JSONRPCNotification) {
                listener.onResponse(new MLMcpServerResponse(true, null, null));
                return;
            }

            // Requests: capture id for any downstream error mapping
            final Object id = (message instanceof McpSchema.JSONRPCRequest) ? ((McpSchema.JSONRPCRequest) message).id() : null;

            // Build a fresh stateless MCP server per request, loading tools from the index.
            mcpToolsHelper.searchAllTools(ActionListener.wrap(tools -> buildServerAndHandle(tools, message, id, listener), error -> {
                log.error("Failed to load MCP tools: " + error.getMessage(), error);
                handleError(id, JSON_RPC_INTERNAL_ERROR, "Internal server error: " + error.getMessage(), listener);
            }));
        } catch (Exception e) {
            log.error("Failed to handle stateless MCP request", e);
            handleError(null, JSON_RPC_INTERNAL_ERROR, "Internal server error: " + e.getMessage(), listener);
        }
    }

    private void buildServerAndHandle(
        List<McpToolRegisterInput> tools,
        McpSchema.JSONRPCMessage message,
        Object id,
        ActionListener<MLMcpServerResponse> listener
    ) {
        try {
            // Skip tools that fail to build (e.g. a tool whose plugin is no longer installed) so one
            // bad tool doesn't fail tools/list and tools/call for every other tool.
            List<McpStatelessServerFeatures.AsyncToolSpecification> specs = new ArrayList<>();
            for (McpToolRegisterInput tool : tools) {
                try {
                    specs.add(mcpToolsHelper.createToolSpecification(tool));
                } catch (Exception e) {
                    log.error("Skipping MCP tool that failed to build: {}", tool.getName(), e);
                }
            }

            McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities
                .builder()
                .tools(true)
                .logging()
                .resources(false, false)
                .prompts(false)
                .build();

            OpenSearchMcpStatelessServerTransportProvider provider = new OpenSearchMcpStatelessServerTransportProvider();
            McpServer
                .async(provider)
                .jsonMapper(JSON_MAPPER)
                .jsonSchemaValidator(SCHEMA_VALIDATOR)
                .serverInfo("OpenSearch-MCP-Stateless-Server", "0.1.0")
                .capabilities(serverCapabilities)
                .instructions("OpenSearch MCP Stateless Server - provides access to ML tools without sessions")
                .tools(specs)
                .build();

            provider.handleRequest(message).subscribe(response -> {
                try {
                    String responseJson = objectMapper.writeValueAsString(response);
                    listener.onResponse(new MLMcpServerResponse(true, responseJson, null));
                } catch (Exception e) {
                    log.error("Response serialization failed: " + e.getMessage(), e);
                    handleError(id, JSON_RPC_INTERNAL_ERROR, "Response serialization failed: " + e.getMessage(), listener);
                }
            }, error -> {
                log.error("Internal server error: " + error.getMessage(), error);
                handleError(id, JSON_RPC_INTERNAL_ERROR, "Internal server error: " + error.getMessage(), listener);
            });
        } catch (Exception e) {
            log.error("Failed to build stateless MCP server: " + e.getMessage(), e);
            handleError(id, JSON_RPC_INTERNAL_ERROR, "Internal server error: " + e.getMessage(), listener);
        }
    }

    /**
     * Creates an error response for MCP server errors
     */
    private void handleError(Object id, int errorCode, String responseMessage, ActionListener<MLMcpServerResponse> listener) {
        Map<String, Object> errorMessage = new HashMap<>();
        errorMessage.put(MESSAGE_FIELD, responseMessage);
        errorMessage.put(ID_FIELD, id);
        errorMessage.put(ERROR_CODE_FIELD, errorCode);
        listener.onResponse(new MLMcpServerResponse(false, null, errorMessage));
    }
}
