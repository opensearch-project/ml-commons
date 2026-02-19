/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_FORWARDED_PROPS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_FORWARDED_PROPS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.ml.common.agent.ConnectorSpec;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.streaming.AgentProxyStreamingHandler;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.log4j.Log4j2;

/**
 * Executor for Agent Proxy requests.
 * Builds AGUI JSON payload from AgentMLInput and forwards to external proxy via SSE streaming.
 */
@Log4j2
public class AgentProxyConnectorExecutor {

    private final ConnectorSpec connectorSpec;
    private final ConnectorClientConfig clientConfig;

    public AgentProxyConnectorExecutor(ConnectorSpec connectorSpec) {
        this.connectorSpec = connectorSpec;

        // Build client config from connector spec timeouts
        this.clientConfig = ConnectorClientConfig
            .builder()
            .connectionTimeout(connectorSpec.getConnectionTimeout())
            .readTimeout(connectorSpec.getReadTimeout())
            .build();
    }

    /**
     * Execute agent proxy request with streaming.
     *
     * @param action Action name (not used for proxy)
     * @param mlInput ML input containing AGUI parameters
     * @param parameters Additional parameters
     * @param executionContext Execution context
     * @param actionListener Streaming action listener
     */
    public void executeStream(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        ExecutionContext executionContext,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        try {
            log.info("Executing agent proxy request to: {}", connectorSpec.getProxyUrl());

            // Build AGUI JSON payload from parameters
            String aguiPayload = buildAGUIPayload(parameters);
            log.debug("Built AGUI payload for proxy: {}", aguiPayload);

            // Build authentication headers
            Map<String, String> headers = buildHeaders();

            // Create streaming handler
            AgentProxyStreamingHandler streamingHandler = new AgentProxyStreamingHandler(
                connectorSpec.getProxyUrl(),
                headers,
                clientConfig
            );

            // Start streaming
            streamingHandler.startStream(action, parameters, aguiPayload, actionListener);

        } catch (Exception e) {
            log.error("Failed to execute agent proxy request", e);
            actionListener.onFailure(new MLException("Failed to execute agent proxy request", e));
        }
    }

    /**
     * Build AGUI JSON payload from parameters.
     * Reconstructs the original AGUI format from stored parameters.
     *
     * @param parameters Parameters containing AGUI fields
     * @return AGUI JSON string
     */
    private String buildAGUIPayload(Map<String, String> parameters) {
        JsonObject aguiJson = new JsonObject();

        // Add required fields
        String threadId = parameters.get(AGUI_PARAM_THREAD_ID);
        String runId = parameters.get(AGUI_PARAM_RUN_ID);

        if (threadId == null || runId == null) {
            throw new IllegalArgumentException("Missing required AGUI fields: threadId or runId");
        }

        aguiJson.addProperty(AGUI_FIELD_THREAD_ID, threadId);
        aguiJson.addProperty(AGUI_FIELD_RUN_ID, runId);

        // Add state (default to empty object if not present)
        String stateJson = parameters.get(AGUI_PARAM_STATE);
        if (stateJson != null && !stateJson.isEmpty()) {
            aguiJson.add(AGUI_FIELD_STATE, JsonParser.parseString(stateJson));
        } else {
            aguiJson.add(AGUI_FIELD_STATE, new JsonObject());
        }

        // Add messages array
        String messagesJson = parameters.get(AGUI_PARAM_MESSAGES);
        if (messagesJson != null && !messagesJson.isEmpty()) {
            aguiJson.add(AGUI_FIELD_MESSAGES, JsonParser.parseString(messagesJson));
        } else {
            throw new IllegalArgumentException("Missing required AGUI field: messages");
        }

        // Add tools array (default to empty array if not present)
        String toolsJson = parameters.get(AGUI_PARAM_TOOLS);
        if (toolsJson != null && !toolsJson.isEmpty()) {
            aguiJson.add(AGUI_FIELD_TOOLS, JsonParser.parseString(toolsJson));
        } else {
            aguiJson.add(AGUI_FIELD_TOOLS, JsonParser.parseString("[]"));
        }

        // Add context array (default to empty array if not present)
        String contextJson = parameters.get(AGUI_PARAM_CONTEXT);
        if (contextJson != null && !contextJson.isEmpty()) {
            aguiJson.add(AGUI_FIELD_CONTEXT, JsonParser.parseString(contextJson));
        } else {
            aguiJson.add(AGUI_FIELD_CONTEXT, JsonParser.parseString("[]"));
        }

        // Add forwarded props (default to empty object if not present)
        String forwardedPropsJson = parameters.get(AGUI_PARAM_FORWARDED_PROPS);
        if (forwardedPropsJson != null && !forwardedPropsJson.isEmpty()) {
            aguiJson.add(AGUI_FIELD_FORWARDED_PROPS, JsonParser.parseString(forwardedPropsJson));
        } else {
            aguiJson.add(AGUI_FIELD_FORWARDED_PROPS, new JsonObject());
        }

        return aguiJson.toString();
    }

    /**
     * Build HTTP headers including authentication.
     *
     * @return Map of header name to value
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();

        // Add Content-Type
        headers.put("Content-Type", "application/json");

        // Add authentication headers based on auth type
        if (connectorSpec.getAuthType() != null && connectorSpec.getCredential() != null) {
            String authType = connectorSpec.getAuthType().toLowerCase();
            Map<String, String> credential = connectorSpec.getCredential();

            switch (authType) {
                case "bearer_token":
                    // Authorization: Bearer <token>
                    String token = credential.get("api_key");
                    if (token != null) {
                        headers.put("Authorization", "Bearer " + token);
                        log.debug("Added bearer token authentication");
                    }
                    break;

                case "api_key":
                    // X-API-Key: <key>
                    String apiKey = credential.get("api_key");
                    if (apiKey != null) {
                        headers.put("X-API-Key", apiKey);
                        log.debug("Added API key authentication");
                    }
                    break;

                case "basic_auth":
                    // Authorization: Basic <base64(username:password)>
                    String username = credential.get("username");
                    String password = credential.get("password");
                    if (username != null && password != null) {
                        String auth = username + ":" + password;
                        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                        headers.put("Authorization", "Basic " + encodedAuth);
                        log.debug("Added basic authentication");
                    }
                    break;

                default:
                    log.warn("Unsupported auth type for agent proxy: {}", authType);
                    break;
            }
        }

        return headers;
    }
}
