/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.ID_FIELD;
import static org.opensearch.ml.common.CommonValue.JSON_RPC_INTERNAL_ERROR;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpServerAction;
import org.opensearch.ml.common.transport.mcpserver.requests.server.MLMcpServerRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.transport.client.node.NodeClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMcpServerAction extends BaseRestHandler {

    private static final String ML_MCP_SERVER_ACTION = "ml_mcp_server_action";
    public static final String MCP_SERVER_ENDPOINT = "/_plugins/_ml/mcp";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final ObjectMapper objectMapper;

    public RestMcpServerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(POST, MCP_SERVER_ENDPOINT), new Route(GET, MCP_SERVER_ENDPOINT));
    }

    @Override
    public String getName() {
        return ML_MCP_SERVER_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }
        if (request.method() == RestRequest.Method.GET) {
            return channel -> { channel.sendResponse(new BytesRestResponse(RestStatus.METHOD_NOT_ALLOWED, "", BytesArray.EMPTY)); };
        }

        final String requestBody = request.content() != null ? request.content().utf8ToString() : null;
        MLMcpServerRequest mcpRequest = new MLMcpServerRequest(requestBody);

        return channel -> {
            client.execute(MLMcpServerAction.INSTANCE, mcpRequest, new ActionListener<MLMcpServerResponse>() {
                @Override
                public void onResponse(MLMcpServerResponse response) {
                    try {
                        if (response.getError() != null) {
                            // Handle error response
                            Map<String, Object> errorMap = response.getError();
                            Object id = errorMap.get(ID_FIELD);
                            int code = (Integer) errorMap.get(ERROR_CODE_FIELD);
                            String message = (String) errorMap.get(MESSAGE_FIELD);
                            sendErrorResponse(channel, id, code, message);
                        } else if (response.getMcpResponse() != null) {
                            channel.sendResponse(new BytesRestResponse(RestStatus.OK, "application/json", response.getMcpResponse()));
                        } else {
                            channel.sendResponse(new BytesRestResponse(RestStatus.ACCEPTED, "", BytesArray.EMPTY));
                        }
                    } catch (Exception e) {
                        log.error("Failed to send response", e);
                        sendErrorResponse(channel, null, JSON_RPC_INTERNAL_ERROR, "Failed to send response");
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to handle MCP request", e);
                    sendErrorResponse(channel, null, JSON_RPC_INTERNAL_ERROR, "Internal server error: " + e.getMessage());
                }
            });
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
                            "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":"
                                + JSON_RPC_INTERNAL_ERROR
                                + ",\"message\":\"Failed to send error response\"}}"
                        )
                    );
            } catch (Exception inner) {
                log.error("Even fallback error response failed", inner);
            }
        }
    }

}
