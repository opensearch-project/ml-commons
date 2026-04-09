/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.converters;

import java.util.Map;
import java.util.Optional;

import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.protobufs.DataAsMap;
import org.opensearch.protobufs.InferenceResults;
import org.opensearch.protobufs.Output;
import org.opensearch.protobufs.PredictResponse;

import lombok.extern.log4j.Log4j2;

/**
 * Converts ML Commons response objects to protobuf messages.
 */
@Log4j2
public class ProtoResponseConverter {

    /**
     * Converts response object to PredictResponse protobuf.
     */
    public static PredictResponse toProto(Object response) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }

        // Handle MLTaskResponse (predictions)
        if (response instanceof MLTaskResponse mlTaskResponse) {
            return toProtoFromMLTaskResponse(mlTaskResponse);
        }

        // Handle MLExecuteTaskResponse (agents)
        if (response instanceof MLExecuteTaskResponse mlExecuteTaskResponse) {
            return toProtoFromMLExecuteTaskResponse(mlExecuteTaskResponse);
        }

        throw new IllegalArgumentException("Unsupported response type: " + response.getClass().getName());
    }

    /**
     * Converts MLTaskResponse to PredictResponse protobuf.
     */
    private static PredictResponse toProtoFromMLTaskResponse(MLTaskResponse response) {
        if (response.getOutput() == null) {
            throw new IllegalArgumentException("MLTaskResponse output cannot be null");
        }

        if (!(response.getOutput() instanceof ModelTensorOutput output)) {
            throw new IllegalArgumentException("Expected ModelTensorOutput but got: " + response.getOutput().getClass().getName());
        }

        return PredictResponse
            .newBuilder()
            .addAllInferenceResults(
                Optional
                    .ofNullable(output.getMlModelOutputs())
                    .orElse(java.util.Collections.emptyList())
                    .stream()
                    .map(ProtoResponseConverter::convertModelTensorsToInferenceResults)
                    .toList()
            )
            .build();
    }

    /**
     * Converts MLExecuteTaskResponse to PredictResponse protobuf.
     */
    private static PredictResponse toProtoFromMLExecuteTaskResponse(MLExecuteTaskResponse response) {
        if (response.getOutput() == null) {
            throw new IllegalArgumentException("MLExecuteTaskResponse output cannot be null");
        }

        if (!(response.getOutput() instanceof ModelTensorOutput output)) {
            throw new IllegalArgumentException("Expected ModelTensorOutput but got: " + response.getOutput().getClass().getName());
        }

        return PredictResponse
            .newBuilder()
            .addAllInferenceResults(
                Optional
                    .ofNullable(output.getMlModelOutputs())
                    .orElse(java.util.Collections.emptyList())
                    .stream()
                    .map(ProtoResponseConverter::convertModelTensorsToInferenceResults)
                    .toList()
            )
            .build();
    }

    /**
     * Converts ModelTensors to InferenceResults proto.
     */
    private static InferenceResults convertModelTensorsToInferenceResults(ModelTensors modelTensors) {
        return InferenceResults
            .newBuilder()
            .addAllOutput(
                Optional
                    .ofNullable(modelTensors.getMlModelTensors())
                    .orElse(java.util.Collections.emptyList())
                    .stream()
                    .map(ProtoResponseConverter::convertModelTensorToOutput)
                    .toList()
            )
            .build();
    }

    /**
     * Converts a single ModelTensor to Output proto.
     */
    private static Output convertModelTensorToOutput(ModelTensor tensor) {
        Output.Builder builder = Output.newBuilder();

        if (tensor.getName() != null) {
            builder.setName(tensor.getName());
        }
        if (tensor.getResult() != null) {
            builder.setResult(tensor.getResult());
        }
        if (tensor.getDataAsMap() != null && !tensor.getDataAsMap().isEmpty()) {
            builder.setDataAsMap(convertToDataAsMap(tensor.getDataAsMap()));
        }
        return builder.build();
    }

    /**
     * Converts a Java Map to DataAsMap proto.
     */
    private static DataAsMap convertToDataAsMap(Map<String, ?> dataMap) {
        DataAsMap.Builder builder = DataAsMap.newBuilder();

        // Extract is_last flag
        Boolean isLast = (Boolean) dataMap.get("is_last");
        if (isLast != null) {
            builder.setIsLast(isLast);
        } else {
            log.warn("Missing is_last flag in dataAsMap, defaulting to false");
            builder.setIsLast(false);
        }

        // Extract content
        Object contentValue = dataMap.get("content");
        if (contentValue != null) {
            builder.setContent(contentValue.toString());
        } else {
            log.warn("Missing content in dataAsMap, using empty string");
            builder.setContent("");
        }
        return builder.build();
    }
}
