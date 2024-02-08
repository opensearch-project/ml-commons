/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.script.ScriptService;

import java.util.HashMap;
import java.util.Map;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RemoteInferencePreProcessFunction extends ConnectorPreProcessFunction {

    public static final String CONVERT_REMOTE_INFERENCE_PARAM_TO_OBJECT = "pre_process_function.convert_remote_inference_param_to_object";
    ScriptService scriptService;
    String preProcessFunction;

    Map<String, String> params;

    @Builder
    public RemoteInferencePreProcessFunction(ScriptService scriptService, String preProcessFunction, Map<String, String> params) {
        this.returnDirectlyForRemoteInferenceInput = false;
        this.scriptService = scriptService;
        this.preProcessFunction = preProcessFunction;
        this.params = params;
    }

    @Override
    public void validate(MLInput mlInput) {
        if (!(mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet)) {
            throw new IllegalArgumentException("This pre_process_function can only support RemoteInferenceInputDataSet");
        }
    }

    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        Map<String, Object> inputParams = new HashMap<>();
        Map<String, String> parameters = ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getParameters();
        if (params.containsKey(CONVERT_REMOTE_INFERENCE_PARAM_TO_OBJECT) &&
                Boolean.parseBoolean(params.get(CONVERT_REMOTE_INFERENCE_PARAM_TO_OBJECT))) {
            for (String key : parameters.keySet()) {
                if (isJson(parameters.get(key))) {
                    inputParams.put(key, gson.fromJson(parameters.get(key), Object.class));
                } else {
                    inputParams.put(key, parameters.get(key));
                }
            }
        } else {
            inputParams.putAll(parameters);
        }
        String processedInput = executeScript(scriptService, preProcessFunction, inputParams);
        if (processedInput == null) {
            throw new IllegalArgumentException("Preprocess function output is null");
        }
        Map<String, Object> map = gson.fromJson(processedInput, Map.class);
        return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(map)).build();
    }

}
