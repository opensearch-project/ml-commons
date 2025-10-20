/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;

public abstract class BaseStreamingHandler implements StreamingHandler {

    protected void sendContentResponse(String content, boolean isLast, StreamPredictActionListener<MLTaskResponse, ?> actionListener) {
        List<ModelTensor> modelTensors = new ArrayList<>();
        Map<String, Object> dataMap = Map.of("content", content, "is_last", isLast);

        modelTensors.add(ModelTensor.builder().name("response").dataAsMap(dataMap).build());
        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(modelTensors).build()))
            .build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();
        actionListener.onStreamResponse(response, isLast);
    }

    protected void sendCompletionResponse(AtomicBoolean isStreamClosed, StreamPredictActionListener<MLTaskResponse, ?> actionListener) {
        if (isStreamClosed.compareAndSet(false, true)) {
            sendContentResponse("", true, actionListener);
        }
    }
}
