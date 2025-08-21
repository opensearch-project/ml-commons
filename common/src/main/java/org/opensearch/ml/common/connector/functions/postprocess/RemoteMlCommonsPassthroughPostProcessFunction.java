/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.opensearch.ml.common.output.model.ModelTensors.OUTPUT_FIELD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

/**
 * A post-processing function for calling a remote ml commons instance that preserves the original neural sparse response structure
 * to avoid double-wrapping when receiving responses from another ML-Commons instance.
 */
public class RemoteMlCommonsPassthroughPostProcessFunction extends ConnectorPostProcessFunction<Map<String, Object>> {
    @Override
    public void validate(Object input) {
        if (!(input instanceof Map) && !(input instanceof List)) {
            throw new IllegalArgumentException("Post process function input must be a Map or List");
        }
    }

    @Override
    public List<ModelTensor> process(Map<String, Object> input, MLResultDataType dataType) {
        // Check if this is an ML-Commons response with inference_results
        if (input.containsKey("inference_results") && input.get("inference_results") instanceof List) {
            List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) input.get("inference_results");

            List<ModelTensor> modelTensors = new ArrayList<>();
            for (Map<String, Object> result : inferenceResults) {
                // Extract the output field which contains the ModelTensor data
                if (result.containsKey("output") && result.get("output") instanceof List) {
                    List<Map<String, Object>> outputs = (List<Map<String, Object>>) result.get("output");
                    for (Map<String, Object> output : outputs) {
                        // This inner map should represent a model tensor, so we try to parse and instantiate a new one.
                        ModelTensor modelTensor = createModelTensorFromMap(output);
                        if (modelTensor != null) {
                            modelTensors.add(modelTensor);
                        }
                    }
                }
            }

            return modelTensors;
        }

        // Fallback for non-ML-Commons responses
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(input).build();

        return List.of(tensor);
    }

    /**
     * Creates a ModelTensor from a Map<String, Object> representation based on the API format
     * of the /_predict API
     */
    private ModelTensor createModelTensorFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        // Get name. If name is null or not a String, default to OUTPUT_FIELD
        Object uncastedName = map.get(ModelTensor.NAME_FIELD);
        String name = uncastedName instanceof String castedName ? castedName : OUTPUT_FIELD;
        String result = (String) map.get(ModelTensor.RESULT_FIELD);
        Map<String, Object> dataAsMap = (Map<String, Object>) map.get(ModelTensor.DATA_AS_MAP_FIELD);

        // Handle data type
        MLResultDataType dataType = null;
        if (map.containsKey(ModelTensor.DATA_TYPE_FIELD)) {
            Object dataTypeObj = map.get(ModelTensor.DATA_TYPE_FIELD);
            if (dataTypeObj instanceof String) {
                try {
                    dataType = MLResultDataType.valueOf((String) dataTypeObj);
                } catch (IllegalArgumentException e) {
                    // Invalid data type, leave as null
                }
            }
        }

        // Handle shape
        long[] shape = null;
        if (map.containsKey(ModelTensor.SHAPE_FIELD)) {
            Object shapeObj = map.get(ModelTensor.SHAPE_FIELD);
            if (shapeObj instanceof List<?> shapeList) {
                shape = new long[shapeList.size()];
                for (int i = 0; i < shapeList.size(); i++) {
                    Object item = shapeList.get(i);
                    if (item instanceof Number) {
                        shape[i] = ((Number) item).longValue();
                    }
                }
            }
        }

        // Handle data array
        Number[] data = null;
        if (map.containsKey(ModelTensor.DATA_FIELD)) {
            Object dataObj = map.get(ModelTensor.DATA_FIELD);
            if (dataObj instanceof List<?> dataList) {
                data = new Number[dataList.size()];
                for (int i = 0; i < dataList.size(); i++) {
                    Object item = dataList.get(i);
                    if (item instanceof Number) {
                        data[i] = (Number) item;
                    }
                }
            }
        }

        // For now, we skip handling byte buffer since it's not needed for neural sparse and dense model use cases.

        return ModelTensor.builder().name(name).dataType(dataType).shape(shape).data(data).result(result).dataAsMap(dataAsMap).build();
    }
}
