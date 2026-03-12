/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.converters;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.protobufs.DataAsMap;
import org.opensearch.protobufs.InferenceResults;
import org.opensearch.protobufs.MlResultDataType;
import org.opensearch.protobufs.NullValue;
import org.opensearch.protobufs.ObjectMap;
import org.opensearch.protobufs.Output;
import org.opensearch.protobufs.PredictResponse;

import lombok.extern.log4j.Log4j2;

/**
 * Converts ML Commons response objects to protobuf messages.
 *
 * <p>This converter handles the mapping between internal ML Commons output structures
 * and gRPC proto messages for streaming responses.
 */
@Log4j2
public class ProtoResponseConverter {

    /**
     * Converts MLTaskResponse to PredictResponse protobuf.
     *
     * <p>This method extracts ModelTensorOutput from the response and converts it
     * to the proto format expected by gRPC clients. The conversion preserves:
     * <ul>
     *   <li>Tensor names and results
     *   <li>Data as map (including is_last flag for streaming)
     *   <li>Model output structure
     * </ul>
     *
     * @param response the ML task response
     * @param isExecuteRequest true if this is an agent execute request (currently unused)
     * @return PredictResponse protobuf
     * @throws IllegalArgumentException if response is invalid
     */
    public static PredictResponse toProto(MLTaskResponse response, boolean isExecuteRequest) {
        if (response == null) {
            throw new IllegalArgumentException("MLTaskResponse cannot be null");
        }

        if (response.getOutput() == null) {
            throw new IllegalArgumentException("MLTaskResponse output cannot be null");
        }

        if (!(response.getOutput() instanceof ModelTensorOutput)) {
            throw new IllegalArgumentException("Expected ModelTensorOutput but got: " + response.getOutput().getClass().getName());
        }

        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();

        PredictResponse.Builder responseBuilder = PredictResponse.newBuilder();

        // Convert model tensor outputs to inference results
        if (output.getMlModelOutputs() != null && !output.getMlModelOutputs().isEmpty()) {
            for (ModelTensors modelTensors : output.getMlModelOutputs()) {
                InferenceResults inferenceResults = convertModelTensorsToInferenceResults(modelTensors);
                responseBuilder.addInferenceResults(inferenceResults);
            }
        }

        return responseBuilder.build();
    }

    /**
     * Converts ModelTensors to InferenceResults proto.
     *
     * @param modelTensors the model tensors from ML Commons
     * @return InferenceResults proto
     */
    private static InferenceResults convertModelTensorsToInferenceResults(ModelTensors modelTensors) {
        InferenceResults.Builder builder = InferenceResults.newBuilder();

        if (modelTensors.getMlModelTensors() != null) {
            for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
                Output output = convertModelTensorToOutput(tensor);
                builder.addOutput(output);
            }
        }

