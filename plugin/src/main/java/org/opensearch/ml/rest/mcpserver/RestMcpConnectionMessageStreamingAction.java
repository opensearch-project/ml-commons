/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.common.bytes.CompositeBytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.http.HttpChunk;
import org.opensearch.ml.action.mcpserver.McpAsyncServerHolder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.transport.client.node.NodeClient;

import io.modelcontextprotocol.spec.McpError;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@ExperimentalApi
public class RestMcpConnectionMessageStreamingAction extends BaseRestHandler {

    private static final String MCP_ACTION = "mcp_action";
    private static final String MESSAGE_ENDPOINT = "/_plugins/_ml/sse/message";
    private static final String SSE_ENDPOINT = "/_plugins/_ml/sse";

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
        String path = request.path();
        final String sessionId = request.param("sessionId");
        final StreamingRestChannelConsumer consumer = (channel) -> {

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
                            List.of("keep-alive"),
                            "Transfer-Encoding",
                            List.of("chunked")
                        )
                );

            if (path.equals("/_plugins/_ml/sse")) {
                // The connection request doesn't have request body, but we're still reading the request body content,
                // The reason is that the response producer is created when http channel is been read, so here
                // we subscribe to channel to trigger the channel read, but ignoring the content.
                Mono
                    .from(channel)
                    .ofType(HttpChunk.class)
                    .map(HttpChunk::content)
                    .flatMap(x -> McpAsyncServerHolder.mcpServerTransportProvider.handleSseConnection(channel))
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
            } else if (path.equals("/_plugins/_ml/sse/message")) {
                if (sessionId == null) {
                    Mono.defer(() -> Mono.error(new McpError("Session ID missing in message endpoint")));
                } else {
                    Flux
                        .from(channel)
                        .ofType(HttpChunk.class)
                        .takeUntil(HttpChunk::isLast)
                        .map(HttpChunk::content)
                        .reduce(CompositeBytesReference::of)
                        .doOnSuccess(
                            x -> McpAsyncServerHolder.mcpServerTransportProvider
                                .handleMessage(sessionId, x.utf8ToString())
                                .doOnSuccess(y -> {
                                    if (Boolean.TRUE.equals(y)) {
                                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, "OK"));
                                    }
                                })
                                .onErrorResume(e -> Mono.fromRunnable(() -> {
                                    try {
                                        channel.sendResponse(new BytesRestResponse(channel, new Exception(e)));
                                    } catch (IOException ex) {
                                        log.error("Failed to send exception response to client during message handling due to IOException");
                                    }
                                }))
                                .subscribe()
                        )
                        .doOnError(e -> Mono.fromRunnable(() -> {
                            try {
                                channel.sendResponse(new BytesRestResponse(channel, new Exception(e)));
                            } catch (IOException ex) {
                                log.error("Failed to send exception response to client during messaging due to IOException");
                            }
                        }))
                        .subscribe();
                }
            }
        };
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
