/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.ml.common.agui.RunErrorEvent;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.transport.MLTaskResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

/**
 * Streaming handler for Agent Proxy.
 * Consumes SSE stream from external proxy and forwards validated AGUI events to client.
 */
@Log4j2
public class AgentProxyStreamingHandler extends BaseStreamingHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final String proxyUrl;
    private final Map<String, String> headers;
    private OkHttpClient okHttpClient;

    public AgentProxyStreamingHandler(String proxyUrl, Map<String, String> headers, ConnectorClientConfig clientConfig) {
        this.proxyUrl = proxyUrl;
        this.headers = headers;

        Duration connectionTimeout = Duration.ofSeconds(clientConfig.getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(clientConfig.getReadTimeout());

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
            throw new RuntimeException("Failed to build OkHttpClient for Agent Proxy", e);
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
            log.info("Starting Agent Proxy SSE connection to: {}", proxyUrl);

            EventSourceListener listener = new AgentProxyEventSourceListener(actionListener);
            Request.Builder requestBuilder = new Request.Builder()
                .url(proxyUrl)
                .post(okhttp3.RequestBody.create(payload, okhttp3.MediaType.parse("application/json")));

            // Add headers (auth, content-type, etc.)
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (value != null) {
                        requestBuilder.addHeader(key, value);
                    }
                });
            }

            // Set SSE accept header
            requestBuilder.addHeader("Accept", "text/event-stream");

            Request request = requestBuilder.build();

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                EventSources.createFactory(okHttpClient).newEventSource(request, listener);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to start Agent Proxy streaming", e);
            handleError(e, actionListener);
        }
    }

    @Override
    public void handleError(Throwable error, StreamPredictActionListener<MLTaskResponse, ?> listener) {
        log.error("Agent Proxy streaming error", error);

        sendAGUIEvent(new RunErrorEvent("Agent Proxy error: " + error.getMessage(), "PROXY_ERROR"), false, listener);
        listener.onFailure(new MLException("Agent Proxy streaming failed", error));
    }

    /**
     * EventSourceListener for Agent Proxy SSE events.
     */
    public final class AgentProxyEventSourceListener extends EventSourceListener {
        private StreamPredictActionListener<MLTaskResponse, ?> streamActionListener;
        private AtomicBoolean isStreamClosed;

        public AgentProxyEventSourceListener(StreamPredictActionListener<MLTaskResponse, ?> streamActionListener) {
            this.streamActionListener = streamActionListener;
            this.isStreamClosed = new AtomicBoolean(false);
        }

        @Override
        public void onOpen(EventSource eventSource, Response response) {
            log.debug("Agent Proxy SSE connection opened");
        }

        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            if (isStreamClosed.get()) {
                log.debug("Stream already closed, ignoring event");
                return;
            }

            if (data == null || data.trim().isEmpty()) {
                log.debug("Empty event data, skipping");
                return;
            }

            // Handle [DONE] signal (OpenAI-style)
            if ("[DONE]".equals(data.trim())) {
                log.debug("Received [DONE] signal");
                sendCompletionResponse(isStreamClosed, streamActionListener);
                return;
            }

            String eventType = getEventType(data);

            // Skip RUN_STARTED from remote agent (REST layer sends its own)
            if ("RUN_STARTED".equals(eventType)) {
                return;
            }

            // Check if this is the final event
            boolean isLast = "RUN_FINISHED".equals(eventType) || "RUN_ERROR".equals(eventType);

            // Forward raw JSON to client
            sendContentResponse(data, isLast, streamActionListener);

            if (isLast) {
                isStreamClosed.set(true);
                log.debug("Received final event: {}", eventType);
            }
        }

        @Override
        public void onClosed(EventSource eventSource) {
            log.debug("Agent Proxy SSE connection closed");
            sendCompletionResponse(isStreamClosed, streamActionListener);
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            if (isStreamClosed.get()) {
                log.debug("Stream already closed, ignoring failure");
                return;
            }

            String errorMessage = "Agent Proxy connection failed";
            if (response != null) {
                errorMessage += " (HTTP " + response.code() + ")";
            }
            if (t != null) {
                errorMessage += ": " + t.getMessage();
            }

            log.error(errorMessage, t);
            sendErrorEvent(errorMessage, true);
            isStreamClosed.set(true);
        }

        /**
         * Send error event to client.
         */
        private void sendErrorEvent(String message, boolean isLast) {
            sendAGUIEvent(new RunErrorEvent(message, "PROXY_ERROR"), isLast, streamActionListener);
        }
    }

    /**
     * Extract the "type" field from a JSON string using Jackson.
     */
    private static String getEventType(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode typeNode = node.get("type");
            return (typeNode != null && !typeNode.isNull()) ? typeNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
