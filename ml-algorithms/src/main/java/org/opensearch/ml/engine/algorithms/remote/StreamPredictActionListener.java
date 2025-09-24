/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

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

    public StreamPredictActionListener(TransportChannel channel) {
        this.channel = channel;
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
        channel.sendResponseBatch(response);
        if (isLastBatch) {
            channel.completeStream();
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
        onStreamResponse(response, true);
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
