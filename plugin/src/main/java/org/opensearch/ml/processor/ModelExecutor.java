/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;

/**
 * General ModelExecutor interface.
 */
public interface ModelExecutor {

    default <T> ActionRequest getRemoteModelInferenceResult(Map<String, String> parameters, String modelId) {

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        if (inputDataSet.getParameters() == null) {
            throw new IllegalArgumentException("wrong input. The model input cannot be empty.");
        }
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

        ActionRequest request = new MLPredictionTaskRequest(modelId, mlInput, null);

        return request;

    }

    default String getModelInputFieldValue(Object originalFieldValue) {
        Gson gson = new Gson();
        String originalFieldValueAsString = gson.toJson(originalFieldValue);
        return originalFieldValueAsString;
    }

    /**
     * filter model outputs by field name, supported using dot path look up for objects
     * default to get all prediction outputs
     */
    default Object getModelOutputField(ModelTensorOutput modelOutput, String fieldName, boolean ignoreMissing) throws IOException {
        Map<String, ?> modelTensorOutputMap = modelOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();

        if (fieldName == null) {
            return modelTensorOutputMap;
        } else if (modelTensorOutputMap.containsKey(fieldName)) {
            return modelTensorOutputMap.get(fieldName);
        } else {
            String modelTensorOutputMapAsString = gson.toJson(modelTensorOutputMap);

            ObjectMapper mapper = new ObjectMapper();

            try {
                Object obj = mapper.readValue(modelTensorOutputMapAsString, Object.class);
                Object filteredOutput = JsonPath.read(obj, fieldName);
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

}
