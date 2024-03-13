/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * General ModelExecutor interface.
 */
public interface ModelExecutor {

    default <T> ActionRequest getRemoteModelInferenceRequest(Map<String, String> parameters, String modelId) {

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        if (inputDataSet.getParameters() == null) {
            throw new IllegalArgumentException("wrong input. The model input cannot be empty.");
        }
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

        ActionRequest request = new MLPredictionTaskRequest(modelId, mlInput, null);

        return request;

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
                // avoid assigning prediction values in all positions of array, remove the last [*]
                String jsonPath = findDotPathForNestedObject(modelTensorOutputMap, fieldName).replaceAll("\\[\\*\\]$", "");
                Object filteredOutput = JsonPath.read(modelTensorOutputMap, jsonPath);
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
        String originalFieldValueAsString = gson.toJson(originalFieldValue);
        return originalFieldValueAsString;
    }

    /**
     * find json path in nested object based on dot path without explicit array locations
     * return a json path
     * for example foo.bar.quz to be  foo[*].bar.quiz[*]
     */
    default String findDotPathForNestedObject(Object json, String dotPath) {
        String[] pathComponents = dotPath.split("\\.");
        StringBuilder revisedPath = new StringBuilder();

        Object jsonObject = JsonPath.parse(json).json();
        Object currentValue = jsonObject;
        StringBuilder currentPath = new StringBuilder();
        for (int i = 0; i < pathComponents.length; i++) {

            if (!(revisedPath.length() == 0)) {
                revisedPath.append(".");
                currentPath.append(".");
            }
            revisedPath.append(pathComponents[i]);
            currentPath.append(pathComponents[i]);

            Configuration suppressExceptionConfiguration = Configuration
                .builder()
                .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build();
            currentValue = JsonPath.using(suppressExceptionConfiguration).parse(jsonObject).read(currentPath.toString());

            if (currentValue instanceof ArrayList) {
                ArrayList<?> valueArray = (ArrayList<?>) currentValue;
                for (int j = 0; j < valueArray.size(); j++) {
                    StringBuilder leafPathInArray = new StringBuilder(currentPath);
                    if (i != pathComponents.length - 1) {
                        leafPathInArray.append(".[").append(j).append("].").append(pathComponents[i + 1]);
                        Object leafValue = JsonPath
                            .using(suppressExceptionConfiguration)
                            .parse(jsonObject)
                            .read(leafPathInArray.toString());
                        if ((leafValue == null) && (j == valueArray.size() - 1)) {
                            return null;
                        } else {
                            currentPath.append("[").append(j).append("]");
                            revisedPath.append("[*]");
                            break;
                        }
                    } else {
                        currentPath.append("[").append(j).append("]");
                        revisedPath.append("[*]");
                        break;
                    }
                }
            }
        }

        return revisedPath.toString();
    }

    /**
     * write new dot path within nested object
     * return a list of dot path
     * for example foo.bar.quk to be [foo.0.bar.quk.0, foo.0.bar.quk.1..]
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

            String leadingDotPathInArrayExpression = findDotPathForNestedObject(json, leadingDotPath);

            List<String> resultPaths = JsonPath.using(configuration).parse(json).read(leadingDotPathInArrayExpression);
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
