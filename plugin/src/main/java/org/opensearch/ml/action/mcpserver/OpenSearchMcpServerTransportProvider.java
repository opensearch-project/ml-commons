/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.CommonValue.MCP_SESSION_MANAGEMENT_INDEX;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.lease.Releasable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.http.HttpChunk;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.transport.client.node.NodeClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.util.Assert;
import lombok.extern.log4j.Log4j2;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is the implementation of McpServerTransportProvider that handles client connection request and client
 * message requests, when client connects to Mcp server, a McpServerSession will be generated and stored in memory.
 * When message request comes, the session will be retrieved from the local memory to handle the request.
 */
@Log4j2
public class OpenSearchMcpServerTransportProvider implements McpServerTransportProvider {
    /**
     * Event type for sending the message endpoint URI to clients.
     */
    public static final String ENDPOINT_EVENT_TYPE = "endpoint";

    private final ObjectMapper objectMapper;

    private McpServerSession.Factory sessionFactory;

    /**
     * Map of active client sessions, keyed by session ID.
     */
    private final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();

    public OpenSearchMcpServerTransportProvider(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");

        this.objectMapper = objectMapper;
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (sessions.isEmpty()) {
            log.debug("No active sessions to broadcast message to");
            return Mono.empty();
        }

        log.debug("Attempting to broadcast message to {} active sessions", sessions.size());

        return Flux
            .fromStream(sessions.values().stream())
            .flatMap(
                session -> session
                    .sendNotification(method, params)
                    .doOnError(e -> log.error("Failed to " + "send message to session " + "{}: {}", session.getId(), e.getMessage()))
                    .onErrorComplete()
            )
            .then();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Flux
            .fromIterable(sessions.values())
            .doFirst(() -> log.debug("Initiating graceful shutdown with {} active sessions", sessions.size()))
            .flatMap(McpServerSession::closeGracefully)
            .then();
    }

    /**
     * Handles new SSE connection requests from clients. Creates a new session for each
     * connection and sets up the SSE event stream.
     */
    public Mono<HttpChunk> handleSseConnection(StreamingRestChannel channel, String nodeId, NodeClient client) {
        return Mono.create(sink -> {
            OpenSearchMcpSessionTransport sessionTransport = new OpenSearchMcpSessionTransport(channel);
            McpServerSession session = sessionFactory.create(sessionTransport);
            String sessionId = session.getId();
            ActionListener<IndexResponse> actionListener = ActionListener.wrap(r -> {
                if (r != null && r.status() == RestStatus.CREATED) {
                    log.debug("Created new SSE connection for session: {}", sessionId);
                    sessions.put(sessionId, session);

                    // Send initial endpoint event
                    log.debug("Sending initial endpoint event to session: {}", sessionId);
                    String result = String.format("/sse/message?sessionId=%s", sessionId);
                    McpAsyncServerHolder.CHANNELS.put(sessionId, channel);
                    sink.success(createHttpChunk(ENDPOINT_EVENT_TYPE, result));
                }
            }, e -> {
                log.error("Failed to write sessionId into MCP session management index", e);
                sink.error(e);
            });
            Map<String, Object> source = ImmutableMap.of("node_id", nodeId, "status", "active", "create_time", Instant.now());
            IndexRequest indexRequest = new IndexRequest(MCP_SESSION_MANAGEMENT_INDEX).id(sessionId).source(source);
            client.index(indexRequest, actionListener);
        });
    }

    public Mono<Void> handleMessage(String sessionId, String requestBody) {
        McpServerSession session = sessions.get(sessionId);
        if (session == null) {
            log.error("Session not found: {}", sessionId);
            return Mono.error(new McpError("Session not found"));
        }
        return Mono.just(requestBody).flatMap(body -> {
            try {
                McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body);
                return session.handle(message);
            } catch (IllegalArgumentException | IOException e) {
                log.error("Failed to deserialize message: {}", e.getMessage());
                return Mono.error(new McpError("Invalid message format"));
            }
        }).onErrorResume(Mono::error);
    }

    private HttpChunk createHttpChunk(String event, String jsonText) {
        String result = String.format("event: %s\ndata: %s\n\n", event, jsonText);
        BytesReference content = BytesReference.fromByteBuffer(ByteBuffer.wrap(result.getBytes(StandardCharsets.UTF_8)));
        return new HttpChunk() {
            @Override
            public void close() {
                if (content instanceof Releasable) {
                    ((Releasable) content).close();
                }
            }

            @Override
            public boolean isLast() {
                return false;
            }

            @Override
            public BytesReference content() {
                return content;
            }
        };
    }

    public class OpenSearchMcpSessionTransport implements McpServerTransport {

        /**
         * Event type for JSON-RPC messages sent through the SSE connection.
         */
        public static final String MESSAGE_EVENT_TYPE = "message";

        private final StreamingRestChannel streamingRestChannel;

        public OpenSearchMcpSessionTransport(StreamingRestChannel streamingRestChannel) {
            this.streamingRestChannel = streamingRestChannel;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromSupplier(() -> writeValueAsString(message)).doOnNext(jsonText -> {
                HttpChunk event = createHttpChunk(MESSAGE_EVENT_TYPE, jsonText);
                streamingRestChannel.sendChunk(event);
            }).doOnError(e -> {
                Throwable exception = Exceptions.unwrap(e);
                try {
                    streamingRestChannel.sendResponse(new BytesRestResponse(streamingRestChannel, new IllegalStateException(exception)));
                } catch (IOException ex) {
                    log.error("Failed to send error response during sending message", ex);
                }
            }).then();
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
            return objectMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        private String writeValueAsString(McpSchema.JSONRPCMessage message) {
            try {
                return objectMapper.writeValueAsString(message);
            } catch (JsonProcessingException e) {
                log.error("Failed to convert the JSONRPCMessage to raw String", e);
                throw new RuntimeException(e);
            }
        }
    }

}
