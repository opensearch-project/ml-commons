/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;

import java.io.IOException;
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
    private static final String STOP_REASON_TOOL_USE = "StopReason=tool_use";

    private enum StreamState {
        STREAMING_CONTENT,
        TOOL_CALL_DETECTED,
        ACCUMULATING_TOOL_INPUT,
        WAITING_FOR_TOOL_RESULT,
        COMPLETED
    }

    public BedrockStreamingHandler(SdkAsyncHttpClient httpClient, AwsConnector connector) {
        this.httpClient = httpClient;
        this.connector = connector;
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
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payloadJson = mapper.readTree(payload);
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
                }
                break;

            case ACCUMULATING_TOOL_INPUT:
                if (isToolInputDelta(event)) {
                    accumulateToolInput(getToolInputFragment(event), toolInput, toolInputAccumulator);
                } else if (isToolInputComplete(event)) {
                    currentState.set(StreamState.WAITING_FOR_TOOL_RESULT);
                    listener.onResponse(createToolUseResponse(toolName, toolInput, toolUseId));
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

    @Override
    public void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener) {
        log.error("HTTP streaming error", error);
        listener.onFailure(new MLException("Fail to execute streaming", error));
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

    private MLTaskResponse createToolUseResponse(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId
    ) {
        // Validate inputs
        if (toolName == null || toolInput == null || toolUseId == null) {
            throw new IllegalArgumentException("Tool references cannot be null");
        }
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
        String role = messageItem.has("role") && messageItem.get("role") != null ? messageItem.get("role").asText() : "assistant";

        List<ContentBlock> contentBlocks = buildContentBlocks(messageItem.get("content"));
        return Message.builder().role(role).content(contentBlocks).build();
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
