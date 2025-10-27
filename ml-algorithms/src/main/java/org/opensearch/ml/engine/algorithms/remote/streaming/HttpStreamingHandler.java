/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.ml.common.agui.BaseEvent;
import org.opensearch.ml.common.agui.RunFinishedEvent;
import org.opensearch.ml.common.agui.ToolCallArgsEvent;
import org.opensearch.ml.common.agui.ToolCallEndEvent;
import org.opensearch.ml.common.agui.ToolCallStartEvent;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.algorithms.remote.ConnectorUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

@Log4j2
public class HttpStreamingHandler extends BaseStreamingHandler {

    private final Connector connector;
    private OkHttpClient okHttpClient;
    private String llmInterface;
    private Map<String, String> parameters;

    public HttpStreamingHandler(String llmInterface, Connector connector, ConnectorClientConfig connectorClientConfig) {
        this(llmInterface, connector, connectorClientConfig, null);
    }

    public HttpStreamingHandler(
        String llmInterface,
        Connector connector,
        ConnectorClientConfig connectorClientConfig,
        Map<String, String> parameters
    ) {
        this.connector = connector;
        this.llmInterface = llmInterface;
        this.parameters = parameters;

        Duration connectionTimeout = Duration.ofSeconds(connectorClientConfig.getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(connectorClientConfig.getReadTimeout());

        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                this.okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(connectionTimeout)
                    .readTimeout(readTimeout)
                    .retryOnConnectionFailure(true)
                    .build();
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OkHttpClient", e);
        }
    }

    @Override
    public void startStream(
        String action,
        Map<String, String> parameters,
        String payload,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        try {
            log.info("Creating SSE connection for streaming request");
            EventSourceListener listener = new HTTPEventSourceListener(actionListener, llmInterface, parameters);
            Request request = ConnectorUtils.buildOKHttpStreamingRequest(action, connector, parameters, payload);

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                EventSources.createFactory(okHttpClient).newEventSource(request, listener);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to start HTTP streaming", e);
            handleError(e, actionListener);
        }
    }

