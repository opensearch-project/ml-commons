/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.rest.RestRequest.Method.POST;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.action.mcpserver.McpStatelessServerHolder;
import org.opensearch.ml.action.mcpserver.OpenSearchMcpStatelessServerTransportProvider;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.transport.client.node.NodeClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMcpStatelessStreamingAction extends BaseRestHandler {

    private static final String ML_STATELESS_MCP_ACTION = "ml_stateless_mcp_action";
    public static final String STATELESS_ENDPOINT = "/_plugins/_ml/mcp/stream";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final ObjectMapper objectMapper;

    public RestMcpStatelessStreamingAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(POST, STATELESS_ENDPOINT));
    }

    @Override
    public String getName() {
        return ML_STATELESS_MCP_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }

        return channel -> {
            try {
                if (request.content() == null) {
                    sendErrorResponse(channel, null, -32700, "Parse error: empty body");
                    return;
                }
                final String requestBody = request.content().utf8ToString();
                if (requestBody == null || requestBody.isBlank()) {
                    sendErrorResponse(channel, null, -32700, "Parse error: empty body");
                    return;
                }

                OpenSearchMcpStatelessServerTransportProvider transportProvider = McpStatelessServerHolder
                    .getMcpStatelessServerTransportProvider();
                if (transportProvider == null || !transportProvider.isHandlerReady()) {
                    log.error("MCP transport provider not ready - server may not be properly initialized");
                    // Server-side failure: unknown id -> null, server error range
                    sendErrorResponse(channel, null, -32000, "MCP handler not ready - server initialization failed");
                    return;
                }

                // Parse to distinguish request vs notification and extract id (if present)
                final McpSchema.JSONRPCMessage msg;
                try {
                    msg = McpSchema.deserializeJsonRpcMessage(objectMapper, requestBody);
                } catch (Exception e) {
                    log.warn("Invalid JSON-RPC message: {}", e.getMessage());
                    // Parse error: id unknown
                    sendErrorResponse(channel, null, -32700, "Parse error: " + e.getMessage());
                    return;
                }

                if (msg instanceof McpSchema.JSONRPCNotification) {
                    // Notifications: acknowledge and do not process
                    channel.sendResponse(new BytesRestResponse(RestStatus.ACCEPTED, "application/json", BytesArray.EMPTY));
                    return;
                }

                // Requests: capture id for any downstream error mapping
                final Object id = (msg instanceof McpSchema.JSONRPCRequest) ? ((McpSchema.JSONRPCRequest) msg).id() : null;

                transportProvider.handleRequest(requestBody).subscribe(response -> {
                    try {
                        String responseJson = objectMapper.writeValueAsString(response);
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, "application/json", responseJson));
                    } catch (Exception e) {
                        log.error("Failed to serialize/send response", e);
                        sendErrorResponse(channel, id, -32603, "Failed to send response");
                    }
                }, error -> {
                    log.error("Failed to handle MCP request", error);
                    sendErrorResponse(channel, id, -32603, "Internal server error: " + error.getMessage());
                });

            } catch (Exception e) {
                log.error("Failed to handle stateless MCP request", e);
                // Transport-level catch-all after POST delivered: map to JSON-RPC internal
                sendErrorResponse(channel, null, -32603, "Internal server error");
            }
        };
    }

    /**
     * Sends a JSON-RPC 2.0 error envelope with HTTP 200 OK (per JSON-RPC over HTTP conventions).
     * Use real HTTP 4xx/5xx only for transport-layer failures (wrong method, unsupported media type, etc).
     */
    private void sendErrorResponse(RestChannel channel, Object id, int code, String message) {
        try {
            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("id", id);
            errorResponse.put("error", Map.of("code", code, "message", message));

            String responseJson = objectMapper.writeValueAsString(errorResponse);
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, "application/json", responseJson));
        } catch (Exception e) {
            log.error("Failed to send error response", e);
            try {
                channel
                    .sendResponse(
                        new BytesRestResponse(
                            RestStatus.INTERNAL_SERVER_ERROR,
                            "application/json",
                            "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Failed to send error response\"}}"
                        )
                    );
            } catch (Exception inner) {
                log.error("Even fallback error response failed", inner);
            }
        }
    }

}
