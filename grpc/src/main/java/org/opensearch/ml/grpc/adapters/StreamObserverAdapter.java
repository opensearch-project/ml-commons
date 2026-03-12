/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.adapters;

import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.grpc.GrpcStatusMapper;
import org.opensearch.ml.grpc.converters.ProtoResponseConverter;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;

/**
 * Adapter that bridges ML Commons streaming logic to gRPC StreamObserver.
 *
 * <p>This adapter allows the existing streaming infrastructure (StreamPredictActionListener,
 * HttpStreamingHandler) to work seamlessly with gRPC server-side streaming. It translates:
 *
 * <ul>
 *   <li>{@code channel.sendResponseBatch()} → {@code StreamObserver.onNext()}
 *   <li>{@code channel.completeStream()} → {@code StreamObserver.onCompleted()}
 *   <li>Exception handling → {@code StreamObserver.onError()}
 * </ul>
 *
 * <p>The adapter extends StreamPredictActionListener to receive proper streaming callbacks
 * with the isLastBatch flag, which is critical for completing the gRPC stream correctly.
 *
 * @param <ProtoResponse> The protobuf response type (typically PredictResponse)
 */
@Log4j2
public class StreamObserverAdapter<ProtoResponse> extends StreamPredictActionListener<MLTaskResponse, MLPredictionTaskRequest> {

    private final StreamObserver<ProtoResponse> responseObserver;
    private final boolean isExecuteRequest;

    /**
     * Creates an adapter for streaming.
     *
     * @param responseObserver the gRPC response observer to stream results to
     * @param isExecuteRequest true if this is an agent execute request, false for model prediction
     */
    public StreamObserverAdapter(StreamObserver<ProtoResponse> responseObserver, boolean isExecuteRequest) {
        super(new GrpcTransportChannel(responseObserver, null, isExecuteRequest));
        this.responseObserver = responseObserver;
        this.isExecuteRequest = isExecuteRequest;
        log.debug("[GRPC-DEBUG] StreamObserverAdapter constructor called");
        // Update the channel to reference this adapter
        try {
            ((GrpcTransportChannel) getChannel()).setAdapter(this);
            log.debug("[GRPC-DEBUG] Successfully set adapter on channel");
        } catch (Exception e) {
            log.debug("[GRPC-DEBUG] Failed to set adapter on channel", e);
            throw e;
        }
    }

    /**
     * Gets the channel from the parent class.
     */
    public org.opensearch.transport.TransportChannel getChannel() {
        try {
            java.lang.reflect.Field field = org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener.class
                .getDeclaredField("channel");
            field.setAccessible(true);
            org.opensearch.transport.TransportChannel channel = (org.opensearch.transport.TransportChannel) field.get(this);
            log.debug("[GRPC-DEBUG] getChannel() returning channel type: {}", channel != null ? channel.getClass().getName() : "null");
            return channel;
        } catch (Exception e) {
            log.debug("[GRPC-DEBUG] Failed to access channel field", e);
            throw new RuntimeException("Failed to access channel field", e);
        }
    }

    /**
     * Handles a streaming response chunk from the remote LLM handler.
     *
     * <p>This method is called by the streaming infrastructure (HttpStreamingHandler,
     * BedrockStreamingHandler) with the isLastBatch flag properly set.
     *
     * @param response the ML task response containing the streaming chunk
     * @param isLastBatch true if this is the last chunk in the stream
     */
    @Override
    public void onStreamResponse(MLTaskResponse response, boolean isLastBatch) {
        log.debug("[GRPC-DEBUG] StreamObserverAdapter.onStreamResponse() called - isLastBatch={}", isLastBatch);
        log.debug("[GRPC-DEBUG] StreamObserverAdapter.onStreamResponse() called - isLastBatch={}", isLastBatch);
        try {
            // Convert ML response to protobuf
            ProtoResponse protoResponse = convertToProto(response);

            // Send the chunk to client
            responseObserver.onNext(protoResponse);
            log.debug("[GRPC-DEBUG] Sent gRPC response chunk via responseObserver.onNext()");

            // Complete stream if this is the last batch OR if this is a complete (non-streaming) response
            boolean shouldComplete = isLastBatch || isCompleteResponse(response);

            if (shouldComplete) {
                log.debug("[GRPC-DEBUG] Completing stream (isLastBatch={}, isComplete={})", isLastBatch, isCompleteResponse(response));
                responseObserver.onCompleted();
            }

        } catch (Exception e) {
            log.debug("Error processing streaming response", e);
            onFailure(e);
        }
    }

