/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGE_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TEXT_MESSAGE_STARTED;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.AuthenticationException;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.agui.BaseEvent;
import org.opensearch.ml.common.agui.RunFinishedEvent;
import org.opensearch.ml.common.agui.TextMessageContentEvent;
import org.opensearch.ml.common.agui.TextMessageEndEvent;
import org.opensearch.ml.common.agui.TextMessageStartEvent;
import org.opensearch.ml.common.agui.ToolCallArgsEvent;
import org.opensearch.ml.common.agui.ToolCallEndEvent;
import org.opensearch.ml.common.agui.ToolCallStartEvent;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.agent.AgentUtils;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorThrottlingException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ResponseStream;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;
import software.amazon.awssdk.services.s3.model.InvalidRequestException;

/**
 * Streaming handler for the Bedrock InvokeModel API ({@code invokeModelWithResponseStream}).
 * <p>
 * Unlike the Converse API handler ({@link BedrockStreamingHandler}), this handler passes
 * the payload as raw bytes, allowing access to model-specific parameters such as
 * message compaction, extended thinking, and other Claude-native features.
 * <p>
 * Event parsing is delegated to an {@link InvokeModelEventParser}, defaulting to
 * {@link ClaudeInvokeModelEventParser} for Claude models. Other model families
 * can be supported by providing a different parser implementation.
 */
@Log4j2
public class BedrockInvokeModelStreamingHandler extends BaseStreamingHandler {

    private final SdkAsyncHttpClient httpClient;
    private final AwsConnector connector;
    private final Map<String, String> parameters;
    private final boolean isAGUIAgent;
    private final InvokeModelEventParser eventParser;

    private enum StreamState {
        STREAMING_CONTENT,
        TOOL_CALL_DETECTED,
        ACCUMULATING_TOOL_INPUT,
        WAITING_FOR_TOOL_RESULT,
        COMPLETED
    }

    public BedrockInvokeModelStreamingHandler(SdkAsyncHttpClient httpClient, AwsConnector connector) {
        this(httpClient, connector, null, new ClaudeInvokeModelEventParser());
    }

    public BedrockInvokeModelStreamingHandler(SdkAsyncHttpClient httpClient, AwsConnector connector, Map<String, String> parameters) {
        this(httpClient, connector, parameters, new ClaudeInvokeModelEventParser());
    }

