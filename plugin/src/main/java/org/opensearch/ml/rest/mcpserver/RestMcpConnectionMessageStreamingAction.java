/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.ml.common.CommonValue.MCP_SESSION_MANAGEMENT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;
import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.bytes.CompositeBytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.http.HttpChunk;
import org.opensearch.ml.action.mcpserver.McpAsyncServerHolder;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpMessageAction;
import org.opensearch.ml.common.transport.mcpserver.requests.message.MLMcpMessageRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@ExperimentalApi
public class RestMcpConnectionMessageStreamingAction extends BaseRestHandler {

    private static final String MCP_ACTION = "mcp_action";
    public static final String MESSAGE_ENDPOINT = "/_plugins/_ml/mcp/sse/message";
    public static final String SSE_ENDPOINT = "/_plugins/_ml/mcp/sse";

    private final ClusterService clusterService;

    private volatile boolean mcpServerEnabled;

    public RestMcpConnectionMessageStreamingAction(ClusterService clusterService) {
        this.clusterService = clusterService;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(GET, SSE_ENDPOINT), new Route(POST, MESSAGE_ENDPOINT));
    }

    @Override
    public String getName() {
        return MCP_ACTION;
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) {
        if (!mcpServerEnabled) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }
        String path = request.path();
        final String sessionId = request.param("sessionId");
        final StreamingRestChannelConsumer consumer = (channel) -> prepareRequestInternal(path, sessionId, channel, client);
        return channel -> {
            if (channel instanceof StreamingRestChannel) {
                consumer.accept((StreamingRestChannel) channel);
            } else {
                final ActionRequestValidationException validationError = new ActionRequestValidationException();
                validationError.addValidationError("Unable to initiate request / response streaming over non-streaming channel");
                channel.sendResponse(new BytesRestResponse(channel, validationError));
            }
        };

    }

    @VisibleForTesting
    protected void prepareRequestInternal(
        final String path,
        final String sessionId,
        final StreamingRestChannel channel,
        final NodeClient client
    ) {
        channel
            .prepareResponse(
                RestStatus.OK,
                Map
                    .of(
                        "Content-Type",
                        List.of("text/event-stream"),
                        "Cache-Control",
                        List.of("no-cache"),
                        "Connection",
                        List.of("keep-alive")
                    )
            );
        if (path.equals(SSE_ENDPOINT)) {
            // The connection request doesn't have request body, but we're still reading the request body content,
            // The reason is that the response producer is created when http channel is been read, so here
            // we subscribe to channel to trigger the channel read, but ignoring the content.
            Mono
                .from(channel)
                .ofType(HttpChunk.class)
                .map(HttpChunk::content)
                .flatMap(
                    x -> McpAsyncServerHolder.mcpServerTransportProvider
                        .handleSseConnection(channel, clusterService.localNode().getId(), client)
                )
                .flatMap(y -> Mono.fromRunnable(() -> {
                    log.debug("starting to send sse connection chunk result");
                    channel.sendChunk(y);
                }))
                .onErrorResume(e -> Mono.fromRunnable(() -> {
                    try {
                        channel.sendResponse(new BytesRestResponse(channel, new Exception(e)));
                    } catch (IOException ex) {
                        log.error("Failed to send exception response to client during connection due to IOException");
                        throw new RuntimeException(ex);
                    }
                }))
                .subscribe();
        } else if (path.equals(MESSAGE_ENDPOINT)) {
            if (sessionId == null) {
                try {
                    channel
                        .sendResponse(
                            new BytesRestResponse(channel, new IllegalArgumentException("Session ID missing in message endpoint"))
                        );
                } catch (IOException ex) {
                    log.error("Failed to send exception response to client when sessionId is null");
                }
            } else {
                Flux
                    .from(channel)
                    .ofType(HttpChunk.class)
                    .takeUntil(HttpChunk::isLast)
                    .map(HttpChunk::content)
                    .reduce(CompositeBytesReference::of)
                    .doOnSuccess(x -> {
                        String requestBody = x.utf8ToString();
                        ActionListener<GetResponse> listener = ActionListener.wrap(r -> {
                            if (r.isExists()) {
                                String nodeId = String.valueOf(r.getSourceAsMap().get("node_id"));
                                DiscoveryNode node = clusterService.state().getNodes().getNodes().get(nodeId);
                                if (node == null) {
                                    log.error("The node:{} is no longer in the current cluster, can not handle the mcp request", nodeId);
                                    channel
                                        .sendResponse(
                                            new BytesRestResponse(
                                                channel,
                                                new IllegalStateException(
                                                    "Session no longer exists as corresponding node crashed, please recreate a new session in client side"
                                                )
                                            )
                                        );
                                } else {
                                    if (clusterService.localNode().getId().equals(nodeId)) {
                                        McpAsyncServerHolder.mcpServerTransportProvider
                                            .handleMessage(sessionId, requestBody)
                                            .doOnSuccess(y -> {
                                                if (requestBody.contains("notifications/initialized")) {
                                                    log
                                                        .debug(
                                                            "Starting to send OK response for notifications/initialized request in local node"
                                                        );
                                                    channel.sendChunk(createInitializedNotificationRes());
                                                }
                                            })
                                            .onErrorResume(e -> Mono.fromRunnable(() -> {
                                                try {
                                                    log.error("Error occurred when handling message", e);
                                                    channel.sendResponse(new BytesRestResponse(channel, new Exception(e)));
                                                } catch (IOException ex) {
                                                    log
                                                        .error(
                                                            "Failed to send exception response to client during message handling in local due to IOException, nodeId: {}",
                                                            nodeId
                                                        );
                                                }
                                            }))
                                            .subscribe();
                                    } else {
                                        ActionListener<AcknowledgedResponse> actionListener = ActionListener.wrap(y -> {
                                            if (y.isAcknowledged()) {
                                                log
                                                    .debug(
                                                        "MCP request has been dispatched to corresponding node and handled successfully!"
                                                    );
                                                if (requestBody.contains("notifications/initialized")) {
                                                    log
                                                        .debug(
                                                            "Starting to send OK response for notifications/initialized request in coordinator node"
                                                        );
                                                    channel.sendChunk(createInitializedNotificationRes());
                                                }
                                            }
                                        },
                                            e -> {
                                                log
                                                    .error(
                                                        "MCP request has been dispatched to corresponding node but peer node failed to handle it",
                                                        e
                                                    );
                                            }
                                        );
                                        client
                                            .execute(
                                                MLMcpMessageAction.INSTANCE,
                                                new MLMcpMessageRequest(nodeId, sessionId, requestBody),
                                                actionListener
                                            );
                                    }
                                }
                            } else {
                                log.error("SessionId not found in cluster, sessionId: {}", sessionId);
                                try {
                                    channel
                                        .sendResponse(
                                            new BytesRestResponse(
                                                channel,
                                                new IllegalArgumentException(
                                                    "SessionId not found in cluster, please try to create session first, sessionId: "
                                                        + sessionId
                                                )
                                            )
                                        );
                                } catch (IOException ex) {
                                    log
                                        .error(
                                            "Failed to send exception response to client when session ID not found in cluster state, sessionId: {}",
                                            sessionId
                                        );
                                }
                            }

                        }, e -> {
                            try {
                                channel.sendResponse(new BytesRestResponse(channel, e));
                            } catch (IOException ex) {
                                log.error("Failed to get the session management index result with sessionId: {}", sessionId);
                            }
                        });
                        getDiscoveryNode(client, sessionId, listener);
                    })
                    .onErrorResume(e -> Mono.fromRunnable(() -> {
                        try {
                            channel.sendResponse(new BytesRestResponse(channel, new Exception(e)));
                        } catch (IOException ex) {
                            log.error("Failed to send exception response to client during message handling due to IOException", ex);
                        }
                    }))
                    .subscribe();
            }
        }
    }

    private HttpChunk createInitializedNotificationRes() {
        return new HttpChunk() {
            @Override
            public boolean isLast() {
                return true;
            }

            @Override
            public BytesReference content() {
                return BytesReference.fromByteBuffer(ByteBuffer.wrap("OK".getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public void close() {

            }
        };
    }

    private void getDiscoveryNode(NodeClient client, String sessionId, ActionListener<GetResponse> listener) {
        GetRequest getRequest = new GetRequest(MCP_SESSION_MANAGEMENT_INDEX, sessionId);
        client.get(getRequest, listener);
    }

    @Override
    public boolean supportsContentStream() {
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean allowsUnsafeBuffers() {
        return true;
    }
}
