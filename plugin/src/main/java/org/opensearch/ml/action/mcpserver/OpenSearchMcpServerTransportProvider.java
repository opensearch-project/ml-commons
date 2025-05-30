/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.OpenSearchException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.lease.Releasable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.http.HttpChunk;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
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
import reactor.core.publisher.MonoSink;

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

    private final MLIndicesHandler mlIndicesHandler;

    private final McpToolsHelper mcpToolsHelper;

    /**
     * Map of active client sessions, keyed by session ID.
     */
    private final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();

    public OpenSearchMcpServerTransportProvider(
        MLIndicesHandler mlIndicesHandler,
        McpToolsHelper mcpToolsHelper,
        ObjectMapper objectMapper
    ) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        this.mlIndicesHandler = mlIndicesHandler;
        this.mcpToolsHelper = mcpToolsHelper;
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
        McpAsyncServerHolder.CHANNELS.clear();
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
    public Mono<HttpChunk> handleSseConnection(StreamingRestChannel channel, boolean appendToBaseUrl, String nodeId, NodeClient client) {
        return Mono.create(sink -> {
            OpenSearchMcpSessionTransport sessionTransport = new OpenSearchMcpSessionTransport(channel);
            McpServerSession session = sessionFactory.create(sessionTransport);
            String sessionId = session.getId();
            ActionListener<Boolean> initIndexListener = ActionListener.wrap(created -> {
                if (created) {
                    log.debug("Successfully created MCP session management index");
                    addSession(sessionId, session, appendToBaseUrl, nodeId, client, channel, sink);
                } else {
                    log.debug("Failed to create MCP session management index for session: {}", sessionId);
                    sink
                        .error(
                            new IllegalStateException(
                                String.format(Locale.ROOT, "Failed to create MCP session management index for session: %s", sessionId)
                            )
                        );
                }
            }, e -> {
                log.error("Failed to create session management index for session: {}", sessionId);
                sink.error(new IllegalStateException("Failed to create session management index for session" + sessionId));
            });
            mlIndicesHandler.initMLMcpSessionManagementIndex(initIndexListener);
        });
    }

    private void addSession(
        String sessionId,
        McpServerSession session,
        boolean appendToBaseUrl,
        String nodeId,
        NodeClient client,
        StreamingRestChannel channel,
        MonoSink<HttpChunk> sink
    ) {
        ActionListener<IndexResponse> actionListener = ActionListener.wrap(r -> {
            if (r != null && r.status() == RestStatus.CREATED) {
                reloadAllMcpTools(sessionId, session, appendToBaseUrl, channel, sink);
            } else {
                log.error("Failed to create new SSE connection for session: {}", sessionId);
                sink.error(new IllegalStateException("Failed to create new SSE connection for session" + sessionId));
            }
        }, e -> {
            log.error("Failed to write sessionId into MCP session management index", e);
            sink.error(e);
        });
        Map<String, Object> source = ImmutableMap.of("node_id", nodeId, "status", "active", CommonValue.CREATE_TIME_FIELD, Instant.now());
        IndexRequest indexRequest = new IndexRequest(MLIndex.MCP_SESSION_MANAGEMENT.getIndexName()).id(sessionId).source(source);
        client.index(indexRequest, actionListener);
    }

    private void reloadAllMcpTools(
        String sessionId,
        McpServerSession session,
        boolean appendToBaseUrl,
        StreamingRestChannel channel,
        MonoSink<HttpChunk> sink
    ) {
        if (this.sessions.isEmpty()) {
            ActionListener<Boolean> reloadMcpToolsListener = ActionListener.wrap(reloadResult -> {
                if (reloadResult) {
                    initSessionInMemory(sessionId, session, appendToBaseUrl, channel, sink);
                }
            }, e -> {
                log.error("Failed to reload mcp tools", e);
                sink
                    .error(
                        new OpenSearchException(
                            String
                                .format(
                                    Locale.ROOT,
                                    "Failed to create SSE connection because the target node MCP server failed to init tools with error: %s",
                                    e.getMessage()
                                )
                        )
                    );
            });
            mcpToolsHelper.autoLoadAllMcpTools(reloadMcpToolsListener);
        } else {
            initSessionInMemory(sessionId, session, appendToBaseUrl, channel, sink);
        }
    }

    private void initSessionInMemory(
        String sessionId,
        McpServerSession session,
        boolean appendToBaseUrl,
        StreamingRestChannel channel,
        MonoSink<HttpChunk> sink
    ) {
        log.debug("Created new SSE connection for session: {}", sessionId);
        sessions.put(sessionId, session);

        // Send initial endpoint event
        log.debug("Sending initial endpoint event to session: {}", sessionId);
        String result;
        if (appendToBaseUrl) {
            result = String.format(Locale.ROOT, "/_plugins/_ml/mcp/sse/message?sessionId=%s", sessionId);
        } else {
            result = String.format(Locale.ROOT, "/sse/message?sessionId=%s", sessionId);
        }
        McpAsyncServerHolder.CHANNELS.put(sessionId, channel);
        sink.success(createHttpChunk(ENDPOINT_EVENT_TYPE, result));
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
        String result = String.format(Locale.ROOT, "event: %s\ndata: %s\n\n", event, jsonText);
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
