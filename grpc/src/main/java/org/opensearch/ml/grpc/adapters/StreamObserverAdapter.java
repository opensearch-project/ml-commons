/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.adapters;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.grpc.GrpcStatusMapper;
import org.opensearch.ml.grpc.converters.ProtoResponseConverter;
import org.opensearch.transport.TransportChannel;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;

/**
 * Adapter that bridges ML Commons streaming logic to gRPC StreamObserver.
 *
 * @param <ProtoResponse> The protobuf response type
 */
@Log4j2
public class StreamObserverAdapter<ProtoResponse> extends
    StreamPredictActionListener<org.opensearch.core.action.ActionResponse, MLPredictionTaskRequest> {

    private final StreamObserver<ProtoResponse> responseObserver;
    private final GrpcTransportChannel channel;

    /**
     * Creates an adapter for streaming.
     *
     * @param responseObserver the gRPC response observer to stream results to
     */
    public StreamObserverAdapter(StreamObserver<ProtoResponse> responseObserver) {
        this(responseObserver, new GrpcTransportChannel(responseObserver, null));
    }

    private StreamObserverAdapter(StreamObserver<ProtoResponse> responseObserver, GrpcTransportChannel channel) {
        super(channel);
        this.responseObserver = responseObserver;
        this.channel = channel;
        this.channel.setAdapter(this);
    }

    public TransportChannel getChannel() {
        return channel;
    }

    /**
     * Handles a streaming response chunk from the remote LLM handler.
     *
     * @param response the ML task response containing the streaming chunk
     * @param isLastBatch true if this is the last chunk in the stream
     */
    @Override
    public void onStreamResponse(org.opensearch.core.action.ActionResponse response, boolean isLastBatch) {
        handleStreamResponse(response, isLastBatch);
    }

    /**
     * Handles errors during streaming.
     *
     * @param e the exception that occurred
     */
    @Override
    public void onFailure(Exception e) {
        try {
            Status status = GrpcStatusMapper.toGrpcStatus(e);
            responseObserver.onError(status.asRuntimeException());
        } catch (Exception ex) {
            log.error("Error handling failure", ex);
            // Ensure stream is completed even if error handling fails
            try {
                responseObserver.onError(Status.INTERNAL.withDescription("Internal error").withCause(e).asRuntimeException());
            } catch (Exception finalEx) {
                log.error("Failed to send error to client", finalEx);
            }
        }
    }

    /**
     * Converts ML response to protobuf response.
     *
     * @param response the ML response
     * @return protobuf response message
     */
    @SuppressWarnings("unchecked")
    private ProtoResponse convertToProto(Object response) {
        return (ProtoResponse) ProtoResponseConverter.toProto(response);
    }

    /**
     * Helper method called by GrpcTransportChannel to handle streaming responses.
     * This is the bridge between the channel's sendResponseBatch() and the gRPC stream.
     *
     * @param response the response object
     * @param isLast true if this is the last chunk in the stream
     */
    public void handleStreamResponse(Object response, boolean isLast) {
        GrpcTransportChannel channel = (GrpcTransportChannel) getChannel();

        if (channel.isCompleted()) {
            return;
        }

        try {
            // Convert response to protobuf
            ProtoResponse protoResponse = convertToProto(response);

            // Send the chunk to client
            responseObserver.onNext(protoResponse);

            // Check if content indicates last chunk
            if (isLast) {
                if (completed.compareAndSet(false, true)) {
                    responseObserver.onCompleted();
                    channel.markCompleted();
                }
            }
        } catch (Exception e) {
            log.error("Error handling streaming response", e);
            onFailure(e);
        }
    }

    /**
     * Helper method called by GrpcTransportChannel to complete the stream.
     */
    public void completeGrpcStream() {
        if (completed.compareAndSet(false, true)) {
            responseObserver.onCompleted();
        }
    }

    private final AtomicBoolean completed = new AtomicBoolean(false);
}
