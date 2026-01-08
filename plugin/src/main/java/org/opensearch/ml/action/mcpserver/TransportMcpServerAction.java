/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.ID_FIELD;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_SERVER_NOT_READY_ERROR;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpServerAction;
import org.opensearch.ml.common.transport.mcpserver.requests.server.MLMcpServerRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpServerAction extends HandledTransportAction<ActionRequest, MLMcpServerResponse> {

    MLFeatureEnabledSetting mlFeatureEnabledSetting;
    ObjectMapper objectMapper;
    McpStatelessServerHolder mcpStatelessServerHolder;

    @Inject
    public TransportMcpServerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        McpStatelessServerHolder mcpStatelessServerHolder
    ) {
        super(MLMcpServerAction.NAME, transportService, actionFilters, MLMcpServerRequest::new);
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.objectMapper = new ObjectMapper();
        this.mcpStatelessServerHolder = mcpStatelessServerHolder;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpServerResponse> listener) {
        try {
            if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
                listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
                return;
            }
            MLMcpServerRequest mlMcpServerRequest = MLMcpServerRequest.fromActionRequest(request);

            OpenSearchMcpStatelessServerTransportProvider transportProvider = mcpStatelessServerHolder
                .getMcpStatelessServerTransportProvider();
            if (transportProvider == null) {
                log.error("MCP handler not ready - server initialization failed");
                handleError(
                    null,
                    JSON_RPC_SERVER_NOT_READY_ERROR,
                    "MCP transport provider not ready - server may not be properly initialized",
                    listener
                );
                return;
            }

            // Get the already-parsed and validated message from the request
            final McpSchema.JSONRPCMessage message = mlMcpServerRequest.getMessage();

            if (message instanceof McpSchema.JSONRPCNotification) {
                listener.onResponse(new MLMcpServerResponse(true, null, null));
                return;
            }

            // Requests: capture id for any downstream error mapping
            final Object id = (message instanceof McpSchema.JSONRPCRequest) ? ((McpSchema.JSONRPCRequest) message).id() : null;

            transportProvider.handleRequest(message).subscribe(response -> {
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
            log.error("Failed to handle stateless MCP request", e);
            handleError(null, JSON_RPC_INTERNAL_ERROR, "Internal server error: " + e.getMessage(), listener);
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
