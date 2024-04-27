/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.action.ActionRequest;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * General ModelExecutor interface.
 */
public interface ModelExecutor {

    Configuration suppressExceptionConfiguration = Configuration
        .builder()
        .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
        .build();

    default <T> ActionRequest getRemoteModelInferenceRequest(Map<String, String> parameters, String modelId) {
        if (parameters == null) {
            throw new IllegalArgumentException("wrong input. The model input cannot be empty.");
        }
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

        ActionRequest request = new MLPredictionTaskRequest(modelId, mlInput, null);

        return request;

    }

    /**
     * Read ModelTensorOutput and find value in DataAsMap and Data based on field Name
     * @param modelTensorOutput
     * @param originalModelOutputFieldName
     * @return
     */
    default Object getModelOutputValue(ModelTensorOutput modelTensorOutput, String originalModelOutputFieldName, boolean ignoreMissing) {
        Object modelOutputValue;
        try {
            // getMlModelOutputs() returns a list or collection.
            // Adding null check for modelTensorOutput
            if (modelTensorOutput != null && !modelTensorOutput.getMlModelOutputs().isEmpty()) {
                // getMlModelOutputs() returns a list of ModelTensors
                // accessing the first element.
                ModelTensors output = modelTensorOutput.getMlModelOutputs().get(0);
                // Adding null check for output
                if (output != null && output.getMlModelTensors() != null && !output.getMlModelTensors().isEmpty()) {
                    // getMlModelTensors() returns a list of ModelTensor
                    if (output.getMlModelTensors().size() == 1) {
                        ModelTensor tensor = output.getMlModelTensors().get(0);
                        // try getDataAsMap first
                        Map<String, ?> tensorInDataAsMap = tensor.getDataAsMap();
                        if (tensorInDataAsMap != null) {
                            modelOutputValue = getModelOutputField(tensorInDataAsMap, originalModelOutputFieldName, ignoreMissing);
                        }
                        // if dataAsMap is empty try getData
                        else {
                            // pase data type
                            modelOutputValue = parseGetDataInTensor(tensor);
                        }
                    } else {

                        // for multiple tensors, initiate an array
                        ArrayList tensorArray = new ArrayList<>();
                        for (int i = 0; i < output.getMlModelTensors().size(); i++) {
                            ModelTensor tensor = output.getMlModelTensors().get(i);

                            // Adding null check for tensor
                            if (tensor != null) {
                                // Assuming getData() method may throw an exception
                                // if the data is not available or is in an invalid state.
                                try {
                                    // try getDataAsMap first
                                    Map<String, ?> tensorInDataAsMap = tensor.getDataAsMap();
                                    if (tensorInDataAsMap != null) {

                                        tensorArray
                                            .add(getModelOutputField(tensorInDataAsMap, originalModelOutputFieldName, ignoreMissing));
                                    }
                                    // if dataAsMap is empty try getData
                                    else {
                                        tensorArray.add(parseGetDataInTensor(tensor));
                                    }
                                } catch (Exception e) {
                                    // Handle the exception accordingly
                                    throw new RuntimeException("Error accessing tensor data: " + e.getMessage());
                                }
                            }
                        }
                        modelOutputValue = tensorArray;
                    }

                } else {
                    throw new RuntimeException("Output tensors are null or empty.");
                }
            } else {
                throw new RuntimeException("Model outputs are null or empty.");
            }
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage());
        }
        return modelOutputValue;
    }

    private static Object parseGetDataInTensor(ModelTensor tensor) {
        Object modelOutputValue;
        if (tensor.getDataType().isInteger()) {
            modelOutputValue = Arrays.stream(tensor.getData()).map(Number::intValue).map(Integer::new).collect(Collectors.toList());
        } else if (tensor.getDataType().isFloating()) {
            modelOutputValue = Arrays.stream(tensor.getData()).map(Number::floatValue).map(Float::new).collect(Collectors.toList());
        } else if (tensor.getDataType().isString()) {
            modelOutputValue = Arrays.stream(tensor.getData()).map(String::valueOf).map(String::new).collect(Collectors.toList());
        } else if (tensor.getDataType().isBoolean()) {
            modelOutputValue = Arrays
                .stream(tensor.getData())
                .map(num -> num.intValue() != 0)
                .map(Boolean::new)
                .collect(Collectors.toList());
        } else {
            throw new RuntimeException("unsupported data type in prediction data.");
        }
        return modelOutputValue;
    }

    /**
     * filter model outputs by field name,
     * supported using dot path look up for objects
     * when a field name not provided, default to get all prediction outputs
     */
    default Object getModelOutputField(Map<String, ?> modelTensorOutputMap, String fieldName, boolean ignoreMissing) throws IOException {

        if (fieldName == null) {
            return modelTensorOutputMap;
        } else if (modelTensorOutputMap.containsKey(fieldName) && modelTensorOutputMap != null) {
            Object filteredOutput = modelTensorOutputMap.get(fieldName);
            return filteredOutput;
        } else {
            try {
                Object filteredOutput = JsonPath.using(suppressExceptionConfiguration).parse(modelTensorOutputMap).read(fieldName);
                return filteredOutput;
            } catch (Exception e) {
                if (ignoreMissing) {
                    return modelTensorOutputMap;
                } else {
                    throw new IOException("model inference output can not find field name: " + fieldName, e);
                }
            }
        }
    }

    /**
     * cast object to JsonString
     */

    default String toString(Object originalFieldValue) {
        return gson.toJson(originalFieldValue);
    }

    /**
     * write new dot path within nested object
     * return a list of dot path
     * for example foo.*.bar.*.quk to be [foo.0.bar.0.quk, foo.0.bar.1.quk..]
     */
    default List<String> writeNewDotPathForNestedObject(Object json, String dotPath) {
        int lastDotIndex = dotPath.lastIndexOf('.');
        List<String> dotPaths = new ArrayList<>();
        if (lastDotIndex != -1) { // Check if dot exists
            String leadingDotPath = dotPath.substring(0, lastDotIndex);
            String lastLeave = dotPath.substring(lastDotIndex + 1, dotPath.length());
            Configuration configuration = Configuration
                .builder()
                .options(Option.ALWAYS_RETURN_LIST, Option.AS_PATH_LIST, Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build();

            List<String> resultPaths = JsonPath.using(configuration).parse(json).read(leadingDotPath);
            for (String path : resultPaths) {
                dotPaths.add(convertToDotPath(path) + "." + lastLeave);
            }
            return dotPaths;
        } else {
            dotPaths.add(dotPath);
        }
        return dotPaths;
    }

    /**
     * Convert JSONPath format to dot path notation format
     * for example $['foo'][0]['bar']['quz'][0] to foo.0.bar.quiz.0
     */
    default String convertToDotPath(String path) {

        return path.replaceAll("\\[(\\d+)\\]", "$1\\.").replaceAll("\\['(.*?)']", "$1\\.").replaceAll("^\\$", "").replaceAll("\\.$", "");
    }

}
