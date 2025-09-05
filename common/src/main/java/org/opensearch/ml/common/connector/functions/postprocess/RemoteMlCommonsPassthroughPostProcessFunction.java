/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.opensearch.ml.common.output.model.ModelTensors.OUTPUT_FIELD;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * Example unwrapped response:
     * {
     * 	"inference_results": [
    * 		        {
     * 			"output": [
     *                {
     * 					"name": "output",
     * 					"dataAsMap": {
     * 						"inference_results": [
     *                            {
     * 								"output": [
     *                                    {
     * 										"name": "output",
     * 										"dataAsMap": {
     * 											"response": [
     *                                                {
     *                     								"increasingly": 0.028670792,
     *                     								"achievements": 0.4906937,
     *                     								...
     *                                                }
     * 											]
     *                                        }
     *                                    }
     * 								],
     * 								"status_code": 200.0
     *                            }
     * 						]
     *                    }
     *                }
     * 			],
     * 			"status_code": 200
     *        }
     * 	]
     * }
     *
     * Example unwrapped response:
     *
     * {
     * 	"inference_results": [
     * 		        {
     * 			"output": [
     *                {
     * 					"name": "output",
     * 					"dataAsMap": {
     * 						"response": [
     *                            {
     * 								"increasingly": 0.028670792,
     * 								"achievements": 0.4906937,
     * 								...
     *                            }
     * 						]
     *                    }
     *                },
     * 			],
     * 			"status_code": 200
     *        }
     * 	]
     * }
     *
     * @param mlCommonsResponse raw remote ml commons response
     * @param dataType the datatype of the result, not used since datatype is set based on the response body
     * @return a list of model tensors representing the inner model tensors
     */
    @Override
    public List<ModelTensor> process(Map<String, Object> mlCommonsResponse, MLResultDataType dataType) {
        // Check if this is an ML-Commons response with inference_results
        if (mlCommonsResponse.containsKey("inference_results") && mlCommonsResponse.get("inference_results") instanceof List) {
            List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) mlCommonsResponse.get("inference_results");

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
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(mlCommonsResponse).build();

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

        // Handle data as map
        Map<String, Object> dataAsMap = (Map<String, Object>) map.get(ModelTensor.DATA_AS_MAP_FIELD);

        // Handle data type. For certain models like neural sparse and non-dense remote models, this field
        // is not populated and left as null instead, which is still valid
        MLResultDataType dataType = null;
        if (map.containsKey(ModelTensor.DATA_TYPE_FIELD)) {
            Object dataTypeObj = map.get(ModelTensor.DATA_TYPE_FIELD);
            if (dataTypeObj instanceof String) {
                try {
                    dataType = MLResultDataType.valueOf((String) dataTypeObj);
                } catch (IllegalArgumentException e) {
                    // Invalid data type, leave as null in case inner data is still useful to be parsed in the future
                }
            }
        }

        // Handle shape. For certain models like neural sparse and non-dense, null is valid since inference result
        // is stored in dataAsMap, not data/shape field
        long[] shape = null;
        if (map.containsKey(ModelTensor.SHAPE_FIELD)) {
            Number[] numbers = processNumericalArray(map, ModelTensor.SHAPE_FIELD, Number.class);
            if (numbers != null) {
                shape = Arrays.stream(numbers).mapToLong(Number::longValue).toArray();
            }
        }

        // Handle shape. For certain models like neural sparse and non-dense, null is valid since inference result
        // is stored in dataAsMap, not data/shape field
        Number[] data = null;
        if (map.containsKey(ModelTensor.DATA_FIELD)) {
            data = processNumericalArray(map, ModelTensor.DATA_FIELD, Number.class);
        }

        // For now, we skip handling byte buffer since it's not needed for neural sparse and dense model use cases.

        return ModelTensor.builder().name(name).dataType(dataType).shape(shape).data(data).result(result).dataAsMap(dataAsMap).build();
    }

    private static <T> T[] processNumericalArray(Map<String, Object> map, String key, Class<T> type) {
        Object obj = map.get(key);
        if (obj instanceof List<?> list) {
            T[] array = (T[]) Array.newInstance(type, list.size());
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (type.isInstance(item)) {
                    array[i] = type.cast(item);
                }
            }
            return array;
        }
        return null;
    }
}
