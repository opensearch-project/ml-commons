/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.adapters;

import org.opensearch.core.transport.TransportResponse;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.grpc.GrpcStatusMapper;
import org.opensearch.transport.TransportChannel;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * A TransportChannel implementation that delegates to gRPC StreamObserver.
 */
@Log4j2
public class GrpcTransportChannel implements TransportChannel {

    private final StreamObserver<?> responseObserver;
    @Setter
    private StreamObserverAdapter<?> adapter;
    @Getter
    private boolean completed = false;

    /**
     * Creates a gRPC transport channel adapter.
     *
     * @param responseObserver the gRPC response observer
     * @param adapter the StreamObserverAdapter that handles conversion
     */
    public GrpcTransportChannel(StreamObserver<?> responseObserver, StreamObserverAdapter<?> adapter) {
        this.responseObserver = responseObserver;
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
        log.warn("GrpcTransportChannel.sendResponse() called - not implemented for streaming");
    }

    @Override
    public void sendResponse(Exception exception) {
        if (!completed) {
            Status status = GrpcStatusMapper.toGrpcStatus(exception);
            responseObserver.onError(status.asRuntimeException());
            completed = true;
        }
    }

    /**
     * Sends a streaming response batch.
     * This is called by StreamPredictActionListener or agent executor for each chunk.
     *
     * @param response the response chunk to send
     */
    @Override
    public void sendResponseBatch(TransportResponse response) {
        if (completed) {
            return;
        }

        if (adapter != null) {
            if (response instanceof MLTaskResponse || response instanceof MLExecuteTaskResponse) {
                adapter.handleStreamResponse(response, false);
            } else {
                log.warn("Unsupported response type in sendResponseBatch: " + response.getClass().getName());
            }
        }
    }

    /**
     * Completes the gRPC stream.
     * This is called by StreamPredictActionListener when streaming is done.
     */
    @Override
    public void completeStream() {
        if (!completed) {
            completed = true;
            if (adapter != null) {
                adapter.completeGrpcStream();
            }
        }
    }

    public void markCompleted() {
        this.completed = true;
    }
}
