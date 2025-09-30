/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;

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

@Log4j2
public class BedrockStreamingHandler extends BaseStreamingHandler {

    private final SdkAsyncHttpClient httpClient;
    private final AwsConnector connector;
    private static final Pattern TOOL_USE_ID_PATTERN = Pattern.compile("ToolUseId=([^,\\)]+)");
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("Name=([^,\\)]+)");
    private static final String STOP_REASON_TOOL_USE = "StopReason=tool_use";

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
            AtomicBoolean functionCallInProgress = new AtomicBoolean(false);
            AtomicBoolean agentExecutionInProgress = new AtomicBoolean(false);
            AtomicReference<String> toolName = new AtomicReference<>();
            AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>();
            AtomicReference<String> toolUseId = new AtomicReference<>();
            StringBuilder toolInputAccumulator = new StringBuilder();

            // Build Bedrock client
            BedrockRuntimeAsyncClient bedrockClient = buildBedrockRuntimeAsyncClient();

            // Parse payload to build ConverseStreamRequest
            ConverseStreamRequest request = buildConverseStreamRequest(payload, parameters);

            // Build response handler with comprehensive raw logging
            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder().onResponse(response -> {}).onError(error -> {
                log.error("Converse stream error: {}", error.getMessage());
                listener.onFailure(new MLException("Error from remote service: " + error.getMessage(), error));
            }).onComplete(() -> {
                if (!functionCallInProgress.get() && !agentExecutionInProgress.get()) {
                    sendCompletionResponse(isStreamClosed, listener);
                } else {
                    log.debug("Function call or agent execution in progress - keeping stream open");
                }
            }).subscriber(event -> {
                handleStreamEvent(
                    event,
                    listener,
                    isStreamClosed,
                    functionCallInProgress,
                    agentExecutionInProgress,
                    toolName,
                    toolInput,
                    toolUseId,
                    toolInputAccumulator
                );
            }).build();

            // Start streaming
            bedrockClient.converseStream(request, handler);
        } catch (Exception e) {
            log.error("Failed to execute Bedrock streaming", e);
            handleError(e, listener);
        }
    }

    private ConverseStreamRequest buildConverseStreamRequest(String payload, Map<String, String> parameters) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payloadJson = mapper.readTree(payload);
            ConverseStreamRequest.Builder requestBuilder = ConverseStreamRequest.builder().modelId(parameters.get("model"));

            // Add system messages if present
            if (payloadJson.has("system")) {
                requestBuilder.system(parseSystemMessages(payloadJson.get("system")));
            }

            // Add messages if present
            if (payloadJson.has("messages")) {
                requestBuilder.messages(parseMessages(payloadJson.get("messages")));
            }

            // Add tool configuration if present
            if (payloadJson.has("toolConfig")) {
                requestBuilder.toolConfig(parseToolConfig(payloadJson.get("toolConfig")));
            }
            return requestBuilder.build();
        } catch (Exception e) {
            throw new MLException("Failed to parse payload for Bedrock request", e);
        }
    }

    private void handleStreamEvent(
        ConverseStreamOutput event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean isStreamClosed,
        AtomicBoolean functionCallInProgress,
        AtomicBoolean agentExecutionInProgress,
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder toolInputAccumulator
    ) {
        switch (event.sdkEventType()) {
            case CONTENT_BLOCK_DELTA:
                ContentBlockDeltaEvent contentEvent = (ContentBlockDeltaEvent) event;

                // Check if this is a tool use delta
                if (contentEvent.delta().toolUse() != null) {
                    log.debug("Tool use delta detected: {}", contentEvent);
                    accumulateToolInput(contentEvent.delta().toolUse().input(), toolInput, toolInputAccumulator);
                    return;
                }

                // Handle regular text content
                if (contentEvent.delta().text() != null) {
                    String chunk = contentEvent.delta().text();
                    sendContentResponse(chunk, false, listener);
                }
                break;

            case CONTENT_BLOCK_START:
                log.debug("Content block started");
                String[] toolInfo = extractToolInfoFromContentBlockStart(event);
                toolName.set(toolInfo[0]);
                toolUseId.set(toolInfo[1]);
                break;

            case CONTENT_BLOCK_STOP:
                log.debug("Content block stop");
                break;

            case MESSAGE_START:
                log.debug("Message started");
                break;

            case MESSAGE_STOP:
                log.debug("Message completed");

                // Check if this is a tool use stop reason
                if (event.toString().contains(STOP_REASON_TOOL_USE)) {
                    functionCallInProgress.set(true);
                    agentExecutionInProgress.set(true);
                    listener.onResponse(createToolUseResponse(toolName, toolInput, toolUseId));
                }

                // Close only if no function call or agent execution in progress
                if (!functionCallInProgress.get() && !agentExecutionInProgress.get()) {
                    sendCompletionResponse(isStreamClosed, listener);
                } else {
                    log.debug("Function call or agent execution in progress - keeping stream open");
                }
                break;

            case METADATA:
                log.debug("Stream metadata: {}", event);
                break;

            default:
                log.warn("Unsupported event type: {}", event.sdkEventType());
                listener.onFailure(new IllegalArgumentException("Unsupported streaming event type: " + event.sdkEventType()));
                break;
        }
    }

    @Override
    public void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener) {
        log.error("HTTP streaming error", error);
        listener.onFailure(new MLException("Fail to execute streaming", error));
    }

    private MLTaskResponse createToolUseResponse(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId
    ) {
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

    private String[] extractToolInfoFromContentBlockStart(ConverseStreamOutput event) {
        String eventString = event.toString();
        String toolName = extractWithPattern(eventString, TOOL_NAME_PATTERN);
        String toolUseId = extractWithPattern(eventString, TOOL_USE_ID_PATTERN);
        log.debug("Extracted tool - name: {}, id: {}", toolName, toolUseId);
        return new String[] { toolName, toolUseId };
    }

    private String extractWithPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private void accumulateToolInput(
        String inputFragment,
        AtomicReference<Map<String, Object>> toolInput,
        StringBuilder toolInputAccumulator
    ) {
        if (inputFragment != null) {
            toolInputAccumulator.append(inputFragment);

            // Try to parse as JSON
            String accumulated = toolInputAccumulator.toString();
            if (accumulated.startsWith("{") && accumulated.endsWith("}")) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> parsedInput = mapper.readValue(accumulated, Map.class);
                    toolInput.set(parsedInput);
                    log.debug("Parse tool input: {}", toolInput);
                } catch (Exception e) {
                    log.debug("Input not yet complete or invalid JSON: {}", accumulated);
                }
            }
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
            return Document.fromList(node.findValues("").stream().map(this::buildDocumentFromJsonNode).collect(Collectors.toList()));
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
