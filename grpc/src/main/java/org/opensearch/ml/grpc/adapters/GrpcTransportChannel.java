/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.adapters;

import org.opensearch.core.transport.TransportResponse;
import org.opensearch.transport.TransportChannel;

import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;

/**
 * A dummy TransportChannel implementation that delegates to gRPC StreamObserver.
 *
 * <p>This adapter allows the existing streaming infrastructure (StreamPredictActionListener,
 * HttpStreamingHandler) to work with gRPC by implementing the TransportChannel interface
 * but delegating all operations to the gRPC StreamObserver.
 *
 * <p>This is used instead of passing null for the channel, which causes NullPointerExceptions
 * in error handling paths.
 */
@Log4j2
public class GrpcTransportChannel implements TransportChannel {

    private final StreamObserver<?> responseObserver;
    private StreamObserverAdapter<?> adapter;  // Not final - set after construction
    private final boolean isExecuteRequest;
    private boolean completed = false;

    /**
     * Creates a gRPC transport channel adapter.
     *
     * @param responseObserver the gRPC response observer
     * @param adapter the StreamObserverAdapter that handles conversion
     * @param isExecuteRequest true if this is an agent execute request
     */
    public GrpcTransportChannel(StreamObserver<?> responseObserver, StreamObserverAdapter<?> adapter, boolean isExecuteRequest) {
        this.responseObserver = responseObserver;
        this.adapter = adapter;
        this.isExecuteRequest = isExecuteRequest;
    }

    /**
     * Sets the adapter after construction (needed for circular reference).
     */
    public void setAdapter(StreamObserverAdapter<?> adapter) {
        this.adapter = adapter;
    }

    @Override
    public String getProfileName() {
        return "grpc";
    }

    @Override
    public String getChannelType() {
        return "grpc";
    }

    @Override
    public void sendResponse(TransportResponse response) {
        log.debug("GrpcTransportChannel.sendResponse() called - not implemented for streaming");
        // For streaming, we use sendResponseBatch() instead
    }

    @Override
    public void sendResponse(Exception exception) {
        log.debug("GrpcTransportChannel.sendResponse(Exception) called", exception);
        if (!completed) {
            io.grpc.Status status = org.opensearch.ml.grpc.GrpcStatusMapper.toGrpcStatus(exception);
            responseObserver.onError(status.asRuntimeException());
            completed = true;
        }
    }

    /**
     * Sends a streaming response batch.
     * This is called by StreamPredictActionListener for each chunk.
     *
     * @param response the response chunk to send
     */
    @Override
    public void sendResponseBatch(TransportResponse response) {
        log
            .debug(
                "[GRPC-DEBUG] GrpcTransportChannel.sendResponseBatch() called - completed={}, adapter={}, response type={}",
                completed,
                adapter != null ? "present" : "null",
                response != null ? response.getClass().getSimpleName() : "null"
            );
        if (completed) {
            log.warn("Attempted to send response batch after stream completed");
            return;
        }

        // Delegate to the adapter to handle the actual gRPC sending
        if (adapter != null && response instanceof org.opensearch.ml.common.transport.MLTaskResponse) {
            log.debug("[GRPC-DEBUG] Delegating to adapter.handleStreamResponse()");
            adapter.handleStreamResponse((org.opensearch.ml.common.transport.MLTaskResponse) response, false);
        } else {
            log
                .debug(
                    "[GRPC-DEBUG] NOT delegating - adapter={}, response type={}",
                    adapter != null ? "present" : "null",
                    response != null ? response.getClass().getName() : "null"
                );
        }
    }

    /**
     * Completes the gRPC stream.
     * This is called by StreamPredictActionListener when streaming is done.
     */
    @Override
    public void completeStream() {
        if (!completed) {
            log.debug("[GRPC-DEBUG] GrpcTransportChannel.completeStream() called");
            completed = true;
            // Delegate to adapter to complete the stream
            if (adapter != null) {
                adapter.completeGrpcStream();
            }
        }
    }
}
