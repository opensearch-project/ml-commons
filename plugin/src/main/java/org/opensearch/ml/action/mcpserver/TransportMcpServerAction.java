package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.CommonValue.*;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
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
                log.error("MCP transport provider not ready - server may not be properly initialized");
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("message", "MCP handler not ready - server initialization failed");
                errorMessage.put("id", null);
                errorMessage.put("errorCode", JSON_RPC_SERVER_NOT_READY_ERROR);
                listener.onResponse(new MLMcpServerResponse(false, null, errorMessage));
                return;
            }

            final McpSchema.JSONRPCMessage message;
            try {
                message = McpSchema.deserializeJsonRpcMessage(objectMapper, mlMcpServerRequest.getRequestBody());
            } catch (Exception e) {
                log.error("Invalid JSON-RPC message: {}", e.getMessage());
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("message", "Parse error: " + e.getMessage());
                errorMessage.put("id", null);
                errorMessage.put("errorCode", JSON_RPC_PARSE_ERROR);
                listener.onResponse(new MLMcpServerResponse(false, null, errorMessage));
                return;
            }

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
                    log.error("Failed to serialize/send response", e);
                    Map<String, Object> errorMessage = new HashMap<>();
                    errorMessage.put("message", "Response serialization failed: " + e.getMessage());
                    errorMessage.put("id", id);
                    errorMessage.put("errorCode", JSON_RPC_INTERNAL_ERROR);
                    listener.onResponse(new MLMcpServerResponse(false, null, errorMessage));
                }
            }, error -> {
                log.error("Failed to handle MCP request", error);
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("message", "Internal server error: " + error.getMessage());
                errorMessage.put("id", id);
                errorMessage.put("errorCode", JSON_RPC_INTERNAL_ERROR);
                listener.onResponse(new MLMcpServerResponse(false, null, errorMessage));
                return;
            });
        } catch (Exception e) {
            log.error("Failed to handle stateless MCP request", e);
            Map<String, Object> errorMessage = new HashMap<>();
            errorMessage.put("message", "Internal server error: " + e.getMessage());
            errorMessage.put("id", null);
            errorMessage.put("errorCode", JSON_RPC_INTERNAL_ERROR);
            listener.onResponse(new MLMcpServerResponse(false, null, errorMessage));
        }
    }
}