    public BedrockInvokeModelStreamingHandler(
        SdkAsyncHttpClient httpClient,
        AwsConnector connector,
        Map<String, String> parameters,
        InvokeModelEventParser eventParser
    ) {
        this.httpClient = httpClient;
        this.connector = connector;
        this.parameters = parameters;
        this.eventParser = eventParser;

        this.isAGUIAgent = AgentUtils.isAGUIAgent(parameters);

        if (isAGUIAgent) {
            log.debug("BedrockInvokeModelStreamingHandler: Detected AG-UI agent");
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
            AtomicBoolean firstToolSent = new AtomicBoolean(false);
            AtomicReference<String> toolName = new AtomicReference<>();
            AtomicReference<Map<String, Object>> toolInput = new AtomicReference<>();
            AtomicReference<String> toolUseId = new AtomicReference<>();
            StringBuilder toolInputAccumulator = new StringBuilder();
            AtomicReference<StreamState> currentState = new AtomicReference<>(StreamState.STREAMING_CONTENT);
            AtomicReference<String> pendingStopReason = new AtomicReference<>();

            BedrockRuntimeAsyncClient bedrockClient = buildBedrockRuntimeAsyncClient();

            InvokeModelWithResponseStreamRequest request = buildInvokeModelRequest(payload, parameters);

            InvokeModelWithResponseStreamResponseHandler handler = InvokeModelWithResponseStreamResponseHandler
                .builder()
                .onResponse(response -> {})
                .onError(error -> {
                    log.error("InvokeModel stream error: {}", error.getMessage());
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
                        listener
                            .onFailure(new OpenSearchStatusException(REMOTE_SERVICE_ERROR + error.getMessage(), RestStatus.BAD_REQUEST));
                    } else {
                        listener.onFailure(new MLException(REMOTE_SERVICE_ERROR + error.getMessage(), error));
                    }
                })
                .onComplete(() -> {
                    if (currentState.get() != StreamState.WAITING_FOR_TOOL_RESULT) {
                        sendCompletionResponse(isStreamClosed, listener);
                    } else {
                        log.debug("Tool execution in progress - keeping stream open");
                    }
                })
                .subscriber(event -> {
                    log.debug("INVOKE_MODEL_RAW_EVENT: Type={}, Event={}", event.sdkEventType(), event);
                    handleResponseStreamEvent(
                        event,
                        listener,
                        isStreamClosed,
                        firstToolSent,
                        toolName,
                        toolInput,
                        toolUseId,
                        toolInputAccumulator,
                        currentState,
                        pendingStopReason
                    );
                })
                .build();

            bedrockClient.invokeModelWithResponseStream(request, handler);
        } catch (Exception e) {
            log.error("Failed to execute Bedrock InvokeModel streaming", e);
            handleError(e, listener);
        }
    }

    @Override
    public void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener) {
        log.error("InvokeModel streaming error", error);
        listener.onFailure(new MLException("Fail to execute streaming", error));
    }

    InvokeModelWithResponseStreamRequest buildInvokeModelRequest(String payload, Map<String, String> parameters) {
        String modelId = parameters.get("model");
        return InvokeModelWithResponseStreamRequest
            .builder()
            .modelId(modelId)
            .contentType("application/json")
            .accept("application/json")
            .body(SdkBytes.fromString(payload, StandardCharsets.UTF_8))
            .build();
    }

    private void handleResponseStreamEvent(
        ResponseStream event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean isStreamClosed,
        AtomicBoolean firstToolSent,
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder toolInputAccumulator,
        AtomicReference<StreamState> currentState,
        AtomicReference<String> pendingStopReason
    ) {
        // Extract JSON from PayloadPart bytes
        String jsonEvent = extractJsonFromEvent(event);
        if (jsonEvent == null) {
            return;
        }

        InvokeModelEventParser.InvokeModelEvent parsed = eventParser.parse(jsonEvent);
        if (parsed == null) {
            return;
        }

        handleParsedEvent(
            parsed,
            listener,
            isStreamClosed,
            firstToolSent,
            toolName,
            toolInput,
            toolUseId,
            toolInputAccumulator,
            currentState,
            pendingStopReason
        );
    }

    private String extractJsonFromEvent(ResponseStream event) {
        try {
            // ResponseStream events contain PayloadPart with bytes
            if (event.sdkEventType() == ResponseStream.EventType.CHUNK) {
                // PayloadPart is the chunk event type
                software.amazon.awssdk.services.bedrockruntime.model.PayloadPart payloadPart =
                    (software.amazon.awssdk.services.bedrockruntime.model.PayloadPart) event;
                if (payloadPart.bytes() != null) {
                    return payloadPart.bytes().asString(StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract JSON from InvokeModel stream event: {}", event, e);
        }
        return null;
    }

    private void handleParsedEvent(
        InvokeModelEventParser.InvokeModelEvent event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean isStreamClosed,
        AtomicBoolean firstToolSent,
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder toolInputAccumulator,
        AtomicReference<StreamState> currentState,
        AtomicReference<String> pendingStopReason
    ) {
        String messageId = (isAGUIAgent && parameters != null) ? parameters.get(AGUI_PARAM_MESSAGE_ID) : null;
        boolean textMessageStarted = (isAGUIAgent && parameters != null)
            && "true".equalsIgnoreCase(parameters.get(AGUI_PARAM_TEXT_MESSAGE_STARTED));

        switch (currentState.get()) {
            case STREAMING_CONTENT:
                handleStreamingContentState(
                    event,
                    listener,
                    isStreamClosed,
                    toolName,
                    toolUseId,
                    currentState,
                    pendingStopReason,
                    messageId,
                    textMessageStarted
                );
                break;

            case TOOL_CALL_DETECTED:
                handleToolCallDetectedState(event, listener, toolInput, toolUseId, toolInputAccumulator, currentState, messageId);
                break;

            case ACCUMULATING_TOOL_INPUT:
                handleAccumulatingToolInputState(
                    event,
                    listener,
                    firstToolSent,
                    toolName,
                    toolInput,
                    toolUseId,
                    toolInputAccumulator,
                    currentState,
                    pendingStopReason,
                    messageId
                );
                break;

            case WAITING_FOR_TOOL_RESULT:
                log.debug("Waiting for tool result - keeping stream open");
                break;

            case COMPLETED:
                break;
        }
    }

    private void handleStreamingContentState(
        InvokeModelEventParser.InvokeModelEvent event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean isStreamClosed,
        AtomicReference<String> toolName,
        AtomicReference<String> toolUseId,
        AtomicReference<StreamState> currentState,
        AtomicReference<String> pendingStopReason,
        String messageId,
        boolean textMessageStarted
    ) {
        switch (event.getType()) {
            case CONTENT_BLOCK_START_TOOL_USE:
                currentState.set(StreamState.TOOL_CALL_DETECTED);
                toolName.set(event.getToolName());
                toolUseId.set(event.getToolUseId());

                if (isAGUIAgent) {
                    if (textMessageStarted) {
                        parameters.put(AGUI_PARAM_TEXT_MESSAGE_STARTED, "false");
                        BaseEvent textMessageEndEvent = new TextMessageEndEvent(messageId);
                        sendAGUIEvent(textMessageEndEvent, false, listener);
                    }

                    BaseEvent toolCallStartEvent = new ToolCallStartEvent(toolUseId.get(), toolName.get(), messageId);
                    sendAGUIEvent(toolCallStartEvent, false, listener);
                    log.debug("AG-UI: Sent TOOL_CALL_START for messageId: {} and toolUseId: {}", messageId, toolUseId);
                }
                break;

            case TEXT_DELTA:
                if (isAGUIAgent) {
                    if (!textMessageStarted) {
                        messageId = "msg_" + System.nanoTime();
                        parameters.put(AGUI_PARAM_MESSAGE_ID, messageId);
                        parameters.put(AGUI_PARAM_TEXT_MESSAGE_STARTED, "true");

                        BaseEvent textMessageStartEvent = new TextMessageStartEvent(messageId, "assistant");
                        sendAGUIEvent(textMessageStartEvent, false, listener);
                        log.debug("AG-UI: Sent TEXT_MESSAGE_START for messageId: {}", messageId);
                    }

                    BaseEvent textMessageContentEvent = new TextMessageContentEvent(messageId, event.getText());
                    sendAGUIEvent(textMessageContentEvent, false, listener);
                } else {
                    sendContentResponse(event.getText(), false, listener);
                }
                break;

            case COMPACTION_DELTA:
                // Stream compaction summary content to the client
                if (isAGUIAgent) {
                    if (!textMessageStarted) {
                        messageId = "msg_" + System.nanoTime();
                        parameters.put(AGUI_PARAM_MESSAGE_ID, messageId);
                        parameters.put(AGUI_PARAM_TEXT_MESSAGE_STARTED, "true");

                        BaseEvent textMessageStartEvent = new TextMessageStartEvent(messageId, "assistant");
                        sendAGUIEvent(textMessageStartEvent, false, listener);
                    }

                    BaseEvent textMessageContentEvent = new TextMessageContentEvent(messageId, event.getText());
                    sendAGUIEvent(textMessageContentEvent, false, listener);
                } else {
                    sendContentResponse(event.getText(), false, listener);
                }
                break;

            case MESSAGE_DELTA:
                pendingStopReason.set(event.getStopReason());
                break;

            case MESSAGE_STOP:
                handleMessageStop(listener, isStreamClosed, currentState, pendingStopReason, messageId, textMessageStarted);
                break;

            default:
                // MESSAGE_START, CONTENT_BLOCK_START_TEXT, CONTENT_BLOCK_START_COMPACTION,
                // CONTENT_BLOCK_STOP are structural events - no action needed in this state
                break;
        }
    }

    private void handleToolCallDetectedState(
        InvokeModelEventParser.InvokeModelEvent event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder toolInputAccumulator,
        AtomicReference<StreamState> currentState,
        String messageId
    ) {
        if (event.getType() == InvokeModelEventParser.EventType.TOOL_INPUT_DELTA) {
            currentState.set(StreamState.ACCUMULATING_TOOL_INPUT);
            String inputFragment = event.getToolInputJson();
            accumulateToolInput(inputFragment, toolInput, toolInputAccumulator);

            if (isAGUIAgent) {
                parameters.put(AGUI_PARAM_TEXT_MESSAGE_STARTED, "false");
                BaseEvent toolCallArgsEvent = new ToolCallArgsEvent(toolUseId.get(), inputFragment);
                sendAGUIEvent(toolCallArgsEvent, false, listener);
                log.debug("AG-UI: Sent TOOL_CALL_ARGS for messageId: {}", messageId);
            } else {
                sendContentResponse(inputFragment, false, listener);
            }
        }
    }

    private void handleAccumulatingToolInputState(
        InvokeModelEventParser.InvokeModelEvent event,
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean firstToolSent,
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId,
        StringBuilder toolInputAccumulator,
        AtomicReference<StreamState> currentState,
        AtomicReference<String> pendingStopReason,
        String messageId
    ) {
        switch (event.getType()) {
            case TOOL_INPUT_DELTA:
                if (!firstToolSent.get()) {
                    String inputFragment = event.getToolInputJson();
                    accumulateToolInput(inputFragment, toolInput, toolInputAccumulator);

                    if (isAGUIAgent) {
                        BaseEvent toolCallArgsEvent = new ToolCallArgsEvent(toolUseId.get(), inputFragment);
                        sendAGUIEvent(toolCallArgsEvent, false, listener);
                        log.debug("AG-UI: Sent TOOL_CALL_ARGS for messageId: {}", messageId);
                    } else {
                        sendContentResponse(inputFragment, false, listener);
                    }
                }
                break;

            case CONTENT_BLOCK_STOP:
                // Current tool's input is complete
                firstToolSent.set(true);
                log.info("First tool complete, will drop events for subsequent tools");
                break;

            case MESSAGE_DELTA:
                pendingStopReason.set(event.getStopReason());
                break;

            case MESSAGE_STOP:
                String stopReason = pendingStopReason.get();
                if ("tool_use".equals(stopReason)) {
                    // Ensure toolInput is set even if it's empty
                    if (toolInput.get() == null) {
                        toolInput.set(Map.of());
                    }

                    if (isAGUIAgent) {
                        BaseEvent toolCallEndEvent = new ToolCallEndEvent(toolUseId.get());
                        sendAGUIEvent(toolCallEndEvent, false, listener);
                        log.debug("AG-UI: Sent TOOL_CALL_END event for tool '{}'", toolName.get());
                    }

                    currentState.set(StreamState.WAITING_FOR_TOOL_RESULT);
                    listener.onResponse(createToolUseResponse(toolName, toolInput, toolUseId));
                } else {
                    currentState.set(StreamState.COMPLETED);
                }
                break;

            default:
                break;
        }
    }

    private void handleMessageStop(
        StreamPredictActionListener<MLTaskResponse, ?> listener,
        AtomicBoolean isStreamClosed,
        AtomicReference<StreamState> currentState,
        AtomicReference<String> pendingStopReason,
        String messageId,
        boolean textMessageStarted
    ) {
        String stopReason = pendingStopReason.get();

        if ("tool_use".equals(stopReason)) {
            // This shouldn't happen in STREAMING_CONTENT state, but handle defensively
            log.warn("Received tool_use stop reason in STREAMING_CONTENT state");
            return;
        }

        // For end_turn, compaction, or any other stop reason, complete the stream
        if (isAGUIAgent && textMessageStarted) {
            parameters.put(AGUI_PARAM_TEXT_MESSAGE_STARTED, "false");
            BaseEvent textMessageEndEvent = new TextMessageEndEvent(messageId);
            sendAGUIEvent(textMessageEndEvent, false, listener);
            log.debug("AG-UI: Sent TEXT_MESSAGE_END for messageId: {}", messageId);

            String threadId = parameters.get(AGUI_PARAM_THREAD_ID);
            String runId = parameters.get(AGUI_PARAM_RUN_ID);
            BaseEvent runFinishedEvent = new RunFinishedEvent(threadId, runId, null);
            sendAGUIEvent(runFinishedEvent, true, listener);
            log.debug("BedrockInvokeModelStreamingHandler: Added RUN_FINISHED event - stream completed");
        }

        currentState.set(StreamState.COMPLETED);
        sendCompletionResponse(isStreamClosed, listener);
    }

    private MLTaskResponse createToolUseResponse(
        AtomicReference<String> toolName,
        AtomicReference<Map<String, Object>> toolInput,
        AtomicReference<String> toolUseId
    ) {
        if (toolName == null || toolInput == null || toolUseId == null) {
            throw new IllegalArgumentException("Tool references cannot be null");
        }
        // Emit in Converse format so BedrockConverseFunctionCalling works unchanged
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

            if (firstToken != JsonToken.START_OBJECT) {
                log.debug("Input does not start with an object: {}", accumulated);
                return;
            }

            int objectDepth = 1;
            while (parser.nextToken() != null) {
                JsonToken currentToken = parser.getCurrentToken();
                if (currentToken == JsonToken.START_OBJECT) {
                    objectDepth++;
                } else if (currentToken == JsonToken.END_OBJECT) {
                    objectDepth--;
                }

                if (objectDepth == 0) {
                    if (parser.nextToken() != null) {
                        log.debug("Extra content after JSON object: {}", accumulated);
                        return;
                    }

                    Map<String, Object> parsedInput = objectMapper.readValue(accumulated, Map.class);
                    toolInput.set(parsedInput);
                    log.debug("Successfully parsed tool input: {}", parsedInput);
                    return;
                }
            }

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

    private boolean isThrottlingError(Throwable error) {
        return error.getMessage().contains("throttling")
            || error.getMessage().contains("TooManyRequestsException")
            || error.getMessage().contains("Rate exceeded");
    }

    private boolean isClientError(Throwable error) {
        return error instanceof ValidationException || error instanceof InvalidRequestException || error instanceof AuthenticationException;
    }
}