        return builder.build();
    }

    /**
     * Converts a single ModelTensor to Output proto.
     *
     * @param tensor the model tensor
     * @return Output proto
     */
    private static Output convertModelTensorToOutput(ModelTensor tensor) {
        Output.Builder builder = Output.newBuilder();

        // Set name if present
        if (tensor.getName() != null) {
            builder.setName(tensor.getName());
        }

        // Set result if present (this is typically the main text output for LLMs)
        if (tensor.getResult() != null) {
            builder.setResult(tensor.getResult());
        }

        // Set data type if determinable
        if (tensor.getDataType() != null) {
            MlResultDataType protoDataType = convertDataType(tensor.getDataType());
            builder.setDataType(protoDataType);
        }

        // Convert data as map (important for streaming metadata like is_last)
        if (tensor.getDataAsMap() != null && !tensor.getDataAsMap().isEmpty()) {
            DataAsMap dataAsMap = convertToDataAsMap(tensor.getDataAsMap());
            builder.setDataAsMap(dataAsMap);
        }

        // Set shape if present
        if (tensor.getShape() != null) {
            for (Long shapeValue : tensor.getShape()) {
                builder.addShape(shapeValue);
            }
        }

        // Set data if present
        if (tensor.getData() != null) {
            for (Number dataValue : tensor.getData()) {
                builder.addData(dataValue.doubleValue());
            }
        }

        return builder.build();
    }

    /**
     * Converts ML Commons data type to proto data type.
     *
     * @param dataType the ML Commons MLResultDataType
     * @return proto MlResultDataType
     */
    private static MlResultDataType convertDataType(org.opensearch.ml.common.output.model.MLResultDataType dataType) {
        switch (dataType) {
            case INT32:
            case INT8:
                return MlResultDataType.ML_RESULT_DATA_TYPE_INT32;
            case INT64:
                return MlResultDataType.ML_RESULT_DATA_TYPE_INT64;
            case UINT8:
                return MlResultDataType.ML_RESULT_DATA_TYPE_UINT8;
            case FLOAT16:
                return MlResultDataType.ML_RESULT_DATA_TYPE_FLOAT16;
            case FLOAT32:
                return MlResultDataType.ML_RESULT_DATA_TYPE_FLOAT32;
            case FLOAT64:
                return MlResultDataType.ML_RESULT_DATA_TYPE_FLOAT64;
            case STRING:
                return MlResultDataType.ML_RESULT_DATA_TYPE_STRING;
            case BOOLEAN:
                return MlResultDataType.ML_RESULT_DATA_TYPE_BOOLEAN;
            case BINARY:
            case UBINARY:
                return MlResultDataType.ML_RESULT_DATA_TYPE_UNSPECIFIED;
            case UNKNOWN:
            default:
                log.warn("Unknown data type: {}, defaulting to UNSPECIFIED", dataType);
                return MlResultDataType.ML_RESULT_DATA_TYPE_UNSPECIFIED;
        }
    }

    /**
     * Converts a Java Map to DataAsMap proto.
     *
     * <p>This is critical for streaming as it preserves the is_last flag.
     * DataAsMap in proto has two fields:
     * <ul>
     *   <li>content (string) - serialized content (JSON of entire map)
     *   <li>is_last (bool) - streaming control flag
     * </ul>
     *
     * @param dataMap the Java map
     * @return DataAsMap proto
     */
    private static DataAsMap convertToDataAsMap(Map<String, ?> dataMap) {
        DataAsMap.Builder builder = DataAsMap.newBuilder();

        // Extract is_last flag if present
        if (dataMap.containsKey("is_last")) {
            Object isLastValue = dataMap.get("is_last");
            if (isLastValue instanceof Boolean) {
                builder.setIsLast((Boolean) isLastValue);
            } else if (isLastValue instanceof String) {
                builder.setIsLast(Boolean.parseBoolean((String) isLastValue));
            }
        }

        // Serialize entire map to JSON string for content field
        try {
            org.opensearch.core.xcontent.XContentBuilder contentBuilder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
            contentBuilder.map(dataMap);
            builder.setContent(contentBuilder.toString());
        } catch (Exception e) {
            log.error("Failed to serialize dataAsMap to JSON", e);
            // Fallback: just convert to string
            builder.setContent(dataMap.toString());
        }

        return builder.build();
    }

    /**
     * Converts a Java Map to ObjectMap proto.
     *
     * @param map the Java map
     * @return ObjectMap proto
     */
    private static ObjectMap convertToObjectMap(Map<String, ?> map) {
        ObjectMap.Builder builder = ObjectMap.newBuilder();

        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            ObjectMap.Value protoValue = convertToProtoValue(value);
            builder.putFields(key, protoValue);
        }

        return builder.build();
    }

    /**
     * Converts a Java object to ObjectMap.Value proto.
     *
     * @param value the Java object
     * @return ObjectMap.Value proto
     */
    private static ObjectMap.Value convertToProtoValue(Object value) {
        ObjectMap.Value.Builder builder = ObjectMap.Value.newBuilder();

        if (value == null) {
            return builder.setNullValue(NullValue.NULL_VALUE_NULL).build();
        }

        if (value instanceof String) {
            return builder.setString((String) value).build();
        }

        if (value instanceof Integer) {
            return builder.setInt32((Integer) value).build();
        }

        if (value instanceof Long) {
            return builder.setInt64((Long) value).build();
        }

        if (value instanceof Float) {
            return builder.setFloat((Float) value).build();
        }

        if (value instanceof Double) {
            return builder.setDouble((Double) value).build();
        }

        if (value instanceof Boolean) {
            return builder.setBool((Boolean) value).build();
        }

        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> mapValue = (Map<String, ?>) value;
            return builder.setObjectMap(convertToObjectMap(mapValue)).build();
        }

        if (value instanceof List) {
            ObjectMap.ListValue.Builder listBuilder = ObjectMap.ListValue.newBuilder();
            for (Object item : (List<?>) value) {
                listBuilder.addValue(convertToProtoValue(item));
            }
            return builder.setListValue(listBuilder.build()).build();
        }

        // Fallback: convert to string
        log.warn("Unknown value type: {}, converting to string", value.getClass().getName());
        return builder.setString(value.toString()).build();
    }

    /**
     * Converts ModelTensorOutput to StreamPredictResponse protobuf.
     *
     * @param output the ML Commons model output
     * @return StreamPredictResponse protobuf (type: org.opensearch.ml.proto.StreamPredictResponse)
     * @throws UnsupportedOperationException until opensearch-protobufs is published
     * @deprecated Use {@link #toProto(MLTaskResponse, boolean)} instead
     */
    @Deprecated
    public static Object toStreamPredictResponse(ModelTensorOutput output) {
        throw new UnsupportedOperationException("Use toProto(MLTaskResponse, boolean) instead");
    }

    /**
     * Converts ModelTensorOutput to StreamExecuteResponse protobuf.
     *
     * @param output the ML Commons execution output
     * @return StreamExecuteResponse protobuf (type: org.opensearch.ml.proto.StreamExecuteResponse)
     * @throws UnsupportedOperationException until opensearch-protobufs is published
     * @deprecated Use {@link #toProto(MLTaskResponse, boolean)} instead
     */
    @Deprecated
    public static Object toStreamExecuteResponse(ModelTensorOutput output) {
        throw new UnsupportedOperationException("Use toProto(MLTaskResponse, boolean) instead");
    }

    /**
     * Converts ModelTensors to protobuf ModelTensors.
     *
     * @param tensors the ML Commons model tensors
     * @return ModelTensors protobuf (type: org.opensearch.ml.proto.ModelTensors)
     * @throws UnsupportedOperationException until opensearch-protobufs is published
     * @deprecated Use {@link #toProto(MLTaskResponse, boolean)} instead
     */
    @Deprecated
    public static Object toProtoModelTensors(ModelTensors tensors) {
        throw new UnsupportedOperationException("Use toProto(MLTaskResponse, boolean) instead");
    }

    /**
     * Converts ModelTensor to protobuf ModelTensor.
     *
     * @param tensor the ML Commons model tensor
     * @return ModelTensor protobuf (type: org.opensearch.ml.proto.ModelTensor)
     * @throws UnsupportedOperationException until opensearch-protobufs is published
     * @deprecated Use {@link #toProto(MLTaskResponse, boolean)} instead
     */
    @Deprecated
    public static Object toProtoModelTensor(ModelTensor tensor) {
        throw new UnsupportedOperationException("Use toProto(MLTaskResponse, boolean) instead");
    }
}
