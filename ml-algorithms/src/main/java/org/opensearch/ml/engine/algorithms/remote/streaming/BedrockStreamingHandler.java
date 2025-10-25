/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_ASSISTANT;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.naming.AuthenticationException;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorThrottlingException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;
import software.amazon.awssdk.services.s3.model.InvalidRequestException;

@Log4j2
public class BedrockStreamingHandler extends BaseStreamingHandler {

    private final SdkAsyncHttpClient httpClient;
    private final AwsConnector connector;
    private final boolean isAGUIAgent;
    private final Map<String, String> parameters;
    private static final String STOP_REASON_TOOL_USE = "StopReason=tool_use";

    private enum StreamState {
        STREAMING_CONTENT,
        TOOL_CALL_DETECTED,
        ACCUMULATING_TOOL_INPUT,
        WAITING_FOR_TOOL_RESULT,
        COMPLETED
    }

    public BedrockStreamingHandler(SdkAsyncHttpClient httpClient, AwsConnector connector) {
        this(httpClient, connector, null);
    }

    public BedrockStreamingHandler(SdkAsyncHttpClient httpClient, AwsConnector connector, Map<String, String> parameters) {
        this.httpClient = httpClient;
        this.connector = connector;
        this.parameters = parameters;

        // Detect if this is an AG-UI agent by checking for AG-UI specific parameters
        this.isAGUIAgent = parameters != null
            && (parameters.containsKey(AGUI_PARAM_THREAD_ID) || parameters.containsKey(AGUI_PARAM_RUN_ID));

        if (isAGUIAgent) {
            log.debug("BedrockStreamingHandler: Detected AG-UI agent - raw tool use events will be filtered");
        }
    }

    @Override
    public void startStream(
        String action,
        Map<String, String> parameters,
        String payload,
        StreamPredictActionListener<MLTaskResponse, ?> listener
    ) {
        try {
            AtomicBoolean isStreamClosed = new AtomicBoolean(false);
            AtomicReference<String> toolName = new AtomicReference<>();
            AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>();
            AtomicReference<String> toolUseId = new AtomicReference<>();
            StringBuilder toolInputAccumulator = new StringBuilder();
            AtomicReference<StreamState> currentState = new AtomicReference<>(StreamState.STREAMING_CONTENT);

            // Build Bedrock client
            BedrockRuntimeAsyncClient bedrockClient = buildBedrockRuntimeAsyncClient();

            // Parse payload to build ConverseStreamRequest
            ConverseStreamRequest request = buildConverseStreamRequest(payload, parameters);

            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder().onResponse(response -> {}).onError(error -> {
                log.error("Converse stream error: {}", error.getMessage());
                if (isThrottlingError(error)) {
                    listener
                        .onFailure(
                            new RemoteConnectorThrottlingException(
                                REMOTE_SERVICE_ERROR
                                    + "The request was denied due to remote server throttling. "
                                    + "To change the retry policy and behavior, please update the connector client_config.",
                                RestStatus.BAD_REQUEST
                            )
                        );
                } else if (isClientError(error)) {
                    // 4XX errors
                    listener.onFailure(new OpenSearchStatusException(REMOTE_SERVICE_ERROR + error.getMessage(), RestStatus.BAD_REQUEST));
                } else {
                    // 5xx errors
                    listener.onFailure(new MLException(REMOTE_SERVICE_ERROR + error.getMessage(), error));
                }
            }).onComplete(() -> {
                if (currentState.get() != StreamState.WAITING_FOR_TOOL_RESULT) {
                    sendCompletionResponse(isStreamClosed, listener);
                } else {
                    log.debug("Tool execution in progress - keeping stream open");
                }
            }).subscriber(event -> {
                handleStreamEvent(event, listener, isStreamClosed, toolName, toolInput, toolUseId, toolInputAccumulator, currentState);
            }).build();

            // Start streaming
            bedrockClient.converseStream(request, handler);
        } catch (Exception e) {
            log.error("Failed to execute Bedrock streaming", e);
            handleError(e, listener);
        }
    }