    /**
     * Checks if this is a complete (non-streaming) response that should terminate the stream.
     *
     * <p>For non-streaming responses, the model returns the complete result in a single call
     * to onResponse(), which delegates to onStreamResponse(response, false). We need to detect
     * these and complete the stream, otherwise it will hang indefinitely.
     *
     * <p>Detection strategy: Check if the dataAsMap contains a complete LLM response with
     * finish_reason=stop, which indicates the response is complete.
     *
     * @param response the ML task response
     * @return true if this is a complete response that should end the stream
     */
    private boolean isCompleteResponse(MLTaskResponse response) {
        if (response == null || response.getOutput() == null) {
            log.debug("isCompleteResponse: response or output is null");
            return false;
        }

        if (!(response.getOutput() instanceof org.opensearch.ml.common.output.model.ModelTensorOutput)) {
            log.debug("isCompleteResponse: output is not ModelTensorOutput, type={}", response.getOutput().getClass().getName());
            return false;
        }

        org.opensearch.ml.common.output.model.ModelTensorOutput output = (org.opensearch.ml.common.output.model.ModelTensorOutput) response
            .getOutput();

        if (output.getMlModelOutputs() == null || output.getMlModelOutputs().isEmpty()) {
            log.debug("isCompleteResponse: mlModelOutputs is null or empty");
            return false;
        }

        // Check first tensor for completion indicators
        org.opensearch.ml.common.output.model.ModelTensors modelTensors = output.getMlModelOutputs().get(0);
        if (modelTensors == null || modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("isCompleteResponse: modelTensors or mlModelTensors is null or empty");
            return false;
        }

        org.opensearch.ml.common.output.model.ModelTensor tensor = modelTensors.getMlModelTensors().get(0);
        if (tensor == null) {
            log.debug("isCompleteResponse: tensor is null");
            return false;
        }

        // Check for is_last flag (streaming completion)
        java.util.Map<String, ?> dataAsMap = tensor.getDataAsMap();
        log.debug("isCompleteResponse: checking dataAsMap={}", dataAsMap);

        if (dataAsMap != null && dataAsMap.containsKey("is_last")) {
            Object isLast = dataAsMap.get("is_last");
            if (isLast instanceof Boolean && (Boolean) isLast) {
                log.debug("isCompleteResponse: found is_last=true (Boolean)");
                return true;
            }
            if (isLast instanceof String && Boolean.parseBoolean((String) isLast)) {
                log.debug("isCompleteResponse: found is_last=true (String)");
                return true;
            }
        }

        // Check for complete OpenAI response (non-streaming)
        // Look for finish_reason: "stop" in the content JSON
        if (dataAsMap != null && dataAsMap.containsKey("content")) {
            Object content = dataAsMap.get("content");
            if (content instanceof String) {
                String contentStr = (String) content;
                // Check if this is a complete OpenAI response with finish_reason
                if (contentStr.contains("\"finish_reason\":\"stop\"") || contentStr.contains("\"finish_reason\": \"stop\"")) {
                    log.debug("Detected complete OpenAI response with finish_reason=stop");
                    return true;
                }
            }
        }

        log.info("isCompleteResponse: no completion indicators found, returning false");
        return false;
    }

    /**
     * Handles errors during streaming.
     *
     * <p>Converts the exception to an appropriate gRPC Status and calls
     * {@code StreamObserver.onError()}.
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
     * Converts MLTaskResponse to protobuf response.
     *
     * @param response the ML task response
     * @return protobuf response message
     */
    @SuppressWarnings("unchecked")
    private ProtoResponse convertToProto(MLTaskResponse response) {
        // Use ProtoResponseConverter to convert to protobuf
        // The converter handles both prediction and execute responses
        return (ProtoResponse) ProtoResponseConverter.toProto(response, isExecuteRequest);
    }

    /**
     * Helper method called by GrpcTransportChannel to handle streaming responses.
     * This is the bridge between the channel's sendResponseBatch() and our onStreamResponse().
     */
    public void handleStreamResponse(MLTaskResponse response, boolean isLast) {
        onStreamResponse(response, isLast);
    }

    /**
     * Helper method called by GrpcTransportChannel to complete the stream.
     */
    public void completeGrpcStream() {
        if (!completed) {
            log.debug("[GRPC-DEBUG] StreamObserverAdapter.completeGrpcStream() - completing stream");
            responseObserver.onCompleted();
            completed = true;
        }
    }

    private boolean completed = false;
}
