/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Log4j2
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor extends AbstractConnectorExecutor {

    @Getter
    private HttpConnector connector;
    @Setter
    @Getter
    private ScriptService scriptService;

    @Setter
    @Getter
    private TokenBucket rateLimiter;
    @Setter
    @Getter
    private Map<String, TokenBucket> userRateLimiterMap;
    @Setter
    @Getter
    private Client client;
    @Setter
    @Getter
    private MLGuard mlGuard;
    @Setter
    private volatile AtomicBoolean connectorPrivateIpEnabled;

    private SdkAsyncHttpClient httpClient;

    private OkHttpClient okHttpClient;
    @Setter
    @Getter
    private StreamTransportService streamTransportService;

    public HttpJsonConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (HttpConnector) connector;
        Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());
        Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection);
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                this.okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .retryOnConnectionFailure(true)
                    .build();
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to build OkHttpClient.", e);
        }
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @SuppressWarnings("removal")
    @Override
    public void invokeRemoteService(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        try {
            SdkHttpFullRequest request;
            switch (connector.getActionHttpMethod(action).toUpperCase(Locale.ROOT)) {
                case "POST":
                    log.debug("original payload to remote model: " + payload);
                    validateHttpClientParameters(action, parameters);
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, POST);
                    break;
                case "GET":
                    validateHttpClientParameters(action, parameters);
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, null, GET);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(request)
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(
                    new MLSdkAsyncHttpResponseHandler(
                        executionContext,
                        actionListener,
                        parameters,
                        connector,
                        scriptService,
                        mlGuard,
                        action
                    )
                )
                .build();
            AccessController.doPrivileged((PrivilegedExceptionAction<CompletableFuture<Void>>) () -> httpClient.execute(executeRequest));
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(e);
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(new MLException("Fail to execute http connector", e));
        }
    }

    @Override
    public void invokeRemoteServiceStream(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        try {
            String llmInterface = parameters.get(LLM_INTERFACE);
            llmInterface = llmInterface.trim().toLowerCase(Locale.ROOT);
            llmInterface = StringEscapeUtils.unescapeJava(llmInterface);
            validateLLMInterface(llmInterface);

            log.info("Creating SSE connection for streaming request");
            EventSourceListener listener = new HTTPEventSourceListener(actionListener, llmInterface);
            Request request = ConnectorUtils.buildOKHttpRequestPOST(action, connector, parameters, payload);

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                EventSources.createFactory(okHttpClient).newEventSource(request, listener);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to execute streaming", e);
            actionListener.onFailure(new MLException("Fail to execute streaming", e));
        }
    }

    private void validateHttpClientParameters(String action, Map<String, String> parameters) throws Exception {
        String endpoint = connector.getActionEndpoint(action, parameters);
        URL url = new URL(endpoint);
        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        MLHttpClientFactory.validate(protocol, host, port, connectorPrivateIpEnabled);
    }

    private void validateLLMInterface(String llmInterface) {
        switch (llmInterface) {
            case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                break;
            default:
                throw new MLException(String.format("Unsupported llm interface: %s", llmInterface));
        }
    }

    public final class HTTPEventSourceListener extends EventSourceListener {
        private StreamPredictActionListener<MLTaskResponse, ?> streamActionListener;
        private final String llmInterface;
        private volatile AtomicBoolean isStreamClosed;
        private boolean functionCallInProgress = false;
        private boolean agentExecutionInProgress = false;
        private String accumulatedToolCallId = null;
        private String accumulatedToolName = null;
        private String accumulatedArguments = "";

        public HTTPEventSourceListener(StreamPredictActionListener<MLTaskResponse, ?> streamActionListener, String llmInterface) {
            this.streamActionListener = streamActionListener;
            this.llmInterface = llmInterface;
            this.isStreamClosed = new AtomicBoolean(false);
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
                    throw new MLException(String.format("Unsupported llm interface: %s", llmInterface));
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

        // TODO: refactor this further
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
            // Handle stop finish reason
            String finishReason = extractPath(dataMap, "$.choices[0].finish_reason");
            if ("stop".equals(finishReason)) {
                agentExecutionInProgress = false;
                sendCompletionResponse(isStreamClosed, streamActionListener);
                return;
            }

            // Process content
            String content = extractPath(dataMap, "$.choices[0].delta.content");
            if (content != null && !content.isEmpty()) {
                sendContentResponse(content, false, streamActionListener);
            }

            // Process tool call
            List<?> toolCalls = extractPath(dataMap, "$.choices[0].delta.tool_calls");
            if (toolCalls != null) {
                accumulateFunctionCall(toolCalls);
                sendContentResponse(StringUtils.toJson(toolCalls), false, streamActionListener);
            }

            // Handle tool_calls finish reason
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
            String completeFunctionCall = buildCompleteFunctionCallResponse();

            // Send to client and agent
            sendContentResponse(completeFunctionCall, false, streamActionListener);
            Map<String, Object> response = gson.fromJson(completeFunctionCall, Map.class);
            ModelTensorOutput output = createModelTensorOutput(response);
            streamActionListener.onResponse(new MLTaskResponse(output));

            // Reset state
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

        private void accumulateFunctionCall(List<?> toolCalls) {
            functionCallInProgress = true;
            for (Object toolCall : toolCalls) {
                Map<String, Object> tcMap = (Map<String, Object>) toolCall;

                // Extract ID and name from first chunk
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
}
