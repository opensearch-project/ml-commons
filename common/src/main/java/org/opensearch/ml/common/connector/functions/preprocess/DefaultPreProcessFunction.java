/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.script.ScriptService;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DefaultPreProcessFunction extends ConnectorPreProcessFunction {

    ScriptService scriptService;
    String preProcessFunction;
    boolean convertInputToJsonString;

    @Builder
    public DefaultPreProcessFunction(ScriptService scriptService, String preProcessFunction, boolean convertInputToJsonString) {
        this.returnDirectlyForRemoteInferenceInput = false;
        this.scriptService = scriptService;
        this.preProcessFunction = preProcessFunction;
        this.convertInputToJsonString = convertInputToJsonString;
    }

    @Override
    public void validate(MLInput mlInput) {}

    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            mlInput.toXContent(builder, EMPTY_PARAMS);
            String inputStr = builder.toString();
            Map inputParams = gson.fromJson(inputStr, Map.class);
            if (convertInputToJsonString) {
                inputParams = convertScriptStringToJsonString(Map.of("parameters", gson.fromJson(inputStr, Map.class)));
            }
            String processedInput = executeScript(scriptService, preProcessFunction, inputParams);
            if (processedInput == null) {
                throw new IllegalArgumentException("Preprocess function output is null");
            }
            Map<String, Object> map = gson.fromJson(processedInput, Map.class);
            return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(map)).build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to run pre-process function: Wrong input");
        }
    }

}
