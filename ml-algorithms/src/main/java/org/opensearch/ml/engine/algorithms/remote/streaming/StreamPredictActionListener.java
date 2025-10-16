/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StreamPredictActionListener<Response extends TransportResponse, Request extends TransportRequest>
    implements
        ActionListener<Response> {

    private final TransportChannel channel;
    private final ActionListener<Response> agentListener;
    private final String memoryId;
    private final String parentInteractionId;

    public StreamPredictActionListener(TransportChannel channel) {
        this(channel, null, null, null);
    }

    public StreamPredictActionListener(
        TransportChannel channel,
        ActionListener<Response> agentListener,
        String memoryId,
        String parentInteractionId
    ) {
        this.channel = channel;
        this.agentListener = agentListener;
        this.memoryId = memoryId;
        this.parentInteractionId = parentInteractionId;
    }

    /**
     * Send streaming responses
     * This allows multiple responses to be sent for a single request.
     *
     * @param response    the intermediate response to send
     * @param isLastBatch whether this response is the last one
     */
    public void onStreamResponse(Response response, boolean isLastBatch) {
        assert response != null;

        // Add metadata to all responses
        Response responseWithMetadata = addMetadataToResponse(response);

        channel.sendResponseBatch(responseWithMetadata);
        if (isLastBatch) {
            channel.completeStream();
        }
    }

    /**
     * Check if the response contains frontend tool calls by examining the ModelTensorOutput structure
     * Frontend tool calls are identified by having tool_calls in the response data
     */
    private boolean containsFrontendToolCalls(Response response) {
        try {
            if (!(response instanceof MLTaskResponse)) {
                return false;
            }

            MLTaskResponse taskResponse = (MLTaskResponse) response;
            if (!(taskResponse.getOutput() instanceof ModelTensorOutput)) {
                return false;
            }

            ModelTensorOutput tensorOutput = (ModelTensorOutput) taskResponse.getOutput();
            if (tensorOutput.getMlModelOutputs() == null) {
                return false;
            }

            // Look for tool_calls in any tensor data
            for (ModelTensors modelTensors : tensorOutput.getMlModelOutputs()) {
                if (modelTensors.getMlModelTensors() != null) {
                    for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
                        if (tensor.getDataAsMap() != null && tensor.getDataAsMap().containsKey("tool_calls")) {
                            log.info("AG-UI: Found tool_calls in tensor: {}", tensor.getName());
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("AG-UI: Error checking for frontend tool calls: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Reuse ActionListener method to send the last stream response
     * This maintains compatibility on data node side
     *
     * @param response the response to send
     */
    @Override
    public final void onResponse(Response response) {
        log
            .info(
                "AG-UI: StreamPredictActionListener.onResponse called - agentListener: {}, response type: {}",
                agentListener != null ? agentListener.getClass().getSimpleName() : "null",
                response != null ? response.getClass().getSimpleName() : "null"
            );

        // Check if this response contains frontend tool calls
        boolean hasFrontendToolCalls = containsFrontendToolCalls(response);
        log.info("AG-UI: Response contains frontend tool calls: {}", hasFrontendToolCalls);

        // For frontend tool calls, skip raw streaming and let AG-UI handle the events
        if (!hasFrontendToolCalls) {
            log.info("AG-UI: Sending raw streaming response");
            onStreamResponse(response, false);
        } else {
            log.info("AG-UI: Skipping raw streaming for frontend tool calls - AG-UI will handle events");
        }

        if (agentListener != null) {
            log.info("AG-UI: Calling agentListener.onResponse()");
            agentListener.onResponse(response);
        } else {
            log.info("AG-UI: agentListener is null, skipping agent processing");
        }
    }

    @Override
    public void onFailure(Exception e) {
        try {
            MLTaskResponse errorResponse = createErrorResponse(e);
            channel.sendResponseBatch(errorResponse);
            channel.completeStream();
        } catch (Exception exc) {
            try {
                channel.completeStream();
            } catch (Exception streamException) {
                log.error("Failed to complete stream", streamException);
            }
        }
    }

    private Response addMetadataToResponse(Response response) {
        if (!(response instanceof MLTaskResponse)) {
            return response;
        }

        // Only add metadata for agent streaming
        if (agentListener == null) {
            return response;
        }

        MLTaskResponse mlResponse = (MLTaskResponse) response;
        if (mlResponse.getOutput() instanceof ModelTensorOutput) {
            ModelTensorOutput output = (ModelTensorOutput) mlResponse.getOutput();
            List<ModelTensors> updatedOutputs = new ArrayList<>();

            // TODO: refactor this to handle other types of agents
            for (ModelTensors tensors : output.getMlModelOutputs()) {
                List<ModelTensor> updatedTensors = new ArrayList<>();

                updatedTensors.add(ModelTensor.builder().name("memory_id").result(memoryId).build());
                updatedTensors.add(ModelTensor.builder().name("parent_interaction_id").result(parentInteractionId).build());

                updatedTensors.addAll(tensors.getMlModelTensors());
                updatedOutputs.add(ModelTensors.builder().mlModelTensors(updatedTensors).build());
            }

            ModelTensorOutput updatedOutput = ModelTensorOutput.builder().mlModelOutputs(updatedOutputs).build();
            return (Response) new MLTaskResponse(updatedOutput);
        }
        return response;
    }

    private MLTaskResponse createErrorResponse(Exception error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "Request failed";
        }

        Map<String, Object> errorData = new LinkedHashMap<>();
        errorData.put("error", errorMessage);
        errorData.put("is_last", true);

        ModelTensor errorTensor = ModelTensor.builder().name("error").dataAsMap(errorData).build();
        ModelTensors errorTensors = ModelTensors.builder().mlModelTensors(List.of(errorTensor)).build();
        ModelTensorOutput errorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(errorTensors)).build();

        return MLTaskResponse.builder().output(errorOutput).build();
    }
}