    @Override
    public void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener) {
        log.error("HTTP streaming error", error);
        listener.onFailure(new MLException("Fail to execute streaming", error));
    }

    private boolean isThrottlingError(Throwable error) {
        return error.getMessage().contains("throttling")
            || error.getMessage().contains("TooManyRequestsException")
            || error.getMessage().contains("Rate exceeded");
    }

    private boolean isClientError(Throwable error) {
        return error instanceof ValidationException || error instanceof InvalidRequestException || error instanceof AuthenticationException;
    }

    private ConverseStreamRequest buildConverseStreamRequest(String payload, Map<String, String> parameters) {
        try {
            log.debug("AG-UI: Building Bedrock request from payload: {}", payload);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payloadJson = mapper.readTree(payload);

            // Log the messages array for debugging
            if (payloadJson.has("messages")) {
                JsonNode messagesArray = payloadJson.get("messages");
                log.debug("AG-UI: Messages array in payload: {}", messagesArray);

                // Check for consecutive messages with the same role (Bedrock doesn't allow this)
                String previousRole = null;
                for (int i = 0; i < messagesArray.size(); i++) {
                    JsonNode msg = messagesArray.get(i);
                    String currentRole = msg.has("role") ? msg.get("role").asText() : "unknown";
                    if (previousRole != null && previousRole.equals(currentRole)) {
                        log
                            .warn(
                                "AG-UI: Found consecutive messages with same role '{}' at index {} and {}. Bedrock requires alternating roles!",
                                currentRole,
                                i - 1,
                                i
                            );
                    }
                    previousRole = currentRole;
                }
            } else {
                log.warn("AG-UI: No messages array found in payload!");
            }

            return ConverseStreamRequest
                .builder()
                .modelId(parameters.get("model"))
                .system(getOptionalNode(payloadJson, "system").map(this::parseSystemMessages).orElse(null))
                .messages(getOptionalNode(payloadJson, "messages").map(this::parseMessages).orElse(null))
                .toolConfig(getOptionalNode(payloadJson, "toolConfig").map(this::parseToolConfig).orElse(null))
                .build();
        } catch (Exception e) {
            throw new MLException("Failed to parse payload for Bedrock request", e);
        }
    }

    private Optional<JsonNode> getOptionalNode(JsonNode json, String field) {
        return Optional.ofNullable(json.get(field));
    }

    private void handleStreamEvent(
        ConverseStreamOutput event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean isStreamClosed,
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder toolInputAccumulator,
        AtomicReference<StreamState> currentState
    ) {
        switch (currentState.get()) {
            case STREAMING_CONTENT:
                if (isToolUseDetected(event)) {
                    currentState.set(StreamState.TOOL_CALL_DETECTED);
                    extractToolInfo(event, toolName, toolUseId);
                } else if (isContentDelta(event)) {
                    sendContentResponse(getTextContent(event), false, listener);
                } else if (isStreamComplete(event)) {
                    currentState.set(StreamState.COMPLETED);
                    sendCompletionResponse(isStreamClosed, listener);
                }
                break;

            case TOOL_CALL_DETECTED:
                if (isToolInputDelta(event)) {
                    currentState.set(StreamState.ACCUMULATING_TOOL_INPUT);
                    accumulateToolInput(getToolInputFragment(event), toolInput, toolInputAccumulator);

                    // Skip streaming raw tool input for AG-UI agents
                    // The proper AG-UI events will be generated in the completion response
                    if (!isAGUIAgent) {
                        sendContentResponse(getToolInputFragment(event), false, listener);
                    } else {
                        log.debug("AG-UI: Suppressing raw tool input start chunk for AG-UI agent");
                    }
                }
                break;

            case ACCUMULATING_TOOL_INPUT:
                if (isToolInputDelta(event)) {
                    accumulateToolInput(getToolInputFragment(event), toolInput, toolInputAccumulator);

                    // Skip streaming raw tool input fragments for AG-UI agents
                    // The proper AG-UI events will be generated in the completion response
                    if (!isAGUIAgent) {
                        sendContentResponse(getToolInputFragment(event), false, listener);
                    } else {
                        log.debug("AG-UI: Suppressing raw tool input fragment chunk for AG-UI agent");
                    }
                } else if (isToolInputComplete(event)) {
                    currentState.set(StreamState.WAITING_FOR_TOOL_RESULT);
                    completeBedrockToolCall(toolName, toolInput, toolUseId, listener);
                }
                break;

            case WAITING_FOR_TOOL_RESULT:
                // Don't close stream - wait for tool execution
                log.debug("Waiting for tool result - keeping stream open");
                break;

            case COMPLETED:
                // Stream already completed
                break;
        }
    }

    // TODO: refactor the event type checker methods
    private void extractToolInfo(ConverseStreamOutput event, AtomicReference<String> toolName, AtomicReference<String> toolUseId) {
        ContentBlockStartEvent startEvent = (ContentBlockStartEvent) event;
        if (startEvent.start() != null && startEvent.start().toolUse() != null) {
            toolName.set(startEvent.start().toolUse().name());
            toolUseId.set(startEvent.start().toolUse().toolUseId());
        }
    }

    private String getTextContent(ConverseStreamOutput event) {
        ContentBlockDeltaEvent contentEvent = (ContentBlockDeltaEvent) event;
        return contentEvent.delta().text();
    }

    private String getToolInputFragment(ConverseStreamOutput event) {
        ContentBlockDeltaEvent contentEvent = (ContentBlockDeltaEvent) event;
        return contentEvent.delta().toolUse().input();
    }

    private boolean isToolUseDetected(ConverseStreamOutput event) {
        return event.sdkEventType() == ConverseStreamOutput.EventType.CONTENT_BLOCK_START;
    }

    private boolean isContentDelta(ConverseStreamOutput event) {
        return event.sdkEventType() == ConverseStreamOutput.EventType.CONTENT_BLOCK_DELTA
            && ((ContentBlockDeltaEvent) event).delta().text() != null;
    }

    private boolean isToolInputDelta(ConverseStreamOutput event) {
        return event.sdkEventType() == ConverseStreamOutput.EventType.CONTENT_BLOCK_DELTA
            && ((ContentBlockDeltaEvent) event).delta().toolUse() != null;
    }

    private boolean isStreamComplete(ConverseStreamOutput event) {
        return event.sdkEventType() == ConverseStreamOutput.EventType.MESSAGE_STOP && !event.toString().contains(STOP_REASON_TOOL_USE);
    }

    private boolean isToolInputComplete(ConverseStreamOutput event) {
        return event.sdkEventType() == ConverseStreamOutput.EventType.MESSAGE_STOP && event.toString().contains(STOP_REASON_TOOL_USE);
    }

    private void completeBedrockToolCall(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StreamPredictActionListener<MLTaskResponse, ?> listener
    ) {
        if (isAGUIAgent) {
            // Check if this is a backend tool or frontend tool
            boolean isBackendTool = isBackendTool(toolName.get());

            if (isBackendTool) {
                // Backend tools: use native Bedrock format for proper ReAct processing
                log.debug("AG-UI: Processing backend tool call '{}' in native Bedrock format", toolName.get());
                listener.onResponse(createBedrockToolUseResponse(toolName, toolInput, toolUseId));
            } else {
                // Frontend tools: convert to OpenAI format for AG-UI event generation
                log.debug("AG-UI: Processing frontend tool call '{}' in OpenAI-compatible format", toolName.get());
                String openAICompatibleResponse = buildOpenAICompatibleToolCall(toolName, toolInput, toolUseId);

                // Send content response for streaming (like OpenAI handler does)
                sendContentResponse(openAICompatibleResponse, false, listener);

                // Create ModelTensorOutput in OpenAI format for AG-UI processing
                ObjectMapper mapper = new ObjectMapper();
                try {
                    Map<String, Object> responseData = mapper.readValue(openAICompatibleResponse, Map.class);
                    ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(responseData).build();
                    ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
                    ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
                    listener.onResponse(new MLTaskResponse(output));

                    log.debug("AG-UI: Sent frontend tool call in OpenAI-compatible format for AG-UI event processing");
                } catch (Exception e) {
                    log.error("AG-UI: Failed to convert frontend tool call to OpenAI format", e);
                    // Fallback to original Bedrock format
                    listener.onResponse(createBedrockToolUseResponse(toolName, toolInput, toolUseId));
                }
            }
        } else {
            // For non-AG-UI agents, use original Bedrock format
            listener.onResponse(createBedrockToolUseResponse(toolName, toolInput, toolUseId));
        }
    }

    private String buildOpenAICompatibleToolCall(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId
    ) {
        // Convert Bedrock tool call to OpenAI-compatible format
        // This matches the format expected by MLAGUIAgentRunner.processToolCallsFromDataMap()

        ObjectMapper mapper = new ObjectMapper();
        String argumentsJson;
        try {
            argumentsJson = mapper.writeValueAsString(toolInput.get());
        } catch (Exception e) {
            argumentsJson = "{}";
        }

        Map<String, Object> function = Map.of("name", toolName.get(), "arguments", argumentsJson);

        Map<String, Object> toolCall = Map.of("id", toolUseId.get(), "type", "function", "function", function);

        Map<String, Object> message = Map.of("tool_calls", List.of(toolCall));
        Map<String, Object> choice = Map.of("message", message, "finish_reason", "tool_calls");

        Map<String, Object> response = Map.of("choices", List.of(choice));

        try {
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to serialize OpenAI-compatible response", e);
            return "{}";
        }
    }

    private MLTaskResponse createBedrockToolUseResponse(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId
    ) {
        // Original Bedrock format for backward compatibility
        Map<String, Object> wrappedResponse = Map
            .of(
                "output",
                Map
                    .of(
                        "message",
                        Map
                            .of(
                                "content",
                                List
                                    .of(
                                        Map
                                            .of(
                                                "toolUse",
                                                Map.of("name", toolName.get(), "input", toolInput.get(), "toolUseId", toolUseId.get())
                                            )
                                    )
                            )
                    ),
                "stopReason",
                "tool_use"
            );

        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(wrappedResponse).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        return new MLTaskResponse(output);
    }

    private boolean isBackendTool(String toolName) {
        if (parameters == null) {
            return false;
        }

        String backendToolNamesJson = parameters.get("backend_tool_names");
        if (backendToolNamesJson == null || backendToolNamesJson.isEmpty()) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Type listType = new TypeToken<List<String>>() {
            }.getType();
            List<String> backendToolNames = new Gson().fromJson(backendToolNamesJson, listType);
            boolean isBackend = backendToolNames.contains(toolName);
            log.debug("AG-UI: Tool '{}' is {} tool (backend tools: {})", toolName, isBackend ? "backend" : "frontend", backendToolNames);
            return isBackend;
        } catch (Exception e) {
            log.warn("AG-UI: Failed to parse backend tool names, assuming tool '{}' is frontend", toolName, e);
            return false; // Default to frontend tool if parsing fails
        }
    }

    private void accumulateToolInput(
        String inputFragment,
        AtomicReference<Map<String, Object>> toolInput,
        StringBuilder toolInputAccumulator
    ) {
        if (inputFragment == null) {
            return;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        toolInputAccumulator.append(inputFragment);
        String accumulated = toolInputAccumulator.toString();

        try {
            JsonParser parser = objectMapper.getFactory().createParser(accumulated);
            JsonToken firstToken = parser.nextToken();

            // Check if it starts with an object
            if (firstToken != JsonToken.START_OBJECT) {
                log.debug("Input does not start with an object: {}", accumulated);
                return;
            }

            // Parse through the entire structure
            int objectDepth = 1;
            while (parser.nextToken() != null) {
                JsonToken currentToken = parser.getCurrentToken();
                if (currentToken == JsonToken.START_OBJECT) {
                    objectDepth++;
                } else if (currentToken == JsonToken.END_OBJECT) {
                    objectDepth--;
                }

                // Check if a complete object is found
                if (objectDepth == 0) {
                    // Check if there's any remaining content
                    if (parser.nextToken() != null) {
                        log.debug("Extra content after JSON object: {}", accumulated);
                        return;
                    }

                    // Valid and complete JSON object found
                    Map<String, Object> parsedInput = objectMapper.readValue(accumulated, Map.class);
                    toolInput.set(parsedInput);
                    log.debug("Successfully parsed tool input: {}", parsedInput);
                    return;
                }
            }

            // JSON is incomplete
            log.debug("Incomplete JSON object: {}", accumulated);

        } catch (JsonParseException e) {
            log.debug("Invalid or incomplete JSON: {}", accumulated);
        } catch (IOException e) {
            log.error("Error parsing JSON input", e);
        }
    }

    private BedrockRuntimeAsyncClient buildBedrockRuntimeAsyncClient() {
        return java.security.AccessController.doPrivileged((java.security.PrivilegedAction<BedrockRuntimeAsyncClient>) () -> {
            AwsCredentialsProvider awsCredentialsProvider = connector.getSessionToken() != null
                ? StaticCredentialsProvider
                    .create(AwsSessionCredentials.create(connector.getAccessKey(), connector.getSecretKey(), connector.getSessionToken()))
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(connector.getAccessKey(), connector.getSecretKey()));

            return BedrockRuntimeAsyncClient
                .builder()
                .region(Region.of(connector.getRegion()))
                .credentialsProvider(awsCredentialsProvider)
                .httpClient(httpClient)
                .build();
        });
    }

    private List<SystemContentBlock> parseSystemMessages(JsonNode systemArray) {
        return systemArray
            .findValuesAsText("text")
            .stream()
            .map(text -> SystemContentBlock.builder().text(text).build())
            .collect(Collectors.toList());
    }

    private List<Message> parseMessages(JsonNode messagesArray) {
        List<Message> messages = new ArrayList<>();
        for (JsonNode messageItem : messagesArray) {
            messages.add(buildMessage(messageItem));
        }
        return messages;
    }

    private Message buildMessage(JsonNode messageItem) {
        String role = messageItem.has("role") && messageItem.get("role") != null ? messageItem.get("role").asText() : AGUI_ROLE_ASSISTANT;

        // Handle AG-UI tool result messages
        if ("tool".equals(role)) {
            return buildToolResultMessage(messageItem);
        }

        List<ContentBlock> contentBlocks = buildContentBlocks(messageItem.get("content"));
        return Message.builder().role(role).content(contentBlocks).build();
    }

    private Message buildToolResultMessage(JsonNode toolMessage) {
        // Convert AG-UI tool message format to Bedrock format
        // AG-UI format: {"role": "tool", "content": "...", "toolCallId": "..."}
        // Bedrock format: {"role": "user", "content": [{"toolResult": {"toolUseId": "...", "content": [{"text": "..."}]}}]}

        String toolCallId = toolMessage.has("toolCallId") ? toolMessage.get("toolCallId").asText() : "";
        String content = toolMessage.has("content") ? toolMessage.get("content").asText() : "";

        ContentBlock toolResultBlock = ContentBlock
            .builder()
            .toolResult(
                ToolResultBlock.builder().toolUseId(toolCallId).content(ToolResultContentBlock.builder().text(content).build()).build()
            )
            .build();

        log.debug("AG-UI: Converted tool message to Bedrock format - toolUseId: {}, content length: {}", toolCallId, content.length());

        return Message
            .builder()
            .role("user")  // Bedrock requires tool results to have "user" role
            .content(List.of(toolResultBlock))
            .build();
    }

    private List<ContentBlock> buildContentBlocks(JsonNode contentArray) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (contentArray != null && contentArray.isArray()) {
            for (JsonNode item : contentArray) {
                addContentBlock(blocks, item);
            }
        }
        return blocks;
    }

    private void addContentBlock(List<ContentBlock> blocks, JsonNode item) {
        if (item.has("text")) {
            blocks.add(ContentBlock.builder().text(item.get("text").asText()).build());
        }
        if (item.has("toolResult")) {
            blocks.add(buildToolResultBlock(item.get("toolResult")));
        }
        if (item.has("toolUse")) {
            blocks.add(buildToolUseBlock(item.get("toolUse")));
        }
    }

    private ContentBlock buildToolResultBlock(JsonNode toolResult) {
        String text = extractResultText(toolResult.get("content"));
        return ContentBlock
            .builder()
            .toolResult(
                ToolResultBlock
                    .builder()
                    .toolUseId(toolResult.get("toolUseId").asText())
                    .content(ToolResultContentBlock.builder().text(text).build())
                    .build()
            )
            .build();
    }

    private String extractResultText(JsonNode content) {
        if (content.isArray() && content.size() > 0) {
            return content.get(0).get("text").asText();
        }
        return content.isTextual() ? content.asText() : "";
    }

    private ContentBlock buildToolUseBlock(JsonNode toolUse) {
        Document input = toolUse.has("input") ? buildDocumentFromJsonNode(toolUse.get("input")) : Document.fromMap(Map.of());

        return ContentBlock
            .builder()
            .toolUse(
                software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock
                    .builder()
                    .toolUseId(toolUse.get("toolUseId").asText())
                    .name(toolUse.get("name").asText())
                    .input(input)
                    .build()
            )
            .build();
    }

    private ToolConfiguration parseToolConfig(JsonNode toolConfig) {
        if (!toolConfig.has("tools"))
            return null;

        List<Tool> tools = new ArrayList<>();
        for (JsonNode toolItem : toolConfig.get("tools")) {
            if (toolItem.has("toolSpec")) {
                tools.add(buildTool(toolItem.get("toolSpec")));
            }
        }
        return ToolConfiguration.builder().tools(tools).build();
    }

    private Tool buildTool(JsonNode toolSpec) {
        Document schema = buildDocumentFromJsonNode(toolSpec.get("inputSchema").get("json"));
        return Tool
            .builder()
            .toolSpec(
                ToolSpecification
                    .builder()
                    .name(toolSpec.get("name").asText())
                    .description(toolSpec.get("description").asText())
                    .inputSchema(ToolInputSchema.builder().json(schema).build())
                    .build()
            )
            .build();
    }

    private Document buildDocumentFromJsonNode(JsonNode node) {
        if (node.isObject()) {
            Map<String, Document> map = new HashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), buildDocumentFromJsonNode(entry.getValue())));
            return Document.fromMap(map);
        }
        if (node.isArray()) {
            List<Document> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(buildDocumentFromJsonNode(item));
            }
            return Document.fromList(list);
        }
        if (node.isTextual())
            return Document.fromString(node.asText());
        if (node.isBoolean())
            return Document.fromBoolean(node.asBoolean());
        if (node.isNumber())
            return Document.fromNumber(node.isInt() ? node.asInt() : node.asDouble());
        return Document.fromString(node.toString());
    }
}
