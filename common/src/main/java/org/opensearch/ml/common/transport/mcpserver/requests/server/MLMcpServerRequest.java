/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.utils.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLMcpServerRequest extends ActionRequest {

    private static final int MAX_ID_LENGTH = 1000;
    private static final int MAX_REQUEST_SIZE = 10 * 1024 * 1024;
    private static final Set<String> VALID_METHODS = Set
        .of(
            McpSchema.METHOD_INITIALIZE,
            McpSchema.METHOD_NOTIFICATION_INITIALIZED,
            McpSchema.METHOD_PING,
            McpSchema.METHOD_NOTIFICATION_PROGRESS,
            McpSchema.METHOD_TOOLS_LIST,
            McpSchema.METHOD_TOOLS_CALL,
            McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED,
            McpSchema.METHOD_RESOURCES_LIST,
            McpSchema.METHOD_RESOURCES_READ,
            McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED,
            McpSchema.METHOD_NOTIFICATION_RESOURCES_UPDATED,
            McpSchema.METHOD_RESOURCES_TEMPLATES_LIST,
            McpSchema.METHOD_RESOURCES_SUBSCRIBE,
            McpSchema.METHOD_RESOURCES_UNSUBSCRIBE,
            McpSchema.METHOD_PROMPT_LIST,
            McpSchema.METHOD_PROMPT_GET,
            McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED,
            McpSchema.METHOD_COMPLETION_COMPLETE,
            McpSchema.METHOD_LOGGING_SET_LEVEL,
            McpSchema.METHOD_NOTIFICATION_MESSAGE,
            McpSchema.METHOD_ROOTS_LIST,
            McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED,
            McpSchema.METHOD_SAMPLING_CREATE_MESSAGE,
            McpSchema.METHOD_ELICITATION_CREATE
        );

    @Getter
    private McpSchema.JSONRPCMessage message;

    public MLMcpServerRequest(StreamInput in) throws IOException {
        super(in);
        validateAndParseRequest(in.readString());
    }

    public MLMcpServerRequest(String requestBody) {
        validateAndParseRequest(requestBody);
    }

    private void validateAndParseRequest(String requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            throw new IllegalArgumentException("Request body cannot be null or empty");
        }
        if (requestBody.length() > MAX_REQUEST_SIZE) {
            throw new IllegalArgumentException("Request body exceeds maximum size of " + MAX_REQUEST_SIZE + " bytes");
        }

        try {
            message = McpSchema.deserializeJsonRpcMessage(new ObjectMapper(), requestBody);
        } catch (Exception e) {
            log.error("Parse error: " + e.getMessage(), e);
            throw new IllegalArgumentException("Failed to parse JSON-RPC message: " + e.getMessage(), e);
        }

        validateMessage();
    }

    private void validateMessage() {
        if (!McpSchema.JSONRPC_VERSION.equals(message.jsonrpc())) {
            throw new IllegalArgumentException("Invalid jsonrpc version. Expected '2.0' but got '" + message.jsonrpc() + "'");
        }

        if (message instanceof McpSchema.JSONRPCRequest request) {
            validateRequestId(request.id());
            validateMethod(request.method());
        } else if (message instanceof McpSchema.JSONRPCNotification notification) {
            validateMethod(notification.method());
        } else if (message instanceof McpSchema.JSONRPCResponse) {
            throw new IllegalArgumentException("JSON-RPC responses are not accepted as incoming messages");
        } else {
            throw new IllegalArgumentException("Unknown JSON-RPC message type: " + message.getClass().getName());
        }
    }

    private void validateRequestId(Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Request ID cannot be null");
        }
        if (!(id instanceof String || id instanceof Integer || id instanceof Long)) {
            throw new IllegalArgumentException("Request ID must be a string or integer, but got: " + id.getClass().getSimpleName());
        }
        if (id instanceof String) {
            String idStr = (String) id;
            if (idStr.length() > MAX_ID_LENGTH) {
                throw new IllegalArgumentException("Request ID exceeds maximum length of " + MAX_ID_LENGTH + " characters");
            }
            if (!StringUtils.matchesSafePattern(idStr)) {
                throw new IllegalArgumentException("Request ID " + StringUtils.SAFE_INPUT_DESCRIPTION);
            }
        }
    }

    private void validateMethod(String method) {
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("Method cannot be null or empty");
        }
        if (!VALID_METHODS.contains(method)) {
            throw new IllegalArgumentException("Invalid MCP method: '" + method + "'. Must be one of the supported MCP methods.");
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        // Serialize the message back to JSON string
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonString = objectMapper.writeValueAsString(message);
            out.writeString(jsonString);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to serialize JSON-RPC message", e);
        }
    }

    public static MLMcpServerRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMcpServerRequest) {
            return (MLMcpServerRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput in = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpServerRequest(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpServerRequest", e);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