    @Override
    public void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener) {
        log.error("HTTP streaming error", error);
        listener.onFailure(new MLException("Fail to execute streaming", error));
    }

    public final class HTTPEventSourceListener extends EventSourceListener {
        private StreamPredictActionListener<MLTaskResponse, ?> streamActionListener;
        private final String llmInterface;
        private final boolean isAGUIAgent;
        private AtomicBoolean isStreamClosed;
        private boolean functionCallInProgress = false;
        private boolean agentExecutionInProgress = false;
        private String accumulatedToolCallId = null;
        private String accumulatedToolName = null;
        private String accumulatedArguments = "";

        public HTTPEventSourceListener(
            StreamPredictActionListener<MLTaskResponse, ?> streamActionListener,
            String llmInterface,
            Map<String, String> parameters
        ) {
            this.streamActionListener = streamActionListener;
            this.llmInterface = llmInterface;
            this.isStreamClosed = new AtomicBoolean(false);

            this.isAGUIAgent = parameters != null
                && (parameters.containsKey(AGUI_PARAM_THREAD_ID) || parameters.containsKey(AGUI_PARAM_RUN_ID));

            if (isAGUIAgent) {
                log.debug("HttpStreamingHandler: Detected AG-UI agent");
            }
        }

        /***
         * Callback when the SSE endpoint connection is made.
         * @param eventSource the event source
         * @param response the response
         */
        @Override
        public void onOpen(EventSource eventSource, Response response) {
            log.debug("Connected to SSE Endpoint.");
        }

        /***
         * For each event received from the SSE endpoint
         * @param eventSource The event source
         * @param id The id of the event
         * @param type The type of the event which is used to filter
         * @param data The event data
         */
        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            log.debug("The data is: {}", data);
            switch (llmInterface) {
                case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                    onOpenAIEvent(data);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unsupported llm interface: %s", llmInterface));
            }
        }

        /***
         * When the connection is closed we receive this even which is currently only logged.
         * @param eventSource The event source
         */
        @Override
        public void onClosed(EventSource eventSource) {
            log.debug("SSE CLOSED.");
        }

        /***
         * If there is any failure we log the error and the stack trace
         * During stream resets with no errors we set the connected flag to false to allow the main thread to attempt a re-connect
         * @param eventSource The event source
         * @param t The error object
         * @param response The response
         */
        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            if (t != null) {
                // Network/connection error
                log.error("Error: " + t.getMessage(), t);
                if (t instanceof StreamResetException && t.getMessage().contains("NO_ERROR")) {
                    // TODO: reconnect
                } else {
                    streamActionListener.onFailure(new MLException("SSE failure with network error", t));
                }
            } else if (response != null) {
                // HTTP error (e.g., 400 Bad Request)
                try {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    streamActionListener.onFailure(new MLException("Error from remote service: " + errorBody));
                } catch (IOException e) {
                    streamActionListener.onFailure(new MLException("SSE failure - unable to read error details"));
                }
            } else {
                // Unknown failure
                streamActionListener.onFailure(new MLException("SSE failure"));
            }
        }

        private void onOpenAIEvent(String data) {
            if ("[DONE]".equals(data)) {
                handleDoneEvent();
                return;
            }

            // Process stream chunk
            try {
                Map<String, Object> dataMap = gson.fromJson(data, Map.class);
                processStreamChunk(dataMap);
            } catch (Exception e) {
                log.debug("Skipping malformed chunk: {}", data);
            }
        }

        private void handleDoneEvent() {
            if (!agentExecutionInProgress) {
                sendCompletionResponse(isStreamClosed, streamActionListener);
            }
        }

        private void processStreamChunk(Map<String, Object> dataMap) {
            String finishReason = extractPath(dataMap, "$.choices[0].finish_reason");
            if ("stop".equals(finishReason)) {
                agentExecutionInProgress = false;
                sendCompletionResponse(isStreamClosed, streamActionListener);
                return;
            }

            String content = extractPath(dataMap, "$.choices[0].delta.content");
            if (content != null && !content.isEmpty()) {
                sendContentResponse(content, false, streamActionListener);
            }

            List<?> toolCalls = extractPath(dataMap, "$.choices[0].delta.tool_calls");
            if (toolCalls != null) {
                if (isAGUIAgent) {
                    processAGUIToolCalls(toolCalls);
                } else {
                    accumulateFunctionCall(toolCalls);
                    sendContentResponse(StringUtils.toJson(toolCalls), false, streamActionListener);
                }
            }

            if ("tool_calls".equals(finishReason) && functionCallInProgress) {
                completeToolCall();
            }
        }

        private <T> T extractPath(Map<String, Object> dataMap, String path) {
            try {
                return JsonPath.read(dataMap, path);
            } catch (Exception e) {
                return null;
            }
        }

        private void completeToolCall() {
            agentExecutionInProgress = true;

            if (isAGUIAgent) {
                List<String> backendToolNames = getBackendToolNames();
                boolean isBackendTool = backendToolNames.contains(accumulatedToolName);

                if (isBackendTool) {
                    String completeFunctionCall = buildCompleteFunctionCallResponse();
                    sendContentResponse(completeFunctionCall, false, streamActionListener);
                    Map<String, Object> response = gson.fromJson(completeFunctionCall, Map.class);
                    ModelTensorOutput output = createModelTensorOutput(response);
                    streamActionListener.onResponse(new MLTaskResponse(output));
                } else {
                    List<BaseEvent> events = List
                        .of(
                            new ToolCallEndEvent(accumulatedToolCallId),
                            new RunFinishedEvent(parameters.get(AGUI_PARAM_THREAD_ID), parameters.get(AGUI_PARAM_RUN_ID), null)
                        );
                    sendAGUIEvents(events, true, streamActionListener);
                }
            } else {
                String completeFunctionCall = buildCompleteFunctionCallResponse();
                sendContentResponse(completeFunctionCall, false, streamActionListener);
                Map<String, Object> response = gson.fromJson(completeFunctionCall, Map.class);
                ModelTensorOutput output = createModelTensorOutput(response);
                streamActionListener.onResponse(new MLTaskResponse(output));
            }

            functionCallInProgress = false;
        }

        private String buildCompleteFunctionCallResponse() {
            Map<String, Object> function = Map.of("name", accumulatedToolName, "arguments", accumulatedArguments);
            Map<String, Object> toolCall = Map.of("id", accumulatedToolCallId, "type", "function", "function", function);
            Map<String, Object> message = Map.of("tool_calls", List.of(toolCall));
            Map<String, Object> choice = Map.of("message", message, "finish_reason", "tool_calls");
            Map<String, Object> response = Map.of("choices", List.of(choice));

            return StringUtils.toJson(response);
        }

        private ModelTensorOutput createModelTensorOutput(Map<String, Object> responseData) {
            ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(responseData).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            return ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        }

        private void processAGUIToolCalls(List<?> toolCalls) {
            functionCallInProgress = true;
            List<String> backendToolNames = getBackendToolNames();

            for (Object toolCall : toolCalls) {
                Map<String, Object> tcMap = (Map<String, Object>) toolCall;

                if (tcMap.containsKey("id")) {
                    String toolCallId = (String) tcMap.get("id");
                    if (accumulatedToolCallId == null) {
                        accumulatedToolCallId = toolCallId;
                    }
                }

                if (tcMap.containsKey("function")) {
                    Map<String, Object> func = (Map<String, Object>) tcMap.get("function");

                    if (func.containsKey("name")) {
                        String toolName = (String) func.get("name");
                        if (accumulatedToolName == null) {
                            accumulatedToolName = toolName;

                            if (!backendToolNames.contains(toolName)) {
                                List<BaseEvent> events = List.of(new ToolCallStartEvent(accumulatedToolCallId, toolName, null));
                                sendAGUIEvents(events, false, streamActionListener);
                            }
                        }
                    }

                    if (func.containsKey("arguments")) {
                        String argsDelta = (String) func.get("arguments");
                        accumulatedArguments += argsDelta;

                        List<BaseEvent> argsEvents = convertToAGUIEvents(
                            ToolCallState.ARGS_DELTA,
                            accumulatedToolCallId,
                            accumulatedToolName,
                            argsDelta,
                            backendToolNames
                        );
                        if (!argsEvents.isEmpty()) {
                            sendAGUIEvents(argsEvents, false, streamActionListener);
                        }
                    }
                }
            }
        }

        private void accumulateFunctionCall(List<?> toolCalls) {
            functionCallInProgress = true;
            for (Object toolCall : toolCalls) {
                Map<String, Object> tcMap = (Map<String, Object>) toolCall;

                if (tcMap.containsKey("id")) {
                    accumulatedToolCallId = (String) tcMap.get("id");
                }
                if (tcMap.containsKey("function")) {
                    Map<String, Object> func = (Map<String, Object>) tcMap.get("function");
                    if (func.containsKey("name")) {
                        accumulatedToolName = (String) func.get("name");
                    }
                    if (func.containsKey("arguments")) {
                        accumulatedArguments += (String) func.get("arguments");
                    }
                }
            }
        }
    }

    @Override
    public List<BaseEvent> convertToAGUIEvents(
        ToolCallState toolCallState,
        String toolCallId,
        String toolName,
        String toolArgsDelta,
        List<String> backendToolNames
    ) {
        if (backendToolNames != null && backendToolNames.contains(toolName)) {
            log.debug("AG-UI: Skipping AG-UI events for backend tool '{}'", toolName);
            return List.of();
        }

        switch (toolCallState) {
            case START:
                log.debug("AG-UI: Generating TOOL_CALL_START event for frontend tool '{}'", toolName);
                return List.of(new ToolCallStartEvent(toolCallId, toolName, null));

            case ARGS_DELTA:
                log
                    .debug(
                        "AG-UI: Generating TOOL_CALL_ARGS event with delta length: {}",
                        toolArgsDelta != null ? toolArgsDelta.length() : 0
                    );
                return List.of(new ToolCallArgsEvent(toolCallId, toolArgsDelta));

            case END:
                log.debug("AG-UI: Generating TOOL_CALL_END event for tool '{}'", toolName);
                return List.of(new ToolCallEndEvent(toolCallId));

            default:
                return List.of();
        }
    }

    private List<String> getBackendToolNames() {
        if (parameters == null) {
            log.debug("AG-UI: parameters is null, returning empty backend tool names list");
            return List.of();
        }

        String backendToolNamesJson = parameters.get("backend_tool_names");
        if (backendToolNamesJson == null || backendToolNamesJson.isEmpty()) {
            log.debug("AG-UI: backend_tool_names parameter not found or empty");
            return List.of();
        }

        try {
            Type listType = new TypeToken<List<String>>() {
            }.getType();
            List<String> backendToolNames = new Gson().fromJson(backendToolNamesJson, listType);
            log.debug("AG-UI: Loaded {} backend tool names: {}", backendToolNames != null ? backendToolNames.size() : 0, backendToolNames);
            return backendToolNames != null ? backendToolNames : List.of();
        } catch (Exception e) {
            log.warn("AG-UI: Failed to parse backend tool names", e);
            return List.of();
        }
    }
}
